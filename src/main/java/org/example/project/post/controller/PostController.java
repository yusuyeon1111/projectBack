package org.example.project.post.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.project.post.dto.ApplyPositionRequestDto;
import org.example.project.post.dto.ApplyResponseDto;
import org.example.project.post.dto.PostRequestDto;
import org.example.project.post.dto.PostResponseDto;
import org.example.project.post.service.PositionService;
import org.example.project.post.service.PostService;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    public ResponseEntity<Long> createPost(@RequestBody PostRequestDto post) {
        Long postId = postService.createPost(post);
        return ResponseEntity.ok(postId);
    }

    @GetMapping("/viewUsername/{postId}")
    public ResponseEntity<PostResponseDto> viewPostWithUsername(@PathVariable Long postId, @RequestParam String username) {
        return ResponseEntity.ok(postService.selectViewByIdWithUsername(postId, username));
    }

    @GetMapping("/view/{postId}")
    public ResponseEntity<PostResponseDto> viewPost(@PathVariable Long postId) {
        return ResponseEntity.ok(postService.selectViewById(postId));
    }

    @PostMapping("/update")
    public ResponseEntity<PostResponseDto> updatePost(@RequestBody PostRequestDto post) {
        return ResponseEntity.ok(postService.updatePost(post));
    }

    @DeleteMapping("/delete/{postId}")
    public ResponseEntity<String> deletePost(@PathVariable Long postId) {
        try {
            postService.deletePost(postId);
            return ResponseEntity.ok("게시글이 삭제되었습니다.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("삭제 중 오류 발생");
        }
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
    public ResponseEntity<String> applyPost(@RequestBody ApplyPositionRequestDto dto) {
        return positionService.applyPostPosition(dto);
    }

    @GetMapping("/applied/{postId}")
    public ResponseEntity<List<Long>> getAppliedPositions(@PathVariable Long postId, @RequestParam String username) {
        List<Long> applied = positionService.getAppliedPositions(postId, username);
        return ResponseEntity.ok(applied);
    }

    @PostMapping("/accept")
    public void acceptPost(@RequestBody ApplyPositionRequestDto dto) {
        positionService.acceptPosition(dto);
    }

    @GetMapping("/applyList/{postId}")
    public List<ApplyResponseDto> getApplyPositionList(@PathVariable Long postId) {
        return positionService.getApplyPositionList(postId);
    }

    @PostMapping("/like")
    public ResponseEntity<String> likePost(@RequestParam Long postId, @RequestParam String username) {
        return postService.likePost(postId, username);
    }

    @DeleteMapping("/unLike")
    public ResponseEntity<String> unLikePost(@RequestParam Long id, @RequestParam String username) {
        return postService.unLikePost(id, username);
    }
}
