package org.example.project.notification.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 글 제목·카테고리·역할은 만든 시점의 값을 복사해 둔다. 글이 수정·삭제돼도 알림 문구가 그대로 남는다.
@Entity
@Table(name = "notification", indexes = @Index(name = "idx_notification_receiver_id", columnList = "receiver, id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String receiver; // 받는 회원의 username

    @Column(nullable = false, length = 10)
    private String type; // APPLY | ACCEPT | REJECT

    private Long postId;
    private String postTitle;
    private String postCategory;
    private String role;
    private String actor; // APPLY일 때 신청자 표시 이름, 나머지는 null

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime readAt; // null이면 안 읽음. read는 MySQL 예약어라 쓰지 않는다

    public Notification(String receiver, String type, Long postId, String postTitle, String postCategory, String role, String actor) {
        this.receiver = receiver;
        this.type = type;
        this.postId = postId;
        this.postTitle = postTitle;
        this.postCategory = postCategory;
        this.role = role;
        this.actor = actor;
        this.createdAt = LocalDateTime.now();
    }

    public void markRead() {
        if (readAt == null) readAt = LocalDateTime.now();
    }
}
