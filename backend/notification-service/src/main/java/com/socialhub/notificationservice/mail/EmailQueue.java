package com.socialhub.notificationservice.mail;

import com.socialhub.common.queue.TaskQueues;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Enqueues an email as a RabbitMQ task. Sending over SMTP is slow and can fail; doing it in a
 * queue keeps the Kafka consumer fast and gives retries with back-off plus a DLQ for free.
 */
@Component
public class EmailQueue {

    private final RabbitTemplate rabbit;
    private final ObjectMapper mapper;

    public EmailQueue(RabbitTemplate rabbit, ObjectMapper mapper) {
        this.rabbit = rabbit;
        this.mapper = mapper;
    }

    public void enqueue(String to, String subject, String text) {
        byte[] body = mapper.writeValueAsString(Map.of("to", to, "subject", subject, "text", text))
                .getBytes(StandardCharsets.UTF_8);
        rabbit.invoke(ops -> {
            ops.send(TaskQueues.NOTIFY_TASKS, TaskQueues.EMAIL_KEY, MessageBuilder.withBody(body)
                    .setContentType("application/json").setDeliveryMode(MessageDeliveryMode.PERSISTENT).build());
            ops.waitForConfirmsOrDie(10_000);
            return null;
        });
    }

    @Configuration
    static class Topology {
        @Bean
        Declarables emailQueues(@Value("${app.mail.retry-delay-ms:30000}") long delay) {
            return TaskQueues.withRetry(TaskQueues.NOTIFY_TASKS, TaskQueues.NOTIFY_DLX, TaskQueues.EMAIL_QUEUE,
                    TaskQueues.EMAIL_KEY, delay);
        }
    }
}
