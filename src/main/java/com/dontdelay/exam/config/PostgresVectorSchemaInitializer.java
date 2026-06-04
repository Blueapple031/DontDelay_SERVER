package com.dontdelay.exam.config;

import com.dontdelay.exam.infra.DatabaseDialectHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PostgresVectorSchemaInitializer implements ApplicationRunner {

    private final DatabaseDialectHelper databaseDialectHelper;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        if (!databaseDialectHelper.isPostgres()) {
            return;
        }

        jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");
        jdbcTemplate.execute("""
                ALTER TABLE document_chunk
                ADD COLUMN IF NOT EXISTS embedding vector(1536)
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_document_chunk_embedding
                ON document_chunk USING hnsw (embedding vector_cosine_ops)
                """);
        log.info("PostgreSQL pgvector schema initialized for document_chunk");
    }
}
