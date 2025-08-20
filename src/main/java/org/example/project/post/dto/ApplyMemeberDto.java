package org.example.project.post.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
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
public class ApplyMemeberDto {
    private String nickname;
    private Long memberId;
    private Long postId;
    private Long positionId;
    private Long postPositionId;
    private String category;
    private String role;
    private String status;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDateTime appliedAt;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDateTime acceptedAt;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDateTime rejectedAt;

    public ApplyMemeberDto(Long id, Long id1, Long id2, String status, String status1, LocalDateTime acceptedAt, int count) {
        this.memberId = id;
    }
}
