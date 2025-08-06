package org.example.project.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.project.member.dto.JwtToken;
import org.example.project.member.entity.Member;
import org.example.project.member.repository.MemberRepository;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final MemberRepository memberRepository;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        String registrationId = oauthToken.getAuthorizedClientRegistrationId();
        //OAuth2 로그인 사용자 정보
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = "";
        if(("kakao").equals(registrationId)) {
            Map<String, Object> kakaoAccount = (Map<String, Object>) oAuth2User.getAttributes().get("kakao_account");
            email = (String)kakaoAccount.get("email");
        } else if(("naver").equals(registrationId)) {
            email = (String)oAuth2User.getAttributes().get("email");
        }

        //기존 회원이라면 정보 가져오기. 없으면 예외
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Not registered user"));

        //JWT 토큰 생성
        JwtToken token = jwtTokenProvider.generateToken(authentication);

        redisTemplate.opsForValue().set("RT:"+authentication.getName(),token.getRefreshToken(), token.getRefreshTokenExpiresIn(), TimeUnit.MILLISECONDS);
        String redirectUri = UriComponentsBuilder
                .fromUriString("http://localhost:3000/oauth2/success")
                .queryParam("accessToken", token.getAccessToken())
                .queryParam("refreshToken", token.getRefreshToken())
                .queryParam("username", member.getUsername())
                .build()
                .toUriString();

        response.sendRedirect(redirectUri);
    }
}
