package org.example.project.notification.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

// EventSource는 Authorization 헤더를 못 붙이므로, Bearer 토큰으로 받은 1회용 티켓을 쿼리스트링에 실어 구독한다.
// URL에 access token을 직접 싣지 않으려는 것이라 티켓은 짧게 살고 한 번 쓰면 사라진다.
@Service
@RequiredArgsConstructor
public class SseTicketService {

    private static final Duration TTL = Duration.ofSeconds(30);

    private final RedisTemplate<String, String> redisTemplate;

    public String issue(String username) {
        String ticket = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set("SSE:" + ticket, username, TTL);
        return ticket;
    }

    // GETDEL로 조회와 삭제를 한 번에 해서, 동시에 같은 티켓을 내도 한 요청만 회원을 얻는다
    public Optional<String> consume(String ticket) {
        if (!StringUtils.hasText(ticket)) {
            return Optional.empty();
        }
        return Optional.ofNullable(redisTemplate.opsForValue().getAndDelete("SSE:" + ticket));
    }
}
