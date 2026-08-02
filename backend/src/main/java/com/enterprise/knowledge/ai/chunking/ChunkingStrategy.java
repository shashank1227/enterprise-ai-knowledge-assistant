package com.enterprise.knowledge.ai.chunking;

import com.enterprise.knowledge.ai.parser.DocumentParser.PageContent;
import com.enterprise.knowledge.config.AppProperties;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.openai.OpenAiTokenizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Intelligent document chunking strategy using LangChain4j.
 * Splits documents into semantic chunks with controlled size and overlap.
 * 
 * Strategy:
 * 1. Use recursive character splitter for natural sentence/paragraph boundaries
 * 2. Target chunk size: 512 tokens (configurable)
 * 3. Overlap: 50 tokens to preserve context at boundaries
 * 4. Preserve page numbers and section titles for citation
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChunkingStrategy {

    private final AppProperties appProperties;

    /**
     * Split document text into chunks suitable for embedding.
     * @param fullText The complete document text
     * @param pages Optional page-level breakdown for citation metadata
     * @return List of text chunks with metadata
     */
    public List<DocumentChunk> chunkDocument(String fullText, List<PageContent> pages) {
        int chunkSize = appProperties.getRag().getChunkSizeTokens();
        int overlap = appProperties.getRag().getChunkOverlapTokens();

        // Use LangChain4j's document splitter with OpenAI tokenizer
        DocumentSplitter splitter = DocumentSplitters.recursive(
            chunkSize,
            overlap,
            new OpenAiTokenizer(appProperties.getOpenai().getEmbeddingModel())
        );

        // Convert to LangChain4j Document
        Document doc = Document.from(fullText);
        List<TextSegment> segments = splitter.split(doc);

        log.info("Split document into {} chunks (target: {} tokens, overlap: {})", 
            segments.size(), chunkSize, overlap);

        List<DocumentChunk> chunks = new ArrayList<>();
        
        for (int i = 0; i < segments.size(); i++) {
            TextSegment segment = segments.get(i);
            String chunkText = segment.text();
            
            // Attempt to determine page number for this chunk
            Integer pageNumber = determinePageNumber(chunkText, pages);
            String sectionTitle = extractSectionTitle(chunkText);
            
            chunks.add(DocumentChunk.builder()
                .index(i)
                .content(chunkText)
                .tokenCount(estimateTokens(chunkText))
                .pageNumber(pageNumber)
                .sectionTitle(sectionTitle)
                .build());
        }

        return chunks;
    }

    /**
     * Estimate token count using simple heuristic (more accurate would use actual tokenizer).
     */
    private int estimateTokens(String text) {
        // Rough estimate: ~4 characters per token for English text
        return text.length() / 4;
    }

    /**
     * Determine which page this chunk likely belongs to based on content matching.
     */
    private Integer determinePageNumber(String chunkText, List<PageContent> pages) {
        if (pages == null || pages.isEmpty()) {
            return null;
        }

        // Find the page that contains the most content from this chunk
        int maxOverlap = 0;
        Integer bestPage = pages.get(0).getPageNumber();

        for (PageContent page : pages) {
            String pageText = page.getContent();
            // Simple overlap check: count matching substrings
            int overlap = calculateOverlap(chunkText, pageText);
            if (overlap > maxOverlap) {
                maxOverlap = overlap;
                bestPage = page.getPageNumber();
            }
        }

        return bestPage;
    }

    /**
     * Calculate rough overlap between chunk and page text.
     */
    private int calculateOverlap(String chunk, String page) {
        // Take first 100 chars of chunk and check if it exists in page
        String prefix = chunk.substring(0, Math.min(100, chunk.length()));
        return page.contains(prefix) ? prefix.length() : 0;
    }

    /**
     * Extract potential section title from chunk (first line if it looks like a heading).
     */
    private String extractSectionTitle(String chunkText) {
        String[] lines = chunkText.split("\n", 2);
        if (lines.length > 0) {
            String firstLine = lines[0].trim();
            // If first line is short (< 100 chars) and doesn't end with punctuation, treat as title
            if (firstLine.length() < 100 && !firstLine.endsWith(".") 
                && !firstLine.endsWith(",") && !firstLine.endsWith(";")) {
                return firstLine;
            }
        }
        return null;
    }

    /**
     * Result of chunking operation.
     */
    @lombok.Data
    @lombok.Builder
    public static class DocumentChunk {
        private int index;
        private String content;
        private int tokenCount;
        private Integer pageNumber;
        private String sectionTitle;
    }
}
