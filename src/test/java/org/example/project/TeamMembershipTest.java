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
import org.example.project.team.service.TeamService;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 탈퇴·퇴출·위임·재신청·팀 상태 변경. 동시 요청을 재현해야 해서 롤백 없이 돌리고 직접 정리한다.
@SpringBootTest(properties = {
        "jwt.secret=dGVzdC1zZWNyZXQta2V5LWZvci1zZWN1cml0eS1hY2Nlc3MtdGVzdA==",
        "spring.data.redis.usessl=false",
        "KAKAO_CLIENT_ID=test",
        "AWS_REGION=ap-northeast-2", "AWS_S3_BUCKET_NAME=test",
})
@AutoConfigureMockMvc
class TeamMembershipTest {

    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider jwt;
    @Autowired PositionService positionService;
    @Autowired TeamService teamService;
    @Autowired PostRepository postRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired PostPositionMemberRepository applicationRepository;
    @Autowired JdbcTemplate jdbc;

    final String prefix = "tm-" + UUID.randomUUID().toString().substring(0, 8) + "-";
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
    void 탈퇴하면_신청이_WITHDRAWN이_되고_마감된_포지션과_글이_다시_열린다() throws Exception {
        PostPosition position = createPost(1).get(0);
        Member a = member("a");
        Long app = apply(position, a);
        accept(app);
        assertThat(positionStatus(position)).isEqualTo("END");

        call(delete(teamUrl("/members/me")), a).andExpect(status().isOk());

        assertThat(appStatus(app)).isEqualTo("WITHDRAWN");
        assertThat(positionStatus(position)).isEqualTo("ING");
        assertThat(jdbc.queryForObject("SELECT status FROM post WHERE id = ?", String.class, post.getId())).isEqualTo("ING");
        assertThat(memberStatus(a)).isEqualTo("LEFT");
        assertThat(events(app)).containsExactly("ACCEPT", "WITHDRAWN");
    }

    @Test
    void 탈퇴와_새_승인이_동시에_와도_정원을_넘지_않는다() throws Exception {
        PostPosition position = createPost(1).get(0);
        Member a = member("a");
        accept(apply(position, a));
        Long other = apply(position, member("b"));
        Long teamId = teamId();

        runConcurrently(2, i -> {
            if (i == 0) teamService.leave(teamId, a.getUsername());
            else accept(other);
        });

        long accepted = jdbc.queryForObject("SELECT COUNT(*) FROM post_position_member WHERE post_position_id = ? AND status = 'ACCEPT'",
                Long.class, position.getId());
        assertThat(accepted).isLessThanOrEqualTo(1);
        assertThat(positionStatus(position)).isEqualTo(accepted == 1 ? "END" : "ING");
    }

    @Test
    void 탈퇴한_사람은_다시_신청하고_다시_승인되면_같은_팀원_행이_살아난다() throws Exception {
        PostPosition position = createPost(1).get(0);
        Member a = member("a");
        Long app = apply(position, a);
        accept(app);
        Long rowId = jdbc.queryForObject("SELECT id FROM team_member WHERE member_id = ?", Long.class, a.getId());
        teamService.leave(teamId(), a.getUsername());

        runConcurrently(5, i -> positionService.reapply(app, a.getUsername()));
        assertThat(appStatus(app)).isEqualTo("APPLY");
        assertThat(events(app)).containsExactly("ACCEPT", "WITHDRAWN", "APPLY"); // 연타해도 한 번만

        accept(app);
        assertThat(jdbc.queryForObject("SELECT id FROM team_member WHERE member_id = ?", Long.class, a.getId())).isEqualTo(rowId);
        assertThat(memberStatus(a)).isEqualTo("ACTIVE");
    }

    @Test
    void 퇴출되거나_거절된_사람은_다시_신청할_수_없다() throws Exception {
        List<PostPosition> positions = createPost(1, 1);
        Member a = member("a");
        Member b = member("b");
        Long appA = apply(positions.get(0), a);
        Long appB = apply(positions.get(0), b);
        accept(appA);
        positionService.rejectPosition(dto(appB), author.getUsername());

        call(delete(teamUrl("/members/" + a.getId())), author).andExpect(status().isOk());
        assertThat(appStatus(appA)).isEqualTo("EXPELLED");
        assertThat(memberStatus(a)).isEqualTo("KICKED");

        call(post("/api/post/reapply").contentType(MediaType.APPLICATION_JSON).content("{\"postPositionId\":" + appA + "}"), a)
                .andExpect(status().isConflict());
        call(post("/api/post/reapply").contentType(MediaType.APPLICATION_JSON).content("{\"postPositionId\":" + appB + "}"), b)
                .andExpect(status().isConflict());
        // 퇴출된 사람은 같은 글의 다른 포지션에도 못 들어온다
        call(post("/api/post/apply").contentType(MediaType.APPLICATION_JSON).content("{\"positionId\":" + positions.get(1).getId() + "}"), a)
                .andExpect(status().isConflict());
    }

    @Test
    void 팀장은_위임_전에는_탈퇴할_수_없고_동시_위임에도_팀장은_한_명이다() throws Exception {
        PostPosition position = createPost(3).get(0);
        Member a = member("a");
        Member b = member("b");
        accept(apply(position, a));
        accept(apply(position, b));
        Long teamId = teamId();

        call(delete(teamUrl("/members/me")), author).andExpect(status().isConflict());

        runConcurrently(2, i -> teamService.changeLeader(teamId, author.getUsername(), (i == 0 ? a : b).getId()));

        Long leader = jdbc.queryForObject("SELECT leader_member_id FROM team WHERE id = ?", Long.class, teamId);
        assertThat(leader).isIn(a.getId(), b.getId());
        assertThat(jdbc.queryForObject("SELECT status FROM team_member WHERE team_id = ? AND member_id = ?", String.class, teamId, leader))
                .isEqualTo("ACTIVE");
    }

    @Test
    void 위임과_대상의_탈퇴가_동시에_와도_팀장은_활성_팀원이다() throws Exception {
        Member a = member("a");
        accept(apply(createPost(2).get(0), a));
        Long teamId = teamId();

        runConcurrently(2, i -> {
            if (i == 0) teamService.changeLeader(teamId, author.getUsername(), a.getId());
            else teamService.leave(teamId, a.getUsername());
        });

        Long leader = jdbc.queryForObject("SELECT leader_member_id FROM team WHERE id = ?", Long.class, teamId);
        assertThat(jdbc.queryForObject("SELECT status FROM team_member WHERE team_id = ? AND member_id = ?", String.class, teamId, leader))
                .isEqualTo("ACTIVE");
    }

    @Test
    void 팀장만_상태를_바꾸고_팀원이_아니면_404() throws Exception {
        Member a = member("a");
        accept(apply(createPost(2).get(0), a));

        call(post(teamUrl("/start")), a).andExpect(status().isForbidden());
        call(delete(teamUrl("/members/" + author.getId())), a).andExpect(status().isForbidden());
        call(post(teamUrl("/start")), member("outsider")).andExpect(status().isNotFound());
        call(post(teamUrl("/complete")), author).andExpect(status().isConflict()); // 시작 전에는 완료할 수 없다
        call(post(teamUrl("/start")), author).andExpect(status().isOk());
        call(post(teamUrl("/complete")), author).andExpect(status().isOk());
    }

    @Test
    void 끝난_팀에는_쓸_수_없고_해산된_팀의_글은_지울_수_있다() throws Exception {
        PostPosition position = createPost(3).get(0);
        Member a = member("a");
        Member b = member("b");
        accept(apply(position, a));
        Long pending = apply(position, b);

        call(post(teamUrl("/disband")), author).andExpect(status().isOk());

        call(delete(teamUrl("/members/me")), a).andExpect(status().isConflict());
        call(put(teamUrl("/leader")).contentType(MediaType.APPLICATION_JSON).content("{\"memberId\":" + a.getId() + "}"), author)
                .andExpect(status().isConflict());
        assertThatThrownBy(() -> accept(pending)).isInstanceOf(IllegalStateException.class);
        call(post(teamUrl("/disband")), author).andExpect(status().isConflict());

        Long teamId = teamId();
        call(delete("/api/post/delete/" + post.getId()), author).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT post_id FROM team WHERE id = ?", Long.class, teamId)).isNull();
    }

    @Test
    void 팀_상태_변경_위임_탈퇴_퇴출은_상대에게_팀_알림을_남긴다() throws Exception {
        Member a = member("a");
        Member b = member("b");
        Member c = member("c");
        PostPosition position = createPost(3).get(0);
        accept(apply(position, a));
        accept(apply(position, b));
        accept(apply(position, c));
        Long teamId = teamId();

        call(post(teamUrl("/start")), author).andExpect(status().isOk());
        call(delete(teamUrl("/members/" + c.getId())), author).andExpect(status().isOk());
        call(put(teamUrl("/leader")).contentType(MediaType.APPLICATION_JSON).content("{\"memberId\":" + a.getId() + "}"), author)
                .andExpect(status().isOk());
        call(delete(teamUrl("/members/me")), b).andExpect(status().isOk());

        // 대상은 모두 TEAM/팀 id이고, 행동한 사람 자신은 받지 않는다. 탈퇴 알림은 위임받은 지금 팀장(a)에게 간다
        assertThat(teamNotifications(author, teamId)).isEmpty();
        assertThat(teamNotifications(a, teamId)).containsExactly("TEAM_STARTED", "TEAM_LEADER", "TEAM_LEFT");
        assertThat(teamNotifications(b, teamId)).containsExactly("TEAM_STARTED");
        assertThat(teamNotifications(c, teamId)).containsExactly("TEAM_STARTED", "TEAM_KICKED");
        assertThat(jdbc.queryForObject("SELECT actor FROM notification WHERE receiver = ? AND type = 'TEAM_LEFT'",
                String.class, a.getUsername())).isEqualTo(b.getNickname() != null ? b.getNickname() : b.getUsername());
    }

    private List<String> teamNotifications(Member m, Long teamId) {
        return jdbc.queryForList("SELECT type FROM notification WHERE receiver = ? AND target_type = 'TEAM' AND target_id = ? ORDER BY id",
                String.class, m.getUsername(), teamId);
    }

    // --- helpers ---

    private List<PostPosition> createPost(int... counts) {
        author = member("author");
        post = Post.builder()
                .title("팀 관리 테스트").author(author.getUsername()).category("project").status("ING")
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
        positionService.acceptPosition(dto(applicationId), author.getUsername());
    }

    private ApplyPositionRequestDto dto(Long applicationId) {
        ApplyPositionRequestDto dto = new ApplyPositionRequestDto();
        dto.setPostPositionId(applicationId);
        return dto;
    }

    private Long teamId() {
        return jdbc.queryForObject("SELECT id FROM team WHERE post_id = ?", Long.class, post.getId());
    }

    private String teamUrl(String path) {
        return "/api/teams/" + teamId() + path;
    }

    private String appStatus(Long applicationId) {
        return jdbc.queryForObject("SELECT status FROM post_position_member WHERE id = ?", String.class, applicationId);
    }

    private String positionStatus(PostPosition position) {
        return jdbc.queryForObject("SELECT status FROM post_position WHERE position_id = ?", String.class, position.getId());
    }

    private String memberStatus(Member m) {
        return jdbc.queryForObject("SELECT status FROM team_member WHERE member_id = ?", String.class, m.getId());
    }

    private List<String> events(Long applicationId) {
        return jdbc.queryForList("SELECT status FROM application_event WHERE application_id = ? ORDER BY id", String.class, applicationId);
    }

    private org.springframework.test.web.servlet.ResultActions call(MockHttpServletRequestBuilder request, Member as) throws Exception {
        var auth = new UsernamePasswordAuthenticationToken(as.getUsername(), null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        return mvc.perform(request.header("Authorization", "Bearer " + jwt.generateToken(auth).getAccessToken()));
    }

    private Member member(String name) {
        Member m = memberRepository.save(Member.builder()
                .username(prefix + name).nickname(prefix + name).email(prefix + name + "@test.com").delYn("N").build());
        members.add(m);
        return m;
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
                    // 먼저 처리된 요청 때문에 거절되는 쪽은 정상
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
