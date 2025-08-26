package org.example.project.post.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Getter
@Setter
@Builder
@Entity
@ToString
@Table(name = "post")
@NoArgsConstructor
@AllArgsConstructor
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")  // DB 컬럼명 맞춤
    private Long id;

    @Column
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column
    private String projectType;

    @Column
    private String region;

    @Column
    private String author;

    @Column
    private String nickname;

    @Column(name = "category")
    private String category;

    @Column
    private Date created;

    @Column
    private Date updated;

    @Column
    private String status;

    @Column
    private Integer likeCount = 0;

    @Column
    private Integer viewCount = 0;

    @Column
    private Date startDate;

    @Column
    private Date endDate;

    @Column
    private String postStatus;

    @Column(nullable = false, columnDefinition = "char(1) default 'N'")
    private String delYn = "N";

    @OneToMany(mappedBy = "post", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PostPosition> positions = new ArrayList<>();

    @OneToMany(mappedBy = "post",fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PostStack> techStacks = new ArrayList<>();

    @OneToMany(mappedBy = "post", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PostLike> likes = new ArrayList<>();
}
