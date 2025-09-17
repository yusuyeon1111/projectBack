package org.example.project.member.controller;

import com.amazonaws.HttpMethod;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import java.net.URL;
import java.util.Date;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/member")
public class MemberController {

    @Value("${aws.s3.access-key-id}")
    private String accessKeyId;

    @Value("${aws.s3.secret-access-key}")
    private String secretAccessKey;

    @Value("${aws.s3.region}")
    private String region;


    private final MemberService memberService;
   private final PostService postService;
   private final PositionService positionService;
    @PostMapping("/signup")
    public ResponseEntity<Member> signUp(@Valid @RequestBody SignUpDto signUpDto) {
       return ResponseEntity.ok(memberService.signUp(signUpDto));
   }

    @PostMapping("/signin")
    public ResponseEntity<?> signIn(@Valid @RequestBody SignInDto signInDto) {
        try {
            JwtToken token = memberService.signIn(signInDto);
            return ResponseEntity.ok(token);
        } catch (RuntimeException ex) {
            // 로그인 실패 시 메시지 반환
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", ex.getMessage()));
        }
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
        System.out.println("=============실행=========="+accept);
       return MyPageResponseDto.builder()
               .member(member)
               .posts(posts)
               .applyPosts(apply)
               .acceptPosts(accept)
               .likePosts(like)
               .build();
    }

    @GetMapping("/getUserInfo/{memberId}")
    public ResponseEntity<MemberResponseDto> getUserInfo(@PathVariable Long memberId) {
        MemberResponseDto member = memberService.selectById(memberId);
        return ResponseEntity.ok(member);
    }

    @GetMapping("/presigned-url")
    public Map<String, String> presignedUrl(@RequestParam String filename, @RequestParam String username) {
        String bucketName = "aws-practice-img-suyeon";
        String objectKey = "uploads/"+username+"/"+filename;

        BasicAWSCredentials creds = new BasicAWSCredentials(
                accessKeyId,
                secretAccessKey
        );

        AmazonS3 amazonS3 = AmazonS3ClientBuilder.standard()
                .withRegion(region)
                .withCredentials(new AWSStaticCredentialsProvider(creds))
                .build();

        Date expiration = new Date(System.currentTimeMillis()+3600*1000);
        GeneratePresignedUrlRequest generatePresignedUrlRequest = new GeneratePresignedUrlRequest(bucketName, objectKey)
                .withExpiration(expiration)
                .withMethod(HttpMethod.PUT);
        URL url = amazonS3.generatePresignedUrl(generatePresignedUrlRequest);


        String fileUrl = "https://" + bucketName + ".s3." + region + ".amazonaws.com/" + objectKey;
        return Map.of("url", url.toString(), "key", objectKey, "fileUrl", fileUrl );
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
