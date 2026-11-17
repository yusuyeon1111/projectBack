package org.example.project;

import org.example.project.security.JwtTokenProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Redis가 연결은 받지만 응답하지 않는 장애(과부하, 네트워크 문제). 기본 설정이면 요청이 60초씩 멈춘다.
@SpringBootTest(properties = {
        "jwt.secret=dGVzdC1zZWNyZXQta2V5LWZvci1zZWN1cml0eS1hY2Nlc3MtdGVzdA==",
        "spring.data.redis.usessl=false",
        "KAKAO_CLIENT_ID=test",
        "AWS_REGION=ap-northeast-2", "AWS_S3_BUCKET_NAME=test",
})
@AutoConfigureMockMvc
class RedisHangTest {

    static final ServerSocket silentRedis;
    static final List<Socket> accepted = new CopyOnWriteArrayList<>();

    static {
        try {
            silentRedis = new ServerSocket(0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        Thread t = new Thread(() -> {
            while (!silentRedis.isClosed()) {
                try {
                    accepted.add(silentRedis.accept()); // 받기만 하고 아무 응답도 하지 않는다
                } catch (IOException ignored) {
                }
            }
        });
        t.setDaemon(true);
        t.start();
    }

    @DynamicPropertySource
    static void redis(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", () -> "127.0.0.1");
        registry.add("spring.data.redis.port", silentRedis::getLocalPort);
    }

    @AfterAll
    static void close() throws IOException {
        silentRedis.close();
        for (Socket s : accepted) s.close();
    }

    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider jwt;

    @Test
    void 응답하지_않는_Redis는_명령_타임아웃_안에_503으로_끝난다() throws Exception {
        var auth = new UsernamePasswordAuthenticationToken("redis-hang-user", null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        String bearer = "Bearer " + jwt.generateToken(auth).getAccessToken();

        long start = System.currentTimeMillis();
        mvc.perform(get("/api/member/myPage").header("Authorization", bearer))
                .andExpect(status().isServiceUnavailable());
        assertThat(System.currentTimeMillis() - start).isLessThan(5000);
    }
}
