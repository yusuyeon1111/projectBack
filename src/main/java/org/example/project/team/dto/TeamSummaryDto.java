package org.example.project.team.dto;

import org.example.project.team.entity.Team;
import org.example.project.team.entity.TeamMember;

public record TeamSummaryDto(Long teamId, Long postId, String name, String status, boolean leader) {
    public TeamSummaryDto(TeamMember membership) {
        this(membership.getTeam().getId(), membership.getTeam().getPostId(), membership.getTeam().getName(),
                membership.getTeam().getStatus(), membership.isLeader());
    }
}
