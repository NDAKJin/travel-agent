package com.travelagent.travelagent.application.rag.admin;

import com.alibaba.fastjson2.JSON;
import com.travelagent.travelagent.application.rag.port.in.RagCatalogUseCase;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class RagAdminService implements RagCatalogUseCase {

    private static final int MAX_BATCH_SIZE = 500;

    private final JdbcTemplate jdbcTemplate;
    private final VectorStore vectorStore;

    @Override
    public List<RagDocumentResponse> documents(String keyword) {
        String value = keyword == null ? "" : keyword.trim();
        return jdbcTemplate.query("""
                SELECT id, document_key, file_name, media_type, title, author, keywords, summary,
                       questions, enabled, chunk_count, created_at, updated_at
                FROM rag_document
                WHERE ? = '' OR file_name LIKE CONCAT('%', ?, '%') OR title LIKE CONCAT('%', ?, '%')
                ORDER BY updated_at DESC
                """, (rs, rowNum) -> new RagDocumentResponse(rs.getLong("id"), rs.getString("document_key"),
                rs.getString("file_name"), rs.getString("media_type"), rs.getString("title"), rs.getString("author"),
                rs.getString("keywords"), rs.getString("summary"), rs.getString("questions"),
                rs.getBoolean("enabled"), rs.getInt("chunk_count"), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()), value, value, value);
    }

    public List<RagChunkResponse> chunks(Long documentId, String keyword) {
        return chunks(documentId, keyword, null);
    }

    @Override
    public List<RagChunkResponse> chunks(Long documentId, String keyword, Integer enabled) {
        String value = keyword == null ? "" : keyword.trim();
        return jdbcTemplate.query("""
                SELECT c.id, c.document_id, d.document_key, d.file_name, c.chunk_index, c.start_offset,
                       c.end_offset, c.content, c.keywords, c.summary, c.questions, c.enabled
                FROM rag_chunk c JOIN rag_document d ON d.id = c.document_id
                WHERE (? IS NULL OR c.document_id = ?)
                  AND (? IS NULL OR c.enabled = ?)
                  AND (? = '' OR c.content LIKE CONCAT('%', ?, '%') OR c.keywords LIKE CONCAT('%', ?, '%'))
                ORDER BY c.document_id DESC, c.chunk_index
                """, (rs, rowNum) -> new RagChunkResponse(rs.getLong("id"), rs.getLong("document_id"),
                rs.getString("document_key"), rs.getString("file_name"), rs.getInt("chunk_index"),
                rs.getInt("start_offset"), rs.getInt("end_offset"), rs.getString("content"),
                rs.getString("keywords"), rs.getString("summary"), rs.getString("questions"),
                rs.getBoolean("enabled")), documentId, documentId, enabled, enabled, value, value, value);
    }

    @Transactional
    @Override
    public void toggleDocument(long documentId, boolean enabled) {
        DocumentRow document = document(documentId);
        int target = enabled ? 1 : 0;
        if (document.enabled() == target) return;
        List<ChunkRow> chunks = chunkRows(documentId);
        if (enabled) addVectors(document, chunks); else deleteVectors(chunks);
        jdbcTemplate.update("UPDATE rag_document SET enabled = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", target, documentId);
        jdbcTemplate.update("UPDATE rag_chunk SET enabled = ? WHERE document_id = ?", target, documentId);
    }

    @Transactional
    @Override
    public void toggleChunk(long chunkId, boolean enabled) {
        ChunkWithDocument row = chunkWithDocument(chunkId);
        int target = enabled ? 1 : 0;
        if (row.chunk().enabled() == target) return;
        if (enabled && row.document().enabled() != 1) throw new IllegalArgumentException("文档未启用，请先启用文档");
        if (enabled) vectorStore.add(List.of(toVectorDocument(row.document(), row.chunk())));
        else vectorStore.delete(List.of(row.chunk().chunkKey()));
        jdbcTemplate.update("UPDATE rag_chunk SET enabled = ? WHERE id = ?", target, chunkId);
    }

    @Override
    public void batchToggleChunks(List<Long> chunkIds, boolean enabled) {
        if (chunkIds == null || chunkIds.isEmpty()) throw new IllegalArgumentException("请至少选择一个 Chunk");
        if (chunkIds.size() > MAX_BATCH_SIZE) throw new IllegalArgumentException("单次最多操作 500 个 Chunk");
        chunkIds.stream().distinct().forEach(id -> toggleChunk(id, enabled));
    }

    private DocumentRow document(long id) {
        List<DocumentRow> rows = jdbcTemplate.query("""
                SELECT id, document_key, file_name, media_type, title, author, keywords, summary, questions, enabled
                FROM rag_document WHERE id = ?
                """, (rs, rowNum) -> new DocumentRow(rs.getLong("id"), rs.getString("document_key"),
                rs.getString("file_name"), rs.getString("media_type"), rs.getString("title"), rs.getString("author"),
                rs.getString("keywords"), rs.getString("summary"), rs.getString("questions"), rs.getInt("enabled")), id);
        if (rows.isEmpty()) throw new IllegalArgumentException("RAG 文档不存在");
        return rows.get(0);
    }

    private ChunkWithDocument chunkWithDocument(long chunkId) {
        List<ChunkWithDocument> rows = jdbcTemplate.query("""
                SELECT d.id document_id, d.document_key, d.file_name, d.media_type, d.title, d.author,
                       d.keywords document_keywords, d.summary document_summary, d.questions document_questions,
                       d.enabled document_enabled, c.id, c.chunk_key, c.chunk_index, c.start_offset, c.end_offset,
                       c.content, c.keywords, c.summary, c.questions, c.enabled
                FROM rag_chunk c JOIN rag_document d ON d.id = c.document_id WHERE c.id = ?
                """, (rs, rowNum) -> {
                    DocumentRow document = new DocumentRow(rs.getLong("document_id"), rs.getString("document_key"),
                            rs.getString("file_name"), rs.getString("media_type"), rs.getString("title"), rs.getString("author"),
                            rs.getString("document_keywords"), rs.getString("document_summary"),
                            rs.getString("document_questions"), rs.getInt("document_enabled"));
                    ChunkRow chunk = new ChunkRow(rs.getLong("id"), rs.getString("chunk_key"), rs.getInt("chunk_index"),
                            rs.getInt("start_offset"), rs.getInt("end_offset"), rs.getString("content"),
                            rs.getString("keywords"), rs.getString("summary"), rs.getString("questions"), rs.getInt("enabled"));
                    return new ChunkWithDocument(document, chunk);
                }, chunkId);
        if (rows.isEmpty()) throw new IllegalArgumentException("RAG Chunk 不存在");
        return rows.get(0);
    }

    private List<ChunkRow> chunkRows(long documentId) {
        return jdbcTemplate.query("""
                SELECT id, chunk_key, chunk_index, start_offset, end_offset, content, keywords, summary, questions, enabled
                FROM rag_chunk WHERE document_id = ? ORDER BY chunk_index
                """, (rs, rowNum) -> new ChunkRow(rs.getLong("id"), rs.getString("chunk_key"), rs.getInt("chunk_index"),
                rs.getInt("start_offset"), rs.getInt("end_offset"), rs.getString("content"), rs.getString("keywords"),
                rs.getString("summary"), rs.getString("questions"), rs.getInt("enabled")), documentId);
    }

    private void addVectors(DocumentRow document, List<ChunkRow> chunks) {
        if (!chunks.isEmpty()) vectorStore.add(chunks.stream().map(chunk -> toVectorDocument(document, chunk)).toList());
    }

    private void deleteVectors(List<ChunkRow> chunks) {
        List<String> ids = chunks.stream().map(ChunkRow::chunkKey).filter(StringUtils::hasText).toList();
        if (!ids.isEmpty()) vectorStore.delete(ids);
    }

    private Document toVectorDocument(DocumentRow document, ChunkRow chunk) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("file_name", document.fileName());
        metadata.put("media_type", document.mediaType());
        metadata.put("chunk_id", chunk.chunkKey());
        metadata.put("chunk_index", chunk.chunkIndex());
        metadata.put("start_offset", chunk.startOffset());
        metadata.put("end_offset", chunk.endOffset());
        metadata.put("content", chunk.content());
        metadata.put("document_title", document.title());
        metadata.put("document_author", document.author());
        metadata.put("document_keywords", jsonList(document.keywords()));
        metadata.put("document_summary", document.summary());
        metadata.put("document_questions", jsonList(document.questions()));
        metadata.put("chunk_keywords", jsonList(chunk.keywords()));
        metadata.put("chunk_summary", chunk.summary());
        metadata.put("chunk_questions", jsonList(chunk.questions()));
        return Document.builder().id(chunk.chunkKey()).text(embeddingText(document, chunk)).metadata(metadata).build();
    }

    private String embeddingText(DocumentRow document, ChunkRow chunk) {
        return String.join("\n", empty(document.title()), String.join("、", jsonList(document.keywords())),
                empty(document.summary()), String.join("、", jsonList(chunk.keywords())), empty(chunk.summary()), empty(chunk.content()));
    }

    private List<String> jsonList(String value) {
        if (!StringUtils.hasText(value)) return List.of();
        try {
            List<String> values = JSON.parseArray(value, String.class);
            return values == null ? List.of() : values;
        } catch (RuntimeException ignored) {
            return List.of(value);
        }
    }

    private String empty(String value) { return value == null ? "" : value; }

    private record DocumentRow(long id, String documentKey, String fileName, String mediaType, String title,
                               String author, String keywords, String summary, String questions, int enabled) { }
    private record ChunkRow(long id, String chunkKey, int chunkIndex, int startOffset, int endOffset,
                            String content, String keywords, String summary, String questions, int enabled) { }
    private record ChunkWithDocument(DocumentRow document, ChunkRow chunk) { }
}
