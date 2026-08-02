package com.enterprise.knowledge.ai.rag;

import com.enterprise.knowledge.ai.embedding.EmbeddingService;
import com.enterprise.knowledge.repository.DocumentChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Vector similarity search service using pgvector.
 * Provides semantic search over document chunks using cosine similarity.
 * 
 * Search modes:
 * - VECTOR: Pure semantic search using embeddings
 * - KEYWORD: Traditional full-text search
 * - HYBRID: Combines both using Reciprocal Rank Fusion
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorSearchService {

    private final DocumentChunkRepository chunkRepository;
    private final EmbeddingService embeddingService;

    /**
     * Perform vector similarity search.
     * @param queryText The search query
     * @param topK Number of results to return
     * @param mode Search mode (VECTOR, KEYWORD, or HYBRID)
     * @return List of relevant chunks ordered by relevance
     */
    public List<SearchResult> search(String queryText, int topK, SearchMode mode) {
        long startTime = System.currentTimeMillis();

        // Generate query embedding
        float[] queryEmbedding = embeddingService.embed(queryText);
        String embeddingJson = embeddingService.toVectorString(queryEmbedding);

        List<SearchResult> results = switch (mode) {
            case VECTOR -> vectorSearch(embeddingJson, topK);
            case KEYWORD -> keywordSearch(queryText, topK);
            case HYBRID -> hybridSearch(embeddingJson, queryText, topK);
        };

        long elapsedMs = System.currentTimeMillis() - startTime;
        log.info("Search completed: mode={}, query='{}', results={}, latency={}ms", 
            mode, queryText.substring(0, Math.min(50, queryText.length())), results.size(), elapsedMs);

        return results;
    }

    /**
     * Pure vector similarity search using cosine distance.
     */
    private List<SearchResult> vectorSearch(String embeddingJson, int topK) {
        List<Object[]> rows = chunkRepository.vectorSearch(embeddingJson, topK);
        return parseSearchResults(rows);
    }

    /**
     * Full-text keyword search using PostgreSQL's tsvector.
     */
    private List<SearchResult> keywordSearch(String queryText, int topK) {
        List<Object[]> rows = chunkRepository.keywordSearch(queryText, topK);
        return parseSearchResults(rows);
    }

    /**
     * Hybrid search combining vector and keyword using RRF.
     * Calls the database function that implements Reciprocal Rank Fusion.
     */
    private List<SearchResult> hybridSearch(String embeddingJson, String queryText, int topK) {
        List<Object[]> rows = chunkRepository.hybridSearch(embeddingJson, queryText, topK);
        
        List<SearchResult> results = new ArrayList<>();
        for (Object[] row : rows) {
            results.add(SearchResult.builder()
                .chunkId((UUID) row[0])
                .documentId((UUID) row[1])
                .content((String) row[2])
                .sectionTitle((String) row[3])
                .pageNumber((Integer) row[4])
                .vectorScore(((Number) row[5]).floatValue())
                .textScore(((Number) row[6]).floatValue())
                .hybridScore(((Number) row[7]).floatValue())
                .build());
        }
        
        return results;
    }

    /**
     * Parse native query results into SearchResult objects.
     */
    private List<SearchResult> parseSearchResults(List<Object[]> rows) {
        List<SearchResult> results = new ArrayList<>();
        
        for (Object[] row : rows) {
            SearchResult result = SearchResult.builder()
                .chunkId((UUID) row[0])
                .documentId((UUID) row[1])
                .content((String) row[2])
                .sectionTitle((String) row[3])
                .pageNumber((Integer) row[4])
                .build();
            
            // row[6] contains the score
            if (row.length > 6 && row[6] instanceof Number) {
                float score = ((Number) row[6]).floatValue();
                result.setVectorScore(score);
                result.setHybridScore(score);
            }
            
            results.add(result);
        }
        
        return results;
    }

    /**
     * Search mode enum.
     */
    public enum SearchMode {
        VECTOR, KEYWORD, HYBRID
    }

    /**
     * Search result containing chunk content and metadata.
     */
    @lombok.Data
    @lombok.Builder
    public static class SearchResult {
        private UUID chunkId;
        private UUID documentId;
        private String content;
        private String sectionTitle;
        private Integer pageNumber;
        private float vectorScore;
        private float textScore;
        private float hybridScore;

        public float getRelevanceScore() {
            // Return the most relevant score based on what's available
            if (hybridScore > 0) return hybridScore;
            if (vectorScore > 0) return vectorScore;
            return textScore;
        }
    }
}
