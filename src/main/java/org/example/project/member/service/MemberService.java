package org.example.project.member.service;

import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.project.admin.dto.MemberPageResponse;
import org.example.project.admin.dto.MemberResponse;
import org.example.project.admin.dto.MonthlyStatsDto;
import org.example.project.admin.dto.PostChartResponse;
import org.example.project.member.dto.*;
import org.example.project.member.entity.Member;
import org.example.project.member.entity.MemberProfile;
import org.example.project.member.repository.MemberRepository;
import org.example.project.security.JwtTokenProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final AuthenticationManagerBuilder authenticationManagerBuilder;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final RedisTemplate<String, String> redisTemplate;
    Date date = new Date();
    @Transactional
    public Member signUp(SignUpDto signUpDto) {
        if (memberRepository.existsByUsername(signUpDto.getUsername())) {
            throw new RuntimeException("The user already exists");
        }
        if(memberRepository.existsByEmail(signUpDto.getEmail())) {
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
                .createdAt(new Date())
                .roles(roles)
                .delYn("N")
                .memberProfile(memberProfile)
                .build();
        return memberRepository.save(member);
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

    public ResponseEntity<String> idChek(String username) {
        boolean exists = memberRepository.existsByUsername(username);
        if (exists) {
            return ResponseEntity.ok("중복된 아이디입니다.");
        } else {
            return ResponseEntity.ok("사용가능한 아이디 입니다.");
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
        if (dto.getImgUrl() != null) {
            profile.setImgUrl(dto.getImgUrl());
        }


        member.setMemberProfile(profile);

        return new MemberResponseDto(member);
    }

    public MemberResponseDto selectMypageById(String username) {
        Member member = memberRepository.findWithProfileAndStacksByUsername(username)
                .orElseThrow(() -> new RuntimeException("해당 유저를 찾을 수 없습니다."));

        return new MemberResponseDto(member);
    }

    public ResponseEntity<String> emlCheck(String email) {
        if (memberRepository.existsByEmail(email)) {
            return ResponseEntity.ok("중복된 이메일입니다.");
        } else {
            return ResponseEntity.ok("사용가능한 이메일 입니다.");
        }
    }

    public List<MonthlyStatsDto> getMonthlyStats(int year) {
        List<Object[]> result = memberRepository.countMemberPerMonth(year);
        return result.stream()
                .map(obj ->
                        new MonthlyStatsDto(
                                String.format("%d월", ((Number)obj[0]).intValue()), // name
                                ((Number)obj[1]).intValue() // count
                        )
                )
                .toList();
    }

    public List<PostChartResponse> getPositionStatus(int year){
        List<Object[]> result = memberRepository.countMemberByPosition(year);
        return result.stream()
                .map(obj ->
                        new PostChartResponse(
                                String.format("%d월", ((Number)obj[0]).intValue()),
                                ((String) obj[1]), // name
                                ((Number)obj[2]).intValue() // count
                        )
                )
                .toList();
    }

    public MemberPageResponse getMemberAllList(Pageable pageable) {
        Page<Member> page = memberRepository.findAll(pageable);
        List<MemberResponse> content = page.stream()
                .map(MemberResponse::new)
                .collect(Collectors.toList());
        return new MemberPageResponse(content, page.getTotalPages(), page.getTotalElements(), page.getNumber());
    }

    public String acceptUser(UpdateMemberDto dto) {
        Long id = dto.getMemberId();
        Member member = memberRepository.findById(id)
                .orElseThrow(()->new RuntimeException("찾을 수 없는 사용자 입니다."));
        member.setDelYn("N");
        memberRepository.save(member);

        return "계정이 활성화 되었습니다.";
    }

    public String banUser(UpdateMemberDto dto) {
        Long id = dto.getMemberId();
        Member member = memberRepository.findById(id)
                .orElseThrow(()->new RuntimeException("찾을 수 없는 사용자 입니다."));
        member.setDelYn("Y");
        memberRepository.save(member);
        return "계정이 비활성화 되었습니다.";
    }

    public MemberResponseDto selectById(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(()->new RuntimeException("찾을 수 없는 사용자 입니다."));
        return new MemberResponseDto(member);
    }

    public JwtToken signIn(@Valid SignInDto signInDto) {
        Member member = memberRepository.findByUsernameAndDelYn(signInDto.getUsername(), "N")
                .orElseThrow(()-> new RuntimeException("비활성화된 계정입니다."));
        if (!passwordEncoder.matches(signInDto.getPassword(), member.getPassword())) {
            throw new RuntimeException("비밀번호가 일치하지 않습니다.");
        }
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(signInDto.getUsername(), signInDto.getPassword());

        Authentication authentication = authenticationManagerBuilder.getObject().authenticate(authenticationToken);

        JwtToken token = jwtTokenProvider.generateToken(authentication);

        redisTemplate.opsForValue().set("RT:"+authentication.getName(), token.getRefreshToken(), token.getRefreshTokenExpiresIn(), TimeUnit.MILLISECONDS);

        return token;
    }
}
