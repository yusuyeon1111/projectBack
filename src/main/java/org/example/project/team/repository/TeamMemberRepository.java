package org.example.project.team.repository;

import org.example.project.team.entity.TeamMember;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {

    boolean existsByTeam_IdAndMember_IdAndStatus(Long teamId, Long memberId, String status);

    boolean existsByTeam_PostIdAndMember_IdAndStatus(Long postId, Long memberId, String status);

    @EntityGraph(attributePaths = {"team", "member"})
    Optional<TeamMember> findByTeam_IdAndMember_UsernameAndStatus(Long teamId, String username, String status);

    @EntityGraph(attributePaths = "team")
    List<TeamMember> findByMember_UsernameAndStatusOrderByIdDesc(String username, String status);

    @EntityGraph(attributePaths = "member")
    List<TeamMember> findByTeam_IdAndStatusOrderByIdAsc(Long teamId, String status);
}
