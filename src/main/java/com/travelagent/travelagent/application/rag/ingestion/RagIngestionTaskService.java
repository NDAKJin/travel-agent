package com.travelagent.travelagent.application.rag.ingestion;

import com.alibaba.fastjson2.JSON;
import com.travelagent.travelagent.application.rag.model.RagIngestionMessage;
import com.travelagent.travelagent.application.rag.port.in.RagIngestionUseCase;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
public class RagIngestionTaskService implements RagIngestionUseCase {
    private final RagIngestionService ingestionService;
    private final JdbcTemplate jdbc;
    private final KafkaTemplate<String, String> kafka;
    private final String topic;
    private final Path root;

    public RagIngestionTaskService(RagIngestionService ingestionService, JdbcTemplate jdbc, KafkaTemplate<String, String> kafka,
                                   @Value("${travel-agent.rag.ingestion-topic:rag-ingestion}") String topic,
                                   @Value("${travel-agent.rag.ingestion-storage-dir:${java.io.tmpdir}/travel-agent-rag}") String storageDir) {
        this.ingestionService = ingestionService;
        this.jdbc = jdbc;
        this.kafka = kafka;
        this.topic = topic;
        this.root = Paths.get(storageDir);
    }

    @Override
    public List<RagIngestionTaskResponse> submit(List<MultipartFile> files) {
        List<RagIngestionTaskResponse> result = new ArrayList<>();
        for (MultipartFile file : files) {
            String name = file.getOriginalFilename() == null ? "unknown" : Path.of(file.getOriginalFilename()).getFileName().toString();
            try {
                if (file.isEmpty()) throw new IllegalArgumentException("文件为空");
                long id = create(name);
                Path dir = root.resolve(Long.toString(id));
                Files.createDirectories(dir);
                Path path = dir.resolve(name);
                Files.write(path, file.getBytes());
                String payload = JSON.toJSONString(new RagIngestionMessage(id, path.toString(), file.getContentType(), name));
                kafka.send(topic, Long.toString(id), payload).whenComplete((ignored, error) -> {
                    if (error != null) {
                        fail(id, error.getMessage());
                        log.error("RAG ingestion message failed: taskId={}", id, error);
                    } else {
                        log.info("RAG ingestion message sent: taskId={}, topic={}", id, topic);
                    }
                });
                result.add(response(id, name));
            } catch (Exception e) {
                result.add(new RagIngestionTaskResponse(null, name, "FAILED", 0, 0, e.getMessage(), Instant.now(), Instant.now()));
            }
        }
        return result;
    }

    @Override
    public void process(RagIngestionMessage message) {
        updateStatus(message.taskId(), "RUNNING", null, 0, 0);
        RagIngestionResult outcome;
        try {
            byte[] bytes = Files.readAllBytes(Path.of(message.path()));
            outcome = ingestionService.process(message.fileName(), message.contentType(), bytes,
                    stage -> updateStatus(message.taskId(), stage, null, 0, 0));
            updateStatus(message.taskId(), outcome.status(), outcome.error(), outcome.chunkCount(), outcome.writtenCount());
            if ("SUCCESS".equals(outcome.status())) Files.deleteIfExists(Path.of(message.path()));
        } catch (Exception e) {
            fail(message.taskId(), e.getMessage());
        }
    }

    @Override
    public List<RagIngestionTaskResponse> list() {
        return jdbc.query("SELECT id,file_name,status,chunk_count,written_count,error_message,created_at,updated_at FROM rag_ingestion_task ORDER BY id DESC",
                (rs, n) -> new RagIngestionTaskResponse(rs.getLong("id"), rs.getString("file_name"), rs.getString("status"),
                        rs.getInt("chunk_count"), rs.getInt("written_count"), rs.getString("error_message"),
                        rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()));
    }

    private long create(String name) {
        KeyHolder holder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("INSERT INTO rag_ingestion_task (file_name,status,chunk_count,written_count,created_at,updated_at) VALUES (?, 'PENDING',0,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, name); return ps;
        }, holder);
        return holder.getKey().longValue();
    }

    private RagIngestionTaskResponse response(long id, String name) {
        return new RagIngestionTaskResponse(id, name, "PENDING", 0, 0, null, Instant.now(), Instant.now());
    }

    private void fail(long id, String error) { updateStatus(id, "FAILED", error, 0, 0); }

    private void updateStatus(long id, String status, String error, int chunks, int written) {
        jdbc.update("UPDATE rag_ingestion_task SET status=?,error_message=?,chunk_count=?,written_count=?,updated_at=CURRENT_TIMESTAMP WHERE id=?", status, error, chunks, written, id);
    }

}
