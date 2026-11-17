package org.example.project.team.service;

import lombok.RequiredArgsConstructor;
import org.example.project.notification.service.SseEmitterRegistry;
import org.example.project.team.dto.ChatMessageDto;
import org.example.project.team.entity.ChatMessage;
import org.example.project.team.entity.TeamMember;
import org.example.project.team.repository.ChatMessageRepository;
import org.example.project.team.repository.TeamMemberRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

// 팀 채팅. 저장(MySQL)이 원본이고, 커밋된 메시지만 알림과 같은 회원별 SSE 연결에 chat 이벤트로 실어 보낸다
@Service
@RequiredArgsConstructor
public class ChatService {

    private static final int MAX_LENGTH = 1000;
    private static final int MAX_LIMIT = 100;

    private final TeamAccess teamAccess;
    private final ChatMessageRepository messageRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final SseEmitterRegistry sseEmitterRegistry;

    public record ChatMessageCreatedEvent(ChatMessageDto message) {}

    @Transactional
    public ChatMessageDto send(Long teamId, String username, String content) {
        TeamMember me = teamAccess.requireWritable(teamId, username);
        if (content == null || content.isBlank()) throw new IllegalArgumentException("메시지를 입력해주세요.");
        if (content.length() > MAX_LENGTH) throw new IllegalArgumentException("메시지는 1000자까지 보낼 수 있습니다.");
        ChatMessageDto message = new ChatMessageDto(messageRepository.save(new ChatMessage(teamId, me.getMember(), content.strip())));
        eventPublisher.publishEvent(new ChatMessageCreatedEvent(message));
        return message;
    }

    // after가 있으면 그 뒤를 오름차순, 없으면 before(없으면 최신) 앞을 내림차순으로 준다
    @Transactional(readOnly = true)
    public List<ChatMessageDto> find(Long teamId, String username, Long after, Long before, Integer limit) {
        teamAccess.requireMember(teamId, username);
        Limit size = Limit.of(Math.max(1, Math.min(limit == null ? 50 : limit, MAX_LIMIT)));
        List<ChatMessage> messages = after != null
                ? messageRepository.findByTeamIdAndIdGreaterThanOrderByIdAsc(teamId, after, size)
                : messageRepository.findByTeamIdAndIdLessThanOrderByIdDesc(teamId, before == null ? Long.MAX_VALUE : before, size);
        return messages.stream().map(ChatMessageDto::new).toList();
    }

    // 받는 사람은 전달하는 시점의 활성 팀원이다(보낸 사람 포함, 다른 탭도 받아야 한다). 메시지마다 한 번 조회한다.
    // ponytail: 서버 한 대 기준으로 이 서버의 연결에만 보낸다. 여러 대면 Redis 채널로 넘긴다(설계 §6.3)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void deliver(ChatMessageCreatedEvent event) {
        teamMemberRepository.findByTeam_IdAndStatusOrderByIdAsc(event.message().teamId(), TeamMember.ACTIVE)
                .forEach(m -> sseEmitterRegistry.sendChat(m.getMember().getUsername(), event.message()));
    }
}
