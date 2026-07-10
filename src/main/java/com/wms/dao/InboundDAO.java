package com.wms.dao;

import com.wms.model.InboundOrder;
import com.wms.model.ReceiptNote;
import com.wms.util.DBConnection;

import java.math.BigDecimal;
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
 * InboundDAO — Data Access Object for inbound order records from MySQL.
 * Handles CRUD operations for inbound orders and receipt notes.
 */
public class InboundDAO {

    private static final Logger LOGGER = Logger.getLogger(InboundDAO.class.getName());

    // ══ InboundOrder queries ════════════════════════════════════════

    private static final String SELECT_INBOUND_BASE =
        "io.inbound_id, io.inbound_code, io.supplier, io.warehouse_id, "
      + "w.warehouse_name, io.status, io.received_by, io.created_by, io.note, io.created_at, io.received_at, "
      + "io.supplier_id, io.expected_date, io.received_date, io.payment_terms, "
      + "io.zone_id, io.delivery_person, io.delivery_phone, "
      + "s.supplier_code, s.contact_person, s.phone, s.email, s.address, s.payment_terms AS supplier_payment_terms, "
      + "ii.inbound_item_id, ii.product_id, "
      + "p.sku_code, p.product_name, "
      + "ii.expected_qty, ii.received_qty, ii.accepted_qty, ii.rejected_qty, ii.reject_reason, ii.unit_cost";

    private static final String SQL_FIND_ALL =
        "SELECT " + SELECT_INBOUND_BASE + " "
      + "FROM inbound_orders io "
      + "LEFT JOIN warehouses w ON io.warehouse_id = w.warehouse_id "
      + "LEFT JOIN suppliers  s ON io.supplier_id = s.supplier_id "
      + "LEFT JOIN inbound_items ii ON io.inbound_id = ii.inbound_id "
      + "LEFT JOIN products p ON ii.product_id = p.product_id "
      + "ORDER BY io.created_at DESC";

    private static final String SQL_FIND_BY_ID =
        "SELECT io.inbound_id, io.inbound_code, io.supplier, io.warehouse_id, io.status, "
      + "io.received_by, io.created_by, io.note, io.expected_date, io.received_date, io.payment_terms, io.created_at, io.received_at, "
      + "io.supplier_id, io.zone_id, io.delivery_person, io.delivery_phone, "
      + "w.warehouse_name, "
      + "z.zone_name, "
      + "s.supplier_code, s.contact_person, s.phone, s.email, s.address, s.payment_terms AS supplier_payment_terms "
      + "FROM inbound_orders io "
      + "LEFT JOIN warehouses w ON io.warehouse_id = w.warehouse_id "
      + "LEFT JOIN zones z ON io.zone_id = z.zone_id "
      + "LEFT JOIN suppliers  s ON io.supplier_id = s.supplier_id "
      + "WHERE io.inbound_id = ?";

    private static final String SQL_FIND_BY_STATUS =
        "SELECT io.inbound_id, io.inbound_code, io.supplier, io.warehouse_id, io.status, "
      + "io.received_by, io.created_by, io.note, io.expected_date, io.received_date, io.payment_terms, io.created_at, io.received_at, "
      + "io.supplier_id, io.zone_id, io.delivery_person, io.delivery_phone, "
      + "w.warehouse_name, "
      + "z.zone_name, "
      + "s.supplier_code, s.contact_person, s.phone, s.email, s.address, s.payment_terms AS supplier_payment_terms, "
      + "ii.inbound_item_id, ii.product_id, "
      + "p.sku_code, p.product_name, "
      + "ii.expected_qty, ii.received_qty, ii.accepted_qty, ii.rejected_qty, ii.reject_reason, ii.unit_cost "
      + "FROM inbound_orders io "
      + "LEFT JOIN warehouses w ON io.warehouse_id = w.warehouse_id "
      + "LEFT JOIN zones z ON io.zone_id = z.zone_id "
      + "LEFT JOIN suppliers  s ON io.supplier_id = s.supplier_id "
      + "LEFT JOIN inbound_items ii ON io.inbound_id = ii.inbound_id "
      + "LEFT JOIN products p ON ii.product_id = p.product_id "
      + "WHERE io.status = ? "
      + "ORDER BY io.created_at DESC LIMIT 200";

    private static final String SQL_FIND_BY_WAREHOUSE =
        "SELECT io.inbound_id, io.inbound_code, io.supplier, io.warehouse_id, io.status, "
      + "io.received_by, io.created_by, io.note, io.expected_date, io.received_date, io.payment_terms, io.created_at, io.received_at, "
      + "io.supplier_id, io.zone_id, io.delivery_person, io.delivery_phone, "
      + "w.warehouse_name, "
      + "z.zone_name, "
      + "s.supplier_code, s.contact_person, s.phone, s.email, s.address, s.payment_terms AS supplier_payment_terms, "
      + "ii.inbound_item_id, ii.product_id, "
      + "p.sku_code, p.product_name, "
      + "ii.expected_qty, ii.received_qty, ii.accepted_qty, ii.rejected_qty, ii.reject_reason, ii.unit_cost "
      + "FROM inbound_orders io "
      + "LEFT JOIN warehouses w ON io.warehouse_id = w.warehouse_id "
      + "LEFT JOIN zones z ON io.zone_id = z.zone_id "
      + "LEFT JOIN suppliers  s ON io.supplier_id = s.supplier_id "
      + "LEFT JOIN inbound_items ii ON io.inbound_id = ii.inbound_id "
      + "LEFT JOIN products p ON ii.product_id = p.product_id "
      + "WHERE io.warehouse_id = ? "
      + "ORDER BY io.created_at DESC LIMIT 200";
    private static final String SQL_INSERT =
        "INSERT INTO inbound_orders (inbound_code, warehouse_id, zone_id, supplier, supplier_id, status, "
      + "created_by, note, expected_date, received_date, payment_terms, "
      + "delivery_person, delivery_phone) "
      + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String SQL_UPDATE =
        "UPDATE inbound_orders SET inbound_code=?, warehouse_id=?, zone_id=?, supplier=?, supplier_id=?, "
      + "status=?, received_by=?, note=?, expected_date=?, received_date=?, payment_terms=?, "
      + "delivery_person=?, delivery_phone=? "
      + "WHERE inbound_id=?";

    private static final String SQL_UPDATE_STATUS =
        "UPDATE inbound_orders SET status=?, received_at=? WHERE inbound_id=?";

    private static final String SQL_UPDATE_STATUS_ONLY =
        "UPDATE inbound_orders SET status=? WHERE inbound_id=?";

    private static final String SQL_NEXT_SEQUENCE =
        "SELECT COALESCE(MAX(CAST(SUBSTRING(inbound_code, 13) AS UNSIGNED)), 0) + 1 AS next_seq "
      + "FROM inbound_orders WHERE inbound_code LIKE ?";

    // ══ ReceiptNote queries ════════════════════════════════════════

    private static final String SQL_FIND_RECEIPTS_BY_INBOUND_ID =
        "SELECT ii.inbound_item_id AS receipt_id, ii.inbound_id, ii.product_id, "
      + "p.sku_code, p.product_name, "
      + "ii.expected_qty, ii.received_qty, ii.accepted_qty, ii.rejected_qty, ii.reject_reason, ii.unit_cost, "
      + "ii.notes, NULL AS received_at "
      + "FROM inbound_items ii "
      + "LEFT JOIN products p ON ii.product_id = p.product_id "
      + "WHERE ii.inbound_id = ?";


    private static final String SQL_INSERT_RECEIPT =
        "INSERT INTO inbound_items (inbound_id, product_id, expected_qty, received_qty, accepted_qty, rejected_qty, reject_reason, unit_cost) "
      + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String SQL_UPDATE_RECEIPT =
        "UPDATE inbound_items SET product_id=?, expected_qty=?, received_qty=?, accepted_qty=?, rejected_qty=?, reject_reason=?, unit_cost=? "
      + "WHERE inbound_item_id=?";
    // ══ InboundOrder row mapper ════════════════════════════════════

    private InboundOrder mapInboundOrder(ResultSet rs) throws SQLException {
        InboundOrder o = new InboundOrder();
        o.setInboundId(rs.getInt("inbound_id"));
        o.setInboundCode(rs.getString("inbound_code"));
        o.setSupplierName(rs.getString("supplier"));
        o.setWarehouseId(rs.getInt("warehouse_id"));
        o.setWarehouseName(rs.getString("warehouse_name"));

        String status = rs.getString("status");
        o.setStatus(status != null ? status : InboundOrder.STATUS_PENDING);

        o.setCreatedBy(rs.getInt("created_by"));
        o.setNotes(rs.getString("note"));

        // expected_date and payment_terms
        java.sql.Date ed = rs.getDate("expected_date");
        if (ed != null) o.setExpectedDate(ed.toLocalDate());
        java.sql.Date rd = rs.getDate("received_date");
        if (rd != null) o.setReceivedDate(rd.toLocalDate());
        String pt = rs.getString("supplier_payment_terms");
        o.setPaymentTerms(pt != null && !pt.isEmpty() ? pt : rs.getString("payment_terms"));

        // Zone linkage
        try {
            int zid = rs.getInt("zone_id");
            o.setZoneId(rs.wasNull() ? null : zid);
        } catch (SQLException ignored) {
            o.setZoneId(null);
        }
        try { o.setZoneName(rs.getString("zone_name")); } catch (SQLException ignored) {}

        // Delivery info
        o.setDeliveryPerson(rs.getString("delivery_person"));
        o.setDeliveryPhone(rs.getString("delivery_phone"));

        // Supplier linkage (LEFT JOIN — các cột có thể null khi PO cũ chưa liên kết)
        try {
            int supId = rs.getInt("supplier_id");
            o.setSupplierId(rs.wasNull() ? null : supId);
        } catch (SQLException ignored) {
            o.setSupplierId(null);
        }
        try { o.setSupplierCode(rs.getString("supplier_code")); } catch (SQLException ignored) {}
        try { o.setSupplierContact(rs.getString("contact_person")); } catch (SQLException ignored) {}
        try { o.setSupplierPhone(rs.getString("phone")); } catch (SQLException ignored) {}
        o.setSupplierAddress(rs.getString("address"));
        try { o.setSupplierEmail(rs.getString("email")); } catch (SQLException ignored) {}

        java.sql.Timestamp ca = rs.getTimestamp("created_at");
        if (ca != null) o.setCreatedAt(ca.toLocalDateTime());

        java.sql.Timestamp ra = rs.getTimestamp("received_at");
        if (ra != null) o.setReceivedDate(ra.toLocalDateTime().toLocalDate());

        return o;
    }

    // ══ ReceiptNote row mapper ════════════════════════════════════

    private ReceiptNote mapReceiptNote(ResultSet rs) throws SQLException {
        ReceiptNote rn = new ReceiptNote();
        rn.setReceiptId(rs.getInt("receipt_id"));
        rn.setInboundId(rs.getInt("inbound_id"));
        rn.setProductId(rs.getInt("product_id"));
        rn.setSkuCode(rs.getString("sku_code"));
        rn.setProductName(rs.getString("product_name"));

        BigDecimal eq = rs.getBigDecimal("expected_qty");
        rn.setExpectedQty(eq != null ? eq : BigDecimal.ZERO);

        BigDecimal rq = rs.getBigDecimal("received_qty");
        rn.setReceivedQty(rq != null ? rq : BigDecimal.ZERO);

        BigDecimal aq = rs.getBigDecimal("accepted_qty");
        rn.setAcceptedQty(aq != null ? aq : BigDecimal.ZERO);

        BigDecimal djq = rs.getBigDecimal("rejected_qty");
        rn.setRejectedQty(djq != null ? djq : BigDecimal.ZERO);
        rn.setRejectReason(rs.getString("reject_reason"));
        rn.setNote(rs.getString("notes"));
        rn.setUnitCost(rs.getBigDecimal("unit_cost"));

        java.sql.Timestamp ra = rs.getTimestamp("received_at");
        if (ra != null) rn.setReceivedAt(ra.toLocalDateTime());

        return rn;
    }

    // ══ InboundOrder CRUD ══════════════════════════════════════════

    /**
     * Returns the latest inbound orders with their items.
     */
    public List<InboundOrder> findAll() {
        List<InboundOrder> list = new ArrayList<>();
        String sql = SQL_FIND_ALL;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            InboundOrder current = null;
            int lastId = -1;

            while (rs.next()) {
                int id = rs.getInt("inbound_id");
                if (id != lastId) {
                    if (current != null) list.add(current);
                    current = mapInboundOrder(rs);
                    lastId = id;
                    
                    int itemId = rs.getInt("inbound_item_id");
                    if (itemId > 0) {
                        ReceiptNote item = new ReceiptNote();
                        item.setReceiptId(itemId);
                        item.setInboundId(id);
                        item.setProductId(rs.getInt("product_id"));
                        item.setSkuCode(rs.getString("sku_code"));
                        item.setProductName(rs.getString("product_name"));

                        BigDecimal eq = rs.getBigDecimal("expected_qty");
                        item.setExpectedQty(eq != null ? eq : BigDecimal.ZERO);

                        BigDecimal rq = rs.getBigDecimal("received_qty");
                        item.setReceivedQty(rq != null ? rq : BigDecimal.ZERO);

                        BigDecimal aq = rs.getBigDecimal("accepted_qty");
                        item.setAcceptedQty(aq != null ? aq : BigDecimal.ZERO);

                        BigDecimal djq = rs.getBigDecimal("rejected_qty");
                        item.setRejectedQty(djq != null ? djq : BigDecimal.ZERO);
                        item.setRejectReason(rs.getString("reject_reason"));
                        item.setUnitCost(rs.getBigDecimal("unit_cost"));

                        current.addItem(item);
                    }
                } else {
                    // Same order, add item
                    int itemId = rs.getInt("inbound_item_id");
                    if (itemId > 0) {
                        ReceiptNote item = new ReceiptNote();
                        item.setReceiptId(itemId);
                        item.setInboundId(id);
                        item.setProductId(rs.getInt("product_id"));
                        item.setSkuCode(rs.getString("sku_code"));
                        item.setProductName(rs.getString("product_name"));

                        BigDecimal eq = rs.getBigDecimal("expected_qty");
                        item.setExpectedQty(eq != null ? eq : BigDecimal.ZERO);

                        BigDecimal rq = rs.getBigDecimal("received_qty");
                        item.setReceivedQty(rq != null ? rq : BigDecimal.ZERO);

                        BigDecimal aq = rs.getBigDecimal("accepted_qty");
                        item.setAcceptedQty(aq != null ? aq : BigDecimal.ZERO);

                        BigDecimal djq = rs.getBigDecimal("rejected_qty");
                        item.setRejectedQty(djq != null ? djq : BigDecimal.ZERO);
                        item.setRejectReason(rs.getString("reject_reason"));
                        item.setUnitCost(rs.getBigDecimal("unit_cost"));

                        current.addItem(item);
                    }
                }
            }
            if (current != null) list.add(current);

        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "InboundDAO.findAll: failed to retrieve inbound orders", e);
        }
        return list;
    }

    /**
     * Returns a single inbound order by ID, or null if not found.
     */
    public InboundOrder findById(int inboundId) {
        String sql = SQL_FIND_BY_ID;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, inboundId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    InboundOrder order = mapInboundOrder(rs);
                    List<ReceiptNote> items = findReceiptsByInboundId(inboundId);
                    if (items != null) {
                        for (ReceiptNote rn : items) {
                            order.addItem(rn);
                        }
                    }
                    return order;
                }
            }

        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "InboundDAO.findById: failed for inboundId=" + inboundId, e);
        }
        return null;
    }

    /**
     * Returns inbound orders filtered by status.
     */
    public List<InboundOrder> findByStatus(String status) {
        List<InboundOrder> list = new ArrayList<>();
        String sql = SQL_FIND_BY_STATUS;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, status);
            try (ResultSet rs = ps.executeQuery()) {
                InboundOrder current = null;
                int lastId = -1;

                while (rs.next()) {
                    int id = rs.getInt("inbound_id");
                    if (id != lastId) {
                        if (current != null) list.add(current);
                        current = mapInboundOrder(rs);
                        lastId = id;
                    }
                    int itemId = rs.getInt("inbound_item_id");
                    if (itemId > 0) {
                        ReceiptNote item = new ReceiptNote();
                        item.setReceiptId(itemId);
                        item.setInboundId(id);
                        item.setProductId(rs.getInt("product_id"));
                        item.setSkuCode(rs.getString("sku_code"));
                        item.setProductName(rs.getString("product_name"));

                        BigDecimal eq = rs.getBigDecimal("expected_qty");
                        item.setExpectedQty(eq != null ? eq : BigDecimal.ZERO);

                        BigDecimal rq = rs.getBigDecimal("received_qty");
                        item.setReceivedQty(rq != null ? rq : BigDecimal.ZERO);

                        BigDecimal aq = rs.getBigDecimal("accepted_qty");
                        item.setAcceptedQty(aq != null ? aq : BigDecimal.ZERO);

                        BigDecimal djq = rs.getBigDecimal("rejected_qty");
                        item.setRejectedQty(djq != null ? djq : BigDecimal.ZERO);
                        item.setRejectReason(rs.getString("reject_reason"));
                        item.setUnitCost(rs.getBigDecimal("unit_cost"));

                        current.addItem(item);
                    }
                }
                if (current != null) list.add(current);
            }

        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "InboundDAO.findByStatus: failed for status=" + status, e);
        }
        return list;
    }

    /**
     * Returns inbound orders for a specific warehouse.
     */
    public List<InboundOrder> findByWarehouse(int warehouseId) {
        List<InboundOrder> list = new ArrayList<>();
        String sql = SQL_FIND_BY_WAREHOUSE;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, warehouseId);
            try (ResultSet rs = ps.executeQuery()) {
                InboundOrder current = null;
                int lastId = -1;

                while (rs.next()) {
                    int id = rs.getInt("inbound_id");
                    if (id != lastId) {
                        if (current != null) list.add(current);
                        current = mapInboundOrder(rs);
                        lastId = id;
                    }
                    int itemId = rs.getInt("inbound_item_id");
                    if (itemId > 0) {
                        ReceiptNote item = new ReceiptNote();
                        item.setReceiptId(itemId);
                        item.setInboundId(id);
                        item.setProductId(rs.getInt("product_id"));
                        item.setSkuCode(rs.getString("sku_code"));
                        item.setProductName(rs.getString("product_name"));

                        BigDecimal eq = rs.getBigDecimal("expected_qty");
                        item.setExpectedQty(eq != null ? eq : BigDecimal.ZERO);

                        BigDecimal rq = rs.getBigDecimal("received_qty");
                        item.setReceivedQty(rq != null ? rq : BigDecimal.ZERO);

                        BigDecimal aq = rs.getBigDecimal("accepted_qty");
                        item.setAcceptedQty(aq != null ? aq : BigDecimal.ZERO);

                        BigDecimal djq = rs.getBigDecimal("rejected_qty");
                        item.setRejectedQty(djq != null ? djq : BigDecimal.ZERO);
                        item.setRejectReason(rs.getString("reject_reason"));
                        item.setUnitCost(rs.getBigDecimal("unit_cost"));

                        current.addItem(item);
                    }
                }
                if (current != null) list.add(current);
            }

        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "InboundDAO.findByWarehouse: failed for warehouseId=" + warehouseId, e);
        }
        return list;
    }

    /**
     * Generates the next inbound code in format IN-YYYYMMDD-SEQ.
     * Thread-safe within a transaction.
     */
    public String generateNextInboundCode(Connection conn) throws SQLException {
        String today = java.time.LocalDate.now().toString().replace("-", "");
        String prefix = "IN-" + today + "-%";

        try (PreparedStatement ps = conn.prepareStatement(SQL_NEXT_SEQUENCE)) {
            ps.setString(1, prefix);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int nextSeq = rs.getInt("next_seq");
                    return String.format("IN-%s-%03d", today, nextSeq);
                }
            }
        }
        String fallbackToday = java.time.LocalDate.now().toString().replace("-", "");
        return String.format("IN-%s-001", fallbackToday);
    }

    /**
     * Inserts a new inbound order and returns the generated inboundId.
     */
    public int insert(InboundOrder order) {
        String sql = SQL_INSERT;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {

            String inboundCode;
            try {
                inboundCode = generateNextInboundCode(conn);
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "InboundDAO.insert: failed to generate code", e);
                inboundCode = "IN-" + System.currentTimeMillis();
            }
            order.setInboundCode(inboundCode);

            ps.setString(1, order.getInboundCode());
            ps.setInt(2, order.getWarehouseId());
            if (order.getZoneId() != null) {
                ps.setInt(3, order.getZoneId());
            } else {
                ps.setNull(3, java.sql.Types.INTEGER);
            }
            ps.setString(4, order.getSupplierName());
            if (order.getSupplierId() != null) {
                ps.setInt(5, order.getSupplierId());
            } else {
                ps.setNull(5, java.sql.Types.INTEGER);
            }
            ps.setString(6, order.getStatus() != null ? order.getStatus() : InboundOrder.STATUS_PENDING);
            ps.setInt(7, order.getCreatedBy());
            ps.setString(8, order.getNotes());
            ps.setDate(9, order.getExpectedDate() != null ? java.sql.Date.valueOf(order.getExpectedDate()) : null);
            ps.setDate(10, order.getReceivedDate() != null ? java.sql.Date.valueOf(order.getReceivedDate()) : null);
            ps.setString(11, order.getPaymentTerms());
            ps.setString(12, order.getDeliveryPerson());
            ps.setString(13, order.getDeliveryPhone());

            int rows = ps.executeUpdate();

            if (rows > 0) {
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        int id = keys.getInt(1);
                        order.setInboundId(id);
                        // Auto-set po_reference to inbound_code if not already set
                        if (order.getInboundCode() != null) {
                            setPoReference(id, order.getInboundCode());
                        }
                        return id;
                    }
                }
            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "InboundDAO.insert: failed to insert inbound order", e);
        }
        return -1;
    }

    private void setPoReference(int inboundId, String poReference) {
        String sql = "UPDATE inbound_orders SET po_reference = ? WHERE inbound_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, poReference);
            ps.setInt(2, inboundId);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    /**
     * Updates an existing inbound order.
     */
    public boolean update(InboundOrder order) {
        String sql = SQL_UPDATE;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, order.getInboundCode());
            ps.setInt(2, order.getWarehouseId());
            if (order.getZoneId() != null) {
                ps.setInt(3, order.getZoneId());
            } else {
                ps.setNull(3, java.sql.Types.INTEGER);
            }
            ps.setString(4, order.getSupplierName());
            if (order.getSupplierId() != null) {
                ps.setInt(5, order.getSupplierId());
            } else {
                ps.setNull(5, java.sql.Types.INTEGER);
            }
            ps.setString(6, order.getStatus());
            ps.setInt(7, order.getCreatedBy());
            ps.setString(8, order.getNotes());
            ps.setDate(9, order.getExpectedDate() != null ? java.sql.Date.valueOf(order.getExpectedDate()) : null);
            ps.setDate(10, order.getReceivedDate() != null ? java.sql.Date.valueOf(order.getReceivedDate()) : null);
            ps.setString(11, order.getPaymentTerms());
            ps.setString(12, order.getDeliveryPerson());
            ps.setString(13, order.getDeliveryPhone());
            ps.setInt(14, order.getInboundId());

            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "InboundDAO.update: failed to update inbound order id=" + order.getInboundId(), e);
            return false;
        }
    }

    /**
     * Updates only the status of an inbound order.
     */
    public boolean updateStatus(int inboundId, String status) {
        String sql = SQL_UPDATE_STATUS;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, status);
            ps.setObject(2, InboundOrder.STATUS_RECEIVED.equals(status)
                ? Timestamp.valueOf(java.time.LocalDateTime.now()) : null, java.sql.Types.TIMESTAMP);
            ps.setInt(3, inboundId);

            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "InboundDAO.updateStatus: failed for inboundId=" + inboundId + ", status=" + status, e);
            return false;
        }
    }

    /**
     * Updates only the status without touching received_at (used for workflow transitions
     * like PENDING → PURCHASED or PURCHASED → IN_PROGRESS).
     */
    public boolean updateStatusOnly(int inboundId, String status) {
        String sql = SQL_UPDATE_STATUS_ONLY;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, status);
            ps.setInt(2, inboundId);

            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "InboundDAO.updateStatusOnly: failed for inboundId=" + inboundId + ", status=" + status, e);
            return false;
        }
    }

    /**
     * Updates the zone_id of an inbound order.
     */
    public boolean updateZoneId(int inboundId, Integer zoneId) {
        String sql = "UPDATE inbound_orders SET zone_id = ? WHERE inbound_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (zoneId != null && zoneId > 0) {
                ps.setInt(1, zoneId);
            } else {
                ps.setNull(1, java.sql.Types.INTEGER);
            }
            ps.setInt(2, inboundId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "InboundDAO.updateZoneId: failed for inboundId=" + inboundId + ", zoneId=" + zoneId, e);
            return false;
        }
    }

    // ══ ReceiptNote CRUD ═══════════════════════════════════════════

    /**
     * Returns all receipt line items for a given inbound order.
     */
    public List<ReceiptNote> findReceiptsByInboundId(int inboundId) {
        List<ReceiptNote> list = new ArrayList<>();
        String sql = SQL_FIND_RECEIPTS_BY_INBOUND_ID;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, inboundId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapReceiptNote(rs));
                }
            }

        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "InboundDAO.findReceiptsByInboundId: failed for inboundId=" + inboundId, e);
        }
        return list;
    }

    /**
     * Checks whether all items in an inbound order have been fully received
     * (received_qty >= expected_qty for every line).
     */
    public boolean isAllItemsReceived(int inboundId) {
        String sql = "SELECT COUNT(*) AS cnt FROM inbound_items "
                + "WHERE inbound_id = ? AND received_qty < expected_qty";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, inboundId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("cnt") == 0;
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "InboundDAO.isAllItemsReceived: failed for inboundId=" + inboundId, e);
        }
        return false;
    }

    /**
     * Inserts a new receipt note line item.
     */
    public boolean insertReceipt(ReceiptNote receipt) {
        String sql = SQL_INSERT_RECEIPT;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {

            ps.setInt(1, receipt.getInboundId());
            ps.setInt(2, receipt.getProductId());
            ps.setBigDecimal(3, receipt.getExpectedQty() != null ? receipt.getExpectedQty() : BigDecimal.ZERO);
            ps.setBigDecimal(4, receipt.getReceivedQty() != null ? receipt.getReceivedQty() : BigDecimal.ZERO);
            ps.setBigDecimal(5, receipt.getAcceptedQty() != null ? receipt.getAcceptedQty() : BigDecimal.ZERO);
            ps.setBigDecimal(6, receipt.getRejectedQty() != null ? receipt.getRejectedQty() : BigDecimal.ZERO);
            ps.setString(7, receipt.getRejectReason());
            ps.setBigDecimal(8, receipt.getUnitCost() != null ? receipt.getUnitCost() : BigDecimal.ZERO);

            int rows = ps.executeUpdate();

            if (rows > 0) {
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        receipt.setReceiptId(keys.getInt(1));
                    }
                }
                return true;
            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "InboundDAO.insertReceipt: failed to insert receipt for inboundId=" + receipt.getInboundId(), e);
        }
        return false;
    }

    /**
     * Updates an existing receipt note.
     */
    public boolean updateReceipt(ReceiptNote receipt) {
        String sql = SQL_UPDATE_RECEIPT;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, receipt.getProductId());
            ps.setBigDecimal(2, receipt.getExpectedQty() != null ? receipt.getExpectedQty() : BigDecimal.ZERO);
            ps.setBigDecimal(3, receipt.getReceivedQty() != null ? receipt.getReceivedQty() : BigDecimal.ZERO);
            ps.setBigDecimal(4, receipt.getAcceptedQty() != null ? receipt.getAcceptedQty() : BigDecimal.ZERO);
            ps.setBigDecimal(5, receipt.getRejectedQty() != null ? receipt.getRejectedQty() : BigDecimal.ZERO);
            ps.setString(6, receipt.getRejectReason());
            ps.setBigDecimal(7, receipt.getUnitCost() != null ? receipt.getUnitCost() : BigDecimal.ZERO);
            ps.setInt(8, receipt.getReceiptId());

            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "InboundDAO.updateReceipt: failed to update receiptId=" + receipt.getReceiptId(), e);
            return false;
        }
    }

    /**
     * Updates received_qty on the existing inbound_items row for a given (inbound_id, product_id) pair.
     * If no row exists yet (edge case), inserts one with expected_qty=0.
     *
     * @param inboundId  The inbound order ID.
     * @param productId  The product ID.
     * @param receivedQty The actual received quantity.
     * @return true on success.
     */
    public boolean updateReceivedQty(int inboundId, int productId, BigDecimal receivedQty) {
        String sqlUpdate = "UPDATE inbound_items SET received_qty = received_qty + ? " +
                           "WHERE inbound_id = ? AND product_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlUpdate)) {
            ps.setBigDecimal(1, receivedQty != null ? receivedQty : BigDecimal.ZERO);
            ps.setInt(2, inboundId);
            ps.setInt(3, productId);
            int rows = ps.executeUpdate();
            if (rows > 0) return true;

            // No existing row — insert one
            String sqlInsert = "INSERT INTO inbound_items (inbound_id, product_id, expected_qty, received_qty) VALUES (?, ?, 0, ?)";
            try (PreparedStatement psIns = conn.prepareStatement(sqlInsert)) {
                psIns.setInt(1, inboundId);
                psIns.setInt(2, productId);
                psIns.setBigDecimal(3, receivedQty != null ? receivedQty : BigDecimal.ZERO);
                return psIns.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "InboundDAO.updateReceivedQty: failed inboundId=" + inboundId
                    + " productId=" + productId, e);
            return false;
        }
    }

    /**
     * Updates received_qty, accepted_qty, rejected_qty, reject_reason, unit_cost on inbound_items.
     */
    public boolean updateReceivedQtys(int inboundId, int productId,
            BigDecimal receivedQty, BigDecimal acceptedQty, BigDecimal rejectedQty,
            String rejectReason, BigDecimal unitCost) {
        String sqlUpdate = "UPDATE inbound_items SET received_qty = received_qty + ?, accepted_qty = accepted_qty + ?, rejected_qty = rejected_qty + ?, reject_reason = ?, unit_cost = ? "
                          + "WHERE inbound_id = ? AND product_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlUpdate)) {
            ps.setBigDecimal(1, receivedQty != null ? receivedQty : BigDecimal.ZERO);
            ps.setBigDecimal(2, acceptedQty != null ? acceptedQty : BigDecimal.ZERO);
            ps.setBigDecimal(3, rejectedQty != null ? rejectedQty : BigDecimal.ZERO);
            ps.setString(4, rejectReason);
            ps.setBigDecimal(5, unitCost != null ? unitCost : BigDecimal.ZERO);
            ps.setInt(6, inboundId);
            ps.setInt(7, productId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "updateReceivedQtys with reject_reason failed", e);
            return false;
        }
    }

    /**
     * Updates received_qty, accepted_qty, rejected_qty, unit_cost on inbound_items.
     * Falls back gracefully if the unit_cost column does not exist yet.
     */
    public boolean updateReceivedQtys(int inboundId, int productId,
            BigDecimal receivedQty, BigDecimal acceptedQty, BigDecimal rejectedQty, BigDecimal unitCost) {
        String sqlUpdate = "UPDATE inbound_items SET received_qty = received_qty + ?, accepted_qty = accepted_qty + ?, rejected_qty = rejected_qty + ?, unit_cost = ? "
                          + "WHERE inbound_id = ? AND product_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlUpdate)) {
            ps.setBigDecimal(1, receivedQty != null ? receivedQty : BigDecimal.ZERO);
            ps.setBigDecimal(2, acceptedQty != null ? acceptedQty : BigDecimal.ZERO);
            ps.setBigDecimal(3, rejectedQty != null ? rejectedQty : BigDecimal.ZERO);
            ps.setBigDecimal(4, unitCost != null ? unitCost : BigDecimal.ZERO);
            ps.setInt(5, inboundId);
            ps.setInt(6, productId);
            int rows = ps.executeUpdate();
            if (rows > 0) return true;
        } catch (SQLException e) {
            LOGGER.fine("updateReceivedQtys with full cols failed, falling back: " + e.getMessage());
        }
        // Fallback nếu schema chưa đầy đủ
        return updateReceivedQtys(inboundId, productId, receivedQty, unitCost);
    }

    /**
     * Updates received_qty + unit_cost on inbound_items (đơn giản hoá sau khi bỏ QC).
     * Falls back gracefully if the unit_cost column does not exist yet.
     */
    public boolean updateReceivedQtys(int inboundId, int productId,
            BigDecimal receivedQty, BigDecimal unitCost) {
        String sqlUpdate = "UPDATE inbound_items SET received_qty = received_qty + ?, unit_cost = ? "
                          + "WHERE inbound_id = ? AND product_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlUpdate)) {
            ps.setBigDecimal(1, receivedQty != null ? receivedQty : BigDecimal.ZERO);
            ps.setBigDecimal(2, unitCost != null ? unitCost : BigDecimal.ZERO);
            ps.setInt(3, inboundId);
            ps.setInt(4, productId);
            int rows = ps.executeUpdate();
            if (rows > 0) return true;
        } catch (SQLException e) {
            LOGGER.fine("updateReceivedQtys with unit_cost failed, falling back: " + e.getMessage());
        }
        // Fallback nếu schema chưa có cột unit_cost
        return updateReceivedQty(inboundId, productId, receivedQty);
    }
}

