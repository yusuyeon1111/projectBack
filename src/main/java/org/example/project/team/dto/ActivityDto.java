package org.example.project.team.dto;

import org.example.project.notification.service.NotificationService;
import org.example.project.team.entity.TeamActivity;

import java.time.LocalDateTime;
import java.util.Map;

public record ActivityDto(Long id, Long actorMemberId, String actorName, String type, String targetType, Long targetId,
                          Map<String, Object> detail, LocalDateTime createdAt) {

    public ActivityDto(TeamActivity a) {
        this(a.getId(), a.getActor().getId(), NotificationService.displayName(a.getActor()), a.getType(), a.getTargetType(),
                a.getTargetId(), a.getDetail(), a.getCreatedAt());
    }
}
