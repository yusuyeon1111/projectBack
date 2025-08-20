package org.example.project.post.service;

import org.example.project.post.entity.Post;
import org.example.project.post.repositoty.PostRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

@Component
public class PostStatusScheduler {

    private final PostRepository postRepository;

    public PostStatusScheduler(PostRepository postRepository) {
        this.postRepository = postRepository;
    }

    @Scheduled(cron = "0 0 0 * * ?")
    public void updatePostStatus() {
        Date today = new Date();
        List<Post> posts = postRepository.findByPostStatusNot("END");
        for (Post post : posts) {
            if (post.getStartDate() != null && post.getStartDate().after(today)) {
                post.setPostStatus("SCHEDULED"); // 시작 전
            } else if (post.getEndDate() != null && post.getEndDate().before(today)) {
                post.setPostStatus("END"); // 종료됨
            } else {
                post.setPostStatus("IN_PROGRESS"); // 시작 후, 종료 전
            }
        }
        postRepository.saveAll(posts);
    }
}
