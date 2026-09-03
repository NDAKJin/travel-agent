package com.travelagent.travelagent.domain.rag.ingestion;

import com.alibaba.fastjson2.JSON;
import com.travelagent.travelagent.domain.rag.model.RagIngestionMessage;
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
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
public class RagIngestionTaskService {
    private final RagIngestionService ingestionService;
    private final JdbcTemplate jdbc;
    private final String topic;
    private final Path root;

    public RagIngestionTaskService(RagIngestionService ingestionService, JdbcTemplate jdbc,
                                   @Value("${travel-agent.rag.ingestion-topic:rag-ingestion}") String topic,
                                   @Value("${travel-agent.rag.ingestion-storage-dir:${java.io.tmpdir}/travel-agent-rag}") String storageDir) {
        this.ingestionService = ingestionService;
        this.jdbc = jdbc;
        this.topic = topic;
        this.root = Paths.get(storageDir);
    }

    @Transactional
    public List<RagIngestionTaskResponse> submit(List<MultipartFile> files) {
        log.info("RAG ingestion upload received: fileCount={}", files.size());
        List<RagIngestionTaskResponse> result = new ArrayList<>();
        for (MultipartFile file : files) {
            String name = file.getOriginalFilename() == null ? "unknown" : Path.of(file.getOriginalFilename()).getFileName().toString();
            Long taskId = null;
            try {
                if (file.isEmpty()) throw new IllegalArgumentException("文件为空");
                taskId = create(name);
                Path dir = root.resolve(Long.toString(taskId));
                log.info("Preparing RAG ingestion storage: taskId={}, root={}, dir={}", taskId, root, dir);
                Files.createDirectories(dir);
                Path path = dir.resolve(name);
                Files.write(path, file.getBytes());
                log.info("RAG ingestion file stored: taskId={}, path={}, bytes={}", taskId, path, file.getSize());
                String payload = JSON.toJSONString(new RagIngestionMessage(taskId, path.toString(), file.getContentType(), name));
                enqueue(taskId, payload);
                log.info("RAG ingestion task queued: taskId={}, fileName={}, bytes={}, contentType={}",
                        taskId, name, file.getSize(), file.getContentType());
                result.add(response(taskId, name));
            } catch (Exception e) {
                if (taskId != null) fail(taskId, e.getMessage());
                log.error("RAG ingestion task submission failed: taskId={}, fileName={}, errorType={}, message={}",
                        taskId, name, e.getClass().getName(), e.getMessage(), e);
                result.add(new RagIngestionTaskResponse(null, name, "FAILED", 0, 0, e.getMessage(), Instant.now(), Instant.now()));
            }
        }
        return result;
    }

    public void process(RagIngestionMessage message) {
        log.info("RAG ingestion task processing started: taskId={}, fileName={}, path={}",
                message.taskId(), message.fileName(), message.path());
        int claimed = jdbc.update("""
                UPDATE rag_ingestion_task SET status = 'RUNNING', error_message = NULL,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status NOT IN ('RUNNING', 'SUCCESS', 'CANCELLED')
                """, message.taskId());
        if (claimed != 1) {
            log.info("RAG ingestion task skipped because it is already handled: taskId={}", message.taskId());
            return;
        }
        RagIngestionResult outcome;
        try {
            byte[] bytes = Files.readAllBytes(Path.of(message.path()));
            outcome = ingestionService.process(message.fileName(), message.contentType(), bytes,
                    (stage, context) -> {
                        if (isCancelled(message.taskId())) {
                            throw new IllegalStateException("任务已取消");
                        }
                        log.info("RAG ingestion task stage updated: taskId={}, fileName={}, stage={}",
                                message.taskId(), message.fileName(), stage);
                        updateStatus(message.taskId(), stage, null, context.chunks().size(), context.writtenCount());
                    }, () -> isCancelled(message.taskId()));
            if ("SUCCESS".equals(outcome.status())) {
                updateStatus(message.taskId(), outcome.status(), outcome.error(), outcome.chunkCount(), outcome.writtenCount());
                Files.deleteIfExists(Path.of(message.path()));
                log.info("RAG ingestion task processing succeeded: taskId={}, fileName={}, chunks={}, written={}",
                        message.taskId(), message.fileName(), outcome.chunkCount(), outcome.writtenCount());
            } else {
                log.error("RAG ingestion task processing failed: taskId={}, fileName={}, error={}",
                        message.taskId(), message.fileName(), outcome.error());
                retryOrFail(message.taskId(), outcome.error());
            }
        } catch (Exception e) {
            log.error("RAG ingestion task processing crashed: taskId={}, fileName={}, errorType={}, message={}",
                    message.taskId(), message.fileName(), e.getClass().getName(), e.getMessage(), e);
            retryOrFail(message.taskId(), e.getMessage());
        }
    }

    public List<RagIngestionTaskResponse> list() {
        return jdbc.query("SELECT id,file_name,status,chunk_count,written_count,error_message,created_at,updated_at FROM rag_ingestion_task ORDER BY id DESC",
                (rs, n) -> new RagIngestionTaskResponse(rs.getLong("id"), rs.getString("file_name"), rs.getString("status"),
                        rs.getInt("chunk_count"), rs.getInt("written_count"), rs.getString("error_message"),
                        rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()));
    }

    public void cancel(long taskId) {
        int updated = jdbc.update("""
                UPDATE rag_ingestion_task
                SET status = 'CANCELLED', error_message = '任务已取消', updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status IN ('PENDING', 'DISPATCHED', 'RUNNING')
                """, taskId);
        /* Legacy mutable-outbox update intentionally disabled:
                UPDATE rag_ingestion_outbox
                SET status = 'CANCELLED', last_error = '任务已取消', updated_at = CURRENT_TIMESTAMP
                WHERE task_id = ? AND status IN ('PENDING', 'SENDING', 'SENT')
                """, taskId); */
        log.info("RAG ingestion task cancellation requested: taskId={}, updated={}", taskId, updated);
    }

    public void markFailed(long taskId, String error) {
        updateStatus(taskId, "FAILED", error, 0, 0);
    }

    /** Re-emits an ingestion outbox INSERT so a failed task can be replayed. */
    @Transactional
    public void retry(long taskId) {
        List<OutboxPayload> rows = jdbc.query("""
                SELECT o.topic, o.message_key, o.payload
                FROM rag_ingestion_outbox o JOIN rag_ingestion_task t ON t.id = o.task_id
                WHERE o.task_id = ? AND t.status NOT IN ('CANCELLED', 'RUNNING')
                ORDER BY o.id DESC
                LIMIT 1
                """, (rs, n) -> new OutboxPayload(rs.getString(1), rs.getString(2), rs.getString(3)), taskId);
        if (rows.isEmpty()) throw new IllegalArgumentException("RAG ingestion task cannot be retried: " + taskId);
        OutboxPayload row = rows.get(0);
        jdbc.update("UPDATE rag_ingestion_task SET status='PENDING', error_message=NULL, updated_at=CURRENT_TIMESTAMP WHERE id=?", taskId);
        // Replay is a new immutable event; keep the previous event for audit.
        jdbc.update("""
                INSERT INTO rag_ingestion_outbox
                    (task_id, topic, message_key, payload, created_at, updated_at)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, taskId, row.topic(), row.messageKey(), row.payload());
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
                    (task_id, topic, message_key, payload, created_at, updated_at)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
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
        if (isCancelled(taskId)) return;
        // Kafka's DefaultErrorHandler owns retry and backoff. Keep the task
        // claimable for the next redelivery, but do not create another
        // outbox row; Kafka redelivery owns the retry schedule.
        log.warn("RAG ingestion task failed and will be retried by Kafka: taskId={}, error={}", taskId, error);
        updateTaskStatus(taskId, "PENDING", error);
        throw new IllegalStateException(error == null ? "RAG ingestion failed" : error);
    }

    private boolean isCancelled(long taskId) {
        return Boolean.TRUE.equals(jdbc.query("SELECT status = 'CANCELLED' FROM rag_ingestion_task WHERE id = ?",
                rs -> rs.next() && rs.getBoolean(1), taskId));
    }

    @Scheduled(fixedDelayString = "${travel-agent.rag.ingestion-stale-scan-ms:60000}")
    public void recoverStaleTasks() {
        List<Long> taskIds = jdbc.query("""
                SELECT id FROM rag_ingestion_task
                WHERE status = 'RUNNING'
                  AND updated_at < DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 30 MINUTE)
                """, (rs, rowNum) -> rs.getLong(1));
        for (Long taskId : taskIds) {
            int updated = jdbc.update("""
                    UPDATE rag_ingestion_task
                    SET status = 'FAILED', error_message = 'PROCESSING_TIMEOUT', updated_at = CURRENT_TIMESTAMP
                    WHERE id = ? AND status = 'RUNNING'
                    """, taskId);
            if (updated == 1) {
                log.error("RAG ingestion task marked failed after stale RUNNING timeout: taskId={}", taskId);
            }
        }
    }

    private void updateStatus(long id, String status, String error, int chunks, int written) {
        jdbc.update("UPDATE rag_ingestion_task SET status=?,error_message=?,chunk_count=?,written_count=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND status <> 'CANCELLED'", status, error, chunks, written, id);
    }

    private record OutboxPayload(String topic, String messageKey, String payload) { }

}
