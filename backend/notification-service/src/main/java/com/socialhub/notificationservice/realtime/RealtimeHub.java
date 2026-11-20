package com.socialhub.notificationservice.realtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Real-time delivery with Server-Sent Events.
 *
 * A browser keeps one SSE connection to ONE notification-service instance, but the event that
 * creates the notification may be consumed by ANOTHER instance. So every new notification is
 * published to Redis channel notif:{userId}; every instance subscribes to notif:* and pushes to
 * the emitters it holds locally. This works with any number of instances behind the gateway.
 */
@Component
public class RealtimeHub {

    private static final Logger log = LoggerFactory.getLogger(RealtimeHub.class);
    private static final long TIMEOUT_MS = 30 * 60 * 1000L;

    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final StringRedisTemplate redis;

    public RealtimeHub(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public SseEmitter connect(long userId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        emitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        Runnable remove = () -> {
            List<SseEmitter> list = emitters.get(userId);
            if (list != null) list.remove(emitter);
        };
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(e -> remove.run());
        try {
            emitter.send(SseEmitter.event().name("ready").data("{}"));
        } catch (IOException e) {
            remove.run();
        }
        return emitter;
    }

    /** Fan out to every instance (including this one) through Redis pub/sub. */
    public void publish(long userId, String json) {
        redis.convertAndSend("notif:" + userId, json);
    }

    void deliverLocally(long userId, String json) {
        List<SseEmitter> list = emitters.get(userId);
        if (list == null) return;
        for (SseEmitter e : list) {
            try {
                e.send(SseEmitter.event().name("notification").data(json));
            } catch (Exception ex) {
                list.remove(e);
            }
        }
    }

    public int connectedUsers() {
        return (int) emitters.values().stream().filter(l -> !l.isEmpty()).count();
    }

    @Configuration
    static class PubSub {
        @Bean
        RedisMessageListenerContainer notificationListener(RedisConnectionFactory cf, RealtimeHub hub) {
            RedisMessageListenerContainer c = new RedisMessageListenerContainer();
            c.setConnectionFactory(cf);
            c.addMessageListener((message, pattern) -> {
                String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
                try {
                    long userId = Long.parseLong(channel.substring("notif:".length()));
                    hub.deliverLocally(userId, new String(message.getBody(), StandardCharsets.UTF_8));
                } catch (NumberFormatException e) {
                    log.debug("Ignoring message on {}", channel);
                }
            }, new PatternTopic("notif:*"));
            return c;
        }
    }
}
