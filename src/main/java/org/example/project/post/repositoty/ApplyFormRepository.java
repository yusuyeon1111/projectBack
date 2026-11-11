package org.example.project.post.repositoty;

import org.example.project.post.entity.ApplyForm;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ApplyFormRepository extends JpaRepository<ApplyForm, Long> {
    Optional<ApplyForm> findByPostId(Long postId);
}
