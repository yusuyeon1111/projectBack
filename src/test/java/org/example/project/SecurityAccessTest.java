package org.example.project;

import org.example.project.post.entity.Post;
import org.example.project.post.repositoty.PostRepository;
import org.example.project.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Date;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// DB/Redis가 필요하다 (CLAUDE.md의 실행 환경변수 참고). JWT·외부 연동 값은 여기서 더미로 채운다.
@SpringBootTest(properties = {
        "jwt.secret=dGVzdC1zZWNyZXQta2V5LWZvci1zZWN1cml0eS1hY2Nlc3MtdGVzdA==",
        "spring.data.redis.usessl=false",
        "KAKAO_CLIENT_ID=test", "AWS_ACCESS_KEY_ID=test", "AWS_SECRET_ACCESS_KEY=test",
        "AWS_REGION=ap-northeast-2", "AWS_S3_BUCKET_NAME=test",
})
@AutoConfigureMockMvc
class SecurityAccessTest {

    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider jwt;
    @Autowired PostRepository postRepository;

    private String bearer(String username, String role) {
        var auth = new UsernamePasswordAuthenticationToken(username, null, List.of(new SimpleGrantedAuthority(role)));
        return "Bearer " + jwt.generateToken(auth).getAccessToken();
    }

    @Test
    void 토큰_없이_보호된_API는_401() throws Exception {
        mvc.perform(post("/api/member/update").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"victim\",\"password\":\"x\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/post/create").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/post/delete/1")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/member/myPage")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/memberAll")).andExpect(status().isUnauthorized());
    }

    @Test
    void 공개_API는_토큰_없이_접근() throws Exception {
        mvc.perform(post("/api/post/list").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/member/idCheck").param("username", "nobody")).andExpect(status().isOk());
    }

    @Test
    void 남의_글은_삭제할_수_없고_본인_글은_삭제된다() throws Exception {
        Post post = postRepository.save(Post.builder()
                .title("권한 테스트").author("owner").category("project")
                .created(new Date()).delYn("N").likeCount(0).viewCount(0)
                .positions(new java.util.ArrayList<>()).techStacks(new java.util.ArrayList<>()).likes(new java.util.ArrayList<>())
                .build());
        try {
            mvc.perform(delete("/api/post/delete/" + post.getId()).header("Authorization", bearer("intruder", "ROLE_USER")))
                    .andExpect(status().isForbidden());
            mvc.perform(delete("/api/post/delete/" + post.getId()).header("Authorization", bearer("owner", "ROLE_USER")))
                    .andExpect(status().isOk());
        } finally {
            postRepository.findById(post.getId()).ifPresent(postRepository::delete);
        }
    }

    @Test
    void 일반_사용자는_관리자_API에_403() throws Exception {
        mvc.perform(get("/api/admin/memberAll").header("Authorization", bearer("someone", "ROLE_USER")))
                .andExpect(status().isForbidden());
    }
}
