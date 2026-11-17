package org.example.project.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.example.project.notification.dto.NotificationDto;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// 회원별로 열린 SSE 연결(탭마다 하나)을 들고 있다.
// ponytail: 서버 메모리에만 있어 서버가 한 대일 때만 맞다. 여러 대로 늘리면 Redis Pub/Sub으로 다른 서버의 연결에도 전달해야 한다.
@Slf4j
@Component
public class SseEmitterRegistry {

    private static final long TIMEOUT = Duration.ofMinutes(30).toMillis();

    private final Map<String, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

    // connected를 등록보다 먼저 보내야, 그 사이 도착한 알림이 connected보다 앞서지 않는다
    public SseEmitter add(String username) {
        SseEmitter emitter = new SseEmitter(TIMEOUT);
        emitter.onCompletion(() -> remove(username, emitter));
        emitter.onTimeout(() -> remove(username, emitter));
        emitter.onError(e -> remove(username, emitter));
        send(username, emitter, SseEmitter.event().name("connected").data("ok"));
        emitters.compute(username, (k, set) -> {
            Set<SseEmitter> s = set != null ? set : ConcurrentHashMap.newKeySet();
            s.add(emitter);
            return s;
        });
        return emitter;
    }

    public void send(String username, NotificationDto notification) {
        emitters.getOrDefault(username, Set.of()).forEach(e -> send(username, e, notification));
    }

    // SseEventBuilder는 build할 때 내용이 바뀌어 재사용할 수 없으므로 연결마다 새로 만든다
    public void send(String username, SseEmitter emitter, NotificationDto notification) {
        send(username, emitter, SseEmitter.event().name("notification").id(String.valueOf(notification.id())).data(notification));
    }

    public void closeAll(String username) {
        Set<SseEmitter> closed = emitters.remove(username);
        if (closed != null) closed.forEach(SseEmitter::complete);
    }

    // 프록시·로드밸런서가 조용한 연결을 끊지 않도록 주석 한 줄을 흘려 보낸다. 실패한 연결도 여기서 정리된다
    @Scheduled(fixedRate = 30_000)
    public void heartbeat() {
        emitters.forEach((username, set) -> set.forEach(e -> send(username, e, SseEmitter.event().comment("heartbeat"))));
    }

    public int count(String username) {
        return emitters.getOrDefault(username, Set.of()).size();
    }

    // 클라이언트가 사라진 연결은 보내다 실패한다. 그 예외가 알림을 만든 요청(승인 등)으로 번지지 않게 여기서 삼키고 연결을 버린다
    private void send(String username, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (Exception e) {
            log.debug("SSE 전송 실패, 연결 제거: {}", username, e);
            remove(username, emitter);
        }
    }

    private void remove(String username, SseEmitter emitter) {
        emitters.computeIfPresent(username, (k, set) -> {
            set.remove(emitter);
            return set.isEmpty() ? null : set;
        });
    }
}
