package com.wms.service.common;

import com.wms.dao.NotificationDAO;
import com.wms.model.Notification;
import com.wms.model.User;
import com.wms.util.AppConstants;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * NotificationService — Business logic layer for creating and retrieving notifications.
 *
 * <p>Use the convenience broadcast methods to send alerts to all members of a role
 * (optionally scoped to a specific warehouse). Use the session-aware getters to
 * retrieve the right notifications for the currently logged-in user.</p>
 *
 * <p>All methods are safe to call even when notifications are disabled — they log
 * a warning and return gracefully without disrupting the caller's transaction.</p>
 */
public class NotificationService {

    private static final Logger LOGGER = Logger.getLogger(NotificationService.class.getName());
    private final NotificationDAO dao = new NotificationDAO();

    // ── Broadcast to Warehouse Staff ──────────────────────────────────

    /**
     * Notifies all warehouse staff of a specific warehouse.
     * Use for: defective goods, RMA arrivals, incoming transfers, inventory checks.
     */
    public void notifyWarehouseStaff(Integer warehouseId, String type, String title,
                                    String message, String refType, Long refId, String priority) {
        Notification tmpl = Notification.forRole(
                AppConstants.ROLE_WAREHOUSE_STAFF, warehouseId,
                type, title, message, refType, refId, priority);
        int count = dao.broadcastToWarehouseStaff(warehouseId, tmpl);
        if (count > 0) {
            LOGGER.log(Level.INFO, "Notified {0} WH staff (warehouse={1}): {2}",
                    new Object[]{count, warehouseId, title});
        }
    }

    /**
     * Shorthand for warehouse-staff notifications with normal priority.
     */
    public void notifyWarehouseStaff(Integer warehouseId, String type, String title,
                                    String message, String refType, Long refId) {
        notifyWarehouseStaff(warehouseId, type, title, message, refType, refId,
                Notification.PRIORITY_NORMAL);
    }

    // ── Broadcast to Sales Staff ──────────────────────────────────────

    /**
     * Notifies all sales staff — use for: new orders from channels.
     */
    public void notifySalesStaff(String title, String message,
                                String refType, Long refId, String priority) {
        Notification tmpl = Notification.forRole(
                AppConstants.ROLE_SALES_STAFF, null,
                Notification.TYPE_ORDER, title, message, refType, refId, priority);
        int count = dao.broadcastToSalesStaff(tmpl);
        if (count > 0) {
            LOGGER.log(Level.INFO, "Notified {0} sales staff: {1}", new Object[]{count, title});
        }
    }

    public void notifySalesStaff(String title, String message, String refType, Long refId) {
        notifySalesStaff(title, message, refType, refId, Notification.PRIORITY_NORMAL);
    }

    // ── Broadcast to Admin ───────────────────────────────────────────

    public void notifyAdmins(String title, String message,
                             String refType, Long refId, String priority) {
        Notification tmpl = Notification.forRole(
                AppConstants.ROLE_ADMIN, null,
                Notification.TYPE_SYSTEM, title, message, refType, refId, priority);
        int count = dao.broadcastToAdmins(tmpl);
        if (count > 0) {
            LOGGER.log(Level.INFO, "Notified {0} admins: {1}", new Object[]{count, title});
        }
    }

    // ── User-specific notification ─────────────────────────────────────

    /**
     * Sends a notification to a specific user.
     */
    public void notifyUser(int userId, String role, Integer warehouseId,
                           String type, String title, String message,
                           String refType, Long refId, String priority) {
        Notification n = Notification.forUser(
                userId, role, warehouseId,
                type, title, message, refType, refId, priority);
        long id = dao.insert(n);
        if (id > 0) {
            LOGGER.log(Level.FINE, "Notified user {0}: {1}", new Object[]{userId, title});
        }
    }

    // ── Session-aware retrieval ───────────────────────────────────────

    /**
     * Returns notifications for the currently logged-in user.
     */
    public List<Notification> getNotificationsForSession(HttpSession session, int limit) {
        User user = (User) session.getAttribute(AppConstants.SESSION_USER);
        if (user == null) return List.of();

        String role = user.getRole();
        Integer warehouseId = null;
        if (AppConstants.ROLE_WAREHOUSE_STAFF.equals(role)) {
            Object whObj = session.getAttribute(AppConstants.SESSION_WAREHOUSE);
            if (whObj instanceof Integer) {
                warehouseId = (Integer) whObj;
            }
        }
        return dao.findForUser(user.getUserId(), role, warehouseId, limit);
    }

    /**
     * Returns the unread notification count for the badge.
     */
    public int getUnreadCountForSession(HttpSession session) {
        User user = (User) session.getAttribute(AppConstants.SESSION_USER);
        if (user == null) return 0;

        String role = user.getRole();
        Integer warehouseId = null;
        if (AppConstants.ROLE_WAREHOUSE_STAFF.equals(role)) {
            Object whObj = session.getAttribute(AppConstants.SESSION_WAREHOUSE);
            if (whObj instanceof Integer) {
                warehouseId = (Integer) whObj;
            }
        }
        return dao.countUnread(user.getUserId(), role, warehouseId);
    }

    // ── Mark as read ─────────────────────────────────────────────────

    public boolean markAsRead(long notificationId, HttpSession session) {
        User user = (User) session.getAttribute(AppConstants.SESSION_USER);
        if (user == null) return false;

        String role = user.getRole();
        Integer warehouseId = null;
        if (AppConstants.ROLE_WAREHOUSE_STAFF.equals(role)) {
            Object whObj = session.getAttribute(AppConstants.SESSION_WAREHOUSE);
            if (whObj instanceof Integer) {
                warehouseId = (Integer) whObj;
            }
        }
        // Use claimAndMarkRead so broadcast notifications (recipient_user_id=0) are
        // correctly per-user: we insert a personal copy with is_read=1 for this
        // user so other sessions see the original as still unread.
        return dao.claimAndMarkRead(notificationId, user.getUserId(), role);
    }

    public int markAllAsReadForSession(HttpSession session) {
        User user = (User) session.getAttribute(AppConstants.SESSION_USER);
        if (user == null) return 0;

        String role = user.getRole();
        Integer warehouseId = null;
        if (AppConstants.ROLE_WAREHOUSE_STAFF.equals(role)) {
            Object whObj = session.getAttribute(AppConstants.SESSION_WAREHOUSE);
            if (whObj instanceof Integer) {
                warehouseId = (Integer) whObj;
            }
        }
        return dao.markAllAsRead(user.getUserId(), role, warehouseId);
    }

    // ── Cleanup ──────────────────────────────────────────────────────

    /**
     * Removes notifications older than 30 days. Safe to call on a schedule.
     */
    public int deleteOldNotifications(int days) {
        return dao.deleteOlderThan(days);
    }

    // ── Convenience helpers for common warehouse events ───────────────

    /**
     * Defective stock alert — sent to WH staff of the warehouse.
     */
    public void notifyDefectiveStock(int warehouseId, String warehouseName,
                                     int defectiveCount, int totalUnits) {
        String title = "Hàng lỗi cần xử lý";
        String msg = "Kho " + warehouseName + " có " + defectiveCount +
                " SKU (" + totalUnits + " đơn vị) hàng lỗi chưa xử lý. " +
                "Cần kiểm tra và trả lại NCC kịp thời.";
        notifyWarehouseStaff(warehouseId, Notification.TYPE_DEFECTIVE,
                title, msg, "DEFECTIVE", null, Notification.PRIORITY_HIGH);
    }

    /**
     * New RMA/return order — sent to WH staff of destination warehouse.
     */
    public void notifyNewReturn(int warehouseId, String warehouseName,
                                long returnId, String returnCode) {
        String title = "Phiếu hoàn hàng mới";
        String msg = "Kho " + warehouseName + " có phiếu hoàn hàng " +
                returnCode + " cần tiếp nhận QC.";
        notifyWarehouseStaff(warehouseId, Notification.TYPE_RETURN,
                title, msg, "RMA", returnId, Notification.PRIORITY_HIGH);
    }

    /**
     * Incoming stock transfer — sent to WH staff of destination warehouse.
     */
    public void notifyIncomingTransfer(int warehouseId, String warehouseName,
                                      long transferId, String transferCode,
                                      int itemCount) {
        String title = "Phiếu chuyển kho đến";
        String msg = "Kho " + warehouseName + " sắp nhận " + itemCount +
                " mặt hàng từ phiếu chuyển " + transferCode + ".";
        notifyWarehouseStaff(warehouseId, Notification.TYPE_TRANSFER,
                title, msg, "TRANSFER", transferId, Notification.PRIORITY_NORMAL);
    }

    /**
     * New order from channel — sent to sales staff.
     */
    public void notifyNewOrder(long orderId, String channelName) {
        String title = "Đơn hàng mới từ " + channelName;
        String msg = "Có đơn hàng mới #" + orderId + " từ " +
                channelName + " cần xác nhận và xử lý.";
        notifySalesStaff(title, msg, "ORDER", orderId, Notification.PRIORITY_HIGH);
    }

    /**
     * Order status update — sent to the sales staff who owns the order.
     */
    public void notifyOrderStatus(int createdByUserId, long orderId,
                                   String orderCode, String oldStatus, String newStatus) {
        String title = "Đơn hàng " + orderCode + " cập nhật trạng thái";
        String msg = "Đơn hàng " + orderCode + " đã chuyển từ '" + oldStatus +
                "' sang '" + newStatus + "'.";
        notifyUser(createdByUserId, AppConstants.ROLE_SALES_STAFF, null,
                Notification.TYPE_ORDER, title, msg, "ORDER", orderId,
                Notification.PRIORITY_NORMAL);
    }
}
