package org.example.project.team.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.project.member.entity.Member;

import java.time.LocalDateTime;

@Entity
@Table(name = "team_member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TeamMember {

    public static final String ACTIVE = "ACTIVE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id")
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id")
    private Member member;

    // 이 멤버십을 만든 신청. 팀장은 신청 없이 들어와 null이다
    private Long sourceApplicationId;

    private String positionRole;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private LocalDateTime joinedAt;

    private LocalDateTime leftAt;

    public TeamMember(Team team, Member member, Long sourceApplicationId, String positionRole) {
        this.team = team;
        this.member = member;
        this.sourceApplicationId = sourceApplicationId;
        this.positionRole = positionRole;
        this.status = ACTIVE;
        this.joinedAt = LocalDateTime.now();
    }

    public boolean isLeader() {
        return team.getLeaderMemberId().equals(member.getId());
    }
}
