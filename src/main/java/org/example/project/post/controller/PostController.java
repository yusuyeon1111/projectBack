package org.example.project.post.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.project.post.dto.*;
import org.example.project.post.service.PositionService;
import org.example.project.post.service.PostService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("api/post")
public class PostController {

    private final PostService postService;
    private final PositionService positionService;

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
