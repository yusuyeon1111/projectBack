package org.example.project.config;

import lombok.RequiredArgsConstructor;
import org.example.project.member.service.CustomerOAuth2UserService;
import org.example.project.security.JwtAuthenticationFilter;
import org.example.project.security.JwtTokenProvider;
import org.example.project.security.OAuth2SuccessHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;
    private final RedisTemplate<String, Object> redisTemplate;

    // 비밀번호 암호화 설정
    @Bean
    public BCryptPasswordEncoder bCryptPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, CustomerOAuth2UserService customerOAuth2UserService, OAuth2SuccessHandler oAuth2SuccessHandler) throws Exception {
        http.formLogin(AbstractHttpConfigurer::disable);
        http.csrf(AbstractHttpConfigurer::disable);
        http.httpBasic(AbstractHttpConfigurer::disable);
        http.authorizeHttpRequests((auth)->auth
                .requestMatchers("/api/member/signup","/","/api/member/signin","/oauth2/**").permitAll()
                .anyRequest().authenticated());
        http.sessionManagement((session)->session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // 세션정책설정
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(info -> info.userService(customerOAuth2UserService))
                        .successHandler(oAuth2SuccessHandler)
                )
                // 커스텀 필터 등록 - 기존의 인증 필터가 처리하기 전에 JWT로 인증처리
        .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider, redisTemplate), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
