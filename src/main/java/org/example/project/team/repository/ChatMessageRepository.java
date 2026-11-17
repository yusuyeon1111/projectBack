package org.example.project.team.repository;

import org.example.project.team.entity.ChatMessage;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    // 재연결·방 열기·탭 복귀 때 빠진 메시지를 오름차순으로
    @EntityGraph(attributePaths = "sender")
    List<ChatMessage> findByTeamIdAndIdGreaterThanOrderByIdAsc(Long teamId, Long after, Limit limit);

    // 이전 대화를 거슬러 올라갈 때 내림차순으로
    @EntityGraph(attributePaths = "sender")
    List<ChatMessage> findByTeamIdAndIdLessThanOrderByIdDesc(Long teamId, Long before, Limit limit);
}
