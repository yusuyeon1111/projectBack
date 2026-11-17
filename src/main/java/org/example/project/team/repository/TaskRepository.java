package org.example.project.team.repository;

import org.example.project.team.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long> {

    // 다른 팀의 업무 id를 경로에 넣어도 찾지 못하게 항상 팀과 함께 조회한다
    Optional<Task> findByIdAndTeamId(Long id, Long teamId);

    // ponytail: 팀 업무 전체를 한 번에 준다. 팀당 업무가 수백 개를 넘으면 페이지를 나눈다
    List<Task> findByTeamIdOrderByIdAsc(Long teamId);

    // 연타·동시 이동에도 한 번만 바뀐다. 필드 수정과 엇갈리지 않게 version도 올린다.
    // 벌크 UPDATE라 이미 읽어 둔 업무 엔티티는 갱신되지 않는다. 부른 뒤에 업무를 읽는다
    @Modifying
    @Query("UPDATE Task t SET t.status = :to, t.version = t.version + 1, t.updatedAt = CURRENT_TIMESTAMP " +
            "WHERE t.id = :id AND t.teamId = :teamId AND t.status = :from")
    int changeStatus(@Param("id") Long id, @Param("teamId") Long teamId, @Param("from") String from, @Param("to") String to);

    // 탈퇴·퇴출한 사람이 맡던 업무의 담당자를 비운다.
    // clearAutomatically를 쓰지 않는다. 탈퇴 처리의 아직 flush 안 된 팀원 변경이 함께 버려진다
    @Modifying
    @Query("UPDATE Task t SET t.assigneeMemberId = NULL, t.version = t.version + 1, t.updatedAt = CURRENT_TIMESTAMP " +
            "WHERE t.teamId = :teamId AND t.assigneeMemberId = :memberId")
    int unassign(@Param("teamId") Long teamId, @Param("memberId") Long memberId);
}
