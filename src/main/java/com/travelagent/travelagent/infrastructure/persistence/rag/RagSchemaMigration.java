package com.travelagent.travelagent.infrastructure.persistence.rag;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RagSchemaMigration implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        ensureEnabledColumn("rag_document");
        ensureEnabledColumn("rag_chunk");
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS rag_ingestion_task (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    file_name VARCHAR(255) NOT NULL,
                    status VARCHAR(16) NOT NULL,
                    chunk_count INT NOT NULL DEFAULT 0,
                    written_count INT NOT NULL DEFAULT 0,
                    error_message TEXT,
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL,
                    INDEX idx_rag_ingestion_task_updated (updated_at)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS rag_ingestion_outbox (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    task_id BIGINT NOT NULL UNIQUE,
                    topic VARCHAR(255) NOT NULL,
                    message_key VARCHAR(128) NOT NULL,
                    payload MEDIUMTEXT NOT NULL,
                    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
                    attempts INT NOT NULL DEFAULT 0,
                    next_attempt_at TIMESTAMP NOT NULL,
                    last_error TEXT,
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL,
                    sent_at TIMESTAMP NULL,
                    CONSTRAINT fk_rag_ingestion_outbox_task FOREIGN KEY (task_id)
                        REFERENCES rag_ingestion_task (id) ON DELETE CASCADE,
                    INDEX idx_rag_ingestion_outbox_dispatch (status, next_attempt_at, updated_at)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS rag_vector_outbox (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    chunk_key VARCHAR(255) NOT NULL,
                    operation VARCHAR(16) NOT NULL,
                    payload MEDIUMTEXT,
                    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
                    attempts INT NOT NULL DEFAULT 0,
                    next_attempt_at TIMESTAMP NOT NULL,
                    last_error TEXT,
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL,
                    processed_at TIMESTAMP NULL,
                    INDEX idx_rag_vector_outbox_dispatch (status, next_attempt_at, updated_at)
                )
                """);
    }

    private void ensureEnabledColumn(String table) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = ? AND column_name = 'enabled'
                """, Integer.class, table);
        if (count != null && count == 0) {
            jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN enabled TINYINT(1) NOT NULL DEFAULT 1");
        }
    }
}
