package org.example.project;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.project.member.entity.Member;
import org.example.project.member.repository.MemberRepository;
import org.example.project.post.repositoty.PostRepository;
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

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 글 등록·수정 입력 검사, 진행 상태 계산, 신청 가능 여부를 실제 API로 확인한다. DB/Redis가 필요하다.
@SpringBootTest(properties = {
        "jwt.secret=dGVzdC1zZWNyZXQta2V5LWZvci1zZWN1cml0eS1hY2Nlc3MtdGVzdA==",
        "spring.data.redis.usessl=false",
        "KAKAO_CLIENT_ID=test", "AWS_ACCESS_KEY_ID=test", "AWS_SECRET_ACCESS_KEY=test",
        "AWS_REGION=ap-northeast-2", "AWS_S3_BUCKET_NAME=test",
})
@AutoConfigureMockMvc
class PostValidationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtTokenProvider jwt;
    @Autowired MemberRepository memberRepository;
    @Autowired PostRepository postRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    final String prefix = "pv-" + UUID.randomUUID().toString().substring(0, 8) + "-";
    final List<Member> members = new ArrayList<>();
    final List<Long> postIds = new ArrayList<>();
    final String today = LocalDate.now(ZoneId.of("Asia/Seoul")).toString();
    Member author, applicant;

    @BeforeEach
    void setUp() {
        author = member("author");
        applicant = member("applicant");
    }

    @AfterEach
    void cleanUp() {
        postIds.forEach(id -> postRepository.findById(id).ifPresent(postRepository::delete));
        memberRepository.deleteAll(members);
        jdbcTemplate.update("DELETE FROM notification WHERE receiver LIKE ?", prefix + "%");
    }

    @Test
    void 잘못된_등록_요청은_400() throws Exception {
        create("{}").andExpect(status().isBadRequest());
        create(body("   ", today, today, "backend", 1)).andExpect(status().isBadRequest());           // 빈 제목
        create(body("t", today, "2000-01-01", "backend", 1)).andExpect(status().isBadRequest());      // 마감 < 시작
        create(body("t", "2000-01-01", "2000-01-02", "backend", 1)).andExpect(status().isBadRequest()); // 지난 시작일
        create(body("t", today, today, "backend", 0)).andExpect(status().isBadRequest());             // 0명
        create(body("t", today, today, "ceo", 1)).andExpect(status().isBadRequest());                 // 없는 포지션
    }

    @Test
    void 진행_상태와_온라인_지역은_서버가_정한다() throws Exception {
        String body = body("오늘 시작", today, "2099-12-31", "backend", 1)
                .replace("\"region\":\"nothing\"", "\"region\":\"서울특별시\",\"postStatus\":\"END\"");
        JsonNode view = view(createId(body));
        assertThat(view.get("postStatus").asText()).isEqualTo("IN_PROGRESS");
        assertThat(view.get("region").asText()).isEqualTo("nothing");
    }

    @Test
    void 수정에서도_마감일이_시작일보다_빠르면_400이고_상태를_다시_계산한다() throws Exception {
        long id = createId(body("t", "2099-01-01", "2099-12-31", "backend", 1));
        assertThat(view(id).get("postStatus").asText()).isEqualTo("SCHEDULED");

        update("{\"id\":" + id + ",\"endDate\":\"2098-01-01\"}").andExpect(status().isBadRequest());
        update("{\"id\":" + id + ",\"startDate\":\"" + today + "\"}").andExpect(status().isOk());
        assertThat(view(id).get("postStatus").asText()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void 내_글이나_끝난_글에는_신청할_수_없다() throws Exception {
        long id = createId(body("t", today, "2099-12-31", "backend", 1));
        long positionId = view(id).at("/positions/0/id").asLong();

        apply(author, positionId).andExpect(status().isBadRequest());

        jdbcTemplate.update("UPDATE post SET post_status = 'END' WHERE id = ?", id);
        apply(applicant, positionId).andExpect(status().isBadRequest());
    }

    private String body(String title, String start, String end, String role, int count) {
        return """
                {"title":"%s","category":"project","content":"c","projectType":"online","region":"nothing",
                 "startDate":"%s","endDate":"%s","positions":[{"role":"%s","count":%d}]}"""
                .formatted(title, start, end, role, count);
    }

    private ResultActions create(String body) throws Exception {
        return mvc.perform(post("/api/post/create").header("Authorization", bearer(author))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long createId(String body) throws Exception {
        long id = Long.parseLong(create(body).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        postIds.add(id);
        return id;
    }

    private ResultActions update(String body) throws Exception {
        return mvc.perform(post("/api/post/update").header("Authorization", bearer(author))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions apply(Member member, long positionId) throws Exception {
        return mvc.perform(post("/api/post/apply").header("Authorization", bearer(member))
                .contentType(MediaType.APPLICATION_JSON).content("{\"positionId\":" + positionId + "}"));
    }

    private JsonNode view(long id) throws Exception {
        return json.readTree(mvc.perform(get("/api/post/view/" + id)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private String bearer(Member member) {
        var auth = new UsernamePasswordAuthenticationToken(member.getUsername(), null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        return "Bearer " + jwt.generateToken(auth).getAccessToken();
    }

    private Member member(String name) {
        Member m = memberRepository.save(Member.builder()
                .username(prefix + name).nickname(prefix + name).email(prefix + name + "@test.com").delYn("N").build());
        members.add(m);
        return m;
    }
}
