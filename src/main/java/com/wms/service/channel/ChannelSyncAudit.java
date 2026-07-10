package com.wms.service.channel;

import com.wms.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ChannelSyncAudit — Centralised logger for every channel API call.
 *
 * Persists to {@code channel_sync_audit}. Use this from any service that
 * talks to a marketplace so the admin dashboard has a single timeline
 * of what we sent, what came back, and how long it took.
 *
 * Why central: the old {@code lazada_sync_log} table only handled
 * ORDER_SYNC. With this helper we can audit STOCK_PUSH, PACK, RTS,
 * RMA_UPDATE, WEBHOOK, etc. uniformly.
 */
public final class ChannelSyncAudit {

    private static final Logger LOGGER = Logger.getLogger(ChannelSyncAudit.class.getName());

    private static final int MAX_EXCERPT = 3500;

    private ChannelSyncAudit() {
    }

    public static void log(int channelId, String operation, String referenceCode,
                           Integer httpStatus, String requestExcerpt, String responseExcerpt,
                           String errorMessage, Long durationMs) {
        String sql = "INSERT INTO channel_sync_audit "
                + "(channel_id, operation, ref_code, "
                + " request_data, response_data, error_message) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, channelId);
            ps.setString(2, operation);
            ps.setString(3, referenceCode);
            ps.setString(4, trim(requestExcerpt));
            ps.setString(5, trim(responseExcerpt));
            ps.setString(6, errorMessage);
            ps.executeUpdate();
        } catch (SQLException e) {
            // Audit must never break the business call; just log and move on.
            LOGGER.log(Level.WARNING,
                    "ChannelSyncAudit: failed to write audit row operation=" + operation, e);
        }
    }

    public static void logSuccess(int channelId, String operation, String referenceCode,
                                  Integer httpStatus, String request, String response,
                                  long durationMs) {
        log(channelId, operation, referenceCode, httpStatus, request, response, null, durationMs);
    }

    public static void logFailure(int channelId, String operation, String referenceCode,
                                  Integer httpStatus, String request, String errorMessage) {
        log(channelId, operation, referenceCode, httpStatus, request, null, errorMessage, null);
    }

    private static String trim(String s) {
        if (s == null) return null;
        return s.length() > MAX_EXCERPT ? s.substring(0, MAX_EXCERPT) + "...[truncated]" : s;
    }
}
