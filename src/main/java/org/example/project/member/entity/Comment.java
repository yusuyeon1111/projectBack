package org.example.project.member.entity;

import jakarta.persistence.*;
import lombok.*;
import org.checkerframework.checker.units.qual.A;

import java.util.Date;

@Entity
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Table(name = "comment")
public class Comment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column
    private Long postId;

    @Column
    private String content;

    @Column
    private String author;

    @Column
    private Date created;
}
