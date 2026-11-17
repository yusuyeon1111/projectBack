package org.example.project;

import org.example.project.member.entity.Member;
import org.example.project.member.repository.MemberRepository;
import org.example.project.security.JwtTokenProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Redis가 죽었을 때의 동작을 고정한다. 아무것도 듣지 않는 포트를 Redis로 지정해 장애를 만든다.
@SpringBootTest(properties = {
        "jwt.secret=dGVzdC1zZWNyZXQta2V5LWZvci1zZWN1cml0eS1hY2Nlc3MtdGVzdA==",
        "spring.data.redis.usessl=false",
        "spring.data.redis.port=1",
        "KAKAO_CLIENT_ID=test", "AWS_ACCESS_KEY_ID=test", "AWS_SECRET_ACCESS_KEY=test",
        "AWS_REGION=ap-northeast-2", "AWS_S3_BUCKET_NAME=test",
})
@AutoConfigureMockMvc
class RedisOutageTest {

    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider jwt;
    @Autowired MemberRepository memberRepository;
    @Autowired PasswordEncoder passwordEncoder;

    final String username = "ro-" + UUID.randomUUID().toString().substring(0, 8);

    String bearer() {
        var auth = new UsernamePasswordAuthenticationToken("redis-outage-user", null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        return "Bearer " + jwt.generateToken(auth).getAccessToken();
    }

    @Test
    void 로그인이_필요한_API는_503을_빨리_준다() throws Exception {
        long start = System.currentTimeMillis();
        mvc.perform(get("/api/member/myPage").header("Authorization", bearer()))
                .andExpect(status().isServiceUnavailable());
        assertThat(System.currentTimeMillis() - start).isLessThan(3000);
    }

    @Test
    void 공개_API는_로그인한_사용자도_비로그인처럼_쓸_수_있다() throws Exception {
        mvc.perform(post("/api/post/list").contentType(MediaType.APPLICATION_JSON).content("{\"page\":0,\"size\":5}")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/member/idCheck").param("username", "x").header("Authorization", bearer()))
                .andExpect(status().isOk());
    }

    @Test
    void 로그인과_재발급은_503이다() throws Exception {
        Member member = memberRepository.save(Member.builder()
                .username(username).nickname(username).email(username + "@test.com")
                .password(passwordEncoder.encode("Test1234!")).roles(new ArrayList<>(List.of("ROLE_USER")))
                .delYn("N").build());
        try {
            mvc.perform(post("/api/member/signin").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"" + username + "\",\"password\":\"Test1234!\"}"))
                    .andExpect(status().isServiceUnavailable());
        } finally {
            memberRepository.delete(member);
        }
        mvc.perform(post("/api/member/reissue").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"x\"}"))
                .andExpect(status().isUnauthorized()); // 형식이 틀린 토큰은 Redis까지 가지 않는다
    }
}
