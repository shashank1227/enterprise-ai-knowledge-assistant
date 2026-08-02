package com.enterprise.knowledge.ai.prompt;

import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Prompt engineering service for RAG-based Q&A.
 * Contains carefully crafted prompts to reduce hallucinations and improve citation accuracy.
 * 
 * Prompt design principles:
 * 1. Explicit instruction to only use provided context
 * 2. Request for citations with specific format
 * 3. Instruction to admit uncertainty when context is insufficient
 * 4. Professional, concise tone appropriate for enterprise use
 */
@Service
public class PromptTemplateService {

    /**
     * System prompt that sets the AI's role and behavior.
     */
    public String getSystemPrompt() {
        return """
            You are an enterprise knowledge assistant for a large organization.
            Your role is to help employees find accurate information from internal documentation.
            
            Guidelines:
            - Answer questions using ONLY the provided context from company documents
            - Always cite your sources using [1], [2], etc. notation
            - If the context doesn't contain enough information, say so clearly
            - Be concise and professional
            - Do not make assumptions or use external knowledge
            - Format your response in clear, readable paragraphs
            - If you reference code, use proper markdown formatting
            """;
    }

    /**
     * Build the complete RAG prompt with context and user question.
     * @param userQuestion The user's question
     * @param contextChunks Retrieved document chunks with metadata
     * @param conversationHistory Previous messages for context (optional)
     * @return Formatted prompt ready for LLM
     */
    public String buildRagPrompt(
        String userQuestion,
        List<RetrievedChunk> contextChunks,
        List<ConversationMessage> conversationHistory
    ) {
        StringBuilder prompt = new StringBuilder();

        // Add conversation history if present (last 3 exchanges)
        if (conversationHistory != null && !conversationHistory.isEmpty()) {
            prompt.append("## Previous Conversation:\n\n");
            int start = Math.max(0, conversationHistory.size() - 6); // Last 3 Q&A pairs
            for (int i = start; i < conversationHistory.size(); i++) {
                ConversationMessage msg = conversationHistory.get(i);
                prompt.append(msg.role()).append(": ").append(msg.content()).append("\n\n");
            }
            prompt.append("---\n\n");
        }

        // Add retrieved context with citation markers
        prompt.append("## Retrieved Context:\n\n");
        for (int i = 0; i < contextChunks.size(); i++) {
            RetrievedChunk chunk = contextChunks.get(i);
            prompt.append("[").append(i + 1).append("] ")
                .append("**").append(chunk.documentTitle()).append("**");
            
            if (chunk.sectionTitle() != null) {
                prompt.append(" - ").append(chunk.sectionTitle());
            }
            if (chunk.pageNumber() != null) {
                prompt.append(" (Page ").append(chunk.pageNumber()).append(")");
            }
            
            prompt.append("\n");
            prompt.append(chunk.content()).append("\n\n");
        }

        prompt.append("---\n\n");

        // Add user question
        prompt.append("## Question:\n").append(userQuestion).append("\n\n");

        // Add instructions
        prompt.append("""
            ## Instructions:
            Answer the question using ONLY the context provided above.
            Include citations using [1], [2], etc. to reference the sources.
            If the context doesn't contain the answer, say "I don't have enough information to answer this question based on the available documents."
            Be concise, accurate, and professional.
            """);

        return prompt.toString();
    }

    /**
     * Build a follow-up prompt when regenerating an answer.
     */
    public String buildRegenerationPrompt(String originalQuestion, String previousAnswer) {
        return String.format("""
            The user asked: "%s"
            
            You previously answered:
            %s
            
            Please provide an alternative answer with different phrasing or additional details if available.
            Use the same context documents and citation format.
            """, originalQuestion, previousAnswer);
    }

    /**
     * Retrieved chunk with metadata for citation.
     */
    public record RetrievedChunk(
        String content,
        String documentTitle,
        String sectionTitle,
        Integer pageNumber,
        float relevanceScore
    ) {}

    /**
     * Conversation message for context window.
     */
    public record ConversationMessage(
        String role,  // "User" or "Assistant"
        String content
    ) {}
}
