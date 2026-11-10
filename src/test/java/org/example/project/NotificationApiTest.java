package org.example.project;

import org.example.project.notification.entity.Notification;
import org.example.project.notification.repository.NotificationRepository;
import org.example.project.security.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 공용 DB를 쓰므로 수신자 이름에 고유 접두어를 붙이고, 그 수신자의 알림만 만들고 지운다.
@SpringBootTest(properties = {
        "jwt.secret=dGVzdC1zZWNyZXQta2V5LWZvci1zZWN1cml0eS1hY2Nlc3MtdGVzdA==",
        "spring.data.redis.usessl=false",
        "KAKAO_CLIENT_ID=test", "AWS_ACCESS_KEY_ID=test", "AWS_SECRET_ACCESS_KEY=test",
        "AWS_REGION=ap-northeast-2", "AWS_S3_BUCKET_NAME=test",
})
@AutoConfigureMockMvc
class NotificationApiTest {

    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider jwt;
    @Autowired NotificationRepository notificationRepository;

    final String prefix = "nt-" + UUID.randomUUID().toString().substring(0, 8) + "-";
    final String me = prefix + "me";
    final String other = prefix + "other";

    @AfterEach
    void cleanUp() {
        for (String receiver : List.of(me, other)) {
            notificationRepository.deleteAll(notificationRepository.findByReceiverAndIdGreaterThanOrderByIdAsc(receiver, 0L));
        }
    }

    @Test
    void 토큰_없이_알림_API는_401() throws Exception {
        mvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/notifications/unread-count")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/notifications/1/read")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/notifications/read-all")).andExpect(status().isUnauthorized());
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

    // --- helpers ---

    private String bearer(String username) {
        var auth = new UsernamePasswordAuthenticationToken(username, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        return "Bearer " + jwt.generateToken(auth).getAccessToken();
    }

    private Notification save(String receiver) {
        return notificationRepository.save(new Notification(receiver, "APPLY", 1L, "알림 테스트", "project", "backend", "지원자"));
    }
}
