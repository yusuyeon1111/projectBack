package org.example.project.notification.service;

import lombok.RequiredArgsConstructor;
import org.example.project.notification.dto.NotificationDto;
import org.example.project.notification.entity.Notification;
import org.example.project.notification.repository.NotificationRepository;
import org.springframework.data.domain.Limit;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final int MAX_SIZE = 50;

    private final NotificationRepository notificationRepository;

    @Transactional(readOnly = true)
    public List<NotificationDto> list(String receiver, Long cursor, int size) {
        long before = cursor == null ? Long.MAX_VALUE : cursor;
        int limit = Math.max(1, Math.min(size, MAX_SIZE));
        return notificationRepository.findByReceiverAndIdLessThanOrderByIdDesc(receiver, before, Limit.of(limit))
                .stream().map(NotificationDto::from).toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(String receiver) {
        return notificationRepository.countByReceiverAndReadAtIsNull(receiver);
    }

    @Transactional
    public void read(Long id, String receiver) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("알림이 존재하지 않습니다."));
        if (!notification.getReceiver().equals(receiver)) {
            throw new AccessDeniedException("본인의 알림만 읽음 처리할 수 있습니다.");
        }
        notification.markRead();
    }

    @Transactional
    public void readAll(String receiver) {
        notificationRepository.markAllRead(receiver, LocalDateTime.now());
    }
}
