package org.example.project.team.service;

import lombok.RequiredArgsConstructor;
import org.example.project.member.entity.Member;
import org.example.project.member.repository.MemberRepository;
import org.example.project.notification.service.NotificationService;
import org.example.project.team.dto.CommentDto;
import org.example.project.team.dto.ActivityDto;
import org.example.project.team.dto.TaskDto;
import org.example.project.team.dto.TaskRequest;
import org.example.project.team.entity.Task;
import org.example.project.team.entity.TaskComment;
import org.example.project.team.entity.TeamActivity;
import org.example.project.team.entity.TeamMember;
import org.example.project.team.repository.TaskCommentRepository;
import org.example.project.team.repository.TaskRepository;
import org.example.project.team.repository.TeamActivityRepository;
import org.example.project.team.repository.TeamMemberRepository;
import org.springframework.data.domain.Limit;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

// 팀 업무와 댓글. 조회·쓰기 모두 팀원만 되고, 팀원이 아니면 팀이 있는지도 알려 주지 않는다(404)
@Service
@RequiredArgsConstructor
public class TaskService {

    private static final int ACTIVITY_PAGE = 30;

    private final TaskRepository taskRepository;
    private final TeamActivityRepository activityRepository;
    private final TeamAccess teamAccess;
    private final TeamMemberRepository teamMemberRepository;
    private final TaskCommentRepository commentRepository;
    private final MemberRepository memberRepository;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public List<TaskDto> findTasks(Long teamId, String username) {
        TeamMember me = teamAccess.requireMember(teamId, username);
        return taskRepository.findByTeamIdOrderByIdAsc(teamId).stream().map(t -> new TaskDto(t, me)).toList();
    }

    @Transactional(readOnly = true)
    public TaskDto findTask(Long teamId, Long taskId, String username) {
        TeamMember me = teamAccess.requireMember(teamId, username);
        return new TaskDto(requireTask(teamId, taskId), me);
    }

    @Transactional
    public TaskDto create(Long teamId, String username, TaskRequest request) {
        TeamMember me = teamAccess.requireWritable(teamId, username);
        validate(teamId, request);
        Task task = taskRepository.save(new Task(teamId, me.getMember().getId(), request.title().strip(),
                request.description(), request.assigneeMemberId(), request.dueDate()));
        record(me, "TASK_CREATED", task, Map.of("title", task.getTitle()));
        notifyAssigned(me, task, null);
        return new TaskDto(task, me);
    }

    // 받은 version이 지금 값과 다르면 그 사이 누가 고친 것이다. 읽은 뒤 저장 전에 끼어든 수정은 @Version이 막는다
    @Transactional
    public TaskDto update(Long teamId, Long taskId, String username, TaskRequest request) {
        TeamMember me = teamAccess.requireWritable(teamId, username);
        if (request.version() == null) throw new IllegalArgumentException("version이 필요합니다.");
        Task task = requireTask(teamId, taskId);
        if (!request.version().equals(task.getVersion())) {
            throw new IllegalStateException("다른 팀원이 먼저 업무를 바꿨습니다. 새로고침한 뒤 다시 시도해주세요.");
        }
        validate(teamId, request);
        Long previousAssignee = task.getAssigneeMemberId();
        task.edit(request.title().strip(), request.description(), request.assigneeMemberId(), request.dueDate());
        taskRepository.flush(); // 올라간 version을 응답에 담는다
        record(me, "TASK_UPDATED", task, Map.of("title", task.getTitle()));
        notifyAssigned(me, task, previousAssignee);
        return new TaskDto(task, me);
    }

    // 조건부 UPDATE라 같은 이동을 연타해도 한 번만 바뀌고 기록도 한 줄만 남는다
    @Transactional
    public TaskDto changeStatus(Long teamId, Long taskId, String username, String from, String to) {
        TeamMember me = teamAccess.requireWritable(teamId, username);
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
        TeamMember me = teamAccess.requireWritable(teamId, username);
        Task task = requireTask(teamId, taskId);
        if (!TaskDto.deletable(task, me)) throw new AccessDeniedException("팀장이나 업무를 만든 사람만 지울 수 있습니다.");
        taskRepository.delete(task);
        record(me, "TASK_DELETED", task, Map.of("title", task.getTitle()));
    }

    // --- 댓글 ---

    @Transactional(readOnly = true)
    public List<CommentDto> findComments(Long teamId, Long taskId, String username) {
        TeamMember me = teamAccess.requireMember(teamId, username);
        requireTask(teamId, taskId);
        return commentRepository.findByTaskIdOrderByIdAsc(taskId).stream().map(c -> new CommentDto(c, me)).toList();
    }

    // 업무를 만든 사람과 담당자에게 알린다(쓴 사람 자신은 빼고)
    @Transactional
    public CommentDto addComment(Long teamId, Long taskId, String username, String content) {
        TeamMember me = teamAccess.requireWritable(teamId, username);
        Task task = requireTask(teamId, taskId);
        if (content == null || content.isBlank()) throw new IllegalArgumentException("댓글을 입력해주세요.");
        if (content.length() > 2000) throw new IllegalArgumentException("댓글은 2000자까지 쓸 수 있습니다.");
        TaskComment comment = commentRepository.save(new TaskComment(taskId, me.getMember(), content.strip()));
        record(me, "COMMENT_CREATED", task, Map.of("title", task.getTitle()));

        Set<Long> receivers = new HashSet<>();
        receivers.add(task.getCreatorMemberId());
        if (task.getAssigneeMemberId() != null) receivers.add(task.getAssigneeMemberId());
        notifyMembers(me, "TASK_COMMENT", receivers);
        return new CommentDto(comment, me);
    }

    @Transactional
    public void deleteComment(Long teamId, Long taskId, Long commentId, String username) {
        TeamMember me = teamAccess.requireWritable(teamId, username);
        Task task = requireTask(teamId, taskId);
        TaskComment comment = commentRepository.findByIdAndTaskId(commentId, taskId)
                .orElseThrow(() -> new NoSuchElementException("댓글을 찾을 수 없습니다."));
        if (!CommentDto.deletable(comment, me)) throw new AccessDeniedException("팀장이나 댓글을 쓴 사람만 지울 수 있습니다.");
        commentRepository.delete(comment);
        record(me, "COMMENT_DELETED", task, Map.of("title", task.getTitle()));
    }

    @Transactional(readOnly = true)
    public List<ActivityDto> findActivities(Long teamId, String username, Long cursor, Long taskId) {
        teamAccess.requireMember(teamId, username);
        return activityRepository.findPage(teamId, cursor, taskId, Limit.of(ACTIVITY_PAGE)).stream().map(ActivityDto::new).toList();
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

    // 담당자가 새로 정해졌을 때만 알린다. 같은 사람으로 다시 저장하거나 담당을 비우면 알리지 않는다
    private void notifyAssigned(TeamMember me, Task task, Long previousAssignee) {
        Long assignee = task.getAssigneeMemberId();
        if (assignee != null && !Objects.equals(assignee, previousAssignee)) notifyMembers(me, "TASK_ASSIGNED", Set.of(assignee));
    }

    // 알림은 팀 알림(target TEAM, post_title에 팀 이름)이라 프론트가 마이페이지 내 팀으로 보낸다
    private void notifyMembers(TeamMember me, String type, Set<Long> memberIds) {
        List<String> usernames = memberRepository.findAllById(memberIds).stream().map(Member::getUsername).toList();
        notificationService.notifyTeam(me.getTeam(), type, usernames, me.getMember().getUsername(),
                NotificationService.displayName(me.getMember()), null);
    }

    private void record(TeamMember actor, String type, Task task, Map<String, Object> detail) {
        activityRepository.save(new TeamActivity(task.getTeamId(), actor.getMember(), type, "TASK", task.getId(), detail));
    }
}
