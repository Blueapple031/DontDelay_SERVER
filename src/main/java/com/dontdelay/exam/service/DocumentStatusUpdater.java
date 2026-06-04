package com.dontdelay.exam.service;

import com.dontdelay.exam.domain.DocumentStatus;
import com.dontdelay.exam.domain.StudyDocument;
import com.dontdelay.exam.repository.StudyDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentStatusUpdater {

    private final StudyDocumentRepository studyDocumentRepository;

    @Transactional
    public void markExtracting(UUID documentId) {
        requireDocument(documentId).markStatus(DocumentStatus.EXTRACTING, 10);
    }

    @Transactional
    public void markIndexing(UUID documentId, int progress) {
        requireDocument(documentId).markStatus(DocumentStatus.INDEXING, progress);
    }

    @Transactional
    public void updateProgress(UUID documentId, int progress, int chunkCount) {
        requireDocument(documentId).updateIndexingProgress(chunkCount, progress);
    }

    @Transactional
    public void markReady(UUID documentId, int pageCount, int chunkCount) {
        requireDocument(documentId).markReady(pageCount, chunkCount);
    }

    @Transactional
    public void markFailed(UUID documentId, String errorCode, String errorMessage) {
        requireDocument(documentId).markFailed(errorCode, errorMessage);
    }

    @Transactional(readOnly = true)
    public StudyDocument requireDocument(UUID documentId) {
        return studyDocumentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalStateException("문서를 찾을 수 없습니다: " + documentId));
    }
}
