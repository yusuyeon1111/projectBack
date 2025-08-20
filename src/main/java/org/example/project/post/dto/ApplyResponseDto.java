package org.example.project.post.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ApplyResponseDto {
    private Long applyId;        // PostPositionMember PK
    private Long memberId;       // Member PK
    private String memberName;   // 지원자 이름
    private Long positionId;     // PostPosition PK
    private String positionName; // 포지션명 (role)
    private String applyStatus;  // 지원 상태
    private String positionStatus; // 포지션 상태
    private Long postId; // 글 id
    private String postTitle; // 글 제목
    private String category; // 글 카테고리
    private Long getMemberCount;
    private LocalDateTime appliedAt; // 신청일자
    private LocalDateTime acceptedAt; // 수락일자
    private LocalDateTime rejectedAt; // 거절일자
    public ApplyResponseDto(
            Long applyId,
            Long memberId,
            String memberName,
            Long positionId,
            String positionName,
            String applyStatus,
            String positionStatus,
            Long postId,
            String postTitle,
            String category,
            LocalDateTime appliedAt,
            LocalDateTime acceptedAt,
            LocalDateTime rejectedAt
    ) {
        this.applyId = applyId;
        this.memberId = memberId;
        this.memberName = memberName;
        this.positionId = positionId;
        this.positionName = positionName;
        this.applyStatus = applyStatus;
        this.positionStatus = positionStatus;
        this.postId = postId;
        this.postTitle = postTitle;
        this.category = category;
        this.appliedAt = appliedAt;
        this.acceptedAt = acceptedAt;
        this.rejectedAt = rejectedAt;
    }
}

