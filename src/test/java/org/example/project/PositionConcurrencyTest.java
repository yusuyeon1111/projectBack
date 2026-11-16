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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

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

// 실제 DB에 커밋되는 동시 요청을 재현해야 해서 @Transactional 롤백 없이 돌리고 직접 정리한다.
@SpringBootTest(properties = {
        "jwt.secret=dGVzdC1zZWNyZXQta2V5LWZvci1zZWN1cml0eS1hY2Nlc3MtdGVzdA==",
        "spring.data.redis.usessl=false",
        "KAKAO_CLIENT_ID=test", "AWS_ACCESS_KEY_ID=test", "AWS_SECRET_ACCESS_KEY=test",
        "AWS_REGION=ap-northeast-2", "AWS_S3_BUCKET_NAME=test",
})
class PositionConcurrencyTest {

    static final int THREADS = 10;

    @Autowired PositionService positionService;
    @Autowired PostRepository postRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired PostPositionMemberRepository applicationRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    final String prefix = "cc-" + UUID.randomUUID().toString().substring(0, 8) + "-";
    final List<Member> members = new ArrayList<>();
    Post post;

    @AfterEach
    void cleanUp() {
        if (post != null) postRepository.findById(post.getId()).ifPresent(postRepository::delete);
        memberRepository.deleteAll(members);
        // 신청·승인 때 생기는 알림도 받는 사람이 모두 이 테스트의 회원이다
        jdbcTemplate.update("DELETE FROM notification WHERE receiver LIKE ?", prefix + "%");
    }

    @Test
    void 정원이_찬_포지션은_추가로_승인되지_않는다() {
        PostPosition position = createPost(1);
        List<Long> applications = applyAll(position, 2);

        positionService.acceptPosition(accept(applications.get(0)), author());
        try {
            positionService.acceptPosition(accept(applications.get(1)), author());
        } catch (RuntimeException ignored) {
        }

        assertThat(acceptedCount(position)).isEqualTo(1);
    }

    @Test
    void 동시에_승인해도_정원을_넘지_않는다() throws Exception {
        PostPosition position = createPost(1);
        List<Long> applications = applyAll(position, THREADS);

        runConcurrently(i -> positionService.acceptPosition(accept(applications.get(i)), author()));

        assertThat(acceptedCount(position)).isEqualTo(1);
    }

    @Test
    void 같은_사람이_동시에_신청해도_한_번만_저장된다() throws Exception {
        PostPosition position = createPost(1);
        Member applicant = member("dup");

        runConcurrently(i -> {
            ApplyPositionRequestDto dto = new ApplyPositionRequestDto();
            dto.setPositionId(position.getId());
            dto.setUsername(applicant.getUsername());
            positionService.applyPostPosition(dto);
        });

        long saved = applicationRepository.findAll().stream()
                .filter(a -> a.getPostPosition().getId().equals(position.getId()))
                .count();
        assertThat(saved).isEqualTo(1);
    }

    // --- helpers ---

    private PostPosition createPost(int count) {
        Member author = member("author");
        post = Post.builder()
                .title("동시성 테스트").author(author.getUsername()).category("project")
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

    private List<Long> applyAll(PostPosition position, int n) {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            PostPositionMember application = new PostPositionMember();
            application.setPostPosition(position);
            application.setMember(member("applicant" + i));
            application.setStatus("APPLY");
            application.setAppliedAt(LocalDateTime.now());
            ids.add(applicationRepository.save(application).getId());
        }
        return ids;
    }

    private Member member(String name) {
        Member m = memberRepository.save(Member.builder()
                .username(prefix + name).email(prefix + name + "@test.com").delYn("N").build());
        members.add(m);
        return m;
    }

    private String author() {
        return post.getAuthor();
    }

    private ApplyPositionRequestDto accept(Long applicationId) {
        ApplyPositionRequestDto dto = new ApplyPositionRequestDto();
        dto.setPostPositionId(applicationId);
        return dto;
    }

    private long acceptedCount(PostPosition position) {
        return applicationRepository.findCountByPostPositionId(position.getId());
    }

    interface Task { void run(int i) throws Exception; }

    // 모든 스레드를 출발선에 세워 두고 한 번에 풀어 실제 경합을 만든다
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
                    // 정원 초과·중복 신청으로 거절되는 요청은 정상
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
