package org.example.project.post.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.project.member.entity.Member;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "post_position_member",
        uniqueConstraints = @UniqueConstraint(name = "uk_position_member", columnNames = {"post_position_id", "member_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PostPositionMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_position_id")
    private PostPosition postPosition;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    @Column
    private String status; // 신청 중 apply 수락 accept 거절 reject

    @Column(name = "applied_at")
    private LocalDateTime appliedAt; // 신청 일자

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt; //수락 일자

    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt; // 거절 일자

    // 신청서 답변. 질문 내용을 복사해 두어 글의 질문이 바뀌어도 제출 당시 그대로 보인다. 질문 없는 글이면 null.
    @JdbcTypeCode(SqlTypes.JSON)
    private List<ApplyAnswer> answers;

}
