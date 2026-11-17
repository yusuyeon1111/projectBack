package org.example.project;

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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 첫 승인 때 팀이 생기는 흐름. 동시 승인을 재현해야 해서 롤백 없이 돌리고 직접 정리한다.
@SpringBootTest(properties = {
        "jwt.secret=dGVzdC1zZWNyZXQta2V5LWZvci1zZWN1cml0eS1hY2Nlc3MtdGVzdA==",
        "spring.data.redis.usessl=false",
        "KAKAO_CLIENT_ID=test", "AWS_ACCESS_KEY_ID=test", "AWS_SECRET_ACCESS_KEY=test",
        "AWS_REGION=ap-northeast-2", "AWS_S3_BUCKET_NAME=test",
})
@AutoConfigureMockMvc
class TeamFormationTest {

    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider jwt;
    @Autowired PositionService positionService;
    @Autowired PostRepository postRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired PostPositionMemberRepository applicationRepository;
    @Autowired JdbcTemplate jdbc;

    final String prefix = "tf-" + UUID.randomUUID().toString().substring(0, 8) + "-";
    final List<Member> members = new ArrayList<>();
    Post post;
    Member author;

    @AfterEach
    void cleanUp() {
        if (post != null) postRepository.findById(post.getId()).ifPresent(postRepository::delete);
        TeamCleanup.deleteFor(jdbc, members);
        memberRepository.deleteAll(members);
        jdbc.update("DELETE FROM notification WHERE receiver LIKE ?", prefix + "%");
    }

    @Test
    void 첫_승인_때_작성자를_팀장으로_팀이_생긴다() {
        List<PostPosition> positions = createPost(2);
        Long app = apply(positions.get(0), member("a"));

        accept(app);

        assertThat(jdbc.queryForObject("SELECT status FROM team WHERE post_id = ?", String.class, post.getId())).isEqualTo("FORMING");
        assertThat(jdbc.queryForObject("SELECT leader_member_id FROM team WHERE post_id = ?", Long.class, post.getId())).isEqualTo(author.getId());
        assertThat(activeMemberIds()).containsExactlyInAnyOrder(author.getId(), members.get(1).getId());
    }

    @Test
    void 동시에_여러_명을_승인해도_팀은_하나이고_정원을_넘지_않는다() throws Exception {
        PostPosition position = createPost(3).get(0);
        List<Long> apps = new ArrayList<>();
        for (int i = 0; i < 10; i++) apps.add(apply(position, member("c" + i)));

        runConcurrently(10, i -> accept(apps.get(i)));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM team WHERE post_id = ?", Long.class, post.getId())).isEqualTo(1);
        assertThat(activeMemberIds()).hasSize(3 + 1); // 정원 3 + 팀장
    }

    @Test
    void 같은_신청을_동시에_승인해도_팀원은_한_번만_들어간다() throws Exception {
        Long app = apply(createPost(5).get(0), member("dup"));

        runConcurrently(5, i -> accept(app));

        assertThat(activeMemberIds()).hasSize(2);
    }

    @Test
    void 작성자_회원을_찾지_못하면_승인까지_롤백된다() {
        Long app = apply(createPost(1).get(0), member("a"));
        jdbc.update("UPDATE post SET author = ? WHERE id = ?", prefix + "ghost", post.getId());

        assertThatThrownBy(() -> positionService.acceptPosition(acceptDto(app), prefix + "ghost"))
                .isInstanceOf(IllegalStateException.class);

        assertThat(applicationRepository.findById(app).orElseThrow().getStatus()).isEqualTo("APPLY");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM team WHERE post_id = ?", Long.class, post.getId())).isZero();
    }

    @Test
    void 이미_팀원인_사람은_같은_글의_다른_포지션에_신청하거나_승인될_수_없다() throws Exception {
        List<PostPosition> positions = createPost(1, 1, 1);
        Member a = member("a");
        Long first = apply(positions.get(0), a);
        Long second = apply(positions.get(1), a); // 팀이 생기기 전에 넣어 둔 두 번째 신청
        accept(first);

        assertThatThrownBy(() -> accept(second)).isInstanceOf(IllegalStateException.class);
        assertThat(applicationRepository.findById(second).orElseThrow().getStatus()).isEqualTo("APPLY");

        mvc.perform(post("/api/post/apply").header("Authorization", bearer(a.getUsername()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"positionId\":" + positions.get(2).getId() + "}"))
                .andExpect(status().isConflict());
    }

    @Test
    void 신청_승인_거절이_이력으로_남는다() throws Exception {
        PostPosition position = createPost(2).get(0);
        Member a = member("a");
        Member b = member("b");
        for (Member m : List.of(a, b)) {
            mvc.perform(post("/api/post/apply").header("Authorization", bearer(m.getUsername()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"positionId\":" + position.getId() + "}"))
                    .andExpect(status().isOk());
        }
        Long appA = applicationId(position, a);
        Long appB = applicationId(position, b);

        accept(appA);
        positionService.rejectPosition(acceptDto(appB), author.getUsername());

        assertThat(events(appA)).containsExactly("APPLY", "ACCEPT");
        assertThat(events(appB)).containsExactly("APPLY", "REJECT");
    }

    @Test
    void 팀원만_팀을_볼_수_있고_관리자도_예외가_아니다() throws Exception {
        Member a = member("a");
        accept(apply(createPost(1).get(0), a));
        Long teamId = jdbc.queryForObject("SELECT id FROM team WHERE post_id = ?", Long.class, post.getId());

        mvc.perform(get("/api/teams/" + teamId).header("Authorization", bearer(a.getUsername())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members.length()").value(2));
        mvc.perform(get("/api/teams/my").header("Authorization", bearer(author.getUsername())))
                .andExpect(jsonPath("$[0].teamId").value(teamId))
                .andExpect(jsonPath("$[0].leader").value(true));

        mvc.perform(get("/api/teams/" + teamId).header("Authorization", bearer(member("outsider").getUsername())))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/teams/" + teamId).header("Authorization", bearer(prefix + "admin", "ROLE_ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void 팀이_있는_글은_삭제할_수_없다() throws Exception {
        accept(apply(createPost(1).get(0), member("a")));

        mvc.perform(delete("/api/post/delete/" + post.getId()).header("Authorization", bearer(author.getUsername())))
                .andExpect(status().isConflict());
        assertThat(postRepository.existsById(post.getId())).isTrue();
    }

    // --- helpers ---

    private List<PostPosition> createPost(int... counts) {
        author = member("author");
        post = Post.builder()
                .title("팀 테스트").author(author.getUsername()).category("project")
                .created(new Date()).delYn("N").likeCount(0).viewCount(0)
                .positions(new ArrayList<>()).techStacks(new ArrayList<>()).likes(new ArrayList<>())
                .build();
        for (int count : counts) {
            PostPosition position = new PostPosition();
            position.setPost(post);
            position.setRole("backend");
            position.setCount(count);
            position.setStatus("ING");
            post.getPositions().add(position);
        }
        post = postRepository.save(post);
        return post.getPositions();
    }

    private Long apply(PostPosition position, Member m) {
        PostPositionMember application = new PostPositionMember();
        application.setPostPosition(position);
        application.setMember(m);
        application.setStatus("APPLY");
        application.setAppliedAt(LocalDateTime.now());
        return applicationRepository.save(application).getId();
    }

    private void accept(Long applicationId) {
        positionService.acceptPosition(acceptDto(applicationId), author.getUsername());
    }

    private ApplyPositionRequestDto acceptDto(Long applicationId) {
        ApplyPositionRequestDto dto = new ApplyPositionRequestDto();
        dto.setPostPositionId(applicationId);
        return dto;
    }

    private Long applicationId(PostPosition position, Member m) {
        return jdbc.queryForObject("SELECT id FROM post_position_member WHERE post_position_id = ? AND member_id = ?",
                Long.class, position.getId(), m.getId());
    }

    private List<Long> activeMemberIds() {
        return jdbc.queryForList("SELECT tm.member_id FROM team_member tm JOIN team t ON t.id = tm.team_id "
                + "WHERE t.post_id = ? AND tm.status = 'ACTIVE'", Long.class, post.getId());
    }

    private List<String> events(Long applicationId) {
        return jdbc.queryForList("SELECT status FROM application_event WHERE application_id = ? ORDER BY id", String.class, applicationId);
    }

    private Member member(String name) {
        Member m = memberRepository.save(Member.builder()
                .username(prefix + name).nickname(prefix + name).email(prefix + name + "@test.com").delYn("N").build());
        members.add(m);
        return m;
    }

    private String bearer(String username) {
        return bearer(username, "ROLE_USER");
    }

    private String bearer(String username, String role) {
        var auth = new UsernamePasswordAuthenticationToken(username, null, List.of(new SimpleGrantedAuthority(role)));
        return "Bearer " + jwt.generateToken(auth).getAccessToken();
    }

    interface Task { void run(int i) throws Exception; }

    private void runConcurrently(int threads, Task task) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            int idx = i;
            pool.submit(() -> {
                try {
                    start.await();
                    task.run(idx);
                } catch (Exception ignored) {
                    // 정원 초과·이미 처리된 신청으로 거절되는 요청은 정상
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await(30, TimeUnit.SECONDS);
        pool.shutdown();
    }
}
