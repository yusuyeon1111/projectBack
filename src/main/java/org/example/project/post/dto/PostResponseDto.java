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
    private String delYn;
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
        this.delYn = post.getDelYn();
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
        // 신청자 목록은 넣지 않는다. 이 DTO는 로그인 없이 부르는 목록·상세 응답에 쓰이고,
        // 신청자는 작성자 본인만 applyList·postMember API로 조회한다.
    }
}
