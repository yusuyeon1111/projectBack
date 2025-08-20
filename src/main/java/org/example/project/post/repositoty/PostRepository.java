package org.example.project.post.repositoty;

import io.lettuce.core.dynamic.annotation.Param;
import org.example.project.post.dto.PostResponseDto;
import org.example.project.post.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Range;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.security.core.parameters.P;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {
    List<Post> findByAuthor(String username);

    // 조회수 증가
    @Modifying
    @Query("UPDATE Post p SET p.viewCount = p.viewCount + 1 WHERE p.id = :id")
    void incrementViewCount(@Param("id") Long id);

    List<Post> findByPostStatusNot(String end);

    @Query("""
    SELECT DISTINCT p FROM Post p
    LEFT JOIN p.positions pos
    LEFT JOIN p.techStacks tech
    WHERE (:category IS NULL OR :category = '' OR p.category = :category)
      AND (:status IS NULL OR :status = '' OR p.status = :status)
      AND (:postStatus IS NULL OR :postStatus = '' OR p.postStatus = :postStatus)
      AND (:projectType IS NULL OR :projectType = '' OR p.projectType = :projectType)
      AND (:region IS NULL OR :region = '' OR p.region = :region)
      AND (:position IS NULL OR :position = '' OR pos.role = :position)
      AND (:keyword IS NULL OR :keyword = '' OR p.title LIKE %:keyword% OR p.content LIKE %:keyword% OR p.author LIKE %:keyword%)
      AND (:stackKeyword IS NULL OR :stackKeyword = '' OR tech.techStack LIKE %:stackKeyword%)
""")
    Page<Post> findPostsWithFilter(
            @Param("category") String category,
            @Param("status") String status,
            @Param("postStatus") String postStatus,
            @Param("projectType") String projectType,
            @Param("region") String region,
            @Param("position") String position,
            @Param("keyword") String keyword,
            @Param("stackKeyword") String stackKeyword,
            Pageable pageable
    );

    @Query("""
    SELECT p FROM Post p
    WHERE p.author = :username
    ORDER BY COALESCE(p.updated, p.created) DESC
""")
    List<Post> findByAuthorOrderByUpdatedOrCreatedDesc(@Param("username") String username);




}
