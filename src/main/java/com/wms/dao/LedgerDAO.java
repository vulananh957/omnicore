package com.wms.dao;

import com.wms.util.DBConnection;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * LedgerDAO — Data Access Object for the Stock Ledger view.
 *
 * Read-only. All inventory mutations are handled by warehouse operation
 * servlets directly.
 */
public class LedgerDAO {

    private static final Logger LOGGER = Logger.getLogger(LedgerDAO.class.getName());
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /**
     * DTO for representing a unified Warehouse Document.
     */
    public static class LedgerDocument {
        public String id;
        public String type; // "Phiếu Nhập Kho" | "Phiếu Xuất Kho" | "Phiếu Kiểm Kê" | "Phiếu Chuyển Kho" | "Phiếu Hoàn Hàng"
        public String date;
        public String warehouse;
        public int warehouseId;
        public String createdBy;
        public int createdById;
        public int items;
        public String status;
        public String statusColor;
        public String remarks;
        public String supplier;
        public String supplierAddress;
        public String supplierPhone;
        public String supplierEmail;
        public String poReference;
        public String paymentTerms;
        public String expectedDate;
        public String customer;
        public String poCode;
        public String supplierCode;
        public String contactPerson;
        public String proposal;
        public String inboundCode;
        public String orderCode;
        public String fromWarehouse;
        public String fromWarehouseCode;
        public String fromAddress;
        public String toWarehouse;
        public String toWarehouseCode;
        public String toAddress;
        public String warehouseCode;
        public String warehouseAddress;
        public String approver;
        public String completedDate;
        public String buyerName;
        public String buyerPhone;
        public String buyerAddress;
        public String lazadaOrderNumber;
        public String receiver;
        public String shippingAddress;
        public String customerPhone;
        public List<Map<String, Object>> itemsList = new ArrayList<>();
    }

    /**
     * DTO for Global Inventory Ledger Entry.
     */
    public static class GlobalLedgerEntry {
        public int ledgerId;
        public String sku;
        public String productName;
        public String warehouse;
        public String type; // "INBOUND" | "OUTBOUND" | "ADJUSTMENT" | "TRANSFER_IN" | "TRANSFER_OUT"
        public double qtyChange;
        public double availChange;
        public String createdBy;
        public String timestamp;
        public String note;
    }

    /**
     * Fetch all documents from all tables, mapping them to a unified format.
     * (Business Manager view — every warehouse.)
     */
    public List<LedgerDocument> findAllDocuments() {
        return findDocuments(null);
    }

    /**
     * Warehouse Staff view — documents scoped to ONE warehouse.
     * Stock transfers match when the warehouse is either the source OR the destination.
     */
    public List<LedgerDocument> findAllDocuments(int warehouseId) {
        return findDocuments(warehouseId);
    }

    /**
     * Aggregates documents from all 6 sources. When {@code warehouseId} is null, returns
     * documents of every warehouse; otherwise scopes each source to that warehouse
     * (stock transfers match on either from_warehouse_id or to_warehouse_id).
     */
    private List<LedgerDocument> findDocuments(Integer warehouseId) {
        List<LedgerDocument> docs = new ArrayList<>();
        boolean byWh = warehouseId != null;

        // 1. Fetch Inbound Orders
        String sqlInbound =
            "SELECT io.inbound_id, io.inbound_code, io.supplier, io.po_reference, io.status, io.note, io.created_at, io.expected_date, " +
            "w.warehouse_id, w.warehouse_name, u.full_name AS creator_name, " +
            "s.supplier_code, s.contact_person, s.phone, s.email, s.address, s.payment_terms AS supplier_payment_terms " +
            "FROM inbound_orders io " +
            "LEFT JOIN warehouses w ON io.warehouse_id = w.warehouse_id " +
            "LEFT JOIN users u ON io.created_by = u.user_id " +
            "LEFT JOIN suppliers s ON io.supplier_id = s.supplier_id " +
            (byWh ? "WHERE io.warehouse_id = ? " : "") +
            "ORDER BY io.created_at DESC LIMIT 100";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlInbound)) {
            if (byWh) ps.setInt(1, warehouseId);
            try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                LedgerDocument d = new LedgerDocument();
                d.id = rs.getString("inbound_code");
                d.type = "Phiếu Nhập Kho";

                Timestamp ca = rs.getTimestamp("created_at");
                d.date = (ca != null) ? ca.toLocalDateTime().format(DATE_FORMATTER) : "";
                d.warehouse = rs.getString("warehouse_name");
                d.warehouseId = rs.getInt("warehouse_id");

                String creator = rs.getString("creator_name");
                d.createdBy = (creator != null) ? creator : "Hệ thống";
                d.status = mapInboundStatus(rs.getString("status"));
                d.statusColor = getStatusColor(d.status);
                d.remarks = rs.getString("note");
                d.supplier = rs.getString("supplier");
                d.supplierCode = rs.getString("supplier_code");
                d.supplierAddress = rs.getString("address");
                d.supplierPhone = rs.getString("phone");
                d.supplierEmail = rs.getString("email");
                d.contactPerson = rs.getString("contact_person");
                d.poReference = rs.getString("po_reference");
                d.paymentTerms = rs.getString("supplier_payment_terms");
                var ed = rs.getDate("expected_date");
                d.expectedDate = (ed != null) ? ed.toLocalDate().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) : null;
                d.items = countInboundItems(conn, rs.getInt("inbound_id"));
                d.itemsList = loadInboundItems(conn, rs.getInt("inbound_id"));
                docs.add(d);
            }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "LedgerDAO: Failed to fetch inbound documents", e);
        }

        // 2. Fetch Outbound Orders
        String sqlOutbound =
            "SELECT oo.outbound_id, oo.outbound_code, oo.status, oo.note, oo.created_at, " +
            "w.warehouse_id, w.warehouse_name, w.warehouse_code, w.address, " +
            "u.full_name AS creator_name, o.channel, o.order_code AS ref_order_code, " +
            "lo.lazada_order_number, " +
            "lo.customer_name AS lazada_buyer_name, lo.customer_phone AS lazada_buyer_phone, lo.shipping_address AS lazada_buyer_address, " +
            "sd.recipient_name AS sd_recipient_name, sd.shipping_address AS sd_shipping_address, " +
            "u2.phone AS customer_phone, u2.full_name AS customer_name " +
            "FROM outbound_orders oo " +
            "LEFT JOIN warehouses w ON oo.warehouse_id = w.warehouse_id " +
            "LEFT JOIN orders o ON oo.order_id = o.order_id " +
            "LEFT JOIN lazada_orders lo ON lo.lazada_order_id_str = o.channel_order_id " +
            "LEFT JOIN order_shipping_details sd ON oo.order_id = sd.order_id " +
            "LEFT JOIN users u2 ON o.customer_id = u2.user_id " +
            "LEFT JOIN users u ON oo.created_by = u.user_id " +
            (byWh ? "WHERE oo.warehouse_id = ? " : "") +
            "ORDER BY oo.created_at DESC LIMIT 100";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlOutbound)) {
            if (byWh) ps.setInt(1, warehouseId);
            try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                LedgerDocument d = new LedgerDocument();
                d.id = rs.getString("outbound_code");
                if (d.id == null) {
                    d.id = "SOUT-OUT-" + rs.getInt("outbound_id");
                }
                d.type = "Phiếu Xuất Kho";

                Timestamp ca = rs.getTimestamp("created_at");
                d.date = (ca != null) ? ca.toLocalDateTime().format(DATE_FORMATTER) : "";
                d.warehouse = rs.getString("warehouse_name");
                d.warehouseCode = rs.getString("warehouse_code");
                d.warehouseAddress = rs.getString("address");
                d.warehouseId = rs.getInt("warehouse_id");

                String creator = rs.getString("creator_name");
                d.createdBy = (creator != null) ? creator : "Hệ thống";
                d.remarks = rs.getString("note");
                d.customer = rs.getString("channel");
                if (d.customer == null) d.customer = "Khách mua lẻ";
                d.orderCode = rs.getString("ref_order_code");
                d.lazadaOrderNumber = rs.getString("lazada_order_number");

                String lazadaName = rs.getString("lazada_buyer_name");
                String lazadaPhone = rs.getString("lazada_buyer_phone");
                String lazadaAddr = rs.getString("lazada_buyer_address");

                String sdName = rs.getString("sd_recipient_name");
                String sdAddr = rs.getString("sd_shipping_address");
                String userPhone = rs.getString("customer_phone");
                String userName = rs.getString("customer_name");

                String name = "Khách mua lẻ";
                if (lazadaName != null && !lazadaName.isEmpty()) {
                    name = lazadaName;
                } else if (sdName != null && !sdName.isEmpty()) {
                    name = sdName;
                } else if (userName != null && !userName.isEmpty()) {
                    name = userName;
                }

                String phone = "";
                if (lazadaPhone != null && !lazadaPhone.isEmpty()) {
                    phone = lazadaPhone;
                } else if (userPhone != null && !userPhone.isEmpty()) {
                    phone = userPhone;
                }

                String address = "Khu vực hàng thường";
                if (lazadaAddr != null && !lazadaAddr.isEmpty()) {
                    address = lazadaAddr;
                } else if (sdAddr != null && !sdAddr.isEmpty()) {
                    address = sdAddr;
                }

                d.buyerName = name;
                d.buyerPhone = phone;
                d.buyerAddress = address;

                d.receiver = name;
                d.customerPhone = phone;
                d.shippingAddress = address;

                d.poReference = (d.lazadaOrderNumber != null && !d.lazadaOrderNumber.isEmpty()) ? d.lazadaOrderNumber : d.orderCode;

                String dbStatus = rs.getString("status");
                if (isOmnichannelChannel(d.customer)) {
                    if ("PENDING".equals(dbStatus) || "PICKING".equals(dbStatus) || "PACKED".equals(dbStatus)) {
                        d.status = "Đã duyệt";
                    } else if ("SHIPPED".equals(dbStatus) || "DELIVERED".equals(dbStatus)) {
                        d.status = "Hoàn thành";
                    } else {
                        d.status = mapOutboundStatus(dbStatus);
                    }
                } else {
                    d.status = mapOutboundStatus(dbStatus);
                }

                d.statusColor = getStatusColor(d.status);
                d.items = countOutboundItems(conn, rs.getInt("outbound_id"));
                try { d.itemsList = loadOutboundItems(conn, rs.getInt("outbound_id")); } catch (Exception ex) { d.itemsList = new java.util.ArrayList<>(); }
                docs.add(d);
            }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "LedgerDAO: Failed to fetch outbound documents", e);
        }

        // 3. Fetch Stock Transfers
        String sqlTransfers =
            "SELECT st.transfer_id, st.transfer_code, st.status, st.note, st.created_at, st.completed_at, " +
            "st.from_warehouse_id, w1.warehouse_name AS from_wh, w1.warehouse_code AS from_code, w1.address AS from_addr, " +
            "st.to_warehouse_id, w2.warehouse_name AS to_wh, w2.warehouse_code AS to_code, w2.address AS to_addr, " +
            "u.full_name AS creator_name, ua.full_name AS approver_name " +
            "FROM stock_transfers st " +
            "LEFT JOIN warehouses w1 ON st.from_warehouse_id = w1.warehouse_id " +
            "LEFT JOIN warehouses w2 ON st.to_warehouse_id = w2.warehouse_id " +
            "LEFT JOIN users u ON st.created_by = u.user_id " +
            "LEFT JOIN users ua ON st.approved_by = ua.user_id " +
            (byWh ? "WHERE (st.from_warehouse_id = ? OR st.to_warehouse_id = ?) " : "") +
            "ORDER BY st.created_at DESC LIMIT 100";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlTransfers)) {
            if (byWh) { ps.setInt(1, warehouseId); ps.setInt(2, warehouseId); }
            try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                LedgerDocument d = new LedgerDocument();
                d.id = rs.getString("transfer_code");
                d.type = "Phiếu Chuyển Kho";

                Timestamp ca = rs.getTimestamp("created_at");
                d.date = (ca != null) ? ca.toLocalDateTime().format(DATE_FORMATTER) : "";
                d.warehouse = rs.getString("from_wh") + " → " + rs.getString("to_wh");
                d.warehouseId = rs.getInt("from_warehouse_id");

                d.fromWarehouse = rs.getString("from_wh");
                d.fromWarehouseCode = rs.getString("from_code");
                d.fromAddress = rs.getString("from_addr");
                d.toWarehouse = rs.getString("to_wh");
                d.toWarehouseCode = rs.getString("to_code");
                d.toAddress = rs.getString("to_addr");

                String creator = rs.getString("creator_name");
                d.createdBy = (creator != null) ? creator : "Hệ thống";
                d.status = mapTransferStatus(rs.getString("status"));
                d.statusColor = getStatusColor(d.status);
                d.remarks = rs.getString("note");
                d.approver = rs.getString("approver_name");

                Timestamp completed = rs.getTimestamp("completed_at");
                d.completedDate = (completed != null) ? completed.toLocalDateTime().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) : null;

                d.items = countTransferItems(conn, rs.getInt("transfer_id"));
                d.itemsList = loadTransferItems(conn, rs.getInt("transfer_id"));
                docs.add(d);
            }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "LedgerDAO: Failed to fetch transfer documents", e);
        }

        // 4. Fetch Physical Inventories
        String sqlPhysical =
            "SELECT pi.inventory_check_id, pi.check_code, pi.status, pi.note, pi.created_at, " +
            "w.warehouse_id, w.warehouse_name, w.warehouse_code, w.address, " +
            "u.full_name AS creator_name, ua.full_name AS approver_name " +
            "FROM physical_inventories pi " +
            "LEFT JOIN warehouses w ON pi.warehouse_id = w.warehouse_id " +
            "LEFT JOIN users u ON pi.created_by = u.user_id " +
            "LEFT JOIN users ua ON pi.created_by = ua.user_id " +
            (byWh ? "WHERE pi.warehouse_id = ? " : "") +
            "ORDER BY pi.created_at DESC LIMIT 100";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlPhysical)) {
            if (byWh) ps.setInt(1, warehouseId);
            LOGGER.info("[LedgerDAO] Physical query warehouseId=" + warehouseId + " sql=" + sqlPhysical.replace("\n", " "));
            try (ResultSet rs = ps.executeQuery()) {
                int count = 0;
                while (rs.next()) {
                    count++;
                    LedgerDocument d = new LedgerDocument();
                    d.id = rs.getString("check_code");
                    d.type = "Phiếu Kiểm Kê";

                    Timestamp ca = rs.getTimestamp("created_at");
                    d.date = (ca != null) ? ca.toLocalDateTime().format(DATE_FORMATTER) : "";
                    d.warehouse = rs.getString("warehouse_name");
                    d.warehouseCode = rs.getString("warehouse_code");
                    d.warehouseAddress = rs.getString("address");
                    d.warehouseId = rs.getInt("warehouse_id");

                    String creator = rs.getString("creator_name");
                    d.createdBy = (creator != null) ? creator : "Hệ thống";
                    d.approver = rs.getString("approver_name");
                    d.status = mapPhysicalStatus(rs.getString("status"));
                    d.statusColor = getStatusColor(d.status);
                    d.remarks = rs.getString("note");
                    d.items = countPhysicalItems(conn, rs.getInt("inventory_check_id"));
                    d.itemsList = loadPhysicalItems(conn, rs.getInt("inventory_check_id"));
                    docs.add(d);
                }
                LOGGER.info("[LedgerDAO] Physical fetched " + count + " rows");
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "LedgerDAO: Failed to fetch physical check documents", e);
        }

        // 5. Fetch RMA / Return Orders
        String sqlReturns =
            "SELECT ro.return_id, ro.return_code, ro.order_id, ro.customer_name, ro.reason, ro.status, ro.created_at, ro.updated_at, " +
            "w.warehouse_id, w.warehouse_name, w.warehouse_code, w.address, " +
            "o.order_code AS ref_order_code, " +
            "u.full_name AS creator_name " +
            "FROM return_orders ro " +
            "LEFT JOIN warehouses w ON ro.warehouse_id = w.warehouse_id " +
            "LEFT JOIN orders o ON ro.order_id = o.order_id " +
            "LEFT JOIN users u ON ro.created_by = u.user_id " +
            (byWh ? "WHERE ro.warehouse_id = ? " : "") +
            "ORDER BY ro.created_at DESC LIMIT 100";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlReturns)) {
            if (byWh) ps.setInt(1, warehouseId);
            try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                LedgerDocument d = new LedgerDocument();
                String rmaCode = rs.getString("return_code");
                d.id = (rmaCode != null && !rmaCode.isEmpty()) ? rmaCode : "RMA-" + String.format("%05d", rs.getInt("return_id"));
                d.type = "Phiếu Hoàn Hàng";

                Timestamp ca = rs.getTimestamp("created_at");
                d.date = (ca != null) ? ca.toLocalDateTime().format(DATE_FORMATTER) : "";
                d.warehouse = rs.getString("warehouse_name");
                d.warehouseCode = rs.getString("warehouse_code");
                d.warehouseAddress = rs.getString("address");
                d.warehouseId = rs.getInt("warehouse_id");

                String creator = rs.getString("creator_name");
                d.createdBy = (creator != null && !creator.isEmpty()) ? creator : "Nhân viên tiếp nhận";

                d.status = mapReturnStatus(rs.getString("status"));
                d.statusColor = getStatusColor(d.status);
                d.remarks = rs.getString("reason");
                d.customer = rs.getString("customer_name");
                d.orderCode = rs.getString("ref_order_code");
                d.items = countReturnItems(conn, rs.getInt("return_id"));
                d.itemsList = loadReturnItems(conn, rs.getInt("return_id"));
                docs.add(d);
            }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "LedgerDAO: Failed to fetch return documents", e);
        }

        // 6. Fetch Scrap / Disposal Issues (warehouse_issues, issue_type = SCRAP)
        String sqlScrap =
            "SELECT wi.issue_id, wi.issue_code, wi.status, wi.created_at, " +
            "w.warehouse_id, w.warehouse_name, u.full_name AS creator_name " +
            "FROM warehouse_issues wi " +
            "LEFT JOIN warehouses w ON wi.warehouse_id = w.warehouse_id " +
            "LEFT JOIN users u ON wi.created_by = u.user_id " +
            "WHERE wi.issue_type = 'SCRAP' " +
            (byWh ? "AND wi.warehouse_id = ? " : "") +
            "ORDER BY wi.created_at DESC LIMIT 100";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlScrap)) {
            if (byWh) ps.setInt(1, warehouseId);
            try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                LedgerDocument d = new LedgerDocument();
                d.id = rs.getString("issue_code");
                d.type = "Phiếu Xuất Hủy";
                Timestamp ca = rs.getTimestamp("created_at");
                d.date = (ca != null) ? ca.toLocalDateTime().format(DATE_FORMATTER) : "";
                d.warehouse = rs.getString("warehouse_name");
                d.warehouseId = rs.getInt("warehouse_id");
                String creator = rs.getString("creator_name");
                d.createdBy = (creator != null) ? creator : "Hệ thống";
                String st = rs.getString("status");
                d.status = "APPROVED".equals(st) ? "Đã duyệt" : ("CANCELLED".equals(st) ? "Đã hủy" : "Nháp");
                d.statusColor = getStatusColor(d.status);
                int itemCount = 0;
                try (PreparedStatement cps = conn.prepareStatement(
                        "SELECT COUNT(*) FROM issue_details WHERE issue_id = ?")) {
                    cps.setInt(1, rs.getInt("issue_id"));
                    try (ResultSet crs = cps.executeQuery()) { if (crs.next()) itemCount = crs.getInt(1); }
                }
                d.items = itemCount;
                docs.add(d);
            }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "LedgerDAO: Failed to fetch scrap documents", e);
        }



        // Sort overall list: newest first
        docs.sort((d1, d2) -> d2.date.compareTo(d1.date));
        return docs;
    }

    public boolean verifyDocumentBelongsToWarehouse(String docId, String docType, int warehouseId) {
        String sql = null;
        
        switch (docType) {
            case "Phiếu Nhập Kho":
                sql = "SELECT warehouse_id FROM inbound_orders WHERE inbound_code = ?";
                break;
            case "Phiếu Xuất Kho":
                sql = "SELECT warehouse_id FROM outbound_orders WHERE outbound_code = ?";
                // Fallback if ID is used
                if (docId.startsWith("SOUT-OUT-")) {
                    sql = "SELECT warehouse_id FROM outbound_orders WHERE outbound_id = ?";
                    try {
                        int obId = Integer.parseInt(docId.substring(9));
                        try (Connection conn = DBConnection.getConnection();
                             PreparedStatement ps = conn.prepareStatement(sql)) {
                            ps.setInt(1, obId);
                            try (ResultSet rs = ps.executeQuery()) {
                                if (rs.next()) {
                                    return rs.getInt("warehouse_id") == warehouseId;
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                    return false;
                }
                break;
            case "Phiếu Chuyển Kho":
                sql = "SELECT from_warehouse_id, to_warehouse_id FROM stock_transfers WHERE transfer_code = ?";
                try (Connection conn = DBConnection.getConnection();
                     PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, docId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            return rs.getInt("from_warehouse_id") == warehouseId || rs.getInt("to_warehouse_id") == warehouseId;
                        }
                    }
                } catch (SQLException e) {
                    LOGGER.log(Level.WARNING, "verifyDocumentBelongsToWarehouse failed for Stock Transfer", e);
                }
                return false;
            case "Phiếu Kiểm Kê":
                sql = "SELECT warehouse_id FROM physical_inventories WHERE check_code = ?";
                break;
            case "Phiếu Hoàn Hàng":
                if (docId.startsWith("RMA-")) {
                    sql = "SELECT warehouse_id FROM return_orders WHERE return_id = ?";
                    try {
                        int rId = Integer.parseInt(docId.substring(4));
                        try (Connection conn = DBConnection.getConnection();
                             PreparedStatement ps = conn.prepareStatement(sql)) {
                            ps.setInt(1, rId);
                            try (ResultSet rs = ps.executeQuery()) {
                                if (rs.next()) {
                                    return rs.getInt("warehouse_id") == warehouseId;
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }
                return false;
            case "Phiếu Xuất Hủy":
                sql = "SELECT warehouse_id FROM warehouse_issues WHERE issue_code = ?";
                break;

            default:
                return false;
        }

        if (sql != null) {
            try (Connection conn = DBConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, docId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("warehouse_id") == warehouseId;
                    }
                }
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "verifyDocumentBelongsToWarehouse failed for docType=" + docType, e);
            }
        }
        return false;
    }

    // ══ approveDocument: handles transfer receipt, outbound shipment, and stocktake adjustment ══
    // Called by:
    //   - TransferService.markReceived()     → Phiếu Chuyển Kho
    //   - OutboundService                  → Phiếu Xuất Kho
    //   - WarehouseInventoryCheckServlet    → Phiếu Kiểm Kê
    // All inventory mutations + ledger writes happen here.
    public boolean approveDocument(String docId, String docType, int userId) {
        Connection conn = null;
        try {
            conn = DBConnection.getConnection();
            conn.setAutoCommit(false);
            try {
                boolean result;
                if ("Phiếu Chuyển Kho".equals(docType)) {
                    result = approveTransfer(conn, docId, userId);
                } else if ("Phiếu Xuất Kho".equals(docType)) {
                    result = approveOutbound(conn, docId, userId);
                } else if ("Phiếu Kiểm Kê".equals(docType)) {
                    result = approveStocktake(conn, docId, userId);
                } else {
                    conn.rollback();
                    return false;
                }
                if (result) conn.commit();
                else conn.rollback();
                return result;
            } catch (SQLException e) {
                try { conn.rollback(); } catch (SQLException ignored) {}
                throw e;
            } finally {
                if (conn != null) try { conn.close(); } catch (SQLException ignored) {}
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "approveDocument failed: " + docId, e);
            return false;
        }
    }

    private boolean approveTransfer(Connection conn, String docId, int userId) throws SQLException {
        int transferId = -1;
        int fromWhId = -1;
        int toWhId = -1;
        String status = "";
        String sqlFind = "SELECT transfer_id, from_warehouse_id, to_warehouse_id, status FROM stock_transfers WHERE transfer_code = ?";
        try (PreparedStatement ps = conn.prepareStatement(sqlFind)) {
            ps.setString(1, docId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    transferId = rs.getInt("transfer_id");
                    fromWhId = rs.getInt("from_warehouse_id");
                    toWhId = rs.getInt("to_warehouse_id");
                    status = rs.getString("status");
                }
            }
        }
        if (transferId == -1) throw new SQLException("Transfer not found: " + docId);
        if ("RECEIVED".equals(status)) return true;

        String sqlItems = "SELECT product_id, shipped_qty, received_qty FROM stock_transfer_items WHERE transfer_id = ?";
        List<Map<String, Object>> xferItems = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sqlItems)) {
            ps.setInt(1, transferId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("productId", rs.getInt("product_id"));
                    m.put("shipped", rs.getBigDecimal("shipped_qty"));
                    BigDecimal rec = rs.getBigDecimal("received_qty");
                    m.put("received", rec != null ? rec : rs.getBigDecimal("shipped_qty"));
                    xferItems.add(m);
                }
            }
        }

        for (Map<String, Object> item : xferItems) {
            int prodId = (int) item.get("productId");
            BigDecimal shipQty = (BigDecimal) item.get("shipped");
            BigDecimal recQty = (BigDecimal) item.get("received");

            int fromInvId = getInventoryIdForUpdate(prodId, fromWhId);
            insertLedgerEntryConn(conn, fromInvId, prodId, fromWhId, "TRANSFER_OUT", transferId,
                    shipQty.negate(), shipQty.negate(), userId, "Chuyển kho ra đi");

            upsertInventoryConn(conn, prodId, toWhId, recQty, recQty);
            int toInvId = getInventoryIdForUpdate(prodId, toWhId);
            insertLedgerEntryConn(conn, toInvId, prodId, toWhId, "TRANSFER_IN", transferId,
                    recQty, recQty, userId, "Chuyển kho nhận về");
        }

        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE stock_transfers SET status = 'RECEIVED', approved_by = ?, completed_at = ? WHERE transfer_id = ?")) {
            ps.setInt(1, userId);
            ps.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
            ps.setInt(3, transferId);
            ps.executeUpdate();
        }
        LOGGER.info("approveDocument (transfer): " + docId);
        return true;
    }

    private boolean approveOutbound(Connection conn, String docId, int userId) throws SQLException {
        int outboundId = -1;
        int warehouseId = -1;
        String status = "";
        String sqlFind = "SELECT outbound_id, warehouse_id, status FROM outbound_orders "
                       + "WHERE outbound_code = ? OR (outbound_code IS NULL AND CONCAT('SOUT-OUT-', outbound_id) = ?)";
        try (PreparedStatement ps = conn.prepareStatement(sqlFind)) {
            ps.setString(1, docId);
            ps.setString(2, docId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    outboundId = rs.getInt("outbound_id");
                    warehouseId = rs.getInt("warehouse_id");
                    status = rs.getString("status");
                }
            }
        }
        if (outboundId == -1) throw new SQLException("Outbound not found: " + docId);
        if ("SHIPPED".equals(status) || "DELIVERED".equals(status)) return true;

        List<Map<String, Object>> items = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT product_id, qty, picked_qty FROM outbound_items WHERE outbound_id = ?")) {
            ps.setInt(1, outboundId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    BigDecimal picked = rs.getBigDecimal("picked_qty");
                    BigDecimal req = rs.getBigDecimal("qty");
                    BigDecimal qty = (picked != null && picked.compareTo(BigDecimal.ZERO) > 0) ? picked : req;
                    Map<String, Object> m = new HashMap<>();
                    m.put("productId", rs.getInt("product_id"));
                    m.put("qty", qty);
                    items.add(m);
                }
            }
        }

        for (Map<String, Object> item : items) {
            int prodId = (int) item.get("productId");
            BigDecimal qty = (BigDecimal) item.get("qty");
            if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) continue;
            upsertInventoryConn(conn, prodId, warehouseId, qty.negate(), qty.negate());
            int invId = getInventoryIdForUpdate(prodId, warehouseId);
            insertLedgerEntryConn(conn, invId, prodId, warehouseId, "OUTBOUND", outboundId,
                    qty.negate(), qty.negate(), userId, "Xuất kho GI approved");
        }

        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE outbound_orders SET status = 'SHIPPED', shipped_at = ? WHERE outbound_id = ?")) {
            ps.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            ps.setInt(2, outboundId);
            ps.executeUpdate();
        }
        LOGGER.info("approveDocument (outbound): " + docId);
        return true;
    }

    private boolean approveStocktake(Connection conn, String docId, int userId) throws SQLException {
        int checkId = -1;
        int warehouseId = -1;
        String status = "";
        String sqlFind = "SELECT inventory_check_id, warehouse_id, status FROM physical_inventories WHERE check_code = ?";
        try (PreparedStatement ps = conn.prepareStatement(sqlFind)) {
            ps.setString(1, docId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    checkId = rs.getInt("inventory_check_id");
                    warehouseId = rs.getInt("warehouse_id");
                    status = rs.getString("status");
                }
            }
        }
        if (checkId == -1) throw new SQLException("Stocktake not found: " + docId);
        if ("APPROVED".equals(status)) return true;

        List<Map<String, Object>> items = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT product_id, delta_qty FROM physical_inventory_details WHERE inventory_check_id = ?")) {
            ps.setInt(1, checkId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("productId", rs.getInt("product_id"));
                    m.put("delta", rs.getBigDecimal("delta_qty"));
                    items.add(m);
                }
            }
        }

        for (Map<String, Object> item : items) {
            int prodId = (int) item.get("productId");
            BigDecimal delta = (BigDecimal) item.get("delta");
            if (delta == null || delta.compareTo(BigDecimal.ZERO) == 0) continue;
            upsertInventoryConn(conn, prodId, warehouseId, delta, delta);
            int invId = getInventoryIdForUpdate(prodId, warehouseId);
            insertLedgerEntryConn(conn, invId, prodId, warehouseId, "ADJUSTMENT", checkId,
                    delta, delta, userId, "Kiểm kê cân đối tồn kho");
        }

        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE physical_inventories SET status = 'APPROVED' WHERE inventory_check_id = ?")) {
            ps.setInt(1, checkId);
            ps.executeUpdate();
        }
        LOGGER.info("approveDocument (stocktake): " + docId);
        return true;
    }

    private void upsertInventoryConn(Connection conn, int productId, int warehouseId,
            BigDecimal qtyChange, BigDecimal availChange) throws SQLException {
        String sql = "INSERT INTO inventory (product_id, warehouse_id, qty_on_hand, holding, qty_available) "
                   + "VALUES (?, ?, ?, 0, ?) "
                   + "ON DUPLICATE KEY UPDATE qty_on_hand = qty_on_hand + ?, qty_available = qty_available + ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            ps.setInt(2, warehouseId);
            ps.setBigDecimal(3, qtyChange);
            ps.setBigDecimal(4, availChange);
            ps.setBigDecimal(5, qtyChange);
            ps.setBigDecimal(6, availChange);
            ps.executeUpdate();
        }
    }

    private void insertLedgerEntryConn(Connection conn, int inventoryId, int productId, int warehouseId,
            String xactType, int refDocId, BigDecimal qtyChange, BigDecimal availChange,
            int userId, String note) throws SQLException {
        String sql = "INSERT INTO inventory_ledger (inventory_id, product_id, warehouse_id, transaction_type, "
                   + "ref_document_id, qty_change, avail_change, created_by, note) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, inventoryId);
            ps.setInt(2, productId);
            ps.setInt(3, warehouseId);
            ps.setString(4, xactType);
            ps.setInt(5, refDocId);
            ps.setBigDecimal(6, qtyChange);
            ps.setBigDecimal(7, availChange);
            ps.setInt(8, userId);
            ps.setString(9, note);
            ps.executeUpdate();
        }
    }

    // ══ Public wrappers for InventoryCommandBus ══════════════════════════════
    /**
     * Returns inventory_id for product/warehouse. Used by InventoryCommandBus.
     */
    public int getInventoryIdForUpdate(int productId, int warehouseId) {
        String sql = "SELECT inventory_id FROM inventory WHERE product_id = ? AND warehouse_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            ps.setInt(2, warehouseId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("inventory_id");
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "getInventoryIdForUpdate failed", e);
        }
        return 0;
    }

    /**
     * Inserts a simple ledger entry from a Map (used by InventoryCommandBus).
     */
    public void insertSimpleLedgerEntry(Map<String, Object> entry) {
        String sql = "INSERT INTO inventory_ledger "
                   + "(inventory_id, product_id, warehouse_id, transaction_type, "
                   + "ref_document_id, qty_change, avail_change, created_by, note) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, (Integer) entry.get("inventoryId"));
            ps.setInt(2, (Integer) entry.get("productId"));
            ps.setInt(3, (Integer) entry.get("warehouseId"));
            ps.setString(4, (String) entry.get("type"));
            ps.setInt(5, 0);
            ps.setBigDecimal(6, (BigDecimal) entry.get("qtyChange"));
            ps.setBigDecimal(7, (BigDecimal) entry.get("availChange"));
            ps.setInt(8, (Integer) entry.get("userId"));
            ps.setString(9, (String) entry.get("note"));
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "insertSimpleLedgerEntry failed", e);
        }
    }

    // ── findGlobalLedgerEntries ──
    public List<GlobalLedgerEntry> findGlobalLedgerEntries() {
        List<GlobalLedgerEntry> entries = new ArrayList<>();
        String sql = 
            "SELECT il.ledger_id, il.transaction_type, il.qty_change, il.avail_change, il.timestamp, il.note, " +
            "p.sku_code, p.product_name, w.warehouse_name, u.full_name AS user_name " +
            "FROM inventory_ledger il " +
            "LEFT JOIN products p ON il.product_id = p.product_id " +
            "LEFT JOIN warehouses w ON il.warehouse_id = w.warehouse_id " +
            "LEFT JOIN users u ON il.created_by = u.user_id " +
            "ORDER BY il.timestamp DESC LIMIT 300";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                GlobalLedgerEntry e = new GlobalLedgerEntry();
                e.ledgerId = rs.getInt("ledger_id");
                e.sku = rs.getString("sku_code");
                e.productName = rs.getString("product_name");
                e.warehouse = rs.getString("warehouse_name");
                e.type = rs.getString("transaction_type");
                e.qtyChange = rs.getDouble("qty_change");
                e.availChange = rs.getDouble("avail_change");
                
                Timestamp ts = rs.getTimestamp("timestamp");
                e.timestamp = (ts != null) ? ts.toLocalDateTime().format(DATE_FORMATTER) : "";
                
                String user = rs.getString("user_name");
                e.createdBy = (user != null) ? user : "Hệ thống";
                e.note = rs.getString("note");
                entries.add(e);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "LedgerDAO: Failed to fetch global ledger entries", e);
        }
        return entries;
    }

    /**
     * Retrieve list items for a specific document.
     */
    public List<Map<String, Object>> findDocumentItems(String docId, String docType) {
        List<Map<String, Object>> items = new ArrayList<>();
        Connection conn = null;
        try {
            conn = DBConnection.getConnection();
            if ("Phiếu Nhập Kho".equals(docType)) {
                String sql =
                    "SELECT ii.expected_qty, ii.received_qty, ii.accepted_qty, ii.rejected_qty, ii.unit_cost, ii.lot_number, ii.expiry_date, ii.notes, p.sku_code, p.product_name, p.unit, p.base_price " +
                    "FROM inbound_items ii " +
                    "LEFT JOIN products p ON ii.product_id = p.product_id " +
                    "LEFT JOIN inbound_orders io ON ii.inbound_id = io.inbound_id " +
                    "WHERE io.inbound_code = ?";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, docId);
                    try (ResultSet rs = ps.executeQuery()) {
                        int index = 1;
                        while (rs.next()) {
                            Map<String, Object> map = new HashMap<>();
                            map.put("stt", index++);
                            map.put("sku", rs.getString("sku_code"));
                            map.put("name", rs.getString("product_name"));
                            map.put("uom", rs.getString("unit"));
                            String lot = rs.getString("lot_number");
                            map.put("lot", (lot != null && !lot.isEmpty()) ? lot : "—");
                            Date exp = rs.getDate("expiry_date");
                            map.put("hsd", (exp != null) ? new java.text.SimpleDateFormat("dd/MM/yyyy").format(exp) : "—");
                            map.put("ordered", rs.getDouble("expected_qty"));
                            map.put("received", rs.getDouble("received_qty"));
                            map.put("accepted", rs.getDouble("accepted_qty"));
                            map.put("rejected", rs.getDouble("rejected_qty"));
                            String itemNotes = rs.getString("notes");
                            map.put("remarks", (itemNotes != null) ? itemNotes : "");
                            // Use unit_cost if available, fallback to base_price
                            double unitCost = rs.getDouble("unit_cost");
                            if (rs.wasNull()) unitCost = rs.getDouble("base_price");
                            map.put("price", unitCost);
                            items.add(map);
                        }
                    }
                }
            } else if ("Phiếu Xuất Kho".equals(docType)) {
                String sql = 
                    "SELECT oi.qty, oi.picked_qty, p.sku_code, p.product_name, p.unit, p.base_price " +
                    "FROM outbound_items oi " +
                    "LEFT JOIN products p ON oi.product_id = p.product_id " +
                    "LEFT JOIN outbound_orders oo ON oi.outbound_id = oo.outbound_id " +
                    "WHERE oo.outbound_code = ? OR (oo.outbound_code IS NULL AND CONCAT('SOUT-OUT-', oo.outbound_id) = ?)";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, docId);
                    ps.setString(2, docId);
                    try (ResultSet rs = ps.executeQuery()) {
                        int index = 1;
                        while (rs.next()) {
                            Map<String, Object> map = new HashMap<>();
                            map.put("stt", index++);
                            map.put("sku", rs.getString("sku_code"));
                            map.put("name", rs.getString("product_name"));
                            map.put("uom", rs.getString("unit"));
                            map.put("lot", "LOT-AUTO");
                            map.put("hsd", "30/06/2027");
                            map.put("qtyRequest", rs.getDouble("qty"));
                            map.put("qtyIssued", rs.getDouble("picked_qty"));
                            map.put("price", rs.getDouble("base_price"));
                            items.add(map);
                        }
                    }
                }
            } else if ("Phiếu Chuyển Kho".equals(docType)) {
                String findTransfer = "SELECT transfer_id FROM stock_transfers WHERE transfer_code = ?";
                try (PreparedStatement ps = conn.prepareStatement(findTransfer)) {
                    ps.setString(1, docId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            int transferId = rs.getInt("transfer_id");
                            String sql =
                                "SELECT ti.shipped_qty, ti.received_qty, ti.lot_number, ti.notes, " +
                                "p.sku_code, p.product_name, p.unit, p.base_price " +
                                "FROM stock_transfer_items ti " +
                                "LEFT JOIN products p ON ti.product_id = p.product_id " +
                                "WHERE ti.transfer_id = ? " +
                                "ORDER BY ti.transfer_item_id ASC";
                            try (PreparedStatement ps2 = conn.prepareStatement(sql)) {
                                ps2.setInt(1, transferId);
                                try (ResultSet rs2 = ps2.executeQuery()) {
                                    int index = 1;
                                    while (rs2.next()) {
                                        Map<String, Object> map = new HashMap<>();
                                        map.put("stt", index++);
                                        map.put("sku", rs2.getString("sku_code"));
                                        map.put("name", rs2.getString("product_name"));
                                        map.put("uom", rs2.getString("unit"));
                                        String lot = rs2.getString("lot_number");
                                        map.put("lot", (lot != null && !lot.isEmpty()) ? lot : "—");
                                        map.put("requested", rs2.getDouble("shipped_qty"));
                                        double receivedQty = rs2.getDouble("received_qty");
                                        if (rs2.wasNull()) receivedQty = rs2.getDouble("shipped_qty");
                                        map.put("transferred", receivedQty);
                                        String note = rs2.getString("notes");
                                        map.put("remark", (note != null) ? note : "");
                                        map.put("price", rs2.getDouble("base_price"));
                                        items.add(map);
                                    }
                                }
                            }
                        }
                    }
                }
            } else if ("Phiếu Kiểm Kê".equals(docType)) {
                String findCheck = "SELECT inventory_check_id FROM physical_inventories WHERE check_code = ?";
                try (PreparedStatement ps = conn.prepareStatement(findCheck)) {
                    ps.setString(1, docId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            int checkId = rs.getInt("inventory_check_id");
                            String sql =
                                "SELECT pid.system_qty, pid.actual_qty, pid.delta_qty, pid.variance_reason, pid.lot_number, " +
                                "p.sku_code, p.product_name, p.unit " +
                                "FROM physical_inventory_details pid " +
                                "LEFT JOIN products p ON pid.product_id = p.product_id " +
                                "WHERE pid.inventory_check_id = ? " +
                                "ORDER BY pid.check_detail_id ASC";
                            try (PreparedStatement ps2 = conn.prepareStatement(sql)) {
                                ps2.setInt(1, checkId);
                                try (ResultSet rs2 = ps2.executeQuery()) {
                                    int index = 1;
                                    while (rs2.next()) {
                                        Map<String, Object> map = new HashMap<>();
                                        map.put("stt", index++);
                                        map.put("sku", rs2.getString("sku_code"));
                                        map.put("name", rs2.getString("product_name"));
                                        map.put("uom", rs2.getString("unit"));
                                        String lot = rs2.getString("lot_number");
                                        map.put("lot", (lot != null && !lot.isEmpty()) ? lot : "—");
                                        map.put("book", rs2.getDouble("system_qty"));
                                        double actual = rs2.getDouble("actual_qty");
                                        if (rs2.wasNull()) actual = rs2.getDouble("system_qty");
                                        map.put("actual", actual);
                                        double diff = rs2.getDouble("delta_qty");
                                        if (rs2.wasNull()) diff = 0.0;
                                        map.put("diff", diff);
                                        String reason = rs2.getString("variance_reason");
                                        map.put("remark", (reason != null) ? reason : "");
                                        items.add(map);
                                    }
                                }
                            }
                        }
                    }
                }
            } else if ("Phiếu Hoàn Hàng".equals(docType)) {
                // RMA: format id is RMA-XXXXX where XXXXX is return_id
                int returnId = Integer.parseInt(docId.replace("RMA-", ""));
                String sql =
                    "SELECT ri.quantity, ri.unit_price, ri.return_reason, p.sku_code, p.product_name, p.unit, p.base_price " +
                    "FROM return_items ri " +
                    "LEFT JOIN products p ON ri.product_id = p.product_id " +
                    "WHERE ri.return_id = ?";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, returnId);
                    try (ResultSet rs = ps.executeQuery()) {
                        int index = 1;
                        while (rs.next()) {
                            Map<String, Object> map = new HashMap<>();
                            map.put("stt", index++);
                            map.put("sku", rs.getString("sku_code"));
                            map.put("name", rs.getString("product_name"));
                            map.put("uom", rs.getString("unit"));
                            map.put("returned", rs.getDouble("quantity"));
                            map.put("reuse", rs.getDouble("quantity"));
                            map.put("destroy", 0.0);
                            double unitPrice = rs.getDouble("unit_price");
                            if (rs.wasNull() || unitPrice == 0) unitPrice = rs.getDouble("base_price");
                            map.put("price", unitPrice);
                            map.put("remark", rs.getString("return_reason"));
                            items.add(map);
                        }
                    }
                }

            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "LedgerDAO: Failed to fetch document items for id=" + docId, e);
        } finally {
            if (conn != null) {
                try { conn.close(); } catch (SQLException ex) {}
            }
        }
        return items;
    }

    // ── Internal Helpers ──

    private int countInboundItems(Connection conn, int id) throws SQLException {
        String sql = "SELECT COUNT(*) FROM inbound_items WHERE inbound_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return 0;
    }

    private int countOutboundItems(Connection conn, int id) throws SQLException {
        String sql = "SELECT COUNT(*) FROM outbound_items WHERE outbound_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return 0;
    }

    private int countTransferItems(Connection conn, int id) throws SQLException {
        String sql = "SELECT COUNT(*) FROM stock_transfer_items WHERE transfer_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return 0;
    }

    private int countPhysicalItems(Connection conn, int id) throws SQLException {
        String sql = "SELECT COUNT(*) FROM physical_inventory_details WHERE inventory_check_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return 0;
    }

    private int countReturnItems(Connection conn, int id) throws SQLException {
        String sql = "SELECT COUNT(*) FROM return_items WHERE return_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return 0;
    }

    private String mapInboundStatus(String dbStatus) {
        if ("PENDING".equals(dbStatus)) return "Chờ nhập hàng";
        if ("PURCHASED".equals(dbStatus)) return "Đã mua hàng";
        if ("IN_PROGRESS".equals(dbStatus)) return "Đang nhập kho";
        if ("RECEIVED".equals(dbStatus)) return "Hoàn thành";
        if ("CANCELLED".equals(dbStatus)) return "Từ chối";
        return dbStatus;
    }

    private String mapOutboundStatus(String dbStatus) {
        if ("PENDING".equals(dbStatus)) return "Chờ duyệt";
        if ("PICKING".equals(dbStatus)) return "Đang xử lý";
        if ("PACKED".equals(dbStatus)) return "Đang xử lý";
        if ("SHIPPED".equals(dbStatus)) return "Đang giao";
        if ("DELIVERED".equals(dbStatus)) return "Hoàn thành";
        if ("CANCELLED".equals(dbStatus)) return "Đã huỷ";
        return dbStatus;
    }

    private String mapTransferStatus(String dbStatus) {
        if ("DRAFT".equals(dbStatus)) return "Nháp";
        if ("IN_TRANSIT".equals(dbStatus)) return "Đang chuyển";
        if ("RECEIVED".equals(dbStatus)) return "Hoàn thành";
        if ("CANCELLED".equals(dbStatus)) return "Đã huỷ";
        return dbStatus;
    }

    private String mapPhysicalStatus(String dbStatus) {
        if ("DRAFT".equals(dbStatus)) return "Nháp";
        if ("IN_PROGRESS".equals(dbStatus)) return "Chờ duyệt";
        if ("APPROVED".equals(dbStatus)) return "Hoàn thành";
        return dbStatus;
    }

    private String mapReturnStatus(String dbStatus) {
        if ("RECEIVED".equals(dbStatus)) return "Chờ xác nhận WH";
        if ("INSPECTING".equals(dbStatus)) return "Chờ xác nhận WH";
        if ("PASS".equals(dbStatus)) return "Đã xử lý";
        if ("FAIL".equals(dbStatus)) return "Đã xử lý";
        if ("RESTOCKED".equals(dbStatus)) return "Đã xử lý";
        if ("SCRAPPED".equals(dbStatus)) return "Đã xử lý";
        return dbStatus;
    }



    /**
     * Load all outbound items for a given outbound_id, using real Lazada quantity
     * and price from lazada_order_items.
     */
    private List<Map<String, Object>> loadOutboundItems(Connection conn, int outboundId) {
        List<Map<String, Object>> items = new ArrayList<>();
        String sql =
            "SELECT oi.qty, oi.picked_qty, oi.shelf_location, " +
            "p.product_id, p.sku_code, p.product_name, p.unit, p.base_price, oo.order_id " +
            "FROM outbound_items oi " +
            "LEFT JOIN products p ON oi.product_id = p.product_id " +
            "LEFT JOIN outbound_orders oo ON oi.outbound_id = oo.outbound_id " +
            "WHERE oi.outbound_id = ? " +
            "ORDER BY oi.outbound_item_id ASC";
            
        Map<String, Map<String, Object>> grouped = new java.util.LinkedHashMap<>();
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, outboundId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String sku = rs.getString("sku_code");
                    if (sku == null) continue;
                    
                    double qty = rs.getDouble("qty");
                    double picked = rs.getDouble("picked_qty");
                    int productId = rs.getInt("product_id");
                    int orderId = rs.getInt("order_id");
                    
                    if (grouped.containsKey(sku)) {
                        Map<String, Object> existing = grouped.get(sku);
                        existing.put("ordered", (double)existing.get("ordered") + qty);
                        existing.put("received", (double)existing.get("received") + picked);
                        existing.put("requested", (double)existing.get("requested") + qty);
                        existing.put("shipped", (double)existing.get("shipped") + picked);
                        if (existing.get("lazadaQty") != null) {
                            existing.put("lazadaQty", (double)existing.get("lazadaQty") + qty);
                        }
                    } else {
                        Map<String, Object> map = new HashMap<>();
                        map.put("sku", sku);
                        map.put("name", rs.getString("product_name"));
                        map.put("uom", rs.getString("unit"));
                        map.put("ordered", qty);
                        map.put("received", picked);
                        map.put("requested", qty);
                        map.put("shipped", picked);
                        String loc = rs.getString("shelf_location");
                        map.put("lot", (loc != null && !loc.isEmpty()) ? loc : "—");
                        
                        // Get actual price from order_items
                        double price = rs.getDouble("base_price");
                        if (orderId > 0 && productId > 0) {
                            String priceSql = "SELECT actual_price FROM order_items WHERE order_id = ? AND product_id = ? LIMIT 1";
                            try (PreparedStatement pps = conn.prepareStatement(priceSql)) {
                                pps.setInt(1, orderId);
                                pps.setInt(2, productId);
                                try (ResultSet prs = pps.executeQuery()) {
                                    if (prs.next()) {
                                        price = prs.getDouble("actual_price");
                                    }
                                }
                            } catch (Exception ex) {
                                // fallback to base_price
                            }
                        }
                        map.put("price", price);
                        map.put("lazadaQty", qty);
                        map.put("lazadaItemPrice", price);
                        grouped.put(sku, map);
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "LedgerDAO: loadOutboundItems failed for outbound_id=" + outboundId, e);
        }
        
        int index = 1;
        for (Map<String, Object> map : grouped.values()) {
            map.put("stt", index++);
            items.add(map);
        }
        return items;
    }

    /**
     * Load all stock transfer items for a given transfer_id.
     */
    private List<Map<String, Object>> loadTransferItems(Connection conn, int transferId) {
        List<Map<String, Object>> items = new ArrayList<>();
        String sql =
            "SELECT ti.shipped_qty, ti.received_qty, ti.lot_number, ti.notes, " +
            "p.sku_code, p.product_name, p.unit, p.base_price " +
            "FROM stock_transfer_items ti " +
            "LEFT JOIN products p ON ti.product_id = p.product_id " +
            "WHERE ti.transfer_id = ? " +
            "ORDER BY ti.transfer_item_id ASC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, transferId);
            try (ResultSet rs = ps.executeQuery()) {
                int index = 1;
                while (rs.next()) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("stt", index++);
                    map.put("sku", rs.getString("sku_code"));
                    map.put("name", rs.getString("product_name"));
                    map.put("uom", rs.getString("unit"));
                    String lot = rs.getString("lot_number");
                    map.put("lot", (lot != null && !lot.isEmpty()) ? lot : "—");
                    double shippedQty = rs.getDouble("shipped_qty");
                    double receivedQty = rs.getDouble("received_qty");
                    if (rs.wasNull()) receivedQty = shippedQty;
                    map.put("ordered", shippedQty);
                    map.put("received", receivedQty);
                    map.put("qty", shippedQty);
                    String note = rs.getString("notes");
                    map.put("remarks", (note != null) ? note : "");
                    map.put("price", rs.getDouble("base_price"));
                    items.add(map);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "LedgerDAO: loadTransferItems failed for transfer_id=" + transferId, e);
        }
        return items;
    }

    /**
     * Load all physical inventory details for a given inventory_check_id.
     */
    private List<Map<String, Object>> loadPhysicalItems(Connection conn, int checkId) {
        List<Map<String, Object>> items = new ArrayList<>();
        String sql =
            "SELECT pid.system_qty, pid.actual_qty, pid.delta_qty, pid.variance_reason, pid.lot_number, " +
            "p.sku_code, p.product_name, p.unit " +
            "FROM physical_inventory_details pid " +
            "LEFT JOIN products p ON pid.product_id = p.product_id " +
            "WHERE pid.inventory_check_id = ? " +
            "ORDER BY pid.check_detail_id ASC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, checkId);
            try (ResultSet rs = ps.executeQuery()) {
                int index = 1;
                while (rs.next()) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("stt", index++);
                    map.put("sku", rs.getString("sku_code"));
                    map.put("name", rs.getString("product_name"));
                    map.put("uom", rs.getString("unit"));
                    String lot = rs.getString("lot_number");
                    map.put("lot", (lot != null && !lot.isEmpty()) ? lot : "—");
                    double systemQty = rs.getDouble("system_qty");
                    double actual = rs.getDouble("actual_qty");
                    if (rs.wasNull()) actual = systemQty;
                    map.put("ordered", systemQty);
                    map.put("received", actual);
                    map.put("systemQty", systemQty);
                    map.put("physicalQty", actual);
                    String reason = rs.getString("variance_reason");
                    map.put("remarks", (reason != null) ? reason : "");
                    items.add(map);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "LedgerDAO: loadPhysicalItems failed for check_id=" + checkId, e);
        }
        return items;
    }

    /**
     * Load all return items (RMA) for a given return_id.
     */
    private List<Map<String, Object>> loadReturnItems(Connection conn, int returnId) {
        List<Map<String, Object>> items = new ArrayList<>();
        String sql =
            "SELECT ri.quantity, ri.unit_price, ri.return_reason, " +
            "p.sku_code, p.product_name, p.unit, p.base_price, qr.decision " +
            "FROM return_items ri " +
            "LEFT JOIN products p ON ri.product_id = p.product_id " +
            "LEFT JOIN qc_records qr ON (ri.return_id = qr.return_id AND ri.product_id = qr.product_id) " +
            "WHERE ri.return_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, returnId);
            try (ResultSet rs = ps.executeQuery()) {
                int index = 1;
                while (rs.next()) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("stt", index++);
                    map.put("sku", rs.getString("sku_code"));
                    map.put("name", rs.getString("product_name"));
                    map.put("uom", rs.getString("unit"));
                    double qty = rs.getDouble("quantity");
                    map.put("ordered", qty);
                    map.put("received", qty);
                    map.put("rejected", 0.0);
                    map.put("returnedQty", qty);
                    
                    String dec = rs.getString("decision");
                    double reuseQty = 0;
                    double destroyQty = 0;
                    if (dec != null) {
                        if ("PASS".equalsIgnoreCase(dec)) {
                            reuseQty = qty;
                        } else {
                            destroyQty = qty;
                        }
                    }
                    map.put("reuseQty", reuseQty);
                    map.put("destroyQty", destroyQty);
                    
                    double unitPrice = rs.getDouble("unit_price");
                    if (rs.wasNull() || unitPrice == 0) unitPrice = rs.getDouble("base_price");
                    map.put("price", unitPrice);
                    String reason = rs.getString("return_reason");
                    map.put("remarks", (reason != null) ? reason : "");
                    items.add(map);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "LedgerDAO: loadReturnItems failed for return_id=" + returnId, e);
        }
        return items;
    }

    /**
     * Load all inbound items (with full detail) for a given inbound_id.
     * Used when listing documents so itemsList is populated for the detail modal.
     */
    private List<Map<String, Object>> loadInboundItems(Connection conn, int inboundId) {
        List<Map<String, Object>> items = new ArrayList<>();
        String sql =
            "SELECT ii.expected_qty, ii.received_qty, ii.accepted_qty, ii.rejected_qty, " +
            "ii.unit_cost, ii.lot_number, ii.expiry_date, ii.notes, " +
            "p.sku_code, p.product_name, p.unit, p.base_price " +
            "FROM inbound_items ii " +
            "LEFT JOIN products p ON ii.product_id = p.product_id " +
            "WHERE ii.inbound_id = ? " +
            "ORDER BY ii.inbound_item_id ASC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, inboundId);
            try (ResultSet rs = ps.executeQuery()) {
                int index = 1;
                while (rs.next()) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("stt", index++);
                    map.put("sku", rs.getString("sku_code"));
                    map.put("name", rs.getString("product_name"));
                    map.put("uom", rs.getString("unit"));
                    String lot = rs.getString("lot_number");
                    map.put("lot", (lot != null && !lot.isEmpty()) ? lot : "—");
                    Date exp = rs.getDate("expiry_date");
                    map.put("hsd", (exp != null) ? new java.text.SimpleDateFormat("dd/MM/yyyy").format(exp) : "—");
                    map.put("ordered", rs.getDouble("expected_qty"));
                    map.put("received", rs.getDouble("received_qty"));
                    map.put("accepted", rs.getDouble("accepted_qty"));
                    map.put("rejected", rs.getDouble("rejected_qty"));
                    String itemNotes = rs.getString("notes");
                    map.put("remarks", (itemNotes != null) ? itemNotes : "");
                    double unitCost = rs.getDouble("unit_cost");
                    if (rs.wasNull()) unitCost = rs.getDouble("base_price");
                    map.put("price", unitCost);
                    items.add(map);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "LedgerDAO: loadInboundItems failed for inbound_id=" + inboundId, e);
        }
        return items;
    }

    private String getStatusColor(String status) {
        if ("Chờ duyệt".equals(status) || "Chờ xác nhận WH".equals(status)) return "#d97706"; // amber
        if ("Đang xử lý".equals(status)) return "#2563eb"; // blue
        if ("Hoàn thành".equals(status) || "Đã duyệt".equals(status) || "Đã xử lý".equals(status)) return "#059669"; // green
        if ("Từ chối".equals(status)) return "#dc2626"; // red
        return "#6b7280"; // gray (Draft/Nháp)
    }

    private boolean isOmnichannelChannel(String channelName) {
        if (channelName == null) return true;
        String lower = channelName.trim().toLowerCase();
        return lower.contains("shopee") || 
               lower.contains("tiktok") || 
               lower.contains("lazada") || 
               lower.contains("website") || 
               lower.contains("online") || 
               lower.contains("khách mua lẻ") ||
               lower.contains("retail");
    }
}
