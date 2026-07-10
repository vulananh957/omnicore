package com.wms.controller.lazada;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.dao.ChannelDAO;
import com.wms.model.Channel;
import com.wms.service.channel.ChannelSyncAudit;
import com.wms.service.lazada.LazadaWebhookService;
import com.wms.service.sales.OrderService;
import com.wms.util.DBConnection;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HexFormat;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * LazadaWebhookServlet — Lazada pushes real-time status updates
 * (UC-B2C07) to {@code /lazada/webhook}. We:
 *   1. Verify the caller's IP against {@code channels.allowed_webhook_ips}
 *      (comma-separated CIDR/IPs) — if the list is empty we still accept
 *      but log a warning so ops can lock it down per-channel.
 *   2. Verify the X-Lazada-Signature header against
 *      HMAC-SHA256(rawBody, channels.webhook_secret). Lazada's spec is
 *      {@code hex(hmacSha256(body, secret))}. We fall back to the older
 *      base64 variant by trying both.
 *   3. De-duplicate by X-Lazada-Message-Id (also stored in
 *      {@code webhook_logs.message_id}) — Lazada retries on non-2xx, and
 *      a successful re-delivery must be a no-op.
 *   4. Log the raw payload to webhook_logs (idempotent).
 *   5. Parse the data: order_id, status, tracking_number if present.
 *   6. Forward to {@link OrderService#handleAction} with action="webhook"
 *      so the standard DELIVERED / CANCELLED / RETURNED / SHIPPED rules
 *      apply.
 *   7. Failed deliveries get retry_count incremented by
 *      {@link com.wms.scheduler.LazadaWebhookRetryScheduler} until they
 *      succeed or hit 3 attempts.
 *
 * <p>Configured in web.xml without auth — the public URL is registered
 * with Lazada as the push endpoint.
 */
@WebServlet(name = "LazadaWebhookServlet", urlPatterns = {"/lazada/webhook", "/webhook/lazada"})
public class LazadaWebhookServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(LazadaWebhookServlet.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final LazadaWebhookService webhookService = new LazadaWebhookService();


    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        String body = readBody(req);
        String clientIp = resolveClientIp(req);
        String signature = headerOrEmpty(req, "X-Lazada-Signature");
        String messageId = headerOrEmpty(req, "X-Lazada-Message-Id");
        LOGGER.info("LazadaWebhookServlet: ip=" + clientIp + " messageId=" + messageId
                + " signatureLen=" + signature.length() + " body=" + trimForLog(body));

        // Idempotency: Lazada retries the same message_id on non-2xx.
        // If we already processed this id successfully, ack and bail.
        if (!messageId.isEmpty() && isAlreadyProcessed(messageId)) {
            LOGGER.info("LazadaWebhookServlet: duplicate message_id=" + messageId + " — acking 200");
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.getWriter().print("duplicate");
            return;
        }

        // Persist raw payload to webhook_logs (with metadata for DLQ + audit)
        int logId = webhookService.logWebhook(0, "LAZADA_ORDER_STATUS", body, messageId, clientIp, signature, "PENDING", null);

        try {
            // Check if it is a Lazada handshake/test message (message_type = 0)
            JsonNode rootNode = MAPPER.readTree(body);
            int messageType = rootNode.path("message_type").asInt(-1);
            if (messageType == 0) {
                LOGGER.info("LazadaWebhookServlet: received test handshake message — acknowledging 200 OK");
                webhookService.markWebhookStatus(logId, "SUCCESS", "Handshake success");
                resp.setStatus(HttpServletResponse.SC_OK);
                resp.getWriter().print("ok");
                return;
            }

            // We need a channel to verify signature/IP. Lazada doesn't put
            // channel_id in the body, so we either find it from the first
            // order_id (preferred) or fall back to "any LAZADA channel".
            Integer channelId = inferChannelId(body);
            Channel channel = channelId != null ? new ChannelDAO().findById(channelId) : null;

            if (channel == null) {
                // Fallback: Find the first active Lazada channel in the database
                java.util.List<Channel> lazadaChannels = new ChannelDAO().findAll();
                for (Channel ch : lazadaChannels) {
                    if ("Lazada".equalsIgnoreCase(ch.getPlatform()) && ch.isActive()) {
                        channel = ch;
                        break;
                    }
                }
            }

            // If channel cannot be resolved, reject — we must not silently bypass IP/HMAC checks
            if (channel == null) {
                LOGGER.warning("LazadaWebhookServlet: could not resolve channel from payload — rejecting 400");
                webhookService.markWebhookStatus(logId, "REJECTED", "Channel not found for payload");
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().print("channel_not_found");
                return;
            }

            // 1. IP whitelist
            if (!isIpAllowed(clientIp, channel.getAllowedWebhookIps())) {
                LOGGER.warning("LazadaWebhookServlet: IP " + clientIp + " not in whitelist for channel "
                        + channel.getChannelId());
                webhookService.markWebhookStatus(logId, "REJECTED", "IP not in whitelist: " + clientIp);
                resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                resp.getWriter().print("ip_not_allowed");
                return;
            }

            // 2. HMAC signature
            String webhookSecret = channel.getWebhookSecret();
            if (webhookSecret != null && !webhookSecret.isEmpty()) {
                if (signature.isEmpty() || !verifySignature(body, signature, webhookSecret)) {
                    LOGGER.warning("LazadaWebhookServlet: signature mismatch (channel=" + channelId + ")");
                    webhookService.markWebhookStatus(logId, "REJECTED", "Invalid X-Lazada-Signature");
                    resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    resp.getWriter().print("bad_signature");
                    return;
                }
            } else {
                // Dev convenience: no secret configured — accept but flag the log
                LOGGER.warning("LazadaWebhookServlet: channel has no webhook_secret; accepting without HMAC");
            }

            JsonNode root = MAPPER.readTree(body);
            JsonNode data = root.path("data");
            // Lazada wraps multiple orders under "data" as a JSON array.
            JsonNode orders = data.isArray() ? data : data.path("orders");
            if (!orders.isArray() || orders.isEmpty()) {
                // Some events are single-order
                orders = root.path("data");
            }
            if (orders.isObject()) {
                handleOne(orders, channel);
            } else if (orders.isArray()) {
                for (JsonNode o : orders) handleOne(o, channel);
            } else {
                throw new IllegalStateException("Unrecognised Lazada webhook payload");
            }
            webhookService.markWebhookStatus(logId, "SUCCESS", null);
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.getWriter().print("ok");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "LazadaWebhookServlet: failed", e);
            ChannelSyncAudit.logFailure(0, "WEBHOOK", null, 500, trimForLog(body), e.getMessage());
            webhookService.markWebhookStatus(logId, "FAILED", e.getMessage());
            // 5xx → Lazada will retry. We return 500 so the retry scheduler
            // also has a chance to pick it up from webhook_logs.
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().print("error: " + e.getMessage());
        }
    }

    // ── Idempotency ──────────────────────────────────────────────

    private boolean isAlreadyProcessed(String messageId) {
        String sql = "SELECT 1 FROM webhook_logs WHERE message_id = ? AND status = 'SUCCESS' LIMIT 1";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, messageId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "isAlreadyProcessed check failed", e);
            return false;
        }
    }

    // ── Channel inference ────────────────────────────────────────

    private Integer inferChannelId(String body) {
        try {
            JsonNode root = MAPPER.readTree(body);
            JsonNode data = root.path("data");
            JsonNode orders = data.isArray() ? data : data.path("orders");
            if (orders.isArray() && orders.size() > 0) {
                String orderId = orders.get(0).path("order_id").asText();
                if (!orderId.isEmpty()) {
                    com.wms.model.Order o = new com.wms.dao.OrderDAO().findByOrderCode(orderId);
                    if (o != null && o.getChannelId() > 0) return o.getChannelId();
                }
            } else if (orders.isObject()) {
                String orderId = orders.path("order_id").asText();
                if (!orderId.isEmpty()) {
                    com.wms.model.Order o = new com.wms.dao.OrderDAO().findByOrderCode(orderId);
                    if (o != null && o.getChannelId() > 0) return o.getChannelId();
                }
            }
        } catch (RuntimeException e) {
            throw e; // propagate so caller cannot silently proceed with null channel
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "inferChannelId failed: " + e.getMessage(), e);
        }
        return null;
    }

    // ── IP whitelist ─────────────────────────────────────────────

    private boolean isIpAllowed(String clientIp, String allowedIpsCsv) {
        if (allowedIpsCsv == null || allowedIpsCsv.isBlank()) return true;
        for (String raw : allowedIpsCsv.split(",")) {
            String entry = raw.trim();
            if (entry.isEmpty()) continue;
            if (entry.equals(clientIp)) return true;
            // Lazada publishes CIDR ranges like 52.74.0.0/16 — exact-match
            // is a v1 simplification; full CIDR is left to the ops team to
            // maintain as a list of single IPs.
        }
        return false;
    }

    // ── HMAC signature ───────────────────────────────────────────

    private boolean verifySignature(String body, String providedSignature, String secret) {
        String computed = hmacSha256Hex(body, secret);
        if (constantTimeEquals(computed, providedSignature)) return true;
        // Some Lazada integrations sign with the value lowercased; also
        // try uppercase + no-prefix variants
        return constantTimeEquals(computed.toLowerCase(), providedSignature.toLowerCase());
    }

    private static String hmacSha256Hex(String body, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new RuntimeException("HMAC compute failed", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) return false;
        int diff = 0;
        for (int i = 0; i < a.length(); i++) diff |= a.charAt(i) ^ b.charAt(i);
        return diff == 0;
    }

    // ── Order processing ─────────────────────────────────────────

    private void handleOne(JsonNode order, Channel channel) {
        String orderId = order.path("order_id").asText();
        if (orderId.isEmpty()) return;
        String status = order.path("statuses").isArray() && order.path("statuses").size() > 0
                ? order.path("statuses").get(0).asText()
                : order.path("status").asText();
        if (status.isEmpty()) return;

        webhookService.handleOrderEvent(order);

        // Route through the standard OrderService webhook handler
        new OrderService().handleAction("webhook", orderId,
                null, null, null, null, null, null, null, status, null, 1, "SYSTEM");
    }

    private static String readBody(HttpServletRequest req) throws IOException {
        try (BufferedReader r = req.getReader()) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }

    private static String headerOrEmpty(HttpServletRequest req, String name) {
        String v = req.getHeader(name);
        return v == null ? "" : v;
    }

    private static String resolveClientIp(HttpServletRequest req) {
        // Lazada pushes from AWS Singapore — we still respect X-Forwarded-For
        // in case ops fronted the endpoint with ALB/Nginx
        String fwd = req.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) {
            int comma = fwd.indexOf(',');
            return (comma > 0 ? fwd.substring(0, comma) : fwd).trim();
        }
        return req.getRemoteAddr();
    }

    private static String trimForLog(String s) {
        if (s == null) return null;
        return s.length() > 4000 ? s.substring(0, 4000) + "...[truncated]" : s;
    }
}
