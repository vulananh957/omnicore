package com.wms.service.lazada;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.dao.ChannelDAO;
import com.wms.dao.InventoryDAO;
import com.wms.dao.LazadaOrderDAO;
import com.wms.dao.OrderDAO;
import com.wms.dao.SkuMappingDAO;
import com.wms.model.Channel;
import com.wms.model.Order;
import com.wms.model.OrderItem;
import com.wms.util.DBConnection;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.wms.service.common.NotificationService;

/**
 * LazadaWebhookService — extracted from LazadaWebhookServlet.
 *
 * <p>Contains all order-persistence and business-logic methods that were previously
 * inlined in the servlet: upserting orders from webhook payloads, soft-allocation
 * after order arrival, and webhook audit logging.
 *
 * <p>The servlet remains responsible only for:
 * <ul>
 *   <li>Signature verification</li>
 *   <li>Idempotency check</li>
 *   <li>Dispatching to service</li>
 *   <li>Returning HTTP response</li>
 * </ul>
 */
public class LazadaWebhookService {

    private static final Logger LOGGER = Logger.getLogger(LazadaWebhookService.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final NotificationService notificationService = new NotificationService();

    private final InventoryDAO inventoryDAO = new InventoryDAO();
    private final OrderDAO orderDAO = new OrderDAO();
    private final ChannelDAO channelDAO = new ChannelDAO();
    private final SkuMappingDAO skuMappingDAO = new SkuMappingDAO();
    private final LazadaOrderDAO lazadaOrderDAO = new LazadaOrderDAO();
    private final LazadaOrderService orderService = new LazadaOrderService();

    /**
     * Called when LazadaWebhookServlet receives a new order status event.
     *
     * <p>If the order does not exist in our DB yet (webhook fires before
     * LazadaSyncScheduler runs), this fetches the order from Lazada API and
     * inserts it here so we never miss a soft-allocation window (Gap 3.1).
     *
     * <p>If the order has a warehouse assigned, performs soft-allocation
     * for every order item (Gap 3.3 / BR-04).
     */
    public void handleOrderEvent(JsonNode order) {
        String orderId = order.path("order_id").asText();
        if (orderId.isEmpty()) return;

        String status = order.path("statuses").isArray() && order.path("statuses").size() > 0
                ? order.path("statuses").get(0).asText()
                : order.path("status").asText();
        if (status.isEmpty()) return;

        String tracking = order.hasNonNull("tracking_number") ? order.get("tracking_number").asText() : null;
        if (tracking == null) {
            tracking = order.hasNonNull("tracking") ? order.get("tracking").asText() : null;
        }

        Order o = orderDAO.findByOrderCode(orderId);

        if (o != null) {
            if (o.getChannelId() > 0) {
                com.wms.service.channel.ChannelSyncAudit.logSuccess(
                        o.getChannelId(), "WEBHOOK", orderId, 200, null, status, 0L);
            }
            // Sync cancel: if seller/buyer cancelled on Lazada → update both tables
            if (status.equalsIgnoreCase("canceled") || status.equalsIgnoreCase("cancelled")
                    || status.equalsIgnoreCase("order_cancelled") || status.equalsIgnoreCase("failed")) {
                LOGGER.info("LazadaWebhookService: orderId=" + orderId + " status=" + status + " currentOrderStatus=" + o.getStatus());
                boolean ordersCancelled = !"CANCELLED".equals(o.getStatus());
                if (ordersCancelled) {
                    LOGGER.info("LazadaWebhookService: order " + orderId
                            + " cancelled on Lazada, updating both tables to CANCELLED");
                    // Update legacy orders table
                    orderDAO.updateOrderStatus(orderId, "CANCELLED");
                    // Also cancel fulfillment request so warehouse staff doesn't see it
                    com.wms.dao.FulfillmentRequestDAO frDAO = new com.wms.dao.FulfillmentRequestDAO();
                    frDAO.cancelByOrderId(orderId);
                    // Also cancel outbound orders so warehouse staff doesn't see it
                    com.wms.dao.OutboundDAO outboundDAO = new com.wms.dao.OutboundDAO();
                    outboundDAO.cancelByOrderId(orderId);
                }
                // Also update lazada_orders table (wms_status)
                lazadaOrderDAO.updateStatus(orderId, "CANCELLED");
                releaseAllocations(orderId);
                return; // no soft-allocation for cancelled orders
            }
            if (tracking != null && !tracking.isEmpty()
                    && (o.getTrackingNo() == null || !o.getTrackingNo().equals(tracking))) {
                orderDAO.updateOrderTrackingNo(orderId, tracking);
            }
        } else {
            // Order not found — webhook fired before scheduler. Fetch and insert.
            LOGGER.info("LazadaWebhookService: order " + orderId
                    + " not found in DB, fetching from Lazada API...");
            o = upsertOrderFromWebhook(orderId, order);
        }

        // BR-04 soft-allocation: only if warehouse is assigned and the order is still PENDING.
        // Once approved, the soft-allocation is handled by the WMS approval/outbound flow.
        if (o != null && o.getWarehouseId() > 0 && "PENDING".equals(o.getStatus())) {
            softAllocateForOrder(o);
        } else if (o != null) {
            LOGGER.fine("LazadaWebhookService: skipping soft-allocation for order " + orderId
                    + " (status=" + o.getStatus() + ", warehouse_id=" + o.getWarehouseId() + ")");
        }
    }

    // ── Order upsert from webhook ──────────────────────────────────

    /**
     * Fetches order detail + items from Lazada and inserts the order row.
     * Used when the webhook fires before LazadaSyncScheduler has run.
     */
    public Order upsertOrderFromWebhook(String orderId, JsonNode webhookPayload) {
        try {
            Channel ch = resolveLazadaChannel();
            if (ch == null) {
                LOGGER.warning("upsertOrderFromWebhook: no active Lazada channel available");
                return null;
            }

            // Get full order detail for shipping address and amount
            String detailJson = orderService.getOrderDetail(ch, orderId);
            JsonNode detailRoot = MAPPER.readTree(detailJson);
            JsonNode detailData = detailRoot.path("data");

            BigDecimal totalAmount = parseTotalAmount(detailData);

            // Upsert orders row
            int orderIdInt = upsertOrderRow(orderId, ch, totalAmount);
            if (orderIdInt <= 0) return null;

            // Upsert shipping details
            upsertShippingRow(orderIdInt, detailData, webhookPayload, orderId);

            // Populate/upsert lazada_orders table
            upsertLazadaOrderTable(detailData, orderId, ch);

            // Upsert order items
            upsertOrderItems(orderIdInt, detailData, webhookPayload, ch, orderId);

            LOGGER.info("upsertOrderFromWebhook: inserted order " + orderId
                    + " (id=" + orderIdInt + ") from webhook before scheduler ran");

            try {
                notificationService.notifyNewOrder(orderIdInt, ch.getChannelName());
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to send notification for new webhook order: " + orderIdInt, e);
            }

            return orderDAO.findByOrderCode(orderId);

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "upsertOrderFromWebhook: failed for " + orderId, e);
            return null;
        }
    }

    private int upsertOrderRow(String orderId, Channel ch, BigDecimal totalAmount) throws Exception {
        String sql = "INSERT INTO orders "
                + "(order_code, channel_id, channel_order_id, channel, status, "
                + " total_amount, sync_status, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, 'SYNCED', NOW()) "
                + "ON DUPLICATE KEY UPDATE "
                + "  channel_id = VALUES(channel_id), "
                + "  status = IF(status = 'PENDING', VALUES(status), status), "
                + "  total_amount = VALUES(total_amount), sync_status = 'SYNCED', "
                + "  updated_at = CURRENT_TIMESTAMP";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, orderId);
            ps.setInt(2, ch.getChannelId());
            ps.setString(3, orderId);
            ps.setString(4, "ONLINE");
            ps.setString(5, "PENDING");
            ps.setBigDecimal(6, totalAmount);
            int affected = ps.executeUpdate();

            if (affected == 1) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) return rs.getInt(1);
                }
            }
            // UPDATE path: find existing ID
            try (PreparedStatement ps2 = conn.prepareStatement(
                    "SELECT order_id FROM orders WHERE order_code = ?")) {
                ps2.setString(1, orderId);
                try (ResultSet rs = ps2.executeQuery()) {
                    if (rs.next()) return rs.getInt("order_id");
                }
            }
            return -1;
        }
    }

    private void upsertShippingRow(int orderIdInt, JsonNode detailData,
                                   JsonNode webhookPayload, String orderId) throws Exception {
        JsonNode addr = detailData.path("address_shipping");
        String recipientName = firstNonEmpty(
                addr.path("first_name").asText(),
                webhookPayload.path("recipient_name").asText(),
                "Lazada Buyer " + orderId);
        String address = firstNonEmpty(
                addr.path("address1").asText(),
                webhookPayload.path("shipping_address").asText(),
                "(địa chỉ chưa rõ)");

        String sql = "INSERT INTO order_shipping_details "
                + "(order_id, recipient_name, shipping_address) "
                + "VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE "
                + "  recipient_name = VALUES(recipient_name), "
                + "  shipping_address = VALUES(shipping_address)";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderIdInt);
            ps.setString(2, recipientName);
            ps.setString(3, address);
            ps.executeUpdate();
        }
    }


    private static java.sql.Timestamp parseTimestamp(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return new java.sql.Timestamp(System.currentTimeMillis());
        }
        String[] patterns = {
                "yyyy-MM-dd HH:mm:ss Z",
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd HH:mm:ss"
        };
        for (String p : patterns) {
            try {
                java.time.format.DateTimeFormatter fmt =
                        java.time.format.DateTimeFormatter.ofPattern(p);
                if (p.contains("XXX") || p.contains("Z") && !p.endsWith("'Z'")) {
                    java.time.ZonedDateTime zdt = java.time.ZonedDateTime.parse(dateStr, fmt);
                    return java.sql.Timestamp.from(zdt.toInstant());
                } else {
                    java.time.LocalDateTime ldt = java.time.LocalDateTime.parse(dateStr, fmt);
                    return java.sql.Timestamp.valueOf(ldt);
                }
            } catch (Exception ignore) {}
        }
        return new java.sql.Timestamp(System.currentTimeMillis());
    }

    private void upsertLazadaOrderTable(JsonNode detailData, String orderId, Channel ch) {
        try {
            com.wms.model.LazadaOrder lo = new com.wms.model.LazadaOrder();
            lo.setLazadaOrderIdStr(orderId);
            String orderNum = detailData.path("order_number").asText();
            lo.setLazadaOrderNumber(orderNum.isEmpty() ? orderId : orderNum);
            lo.setChannelId(ch.getChannelId());
            
            String status = detailData.path("status").asText("pending");
            lo.setStatus(status);
            if (status.equalsIgnoreCase("canceled") || status.equalsIgnoreCase("cancelled") 
                || status.equalsIgnoreCase("order_cancelled") || status.equalsIgnoreCase("failed")) {
                lo.setWmsStatus("CANCELLED");
            } else {
                lo.setWmsStatus("NEW");
            }
            
            String firstName = detailData.path("address_shipping").path("first_name").asText();
            String lastName = detailData.path("address_shipping").path("last_name").asText();
            String recipientName = (firstName + " " + lastName).trim();
            if (recipientName.isEmpty()) recipientName = "Lazada Customer";
            lo.setCustomerName(recipientName);
            lo.setCustomerPhone(detailData.path("address_shipping").path("phone").asText());
            
            String addr1 = detailData.path("address_shipping").path("address1").asText();
            String addr2 = detailData.path("address_shipping").path("address2").asText();
            String addr3 = detailData.path("address_shipping").path("address3").asText();
            java.util.List<String> addrParts = new java.util.ArrayList<>();
            if (addr1 != null && !addr1.trim().isEmpty()) addrParts.add(addr1);
            if (addr2 != null && !addr2.trim().isEmpty()) addrParts.add(addr2);
            if (addr3 != null && !addr3.trim().isEmpty()) addrParts.add(addr3);
            String address = String.join(", ", addrParts);
            lo.setShippingAddress(address.isEmpty() ? "Lazada Address" : address);
            lo.setShippingCity(detailData.path("address_shipping").path("city").asText());
            
            lo.setPrice(BigDecimal.valueOf(detailData.path("price").asDouble(0.0)));
            lo.setShippingFee(BigDecimal.valueOf(detailData.path("shipping_fee").asDouble(0.0)));
            lo.setVoucherSeller(BigDecimal.valueOf(detailData.path("voucher_seller").asDouble(0.0)));
            lo.setVoucherPlatform(BigDecimal.valueOf(detailData.path("voucher_platform").asDouble(0.0)));
            lo.setPaymentMethod(detailData.path("payment_method").asText("COD"));
            lo.setBuyerNote(detailData.path("buyer_note").asText(""));
            
            String createdAtStr = detailData.path("created_at").asText();
            String updatedAtStr = detailData.path("updated_at").asText();
            if (createdAtStr != null && !createdAtStr.isEmpty()) {
                lo.setLazadaCreatedAt(parseTimestamp(createdAtStr).toLocalDateTime());
            }
            if (updatedAtStr != null && !updatedAtStr.isEmpty()) {
                lo.setLazadaUpdatedAt(parseTimestamp(updatedAtStr).toLocalDateTime());
            }
            lo.setSyncedAt(java.time.LocalDateTime.now());
            
            lazadaOrderDAO.upsertFromApi(lo);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to upsert lazada_orders table in webhook for order " + orderId, e);
        }
    }

    private void deleteOrderItems(int orderId) throws java.sql.SQLException {
        String sql = "DELETE FROM order_items WHERE order_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            ps.executeUpdate();
        }
    }

    private void upsertOrderItems(int orderIdInt, JsonNode detailData, JsonNode webhookPayload,
                                  Channel ch, String orderId) throws Exception {
        JsonNode items = detailData.path("order_items");
        if (!items.isArray() || items.isEmpty()) {
            items = webhookPayload.path("order_items");
        }
        if (!items.isArray() || items.isEmpty()) {
            // Fetch from Lazada API
            try {
                com.wms.service.lazada.LazadaOrderService orderService = new com.wms.service.lazada.LazadaOrderService();
                String itemsJson = orderService.getOrderItems(ch, orderId);
                if (itemsJson != null) {
                    JsonNode root = MAPPER.readTree(itemsJson);
                    items = root.path("data");
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to fetch order items from API in webhook for order " + orderId, e);
            }
        }
        if (!items.isArray() || items.isEmpty()) return;

        // Delete existing items to prevent duplicates
        deleteOrderItems(orderIdInt);

        String sql = "INSERT INTO order_items (order_id, product_id, qty, unit_price, actual_price) "
                + "VALUES (?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE qty = VALUES(qty), unit_price = VALUES(unit_price), actual_price = VALUES(actual_price)";

        List<com.wms.model.LazadaOrderItem> lazadaItems = new java.util.ArrayList<>();

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (JsonNode item : items) {
                String externalSku = stripNull(item.path("sku").asText());
                double qty = item.path("quantity").asDouble(1);
                if (qty <= 0) qty = 1;
                double unitPrice = item.path("item_price").asDouble(0);
                if (unitPrice == 0) unitPrice = item.path("price").asDouble(0);

                // Skip items with no SKU mapping — do NOT fall back to a dummy placeholder
                if (externalSku.isEmpty()) {
                    LOGGER.warning("LazadaWebhookService: empty SKU in webhook orderId=" + orderId + ", skipping item");
                    continue;
                }
                var mapping = skuMappingDAO.findActiveMapping(ch.getChannelId(), externalSku);
                if (mapping == null || mapping.getSkuId() <= 0) {
                    skuMappingDAO.logMappingException(ch.getChannelId(), externalSku, orderId,
                            "No SKU mapping for Lazada webhook order item");
                    LOGGER.warning("LazadaWebhookService: unmapped SKU '" + externalSku
                            + "' orderId=" + orderId + " — item skipped, map SKU first");
                    continue;
                }
                int productId = mapping.getSkuId();

                ps.setInt(1, orderIdInt);
                ps.setInt(2, productId);
                ps.setDouble(3, qty);
                ps.setDouble(4, unitPrice);
                ps.setDouble(5, qty * unitPrice);
                ps.addBatch();

                // Map to LazadaOrderItem
                com.wms.model.LazadaOrderItem loi = new com.wms.model.LazadaOrderItem();
                loi.setLazadaOrderIdStr(orderId);
                loi.setOrderItemId(String.valueOf(item.path("order_item_id").asLong()));
                loi.setSku(externalSku);
                loi.setShopSku(item.path("shop_sku").asText());
                loi.setProductName(item.path("product_name").asText(item.path("name").asText("Lazada Item")));
                loi.setProductImage(item.path("product_image").asText());
                loi.setQuantity((int)qty);
                loi.setPaidPrice(BigDecimal.valueOf(item.path("paid_price").asDouble(unitPrice)));
                loi.setItemPrice(BigDecimal.valueOf(unitPrice));
                loi.setSupplyPrice(BigDecimal.valueOf(item.path("supply_price").asDouble(0.0)));
                String status = item.path("status").asText();
                loi.setStatus(status.isEmpty() ? "pending" : status);
                loi.setProductId(productId);
                loi.setReservedQty(item.path("reserved_qty").asInt(0));
                loi.setFulfilledQty(item.path("fulfilled_qty").asInt(0));
                lazadaItems.add(loi);
            }
            ps.executeBatch();
        }

        // Upsert into lazada_order_items table
        if (!lazadaItems.isEmpty()) {
            lazadaOrderDAO.upsertItems(orderId, lazadaItems);
        }
    }

    // ── Soft-allocation ───────────────────────────────────────────

    /**
     * Executes BR-04 soft-allocation for every item in the given order.
     * Increases holding and decreases qty_available atomically.
     * Continues with remaining items even if one item fails (best-effort).
     */
    public void softAllocateForOrder(Order o) {
        if (o.getWarehouseId() <= 0) return;
        List<OrderItem> items = orderDAO.findItemsByOrderId(o.getOrderId());
        if (items.isEmpty()) {
            LOGGER.fine("softAllocateForOrder: no items for order " + o.getOrderCode());
            return;
        }
        int allocated = 0;
        int failed = 0;
        for (OrderItem item : items) {
            if (item.getProductId() <= 0 || item.getQuantity() <= 0) continue;
            try {
                boolean ok = inventoryDAO.softAllocateInventory(
                        item.getProductId(), o.getWarehouseId(), item.getQuantity());
                if (ok) {
                    allocated++;
                } else {
                    LOGGER.warning("softAllocateForOrder: insufficient stock productId="
                            + item.getProductId() + " warehouseId=" + o.getWarehouseId()
                            + " qty=" + item.getQuantity());
                    failed++;
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "softAllocateForOrder: exception productId="
                        + item.getProductId(), e);
                failed++;
            }
        }
        LOGGER.info("softAllocateForOrder: order=" + o.getOrderCode()
                + " warehouseId=" + o.getWarehouseId()
                + " allocated=" + allocated + " failed=" + failed);
    }

    private void releaseAllocations(String orderCode) {
        Order o = orderDAO.findByOrderCode(orderCode);
        if (o == null || o.getWarehouseId() <= 0) return;
        List<OrderItem> items = orderDAO.findItemsByOrderId(o.getOrderId());
        for (OrderItem item : items) {
            if (item.getProductId() <= 0 || item.getQuantity() <= 0) continue;
            try {
                inventoryDAO.releaseSoftAllocateInventory(
                        item.getProductId(),
                        o.getWarehouseId(),
                        BigDecimal.valueOf(item.getQuantity()));
            } catch (Exception e) {
                LOGGER.log(Level.WARNING,
                        "releaseAllocations: failed for productId=" + item.getProductId(), e);
            }
        }
    }

    // ── Webhook audit logging ──────────────────────────────────────

    public int logWebhook(int channelId, String eventType, String payload,
                          String messageId, String clientIp, String signature,
                          String status, String errorTrace) {
        String sql = "INSERT INTO webhook_logs "
                + "(channel_id, event_type, payload, message_id, "
                + " request_ip, request_signature, status, error_trace, retry_count) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            if (channelId <= 0) ps.setNull(1, Types.INTEGER); else ps.setInt(1, channelId);
            ps.setString(2, eventType);
            ps.setString(3, payload != null && payload.length() > 4000 ? payload.substring(0, 4000) + "...[truncated]" : payload);
            if (messageId == null || messageId.isEmpty()) ps.setNull(4, Types.VARCHAR); else ps.setString(4, messageId);
            if (clientIp == null || clientIp.isEmpty()) ps.setNull(5, Types.VARCHAR); else ps.setString(5, clientIp);
            if (signature == null || signature.isEmpty()) ps.setNull(6, Types.VARCHAR); else ps.setString(6, signature);
            ps.setString(7, status);
            ps.setString(8, errorTrace);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "logWebhook insert failed", e);
        }
        return 0;
    }

    public void markWebhookStatus(int logId, String status, String errorTrace) {
        if (logId <= 0) return;
        String sql = "UPDATE webhook_logs SET status = ?, error_trace = ?, "
                + "processed_at = CASE WHEN ? = 'SUCCESS' THEN CURRENT_TIMESTAMP ELSE processed_at END, "
                + "retry_count = retry_count + CASE WHEN ? = 'FAILED' THEN 1 ELSE 0 END "
                + "WHERE log_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, errorTrace);
            ps.setString(3, status);
            ps.setString(4, status);
            ps.setInt(5, logId);
            ps.executeUpdate();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "markWebhookStatus failed", e);
        }
    }

    // ── Helpers ────────────────────────────────────────────────────

    private Channel resolveLazadaChannel() {
        var channels = channelDAO.findAll();
        for (Channel c : channels) {
            if (c.isActive() && "Lazada".equalsIgnoreCase(c.getPlatform())
                    && c.getAccessToken() != null && !c.getAccessToken().isEmpty()) {
                return c;
            }
        }
        return null;
    }

    private BigDecimal parseTotalAmount(JsonNode detailData) {
        String t1 = detailData.path("price").asText();
        String t2 = detailData.path("total_amount").asText();
        String chosen = firstNonEmpty(t1, t2, "0");
        try {
            return new BigDecimal(chosen.trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private static String stripNull(String s) {
        return s == null ? "" : s;
    }

    private static String firstNonEmpty(String... candidates) {
        for (String c : candidates) {
            if (c != null && !c.trim().isEmpty()) return c;
        }
        return "";
    }
}
