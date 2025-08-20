package org.example.project.post.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class PostLikeDto {

    private Long postId;
    private String postTitle;
    private Integer likeCount;
}
