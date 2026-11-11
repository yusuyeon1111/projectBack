package org.example.project.post.service;

import com.amazonaws.HttpMethod;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.ResponseHeaderOverrides;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

/**
 * 신청서 첨부 파일을 S3에 두고 꺼내는 일.
 * 서버는 파일 내용을 받지 않는다. 브라우저가 presigned URL로 직접 올리고 받는다.
 * 프로필 이미지와 달리 공개 URL을 쓰지 않는다. 이력서·포트폴리오라서 권한 확인 뒤 짧게 유효한 URL만 내준다.
 */
@Component
public class ApplyFileStorage {

    private static final long UPLOAD_URL_MINUTES = 10;
    private static final long DOWNLOAD_URL_MINUTES = 5;

    private final AmazonS3 s3;
    private final String bucket;

    public ApplyFileStorage(@Value("${aws.s3.access-key-id}") String accessKeyId,
                            @Value("${aws.s3.secret-access-key}") String secretAccessKey,
                            @Value("${aws.s3.region}") String region,
                            @Value("${aws.s3.bucket-name}") String bucket) {
        this.s3 = AmazonS3ClientBuilder.standard()
                .withRegion(region)
                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials(accessKeyId, secretAccessKey)))
                .build();
        this.bucket = bucket;
    }

    public String uploadUrl(String key) {
        return presign(new GeneratePresignedUrlRequest(bucket, key).withMethod(HttpMethod.PUT), UPLOAD_URL_MINUTES);
    }

    /** 받을 때 원래 파일명으로 저장되게 Content-Disposition을 붙인다. */
    public String downloadUrl(String key, String filename) {
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucket, key)
                .withMethod(HttpMethod.GET)
                .withResponseHeaders(new ResponseHeaderOverrides()
                        .withContentDisposition("attachment; filename*=UTF-8''" + encoded));
        return presign(request, DOWNLOAD_URL_MINUTES);
    }

    /** 실제로 올라간 파일의 크기. 올라가지 않았으면 비어 있다. */
    public Optional<Long> sizeOf(String key) {
        try {
            return Optional.of(s3.getObjectMetadata(bucket, key).getContentLength());
        } catch (AmazonS3Exception e) {
            if (e.getStatusCode() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    private String presign(GeneratePresignedUrlRequest request, long minutes) {
        request.setExpiration(new Date(System.currentTimeMillis() + minutes * 60_000));
        return s3.generatePresignedUrl(request).toString();
    }
}
