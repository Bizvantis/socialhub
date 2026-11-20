package com.socialhub.notificationservice.service;

import com.socialhub.notificationservice.mail.EmailQueue;
import com.socialhub.notificationservice.realtime.RealtimeHub;
import com.socialhub.notificationservice.store.NotificationDao;
import com.socialhub.notificationservice.store.NotificationDao.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

import java.util.*;

/**
 * Creates a notification and, once the transaction commits, pushes it in real time and
 * (for selected types) queues an email. Side effects after commit: a rolled-back consumer
 * (to be retried) must not have already pushed or emailed.
 */
@Service
public class Notifier {

    private static final Logger log = LoggerFactory.getLogger(Notifier.class);

    private final NotificationDao dao;
    private final RealtimeHub realtime;
    private final EmailQueue email;
    private final ObjectMapper mapper;
    private final Set<String> emailTypes;
    private final String webBaseUrl;

    public Notifier(NotificationDao dao, RealtimeHub realtime, EmailQueue email, ObjectMapper mapper,
                    @Value("${app.notifications.email-types:}") List<String> emailTypes,
                    @Value("${app.mail.web-base-url:http://localhost:5173}") String webBaseUrl) {
        this.dao = dao;
        this.realtime = realtime;
        this.email = email;
        this.mapper = mapper;
        this.emailTypes = new HashSet<>(emailTypes);
        this.webBaseUrl = webBaseUrl;
    }

    public void notify(long recipient, String type, Long actor, String targetType, Long targetId, Long entityId,
                       String preview, String dedupeKey) {
        if (actor != null && actor == recipient) return; // never notify people about their own actions
        dao.insert(recipient, type, actor, targetType, targetId, entityId, preview, dedupeKey)
                .ifPresent(n -> afterCommit(() -> deliver(n)));
    }

    private void deliver(Notification n) {
        try {
            realtime.publish(n.userId(), mapper.writeValueAsString(n));
        } catch (Exception e) {
            log.warn("Realtime push failed for notification {}: {}", n.id(), e.getMessage());
        }
        if (!emailTypes.contains(n.type())) return;
        dao.contact(n.userId()).filter(NotificationDao.Contact::emailEnabled).ifPresent(c -> {
            try {
                email.enqueue(c.email(), subject(n), body(c.username(), n));
            } catch (Exception e) {
                log.warn("Could not queue email for notification {}: {}", n.id(), e.getMessage());
            }
        });
    }

    private static String subject(Notification n) {
        return switch (n.type()) {
            case "FOLLOW" -> "You have a new follower";
            case "COMMENT" -> "New comment on your " + lower(n.targetType());
            case "REPLY" -> "Someone replied to your comment";
            case "MENTION" -> "You were mentioned in a post";
            case "CONTENT_REMOVED" -> "Your " + lower(n.targetType()) + " was removed";
            default -> "New activity on SocialHub";
        };
    }

    private String body(String username, Notification n) {
        String link = switch (String.valueOf(n.targetType())) {
            case "VIDEO" -> webBaseUrl + "/watch/" + n.targetId();
            case "POST" -> webBaseUrl + "/p/" + n.targetId();
            default -> webBaseUrl + "/notifications";
        };
        return "Hi " + username + ",\n\n" + subject(n) + (n.preview() == null ? "" : ":\n\n  \"" + n.preview() + "\"")
                + "\n\nOpen SocialHub: " + link
                + "\n\nYou can turn these emails off in Settings > Notifications.\n";
    }

    private static String lower(String s) {
        return s == null ? "content" : s.toLowerCase(Locale.ROOT);
    }

    private static void afterCommit(Runnable r) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    r.run();
                }
            });
        } else {
            r.run();
        }
    }
}
