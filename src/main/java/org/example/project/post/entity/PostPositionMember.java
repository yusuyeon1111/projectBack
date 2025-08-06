package org.example.project.post.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.project.member.entity.Member;

@Entity
@Table(name = "post_position_member")
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
    private String status;
}
