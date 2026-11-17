package org.example.project;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.project.member.entity.Member;
import org.example.project.member.repository.MemberRepository;
import org.example.project.notification.service.SseEmitterRegistry;
import org.example.project.post.dto.ApplyPositionRequestDto;
import org.example.project.post.entity.Post;
import org.example.project.post.entity.PostPosition;
import org.example.project.post.entity.PostPositionMember;
import org.example.project.post.repositoty.PostPositionMemberRepository;
import org.example.project.post.repositoty.PostRepository;
import org.example.project.post.service.PositionService;
import org.example.project.security.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 팀 채팅 저장·조회·전달 대상. 커밋 후 전달을 보려고 롤백 없이 돌리고 직접 정리한다.
// 실제 SSE로 chat 이벤트가 id 없이 오는지는 NotificationStreamTest가 본다
@SpringBootTest(properties = {
        "jwt.secret=dGVzdC1zZWNyZXQta2V5LWZvci1zZWN1cml0eS1hY2Nlc3MtdGVzdA==",
        "spring.data.redis.usessl=false",
        "KAKAO_CLIENT_ID=test", "AWS_ACCESS_KEY_ID=test", "AWS_SECRET_ACCESS_KEY=test",
        "AWS_REGION=ap-northeast-2", "AWS_S3_BUCKET_NAME=test",
})
@AutoConfigureMockMvc
class ChatTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtTokenProvider jwt;
    @Autowired PositionService positionService;
    @Autowired PostRepository postRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired PostPositionMemberRepository applicationRepository;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean SseEmitterRegistry registry;

    final String prefix = "ch-" + UUID.randomUUID().toString().substring(0, 8) + "-";
    final List<Member> members = new ArrayList<>();
    final List<Post> posts = new ArrayList<>();
    Member author;
    Member a;
    Member b;
    Long teamId;

    @BeforeEach
    void setUp() {
        author = member("author");
        a = member("a");
        b = member("b");
        teamId = createTeam(author, a, b);
    }

    @AfterEach
    void cleanUp() {
        posts.forEach(p -> postRepository.findById(p.getId()).ifPresent(postRepository::delete));
        TeamCleanup.deleteFor(jdbc, members);
        memberRepository.deleteAll(members);
        jdbc.update("DELETE FROM notification WHERE receiver LIKE ?", prefix + "%");
    }

    @Test
    void after는_빠짐없이_오름차순이고_before는_이전_대화를_내림차순으로_준다() throws Exception {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 5; i++) ids.add(send(i % 2 == 0 ? a : b, "메시지" + i).get("id").asLong());

        JsonNode after = body(call(get(messages() + "?after=" + ids.get(1) + "&limit=2"), a).andExpect(status().isOk()));
        assertThat(after.findValuesAsText("content")).containsExactly("메시지2", "메시지3");
        JsonNode rest = body(call(get(messages() + "?after=" + after.get(1).get("id").asLong()), a));
        assertThat(rest.findValuesAsText("content")).containsExactly("메시지4");

        JsonNode latest = body(call(get(messages() + "?limit=2"), b));
        assertThat(latest.findValuesAsText("content")).containsExactly("메시지4", "메시지3");
        JsonNode older = body(call(get(messages() + "?before=" + ids.get(3)), b));
        assertThat(older.findValuesAsText("content")).containsExactly("메시지2", "메시지1", "메시지0");
        assertThat(older.get(0).get("senderName").asText()).isEqualTo(a.getNickname());
    }

    @Test
    void 커밋된_메시지를_지금_활성_팀원에게만_보낸다() throws Exception {
        call(delete("/api/teams/" + teamId + "/members/me"), b).andExpect(status().isOk());

        send(a, "안녕하세요");

        verify(registry).sendChat(eq(author.getUsername()), any());
        verify(registry).sendChat(eq(a.getUsername()), any()); // 보낸 사람의 다른 탭도 받는다
        verify(registry, never()).sendChat(eq(b.getUsername()), any());
    }

    @Test
    void 팀원이_아니면_404이고_빈_메시지는_400이고_끝난_팀은_읽기만_된다() throws Exception {
        Member outsider = member("outsider");
        call(get(messages()), outsider).andExpect(status().isNotFound());
        call(post(messages()).contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"hi\"}"), outsider)
                .andExpect(status().isNotFound());
        call(post(messages()).contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"  \"}"), a)
                .andExpect(status().isBadRequest());
        call(post(messages()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("content", "x".repeat(1001)))), a)
                .andExpect(status().isBadRequest());

        send(a, "마지막 인사");
        call(post("/api/teams/" + teamId + "/disband"), author).andExpect(status().isOk());
        call(post(messages()).contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"hi\"}"), a)
                .andExpect(status().isConflict());
        assertThat(body(call(get(messages()), a)).findValuesAsText("content")).containsExactly("마지막 인사");
    }

    private JsonNode send(Member as, String content) throws Exception {
        return body(call(post(messages()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("content", content))), as).andExpect(status().isOk()));
    }

    private String messages() {
        return "/api/teams/" + teamId + "/messages";
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    // leader가 쓴 글에 joiners를 승인해 팀을 만든다
    private Long createTeam(Member leader, Member... joiners) {
        Post post = Post.builder()
                .title("업무 테스트").author(leader.getUsername()).category("project").status("ING")
                .created(new Date()).delYn("N").likeCount(0).viewCount(0)
                .positions(new ArrayList<>()).techStacks(new ArrayList<>()).likes(new ArrayList<>())
                .build();
        PostPosition position = new PostPosition();
        position.setPost(post);
        position.setRole("backend");
        position.setCount(joiners.length);
        position.setStatus("ING");
        post.getPositions().add(position);
        post = postRepository.save(post);
        posts.add(post);
        for (Member joiner : joiners) {
            PostPositionMember application = new PostPositionMember();
            application.setPostPosition(post.getPositions().get(0));
            application.setMember(joiner);
            application.setStatus("APPLY");
            application.setAppliedAt(LocalDateTime.now());
            ApplyPositionRequestDto dto = new ApplyPositionRequestDto();
            dto.setPostPositionId(applicationRepository.save(application).getId());
            positionService.acceptPosition(dto, leader.getUsername());
        }
        return jdbc.queryForObject("SELECT id FROM team WHERE post_id = ?", Long.class, post.getId());
    }

    private ResultActions call(MockHttpServletRequestBuilder request, Member as) throws Exception {
        var auth = new UsernamePasswordAuthenticationToken(as.getUsername(), null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        return mvc.perform(request.header("Authorization", "Bearer " + jwt.generateToken(auth).getAccessToken()));
    }

    private Member member(String name) {
        Member m = memberRepository.save(Member.builder()
                .username(prefix + name).nickname(prefix + name).email(prefix + name + "@test.com").delYn("N").build());
        members.add(m);
        return m;
    }

}
