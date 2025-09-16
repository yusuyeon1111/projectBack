package org.example.project.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisTest {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @PostConstruct
    public void testRedis() {
        try {
            redisTemplate.opsForValue().set("testKey", "hello");
            String value = redisTemplate.opsForValue().get("testKey");
            System.out.println("✅ Redis 연결 성공: " + value);
        } catch (Exception e) {
            System.out.println("❌ Redis 연결 실패: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
