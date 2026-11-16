package org.example.project.notification.dto;

import org.example.project.notification.entity.Notification;

import java.time.LocalDateTime;

public record NotificationDto(Long id, String type, Long postId, String postTitle, String postCategory,
                              String role, String actor, boolean read, LocalDateTime createdAt) {

    public static NotificationDto from(Notification n) {
        return new NotificationDto(n.getId(), n.getType(), n.getPostId(), n.getPostTitle(), n.getPostCategory(),
                n.getRole(), n.getActor(), n.getReadAt() != null, n.getCreatedAt());
    }
}
