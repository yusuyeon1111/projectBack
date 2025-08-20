package org.example.project.post.repositoty;

import org.example.project.post.entity.PostPosition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PostPositionRepository extends JpaRepository<PostPosition, Long> {
    long countByPostIdAndStatus(Long postId, String status);

    long countByPostId(Long postId);

}
