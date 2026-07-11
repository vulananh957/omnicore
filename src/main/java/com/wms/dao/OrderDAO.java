package com.wms.dao;

import com.wms.model.Order;
import com.wms.model.OrderItem;
import com.wms.model.Product;
import com.wms.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * OrderDAO — Data Access Object for handling order records from MySQL.
 */
public class OrderDAO extends BaseDAO {

    private static final Logger LOGGER = Logger.getLogger(OrderDAO.class.getName());

    /**
     * Retrieves the top 100 latest orders ordered by created_at descending.
     * Uses try-with-resources to ensure proper connection and resource closing.
     *
     * @return List of Order objects.
     */
    public List<Order> getAllOrders() {        List<Order> list = new ArrayList<>();
        String sqlOrders = "SELECT o.order_id, o.order_code, o.customer_id, o.warehouse_id, w.warehouse_name, o.channel, o.status, o.total_amount, o.note, o.created_by, o.created_at, o.updated_at, "
                           + "o.tracking_no, o.review_note, o.rma_reason, o.rma_physical_status, o.rma_platform_status, o.dispute_evidence_video, o.dispute_note, o.shipment_provider, o.web_order_ref, "
                           + "sd.recipient_name, sd.shipping_address, sd.recipient_phone AS shipping_recipient_phone, u.phone AS customer_phone, u.full_name AS customer_name, "
                           + "lo.customer_phone AS lazada_customer_phone, lo.customer_name AS lazada_customer_name, lo.shipping_address AS lazada_shipping_address "
                           + "FROM orders o "
                           + "LEFT JOIN warehouses w ON o.warehouse_id = w.warehouse_id "
                           + "LEFT JOIN order_shipping_details sd ON o.order_id = sd.order_id "
                           + "LEFT JOIN users u ON o.customer_id = u.user_id "
                           + "LEFT JOIN lazada_orders lo ON o.channel_order_id = lo.lazada_order_id_str "
                           + "ORDER BY o.created_at DESC LIMIT 100";
        String sqlItems = "SELECT p.product_id, p.sku_code, p.product_name, oi.qty, oi.unit_price " +
                          "FROM order_items oi " +
                          "JOIN products p ON oi.product_id = p.product_id " +
                          "WHERE oi.order_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement psOrders = conn.prepareStatement(sqlOrders);
             ResultSet rsOrders = psOrders.executeQuery()) {

            try (PreparedStatement psItems = conn.prepareStatement(sqlItems)) {
                while (rsOrders.next()) {
                    Order order = new Order();
                    int orderId = rsOrders.getInt("order_id");
                    order.setOrderId(orderId);
                    order.setOrderCode(rsOrders.getString("order_code"));
                    
                    int customerId = rsOrders.getInt("customer_id");
                    order.setCustomerId(rsOrders.wasNull() ? null : customerId);
                    
                    order.setWarehouseId(rsOrders.getInt("warehouse_id"));
                    order.setWarehouseName(rsOrders.getString("warehouse_name"));
                    
                    String rawChannel = rsOrders.getString("channel");
                    String note = rsOrders.getString("note");
                    String trackingNo = rsOrders.getString("tracking_no");
                    order.setNote(note);
                    order.setChannel(detectChannel(rawChannel, note, trackingNo));
                    
                    order.setStatus(rsOrders.getString("status"));
                    order.setTotalAmount(rsOrders.getDouble("total_amount"));
                    
                    int createdBy = rsOrders.getInt("created_by");
                    order.setCreatedBy(rsOrders.wasNull() ? null : createdBy);

                    Timestamp ca = rsOrders.getTimestamp("created_at");
                    if (ca != null) {
                        order.setCreatedAt(ca.toLocalDateTime());
                    }
                    
                    Timestamp ua = rsOrders.getTimestamp("updated_at");
                    if (ua != null) {
                        order.setUpdatedAt(ua.toLocalDateTime());
                    }

                    // Custom WMS fields
                    order.setTrackingNo(rsOrders.getString("tracking_no"));
                    order.setShipmentProvider(rsOrders.getString("shipment_provider"));
                    order.setReviewNote(rsOrders.getString("review_note"));
                    order.setRmaReason(rsOrders.getString("rma_reason"));
                    order.setRmaPhysicalStatus(rsOrders.getString("rma_physical_status"));
                    order.setRmaPlatformStatus(rsOrders.getString("rma_platform_status"));
                    order.setDisputeEvidenceVideo(rsOrders.getString("dispute_evidence_video"));
                    order.setDisputeNote(rsOrders.getString("dispute_note"));
                    order.setWebOrderRef(rsOrders.getString("web_order_ref"));

                    // Customer & recipient details — prefer order_shipping_details first, fall back to lazada_orders, then users table
                    String recipientName = rsOrders.getString("recipient_name");
                    String userFullName = rsOrders.getString("customer_name");
                    String lazadaName = rsOrders.getString("lazada_customer_name");
                    String finalCustomerName = (recipientName != null && !recipientName.trim().isEmpty())
                            ? recipientName
                            : ((lazadaName != null && !lazadaName.trim().isEmpty()) ? lazadaName
                              : ((userFullName != null && !userFullName.trim().isEmpty()) ? userFullName
                                : "Khách hàng #" + (order.getCustomerId() != null ? order.getCustomerId() : "N/A")));
                    order.setCustomerName(finalCustomerName);

                    String shippingPhone = rsOrders.getString("shipping_recipient_phone");
                    String customerPhone = rsOrders.getString("customer_phone");
                    String lazadaPhone = rsOrders.getString("lazada_customer_phone");
                    order.setCustomerPhone(
                        (shippingPhone != null && !shippingPhone.trim().isEmpty()) ? shippingPhone
                        : ((customerPhone != null && !customerPhone.trim().isEmpty()) ? customerPhone
                        : ((lazadaPhone != null && !lazadaPhone.trim().isEmpty()) ? lazadaPhone : "Chưa có SĐT")));

                    String shippingAddress = rsOrders.getString("shipping_address");
                    String lazadaAddress = rsOrders.getString("lazada_shipping_address");
                    order.setCustomerAddress(
                        (shippingAddress != null && !shippingAddress.trim().isEmpty()) ? shippingAddress
                        : ((lazadaAddress != null && !lazadaAddress.trim().isEmpty()) ? lazadaAddress : "Chưa có địa chỉ"));

                    // Query and set items
                    psItems.setInt(1, orderId);
                    try (ResultSet rsItems = psItems.executeQuery()) {
                        List<OrderItem> items = new ArrayList<>();
                        while (rsItems.next()) {
                            OrderItem item = new OrderItem();
                            item.setProductId(rsItems.getInt("product_id"));
                            item.setSkuCode(rsItems.getString("sku_code"));
                            item.setProductName(rsItems.getString("product_name"));
                            item.setQuantity(rsItems.getInt("qty"));
                            item.setUnitPrice(rsItems.getDouble("unit_price"));
                            items.add(item);
                        }
                        order.setItems(items);
                    }

                    list.add(order);
                }
            }

        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "OrderDAO: Failed to retrieve orders", e);
        }
        return list;
    }

    /**
     * Lazada end-to-end: returns SHIPPED orders belonging to a single channel.
     * Used by LazadaTrackingPollScheduler to know which orders to poll for
     * delivery confirmation. Lightweight — loads only what the tracker needs.
     */
    public List<Order> findInTransitByChannel(int channelId) {
        List<Order> list = new ArrayList<>();
        String sql = "SELECT order_id, order_code, tracking_no, lazada_package_id, "
                   + "is_rts_pushed, status "
                   + "FROM orders "
                   + "WHERE channel_id = ? AND status = 'SHIPPED' "
                   + "ORDER BY updated_at DESC LIMIT 200";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, channelId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Order o = new Order();
                    o.setOrderId(rs.getInt("order_id"));
                    o.setOrderCode(rs.getString("order_code"));
                    o.setTrackingNo(rs.getString("tracking_no"));
                    o.setLazadaPackageId(rs.getString("lazada_package_id"));
                    o.setRtsPushed(rs.getInt("is_rts_pushed") == 1);
                    o.setStatus(rs.getString("status"));
                    list.add(o);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "OrderDAO.findInTransitByChannel failed", e);
        }
        return list;
    }

    /**
     * Tự động nhận diện kênh bán hàng dựa vào tên kênh thô, ghi chú đơn hàng hoặc mã vận đơn.
     */
    private String detectChannel(String rawChannel, String note, String trackingNo) {
        if (note != null) {
            String noteLower = note.toLowerCase();
            if (noteLower.contains("shopee")) return "Shopee";
            if (noteLower.contains("lazada")) return "Lazada";
            if (noteLower.contains("tiktok")) return "TikTok";
            if (noteLower.contains("website") || noteLower.contains("web")) return "Website";
        }
        if (trackingNo != null) {
            String trackingLower = trackingNo.toLowerCase();
            if (trackingLower.startsWith("lze") || trackingLower.contains("lazada")) return "Lazada";
            if (trackingLower.startsWith("tkt") || trackingLower.contains("tiktok")) return "TikTok";
            if (trackingLower.startsWith("vtp") || trackingLower.contains("viettel")) return "Website";
            if (trackingLower.startsWith("spx") || trackingLower.contains("shopee")) return "Shopee";
        }
        if ("ONLINE".equalsIgnoreCase(rawChannel)) {
            return "Lazada"; // Mặc định là Lazada cho các đơn ONLINE khác nếu không phân tích được
        }
        return rawChannel;
    }

    public boolean updateOrderStatusAndWarehouse(String orderCode, String status, int warehouseId, String reviewNote) {
        return update(LOGGER,
            "UPDATE orders SET status = ?, warehouse_id = ?, review_note = ?, updated_at = CURRENT_TIMESTAMP WHERE order_code = ?",
            status, warehouseId, reviewNote, orderCode) > 0;
    }

    public boolean updateOrderTrackingNo(String orderCode, String trackingNo) {
        // Bỏ logic set status='PACKED' ngầm - tách bạch tracking assignment với print_shipping action
        return update(LOGGER,
            "UPDATE orders SET tracking_no = ?, updated_at = CURRENT_TIMESTAMP WHERE order_code = ?",
            trackingNo, orderCode) > 0;
    }

    /**
     * Updates the shipment_provider field on the orders table.
     * Called after a successful Pack API call to persist the canonical carrier name
     * returned by Lazada (or selected by Sales Staff before packing).
     */
    public boolean updateShipmentProvider(String orderCode, String shipmentProvider) {
        return update(LOGGER,
            "UPDATE orders SET shipment_provider = ?, updated_at = CURRENT_TIMESTAMP WHERE order_code = ?",
            shipmentProvider, orderCode) > 0;
    }

    /**
     * Returns true if a tracking number already exists in the orders table.
     * Used by OrderService to avoid duplicate tracking numbers during server-side generation.
     */
    public boolean existsByTrackingNo(String trackingNo) {
        if (trackingNo == null || trackingNo.trim().isEmpty()) {
            return false;
        }
        String sql = "SELECT 1 FROM orders WHERE tracking_no = ? LIMIT 1";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, trackingNo.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "OrderDAO.existsByTrackingNo failed for " + trackingNo, e);
        }
        return false;
    }

    /**
     * Lazada end-to-end: update package_id + is_pack_requested + is_rts_pushed.
     * Persists the state of the Lazada fulfillment pipeline so retries are safe.
     */
    public boolean updateLazadaPackage(String orderCode, String packageId,
                                       boolean packRequested, boolean rtsPushed) {
        return update(LOGGER,
            "UPDATE orders SET lazada_package_id = ?, "
                + "is_pack_requested = ?, is_rts_pushed = ?, "
                + "updated_at = CURRENT_TIMESTAMP WHERE order_code = ?",
            packageId,
            packRequested ? 1 : 0,
            rtsPushed ? 1 : 0,
            orderCode) > 0;
    }

    /**
     * Mark that the Lazada AWB shipping label has been printed.
     * Called by LazadaLabelServlet after successfully streaming the PDF.
     */
    public boolean markLabelPrinted(String orderCode) {
        return update(LOGGER,
            "UPDATE orders SET is_label_printed = 1, updated_at = CURRENT_TIMESTAMP WHERE order_code = ?",
            orderCode) > 0;
    }

    public boolean updateOrderStatus(String orderCode, String status) {
        return update(LOGGER,
            "UPDATE orders SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE order_code = ?",
            status, orderCode) > 0;
    }

    public boolean updateOrderRMA(String orderCode, String status, String rmaReason, String rmaPhysicalStatus, String rmaPlatformStatus) {
        return update(LOGGER,
            "UPDATE orders SET status = ?, rma_reason = ?, rma_physical_status = ?, rma_platform_status = ?, updated_at = CURRENT_TIMESTAMP WHERE order_code = ?",
            status, rmaReason, rmaPhysicalStatus, rmaPlatformStatus, orderCode) > 0;
    }

    public boolean updateOrderDispute(String orderCode, String status, String video, String note, String platformStatus) {
        return update(LOGGER,
            "UPDATE orders SET status = ?, dispute_evidence_video = ?, dispute_note = ?, rma_platform_status = ?, updated_at = CURRENT_TIMESTAMP WHERE order_code = ?",
            status, video, note, platformStatus, orderCode) > 0;
    }

    /** Manual "Sales/Kho xác nhận đã giao" action — stamps delivered_at for the 7-day return window. */
    public boolean markDelivered(String orderCode) {
        return update(LOGGER,
            "UPDATE orders SET status = 'DELIVERED', delivered_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP "
          + "WHERE order_code = ? AND status = 'SHIPPED'",
            orderCode) > 0;
    }

    /**
     * Used by the storefront confirm-received/return API — status + delivered_at, scoped to
     * web_order_ref IS NOT NULL so a forged orderId belonging to a non-Website order 404s.
     */
    public java.util.Map<String, Object> findDeliveryInfoById(int orderId) {
        return queryOne(LOGGER,
            "SELECT status, delivered_at FROM orders WHERE order_id = ? AND web_order_ref IS NOT NULL",
            rs -> {
                java.util.Map<String, Object> m = new java.util.HashMap<>();
                m.put("status", rs.getString("status"));
                Timestamp deliveredAt = rs.getTimestamp("delivered_at");
                m.put("deliveredAt", deliveredAt == null ? null : deliveredAt.toLocalDateTime());
                return m;
            }, orderId);
    }

    /** Customer clicked "Đã nhận" early — only valid while still DELIVERED (not already COMPLETED/DISPUTED/etc). */
    public boolean markCompletedByOrderId(int orderId) {
        return update(LOGGER,
            "UPDATE orders SET status = 'COMPLETED', updated_at = CURRENT_TIMESTAMP "
          + "WHERE order_id = ? AND web_order_ref IS NOT NULL AND status = 'DELIVERED'",
            orderId) > 0;
    }

    /** Customer submitted a return request — moves the order into Sales review. */
    public boolean markDisputedByOrderId(int orderId) {
        return update(LOGGER,
            "UPDATE orders SET status = 'DISPUTED', updated_at = CURRENT_TIMESTAMP "
          + "WHERE order_id = ? AND web_order_ref IS NOT NULL AND status IN ('DELIVERED', 'COMPLETED')",
            orderId) > 0;
    }

    /** Sales approved the return — feeds the existing physical-return warehouse pipeline. */
    public boolean markReturnedByOrderId(int orderId) {
        return update(LOGGER,
            "UPDATE orders SET status = 'RETURNED', updated_at = CURRENT_TIMESTAMP WHERE order_id = ?",
            orderId) > 0;
    }

    /** Sales rejected the return request — order stands as completed. */
    public boolean revertDisputeToCompleted(int orderId) {
        return update(LOGGER,
            "UPDATE orders SET status = 'COMPLETED', updated_at = CURRENT_TIMESTAMP "
          + "WHERE order_id = ? AND status = 'DISPUTED'",
            orderId) > 0;
    }

    /**
     * Website orders DELIVERED more than 7 days ago with no pending return request —
     * auto-advance to COMPLETED. Scoped to web_order_ref IS NOT NULL only (Lazada untouched).
     */
    public List<Integer> findWebsiteOrderIdsEligibleForAutoComplete() {
        return queryList(LOGGER,
            "SELECT o.order_id FROM orders o "
          + "WHERE o.status = 'DELIVERED' AND o.web_order_ref IS NOT NULL "
          + "AND o.delivered_at IS NOT NULL AND o.delivered_at <= (NOW() - INTERVAL 7 DAY) "
          + "AND NOT EXISTS (SELECT 1 FROM rma_requests r WHERE r.order_id = o.order_id AND r.status = 'PENDING')",
            rs -> rs.getInt("order_id"));
    }

    /**
     * Returns top-selling products by total revenue for the dashboard.
     */
    public List<Product> getTopProducts(int limit) {
        List<Product> list = new ArrayList<>();
        String sql =
            "SELECT p.product_id, p.sku_code, p.product_name, SUM(oi.qty * oi.unit_price) AS total_revenue, COUNT(DISTINCT o.order_id) AS order_count " +
            "FROM order_items oi " +
            "JOIN products p ON oi.product_id = p.product_id " +
            "JOIN orders o ON oi.order_id = o.order_id " +
            "WHERE o.status NOT IN ('CANCELLED','REJECTED') " +
            "GROUP BY p.product_id, p.sku_code, p.product_name " +
            "ORDER BY total_revenue DESC LIMIT ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Product p = new Product();
                    p.setProductId(rs.getInt("product_id"));
                    p.setSkuCode(rs.getString("sku_code"));
                    p.setProductName(rs.getString("product_name"));
                    list.add(p);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "OrderDAO.getTopProducts: failed", e);
        }
        return list;
    }

    public Order findByOrderCode(String orderCode) {
        String sql = "SELECT o.order_id, o.order_code, o.customer_id, o.warehouse_id, w.warehouse_name, "
                   + "o.channel, o.status, o.total_amount, o.note, o.created_by, o.created_at, "
                   + "o.tracking_no, o.review_note, o.channel_id, o.lazada_package_id, "
                   + "o.is_pack_requested, o.is_rts_pushed, o.is_label_printed, o.shipment_provider, o.web_order_ref, "
                   + "sd.recipient_name, sd.shipping_address, sd.recipient_phone AS shipping_recipient_phone, u.phone AS customer_phone, u.full_name AS customer_name, "
                   + "lo.customer_phone AS lazada_customer_phone, lo.customer_name AS lazada_customer_name, lo.shipping_address AS lazada_shipping_address "
                   + "FROM orders o "
                   + "LEFT JOIN warehouses w ON o.warehouse_id = w.warehouse_id "
                   + "LEFT JOIN order_shipping_details sd ON o.order_id = sd.order_id "
                   + "LEFT JOIN users u ON o.customer_id = u.user_id "
                   + "LEFT JOIN lazada_orders lo ON o.channel_order_id = lo.lazada_order_id_str "
                   + "WHERE o.order_code = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, orderCode);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Order order = new Order();
                    order.setOrderId(rs.getInt("order_id"));
                    order.setOrderCode(rs.getString("order_code"));
                    int customerId = rs.getInt("customer_id");
                    order.setCustomerId(rs.wasNull() ? null : customerId);
                    order.setWarehouseId(rs.getInt("warehouse_id"));
                    order.setWarehouseName(rs.getString("warehouse_name"));
                    order.setChannelId(rs.getInt("channel_id"));
                    order.setLazadaPackageId(rs.getString("lazada_package_id"));
                    order.setPackRequested(rs.getInt("is_pack_requested") == 1);
                    order.setRtsPushed(rs.getInt("is_rts_pushed") == 1);
                    order.setLabelPrinted(rs.getInt("is_label_printed") == 1);
                    order.setChannel(detectChannel(rs.getString("channel"), rs.getString("note"), rs.getString("tracking_no")));
                    order.setStatus(rs.getString("status"));
                    order.setTotalAmount(rs.getDouble("total_amount"));
                    int createdBy = rs.getInt("created_by");
                    order.setCreatedBy(rs.wasNull() ? null : createdBy);
                    Timestamp ca = rs.getTimestamp("created_at");
                    if (ca != null) order.setCreatedAt(ca.toLocalDateTime());
                    order.setTrackingNo(rs.getString("tracking_no"));
                    order.setReviewNote(rs.getString("review_note"));
                    String recipientName = rs.getString("recipient_name");
                    String userFullName = rs.getString("customer_name");
                    String lazadaName = rs.getString("lazada_customer_name");
                    String finalCustomerName = (recipientName != null && !recipientName.trim().isEmpty())
                            ? recipientName
                            : ((lazadaName != null && !lazadaName.trim().isEmpty()) ? lazadaName
                              : ((userFullName != null && !userFullName.trim().isEmpty()) ? userFullName : "Khách hàng"));
                    order.setCustomerName(finalCustomerName);
                    String shippingPhone = rs.getString("shipping_recipient_phone");
                    String customerPhone = rs.getString("customer_phone");
                    String lazadaPhone = rs.getString("lazada_customer_phone");
                    order.setCustomerPhone(
                        (shippingPhone != null && !shippingPhone.trim().isEmpty()) ? shippingPhone
                        : ((customerPhone != null && !customerPhone.trim().isEmpty()) ? customerPhone
                        : ((lazadaPhone != null && !lazadaPhone.trim().isEmpty()) ? lazadaPhone : "Chưa có SĐT")));
                    String shippingAddress = rs.getString("shipping_address");
                    String lazadaAddress = rs.getString("lazada_shipping_address");
                    order.setCustomerAddress(
                        (shippingAddress != null && !shippingAddress.trim().isEmpty()) ? shippingAddress
                        : ((lazadaAddress != null && !lazadaAddress.trim().isEmpty()) ? lazadaAddress : "Chưa có địa chỉ"));
                    order.setShipmentProvider(rs.getString("shipment_provider"));
                    order.setWebOrderRef(rs.getString("web_order_ref"));
                    return order;
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "OrderDAO.findByOrderCode: failed for " + orderCode, e);
        }
        return null;
    }

    public List<OrderItem> findItemsByOrderId(int orderId) {
        List<OrderItem> items = new ArrayList<>();
        String sql = "SELECT oi.product_id, p.sku_code, p.product_name, oi.qty, oi.unit_price "
                   + "FROM order_items oi "
                   + "JOIN products p ON oi.product_id = p.product_id "
                   + "WHERE oi.order_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    OrderItem item = new OrderItem();
                    item.setProductId(rs.getInt("product_id"));
                    item.setSkuCode(rs.getString("sku_code"));
                    item.setProductName(rs.getString("product_name"));
                    item.setQuantity(rs.getInt("qty"));
                    item.setUnitPrice(rs.getDouble("unit_price"));
                    items.add(item);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "OrderDAO.findItemsByOrderId: failed for orderId=" + orderId, e);
        }
        return items;
    }
}
