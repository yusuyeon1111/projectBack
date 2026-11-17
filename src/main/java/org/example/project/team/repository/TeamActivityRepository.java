package org.example.project.team.repository;

import org.example.project.team.entity.TeamActivity;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TeamActivityRepository extends JpaRepository<TeamActivity, Long> {

    // 최신순. cursor는 앞 페이지의 마지막 id, taskId를 주면 그 업무의 기록만
    @Query("SELECT a FROM TeamActivity a JOIN FETCH a.actor WHERE a.teamId = :teamId " +
            "AND (:cursor IS NULL OR a.id < :cursor) " +
            "AND (:taskId IS NULL OR (a.targetType = 'TASK' AND a.targetId = :taskId)) ORDER BY a.id DESC")
    List<TeamActivity> findPage(@Param("teamId") Long teamId, @Param("cursor") Long cursor, @Param("taskId") Long taskId, Limit limit);
}
