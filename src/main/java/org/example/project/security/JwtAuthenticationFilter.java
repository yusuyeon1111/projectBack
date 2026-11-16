package org.example.project.security;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.GenericFilterBean;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends GenericFilterBean {

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
            // 로그아웃 여부 확인
            String isLogout = (String) redisTemplate.opsForValue().get(token);
            // 인증 객체 생성 및 등록
            if(ObjectUtils.isEmpty(isLogout)) {
                Authentication authentication = jwtTokenProvider.getAuthentication(token);
                // 차단된 회원이나 refresh token 재사용으로 끊긴 기기의 토큰은 인증하지 않는다. 키 둘을 EXISTS 한 번으로 본다
                List<String> blocked = new ArrayList<>(List.of("BAN:" + authentication.getName()));
                String sid = jwtTokenProvider.getSid(token);
                if (sid != null) blocked.add(RefreshTokenService.revokedKey(sid));
                Long hits = redisTemplate.countExistingKeys(blocked);
                if (hits == null || hits == 0) {
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        }

        chain.doFilter(request, response);
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
