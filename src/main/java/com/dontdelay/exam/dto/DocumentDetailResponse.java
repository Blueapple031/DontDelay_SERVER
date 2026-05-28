package com.dontdelay.exam.dto;

import com.dontdelay.exam.domain.DocumentStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DocumentDetailResponse(
        UUID documentId,
        String title,
        String subject,
        DocumentStatus status,
        int progress,
        Integer pageCount,
        int chunkCount,
        String errorCode,
        String errorMessage,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
