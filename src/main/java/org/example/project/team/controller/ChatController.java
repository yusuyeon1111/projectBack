package org.example.project.team.controller;

import lombok.RequiredArgsConstructor;
import org.example.project.team.dto.ChatMessageDto;
import org.example.project.team.service.ChatService;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/teams/{teamId}/messages")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @GetMapping
    public List<ChatMessageDto> messages(@PathVariable Long teamId, @RequestParam(required = false) Long after,
                                         @RequestParam(required = false) Long before, @RequestParam(required = false) Integer limit,
                                         Principal principal) {
        return chatService.find(teamId, principal.getName(), after, before, limit);
    }

    public record SendRequest(String content) {}

    @PostMapping
    public ChatMessageDto send(@PathVariable Long teamId, @RequestBody SendRequest request, Principal principal) {
        return chatService.send(teamId, principal.getName(), request.content());
    }
}
