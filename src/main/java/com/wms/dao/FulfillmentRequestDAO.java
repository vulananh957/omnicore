package com.wms.dao;

import com.wms.model.FulfillmentRequest;
import com.wms.model.FulfillmentRequestItem;
import com.wms.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * FulfillmentRequestDAO — Data Access Object for fulfillment_requests and
 * fulfillment_request_items tables.
 */
public class FulfillmentRequestDAO {

    private static final Logger LOGGER = Logger.getLogger(FulfillmentRequestDAO.class.getName());

    /**
     * Retrieves all PENDING fulfillment requests with their items.
     */
    public List<FulfillmentRequest> findPending() {
        List<FulfillmentRequest> list = new ArrayList<>();
        String sql = "SELECT request_id, order_id, warehouse_id, status, auto_created, created_at, updated_at "
                   + "FROM fulfillment_requests WHERE status = 'PENDING' ORDER BY created_at DESC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                FulfillmentRequest fr = mapRow(rs);
                fr.setItems(findItemsByRequestId(conn, fr.getRequestId()));
                list.add(fr);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "findPending failed", e);
        }
        return list;
    }

    /**
     * Retrieves PENDING fulfillment requests for one warehouse only.
     */
    public List<FulfillmentRequest> findPendingByWarehouse(int warehouseId) {
        List<FulfillmentRequest> list = new ArrayList<>();
        String sql = "SELECT request_id, order_id, warehouse_id, status, auto_created, created_at, updated_at "
                   + "FROM fulfillment_requests WHERE status = 'PENDING' AND warehouse_id = ? ORDER BY created_at DESC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, warehouseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    FulfillmentRequest fr = mapRow(rs);
                    fr.setItems(findItemsByRequestId(conn, fr.getRequestId()));
                    list.add(fr);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "findPendingByWarehouse failed for warehouse=" + warehouseId, e);
        }
        return list;
    }

    /**
     * Retrieves all fulfillment requests regardless of status.
     */
    public List<FulfillmentRequest> findAll() {
        List<FulfillmentRequest> list = new ArrayList<>();
        String sql = "SELECT request_id, order_id, warehouse_id, status, auto_created, created_at, updated_at "
                   + "FROM fulfillment_requests ORDER BY created_at DESC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                FulfillmentRequest fr = mapRow(rs);
                fr.setItems(findItemsByRequestId(conn, fr.getRequestId()));
                list.add(fr);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "findAll failed", e);
        }
        return list;
    }

    /**
     * Retrieves a single fulfillment request by ID.
     */
    public FulfillmentRequest findById(String requestId) {
        String sql = "SELECT request_id, order_id, warehouse_id, status, auto_created, created_at, updated_at "
                   + "FROM fulfillment_requests WHERE request_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, requestId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    FulfillmentRequest fr = mapRow(rs);
                    fr.setItems(findItemsByRequestId(conn, fr.getRequestId()));
                    return fr;
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "findById failed: " + requestId, e);
        }
        return null;
    }

    /**
     * Updates the status of a fulfillment request.
     */
    public boolean updateStatus(String requestId, String status) {
        String sql = "UPDATE fulfillment_requests SET status = ?, updated_at = NOW() WHERE request_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, status);
            ps.setString(2, requestId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "updateStatus failed: " + requestId, e);
            return false;
        }
    }

    /**
     * Cancels all pending fulfillment requests for a given order.
     * Called when an order is cancelled so warehouse staff no longer sees it.
     */
    public int cancelByOrderId(String orderId) {
        String sql = "UPDATE fulfillment_requests SET status = 'CANCELLED', updated_at = NOW() "
                   + "WHERE order_id = ? AND status = 'PENDING'";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, orderId);
            int updated = ps.executeUpdate();
            if (updated > 0) {
                LOGGER.info("Cancelled " + updated + " fulfillment request(s) for order: " + orderId);
            }
            return updated;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "cancelByOrderId failed: " + orderId, e);
            return 0;
        }
    }

    /**
     * Checks if there is already a PENDING FulfillmentRequest for the given orderCode.
     * Used for idempotency in autoCreateFulfillmentRequest.
     */
    public boolean existsPendingForOrder(String orderCode) {
        String sql = "SELECT 1 FROM fulfillment_requests WHERE order_id = ? AND status = 'PENDING' LIMIT 1";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, orderCode);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "existsPendingForOrder failed: " + orderCode, e);
            return false;
        }
    }

    /**
     * Inserts a new fulfillment request.
     */
    public boolean insert(FulfillmentRequest fr) {
        String sql = "INSERT INTO fulfillment_requests (request_id, order_id, warehouse_id, status, auto_created) "
                   + "VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, fr.getRequestId());
            ps.setString(2, fr.getOrderId());
            ps.setInt(3, fr.getWarehouseId());
            ps.setString(4, fr.getStatus());
            ps.setBoolean(5, fr.isAutoCreated());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "insert failed: " + fr.getRequestId(), e);
            return false;
        }
    }

    /**
     * Seeds 2 test fulfillment requests into the database.
     * Uses INSERT IGNORE so it's safe to call multiple times.
     */
    public void seedTestData() {
        try (Connection conn = DBConnection.getConnection();
             Statement st = conn.createStatement()) {

            // Ensure test SKUs exist
            st.executeUpdate("REPLACE INTO skus (sku_code, product_name, category, unit) VALUES "
                    + "('SKU-TEST-001','Áo Thun Nam Cao Cấp Màu Đen','Thời trang','Cái'),"
                    + "('SKU-TEST-002','Quần Jeans Slim Fit Size 32','Thời trang','Cái')");

            // Seed 2 fulfillment requests
            st.executeUpdate("REPLACE INTO fulfillment_requests (request_id, order_id, warehouse_id, status, auto_created) VALUES "
                    + "('FR-2026-0001','SO-2026-1001',1,'PENDING',0),"
                    + "('FR-2026-0002','SO-2026-1002',1,'PENDING',1)");

            // Seed items for request 1
            st.executeUpdate("REPLACE INTO fulfillment_request_items (request_id, sku_code, sku_name, qty) VALUES "
                    + "('FR-2026-0001','SKU-TEST-001','Áo Thun Nam Cao Cấp Màu Đen',5),"
                    + "('FR-2026-0001','SKU-TEST-002','Quần Jeans Slim Fit Size 32',3)");

            // Seed items for request 2
            st.executeUpdate("REPLACE INTO fulfillment_request_items (request_id, sku_code, sku_name, qty) VALUES "
                    + "('FR-2026-0002','SKU-TEST-001','Áo Thun Nam Cao Cấp Màu Đen',2)");

            LOGGER.info("FulfillmentRequestDAO: Test data seeded (2 requests).");
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "seedTestData failed", e);
        }
    }

    /**
     * Deletes all test fulfillment requests and their items.
     * Use this to clean up after testing.
     */
    public void deleteTestData() {
        try (Connection conn = DBConnection.getConnection();
             Statement st = conn.createStatement()) {

            st.executeUpdate("DELETE FROM fulfillment_request_items WHERE request_id IN ('FR-2026-0001','FR-2026-0002')");
            st.executeUpdate("DELETE FROM fulfillment_requests WHERE request_id IN ('FR-2026-0001','FR-2026-0002')");
            LOGGER.info("FulfillmentRequestDAO: Test data deleted.");
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "deleteTestData failed", e);
        }
    }

    private List<FulfillmentRequestItem> findItemsByRequestId(Connection conn, String requestId) {
        List<FulfillmentRequestItem> items = new ArrayList<>();
        String sql = "SELECT item_id, request_id, sku_code, sku_name, qty "
                   + "FROM fulfillment_request_items WHERE request_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, requestId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    FulfillmentRequestItem item = new FulfillmentRequestItem();
                    item.setItemId(rs.getInt("item_id"));
                    item.setRequestId(rs.getString("request_id"));
                    item.setSkuCode(rs.getString("sku_code"));
                    item.setSkuName(rs.getString("sku_name"));
                    item.setQty(rs.getInt("qty"));
                    items.add(item);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "findItemsByRequestId failed: " + requestId, e);
        }
        return items;
    }

    private FulfillmentRequest mapRow(ResultSet rs) throws SQLException {
        FulfillmentRequest fr = new FulfillmentRequest();
        fr.setRequestId(rs.getString("request_id"));
        fr.setOrderId(rs.getString("order_id"));
        fr.setWarehouseId(rs.getInt("warehouse_id"));
        fr.setStatus(rs.getString("status"));
        fr.setAutoCreated(rs.getBoolean("auto_created"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) fr.setCreatedAt(createdAt.toLocalDateTime());
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        if (updatedAt != null) fr.setUpdatedAt(updatedAt.toLocalDateTime());
        return fr;
    }
}
