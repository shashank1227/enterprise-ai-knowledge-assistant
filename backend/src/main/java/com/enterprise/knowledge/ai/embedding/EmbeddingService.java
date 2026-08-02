package com.enterprise.knowledge.ai.embedding;

import com.enterprise.knowledge.config.AppProperties;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Embedding generation service using OpenAI's text-embedding-3-small model.
 * Converts text into 1536-dimensional vectors for semantic search.
 * 
 * Features:
 * - Batched embedding generation for efficiency
 * - Retry logic with exponential backoff
 * - Rate limiting to avoid OpenAI quota issues
 * - Caching for frequently embedded text (future enhancement)
 */
@Slf4j
@Service
public class EmbeddingService {

    private EmbeddingModel embeddingModel;
    private final AppProperties appProperties;

    public EmbeddingService(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    private synchronized EmbeddingModel embeddingModel() {
        if (embeddingModel != null) {
            return embeddingModel;
        }
        if (appProperties.getOpenai().getApiKey() == null
            || appProperties.getOpenai().getApiKey().isBlank()) {
            throw new IllegalStateException("OpenAI API key is not configured. Set OPENAI_API_KEY to use embeddings.");
        }
        embeddingModel = OpenAiEmbeddingModel.builder()
            .apiKey(appProperties.getOpenai().getApiKey())
            .modelName(appProperties.getOpenai().getEmbeddingModel())
            .dimensions(appProperties.getOpenai().getEmbeddingDimensions())
            .timeout(Duration.ofSeconds(appProperties.getOpenai().getTimeoutSeconds()))
            .maxRetries(appProperties.getOpenai().getMaxRetries())
            .logRequests(false)
            .logResponses(false)
            .build();
        return embeddingModel;
    }

    /**
     * Generate embedding vector for a single text string.
     * @param text The text to embed
     * @return 1536-dimensional float array
     */
    public float[] embed(String text) {
        if (text == null || text.trim().isEmpty()) {
            log.warn("Attempted to embed empty text, returning zero vector");
            return new float[appProperties.getOpenai().getEmbeddingDimensions()];
        }

        try {
            Response<Embedding> response = embeddingModel().embed(text);
            Embedding embedding = response.content();
            
            return embedding.vector();
                
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to generate embedding for text (length: {}): {}",
                text.length(), e.getMessage());
            Throwable root = e;
            while (root.getCause() != null) {
                root = root.getCause();
            }
            String detail = root.getMessage() != null ? root.getMessage() : e.getMessage();
            throw new RuntimeException("Embedding generation failed: " + detail, e);
        }
    }

    /**
     * Generate embeddings for multiple texts in a batch.
     * More efficient than calling embed() multiple times.
     * @param texts List of texts to embed
     * @return List of embedding vectors in the same order
     */
    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return new ArrayList<>();
        }

        log.info("Generating embeddings for batch of {} texts", texts.size());

        try {
            List<TextSegment> segments = texts.stream()
                .map(TextSegment::from)
                .toList();

            Response<List<Embedding>> response = embeddingModel().embedAll(segments);
            List<Embedding> embeddings = response.content();

            return embeddings.stream()
                .map(Embedding::vector)
                .toList();

        } catch (Exception e) {
            log.error("Failed to generate batch embeddings: {}", e.getMessage());
            throw new RuntimeException("Batch embedding generation failed", e);
        }
    }

    /**
     * Calculate cosine similarity between two embedding vectors.
     * Returns value between -1 and 1 (1 = identical, 0 = orthogonal, -1 = opposite).
     */
    public double cosineSimilarity(float[] vec1, float[] vec2) {
        if (vec1.length != vec2.length) {
            throw new IllegalArgumentException("Vectors must have same dimensions");
        }

        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (int i = 0; i < vec1.length; i++) {
            dotProduct += vec1[i] * vec2[i];
            norm1 += vec1[i] * vec1[i];
            norm2 += vec2[i] * vec2[i];
        }

        if (norm1 == 0.0 || norm2 == 0.0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    /**
     * Convert float array to PostgreSQL vector string format: '[0.1,0.2,0.3,...]'
     */
    public String toVectorString(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(embedding[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Parse PostgreSQL vector string back to float array.
     */
    public float[] fromVectorString(String vectorStr) {
        if (vectorStr == null || vectorStr.isEmpty()) {
            return new float[0];
        }

        String stripped = vectorStr.replaceAll("[\\[\\]]", "");
        String[] parts = stripped.split(",");
        float[] result = new float[parts.length];

        for (int i = 0; i < parts.length; i++) {
            result[i] = Float.parseFloat(parts[i].trim());
        }

        return result;
    }
}
