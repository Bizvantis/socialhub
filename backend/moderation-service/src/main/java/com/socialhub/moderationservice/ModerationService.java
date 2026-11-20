package com.socialhub.moderationservice;

import com.socialhub.common.event.Topics;
import com.socialhub.common.outbox.OutboxWriter;
import com.socialhub.common.web.ApiException;
import com.socialhub.common.web.CurrentUser;
import com.socialhub.common.web.CursorPage;
import com.socialhub.common.web.PageSize;
import com.socialhub.moderationservice.TargetLookup.TargetType;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class ModerationService {

    public enum Reason { SPAM, HARASSMENT, HATE, NUDITY, VIOLENCE, MISINFORMATION, COPYRIGHT, OTHER }

    public record Report(Long id, Long reporterId, String targetType, Long targetId, Long targetOwnerId,
                         String reason, String details, String status, Instant createdAt) {}

    /** One row of the admin queue: a target with all its open reports aggregated. */
    public record QueueItem(String targetType, Long targetId, Long targetOwnerId, long openReports,
                            List<String> reasons, Instant firstReportedAt) {}

    public record Action(Long id, Long adminId, String action, String targetType, Long targetId, String reason,
                         Instant createdAt) {}

    private final JdbcTemplate jdbc;
    private final TargetLookup targets;
    private final OutboxWriter outbox;

    public ModerationService(JdbcTemplate jdbc, TargetLookup targets, OutboxWriter outbox) {
        this.jdbc = jdbc;
        this.targets = targets;
        this.outbox = outbox;
    }

    // ---- user side -------------------------------------------------------

    @Transactional
    public Report report(CurrentUser user, TargetType type, long targetId, Reason reason, String details) {
        long owner = targets.ownerOf(type, targetId);
        if (owner == user.id()) throw ApiException.badRequest("You cannot report your own content");
        try {
            jdbc.update("""
                    INSERT INTO reports (reporter_id, target_type, target_id, target_owner_id, reason, details)
                    VALUES (?, ?, ?, ?, ?, ?)""", user.id(), type.name(), targetId, owner, reason.name(), details);
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("You already reported this");
        }
        return mine(user, null, 1).items().get(0);
    }

    @Transactional(readOnly = true)
    public CursorPage<Report> mine(CurrentUser user, Long before, int size) {
        int s = PageSize.clamp(size);
        List<Report> items = jdbc.query("""
                SELECT id, reporter_id, target_type, target_id, target_owner_id, reason, details, status, created_at
                FROM reports WHERE reporter_id = ? AND id < ? ORDER BY id DESC LIMIT ?""",
                (rs, i) -> new Report(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getLong(4), rs.getLong(5),
                        rs.getString(6), rs.getString(7), rs.getString(8), rs.getTimestamp(9).toInstant()),
                user.id(), before == null ? Long.MAX_VALUE : before, s);
        return CursorPage.of(items, items.size() == s ? String.valueOf(items.get(items.size() - 1).id()) : null);
    }

    // ---- admin side ------------------------------------------------------

    /** Most-reported first: targets with many independent reports are reviewed before single ones. */
    @Transactional(readOnly = true)
    public List<QueueItem> queue(CurrentUser admin, String targetType, int limit) {
        admin.requireRole("ADMIN");
        String filter = targetType == null ? "" : " AND target_type = ?";
        Object[] args = targetType == null ? new Object[]{PageSize.clamp(limit)}
                : new Object[]{targetType.toUpperCase(Locale.ROOT), PageSize.clamp(limit)};
        return jdbc.query("""
                SELECT target_type, target_id, MAX(target_owner_id), COUNT(*), GROUP_CONCAT(DISTINCT reason), MIN(created_at)
                FROM reports WHERE status = 'OPEN'
                """ + filter + """

                GROUP BY target_type, target_id ORDER BY COUNT(*) DESC, MIN(created_at) LIMIT ?""",
                (rs, i) -> new QueueItem(rs.getString(1), rs.getLong(2), rs.getLong(3), rs.getLong(4),
                        Arrays.asList(rs.getString(5).split(",")), rs.getTimestamp(6).toInstant()), args);
    }

    @Transactional
    public Map<String, Object> removeContent(CurrentUser admin, TargetType type, long id, String reason) {
        admin.requireRole("ADMIN");
        if (type == TargetType.USER) throw ApiException.badRequest("Use suspend for users");
        long owner = targets.ownerOf(type, id);
        int resolved = resolveReports(admin, type, id, "ACTIONED");
        log(admin, "REMOVE_CONTENT", type, id, reason);
        outbox.publish(Topics.MODERATION_EVENTS, "CONTENT_REMOVED", type + ":" + id, payload(type, id, owner, reason));
        return Map.of("removed", true, "reportsResolved", resolved);
    }

    @Transactional
    public Map<String, Object> restoreContent(CurrentUser admin, TargetType type, long id, String reason) {
        admin.requireRole("ADMIN");
        long owner = targets.ownerOf(type, id);
        log(admin, "RESTORE_CONTENT", type, id, reason);
        outbox.publish(Topics.MODERATION_EVENTS, "CONTENT_RESTORED", type + ":" + id, payload(type, id, owner, reason));
        return Map.of("restored", true);
    }

    @Transactional
    public Map<String, Object> suspend(CurrentUser admin, long userId, String reason, boolean suspend) {
        admin.requireRole("ADMIN");
        if (admin.id() == userId) throw ApiException.badRequest("You cannot suspend yourself");
        targets.ownerOf(TargetType.USER, userId);
        int resolved = suspend ? resolveReports(admin, TargetType.USER, userId, "ACTIONED") : 0;
        log(admin, suspend ? "SUSPEND_USER" : "REINSTATE_USER", TargetType.USER, userId, reason);
        Map<String, Object> p = new HashMap<>();
        p.put("userId", userId);
        p.put("reason", reason);
        p.put("adminId", admin.id());
        outbox.publish(Topics.MODERATION_EVENTS, suspend ? "USER_SUSPENDED" : "USER_REINSTATED", userId, p);
        return Map.of(suspend ? "suspended" : "reinstated", true, "reportsResolved", resolved);
    }

    @Transactional
    public Map<String, Object> dismiss(CurrentUser admin, TargetType type, long id, String reason) {
        admin.requireRole("ADMIN");
        int n = resolveReports(admin, type, id, "DISMISSED");
        log(admin, "DISMISS_REPORTS", type, id, reason);
        return Map.of("dismissed", n);
    }

    @Transactional(readOnly = true)
    public CursorPage<Action> actions(CurrentUser admin, Long before, int size) {
        admin.requireRole("ADMIN");
        int s = PageSize.clamp(size);
        List<Action> items = jdbc.query("""
                SELECT id, admin_id, action, target_type, target_id, reason, created_at FROM moderation_actions
                WHERE id < ? ORDER BY id DESC LIMIT ?""",
                (rs, i) -> new Action(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getLong(5),
                        rs.getString(6), rs.getTimestamp(7).toInstant()),
                before == null ? Long.MAX_VALUE : before, s);
        return CursorPage.of(items, items.size() == s ? String.valueOf(items.get(items.size() - 1).id()) : null);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> stats(CurrentUser admin) {
        admin.requireRole("ADMIN");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("openReports", jdbc.queryForObject("SELECT COUNT(*) FROM reports WHERE status = 'OPEN'", Long.class));
        Map<String, Long> byReason = new LinkedHashMap<>();
        jdbc.query("SELECT reason, COUNT(*) FROM reports WHERE status = 'OPEN' GROUP BY reason ORDER BY 2 DESC",
                rs -> { byReason.put(rs.getString(1), rs.getLong(2)); });
        out.put("openByReason", byReason);
        out.put("actionsLast24h", jdbc.queryForObject(
                "SELECT COUNT(*) FROM moderation_actions WHERE created_at > NOW() - INTERVAL 1 DAY", Long.class));
        return out;
    }

    // ------------------------------------------------------------------

    private int resolveReports(CurrentUser admin, TargetType type, long id, String status) {
        return jdbc.update("""
                UPDATE reports SET status = ?, resolved_by = ?, resolved_at = CURRENT_TIMESTAMP(3)
                WHERE target_type = ? AND target_id = ? AND status = 'OPEN'""", status, admin.id(), type.name(), id);
    }

    private void log(CurrentUser admin, String action, TargetType type, long id, String reason) {
        jdbc.update("INSERT INTO moderation_actions (admin_id, action, target_type, target_id, reason) VALUES (?, ?, ?, ?, ?)",
                admin.id(), action, type.name(), id, reason);
    }

    private static Map<String, Object> payload(TargetType type, long id, long owner, String reason) {
        Map<String, Object> p = new HashMap<>();
        p.put("targetType", type.name());
        p.put("targetId", id);
        p.put("ownerId", owner);
        p.put("reason", reason);
        return p;
    }
}
