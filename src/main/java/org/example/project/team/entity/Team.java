package org.example.project.team.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 모집글의 첫 승인 때 생기는 팀. 글이 지워져도 남도록 글은 id로만 가리킨다
@Entity
@Table(name = "team")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Team {

    public static final String FORMING = "FORMING";
    public static final String ACTIVE = "ACTIVE";
    public static final String COMPLETED = "COMPLETED";
    public static final String DISBANDED = "DISBANDED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long postId;

    @Column(nullable = false)
    private String name;

    // 팀장을 한 칸에 둬서 팀장이 둘일 수 없다. MySQL에는 부분 유니크 인덱스가 없어 role 컬럼으로는 강제하기 어렵다
    @Column(nullable = false)
    private Long leaderMemberId;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime startedAt;
    private LocalDateTime endedAt;

    public Team(Long postId, String name, Long leaderMemberId) {
        this.postId = postId;
        this.name = name;
        this.leaderMemberId = leaderMemberId;
        this.status = FORMING;
        this.createdAt = LocalDateTime.now();
    }

    // 완료·해산된 팀은 읽기 전용이다
    public boolean isOpen() {
        return FORMING.equals(status) || ACTIVE.equals(status);
    }

    public void start() {
        if (!FORMING.equals(status)) throw new IllegalStateException("모집 중인 팀만 시작할 수 있습니다.");
        status = ACTIVE;
        startedAt = LocalDateTime.now();
    }

    public void complete() {
        if (!ACTIVE.equals(status)) throw new IllegalStateException("진행 중인 팀만 완료할 수 있습니다.");
        status = COMPLETED;
        endedAt = LocalDateTime.now();
    }

    public void disband() {
        if (!isOpen()) throw new IllegalStateException("이미 끝난 팀입니다.");
        status = DISBANDED;
        endedAt = LocalDateTime.now();
    }

    public void changeLeader(Long memberId) {
        leaderMemberId = memberId;
    }
}
