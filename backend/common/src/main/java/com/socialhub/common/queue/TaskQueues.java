package com.socialhub.common.queue;

import org.springframework.amqp.core.*;
import org.springframework.amqp.core.Message;

import java.util.List;
import java.util.Map;

/**
 * RabbitMQ topology for background TASKS (Kafka carries facts/events; RabbitMQ carries work).
 *
 * Every task queue gets the same retry shape, built only from standard RabbitMQ features:
 * <pre>
 *   [exchange] --rk--> [queue]  --nack (no requeue)--> [dlx] --rk.retry--> [queue.retry]
 *                         ^                                                   | TTL expires
 *                         +---------------------------------------------------+
 *   after the last attempt the consumer routes it to:   [dlx] --rk.dlq--> [queue.dlq]  (parking lot)
 * </pre>
 * The delay between attempts is the retry queue's message TTL; the attempt number is read from
 * the x-death header RabbitMQ adds on every dead-lettering.
 */
public final class TaskQueues {

    private TaskQueues() {}

    // video processing (media-service -> video-worker)
    public static final String MEDIA_TASKS = "media.tasks";
    public static final String MEDIA_DLX = "media.dlx";
    public static final String TRANSCODE_QUEUE = "media.transcode";
    public static final String TRANSCODE_KEY = "transcode";

    // processing results (video-worker -> media-service)
    public static final String MEDIA_RESULTS_EXCHANGE = "media.results";
    public static final String MEDIA_RESULTS_QUEUE = "media.results";
    public static final String MEDIA_RESULTS_KEY = "result";

    // email delivery (notification/auth -> notification-service mailer)
    public static final String NOTIFY_TASKS = "notify.tasks";
    public static final String NOTIFY_DLX = "notify.dlx";
    public static final String EMAIL_QUEUE = "notify.email";
    public static final String EMAIL_KEY = "email";

    /** Main queue + delayed retry queue + parking (DLQ) queue for one task type. */
    public static Declarables withRetry(String exchange, String dlx, String queue, String key, long retryDelayMs) {
        DirectExchange main = ExchangeBuilder.directExchange(exchange).durable(true).build();
        DirectExchange dead = ExchangeBuilder.directExchange(dlx).durable(true).build();
        Queue work = QueueBuilder.durable(queue)
                .withArgument("x-dead-letter-exchange", dlx)
                .withArgument("x-dead-letter-routing-key", key + ".retry")
                .build();
        Queue retry = QueueBuilder.durable(queue + ".retry")
                .withArgument("x-message-ttl", retryDelayMs)
                .withArgument("x-dead-letter-exchange", exchange)
                .withArgument("x-dead-letter-routing-key", key)
                .build();
        Queue parking = QueueBuilder.durable(queue + ".dlq").build();
        return new Declarables(main, dead, work, retry, parking,
                BindingBuilder.bind(work).to(main).with(key),
                BindingBuilder.bind(retry).to(dead).with(key + ".retry"),
                BindingBuilder.bind(parking).to(dead).with(key + ".dlq"));
    }

    /** Simple durable queue bound to a direct exchange (no retry needed, e.g. results). */
    public static Declarables simple(String exchange, String queue, String key) {
        DirectExchange ex = ExchangeBuilder.directExchange(exchange).durable(true).build();
        Queue q = QueueBuilder.durable(queue).build();
        return new Declarables(ex, q, BindingBuilder.bind(q).to(ex).with(key));
    }

    /** How many times RabbitMQ has already dead-lettered this message from {@code queue}. */
    @SuppressWarnings("unchecked")
    public static long previousAttempts(Message message, String queue) {
        Object xDeath = message.getMessageProperties().getHeaders().get("x-death");
        if (!(xDeath instanceof List<?> deaths)) return 0;
        for (Object d : deaths) {
            if (d instanceof Map<?, ?> m && queue.equals(String.valueOf(m.get("queue")))
                    && "rejected".equals(String.valueOf(m.get("reason")))) {
                Object count = m.get("count");
                return count instanceof Number n ? n.longValue() : 0;
            }
        }
        return 0;
    }
}
