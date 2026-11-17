package org.example.project;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.project.member.entity.Member;
import org.example.project.member.repository.MemberRepository;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 팀 업무 CRUD·낙관적 락·조건부 상태 변경·활동 기록·댓글·업무 알림. 동시 요청을 재현해야 해서 롤백 없이 돌리고 직접 정리한다.
@SpringBootTest(properties = {
        "jwt.secret=dGVzdC1zZWNyZXQta2V5LWZvci1zZWN1cml0eS1hY2Nlc3MtdGVzdA==",
        "spring.data.redis.usessl=false",
        "KAKAO_CLIENT_ID=test", "AWS_ACCESS_KEY_ID=test", "AWS_SECRET_ACCESS_KEY=test",
        "AWS_REGION=ap-northeast-2", "AWS_S3_BUCKET_NAME=test",
})
@AutoConfigureMockMvc
class TaskTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtTokenProvider jwt;
    @Autowired PositionService positionService;
    @Autowired PostRepository postRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired PostPositionMemberRepository applicationRepository;
    @Autowired JdbcTemplate jdbc;

    final String prefix = "tk-" + UUID.randomUUID().toString().substring(0, 8) + "-";
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
    void 팀원은_업무를_만들고_고치고_옮기며_활동이_남는다() throws Exception {
        JsonNode task = create(a, "API 명세", b.getId());
        assertThat(task.get("status").asText()).isEqualTo("TODO");
        assertThat(task.get("deletable").asBoolean()).isTrue();

        JsonNode edited = body(call(patch(taskUrl(task)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("title", "API 명세 v2", "version", task.get("version").asLong()))), b)
                .andExpect(status().isOk()));
        assertThat(edited.get("assigneeMemberId").isNull()).isTrue(); // 칸 전체를 바꾼다
        assertThat(edited.get("version").asLong()).isGreaterThan(task.get("version").asLong());

        call(patch(taskUrl(task) + "/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"from\":\"TODO\",\"to\":\"IN_PROGRESS\"}"), b).andExpect(status().isOk());

        JsonNode activities = body(call(get("/api/teams/" + teamId + "/activities?taskId=" + task.get("taskId").asLong()), a)
                .andExpect(status().isOk()));
        assertThat(activities.findValuesAsText("type")).containsExactly("TASK_STATUS", "TASK_UPDATED", "TASK_CREATED");
        assertThat(activities.get(0).get("detail").get("to").asText()).isEqualTo("IN_PROGRESS");
        assertThat(activities.get(0).get("actorName").asText()).isEqualTo(b.getNickname());
    }

    @Test
    void 팀원이_아니면_404이고_다른_팀의_업무는_찾지_못한다() throws Exception {
        JsonNode task = create(a, "숨은 업무", null);
        Member outsider = member("outsider");
        call(get("/api/teams/" + teamId + "/tasks"), outsider).andExpect(status().isNotFound());
        call(post("/api/teams/" + teamId + "/tasks").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"x\"}"), outsider)
                .andExpect(status().isNotFound());

        // outsider가 팀장인 다른 팀 경로에 이 팀의 업무 id를 넣어도 못 찾는다
        Long otherTeam = createTeam(outsider, member("c"));
        call(get("/api/teams/" + otherTeam + "/tasks/" + task.get("taskId").asLong()), outsider).andExpect(status().isNotFound());
        call(delete("/api/teams/" + otherTeam + "/tasks/" + task.get("taskId").asLong()), outsider).andExpect(status().isNotFound());
    }

    @Test
    void 오래된_version으로_고치거나_동시에_고치면_한쪽은_409() throws Exception {
        JsonNode task = create(a, "충돌", null);
        long version = task.get("version").asLong();
        String url = taskUrl(task);

        AtomicInteger ok = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();
        runConcurrently(2, i -> {
            int code = call(patch(url).contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("title", "수정" + i, "version", version))), i == 0 ? a : b)
                    .andReturn().getResponse().getStatus();
            (code == 200 ? ok : conflict).incrementAndGet();
            assertThat(code).isIn(200, 409);
        });
        assertThat(ok.get()).isEqualTo(1);
        assertThat(conflict.get()).isEqualTo(1);

        // 상태 변경도 version을 올리므로 그 전에 읽은 값으로는 고칠 수 없다
        long current = jdbc.queryForObject("SELECT version FROM task WHERE id = ?", Long.class, task.get("taskId").asLong());
        call(patch(url + "/status").contentType(MediaType.APPLICATION_JSON).content("{\"from\":\"TODO\",\"to\":\"DONE\"}"), a)
                .andExpect(status().isOk());
        call(patch(url).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("title", "늦은 수정", "version", current))), a).andExpect(status().isConflict());
    }

    @Test
    void 같은_상태_이동을_연타해도_한_번만_바뀌고_기록도_하나다() throws Exception {
        JsonNode task = create(a, "연타", null);
        Long taskId = task.get("taskId").asLong();

        runConcurrently(5, i -> call(patch(taskUrl(task) + "/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"from\":\"TODO\",\"to\":\"IN_PROGRESS\"}"), a));

        assertThat(jdbc.queryForObject("SELECT status FROM task WHERE id = ?", String.class, taskId)).isEqualTo("IN_PROGRESS");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM team_activity WHERE target_type = 'TASK' AND target_id = ? AND type = 'TASK_STATUS'",
                Long.class, taskId)).isEqualTo(1);
    }

    @Test
    void 업무는_팀장과_만든_사람만_지운다() throws Exception {
        JsonNode byA = create(a, "a의 업무", null);
        JsonNode byB = create(b, "b의 업무", null);

        call(delete(taskUrl(byA)), b).andExpect(status().isForbidden());
        call(delete(taskUrl(byA)), a).andExpect(status().isOk());
        call(delete(taskUrl(byB)), author).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM task WHERE team_id = ?", Long.class, teamId)).isZero();
    }

    @Test
    void 담당자는_팀원이어야_하고_탈퇴하면_담당이_비워진다() throws Exception {
        call(post("/api/teams/" + teamId + "/tasks").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("title", "x", "assigneeMemberId", member("outsider").getId()))), a)
                .andExpect(status().isBadRequest());

        JsonNode task = create(a, "b 담당", b.getId());
        call(delete("/api/teams/" + teamId + "/members/me"), b).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT assignee_member_id FROM task WHERE id = ?", Long.class, task.get("taskId").asLong())).isNull();
        assertThat(jdbc.queryForObject("SELECT status FROM team_member WHERE team_id = ? AND member_id = ?", String.class, teamId, b.getId()))
                .isEqualTo("LEFT"); // 담당 비우기가 탈퇴 변경을 지우지 않는다
    }

    @Test
    void 완료된_팀의_업무는_읽기만_된다() throws Exception {
        JsonNode task = create(a, "끝난 팀", null);
        call(post("/api/teams/" + teamId + "/start"), author).andExpect(status().isOk());
        call(post("/api/teams/" + teamId + "/complete"), author).andExpect(status().isOk());

        call(post("/api/teams/" + teamId + "/tasks").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"x\"}"), a)
                .andExpect(status().isConflict());
        call(patch(taskUrl(task) + "/status").contentType(MediaType.APPLICATION_JSON).content("{\"from\":\"TODO\",\"to\":\"DONE\"}"), a)
                .andExpect(status().isConflict());
        call(delete(taskUrl(task)), author).andExpect(status().isConflict());
        call(get("/api/teams/" + teamId + "/tasks"), a).andExpect(status().isOk());

        // 팀 상태 변경도 활동에 남는다
        JsonNode activities = body(call(get("/api/teams/" + teamId + "/activities"), a));
        assertThat(activities.findValuesAsText("type")).startsWith("TEAM_COMPLETED", "TEAM_STARTED");
    }

    @Test
    void 담당자로_새로_정해질_때만_알린다() throws Exception {
        JsonNode task = create(a, "b에게", b.getId());
        create(a, "스스로 맡음", a.getId());
        // 같은 담당자로 다시 저장하면 알리지 않고, 담당이 바뀌면 새 담당자에게 알린다
        JsonNode same = body(call(patch(taskUrl(task)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
                Map.of("title", "b에게", "assigneeMemberId", b.getId(), "version", task.get("version").asLong()))), a));
        call(patch(taskUrl(task)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
                Map.of("title", "b에게", "assigneeMemberId", author.getId(), "version", same.get("version").asLong()))), a)
                .andExpect(status().isOk());

        assertThat(notifications(b)).containsExactly("TASK_ASSIGNED");
        assertThat(notifications(a)).isEmpty();
        assertThat(notifications(author)).containsExactly("TASK_ASSIGNED");
        assertThat(jdbc.queryForObject("SELECT actor FROM notification WHERE receiver = ? AND type = 'TASK_ASSIGNED'", String.class, b.getUsername()))
                .isEqualTo(a.getNickname());
    }

    @Test
    void 댓글은_업무를_만든_사람과_담당자에게_알리고_쓴_사람과_팀장만_지운다() throws Exception {
        JsonNode task = create(a, "댓글 업무", b.getId());
        String comments = taskUrl(task) + "/comments";
        jdbc.update("DELETE FROM notification WHERE receiver LIKE ?", prefix + "%"); // 담당 알림은 빼고 본다

        JsonNode byB = body(call(post(comments).contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"확인했습니다\"}"), b)
                .andExpect(status().isOk()));
        JsonNode byAuthor = body(call(post(comments).contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"팀장 의견\"}"), author)
                .andExpect(status().isOk()));
        call(post(comments).contentType(MediaType.APPLICATION_JSON).content("{\"content\":\" \"}"), b).andExpect(status().isBadRequest());

        assertThat(notifications(a)).containsExactly("TASK_COMMENT", "TASK_COMMENT");
        assertThat(notifications(b)).containsExactly("TASK_COMMENT"); // 자기 댓글은 받지 않는다
        assertThat(notifications(author)).isEmpty();

        JsonNode list = body(call(get(comments), a).andExpect(status().isOk()));
        assertThat(list.findValuesAsText("content")).containsExactly("확인했습니다", "팀장 의견");
        assertThat(list.get(0).get("deletable").asBoolean()).isFalse();

        call(delete(comments + "/" + byAuthor.get("commentId").asLong()), b).andExpect(status().isForbidden());
        call(delete(comments + "/" + byB.get("commentId").asLong()), author).andExpect(status().isOk());
        call(delete(comments + "/" + byAuthor.get("commentId").asLong()), author).andExpect(status().isOk());
        call(get(comments), member("outsider")).andExpect(status().isNotFound());
    }

    @Test
    void 다른_업무_경로로는_댓글을_못_지우고_업무를_지우면_댓글도_지워진다() throws Exception {
        JsonNode task = create(a, "원래 업무", null);
        JsonNode other = create(a, "다른 업무", null);
        JsonNode comment = body(call(post(taskUrl(task) + "/comments").contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"댓글\"}"), a));

        call(delete(taskUrl(other) + "/comments/" + comment.get("commentId").asLong()), author).andExpect(status().isNotFound());
        call(delete(taskUrl(task)), a).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM task_comment WHERE id = ?", Long.class, comment.get("commentId").asLong()))
                .isZero();
    }

    private List<String> notifications(Member m) {
        return jdbc.queryForList("SELECT type FROM notification WHERE receiver = ? AND type LIKE 'TASK_%' ORDER BY id",
                String.class, m.getUsername());
    }

    // --- helpers ---

    private JsonNode create(Member as, String title, Long assignee) throws Exception {
        var request = new java.util.HashMap<String, Object>(Map.of("title", title));
        if (assignee != null) request.put("assigneeMemberId", assignee);
        return body(call(post("/api/teams/" + teamId + "/tasks").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(request)), as).andExpect(status().isOk()));
    }

    private String taskUrl(JsonNode task) {
        return "/api/teams/" + teamId + "/tasks/" + task.get("taskId").asLong();
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

    interface Work { void run(int i) throws Exception; }

    // 실패한 단언도 놓치지 않도록 작업 안의 예외를 모아 다시 던진다
    private void runConcurrently(int threads, Work work) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            int idx = i;
            futures.add(pool.submit(() -> {
                start.await();
                work.run(idx);
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) f.get(30, TimeUnit.SECONDS);
        pool.shutdown();
    }
}
