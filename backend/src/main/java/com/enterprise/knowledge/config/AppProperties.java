package com.enterprise.knowledge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Centralized application configuration properties mapped from application.yml.
 * Enables type-safe configuration access throughout the application.
 */
@Data
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String baseUrl;
    private String frontendUrl;
    private JwtConfig jwt;
    private OpenAiConfig openai;
    private RagConfig rag;
    private StorageConfig storage;
    private RateLimitConfig rateLimit;
    private SecurityConfig security;

    @Data
    public static class JwtConfig {
        private String secret;
        private long accessTokenExpiryMs;
        private long refreshTokenExpiryMs;
    }

    @Data
    public static class OpenAiConfig {
        private String apiKey;
        private String chatModel;
        private String embeddingModel;
        private int embeddingDimensions;
        private int maxTokens;
        private double temperature;
        private int timeoutSeconds;
        private int maxRetries;
    }

    @Data
    public static class RagConfig {
        private int chunkSizeTokens;
        private int chunkOverlapTokens;
        private int topKRetrieval;
        private int maxContextTokens;
        private String searchMode;
        private double minRelevanceScore;
        private double confidenceThreshold;
    }

    @Data
    public static class StorageConfig {
        private String type;
        private S3Config s3;
        private LocalConfig local;

        @Data
        public static class S3Config {
            private String bucket;
            private String region;
            private String prefix;
            private int presignedUrlExpiryMinutes;
        }

        @Data
        public static class LocalConfig {
            private String uploadDir;
        }
    }

    @Data
    public static class RateLimitConfig {
        private boolean enabled;
        private ChatLimit chat;
        private UploadLimit upload;
        private GlobalLimit global;

        @Data
        public static class ChatLimit {
            private int requestsPerMinute;
        }

        @Data
        public static class UploadLimit {
            private int requestsPerMinute;
        }

        @Data
        public static class GlobalLimit {
            private int requestsPerMinute;
        }
    }

    @Data
    public static class SecurityConfig {
        private CorsConfig cors;
        private OAuth2Config oauth2;

        @Data
        public static class CorsConfig {
            private List<String> allowedOrigins;
            private String allowedMethods;
            private String allowedHeaders;
            private boolean allowCredentials;
            private long maxAge;
        }

        @Data
        public static class OAuth2Config {
            private boolean enabled;
            private GoogleConfig google;

            @Data
            public static class GoogleConfig {
                private String clientId;
                private String clientSecret;
            }
        }
    }
}
