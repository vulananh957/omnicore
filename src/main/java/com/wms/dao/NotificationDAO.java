package com.wms.dao;

import com.wms.model.Notification;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * NotificationDAO — Data access for the notifications table.
 *
 * <p>Notifications are scoped by role and optionally by warehouse_id so that
 * warehouse staff only see alerts for their own warehouse. A "role broadcast"
 * (recipient_user_id = 0) is sent to all active users with that role + warehouse
 * context.</p>
 */
public class NotificationDAO extends BaseDAO {

    private static final Logger LOGGER = Logger.getLogger(NotificationDAO.class.getName());

    // ── SQL constants ────────────────────────────────────────────────

    private static final String INSERT =
        "INSERT INTO notifications " +
        "(recipient_user_id, recipient_role, warehouse_id, notification_type, " +
        " title, message, reference_type, reference_id, priority, is_read, created_at) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, NOW())";

    private static final String SELECT_BASE =
        "SELECT n.id, n.recipient_user_id, n.recipient_role, n.warehouse_id, " +
        "       n.notification_type, n.title, n.message, " +
        "       n.reference_type, n.reference_id, n.priority, n.is_read, " +
        "       n.created_at, n.read_at, " +
        "       w.warehouse_name " +
        "FROM notifications n " +
        "LEFT JOIN warehouses w ON n.warehouse_id = w.warehouse_id";

    private static final String MARK_READ =
        "UPDATE notifications SET is_read = 1, read_at = NOW() WHERE id = ?";

    private static final String MARK_ALL_READ =
        "UPDATE notifications SET is_read = 1, read_at = NOW() " +
        "WHERE is_read = 0 AND recipient_user_id = ? AND recipient_role = ? " +
        "AND (warehouse_id = ? OR warehouse_id IS NULL)";

    private static final String DELETE_OLD =
        "DELETE FROM notifications WHERE created_at < DATE_SUB(NOW(), INTERVAL ? DAY)";

    // ── Insert ──────────────────────────────────────────────────────

    /**
     * Inserts a single notification. Returns the generated id or -1 on failure.
     */
    public long insert(Notification n) {
        try (Connection conn = openConnection(LOGGER)) {
            if (conn == null) return -1;
            try (PreparedStatement ps = conn.prepareStatement(INSERT,
                    PreparedStatement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, n.getRecipientUserId());
                ps.setString(2, n.getRecipientRole());
                if (n.getWarehouseId() != null) {
                    ps.setInt(3, n.getWarehouseId());
                } else {
                    ps.setNull(3, java.sql.Types.INTEGER);
                }
                ps.setString(4, n.getNotificationType());
                ps.setString(5, n.getTitle());
                ps.setString(6, n.getMessage());
                ps.setString(7, n.getReferenceType());
                if (n.getReferenceId() != null) {
                    ps.setLong(8, n.getReferenceId());
                } else {
                    ps.setNull(8, java.sql.Types.BIGINT);
                }
                ps.setString(9, n.getPriority() != null ? n.getPriority() : Notification.PRIORITY_NORMAL);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) return rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "insert notification failed", e);
        }
        return -1;
    }

    /**
     * Broadcasts a notification to every active user with the given role.
     * If warehouseId is not null, only users assigned to that warehouse receive it.
     * Returns the number of rows inserted.
     */
    public int broadcastToRole(String recipientRole, Integer warehouseId, Notification tmpl) {
        StringBuilder sql = new StringBuilder(
            "INSERT INTO notifications " +
            "(recipient_user_id, recipient_role, warehouse_id, notification_type, " +
            " title, message, reference_type, reference_id, priority, is_read, created_at) " +
            "SELECT u.user_id, ?, ?, ?, ?, ?, ?, ?, ?, 0, NOW() " +
            "FROM users u " +
            "WHERE u.role = ? AND u.active = 1"
        );
        List<Object> params = new ArrayList<>();
        params.add(recipientRole);
        params.add(warehouseId);
        params.add(tmpl.getNotificationType());
        params.add(tmpl.getTitle());
        params.add(tmpl.getMessage());
        params.add(tmpl.getReferenceType());
        params.add(tmpl.getReferenceId());
        params.add(tmpl.getPriority() != null ? tmpl.getPriority() : Notification.PRIORITY_NORMAL);
        params.add(recipientRole);

        if (warehouseId != null) {
            sql.append(" AND u.warehouse_id = ?");
            params.add(warehouseId);
        }

        try (Connection conn = openConnection(LOGGER)) {
            if (conn == null) return 0;
            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    Object val = params.get(i);
                    if (val == null) {
                        ps.setNull(i + 1, java.sql.Types.NULL);
                    } else {
                        ps.setObject(i + 1, val);
                    }
                }
                return ps.executeUpdate();
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "broadcastToRole failed", e);
        }
        return 0;
    }

    /**
     * Broadcasts a notification to all warehouse staff of the given warehouse.
     */
    public int broadcastToWarehouseStaff(Integer warehouseId, Notification tmpl) {
        return broadcastToRole("WAREHOUSE_STAFF", warehouseId, tmpl);
    }

    /**
     * Broadcasts a notification to all managers.
     */
    public int broadcastToManagers(Notification tmpl) {
        return broadcastToRole("MANAGER", null, tmpl);
    }

    /**
     * Broadcasts a notification to all sales staff.
     */
    public int broadcastToSalesStaff(Notification tmpl) {
        return broadcastToRole("SALES_STAFF", null, tmpl);
    }

    /**
     * Broadcasts a notification to all admins.
     */
    public int broadcastToAdmins(Notification tmpl) {
        return broadcastToRole("ADMIN", null, tmpl);
    }

    // ── Select ─────────────────────────────────────────────────────

    private Notification mapNotification(ResultSet rs) throws SQLException {
        Notification n = new Notification();
        n.setId(rs.getLong("id"));
        n.setRecipientUserId(rs.getInt("recipient_user_id"));
        n.setRecipientRole(rs.getString("recipient_role"));
        int whId = rs.getInt("warehouse_id");
        n.setWarehouseId(rs.wasNull() ? null : whId);
        n.setNotificationType(rs.getString("notification_type"));
        n.setTitle(rs.getString("title"));
        n.setMessage(rs.getString("message"));
        n.setReferenceType(rs.getString("reference_type"));
        long refId = rs.getLong("reference_id");
        n.setReferenceId(rs.wasNull() ? null : refId);
        n.setPriority(rs.getString("priority"));
        n.setRead(rs.getBoolean("is_read"));
        n.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        java.sql.Timestamp readAt = rs.getTimestamp("read_at");
        if (readAt != null) n.setReadAt(readAt.toLocalDateTime());
        String whName = rs.getString("warehouse_name");
        if (whName != null) n.setWarehouseName(whName);
        return n;
    }

    /**
     * Finds notifications for a specific user (including role broadcasts where recipient_user_id = 0).
     *
     * @param userId      the user's id
     * @param role        the user's role string
     * @param warehouseId the user's warehouse (null for MANAGER/SALES_STAFF/ADMIN)
     * @param limit       max rows to return
     */
    public List<Notification> findForUser(int userId, String role, Integer warehouseId, int limit) {
        String sql = SELECT_BASE +
            " WHERE (n.recipient_user_id = ? OR n.recipient_user_id = 0) " +
            "   AND n.recipient_role = ? " +
            "   AND (n.warehouse_id = ? OR n.warehouse_id IS NULL) " +
            "ORDER BY n.is_read ASC, n.created_at DESC " +
            "LIMIT ?";
        return queryList(LOGGER, sql, this::mapNotification,
                userId, role, warehouseId != null ? warehouseId : 0, limit);
    }

    /**
     * Finds the most recent notifications for a user (convenience wrapper).
     */
    public List<Notification> findRecentForUser(int userId, String role, Integer warehouseId) {
        return findForUser(userId, role, warehouseId, 20);
    }

    /**
     * Counts unread notifications for the badge.
     */
    public int countUnread(int userId, String role, Integer warehouseId) {
        String sql =
            "SELECT COUNT(*) FROM notifications n " +
            "WHERE (n.recipient_user_id = ? OR n.recipient_user_id = 0) " +
            "  AND n.recipient_role = ? " +
            "  AND n.is_read = 0 " +
            "  AND (n.warehouse_id = ? OR n.warehouse_id IS NULL)";
        try (Connection conn = openConnection(LOGGER)) {
            if (conn == null) return 0;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, userId);
                ps.setString(2, role);
                if (warehouseId != null) {
                    ps.setInt(3, warehouseId);
                } else {
                    ps.setNull(3, java.sql.Types.INTEGER);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "countUnread failed", e);
        }
        return 0;
    }

    // ── Update ─────────────────────────────────────────────────────

    /**
     * Marks a single notification as read.
     */
    public boolean markAsRead(long notificationId) {
        return update(LOGGER, MARK_READ, notificationId) >= 0;
    }

    /**
     * Claims a broadcast notification (recipient_user_id = 0) for a specific user,
     * then marks it as read. This ensures that when other sessions/reloads query
     * for unread notifications, the notification is correctly shown as read for
     * this user only — without affecting other users' broadcast copies.
     */
    public boolean claimAndMarkRead(long notificationId, int userId, String role) {
        String claimSql =
            "UPDATE notifications " +
            "SET recipient_user_id = ?, is_read = 1, read_at = NOW() " +
            "WHERE id = ? AND recipient_user_id = 0 AND recipient_role = ? AND is_read = 0";
        int affected = update(LOGGER, claimSql, userId, notificationId, role);
        return affected >= 0;
    }

    /**
     * Marks all notifications for a user as read.
     */
    public int markAllAsRead(int userId, String role, Integer warehouseId) {
        return update(LOGGER, MARK_ALL_READ, userId, role,
                warehouseId != null ? warehouseId : 0);
    }

    // ── Delete ─────────────────────────────────────────────────────

    /**
     * Deletes notifications older than the specified number of days.
     */
    public int deleteOlderThan(int days) {
        return update(LOGGER, DELETE_OLD, days);
    }
}
