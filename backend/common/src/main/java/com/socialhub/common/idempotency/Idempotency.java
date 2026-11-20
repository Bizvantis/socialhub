package com.socialhub.common.idempotency;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * "Process each event once" for Kafka consumers (Kafka delivers at least once).
 *
 * Call {@link #firstTime} at the start of the consumer's transaction. The marker row in
 * {@code processed_events} commits or rolls back together with the consumer's own
 * changes, so a failed handler is retried and a successful one is never applied twice.
 */
public class Idempotency {

    private final JdbcTemplate jdbc;

    public Idempotency(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean firstTime(String consumer, String eventId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Idempotency.firstTime must run inside a transaction");
        }
        try {
            jdbc.update("INSERT INTO processed_events (consumer, event_id) VALUES (?, ?)", consumer, eventId);
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }
}
