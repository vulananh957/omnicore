package com.wms.service.marketplace;

import com.wms.dao.ChannelDAO;
import com.wms.dao.InventoryDAO;
import com.wms.dao.SkuMappingDAO;
import com.wms.model.Channel;
import com.wms.model.SkuMapping;
import com.wms.model.StockPushItem;
import com.wms.service.channel.LazadaChannelGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;

/**
 * MarketplaceSyncService — Orchestrates real-time stock push to Lazada
 * immediately after an inbound receipt is confirmed.
 *
 * <p>Responsibilities:
 * <ol>
 *   <li>Resolve which SKUs need to be pushed (active Lazada mappings).</li>
 *   <li>Compute Push_Qty = SUM(qty_available all warehouses) - bufferStock
 *       per SKU (BR-02 rule, UC-B2C10).</li>
 *   <li>Batch items into groups of 20 (Lazada recommended limit).</li>
 *   <li>Call LazadaChannelGateway.updateSellableQuantity() per batch.</li>
 *   <li>Parse response and log every push to {@code lazada_stock_push_log}.</li>
 *   <li>Handle rate-limit (E901) with exponential-backoff retry (max 3 attempts).</li>
 * </ol>
 *
 * <p>This service runs asynchronously (called from a background thread) so
 * it does not block the inbound-receive HTTP response. Target SLA: &lt;60s total.
 */
public class MarketplaceSyncService {

    private static final Logger log = LoggerFactory.getLogger(MarketplaceSyncService.class);

    private static final int BATCH_SIZE = 20;
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_BASE_DELAY_MS = 1_000;

    private final SkuMappingDAO skuMappingDAO = new SkuMappingDAO();
    private final InventoryDAO inventoryDAO = new InventoryDAO();
    private final ChannelDAO channelDAO = new ChannelDAO();
    private final LazadaChannelGateway lazadaGateway = new LazadaChannelGateway();

    /**
     * Triggered by {@link com.wms.service.warehouse.InboundService#receiveGoods}
     * after the inventory update commits. Runs on a background thread.
     *
     * @param productIds   List of internal product IDs that were just received.
     * @param inboundCode  Inbound receipt code for audit logging.
     */
    public void triggerStockSyncAfterInbound(List<Integer> productIds, String inboundCode) {
        long start = System.currentTimeMillis();
        log.info("MarketplaceSyncService: Starting stock sync for inbound={} productIds={}",
                inboundCode, productIds);

        List<Channel> lazadaChannels = getActiveLazadaChannels();
        if (lazadaChannels.isEmpty()) {
            log.info("MarketplaceSyncService: No active Lazada channel configured, skipping sync.");
            return;
        }

        for (Channel channel : lazadaChannels) {
            try {
                pushForChannel(channel, productIds, inboundCode);
            } catch (Exception e) {
                log.warn("MarketplaceSyncService: Error pushing to channel {}: {}",
                        channel.getChannelId(), e.getMessage());
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info("MarketplaceSyncService: Completed stock sync for inbound={} in {}ms",
                inboundCode, elapsed);
    }

    private void pushForChannel(Channel channel, List<Integer> productIds, String inboundCode) {
        List<SkuMapping> mappings =
                skuMappingDAO.findActiveMappingsByProductIds(productIds, channel.getChannelId());
        if (mappings.isEmpty()) {
            log.info("MarketplaceSyncService: No active Lazada mappings for channel={} products={}",
                    channel.getChannelId(), productIds);
            return;
        }

        log.info("MarketplaceSyncService: Resolved {} Lazada mappings for channel={}",
                mappings.size(), channel.getChannelId());

        List<StockPushItem> pushItems = new ArrayList<>();
        for (SkuMapping mapping : mappings) {
            int productId = mapping.getSkuId();

            BigDecimal sumAvailable = inventoryDAO.sumAvailableByProductId(productId);
            BigDecimal bufferStock = BigDecimal.valueOf(channel.getBufferStock());

            BigDecimal pushQty = sumAvailable.subtract(bufferStock);
            if (pushQty.compareTo(BigDecimal.ZERO) < 0) {
                pushQty = BigDecimal.ZERO;
            }

            StockPushItem item = new StockPushItem(
                    productId,
                    resolveSellerSku(mapping),
                    mapping.getLazadaSkuId(),
                    mapping.getChannelItemId(),
                    pushQty,
                    sumAvailable,
                    bufferStock,
                    channel.getChannelId(),
                    inboundCode
            );
            pushItems.add(item);

            log.debug("MarketplaceSyncService: productId={} sellerSku={} sumAvail={} buffer={} pushQty={}",
                    productId, item.getSellerSku(), sumAvailable, bufferStock, pushQty);
        }

        List<List<StockPushItem>> batches = partition(pushItems, BATCH_SIZE);
        log.info("MarketplaceSyncService: Pushing {} SKUs in {} batches to channel={}",
                pushItems.size(), batches.size(), channel.getChannelId());

        for (int batchIdx = 0; batchIdx < batches.size(); batchIdx++) {
            List<StockPushItem> batch = batches.get(batchIdx);
            boolean ok = pushBatchWithRetry(channel, batch, batchIdx + 1, batches.size());
            if (!ok) {
                log.warn("MarketplaceSyncService: Batch {}/{} failed after retries for channel={}",
                        batchIdx + 1, batches.size(), channel.getChannelId());
            }
        }
    }

    private boolean pushBatchWithRetry(Channel channel, List<StockPushItem> batch,
                                        int batchNumber, int totalBatches) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                String response = lazadaGateway.updateSellableQuantity(channel, batch);
                boolean success = parseAndLogResponse(channel, batch, response);

                if (success) {
                    log.info("MarketplaceSyncService: Batch {}/{} succeeded on attempt {} for channel={}",
                            batchNumber, totalBatches, attempt, channel.getChannelId());
                    return true;
                }

                if (isRetryable(response)) {
                    log.warn("MarketplaceSyncService: Batch {}/{} retryable error on attempt {} (channel={}): {}",
                            batchNumber, totalBatches, attempt, channel.getChannelId(),
                            truncate(response, 200));
                    if (attempt < MAX_RETRIES) {
                        sleep(RETRY_BASE_DELAY_MS * attempt);
                        continue;
                    }
                } else {
                    log.warn("MarketplaceSyncService: Batch {}/{} non-retryable error (channel={}): {}",
                            batchNumber, totalBatches, channel.getChannelId(),
                            truncate(response, 200));
                    return false;
                }
            } catch (Exception e) {
                log.warn("MarketplaceSyncService: Batch {}/{} threw on attempt {} (channel={}): {}",
                        batchNumber, totalBatches, attempt, channel.getChannelId(), e.getMessage());
                if (attempt < MAX_RETRIES) {
                    sleep(RETRY_BASE_DELAY_MS * attempt);
                }
            }
        }
        return false;
    }

    private boolean parseAndLogResponse(Channel channel, List<StockPushItem> batch, String response) {
        boolean success = false;
        try {
            int codeStart = response.indexOf("\"code\":\"");
            if (codeStart >= 0) {
                String codeVal = response.substring(codeStart + 8,
                        response.indexOf("\"", codeStart + 8));
                success = "0".equals(codeVal);
            }
        } catch (Exception e) {
            log.warn("Failed to parse Lazada response code: {}", e.getMessage());
        }

        for (StockPushItem item : batch) {
            String skuErrorCode = null;
            String skuErrorMsg = null;
            if (!success) {
                skuErrorCode = extractLazadaErrorCode(response, item.getSellerSku());
                skuErrorMsg = extractLazadaErrorMessage(response, item.getSellerSku());
            }
            logPush(channel, item, success ? "SUCCESS" : "FAILED",
                    success ? null : skuErrorCode, skuErrorMsg);
        }

        return success;
    }

    private String extractLazadaErrorCode(String response, String sellerSku) {
        int idx = response.indexOf(sellerSku);
        if (idx < 0) return "UNKNOWN";
        int codePos = response.lastIndexOf("\"code\"", idx);
        if (codePos < 0) return "UNKNOWN";
        int colon = response.indexOf(":", codePos);
        int comma = response.indexOf(",", colon);
        int end = comma > colon ? comma : response.indexOf("}", colon);
        return response.substring(colon + 1, end).trim().replaceAll("[^0-9]", "");
    }

    private String extractLazadaErrorMessage(String response, String sellerSku) {
        int idx = response.indexOf(sellerSku);
        if (idx < 0) return null;
        int msgPos = response.lastIndexOf("\"message\"", idx);
        if (msgPos < 0) return null;
        int colon = response.indexOf(":", msgPos);
        int comma = response.indexOf(",", colon);
        int end = comma > colon ? comma : response.indexOf("}", colon);
        return response.substring(colon + 1, end).trim().replaceAll("^\"|\"$", "");
    }

    private void logPush(Channel channel, StockPushItem item, String status,
                         String errorCode, String errorMessage) {
        String sql = "INSERT INTO lazada_stock_push_log "
                   + "(channel_id, product_id, seller_sku, qty_available, buffer_stock, push_qty, "
                   + "status, error_code, error_message, inbound_receipt_code, pushed_at, created_at) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())";
        try (Connection conn = com.wms.util.DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, channel.getChannelId());
            ps.setInt(2, item.getProductId());
            ps.setString(3, item.getSellerSku());
            ps.setBigDecimal(4, item.getSystemAvailable());
            ps.setBigDecimal(5, item.getBufferStock());
            ps.setBigDecimal(6, item.getPushQty());
            ps.setString(7, status);
            ps.setString(8, errorCode);
            ps.setString(9, errorMessage);
            ps.setString(10, item.getInboundCode());
            ps.executeUpdate();

            if ("SUCCESS".equals(status)) {
                String cpSql = "UPDATE channel_products SET channel_stock = ?, last_push_qty = ?, last_push_at = NOW(), last_error_code = NULL, last_error_message = NULL, updated_at = NOW() WHERE channel_id = ? AND product_id = ?";
                try (PreparedStatement cpPs = conn.prepareStatement(cpSql)) {
                    cpPs.setBigDecimal(1, item.getPushQty());
                    cpPs.setBigDecimal(2, item.getPushQty());
                    cpPs.setInt(3, channel.getChannelId());
                    cpPs.setInt(4, item.getProductId());
                    cpPs.executeUpdate();
                }
            } else {
                String cpSql = "UPDATE channel_products SET last_error_code = ?, last_error_message = ?, updated_at = NOW() WHERE channel_id = ? AND product_id = ?";
                try (PreparedStatement cpPs = conn.prepareStatement(cpSql)) {
                    cpPs.setString(1, errorCode);
                    cpPs.setString(2, errorMessage);
                    cpPs.setInt(3, channel.getChannelId());
                    cpPs.setInt(4, item.getProductId());
                    cpPs.executeUpdate();
                }
            }
        } catch (Exception e) {
            log.warn("MarketplaceSyncService: Failed to log push for sellerSku={}: {}",
                    item.getSellerSku(), e.getMessage());
        }
    }

    private List<Channel> getActiveLazadaChannels() {
        List<Channel> all = channelDAO.findAll();
        List<Channel> result = new ArrayList<>();
        for (Channel ch : all) {
            if (ch.isActive() && "Lazada".equalsIgnoreCase(ch.getPlatform())
                    && ch.getAccessToken() != null && !ch.getAccessToken().isEmpty()) {
                result.add(ch);
            }
        }
        return result;
    }

    private String resolveSellerSku(SkuMapping mapping) {
        if (mapping.getSellerSku() != null && !mapping.getSellerSku().isEmpty()) {
            return mapping.getSellerSku();
        }
        return mapping.getSkuCode();
    }

    private boolean isRetryable(String response) {
        return response.contains("\"code\":\"901\"") || response.contains("\"code\": 901");
    }

    private <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    public void triggerStockSyncForChannel(Channel channel) {
        if (channel == null || !"Lazada".equalsIgnoreCase(channel.getPlatform())) return;
        List<com.wms.model.ChannelProduct> products = new com.wms.dao.ChannelProductDAO().findByChannel(channel.getChannelId());
        List<Integer> productIds = products.stream()
                .filter(cp -> "ACTIVE".equalsIgnoreCase(cp.getStatus()))
                .map(com.wms.model.ChannelProduct::getProductId)
                .toList();
        if (!productIds.isEmpty()) {
            triggerStockSyncAfterInbound(productIds, "BUFFER_UPDATE_" + channel.getChannelId());
        }
    }
}
