package com.travelagent.travelagent.infrastructure.messaging.rag;

import com.alibaba.otter.canal.client.CanalConnector;
import com.alibaba.otter.canal.client.CanalConnectors;
import com.alibaba.otter.canal.protocol.CanalEntry;
import com.alibaba.otter.canal.protocol.Message;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Relays append-only file-ingestion outbox INSERTs from Canal to Kafka. */
@Slf4j
@Component
@ConditionalOnProperty(name = "travel-agent.rag.canal.enabled", havingValue = "true")
public class RagIngestionCanalKafkaRelay {
    private final KafkaTemplate<String, String> kafka;
    private final String host;
    private final int port;
    private final String destination;
    private final String filter;
    private final CanalConnector connector;
    private final Object monitor = new Object();
    private boolean connected;

    public RagIngestionCanalKafkaRelay(KafkaTemplate<String, String> kafka,
            @Value("${travel-agent.rag.canal.host:localhost}") String host,
            @Value("${travel-agent.rag.canal.port:11111}") int port,
            @Value("${travel-agent.rag.canal.destination:example}") String destination,
            @Value("${travel-agent.rag.canal.username:}") String username,
            @Value("${travel-agent.rag.canal.password:}") String password,
            @Value("${travel-agent.rag.canal.ingestion-filter:travelagent.rag_ingestion_outbox}") String filter) {
        this.kafka = kafka;
        this.host = host;
        this.port = port;
        this.destination = destination;
        this.filter = filter;
        this.connector = CanalConnectors.newSingleConnector(
                new InetSocketAddress(host, port), destination, username, password);
    }

    @Scheduled(fixedDelayString = "${travel-agent.rag.canal.poll-interval-ms:1000}")
    public void relay() {
        synchronized (monitor) {
            try {
                if (!connected) {
                    connector.connect();
                    connector.subscribe(filter);
                    connected = true;
                }
                Message message = connector.get(100);
                if (message == null || message.getEntries() == null || message.getEntries().isEmpty()) return;
                for (CanalEntry.Entry entry : message.getEntries()) relayEntry(entry);
                connector.ack(message.getId());
            } catch (Exception ex) {
                log.warn("Canal ingestion outbox relay failed ({}:{} / {}): {}", host, port, destination, ex.getMessage());
                try { connector.rollback(); } catch (Exception rollbackError) { log.debug("Canal rollback failed", rollbackError); }
                try { connector.disconnect(); } catch (Exception ignored) { }
                connected = false;
            }
        }
    }

    private void relayEntry(CanalEntry.Entry entry) throws Exception {
        if (entry.getEntryType() != CanalEntry.EntryType.ROWDATA) return;
        CanalEntry.RowChange change = CanalEntry.RowChange.parseFrom(entry.getStoreValue());
        if (change.getEventType() != CanalEntry.EventType.INSERT) return;
        for (CanalEntry.RowData row : change.getRowDatasList()) {
            Map<String, String> values = new HashMap<>();
            for (CanalEntry.Column column : row.getAfterColumnsList()) values.put(column.getName(), column.getValue());
            String payload = values.get("payload");
            String topic = values.get("topic");
            String key = values.get("message_key");
            if (payload == null || topic == null || key == null) continue;
            kafka.send(topic, key, payload).get(30, TimeUnit.SECONDS);
        }
    }

    @PreDestroy
    public void close() {
        synchronized (monitor) {
            try { connector.disconnect(); } catch (Exception ignored) { }
            connected = false;
        }
    }
}
