package com.travelagent.travelagent.infrastructure.persistence.rag;

import java.util.List;
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
        // The document lock is distributed through Redis; remove the obsolete
        // database lock table left by an earlier migration if it exists.
        jdbcTemplate.execute("DROP TABLE IF EXISTS rag_document_lock");
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
                    task_id BIGINT NOT NULL,
                    topic VARCHAR(255) NOT NULL,
                    message_key VARCHAR(128) NOT NULL,
                    payload MEDIUMTEXT NOT NULL,
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL,
                    CONSTRAINT fk_rag_ingestion_outbox_task FOREIGN KEY (task_id)
                        REFERENCES rag_ingestion_task (id) ON DELETE CASCADE,
                    INDEX idx_rag_ingestion_outbox_task (task_id),
                    INDEX idx_rag_ingestion_outbox_created (created_at)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS rag_vector_outbox (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    chunk_key VARCHAR(255) NOT NULL,
                    operation VARCHAR(16) NOT NULL,
                    payload MEDIUMTEXT,
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL,
                    INDEX idx_rag_vector_outbox_created (created_at),
                    INDEX idx_rag_vector_outbox_chunk (chunk_key)
                )
                """);
        migrateOutboxTables();
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS rag_vector_delivery (
                    event_id BIGINT PRIMARY KEY,
                    chunk_key VARCHAR(255) NOT NULL,
                    operation VARCHAR(16) NOT NULL,
                    canal_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
                    kafka_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
                    qdrant_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
                    attempts INT NOT NULL DEFAULT 0,
                    last_error TEXT,
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL,
                    processed_at TIMESTAMP NULL,
                    INDEX idx_rag_vector_delivery_status (qdrant_status, updated_at),
                    INDEX idx_rag_vector_delivery_chunk (chunk_key)
                )
                """);
    }

    private void migrateOutboxTables() {
        ensureIndex("rag_ingestion_outbox", "idx_rag_ingestion_outbox_task", "task_id");
        dropSingleColumnUniqueIndexes("rag_ingestion_outbox", "task_id");
        dropIndexIfExists("rag_ingestion_outbox", "idx_rag_ingestion_outbox_dispatch");
        dropColumnIfExists("rag_ingestion_outbox", "status");
        dropColumnIfExists("rag_ingestion_outbox", "attempts");
        dropColumnIfExists("rag_ingestion_outbox", "next_attempt_at");
        dropColumnIfExists("rag_ingestion_outbox", "last_error");
        dropColumnIfExists("rag_ingestion_outbox", "sent_at");
        ensureIndex("rag_ingestion_outbox", "idx_rag_ingestion_outbox_created", "created_at");

        dropIndexIfExists("rag_vector_outbox", "idx_rag_vector_outbox_dispatch");
        dropColumnIfExists("rag_vector_outbox", "status");
        dropColumnIfExists("rag_vector_outbox", "attempts");
        dropColumnIfExists("rag_vector_outbox", "next_attempt_at");
        dropColumnIfExists("rag_vector_outbox", "last_error");
        dropColumnIfExists("rag_vector_outbox", "processed_at");
        ensureIndex("rag_vector_outbox", "idx_rag_vector_outbox_created", "created_at");
        ensureIndex("rag_vector_outbox", "idx_rag_vector_outbox_chunk", "chunk_key");
    }

    private void ensureIndex(String table, String index, String column) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ?
                """, Integer.class, table, index);
        if (count != null && count == 0) {
            jdbcTemplate.execute("ALTER TABLE " + table + " ADD INDEX " + index + " (" + column + ")");
        }
    }

    private void dropSingleColumnUniqueIndexes(String table, String column) {
        List<String> indexes = jdbcTemplate.query("""
                SELECT index_name
                FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?
                  AND non_unique = 0 AND index_name <> 'PRIMARY'
                GROUP BY index_name
                HAVING COUNT(*) = 1
                """, (rs, rowNum) -> rs.getString(1), table, column);
        for (String index : indexes) {
            jdbcTemplate.execute("ALTER TABLE " + table + " DROP INDEX " + index);
        }
    }

    private void dropIndexIfExists(String table, String index) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ?
                """, Integer.class, table, index);
        if (count != null && count > 0) {
            jdbcTemplate.execute("ALTER TABLE " + table + " DROP INDEX " + index);
        }
    }

    private void dropColumnIfExists(String table, String column) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?
                """, Integer.class, table, column);
        if (count != null && count > 0) {
            jdbcTemplate.execute("ALTER TABLE " + table + " DROP COLUMN " + column);
        }
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
