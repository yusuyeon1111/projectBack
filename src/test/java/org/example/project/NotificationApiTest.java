package org.example.project;

import org.example.project.member.entity.Member;
import org.example.project.member.repository.MemberRepository;
import org.example.project.notification.entity.Notification;
import org.example.project.notification.repository.NotificationRepository;
import org.example.project.notification.service.NotificationCreatedEvent;
import org.example.project.post.entity.Post;
import org.example.project.post.entity.PostPosition;
import org.example.project.post.entity.PostPositionMember;
import org.example.project.post.repositoty.PostPositionMemberRepository;
import org.example.project.post.repositoty.PostRepository;
import org.example.project.security.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 공용 DB를 쓰므로 회원·수신자 이름에 고유 접두어를 붙이고, 만든 행은 직접 지운다.
@SpringBootTest(properties = {
        "jwt.secret=dGVzdC1zZWNyZXQta2V5LWZvci1zZWN1cml0eS1hY2Nlc3MtdGVzdA==",
        "spring.data.redis.usessl=false",
        "KAKAO_CLIENT_ID=test", "AWS_ACCESS_KEY_ID=test", "AWS_SECRET_ACCESS_KEY=test",
        "AWS_REGION=ap-northeast-2", "AWS_S3_BUCKET_NAME=test",
})
@AutoConfigureMockMvc
@RecordApplicationEvents
class NotificationApiTest {

    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider jwt;
    @Autowired NotificationRepository notificationRepository;
    @Autowired PostRepository postRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired PostPositionMemberRepository applicationRepository;
    @Autowired ApplicationEvents events;

    final String prefix = "nt-" + UUID.randomUUID().toString().substring(0, 8) + "-";
    final String me = prefix + "me";       // 글 작성자
    final String other = prefix + "other"; // 신청자
    final List<Member> members = new ArrayList<>();
    Post post;

    @AfterEach
    void cleanUp() {
        for (String receiver : List.of(me, other, prefix + "second")) {
            notificationRepository.deleteAll(notificationRepository.findByReceiverAndIdGreaterThanOrderByIdAsc(receiver, 0L));
        }
        if (post != null) postRepository.findById(post.getId()).ifPresent(postRepository::delete);
        memberRepository.deleteAll(members);
    }

    // --- 조회·읽음 API ---

    @Test
    void 토큰_없이_알림_API는_401() throws Exception {
        mvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/notifications/unread-count")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/notifications/1/read")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/notifications/read-all")).andExpect(status().isUnauthorized());
    }

    @Test
    void 운영_프론트에서_PATCH_preflight가_허용된다() throws Exception {
        mvc.perform(options("/api/notifications/read-all")
                        .header("Origin", "https://teamhaeyo.kro.kr")
                        .header("Access-Control-Request-Method", "PATCH"))
                .andExpect(status().isOk());
    }

    @Test
    void 목록은_본인_알림만_최신순이고_cursor보다_작은_id만() throws Exception {
        long first = save(me).getId();
        long second = save(me).getId();
        long third = save(me).getId();
        save(other);

        mvc.perform(get("/api/notifications").header("Authorization", bearer(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains((int) third, (int) second, (int) first)))
                .andExpect(jsonPath("$[0].type").value("APPLY"))
                .andExpect(jsonPath("$[0].postTitle").value("알림 테스트"))
                .andExpect(jsonPath("$[0].postCategory").value("project"))
                .andExpect(jsonPath("$[0].role").value("backend"))
                .andExpect(jsonPath("$[0].actor").value("지원자"))
                .andExpect(jsonPath("$[0].read").value(false))
                .andExpect(jsonPath("$[0].createdAt").exists());
        mvc.perform(get("/api/notifications").param("cursor", String.valueOf(third)).param("size", "1")
                        .header("Authorization", bearer(me)))
                .andExpect(jsonPath("$[*].id", contains((int) second)));
        mvc.perform(get("/api/notifications").param("cursor", String.valueOf(first)).header("Authorization", bearer(me)))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void 하나_읽음과_모두_읽음이_안읽은_개수에_반영된다() throws Exception {
        long first = save(me).getId();
        save(me);
        save(me);
        save(other);

        mvc.perform(get("/api/notifications/unread-count").header("Authorization", bearer(me)))
                .andExpect(jsonPath("$.count").value(3));
        mvc.perform(patch("/api/notifications/" + first + "/read").header("Authorization", bearer(me)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/notifications/unread-count").header("Authorization", bearer(me)))
                .andExpect(jsonPath("$.count").value(2));
        mvc.perform(patch("/api/notifications/read-all").header("Authorization", bearer(me)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/notifications/unread-count").header("Authorization", bearer(me)))
                .andExpect(jsonPath("$.count").value(0));
        mvc.perform(get("/api/notifications").header("Authorization", bearer(me)))
                .andExpect(jsonPath("$[0].read").value(true));
        // 남의 알림은 건드리지 않는다
        mvc.perform(get("/api/notifications/unread-count").header("Authorization", bearer(other)))
                .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    void 남의_알림은_읽음_처리할_수_없다() throws Exception {
        long othersId = save(other).getId();

        mvc.perform(patch("/api/notifications/" + othersId + "/read").header("Authorization", bearer(me)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/notifications/unread-count").header("Authorization", bearer(other)))
                .andExpect(jsonPath("$.count").value(1));
    }

    // --- 신청·승인·거절 시 생성 ---

    @Test
    void 신청하면_작성자에게_안읽은_알림이_생기고_이벤트가_발행된다() throws Exception {
        PostPosition position = createPost(1);
        member(other, "지원자닉");

        mvc.perform(post("/api/post/apply").header("Authorization", bearer(other))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"positionId\":" + position.getId() + "}"))
                .andExpect(status().isOk());

        List<Notification> saved = notificationsOf(me);
        assertThat(saved).hasSize(1);
        mvc.perform(get("/api/notifications").header("Authorization", bearer(me)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].type").value("APPLY"))
                .andExpect(jsonPath("$[0].postId").value(post.getId()))
                .andExpect(jsonPath("$[0].postTitle").value("알림 생성 테스트"))
                .andExpect(jsonPath("$[0].postCategory").value("study"))
                .andExpect(jsonPath("$[0].role").value("backend"))
                .andExpect(jsonPath("$[0].actor").value("지원자닉"))
                .andExpect(jsonPath("$[0].read").value(false));

        List<NotificationCreatedEvent> published = events.stream(NotificationCreatedEvent.class).toList();
        assertThat(published).hasSize(1);
        assertThat(published.get(0).receiver()).isEqualTo(me);
        assertThat(published.get(0).notification().id()).isEqualTo(saved.get(0).getId());
        assertThat(published.get(0).notification().type()).isEqualTo("APPLY");
    }

    @Test
    void 닉네임이_없으면_신청자_아이디로_표시한다() throws Exception {
        PostPosition position = createPost(1);
        member(other, null);

        mvc.perform(post("/api/post/apply").header("Authorization", bearer(other))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"positionId\":" + position.getId() + "}"))
                .andExpect(status().isOk());

        assertThat(notificationsOf(me)).extracting(Notification::getActor).containsExactly(other);
    }

    @Test
    void 자기_글에_신청하면_알림을_만들지_않는다() throws Exception {
        PostPosition position = createPost(1);
        member(me, null);

        mvc.perform(post("/api/post/apply").header("Authorization", bearer(me))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"positionId\":" + position.getId() + "}"))
                .andExpect(status().isOk());

        assertThat(notificationsOf(me)).isEmpty();
        assertThat(events.stream(NotificationCreatedEvent.class)).isEmpty();
    }

    @Test
    void 승인하면_신청자에게_ACCEPT_알림() throws Exception {
        PostPosition position = createPost(1);
        long application = apply(position, member(other, "지원자닉"));

        mvc.perform(post("/api/post/accept").header("Authorization", bearer(me))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"postPositionId\":" + application + "}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/notifications").header("Authorization", bearer(other)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].type").value("ACCEPT"))
                .andExpect(jsonPath("$[0].postId").value(post.getId()))
                .andExpect(jsonPath("$[0].role").value("backend"))
                .andExpect(jsonPath("$[0].actor").value(nullValue()));
        assertThat(events.stream(NotificationCreatedEvent.class)).extracting(NotificationCreatedEvent::receiver)
                .containsExactly(other);
    }

    @Test
    void 거절하면_신청자에게_REJECT_알림() throws Exception {
        PostPosition position = createPost(1);
        long application = apply(position, member(other, "지원자닉"));

        mvc.perform(post("/api/post/reject").header("Authorization", bearer(me))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"postPositionId\":" + application + "}"))
                .andExpect(status().isOk());

        assertThat(notificationsOf(other)).extracting(Notification::getType).containsExactly("REJECT");
    }

    @Test
    void 정원이_차서_409로_거절된_승인은_알림을_남기지_않는다() throws Exception {
        PostPosition position = createPost(1);
        long first = apply(position, member(other, null));
        long second = apply(position, member(prefix + "second", null));

        mvc.perform(post("/api/post/accept").header("Authorization", bearer(me))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"postPositionId\":" + first + "}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/post/accept").header("Authorization", bearer(me))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"postPositionId\":" + second + "}"))
                .andExpect(status().isConflict());

        assertThat(notificationsOf(other)).hasSize(1);
        assertThat(notificationsOf(prefix + "second")).isEmpty();
    }

    // --- helpers ---

    private String bearer(String username) {
        var auth = new UsernamePasswordAuthenticationToken(username, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        return "Bearer " + jwt.generateToken(auth).getAccessToken();
    }

    private Notification save(String receiver) {
        return notificationRepository.save(new Notification(receiver, "APPLY", 1L, "알림 테스트", "project", "backend", "지원자"));
    }

    private List<Notification> notificationsOf(String receiver) {
        return notificationRepository.findByReceiverAndIdGreaterThanOrderByIdAsc(receiver, 0L);
    }

    private PostPosition createPost(int count) {
        post = Post.builder()
                .title("알림 생성 테스트").author(me).category("study")
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

    private Member member(String username, String nickname) {
        Member m = memberRepository.save(Member.builder()
                .username(username).nickname(nickname).email(username + "@test.com").delYn("N").build());
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
}
