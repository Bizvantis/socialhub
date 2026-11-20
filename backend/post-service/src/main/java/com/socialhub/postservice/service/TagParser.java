package com.socialhub.postservice.service;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts #hashtags and @mentions from captions (Unicode letters allowed in hashtags). */
public final class TagParser {

    // \p{M} (combining marks) matters for Indic scripts: the vowel signs in "#భారత్" are marks, not letters.
    private static final Pattern HASHTAG = Pattern.compile("(?<![\\p{L}\\p{M}\\p{N}_])#([\\p{L}\\p{M}\\p{N}_]{1,50})");
    private static final Pattern MENTION = Pattern.compile("(?<![A-Za-z0-9_.])@([A-Za-z0-9_]{3,30})");

    public static final int MAX_HASHTAGS = 30;  // Instagram's limit
    public static final int MAX_MENTIONS = 20;

    private TagParser() {}

    public static Set<String> hashtags(String text) {
        return extract(HASHTAG, text, MAX_HASHTAGS);
    }

    public static Set<String> mentions(String text) {
        return extract(MENTION, text, MAX_MENTIONS);
    }

    private static Set<String> extract(Pattern p, String text, int max) {
        Set<String> out = new LinkedHashSet<>();
        if (text == null) return out;
        Matcher m = p.matcher(text);
        while (m.find() && out.size() < max) out.add(m.group(1).toLowerCase(Locale.ROOT));
        return out;
    }
}
