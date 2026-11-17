package org.example.project.team.repository;

import jakarta.persistence.LockModeType;
import org.example.project.team.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TeamRepository extends JpaRepository<Team, Long> {
    Optional<Team> findByPostId(Long postId);

    boolean existsByPostIdAndStatusNot(Long postId, String status);

    // 글이 지워진 팀(post_id NULL)과 없는 팀은 둘 다 빈 값이다
    @Query("SELECT t.postId FROM Team t WHERE t.id = :id")
    Optional<Long> findPostIdById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Team t WHERE t.id = :id")
    Optional<Team> findByIdForUpdate(@Param("id") Long id);
}
