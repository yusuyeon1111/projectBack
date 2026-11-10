package org.example.project.notification.service;

import lombok.RequiredArgsConstructor;
import org.example.project.member.entity.Member;
import org.example.project.notification.dto.NotificationDto;
import org.example.project.notification.entity.Notification;
import org.example.project.notification.repository.NotificationRepository;
import org.example.project.post.entity.Post;
import org.example.project.post.entity.PostPosition;
import org.example.project.post.entity.PostPositionMember;
import org.example.project.post.repositoty.PostPositionRepository;
import org.springframework.context.ApplicationEventPublisher;
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
    private final PostPositionRepository postPositionRepository;
    private final ApplicationEventPublisher eventPublisher;

    // 신청은 트랜잭션 없이 저장되므로(PositionService 참고) 포지션과 글을 여기서 다시 읽는다
    @Transactional
    public void notifyApply(Long positionId, Member applicant) {
        PostPosition position = postPositionRepository.findById(positionId)
                .orElseThrow(() -> new RuntimeException("포지션이 존재하지 않습니다."));
        String actor = applicant.getNickname() != null ? applicant.getNickname() : applicant.getUsername();
        create(position.getPost().getAuthor(), "APPLY", position, applicant.getUsername(), actor);
    }

    // 승인·거절 트랜잭션에 합류해 신청 상태 변경과 함께 커밋·롤백된다. type은 처리된 신청의 상태(ACCEPT/REJECT)
    @Transactional
    public void notifyResult(PostPositionMember application) {
        PostPosition position = application.getPostPosition();
        create(application.getMember().getUsername(), application.getStatus(), position,
                position.getPost().getAuthor(), null);
    }

    private void create(String receiver, String type, PostPosition position, String actingUser, String actor) {
        if (receiver.equals(actingUser)) return;
        Post post = position.getPost();
        Notification saved = notificationRepository.save(new Notification(
                receiver, type, post.getId(), post.getTitle(), post.getCategory(), position.getRole(), actor));
        eventPublisher.publishEvent(new NotificationCreatedEvent(receiver, NotificationDto.from(saved)));
    }

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
