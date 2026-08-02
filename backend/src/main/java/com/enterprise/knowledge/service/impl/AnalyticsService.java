package com.enterprise.knowledge.service.impl;

import com.enterprise.knowledge.domain.Document;
import com.enterprise.knowledge.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Analytics service for dashboard metrics and reports.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final QueryAnalyticsRepository queryAnalyticsRepository;
    private final UserRepository userRepository;
    private final ConversationRepository conversationRepository;

    /**
     * Get high-level analytics overview for admin dashboard.
     */
    @Transactional(readOnly = true)
    public AnalyticsOverview getOverview(Instant fromDate, Instant toDate) {
        if (fromDate == null) {
            fromDate = Instant.now().minus(30, ChronoUnit.DAYS);
        }
        if (toDate == null) {
            toDate = Instant.now();
        }

        long totalDocuments = documentRepository.countActive();
        long totalChunks = chunkRepository.countAll();
        long totalQueries = queryAnalyticsRepository.countSince(fromDate);
        long totalUsers = userRepository.countActiveUsers();

        Double avgLatency = queryAnalyticsRepository.avgLatencySince(fromDate);
        Long totalTokens = queryAnalyticsRepository.sumTokensSince(fromDate);
        BigDecimal totalCost = queryAnalyticsRepository.sumCostSince(fromDate);

        // Get daily query counts for the last 7 days
        List<DailyCount> queriesLast7Days = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            Instant dayStart = Instant.now().minus(i, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS);
            Instant dayEnd = dayStart.plus(1, ChronoUnit.DAYS);
            long count = queryAnalyticsRepository.countSince(dayStart);
            queriesLast7Days.add(new DailyCount(dayStart, count));
        }

        return AnalyticsOverview.builder()
            .totalDocuments(totalDocuments)
            .totalChunks(totalChunks)
            .totalQueries(totalQueries)
            .totalUsers(totalUsers)
            .avgLatencyMs(avgLatency != null ? avgLatency.intValue() : 0)
            .totalTokensUsed(totalTokens != null ? totalTokens : 0L)
            .estimatedCostUsd(totalCost != null ? totalCost.doubleValue() : 0.0)
            .queriesLast7Days(queriesLast7Days)
            .build();
    }

    /**
     * Get most popular questions.
     */
    @Transactional(readOnly = true)
    public List<PopularQuestion> getPopularQuestions(int limit, int daysBack) {
        Instant since = Instant.now().minus(daysBack, ChronoUnit.DAYS);
        List<Object[]> results = queryAnalyticsRepository.findPopularQueries(since, limit);

        List<PopularQuestion> questions = new ArrayList<>();
        for (Object[] row : results) {
            questions.add(new PopularQuestion(
                (String) row[0],                           // query_text
                ((Number) row[1]).longValue(),             // count
                ((Number) row[2]).doubleValue()            // avg_latency
            ));
        }
        return questions;
    }

    /**
     * Get daily token usage and costs.
     */
    @Transactional(readOnly = true)
    public List<DailyTokenUsage> getDailyTokenUsage(Instant fromDate, Instant toDate) {
        if (fromDate == null) {
            fromDate = Instant.now().minus(30, ChronoUnit.DAYS);
        }

        List<Object[]> results = queryAnalyticsRepository.getDailyTokenUsage(fromDate);
        
        List<DailyTokenUsage> usage = new ArrayList<>();
        for (Object[] row : results) {
            usage.add(new DailyTokenUsage(
                row[0].toString(),                        // day
                ((Number) row[1]).longValue(),            // prompt_tokens
                ((Number) row[2]).longValue(),            // completion_tokens
                ((Number) row[3]).longValue(),            // total_tokens
                ((Number) row[4]).doubleValue()           // cost
            ));
        }
        return usage;
    }

    /**
     * Get document statistics.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getDocumentStats() {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("total", documentRepository.countActive());
        stats.put("indexed", documentRepository.countByStatus(Document.DocumentStatus.INDEXED));
        stats.put("processing", documentRepository.countByStatus(Document.DocumentStatus.PROCESSING));
        stats.put("failed", documentRepository.countByStatus(Document.DocumentStatus.FAILED));
        stats.put("totalSizeBytes", documentRepository.sumFileSizeBytes());
        
        return stats;
    }

    // DTOs

    @lombok.Data
    @lombok.Builder
    public static class AnalyticsOverview {
        private long totalDocuments;
        private long totalChunks;
        private long totalQueries;
        private long totalUsers;
        private int avgLatencyMs;
        private long totalTokensUsed;
        private double estimatedCostUsd;
        private List<DailyCount> queriesLast7Days;
    }

    public record DailyCount(Instant date, long count) {}

    public record PopularQuestion(String query, long count, double avgLatencyMs) {}

    public record DailyTokenUsage(
        String date,
        long promptTokens,
        long completionTokens,
        long totalTokens,
        double estimatedCostUsd
    ) {}
}
