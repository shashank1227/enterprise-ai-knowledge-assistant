package com.enterprise.knowledge.controller;

import com.enterprise.knowledge.dto.request.ChatRequest;
import com.enterprise.knowledge.dto.request.FeedbackRequest;
import com.enterprise.knowledge.dto.response.ChatResponse;
import com.enterprise.knowledge.dto.response.ConversationResponse;
import com.enterprise.knowledge.service.impl.ConversationService;
import com.enterprise.knowledge.service.impl.FeedbackService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Chat and conversation REST controller.
 * Handles RAG-powered Q&A, conversation management, and streaming responses.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ConversationService conversationService;
    private final FeedbackService feedbackService;
    private final ExecutorService sseExecutor = Executors.newCachedThreadPool();

    /**
     * Get user's conversations with pagination.
     */
    @GetMapping("/conversations")
    @PreAuthorize("hasAnyRole('USER', 'VIEWER', 'ADMIN')")
    public ResponseEntity<Page<ConversationResponse>> getConversations(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "false") boolean pinned
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<ConversationResponse> conversations = conversationService.getUserConversations(
            pinned,
            pageable
        );
        return ResponseEntity.ok(conversations);
    }

    /**
     * Create a new conversation.
     */
    @PostMapping("/conversations")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ConversationResponse> createConversation(
        @RequestBody(required = false) Map<String, String> request
    ) {
        String title = request != null ? request.get("title") : null;
        ConversationResponse conversation = conversationService.createConversation(title);
        return ResponseEntity.status(HttpStatus.CREATED).body(conversation);
    }

    /**
     * Get conversation by ID with message history.
     */
    @GetMapping("/conversations/{id}")
    @PreAuthorize("hasAnyRole('USER', 'VIEWER', 'ADMIN')")
    public ResponseEntity<ConversationResponse> getConversation(@PathVariable UUID id) {
        // For now, return basic conversation info
        // Full implementation would include message history
        return ResponseEntity.ok(conversationService.getUserConversations(false, PageRequest.of(0, 1))
            .stream()
            .filter(conv -> conv.getId().equals(id))
            .findFirst()
            .orElseThrow());
    }

    /**
     * Toggle pin status of a conversation.
     */
    @PutMapping("/conversations/{id}/pin")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ConversationResponse> togglePin(@PathVariable UUID id) {
        ConversationResponse conversation = conversationService.togglePin(id);
        return ResponseEntity.ok(conversation);
    }

    /**
     * Delete a conversation.
     */
    @DeleteMapping("/conversations/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<Void> deleteConversation(@PathVariable UUID id) {
        conversationService.deleteConversation(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Ask a question (non-streaming response).
     */
    @PostMapping("/ask")
    @PreAuthorize("hasAnyRole('USER', 'VIEWER', 'ADMIN')")
    public ResponseEntity<ChatResponse> ask(@Valid @RequestBody ChatRequest request) {
        log.info("Chat request received: '{}'", 
            request.getMessage().substring(0, Math.min(50, request.getMessage().length())));
        
        ChatResponse response = conversationService.chat(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Ask a question with Server-Sent Events streaming.
     * Streams tokens as they're generated for a better UX.
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAnyRole('USER', 'VIEWER', 'ADMIN')")
    public SseEmitter streamChat(@Valid @RequestBody ChatRequest request) {
        log.info("Streaming chat request received");
        
        SseEmitter emitter = new SseEmitter(60000L); // 60 second timeout
        
        sseExecutor.execute(() -> {
            try {
                // Generate response (in production, this would stream from LLM)
                ChatResponse response = conversationService.chat(request);
                
                // Simulate streaming by breaking answer into words
                String[] words = response.getAnswer().split(" ");
                for (String word : words) {
                    emitter.send(SseEmitter.event()
                        .data(Map.of("type", "token", "content", word + " "))
                        .id(UUID.randomUUID().toString()));
                    Thread.sleep(50); // Simulate streaming delay
                }
                
                // Send citations
                emitter.send(SseEmitter.event()
                    .data(Map.of("type", "citations", "citations", response.getCitations()))
                    .id(UUID.randomUUID().toString()));
                
                // Send completion
                emitter.send(SseEmitter.event()
                    .data(Map.of(
                        "type", "done",
                        "messageId", response.getMessageId().toString(),
                        "conversationId", response.getConversationId().toString()
                    ))
                    .id(UUID.randomUUID().toString()));
                
                emitter.complete();
                
            } catch (Exception e) {
                log.error("Error during streaming: {}", e.getMessage());
                emitter.completeWithError(e);
            }
        });
        
        return emitter;
    }

    /**
     * Regenerate AI response for a message.
     */
    @PostMapping("/messages/{id}/regenerate")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ChatResponse> regenerate(@PathVariable UUID id) {
        log.info("Regenerate request for message: {}", id);
        // Implementation would fetch original question and regenerate
        // For now, return 501 Not Implemented
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    /**
     * Submit feedback for an AI response.
     */
    @PostMapping("/messages/{id}/feedback")
    @PreAuthorize("hasAnyRole('USER', 'VIEWER', 'ADMIN')")
    public ResponseEntity<Void> submitFeedback(
        @PathVariable UUID id,
        @Valid @RequestBody FeedbackRequest request
    ) {
        log.info("Feedback received for message {}: rating={}", id, request.getRating());
        feedbackService.submitFeedback(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
