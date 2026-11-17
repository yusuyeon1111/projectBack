package org.example.project.team.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.project.member.entity.Member;

import java.time.LocalDateTime;

@Entity
@Table(name = "task_comment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaskComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long taskId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_member_id")
    private Member author;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public TaskComment(Long taskId, Member author, String content) {
        this.taskId = taskId;
        this.author = author;
        this.content = content;
        this.createdAt = LocalDateTime.now();
    }
}
