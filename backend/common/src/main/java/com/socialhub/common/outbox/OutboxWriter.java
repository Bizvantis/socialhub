package com.socialhub.common.outbox;

import com.socialhub.common.event.EventEnvelope;
import com.socialhub.common.event.EventJson;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Timestamp;
import java.util.Map;

/**
 * Transactional outbox: instead of sending to Kafka directly (which can succeed while the
 * DB transaction rolls back, or fail after it commits), the event is inserted into the
 * service's own {@code outbox_events} table in the SAME transaction as the business change.
 * {@link OutboxRelay} then publishes committed rows to Kafka.
 *
 * Result: an event exists if and only if the change was committed (at-least-once delivery).
 */
public class OutboxWriter {

    private final JdbcTemplate jdbc;
    private final EventJson json;

    public OutboxWriter(JdbcTemplate jdbc, EventJson json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public EventEnvelope publish(String topic, String type, Object aggregateId, Map<String, Object> payload) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("OutboxWriter.publish must run inside a transaction");
        }
        EventEnvelope event = EventEnvelope.of(type, aggregateId, payload);
        jdbc.update("""
                INSERT INTO outbox_events (event_id, topic, aggregate_id, event_type, payload, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                event.eventId(), topic, event.aggregateId(), type, json.write(event),
                Timestamp.from(event.occurredAt()));
        return event;
    }
}
