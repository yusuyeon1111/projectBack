package org.example.project.team.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

@Entity
@Table(name = "task")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Task {

    public static final String TODO = "TODO";
    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String DONE = "DONE";
    public static final Set<String> STATUSES = Set.of(TODO, IN_PROGRESS, DONE);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long teamId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private String status;

    private Long assigneeMemberId;

    @Column(nullable = false)
    private Long creatorMemberId;

    private LocalDate dueDate;

    // 같은 업무를 두 사람이 동시에 고치면 나중 저장이 앞의 수정을 덮지 않고 409가 된다
    @Version
    private Long version;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public Task(Long teamId, Long creatorMemberId, String title, String description, Long assigneeMemberId, LocalDate dueDate) {
        this.teamId = teamId;
        this.creatorMemberId = creatorMemberId;
        this.status = TODO;
        this.createdAt = LocalDateTime.now();
        edit(title, description, assigneeMemberId, dueDate);
    }

    public void edit(String title, String description, Long assigneeMemberId, LocalDate dueDate) {
        this.title = title;
        this.description = description;
        this.assigneeMemberId = assigneeMemberId;
        this.dueDate = dueDate;
        this.updatedAt = LocalDateTime.now();
    }
}
