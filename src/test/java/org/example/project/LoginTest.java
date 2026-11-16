package org.example.project;

import org.example.project.member.entity.Member;
import org.example.project.member.repository.MemberRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

// 로그인 실패 응답이 아이디 존재 여부를 드러내지 않는지, 시도 횟수 제한이 동시 요청에도 지켜지는지 확인한다.
@SpringBootTest(properties = {
        "jwt.secret=dGVzdC1zZWNyZXQta2V5LWZvci1zZWN1cml0eS1hY2Nlc3MtdGVzdA==",
        "spring.data.redis.usessl=false",
        "KAKAO_CLIENT_ID=test", "AWS_ACCESS_KEY_ID=test", "AWS_SECRET_ACCESS_KEY=test",
        "AWS_REGION=ap-northeast-2", "AWS_S3_BUCKET_NAME=test",
})
@AutoConfigureMockMvc
class LoginTest {

    static final String PASSWORD = "Test1234!";

    @Autowired MockMvc mvc;
    @Autowired MemberRepository memberRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired StringRedisTemplate redis;

    final String username = "lg-" + UUID.randomUUID().toString().substring(0, 8);
    final String nobody = username + "-nobody";
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
        redis.delete(List.of("LOGIN_FAIL:" + username, "LOGIN_FAIL:" + nobody, "RT:" + username));
    }

    @Test
    void 없는_아이디와_틀린_비밀번호는_같은_응답이다() throws Exception {
        MvcResult wrongPassword = signIn(username, "wrong");
        MvcResult noSuchUser = signIn(nobody, "wrong");

        assertThat(wrongPassword.getResponse().getStatus()).isEqualTo(401);
        assertThat(noSuchUser.getResponse().getStatus()).isEqualTo(401);
        assertThat(wrongPassword.getResponse().getContentAsString())
                .isEqualTo(noSuchUser.getResponse().getContentAsString());
    }

    @Test
    void 차단된_회원은_비밀번호가_맞아도_로그인되지_않는다() throws Exception {
        member.setDelYn("Y");
        memberRepository.save(member);

        assertThat(signIn(username, PASSWORD).getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void 다섯_번_틀리면_맞는_비밀번호로도_잠시_막힌다() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(signIn(username, "wrong").getResponse().getStatus()).isEqualTo(401);
        }
        assertThat(signIn(username, PASSWORD).getResponse().getStatus()).isEqualTo(429);
    }

    @Test
    void 로그인에_성공하면_실패_기록이_지워진다() throws Exception {
        for (int i = 0; i < 4; i++) signIn(username, "wrong");
        assertThat(signIn(username, PASSWORD).getResponse().getStatus()).isEqualTo(200);

        for (int i = 0; i < 4; i++) signIn(username, "wrong");
        assertThat(signIn(username, PASSWORD).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void 동시에_시도해도_비밀번호_확인은_다섯_번까지만_한다() throws Exception {
        List<Integer> statuses = Collections.synchronizedList(new ArrayList<>());
        ExecutorService pool = Executors.newFixedThreadPool(20);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(20);
        for (int i = 0; i < 20; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    statuses.add(signIn(username, "wrong").getResponse().getStatus());
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

        assertThat(statuses.stream().filter(s -> s == 401)).hasSize(5);
        assertThat(statuses.stream().filter(s -> s == 429)).hasSize(15);
    }

    private MvcResult signIn(String username, String password) throws Exception {
        return mvc.perform(post("/api/member/signin").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}")).andReturn();
    }
}
