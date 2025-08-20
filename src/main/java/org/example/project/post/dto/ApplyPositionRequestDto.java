package org.example.project.post.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ApplyPositionRequestDto {
    private Long postId;
    private Long positionId;
    private Long memberId;
    private String username;
    private Long postPositionId;
}
