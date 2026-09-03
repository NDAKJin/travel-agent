package com.travelagent.travelagent.domain.rag.ingestion;

import com.alibaba.fastjson2.JSON;
import com.travelagent.travelagent.domain.rag.model.ChunkMetadata;
import com.travelagent.travelagent.domain.rag.model.DocumentMetadata;
import com.travelagent.travelagent.domain.rag.model.EmbeddingChunk;
import com.travelagent.travelagent.infrastructure.rag.qdrant.RagVectorOutboxService;
import com.travelagent.travelagent.infrastructure.cache.RedisDocumentLock;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import org.springframework.ai.document.Document;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Persists the MySQL knowledge model and vector events atomically. */
@Service
public class RagKnowledgePersistenceService {
    private final JdbcTemplate jdbc;
    private final RagVectorOutboxService vectorOutbox;
    private final RedisDocumentLock documentLock;
    private final TransactionTemplate transactionTemplate;

    public RagKnowledgePersistenceService(JdbcTemplate jdbc, RagVectorOutboxService vectorOutbox,
            RedisDocumentLock documentLock, TransactionTemplate transactionTemplate) {
        this.jdbc = jdbc;
        this.vectorOutbox = vectorOutbox;
        this.documentLock = documentLock;
        this.transactionTemplate = transactionTemplate;
    }

    public int persist(String fileName, String mediaType, String content, String documentKey,
            DocumentMetadata document, List<EmbeddingChunk> chunks, List<Document> vectors) {
        // Acquire distributed locks before opening a JDBC transaction. Lock
        // contention must not consume a database connection from the pool.
        try (RedisDocumentLock.LockHandle ignored = documentLock.acquire(fileName, documentKey)) {
            Integer written = transactionTemplate.execute(status ->
                    persistLocked(fileName, mediaType, content, documentKey, document, chunks, vectors));
            return written == null ? 0 : written;
        }
    }

    private int persistLocked(String fileName, String mediaType, String content, String documentKey,
            DocumentMetadata document, List<EmbeddingChunk> chunks, List<Document> vectors) {
        Set<String> previousKeys = new HashSet<>(jdbc.query("""
                SELECT c.chunk_key
                FROM rag_chunk c JOIN rag_document d ON d.id = c.document_id
                WHERE d.file_name = ? OR d.document_key = ?
                """, (rs, rowNum) -> rs.getString(1), fileName, documentKey));
        Set<String> nextKeys = new HashSet<>();
        for (Document vector : vectors) {
            if (vector.getId() != null) nextKeys.add(vector.getId());
        }
        // A content hash changes when a file is re-imported. Treat the file name
        // as the logical document identity so the previous version is removed too.
        jdbc.update("DELETE FROM rag_document WHERE file_name = ? OR document_key = ?", fileName, documentKey);
        jdbc.update("""
                INSERT INTO rag_document (document_key, file_name, media_type, title, author, keywords,
                    summary, questions, enabled, content, chunk_count, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, documentKey, fileName, mediaType, document.title(), document.author(),
                JSON.toJSONString(document.keywords()), document.summary(), JSON.toJSONString(document.questions()),
                content, chunks.size());
        Long documentId = jdbc.queryForObject("SELECT id FROM rag_document WHERE document_key = ?",
                Long.class, documentKey);
        if (documentId == null) throw new IllegalStateException("RAG document persistence failed");
        for (EmbeddingChunk chunk : chunks) {
            ChunkMetadata metadata = chunk.chunkMetadata();
            jdbc.update("""
                    INSERT INTO rag_chunk (document_id, chunk_key, chunk_index, start_offset, end_offset,
                        content, keywords, summary, questions, enabled, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1, CURRENT_TIMESTAMP)
                    """, documentId, documentKey + "-" + chunk.index(), chunk.index(), chunk.startOffset(),
                    chunk.endOffset(), chunk.content(), JSON.toJSONString(metadata.keywords()), metadata.summary(),
                    JSON.toJSONString(metadata.questions()));
        }
        // Remove points that belonged to the previous version but are absent from
        // the new chunk set. The DELETE events are in the same DB transaction as
        // the replacement and are emitted before the new UPSERT events.
        previousKeys.removeAll(nextKeys);
        for (String staleKey : previousKeys) vectorOutbox.enqueueDelete(staleKey);
        for (Document vector : vectors) vectorOutbox.enqueueUpsert(vector);
        return vectors.size();
    }

}
