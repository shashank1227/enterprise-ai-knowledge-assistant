package com.enterprise.knowledge.repository;

import com.enterprise.knowledge.domain.QueryAnalytics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface QueryAnalyticsRepository extends JpaRepository<QueryAnalytics, UUID> {

    @Query("SELECT COUNT(qa) FROM QueryAnalytics qa WHERE qa.createdAt >= :since")
    long countSince(@Param("since") Instant since);

    @Query("SELECT AVG(qa.totalLatencyMs) FROM QueryAnalytics qa WHERE qa.createdAt >= :since")
    Double avgLatencySince(@Param("since") Instant since);

    @Query("SELECT SUM(qa.totalTokens) FROM QueryAnalytics qa WHERE qa.createdAt >= :since")
    Long sumTokensSince(@Param("since") Instant since);

    @Query("SELECT SUM(qa.estimatedCostUsd) FROM QueryAnalytics qa WHERE qa.createdAt >= :since")
    BigDecimal sumCostSince(@Param("since") Instant since);

    @Query(value = """
        SELECT query_text, COUNT(*) as count, AVG(total_latency_ms) as avg_latency
        FROM query_analytics
        WHERE created_at >= :since
        GROUP BY query_text
        ORDER BY count DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<Object[]> findPopularQueries(@Param("since") Instant since, @Param("limit") int limit);

    @Query(value = """
        SELECT DATE(created_at) as day,
               SUM(prompt_tokens) as prompt_tokens,
               SUM(completion_tokens) as completion_tokens,
               SUM(total_tokens) as total_tokens,
               SUM(estimated_cost_usd) as cost
        FROM query_analytics
        WHERE created_at >= :since
        GROUP BY DATE(created_at)
        ORDER BY day DESC
        """, nativeQuery = true)
    List<Object[]> getDailyTokenUsage(@Param("since") Instant since);
}
