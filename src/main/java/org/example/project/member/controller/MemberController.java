package org.example.project.member.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.project.member.dto.*;
import org.example.project.member.entity.Member;
import org.example.project.member.service.MemberService;
import org.example.project.post.dto.*;
import org.example.project.post.service.PositionService;
import org.example.project.post.service.PostService;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/member")
public class MemberController {

   private final MemberService memberService;
   private final PostService postService;
   private final PositionService positionService;
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
    public ResponseEntity<String> idCheck(@RequestParam("username") String username) {
       return memberService.idChek(username);
    }

    @GetMapping("/emlCheck")
    public ResponseEntity<String> emlCheck(@RequestParam("email") String email) {
        return memberService.emlCheck(email);
    }

    @PostMapping("/update")
    public ResponseEntity<MemberResponseDto> update(@Valid @RequestBody UpdateMemberDto updateMemberDto) {
       return ResponseEntity.ok(memberService.updateInfo(updateMemberDto));
    }

    @GetMapping("/myPage")
    public MyPageResponseDto myPage(@RequestParam String username) {
       MemberResponseDto member = memberService.selectMypageById(username);
       List<PostResponseDto> posts = postService.findMyPosts(username);
       List<ApplyResponseDto> apply = positionService.findApplyPost(username);
       List<PostAcceptDto> accept = positionService.findAcceptPost(username);
       List<PostResponseDto> like = postService.findLikePost(username);

       return MyPageResponseDto.builder()
               .member(member)
               .posts(posts)
               .applyPosts(apply)
               .acceptPosts(accept)
               .likePosts(like)
               .build();
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
