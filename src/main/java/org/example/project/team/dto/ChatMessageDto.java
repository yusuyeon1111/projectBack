package org.example.project.team.dto;

import org.example.project.notification.service.NotificationService;
import org.example.project.team.entity.ChatMessage;

import java.time.LocalDateTime;

public record ChatMessageDto(Long id, Long teamId, Long senderMemberId, String senderName, String content, LocalDateTime createdAt) {

    public ChatMessageDto(ChatMessage m) {
        this(m.getId(), m.getTeamId(), m.getSender().getId(), NotificationService.displayName(m.getSender()), m.getContent(), m.getCreatedAt());
    }
}
