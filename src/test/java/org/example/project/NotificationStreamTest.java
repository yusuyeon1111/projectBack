package org.example.project;

import org.example.project.member.entity.Member;
import org.example.project.member.repository.MemberRepository;
import org.example.project.notification.entity.Notification;
import org.example.project.notification.repository.NotificationRepository;
import org.example.project.notification.service.NotificationService;
import org.example.project.notification.service.SseEmitterRegistry;
import org.example.project.notification.service.SseTicketService;
import org.example.project.post.entity.Post;
import org.example.project.post.entity.PostPosition;
import org.example.project.post.entity.PostPositionMember;
import org.example.project.post.repositoty.PostPositionMemberRepository;
import org.example.project.post.repositoty.PostRepository;
import org.example.project.security.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

// 실제 스트리밍을 확인하려고 내장 서버를 띄우고 HTTP로 구독한다.
// 공용 DB를 쓰므로 회원·수신자 이름에 고유 접두어를 붙이고, 만든 행·키는 직접 지운다.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "jwt.secret=dGVzdC1zZWNyZXQta2V5LWZvci1zZWN1cml0eS1hY2Nlc3MtdGVzdA==",
        "spring.data.redis.usessl=false",
        "KAKAO_CLIENT_ID=test", "AWS_ACCESS_KEY_ID=test", "AWS_SECRET_ACCESS_KEY=test",
        "AWS_REGION=ap-northeast-2", "AWS_S3_BUCKET_NAME=test",
})
class NotificationStreamTest {

    static final Duration WAIT = Duration.ofSeconds(5);

    @LocalServerPort int port;
    @Autowired JwtTokenProvider jwt;
    @Autowired SseTicketService ticketService;
    @Autowired SseEmitterRegistry registry;
    @Autowired NotificationService notificationService;
    @Autowired NotificationRepository notificationRepository;
    @Autowired PostRepository postRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired PostPositionMemberRepository applicationRepository;
    @Autowired RedisTemplate<String, String> redisTemplate;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired PlatformTransactionManager transactionManager;

    final HttpClient http = HttpClient.newHttpClient();
    final String prefix = "st-" + UUID.randomUUID().toString().substring(0, 8) + "-";
    final String author = prefix + "author";
    final List<Member> members = new ArrayList<>();
    final List<SseStream> streams = new ArrayList<>();
    final List<String> redisKeys = new ArrayList<>();
    Post post;

    @AfterEach
    void cleanUp() {
        streams.forEach(SseStream::close);
        redisTemplate.delete(redisKeys);
        if (post != null) postRepository.findById(post.getId()).ifPresent(postRepository::delete);
        memberRepository.deleteAll(members);
        jdbcTemplate.update("DELETE FROM notification WHERE receiver LIKE ?", prefix + "%");
    }

    // --- 구독 인증 ---

    @Test
    void 티켓이_없거나_모르거나_이미_쓴_티켓이면_401() throws Exception {
        assertThat(subscribeStatus("/api/notifications/subscribe")).isEqualTo(401);
        assertThat(subscribeStatus("/api/notifications/subscribe?ticket=no-such-ticket")).isEqualTo(401);

        String ticket = ticketService.issue(prefix + "a");
        subscribe(ticket, null, null);
        assertThat(subscribeStatus("/api/notifications/subscribe?ticket=" + ticket)).isEqualTo(401);
    }

    @Test
    void 티켓_발급_뒤_차단된_회원은_401() throws Exception {
        String user = prefix + "banned";
        String ticket = ticketService.issue(user);
        redisTemplate.opsForValue().set("BAN:" + user, "ban", 1, TimeUnit.MINUTES);
        redisKeys.add("BAN:" + user);

        assertThat(subscribeStatus("/api/notifications/subscribe?ticket=" + ticket)).isEqualTo(401);
    }

    @Test
    void 연결되면_connected_이벤트가_먼저_온다() throws Exception {
        HttpResponse<Stream<String>> response = open("/api/notifications/subscribe?ticket=" + ticketService.issue(prefix + "a"), null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(v -> assertThat(v).startsWith("text/event-stream"));
        SseStream stream = track(new SseStream(response));

        Map<String, String> first = stream.next();
        assertThat(first).containsEntry("event", "connected").containsEntry("data", "ok");
    }

    // --- 커밋 후 전달 ---

    @Test
    void 승인하면_신청자에게만_notification_이벤트가_간다() throws Exception {
        PostPosition position = createPost(2);
        long a = apply(position, member("a"));
        long b = apply(position, member("b"));
        SseStream aStream = connected(prefix + "a");
        SseStream bStream = connected(prefix + "b");

        assertThat(accept(a)).isEqualTo(200);

        Map<String, String> event = aStream.next();
        Notification saved = only(prefix + "a");
        assertThat(event).containsEntry("event", "notification").containsEntry("id", String.valueOf(saved.getId()));
        assertThat(event.get("data")).contains("\"id\":" + saved.getId(), "\"type\":\"ACCEPT\"", "\"postId\":" + post.getId());

        // b의 다음 이벤트가 자기 거절 알림이면, 그 앞에 a의 승인 알림이 끼지 않은 것이다
        assertThat(reject(b)).isEqualTo(200);
        assertThat(bStream.next()).containsEntry("event", "notification").containsEntry("id", String.valueOf(only(prefix + "b").getId()));
    }

    @Test
    void 정원이_차서_409인_승인은_아무것도_보내지_않는다() throws Exception {
        PostPosition position = createPost(1);
        long a = apply(position, member("a"));
        long b = apply(position, member("b"));
        assertThat(accept(a)).isEqualTo(200);
        SseStream bStream = connected(prefix + "b");

        assertThat(accept(b)).isEqualTo(409);
        assertThat(reject(b)).isEqualTo(200);

        Map<String, String> event = bStream.next();
        assertThat(event.get("data")).contains("\"type\":\"REJECT\"");
    }

    @Test
    void 롤백된_트랜잭션에서_만든_알림은_보내지_않는다() throws Exception {
        PostPosition position = createPost(1);
        long a = apply(position, member("a"));
        SseStream aStream = connected(prefix + "a");

        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            PostPositionMember application = applicationRepository.findById(a).orElseThrow();
            application.setStatus("ACCEPT");
            notificationService.notifyResult(application);
            tx.setRollbackOnly();
        });
        assertThat(reject(a)).isEqualTo(200);

        Map<String, String> event = aStream.next();
        assertThat(event.get("data")).contains("\"type\":\"REJECT\"");
        assertThat(event).containsEntry("id", String.valueOf(only(prefix + "a").getId()));
    }

    @Test
    void 같은_회원의_두_연결이_모두_받는다() throws Exception {
        PostPosition position = createPost(1);
        long a = apply(position, member("a"));
        SseStream first = connected(prefix + "a");
        SseStream second = connected(prefix + "a");

        assertThat(accept(a)).isEqualTo(200);

        String id = String.valueOf(only(prefix + "a").getId());
        assertThat(first.next()).containsEntry("id", id);
        assertThat(second.next()).containsEntry("id", id);
    }

    @Test
    void 끊긴_연결은_지우고_다른_연결과_승인_요청에는_영향이_없다() throws Exception {
        PostPosition position = createPost(1);
        long a = apply(position, member("a"));
        SseStream live = connected(prefix + "a");
        // 응답에 붙지 않은 채 끝난 emitter는 send가 예외를 던진다. 클라이언트가 사라진 연결과 같은 상황
        registry.add(prefix + "a").complete();
        assertThat(registry.count(prefix + "a")).isEqualTo(2);

        assertThat(accept(a)).isEqualTo(200);

        assertThat(live.next()).containsEntry("id", String.valueOf(only(prefix + "a").getId()));
        assertThat(registry.count(prefix + "a")).isEqualTo(1);
    }

    // --- 재연결 시 재전송 ---

    @Test
    void lastEventId_쿼리보다_큰_알림을_오름차순으로_재전송한다() throws Exception {
        long n1 = save(prefix + "a"), n2 = save(prefix + "a"), n3 = save(prefix + "a");
        save(prefix + "other");

        SseStream stream = subscribe(ticketService.issue(prefix + "a"), n1, null);

        assertThat(stream.next()).containsEntry("event", "connected");
        assertThat(stream.next()).containsEntry("event", "notification").containsEntry("id", String.valueOf(n2));
        assertThat(stream.next()).containsEntry("id", String.valueOf(n3));
    }

    @Test
    void Last_Event_ID_헤더로도_재전송하고_쿼리가_있으면_쿼리가_우선한다() throws Exception {
        long n1 = save(prefix + "a"), n2 = save(prefix + "a"), n3 = save(prefix + "a");

        SseStream byHeader = subscribe(ticketService.issue(prefix + "a"), null, n2);
        assertThat(byHeader.next()).containsEntry("event", "connected");
        assertThat(byHeader.next()).containsEntry("id", String.valueOf(n3));

        SseStream both = subscribe(ticketService.issue(prefix + "a"), n1, n2);
        assertThat(both.next()).containsEntry("event", "connected");
        assertThat(both.next()).containsEntry("id", String.valueOf(n2));
        assertThat(both.next()).containsEntry("id", String.valueOf(n3));
    }

    // --- 연결 유지 ---

    @Test
    void 하트비트는_주석으로_보낸다() throws Exception {
        SseStream stream = connected(prefix + "a");

        registry.heartbeat();

        assertThat(stream.next()).containsKey("comment").doesNotContainKey("event");
    }

    // --- helpers ---

    private SseStream connected(String username) throws Exception {
        SseStream stream = subscribe(ticketService.issue(username), null, null);
        assertThat(stream.next()).containsEntry("event", "connected");
        return stream;
    }

    private SseStream subscribe(String ticket, Long lastEventId, Long lastEventIdHeader) throws Exception {
        String path = "/api/notifications/subscribe?ticket=" + ticket + (lastEventId == null ? "" : "&lastEventId=" + lastEventId);
        HttpResponse<Stream<String>> response = open(path, lastEventIdHeader);
        assertThat(response.statusCode()).isEqualTo(200);
        return track(new SseStream(response));
    }

    private HttpResponse<Stream<String>> open(String path, Long lastEventIdHeader) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Accept", "text/event-stream").timeout(WAIT);
        if (lastEventIdHeader != null) request.header("Last-Event-ID", String.valueOf(lastEventIdHeader));
        return http.send(request.build(), HttpResponse.BodyHandlers.ofLines());
    }

    private int subscribeStatus(String path) throws Exception {
        HttpResponse<Stream<String>> response = open(path, null);
        response.body().close();
        return response.statusCode();
    }

    private SseStream track(SseStream stream) {
        streams.add(stream);
        return stream;
    }

    private int post(String path, String token, String json) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json))
                .timeout(WAIT).build();
        return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private int accept(long application) throws Exception {
        return post("/api/post/accept", accessToken(author, "ROLE_USER"), "{\"postPositionId\":" + application + "}");
    }

    private int reject(long application) throws Exception {
        return post("/api/post/reject", accessToken(author, "ROLE_USER"), "{\"postPositionId\":" + application + "}");
    }

    private String accessToken(String username, String role) {
        var auth = new UsernamePasswordAuthenticationToken(username, null, List.of(new SimpleGrantedAuthority(role)));
        return jwt.generateToken(auth).getAccessToken();
    }

    private long save(String receiver) {
        return notificationRepository.save(new Notification(receiver, "APPLY", 1L, "재전송 테스트", "project", "backend", "지원자")).getId();
    }

    private Notification only(String receiver) {
        List<Notification> list = notificationRepository.findByReceiverAndIdGreaterThanOrderByIdAsc(receiver, 0L);
        assertThat(list).hasSize(1);
        return list.get(0);
    }

    private PostPosition createPost(int count) {
        post = Post.builder()
                .title("실시간 알림 테스트").author(author).category("project")
                .created(new Date()).delYn("N").likeCount(0).viewCount(0)
                .positions(new ArrayList<>()).techStacks(new ArrayList<>()).likes(new ArrayList<>())
                .build();
        PostPosition position = new PostPosition();
        position.setPost(post);
        position.setRole("backend");
        position.setCount(count);
        position.setStatus("ING");
        post.getPositions().add(position);
        post = postRepository.save(post);
        return post.getPositions().get(0);
    }

    private Member member(String name) {
        Member m = memberRepository.save(Member.builder()
                .username(prefix + name).email(prefix + name + "@test.com").delYn("N").build());
        members.add(m);
        return m;
    }

    private long apply(PostPosition position, Member applicant) {
        PostPositionMember application = new PostPositionMember();
        application.setPostPosition(position);
        application.setMember(applicant);
        application.setStatus("APPLY");
        application.setAppliedAt(LocalDateTime.now());
        return applicationRepository.save(application).getId();
    }

    // 응답 본문을 별도 스레드에서 줄 단위로 읽어 큐에 쌓고, 빈 줄까지를 이벤트 하나로 묶어 꺼낸다
    static class SseStream {
        static final String EOF = "\u0000EOF";
        final HttpResponse<Stream<String>> response;
        final BlockingQueue<String> lines = new LinkedBlockingQueue<>();

        SseStream(HttpResponse<Stream<String>> response) {
            this.response = response;
            Thread reader = new Thread(() -> {
                try {
                    response.body().forEach(lines::add);
                } catch (Exception ignored) {
                } finally {
                    lines.add(EOF);
                }
            });
            reader.setDaemon(true);
            reader.start();
        }

        // 다음 이벤트의 필드들. 스트림이 끝났으면 null, 시간 안에 안 오면 실패
        Map<String, String> next() throws InterruptedException {
            Map<String, String> event = new HashMap<>();
            while (true) {
                String line = lines.poll(WAIT.toMillis(), TimeUnit.MILLISECONDS);
                assertThat(line).as("이벤트를 기다리다 시간 초과").isNotNull();
                if (line.equals(EOF)) {
                    lines.add(EOF);
                    return null;
                }
                if (line.isEmpty()) {
                    if (event.isEmpty()) continue;
                    return event;
                }
                int colon = line.indexOf(':');
                String field = colon == 0 ? "comment" : colon < 0 ? line : line.substring(0, colon);
                String value = colon < 0 ? "" : line.substring(colon + 1).replaceFirst("^ ", "");
                event.merge(field, value, (prev, v) -> prev + "\n" + v);
            }
        }

        void close() {
            response.body().close();
        }
    }
}
