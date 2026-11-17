package org.example.project.team.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.project.member.entity.Member;
import org.example.project.member.repository.MemberRepository;
import org.example.project.post.entity.Post;
import org.example.project.post.entity.PostPositionMember;
import org.example.project.post.repositoty.PostPositionMemberRepository;
import org.example.project.post.repositoty.PostPositionRepository;
import org.example.project.post.repositoty.PostRepository;
import org.example.project.team.dto.TeamDetailDto;
import org.example.project.team.dto.TeamSummaryDto;
import org.example.project.team.entity.ApplicationEvent;
import org.example.project.team.entity.Team;
import org.example.project.team.entity.TeamMember;
import org.example.project.team.repository.ApplicationEventRepository;
import org.example.project.team.repository.TeamMemberRepository;
import org.example.project.team.repository.TeamRepository;
import org.springframework.security.access.AccessDeniedException;
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
    private final PostRepository postRepository;
    private final PostPositionRepository postPositionRepository;
    private final PostPositionMemberRepository applicationRepository;

    // 승인된 신청자를 팀에 넣는다. 팀이 없으면(첫 승인) 작성자를 팀장으로 팀을 만든다.
    // 승인 트랜잭션 안에서, 글 행을 잠근 뒤에 불러야 한다. 같은 글의 승인이 글 잠금으로 줄을 서므로
    // 팀이 두 개 생기거나 같은 사람이 두 번 들어가지 않는다(uk_team_post, uk_team_member가 마지막 방어선).
    public void joinOnAccept(PostPositionMember application) {
        Post post = application.getPostPosition().getPost();
        Team team = teamRepository.findByPostId(post.getId()).orElseGet(() -> createTeam(post));
        if (!team.isOpen()) {
            throw new IllegalStateException("완료되었거나 해산된 팀에는 팀원을 더할 수 없습니다.");
        }

        Member applicant = application.getMember();
        String role = application.getPostPosition().getRole();
        TeamMember existing = teamMemberRepository.findByTeam_IdAndMember_Id(team.getId(), applicant.getId()).orElse(null);
        if (existing == null) {
            teamMemberRepository.save(new TeamMember(team, applicant, application.getId(), role));
        } else if (TeamMember.LEFT.equals(existing.getStatus())) {
            existing.rejoin(application.getId(), role); // 탈퇴했다가 다시 승인된 사람
        } else {
            throw new IllegalStateException(existing.isActive() ? "이미 이 팀의 팀원입니다." : "이 팀에서 퇴출된 사람입니다.");
        }
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

    // 같은 글의 팀에 이미 들어가 있거나 퇴출된 사람은 다른 포지션에도 신청할 수 없다. 탈퇴한 사람은 된다
    public void checkNotTeamMember(Long postId, Long memberId) {
        teamMemberRepository.findByTeam_PostIdAndMember_Id(postId, memberId).ifPresent(m -> {
            if (m.isActive()) throw new IllegalStateException("이미 이 글의 팀에 참여하고 있습니다.");
            if (TeamMember.KICKED.equals(m.getStatus())) throw new IllegalStateException("이 팀에서 퇴출되어 신청할 수 없습니다.");
        });
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

    // --- 팀 쓰기 작업(팀장·팀원) ---

    @Transactional
    public void start(Long teamId, String username) {
        requireLeader(lockTeam(teamId), username).getTeam().start();
    }

    @Transactional
    public void complete(Long teamId, String username) {
        requireLeader(lockTeam(teamId), username).getTeam().complete();
    }

    @Transactional
    public void disband(Long teamId, String username) {
        requireLeader(lockTeam(teamId), username).getTeam().disband();
    }

    @Transactional
    public void changeLeader(Long teamId, String username, Long targetMemberId) {
        Team team = lockTeam(teamId);
        requireOpen(requireLeader(team, username));
        TeamMember target = requireActiveMember(teamId, targetMemberId);
        if (target.isLeader()) throw new IllegalArgumentException("이미 팀장입니다.");
        team.changeLeader(targetMemberId);
    }

    @Transactional
    public void leave(Long teamId, String username) {
        TeamMember me = requireOpen(requireMember(lockTeam(teamId), username));
        if (me.isLeader()) throw new IllegalStateException("팀장은 다른 팀원에게 팀장을 넘긴 뒤 탈퇴할 수 있습니다.");
        release(me, TeamMember.LEFT, "WITHDRAWN", me.getMember().getId());
    }

    @Transactional
    public void kick(Long teamId, String username, Long targetMemberId) {
        TeamMember leader = requireOpen(requireLeader(lockTeam(teamId), username));
        TeamMember target = requireActiveMember(teamId, targetMemberId);
        if (target.isLeader()) throw new IllegalArgumentException("팀장은 퇴출할 수 없습니다.");
        release(target, TeamMember.KICKED, "EXPELLED", leader.getMember().getId());
    }

    // 승인과 같은 순서(글 → 팀)로 잠가 교착을 피하고, 탈퇴와 승인이 동시에 와도 정원 계산이 줄을 선다.
    // 잠금 전에 post_id를 읽어 스냅샷이 먼저 잡히므로, 이후 상태 확인은 모두 잠금 읽기나 조건부 UPDATE로 한다.
    private Team lockTeam(Long teamId) {
        teamRepository.findPostIdById(teamId).ifPresent(postRepository::findByIdForUpdate);
        return teamRepository.findByIdForUpdate(teamId).orElseThrow(() -> new NoSuchElementException("팀을 찾을 수 없습니다."));
    }

    // 팀원이 아니면 팀이 있는지도 알려 주지 않는다(404)
    private TeamMember requireMember(Team team, String username) {
        return teamMemberRepository.findForUpdateByTeam_IdAndMember_Username(team.getId(), username)
                .filter(TeamMember::isActive)
                .orElseThrow(() -> new NoSuchElementException("팀을 찾을 수 없습니다."));
    }

    private TeamMember requireLeader(Team team, String username) {
        TeamMember me = requireMember(team, username);
        if (!me.isLeader()) throw new AccessDeniedException("팀장만 할 수 있습니다.");
        return me;
    }

    private TeamMember requireActiveMember(Long teamId, Long memberId) {
        return teamMemberRepository.findForUpdateByTeam_IdAndMember_Id(teamId, memberId)
                .filter(TeamMember::isActive)
                .orElseThrow(() -> new NoSuchElementException("팀원을 찾을 수 없습니다."));
    }

    private TeamMember requireOpen(TeamMember member) {
        if (!member.getTeam().isOpen()) throw new IllegalStateException("완료되었거나 해산된 팀은 바꿀 수 없습니다.");
        return member;
    }

    // 팀에서 빠지면 신청도 승인에서 내려오고, 정원 아래가 된 포지션과 글을 다시 모집 중으로 연다
    private void release(TeamMember target, String memberStatus, String applicationStatus, Long actorId) {
        target.leave(memberStatus);
        Long applicationId = target.getSourceApplicationId(); // 글이 지워졌으면 null
        if (applicationId == null || applicationRepository.changeStatus(applicationId, "ACCEPT", applicationStatus) == 0) {
            return;
        }
        postPositionRepository.reopenByApplicationId(applicationId);
        postRepository.reopen(target.getTeam().getPostId());
        recordApplication(applicationId, applicationStatus, actorId);
    }
}
