package org.example.project.notification.controller;

import lombok.RequiredArgsConstructor;
import org.example.project.notification.service.NotificationService;
import org.example.project.notification.service.SseTicketService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Optional;

// Bearer 헤더 대신 1회용 티켓으로 회원을 확인하므로 SecurityConfig에서는 permitAll이다
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationStreamController {

    private final SseTicketService sseTicketService;
    private final NotificationService notificationService;
    private final RedisTemplate<String, String> redisTemplate;

    // 재연결 시 마지막으로 받은 id는 Last-Event-ID 헤더나 lastEventId 쿼리로 받는다. 둘 다 있으면 쿼리를 쓴다
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> subscribe(@RequestParam(required = false) String ticket,
                                                @RequestParam(required = false) Long lastEventId,
                                                @RequestHeader(value = "Last-Event-ID", required = false) Long lastEventIdHeader) {
        // 티켓 발급 뒤 30초 안에 차단된 회원도 막는다
        Optional<String> username = sseTicketService.consume(ticket)
                .filter(u -> !Boolean.TRUE.equals(redisTemplate.hasKey("BAN:" + u)));
        if (username.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(notificationService.subscribe(username.get(), lastEventId != null ? lastEventId : lastEventIdHeader));
    }
}
