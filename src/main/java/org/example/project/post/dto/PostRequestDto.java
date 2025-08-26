package org.example.project.post.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.List;

@Getter
@Setter
public class PostRequestDto {

    private Long id;
    private String title;
    private String category;
    private String content;
    private String author;
    private String projectType;
    private String status;
    private String region;
    private List<PostPositionDto> positions;
    private List<String> techStacks;
    private String nickname;
    private int likeCount;
    private int viewCount;
    private Date endDate;
    private Date startDate;
    private String postStatus;
    private String username;
    private String delYn;
    // 조회용 필드
    private Integer page = 0;
    private Integer size = 10;
    private String sortType = "latest";

    // 필터용 필드
    private String keyword;
    private String stackKeyword;
    private String position;

    @Getter
    @Setter
    public static class PostPositionDto {
        private String role;
        private int count;
        private String status;
        private List<PostPositionMemberDto> positionMemberList;

        @Getter
        @Setter
        public static class PostPositionMemberDto {
            private Long memberId;
            private String status;
        }
    }

    @Getter
    @Setter
    public static class PostStackDto {
        private String techStack;
        private Long postId;
    }
}
