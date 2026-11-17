package org.example.project.post.repositoty;

import io.lettuce.core.dynamic.annotation.Param;
import org.example.project.post.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PostRepository extends JpaRepository<Post, Long>, JpaSpecificationExecutor<Post> {
    List<Post> findByAuthor(String username);

    // 신청 처리(승인/거절)를 글 단위로 한 줄로 세우기 위해 신청이 달린 글 행을 잠근다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT pp.post FROM PostPositionMember ppm JOIN ppm.postPosition pp WHERE ppm.id = :applicationId")
    Optional<Post> findByApplicationIdForUpdate(@Param("applicationId") Long applicationId);

    // 조회수 증가
    @Modifying
    @Query("UPDATE Post p SET p.viewCount = p.viewCount + 1 WHERE p.id = :id")
    void incrementViewCount(@Param("id") Long id);

    // 좋아요 INSERT는 외래키 검사로 글 행에 공유락을 걸어서, 이어지는 like_count UPDATE(배타락)와
    // 동시 요청끼리 데드락이 난다. 글 행을 먼저 배타락으로 잡아 같은 글의 좋아요를 한 줄로 세운다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Post p WHERE p.id = :id")
    Optional<Post> findByIdForUpdate(@Param("id") Long id);

    // 엔티티 값에 +1 해서 저장하면 동시 요청끼리 서로의 증가분을 덮어쓴다. DB에서 더한다
    @Modifying
    @Query("UPDATE Post p SET p.likeCount = p.likeCount + :delta WHERE p.id = :id")
    void addLikeCount(@Param("id") Long id, @Param("delta") int delta);

    List<Post> findByPostStatusNot(String end);

    @Query("""
    SELECT p FROM Post p
    WHERE p.author = :username
    ORDER BY COALESCE(p.updated, p.created) DESC
""")
    List<Post> findByAuthorOrderByUpdatedOrCreatedDesc(@Param("username") String username);

    @Query("""
        SELECT FUNCTION('MONTH', p.created) as month, COUNT(p) as count
        FROM Post p
        WHERE FUNCTION('YEAR', p.created) = :year
        GROUP BY FUNCTION('MONTH', p.created)
        ORDER BY FUNCTION('MONTH', p.created)
    """)
    List<Object[]> countPostPerMonth(@Param("year") int year);

    @Query("""
        SELECT p.postStatus as status, COUNT(p) as count
        FROM Post p
        WHERE FUNCTION('YEAR', p.created) = :year
        GROUP BY p.postStatus
    """)
    List<Object[]> countPostGroupByStatus(@Param("year") int year);

    @Query("""
        SELECT FUNCTION('MONTH', p.created), p.category as category, COUNT(p) as count
        FROM Post p
        WHERE FUNCTION('YEAR', p.created) = :year
        GROUP BY FUNCTION('MONTH', p.created), p.category
        ORDER BY FUNCTION('MONTH', p.created)
    """)
    List<Object[]> countPostGroupByCategory(int year);

    @Modifying
    @Query("UPDATE Post p SET p.status = 'ING' WHERE p.id = :id")
    void reopen(@Param("id") Long id);
}
