package com.socialhub.searchservice.es;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Index names and creation. Each index = shared analysis settings (_settings.json:
 * edge-ngram "autocomplete" analyzer, ascii-folding) + its own strict mapping, so an
 * unexpected field fails loudly instead of silently changing the schema.
 */
@Component
public class Indices implements InitializingBean {

    public static final String VIDEOS = "sh_videos";
    public static final String POSTS = "sh_posts";
    public static final String USERS = "sh_users";
    public static final String CHANNELS = "sh_channels";

    private static final Logger log = LoggerFactory.getLogger(Indices.class);

    private final EsClient es;

    public Indices(EsClient es) {
        this.es = es;
    }

    /**
     * Runs while beans are created, i.e. BEFORE Kafka listeners start (IndexingConsumer depends on
     * this bean). Otherwise the first event could auto-create an index with a guessed mapping.
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        String settings = read("es/_settings.json");
        create(VIDEOS, settings, read("es/videos.json"));
        create(POSTS, settings, read("es/posts.json"));
        create(USERS, settings, read("es/users.json"));
        create(CHANNELS, settings, read("es/channels.json"));
    }

    private void create(String index, String settings, String mappings) {
        if (es.indexExists(index)) return;
        es.createIndex(index, "{\"settings\":" + settings + ",\"mappings\":" + mappings + "}");
        log.info("Index {} ready", index);
    }

    private static String read(String path) throws Exception {
        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    }
}
