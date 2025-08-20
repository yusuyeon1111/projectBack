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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.*;
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
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = "";
        String name = "";

        if ("kakao".equals(registrationId)) {
            Map<String, Object> kakaoAccount = (Map<String, Object>) oAuth2User.getAttributes().get("kakao_account");
            Map<String, Object> profile = (Map<String, Object>) kakaoAccount.get("profile");
            email = (String) kakaoAccount.get("email");
            name = (String) profile.get("nickname");
        } else if ("naver".equals(registrationId)) {
            email = (String) oAuth2User.getAttributes().get("email");
            name = (String) oAuth2User.getAttributes().get("name");
        }

        boolean isNewUser = false;
        Optional<Member> memberOpt = memberRepository.findByEmail(email);

        Member member;
        if (memberOpt.isPresent()) {
            member = memberOpt.get();
        } else {
            isNewUser = true;
            member = Member.builder()
                    .email(email)
                    .name(name)
                    .username(email)
                    .password(new BCryptPasswordEncoder().encode(UUID.randomUUID().toString()))
                    .roles(List.of("ROLE_USER"))
                    .provider(registrationId)
                    .build();
            memberRepository.save(member);
        }

        // 토큰 생성
        JwtToken token = jwtTokenProvider.generateToken(authentication);
        redisTemplate.opsForValue().set("RT:" + authentication.getName(), token.getRefreshToken(), token.getRefreshTokenExpiresIn(), TimeUnit.MILLISECONDS);

        String redirectUri = UriComponentsBuilder
                .fromUriString("http://localhost:3000/oauth2/success")
                .queryParam("accessToken", token.getAccessToken())
                .queryParam("refreshToken", token.getRefreshToken())
                .queryParam("username", member.getUsername())
                .queryParam("isNewUser", isNewUser)
                .queryParam("email", email)
                .build()
                .toUriString();

        response.sendRedirect(redirectUri);
    }

}
