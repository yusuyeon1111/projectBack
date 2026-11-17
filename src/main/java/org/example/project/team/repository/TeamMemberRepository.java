package org.example.project.team.repository;

import org.example.project.team.entity.TeamMember;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {

    boolean existsByTeam_IdAndMember_IdAndStatus(Long teamId, Long memberId, String status);

    // 글당 팀은 하나이고 팀당 같은 사람의 행도 하나다
    Optional<TeamMember> findByTeam_PostIdAndMember_Id(Long postId, Long memberId);

    Optional<TeamMember> findByTeam_IdAndMember_Id(Long teamId, Long memberId);

    // 팀 쓰기 작업은 잠금 전에 스냅샷이 잡혀 있을 수 있어서, 상태를 확인하는 행은 잠금 읽기로 최신 값을 본다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "member")
    Optional<TeamMember> findForUpdateByTeam_IdAndMember_Id(Long teamId, Long memberId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "member")
    Optional<TeamMember> findForUpdateByTeam_IdAndMember_Username(Long teamId, String username);

    @EntityGraph(attributePaths = {"team", "member"})
    Optional<TeamMember> findByTeam_IdAndMember_UsernameAndStatus(Long teamId, String username, String status);

    @EntityGraph(attributePaths = "team")
    List<TeamMember> findByMember_UsernameAndStatusOrderByIdDesc(String username, String status);

    @EntityGraph(attributePaths = "member")
    List<TeamMember> findByTeam_IdAndStatusOrderByIdAsc(Long teamId, String status);
}
