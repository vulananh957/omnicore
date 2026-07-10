package com.wms.scheduler;

import com.fasterxml.jackson.databind.JsonNode;
import com.wms.dao.ChannelDAO;
import com.wms.model.Channel;
import com.wms.service.lazada.LazadaOrderService;
import com.wms.service.lazada.LazadaOrderSyncService;
import com.wms.util.DBConnection;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * LazadaSyncScheduler — Background timer that periodically syncs Lazada orders.
 *
 * Pipeline per cycle, per active Lazada channel:
 *   1. Read {@code last_order_sync_at} from the channel record (incremental sync)
 *   2. GET /orders/get?status=pending&updated_after=...
 *   3. For each new order:
 *      a. GET /order/get to fetch full detail (shipping, address, payment)
 *      b. INSERT IGNORE orders (status = PENDING) with channel_id + channel_order_id
 *      c. INSERT IGNORE order_shipping_details (recipient, address, courier)
 *      d. INSERT IGNORE order_items (with SKU mapping resolution)
 *      e. Soft-allocate inventory (holding +qty, qty_available -qty)
 *   4. Persist last_order_sync_at back to the channel row
 *   5. Log every cycle to lazada_sync_log
 *
 * <p>Per user decision (Lazada end-to-end plan):
 * <ul>
 *   <li>Newly-synced orders land in {@code status = 'PENDING'} so Sales can review
 *       and pick a warehouse (UC-B2C05 manual review).</li>
 *   <li>{@code warehouse_id = 0} on first sync — Sales staff assigns on approval.</li>
 *   <li>Soft-allocate (BR-04) is deferred to {@code OrderService.handleAction("approve")},
 *       because the order does not yet have a warehouse at sync time.</li>
 * </ul>
 *
 * Interval and feature flag are read from web.xml context-params.
 */
@WebListener
public class LazadaSyncScheduler implements ServletContextListener {

    private static final Logger LOGGER = Logger.getLogger(LazadaSyncScheduler.class.getName());

    private Timer timer;
    private ServletContext servletContext;

    /** Context-param key controlling the interval in minutes. */
    public static final String CTX_PARAM_INTERVAL = "lazada.sync.interval.minutes";
    /** Context-param key controlling the enabled flag. */
    public static final String CTX_PARAM_ENABLED = "lazada.sync.enabled";
    /** Context-param key for the sync lookback window (minutes). */
    public static final String CTX_PARAM_LOOKBACK = "lazada.sync.lookback.minutes";

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        servletContext = sce.getServletContext();
        String enabled = servletContext.getInitParameter(CTX_PARAM_ENABLED);
        if ("false".equalsIgnoreCase(enabled)) {
            LOGGER.info("LazadaSyncScheduler: Disabled by configuration. Skipping startup.");
            return;
        }

        int intervalMinutes = parseInterval(servletContext.getInitParameter(CTX_PARAM_INTERVAL));
        LOGGER.info("LazadaSyncScheduler: Starting with interval=" + intervalMinutes + " minutes.");
        timer = new Timer("LazadaSyncTimer", true);
        timer.scheduleAtFixedRate(new SyncTask(servletContext), 0L, intervalMinutes * 60_000L);
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        if (timer != null) {
            LOGGER.info("LazadaSyncScheduler: Cancelling timer...");
            timer.cancel();
            timer = null;
        }
    }

    private int parseInterval(String prop) {
        try {
            int val = Integer.parseInt(prop);
            return (val > 0) ? val : 5;
        } catch (Exception e) {
            return 5;
        }
    }

    private int parseLookback(ServletContext ctx) {
        try {
            int val = Integer.parseInt(ctx.getInitParameter(CTX_PARAM_LOOKBACK));
            return (val > 0) ? val : 60; // default: re-fetch last 60 minutes on first run
        } catch (Exception e) {
            return 60;
        }
    }

    // ───────────────────────────────────────────────────────────
    // Sync task
    // ───────────────────────────────────────────────────────────

    private class SyncTask extends TimerTask {

        private final LazadaOrderService orderService = new LazadaOrderService();
        private final ChannelDAO channelDAO = new ChannelDAO();
        private final LazadaOrderSyncService syncService = new LazadaOrderSyncService();
        private final int lookbackMinutes;
        SyncTask(ServletContext servletContext) {
            this.lookbackMinutes = parseLookback(servletContext);
        }

        @Override
        public void run() {
            LOGGER.info("LazadaSyncScheduler: Sync cycle started.");
            int totalSynced = 0;
            int totalUpdated = 0;
            try {
                List<Channel> channels = channelDAO.findAll();
                for (Channel channel : channels) {
                    if (!channel.isActive() || !"Lazada".equalsIgnoreCase(channel.getPlatform())) {
                        continue;
                    }
                    if (channel.getAccessToken() == null || channel.getAccessToken().trim().isEmpty()) {
                        LOGGER.fine("LazadaSyncScheduler: Channel '" + channel.getChannelName()
                                + "' has no access token, skipping.");
                        continue;
                    }
                    try {
                        SyncOutcome outcome = syncChannel(channel);
                        totalSynced += outcome.newOrders;
                        totalUpdated += outcome.updatedOrders;
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, "LazadaSyncScheduler: Failed to sync channel '"
                                + channel.getChannelName() + "': " + e.getMessage(), e);
                        logSync(channel.getChannelId(), "ORDER_SYNC", "FAILED", null, null, e.getMessage());
                    }
                }
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "LazadaSyncScheduler: Sync cycle failed: " + e.getMessage(), e);
            }
            LOGGER.info("LazadaSyncScheduler: Sync cycle completed. new=" + totalSynced
                    + " updated=" + totalUpdated);
        }

        private SyncOutcome syncChannel(Channel channel) {
            // Inject channel into the service (service holds state for the cycle)
            syncService.setChannel(channel);

            long lastSyncAtMs = readLastSyncAt(channel.getChannelId());
            long sinceMs = lastSyncAtMs > 0
                    ? lastSyncAtMs
                    : System.currentTimeMillis() - (long) lookbackMinutes * 60_000L;
            long sinceSeconds = sinceMs / 1000L;

            // 1. List orders (status=pending, updated after our last sync)
            String listJson = orderService.getOrders(channel, "pending", null, sinceMs);
            logSync(channel.getChannelId(), "ORDER_LIST", "SUCCESS", null, listJson, null);

            JsonNode root;
            try {
                root = com.wms.util.JsonUtil.getMapper().readTree(listJson);
            } catch (Exception e) {
                throw new RuntimeException("Failed to parse Lazada /orders/get response", e);
            }

            JsonNode ordersArray = root.path("data").path("orders");
            if (!ordersArray.isArray() || ordersArray.isEmpty()) {
                updateLastSyncAt(channel.getChannelId());
                return new SyncOutcome(0, 0);
            }

            int newCount = 0;
            int updatedCount = 0;
            try (Connection conn = DBConnection.getConnection()) {
                conn.setAutoCommit(false);
                for (JsonNode orderNode : ordersArray) {
                    String orderCode = orderNode.path("order_id").asText();
                    String detailJson = null;
                    try {
                        detailJson = orderService.getOrderDetail(channel, orderCode);
                    } catch (Exception e) {
                        LOGGER.log(Level.FINE, "getOrderDetail failed for " + orderCode, e);
                    }
                    var result = syncService.saveOneOrder(conn, orderNode, detailJson);
                    if (result == LazadaOrderSyncService.SyncResult.NEW) newCount++;
                    else if (result == LazadaOrderSyncService.SyncResult.UPDATED) updatedCount++;
                }
                conn.commit();
            } catch (SQLException sqle) {
                throw new RuntimeException("DB error during sync for channel " + channel.getChannelId(), sqle);
            }

            updateLastSyncAt(channel.getChannelId());
            LOGGER.info("LazadaSyncScheduler: Channel '" + channel.getChannelName()
                    + "' synced new=" + newCount + " updated=" + updatedCount);
            return new SyncOutcome(newCount, updatedCount);
        }

        // ── Scheduler-only helpers (channel sync tracking & logging) ─

        private long readLastSyncAt(int channelId) {
            String sql = "SELECT last_order_sync_at FROM channels WHERE channel_id = ?";
            try (Connection conn = DBConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, channelId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        Timestamp ts = rs.getTimestamp(1);
                        return ts == null ? 0L : ts.getTime();
                    }
                }
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "readLastSyncAt failed channelId=" + channelId, e);
            }
            return 0L;
        }

        private void updateLastSyncAt(int channelId) {
            String sql = "UPDATE channels SET last_order_sync_at = CURRENT_TIMESTAMP WHERE channel_id = ?";
            try (Connection conn = DBConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, channelId);
                ps.executeUpdate();
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "updateLastSyncAt failed channelId=" + channelId, e);
            }
        }

        private void logSync(int channelId, String syncType, String status,
                             String requestData, String responseData, String errorMsg) {
            String sql = "INSERT INTO lazada_sync_log "
                    + "(channel_id, sync_type, status, request_data, response_data, error_msg) "
                    + "VALUES (?, ?, ?, ?, ?, ?)";
            try (Connection conn = DBConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, channelId);
                ps.setString(2, syncType);
                ps.setString(3, status);
                ps.setString(4, trimForLog(requestData));
                ps.setString(5, trimForLog(responseData));
                ps.setString(6, errorMsg);
                ps.executeUpdate();
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Failed to log sync", e);
            }
        }

        private String trimForLog(String s) {
            if (s == null) return null;
            return s.length() > 4000 ? s.substring(0, 4000) + "...[truncated]" : s;
        }
    }

    // ── Internal value types ────────────────────────────────────

    private static class SyncOutcome {
        final int newOrders;
        final int updatedOrders;
        SyncOutcome(int n, int u) { this.newOrders = n; this.updatedOrders = u; }
    }
}
