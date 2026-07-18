package com.wms.dao;

import com.wms.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * AuditLogDAO — Logs all API calls for debugging, compliance, and anomaly detection.
 *
 * Tracks:
 * - Every request (method, path, channel, IP, timestamp)
 * - Signature verification (valid/invalid)
 * - Deduction success/fail + inventory state
 * - Errors and exceptions
 */
public class AuditLogDAO {

    private static final Logger LOGGER = Logger.getLogger(AuditLogDAO.class.getName());

    /**
     * Log API authentication attempt (success or failure).
     *
     * @param channel       Channel name (e.g., "Website", "Lazada")
     * @param method        HTTP method
     * @param path          Request path
     * @param remoteIp      Client IP
     * @param signatureValid true if signature matched
     * @param reason        Reason for failure (null if successful)
     */
    public boolean logApiAuth(String channel, String method, String path, String remoteIp,
                             boolean signatureValid, String reason) {
        String sql = "INSERT INTO api_audit_log (channel, method, path, remote_ip, signature_valid, reason, logged_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, NOW())";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, channel);
            ps.setString(2, method);
            ps.setString(3, path);
            ps.setString(4, remoteIp);
            ps.setBoolean(5, signatureValid);
            ps.setString(6, reason);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "logApiAuth failed for channel=" + channel + " path=" + path, e);
            return false;
        }
    }

    /**
     * Log inventory deduction decision (success or failure).
     *
     * @param orderId       Order ID
     * @param orderRef      Order reference code
     * @param productId     Product being deducted
     * @param channel       Channel (WEB, LAZADA, SHOPEE)
     * @param qtyRequested  Quantity requested
     * @param qtyAvailable  Quantity available (before attempt)
     * @param deductSuccess true if deduction succeeded
     * @param reason        Reason for failure (null if successful)
     */
    public boolean logDeductionAttempt(int orderId, String orderRef, int productId, String channel,
                                      int qtyRequested, int qtyAvailable, boolean deductSuccess, String reason) {
        String sql = "INSERT INTO deduction_audit_log (order_id, order_ref, product_id, channel, " +
                "qty_requested, qty_available, deduct_success, reason, logged_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW())";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            ps.setString(2, orderRef);
            ps.setInt(3, productId);
            ps.setString(4, channel);
            ps.setInt(5, qtyRequested);
            ps.setInt(6, qtyAvailable);
            ps.setBoolean(7, deductSuccess);
            ps.setString(8, reason);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "logDeductionAttempt failed for order " + orderRef, e);
            return false;
        }
    }

    /**
     * Log sync errors between Web and Main.
     */
    public boolean logSyncError(String fromSystem, String toSystem, String endpoint,
                               String errorMessage, int httpStatus) {
        String sql = "INSERT INTO sync_error_log (from_system, to_system, endpoint, error_message, http_status, logged_at) " +
                "VALUES (?, ?, ?, ?, ?, NOW())";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, fromSystem);
            ps.setString(2, toSystem);
            ps.setString(3, endpoint);
            ps.setString(4, errorMessage);
            ps.setInt(5, httpStatus);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "logSyncError failed for endpoint " + endpoint, e);
            return false;
        }
    }
}
