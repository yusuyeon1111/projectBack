package org.example.project.post.repositoty;

import org.example.project.post.dto.PostLikeDto;
import org.example.project.post.dto.PostResponseDto;
import org.example.project.post.entity.PostLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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
    """
    )
    List<PostResponseDto> findAllByUsername(String username);

    Optional<Object> findByPostIdAndMemberId(Long postId, Long id);

    void deleteByPostIdAndMemberId(Long postId, Long id);
}
