package com.socialhub.common.event;

import com.socialhub.common.idempotency.Idempotency;
import com.socialhub.common.outbox.OutboxRelay;
import com.socialhub.common.outbox.OutboxWriter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.backoff.ExponentialBackOff;
import tools.jackson.databind.ObjectMapper;

/**
 * Wires the eventing plumbing according to what each service has on its classpath:
 * JDBC gives idempotency, JDBC + Kafka gives the outbox, and Kafka gives consumer retries + DLT.
 */
@AutoConfiguration(afterName = {
        "org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration",
        "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration",
        "org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration"})
@ConditionalOnClass(ObjectMapper.class)
public class EventingAutoConfiguration {

    @Bean
    EventJson eventJson(ObjectMapper mapper) {
        return new EventJson(mapper);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(JdbcTemplate.class)
    static class JdbcEventing {
        @Bean
        Idempotency idempotency(JdbcTemplate jdbc) {
            return new Idempotency(jdbc);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass({JdbcTemplate.class, KafkaTemplate.class})
    @ConditionalOnProperty(prefix = "app.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
    @EnableScheduling
    static class Outbox {
        @Bean
        OutboxWriter outboxWriter(JdbcTemplate jdbc, EventJson json) {
            return new OutboxWriter(jdbc, json);
        }

        @Bean
        OutboxRelay outboxRelay(JdbcTemplate jdbc, KafkaTemplate<String, String> kafka,
                                PlatformTransactionManager txm) {
            return new OutboxRelay(jdbc, kafka, new TransactionTemplate(txm));
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(KafkaTemplate.class)
    static class KafkaConsumers {
        /**
         * Consumer error policy: retry with exponential back-off (1s, 2s, 4s), then publish the
         * record to "<topic>-dlt" so one poison message never blocks a partition.
         */
        @Bean
        CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafka) {
            var recoverer = new DeadLetterPublishingRecoverer(kafka,
                    (rec, ex) -> new org.apache.kafka.common.TopicPartition(rec.topic() + "-dlt", -1));
            var backOff = new ExponentialBackOff(1000, 2.0);
            backOff.setMaxAttempts(3);
            return new DefaultErrorHandler(recoverer, backOff);
        }
    }
}
