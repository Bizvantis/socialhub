package com.socialhub.common.event;

import tools.jackson.databind.ObjectMapper;

/** Serializes and parses {@link EventEnvelope}s with the service's configured Jackson mapper. */
public class EventJson {

    private final ObjectMapper mapper;

    public EventJson(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String write(EventEnvelope event) {
        return mapper.writeValueAsString(event);
    }

    public EventEnvelope read(String json) {
        return mapper.readValue(json, EventEnvelope.class);
    }
}
