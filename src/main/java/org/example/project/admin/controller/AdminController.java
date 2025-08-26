package org.example.project.admin.controller;

import lombok.RequiredArgsConstructor;
import org.example.project.admin.dto.*;
import org.example.project.member.dto.UpdateMemberDto;
import org.example.project.member.entity.Member;
import org.example.project.member.service.MemberService;
import org.example.project.post.dto.PostRequestDto;
import org.example.project.post.entity.Post;
import org.example.project.post.service.PostService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {
    private final MemberService memberService;
    private final PostService postService;

    @GetMapping("/chartAll")
    public AdminChartResponse chartAll(@RequestParam int year) {
       return new AdminChartResponse(
               memberService.getMonthlyStats(year),
               postService.getMonthlyStats(year),
               postService.getPostStauts(year)
       );
    }

    @GetMapping("/chartPosition")
    public List<MonthlyPostDto> chartPosition(@RequestParam int year) {
        return postService.getTotalPositionStats(year);
    }

    @GetMapping("/chartPost")
    public List<PostChartResponse> chartPost(@RequestParam int year) {
       return postService.getPostCategory(year);
    }

    @GetMapping("/chartPre")
    public List<PostChartResponse> chartPre(@RequestParam int year) {
        return memberService.getPositionStatus(year);
    }

    @GetMapping("/memberAll")
    public MemberPageResponse memberAll(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return memberService.getMemberAllList(pageable);
    }

    @PostMapping("/acceptUser")
    public ResponseEntity<String> acceptUser(@RequestBody UpdateMemberDto dto) {
        return ResponseEntity.ok(memberService.acceptUser(dto));
    }

    @PostMapping("/banUser")
    public ResponseEntity<String> banUser(@RequestBody UpdateMemberDto dto) {
        return ResponseEntity.ok(memberService.banUser(dto));
    }

    @PostMapping("/changePostStatus")
    public ResponseEntity<String> changePostStatus(@RequestBody PostRequestDto dto) {
        return ResponseEntity.ok(postService.updateDelYn(dto));
    }
}
