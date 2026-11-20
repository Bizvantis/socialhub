package com.socialhub.feedservice.service;

/**
 * Home-feed score (pure function, unit-tested).
 *
 *   score = engagement + affinity - age penalty
 *   engagement = ln(1 + likes + 2*comments + 3*shares)   (diminishing returns: 10 likes matter,
 *                                                          the 10,001st barely does)
 *   affinity   = ln(1 + interactions with the author)    (people you engage with rank higher)
 *   age        = hours since posting / 6                 (4 points lost per day: fresh content wins)
 *
 * Deliberately simple and explainable; a learned model would replace this function only.
 */
public final class Ranker {

    private Ranker() {}

    public static double score(long likes, long comments, long shares, double affinity, long ageMillis) {
        double engagement = Math.log1p(Math.max(0, likes) + 2.0 * Math.max(0, comments) + 3.0 * Math.max(0, shares));
        double aff = Math.log1p(Math.max(0, affinity));
        double hours = Math.max(0, ageMillis) / 3_600_000.0;
        return engagement + aff - hours / 6.0;
    }
}
