package org.example.project.notification.controller;

import lombok.RequiredArgsConstructor;
import org.example.project.notification.dto.NotificationDto;
import org.example.project.notification.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public List<NotificationDto> list(@RequestParam(required = false) Long cursor,
                                      @RequestParam(defaultValue = "20") int size, Principal principal) {
        return notificationService.list(principal.getName(), cursor, size);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(Principal principal) {
        return Map.of("count", notificationService.unreadCount(principal.getName()));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> read(@PathVariable Long id, Principal principal) {
        notificationService.read(id, principal.getName());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/read-all")
    public ResponseEntity<Void> readAll(Principal principal) {
        notificationService.readAll(principal.getName());
        return ResponseEntity.noContent().build();
    }
}
