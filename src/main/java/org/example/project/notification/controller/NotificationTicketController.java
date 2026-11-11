package org.example.project.notification.controller;

import lombok.RequiredArgsConstructor;
import org.example.project.notification.service.SseTicketService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationTicketController {

    private final SseTicketService sseTicketService;

    @PostMapping("/ticket")
    public Map<String, String> issueTicket(Principal principal) {
        return Map.of("ticket", sseTicketService.issue(principal.getName()));
    }
}
