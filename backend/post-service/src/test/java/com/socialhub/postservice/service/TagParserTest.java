package com.socialhub.postservice.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TagParserTest {

    @Test
    void extractsHashtagsCaseInsensitivelyAndDeduplicates() {
        assertEquals(Set.of("sunset", "goa", "travel_2026"),
                TagParser.hashtags("#Sunset in #goa! #SUNSET again #travel_2026"));
    }

    @Test
    void supportsUnicodeHashtags() {
        assertEquals(Set.of("భారత్"), TagParser.hashtags("Hello #భారత్"));
    }

    @Test
    void ignoresHashInsideWordsAndEmails() {
        assertTrue(TagParser.hashtags("issue#42 and C#").isEmpty());
        assertTrue(TagParser.mentions("mail me at alice@example.com").isEmpty());
    }

    @Test
    void extractsMentionsWithUsernameRules() {
        assertEquals(List.of("alice", "bob_99"), List.copyOf(TagParser.mentions("hi @Alice and @bob_99, not @x")));
    }

    @Test
    void capsTheNumberOfHashtags() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 40; i++) sb.append(" #t").append(i);
        assertEquals(TagParser.MAX_HASHTAGS, TagParser.hashtags(sb.toString()).size());
    }

    @Test
    void nullCaptionIsSafe() {
        assertTrue(TagParser.hashtags(null).isEmpty());
        assertTrue(TagParser.mentions(null).isEmpty());
    }
}
