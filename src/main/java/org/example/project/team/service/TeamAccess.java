package org.example.project.team.service;

import lombok.RequiredArgsConstructor;
import org.example.project.team.entity.Team;
import org.example.project.team.entity.TeamMember;
import org.example.project.team.repository.TeamMemberRepository;
import org.example.project.team.repository.TeamRepository;
import org.springframework.stereotype.Component;

import java.util.NoSuchElementException;

// 팀 하위 자원(업무, 댓글, 채팅)의 권한 확인. 서비스 메서드의 첫 줄에서 부른다.
// 팀원이 아니면 관리자라도 팀이 있는지조차 알려 주지 않는다(404)
@Component
@RequiredArgsConstructor
public class TeamAccess {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;

    public TeamMember requireMember(Long teamId, String username) {
        return teamMemberRepository.findByTeam_IdAndMember_UsernameAndStatus(teamId, username, TeamMember.ACTIVE)
                .orElseThrow(() -> new NoSuchElementException("팀을 찾을 수 없습니다."));
    }

    // 팀 행을 공유 잠금으로 먼저 잡는다. 완료·해산·퇴출은 팀 행을 배타 잠금하므로 쓰기와 줄을 서고,
    // 잠금 읽기는 스냅샷을 만들지 않아 그 뒤의 팀원 조회가 방금 커밋된 퇴출까지 본다. 트랜잭션의 첫 조회여야 한다
    public TeamMember requireWritable(Long teamId, String username) {
        Team team = teamRepository.findByIdForShare(teamId).orElseThrow(() -> new NoSuchElementException("팀을 찾을 수 없습니다."));
        TeamMember me = requireMember(teamId, username);
        if (!team.isOpen()) throw new IllegalStateException("완료되었거나 해산된 팀은 바꿀 수 없습니다.");
        return me;
    }
}
