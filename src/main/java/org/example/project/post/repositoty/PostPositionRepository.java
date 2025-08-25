package org.example.project.post.repositoty;

import io.lettuce.core.dynamic.annotation.Param;
import org.example.project.post.entity.PostPosition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PostPositionRepository extends JpaRepository<PostPosition, Long> {
    long countByPostIdAndStatus(Long postId, String status);

    long countByPostId(Long postId);

    @Query("""
        SELECT FUNCTION('MONTH', p.created) as month, SUM(pp.count) as count
        FROM PostPosition pp
        JOIN pp.post p
        WHERE FUNCTION('YEAR', p.created) = :year
        GROUP BY FUNCTION('MONTH', p.created)
        ORDER BY FUNCTION('MONTH', p.created)
    """)
    List<Object[]> sumPostPositionCountPerMonth(@Param("year") int year);

}
