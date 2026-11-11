package org.example.project;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.project.member.entity.Member;
import org.example.project.member.repository.MemberRepository;
import org.example.project.post.repositoty.ApplyFormRepository;
import org.example.project.post.repositoty.PostPositionMemberRepository;
import org.example.project.post.repositoty.PostRepository;
import org.example.project.post.service.ApplyFileStorage;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 신청서 질문 저장, 답변 검증·스냅샷, 열람 권한을 실제 API로 확인한다. DB/Redis가 필요하고 S3만 가짜로 바꾼다.
@SpringBootTest(properties = {
        "jwt.secret=dGVzdC1zZWNyZXQta2V5LWZvci1zZWN1cml0eS1hY2Nlc3MtdGVzdA==",
        "spring.data.redis.usessl=false",
        "KAKAO_CLIENT_ID=test", "AWS_ACCESS_KEY_ID=test", "AWS_SECRET_ACCESS_KEY=test",
        "AWS_REGION=ap-northeast-2", "AWS_S3_BUCKET_NAME=test",
})
@AutoConfigureMockMvc
class ApplyFormTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtTokenProvider jwt;
    @Autowired MemberRepository memberRepository;
    @Autowired PostRepository postRepository;
    @Autowired ApplyFormRepository applyFormRepository;
    @Autowired PostPositionMemberRepository applicationRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @MockitoBean ApplyFileStorage fileStorage;

    final String prefix = "af-" + UUID.randomUUID().toString().substring(0, 8) + "-";
    final List<Member> members = new ArrayList<>();
    Member author, applicant, stranger;
    Long postId, positionId;
    List<String> keys; // 0 자기소개(TEXT 필수), 1 경험(RADIO 필수), 2 깃허브(URL), 3 포트폴리오(FILE)

    @BeforeEach
    void setUp() throws Exception {
        author = member("author");
        applicant = member("applicant");
        stranger = member("stranger");
        String body = """
                {"title":"신청서 테스트","category":"study","content":"c","projectType":"online","region":"nothing",
                 "positions":[{"role":"totalCount","count":2,"status":"ING"}],
                 "applyQuestions":[
                   {"type":"TEXT","label":" 자기소개 ","required":true},
                   {"type":"RADIO","label":"경험","required":true,"options":["있음","없음"," 있음 "]},
                   {"type":"URL","label":"깃허브","required":false},
                   {"type":"FILE","label":"포트폴리오","required":false}]}""";
        postId = Long.valueOf(call(post("/api/post/create"), author, body).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        JsonNode view = read(call(get("/api/post/view/" + postId), null, null));
        positionId = view.at("/positions/0/id").asLong();
        keys = new ArrayList<>();
        view.get("applyQuestions").forEach(q -> keys.add(q.get("key").asText()));
    }

    @AfterEach
    void cleanUp() {
        if (postId != null) postRepository.findById(postId).ifPresent(postRepository::delete);
        memberRepository.deleteAll(members);
        jdbcTemplate.update("DELETE FROM notification WHERE receiver LIKE ?", prefix + "%");
    }

    @Test
    void 질문은_공백을_다듬고_중복_선택지를_합쳐_저장한다() throws Exception {
        JsonNode questions = read(call(get("/api/post/view/" + postId), null, null)).get("applyQuestions");
        assertThat(questions.get(0).get("label").asText()).isEqualTo("자기소개");
        assertThat(questions.get(1).get("options").toString()).isEqualTo("[\"있음\",\"없음\"]");
        assertThat(keys).doesNotContainNull().doesNotHaveDuplicates();
    }

    @Test
    void 선택지_없는_선택형_질문은_400() throws Exception {
        String body = """
                {"title":"t","category":"study","projectType":"online","region":"nothing","positions":[],
                 "applyQuestions":[{"type":"RADIO","label":"경험","required":true,"options":[]}]}""";
        call(post("/api/post/create"), author, body).andExpect(status().isBadRequest());
    }

    @Test
    void 답변이_질문_규칙에_맞지_않으면_400이고_저장되지_않는다() throws Exception {
        apply(answers(Map.of(1, "있음"))).andExpect(status().isBadRequest());                       // 필수 누락
        apply(answers(Map.of(0, "안녕", 1, "아마도"))).andExpect(status().isBadRequest());           // 없는 선택지
        apply(answers(Map.of(0, "안녕", 1, "있음", 2, "javascript:alert(1)"))).andExpect(status().isBadRequest());
        apply(answers(Map.of(0, "x".repeat(201), 1, "있음"))).andExpect(status().isBadRequest());     // 길이 초과
        apply("[{\"questionKey\":\"unknown\",\"values\":[\"x\"]}]").andExpect(status().isBadRequest());

        assertThat(applicationsOfPost()).isZero();
    }

    @Test
    void 첨부는_본인_경로에_실제로_올라간_제한_이내_파일만_받는다() throws Exception {
        String own = "apply/" + applicant.getId() + "/" + UUID.randomUUID();
        String others = "apply/" + stranger.getId() + "/" + UUID.randomUUID();
        given(fileStorage.sizeOf(others)).willReturn(Optional.of(100L));
        given(fileStorage.sizeOf(own)).willReturn(Optional.empty());

        apply(withFile(others, "a.pdf")).andExpect(status().isBadRequest());   // 남의 경로
        apply(withFile(own, "a.pdf")).andExpect(status().isBadRequest());      // 올라가지 않음
        given(fileStorage.sizeOf(own)).willReturn(Optional.of(11L * 1024 * 1024));
        apply(withFile(own, "a.pdf")).andExpect(status().isBadRequest());      // 10MB 초과
        given(fileStorage.sizeOf(own)).willReturn(Optional.of(1234L));
        apply(withFile(own, "a.exe")).andExpect(status().isBadRequest());      // 확장자
        assertThat(applicationsOfPost()).isZero();

        apply(withFile(own, "a.pdf")).andExpect(status().isOk());
        JsonNode file = read(call(get("/api/post/application/" + applicationId()), author, null)).at("/3/files/0");
        assertThat(file.get("size").asLong()).isEqualTo(1234L); // 클라이언트가 아니라 S3에서 읽은 크기
    }

    @Test
    void 질문을_고쳐도_이미_낸_신청서는_제출_당시_질문으로_보인다() throws Exception {
        apply(answers(Map.of(0, "안녕하세요", 1, "있음"))).andExpect(status().isOk());

        String edit = "{\"id\":" + postId + ",\"applyQuestions\":[{\"key\":\"" + keys.get(0)
                + "\",\"type\":\"TEXT\",\"label\":\"바뀐 질문\",\"required\":true}]}";
        call(post("/api/post/update"), author, edit).andExpect(status().isOk());

        JsonNode answers = read(call(get("/api/post/application/" + applicationId()), author, null));
        assertThat(answers).hasSize(4);
        assertThat(answers.get(0).get("label").asText()).isEqualTo("자기소개");
        assertThat(answers.get(0).at("/values/0").asText()).isEqualTo("안녕하세요");
        assertThat(answers.get(1).get("options").toString()).isEqualTo("[\"있음\",\"없음\"]");
    }

    @Test
    void 답변과_첨부는_글_작성자와_신청자_본인만_본다() throws Exception {
        String own = "apply/" + applicant.getId() + "/" + UUID.randomUUID();
        given(fileStorage.sizeOf(own)).willReturn(Optional.of(10L));
        given(fileStorage.downloadUrl(eq(own), anyString())).willReturn("https://signed");
        apply(withFile(own, "이력서.pdf")).andExpect(status().isOk());
        String path = "/api/post/application/" + applicationId();

        call(get(path), author, null).andExpect(status().isOk());
        call(get(path), applicant, null).andExpect(status().isOk());
        call(get(path), stranger, null).andExpect(status().isForbidden());
        mvc.perform(get(path).header("Authorization", bearer(stranger.getUsername(), "ROLE_ADMIN")))
                .andExpect(status().isForbidden());

        call(get(path + "/file").param("key", own), author, null).andExpect(status().isOk());
        call(get(path + "/file").param("key", own), stranger, null).andExpect(status().isForbidden());
        call(get(path + "/file").param("key", "apply/1/other"), author, null).andExpect(status().isBadRequest());
    }

    @Test
    void 빈_질문_목록으로_수정하면_신청서가_없어지고_바로_신청된다() throws Exception {
        call(post("/api/post/update"), author, "{\"id\":" + postId + ",\"applyQuestions\":[]}").andExpect(status().isOk());
        assertThat(applyFormRepository.findByPostId(postId)).isEmpty();

        apply("[]").andExpect(status().isOk());
    }

    @Test
    void 글을_지우면_신청서도_지워진다() throws Exception {
        call(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/post/delete/" + postId),
                author, null).andExpect(status().isOk());
        assertThat(applyFormRepository.findByPostId(postId)).isEmpty();
        postId = null;
    }

    // --- helpers ---

    private ResultActions apply(String answersJson) throws Exception {
        return call(post("/api/post/apply"), applicant, "{\"positionId\":" + positionId + ",\"answers\":" + answersJson + "}");
    }

    private String answers(Map<Integer, String> values) throws Exception {
        List<Map<String, Object>> list = new ArrayList<>();
        values.forEach((i, v) -> list.add(Map.of("questionKey", keys.get(i), "values", List.of(v))));
        return json.writeValueAsString(list);
    }

    private String withFile(String key, String name) throws Exception {
        return json.writeValueAsString(List.of(
                Map.of("questionKey", keys.get(0), "values", List.of("안녕")),
                Map.of("questionKey", keys.get(1), "values", List.of("있음")),
                Map.of("questionKey", keys.get(3), "files", List.of(Map.of("key", key, "name", name)))));
    }

    private ResultActions call(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                               Member as, String body) throws Exception {
        if (as != null) request.header("Authorization", bearer(as.getUsername(), "ROLE_USER"));
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(request);
    }

    private JsonNode read(ResultActions result) throws Exception {
        return json.readTree(result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private long applicationsOfPost() {
        return applicationRepository.findAll().stream()
                .filter(a -> a.getPostPosition().getId().equals(positionId)).count();
    }

    private Long applicationId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM post_position_member WHERE post_position_id = ?", Long.class, positionId);
    }

    private String bearer(String username, String role) {
        var auth = new UsernamePasswordAuthenticationToken(username, null, List.of(new SimpleGrantedAuthority(role)));
        return "Bearer " + jwt.generateToken(auth).getAccessToken();
    }

    private Member member(String name) {
        Member m = memberRepository.save(Member.builder()
                .username(prefix + name).nickname(prefix + name).email(prefix + name + "@test.com").delYn("N").build());
        members.add(m);
        return m;
    }
}
