package com.dontdelay.exam.service;

import com.dontdelay.exam.domain.DocumentChunk;
import com.dontdelay.exam.domain.StudyDocument;
import com.dontdelay.exam.infra.DatabaseDialectHelper;
import com.dontdelay.exam.repository.DocumentChunkRepository;
import com.dontdelay.exam.service.model.TextChunk;
import com.pgvector.PGvector;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentChunkPersistenceService {

    private final DocumentChunkRepository documentChunkRepository;
    private final DatabaseDialectHelper databaseDialectHelper;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public void replaceChunks(StudyDocument document, List<TextChunk> chunks, List<float[]> embeddings) {
        documentChunkRepository.deleteByDocumentId(document.getId());

        if (chunks.isEmpty()) {
            return;
        }

        OffsetDateTime now = OffsetDateTime.now();
        if (databaseDialectHelper.isPostgres()) {
            saveWithPgVector(document, chunks, embeddings, now);
            return;
        }

        List<DocumentChunk> entities = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            TextChunk chunk = chunks.get(i);
            entities.add(DocumentChunk.builder()
                    .id(UUID.randomUUID())
                    .document(document)
                    .chunkIndex(chunk.chunkIndex())
                    .pageNo(chunk.pageNo())
                    .content(chunk.content())
                    .embedding(embeddings.get(i))
                    .createdAt(now)
                    .build());
        }
        documentChunkRepository.saveAll(entities);
    }

    private void saveWithPgVector(
            StudyDocument document,
            List<TextChunk> chunks,
            List<float[]> embeddings,
            OffsetDateTime now
    ) {
        String sql = """
                INSERT INTO document_chunk
                    (id, document_id, chunk_index, page_no, content, embedding, created_at)
                VALUES (?, ?, ?, ?, ?, ?::vector, ?)
                """;

        for (int i = 0; i < chunks.size(); i++) {
            TextChunk chunk = chunks.get(i);
            jdbcTemplate.update(
                    sql,
                    UUID.randomUUID(),
                    document.getId(),
                    chunk.chunkIndex(),
                    chunk.pageNo(),
                    chunk.content(),
                    new PGvector(embeddings.get(i)),
                    now
            );
        }
    }
}
