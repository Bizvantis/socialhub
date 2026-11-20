package com.socialhub.userservice.controller;

import com.socialhub.userservice.dto.ApiResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;

/**
 * Service-to-service API (reached through Eureka; the gateway blocks /internal/**).
 * Read-only lookups other services need to accept a request or fan out an event:
 * mentions (@username -> id), author cards, follower/following pages, follow checks.
 */
@RestController
@RequestMapping("/internal")
public class InternalUserController {

    public record UserSummary(Long userId, String username, String displayName, Long avatarMediaId,
                              boolean privateAccount) {}

    private static final String SUMMARY_SQL =
            "SELECT user_id, username, display_name, avatar_media_id, is_private FROM profiles ";

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;

    public InternalUserController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.named = new NamedParameterJdbcTemplate(jdbc);
    }

    @GetMapping("/users/lookup")
    public ApiResponse<List<UserSummary>> lookup(@RequestParam List<String> usernames) {
        List<String> names = usernames.stream().limit(50).map(u -> u.toLowerCase(Locale.ROOT)).toList();
        if (names.isEmpty()) return ApiResponse.success(List.of());
        return ApiResponse.success(named.query(SUMMARY_SQL + "WHERE LOWER(username) IN (:n)",
                new MapSqlParameterSource("n", names), (rs, i) -> map(rs)));
    }

    @GetMapping("/users/batch")
    public ApiResponse<List<UserSummary>> batch(@RequestParam List<Long> ids) {
        return ApiResponse.success(summaries(ids));
    }

    @GetMapping("/users/{id}/following")
    public ApiResponse<List<Long>> following(@PathVariable Long id, @RequestParam(defaultValue = "0") long after,
                                             @RequestParam(defaultValue = "1000") int limit) {
        return ApiResponse.success(jdbc.queryForList("""
                SELECT followee_id FROM follows WHERE follower_id = ? AND followee_id > ? AND status = 'ACCEPTED'
                ORDER BY followee_id LIMIT ?""", Long.class, id, after, clamp(limit)));
    }

    @GetMapping("/users/{id}/followers")
    public ApiResponse<List<Long>> followers(@PathVariable Long id, @RequestParam(defaultValue = "0") long after,
                                             @RequestParam(defaultValue = "1000") int limit) {
        return ApiResponse.success(jdbc.queryForList("""
                SELECT follower_id FROM follows WHERE followee_id = ? AND follower_id > ? AND status = 'ACCEPTED'
                ORDER BY follower_id LIMIT ?""", Long.class, id, after, clamp(limit)));
    }

    @GetMapping("/follows")
    public ApiResponse<Boolean> follows(@RequestParam Long follower, @RequestParam Long followee) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM follows WHERE follower_id = ? AND followee_id = ? AND status = 'ACCEPTED'",
                Integer.class, follower, followee);
        return ApiResponse.success(n != null && n > 0);
    }

    List<UserSummary> summaries(List<Long> ids) {
        List<Long> capped = ids.stream().distinct().limit(200).toList();
        if (capped.isEmpty()) return List.of();
        return named.query(SUMMARY_SQL + "WHERE user_id IN (:ids)", new MapSqlParameterSource("ids", capped),
                (rs, i) -> map(rs));
    }

    private static UserSummary map(java.sql.ResultSet rs) throws java.sql.SQLException {
        long avatar = rs.getLong(4);
        return new UserSummary(rs.getLong(1), rs.getString(2), rs.getString(3), rs.wasNull() ? null : avatar,
                rs.getBoolean(5));
    }

    private static int clamp(int limit) {
        return Math.min(Math.max(limit, 1), 5000);
    }
}
