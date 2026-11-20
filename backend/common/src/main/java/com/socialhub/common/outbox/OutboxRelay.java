package com.socialhub.common.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Polls committed outbox rows and publishes them to Kafka in insertion order.
 *
 * - {@code FOR UPDATE SKIP LOCKED} lets several instances of a service run the relay
 *   without publishing the same row twice concurrently.
 * - Each send is awaited (acks=all, idempotent producer). If Kafka is down the
 *   transaction rolls back and the batch is retried on the next poll, so nothing is lost.
 * - A crash between "sent" and "marked published" re-sends the row; consumers are
 *   idempotent by eventId, so duplicates are harmless.
 */
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int BATCH = 200;

    private final JdbcTemplate jdbc;
    private final KafkaTemplate<String, String> kafka;
    private final TransactionTemplate tx;

    public OutboxRelay(JdbcTemplate jdbc, KafkaTemplate<String, String> kafka, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.kafka = kafka;
        this.tx = tx;
    }

    private record Row(long id, String topic, String key, String payload) {}

    @Scheduled(fixedDelayString = "${app.outbox.poll-ms:300}")
    public void relay() {
        try {
            Integer sent;
            do {
                sent = tx.execute(status -> publishBatch());
            } while (sent != null && sent == BATCH);
        } catch (Exception e) {
            log.warn("Outbox relay failed, will retry: {}", e.getMessage());
        }
    }

    private int publishBatch() {
        List<Row> rows = jdbc.query("""
                SELECT id, topic, aggregate_id, payload FROM outbox_events
                WHERE published_at IS NULL ORDER BY id LIMIT ? FOR UPDATE SKIP LOCKED
                """,
                (rs, i) -> new Row(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4)), BATCH);
        if (rows.isEmpty()) return 0;
        for (Row r : rows) {
            try {
                kafka.send(r.topic(), r.key(), r.payload()).get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new IllegalStateException("Kafka send failed for outbox id " + r.id(), e);
            }
        }
        List<Object[]> ids = rows.stream().map(r -> new Object[]{r.id()}).toList();
        jdbc.batchUpdate("UPDATE outbox_events SET published_at = CURRENT_TIMESTAMP(3) WHERE id = ?", ids);
        return rows.size();
    }

    /** Published rows are only needed for debugging; keep 3 days. */
    @Scheduled(cron = "${app.outbox.cleanup-cron:0 15 * * * *}")
    public void cleanup() {
        int n = jdbc.update("DELETE FROM outbox_events WHERE published_at < NOW() - INTERVAL 3 DAY LIMIT 10000");
        if (n > 0) log.info("Outbox cleanup removed {} rows", n);
    }
}
