package org.example.project;

import com.jayway.jsonpath.JsonPath;
import org.example.project.notification.service.SseTicketService;
import org.example.project.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// DB/Redis가 필요하다 (CLAUDE.md의 실행 환경변수 참고). JWT·외부 연동 값은 여기서 더미로 채운다.
@SpringBootTest(properties = {
        "jwt.secret=dGVzdC1zZWNyZXQta2V5LWZvci1zZWN1cml0eS1hY2Nlc3MtdGVzdA==",
        "spring.data.redis.usessl=false",
        "KAKAO_CLIENT_ID=test", "AWS_ACCESS_KEY_ID=test", "AWS_SECRET_ACCESS_KEY=test",
        "AWS_REGION=ap-northeast-2", "AWS_S3_BUCKET_NAME=test",
})
@AutoConfigureMockMvc
class SseTicketTest {

    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider jwt;
    @Autowired SseTicketService ticketService;
    @Autowired RedisTemplate<String, String> redisTemplate;

    private String accessToken(String username) {
        var auth = new UsernamePasswordAuthenticationToken(username, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        return jwt.generateToken(auth).getAccessToken();
    }

    private String issueViaApi(String token) throws Exception {
        String body = mvc.perform(post("/api/notifications/ticket").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.ticket");
    }

    @Test
    void 발급된_티켓은_토큰의_회원에_묶이고_한_번만_쓸_수_있다() throws Exception {
        String ticket = issueViaApi(accessToken("ticket-user"));
        try {
            Long ttl = redisTemplate.getExpire("SSE:" + ticket, TimeUnit.SECONDS);
            assertThat(ttl).isPositive().isLessThanOrEqualTo(30);

            assertThat(ticketService.consume(ticket)).contains("ticket-user");
            assertThat(ticketService.consume(ticket)).isEmpty();
            assertThat(redisTemplate.hasKey("SSE:" + ticket)).isFalse();
        } finally {
            redisTemplate.delete("SSE:" + ticket);
        }
    }

    @Test
    void 모르는_티켓이나_빈_티켓은_비어_있다() {
        assertThat(ticketService.consume("no-such-ticket")).isEmpty();
        assertThat(ticketService.consume("")).isEmpty();
        assertThat(ticketService.consume("  ")).isEmpty();
        assertThat(ticketService.consume(null)).isEqualTo(Optional.empty());
    }

    @Test
    void 토큰_없이_티켓_발급은_401() throws Exception {
        mvc.perform(post("/api/notifications/ticket")).andExpect(status().isUnauthorized());
    }

    @Test
    void 로그아웃한_토큰으로는_티켓을_받을_수_없다() throws Exception {
        String token = accessToken("ticket-logout");
        try {
            mvc.perform(post("/api/member/logout").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
            mvc.perform(post("/api/notifications/ticket").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        } finally {
            redisTemplate.delete(token);
        }
    }

    @Test
    void 차단된_회원의_토큰으로는_티켓을_받을_수_없다() throws Exception {
        String token = accessToken("ticket-banned");
        redisTemplate.opsForValue().set("BAN:ticket-banned", "ban", 1, TimeUnit.MINUTES);
        try {
            mvc.perform(post("/api/notifications/ticket").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        } finally {
            redisTemplate.delete("BAN:ticket-banned");
        }
    }
}
