package com.wms.controller.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.wms.dao.ChannelDAO;
import com.wms.dao.InventoryDAO;
import com.wms.dao.WarehouseDAO;
import com.wms.model.Channel;
import com.wms.model.Warehouse;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * POST /api/website/orders — creates an order from omnicore-web.
 * GET  /api/website/orders/{id} — order status lookup for the storefront.
 *
 * Customer identity is intentionally NOT resolved to a real `users` row (decision:
 * keep the customer models separate — see project memory). Contact info is stored as a
 * snapshot in `order_shipping_details`, the same table already used for Lazada/manual
 * orders that have no real WMS user behind them.
 */
public class WebsiteOrderApiServlet extends BaseApiServlet {

    private static final Logger LOGGER = Logger.getLogger(WebsiteOrderApiServlet.class.getName());
    private static final String PLATFORM = "OwnWebsite";

    private final ChannelDAO channelDAO = new ChannelDAO();
    private final WarehouseDAO warehouseDAO = new WarehouseDAO();
    private final InventoryDAO inventoryDAO = new InventoryDAO();

    private record AllocatedItem(int productId, int warehouseId, int qty) {}

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        if (authenticateAndReadBody(req, resp) == null) return;

        String pathInfo = req.getPathInfo();
        Integer orderId = parseOrderId(pathInfo);
        if (orderId == null) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Missing or invalid order id in path");
            return;
        }

        String sql = "SELECT status FROM orders WHERE order_id = ? AND web_order_ref IS NOT NULL";
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
                sendJson(resp, HttpServletResponse.SC_OK, data);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "WebsiteOrderApiServlet.doGet failed for orderId=" + orderId, e);
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Lỗi hệ thống");
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
            int productId = item.path("product_id").asInt(-1);
            int qty = item.path("qty").asInt(-1);
            double unitPrice = item.path("unit_price").asDouble(-1);
            if (productId <= 0 || qty <= 0 || unitPrice < 0) {
                sendError(resp, HttpServletResponse.SC_BAD_REQUEST,
                        "Each item requires product_id, qty > 0, unit_price >= 0");
                return;
            }
            requestedItems.add(new int[]{productId, qty});
            unitPrices.add(unitPrice);
            totalAmount += qty * unitPrice;
        }

        // Phase A — reserve stock across active warehouses before writing anything.
        List<Warehouse> activeWarehouses = warehouseDAO.findAll().stream()
                .filter(Warehouse::isActive)
                .toList();

        List<AllocatedItem> allocated = new ArrayList<>();
        for (int[] req0 : requestedItems) {
            int productId = req0[0];
            int qty = req0[1];
            Warehouse chosen = activeWarehouses.stream()
                    .filter(w -> inventoryDAO.getAvailableStock(productId, w.getWarehouseId()) >= qty)
                    .max(Comparator.comparingInt(w -> inventoryDAO.getAvailableStock(productId, w.getWarehouseId())))
                    .orElse(null);

            boolean ok = chosen != null && inventoryDAO.softAllocateInventory(productId, chosen.getWarehouseId(), qty);
            if (!ok) {
                releaseAll(allocated);
                int available = inventoryDAO.getTotalAvailableStock(productId);
                Map<String, Object> detail = new LinkedHashMap<>();
                detail.put("product_id", productId);
                detail.put("requested", qty);
                detail.put("available", available);
                sendErrorWithDetail(resp, HttpServletResponse.SC_CONFLICT, "Insufficient stock", detail);
                return;
            }
            allocated.add(new AllocatedItem(productId, chosen.getWarehouseId(), qty));
        }

        // Phase B — persist order + items + shipping snapshot in one transaction.
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int orderId = insertOrder(conn, webOrderRef, webCustomerRef, channel.getChannelId(), totalAmount,
                        customerName, customerPhone, customerAddress);
                for (int i = 0; i < requestedItems.size(); i++) {
                    insertOrderItem(conn, orderId, requestedItems.get(i)[0], requestedItems.get(i)[1], unitPrices.get(i));
                }
                conn.commit();

                Map<String, Object> data = new LinkedHashMap<>();
                data.put("order_id", orderId);
                data.put("web_order_ref", webOrderRef);
                data.put("status", "PENDING");
                data.put("total_amount", totalAmount);
                sendJson(resp, HttpServletResponse.SC_CREATED, data);
            } catch (SQLException e) {
                conn.rollback();
                releaseAll(allocated);
                LOGGER.log(Level.SEVERE, "WebsiteOrderApiServlet: failed to persist order webOrderRef=" + webOrderRef, e);
                sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Lỗi hệ thống khi tạo đơn hàng");
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            releaseAll(allocated);
            LOGGER.log(Level.SEVERE, "WebsiteOrderApiServlet: DB connection failed webOrderRef=" + webOrderRef, e);
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Lỗi hệ thống khi tạo đơn hàng");
        }
    }

    private void releaseAll(List<AllocatedItem> allocated) {
        for (AllocatedItem a : allocated) {
            inventoryDAO.releaseSoftAllocateInventory(a.productId(), a.warehouseId(), java.math.BigDecimal.valueOf(a.qty()));
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
                             String customerName, String customerPhone, String customerAddress)
            throws SQLException {
        // channel='WEBSITE' (not 'ONLINE') — OrderDAO.detectChannel() auto-labels raw
        // channel='ONLINE' as "Lazada" for admin display, which would mislabel website orders.
        String sql = "INSERT INTO orders (order_code, channel, status, total_amount, channel_id, "
                + "web_order_ref, web_customer_ref, sync_status, customer_name, customer_phone, customer_address) "
                + "VALUES (?, 'WEBSITE', 'PENDING', ?, ?, ?, ?, 'SYNCED', ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, webOrderRef);
            ps.setDouble(2, totalAmount);
            ps.setInt(3, channelId);
            ps.setString(4, webOrderRef);
            ps.setString(5, webCustomerRef);
            ps.setString(6, customerName);
            ps.setString(7, customerPhone);
            ps.setString(8, customerAddress);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
        throw new SQLException("Insert into orders did not return a generated key");
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
