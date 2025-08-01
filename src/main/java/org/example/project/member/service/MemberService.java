package org.example.project.member.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.example.project.member.dto.JwtToken;
import org.example.project.member.dto.SignUpDto;
import org.example.project.member.entity.Member;
import org.example.project.member.repository.MemberRepository;
import org.example.project.security.JwtTokenProvider;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final AuthenticationManagerBuilder authenticationManagerBuilder;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final RedisTemplate<String, String> redisTemplate;

    @Transactional
    public Member signUp(SignUpDto signUpDto) {
        if (memberRepository.existsByUsername(signUpDto.getUsername())) {
            throw new RuntimeException("The user already exists");
        }
        String encodedPassword = passwordEncoder.encode(signUpDto.getPassword());

        List<String> roles = new ArrayList<>();
        roles.add("ROLE_USER");

        Member member = Member.builder()
                .username(signUpDto.getUsername())
                .password(encodedPassword)
                .nickname(signUpDto.getNickname())
                .email(signUpDto.getEmail())
                .roles(roles)
                .build();
        return memberRepository.save(member);
    }

    @Transactional
    public JwtToken signIn(String username, String password) {
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(username, password);
        Authentication authentication = authenticationManagerBuilder.getObject().authenticate(authenticationToken);

        JwtToken token = jwtTokenProvider.generateToken(authentication);

        redisTemplate.opsForValue().set("RT:"+authentication.getName(), token.getRefreshToken(), token.getRefreshTokenExpiresIn(), TimeUnit.MILLISECONDS);

        return token;
    }

    @Transactional
    public String logout(String accessToken) {
        if(!jwtTokenProvider.validateToken(accessToken)) {
            throw new RuntimeException("Invalid access token");
        }

        Authentication authentication = jwtTokenProvider.getAuthentication(accessToken);

        if(redisTemplate.opsForValue().get("RT:"+authentication.getName())!=null) {
            redisTemplate.delete("RT:"+authentication.getName());
        }

        Long expiration = jwtTokenProvider.getExpiration(accessToken);
        redisTemplate.opsForValue().set(accessToken, "logout", expiration, TimeUnit.MILLISECONDS);

        return "Logout successful";
    }

}
