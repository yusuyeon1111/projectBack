package org.example.project.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.security.authentication.LockedException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 아이디별 로그인 시도 제한. WINDOW 안에 MAX_ATTEMPTS번 실패하면 그 아이디의 로그인을 막는다.
 *
 * 비밀번호를 확인하기 "전에" 시도 횟수를 먼저 올린다. 실패한 뒤에 세면 동시에 들어온 요청들이 모두
 * "아직 5번 안 됨"을 보고 통과해 제한보다 많이 시도할 수 있다. 성공하면 기록을 지운다.
 *
 * ponytail: 아이디 기준이라 남이 일부러 틀려 내 계정을 15분 막을 수 있다. 프록시 뒤라 클라이언트 IP를
 * 믿을 수 없어서 IP 기준은 넣지 않았다. 문제가 되면 X-Forwarded-For를 신뢰 설정한 뒤 IP 기준을 더한다.
 */
@Component
@RequiredArgsConstructor
public class LoginAttemptLimiter {

    static final int MAX_ATTEMPTS = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);

    // 첫 시도에만 만료를 건다. INCR와 EXPIRE를 따로 부르다 중간에 끊기면 만료 없는 키가 남아 영구히 막힌다
    private static final RedisScript<Long> INCREMENT = RedisScript.of("""
            local n = redis.call('INCR', KEYS[1])
            if n == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
            return n
            """, Long.class);

    private final StringRedisTemplate redis;

    static String key(String username) {
        return "LOGIN_FAIL:" + username;
    }

    public void acquire(String username) {
        Long attempts = redis.execute(INCREMENT, List.of(key(username)), String.valueOf(WINDOW.toMillis()));
        if (attempts != null && attempts > MAX_ATTEMPTS) {
            throw new LockedException("로그인 시도가 너무 많습니다. " + WINDOW.toMinutes() + "분 후 다시 시도해주세요.");
        }
    }

    public void reset(String username) {
        redis.delete(key(username));
    }
}
