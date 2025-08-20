package org.example.project.post.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;
import org.example.project.post.entity.Post;
import org.example.project.post.entity.PostStack;

import java.util.Date;
import java.util.List;

@Getter
@Setter
public class PostResponseDto {

    private Long id;
    private String title;
    private String content;
    private String projectType;
    private String region;
    private String author;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm")
    private Date created;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm")
    private Date updated;
    private String status;
    private List<String> techStacks;
    private List<PostPositionDto> positions;
    private String category;
    private String nickname;
    private Integer likeCount;
    private Integer viewCount;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private Date startDate;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private Date endDate;
    private String postStatus;
    private boolean likedByUser;
    public PostResponseDto(Post post) {
        this.id = post.getId();
        this.title = post.getTitle();
        this.content = post.getContent();
        this.projectType = post.getProjectType();
        this.region = post.getRegion();
        this.author = post.getAuthor();
        this.created = post.getCreated();
        this.updated = post.getUpdated();
        this.status = post.getStatus();
        this.category = post.getCategory();
        this.nickname = post.getNickname();
        this.likeCount = post.getLikeCount();
        this.viewCount = post.getViewCount();
        this.startDate = post.getStartDate();
        this.endDate = post.getEndDate();
        this.postStatus = post.getPostStatus();
        this.techStacks = post.getTechStacks()
                .stream()
                .map(PostStack::getTechStack)
                .toList();
        this.positions = post.getPositions().stream().map(position -> {
            PostPositionDto dto = new PostPositionDto();
            dto.setId(position.getId());
            dto.setRole(position.getRole());
            dto.setCount(position.getCount());
            dto.setStatus(position.getStatus());
            dto.setPositionMemberList(
                    position.getPositionMemberList().stream().map(pm -> {
                        PostPositionDto.PostPositionMemberDto memberDto = new PostPositionDto.PostPositionMemberDto();
                        memberDto.setId(pm.getId());
                        memberDto.setMemberId(pm.getMember().getId());
                        memberDto.setMemberName(pm.getMember().getUsername()); // 또는 .getNickname() 등
                        memberDto.setStatus(pm.getStatus());
                        return memberDto;
                    }).toList()
            );

            return dto;
        }).toList();
    }

    public PostResponseDto(Post post, boolean likedByUser) {
        this.id = post.getId();
        this.title = post.getTitle();
        this.content = post.getContent();
        this.projectType = post.getProjectType();
        this.region = post.getRegion();
        this.author = post.getAuthor();
        this.created = post.getCreated();
        this.updated = post.getUpdated();
        this.status = post.getStatus();
        this.category = post.getCategory();
        this.nickname = post.getNickname();
        this.likeCount = post.getLikeCount();
        this.viewCount = post.getViewCount();
        this.startDate = post.getStartDate();
        this.endDate = post.getEndDate();
        this.likedByUser = likedByUser;
        this.postStatus = post.getPostStatus();
        this.techStacks = post.getTechStacks()
                .stream()
                .map(PostStack::getTechStack)
                .toList();
        this.positions = post.getPositions().stream().map(position -> {
            PostPositionDto dto = new PostPositionDto();
            dto.setId(position.getId());
            dto.setRole(position.getRole());
            dto.setCount(position.getCount());
            dto.setStatus(position.getStatus());
            dto.setPositionMemberList(
                    position.getPositionMemberList().stream().map(pm -> {
                        PostPositionDto.PostPositionMemberDto memberDto = new PostPositionDto.PostPositionMemberDto();
                        memberDto.setId(pm.getId());
                        memberDto.setMemberId(pm.getMember().getId());
                        memberDto.setMemberName(pm.getMember().getUsername()); // 또는 .getNickname() 등
                        memberDto.setStatus(pm.getStatus());
                        return memberDto;
                    }).toList()
            );

            return dto;
        }).toList();
    }

    @Getter
    @Setter
    public static class PostPositionDto {
        private Long id;
        private String role;
        private int count;
        private String status;
        private List<PostPositionMemberDto> positionMemberList;

        @Getter
        @Setter
        public static class PostPositionMemberDto {
            private Long id;
            private Long memberId;
            private String memberName; // Member 엔티티에서 가져올 수 있음
            private String status;
        }
    }
}
