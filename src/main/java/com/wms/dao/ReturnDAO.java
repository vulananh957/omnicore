package com.wms.dao;

import com.wms.model.ReturnItem;
import com.wms.model.ReturnOrder;
import com.wms.util.DBConnection;

import java.math.BigDecimal;
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
 * ReturnDAO — Handles database operations for return orders (return_orders,
 * return_items, qc_records, scrap_records).
 */
public class ReturnDAO {

    private static final Logger LOGGER = Logger.getLogger(ReturnDAO.class.getName());
    private Integer lastInsertedId;

    public Integer getLastInsertedId() {
        return lastInsertedId;
    }

    /**
     * Retrieves all return orders with joined details and items.
     */
    public List<ReturnOrder> findAll() {
        List<ReturnOrder> list = new ArrayList<>();
        String sqlOrders = "SELECT ro.return_id, ro.return_code, ro.order_id, o.order_code, ro.outbound_id, ro.customer_name, ro.customer_phone, "
                + "ro.reason, ro.status, ro.warehouse_id, ro.created_at, ro.updated_at, o.channel "
                + "FROM return_orders ro "
                + "LEFT JOIN orders o ON ro.order_id = o.order_id "
                + "ORDER BY ro.created_at DESC LIMIT 100";

        String sqlItems = "SELECT ri.return_item_id, ri.return_id, ri.product_id, ri.quantity, ri.return_reason, "
                + "p.sku_code, p.product_name, qr.decision, qr.qc_notes "
                + "FROM return_items ri "
                + "JOIN products p ON ri.product_id = p.product_id "
                + "LEFT JOIN qc_records qr ON (ri.return_id = qr.return_id AND ri.product_id = qr.product_id) "
                + "WHERE ri.return_id = ?";

        System.out.println("[ReturnDAO] DEBUG: Executing findAll()...");
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement psOrders = conn.prepareStatement(sqlOrders);
                ResultSet rsOrders = psOrders.executeQuery()) {

            try (PreparedStatement psItems = conn.prepareStatement(sqlItems)) {
                while (rsOrders.next()) {
                    ReturnOrder ro = new ReturnOrder();
                    int returnId = rsOrders.getInt("return_id");
                    ro.setReturnId(returnId);
                    ro.setReturnCode(rsOrders.getString("return_code"));

                    int orderId = rsOrders.getInt("order_id");
                    ro.setOrderId(rsOrders.wasNull() ? null : orderId);
                    ro.setOrderCode(rsOrders.getString("order_code"));

                    int outboundId = rsOrders.getInt("outbound_id");
                    ro.setOutboundId(rsOrders.wasNull() ? null : outboundId);

                    ro.setCustomerName(rsOrders.getString("customer_name"));
                    ro.setCustomerPhone(rsOrders.getString("customer_phone"));
                    ro.setReason(rsOrders.getString("reason"));
                    ro.setStatus(rsOrders.getString("status"));
                    ro.setWarehouseId(rsOrders.getInt("warehouse_id"));
                    ro.setChannel(rsOrders.getString("channel"));

                    Timestamp ca = rsOrders.getTimestamp("created_at");
                    if (ca != null) {
                        ro.setCreatedAt(ca.toLocalDateTime());
                    }
                    Timestamp ua = rsOrders.getTimestamp("updated_at");
                    if (ua != null) {
                        ro.setUpdatedAt(ua.toLocalDateTime());
                    }

                    // Query and set items
                    psItems.setInt(1, returnId);
                    try (ResultSet rsItems = psItems.executeQuery()) {
                        List<ReturnItem> items = new ArrayList<>();
                        while (rsItems.next()) {
                            ReturnItem item = new ReturnItem();
                            item.setReturnItemId(rsItems.getInt("return_item_id"));
                            item.setReturnId(rsItems.getInt("return_id"));
                            item.setProductId(rsItems.getInt("product_id"));
                            item.setQty(rsItems.getBigDecimal("quantity"));
                            item.setReturnReason(rsItems.getString("return_reason"));
                            item.setSkuCode(rsItems.getString("sku_code"));
                            item.setSkuName(rsItems.getString("product_name"));

                            String dec = rsItems.getString("decision");
                            if (dec != null) {
                                item.setQcDecision("PASS".equalsIgnoreCase(dec) ? "resalable" : "defective");
                            } else {
                                item.setQcDecision("pending");
                            }
                            item.setQcNote(rsItems.getString("qc_notes"));
                            items.add(item);
                        }
                        ro.setItems(items);
                    }
                    list.add(ro);
                }
            }

        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "ReturnDAO: Failed to retrieve return orders", e);
            System.out.println("[ReturnDAO] SQL ERROR: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println("[ReturnDAO] DEBUG: Returning " + list.size() + " return orders");
        return list;
    }

    public List<ReturnOrder> findByWarehouse(int warehouseId) {
        List<ReturnOrder> list = new ArrayList<>();
        String sqlOrders = "SELECT ro.return_id, ro.return_code, ro.order_id, o.order_code, ro.outbound_id, ro.customer_name, ro.customer_phone, "
                + "ro.reason, ro.status, ro.warehouse_id, ro.created_at, ro.updated_at, o.channel "
                + "FROM return_orders ro "
                + "LEFT JOIN orders o ON ro.order_id = o.order_id "
                + "WHERE ro.warehouse_id = ? "
                + "ORDER BY ro.created_at DESC LIMIT 100";

        String sqlItems = "SELECT ri.return_item_id, ri.return_id, ri.product_id, ri.quantity, ri.return_reason, "
                + "p.sku_code, p.product_name, qr.decision, qr.qc_notes "
                + "FROM return_items ri "
                + "JOIN products p ON ri.product_id = p.product_id "
                + "LEFT JOIN qc_records qr ON (ri.return_id = qr.return_id AND ri.product_id = qr.product_id) "
                + "WHERE ri.return_id = ?";

        System.out.println("[ReturnDAO] DEBUG: Executing findByWarehouse(" + warehouseId + ")...");
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement psOrders = conn.prepareStatement(sqlOrders)) {

            psOrders.setInt(1, warehouseId);
            try (ResultSet rsOrders = psOrders.executeQuery();
                    PreparedStatement psItems = conn.prepareStatement(sqlItems)) {
                while (rsOrders.next()) {
                    ReturnOrder ro = new ReturnOrder();
                    int returnId = rsOrders.getInt("return_id");
                    ro.setReturnId(returnId);
                    ro.setReturnCode(rsOrders.getString("return_code"));

                    int orderId = rsOrders.getInt("order_id");
                    ro.setOrderId(rsOrders.wasNull() ? null : orderId);
                    ro.setOrderCode(rsOrders.getString("order_code"));

                    int outboundId = rsOrders.getInt("outbound_id");
                    ro.setOutboundId(rsOrders.wasNull() ? null : outboundId);

                    ro.setCustomerName(rsOrders.getString("customer_name"));
                    ro.setCustomerPhone(rsOrders.getString("customer_phone"));
                    ro.setReason(rsOrders.getString("reason"));
                    ro.setStatus(rsOrders.getString("status"));
                    ro.setWarehouseId(rsOrders.getInt("warehouse_id"));
                    ro.setChannel(rsOrders.getString("channel"));

                    Timestamp ca = rsOrders.getTimestamp("created_at");
                    if (ca != null) {
                        ro.setCreatedAt(ca.toLocalDateTime());
                    }
                    Timestamp ua = rsOrders.getTimestamp("updated_at");
                    if (ua != null) {
                        ro.setUpdatedAt(ua.toLocalDateTime());
                    }

                    // Query and set items
                    psItems.setInt(1, returnId);
                    try (ResultSet rsItems = psItems.executeQuery()) {
                        List<ReturnItem> items = new ArrayList<>();
                        while (rsItems.next()) {
                            ReturnItem item = new ReturnItem();
                            item.setReturnItemId(rsItems.getInt("return_item_id"));
                            item.setReturnId(rsItems.getInt("return_id"));
                            item.setProductId(rsItems.getInt("product_id"));
                            item.setQty(rsItems.getBigDecimal("quantity"));
                            item.setReturnReason(rsItems.getString("return_reason"));
                            item.setSkuCode(rsItems.getString("sku_code"));
                            item.setSkuName(rsItems.getString("product_name"));

                            String dec = rsItems.getString("decision");
                            if (dec != null) {
                                item.setQcDecision("PASS".equalsIgnoreCase(dec) ? "resalable" : "defective");
                            } else {
                                item.setQcDecision("pending");
                            }
                            item.setQcNote(rsItems.getString("qc_notes"));
                            items.add(item);
                        }
                        ro.setItems(items);
                    }
                    list.add(ro);
                }
            }

        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "ReturnDAO: Failed to retrieve return orders by warehouse", e);
            System.out.println("[ReturnDAO] SQL ERROR: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println("[ReturnDAO] DEBUG: Returning " + list.size() + " return orders for warehouse " + warehouseId);
        return list;
    }

    /**
     * Inserts a return order and its items in a transaction.
     */
    public boolean insert(ReturnOrder order) {
        Connection conn = null;
        PreparedStatement psOrder = null;
        PreparedStatement psItem = null;

        try {
            conn = DBConnection.getConnection();
            conn.setAutoCommit(false);

            // Resolve order_id from order_code
            Integer orderId = null;
            Integer outboundId = null;
            String sqlFindOrder = "SELECT o.order_id, ob.outbound_id FROM orders o "
                    + "LEFT JOIN outbound_orders ob ON o.order_id = ob.order_id "
                    + "WHERE o.order_code = ?";
            try (PreparedStatement psF = conn.prepareStatement(sqlFindOrder)) {
                psF.setString(1, order.getOrderCode());
                try (ResultSet rs = psF.executeQuery()) {
                    if (rs.next()) {
                        orderId = rs.getInt("order_id");
                        int obId = rs.getInt("outbound_id");
                        if (!rs.wasNull()) {
                            outboundId = obId;
                        }
                    }
                }
            }

            if (orderId == null) {
                LOGGER.warning("ReturnDAO: Original order not found for code: " + order.getOrderCode());
                return false;
            }

            String returnCode = order.getReturnCode();
            if (returnCode == null || returnCode.trim().isEmpty()) {
                String today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
                int seq = (int)(Math.random() * 999);
                returnCode = "RT-" + today + "-" + String.format("%03d", seq);
                order.setReturnCode(returnCode);
            }

            String sqlInsertOrder = "INSERT INTO return_orders (return_code, order_id, outbound_id, customer_name, customer_phone, reason, status, warehouse_id, created_at, updated_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, 'RECEIVED', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";

            psOrder = conn.prepareStatement(sqlInsertOrder, Statement.RETURN_GENERATED_KEYS);
            psOrder.setString(1, returnCode);
            psOrder.setInt(2, orderId);
            if (outboundId != null) {
                psOrder.setInt(3, outboundId);
            } else {
                psOrder.setNull(3, java.sql.Types.INTEGER);
            }
            psOrder.setString(4, order.getCustomerName());
            psOrder.setString(5, order.getCustomerPhone());
            psOrder.setString(6, order.getReason());
            psOrder.setInt(7, order.getWarehouseId() > 0 ? order.getWarehouseId() : 1);

            int rows = psOrder.executeUpdate();
            int returnId = -1;
            if (rows > 0) {
                try (ResultSet rsKeys = psOrder.getGeneratedKeys()) {
                    if (rsKeys.next()) {
                        returnId = rsKeys.getInt(1);
                        this.lastInsertedId = returnId;
                    }
                }
            }

            if (returnId == -1) {
                conn.rollback();
                return false;
            }

            // Insert return items
            String sqlInsertItem = "INSERT INTO return_items (return_id, product_id, quantity, return_reason) VALUES (?, ?, ?, ?)";
            psItem = conn.prepareStatement(sqlInsertItem);

            for (ReturnItem item : order.getItems()) {
                // Resolve product_id from sku_code
                int productId = -1;
                String sqlFindProduct = "SELECT product_id FROM products WHERE sku_code = ?";
                try (PreparedStatement psP = conn.prepareStatement(sqlFindProduct)) {
                    psP.setString(1, item.getSkuCode());
                    try (ResultSet rs = psP.executeQuery()) {
                        if (rs.next()) {
                            productId = rs.getInt("product_id");
                        }
                    }
                }

                if (productId == -1) {
                    LOGGER.warning("ReturnDAO: Product not found for SKU: " + item.getSkuCode());
                    conn.rollback();
                    return false;
                }

                psItem.setInt(1, returnId);
                psItem.setInt(2, productId);
                psItem.setBigDecimal(3, item.getQty() != null ? item.getQty() : BigDecimal.ONE);
                psItem.setString(4, item.getReturnReason());
                psItem.addBatch();
            }

            psItem.executeBatch();
            conn.commit();
            LOGGER.info("ReturnDAO: Inserted return order ID " + returnId + " successfully.");
            return true;

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "ReturnDAO: Error during insert return order", e);
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    LOGGER.log(Level.SEVERE, "Rollback failed", ex);
                }
            }
            return false;
        } finally {
            DBConnection.closeQuietly(psOrder, psItem);
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException ignored) {
                }
            }
        }
    }

    /**
     * Saves the QC results for items and updates return order status.
     */
    public boolean saveQC(int returnId, List<ReturnItem> items, int userId) {
        Connection conn = null;
        PreparedStatement psDel = null;
        PreparedStatement psIns = null;
        PreparedStatement psUpd = null;

        try {
            conn = DBConnection.getConnection();
            conn.setAutoCommit(false);

            // Delete existing QC records for this return order
            String sqlDel = "DELETE FROM qc_records WHERE return_id = ?";
            psDel = conn.prepareStatement(sqlDel);
            psDel.setInt(1, returnId);
            psDel.executeUpdate();

            // Insert new QC records
            String sqlIns = "INSERT INTO qc_records (return_id, product_id, decision, qc_notes, qc_by, qc_at) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";
            psIns = conn.prepareStatement(sqlIns);

            boolean hasResalable = false;
            boolean hasDefective = false;
            boolean hasPending = false;

            for (ReturnItem item : items) {
                if ("pending".equalsIgnoreCase(item.getQcDecision())) {
                    hasPending = true;
                    // Still insert a pending QC record so history is preserved
                    psIns.setInt(1, returnId);
                    psIns.setInt(2, item.getProductId());
                    psIns.setString(3, "PENDING");
                    psIns.setString(4, item.getQcNote());
                    psIns.setInt(5, userId > 0 ? userId : 1);
                    psIns.addBatch();
                    continue;
                }

                String decision = "resalable".equalsIgnoreCase(item.getQcDecision()) ? "PASS" : "FAIL";
                if ("PASS".equals(decision))
                    hasResalable = true;
                else
                    hasDefective = true;

                psIns.setInt(1, returnId);
                psIns.setInt(2, item.getProductId());
                psIns.setString(3, decision);
                psIns.setString(4, item.getQcNote());
                psIns.setInt(5, userId > 0 ? userId : 1);
                psIns.addBatch();
            }

            psIns.executeBatch();

            // Compute return order status based on item decisions
            String status = "INSPECTING";
            if (!hasPending) {
                if (hasResalable && !hasDefective) {
                    status = "PASS";
                } else if (!hasResalable && hasDefective) {
                    status = "FAIL";
                } else if (hasResalable && hasDefective) {
                    status = "INSPECTING"; // mixed
                }
            }

            String sqlUpd = "UPDATE return_orders SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE return_id = ?";
            psUpd = conn.prepareStatement(sqlUpd);
            psUpd.setString(1, status);
            psUpd.setInt(2, returnId);
            psUpd.executeUpdate();

            conn.commit();
            LOGGER.info("ReturnDAO: Saved QC for return ID " + returnId + ", status=" + status);
            return true;

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "ReturnDAO: Error saving QC results", e);
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    LOGGER.log(Level.SEVERE, "Rollback failed", ex);
                }
            }
            return false;
        } finally {
            DBConnection.closeQuietly(psDel, psIns, psUpd);
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException ignored) {
                }
            }
        }
    }

    /**
     * Applies restock for resalable items and scrap for defective items.
     * Updates return order status to RESTOCKED or SCRAPPED.
     */
    /**
     * Submits restock/scrap results for Manager approval.
     * This method only updates the status to PENDING_APPROVAL.
     * The actual UPSERT inventory + INSERT ledger is delegated to
     * LedgerDAO.approveDocument() when Manager clicks "Approve" in /business/ledger.
     *
     * Previously applyRestock() did both, which caused double-count when Manager
     * also clicked approve afterwards.
     */
    public boolean submitRestockForApproval(int returnId) {
        String sql = "UPDATE return_orders SET status = 'PENDING_APPROVAL', updated_at = CURRENT_TIMESTAMP "
                   + "WHERE return_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, returnId);
            int rows = ps.executeUpdate();
            LOGGER.info("ReturnDAO.submitRestockForApproval: returnId=" + returnId + " rows=" + rows);
            return rows > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "ReturnDAO.submitRestockForApproval failed", e);
            return false;
        }
    }

    /** Backward-compat alias: gọi tới submitRestockForApproval. */
    public boolean applyRestock(int returnId, int userId) {
        return submitRestockForApproval(returnId);
    }

    /**
     * Manager approves a restock. Performs the actual UPSERT inventory +
     * INSERT ledger. Called from LedgerServlet when user clicks "Approve"
     * on a return voucher. Body kept identical to the original applyRestock().
     */
    public boolean approveRestock(int returnId, int userId) {
        Connection conn = null;
        PreparedStatement psInventory = null;
        PreparedStatement psLedger = null;
        PreparedStatement psScrap = null;
        PreparedStatement psStatus = null;
        PreparedStatement psOrderUpdate = null;

        try {
            conn = DBConnection.getConnection();
            conn.setAutoCommit(false);

            // 1. Fetch return order header and items with QC decisions
            int warehouseId = 1;
            int orderId = -1;
            String sqlHeader = "SELECT warehouse_id, order_id FROM return_orders WHERE return_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sqlHeader)) {
                ps.setInt(1, returnId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        warehouseId = rs.getInt("warehouse_id");
                        orderId = rs.getInt("order_id");
                    }
                }
            }

            List<ReturnItem> qcItems = new ArrayList<>();
            String sqlItems = "SELECT ri.product_id, ri.quantity, ri.return_reason, qr.decision, qr.qc_notes "
                    + "FROM return_items ri "
                    + "LEFT JOIN qc_records qr ON (ri.return_id = qr.return_id AND ri.product_id = qr.product_id) "
                    + "WHERE ri.return_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sqlItems)) {
                ps.setInt(1, returnId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        ReturnItem item = new ReturnItem();
                        item.setProductId(rs.getInt("product_id"));
                        item.setQty(rs.getBigDecimal("quantity"));
                        item.setReturnReason(rs.getString("return_reason"));

                        String dec = rs.getString("decision");
                        item.setQcDecision(dec != null ? dec : "pending");
                        item.setQcNote(rs.getString("qc_notes"));
                        qcItems.add(item);
                    }
                }
            }

            boolean allDefective = true;

            // 2. Loop through items to apply restock/scrap
            String sqlUpsertInventory = "INSERT INTO inventory (product_id, warehouse_id, qty_on_hand, holding, qty_available, updated_at) "
                    + "VALUES (?, ?, ?, 0, ?, CURRENT_TIMESTAMP) "
                    + "ON DUPLICATE KEY UPDATE qty_on_hand = qty_on_hand + VALUES(qty_on_hand), "
                    + "qty_available = qty_available + VALUES(qty_available), updated_at = CURRENT_TIMESTAMP";
            psInventory = conn.prepareStatement(sqlUpsertInventory);

            String sqlInsertLedger = "INSERT INTO inventory_ledger (inventory_id, product_id, warehouse_id, transaction_type, qty_change, avail_change, created_by, note, timestamp) "
                    + "VALUES (?, ?, ?, 'INBOUND', ?, ?, ?, ?, CURRENT_TIMESTAMP)";
            psLedger = conn.prepareStatement(sqlInsertLedger);

            String sqlInsertScrap = "INSERT INTO scrap_records (return_id, product_id, qty, reason, scrap_by, scrap_at) "
                    + "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";
            psScrap = conn.prepareStatement(sqlInsertScrap);

            for (ReturnItem item : qcItems) {
                String decision = item.getQcDecision();
                if ("PASS".equalsIgnoreCase(decision)) {
                    // A. Increment inventory (Auto Restock on Pass)
                    psInventory.setInt(1, item.getProductId());
                    psInventory.setInt(2, warehouseId);
                    psInventory.setBigDecimal(3, item.getQty());
                    psInventory.setBigDecimal(4, item.getQty());
                    psInventory.executeUpdate();

                    // B. Get inventory ID
                    int inventoryId = -1;
                    String sqlGetInvId = "SELECT inventory_id FROM inventory WHERE product_id = ? AND warehouse_id = ?";
                    try (PreparedStatement psGet = conn.prepareStatement(sqlGetInvId)) {
                        psGet.setInt(1, item.getProductId());
                        psGet.setInt(2, warehouseId);
                        try (ResultSet rs = psGet.executeQuery()) {
                            if (rs.next()) {
                                inventoryId = rs.getInt("inventory_id");
                            }
                        }
                    }

                    // C. Insert inventory ledger record
                    psLedger.setInt(1, inventoryId);
                    psLedger.setInt(2, item.getProductId());
                    psLedger.setInt(3, warehouseId);
                    psLedger.setBigDecimal(4, item.getQty());
                    psLedger.setBigDecimal(5, item.getQty());
                    psLedger.setInt(6, userId > 0 ? userId : 1);
                    psLedger.setString(7, "Restock hàng hoàn trả (RMA #" + returnId + ")");
                    psLedger.executeUpdate();

                } else if ("FAIL".equalsIgnoreCase(decision) || "defective".equalsIgnoreCase(decision)) {
                    // Defective -> Scrap
                    allDefective = false;
                    psScrap.setInt(1, returnId);
                    psScrap.setInt(2, item.getProductId());
                    psScrap.setBigDecimal(3, item.getQty());
                    psScrap.setString(4,
                            item.getQcNote() != null && !item.getQcNote().isEmpty() ? item.getQcNote() : "Hàng lỗi QC");
                    psScrap.setInt(5, userId > 0 ? userId : 1);
                    psScrap.executeUpdate();

                } else {
                    continue;  // PENDING — skip
                }
            }

            // 3. Update return order status
            String nextStatus = allDefective ? "SCRAPPED" : "RESTOCKED";
            String sqlStatus = "UPDATE return_orders SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE return_id = ?";
            psStatus = conn.prepareStatement(sqlStatus);
            psStatus.setString(1, nextStatus);
            psStatus.setInt(2, returnId);
            psStatus.executeUpdate();

            // 4. Update original order status in orders table to RETURNED if orderId is valid
            if (orderId > 0) {
                String sqlOrderUpdate = "UPDATE orders SET status = 'RETURNED', updated_at = CURRENT_TIMESTAMP WHERE order_id = ?";
                psOrderUpdate = conn.prepareStatement(sqlOrderUpdate);
                psOrderUpdate.setInt(1, orderId);
                psOrderUpdate.executeUpdate();
            }

            conn.commit();
            LOGGER.info("ReturnDAO.approveRestock: returnId=" + returnId + " nextStatus=" + nextStatus);
            return true;

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "ReturnDAO.approveRestock failed", e);
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ex) { /* ignore */ }
            }
            return false;
        } finally {
            DBConnection.closeQuietly(psInventory, psLedger, psScrap, psStatus, psOrderUpdate);
            if (conn != null) {
                try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ignored) {}
            }
        }
    }

    public int getWarehouseIdForReturn(int returnId) {
        String sql = "SELECT warehouse_id FROM return_orders WHERE return_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, returnId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("warehouse_id");
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to get warehouse_id for return_id=" + returnId, e);
        }
        return -1;
    }

    /**
     * Checks whether all items in a return order have been inspected (no pending
     * items).
     * Used to gate the apply/restock action.
     */
    public boolean isQCComplete(int returnId) {
        String sql = "SELECT COUNT(*) AS pending_count FROM qc_records "
                + "WHERE return_id = ? AND decision = 'PENDING'";
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, returnId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("pending_count") == 0;
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "ReturnDAO.isQCComplete: failed for returnId=" + returnId, e);
        }
        return false;
    }
}
