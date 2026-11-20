package com.socialhub.common.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Wire format of every domain event on Kafka (schema documented in docs/events/README.md).
 *
 * @param eventId     unique id; consumers use it for idempotency (at-least-once delivery)
 * @param type        event type, e.g. VIDEO_PUBLISHED
 * @param version     payload schema version; consumers ignore versions they do not know
 * @param occurredAt  when the change was committed in the producing service
 * @param aggregateId id of the entity the event is about; also the Kafka message key,
 *                    so all events of one entity stay ordered in one partition
 * @param payload     event-specific fields
 */
public record EventEnvelope(String eventId, String type, int version, Instant occurredAt,
                            String aggregateId, Map<String, Object> payload) {

    public static EventEnvelope of(String type, Object aggregateId, Map<String, Object> payload) {
        return new EventEnvelope(UUID.randomUUID().toString(), type, 1, Instant.now(),
                String.valueOf(aggregateId), payload);
    }

    public Long longField(String name) {
        Object v = payload.get(name);
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        return Long.parseLong(v.toString());
    }

    public String stringField(String name) {
        Object v = payload.get(name);
        return v == null ? null : v.toString();
    }

    public boolean boolField(String name) {
        Object v = payload.get(name);
        return v instanceof Boolean b ? b : v != null && Boolean.parseBoolean(v.toString());
    }
}
