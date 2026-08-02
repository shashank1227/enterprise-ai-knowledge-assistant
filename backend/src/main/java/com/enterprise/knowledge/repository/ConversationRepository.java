package com.enterprise.knowledge.repository;

import com.enterprise.knowledge.domain.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    @Query("""
        SELECT c FROM Conversation c
        WHERE c.user.id = :userId
          AND c.isArchived = false
          AND (:pinnedOnly = false OR c.isPinned = true)
        ORDER BY c.isPinned DESC, c.lastMessageAt DESC NULLS LAST
        """)
    Page<Conversation> findByUserId(
        @Param("userId")     UUID userId,
        @Param("pinnedOnly") boolean pinnedOnly,
        Pageable pageable
    );

    Optional<Conversation> findByIdAndUserId(UUID id, UUID userId);

    @Query("SELECT COUNT(c) FROM Conversation c WHERE c.user.id = :userId")
    long countByUserId(@Param("userId") UUID userId);
}
