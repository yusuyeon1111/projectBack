package org.example.project.config;

import lombok.RequiredArgsConstructor;
import org.example.project.member.service.CustomerOAuth2UserService;
import org.example.project.security.JwtAuthenticationFilter;
import org.example.project.security.JwtTokenProvider;
import org.example.project.security.OAuth2SuccessHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
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
        http.cors(Customizer.withDefaults());
        // 공개 API만 명시하고 나머지는 모두 인증 필요. 사용자 식별은 요청값이 아닌 토큰(Principal)으로 한다.
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/error", "/oauth2/**", "/login/oauth2/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/member/signup", "/api/member/signin", "/api/post/list").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/member/idCheck", "/api/member/emlCheck", "/api/post/view/**").permitAll()
                // EventSource는 Authorization 헤더를 못 붙여서 1회용 티켓으로 컨트롤러에서 인증한다
                .requestMatchers(HttpMethod.GET, "/api/notifications/subscribe").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
        );
        // 미인증 요청은 OAuth 로그인 페이지로 302 리다이렉트하지 말고 401로 응답
        http.exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
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
