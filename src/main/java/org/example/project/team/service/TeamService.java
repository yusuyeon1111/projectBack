package org.example.project.team.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.project.member.entity.Member;
import org.example.project.member.repository.MemberRepository;
import org.example.project.post.entity.Post;
import org.example.project.post.entity.PostPositionMember;
import org.example.project.team.dto.TeamDetailDto;
import org.example.project.team.dto.TeamSummaryDto;
import org.example.project.team.entity.ApplicationEvent;
import org.example.project.team.entity.Team;
import org.example.project.team.entity.TeamMember;
import org.example.project.team.repository.ApplicationEventRepository;
import org.example.project.team.repository.TeamMemberRepository;
import org.example.project.team.repository.TeamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Slf4j
@Service
@RequiredArgsConstructor
public class TeamService {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final ApplicationEventRepository applicationEventRepository;
    private final MemberRepository memberRepository;

    // 승인된 신청자를 팀에 넣는다. 팀이 없으면(첫 승인) 작성자를 팀장으로 팀을 만든다.
    // 승인 트랜잭션 안에서, 글 행을 잠근 뒤에 불러야 한다. 같은 글의 승인이 글 잠금으로 줄을 서므로
    // 팀이 두 개 생기거나 같은 사람이 두 번 들어가지 않는다(uk_team_post, uk_team_member가 마지막 방어선).
    public void joinOnAccept(PostPositionMember application) {
        Post post = application.getPostPosition().getPost();
        Team team = teamRepository.findByPostId(post.getId()).orElseGet(() -> createTeam(post));

        Member applicant = application.getMember();
        if (teamMemberRepository.existsByTeam_IdAndMember_IdAndStatus(team.getId(), applicant.getId(), TeamMember.ACTIVE)) {
            throw new IllegalStateException("이미 이 팀의 팀원입니다.");
        }
        teamMemberRepository.save(new TeamMember(team, applicant, application.getId(), application.getPostPosition().getRole()));
    }

    // 팀장은 첫 승인자가 아니라 글 작성자다. 승인은 작성자만 할 수 있으니 정상이라면 조회가 실패하지 않는다.
    // 실패하면 데이터가 깨진 것이므로 승인까지 롤백한다(승인은 됐는데 팀이 없는 상태를 만들지 않는다).
    private Team createTeam(Post post) {
        Member leader = memberRepository.findByUsername(post.getAuthor()).orElseThrow(() -> {
            log.error("작성자 회원을 찾지 못해 팀을 만들 수 없음 postId={} author={}", post.getId(), post.getAuthor());
            return new IllegalStateException("작성자 회원 정보를 찾을 수 없어 승인할 수 없습니다.");
        });
        Team team = teamRepository.save(new Team(post.getId(), post.getTitle() == null ? "(제목 없음)" : post.getTitle(), leader.getId()));
        teamMemberRepository.save(new TeamMember(team, leader, null, null));
        return team;
    }

    // 같은 글의 팀에 이미 들어가 있는 사람은 다른 포지션에 신청할 수 없다
    public void checkNotTeamMember(Long postId, Long memberId) {
        if (teamMemberRepository.existsByTeam_PostIdAndMember_IdAndStatus(postId, memberId, TeamMember.ACTIVE)) {
            throw new IllegalStateException("이미 이 글의 팀에 참여하고 있습니다.");
        }
    }

    // 해산되지 않은 팀이 있는 글은 지울 수 없다
    public void checkDeletable(Long postId) {
        if (teamRepository.existsByPostIdAndStatusNot(postId, Team.DISBANDED)) {
            throw new IllegalStateException("팀이 만들어진 모집글은 삭제할 수 없습니다.");
        }
    }

    public void recordApplication(Long applicationId, String status, Long actorMemberId) {
        applicationEventRepository.save(new ApplicationEvent(applicationId, status, actorMemberId));
    }

    @Transactional(readOnly = true)
    public List<TeamSummaryDto> findMyTeams(String username) {
        return teamMemberRepository.findByMember_UsernameAndStatusOrderByIdDesc(username, TeamMember.ACTIVE).stream()
                .map(TeamSummaryDto::new)
                .toList();
    }

    // 팀원이 아니면 관리자라도 팀이 있는지조차 알려 주지 않는다(404)
    @Transactional(readOnly = true)
    public TeamDetailDto findTeam(Long teamId, String username) {
        TeamMember me = teamMemberRepository.findByTeam_IdAndMember_UsernameAndStatus(teamId, username, TeamMember.ACTIVE)
                .orElseThrow(() -> new NoSuchElementException("팀을 찾을 수 없습니다."));
        return new TeamDetailDto(me.getTeam(), teamMemberRepository.findByTeam_IdAndStatusOrderByIdAsc(teamId, TeamMember.ACTIVE));
    }
}
