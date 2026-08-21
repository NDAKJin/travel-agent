package com.travelagent.travelagent.rag.ingestion;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import com.travelagent.travelagent.agent.prompt.PromptResourceLoader;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.apache.tika.parser.ParseContext;

@Service
public class RagIngestionService {

    private static final int CHUNK_SIZE = 800;
    private static final int CHUNK_OVERLAP = 100;
    private static final int MAX_LLM_INPUT_CHARS = 20_000;
    private static final List<String> SEPARATORS = List.of("\n\n", "\n", "。", "！", "？", "；", "，");

    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    private final PromptResourceLoader promptResourceLoader;
    private final JdbcTemplate jdbcTemplate;
    private final List<RagIngestionNode> nodes;

    public RagIngestionService(VectorStore vectorStore,
                               @Qualifier("finalizerChatClient") ChatClient chatClient,
                               PromptResourceLoader promptResourceLoader) {
        this(vectorStore, chatClient, promptResourceLoader, null);
    }

    @Autowired
    public RagIngestionService(VectorStore vectorStore,
                               @Qualifier("finalizerChatClient") ChatClient chatClient,
                               PromptResourceLoader promptResourceLoader,
                               JdbcTemplate jdbcTemplate) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClient;
        this.promptResourceLoader = promptResourceLoader;
        this.jdbcTemplate = jdbcTemplate;
        this.nodes = List.of(new ParseNode(), new DocumentMetadataNode(), new ChunkNode(),
                new ChunkMetadataNode(), new PersistNode());
    }

    public List<RagIngestionResult> importFiles(List<MultipartFile> files) {
        List<RagIngestionResult> results = new ArrayList<>();
        for (MultipartFile file : files) {
            results.add(importFile(file));
        }
        return results;
    }

    private RagIngestionResult importFile(MultipartFile file) {
        String fileName = StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : "unknown";
        try {
            if (file.isEmpty()) {
                return RagIngestionResult.failure(fileName, "文件为空");
            }
            return process(fileName, file.getContentType(), file.getBytes());
        } catch (Exception exception) {
            return RagIngestionResult.failure(fileName,
                    StringUtils.hasText(exception.getMessage()) ? exception.getMessage() : exception.getClass().getSimpleName());
        }
    }

    public RagIngestionResult process(String fileName, String contentType, byte[] bytes) {
        return process(fileName, contentType, bytes, ignored -> { });
    }

    public RagIngestionResult process(String fileName, String contentType, byte[] bytes,
                                      Consumer<String> stageListener) {
        RagIngestionContext context = new RagIngestionContext(fileName, contentType, bytes);
        try {
            for (RagIngestionNode node : nodes) {
                stageListener.accept(node.stage());
                node.execute(context);
            }
            return RagIngestionResult.success(fileName, context.chunks().size(), context.writtenCount());
        } catch (Exception exception) {
            return RagIngestionResult.failure(fileName,
                    StringUtils.hasText(exception.getMessage()) ? exception.getMessage() : exception.getClass().getSimpleName());
        }
    }

    private final class ParseNode implements RagIngestionNode {
        public String stage() { return "PARSING"; }
        public void execute(RagIngestionContext context) throws Exception {
            ParsedFile parsed = parse(context.bytes(), context.fileName(), context.contentType());
            if (!StringUtils.hasText(parsed.text())) throw new IllegalStateException("未解析到正文");
            context.text(parsed.text());
            context.mediaType(parsed.mediaType());
        }
    }

    private final class DocumentMetadataNode implements RagIngestionNode {
        public String stage() { return "DOC_ENRICHING"; }
        public void execute(RagIngestionContext context) {
            context.documentMetadata(extractDocumentMetadata(context.text()));
        }
    }

    private final class ChunkNode implements RagIngestionNode {
        public String stage() { return "CHUNKING"; }
        public void execute(RagIngestionContext context) {
            context.chunks(split(context.text(), context.documentMetadata()));
        }
    }

    private final class ChunkMetadataNode implements RagIngestionNode {
        public String stage() { return "CHUNK_ENRICHING"; }
        public void execute(RagIngestionContext context) {
            List<EmbeddingChunk> chunks = context.chunks();
            for (int i = 0; i < chunks.size(); i++) {
                chunks.set(i, chunks.get(i).withChunkMetadata(
                        extractChunkMetadata(context.documentMetadata(), chunks.get(i))));
            }
        }
    }

    private final class PersistNode implements RagIngestionNode {
        public String stage() { return "VECTORIZING"; }
        public void execute(RagIngestionContext context) {
            int written = write(context.fileName(), context.mediaType(), context.bytes(), context.chunks());
            persist(context.fileName(), context.mediaType(), context.text(), context.bytes(),
                    context.documentMetadata(), context.chunks());
            context.writtenCount(written);
        }
    }

    private ParsedFile parse(byte[] bytes, String fileName, String contentType) throws Exception {
        Metadata metadata = new Metadata();
        metadata.set("resourceName", fileName);
        BodyContentHandler handler = new BodyContentHandler(-1);
        new AutoDetectParser().parse(new ByteArrayInputStream(bytes), handler, metadata, new ParseContext());
        String mediaType = metadata.get(Metadata.CONTENT_TYPE);
        return new ParsedFile(handler.toString().trim(), mediaType == null ? contentType : mediaType);
    }

    private DocumentMetadata extractDocumentMetadata(String text) {
        String input = JSON.toJSONString(Map.of("text", limit(text)), JSONWriter.Feature.WriteMapNullValue);
        JSONObject json = callJson("rag-document-metadata", input);
        return new DocumentMetadata(
                text(json.getString("title")),
                text(json.getString("author")),
                strings(json.getJSONArray("keywords"), 10),
                text(json.getString("summary")),
                strings(json.getJSONArray("questions"), 5));
    }

    private ChunkMetadata extractChunkMetadata(DocumentMetadata documentMetadata, EmbeddingChunk chunk) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("document", documentMetadata);
        input.put("chunk", Map.of(
                "index", chunk.index(),
                "startOffset", chunk.startOffset(),
                "endOffset", chunk.endOffset(),
                "content", chunk.content()));
        JSONObject json = callJson("rag-chunk-metadata", JSON.toJSONString(input, JSONWriter.Feature.WriteMapNullValue));
        return new ChunkMetadata(
                strings(json.getJSONArray("keywords"), 10),
                text(json.getString("summary")),
                strings(json.getJSONArray("questions"), 5));
    }

    private JSONObject callJson(String promptName, String input) {
        String output = chatClient.prompt()
                .system(promptResourceLoader.load(promptName))
                .user(input)
                .call()
                .content();
        String normalized = stripCodeFence(output);
        JSONObject json = JSON.parseObject(normalized);
        if (json == null) {
            throw new IllegalStateException("LLM 返回的 JSON 无效");
        }
        return json;
    }

    private List<EmbeddingChunk> split(String text, DocumentMetadata documentMetadata) {
        List<EmbeddingChunk> chunks = new ArrayList<>();
        if (!StringUtils.hasText(text)) {
            return chunks;
        }
        int start = 0;
        int index = 0;
        while (start < text.length()) {
            int maxEnd = Math.min(text.length(), start + CHUNK_SIZE);
            int end = bestSplitPosition(text, start, maxEnd);
            if (end <= start) {
                end = maxEnd;
            }
            String content = text.substring(start, end).trim();
            if (!content.isEmpty()) {
                chunks.add(new EmbeddingChunk(index++, start, end, content, documentMetadata, null));
            }
            if (end >= text.length()) {
                break;
            }
            int nextStart = Math.max(start + 1, end - CHUNK_OVERLAP);
            start = nextStart;
        }
        return chunks;
    }

    private int bestSplitPosition(String text, int start, int maxEnd) {
        for (String separator : SEPARATORS) {
            int position = text.lastIndexOf(separator, maxEnd - separator.length());
            if (position > start) {
                return position + separator.length();
            }
        }
        return maxEnd;
    }

    private int write(String fileName, String mediaType, byte[] bytes, List<EmbeddingChunk> chunks) {
        if (chunks.isEmpty()) {
            return 0;
        }
        String fileHash = sha256(bytes);
        List<Document> documents = chunks.stream().map(chunk -> {
            String id = fileHash + "-" + chunk.index();
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("file_name", fileName);
            metadata.put("media_type", mediaType);
            metadata.put("chunk_id", id);
            metadata.put("chunk_index", chunk.index());
            metadata.put("start_offset", chunk.startOffset());
            metadata.put("end_offset", chunk.endOffset());
            metadata.put("content", chunk.content());
            metadata.put("document_title", chunk.documentMetadata().title());
            metadata.put("document_author", chunk.documentMetadata().author());
            metadata.put("document_keywords", chunk.documentMetadata().keywords());
            metadata.put("document_summary", chunk.documentMetadata().summary());
            metadata.put("document_questions", chunk.documentMetadata().questions());
            metadata.put("chunk_keywords", chunk.chunkMetadata().keywords());
            metadata.put("chunk_summary", chunk.chunkMetadata().summary());
            metadata.put("chunk_questions", chunk.chunkMetadata().questions());
            return Document.builder().id(id).text(chunk.embeddingText()).metadata(metadata).build();
        }).toList();
        vectorStore.add(documents);
        return documents.size();
    }

    private void persist(String fileName, String mediaType, String content, byte[] bytes,
                         DocumentMetadata document, List<EmbeddingChunk> chunks) {
        if (jdbcTemplate == null) {
            return;
        }
        String documentKey = sha256(bytes);
        jdbcTemplate.update("DELETE FROM rag_document WHERE document_key = ?", documentKey);
        jdbcTemplate.update("""
                INSERT INTO rag_document (document_key, file_name, media_type, title, author, keywords,
                    summary, questions, enabled, content, chunk_count, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, documentKey, fileName, mediaType, document.title(), document.author(),
                JSON.toJSONString(document.keywords()), document.summary(), JSON.toJSONString(document.questions()),
                content, chunks.size());
        Long documentId = jdbcTemplate.queryForObject("SELECT id FROM rag_document WHERE document_key = ?",
                Long.class, documentKey);
        if (documentId == null) {
            throw new IllegalStateException("RAG 文档索引写入失败");
        }
        for (EmbeddingChunk chunk : chunks) {
            ChunkMetadata metadata = chunk.chunkMetadata();
            jdbcTemplate.update("""
                    INSERT INTO rag_chunk (document_id, chunk_key, chunk_index, start_offset, end_offset,
                        content, keywords, summary, questions, enabled, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1, CURRENT_TIMESTAMP)
                    """, documentId, documentKey + "-" + chunk.index(), chunk.index(), chunk.startOffset(),
                    chunk.endOffset(), chunk.content(), JSON.toJSONString(metadata.keywords()), metadata.summary(),
                    JSON.toJSONString(metadata.questions()));
        }
    }

    private List<String> strings(JSONArray values, int limit) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .map(String::valueOf)
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .limit(limit)
                .toList();
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }

    private String stripCodeFence(String value) {
        if (!StringUtils.hasText(value)) {
            return "{}";
        }
        String normalized = value.trim();
        if (normalized.startsWith("```") && normalized.endsWith("```")) {
            int firstLineEnd = normalized.indexOf('\n');
            normalized = firstLineEnd >= 0 ? normalized.substring(firstLineEnd + 1, normalized.length() - 3) : "{}";
        }
        return normalized.trim();
    }

    private String limit(String value) {
        if (value == null || value.length() <= MAX_LLM_INPUT_CHARS) {
            return value == null ? "" : value;
        }
        return value.substring(0, MAX_LLM_INPUT_CHARS);
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    private record ParsedFile(String text, String mediaType) { }

    static record DocumentMetadata(String title, String author, List<String> keywords,
                                   String summary, List<String> questions) { }

    private record ChunkMetadata(List<String> keywords, String summary, List<String> questions) { }

    static record EmbeddingChunk(int index, int startOffset, int endOffset, String content,
                                 DocumentMetadata documentMetadata, ChunkMetadata chunkMetadata) {
        EmbeddingChunk withChunkMetadata(ChunkMetadata metadata) {
            return new EmbeddingChunk(index, startOffset, endOffset, content, documentMetadata, metadata);
        }

        String embeddingText() {
            return String.join("\n", List.of(
                    nullToEmpty(documentMetadata.title()),
                    String.join("、", documentMetadata.keywords()),
                    nullToEmpty(documentMetadata.summary()),
                    String.join("、", chunkMetadata.keywords()),
                    nullToEmpty(chunkMetadata.summary()),
                    content));
        }

        private static String nullToEmpty(String value) {
            return value == null ? "" : value;
        }
    }
}
