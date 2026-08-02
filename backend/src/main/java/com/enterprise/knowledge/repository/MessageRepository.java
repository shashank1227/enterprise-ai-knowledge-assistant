package com.enterprise.knowledge.repository;

import com.enterprise.knowledge.domain.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MessageRepository extends JpaRepository<Message, UUID> {

    /**
     * Fetch all messages in a conversation ordered by creation time.
     */
    List<Message> findAllByConversationIdOrderByCreatedAtAsc(UUID conversationId);

    /**
     * Fetch the last N messages in a conversation for building conversation history.
     * Used by RAG pipeline to provide context for multi-turn conversations.
     */
    @Query("""
        SELECT m FROM Message m
        WHERE m.conversation.id = :conversationId
        ORDER BY m.createdAt DESC
        LIMIT :limit
        """)
    List<Message> findLastNMessages(
        @Param("conversationId") UUID conversationId,
        @Param("limit") int limit
    );

    /**
     * Count messages in a conversation.
     */
    long countByConversationId(UUID conversationId);

    /**
     * Delete all messages in a conversation.
     */
    void deleteAllByConversationId(UUID conversationId);
}
