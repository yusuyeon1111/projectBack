package org.example.project.post.repositoty;

import io.lettuce.core.dynamic.annotation.Param;
import jakarta.persistence.Column;
import org.example.project.member.entity.Member;
import org.example.project.post.dto.ApplyMemeberDto;
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
        ORDER BY ppm.id DESC
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

    @Query("""
        SELECT new org.example.project.post.dto.ApplyMemeberDto (
        m.nickname,
        m.id,
        p.id,
        pp.id,
        ppm.id,
        p.category,
        pp.role,
        ppm.status,
        ppm.appliedAt,
        ppm.acceptedAt,
        ppm.rejectedAt
        )
        FROM PostPositionMember ppm
            JOIN ppm.member m
            JOIN ppm.postPosition pp
            JOIN pp.post p
        WHERE p.id = :postId
        ORDER BY ppm.acceptedAt DESC
""")
    List<ApplyMemeberDto> findPostPositionMemberByPostId(@Param("postId") Long postId);

    @Query("""
        SELECT FUNCTION('MONTH', ppm.acceptedAt) as month, COUNT(p) as count
        FROM PostPositionMember ppm
        JOIN ppm.postPosition pp
        JOIN pp.post p
        WHERE FUNCTION('YEAR', ppm.acceptedAt) = :year
        AND ppm.status = 'ACCEPT'
        GROUP BY FUNCTION('MONTH', ppm.acceptedAt)
        ORDER BY FUNCTION('MONTH', ppm.acceptedAt)
    """)
    List<Object[]> countAcceptMonth(@Param("year") int year);

    @Query("""
        SELECT FUNCTION('MONTH', ppm.appliedAt) as month, COUNT(p) as count
        FROM PostPositionMember ppm
        JOIN ppm.postPosition pp
        JOIN pp.post p
        WHERE FUNCTION('YEAR', ppm.appliedAt) = :year
        AND ppm.status = 'APPLY'
        GROUP BY FUNCTION('MONTH', ppm.appliedAt)
        ORDER BY FUNCTION('MONTH', ppm.appliedAt)
    """)
    List<Object[]> countApplyMonth(@Param("year") int year);

    @Query("""
        SELECT FUNCTION('MONTH', ppm.rejectedAt) as month, COUNT(p) as count
        FROM PostPositionMember ppm
        JOIN ppm.postPosition pp
        JOIN pp.post p
        WHERE FUNCTION('YEAR', ppm.rejectedAt) = :year
        AND ppm.status = 'REJECT'
        GROUP BY FUNCTION('MONTH', ppm.rejectedAt)
        ORDER BY FUNCTION('MONTH', ppm.rejectedAt)
    """)
    List<Object[]> countRejectMonth(int year);
}
