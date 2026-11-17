package org.example.project.team.repository;

import org.example.project.team.entity.TaskComment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TaskCommentRepository extends JpaRepository<TaskComment, Long> {

    // ponytail: 업무 하나의 댓글을 한 번에 준다. 수백 개를 넘으면 cursor로 나눈다
    @EntityGraph(attributePaths = "author")
    List<TaskComment> findByTaskIdOrderByIdAsc(Long taskId);

    // 업무는 이미 팀과 함께 찾았으므로 댓글은 업무와 함께 찾으면 다른 팀 댓글이 걸리지 않는다
    @EntityGraph(attributePaths = "author")
    Optional<TaskComment> findByIdAndTaskId(Long id, Long taskId);
}
