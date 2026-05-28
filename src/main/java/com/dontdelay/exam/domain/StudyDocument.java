package com.dontdelay.exam.domain;

import com.dontdelay.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "study_document")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudyDocument {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String title;

    @Column
    private String subject;

    @Column(name = "s3_key", nullable = false)
    private String s3Key;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentStatus status;

    @Column(nullable = false)
    private int progress;

    @Column(name = "page_count")
    private Integer pageCount;

    @Column(name = "chunk_count", nullable = false)
    private int chunkCount;

    @Column(name = "file_size_bytes", nullable = false)
    private long fileSizeBytes;

    @Column(name = "error_code")
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Builder
    public StudyDocument(
            UUID id,
            User user,
            String title,
            String subject,
            String s3Key,
            DocumentStatus status,
            int progress,
            Integer pageCount,
            int chunkCount,
            long fileSizeBytes,
            String errorCode,
            String errorMessage,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        this.id = id;
        this.user = user;
        this.title = title;
        this.subject = subject;
        this.s3Key = s3Key;
        this.status = status;
        this.progress = progress;
        this.pageCount = pageCount;
        this.chunkCount = chunkCount;
        this.fileSizeBytes = fileSizeBytes;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public void touchUpdatedAt() {
        this.updatedAt = OffsetDateTime.now();
    }
}
