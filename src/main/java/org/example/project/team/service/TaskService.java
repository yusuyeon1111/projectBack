package org.example.project.team.service;

import lombok.RequiredArgsConstructor;
import org.example.project.team.dto.ActivityDto;
import org.example.project.team.dto.TaskDto;
import org.example.project.team.dto.TaskRequest;
import org.example.project.team.entity.Task;
import org.example.project.team.entity.Team;
import org.example.project.team.entity.TeamActivity;
import org.example.project.team.entity.TeamMember;
import org.example.project.team.repository.TaskRepository;
import org.example.project.team.repository.TeamActivityRepository;
import org.example.project.team.repository.TeamMemberRepository;
import org.example.project.team.repository.TeamRepository;
import org.springframework.data.domain.Limit;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

// 팀 업무. 조회·쓰기 모두 팀원만 되고, 팀원이 아니면 팀이 있는지도 알려 주지 않는다(404)
@Service
@RequiredArgsConstructor
public class TaskService {

    private static final int ACTIVITY_PAGE = 30;

    private final TaskRepository taskRepository;
    private final TeamActivityRepository activityRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;

    @Transactional(readOnly = true)
    public List<TaskDto> findTasks(Long teamId, String username) {
        TeamMember me = requireMember(teamId, username);
        return taskRepository.findByTeamIdOrderByIdAsc(teamId).stream().map(t -> new TaskDto(t, me)).toList();
    }

    @Transactional(readOnly = true)
    public TaskDto findTask(Long teamId, Long taskId, String username) {
        TeamMember me = requireMember(teamId, username);
        return new TaskDto(requireTask(teamId, taskId), me);
    }

    @Transactional
    public TaskDto create(Long teamId, String username, TaskRequest request) {
        TeamMember me = requireWritable(teamId, username);
        validate(teamId, request);
        Task task = taskRepository.save(new Task(teamId, me.getMember().getId(), request.title().strip(),
                request.description(), request.assigneeMemberId(), request.dueDate()));
        record(me, "TASK_CREATED", task, Map.of("title", task.getTitle()));
        return new TaskDto(task, me);
    }

    // 받은 version이 지금 값과 다르면 그 사이 누가 고친 것이다. 읽은 뒤 저장 전에 끼어든 수정은 @Version이 막는다
    @Transactional
    public TaskDto update(Long teamId, Long taskId, String username, TaskRequest request) {
        TeamMember me = requireWritable(teamId, username);
        if (request.version() == null) throw new IllegalArgumentException("version이 필요합니다.");
        Task task = requireTask(teamId, taskId);
        if (!request.version().equals(task.getVersion())) {
            throw new IllegalStateException("다른 팀원이 먼저 업무를 바꿨습니다. 새로고침한 뒤 다시 시도해주세요.");
        }
        validate(teamId, request);
        task.edit(request.title().strip(), request.description(), request.assigneeMemberId(), request.dueDate());
        taskRepository.flush(); // 올라간 version을 응답에 담는다
        record(me, "TASK_UPDATED", task, Map.of("title", task.getTitle()));
        return new TaskDto(task, me);
    }

    // 조건부 UPDATE라 같은 이동을 연타해도 한 번만 바뀌고 기록도 한 줄만 남는다
    @Transactional
    public TaskDto changeStatus(Long teamId, Long taskId, String username, String from, String to) {
        TeamMember me = requireWritable(teamId, username);
        if (!Task.STATUSES.contains(from) || !Task.STATUSES.contains(to) || from.equals(to)) {
            throw new IllegalArgumentException("바꿀 수 없는 상태입니다.");
        }
        if (taskRepository.changeStatus(taskId, teamId, from, to) == 0) {
            requireTask(teamId, taskId); // 없는 업무면 404
            throw new IllegalStateException("업무 상태가 이미 바뀌었습니다. 새로고침한 뒤 다시 시도해주세요.");
        }
        Task task = requireTask(teamId, taskId);
        record(me, "TASK_STATUS", task, Map.of("title", task.getTitle(), "from", from, "to", to));
        return new TaskDto(task, me);
    }

    @Transactional
    public void delete(Long teamId, Long taskId, String username) {
        TeamMember me = requireWritable(teamId, username);
        Task task = requireTask(teamId, taskId);
        if (!TaskDto.deletable(task, me)) throw new AccessDeniedException("팀장이나 업무를 만든 사람만 지울 수 있습니다.");
        taskRepository.delete(task);
        record(me, "TASK_DELETED", task, Map.of("title", task.getTitle()));
    }

    @Transactional(readOnly = true)
    public List<ActivityDto> findActivities(Long teamId, String username, Long cursor, Long taskId) {
        requireMember(teamId, username);
        return activityRepository.findPage(teamId, cursor, taskId, Limit.of(ACTIVITY_PAGE)).stream().map(ActivityDto::new).toList();
    }

    private TeamMember requireMember(Long teamId, String username) {
        return teamMemberRepository.findByTeam_IdAndMember_UsernameAndStatus(teamId, username, TeamMember.ACTIVE)
                .orElseThrow(() -> new NoSuchElementException("팀을 찾을 수 없습니다."));
    }

    // 팀 행을 공유 잠금으로 먼저 잡는다. 완료·해산·퇴출은 팀 행을 배타 잠금하므로 업무 쓰기와 줄을 서고,
    // 잠금 읽기는 스냅샷을 만들지 않아 그 뒤의 팀원 조회가 방금 커밋된 퇴출까지 본다
    private TeamMember requireWritable(Long teamId, String username) {
        Team team = teamRepository.findByIdForShare(teamId).orElseThrow(() -> new NoSuchElementException("팀을 찾을 수 없습니다."));
        TeamMember me = requireMember(teamId, username);
        if (!team.isOpen()) throw new IllegalStateException("완료되었거나 해산된 팀은 바꿀 수 없습니다.");
        return me;
    }

    private Task requireTask(Long teamId, Long taskId) {
        return taskRepository.findByIdAndTeamId(taskId, teamId).orElseThrow(() -> new NoSuchElementException("업무를 찾을 수 없습니다."));
    }

    private void validate(Long teamId, TaskRequest request) {
        if (request.title() == null || request.title().isBlank()) throw new IllegalArgumentException("업무 제목을 입력해주세요.");
        if (request.title().strip().length() > 100) throw new IllegalArgumentException("업무 제목은 100자까지 쓸 수 있습니다.");
        if (request.description() != null && request.description().length() > 5000) {
            throw new IllegalArgumentException("업무 설명은 5000자까지 쓸 수 있습니다.");
        }
        if (request.assigneeMemberId() != null
                && !teamMemberRepository.existsByTeam_IdAndMember_IdAndStatus(teamId, request.assigneeMemberId(), TeamMember.ACTIVE)) {
            throw new IllegalArgumentException("담당자는 팀원이어야 합니다.");
        }
    }

    private void record(TeamMember actor, String type, Task task, Map<String, Object> detail) {
        activityRepository.save(new TeamActivity(task.getTeamId(), actor.getMember(), type, "TASK", task.getId(), detail));
    }
}
