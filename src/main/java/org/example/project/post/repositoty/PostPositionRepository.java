package org.example.project.post.repositoty;

import io.lettuce.core.dynamic.annotation.Param;
import org.example.project.post.entity.PostPosition;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PostPositionRepository extends JpaRepository<PostPosition, Long> {
    long countByPostIdAndStatus(Long postId, String status);

    long countByPostId(Long postId);

    // 신청은 트랜잭션 없이 돌아서 지연 로딩을 못 하므로 글(작성자·진행 상태)을 함께 읽는다
    @EntityGraph(attributePaths = "post")
    Optional<PostPosition> findWithPostById(Long id);

    @Query("""
        SELECT FUNCTION('MONTH', p.created) as month, SUM(pp.count) as count
        FROM PostPosition pp
        JOIN pp.post p
        WHERE FUNCTION('YEAR', p.created) = :year
        GROUP BY FUNCTION('MONTH', p.created)
        ORDER BY FUNCTION('MONTH', p.created)
    """)
    List<Object[]> sumPostPositionCountPerMonth(@Param("year") int year);

    // 승인된 사람이 빠지면 그 포지션은 정원 아래가 되므로 다시 연다
    @Modifying
    @Query(value = "UPDATE post_position SET status = 'ING' WHERE position_id = "
            + "(SELECT post_position_id FROM post_position_member WHERE id = :applicationId)", nativeQuery = true)
    void reopenByApplicationId(@Param("applicationId") Long applicationId);
}
