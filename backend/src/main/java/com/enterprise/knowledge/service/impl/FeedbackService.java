package com.enterprise.knowledge.service.impl;

import com.enterprise.knowledge.domain.Message;
import com.enterprise.knowledge.domain.MessageFeedback;
import com.enterprise.knowledge.domain.User;
import com.enterprise.knowledge.dto.request.FeedbackRequest;
import com.enterprise.knowledge.exception.ResourceNotFoundException;
import com.enterprise.knowledge.repository.MessageFeedbackRepository;
import com.enterprise.knowledge.repository.MessageRepository;
import com.enterprise.knowledge.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Feedback service for collecting user ratings on AI responses.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final MessageRepository messageRepository;
    private final MessageFeedbackRepository feedbackRepository;
    private final SecurityUtils securityUtils;

    @Transactional
    public void submitFeedback(UUID messageId, FeedbackRequest request) {
        User user = securityUtils.getCurrentUser();
        
        Message message = messageRepository.findById(messageId)
            .orElseThrow(() -> new ResourceNotFoundException("Message", messageId.toString()));

        // Check if feedback already exists
        MessageFeedback feedback = feedbackRepository
            .findByMessageIdAndUserId(messageId, user.getId())
            .orElse(MessageFeedback.builder()
                .message(message)
                .user(user)
                .build());

        feedback.setRating(request.getRating());
        feedback.setComment(request.getComment());
        
        if (request.getFeedbackType() != null) {
            feedback.setFeedbackType(
                MessageFeedback.FeedbackType.valueOf(request.getFeedbackType().name())
            );
        }

        feedbackRepository.save(feedback);
        log.info("Feedback saved for message {} by user {}", messageId, user.getEmail());
    }
}
