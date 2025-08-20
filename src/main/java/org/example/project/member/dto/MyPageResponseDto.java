package org.example.project.member.dto;

import lombok.*;
import org.example.project.post.dto.ApplyResponseDto;
import org.example.project.post.dto.PostAcceptDto;
import org.example.project.post.dto.PostLikeDto;
import org.example.project.post.dto.PostResponseDto;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MyPageResponseDto {
    private MemberResponseDto member;
    private List<PostResponseDto> posts;
    private List<ApplyResponseDto> applyPosts;
    private List<PostAcceptDto> acceptPosts;
    private List<PostResponseDto> likePosts;
}
