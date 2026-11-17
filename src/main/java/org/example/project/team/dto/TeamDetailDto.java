package org.example.project.team.dto;

import org.example.project.team.entity.Team;
import org.example.project.team.entity.TeamMember;

import java.time.LocalDateTime;
import java.util.List;

// myMemberId: 보는 사람 자신. 화면이 내 채팅 말풍선·내 업무를 구분하는 데 쓴다
public record TeamDetailDto(Long teamId, Long postId, String name, String status, LocalDateTime createdAt, List<Member> members,
                            Long myMemberId) {

    public record Member(Long memberId, String nickname, String positionRole, boolean leader, LocalDateTime joinedAt) {}

    public TeamDetailDto(Team team, List<TeamMember> members, Long myMemberId) {
        this(team.getId(), team.getPostId(), team.getName(), team.getStatus(), team.getCreatedAt(),
                members.stream().map(m -> new Member(m.getMember().getId(), m.getMember().getNickname(),
                        m.getPositionRole(), m.isLeader(), m.getJoinedAt())).toList(), myMemberId);
    }
}
