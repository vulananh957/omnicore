package com.wms.dao;

import com.wms.util.DBConnection;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

/**
 * WarehouseIssueDAO — Data Access Object for warehouse issue notes (warehouse_issues).
 * Currently supports SCRAP (disposal) issues.
 */
public class WarehouseIssueDAO {

    private static final Logger LOGGER = Logger.getLogger(WarehouseIssueDAO.class.getName());
    private static final DateTimeFormatter CODE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * Creates a SCRAP (disposal) issue note in DRAFT status with a single line item.
     * Does NOT touch inventory — stock deduction happens later on BM approval.
     *
     * @return the generated issue_code, or null on failure.
     */
    public String createScrapIssue(int warehouseId, int createdBy, int productId, BigDecimal qty, String reason) {
        String issueCode = "XH-" + LocalDate.now().format(CODE_FMT) + "-"
                         + String.format("%03d", (int) (Math.random() * 1000));

        String sqlIssue = "INSERT INTO warehouse_issues "
                        + "(issue_code, warehouse_id, issue_type, created_by, status, created_at) "
                        + "VALUES (?, ?, 'SCRAP', ?, 'APPROVED', NOW())";
        String sqlDetail = "INSERT INTO issue_details (issue_id, product_id, quantity, note) "
                         + "VALUES (?, ?, ?, ?)";
        String sqlFindInv = "SELECT inventory_id FROM inventory WHERE product_id = ? AND warehouse_id = ?";
        String sqlLedger = "INSERT INTO inventory_ledger "
                         + "(inventory_id, product_id, warehouse_id, transaction_type, ledger_type, ref_document_id, qty_change, avail_change, created_by, note, timestamp) "
                         + "VALUES (?, ?, ?, 'OUTBOUND', 'DEFECTIVE', ?, ?, 0, ?, ?, NOW())";

        Connection conn = null;
        try {
            conn = DBConnection.getConnection();
            conn.setAutoCommit(false);

            int issueId;
            try (PreparedStatement ps = conn.prepareStatement(sqlIssue, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, issueCode);
                ps.setInt(2, warehouseId);
                ps.setInt(3, createdBy);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (!rs.next()) { conn.rollback(); return null; }
                    issueId = rs.getInt(1);
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(sqlDetail)) {
                ps.setInt(1, issueId);
                ps.setInt(2, productId);
                ps.setBigDecimal(3, qty);
                ps.setString(4, reason);
                ps.executeUpdate();
            }

            int inventoryId = 0;
            try (PreparedStatement psInv = conn.prepareStatement(sqlFindInv)) {
                psInv.setInt(1, productId);
                psInv.setInt(2, warehouseId);
                try (ResultSet rs = psInv.executeQuery()) {
                    if (rs.next()) {
                        inventoryId = rs.getInt("inventory_id");
                    }
                }
            }

            if (inventoryId > 0) {
                try (PreparedStatement psL = conn.prepareStatement(sqlLedger)) {
                    psL.setInt(1, inventoryId);
                    psL.setInt(2, productId);
                    psL.setInt(3, warehouseId);
                    psL.setInt(4, issueId);
                    psL.setBigDecimal(5, qty != null ? qty.negate() : BigDecimal.ZERO);
                    psL.setInt(6, createdBy);
                    psL.setString(7, "Xuất hủy hàng hỏng: " + (reason != null ? reason : ""));
                    psL.executeUpdate();
                }
            }

            conn.commit();
            LOGGER.info("createScrapIssue: created & approved " + issueCode + " for product " + productId);
            return issueCode;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "WarehouseIssueDAO: Failed to create scrap issue", e);
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ex) { /* ignore */ }
            }
            return null;
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ex) { /* ignore */ }
            }
        }
    }

    public List<Map<String, Object>> findScrapProductsWithQty(int warehouseId) {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT p.product_id, p.sku_code, p.product_name, "
                   + "COALESCE(s.total_scrap, 0) - COALESCE(i.total_issued, 0) AS scrap_qty "
                   + "FROM products p "
                   + "JOIN ("
                   + "    SELECT sr.product_id, SUM(sr.qty) AS total_scrap "
                   + "    FROM scrap_records sr "
                   + "    JOIN return_orders ro ON sr.return_id = ro.return_id "
                   + "    WHERE ro.warehouse_id = ? "
                   + "    GROUP BY sr.product_id"
                   + ") s ON p.product_id = s.product_id "
                   + "LEFT JOIN ("
                   + "    SELECT id.product_id, SUM(id.quantity) AS total_issued "
                   + "    FROM issue_details id "
                   + "    JOIN warehouse_issues wi ON id.issue_id = wi.issue_id "
                   + "    WHERE wi.issue_type = 'SCRAP' AND wi.warehouse_id = ? "
                   + "    GROUP BY id.product_id"
                   + ") i ON p.product_id = i.product_id "
                   + "HAVING scrap_qty > 0";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, warehouseId);
            ps.setInt(2, warehouseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("productId", rs.getInt("product_id"));
                    map.put("skuCode", rs.getString("sku_code"));
                    map.put("productName", rs.getString("product_name"));
                    map.put("scrapQty", rs.getBigDecimal("scrap_qty"));
                    list.add(map);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "WarehouseIssueDAO: Failed to query scrap products", e);
        }
        return list;
    }
}
