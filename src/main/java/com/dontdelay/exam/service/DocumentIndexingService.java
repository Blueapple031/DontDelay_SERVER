package com.dontdelay.exam.service;

import com.dontdelay.exam.domain.DocumentStatus;
import com.dontdelay.exam.domain.StudyDocument;
import com.dontdelay.exam.infra.StorageClient;
import com.dontdelay.exam.service.model.ExtractedPage;
import com.dontdelay.exam.service.model.TextChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIndexingService {

    private final DocumentStatusUpdater documentStatusUpdater;
    private final StorageClient storageClient;
    private final ExtractionService extractionService;
    private final ChunkService chunkService;
    private final EmbeddingService embeddingService;
    private final DocumentChunkPersistenceService documentChunkPersistenceService;

    public void index(UUID documentId) {
        StudyDocument document;
        try {
            document = documentStatusUpdater.requireDocument(documentId);
        } catch (IllegalStateException e) {
            log.warn("Document indexing skipped; document not found: {}", documentId);
            return;
        }

        if (document.getStatus() == DocumentStatus.READY) {
            log.info("Document already indexed: {}", documentId);
            return;
        }

        String storageKey = document.getS3Key();

        try {
            documentStatusUpdater.markExtracting(documentId);
            List<ExtractedPage> pages;
            try (var inputStream = storageClient.get(storageKey)) {
                pages = extractionService.extract(inputStream);
            }

            documentStatusUpdater.markIndexing(documentId, 20);
            List<TextChunk> chunks = chunkService.chunkPages(pages);
            if (chunks.isEmpty()) {
                throw new ExtractionException("EMPTY_PDF", "청크로 분할할 텍스트가 없습니다.");
            }

            documentStatusUpdater.updateProgress(documentId, 50, 0);
            List<String> texts = chunks.stream().map(TextChunk::content).toList();
            List<float[]> embeddings = embeddingService.embedChunks(texts);

            StudyDocument indexingDocument = documentStatusUpdater.requireDocument(documentId);
            documentChunkPersistenceService.replaceChunks(indexingDocument, chunks, embeddings);

            documentStatusUpdater.markReady(documentId, pages.size(), chunks.size());
            log.info("Document indexing completed: documentId={}, chunks={}", documentId, chunks.size());
        } catch (ExtractionException e) {
            documentStatusUpdater.markFailed(documentId, e.getErrorCode(), e.getMessage());
            log.warn("Document indexing failed: documentId={}, code={}", documentId, e.getErrorCode());
        } catch (Exception e) {
            documentStatusUpdater.markFailed(documentId, "INDEXING_FAILED", "문서 인덱싱 중 오류가 발생했습니다.");
            log.error("Document indexing failed: documentId={}", documentId, e);
        }
    }
}
