package org.example.project.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.project.member.dto.JwtToken;
import org.example.project.member.entity.Member;
import org.example.project.member.repository.MemberRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Refresh Token 회전(rotation).
 *
 * 재발급할 때마다 refresh token을 새로 주고 이전 것은 못 쓰게 한다. 기기(로그인)마다 지금 유효한 refresh token의 jti를
 * Redis 해시 RTS:{username}의 필드 {sid}에 둔다. 해시라서 차단은 키 하나를 지우면 모든 기기가 끊긴다.
 *
 * - 이미 바뀐 refresh token이 다시 오면 탈취로 보고 그 기기의 세션을 끊는다(재사용 탐지).
 * - 단, 탭 여러 개가 같은 토큰으로 거의 동시에 재발급하면 정상 사용자도 재사용으로 걸린다. 그래서 바꾼 직후
 *   GRACE 동안은 같은 이전 토큰에 방금 만든 토큰 쌍을 그대로 돌려준다(RTG:{이전 jti}).
 * - 비교와 교체가 따로 실행되면 동시 요청 둘 다 "현재 토큰이 맞다"를 보고 각자 회전한다. Lua 스크립트로 한 번에 실행한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    static final Duration GRACE = Duration.ofSeconds(10);

    private static final RedisScript<String> ROTATE = RedisScript.of("""
            local current = redis.call('HGET', KEYS[1], ARGV[1])
            if current == ARGV[2] then
              redis.call('HSET', KEYS[1], ARGV[1], ARGV[3])
              redis.call('PEXPIRE', KEYS[1], ARGV[4])
              redis.call('SET', KEYS[2], ARGV[5], 'PX', ARGV[6])
              return ARGV[5]
            end
            local issued = redis.call('GET', KEYS[2])
            if issued then return issued end
            if current then
              redis.call('HDEL', KEYS[1], ARGV[1])
              return 'REUSED'
            end
            return 'NONE'
            """, String.class);

    private final JwtTokenProvider jwtTokenProvider;
    private final StringRedisTemplate redis;
    private final MemberRepository memberRepository;
    private final ObjectMapper objectMapper;

    static String sessionsKey(String username) {
        return "RTS:" + username;
    }

    // 로그인할 때 새 기기 세션을 만든다
    public JwtToken issue(Authentication authentication) {
        String sid = UUID.randomUUID().toString();
        String jti = UUID.randomUUID().toString();
        JwtToken token = jwtTokenProvider.generateToken(authentication, sid, jti);
        String key = sessionsKey(authentication.getName());
        redis.opsForHash().put(key, sid, jti);
        redis.expire(key, Duration.ofMillis(JwtTokenProvider.REFRESH_TIME));
        return token;
    }

    // 회전에 성공하면 새 토큰 쌍, 아니면 empty(호출 측에서 401)
    public Optional<JwtToken> reissue(String refreshToken) {
        Claims claims = jwtTokenProvider.parseRefreshToken(refreshToken);
        if (claims == null) return Optional.empty();
        String username = claims.getSubject();
        String sid = claims.get("sid", String.class);

        // 권한·차단 여부는 토큰이 아니라 DB 기준이다
        Optional<Member> member = memberRepository.findByUsernameAndDelYn(username, "N");
        if (member.isEmpty()) return Optional.empty();
        Authentication auth = new UsernamePasswordAuthenticationToken(username, null,
                member.get().getRoles().stream().map(SimpleGrantedAuthority::new).toList());
        String nextJti = UUID.randomUUID().toString();
        JwtToken next = jwtTokenProvider.generateToken(auth, sid, nextJti);

        String result = redis.execute(ROTATE,
                List.of(sessionsKey(username), "RTG:" + claims.getId()),
                sid, claims.getId(), nextJti,
                String.valueOf(JwtTokenProvider.REFRESH_TIME), toJson(next), String.valueOf(GRACE.toMillis()));

        if ("REUSED".equals(result)) {
            log.warn("이미 교체된 refresh token 재사용 감지 - 해당 기기 세션 종료: username={}, sid={}", username, sid);
            return Optional.empty();
        }
        if ("NONE".equals(result)) return Optional.empty(); // 로그아웃·차단으로 세션이 없다
        return Optional.of(fromJson(result));
    }

    // 로그아웃한 기기의 세션만 지운다. sid가 없는 예전 토큰이면 지울 것이 없다
    public void revoke(String username, String sid) {
        if (sid != null) redis.opsForHash().delete(sessionsKey(username), sid);
    }

    // 차단: 모든 기기의 세션을 지운다
    public void revokeAll(String username) {
        redis.delete(sessionsKey(username));
    }

    private String toJson(JwtToken token) {
        try {
            return objectMapper.writeValueAsString(token);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private JwtToken fromJson(String json) {
        try {
            return objectMapper.readValue(json, JwtToken.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
