package org.example.project.security;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.GenericFilterBean;

import org.springframework.dao.DataAccessException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends GenericFilterBean {

    public static final String REDIS_UNAVAILABLE = JwtAuthenticationFilter.class.getName() + ".REDIS_UNAVAILABLE";

    private final JwtTokenProvider jwtTokenProvider;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String path = httpRequest.getRequestURI();

        if(path.startsWith("/api/post/list")) {
            chain.doFilter(request, response);
            return;
        }

        String token = resolveToken(httpRequest);
        // 토큰 유효성 검사
        // refresh token은 서명이 맞아도 권한 정보가 없어 인증에 쓰지 않는다(재발급 API 전용)
        if (token != null && jwtTokenProvider.validateToken(token) && jwtTokenProvider.parseRefreshToken(token) == null) {
            Authentication authentication = jwtTokenProvider.getAuthentication(token);
            try {
                if (!isRevoked(token, authentication.getName())) {
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (DataAccessException e) {
                // Redis를 못 보면 로그아웃·차단된 토큰인지 알 수 없으므로 인증하지 않는다(fail-closed).
                // 공개 API는 비로그인으로 계속 쓰고, 로그인이 필요한 API는 진입점이 401 대신 503을 준다
                log.warn("Redis 장애로 토큰 폐기 여부를 확인하지 못해 비로그인으로 처리: {}", e.getMessage());
                request.setAttribute(REDIS_UNAVAILABLE, true);
            }
        }

        chain.doFilter(request, response);
    }

    // 로그아웃한 토큰, 차단된 회원, refresh token 재사용으로 끊긴 기기(sid)의 토큰인지 Redis에서 확인한다
    private boolean isRevoked(String token, String username) {
        if (!ObjectUtils.isEmpty(redisTemplate.opsForValue().get(token))) return true;
        List<String> keys = new ArrayList<>(List.of("BAN:" + username));
        String sid = jwtTokenProvider.getSid(token);
        if (sid != null) keys.add(RefreshTokenService.revokedKey(sid));
        Long hits = redisTemplate.countExistingKeys(keys);
        return hits != null && hits > 0;
    }

    // HTTP 요청 헤더에서 JWT를 꺼내 반환
    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);  // "Bearer " 제거 실제 토큰만 추출
        }
        return null;
    }
}
