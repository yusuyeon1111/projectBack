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
// 목록 정렬(최신순·인기순)을 인덱스 순서대로 읽어 앞쪽 몇 건에서 멈추게 한다
@Table(name = "post", indexes = {
        @Index(name = "idx_post_last_modified", columnList = "last_modified, id"),
        @Index(name = "idx_post_like_count", columnList = "like_count, id"),
})
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

    // 최신순 정렬 기준(수정했으면 수정일, 아니면 작성일). DB가 계산하는 생성 컬럼이라 앱은 쓰지 않는다.
    // COALESCE 식으로 정렬하면 인덱스를 못 써서 매번 전체 글을 정렬했다.
    @Column(insertable = false, updatable = false,
            columnDefinition = "datetime(6) generated always as (coalesce(updated, created)) virtual")
    private Date lastModified;

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
