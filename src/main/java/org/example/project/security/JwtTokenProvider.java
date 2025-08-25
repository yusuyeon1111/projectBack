package org.example.project.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.*;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.example.project.member.dto.JwtToken;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.stream.Collectors;

@Slf4j
@Component
public class JwtTokenProvider {
    private final Key key;
    public static final long ACCESS_TIME = Duration.ofHours(2).toMillis();
    public static final long REFRESH_TIME = Duration.ofDays(7).toMillis();

    public JwtTokenProvider(@Value("${jwt.secret}") String secretkey) {
        byte[] keyBytes = Decoders.BASE64.decode(secretkey);
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    // JWT Access Token과 Refrech Token 생성
    public JwtToken generateToken(Authentication authentication) {
        String authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));
        long now = (new Date().getTime());

        Date assessTokenExpiresIn = new Date(now + ACCESS_TIME);
        Date refreshTokenExpiresIn = new Date(now + REFRESH_TIME);

        // Access Token 생성
        String accessToken = Jwts.builder()
                .setSubject(authentication.getName()) // 사용자 식별자 (주로 username)
                .claim("roles",authorities)            // 권한 정보를 "auth"라는 이름으로 저장
                .setExpiration(assessTokenExpiresIn)  // 만료 시간 설정
                .signWith(key, SignatureAlgorithm.HS256) // 비밀키와 서명 알고리즘 지정
                .compact();

        // Refresh Token 생성 사용자 정보나 권한을 넣지 않고 유효 기간과 서명만 설정
        String refreshToken = Jwts.builder()
                .setExpiration(refreshTokenExpiresIn)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        // 토큰 DTO 반환
        return JwtToken.builder()
                .grantType("Bearer")
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .refreshTokenExpiresIn(REFRESH_TIME)
                .roles(Arrays.asList(authorities.split(",")))
                .build();
    }

    // JWT 토큰 → 인증 객체(Authentication)로 변환
    public Authentication getAuthentication(String token) {
        Claims claims = extractClaims(token);

        if (claims.get("roles") == null) {
            throw new RuntimeException("Invalid token");
        }

        Collection<? extends GrantedAuthority> authorities = Arrays.stream(claims.get("roles").toString().split(","))
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());

        UserDetails principal = new User(claims.getSubject(),"",authorities);

        return new UsernamePasswordAuthenticationToken(principal,"",authorities);
    }

    // secret key를 사용해 JWT를 파싱하여 토큰이 유효한지 검증
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (SecurityException | MalformedJwtException e) {
            log.error("Invalid JWT signature");
            return false;
        } catch (ExpiredJwtException e) {
            log.error("Expired JWT");
            return false;
        } catch (UnsupportedJwtException e) {
            log.error("Unsupported JWT token");
            return false;
        } catch (IllegalArgumentException e) {
            log.error("JWT claims string is empty");
            return false;
        } catch (Exception e) {
            log.error(e.getMessage());
            return false;
        }
    }

    // 토큰의 남은 유효 시간을 계산
    public Long getExpiration(String token) {
        Date expiration = extractClaims(token).getExpiration();
        Long now = new Date().getTime();

        return (expiration.getTime() - now);
    }

    // 토큰에서 Payload(Claims)를 추출, 만료되었어도 정보는 반환
    private Claims extractClaims(String token) {
        try {
          return Jwts.parserBuilder()
                  .setSigningKey(key)
                  .build()
                  .parseClaimsJws(token)
                  .getBody();
        } catch (ExpiredJwtException e){
            return e.getClaims();
        }
    }
}
