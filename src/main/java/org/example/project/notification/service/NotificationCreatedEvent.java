package org.example.project.notification.service;

import org.example.project.notification.dto.NotificationDto;

// 알림이 저장된 직후 발행된다. 실시간 전달은 이 이벤트를 커밋 이후에 받아서 처리한다.
public record NotificationCreatedEvent(String receiver, NotificationDto notification) {
}
