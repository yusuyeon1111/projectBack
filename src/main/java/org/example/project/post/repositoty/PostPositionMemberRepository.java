package org.example.project.post.repositoty;

import io.lettuce.core.dynamic.annotation.Param;
import jakarta.persistence.Column;
import org.example.project.member.entity.Member;
import org.example.project.post.dto.ApplyResponseDto;
import org.example.project.post.dto.PostAcceptDto;
import org.example.project.post.entity.PostPosition;
import org.example.project.post.entity.PostPositionMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PostPositionMemberRepository extends JpaRepository<PostPositionMember, Long> {
    boolean existsByPostPositionAndMember(PostPosition position, Member member);

    List<PostPositionMember> findByMemberUsernameAndPostPosition_Post_Id(String username, Long postId);

    @Query("""
            SELECT ppm, m
            FROM PostPositionMember ppm
             JOIN ppm.member m
             JOIN ppm.postPosition pp
            WHERE pp.post.id = :postId
            """)
    List<Object[]> findByPostId(@Param("postId") Long postId);

    @Query("""
    SELECT COUNT(ppm)
    FROM PostPositionMember ppm
    WHERE ppm.postPosition.id = :postPositionId
    AND ppm.status = 'ACCEPT'
    """)
    Long findCountByPostPositionId(@Param("postPositionId") Long postPositionId);

    @Query("""
        SELECT new org.example.project.post.dto.ApplyResponseDto(
            ppm.id,
            m.id,
            m.username,
            pp.id,
            pp.role,
            ppm.status,
            pp.status,
            p.id,
            p.title,
            p.category,
            ppm.appliedAt,
            ppm.acceptedAt,
            ppm.rejectedAt
            )
        FROM PostPositionMember ppm
            JOIN ppm.member m
            JOIN ppm.postPosition pp
            JOIN pp.post p
        WHERE ppm.member.username = :username
    """)
    List<ApplyResponseDto> findAllByMemberUsername(@Param("username") String username);

    @Query("""
    SELECT new org.example.project.post.dto.PostAcceptDto(
        p.id,
        p.title,
        ppm.appliedAt,
        p.startDate,
        p.endDate,
        p.postStatus
    )
    FROM PostPositionMember ppm
        JOIN ppm.member m
        JOIN ppm.postPosition pp
        JOIN pp.post p
    WHERE ppm.member.username = :username
    AND ppm.status = 'ACCEPT'
""")
    List<PostAcceptDto> findAcceptedPostsByUsername(@Param("username") String username);
}
