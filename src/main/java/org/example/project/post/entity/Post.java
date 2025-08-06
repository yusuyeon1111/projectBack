package org.example.project.post.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Getter
@Builder
@Entity
@ToString
@Table(name = "post")
@NoArgsConstructor
@AllArgsConstructor
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "post_id")
    private Long id;

    @Column
    private String title;

    @Column
    private String content;

    @Column
    private String projectType;

    @Column
    private String region;

    @Column
    private String author;

    @Column
    private String category;

    @Column
    private Date created;

    @Column
    private Date updated;

    @Column
    private char status;

    @OneToMany(mappedBy = "post", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PostPosition> positions = new ArrayList<>();
}
