package com.wms.controller.api;

import com.wms.dao.ChannelDAO;
import com.wms.dao.ChannelProductDAO;
import com.wms.dao.InventoryDAO;
import com.wms.model.Channel;
import com.wms.model.ChannelProduct;
import com.wms.service.channel.ChannelGateway;
import com.wms.service.channel.ChannelRegistry;
import com.wms.service.channel.ChannelSyncAudit;
import com.wms.service.lazada.LazadaProductService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manual trigger for Lazada inventory push.
 * GET /api/lazada/stock-sync
 *
 * This servlet performs two steps:
 *   1. Auto-pull Lazada catalog to create/update channel_products mappings
 *      (catches products listed directly on Lazada Seller Center that aren't
 *      yet in our system, e.g. KMAT-KRAM-001).
 *   2. Push current WMS inventory to Lazada for all ACTIVE mapped products.
 *
 * Run with: curl http://app.example.com/api/lazada/stock-sync
 */
public class LazadaStockSyncServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(LazadaStockSyncServlet.class.getName());
    private final InventoryDAO invDAO = new InventoryDAO();
    private final ChannelProductDAO cpDAO = new ChannelProductDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        StringBuilder output = new StringBuilder();
        output.append("Lazada Stock Sync Results\n");
        output.append("========================\n\n");

        ChannelGateway gateway = ChannelRegistry.get("Lazada");
        if (gateway == null) {
            output.append("ERROR: No Lazada gateway registered\n");
            resp.getWriter().write(output.toString());
            return;
        }

        List<Channel> channels = new ChannelDAO().findAll();

        String itemIdParam = req.getParameter("itemId");
        if (itemIdParam != null && !itemIdParam.isBlank()) {
            // Single-item mode: fetch by Lazada item_id
            output.append("Single Item Mode: itemId=").append(itemIdParam).append("\n\n");
            for (Channel ch : channels) {
                if (!ch.isActive() || !"Lazada".equalsIgnoreCase(ch.getPlatform())) continue;
                if (ch.getAccessToken() == null || ch.getAccessToken().isEmpty()) continue;

                output.append("Channel: ").append(ch.getChannelName()).append("\n");
                try {
                    LazadaProductService productService = new LazadaProductService();
                    LazadaProductService.PullResult pr = productService.pullProductByItemId(ch, itemIdParam.trim());
                    LOGGER.info("pullProductByItemId result: ok=" + pr.ok + " upserted=" + pr.upserted + " unmapped=" + pr.unmapped + " error=" + pr.error);
                    if (pr.ok && pr.upserted > 0) {
                        output.append("  SUCCESS: product mapped and upserted\n");
                    } else if (pr.unmapped > 0) {
                        output.append("  UNMAPPED: Lazada product found but no matching WMS SKU\n");
                    } else {
                        output.append("  FAILED: ").append(pr.error != null ? pr.error : "upserted=" + pr.upserted + " ok=" + pr.ok).append("\n");
                    }
                } catch (Exception e) {
                    output.append("  ERROR: ").append(e.getMessage()).append("\n");
                }
                break; // only one channel needed for item_id lookup
            }
            resp.setContentType("text/plain; charset=UTF-8");
            resp.getWriter().write(output.toString());
            return;
        }

        // Full sync mode
        int totalPushed = 0, totalFailed = 0;
        for (Channel ch : channels) {
            if (!ch.isActive() || !"Lazada".equalsIgnoreCase(ch.getPlatform())) continue;
            if (ch.getAccessToken() == null || ch.getAccessToken().isEmpty()) continue;

            output.append("Channel: ").append(ch.getChannelName()).append("\n\n");

            // ── Step 1: Pull Lazada catalog to create missing channel_products mappings ──
            output.append("  [1/2] Pulling Lazada catalog...\n");
            int pullPulled = 0, pullMapped = 0, pullUnmapped = 0;
            try {
                LazadaProductService productService = new LazadaProductService();
                LazadaProductService.PullResult pr = productService.pullProducts(ch);
                pullPulled = pr.pulled;
                pullMapped = pr.upserted;
                pullUnmapped = pr.unmapped;
                output.append("    Pulled=").append(pr.pulled)
                      .append("  Mapped=").append(pr.upserted)
                      .append("  Unmapped=").append(pr.unmapped)
                      .append("  OK=").append(pr.ok).append("\n");
                if (!pr.ok && pr.error != null) {
                    output.append("    Pull warning: ").append(pr.error).append("\n");
                }
            } catch (Exception e) {
                output.append("    Pull FAILED: ").append(e.getMessage()).append("\n");
                LOGGER.log(Level.WARNING, "LazadaStockSync: pullProducts failed for channel "
                        + ch.getChannelName(), e);
            }

            // ── Step 2: Push inventory for all ACTIVE mapped products ──
            output.append("  [2/2] Pushing inventory...\n");
            List<ChannelProduct> products = cpDAO.findByChannel(ch.getChannelId());
            if (products.isEmpty()) {
                output.append("    No products mapped yet.\n");
                output.append("    -> If your Lazada products are not in WMS, they won't receive stock updates.\n");
                output.append("    -> Manually create mappings via Sales > SKU Mapping or pull the catalog.\n\n");
                continue;
            }

            List<StockItem> items = new ArrayList<>();
            for (ChannelProduct cp : products) {
                if (!"ACTIVE".equalsIgnoreCase(cp.getStatus())) continue;
                String sellerSku = cp.getChannelSkuCode();
                if (sellerSku == null || sellerSku.isEmpty()) continue;
                String skuId = cp.getLazadaSkuId();

                int available = invDAO.getTotalAvailableStock(cp.getProductId());
                BigDecimal buffer = BigDecimal.valueOf(ch.getBufferStock());
                BigDecimal pushQty = BigDecimal.valueOf(available).subtract(buffer);
                if (pushQty.signum() < 0) pushQty = BigDecimal.ZERO;
                int qty = pushQty.setScale(0, RoundingMode.FLOOR).intValue();

                items.add(new StockItem(cp.getProductId(), sellerSku, skuId, available, qty, ch.getBufferStock()));
                output.append("    SKU=").append(sellerSku)
                      .append(" skuId=").append(skuId == null ? "(missing)" : skuId)
                      .append(" available=").append(available)
                      .append(" buffer=").append(ch.getBufferStock())
                      .append(" push=").append(qty).append("\n");
            }

            if (items.isEmpty()) {
                output.append("    No ACTIVE items to push\n\n");
                continue;
            }

            // Batch push
            List<ChannelGateway.StockUpdate> batch = items.stream()
                    .map(it -> new ChannelGateway.StockUpdate(it.sellerSku, it.skuId, it.qty))
                    .toList();

            try {
                String respStr = gateway.updateProductStockBatch(ch, batch);
                ChannelSyncAudit.logSuccess(ch.getChannelId(), "MANUAL_STOCK_SYNC",
                        "size=" + batch.size(), 200, "OK", respStr, 0);
                output.append("    Pushed ").append(batch.size()).append(" items - SUCCESS\n\n");
                totalPushed += batch.size();
            } catch (Exception e) {
                ChannelSyncAudit.logFailure(ch.getChannelId(), "MANUAL_STOCK_SYNC",
                        "size=" + batch.size(), 500, null, e.getMessage());
                output.append("    FAILED: ").append(e.getMessage()).append("\n\n");
                totalFailed += items.size();
            }
        }

        output.append("========================\n");
        output.append("Total pushed: ").append(totalPushed).append("\n");
        output.append("Total failed: ").append(totalFailed).append("\n");

        LOGGER.info("LazadaStockSync: pushed=" + totalPushed + " failed=" + totalFailed);
        resp.setContentType("text/plain; charset=UTF-8");
        resp.getWriter().write(output.toString());
    }

    private record StockItem(int productId, String sellerSku, String skuId, int available, int qty, double bufferStock) {}
}
