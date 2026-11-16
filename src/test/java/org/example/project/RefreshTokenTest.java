package org.example.project;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.project.member.entity.Member;
import org.example.project.member.repository.MemberRepository;
import org.example.project.security.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Refresh Token 회전·재사용 탐지·동시 재발급 유예를 실제 Redis로 확인한다.
@SpringBootTest(properties = {
        "jwt.secret=dGVzdC1zZWNyZXQta2V5LWZvci1zZWN1cml0eS1hY2Nlc3MtdGVzdA==",
        "spring.data.redis.usessl=false",
        "KAKAO_CLIENT_ID=test", "AWS_ACCESS_KEY_ID=test", "AWS_SECRET_ACCESS_KEY=test",
        "AWS_REGION=ap-northeast-2", "AWS_S3_BUCKET_NAME=test",
})
@AutoConfigureMockMvc
class RefreshTokenTest {

    static final String PASSWORD = "Test1234!";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired MemberRepository memberRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired StringRedisTemplate redis;
    @Autowired JwtTokenProvider jwt;

    final String username = "rt-" + UUID.randomUUID().toString().substring(0, 8);
    Member member;

    @BeforeEach
    void setUp() {
        member = memberRepository.save(Member.builder()
                .username(username).nickname(username).email(username + "@test.com")
                .password(passwordEncoder.encode(PASSWORD)).roles(new ArrayList<>(List.of("ROLE_USER")))
                .delYn("N").build());
    }

    @AfterEach
    void cleanUp() {
        memberRepository.delete(member);
        redis.delete(List.of("RTS:" + username, "BAN:" + username));
    }

    @Test
    void 재발급하면_새_토큰을_주고_새_access_token으로_인증된다() throws Exception {
        JsonNode login = signIn();
        JsonNode next = reissueOk(login.get("refreshToken").asText());

        assertThat(next.get("refreshToken").asText()).isNotEqualTo(login.get("refreshToken").asText());
        mvc.perform(get("/api/member/myPage").header("Authorization", "Bearer " + next.get("accessToken").asText()))
                .andExpect(status().isOk());
        reissueOk(next.get("refreshToken").asText()); // 새 refresh token으로 다시 회전할 수 있다
    }

    @Test
    void 동시에_같은_토큰으로_재발급해도_모두_같은_새_토큰을_받는다() throws Exception {
        String refresh = signIn().get("refreshToken").asText();
        List<Integer> statuses = Collections.synchronizedList(new ArrayList<>());
        List<String> issued = Collections.synchronizedList(new ArrayList<>());

        runConcurrently(10, () -> {
            MvcResult r = reissue(refresh);
            statuses.add(r.getResponse().getStatus());
            if (r.getResponse().getStatus() == 200) issued.add(body(r).get("refreshToken").asText());
        });

        assertThat(statuses).containsOnly(200).hasSize(10);
        assertThat(issued.stream().distinct()).hasSize(1);
        reissueOk(issued.get(0)); // 그 토큰이 현재 토큰이라 이어서 쓸 수 있다
    }

    @Test
    void 유예가_지난_뒤_이전_토큰이_다시_오면_탈취로_보고_그_기기_세션을_끊는다() throws Exception {
        String old = signIn().get("refreshToken").asText();
        String current = reissueOk(old).get("refreshToken").asText();
        expireGrace(old);

        assertThat(reissue(old).getResponse().getStatus()).isEqualTo(401);
        // 정상 사용자가 가진 최신 토큰도 함께 무효가 된다. 누가 진짜인지 서버는 모르기 때문이다
        assertThat(reissue(current).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void 기기마다_세션이_따로라서_한_기기의_재발급이_다른_기기를_끊지_않는다() throws Exception {
        String phone = signIn().get("refreshToken").asText();
        String laptop = signIn().get("refreshToken").asText();

        reissueOk(phone);
        reissueOk(laptop);
    }

    @Test
    void 로그아웃한_기기는_재발급되지_않고_다른_기기는_유지된다() throws Exception {
        JsonNode phone = signIn();
        String laptop = signIn().get("refreshToken").asText();

        mvc.perform(post("/api/member/logout").header("Authorization", "Bearer " + phone.get("accessToken").asText()))
                .andExpect(status().isOk());

        assertThat(reissue(phone.get("refreshToken").asText()).getResponse().getStatus()).isEqualTo(401);
        reissueOk(laptop);
    }

    @Test
    void 차단된_회원은_재발급되지_않는다() throws Exception {
        String refresh = signIn().get("refreshToken").asText();
        member.setDelYn("Y");
        memberRepository.save(member);

        assertThat(reissue(refresh).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void access_token이나_엉터리_값으로는_재발급되지_않는다() throws Exception {
        String access = signIn().get("accessToken").asText();

        assertThat(reissue(access).getResponse().getStatus()).isEqualTo(401);
        assertThat(reissue("not-a-jwt").getResponse().getStatus()).isEqualTo(401);
    }


    private JsonNode signIn() throws Exception {
        return body(mvc.perform(post("/api/member/signin").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn());
    }

    private MvcResult reissue(String refreshToken) throws Exception {
        return mvc.perform(post("/api/member/reissue").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("refreshToken", refreshToken)))).andReturn();
    }

    private JsonNode reissueOk(String refreshToken) throws Exception {
        MvcResult r = reissue(refreshToken);
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        return body(r);
    }

    private JsonNode body(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString());
    }

    // 이전 토큰의 유예 키를 지워 10초가 지난 상태를 만든다
    private void expireGrace(String oldRefreshToken) {
        redis.delete("RTG:" + jwt.parseRefreshToken(oldRefreshToken).getId());
    }

    interface Task { void run() throws Exception; }

    private void runConcurrently(int threads, Task task) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    task.run();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await(30, TimeUnit.SECONDS);
        pool.shutdown();
    }
}
