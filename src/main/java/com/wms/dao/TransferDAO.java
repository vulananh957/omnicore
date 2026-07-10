package com.wms.dao;

import com.wms.util.DBConnection;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * TransferDAO — Data Access Object for stock transfer records.
 */
public class TransferDAO {

    private static final Logger LOGGER = Logger.getLogger(TransferDAO.class.getName());

    private static final String SQL_FIND_ALL =
        "SELECT st.transfer_id, st.transfer_code, "
      + "st.from_warehouse_id, fw.warehouse_name AS from_warehouse_name, "
      + "st.to_warehouse_id,   tw.warehouse_name AS to_warehouse_name, "
      + "st.created_by, creator.full_name AS creator_name, "
      + "st.approved_by, approver.full_name AS approver_name, "
      + "st.status, st.note, st.created_at, st.completed_at "
      + "FROM stock_transfers st "
      + "LEFT JOIN warehouses fw ON st.from_warehouse_id = fw.warehouse_id "
      + "LEFT JOIN warehouses tw ON st.to_warehouse_id   = tw.warehouse_id "
      + "LEFT JOIN users creator ON st.created_by   = creator.user_id "
      + "LEFT JOIN users approver ON st.approved_by = approver.user_id "
      + "ORDER BY st.created_at DESC LIMIT 200";

    private static final String SQL_FIND_BY_ID =
        "SELECT st.transfer_id, st.transfer_code, "
      + "st.from_warehouse_id, fw.warehouse_name AS from_warehouse_name, "
      + "st.to_warehouse_id,   tw.warehouse_name AS to_warehouse_name, "
      + "st.created_by, creator.full_name AS creator_name, "
      + "st.approved_by, approver.full_name AS approver_name, "
      + "st.status, st.note, st.created_at, st.completed_at "
      + "FROM stock_transfers st "
      + "LEFT JOIN warehouses fw ON st.from_warehouse_id = fw.warehouse_id "
      + "LEFT JOIN warehouses tw ON st.to_warehouse_id   = tw.warehouse_id "
      + "LEFT JOIN users creator ON st.created_by   = creator.user_id "
      + "LEFT JOIN users approver ON st.approved_by = approver.user_id "
      + "WHERE st.transfer_id = ?";

    /**
     * Returns all stock transfers.
     */
    public List<Transfer> findAll() {
        List<Transfer> list = new ArrayList<>();
        String sql = SQL_FIND_ALL;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Transfer t = new Transfer();
                t.setTransferId(rs.getInt("transfer_id"));
                t.setTransferCode(rs.getString("transfer_code"));
                t.setFromWarehouseId(rs.getInt("from_warehouse_id"));
                t.setFromWarehouseName(rs.getString("from_warehouse_name"));
                t.setToWarehouseId(rs.getInt("to_warehouse_id"));
                t.setToWarehouseName(rs.getString("to_warehouse_name"));
                t.setCreatedBy(rs.getInt("created_by"));
                try { t.setCreatorName(rs.getString("creator_name")); } catch (SQLException ignored) {}
                t.setApprovedBy(rs.getObject("approved_by") != null ? rs.getInt("approved_by") : null);
                try { t.setApproverName(rs.getString("approver_name")); } catch (SQLException ignored) {}
                t.setStatus(rs.getString("status"));
                t.setNote(rs.getString("note"));
                java.sql.Timestamp ca = rs.getTimestamp("created_at");
                if (ca != null) t.setCreatedAt(ca.toLocalDateTime());
                java.sql.Timestamp cma = rs.getTimestamp("completed_at");
                if (cma != null) t.setCompletedAt(cma.toLocalDateTime());
                list.add(t);
            }

        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "TransferDAO.findAll: failed", e);
        }
        return list;
    }

    public List<Transfer> findByWarehouseId(int warehouseId) {
        List<Transfer> list = new ArrayList<>();
        String sql = "SELECT st.transfer_id, st.transfer_code, "
                   + "st.from_warehouse_id, fw.warehouse_name AS from_warehouse_name, "
                   + "st.to_warehouse_id,   tw.warehouse_name AS to_warehouse_name, "
                   + "st.created_by, creator.full_name AS creator_name, "
                   + "st.approved_by, approver.full_name AS approver_name, "
                   + "st.status, st.note, st.created_at, st.completed_at "
                   + "FROM stock_transfers st "
                   + "LEFT JOIN warehouses fw ON st.from_warehouse_id = fw.warehouse_id "
                   + "LEFT JOIN warehouses tw ON st.to_warehouse_id   = tw.warehouse_id "
                   + "LEFT JOIN users creator ON st.created_by   = creator.user_id "
                   + "LEFT JOIN users approver ON st.approved_by = approver.user_id "
                   + "WHERE st.from_warehouse_id = ? OR st.to_warehouse_id = ? "
                   + "ORDER BY st.created_at DESC LIMIT 200";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, warehouseId);
            ps.setInt(2, warehouseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Transfer t = new Transfer();
                    t.setTransferId(rs.getInt("transfer_id"));
                    t.setTransferCode(rs.getString("transfer_code"));
                    t.setFromWarehouseId(rs.getInt("from_warehouse_id"));
                    t.setFromWarehouseName(rs.getString("from_warehouse_name"));
                    t.setToWarehouseId(rs.getInt("to_warehouse_id"));
                    t.setToWarehouseName(rs.getString("to_warehouse_name"));
                    t.setCreatedBy(rs.getInt("created_by"));
                    try { t.setCreatorName(rs.getString("creator_name")); } catch (SQLException ignored) {}
                    t.setApprovedBy(rs.getObject("approved_by") != null ? rs.getInt("approved_by") : null);
                    try { t.setApproverName(rs.getString("approver_name")); } catch (SQLException ignored) {}
                    t.setStatus(rs.getString("status"));
                    t.setNote(rs.getString("note"));
                    java.sql.Timestamp ca = rs.getTimestamp("created_at");
                    if (ca != null) t.setCreatedAt(ca.toLocalDateTime());
                    java.sql.Timestamp cma = rs.getTimestamp("completed_at");
                    if (cma != null) t.setCompletedAt(cma.toLocalDateTime());
                    list.add(t);
                }
            }

        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "TransferDAO.findByWarehouseId: failed for warehouseId=" + warehouseId, e);
        }
        return list;
    }

    /**
     * Returns a single transfer by ID.
     */
    public Transfer findById(int transferId) {
        String sql = SQL_FIND_BY_ID;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, transferId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Transfer t = new Transfer();
                    t.setTransferId(rs.getInt("transfer_id"));
                    t.setTransferCode(rs.getString("transfer_code"));
                    t.setFromWarehouseId(rs.getInt("from_warehouse_id"));
                    t.setFromWarehouseName(rs.getString("from_warehouse_name"));
                    t.setToWarehouseId(rs.getInt("to_warehouse_id"));
                    t.setToWarehouseName(rs.getString("to_warehouse_name"));
                t.setCreatedBy(rs.getInt("created_by"));
                try { t.setCreatorName(rs.getString("creator_name")); } catch (SQLException ignored) {}
                t.setApprovedBy(rs.getObject("approved_by") != null ? rs.getInt("approved_by") : null);
                try { t.setApproverName(rs.getString("approver_name")); } catch (SQLException ignored) {}
                t.setStatus(rs.getString("status"));
                t.setNote(rs.getString("note"));
                java.sql.Timestamp ca = rs.getTimestamp("created_at");
                if (ca != null) t.setCreatedAt(ca.toLocalDateTime());
                return t;
                }
            }

        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "TransferDAO.findById: failed for id=" + transferId, e);
        }
        return null;
    }

    /**
     * Returns all transfer items for a given transfer.
     */
    public List<TransferItem> findItemsByTransferId(int transferId) {
        List<TransferItem> list = new ArrayList<>();
        String sql =
            "SELECT sti.transfer_item_id, sti.transfer_id, sti.product_id, "
          + "p.sku_code, p.product_name, sti.shipped_qty, sti.received_qty "
          + "FROM stock_transfer_items sti "
          + "LEFT JOIN products p ON sti.product_id = p.product_id "
          + "WHERE sti.transfer_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, transferId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    TransferItem ti = new TransferItem();
                    ti.setTransferItemId(rs.getInt("transfer_item_id"));
                    ti.setTransferId(rs.getInt("transfer_id"));
                    ti.setProductId(rs.getInt("product_id"));
                    ti.setSkuCode(rs.getString("sku_code"));
                    ti.setProductName(rs.getString("product_name"));
                    ti.setShippedQty(rs.getBigDecimal("shipped_qty"));
                    ti.setReceivedQty(rs.getBigDecimal("received_qty"));
                    list.add(ti);
                }
            }

        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "TransferDAO.findItemsByTransferId: failed for id=" + transferId, e);
        }
        return list;
    }

    /**
     * Inserts a new stock transfer record and returns the generated transfer_id.
     */
    public int insert(Transfer t) throws SQLException {
        String sql = "INSERT INTO stock_transfers "
            + "(transfer_code, from_warehouse_id, to_warehouse_id, created_by, status, note, created_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, t.getTransferCode());
            ps.setInt(2, t.getFromWarehouseId());
            ps.setInt(3, t.getToWarehouseId());
            ps.setInt(4, t.getCreatedBy());
            ps.setString(5, t.getStatus() != null ? t.getStatus() : Transfer.STATUS_DRAFT);
            ps.setString(6, t.getNote());

            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new SQLException("Creating transfer failed, no rows affected.");
            }

            int transferId;
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    transferId = keys.getInt(1);
                } else {
                    throw new SQLException("Creating transfer failed, no ID obtained.");
                }
            }
            return transferId;
        }
    }

    /**
     * Inserts a transfer item row.
     */
    public void insertItem(TransferItem ti) throws SQLException {
        String sql = "INSERT INTO stock_transfer_items "
            + "(transfer_id, product_id, shipped_qty, received_qty) "
            + "VALUES (?, ?, ?, ?)";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, ti.getTransferId());
            ps.setInt(2, ti.getProductId());
            ps.setBigDecimal(3, ti.getShippedQty());
            ps.setBigDecimal(4, ti.getReceivedQty());
            ps.executeUpdate();
        }
    }

    /**
     * Updates the status of a stock transfer.
     */
    public void updateStatus(int transferId, String status) throws SQLException {
        String sql = "UPDATE stock_transfers SET status = ?, completed_at = CURRENT_TIMESTAMP WHERE transfer_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, transferId);
            ps.executeUpdate();
        }
    }

    /**
     * Updates received_qty for a single stock_transfer_items row.
     * Used when the destination warehouse confirms receipt.
     */
    public void updateReceivedQty(int transferItemId, BigDecimal receivedQty) throws SQLException {
        String sql = "UPDATE stock_transfer_items SET received_qty = ? WHERE transfer_item_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBigDecimal(1, receivedQty);
            ps.setInt(2, transferItemId);
            ps.executeUpdate();
        }
    }

    /**
     * Simple domain object for StockTransfer.
     */
    public static class Transfer {
        public static final String STATUS_DRAFT     = "DRAFT";
        public static final String STATUS_IN_TRANSIT = "IN_TRANSIT";
        public static final String STATUS_RECEIVED  = "RECEIVED";
        public static final String STATUS_CANCELLED  = "CANCELLED";

        private int transferId;
        private String transferCode;
        private int fromWarehouseId;
        private String fromWarehouseName;
        private int toWarehouseId;
        private String toWarehouseName;
        private int createdBy;
        private String creatorName;
        private Integer approvedBy;
        private String approverName;
        private String status;
        private String note;
        private java.time.LocalDateTime createdAt;
        private java.time.LocalDateTime completedAt;

        public int getTransferId() { return transferId; }
        public void setTransferId(int transferId) { this.transferId = transferId; }
        public String getTransferCode() { return transferCode; }
        public void setTransferCode(String transferCode) { this.transferCode = transferCode; }
        public int getFromWarehouseId() { return fromWarehouseId; }
        public void setFromWarehouseId(int fromWarehouseId) { this.fromWarehouseId = fromWarehouseId; }
        public String getFromWarehouseName() { return fromWarehouseName; }
        public void setFromWarehouseName(String fromWarehouseName) { this.fromWarehouseName = fromWarehouseName; }
        public int getToWarehouseId() { return toWarehouseId; }
        public void setToWarehouseId(int toWarehouseId) { this.toWarehouseId = toWarehouseId; }
        public String getToWarehouseName() { return toWarehouseName; }
        public void setToWarehouseName(String toWarehouseName) { this.toWarehouseName = toWarehouseName; }
        public int getCreatedBy() { return createdBy; }
        public void setCreatedBy(int createdBy) { this.createdBy = createdBy; }
        public String getCreatorName() { return creatorName; }
        public void setCreatorName(String creatorName) { this.creatorName = creatorName; }
        public Integer getApprovedBy() { return approvedBy; }
        public void setApprovedBy(Integer approvedBy) { this.approvedBy = approvedBy; }
        public String getApproverName() { return approverName; }
        public void setApproverName(String approverName) { this.approverName = approverName; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getNote() { return note; }
        public void setNote(String note) { this.note = note; }
        public java.time.LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(java.time.LocalDateTime createdAt) { this.createdAt = createdAt; }
        public java.time.LocalDateTime getCompletedAt() { return completedAt; }
        public void setCompletedAt(java.time.LocalDateTime completedAt) { this.completedAt = completedAt; }
    }

    /**
     * Simple domain object for TransferItem.
     */
    public static class TransferItem {
        private int transferItemId;
        private int transferId;
        private int productId;
        private String skuCode;
        private String productName;
        private java.math.BigDecimal shippedQty;
        private java.math.BigDecimal receivedQty;

        public int getTransferItemId() { return transferItemId; }
        public void setTransferItemId(int transferItemId) { this.transferItemId = transferItemId; }
        public int getTransferId() { return transferId; }
        public void setTransferId(int transferId) { this.transferId = transferId; }
        public int getProductId() { return productId; }
        public void setProductId(int productId) { this.productId = productId; }
        public String getSkuCode() { return skuCode; }
        public void setSkuCode(String skuCode) { this.skuCode = skuCode; }
        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }
        public java.math.BigDecimal getShippedQty() { return shippedQty; }
        public void setShippedQty(java.math.BigDecimal shippedQty) { this.shippedQty = shippedQty; }
        public java.math.BigDecimal getReceivedQty() { return receivedQty; }
        public void setReceivedQty(java.math.BigDecimal receivedQty) { this.receivedQty = receivedQty; }
    }
}
