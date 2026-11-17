package org.example.project;

import org.example.project.member.entity.Member;
import org.example.project.member.repository.MemberRepository;
import org.example.project.post.entity.Post;
import org.example.project.post.entity.PostPosition;
import org.example.project.post.entity.PostPositionMember;
import org.example.project.post.repositoty.PostPositionMemberRepository;
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
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
    @Autowired MemberRepository memberRepository;
    @Autowired PostPositionMemberRepository applicationRepository;

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

    @Test
    void 회원_정보는_본인만_보고_무관한_회원은_403() throws Exception {
        Member member = memberRepository.save(Member.builder()
                .username("info-target").email("target@test.com").delYn("N").build());
        try {
            mvc.perform(get("/api/member/getUserInfo/" + member.getId()).header("Authorization", bearer("info-target", "ROLE_USER")))
                    .andExpect(status().isOk());
            mvc.perform(get("/api/member/getUserInfo/" + member.getId()).header("Authorization", bearer("stranger", "ROLE_USER")))
                    .andExpect(status().isForbidden());
        } finally {
            memberRepository.delete(member);
        }
    }

    @Test
    void 관리자는_자기_계정을_비활성화할_수_없다() throws Exception {
        Member admin = memberRepository.save(Member.builder()
                .username("self-ban-admin").email("self-ban@test.com").delYn("N").build());
        try {
            mvc.perform(post("/api/admin/banUser").header("Authorization", bearer("self-ban-admin", "ROLE_ADMIN"))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"memberId\":" + admin.getId() + "}"))
                    .andExpect(status().isBadRequest());
            org.assertj.core.api.Assertions.assertThat(memberRepository.findById(admin.getId()).orElseThrow().getDelYn()).isEqualTo("N");
        } finally {
            memberRepository.delete(admin);
        }
    }

    @Test
    void 차단된_회원의_기존_토큰은_401() throws Exception {
        Member member = memberRepository.save(Member.builder()
                .username("ban-target").email("ban@test.com").delYn("N").build());
        String token = bearer("ban-target", "ROLE_USER");
        try {
            mvc.perform(get("/api/member/myPage").header("Authorization", token)).andExpect(status().isOk());
            mvc.perform(post("/api/admin/banUser").header("Authorization", bearer("admin", "ROLE_ADMIN"))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"memberId\":" + member.getId() + "}"))
                    .andExpect(status().isOk());
            mvc.perform(get("/api/member/myPage").header("Authorization", token)).andExpect(status().isUnauthorized());
            mvc.perform(post("/api/admin/acceptUser").header("Authorization", bearer("admin", "ROLE_ADMIN"))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"memberId\":" + member.getId() + "}"))
                    .andExpect(status().isOk());
            mvc.perform(get("/api/member/myPage").header("Authorization", token)).andExpect(status().isOk());
        } finally {
            memberRepository.delete(member);
        }
    }

    @Test
    void 관리자가_숨긴_글은_작성자만_보고_신청할_수_없다() throws Exception {
        Member owner = memberRepository.save(Member.builder()
                .username("hidden-owner").email("hidden-owner@test.com").delYn("N").build());
        Post post = Post.builder()
                .title("숨김 테스트").author("hidden-owner").category("project")
                .created(new Date()).delYn("Y").likeCount(0).viewCount(0)
                .positions(new java.util.ArrayList<>()).techStacks(new java.util.ArrayList<>()).likes(new java.util.ArrayList<>())
                .build();
        PostPosition position = new PostPosition();
        position.setPost(post);
        position.setRole("backend");
        position.setCount(1);
        post.getPositions().add(position);
        post = postRepository.save(post);
        try {
            mvc.perform(get("/api/post/view/" + post.getId())).andExpect(status().isNotFound());
            mvc.perform(get("/api/post/viewUsername/" + post.getId()).header("Authorization", bearer("stranger", "ROLE_USER")))
                    .andExpect(status().isNotFound());
            mvc.perform(post("/api/post/apply").header("Authorization", bearer("stranger", "ROLE_USER"))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"positionId\":" + post.getPositions().get(0).getId() + "}"))
                    .andExpect(status().isNotFound());
            // 막힌 조회는 롤백되어 조회수가 오르지 않는다
            org.assertj.core.api.Assertions.assertThat(postRepository.findById(post.getId()).orElseThrow().getViewCount()).isZero();
            mvc.perform(get("/api/post/viewUsername/" + post.getId()).header("Authorization", bearer("hidden-owner", "ROLE_USER")))
                    .andExpect(status().isOk());
        } finally {
            postRepository.findById(post.getId()).ifPresent(postRepository::delete);
            memberRepository.delete(owner);
        }
    }

    @Test
    void 비로그인_상세_응답에_신청자_정보가_없다() throws Exception {
        Member applicant = memberRepository.save(Member.builder()
                .username("leak-applicant").email("leak@test.com").delYn("N").build());
        Post post = Post.builder()
                .title("신청자 노출 테스트").author("owner").category("project")
                .created(new Date()).delYn("N").likeCount(0).viewCount(0)
                .positions(new java.util.ArrayList<>()).techStacks(new java.util.ArrayList<>()).likes(new java.util.ArrayList<>())
                .build();
        PostPosition position = new PostPosition();
        position.setPost(post);
        position.setRole("backend");
        position.setCount(1);
        post.getPositions().add(position);
        post = postRepository.save(post);
        PostPositionMember application = new PostPositionMember();
        application.setPostPosition(post.getPositions().get(0));
        application.setMember(applicant);
        application.setStatus("APPLY");
        applicationRepository.save(application);
        try {
            mvc.perform(get("/api/post/view/" + post.getId()))
                    .andExpect(status().isOk())
                    .andExpect(content().string(not(containsString("leak-applicant"))))
                    .andExpect(content().string(not(containsString("positionMemberList"))));
        } finally {
            postRepository.findById(post.getId()).ifPresent(postRepository::delete);
            memberRepository.delete(applicant);
        }
    }

    @Test
    void 회원가입_응답에_비밀번호_해시가_없다() throws Exception {
        String username = "leak-signup-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        try {
            mvc.perform(post("/api/member/signup").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"" + username + "\",\"password\":\"Test1234!\",\"nickname\":\"" + username
                                    + "\",\"email\":\"" + username + "@test.com\",\"name\":\"n\",\"preferredTechStacks\":[]}"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(not(containsString("password"))))
                    .andExpect(content().string(not(containsString("$2a$"))));
        } finally {
            memberRepository.findByUsername(username).ifPresent(memberRepository::delete);
        }
    }
}
