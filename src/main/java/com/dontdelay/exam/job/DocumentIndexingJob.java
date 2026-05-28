package com.dontdelay.exam.job;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Phase 2에서 PDF 추출·chunking·임베딩 파이프라인을 구현한다.
 */
@Slf4j
@Component
public class DocumentIndexingJob {

    @Async
    public void start(UUID documentId) {
        log.info("Document indexing queued (Phase 2): documentId={}", documentId);
    }
}
