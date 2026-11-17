package org.example.project.post.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.project.member.repository.MemberRepository;
import org.example.project.post.dto.*;
import org.example.project.post.entity.ApplyAnswer;
import org.example.project.post.entity.ApplyFile;
import org.example.project.post.entity.PostPositionMember;
import org.example.project.post.service.ApplyFormService;
import org.example.project.post.service.PositionService;
import org.example.project.post.service.PostService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("api/post")
public class PostController {

    private final PostService postService;
    private final PositionService positionService;
    private final ApplyFormService applyFormService;
    private final MemberRepository memberRepository;

    @PostMapping("/create")
    public ResponseEntity<Long> createPost(@RequestBody PostRequestDto post, Principal principal) {
        post.setAuthor(principal.getName()); // 작성자는 토큰 사용자로 고정
        Long postId = postService.createPost(post);
        return ResponseEntity.ok(postId);
    }

    @GetMapping("/viewUsername/{postId}")
    public ResponseEntity<PostResponseDto> viewPostWithUsername(@PathVariable Long postId, Principal principal) {
        return ResponseEntity.ok(postService.selectViewByIdWithUsername(postId, principal.getName()));
    }

    @GetMapping("/view/{postId}")
    public ResponseEntity<PostResponseDto> viewPost(@PathVariable Long postId) {
        return ResponseEntity.ok(postService.selectViewById(postId));
    }

    @PostMapping("/update")
    public ResponseEntity<PostResponseDto> updatePost(@RequestBody PostRequestDto post, Principal principal) {
        return ResponseEntity.ok(postService.updatePost(post, principal.getName()));
    }

    @DeleteMapping("/delete/{postId}")
    public ResponseEntity<String> deletePost(@PathVariable Long postId, Principal principal) {
        postService.deletePost(postId, principal.getName());
        return ResponseEntity.ok("게시글이 삭제되었습니다.");
    }

    @PostMapping("/list")
    public ResponseEntity<Map<String, Object>> listPostsNew(@RequestBody PostRequestDto post) {
        Page<PostResponseDto> newPostPage = postService.getPostList(post);

        Map<String, Object> response = new HashMap<>();
        response.put("content", newPostPage.getContent());
        response.put("page", newPostPage.getNumber());
        response.put("size", newPostPage.getSize());
        response.put("totalElements", newPostPage.getTotalElements());
        response.put("totalPages", newPostPage.getTotalPages());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/apply")
    public ResponseEntity<String> applyPost(@RequestBody ApplyPositionRequestDto dto, Principal principal) {
        dto.setUsername(principal.getName());
        return positionService.applyPostPosition(dto);
    }

    @GetMapping("/applied/{postId}")
    public ResponseEntity<List<Long>> getAppliedPositions(@PathVariable Long postId, Principal principal) {
        List<Long> applied = positionService.getAppliedPositions(postId, principal.getName());
        return ResponseEntity.ok(applied);
    }

    @PostMapping("/accept")
    public List<ApplyMemeberDto> acceptPost(@RequestBody ApplyPositionRequestDto dto, Principal principal) {
        return positionService.acceptPosition(dto, principal.getName());
    }

    @PostMapping("/reject")
    public List<ApplyMemeberDto> rejectPost(@RequestBody ApplyPositionRequestDto dto, Principal principal) {
        return positionService.rejectPosition(dto, principal.getName());
    }

    @GetMapping("/applyList/{postId}")
    public List<ApplyResponseDto> getApplyPositionList(@PathVariable Long postId, Principal principal) {
        postService.findOwnedPost(postId, principal.getName());
        return positionService.getApplyPositionList(postId);
    }

    // 신청서 답변. 글 작성자와 신청자 본인만 볼 수 있다.
    @GetMapping("/application/{applicationId}")
    public List<ApplyAnswer> applicationAnswers(@PathVariable Long applicationId, Principal principal) {
        List<ApplyAnswer> answers = positionService.findReadableApplication(applicationId, principal.getName()).getAnswers();
        return answers == null ? List.of() : answers;
    }

    // 첨부 파일 받기. 그 신청서에 실제로 붙은 파일만 짧게 유효한 URL로 내준다.
    @GetMapping("/application/{applicationId}/file")
    public Map<String, String> applicationFile(@PathVariable Long applicationId, @RequestParam String key, Principal principal) {
        PostPositionMember application = positionService.findReadableApplication(applicationId, principal.getName());
        ApplyFile file = Optional.ofNullable(application.getAnswers()).orElse(List.of()).stream()
                .flatMap(a -> a.files().stream())
                .filter(f -> f.key().equals(key))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("첨부 파일이 없습니다."));
        return Map.of("url", applyFormService.issueDownloadUrl(file));
    }

    // 신청서 첨부 파일 올리기. 키는 서버가 만들고, 브라우저가 받은 URL로 S3에 직접 PUT한다.
    @GetMapping("/apply-file/upload-url")
    public Map<String, String> applyFileUploadUrl(@RequestParam String filename, Principal principal) {
        Long memberId = memberRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new RuntimeException("사용자가 존재하지 않습니다.")).getId();
        return applyFormService.issueUploadUrl(memberId, filename);
    }

    @PostMapping("/like")
    public ResponseEntity<String> likePost(@RequestParam Long postId, Principal principal) {
        return postService.likePost(postId, principal.getName());
    }

    @DeleteMapping("/unLike")
    public ResponseEntity<String> unLikePost(@RequestParam Long id, Principal principal) {
        return postService.unLikePost(id, principal.getName());
    }

    @GetMapping("/postMember/{postId}")
    public List<ApplyMemeberDto> postMember(@PathVariable Long postId, Principal principal) {
        postService.findOwnedPost(postId, principal.getName());
        return postService.getPostApplyMember(postId);
    }
}
