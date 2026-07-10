package com.wms.service.lazada;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.model.Channel;
import com.wms.service.channel.ChannelGateway;
import com.wms.service.channel.ChannelRegistry;
import com.wms.service.channel.ChannelSyncAudit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * LazadaReverseService — UC-B2C08 (Dispute Platform RMA) + BR-09
 * (RMA packages go to RETURN zone before QC).
 *
 * <p>Two flows:
 * <ol>
 *   <li>syncReverseOrders(channel): pull all open reverse orders from
 *       Lazada and persist them to {@code lazada_reverse_orders}.
 *       Stock is NOT credited back yet (BR-09).</li>
 *   <li>disputeReturn(reverseId, action, reason, comment, imageUrls):
 *       call /order/reverse/return/update to either accept or refuse
 *       the return. "refuseReturn" / "refuseRefund" require evidence
 *       (image_info) per Lazada's policy.</li>
 * </ol>
 *
 * <p>The physical handling (move returned package to RETURN zone, run
 * QC, then decide restock vs scrap) is performed by the existing
 * WarehouseReturnsServlet + RMA flow; this service only deals with
 * the platform-side state machine.
 */
public class LazadaReverseService {

    private static final Logger LOGGER = Logger.getLogger(LazadaReverseService.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ChannelGateway gateway;

    public LazadaReverseService() {
        this.gateway = ChannelRegistry.get("Lazada");
    }

    // ── Sync (poll RMA list) ───────────────────────────────────

    public SyncResult syncReverseOrders(Channel channel) {
        if (gateway == null) return SyncResult.fail("No gateway");
        long t0 = System.currentTimeMillis();
        String response;
        try {
            response = gateway.listReverseOrders(channel, null);
        } catch (Exception e) {
            ChannelSyncAudit.logFailure(channel.getChannelId(), "RMA_PULL",
                    null, 500, null, e.getMessage());
            return SyncResult.fail(e.getMessage());
        }
        long dt = System.currentTimeMillis() - t0;
        ChannelSyncAudit.logSuccess(channel.getChannelId(), "RMA_PULL",
                null, 200, null, response, dt);

        int saved = 0;
        try {
            JsonNode root = MAPPER.readTree(response);
            JsonNode data = root.path("data");
            JsonNode reverses = data.isArray() ? data : data.path("reverse_order_list");
            if (reverses.isArray()) {
                for (JsonNode rev : reverses) {
                    if (persistOne(channel, rev)) saved++;
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "RMA sync parse failed", e);
            return SyncResult.fail(e.getMessage());
        }
        return SyncResult.ok(saved);
    }

    private boolean persistOne(Channel ch, JsonNode rev) {
        String reverseId = rev.path("reverse_order_id").isMissingNode() ? "" : rev.path("reverse_order_id").asText();
        if (reverseId.isEmpty()) return false;
        String sql = "INSERT INTO lazada_reverse_orders "
                + "(channel_id, lazada_reverse_order_id, lazada_order_id, return_type, "
                + " status, reason, tracking_number, raw_payload) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE "
                + "  status = VALUES(status), "
                + "  reason = VALUES(reason), "
                + "  tracking_number = VALUES(tracking_number), "
                + "  raw_payload = VALUES(raw_payload), "
                + "  updated_at = CURRENT_TIMESTAMP";
        try (Connection conn = com.wms.util.DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, ch.getChannelId());
            ps.setString(2, reverseId);
            ps.setString(3, rev.path("order_id").isMissingNode() ? "" : rev.path("order_id").asText());
            ps.setString(4, rev.path("return_type").isMissingNode() ? "" : rev.path("return_type").asText());
            ps.setString(5, rev.path("status").isMissingNode() ? "" : rev.path("status").asText());
            ps.setString(6, rev.path("reason").isMissingNode() ? "" : rev.path("reason").asText());
            ps.setString(7, rev.path("tracking_number").isMissingNode() ? "" : rev.path("tracking_number").asText());
            ps.setString(8, rev.toString());
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "persistOne RMA failed for " + reverseId, e);
            return false;
        }
    }

    // ── Dispute / Confirm ──────────────────────────────────────

    public static final class DisputeResult {
        public final boolean success;
        public final String errorMessage;
        private DisputeResult(boolean s, String e) { this.success = s; this.errorMessage = e; }
        public static DisputeResult ok()      { return new DisputeResult(true, null); }
        public static DisputeResult fail(String e) { return new DisputeResult(false, e); }
    }

    /**
     * Calls /order/reverse/return/update to accept or refuse a return.
     * @param action   "acceptReturn" | "refuseReturn" | "refuseRefund"
     * @param reasonId Lazada reason id (required for refuse*)
     * @param comment  human-readable reason / comment
     * @param imageUrls optional list of evidence image URLs
     */
    public DisputeResult dispute(Channel channel, String reverseOrderId,
                                 String action, String reasonId, String comment,
                                 String imageUrls) {
        if (gateway == null) return DisputeResult.fail("No gateway");
        Map<String, String> payload = new HashMap<>();
        if (reasonId != null) payload.put("reason_id", reasonId);
        if (comment  != null) payload.put("comment", comment);
        // Lazada expects image_info as a JSON array string
        if (imageUrls != null && !imageUrls.isEmpty()) {
            payload.put("image_info", imageUrls);
        }
        long t0 = System.currentTimeMillis();
        try {
            String response = gateway.updateReverseOrder(channel, reverseOrderId, action, payload);
            long dt = System.currentTimeMillis() - t0;
            ChannelSyncAudit.logSuccess(channel.getChannelId(), "RMA_UPDATE",
                    reverseOrderId, 200, "action=" + action, response, dt);

            JsonNode root = MAPPER.readTree(response);
            String code = root.path("code").isMissingNode() ? "" : root.path("code").asText();
            if (!"0".equals(code) && !code.isEmpty()) {
                String msg = root.path("message").isMissingNode() ? "" : root.path("message").asText();
                if (msg.isEmpty()) msg = "Lazada rejected the RMA update";
                return DisputeResult.fail(msg);
            }
            // Update local state to reflect the dispute action
            String qcStatus = action.startsWith("refuse") ? "DISPUTED" : "PENDING";
            updateQcStatus(channel.getChannelId(), reverseOrderId, qcStatus, action);
            return DisputeResult.ok();
        } catch (Exception e) {
            ChannelSyncAudit.logFailure(channel.getChannelId(), "RMA_UPDATE",
                    reverseOrderId, 500, "action=" + action, e.getMessage());
            return DisputeResult.fail(e.getMessage());
        }
    }

    private void updateQcStatus(int channelId, String reverseOrderId,
                                String qcStatus, String disputeAction) {
        String sql = "UPDATE lazada_reverse_orders "
                   + "SET qc_status = ?, dispute_action = ?, updated_at = CURRENT_TIMESTAMP "
                   + "WHERE channel_id = ? AND lazada_reverse_order_id = ?";
        try (Connection conn = com.wms.util.DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, qcStatus);
            ps.setString(2, disputeAction);
            ps.setInt(3, channelId);
            ps.setString(4, reverseOrderId);
            ps.executeUpdate();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "updateQcStatus failed", e);
        }
    }

    // ── Cancel Order ─────────────────────────────────────────────

    /**
     * Step 1 — calls GET /order/reverse/cancel/validate to check if the order
     * can be cancelled and receive the available reason options.
     *
     * @param orderItemIdList JSON array string of order_item_ids, e.g. ["100827","..."]
     * @return CancelValidateResult with success flag, reason_options list, and tip
     */
    public CancelValidateResult cancelValidate(Channel channel, String orderId, String orderItemIdList) {
        if (gateway == null) return CancelValidateResult.fail("No gateway");
        try {
            String response = gateway.cancelValidate(channel, orderId, orderItemIdList);
            JsonNode root = MAPPER.readTree(response);
            String code = root.path("code").asText();
            String message = root.path("message").asText();

            if (!"0".equals(code) && !code.isEmpty()) {
                return CancelValidateResult.fail("Lazada: " + message);
            }

            JsonNode data = root.path("data");
            String tipType = data.path("tip_type").asText();
            String tipContent = data.path("tip_content").asText();

            List<ReasonOption> reasons = new ArrayList<>();
            JsonNode reasonOptions = data.path("reason_options");
            if (reasonOptions.isArray()) {
                for (JsonNode opt : reasonOptions) {
                    reasons.add(new ReasonOption(
                            opt.path("reason_id").asText(),
                            opt.path("reason_name").asText()));
                }
            }
            return CancelValidateResult.ok(reasons, tipType, tipContent);

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "cancelValidate failed for orderId=" + orderId, e);
            return CancelValidateResult.fail(e.getMessage());
        }
    }

    /**
     * Step 2 — calls GET /order/reverse/cancel/create to submit the cancel request.
     *
     * @param orderItemIdList JSON array string of order_item_ids, e.g. ["100827","..."]
     * @param reasonId       reason ID selected by staff from the validate step
     * @return CancelCreateResult with success flag and optional tip from Lazada
     */
    public CancelCreateResult cancelCreate(Channel channel, String orderId,
                                          String orderItemIdList, String reasonId) {
        if (gateway == null) return CancelCreateResult.fail("No gateway");
        try {
            String response = gateway.cancelCreate(channel, orderId, orderItemIdList, reasonId);
            JsonNode root = MAPPER.readTree(response);
            String code = root.path("code").asText();
            String message = root.path("message").asText();

            if (!"0".equals(code) && !code.isEmpty()) {
                return CancelCreateResult.fail("Lazada: " + message);
            }

            JsonNode data = root.path("data");
            String tipType = data.path("tip_type").asText();
            String tipContent = data.path("tip_content").asText();
            return CancelCreateResult.ok(tipType, tipContent);

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "cancelCreate failed for orderId=" + orderId, e);
            return CancelCreateResult.fail(e.getMessage());
        }
    }

    // ── DTO ────────────────────────────────────────────────────

    public static final class SyncResult {
        public final boolean ok;
        public final int saved;
        public final String error;
        private SyncResult(boolean o, int s, String e) { ok = o; saved = s; error = e; }
        public static SyncResult ok(int s) { return new SyncResult(true, s, null); }
        public static SyncResult fail(String e) { return new SyncResult(false, 0, e); }
    }

    public static final class ReasonOption {
        public final String reasonId;
        public final String reasonName;
        public ReasonOption(String reasonId, String reasonName) {
            this.reasonId = reasonId;
            this.reasonName = reasonName;
        }
    }

    public static final class CancelValidateResult {
        public final boolean success;
        public final List<ReasonOption> reasons;
        public final String tipType;
        public final String tipContent;
        public final String error;
        private CancelValidateResult(boolean s, List<ReasonOption> r, String tt, String tc, String e) {
            success = s; reasons = r; tipType = tt; tipContent = tc; error = e;
        }
        public static CancelValidateResult ok(List<ReasonOption> r, String tt, String tc) {
            return new CancelValidateResult(true, r, tt, tc, null);
        }
        public static CancelValidateResult fail(String e) {
            return new CancelValidateResult(false, null, null, null, e);
        }
    }

    public static final class CancelCreateResult {
        public final boolean success;
        public final String tipType;
        public final String tipContent;
        public final String error;
        private CancelCreateResult(boolean s, String tt, String tc, String e) {
            success = s; tipType = tt; tipContent = tc; error = e;
        }
        public static CancelCreateResult ok(String tt, String tc) {
            return new CancelCreateResult(true, tt, tc, null);
        }
        public static CancelCreateResult fail(String e) {
            return new CancelCreateResult(false, null, null, e);
        }
    }
}
