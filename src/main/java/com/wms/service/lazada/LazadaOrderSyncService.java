package com.wms.service.lazada;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.dao.LazadaOrderDAO;
import com.wms.dao.LazadaShipmentProviderDAO;
import com.wms.dao.SkuMappingDAO;
import com.wms.model.Channel;
import com.wms.model.LazadaOrder;
import com.wms.model.LazadaOrderItem;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.wms.service.common.NotificationService;

/**
 * LazadaOrderSyncService — extracted from LazadaSyncScheduler.
 *
 * <p>Contains all order-persistence logic that was previously inlined in the scheduler:
 * upsert orders, shipping details, order items (with SKU mapping), and soft-allocation.
 *
 * <p>This is a stateful service: caller must call {@link #setChannel(Channel)} before
 * each sync cycle, or pass the channel explicitly on each call.
 *
 * <p>All DB operations accept an external {@link Connection} so the caller controls
 * the transaction boundary. On error, the caller is responsible for rollback.
 */
public class LazadaOrderSyncService {

    private static final Logger LOGGER = Logger.getLogger(LazadaOrderSyncService.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final NotificationService notificationService = new NotificationService();

    /** The channel being synced — set once per sync cycle via {@link #setChannel}. */
    private Channel currentChannel;

    public LazadaOrderSyncService() {}


    public void setChannel(Channel channel) {
        this.currentChannel = channel;
    }

    public Channel getChannel() {
        return currentChannel;
    }

    // ── Order upsert ──────────────────────────────────────────────

    /**
     * Persists one Lazada order: orders + order_shipping_details + order_items.
     * Returns NEW if the order was inserted, UPDATED if it was already known,
     * or SKIPPED if the order_id is invalid.
     */
    public SyncResult saveOneOrder(Connection conn, JsonNode orderNode, String detailJson) throws SQLException {
        if (currentChannel == null) {
            throw new IllegalStateException("LazadaOrderSyncService: channel not set — call setChannel() first");
        }
        long lazadaOrderId = orderNode.path("order_id").asLong();
        if (lazadaOrderId <= 0) {
            return SyncResult.SKIPPED;
        }
        String orderCode = String.valueOf(lazadaOrderId);

        JsonNode detailRoot = (detailJson != null) ? safeParse(detailJson) : null;
        JsonNode detailData = detailRoot != null ? detailRoot.path("data") : orderNode;

        int channelId = currentChannel.getChannelId();

        BigDecimal totalAmount = parseTotalAmount(detailData, orderNode);
        Timestamp createdAt = parseTimestamp(
                firstNonEmpty(detailData.path("created_at").asText(),
                        orderNode.path("created_at").asText(), ""));
        String feeBreakdown = buildFeeBreakdownJson(detailData);
        // Extract status from detail or order node (Lazada returns array of statuses)
        String lazadaStatus = orderService.extractStatus(detailData);
        if (lazadaStatus.isEmpty() || lazadaStatus.equals("pending")) {
            lazadaStatus = orderService.extractStatus(orderNode);
        }

        int generatedId = upsertOrder(conn, orderCode, channelId, totalAmount, feeBreakdown, createdAt, lazadaStatus);
        if (generatedId <= 0) {
            return SyncResult.SKIPPED;
        }

        upsertShippingDetails(conn, generatedId, detailData, orderCode);
        
        // Populate/upsert lazada_orders table
        upsertLazadaOrderTable(detailData, orderCode, channelId);

        int itemCount = upsertOrderItems(conn, generatedId, detailData, orderCode);
        if (itemCount == 0) {
            LOGGER.warning("LazadaOrderSyncService: no items could be mapped for orderCode=" + orderCode
                    + " — all SKUs are unmapped. Order saved with 0 items; SKU mapping required.");
        }

        // Broadcast notification to Sales Staff
        try {
            notificationService.notifyNewOrder(generatedId, currentChannel.getChannelName());
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to send notification for new order: " + generatedId, e);
        }

        return SyncResult.NEW;
    }

    private int upsertOrder(Connection conn, String orderCode, int channelId,
                            BigDecimal totalAmount, String feeBreakdown, Timestamp createdAt,
                            String lazadaStatus) throws SQLException {
        // Map Lazada status → WMS status for the orders table
        String wmsStatus = orderService.mapLazadaStatus(lazadaStatus);
        String sql = "INSERT INTO orders "
                + "(order_code, channel_id, channel_order_id, warehouse_id, channel, status, "
                + " total_amount, fee_breakdown_json, sync_status, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'SYNCED', ?) "
                + "ON DUPLICATE KEY UPDATE "
                + "  channel_id = VALUES(channel_id), "
                + "  channel_order_id = VALUES(channel_order_id), "
                + "  status = VALUES(status), "
                + "  total_amount = VALUES(total_amount), "
                + "  fee_breakdown_json = VALUES(fee_breakdown_json), "
                + "  sync_status = 'SYNCED', "
                + "  updated_at = CURRENT_TIMESTAMP";

        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, orderCode);
            ps.setInt(2, channelId);
            ps.setString(3, orderCode);
            ps.setNull(4, java.sql.Types.INTEGER); // warehouse_id=null until Sales approves
            ps.setString(5, "ONLINE");
            ps.setString(6, wmsStatus);
            ps.setBigDecimal(7, totalAmount);
            ps.setString(8, feeBreakdown);
            ps.setTimestamp(9, createdAt);
            int affected = ps.executeUpdate();

            if (affected == 1) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) return rs.getInt(1);
                }
            }
            return findOrderIdByCode(conn, orderCode);
        }
    }

    private void upsertShippingDetails(Connection conn, int orderId, JsonNode detail, String orderCode)
            throws SQLException {
        JsonNode addr = detail.path("address_shipping");
        String recipientName = firstNonEmpty(
                addr.path("first_name").asText(),
                detail.path("recipient_name").asText(),
                "Lazada Buyer " + orderCode);
        String address = firstNonEmpty(
                addr.path("address1").asText(),
                detail.path("shipping_address").asText(),
                "(địa chỉ chưa rõ)");
        String city = addr.path("city").asText();
        String postcode = addr.path("postcode").asText();
        if (!city.isEmpty()) address += ", " + city;
        if (!postcode.isEmpty()) address += " " + postcode;
        String courier = firstNonEmpty(
                detail.path("shipping_provider").asText(),
                detail.path("shipment_provider").asText(),
                "Lazada");
        String waybill = stripNull(detail.path("tracking_number").asText());
        String shippingStatus = mapLazadaShippingStatus(detail);

        String sql = "INSERT INTO order_shipping_details "
                + "(order_id, recipient_name, shipping_address, courier_name, waybill_code, shipping_status) "
                + "VALUES (?, ?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE "
                + "  recipient_name = VALUES(recipient_name), "
                + "  shipping_address = VALUES(shipping_address), "
                + "  courier_name = VALUES(courier_name), "
                + "  waybill_code = COALESCE(NULLIF(VALUES(waybill_code), ''), waybill_code), "
                + "  shipping_status = VALUES(shipping_status)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            ps.setString(2, recipientName);
            ps.setString(3, address);
            ps.setString(4, courier);
            if (waybill.isEmpty()) ps.setNull(5, Types.VARCHAR); else ps.setString(5, waybill);
            ps.setString(6, shippingStatus);
            ps.executeUpdate();
        }
    }

    private void deleteOrderItems(Connection conn, int orderId) throws SQLException {
        String sql = "DELETE FROM order_items WHERE order_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            ps.executeUpdate();
        }
    }

    private int upsertOrderItems(Connection conn, int orderId, JsonNode detail,
                                 String orderCode) throws SQLException {
        JsonNode items = detail.path("order_items");
        if (!items.isArray() || items.isEmpty()) {
            items = detail.path("data");
        }
        if (!items.isArray() || items.isEmpty()) {
            // Fetch from Lazada API
            try {
                String itemsJson = orderService.getOrderItems(currentChannel, orderCode);
                if (itemsJson != null) {
                    JsonNode root = MAPPER.readTree(itemsJson);
                    items = root.path("data");
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to fetch order items from API for order " + orderCode, e);
            }
        }
        if (!items.isArray() || items.isEmpty()) {
            return 0;
        }

        // Delete existing items to prevent duplicates
        deleteOrderItems(conn, orderId);

        SkuMappingDAO mappingDAO = new SkuMappingDAO();
        int channelId = currentChannel.getChannelId();
        int count = 0;
        List<LazadaOrderItem> lazadaItems = new ArrayList<>();

        String sql = "INSERT INTO order_items (order_id, product_id, qty, unit_price, actual_price) "
                + "VALUES (?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE qty = VALUES(qty), unit_price = VALUES(unit_price), actual_price = VALUES(actual_price)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (JsonNode itemNode : items) {
                String externalSku = stripNull(itemNode.path("sku").asText());
                double qty = itemNode.path("quantity").asDouble(1);
                if (qty <= 0) qty = 1;
                double unitPrice = itemNode.path("item_price").asDouble(0);
                if (unitPrice == 0) unitPrice = itemNode.path("price").asDouble(0);

                // Skip items with no SKU mapping — do NOT fall back to a dummy placeholder
                if (externalSku.isEmpty()) {
                    LOGGER.warning("LazadaOrderSyncService: empty SKU in orderCode=" + orderCode + ", skipping item");
                    continue;
                }
                var mapping = mappingDAO.findActiveMapping(channelId, externalSku);
                if (mapping == null || mapping.getSkuId() <= 0) {
                    mappingDAO.logMappingException(channelId, externalSku, orderCode,
                            "No active SKU mapping for Lazada order item");
                    LOGGER.warning("LazadaOrderSyncService: unmapped SKU '" + externalSku
                            + "' orderCode=" + orderCode + " — item skipped, map SKU first");
                    continue;
                }
                int productId = mapping.getSkuId();

                ps.setInt(1, orderId);
                ps.setInt(2, productId);
                ps.setDouble(3, qty);
                ps.setDouble(4, unitPrice);
                ps.setDouble(5, qty * unitPrice);
                ps.executeUpdate();
                count++;

                // Map to LazadaOrderItem
                LazadaOrderItem loi = new LazadaOrderItem();
                loi.setLazadaOrderIdStr(orderCode);
                loi.setOrderItemId(String.valueOf(itemNode.path("order_item_id").asLong()));
                loi.setSku(externalSku);
                loi.setShopSku(itemNode.path("shop_sku").asText());
                loi.setProductName(itemNode.path("product_name").asText(itemNode.path("name").asText("Lazada Item")));
                loi.setProductImage(itemNode.path("product_image").asText());
                loi.setQuantity((int)qty);
                loi.setPaidPrice(BigDecimal.valueOf(itemNode.path("paid_price").asDouble(unitPrice)));
                loi.setItemPrice(BigDecimal.valueOf(unitPrice));
                loi.setSupplyPrice(BigDecimal.valueOf(itemNode.path("supply_price").asDouble(0.0)));
                String status = itemNode.path("status").asText();
                loi.setStatus(status.isEmpty() ? "pending" : status);
                loi.setProductId(productId);
                loi.setReservedQty(itemNode.path("reserved_qty").asInt(0));
                loi.setFulfilledQty(itemNode.path("fulfilled_qty").asInt(0));
                lazadaItems.add(loi);
            }
        }

        // Upsert into lazada_order_items table
        if (!lazadaItems.isEmpty()) {
            lazadaOrderDAO.upsertItems(orderCode, lazadaItems);
        }

        return count;
    }


    private void upsertLazadaOrderTable(JsonNode detailData, String orderCode, int channelId) {
        try {
            LazadaOrder lo = new LazadaOrder();
            lo.setLazadaOrderIdStr(orderCode);
            String orderNum = detailData.path("order_number").asText();
            lo.setLazadaOrderNumber(orderNum.isEmpty() ? orderCode : orderNum);
            lo.setChannelId(channelId);
            
            // Lazada returns status as an array "statuses": ["canceled"] — use extractStatus()
            String status = orderService.extractStatus(detailData);
            if (status.isEmpty()) status = "pending";
            lo.setStatus(status);
            if (isCancelledStatus(status)) {
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
            LOGGER.log(Level.WARNING, "Failed to upsert lazada_orders table for order " + orderCode, e);
        }
    }


    private int findOrderIdByCode(Connection conn, String orderCode) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT order_id FROM orders WHERE order_code = ?")) {
            ps.setString(1, orderCode);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("order_id");
            }
        }
        return -1;
    }

    // ── Helpers ────────────────────────────────────────────────────

    private BigDecimal parseTotalAmount(JsonNode detail, JsonNode orderNode) {
        String t1 = detail.path("price").asText();
        String t2 = detail.path("total_amount").asText();
        String t3 = orderNode.path("total_amount").asText();
        String chosen = firstNonEmpty(t1, t2, t3, "0");
        try {
            return new BigDecimal(chosen.trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private String mapLazadaShippingStatus(JsonNode detail) {
        // Fallback: derive from order status if no explicit shipping status
        String orderStatus = detail.path("status").asText();
        return orderStatus.isEmpty() ? "PENDING" : orderStatus;
    }

    private JsonNode safeParse(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return MAPPER.readTree(s);
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "safeParse failed", e);
            return null;
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

    private static Timestamp parseTimestamp(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return new Timestamp(System.currentTimeMillis());
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
                    return Timestamp.from(zdt.toInstant());
                } else {
                    java.time.LocalDateTime ldt = java.time.LocalDateTime.parse(dateStr, fmt);
                    return Timestamp.valueOf(ldt);
                }
            } catch (Exception ignore) {}
        }
        return new Timestamp(System.currentTimeMillis());
    }

    private static String buildFeeBreakdownJson(JsonNode detail) {
        try {
            com.fasterxml.jackson.databind.node.ObjectNode node = MAPPER.createObjectNode();
            String shippingFee = detail.path("shipping_fee").asText();
            if (shippingFee.isEmpty()) shippingFee = "0";
            String voucherAmount = detail.path("voucher_amount").asText();
            if (voucherAmount.isEmpty()) voucherAmount = "0";
            node.put("shipping_fee", shippingFee);
            node.put("voucher_amount", voucherAmount);
            node.put("payment_method", detail.path("payment_method").asText());
            node.put("gift_option", detail.path("gift_option").asText());
            return MAPPER.writeValueAsString(node);
        } catch (Exception e) {
            return null;
        }
    }

    // ── Scheduler-facing API (called by LazadaSyncScheduler / LazadaOrderSyncScheduler) ──

    private final LazadaOrderService orderService = new LazadaOrderService();
    private final LazadaOrderDAO lazadaOrderDAO = new LazadaOrderDAO();

    /**
     * Fetches pending orders from Lazada API, upserts them into lazada_orders + lazada_order_items.
     *
     * @param channel  Lazada channel credentials
     * @param limit    max orders to fetch per call
     * @return number of new orders inserted
     */
    public int syncNewOrdersFromApi(Channel channel, int limit) {
        int total = 0;
        // Fetch pending orders
        try {
            String json = orderService.getPendingOrders(channel);
            total += parseAndUpsertOrders(channel, json, limit);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "syncNewOrdersFromApi (pending) failed: " + e.getMessage(), e);
        }
        // Also fetch canceled orders so they appear in the system with correct status
        try {
            String json = orderService.getCanceledOrders(channel);
            total += parseAndUpsertOrders(channel, json, limit);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "syncNewOrdersFromApi (canceled) failed: " + e.getMessage(), e);
        }
        return total;
    }

    /**
     * Fetches orders updated since the given instant, upserts status changes.
     *
     * @param channel  Lazada channel credentials
     * @param since    Instant from which to fetch updates (null = all)
     * @return number of updated orders
     */
    public int syncExistingOrdersFromApi(Channel channel, Instant since) {
        if (since == null) return 0;
        try {
            Long sinceMs = since.toEpochMilli();
            String json = orderService.getOrdersUpdatedAfter(channel, sinceMs, null);
            return parseAndUpsertOrders(channel, json, 100);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "syncExistingOrdersFromApi failed: " + e.getMessage(), e);
            return 0;
        }
    }

    /**
     * Batch-fetches line items for the given Lazada order IDs from Lazada API,
     * then upserts them into lazada_order_items.
     *
     * @param channel   Lazada channel credentials
     * @param orderIds  list of Lazada order_id strings (max 50 per API call)
     * @return number of items inserted
     */
    public int fetchOrderItemsFromApi(Channel channel, List<String> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) return 0;
        int total = 0;
        List<String> batch = new ArrayList<>();
        for (String oid : orderIds) {
            batch.add(oid);
            if (batch.size() >= 50) {
                total += fetchOrderItemsBatch(channel, batch);
                batch = new ArrayList<>();
            }
        }
        if (!batch.isEmpty()) total += fetchOrderItemsBatch(channel, batch);
        return total;
    }

    private int fetchOrderItemsBatch(Channel channel, List<String> orderIds) {
        try {
            String json = orderService.getOrderItems(channel, String.join(",", orderIds));
            JsonNode root = MAPPER.readTree(json);
            JsonNode items = root.path("data");
            if (!items.isArray()) return 0;
            int count = 0;
            for (JsonNode itemNode : items) {
                LazadaOrderItem item = new LazadaOrderItem();
                item.setLazadaOrderIdStr(String.valueOf(itemNode.path("order_id").asLong()));
                item.setOrderItemId(String.valueOf(itemNode.path("order_item_id").asLong()));
                item.setSku(itemNode.path("sku").asText());
                item.setShopSku(itemNode.path("shop_sku").asText());
                item.setProductName(itemNode.path("product_name").asText());
                item.setProductImage(itemNode.path("product_image").asText());
                item.setQuantity(itemNode.path("quantity").asInt(1));
                item.setPaidPrice(BigDecimal.valueOf(itemNode.path("paid_price").asDouble()));
                item.setItemPrice(BigDecimal.valueOf(itemNode.path("item_price").asDouble()));
                item.setSupplyPrice(BigDecimal.valueOf(itemNode.path("supply_price").asDouble()));
                String status = itemNode.path("status").asText();
                item.setStatus(status.isEmpty() ? "pending" : status);
                item.setReservedQty(itemNode.path("reserved_qty").asInt(0));
                item.setFulfilledQty(itemNode.path("fulfilled_qty").asInt(0));
                lazadaOrderDAO.insertItem(item);
                count++;
            }
            return count;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "fetchOrderItemsBatch failed: " + e.getMessage(), e);
            return 0;
        }
    }

    private int parseAndUpsertOrders(Channel channel, String json, int limit) throws Exception {
        JsonNode root = MAPPER.readTree(json);
        JsonNode orders = root.path("data").path("orders");
        if (!orders.isArray()) return 0;
        int count = 0;
        int channelId = channel.getChannelId();
        for (JsonNode orderNode : orders) {
            if (count >= limit) break;
            long orderId = orderNode.path("order_id").asLong();
            if (orderId <= 0) continue;
            String orderCode = String.valueOf(orderId);

            String lazadaStatus = orderService.extractStatus(orderNode);

            LazadaOrder existing = lazadaOrderDAO.findByLazadaOrderIdStrForChannel(orderCode, channelId);
            if (existing != null) {
                // Update status for existing orders (e.g., cancelled on Lazada)
                if (isCancelledStatus(lazadaStatus)) {
                    LOGGER.info("syncExistingOrdersFromApi: order " + orderCode
                            + " cancelled on Lazada, updating both tables to CANCELLED");
                    // Update lazada_orders table
                    lazadaOrderDAO.updateStatus(orderCode, "CANCELLED");
                    // Also update legacy orders table
                    com.wms.dao.OrderDAO legacyDAO = new com.wms.dao.OrderDAO();
                    legacyDAO.updateOrderStatus(orderCode, "CANCELLED");
                    // Also cancel fulfillment request so warehouse staff doesn't see it
                    com.wms.dao.FulfillmentRequestDAO frDAO = new com.wms.dao.FulfillmentRequestDAO();
                    frDAO.cancelByOrderId(orderCode);
                    // Also cancel outbound orders so warehouse staff doesn't see it
                    com.wms.dao.OutboundDAO outboundDAO = new com.wms.dao.OutboundDAO();
                    outboundDAO.cancelByOrderId(orderCode);
                    count++;
                }
                continue;
            }

            // For new orders, save to BOTH orders and lazada_orders tables using saveOneOrder
            String detailJson = null;
            try {
                detailJson = orderService.getOrderDetail(channel, orderCode);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "parseAndUpsertOrders: getOrderDetail failed for " + orderCode, e);
            }
            
            try (Connection conn = com.wms.util.DBConnection.getConnection()) {
                conn.setAutoCommit(false);
                setChannel(channel);
                saveOneOrder(conn, orderNode, detailJson);
                conn.commit();
                count++;
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "parseAndUpsertOrders: saveOneOrder failed for orderCode=" + orderCode, e);
            }
            
            // Left in PENDING status with no warehouse assigned so Sales Staff can manually assign it on WMS.
        }
        return count;
    }

    private boolean isCancelledStatus(String status) {
        if (status == null) return false;
        String lower = status.toLowerCase();
        return lower.equals("canceled") || lower.equals("cancelled") 
            || lower.equals("order_cancelled") || lower.equals("failed");
    }

    public void syncAllActiveChannels(int limit) {
        try {
            List<Channel> channels = new com.wms.dao.ChannelDAO().findAll();
            for (Channel channel : channels) {
                if (!channel.isActive() || !"Lazada".equalsIgnoreCase(channel.getPlatform())) {
                    continue;
                }
                java.time.LocalDateTime lastSync = channel.getLastOrderSyncAt();
                java.time.LocalDateTime tenSecAgo = java.time.LocalDateTime.now().minusSeconds(10);
                if (lastSync == null || lastSync.isBefore(tenSecAgo)) {
                    try {
                        LOGGER.info("LazadaOrderSyncService: Syncing active channel: " + channel.getChannelName());
                        syncNewOrdersFromApi(channel, limit);
                        
                        try {
                            LOGGER.info("LazadaOrderSyncService: Syncing existing order updates in last 24 hours...");
                            syncExistingOrdersFromApi(channel, java.time.Instant.now().minus(24, java.time.temporal.ChronoUnit.HOURS));
                        } catch (Exception ex) {
                            LOGGER.warning("LazadaOrderSyncService: failed to sync existing orders: " + ex.getMessage());
                        }
                        
                        // Fetch missing items
                        List<String> orderIds = new ArrayList<>();
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
                        try (java.sql.Connection conn = com.wms.util.DBConnection.getConnection();
                             java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
                            ps.setInt(1, channel.getChannelId());
                            ps.setInt(2, limit);
                            try (java.sql.ResultSet rs = ps.executeQuery()) {
                                while (rs.next()) orderIds.add(rs.getString(1));
                            }
                        }
                        if (!orderIds.isEmpty()) {
                            fetchOrderItemsFromApi(channel, orderIds);
                        }
                        
                        // Update last sync time
                        new com.wms.dao.ChannelDAO().updateLastOrderSyncAt(channel.getChannelId(), java.time.LocalDateTime.now());
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, "LazadaOrderSyncService: Failed to sync channel " + channel.getChannelName(), e);
                    }
                } else {
                    LOGGER.info("LazadaOrderSyncService: Skipping sync for channel " + channel.getChannelName() + " (synced < 10 seconds ago)");
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "LazadaOrderSyncService: Failed to sync all active channels", e);
        }
    }

    // ── Result type ────────────────────────────────────────────────

    public enum SyncResult { NEW, UPDATED, SKIPPED }
}
