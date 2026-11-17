package org.example.project.member.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor // 재발급 유예 기간에 Redis에 둔 토큰 쌍을 다시 읽는다
public class JwtToken {
    private String grantType;

    private String accessToken;

    private String refreshToken;

    private Long refreshTokenExpiresIn;

    private List<String> roles;
}
