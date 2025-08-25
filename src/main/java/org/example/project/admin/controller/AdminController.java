package org.example.project.admin.controller;

import lombok.RequiredArgsConstructor;
import org.example.project.admin.dto.AdminChartResponse;
import org.example.project.admin.dto.MonthlyPostDto;
import org.example.project.admin.dto.PostChartResponse;
import org.example.project.member.service.MemberService;
import org.example.project.post.service.PostService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

}
