package com.socialhub.mediaservice.processing;

import com.socialhub.common.queue.TaskQueues;
import org.springframework.amqp.core.Declarables;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Declares the RabbitMQ topology media-service produces to and consumes from (idempotent). */
@Configuration
public class ProcessingConfig {

    @Bean
    Declarables transcodeQueues(@Value("${app.processing.retry-delay-ms:15000}") long retryDelayMs) {
        return TaskQueues.withRetry(TaskQueues.MEDIA_TASKS, TaskQueues.MEDIA_DLX, TaskQueues.TRANSCODE_QUEUE,
                TaskQueues.TRANSCODE_KEY, retryDelayMs);
    }

    @Bean
    Declarables resultQueues() {
        return TaskQueues.simple(TaskQueues.MEDIA_RESULTS_EXCHANGE, TaskQueues.MEDIA_RESULTS_QUEUE,
                TaskQueues.MEDIA_RESULTS_KEY);
    }
}
