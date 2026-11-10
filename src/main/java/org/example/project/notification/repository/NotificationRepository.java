package org.example.project.notification.repository;

import org.example.project.notification.entity.Notification;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // 커서 기반 목록. (receiver, id) 인덱스를 거꾸로 읽다가 limit에서 멈춘다
    List<Notification> findByReceiverAndIdLessThanOrderByIdDesc(String receiver, Long cursor, Limit limit);

    // 재연결 시 놓친 알림을 다시 보내는 용도
    List<Notification> findByReceiverAndIdGreaterThanOrderByIdAsc(String receiver, Long id);

    long countByReceiverAndReadAtIsNull(String receiver);

    @Modifying
    @Query("UPDATE Notification n SET n.readAt = :now WHERE n.receiver = :receiver AND n.readAt IS NULL")
    int markAllRead(@Param("receiver") String receiver, @Param("now") LocalDateTime now);
}
