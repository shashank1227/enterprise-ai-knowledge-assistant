package com.enterprise.knowledge.repository;

import com.enterprise.knowledge.domain.MessageFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MessageFeedbackRepository extends JpaRepository<MessageFeedback, UUID> {

    Optional<MessageFeedback> findByMessageIdAndUserId(UUID messageId, UUID userId);

    boolean existsByMessageIdAndUserId(UUID messageId, UUID userId);
}
