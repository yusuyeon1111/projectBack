package org.example.project.config;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 자격 증명은 코드에 두지 않고 SDK 기본 체인에 맡긴다.
 * 운영 EC2에서는 인스턴스 역할(teamhaeyo-ec2)을, 로컬에서는 환경변수(AWS_ACCESS_KEY_ID 등)를 쓴다.
 * 키는 요청할 때 찾으므로 키가 없는 테스트 환경에서도 기동은 된다.
 */
@Configuration
public class S3Config {

    @Bean
    public AmazonS3 amazonS3(@Value("${aws.s3.region}") String region) {
        return AmazonS3ClientBuilder.standard().withRegion(region).build();
    }
}
