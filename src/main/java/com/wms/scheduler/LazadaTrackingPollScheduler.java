package com.wms.scheduler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.dao.ChannelDAO;
import com.wms.dao.OrderDAO;
import com.wms.model.Channel;
import com.wms.model.Order;
import com.wms.service.channel.ChannelGateway;
import com.wms.service.channel.ChannelRegistry;
import com.wms.service.channel.ChannelSyncAudit;
import com.wms.service.sales.OrderService;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * LazadaTrackingPollScheduler — UC-B2C07.
 *
 * <p>For every SHIPPED Lazada order that has not been DELIVERED yet,
 * poll {@code /logistic/order/trace} and append the new tracking
 * events to {@code order_tracking_events}. When the most recent
 * event signals "delivered", the order is moved to DELIVERED.
 *
 * <p>Disabled by default — set {@code lazada.tracking.poll.enabled=true}
 * in web.xml to enable. Default interval: 30 minutes.
 */
@WebListener
public class LazadaTrackingPollScheduler implements ServletContextListener {

    private static final Logger LOGGER = Logger.getLogger(LazadaTrackingPollScheduler.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static final String CTX_PARAM_ENABLED  = "lazada.tracking.poll.enabled";
    public static final String CTX_PARAM_INTERVAL = "lazada.tracking.poll.interval.minutes";

    private Timer timer;
    private ServletContext ctx;

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        this.ctx = sce.getServletContext();
        String enabled = ctx.getInitParameter(CTX_PARAM_ENABLED);
        if (!"true".equalsIgnoreCase(enabled)) {
            LOGGER.info("LazadaTrackingPollScheduler: disabled (set "
                    + CTX_PARAM_ENABLED + "=true to enable).");
            return;
        }
        int minutes = parseInterval(ctx.getInitParameter(CTX_PARAM_INTERVAL));
        LOGGER.info("LazadaTrackingPollScheduler: enabled, interval=" + minutes + " min");
        timer = new Timer("LazadaTrackingPollTimer", true);
        timer.scheduleAtFixedRate(new PollTask(), 90_000L, minutes * 60_000L);
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    private int parseInterval(String v) {
        try { int n = Integer.parseInt(v); return n > 0 ? n : 30; }
        catch (Exception e) { return 30; }
    }

    // ── Poll task ─────────────────────────────────────────────

    private class PollTask extends TimerTask {

        private final OrderDAO orderDAO = new OrderDAO();
        private final ChannelDAO channelDAO = new ChannelDAO();
        private final OrderService orderService = new OrderService();

        @Override
        public void run() {
            LOGGER.info("LazadaTrackingPollScheduler: cycle started");
            try {
                List<Channel> channels = channelDAO.findAll();
                for (Channel ch : channels) {
                    if (!ch.isActive() || !"Lazada".equalsIgnoreCase(ch.getPlatform())) continue;
                    if (ch.getAccessToken() == null || ch.getAccessToken().isEmpty()) continue;
                    try {
                        pollChannel(ch);
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING,
                                "LazadaTrackingPollScheduler: failed channel "
                                        + ch.getChannelName(), e);
                    }
                }
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "LazadaTrackingPollScheduler: cycle failed", e);
            }
        }

        private void pollChannel(Channel ch) {
            ChannelGateway gateway = ChannelRegistry.get("Lazada");
            if (gateway == null) return;
            List<Order> openOrders = orderDAO.findInTransitByChannel(ch.getChannelId());
            int polled = 0, appended = 0, delivered = 0;
            for (Order o : openOrders) {
                if (o.getOrderCode() == null || o.getOrderCode().isEmpty()) continue;
                polled++;
                long t0 = System.currentTimeMillis();
                String response;
                try {
                    response = gateway.getTrackingTrace(ch, o.getOrderCode());
                } catch (Exception e) {
                    ChannelSyncAudit.logFailure(ch.getChannelId(), "TRACKING_POLL",
                            o.getOrderCode(), 500, null, e.getMessage());
                    continue;
                }
                long dt = System.currentTimeMillis() - t0;
                ChannelSyncAudit.logSuccess(ch.getChannelId(), "TRACKING_POLL",
                        o.getOrderCode(), 200, null, response, dt);

                try {
                    JsonNode root = MAPPER.readTree(response);
                    JsonNode data = root.path("data");
                    JsonNode events = data.isArray() ? data
                            : data.path("logistic_detail_info_list");
                    if (!events.isArray() || events.isEmpty()) continue;

                    boolean justDelivered = false;
                    for (JsonNode ev : events) {
                        String desc = ev.path("status_description").asText();
                        String timeStr = ev.path("event_time").asText();
                        LocalDateTime eventTime = parseTime(timeStr);
                        if (eventTime == null) continue;
                        boolean inserted = insertEvent(o.getOrderId(), ch.getChannelId(),
                                ev.path("status_code").asText(), desc,
                                ev.path("location").asText(), eventTime, ev.toString());
                        if (inserted) appended++;
                        if (isDeliveredStatus(desc)) justDelivered = true;
                    }
                    if (justDelivered) {
                        // UC-B2C07: Delivered triggers status update via OrderService
                        // (which checks the BR-09 RMA grace period separately).
                        orderService.handleAction("webhook", o.getOrderCode(),
                                null, null, null, null, null, null, null, "DELIVERED", null, 1, "SYSTEM");
                        delivered++;
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING,
                            "LazadaTrackingPollScheduler: parse failure for " + o.getOrderCode(), e);
                }
            }
            LOGGER.info("LazadaTrackingPollScheduler: channel " + ch.getChannelName()
                    + " polled=" + polled + " appended=" + appended
                    + " delivered=" + delivered);
        }

        private boolean isDeliveredStatus(String description) {
            if (description == null) return false;
            String d = description.toLowerCase();
            return d.contains("delivered") || d.contains("đã giao");
        }

        private LocalDateTime parseTime(String s) {
            if (s == null || s.isEmpty()) return null;
            try {
                // Lazada's event_time looks like "2024-05-12 10:15:00"
                if (s.length() >= 19) {
                    return LocalDateTime.parse(s.substring(0, 19));
                }
            } catch (Exception ignore) { }
            try {
                long epoch = Long.parseLong(s);
                if (epoch > 1_000_000_000_000L) epoch /= 1000L;
                return LocalDateTime.ofEpochSecond(epoch, 0, java.time.ZoneOffset.UTC);
            } catch (Exception ignore) { }
            return null;
        }

        private boolean insertEvent(int orderId, int channelId, String code, String desc,
                                    String location, LocalDateTime eventTime, String raw) {
            String sql = "INSERT INTO order_tracking_events "
                    + "(order_id, channel_id, event_time, status_code, status_description, "
                    + " location, raw_payload) "
                    + "SELECT ?, ?, ?, ?, ?, ?, ? "
                    + "FROM dual "
                    + "WHERE NOT EXISTS ("
                    + "  SELECT 1 FROM order_tracking_events "
                    + "  WHERE order_id = ? AND channel_id = ? AND event_time = ? "
                    + "        AND status_code = ?)";
            try (Connection conn = com.wms.util.DBConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, orderId);
                ps.setInt(2, channelId);
                ps.setTimestamp(3, Timestamp.valueOf(eventTime));
                ps.setString(4, code);
                ps.setString(5, desc);
                ps.setString(6, location);
                ps.setString(7, raw);
                ps.setInt(8, orderId);
                ps.setInt(9, channelId);
                ps.setTimestamp(10, Timestamp.valueOf(eventTime));
                ps.setString(11, code);
                return ps.executeUpdate() > 0;
            } catch (Exception e) {
                LOGGER.log(Level.WARNING,
                        "LazadaTrackingPollScheduler: insertEvent failed", e);
                return false;
            }
        }
    }
}
