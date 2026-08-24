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
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
public class RagIngestionTaskService implements RagIngestionUseCase {
    private static final int MAX_ATTEMPTS = 5;
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

    @Transactional
    @Override
    public List<RagIngestionTaskResponse> submit(List<MultipartFile> files) {
        List<RagIngestionTaskResponse> result = new ArrayList<>();
        for (MultipartFile file : files) {
            String name = file.getOriginalFilename() == null ? "unknown" : Path.of(file.getOriginalFilename()).getFileName().toString();
            Long taskId = null;
            try {
                if (file.isEmpty()) throw new IllegalArgumentException("文件为空");
                taskId = create(name);
                Path dir = root.resolve(Long.toString(taskId));
                Files.createDirectories(dir);
                Path path = dir.resolve(name);
                Files.write(path, file.getBytes());
                String payload = JSON.toJSONString(new RagIngestionMessage(taskId, path.toString(), file.getContentType(), name));
                enqueue(taskId, payload);
                result.add(response(taskId, name));
            } catch (Exception e) {
                if (taskId != null) fail(taskId, e.getMessage());
                result.add(new RagIngestionTaskResponse(null, name, "FAILED", 0, 0, e.getMessage(), Instant.now(), Instant.now()));
            }
        }
        return result;
    }

    @Scheduled(fixedDelayString = "${travel-agent.rag.outbox-dispatch-interval-ms:1000}")
    public void dispatchOutbox() {
        recoverStaleTasks();
        List<OutboxRow> rows = jdbc.query("""
                SELECT id, task_id, topic, message_key, payload, attempts
                FROM rag_ingestion_outbox
                WHERE next_attempt_at <= CURRENT_TIMESTAMP
                  AND (status = 'PENDING'
                       OR (status = 'SENDING' AND updated_at < DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 1 MINUTE)))
                ORDER BY id
                LIMIT 50
                """, (rs, rowNum) -> new OutboxRow(rs.getLong("id"), rs.getLong("task_id"),
                rs.getString("topic"), rs.getString("message_key"), rs.getString("payload"), rs.getInt("attempts")));
        for (OutboxRow row : rows) {
            if (jdbc.update("""
                    UPDATE rag_ingestion_outbox
                    SET status = 'SENDING', attempts = attempts + 1, updated_at = CURRENT_TIMESTAMP
                    WHERE id = ? AND (status = 'PENDING'
                        OR (status = 'SENDING' AND updated_at < DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 1 MINUTE)))
                    """, row.id()) != 1) continue;
            kafka.send(row.topic(), row.messageKey(), row.payload()).whenComplete((ignored, error) -> {
                if (error == null) {
                    jdbc.update("""
                            UPDATE rag_ingestion_outbox
                            SET status = 'SENT', sent_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                            WHERE id = ?
                            """, row.id());
                    jdbc.update("""
                            UPDATE rag_ingestion_task SET status = 'DISPATCHED', error_message = NULL,
                                updated_at = CURRENT_TIMESTAMP
                            WHERE id = ? AND status = 'PENDING'
                            """, row.taskId());
                    log.info("RAG ingestion message sent: taskId={}, outboxId={}, topic={}",
                            row.taskId(), row.id(), row.topic());
                    return;
                }
                int attempt = row.attempts() + 1;
                if (attempt >= MAX_ATTEMPTS) {
                    jdbc.update("UPDATE rag_ingestion_outbox SET status = 'FAILED', last_error = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                            error.getMessage(), row.id());
                    updateTaskStatus(row.taskId(), "FAILED", error.getMessage());
                    log.error("RAG ingestion message reached retry limit: taskId={}, outboxId={}", row.taskId(), row.id(), error);
                    return;
                }
                Instant nextAttempt = Instant.now().plusSeconds(backoffSeconds(row.attempts() + 1));
                jdbc.update("""
                        UPDATE rag_ingestion_outbox
                        SET status = 'PENDING', next_attempt_at = ?, last_error = ?, updated_at = CURRENT_TIMESTAMP
                        WHERE id = ?
                        """, nextAttempt, error.getMessage(), row.id());
                log.error("RAG ingestion message failed: taskId={}, outboxId={}", row.taskId(), row.id(), error);
            });
        }
    }

    @Override
    public void process(RagIngestionMessage message) {
        int claimed = jdbc.update("""
                UPDATE rag_ingestion_task SET status = 'RUNNING', error_message = NULL,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status NOT IN ('RUNNING', 'SUCCESS')
                """, message.taskId());
        if (claimed != 1) {
            log.info("RAG ingestion task is already handled: taskId={}", message.taskId());
            return;
        }
        RagIngestionResult outcome;
        try {
            byte[] bytes = Files.readAllBytes(Path.of(message.path()));
            outcome = ingestionService.process(message.fileName(), message.contentType(), bytes,
                    stage -> updateStatus(message.taskId(), stage, null, 0, 0));
            if ("SUCCESS".equals(outcome.status())) {
                updateStatus(message.taskId(), outcome.status(), outcome.error(), outcome.chunkCount(), outcome.writtenCount());
                Files.deleteIfExists(Path.of(message.path()));
            } else {
                retryOrFail(message.taskId(), outcome.error());
            }
        } catch (Exception e) {
            retryOrFail(message.taskId(), e.getMessage());
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

    private void enqueue(long taskId, String payload) {
        jdbc.update("""
                INSERT INTO rag_ingestion_outbox
                    (task_id, topic, message_key, payload, status, attempts, next_attempt_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'PENDING', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, taskId, topic, Long.toString(taskId), payload);
    }

    private RagIngestionTaskResponse response(long id, String name) {
        return new RagIngestionTaskResponse(id, name, "PENDING", 0, 0, null, Instant.now(), Instant.now());
    }

    private void fail(long id, String error) { updateStatus(id, "FAILED", error, 0, 0); }

    private void updateTaskStatus(long id, String status, String error) {
        jdbc.update("UPDATE rag_ingestion_task SET status=?,error_message=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",
                status, error, id);
    }

    private void retryOrFail(long taskId, String error) {
        Integer attempts = jdbc.query("SELECT attempts FROM rag_ingestion_outbox WHERE task_id = ?",
                rs -> rs.next() ? rs.getInt(1) : null, taskId);
        if (attempts == null || attempts >= MAX_ATTEMPTS) {
            updateTaskStatus(taskId, "FAILED", error);
            jdbc.update("UPDATE rag_ingestion_outbox SET status = 'FAILED', last_error = ?, updated_at = CURRENT_TIMESTAMP WHERE task_id = ?",
                    error, taskId);
            return;
        }
        Instant nextAttempt = Instant.now().plusSeconds(backoffSeconds(attempts));
        jdbc.update("""
                UPDATE rag_ingestion_outbox
                SET status = 'PENDING', next_attempt_at = ?, last_error = ?, updated_at = CURRENT_TIMESTAMP
                WHERE task_id = ? AND status = 'SENT'
                """, nextAttempt, error, taskId);
        updateTaskStatus(taskId, "PENDING", error);
    }

    private void recoverStaleTasks() {
        List<Long> taskIds = jdbc.query("""
                SELECT id FROM rag_ingestion_task
                WHERE status = 'RUNNING'
                  AND updated_at < DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 30 MINUTE)
                """, (rs, rowNum) -> rs.getLong(1));
        taskIds.forEach(taskId -> retryOrFail(taskId, "处理超时，已自动重试"));
    }

    private long backoffSeconds(int attempts) {
        return Math.min(Duration.ofMinutes(5).toSeconds(), 1L << Math.min(attempts, 8));
    }

    private void updateStatus(long id, String status, String error, int chunks, int written) {
        jdbc.update("UPDATE rag_ingestion_task SET status=?,error_message=?,chunk_count=?,written_count=?,updated_at=CURRENT_TIMESTAMP WHERE id=?", status, error, chunks, written, id);
    }

    private record OutboxRow(long id, long taskId, String topic, String messageKey, String payload, int attempts) { }

}
