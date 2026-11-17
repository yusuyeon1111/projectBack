package org.example.project.team.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.project.member.entity.Member;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

// 팀 안에서 일어난 일을 한 줄씩 쌓는다(append-only). 변경과 같은 트랜잭션에 저장한다
@Entity
@Table(name = "team_activity")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TeamActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long teamId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_member_id")
    private Member actor;

    @Column(nullable = false)
    private String type;

    // TASK, MEMBER, TEAM
    @Column(nullable = false)
    private String targetType;

    private Long targetId;

    // 업무 제목·바뀐 상태처럼 그때의 값. 업무가 지워지거나 바뀌어도 기록은 그대로 읽힌다
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> detail;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public TeamActivity(Long teamId, Member actor, String type, String targetType, Long targetId, Map<String, Object> detail) {
        this.teamId = teamId;
        this.actor = actor;
        this.type = type;
        this.targetType = targetType;
        this.targetId = targetId;
        this.detail = detail;
        this.createdAt = LocalDateTime.now();
    }
}
