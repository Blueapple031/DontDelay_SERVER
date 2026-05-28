package com.dontdelay.exam.dto;

import com.dontdelay.exam.domain.DocumentStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UploadDocumentResponse(
        UUID documentId,
        String title,
        String subject,
        DocumentStatus status,
        long fileSizeBytes,
        OffsetDateTime createdAt
) {
}
