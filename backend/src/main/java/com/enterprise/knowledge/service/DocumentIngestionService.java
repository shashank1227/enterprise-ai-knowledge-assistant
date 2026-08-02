package com.enterprise.knowledge.service;

import com.enterprise.knowledge.ai.chunking.ChunkingStrategy;
import com.enterprise.knowledge.ai.embedding.EmbeddingService;
import com.enterprise.knowledge.ai.parser.DocumentParser;
import com.enterprise.knowledge.domain.Document;
import com.enterprise.knowledge.domain.DocumentChunk;
import com.enterprise.knowledge.repository.DocumentChunkRepository;
import com.enterprise.knowledge.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Document ingestion pipeline service.
 * Orchestrates the end-to-end process: parse → chunk → embed → store.
 * 
 * Pipeline stages:
 * 1. Parse: Extract text from PDF/DOCX/etc
 * 2. Chunk: Split into semantic chunks with overlap
 * 3. Embed: Generate vector embeddings for each chunk
 * 4. Store: Save to PostgreSQL with pgvector
 * 
 * Runs asynchronously to avoid blocking upload requests.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIngestionService {

    private final DocumentParser documentParser;
    private final ChunkingStrategy chunkingStrategy;
    private final EmbeddingService embeddingService;
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;

    /**
     * Process a document through the full ingestion pipeline.
     * This method runs asynchronously to avoid blocking the upload request.
     * 
     * @param documentId The document ID to process
     * @param inputStream The file input stream
     */
    @Async("documentProcessingExecutor")
    @Transactional
    public void ingestDocument(UUID documentId, InputStream inputStream) {
        log.info("Starting document ingestion for document ID: {}", documentId);
        long startTime = System.currentTimeMillis();

        Document document = documentRepository.findById(documentId)
            .orElseThrow(() -> new RuntimeException("Document not found: " + documentId));

        try {
            // Update status to PROCESSING
            document.setStatus(Document.DocumentStatus.PROCESSING);
            documentRepository.save(document);

            // Stage 1: Parse document
            log.info("[{}] Stage 1/4: Parsing {} file", documentId, document.getFileType());
            DocumentParser.ParsedDocument parsed = documentParser.parse(
                inputStream,
                document.getFileType()
            );
            
            document.setPageCount(parsed.getPageCount());
            document.setWordCount(parsed.getWordCount());
            documentRepository.save(document);

            // Stage 2: Chunk document
            log.info("[{}] Stage 2/4: Chunking text ({} words)", 
                documentId, parsed.getWordCount());
            List<ChunkingStrategy.DocumentChunk> chunks = chunkingStrategy.chunkDocument(
                parsed.getFullText(),
                parsed.getPages()
            );
            
            if (chunks.isEmpty()) {
                throw new RuntimeException("No chunks generated from document");
            }

            log.info("[{}] Generated {} chunks", documentId, chunks.size());

            // Stage 3: Generate embeddings (batched for efficiency)
            log.info("[{}] Stage 3/4: Generating embeddings", documentId);
            List<String> chunkTexts = chunks.stream()
                .map(ChunkingStrategy.DocumentChunk::getContent)
                .toList();
            
            List<float[]> embeddings = embeddingService.embedBatch(chunkTexts);

            // Stage 4: Save chunks to database
            log.info("[{}] Stage 4/4: Saving {} chunks to database", documentId, chunks.size());
            List<DocumentChunk> chunkEntities = new ArrayList<>();
            
            for (int i = 0; i < chunks.size(); i++) {
                ChunkingStrategy.DocumentChunk chunk = chunks.get(i);
                float[] embedding = embeddings.get(i);

                DocumentChunk entity = DocumentChunk.builder()
                    .document(document)
                    .chunkIndex(chunk.getIndex())
                    .content(chunk.getContent())
                    .contentTokens(chunk.getTokenCount())
                    .pageNumber(chunk.getPageNumber())
                    .sectionTitle(chunk.getSectionTitle())
                    .embedding(embedding)
                    .embeddingModel(embeddingService.getClass().getSimpleName())
                    .embeddingVersion(1)
                    .build();

                chunkEntities.add(entity);
            }

            chunkRepository.saveAll(chunkEntities);

            // Update document status
            document.setStatus(Document.DocumentStatus.INDEXED);
            document.setIndexedAt(Instant.now());
            document.setTokenCount(chunks.stream()
                .mapToInt(ChunkingStrategy.DocumentChunk::getTokenCount)
                .sum());
            documentRepository.save(document);

            long elapsedMs = System.currentTimeMillis() - startTime;
            log.info("[{}] Document ingestion completed successfully in {}ms", 
                documentId, elapsedMs);

        } catch (Exception e) {
            log.error("[{}] Document ingestion failed: {}", documentId, e.getMessage(), e);
            
            document.setStatus(Document.DocumentStatus.FAILED);
            document.setProcessingError(e.getMessage());
            documentRepository.save(document);
            
            throw new RuntimeException("Document ingestion failed", e);
        }
    }

    /**
     * Re-index an existing document (e.g., after embedding model change).
     */
    @Async("documentProcessingExecutor")
    @Transactional
    public void reindexDocument(UUID documentId, InputStream inputStream) {
        log.info("Re-indexing document: {}", documentId);
        
        // Delete existing chunks
        chunkRepository.deleteAllByDocumentId(documentId);
        
        // Run ingestion again
        ingestDocument(documentId, inputStream);
    }

    /**
     * Get ingestion statistics.
     */
    public IngestionStats getStats() {
        long totalDocs = documentRepository.countActive();
        long indexedDocs = documentRepository.countByStatus(Document.DocumentStatus.INDEXED);
        long processingDocs = documentRepository.countByStatus(Document.DocumentStatus.PROCESSING);
        long failedDocs = documentRepository.countByStatus(Document.DocumentStatus.FAILED);
        long totalChunks = chunkRepository.countAll();
        Long totalSize = documentRepository.sumFileSizeBytes();

        return new IngestionStats(
            totalDocs,
            indexedDocs,
            processingDocs,
            failedDocs,
            totalChunks,
            totalSize != null ? totalSize : 0L
        );
    }

    /**
     * Ingestion statistics record.
     */
    public record IngestionStats(
        long totalDocuments,
        long indexedDocuments,
        long processingDocuments,
        long failedDocuments,
        long totalChunks,
        long totalStorageBytes
    ) {}
}
