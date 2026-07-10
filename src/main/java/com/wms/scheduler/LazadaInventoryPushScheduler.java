package com.wms.scheduler;

import com.wms.dao.ChannelProductDAO;
import com.wms.dao.InventoryDAO;
import com.wms.model.Channel;
import com.wms.model.ChannelProduct;
import com.wms.service.channel.ChannelGateway;
import com.wms.service.channel.ChannelRegistry;
import com.wms.service.channel.ChannelSyncAudit;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * LazadaInventoryPushScheduler — BR-02 background push.
 *
 * <p>For every active Lazada channel:
 * <ol>
 *   <li>Reads all channel_products rows (ACTIVE status).</li>
 *   <li>For each, fetches the matching inventory row from any warehouse
 *       and computes Push_Qty = max(qty_available - buffer_stock, 0).</li>
 *   <li>Calls Lazada /product/stock/sellable/update (or batch if we add
 *       a LazadaChannelGateway.batchPush later).</li>
 *   <li>Logs every push to lazada_stock_push_log so we can audit why
 *       we sent 0 (overselling protection).</li>
 * </ol>
 *
 * Buffer stock comes from {@code channels.buffer_stock}, which Sales
 * configures from the channel admin page.
 */
@WebListener
public class LazadaInventoryPushScheduler implements ServletContextListener {

    private static final Logger LOGGER = Logger.getLogger(LazadaInventoryPushScheduler.class.getName());

    public static final String CTX_PARAM_ENABLED  = "lazada.stock.push.enabled";
    public static final String CTX_PARAM_INTERVAL = "lazada.stock.push.interval.minutes";
    public static final String CTX_PARAM_BATCH    = "lazada.stock.push.batch.size";

    private Timer timer;
    private ServletContext ctx;

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        this.ctx = sce.getServletContext();
        String enabled = ctx.getInitParameter(CTX_PARAM_ENABLED);
        if (!"true".equalsIgnoreCase(enabled)) {
            LOGGER.info("LazadaInventoryPushScheduler: disabled (set "
                    + CTX_PARAM_ENABLED + "=true to enable).");
            return;
        }
        int minutes = parseInterval(ctx.getInitParameter(CTX_PARAM_INTERVAL));
        int batchSize = parseBatch(ctx.getInitParameter(CTX_PARAM_BATCH));
        LOGGER.info("LazadaInventoryPushScheduler: enabled, interval=" + minutes
                + " min, batch=" + batchSize);
        timer = new Timer("LazadaStockPushTimer", true);
        timer.scheduleAtFixedRate(new PushTask(batchSize), 60_000L, minutes * 60_000L);
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
    private int parseBatch(String v) {
        try { int n = Integer.parseInt(v); return n > 0 ? Math.min(n, 50) : 20; }
        catch (Exception e) { return 20; }
    }

    // ── Push task ─────────────────────────────────────────────

    private class PushTask extends TimerTask {

        private final ChannelGateway gateway = ChannelRegistry.get("Lazada");
        private final ChannelProductDAO cpDAO = new ChannelProductDAO();
        private final InventoryDAO invDAO = new InventoryDAO();
        private final int batchSize;

        PushTask(int batchSize) { this.batchSize = batchSize; }

        @Override
        public void run() {
            if (gateway == null) {
                LOGGER.warning("LazadaInventoryPushScheduler: no gateway registered");
                return;
            }
            List<Channel> channels = new com.wms.dao.ChannelDAO().findAll();
            for (Channel ch : channels) {
                if (!ch.isActive() || !"Lazada".equalsIgnoreCase(ch.getPlatform())) continue;
                if (ch.getAccessToken() == null || ch.getAccessToken().isEmpty()) continue;
                try {
                    pushForChannel(ch);
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING,
                            "LazadaInventoryPushScheduler: channel "
                                    + ch.getChannelName() + " failed", e);
                }
            }
        }

        private void pushForChannel(Channel ch) {
            List<ChannelProduct> products = cpDAO.findByChannel(ch.getChannelId());
            if (products.isEmpty()) {
                LOGGER.fine("LazadaInventoryPushScheduler: no products for channel "
                        + ch.getChannelName());
                return;
            }

            // Step 1: compute pushQty for every active SKU
            List<SkuPushItem> todo = new ArrayList<>();
            for (ChannelProduct cp : products) {
                if (!"ACTIVE".equalsIgnoreCase(cp.getStatus())) continue;
                String sellerSku = cp.getChannelSkuCode();
                if (sellerSku == null || sellerSku.isEmpty()) continue;

                int available = invDAO.getTotalAvailableStock(cp.getProductId());
                BigDecimal buffer = BigDecimal.valueOf(ch.getBufferStock());
                BigDecimal pushQty = BigDecimal.valueOf(available).subtract(buffer);
                if (pushQty.signum() < 0) pushQty = BigDecimal.ZERO;
                int intQty = pushQty.setScale(0, RoundingMode.FLOOR).intValue();

                todo.add(new SkuPushItem(cp.getProductId(), sellerSku, cp.getLazadaSkuId(), available, intQty,
                        ch.getBufferStock(), pushQty));
            }
            if (todo.isEmpty()) {
                LOGGER.fine("LazadaInventoryPushScheduler: no active SKUs to push for channel "
                        + ch.getChannelName());
                return;
            }

            // Step 2: send in batches of batchSize (max 50 per Lazada spec)
            int pushed = 0, skipped = 0, failed = 0;
            for (int i = 0; i < todo.size(); i += batchSize) {
                int end = Math.min(i + batchSize, todo.size());
                List<SkuPushItem> batch = todo.subList(i, end);

                List<ChannelGateway.StockUpdate> batchUpdates = batch.stream()
                        .map(it -> new ChannelGateway.StockUpdate(it.sellerSku, it.skuId, it.intQty))
                        .toList();

                long t0 = System.currentTimeMillis();
                try {
                    String resp = gateway.updateProductStockBatch(ch, batchUpdates);
                    long dt = System.currentTimeMillis() - t0;
                    ChannelSyncAudit.logSuccess(ch.getChannelId(), "STOCK_PUSH_BATCH",
                            "batch(" + i + "-" + end + ")", 200,
                            "size=" + batch.size(), resp, dt);
                    // Log each item in batch as individual success
                    for (SkuPushItem it : batch) {
                        logPushOutcome(ch.getChannelId(), it.productId, it.sellerSku,
                                BigDecimal.valueOf(it.available), BigDecimal.valueOf(it.available),
                                BigDecimal.valueOf(ch.getBufferStock()), BigDecimal.ZERO,
                                it.pushQty, "SUCCESS", null);
                        var mapping = cpDAO.findByChannelSku(ch.getChannelId(), it.sellerSku);
                        if (mapping != null) {
                            cpDAO.recordStockPush(mapping.getId(), it.pushQty);
                        } else {
                            LOGGER.fine("recordStockPush: no channel_product mapping for sellerSku="
                                    + it.sellerSku + " — skipping push record");
                        }
                        pushed++;
                    }
                } catch (Exception e) {
                    ChannelSyncAudit.logFailure(ch.getChannelId(), "STOCK_PUSH_BATCH",
                            "batch(" + i + "-" + end + ")", 500,
                            "size=" + batch.size(), e.getMessage());
                    // Log every item in failed batch
                    for (SkuPushItem it : batch) {
                        logPushOutcome(ch.getChannelId(), it.productId, it.sellerSku,
                                BigDecimal.valueOf(it.available), BigDecimal.valueOf(it.available),
                                BigDecimal.valueOf(ch.getBufferStock()), BigDecimal.ZERO,
                                it.pushQty, "FAILED", e.getMessage());
                        failed++;
                    }
                }
            }
            LOGGER.info("LazadaInventoryPushScheduler: channel " + ch.getChannelName()
                    + " pushed=" + pushed + " skipped=" + skipped + " failed=" + failed);
        }

        /** Lightweight holder for computed push data, avoids allocating ChannelProduct per item. */
        private record SkuPushItem(
                int productId,
                String sellerSku,
                String skuId,
                int available,
                int intQty,
                double bufferStock,
                BigDecimal pushQty
        ) {}

        private void logPushOutcome(int channelId, int productId, String sellerSku,
                                    BigDecimal qtyOnHand, BigDecimal qtyAvailable,
                                    BigDecimal holding, BigDecimal buffer,
                                    BigDecimal pushQty, String status, String errorMessage) {
            String sql = "INSERT INTO lazada_stock_push_log "
                    + "(channel_id, product_id, seller_sku, qty_on_hand, qty_available, "
                    + " holding, buffer_stock, push_qty, status, error_message) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (Connection conn = com.wms.util.DBConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, channelId);
                ps.setInt(2, productId);
                ps.setString(3, sellerSku);
                ps.setBigDecimal(4, qtyOnHand);
                ps.setBigDecimal(5, qtyAvailable);
                ps.setBigDecimal(6, holding);
                ps.setBigDecimal(7, buffer);
                ps.setBigDecimal(8, pushQty);
                ps.setString(9, status);
                ps.setString(10, errorMessage);
                ps.executeUpdate();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING,
                        "LazadaInventoryPushScheduler: logPushOutcome failed", e);
            }
        }
    }
}
