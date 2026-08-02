package com.enterprise.knowledge.ai.rag;

import com.enterprise.knowledge.ai.prompt.PromptTemplateService;
import com.enterprise.knowledge.config.AppProperties;
import com.enterprise.knowledge.domain.Document;
import com.enterprise.knowledge.repository.DocumentRepository;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * RAG (Retrieval Augmented Generation) orchestration service.
 * Coordinates the full pipeline: query → retrieve → augment → generate.
 * 
 * Pipeline:
 * 1. Receive user question
 * 2. Search for relevant document chunks (semantic + keyword)
 * 3. Build prompt with retrieved context
 * 4. Generate answer using LLM
 * 5. Extract citations and metadata
 * 6. Return structured response
 */
@Slf4j
@Service
public class RagService {

    private final VectorSearchService vectorSearchService;
    private final PromptTemplateService promptTemplateService;
    private final DocumentRepository documentRepository;
    private ChatLanguageModel chatModel;
    private final AppProperties appProperties;

    public RagService(
        VectorSearchService vectorSearchService,
        PromptTemplateService promptTemplateService,
        DocumentRepository documentRepository,
        AppProperties appProperties
    ) {
        this.vectorSearchService = vectorSearchService;
        this.promptTemplateService = promptTemplateService;
        this.documentRepository = documentRepository;
        this.appProperties = appProperties;
    }

    private synchronized ChatLanguageModel chatModel() {
        if (chatModel != null) {
            return chatModel;
        }
        if (appProperties.getOpenai().getApiKey() == null
            || appProperties.getOpenai().getApiKey().isBlank()) {
            throw new IllegalStateException("OpenAI API key is not configured. Set OPENAI_API_KEY to use chat.");
        }
        chatModel = OpenAiChatModel.builder()
            .apiKey(appProperties.getOpenai().getApiKey())
            .modelName(appProperties.getOpenai().getChatModel())
            .temperature(appProperties.getOpenai().getTemperature())
            .maxTokens(appProperties.getOpenai().getMaxTokens())
            .timeout(Duration.ofSeconds(appProperties.getOpenai().getTimeoutSeconds()))
            .maxRetries(appProperties.getOpenai().getMaxRetries())
            .logRequests(false)
            .logResponses(false)
            .build();
        return chatModel;
    }

    /**
     * Execute the full RAG pipeline.
     * @param request RAG request with query and options
     * @return RAG response with answer and citations
     */
    public RagResponse query(RagRequest request) {
        long startTime = System.currentTimeMillis();
        
        log.info("RAG query started: '{}'", request.query());

        // No indexed corpus yet — answer without embeddings/LLM so local demo works
        // even before OPENAI_API_KEY is configured.
        long indexedCount = documentRepository.countByStatus(
            com.enterprise.knowledge.domain.Document.DocumentStatus.INDEXED
        );
        if (indexedCount == 0) {
            return RagResponse.builder()
                .answer("I don't have any indexed documents yet. Upload a document from the Documents page, wait for it to finish indexing, then ask again. Note: indexing and AI answers require a valid OPENAI_API_KEY.")
                .citations(List.of())
                .retrievalLatencyMs(0)
                .llmLatencyMs(0)
                .totalLatencyMs((int) (System.currentTimeMillis() - startTime))
                .build();
        }

        // 1. Retrieve relevant chunks
        long retrievalStart = System.currentTimeMillis();
        VectorSearchService.SearchMode searchMode = parseSearchMode(request.searchMode());
        List<VectorSearchService.SearchResult> searchResults = vectorSearchService.search(
            request.query(),
            request.topK() != null ? request.topK() : appProperties.getRag().getTopKRetrieval(),
            searchMode
        );
        long retrievalMs = System.currentTimeMillis() - retrievalStart;

        if (searchResults.isEmpty()) {
            log.warn("No relevant documents found for query");
            return RagResponse.builder()
                .answer("I couldn't find any relevant documents to answer your question. Please try rephrasing or check if documents related to this topic have been uploaded.")
                .citations(List.of())
                .retrievalLatencyMs((int) retrievalMs)
                .llmLatencyMs(0)
                .totalLatencyMs((int) (System.currentTimeMillis() - startTime))
                .build();
        }

        // 2. Fetch document metadata for citations
        List<RetrievedContext> contexts = buildContextList(searchResults);

        // 3. Build prompt with context
        List<PromptTemplateService.RetrievedChunk> chunks = contexts.stream()
            .map(ctx -> new PromptTemplateService.RetrievedChunk(
                ctx.content(),
                ctx.documentTitle(),
                ctx.sectionTitle(),
                ctx.pageNumber(),
                ctx.relevanceScore()
            ))
            .toList();

        String prompt = promptTemplateService.buildRagPrompt(
            request.query(),
            chunks,
            request.conversationHistory()
        );

        // 4. Generate answer using LLM
        long llmStart = System.currentTimeMillis();
        Response<AiMessage> response = generateAnswer(prompt);
        String answer = response.content().text();
        long llmMs = System.currentTimeMillis() - llmStart;

        // 5. Build structured response
        List<Citation> citations = buildCitations(contexts);
        
        int totalTokens = response.tokenUsage() != null ? response.tokenUsage().totalTokenCount() : 0;
        
        log.info("RAG query completed: retrieval={}ms, llm={}ms, tokens={}", 
            retrievalMs, llmMs, totalTokens);

        return RagResponse.builder()
            .answer(answer)
            .citations(citations)
            .retrievalLatencyMs((int) retrievalMs)
            .llmLatencyMs((int) llmMs)
            .totalLatencyMs((int) (System.currentTimeMillis() - startTime))
            .tokensUsed(totalTokens)
            .model(appProperties.getOpenai().getChatModel())
            .retrievedChunkIds(contexts.stream().map(RetrievedContext::chunkId).toList())
            .build();
    }

    /**
     * Generate answer using chat model with system and user messages.
     */
    private Response<AiMessage> generateAnswer(String prompt) {
        List<ChatMessage> messages = List.of(
            SystemMessage.from(promptTemplateService.getSystemPrompt()),
            UserMessage.from(prompt)
        );

        return chatModel().generate(messages);
    }

    /**
     * Build context list with document metadata for citations.
     */
    private List<RetrievedContext> buildContextList(List<VectorSearchService.SearchResult> searchResults) {
        List<RetrievedContext> contexts = new ArrayList<>();
        
        // Batch fetch all unique documents
        List<UUID> documentIds = searchResults.stream()
            .map(VectorSearchService.SearchResult::getDocumentId)
            .distinct()
            .toList();
        
        List<Document> documents = documentRepository.findAllById(documentIds);
        var docMap = documents.stream()
            .collect(Collectors.toMap(Document::getId, doc -> doc));

        for (VectorSearchService.SearchResult result : searchResults) {
            Document doc = docMap.get(result.getDocumentId());
            if (doc != null) {
                contexts.add(new RetrievedContext(
                    result.getChunkId(),
                    result.getDocumentId(),
                    result.getContent(),
                    doc.getTitle(),
                    result.getSectionTitle(),
                    result.getPageNumber(),
                    result.getRelevanceScore()
                ));
            }
        }

        return contexts;
    }

    /**
     * Build citation list from retrieved contexts.
     */
    private List<Citation> buildCitations(List<RetrievedContext> contexts) {
        List<Citation> citations = new ArrayList<>();
        for (int i = 0; i < contexts.size(); i++) {
            RetrievedContext ctx = contexts.get(i);
            citations.add(new Citation(
                i + 1,
                ctx.chunkId(),
                ctx.documentId(),
                ctx.documentTitle(),
                ctx.sectionTitle(),
                ctx.pageNumber(),
                ctx.relevanceScore(),
                truncateExcerpt(ctx.content())
            ));
        }
        return citations;
    }

    /**
     * Truncate content to create an excerpt for citation.
     */
    private String truncateExcerpt(String content) {
        if (content.length() <= 200) {
            return content;
        }
        return content.substring(0, 197) + "...";
    }

    private VectorSearchService.SearchMode parseSearchMode(String mode) {
        if (mode == null) {
            return VectorSearchService.SearchMode.HYBRID;
        }
        try {
            return VectorSearchService.SearchMode.valueOf(mode.toUpperCase());
        } catch (IllegalArgumentException e) {
            return VectorSearchService.SearchMode.HYBRID;
        }
    }

    /**
     * RAG request.
     */
    public record RagRequest(
        String query,
        Integer topK,
        String searchMode,
        List<PromptTemplateService.ConversationMessage> conversationHistory
    ) {}

    /**
     * RAG response with answer and metadata.
     */
    @lombok.Data
    @lombok.Builder
    public static class RagResponse {
        private String answer;
        private List<Citation> citations;
        private int retrievalLatencyMs;
        private int llmLatencyMs;
        private int totalLatencyMs;
        private int tokensUsed;
        private String model;
        private List<UUID> retrievedChunkIds;
    }

    /**
     * Citation information for frontend display.
     */
    public record Citation(
        int index,
        UUID chunkId,
        UUID documentId,
        String documentTitle,
        String sectionTitle,
        Integer pageNumber,
        float relevanceScore,
        String excerpt
    ) {}

    /**
     * Retrieved context with full metadata.
     */
    private record RetrievedContext(
        UUID chunkId,
        UUID documentId,
        String content,
        String documentTitle,
        String sectionTitle,
        Integer pageNumber,
        float relevanceScore
    ) {}
}
