package org.example.project.post.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Date;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PostAcceptDto {
    private Long postId;
    private String postTitle;
    private LocalDateTime acceptedAt;
    private Date endDate;
    private Date startDate;
    private String postStatus;
}
