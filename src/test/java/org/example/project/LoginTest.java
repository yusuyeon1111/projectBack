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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

// 로그인 실패 응답이 아이디 존재 여부를 드러내지 않는지 확인한다.
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
        redis.delete("RT:" + username);
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

    private MvcResult signIn(String username, String password) throws Exception {
        return mvc.perform(post("/api/member/signin").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}")).andReturn();
    }
}
