package com.socialhub.notificationservice.mail;

import com.socialhub.common.queue.TaskQueues;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/** Consumes notify.email tasks and sends them over SMTP (retry 3x with delay, then park in DLQ). */
@Component
public class Mailer {

    private static final Logger log = LoggerFactory.getLogger(Mailer.class);
    private static final int MAX_ATTEMPTS = 3;

    private final JavaMailSender mail;
    private final RabbitTemplate rabbit;
    private final ObjectMapper mapper;
    private final String from;

    public Mailer(JavaMailSender mail, RabbitTemplate rabbit, ObjectMapper mapper, @Value("${app.mail.from}") String from) {
        this.mail = mail;
        this.rabbit = rabbit;
        this.mapper = mapper;
        this.from = from;
    }

    @RabbitListener(queues = TaskQueues.EMAIL_QUEUE)
    public void onEmail(Message message) {
        Map<String, String> m = mapper.readValue(message.getBody(), new TypeReference<Map<String, String>>() {});
        try {
            SimpleMailMessage email = new SimpleMailMessage();
            email.setFrom(from);
            email.setTo(m.get("to"));
            email.setSubject(m.get("subject"));
            email.setText(m.get("text"));
            mail.send(email);
            log.info("Email sent to {}: {}", mask(m.get("to")), m.get("subject"));
        } catch (Exception e) {
            long attempt = TaskQueues.previousAttempts(message, TaskQueues.EMAIL_QUEUE) + 1;
            if (attempt >= MAX_ATTEMPTS) {
                log.error("Email to {} failed permanently: {}", mask(m.get("to")), e.getMessage());
                rabbit.send(TaskQueues.NOTIFY_DLX, TaskQueues.EMAIL_KEY + ".dlq", message);
                return;
            }
            throw new AmqpRejectAndDontRequeueException("retry later", e);
        }
    }

    /** Never log full email addresses (personal data). */
    static String mask(String email) {
        if (email == null || !email.contains("@")) return "***";
        int at = email.indexOf('@');
        return email.charAt(0) + "***" + email.substring(at);
    }
}
