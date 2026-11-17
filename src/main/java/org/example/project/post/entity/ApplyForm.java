package org.example.project.post.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

/**
 * 글 하나에 붙는 신청서 질문 목록.
 * Post의 컬럼으로 두지 않은 이유: 목록 API가 Post 엔티티를 통째로 읽으므로 긴 JSON 컬럼이 목록 조회마다 따라온다.
 * Post 쪽에 mappedBy 연관도 두지 않는다. 주인이 아닌 쪽의 1:1은 지연 로딩이 안 돼 목록에서 글마다 쿼리가 하나씩 더 나간다.
 * 글이 지워지면 DB의 ON DELETE CASCADE로 같이 지워진다.
 */
@Entity
@Table(name = "apply_form")
@Getter
@Setter
@NoArgsConstructor
public class ApplyForm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false, unique = true)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Post post;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<ApplyQuestion> questions = new ArrayList<>();

    public ApplyForm(Post post, List<ApplyQuestion> questions) {
        this.post = post;
        this.questions = questions;
    }
}
