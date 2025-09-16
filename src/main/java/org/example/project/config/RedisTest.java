package org.example.project.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisTest {

    private static final Logger log = LoggerFactory.getLogger(RedisTest.class);

    private final StringRedisTemplate redisTemplate;

    public RedisTest(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @PostConstruct
    public void testRedis() {
        try {
            redisTemplate.opsForValue().set("testKey", "hello");
            String value = redisTemplate.opsForValue().get("testKey");
            log.info("✅ Redis 연결 성공: {}", value);
        } catch (Exception e) {
            log.error("❌ Redis 연결 실패", e);
        }
    }
}

