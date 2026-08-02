package com.enterprise.knowledge.service.impl;

import com.enterprise.knowledge.ai.prompt.PromptTemplateService;
import com.enterprise.knowledge.ai.rag.RagService;
import com.enterprise.knowledge.domain.Conversation;
import com.enterprise.knowledge.domain.Message;
import com.enterprise.knowledge.domain.MessageCitation;
import com.enterprise.knowledge.domain.User;
import com.enterprise.knowledge.dto.request.ChatRequest;
import com.enterprise.knowledge.dto.response.ChatResponse;
import com.enterprise.knowledge.dto.response.CitationResponse;
import com.enterprise.knowledge.dto.response.ConversationResponse;
import com.enterprise.knowledge.exception.ResourceNotFoundException;
import com.enterprise.knowledge.mapper.ConversationMapper;
import com.enterprise.knowledge.repository.*;
import com.enterprise.knowledge.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Conversation and chat service.
 * Manages conversations, messages, and integrates with RAG pipeline.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final MessageCitationRepository citationRepository;
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final RagService ragService;
    private final SecurityUtils securityUtils;
    private final ConversationMapper conversationMapper;

    /**
     * Get user's conversations with pagination.
     */
    @Transactional(readOnly = true)
    public Page<ConversationResponse> getUserConversations(boolean pinnedOnly, Pageable pageable) {
        UUID userId = securityUtils.getCurrentUserId();
        Page<Conversation> conversations = conversationRepository.findByUserId(
            userId,
            pinnedOnly,
            pageable
        );
        return conversations.map(conversationMapper::toConversationResponse);
    }

    /**
     * Create a new conversation.
     */
    @Transactional
    public ConversationResponse createConversation(String title) {
        User user = securityUtils.getCurrentUser();
        
        Conversation conversation = Conversation.builder()
            .user(user)
            .title(title)
            .build();

        conversation = conversationRepository.save(conversation);
        log.info("Created conversation {} for user {}", conversation.getId(), user.getEmail());

        return conversationMapper.toConversationResponse(conversation);
    }

    /**
     * Toggle pin status of a conversation.
     */
    @Transactional
    public ConversationResponse togglePin(UUID conversationId) {
        UUID userId = securityUtils.getCurrentUserId();
        
        Conversation conversation = conversationRepository.findByIdAndUserId(conversationId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Conversation", conversationId.toString()));

        conversation.togglePin();
        conversation = conversationRepository.save(conversation);

        return conversationMapper.toConversationResponse(conversation);
    }

    /**
     * Delete a conversation.
     */
    @Transactional
    public void deleteConversation(UUID conversationId) {
        UUID userId = securityUtils.getCurrentUserId();
        
        Conversation conversation = conversationRepository.findByIdAndUserId(conversationId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Conversation", conversationId.toString()));

        conversationRepository.delete(conversation);
        log.info("Deleted conversation {} for user {}", conversationId, userId);
    }

    /**
     * Handle chat request through RAG pipeline.
     */
    @Transactional
    public ChatResponse chat(ChatRequest request) {
        User user = securityUtils.getCurrentUser();
        long startTime = System.currentTimeMillis();

        log.info("Processing chat request from user {}: '{}'", 
            user.getEmail(), request.getMessage().substring(0, Math.min(50, request.getMessage().length())));

        // Get or create conversation
        Conversation conversation;
        if (request.getConversationId() != null) {
            conversation = conversationRepository.findByIdAndUserId(request.getConversationId(), user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", request.getConversationId().toString()));
        } else {
            // Create new conversation with auto-generated title
            conversation = Conversation.builder()
                .user(user)
                .title(generateConversationTitle(request.getMessage()))
                .build();
            conversation = conversationRepository.save(conversation);
        }

        // Save user message
        Message userMessage = Message.builder()
            .conversation(conversation)
            .role(Message.MessageRole.USER)
            .content(request.getMessage())
            .build();
        messageRepository.save(userMessage);

        // Build conversation history for context
        List<PromptTemplateService.ConversationMessage> history = buildConversationHistory(conversation.getId());

        // Execute RAG pipeline
        RagService.RagRequest ragRequest = new RagService.RagRequest(
            request.getMessage(),
            request.getTopK(),
            request.getSearchMode().name(),
            history
        );

        RagService.RagResponse ragResponse = ragService.query(ragRequest);

        // Save assistant message
        Message assistantMessage = Message.builder()
            .conversation(conversation)
            .role(Message.MessageRole.ASSISTANT)
            .content(ragResponse.getAnswer())
            .modelUsed(ragResponse.getModel())
            .totalTokens(ragResponse.getTokensUsed())
            .latencyMs(ragResponse.getTotalLatencyMs())
            .finishReason("stop")
            .build();
        assistantMessage = messageRepository.save(assistantMessage);

        // Save citations
        List<MessageCitation> citations = new ArrayList<>();
        for (RagService.Citation cite : ragResponse.getCitations()) {
            MessageCitation citation = MessageCitation.builder()
                .message(assistantMessage)
                .chunk(chunkRepository.findById(cite.chunkId()).orElse(null))
                .document(documentRepository.findById(cite.documentId()).orElse(null))
                .citationIndex(cite.index())
                .relevanceScore(cite.relevanceScore())
                .excerpt(cite.excerpt())
                .build();
            citations.add(citation);
        }
        citationRepository.saveAll(citations);

        // Build response
        List<CitationResponse> citationResponses = ragResponse.getCitations().stream()
            .map(cite -> CitationResponse.builder()
                .index(cite.index())
                .documentId(cite.documentId())
                .documentTitle(cite.documentTitle())
                .chunkId(cite.chunkId())
                .excerpt(cite.excerpt())
                .pageNumber(cite.pageNumber())
                .sectionTitle(cite.sectionTitle())
                .relevanceScore(cite.relevanceScore())
                .build())
            .toList();

        log.info("Chat completed in {}ms with {} citations", 
            System.currentTimeMillis() - startTime, citationResponses.size());

        return ChatResponse.builder()
            .messageId(assistantMessage.getId())
            .conversationId(conversation.getId())
            .answer(ragResponse.getAnswer())
            .citations(citationResponses)
            .tokensUsed(ragResponse.getTokensUsed())
            .latencyMs(ragResponse.getTotalLatencyMs())
            .model(ragResponse.getModel())
            .build();
    }

    /**
     * Build conversation history for RAG context.
     */
    private List<PromptTemplateService.ConversationMessage> buildConversationHistory(UUID conversationId) {
        List<Message> messages = messageRepository.findLastNMessages(conversationId, 6); // Last 3 exchanges
        
        List<PromptTemplateService.ConversationMessage> history = new ArrayList<>();
        for (Message msg : messages) {
            String role = msg.getRole() == Message.MessageRole.USER ? "User" : "Assistant";
            history.add(new PromptTemplateService.ConversationMessage(role, msg.getContent()));
        }
        
        return history;
    }

    /**
     * Generate conversation title from first message.
     */
    private String generateConversationTitle(String firstMessage) {
        if (firstMessage.length() <= 50) {
            return firstMessage;
        }
        return firstMessage.substring(0, 47) + "...";
    }
}
