package com.socialhub.feedservice.service;

import com.socialhub.common.web.CursorPage;
import com.socialhub.common.web.Pages;
import com.socialhub.feedservice.FeedProperties;
import com.socialhub.feedservice.store.FeedStore;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Service;

import java.util.*;

/** Assembles feeds: own fanned-out feed + pull from celebrities, then hydrate snapshots. */
@Service
public class FeedReader {

    private static final int HOME_CANDIDATES = 300;

    private final FeedStore store;
    private final FeedProperties props;

    public FeedReader(FeedStore store, FeedProperties props) {
        this.store = store;
        this.props = props;
    }

    /** Chronological feed of people I follow and channels I subscribe to; cursor = score (ms). */
    public CursorPage<Map<String, Object>> following(long userId, String cursor, int size) {
        int s = Pages.clampSize(size);
        double before = cursor == null ? Double.POSITIVE_INFINITY : Double.parseDouble(cursor) - 1;
        List<TypedTuple<String>> merged = candidates(userId, before, s);
        List<TypedTuple<String>> page = merged.subList(0, Math.min(s, merged.size()));
        List<Map<String, Object>> items = hydrate(page);
        String next = page.size() == s ? String.valueOf(page.get(page.size() - 1).getScore().longValue()) : null;
        return CursorPage.of(items, next);
    }

    /**
     * Ranked home feed: newest candidates scored by engagement, affinity and age. Offset cursor,
     * because the ranking is recomputed per request and not stable enough for a keyset.
     * New users who follow nobody get the newest public items (cold start).
     */
    public CursorPage<Map<String, Object>> home(long userId, String cursor, int size) {
        int s = Pages.clampSize(size);
        int offset = cursor == null ? 0 : Integer.parseInt(cursor);
        List<TypedTuple<String>> cands = candidates(userId, Double.POSITIVE_INFINITY, HOME_CANDIDATES);
        if (cands.size() < s) {
            Set<String> have = new HashSet<>();
            cands.forEach(t -> have.add(t.getValue()));
            for (TypedTuple<String> t : store.newest("tl:global", Double.POSITIVE_INFINITY, HOME_CANDIDATES)) {
                if (have.add(t.getValue())) cands.add(t);
            }
        }
        Map<String, Map<String, Object>> snaps = store.items(cands.stream().map(TypedTuple::getValue).toList());
        Map<String, Map<Object, Object>> stats = store.stats(snaps.keySet());
        Map<String, Double> aff = store.affinity(userId);
        long now = System.currentTimeMillis();

        List<Map.Entry<String, Double>> ranked = new ArrayList<>();
        for (TypedTuple<String> t : cands) {
            Map<String, Object> snap = snaps.get(t.getValue());
            if (snap == null) continue;
            Map<Object, Object> st = stats.getOrDefault(t.getValue(), Map.of());
            double score = Ranker.score(num(st.get("likes")), num(st.get("comments")), num(st.get("shares")),
                    aff.getOrDefault(String.valueOf(snap.get("authorId")), 0.0),
                    now - t.getScore().longValue());
            ranked.add(Map.entry(t.getValue(), score));
        }
        ranked.sort(Map.Entry.<String, Double>comparingByValue().reversed());
        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = offset; i < Math.min(ranked.size(), offset + s); i++) {
            Map<String, Object> item = new LinkedHashMap<>(snaps.get(ranked.get(i).getKey()));
            item.put("rank", Math.round(ranked.get(i).getValue() * 1000) / 1000.0);
            items.add(item);
        }
        String next = offset + s < ranked.size() ? String.valueOf(offset + s) : null;
        return CursorPage.of(items, next);
    }

    /** "Subscriptions" tab: videos from channels I subscribe to, newest first (pull model). */
    public CursorPage<Map<String, Object>> subscriptions(long userId, String cursor, int size) {
        int s = Pages.clampSize(size);
        double before = cursor == null ? Double.POSITIVE_INFINITY : Double.parseDouble(cursor) - 1;
        List<TypedTuple<String>> all = new ArrayList<>();
        for (String ch : store.channelsOf(userId)) all.addAll(store.newest("tl:c:" + ch, before, s));
        all.sort(Comparator.comparingDouble((TypedTuple<String> t) -> t.getScore()).reversed());
        List<TypedTuple<String>> unique = dedupe(all);
        List<TypedTuple<String>> page = unique.subList(0, Math.min(s, unique.size()));
        String next = page.size() == s ? String.valueOf(page.get(page.size() - 1).getScore().longValue()) : null;
        return CursorPage.of(hydrate(page), next);
    }

    /** Fanned-out feed + celebrity timelines (fan-out on read), newest first, deduplicated. */
    List<TypedTuple<String>> candidates(long userId, double before, int limit) {
        List<TypedTuple<String>> all = new ArrayList<>(store.newest("feed:" + userId, before, limit));
        for (String f : store.following(userId)) {
            if (store.followerCount(Long.parseLong(f)) > props.celebrityThreshold()) {
                all.addAll(store.newest("tl:u:" + f, before, limit));
            }
        }
        for (String ch : store.channelsOf(userId)) {
            if (store.subscriberCount(Long.parseLong(ch)) > props.celebrityThreshold()) {
                all.addAll(store.newest("tl:c:" + ch, before, limit));
            }
        }
        all.sort(Comparator.comparingDouble((TypedTuple<String> t) -> t.getScore()).reversed());
        List<TypedTuple<String>> d = dedupe(all);
        return new ArrayList<>(d.subList(0, Math.min(limit, d.size())));
    }

    private List<Map<String, Object>> hydrate(List<TypedTuple<String>> page) {
        Map<String, Map<String, Object>> snaps = store.items(page.stream().map(TypedTuple::getValue).toList());
        return page.stream().map(t -> snaps.get(t.getValue())).filter(Objects::nonNull).toList();
    }

    private static List<TypedTuple<String>> dedupe(List<TypedTuple<String>> sorted) {
        Set<String> seen = new HashSet<>();
        List<TypedTuple<String>> out = new ArrayList<>();
        for (TypedTuple<String> t : sorted) if (seen.add(t.getValue())) out.add(t);
        return out;
    }

    private static long num(Object o) {
        if (o == null) return 0;
        try {
            return Long.parseLong(o.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
