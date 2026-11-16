package org.example.project.post.repositoty;

import org.example.project.post.dto.PostLikeDto;
import org.example.project.post.dto.PostResponseDto;
import org.example.project.post.entity.PostLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PostLikeRepository extends JpaRepository<PostLike, Long> {
    @Query("""
        SELECT new org.example.project.post.dto.PostResponseDto(p)
        FROM PostLike pl
            JOIN pl.post p
            JOIN pl.member m
        WHERE m.username = :username
        ORDER BY p.id DESC
    """
    )
    List<PostResponseDto> findAllByUsername(String username);

    Optional<Object> findByPostIdAndMemberId(Long postId, Long id);

    // (post_id, member_id) 유니크 제약에 걸리면 0행. 먼저 조회하고 넣으면 동시 요청이 둘 다 '없음'을 본다
    @Modifying
    @Query(value = "INSERT IGNORE INTO post_like (post_id, member_id, created) VALUES (:postId, :memberId, NOW())",
            nativeQuery = true)
    int insertIfAbsent(@Param("postId") Long postId, @Param("memberId") Long memberId);

    @Modifying
    @Query("DELETE FROM PostLike pl WHERE pl.post.id = :postId AND pl.member.id = :memberId")
    int deleteByPostIdAndMemberId(@Param("postId") Long postId, @Param("memberId") Long memberId);
}
