package com.enterprise.knowledge.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * AWS SDK configuration for S3 document storage.
 * Uses DefaultCredentialsProvider which supports:
 * - Environment variables (AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY)
 * - System properties
 * - IAM instance profile credentials (for EC2/ECS)
 * - Container credentials (for ECS tasks)
 */
@Configuration
@RequiredArgsConstructor
public class AwsConfig {

    private final AppProperties appProperties;

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
            .region(Region.of(appProperties.getStorage().getS3().getRegion()))
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
            .region(Region.of(appProperties.getStorage().getS3().getRegion()))
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build();
    }
}
