package org.example.project.post.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Builder
@Entity
@Table(name = "post_position")
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class PostPosition {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "position_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id")
    private Post post;

    @Column
    private String role;

    @Column
    private int count;

    @OneToMany(mappedBy = "postPostion", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PostPositionMember> positionMemberList = new ArrayList<>();
}
