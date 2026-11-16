package org.example.project;

import org.example.project.member.entity.Member;
import org.example.project.member.repository.MemberRepository;
import org.example.project.post.entity.Post;
import org.example.project.post.repositoty.PostRepository;
import org.example.project.security.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

// 좋아요 수·조회수가 동시 요청끼리 서로의 증가분을 덮어쓰지 않는지 확인한다.
// 커밋된 결과를 봐야 해서 롤백 없이 돌리고 직접 정리한다.
@SpringBootTest(properties = {
        "jwt.secret=dGVzdC1zZWNyZXQta2V5LWZvci1zZWN1cml0eS1hY2Nlc3MtdGVzdA==",
        "spring.data.redis.usessl=false",
        "KAKAO_CLIENT_ID=test", "AWS_ACCESS_KEY_ID=test", "AWS_SECRET_ACCESS_KEY=test",
        "AWS_REGION=ap-northeast-2", "AWS_S3_BUCKET_NAME=test",
})
@AutoConfigureMockMvc
class CounterConcurrencyTest {

    static final int THREADS = 10;

    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider jwt;
    @Autowired MemberRepository memberRepository;
    @Autowired PostRepository postRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    final String prefix = "lk-" + UUID.randomUUID().toString().substring(0, 8) + "-";
    final List<Member> members = new ArrayList<>();
    Post post;

    @BeforeEach
    void setUp() {
        post = postRepository.save(Post.builder()
                .title("좋아요 테스트").author(prefix + "author").category("project")
                .created(new Date()).delYn("N").likeCount(0).viewCount(0)
                .positions(new ArrayList<>()).techStacks(new ArrayList<>()).likes(new ArrayList<>())
                .build());
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM post_like WHERE post_id = ?", post.getId());
        postRepository.deleteById(post.getId());
        memberRepository.deleteAll(members);
    }

    @Test
    void 여러_명이_동시에_좋아요해도_수가_줄지_않는다() throws Exception {
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) tokens.add(bearer(member("m" + i)));

        runConcurrently(i -> like(tokens.get(i)));

        assertThat(rows()).isEqualTo(THREADS);
        assertThat(likeCount()).isEqualTo(THREADS);
    }

    @Test
    void 같은_사람이_연달아_눌러도_한_번만_센다() throws Exception {
        String token = bearer(member("dup"));

        runConcurrently(i -> like(token));

        assertThat(rows()).isEqualTo(1);
        assertThat(likeCount()).isEqualTo(1);
    }

    @Test
    void 좋아요_취소를_여러_번_보내도_음수가_되지_않는다() throws Exception {
        String token = bearer(member("undo"));
        like(token);

        runConcurrently(i -> unlike(token));
        unlike(bearer(member("never-liked"))); // 누른 적 없는 사람의 취소

        assertThat(rows()).isZero();
        assertThat(likeCount()).isZero();
    }

    private void like(String token) throws Exception {
        mvc.perform(post("/api/post/like").param("postId", post.getId().toString()).header("Authorization", token));
    }

    private void unlike(String token) throws Exception {
        mvc.perform(delete("/api/post/unLike").param("id", post.getId().toString()).header("Authorization", token));
    }

    private int rows() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM post_like WHERE post_id = ?", Integer.class, post.getId());
    }

    private int likeCount() {
        return jdbcTemplate.queryForObject("SELECT like_count FROM post WHERE id = ?", Integer.class, post.getId());
    }

    private Member member(String name) {
        Member m = memberRepository.save(Member.builder()
                .username(prefix + name).nickname(prefix + name).email(prefix + name + "@test.com").delYn("N").build());
        members.add(m);
        return m;
    }

    private String bearer(Member member) {
        var auth = new UsernamePasswordAuthenticationToken(member.getUsername(), null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        return "Bearer " + jwt.generateToken(auth).getAccessToken();
    }

    interface Task { void run(int i) throws Exception; }

    private void runConcurrently(Task task) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);
        for (int i = 0; i < THREADS; i++) {
            int idx = i;
            pool.submit(() -> {
                try {
                    start.await();
                    task.run(idx);
                } catch (Exception ignored) {
                    // 중복 좋아요가 거절되는 것은 정상
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
