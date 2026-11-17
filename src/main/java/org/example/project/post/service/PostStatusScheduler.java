package org.example.project.post.service;

import org.example.project.post.entity.Post;
import org.example.project.post.repositoty.PostRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
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
        posts.forEach(post -> post.refreshPostStatus(today));
        postRepository.saveAll(posts);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void updateStatusOnStartup() {
        updatePostStatus();
    }
}
