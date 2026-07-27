package com.wms.controller.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.wms.dao.ChannelDAO;
import com.wms.dao.InventoryDAO;
import com.wms.mockshipping.MockShippingService;
import com.wms.model.Channel;
import com.wms.util.DBConnection;
import com.wms.util.JsonUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * POST /api/website/orders — creates an order from omnicore-web.
 * GET  /api/website/orders/{id} — order status lookup for the storefront.
 * GET  /api/website/orders/{id}/tracking — tracking code/provider/URL for the storefront.
 *
 * Customer identity is intentionally NOT resolved to a real `users` row (decision:
 * keep the customer models separate — see project memory). Contact info is stored as a
 * snapshot in `order_shipping_details`, the same table already used for Lazada/manual
 * orders that have no real WMS user behind them.
 *
 * Note: the /tracking sub-path used to be its own servlet (WebsiteOrderTrackingServlet)
 * mapped to this SAME url-pattern (/api/website/orders/*) — two servlets can't share one
 * pattern, so Tomcat refused to start the whole webapp ("both mapped to the url-pattern").
 * Folded the tracking logic in here since this servlet already owns the pattern; the old
 * class file is kept (not deleted) but no longer registered in web.xml.
 */
public class WebsiteOrderApiServlet extends BaseApiServlet {

    private static final Logger LOGGER = Logger.getLogger(WebsiteOrderApiServlet.class.getName());
    private static final String PLATFORM = "Website";

    private final ChannelDAO channelDAO = new ChannelDAO();
    private final InventoryDAO inventoryDAO = new InventoryDAO();
    private final MockShippingService mockShippingService = new MockShippingService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        if (authenticateAndReadBody(req, resp) == null) return;

        String pathInfo = req.getPathInfo(); // "/{id}" or "/{id}/tracking"
        String[] parts = pathInfo == null ? null : pathInfo.replaceFirst("^/", "").split("/");

        if (parts != null && parts.length == 2 && "tracking".equals(parts[1])) {
            Integer trackingOrderId = parsePositiveIntOrNull(parts[0]);
            if (trackingOrderId == null) {
                sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid order id");
                return;
            }
            handleTracking(resp, trackingOrderId);
            return;
        }

        Integer orderId = parseOrderId(pathInfo);
        if (orderId == null) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Missing or invalid order id in path");
            return;
        }

        String sql = "SELECT status, delivered_at FROM orders WHERE order_id = ? AND web_order_ref IS NOT NULL";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    sendError(resp, HttpServletResponse.SC_NOT_FOUND, "Order not found");
                    return;
                }
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("order_id", orderId);
                data.put("status", rs.getString("status"));
                java.sql.Timestamp deliveredAt = rs.getTimestamp("delivered_at");
                if (deliveredAt != null) {
                    data.put("delivered_at", deliveredAt.toLocalDateTime().toString());
                    data.put("return_deadline", deliveredAt.toLocalDateTime().plusDays(7).toString());
                } else {
                    data.put("delivered_at", null);
                    data.put("return_deadline", null);
                }
                sendJson(resp, HttpServletResponse.SC_OK, data);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "WebsiteOrderApiServlet.doGet failed for orderId=" + orderId, e);
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Lỗi hệ thống");
        }
    }

    /**
     * GET /api/website/orders/{id}/tracking — {tracking_code, shipment_provider, eta,
     * tracking_url}. Null fields when the order hasn't shipped yet. Folded in from the
     * former WebsiteOrderTrackingServlet (see class javadoc).
     */
    private void handleTracking(HttpServletResponse resp, int orderId) throws IOException {
        String sql = "SELECT order_id, status, tracking_no, shipment_provider, web_order_ref "
                   + "FROM orders WHERE order_id = ? AND web_order_ref IS NOT NULL";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    sendError(resp, HttpServletResponse.SC_NOT_FOUND, "Order not found");
                    return;
                }

                String trackingNo = rs.getString("tracking_no");
                String shipmentProvider = rs.getString("shipment_provider");

                Map<String, Object> data = new LinkedHashMap<>();
                data.put("order_id", orderId);
                data.put("status", rs.getString("status"));

                if (trackingNo != null && !trackingNo.isBlank()) {
                    data.put("tracking_code", trackingNo);
                    data.put("shipment_provider", shipmentProvider);
                    data.put("eta", null); // no source yet — could pull from carrier API later
                    data.put("tracking_url", generateTrackingUrl(shipmentProvider, trackingNo));
                } else {
                    data.put("tracking_code", null);
                    data.put("shipment_provider", null);
                    data.put("eta", null);
                    data.put("tracking_url", null);
                }

                sendJson(resp, HttpServletResponse.SC_OK, data);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "WebsiteOrderApiServlet.handleTracking failed for orderId=" + orderId, e);
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Lỗi hệ thống");
        }
    }

    /** Ví dụ: GHN → https://khachhang.ghn.vn/tracking/..., GHTK → https://track.ghtk.vn/?... */
    private String generateTrackingUrl(String provider, String trackingCode) {
        if (provider == null || provider.isBlank() || trackingCode == null || trackingCode.isBlank()) {
            return null;
        }
        return switch (provider.toLowerCase().trim()) {
            case "ghn" -> "https://khachhang.ghn.vn/tracking/" + trackingCode;
            case "ghtk" -> "https://track.ghtk.vn/?tracking_number=" + trackingCode;
            case "viettel" -> "https://tracking.viettelpost.vn/en/web/tracking/detail/" + trackingCode;
            case "jt" -> "https://tracker.jne.co.id/tracksolv/Tracking.htm?number=" + trackingCode;
            case "grab" -> "https://grab.com/track/shipments/" + trackingCode;
            case "shopee" -> "https://seller.shopee.vn/"; // Shopee không cung cấp tracking URL public
            default -> null;
        };
    }

    private Integer parsePositiveIntOrNull(String s) {
        try {
            int n = Integer.parseInt(s);
            return n > 0 ? n : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String body = authenticateAndReadBody(req, resp);
        if (body == null) return;

        JsonNode node;
        try {
            node = JsonUtil.getMapper().readTree(body);
        } catch (Exception e) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid JSON body");
            return;
        }

        // Optional: customer's chosen mock shipping carrier (com.wms.mockshipping — Website
        // only, isolated package). 0/absent = no carrier chosen (mock off, or checkout didn't
        // send one) — order is created without shipment_provider/shipping_fee, same as before
        // this feature existed.
        int mockCarrierId = node.path("mock_carrier_id").asInt(0);

        String webOrderRef = node.path("web_order_ref").asText(null);
        String customerName = node.path("customer_name").asText(null);
        String customerPhone = node.path("customer_phone").asText(null);
        String customerEmail = node.path("customer_email").asText(null);
        String webCustomerRef = node.path("web_customer_ref").asText(null);
        String customerAddress = node.path("customer_address").asText(null);
        JsonNode itemsNode = node.path("items");

        if (webOrderRef == null || webOrderRef.isBlank()) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "web_order_ref is required");
            return;
        }
        if (customerName == null || customerName.isBlank() || customerPhone == null || customerPhone.isBlank()
                || customerAddress == null || customerAddress.isBlank()) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST,
                    "customer_name, customer_phone, customer_address are required");
            return;
        }
        if (!itemsNode.isArray() || itemsNode.isEmpty()) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "items must be a non-empty array");
            return;
        }

        // Idempotency: a retry with the same web_order_ref returns the existing order
        // instead of creating a duplicate.
        Map<String, Object> existing = findExistingByWebOrderRef(webOrderRef);
        if (existing != null) {
            sendJson(resp, HttpServletResponse.SC_OK, existing);
            return;
        }

        Channel channel = channelDAO.findByPlatform(PLATFORM);
        if (channel == null) {
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Storefront channel not configured");
            return;
        }

        List<int[]> requestedItems = new ArrayList<>(); // {productId, qty}
        List<Double> unitPrices = new ArrayList<>();
        double totalAmount = 0;
        for (JsonNode item : itemsNode) {
            int productId = item.has("product_id") ? item.path("product_id").asInt(-1) : item.path("productId").asInt(-1);
            int qty = item.has("qty") ? item.path("qty").asInt(-1) : item.path("quantity").asInt(-1);
            double unitPrice = item.has("unit_price") ? item.path("unit_price").asDouble(-1)
                    : (item.has("unitPrice") ? item.path("unitPrice").asDouble(-1)
                    : item.path("price").asDouble(-1));
            if (productId <= 0 || qty <= 0 || unitPrice < 0) {
                sendError(resp, HttpServletResponse.SC_BAD_REQUEST,
                        "Each item requires product_id, qty > 0, unit_price >= 0");
                return;
            }
            requestedItems.add(new int[]{productId, qty});
            unitPrices.add(unitPrice);
            totalAmount += qty * unitPrice;
        }

        // Phase A — validate total available stock without soft-allocating.
        // NOTE: No soft-allocation here because the warehouse is not yet assigned at PENDING stage.
        // Soft-allocation happens in OutboundService.autoCreateFromOrder() when the order is approved
        // and a specific warehouse is confirmed. Pre-allocating here caused double-reservation (holding
        // inflated 2x per order) because the approval path always soft-allocates again.
        for (int[] req0 : requestedItems) {
            int productId = req0[0];
            int qty       = req0[1];
            int available = inventoryDAO.getTotalAvailableStock(productId);
            if (available < qty) {
                Map<String, Object> detail = new LinkedHashMap<>();
                detail.put("product_id", productId);
                detail.put("requested", qty);
                detail.put("available", available);
                sendErrorWithDetail(resp, HttpServletResponse.SC_CONFLICT, "Insufficient stock", detail);
                return;
            }
        }

        // Phase B — persist order + items + shipping snapshot in one transaction.
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                double shippingFee = (mockCarrierId <= 0) ? (totalAmount >= 500000 ? 0 : 30000) : 0;
                int orderId = insertOrder(conn, webOrderRef, webCustomerRef, channel.getChannelId(), totalAmount,
                        customerName, customerPhone, customerAddress, shippingFee);
                for (int i = 0; i < requestedItems.size(); i++) {
                    int pId = requestedItems.get(i)[0];
                    insertOrderItem(conn, orderId, pId, requestedItems.get(i)[1], unitPrices.get(i));
                    inventoryDAO.logDeductionForPush(conn, pId, 0, 0);
                }
                conn.commit();

                double finalTotal = totalAmount + shippingFee;
                if (mockCarrierId > 0) {
                    // Best-effort: a failure here shouldn't fail order creation (stock is
                    // already committed) — just means shipment_provider/shipping_fee stay
                    // unset, same as if the customer hadn't picked a carrier.
                    if (mockShippingService.assignCarrier(orderId, mockCarrierId)) {
                        finalTotal += mockShippingService.feeFor(mockCarrierId).doubleValue();
                    } else {
                        LOGGER.warning("WebsiteOrderApiServlet: mock carrier assign failed orderId=" + orderId
                                + " carrierId=" + mockCarrierId);
                    }
                }

                Map<String, Object> data = new LinkedHashMap<>();
                data.put("order_id", orderId);
                data.put("web_order_ref", webOrderRef);
                data.put("status", "PENDING");
                data.put("total_amount", finalTotal);
                sendJson(resp, HttpServletResponse.SC_CREATED, data);
            } catch (SQLException e) {
                conn.rollback();
                LOGGER.log(Level.SEVERE, "WebsiteOrderApiServlet: failed to persist order webOrderRef=" + webOrderRef, e);
                sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Lỗi hệ thống khi tạo đơn hàng");
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "WebsiteOrderApiServlet: DB connection failed webOrderRef=" + webOrderRef, e);
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Lỗi hệ thống khi tạo đơn hàng");
        }
    }

    private Map<String, Object> findExistingByWebOrderRef(String webOrderRef) {
        String sql = "SELECT order_id, status, total_amount FROM orders WHERE web_order_ref = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, webOrderRef);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("order_id", rs.getInt("order_id"));
                    data.put("web_order_ref", webOrderRef);
                    data.put("status", rs.getString("status"));
                    data.put("total_amount", rs.getBigDecimal("total_amount"));
                    data.put("message", "Order already exists");
                    return data;
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "WebsiteOrderApiServlet: idempotency check failed for " + webOrderRef, e);
        }
        return null;
    }

    private int insertOrder(Connection conn, String webOrderRef, String webCustomerRef, int channelId, double totalAmount,
                             String customerName, String customerPhone, String customerAddress, double shippingFee)
            throws SQLException {
        // channel='WEBSITE' (not 'ONLINE') — OrderDAO.detectChannel() auto-labels raw
        // channel='ONLINE' as "Lazada" for admin display, which would mislabel website orders.
        String sql = "INSERT INTO orders (order_code, channel, status, total_amount, channel_id, "
                + "web_order_ref, web_customer_ref, sync_status, shipping_fee) "
                + "VALUES (?, 'WEBSITE', 'PENDING', ?, ?, ?, ?, 'SYNCED', ?)";
        int orderId = 0;
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, webOrderRef);
            ps.setDouble(2, totalAmount + shippingFee);
            ps.setInt(3, channelId);
            ps.setString(4, webOrderRef);
            ps.setString(5, webCustomerRef);
            ps.setDouble(6, shippingFee);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    orderId = keys.getInt(1);
                }
            }
        }
        if (orderId == 0) {
            throw new SQLException("Insert into orders did not return a generated key");
        }

        // Insert shipping details into order_shipping_details
        String sqlShipping = "INSERT INTO order_shipping_details (order_id, recipient_name, recipient_phone, shipping_address, shipping_status) "
                + "VALUES (?, ?, ?, ?, 'PENDING')";
        try (PreparedStatement ps = conn.prepareStatement(sqlShipping)) {
            ps.setInt(1, orderId);
            ps.setString(2, customerName);
            ps.setString(3, customerPhone);
            ps.setString(4, customerAddress);
            ps.executeUpdate();
        }

        return orderId;
    }

    private void insertOrderItem(Connection conn, int orderId, int productId, int qty, double unitPrice) throws SQLException {
        String sql = "INSERT INTO order_items (order_id, product_id, qty, unit_price) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            ps.setInt(2, productId);
            ps.setInt(3, qty);
            ps.setDouble(4, unitPrice);
            ps.executeUpdate();
        }
    }

    private void deleteOrder(int orderId) {
        String sqlItems = "DELETE FROM order_items WHERE order_id = ?";
        String sqlShipping = "DELETE FROM order_shipping_details WHERE order_id = ?";
        String sqlOrder = "DELETE FROM orders WHERE order_id = ?";
        try (Connection conn = DBConnection.getConnection()) {
            try (PreparedStatement ps1 = conn.prepareStatement(sqlItems);
                 PreparedStatement ps2 = conn.prepareStatement(sqlShipping);
                 PreparedStatement ps3 = conn.prepareStatement(sqlOrder)) {
                ps1.setInt(1, orderId);
                ps1.executeUpdate();
                ps2.setInt(1, orderId);
                ps2.executeUpdate();
                ps3.setInt(1, orderId);
                ps3.executeUpdate();
                LOGGER.info("deleteOrder: rolled back order " + orderId);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "deleteOrder: failed to rollback order " + orderId, e);
        }
    }

    private void sendErrorWithDetail(HttpServletResponse resp, int status, String message, Map<String, Object> detail)
            throws IOException {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("success", false);
        envelope.put("message", message);
        envelope.put("detail", detail);
        resp.setStatus(status);
        resp.setContentType("application/json;charset=UTF-8");
        try (var w = resp.getWriter()) {
            w.print(JsonUtil.getMapper().writeValueAsString(envelope));
        }
    }

    private Integer parseOrderId(String pathInfo) {
        if (pathInfo == null || pathInfo.isBlank() || pathInfo.equals("/")) return null;
        String raw = pathInfo.startsWith("/") ? pathInfo.substring(1) : pathInfo;
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
