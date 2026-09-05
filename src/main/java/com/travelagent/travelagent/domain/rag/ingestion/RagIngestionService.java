package com.travelagent.travelagent.domain.rag.ingestion;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import com.travelagent.travelagent.domain.rag.model.ChunkMetadata;
import com.travelagent.travelagent.domain.rag.model.DocumentMetadata;
import com.travelagent.travelagent.domain.rag.model.EmbeddingChunk;
import com.travelagent.travelagent.infrastructure.ai.prompt.PromptResourceLoader;
import com.travelagent.travelagent.domain.rag.service.RagTextChunker;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class RagIngestionService {
    private static final int MAX_LLM_INPUT_CHARS = 20_000;

    private final ChatClient chatClient;
    private final PromptResourceLoader promptResourceLoader;
    private final RagKnowledgePersistenceService persistence;
    private final List<RagIngestionNode> nodes;
    private final RagTextChunker chunker = new RagTextChunker(
            800, 100, List.of("\n\n", "\n", ".", ",", "!", "?", "。", "，", "！", "？"));

    public RagIngestionService(@Qualifier("finalizerChatClient") ChatClient chatClient,
            PromptResourceLoader promptResourceLoader, RagKnowledgePersistenceService persistence) {
        this.chatClient = chatClient;
        this.promptResourceLoader = promptResourceLoader;
        this.persistence = persistence;
        this.nodes = List.of(new ParseNode(), new DocumentMetadataNode(), new ChunkNode(),
                new ChunkMetadataNode(), new PersistNode());
    }

    public RagIngestionResult process(String fileName, String contentType, byte[] bytes) {
        return process(fileName, contentType, bytes, (ignored, context) -> { });
    }

    public RagIngestionResult process(String fileName, String contentType, byte[] bytes,
            Consumer<String> stageListener) {
        return process(fileName, contentType, bytes, (stage, ignored) -> stageListener.accept(stage));
    }

    /** Executes exactly one pipeline stage and returns a serializable hand-off artifact. */
    public RagStageArtifact processStage(String stage, RagStageArtifact artifact) throws Exception {
        byte[] bytes = Base64.getDecoder().decode(artifact.bytesBase64());
        RagIngestionContext context = new RagIngestionContext(artifact.fileName(), artifact.contentType(), bytes);
        context.text(artifact.text());
        context.mediaType(artifact.mediaType());
        context.documentMetadata(artifact.documentMetadata());
        context.chunks(artifact.chunks() == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(artifact.chunks()));
        context.writtenCount(artifact.writtenCount());
        RagIngestionNode node = nodes.stream().filter(candidate -> candidate.stage().equals(stage)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown RAG stage: " + stage));
        node.execute(context);
        return new RagStageArtifact(context.fileName(), context.contentType(), Base64.getEncoder().encodeToString(context.bytes()),
                context.text(), context.mediaType(), context.documentMetadata(), context.chunks(), context.writtenCount());
    }

    public RagStageArtifact initialArtifact(String fileName, String contentType, byte[] bytes) {
        return new RagStageArtifact(fileName, contentType, Base64.getEncoder().encodeToString(bytes), null, null, null, List.of(), 0);
    }

    public RagIngestionResult process(String fileName, String contentType, byte[] bytes,
            BiConsumer<String, RagIngestionContext> stageListener) {
        return process(fileName, contentType, bytes, stageListener, () -> false);
    }

    public RagIngestionResult process(String fileName, String contentType, byte[] bytes,
            BiConsumer<String, RagIngestionContext> stageListener, BooleanSupplier cancellationCheck) {
        RagIngestionContext context = new RagIngestionContext(fileName, contentType, bytes);
        context.cancellationCheck(cancellationCheck);
        String stage = "INITIALIZING";
        log.info("RAG ingestion started: fileName={}, contentType={}, bytes={}", fileName, contentType, bytes.length);
        try {
            for (RagIngestionNode node : nodes) {
                stage = node.stage();
                stageListener.accept(stage, context);
                node.execute(context);
                stageListener.accept(stage, context);
            }
            return RagIngestionResult.success(fileName, context.chunks().size(), context.writtenCount());
        } catch (Exception exception) {
            log.error("RAG ingestion failed: fileName={}, stage={}", fileName, stage, exception);
            return RagIngestionResult.failure(fileName,
                    StringUtils.hasText(exception.getMessage()) ? exception.getMessage() : exception.getClass().getSimpleName(),
                    context.chunks().size(), context.writtenCount());
        }
    }

    private final class ParseNode implements RagIngestionNode {
        public String stage() { return "PARSING"; }
        public void execute(RagIngestionContext context) throws Exception {
            ParsedFile parsed = parse(context.bytes(), context.fileName(), context.contentType());
            if (!StringUtils.hasText(parsed.text())) throw new IllegalStateException("No text extracted");
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
            context.chunks(chunker.split(context.text(), context.documentMetadata()));
        }
    }

    private final class ChunkMetadataNode implements RagIngestionNode {
        public String stage() { return "CHUNK_ENRICHING"; }
        public void execute(RagIngestionContext context) {
            List<EmbeddingChunk> chunks = context.chunks();
            for (int i = 0; i < chunks.size(); i++) {
                if (context.cancelled()) throw new IllegalStateException("Task cancelled");
                EmbeddingChunk chunk = chunks.get(i);
                chunks.set(i, chunk.withChunkMetadata(extractChunkMetadata(context.documentMetadata(), chunk)));
            }
        }
    }

    private final class PersistNode implements RagIngestionNode {
        public String stage() { return "PERSISTING"; }
        public void execute(RagIngestionContext context) {
            String documentKey = sha256(context.bytes());
            List<Document> vectors = buildVectorDocuments(context.fileName(), context.mediaType(), documentKey,
                    context.chunks());
            int written = persistence.persist(context.fileName(), context.mediaType(), context.text(), documentKey,
                    context.documentMetadata(), context.chunks(), vectors);
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
        JSONObject json = callJson("rag-document-metadata",
                JSON.toJSONString(Map.of("text", limit(text)), JSONWriter.Feature.WriteMapNullValue));
        return new DocumentMetadata(text(json.getString("title")), text(json.getString("author")),
                strings(json.getJSONArray("keywords"), 10), text(json.getString("summary")),
                strings(json.getJSONArray("questions"), 5));
    }

    private ChunkMetadata extractChunkMetadata(DocumentMetadata documentMetadata, EmbeddingChunk chunk) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("document", documentMetadata);
        input.put("chunk", Map.of("index", chunk.index(), "startOffset", chunk.startOffset(),
                "endOffset", chunk.endOffset(), "content", chunk.content()));
        JSONObject json = callJson("rag-chunk-metadata", JSON.toJSONString(input, JSONWriter.Feature.WriteMapNullValue));
        return new ChunkMetadata(strings(json.getJSONArray("keywords"), 10), text(json.getString("summary")),
                strings(json.getJSONArray("questions"), 5));
    }

    private JSONObject callJson(String promptName, String input) {
        String output = chatClient.prompt().system(promptResourceLoader.load(promptName)).user(input).call().content();
        String normalized = stripCodeFence(output);
        JSONObject json = JSON.parseObject(normalized);
        if (json == null) throw new IllegalStateException("Metadata model returned empty JSON");
        return json;
    }

    private List<Document> buildVectorDocuments(String fileName, String mediaType, String documentKey,
            List<EmbeddingChunk> chunks) {
        return chunks.stream().map(chunk -> {
            String id = documentKey + "-" + chunk.index();
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
            metadata.put("enabled", true);
            return Document.builder().id(id).text(chunk.embeddingText()).metadata(metadata).build();
        }).toList();
    }

    private List<String> strings(JSONArray values, int limit) {
        if (values == null) return List.of();
        return values.stream().map(String::valueOf).map(String::trim).filter(StringUtils::hasText)
                .distinct().limit(limit).toList();
    }

    private String text(String value) { return value == null ? "" : value.trim(); }

    private String stripCodeFence(String value) {
        if (!StringUtils.hasText(value)) return "{}";
        String normalized = value.trim();
        if (normalized.startsWith("```") && normalized.endsWith("```")) {
            int firstLineEnd = normalized.indexOf('\n');
            normalized = firstLineEnd >= 0 ? normalized.substring(firstLineEnd + 1, normalized.length() - 3) : "{}";
        }
        return normalized.trim();
    }

    private String limit(String value) {
        if (value == null || value.length() <= MAX_LLM_INPUT_CHARS) return value == null ? "" : value;
        return value.substring(0, MAX_LLM_INPUT_CHARS);
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private record ParsedFile(String text, String mediaType) { }
}
