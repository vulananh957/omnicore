package com.wms.scheduler;

import com.wms.dao.ChannelDAO;
import com.wms.model.Channel;
import com.wms.service.lazada.LazadaOrderSyncService;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * LazadaOrderSyncScheduler — Background timer that periodically syncs Lazada orders
 * from the Lazada API into the WMS database.
 *
 * <p>For each active Lazada channel, the scheduler:</p>
 * <ol>
 *   <li>Fetches new orders (status = pending, topack, toship)</li>
 *   <li>Upserts them into lazada_orders via {@link LazadaOrderSyncService}</li>
 *   <li>Fetches line items for new orders</li>
 *   <li>Updates last_order_sync_at on the channel</li>
 * </ol>
 *
 * <p>Configuration (web.xml context-params):</p>
 * <ul>
 *   <li>{@code lazada.orderSync.enabled} — "true" (default) or "false"</li>
 *   <li>{@code lazada.orderSync.interval.minutes} — check interval (default: 15)</li>
 *   <li>{@code lazada.orderSync.limit} — max orders per status per channel per run (default: 100)</li>
 * </ul>
 */
@WebListener
public class LazadaOrderSyncScheduler implements ServletContextListener {

    private static final Logger LOGGER = Logger.getLogger(LazadaOrderSyncScheduler.class.getName());

    private Timer timer;
    private ServletContext servletContext;

    public static final String CTX_ENABLED      = "lazada.orderSync.enabled";
    public static final String CTX_INTERVAL_MIN  = "lazada.orderSync.interval.minutes";
    public static final String CTX_LIMIT        = "lazada.orderSync.limit";

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        servletContext = sce.getServletContext();

        String enabled = servletContext.getInitParameter(CTX_ENABLED);
        if (!"true".equalsIgnoreCase(enabled)) {
            LOGGER.info("LazadaOrderSyncScheduler: Disabled by configuration. Skipping startup.");
            return;
        }

        int intervalMinutes = parseInt(servletContext.getInitParameter(CTX_INTERVAL_MIN), 15);
        int limit          = parseInt(servletContext.getInitParameter(CTX_LIMIT), 100);

        LOGGER.info("LazadaOrderSyncScheduler: Starting. "
                + "interval=" + intervalMinutes + "min, limit=" + limit);

        timer = new Timer("LazadaOrderSyncTimer", true);
        // First run after 60 seconds, then repeat at the configured interval
        timer.scheduleAtFixedRate(
                new SyncTask(limit),
                60_000L,
                intervalMinutes * 60_000L);
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        if (timer != null) {
            LOGGER.info("LazadaOrderSyncScheduler: Cancelling timer...");
            timer.cancel();
            timer = null;
        }
    }

    private static int parseInt(String prop, int defaultVal) {
        try {
            int val = Integer.parseInt(prop);
            return val > 0 ? val : defaultVal;
        } catch (Exception e) {
            return defaultVal;
        }
    }

    // ───────────────────────────────────────────────────────────────────────────
    // Sync task
    // ───────────────────────────────────────────────────────────────────────────

    private static class SyncTask extends TimerTask {

        private final ChannelDAO channelDAO = new ChannelDAO();
        private final LazadaOrderSyncService syncService = new LazadaOrderSyncService();
        // TODO: LazadaOrderReconciliationService not implemented yet
        // private final LazadaOrderReconciliationService reconciliationService = new LazadaOrderReconciliationService();
        private final int limit;

        SyncTask(int limit) {
            this.limit = limit;
        }

        @Override
        public void run() {
            LOGGER.info("LazadaOrderSyncScheduler: Sync cycle started.");

            int totalNew = 0;
            int totalUpdated = 0;
            int totalReconciled = 0;
            int totalErrors = 0;

            try {
                // Find all active Lazada channels
                List<Channel> channels = channelDAO.findAll();
                for (Channel channel : channels) {
                    if (!channel.isActive() || !"Lazada".equalsIgnoreCase(channel.getPlatform())) {
                        continue;
                    }

                    try {
                        // 1. Fetch and upsert new orders
                        int newOrders = syncService.syncNewOrdersFromApi(channel, limit);
                        totalNew += newOrders;

                        // 2. Reconciliation: sync all order statuses from Lazada (fixes stale WMS data)
                        // TODO: LazadaOrderReconciliationService not implemented yet
                        // int reconciled = reconciliationService.reconcileChannel(channel.getChannelId());
                        // totalReconciled += reconciled;

                        // 3. Fetch items for orders that have no items yet (lazy-load after upsert)
                        // This ensures Sales can approve immediately without waiting for a separate sync run.
                        fetchMissingItemsForNewOrders(channel, syncService, limit);

                        LOGGER.info("LazadaOrderSyncScheduler: channel="
                                + channel.getChannelName() + " new=" + newOrders + " reconciled=0 (disabled)");

                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING,
                                "LazadaOrderSyncScheduler: Sync failed for channel '"
                                        + channel.getChannelName() + "': " + e.getMessage(), e);
                        totalErrors++;
                    }
                }

            } catch (Exception e) {
                LOGGER.log(Level.SEVERE,
                        "LazadaOrderSyncScheduler: Sync cycle failed: " + e.getMessage(), e);
                totalErrors++;
            }

            LOGGER.info("LazadaOrderSyncScheduler: cycle completed. "
                    + "newOrders=" + totalNew + " updated=" + totalUpdated + " errors=" + totalErrors);
        }

        /**
         * Finds orders that were synced but have no items in lazada_order_items,
         * then batch-fetches their items from Lazada API (up to 50/order_ids per call).
         * This prevents the "order has no items" error when Sales tries to approve.
         */
        private void fetchMissingItemsForNewOrders(Channel channel,
                                                   LazadaOrderSyncService syncService, int limit) {
            // Find NEW orders without items
            String sql = """
                SELECT lo.lazada_order_id_str
                FROM lazada_orders lo
                LEFT JOIN lazada_order_items loi ON lo.lazada_order_id_str = loi.lazada_order_id_str
                WHERE lo.channel_id = ?
                  AND lo.wms_status = 'NEW'
                  AND lo.lazada_created_at > DATE_SUB(NOW(), INTERVAL 7 DAY)
                  AND loi.item_id IS NULL
                ORDER BY lo.lazada_created_at ASC
                LIMIT ?
                """;
            List<String> orderIds = new ArrayList<>();
            try (java.sql.Connection conn = com.wms.util.DBConnection.getConnection();
                 java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, channel.getChannelId());
                ps.setInt(2, limit);
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) orderIds.add(rs.getString(1));
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING,
                        "fetchMissingItemsForNewOrders: query failed for channel " + channel.getChannelId(), e);
                return;
            }

            if (orderIds.isEmpty()) return;

            LOGGER.info("LazadaOrderSyncScheduler: fetching items for " + orderIds.size()
                    + " orders without items on channel " + channel.getChannelId());
            int fetched = syncService.fetchOrderItemsFromApi(channel, orderIds);
            LOGGER.info("LazadaOrderSyncScheduler: fetched " + fetched + " items");
        }
    }
}
