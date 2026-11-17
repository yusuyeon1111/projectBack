package org.example.project.team.dto;

import org.example.project.notification.service.NotificationService;
import org.example.project.team.entity.TaskComment;
import org.example.project.team.entity.TeamMember;

import java.time.LocalDateTime;

public record CommentDto(Long commentId, Long authorMemberId, String authorName, String content, LocalDateTime createdAt,
                         boolean deletable) {

    public CommentDto(TaskComment c, TeamMember viewer) {
        this(c.getId(), c.getAuthor().getId(), NotificationService.displayName(c.getAuthor()), c.getContent(), c.getCreatedAt(),
                deletable(c, viewer));
    }

    // 팀장이거나 댓글을 쓴 사람
    public static boolean deletable(TaskComment c, TeamMember viewer) {
        return viewer.isLeader() || c.getAuthor().getId().equals(viewer.getMember().getId());
    }
}
