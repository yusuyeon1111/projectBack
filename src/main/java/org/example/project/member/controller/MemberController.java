package org.example.project.member.controller;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.example.project.member.dto.*;
import org.example.project.member.entity.Member;
import org.example.project.member.service.MemberService;
import org.example.project.post.dto.*;
import org.example.project.post.service.PositionService;
import org.example.project.post.service.PostService;
import org.example.project.security.RefreshTokenService;
import org.example.project.exception.GlobalExceptionHandler;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import com.amazonaws.services.s3.AmazonS3;
import java.net.URL;
import java.security.Principal;
import java.util.Date;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/member")
public class MemberController {

    @Value("${aws.s3.region}")
    private String region;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;


    private final MemberService memberService;
   private final PostService postService;
   private final PositionService positionService;
   private final RefreshTokenService refreshTokenService;
    private final AmazonS3 amazonS3;
    @PostMapping("/signup")
    public ResponseEntity<Member> signUp(@Valid @RequestBody SignUpDto signUpDto) {
       return ResponseEntity.ok(memberService.signUp(signUpDto));
   }

    @PostMapping("/signin")
    public ResponseEntity<?> signIn(@Valid @RequestBody SignInDto signInDto) {
        try {
            return ResponseEntity.ok(memberService.signIn(signInDto));
        } catch (BadCredentialsException ex) {
            // 없는 아이디와 틀린 비밀번호를 같은 응답으로 돌려 아이디 존재 여부를 알 수 없게 한다
            return error(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다.");
        } catch (LockedException ex) {
            return error(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
        } catch (DisabledException ex) {
            return error(HttpStatus.FORBIDDEN, "비활성화된 계정입니다.");
        }
        // Redis 장애(시도 횟수 확인, 토큰 저장)는 잡지 않아 아래 핸들러가 503으로 바꾼다
    }

    // access token이 만료됐을 때 refresh token으로 새 토큰 쌍을 받는다. 실패는 모두 401(다시 로그인)
    @PostMapping("/reissue")
    public ResponseEntity<?> reissue(@RequestBody Map<String, String> body) {
        return refreshTokenService.reissue(body.get("refreshToken"))
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "로그인이 만료되었습니다. 다시 로그인해주세요.")));
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
    public ResponseEntity<MemberResponseDto> update(@Valid @RequestBody UpdateMemberDto updateMemberDto, Principal principal) {
       updateMemberDto.setUsername(principal.getName()); // 본문의 username은 무시하고 본인만 수정
       return ResponseEntity.ok(memberService.updateInfo(updateMemberDto));
    }

    @GetMapping("/myPage")
    public MyPageResponseDto myPage(Principal principal) {
       String username = principal.getName();
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

    @GetMapping("/getUserInfo/{memberId}")
    public ResponseEntity<MemberResponseDto> getUserInfo(@PathVariable Long memberId, Principal principal) {
        MemberResponseDto member = memberService.selectById(memberId, principal.getName());
        return ResponseEntity.ok(member);
    }

    @GetMapping("/presigned-url")
    public Map<String, String> presignedUrl(@RequestParam String filename, Principal principal) {
        String username = principal.getName();
        String objectKey = "uploads/"+username+"/"+filename;

        Date expiration = new Date(System.currentTimeMillis()+3600*1000);
        GeneratePresignedUrlRequest generatePresignedUrlRequest = new GeneratePresignedUrlRequest(bucketName, objectKey)
                .withExpiration(expiration)
                .withMethod(HttpMethod.PUT);
        URL url = amazonS3.generatePresignedUrl(generatePresignedUrlRequest);


        String fileUrl = "https://" + bucketName + ".s3." + region + ".amazonaws.com/" + objectKey;
        return Map.of("url", url.toString(), "key", objectKey, "fileUrl", fileUrl );
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }

    private String resolveToken(String token) {
        if (StringUtils.hasText(token) && token.startsWith("Bearer")) {
            return token.substring(7);
        }
        return null;
    }

    // 아래 RuntimeException(400) 핸들러에 권한 거부까지 묻히지 않도록 403으로 분리
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<String> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ex.getMessage());
    }

    // 이 컨트롤러의 RuntimeException(400) 핸들러가 전역 핸들러보다 먼저 잡으므로 여기서도 503으로 바꾼다
    @ExceptionHandler({DataAccessResourceFailureException.class, QueryTimeoutException.class})
    public ResponseEntity<Map<String, String>> handleUnavailable(RuntimeException ex) {
        return GlobalExceptionHandler.unavailable();
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<String> handleRuntimeException(RuntimeException ex) {
        return ResponseEntity.badRequest().body(ex.getMessage());
    }


}
