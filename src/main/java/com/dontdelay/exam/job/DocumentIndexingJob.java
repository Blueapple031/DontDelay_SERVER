package com.dontdelay.exam.job;

import com.dontdelay.exam.service.DocumentIndexingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentIndexingJob {

    private final DocumentIndexingService documentIndexingService;

    @Async
    public void start(UUID documentId) {
        log.info("Document indexing started: documentId={}", documentId);
        documentIndexingService.index(documentId);
    }
}
