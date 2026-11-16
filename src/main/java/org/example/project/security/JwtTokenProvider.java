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
import java.util.UUID;
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

    // 테스트·내부용: 저장하지 않는 새 로그인 세션으로 토큰을 만든다. 실제 로그인은 RefreshTokenService.issue를 쓴다
    public JwtToken generateToken(Authentication authentication) {
        return generateToken(authentication, UUID.randomUUID().toString(), UUID.randomUUID().toString());
    }

    /**
     * Access Token과 Refresh Token을 만든다.
     * sid는 로그인 한 번(기기 하나)을 가리키고 재발급해도 유지된다. jti는 refresh token 한 장마다 새로 붙는다.
     * 둘 다 있어야 기기별로 "지금 유효한 refresh token이 이것인지"를 Redis에서 확인할 수 있다.
     */
    public JwtToken generateToken(Authentication authentication, String sid, String refreshJti) {
        String authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));
        long now = (new Date().getTime());

        String accessToken = Jwts.builder()
                .setSubject(authentication.getName()) // 회원 username
                .claim("roles", authorities)
                .claim("sid", sid) // 로그아웃 때 이 기기의 refresh token만 지우려고 넣는다
                .setExpiration(new Date(now + ACCESS_TIME))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        // 권한은 넣지 않는다. 재발급 때 DB에서 다시 읽어 차단·권한 변경이 바로 반영되게 한다
        String refreshToken = Jwts.builder()
                .setSubject(authentication.getName())
                .setId(refreshJti)
                .claim("sid", sid)
                .claim("typ", "refresh")
                .setExpiration(new Date(now + REFRESH_TIME))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        return JwtToken.builder()
                .grantType("Bearer")
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .refreshTokenExpiresIn(REFRESH_TIME)
                .roles(Arrays.asList(authorities.split(",")))
                .build();
    }

    // 서명이 맞고 만료되지 않은 refresh token이면 claims, 아니면 null. access token은 typ가 없어 걸러진다
    public Claims parseRefreshToken(String token) {
        try {
            Claims claims = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
            boolean valid = "refresh".equals(claims.get("typ")) && claims.getSubject() != null
                    && claims.getId() != null && claims.get("sid") != null;
            return valid ? claims : null;
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    // access token의 sid. 이 기능 전에 발급된 토큰에는 없다
    public String getSid(String accessToken) {
        Object sid = extractClaims(accessToken).get("sid");
        return sid == null ? null : sid.toString();
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
