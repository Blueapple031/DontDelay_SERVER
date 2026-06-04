package com.dontdelay.exam.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "document_chunk")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentChunk {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private StudyDocument document;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(name = "page_no")
    private Integer pageNo;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** H2: BLOB. PostgreSQL 전환 시 pgvector `embedding` 컬럼은 JDBC로 별도 저장. */
    @Convert(converter = FloatArrayEmbeddingConverter.class)
    @Column(name = "embedding_data")
    private float[] embedding;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Builder
    public DocumentChunk(
            UUID id,
            StudyDocument document,
            int chunkIndex,
            Integer pageNo,
            String content,
            float[] embedding,
            OffsetDateTime createdAt
    ) {
        this.id = id;
        this.document = document;
        this.chunkIndex = chunkIndex;
        this.pageNo = pageNo;
        this.content = content;
        this.embedding = embedding;
        this.createdAt = createdAt;
    }
}
