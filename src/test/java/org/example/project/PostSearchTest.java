package org.example.project;

import org.example.project.post.dto.PostRequestDto;
import org.example.project.post.dto.PostResponseDto;
import org.example.project.post.entity.Post;
import org.example.project.post.entity.PostPosition;
import org.example.project.post.entity.PostStack;
import org.example.project.post.repositoty.PostRepository;
import org.example.project.post.service.PostService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

// 목록 검색 쿼리를 바꿔도 필터·정렬·중복 없음이 그대로인지 확인한다.
// 공용 DB를 쓰므로 제목·본문에 고유 표식을 넣고 키워드로 이 테스트의 글만 걸러서 본다.
@SpringBootTest(properties = {
        "jwt.secret=dGVzdC1zZWNyZXQta2V5LWZvci1zZWN1cml0eS1hY2Nlc3MtdGVzdA==",
        "spring.data.redis.usessl=false",
        "KAKAO_CLIENT_ID=test", "AWS_ACCESS_KEY_ID=test", "AWS_SECRET_ACCESS_KEY=test",
        "AWS_REGION=ap-northeast-2", "AWS_S3_BUCKET_NAME=test",
})
class PostSearchTest {

    @Autowired PostService postService;
    @Autowired PostRepository postRepository;

    final String marker = "srch" + UUID.randomUUID().toString().substring(0, 8);
    final String contentMarker = "body" + UUID.randomUUID().toString().substring(0, 8);
    final List<Post> saved = new ArrayList<>();
    Post a, b, c;

    @BeforeEach
    void setUp() {
        long now = System.currentTimeMillis();
        long hour = 3_600_000L;
        // A: 오래된 프로젝트. 같은 역할 포지션 2개, "Re"가 들어간 스택 2개 → 조인하면 중복이 생기는 글
        a = save("A", "project", "ING", "서울특별시", 5, new Date(now - 72 * hour), null, "",
                List.of("backend", "backend"), List.of("Redis", "React"));
        // B: 생성은 중간이지만 1시간 전에 수정 → 최신순 맨 앞
        b = save("B", "project", "END", "부산광역시", 1, new Date(now - 48 * hour), new Date(now - hour), "",
                List.of("frontend"), List.of("Java"));
        // C: 스터디, 본문에만 별도 표식. 지역도 고유 값이라 한 글자 키워드 테스트에서 이 글만 걸러 볼 수 있다
        c = save("C", "study", "ING", marker, 9, new Date(now - 24 * hour), null, contentMarker,
                List.of("totalCount"), List.of());
    }

    @AfterEach
    void cleanUp() {
        saved.forEach(p -> postRepository.findById(p.getId()).ifPresent(postRepository::delete));
    }

    @Test
    void 필터가_없으면_수정일_또는_작성일_최신순() {
        assertThat(ids(search(d -> {}))).containsExactly(b.getId(), c.getId(), a.getId());
    }

    @Test
    void 인기순은_좋아요_많은_순() {
        assertThat(ids(search(d -> d.setSortType("popular")))).containsExactly(c.getId(), a.getId(), b.getId());
    }

    @Test
    void 포지션_필터는_해당_역할이_있는_글만_중복_없이() {
        Page<PostResponseDto> page = search(d -> d.setPosition("backend"));
        assertThat(ids(page)).containsExactly(a.getId());
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    void 기술스택_검색은_부분일치_중복_없이() {
        Page<PostResponseDto> page = search(d -> d.setStackKeyword("Re"));
        assertThat(ids(page)).containsExactly(a.getId());
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    void 카테고리_모집상태_지역_필터() {
        assertThat(ids(search(d -> { d.setCategory("project"); d.setStatus("ING"); }))).containsExactly(a.getId());
        assertThat(ids(search(d -> d.setRegion("부산광역시")))).containsExactly(b.getId());
    }

    @Test
    void 키워드는_본문도_검색한다() {
        PostRequestDto dto = baseRequest();
        dto.setKeyword(contentMarker);
        assertThat(ids(postService.getPostList(dto))).containsExactly(c.getId());
    }

    @Test
    void 키워드의_퍼센트는_와일드카드가_아니라_글자로_찾는다() {
        PostRequestDto dto = baseRequest();
        dto.setKeyword(marker + "%");
        assertThat(postService.getPostList(dto).getTotalElements()).isZero();
    }

    @Test
    void 한_글자_키워드도_검색한다() {
        PostRequestDto dto = baseRequest();
        dto.setRegion(marker);
        dto.setKeyword("C");
        assertThat(ids(postService.getPostList(dto))).containsExactly(c.getId());
        dto.setKeyword("X");
        assertThat(postService.getPostList(dto).getTotalElements()).isZero();
    }

    // --- helpers ---

    private Page<PostResponseDto> search(Consumer<PostRequestDto> filter) {
        PostRequestDto dto = baseRequest();
        dto.setKeyword(marker);
        filter.accept(dto);
        return postService.getPostList(dto);
    }

    // 프론트(Main.tsx)처럼 빈 필터는 ""로 보낸다
    private PostRequestDto baseRequest() {
        PostRequestDto dto = new PostRequestDto();
        dto.setCategory(""); dto.setStatus(""); dto.setPostStatus(""); dto.setRegion(""); dto.setPosition("");
        dto.setKeyword(""); dto.setProjectType(""); dto.setStackKeyword("");
        dto.setPage(0); dto.setSize(10); dto.setSortType("latest");
        return dto;
    }

    private List<Long> ids(Page<PostResponseDto> page) {
        return page.getContent().stream().map(PostResponseDto::getId).toList();
    }

    private Post save(String name, String category, String status, String region, int likes, Date created, Date updated,
                      String content, List<String> roles, List<String> stacks) {
        Post post = Post.builder()
                .title(marker + " " + name).content(content).author("search-tester").category(category).status(status)
                .region(region).projectType("online").created(created).updated(updated)
                .delYn("N").likeCount(likes).viewCount(0)
                .positions(new ArrayList<>()).techStacks(new ArrayList<>()).likes(new ArrayList<>())
                .build();
        for (String role : roles) {
            PostPosition position = new PostPosition();
            position.setPost(post);
            position.setRole(role);
            position.setCount(1);
            post.getPositions().add(position);
        }
        for (String stack : stacks) {
            PostStack postStack = new PostStack();
            postStack.setPost(post);
            postStack.setTechStack(stack);
            post.getTechStacks().add(postStack);
        }
        Post result = postRepository.save(post);
        saved.add(result);
        return result;
    }
}
