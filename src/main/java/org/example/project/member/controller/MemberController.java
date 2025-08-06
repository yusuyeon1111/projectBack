package org.example.project.member.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.project.member.dto.*;
import org.example.project.member.entity.Member;
import org.example.project.member.service.MemberService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/member")
public class MemberController {

   private final MemberService memberService;

   @PostMapping("/signup")
    public ResponseEntity<Member> signUp(@Valid @RequestBody SignUpDto signUpDto) {
       return ResponseEntity.ok(memberService.signUp(signUpDto));
   }

   @PostMapping("/signin")
    public ResponseEntity<JwtToken> signIn(@Valid @RequestBody SignInDto signInDto) {
       return ResponseEntity.ok(memberService.signIn(signInDto.getUsername(), signInDto.getPassword()));
   }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(@RequestHeader("Authorization") String token) {
        String accessToken = resolveToken(token);
        return ResponseEntity.ok(memberService.logout(accessToken));
    }

    @GetMapping("/idCheck")
    public ResponseEntity<String> idCheck(@RequestParam String username) {
       return ResponseEntity.ok(memberService.idChek(username));
    }

    @PostMapping("/update")
    public ResponseEntity<MemberResponseDto> update(@Valid @RequestBody UpdateMemberDto updateMemberDto) {
       return ResponseEntity.ok(memberService.updateInfo(updateMemberDto));
    }

    @GetMapping("/myPage")
    public ResponseEntity<MemberResponseDto> myPage(@RequestParam String username) {
        return ResponseEntity.ok(memberService.selectMypageById(username));
    }

    private String resolveToken(String token) {
        if (StringUtils.hasText(token) && token.startsWith("Bearer")) {
            return token.substring(7);
        }
        return null;
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<String> handleRuntimeException(RuntimeException ex) {
        return ResponseEntity.badRequest().body(ex.getMessage());
    }

}
