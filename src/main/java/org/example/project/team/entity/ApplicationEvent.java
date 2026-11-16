package org.example.project.team.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 신청 상태가 바뀔 때마다 한 줄씩 쌓는 이력. 신청 행은 현재 상태만 담는다
@Entity
@Table(name = "application_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApplicationEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long applicationId;

    @Column(nullable = false)
    private String status;

    private Long actorMemberId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public ApplicationEvent(Long applicationId, String status, Long actorMemberId) {
        this.applicationId = applicationId;
        this.status = status;
        this.actorMemberId = actorMemberId;
        this.createdAt = LocalDateTime.now();
    }
}
