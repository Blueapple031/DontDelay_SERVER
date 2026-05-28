package com.dontdelay.exam.dto;

import com.dontdelay.exam.domain.DocumentStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DocumentSummaryDto(
        UUID documentId,
        String title,
        String subject,
        DocumentStatus status,
        Integer pageCount,
        int chunkCount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
