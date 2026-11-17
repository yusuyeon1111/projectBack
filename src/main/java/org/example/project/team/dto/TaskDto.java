package org.example.project.team.dto;

import org.example.project.team.entity.Task;
import org.example.project.team.entity.TeamMember;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record TaskDto(Long taskId, String title, String description, String status, Long assigneeMemberId,
                      Long creatorMemberId, LocalDate dueDate, Long version, LocalDateTime createdAt,
                      LocalDateTime updatedAt, boolean deletable) {

    public TaskDto(Task t, TeamMember viewer) {
        this(t.getId(), t.getTitle(), t.getDescription(), t.getStatus(), t.getAssigneeMemberId(), t.getCreatorMemberId(),
                t.getDueDate(), t.getVersion(), t.getCreatedAt(), t.getUpdatedAt(), deletable(t, viewer));
    }

    // 팀장이거나 업무를 만든 사람
    public static boolean deletable(Task task, TeamMember viewer) {
        return viewer.isLeader() || task.getCreatorMemberId().equals(viewer.getMember().getId());
    }
}
