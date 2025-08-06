package org.example.project.member.service;

import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.project.member.dto.JwtToken;
import org.example.project.member.dto.MemberResponseDto;
import org.example.project.member.dto.SignUpDto;
import org.example.project.member.dto.UpdateMemberDto;
import org.example.project.member.entity.Member;
import org.example.project.member.entity.MemberProfile;
import org.example.project.member.repository.MemberRepository;
import org.example.project.security.JwtTokenProvider;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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

        MemberProfile memberProfile = new MemberProfile();
        memberProfile.setPreferredPosition(signUpDto.getPreferredPosition());
        memberProfile.setPreferredTechStacks(signUpDto.getPreferredTechStacks());
        Member member = Member.builder()
                .username(signUpDto.getUsername())
                .password(encodedPassword)
                .nickname(signUpDto.getNickname())
                .name(signUpDto.getName())
                .email(signUpDto.getEmail())
                .roles(roles)
                .memberProfile(memberProfile)
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

    public String idChek(String username) {
        if (memberRepository.existsByUsername(username)) {
            throw new RuntimeException("중복된 아이디입니다.");
        } else {
            return "사용가능한 아이디 입니다.";
        }
    }

    @Transactional
    public MemberResponseDto updateInfo(@Valid UpdateMemberDto dto) {
        Member member = memberRepository.findByUsername(dto.getUsername())
                .orElseThrow(() -> new RuntimeException("회원이 존재하지 않습니다"));

        // Member 업데이트
        if (dto.getPassword() != null) {
            member.setPassword(passwordEncoder.encode(dto.getPassword()));
        }
        if (dto.getNickname() != null) {
            member.setNickname(dto.getNickname());
        }
        if (dto.getEmail() != null) {
            member.setEmail(dto.getEmail());
        }
        if (dto.getName() != null) {
            member.setName(dto.getName());
        }

        // MemberProfile 업데이트 (없으면 새로 생성)
        MemberProfile profile = member.getMemberProfile();
        if (profile == null) {
            profile = new MemberProfile();
        }
        if (dto.getIntroduce() != null){
            profile.setIntroduce(dto.getIntroduce());
        }
        if (dto.getPreferredPosition() != null) {
            profile.setPreferredPosition(dto.getPreferredPosition());
        }
        if (dto.getPreferredTechStacks() != null) {
            profile.setPreferredTechStacks(dto.getPreferredTechStacks());
        }

        member.setMemberProfile(profile);

        return new MemberResponseDto(member);
    }

    public MemberResponseDto selectMypageById(String username) {
      Member member = memberRepository.findWithProfileAndStacksByUsername(username)
              .orElseThrow(()-> new RuntimeException("해당 유저를 찾을 수 없습니다."));
      return new MemberResponseDto(member);
    }
}
