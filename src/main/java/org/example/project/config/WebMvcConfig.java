package org.example.project.config;

import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final EntityManagerFactory entityManagerFactory;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("https://teamhaeyo.kro.kr")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }

    // Spring Boot 기본 OSIV(spring.jpa.open-in-view)는 끄고, 알림 구독(SSE)만 빼고 같은 것을 직접 건다.
    // OSIV는 요청이 끝날 때까지 DB 커넥션을 쥐고 있는데, SSE 요청은 최대 30분 열려 있어서
    // 구독자 수만큼 커넥션 풀(기본 10개)이 바닥난다.
    // ponytail: 다른 API는 아직 OSIV의 지연 로딩에 기대는 곳이 있을 수 있어 그대로 둔다. 전부 서비스 트랜잭션 안으로 옮기면 이 설정을 지운다.
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        OpenEntityManagerInViewInterceptor openInView = new OpenEntityManagerInViewInterceptor();
        openInView.setEntityManagerFactory(entityManagerFactory);
        registry.addWebRequestInterceptor(openInView).excludePathPatterns("/api/notifications/subscribe");
    }
}
