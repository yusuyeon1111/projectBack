package org.example.project.team.dto;

import org.example.project.team.entity.Team;
import org.example.project.team.entity.TeamMember;

import java.time.LocalDateTime;
import java.util.List;

public record TeamDetailDto(Long teamId, Long postId, String name, String status, LocalDateTime createdAt, List<Member> members) {

    public record Member(Long memberId, String nickname, String positionRole, boolean leader, LocalDateTime joinedAt) {}

    public TeamDetailDto(Team team, List<TeamMember> members) {
        this(team.getId(), team.getPostId(), team.getName(), team.getStatus(), team.getCreatedAt(),
                members.stream().map(m -> new Member(m.getMember().getId(), m.getMember().getNickname(),
                        m.getPositionRole(), m.isLeader(), m.getJoinedAt())).toList());
    }
}
