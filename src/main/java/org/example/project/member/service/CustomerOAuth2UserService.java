package org.example.project.member.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.project.member.entity.Member;
import org.example.project.member.repository.MemberRepository;
import org.example.project.security.JwtTokenProvider;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@RequiredArgsConstructor
@Service
public class CustomerOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {
    private final MemberRepository memberRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final MemberService memberService;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;


    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        String registeredId = userRequest.getClientRegistration().getRegistrationId();
        OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate = new DefaultOAuth2UserService();
        OAuth2User oAuth2User = delegate.loadUser(userRequest);
        Map<String, Object> attributes = oAuth2User.getAttributes();
        String email;
        String name;
        String userNameAttributeName = "id";

        if ("kakao".equals(registeredId)) {
            Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
            Map<String, Object> profile = (Map<String, Object>) kakaoAccount.get("profile");
            email = (String) kakaoAccount.get("email");
            name = (String) profile.get("nickname");
            userNameAttributeName = "id";
        } else if ("naver".equals(registeredId)) {
            Map<String, Object> response = (Map<String, Object>) attributes.get("response");
            email = (String) response.get("email");
            name = (String) response.get("name");
            attributes = response;
            userNameAttributeName = "id";
        } else {
            name = "";
            email = "";
        }

        if (email == null) {
            throw new OAuth2AuthenticationException("이메일을 가져올 수 없습니다.");
        }

        memberRepository.findByEmail(email).orElseGet(() -> {
            Member member = Member.builder()
                    .email(email)
                    .name(name)
                    .username(email)
                    .password(bCryptPasswordEncoder.encode(UUID.randomUUID().toString()))
                    .roles(List.of("ROLE_USER"))
                    .provider(registeredId)
                    .build();
            return memberRepository.save(member);
        });

        return new DefaultOAuth2User(
                Collections.singleton(new SimpleGrantedAuthority("ROLE_USER")),
                attributes,
                userNameAttributeName
        );
    }

}
