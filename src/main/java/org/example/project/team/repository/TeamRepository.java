package org.example.project.team.repository;

import org.example.project.team.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TeamRepository extends JpaRepository<Team, Long> {
    Optional<Team> findByPostId(Long postId);

    boolean existsByPostIdAndStatusNot(Long postId, String status);
}
