package com.wms.dao;

import com.wms.model.OutboundItem;
import com.wms.model.OutboundOrder;
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
 * OutboundDAO — Data Access Object for outbound order and item operations.
 */
public class OutboundDAO extends BaseDAO {

    private static final Logger LOGGER = Logger.getLogger(OutboundDAO.class.getName());

    /** insert() sentinel: outbound_code collided with an existing row (uq_outbound_code) — caller should retry with a new code. */
    public static final int DUPLICATE_CODE = -2;

    private static final java.util.Map<Integer, String> warehouseStaffCache = new java.util.concurrent.ConcurrentHashMap<>();

    private String getPrimaryWarehouseStaffName(int warehouseId) {
        return warehouseStaffCache.computeIfAbsent(warehouseId, id -> {
            try {
                com.wms.model.User u = new com.wms.dao.UserDAO().findPrimaryWarehouseStaff(id);
                return u != null ? u.getFullName() : "";
            } catch (Exception e) {
                return "";
            }
        });
    }

    /**
     * Retrieves all outbound orders (top 100, newest first).
     */
    public List<OutboundOrder> findAll() {
        List<OutboundOrder> list = new ArrayList<>();
        String sql = "SELECT o.outbound_id, o.outbound_code, o.order_id, o.warehouse_id, o.version, o.restocked_at, "
                   + "w.warehouse_name, o.status, o.note, o.created_at, o.picked_by, o.shipped_at, "
                   + "ord.order_code, ord.tracking_no, ord.is_label_printed, ord.is_rts_pushed, ord.review_note, sd.shipping_address, sd.courier_name, sd.recipient_name, "
                   + "ch.platform AS channel_name, p.full_name AS picker_name "
                   + "FROM outbound_orders o "
                   + "LEFT JOIN warehouses w ON o.warehouse_id = w.warehouse_id "
                   + "LEFT JOIN orders ord ON o.order_id = ord.order_id "
                   + "LEFT JOIN order_shipping_details sd ON o.order_id = sd.order_id "
                   + "LEFT JOIN channels ch ON ord.channel_id = ch.channel_id "
                   + "LEFT JOIN users p ON o.picked_by = p.user_id "
                   + "ORDER BY o.created_at DESC LIMIT 100";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                OutboundOrder order = mapRow(rs);
                order.setItems(findItemsByOutboundId(order.getOutboundId()));
                list.add(order);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "OutboundDAO: Failed to retrieve outbound orders", e);
        }
        return list;
    }

    /**
     * Finds a single outbound order by ID.
     */
    public OutboundOrder findById(int id) {
        String sql = "SELECT o.outbound_id, o.outbound_code, o.order_id, o.warehouse_id, o.version, o.restocked_at, "
                   + "w.warehouse_name, o.status, o.note, o.created_at, o.picked_by, o.shipped_at, "
                   + "ord.order_code, ord.tracking_no, ord.is_label_printed, ord.is_rts_pushed, ord.review_note, sd.shipping_address, sd.courier_name, sd.recipient_name, "
                   + "ch.platform AS channel_name, p.full_name AS picker_name "
                   + "FROM outbound_orders o "
                   + "LEFT JOIN warehouses w ON o.warehouse_id = w.warehouse_id "
                   + "LEFT JOIN orders ord ON o.order_id = ord.order_id "
                   + "LEFT JOIN order_shipping_details sd ON o.order_id = sd.order_id "
                   + "LEFT JOIN channels ch ON ord.channel_id = ch.channel_id "
                   + "LEFT JOIN users p ON o.picked_by = p.user_id "
                   + "WHERE o.outbound_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    OutboundOrder order = mapRow(rs);
                    order.setItems(findItemsByOutboundId(order.getOutboundId()));
                    return order;
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "OutboundDAO: Failed to find outbound order by id=" + id, e);
        }
        return null;
    }

    /**
     * Finds outbound orders filtered by status.
     */
    public List<OutboundOrder> findByStatus(String status) {
        List<OutboundOrder> list = new ArrayList<>();
        String sql = "SELECT o.outbound_id, o.outbound_code, o.order_id, o.warehouse_id, o.version, o.restocked_at, "
                   + "w.warehouse_name, o.status, o.note, o.created_at, o.picked_by, o.shipped_at, "
                   + "ord.order_code, ord.tracking_no, ord.is_label_printed, ord.is_rts_pushed, ord.review_note, sd.shipping_address, sd.courier_name, sd.recipient_name, "
                   + "ch.platform AS channel_name, p.full_name AS picker_name "
                   + "FROM outbound_orders o "
                   + "LEFT JOIN warehouses w ON o.warehouse_id = w.warehouse_id "
                   + "LEFT JOIN orders ord ON o.order_id = ord.order_id "
                   + "LEFT JOIN order_shipping_details sd ON o.order_id = sd.order_id "
                   + "LEFT JOIN channels ch ON ord.channel_id = ch.channel_id "
                   + "LEFT JOIN users p ON o.picked_by = p.user_id "
                   + "WHERE o.status = ? "
                   + "ORDER BY o.created_at DESC LIMIT 100";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, status);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    OutboundOrder order = mapRow(rs);
                    order.setItems(findItemsByOutboundId(order.getOutboundId()));
                    list.add(order);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "OutboundDAO: Failed to find outbound orders by status=" + status, e);
        }
        return list;
    }

    /**
     * Finds outbound orders filtered by warehouse.
     */
    public List<OutboundOrder> findByWarehouse(int warehouseId) {
        List<OutboundOrder> list = new ArrayList<>();
        String sql = "SELECT o.outbound_id, o.outbound_code, o.order_id, o.warehouse_id, o.version, o.restocked_at, "
                   + "w.warehouse_name, o.status, o.note, o.created_at, o.picked_by, o.shipped_at, "
                   + "ord.order_code, ord.tracking_no, ord.is_label_printed, ord.is_rts_pushed, ord.review_note, sd.shipping_address, sd.courier_name, sd.recipient_name, "
                   + "ch.platform AS channel_name, p.full_name AS picker_name "
                   + "FROM outbound_orders o "
                   + "LEFT JOIN warehouses w ON o.warehouse_id = w.warehouse_id "
                   + "LEFT JOIN orders ord ON o.order_id = ord.order_id "
                   + "LEFT JOIN order_shipping_details sd ON o.order_id = sd.order_id "
                   + "LEFT JOIN channels ch ON ord.channel_id = ch.channel_id "
                   + "LEFT JOIN users p ON o.picked_by = p.user_id "
                   + "WHERE o.warehouse_id = ? "
                   + "ORDER BY o.created_at DESC LIMIT 100";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, warehouseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    OutboundOrder order = mapRow(rs);
                    order.setItems(findItemsByOutboundId(order.getOutboundId()));
                    list.add(order);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "OutboundDAO: Failed to find outbound orders by warehouse=" + warehouseId, e);
        }
        return list;
    }

    /**
     * Returns true if any non-cancelled outbound already exists for the given order.
     * Used by autoCreateFromOrder to prevent duplicate outbound sheets on re-approve.
     */
    public boolean hasActiveOutboundForOrder(int orderId) {
        String sql = "SELECT 1 FROM outbound_orders WHERE order_id = ? AND status != 'CANCELLED' LIMIT 1";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "hasActiveOutboundForOrder failed orderId=" + orderId, e);
            return false;
        }
    }

    /**
     * Inserts a new outbound order and returns the generated outboundId.
     */
    public int insert(OutboundOrder order) {
        String sql = "INSERT INTO outbound_orders "
                   + "(outbound_code, order_id, warehouse_id, created_by, status, note, created_at, picked_by, shipped_at) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, order.getOutboundCode());
            ps.setInt(2, order.getOrderId());
            ps.setInt(3, order.getWarehouseId());
            if (order.getCreatedBy() != null) {
                ps.setInt(4, order.getCreatedBy());
            } else {
                ps.setNull(4, java.sql.Types.INTEGER);
            }
            ps.setString(5, order.getStatus() != null ? order.getStatus() : OutboundOrder.STATUS_PENDING);
            ps.setString(6, order.getNotes());
            ps.setTimestamp(7, order.getCreatedAt() != null ? Timestamp.valueOf(order.getCreatedAt()) : new Timestamp(System.currentTimeMillis()));
            if (order.getPickedBy() != null) {
                ps.setInt(8, order.getPickedBy());
            } else {
                ps.setNull(8, java.sql.Types.INTEGER);
            }
            ps.setTimestamp(9, order.getPickedAt() != null ? Timestamp.valueOf(order.getPickedAt()) : null);

            int rows = ps.executeUpdate();
            if (rows > 0) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            }
        } catch (java.sql.SQLIntegrityConstraintViolationException e) {
            LOGGER.log(Level.WARNING, "OutboundDAO: outbound_code collided with an existing row: " + order.getOutboundCode(), e);
            return DUPLICATE_CODE;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "OutboundDAO: Failed to insert outbound order", e);
        }
        return -1;
    }

    /**
     * Updates an existing outbound order.
     */
    public boolean update(OutboundOrder order) {
        String sql = "UPDATE outbound_orders SET "
                   + "outbound_code = ?, order_id = ?, warehouse_id = ?, status = ?, "
                   + "note = ?, picked_by = ?, shipped_at = ? "
                   + "WHERE outbound_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, order.getOutboundCode());
            ps.setInt(2, order.getOrderId());
            ps.setInt(3, order.getWarehouseId());
            ps.setString(4, order.getStatus());
            ps.setString(5, order.getNotes());
            if (order.getPickedBy() != null) {
                ps.setInt(6, order.getPickedBy());
            } else {
                ps.setNull(6, java.sql.Types.INTEGER);
            }
            ps.setTimestamp(7, order.getPickedAt() != null ? Timestamp.valueOf(order.getPickedAt()) : null);
            ps.setInt(8, order.getOutboundId());

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "OutboundDAO: Failed to update outbound order id=" + order.getOutboundId(), e);
        }
        return false;
    }

    /**
     * Updates only the status of an outbound order.
     */
    public boolean updateStatus(int outboundId, String status) {
        String sql = "UPDATE outbound_orders SET status = ? WHERE outbound_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, status);
            ps.setInt(2, outboundId);

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "OutboundDAO: Failed to update status for id=" + outboundId, e);
        }
        return false;
    }

    /**
     * Optimistic Locking for SHIPPED.
     *
     * Previously updateStatus() only set WHERE outbound_id = ?, ignoring version.
     * Two pickers could both click SHIPPED → race condition → stock deducted twice.
     *
     * Fix: pass expectedVersion (the version read earlier). If the DB version
     * already changed, 0 rows affected → return false, caller must surface error.
     *
     * Requires the "version" column on outbound_orders (schema migration included).
     */
    public boolean compareAndSetStatus(int outboundId, String status, int expectedVersion) {
        String sql = "UPDATE outbound_orders SET status = ?, version = version + 1 "
                   + "WHERE outbound_id = ? AND version = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, status);
            ps.setInt(2, outboundId);
            ps.setInt(3, expectedVersion);

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "OutboundDAO.compareAndSetStatus failed id=" + outboundId, e);
        }
        return false;
    }

    /**
     * Atomically claims the restock for a cancelled outbound order.
     * Returns true only if this call is the one that transitions restocked_at from NULL to set —
     * a second call (stale UI, cleared localStorage, different browser) returns false instead of
     * releasing the same inventory allocation a second time.
     */
    public boolean claimRestock(int outboundId, Integer userId) {
        String sql = "UPDATE outbound_orders SET restocked_at = NOW(), restocked_by = ? "
                   + "WHERE outbound_id = ? AND restocked_at IS NULL";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (userId != null) ps.setInt(1, userId);
            else ps.setNull(1, java.sql.Types.INTEGER);
            ps.setInt(2, outboundId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "OutboundDAO.claimRestock failed outboundId=" + outboundId, e);
        }
        return false;
    }

    /**
     * Retrieves all line items for a given outbound order.
     */
    public List<OutboundItem> findItemsByOutboundId(int outboundId) {
        List<OutboundItem> list = new ArrayList<>();
        String sql = "SELECT i.outbound_item_id, i.outbound_id, i.product_id, i.qty, i.picked_qty, i.shelf_location, "
                   + "p.sku_code, p.product_name "
                   + "FROM outbound_items i "
                   + "LEFT JOIN products p ON i.product_id = p.product_id "
                   + "WHERE i.outbound_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, outboundId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    OutboundItem item = new OutboundItem();
                    item.setOutboundItemId(rs.getInt("outbound_item_id"));
                    item.setOutboundId(rs.getInt("outbound_id"));
                    item.setProductId(rs.getInt("product_id"));
                    item.setQty(rs.getBigDecimal("qty"));
                    item.setPickedQty(rs.getBigDecimal("picked_qty"));
                    item.setShelfLocation(rs.getString("shelf_location"));
                    item.setSkuCode(rs.getString("sku_code"));
                    item.setSkuName(rs.getString("product_name"));
                    list.add(item);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "OutboundDAO: Failed to retrieve items for outboundId=" + outboundId, e);
        }
        return list;
    }

    /**
     * Inserts a single outbound line item.
     */
    public boolean insertItem(OutboundItem item) {
        String sql = "INSERT INTO outbound_items "
                   + "(outbound_id, product_id, qty, picked_qty, shelf_location) "
                   + "VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, item.getOutboundId());
            ps.setInt(2, item.getProductId());
            ps.setBigDecimal(3, item.getQty());
            ps.setBigDecimal(4, item.getPickedQty() != null ? item.getPickedQty() : BigDecimal.ZERO);
            ps.setString(5, item.getShelfLocation());

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "OutboundDAO: Failed to insert outbound item", e);
        }
        return false;
    }

    public boolean updateItemQty(int outboundId, int productId, BigDecimal qty) {
        String sql = "UPDATE outbound_items SET qty = ? WHERE outbound_id = ? AND product_id = ?";
        return super.update(LOGGER, sql, qty, outboundId, productId) > 0;
    }

    public boolean updateNote(int outboundId, String note) {
        String sql = "UPDATE outbound_orders SET note = ? WHERE outbound_id = ?";
        return super.update(LOGGER, sql, note, outboundId) > 0;
    }

    /**
     * Maps a ResultSet row to an OutboundOrder instance.
     */
    private OutboundOrder mapRow(ResultSet rs) throws SQLException {
        OutboundOrder o = new OutboundOrder();
        o.setOutboundId(rs.getInt("outbound_id"));
        o.setOutboundCode(rs.getString("outbound_code"));
        o.setOrderId(rs.getInt("order_id"));
        o.setWarehouseId(rs.getInt("warehouse_id"));
        o.setWarehouseName(rs.getString("warehouse_name"));
        o.setStatus(rs.getString("status"));
        o.setNotes(rs.getString("note"));
        o.setVersion(rs.getInt("version"));
        o.setRestocked(rs.getTimestamp("restocked_at") != null);

        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            o.setCreatedAt(createdAt.toLocalDateTime());
        }

        int pickedBy = rs.getInt("picked_by");
        o.setPickedBy(rs.wasNull() ? null : pickedBy);

        Timestamp shippedAt = rs.getTimestamp("shipped_at");
        if (shippedAt != null) {
            o.setPickedAt(shippedAt.toLocalDateTime());
        }

        try {
            o.setOrderCode(rs.getString("order_code"));
        } catch (SQLException e) { /* ignore */ }
        try {
            o.setShippingAddress(rs.getString("shipping_address"));
        } catch (SQLException e) { /* ignore */ }
        try {
            o.setCourierName(rs.getString("courier_name"));
        } catch (SQLException e) { /* ignore */ }
        try {
            o.setRecipientName(rs.getString("recipient_name"));
        } catch (SQLException e) { /* ignore */ }

        try {
            o.setTrackingNo(rs.getString("tracking_no"));
        } catch (SQLException e) { /* ignore */ }

        try {
            o.setLabelPrinted(rs.getInt("is_label_printed") == 1);
        } catch (SQLException e) { /* ignore */ }
        try {
            o.setRtsPushed(rs.getInt("is_rts_pushed") == 1);
        } catch (SQLException e) { /* ignore */ }
        try {
            o.setReviewNote(rs.getString("review_note"));
        } catch (SQLException e) { /* ignore */ }

        try {
            o.setChannelName(rs.getString("channel_name"));
        } catch (SQLException e) { /* ignore */ }

        String pickerName = null;
        try {
            pickerName = rs.getString("picker_name");
        } catch (SQLException e) { /* ignore */ }
        if (pickerName == null || pickerName.trim().isEmpty()) {
            pickerName = getPrimaryWarehouseStaffName(o.getWarehouseId());
        }
        o.setPickerName(pickerName);

        return o;
    }

    // ── Picking-sheet lifecycle (Directed Picking) ──
    // Replaces prior stubs with real implementations to support directed picking.

    public void assignPicker(int outboundId, Integer userId) {
        String sql = "UPDATE outbound_orders SET picked_by = ? WHERE outbound_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (userId != null) ps.setInt(1, userId);
            else ps.setNull(1, java.sql.Types.INTEGER);
            ps.setInt(2, outboundId);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "OutboundDAO.assignPicker failed outboundId=" + outboundId, e);
        }
    }

    public void createPickingSheet(int outboundId, Integer userId) {
        String checkSql = "SELECT sheet_id FROM picking_sheets WHERE outbound_id = ? LIMIT 1";
        String insertSql = "INSERT INTO picking_sheets (outbound_id, picker_id, status, started_at) "
                         + "VALUES (?, ?, 'IN_PROGRESS', NOW())";

        try (Connection conn = DBConnection.getConnection()) {
            try (PreparedStatement check = conn.prepareStatement(checkSql)) {
                check.setInt(1, outboundId);
                try (ResultSet rs = check.executeQuery()) {
                    if (rs.next()) {
                        return;
                    }
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setInt(1, outboundId);
                if (userId != null) {
                    ps.setInt(2, userId);
                } else {
                    ps.setNull(2, java.sql.Types.INTEGER);
                }
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "OutboundDAO: Failed to create picking sheet outboundId=" + outboundId, e);
        }
    }

    /**
     * Marks all line items as picked (set picked_qty = qty).
     * Called when Warehouse staff clicks "Complete picking".
     */
    public void markAllPicked(int outboundId) {
        String sql = "UPDATE outbound_items SET picked_qty = qty WHERE outbound_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, outboundId);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "OutboundDAO.markAllPicked failed outboundId=" + outboundId, e);
        }
    }

    /**
     * Closes the picking sheet with status COMPLETED.
     */
    public void completePickingSheet(int outboundId) {
        String sql = "UPDATE picking_sheets SET status = 'COMPLETED', completed_at = NOW() "
                   + "WHERE outbound_id = ? AND status = 'IN_PROGRESS'";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, outboundId);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "OutboundDAO.completePickingSheet failed outboundId=" + outboundId, e);
        }
    }

    /**
     * Creates a shipping label record when packing is done.
     * Stores a placeholder tracking_no if Sales has not provided one.
     */
    public void createShippingLabel(int outboundId) {
        String sql = "INSERT INTO shipping_labels (outbound_id, courier_name, status, created_at) "
                   + "VALUES (?, 'CHƯA CHỌN', 'CREATED', NOW())";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, outboundId);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "OutboundDAO.createShippingLabel failed outboundId=" + outboundId, e);
        }
    }

    /**
     * Creates a delivery note record when the order is SHIPPED.
     */
    public void createDeliveryNote(int outboundId, Integer userId) {
        String sql = "INSERT INTO delivery_notes (outbound_id, delivered_by, status, created_at) "
                   + "VALUES (?, ?, 'SHIPPED', NOW())";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, outboundId);
            if (userId != null) ps.setInt(2, userId);
            else ps.setNull(2, java.sql.Types.INTEGER);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "OutboundDAO.createDeliveryNote failed outboundId=" + outboundId, e);
        }
    }

    /**
     * Persists the picked state of a single outbound line item.
     * When picked=true, picked_qty is set equal to the ordered qty; when
     * picked=false it is reset to 0.
     */
    public boolean updateItemPicked(int outboundId, int productId, boolean picked) {
        return super.update(LOGGER,
            "UPDATE outbound_items SET picked_qty = CASE WHEN ? THEN qty ELSE 0 END "
          + "WHERE outbound_id = ? AND product_id = ?",
            picked, outboundId, productId) > 0;
    }

    /**
     * Find outbound orders for one warehouse filtered by status.
     * Used by warehouse staff dashboards.
     */
    public List<OutboundOrder> findByWarehouseAndStatus(int warehouseId, String status) {
        List<OutboundOrder> list = new ArrayList<>();
        String sql = "SELECT o.outbound_id, o.outbound_code, o.order_id, o.warehouse_id, o.version, o.restocked_at, "
                   + "w.warehouse_name, o.status, o.note, o.created_at, o.picked_by, o.shipped_at, "
                   + "ord.order_code, ord.tracking_no, ord.is_label_printed, ord.is_rts_pushed, ord.review_note, sd.shipping_address, sd.courier_name, sd.recipient_name, "
                   + "ch.platform AS channel_name, p.full_name AS picker_name "
                   + "FROM outbound_orders o "
                   + "LEFT JOIN warehouses w ON o.warehouse_id = w.warehouse_id "
                   + "LEFT JOIN orders ord ON o.order_id = ord.order_id "
                   + "LEFT JOIN order_shipping_details sd ON o.order_id = sd.order_id "
                   + "LEFT JOIN channels ch ON ord.channel_id = ch.channel_id "
                   + "LEFT JOIN users p ON o.picked_by = p.user_id "
                   + "WHERE o.warehouse_id = ? AND o.status = ? "
                   + "ORDER BY o.created_at DESC LIMIT 100";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, warehouseId);
            ps.setString(2, status);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    OutboundOrder order = mapRow(rs);
                    order.setItems(findItemsByOutboundId(order.getOutboundId()));
                    list.add(order);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING,
                "OutboundDAO: Failed to find by warehouse=" + warehouseId + " status=" + status, e);
        }
        return list;
    }

    public int findActiveOutboundIdByOrderCode(String orderCode) {
        String sql = "SELECT oo.outbound_id FROM outbound_orders oo "
                   + "JOIN orders o ON oo.order_id = o.order_id "
                   + "WHERE o.order_code = ? AND oo.status != 'CANCELLED' LIMIT 1";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, orderCode);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "findActiveOutboundIdByOrderCode failed for orderCode=" + orderCode, e);
        }
        return -1;
    }

    /**
     * Checks whether the outbound order belongs to an omni-channel (Lazada, Shopee,
     * TikTok, Website, etc.) vs a walk-in counter sale.
     * Used by OutboundService to decide whether to auto-approve the ledger.
     */
    public boolean isOmnichannelOutbound(int outboundId) {
        String sql =
            "SELECT c.platform AS channel_name, o.channel, o.note " +
            "FROM outbound_orders oo " +
            "LEFT JOIN orders o ON oo.order_id = o.order_id " +
            "LEFT JOIN channels c ON o.channel_id = c.channel_id " +
            "WHERE oo.outbound_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, outboundId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String channelName = rs.getString("channel_name");
                    String rawChannel  = rs.getString("channel");
                    String note        = rs.getString("note");

                    if (channelName == null) channelName = "Khách mua lẻ";
                    String lowerName = channelName.toLowerCase();
                    if (lowerName.contains("shopee")  || lowerName.contains("tiktok")  ||
                        lowerName.contains("lazada")  || lowerName.contains("website") ||
                        lowerName.contains("online")  || lowerName.contains("khách mua lẻ") ||
                        lowerName.contains("retail")) {
                        return true;
                    }
                    if (rawChannel != null) {
                        String lowerRaw = rawChannel.toLowerCase();
                        if (lowerRaw.contains("shopee")  || lowerRaw.contains("tiktok")  ||
                            lowerRaw.contains("lazada")  || lowerRaw.contains("website") ||
                            lowerRaw.contains("online")  || lowerRaw.contains("retail")) {
                            return true;
                        }
                    }
                    if (note != null) {
                        String lowerNote = note.toLowerCase();
                        if (lowerNote.contains("shopee") || lowerNote.contains("tiktok") ||
                            lowerNote.contains("lazada") || lowerNote.contains("website")) {
                            return true;
                        }
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "OutboundDAO.isOmnichannelOutbound failed for outboundId=" + outboundId, e);
        }
        return false;
    }

    /**
     * Cancels all pending outbound orders for a given order code (legacy string).
     * Looks up the numeric order_id from orders table first.
     */
    public int cancelByOrderId(String orderCode) {
        // First find the numeric order_id
        int numericOrderId = -1;
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT order_id FROM orders WHERE order_code = ?")) {
            ps.setString(1, orderCode);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    numericOrderId = rs.getInt("order_id");
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "cancelByOrderId: failed to find order_id for " + orderCode, e);
            return 0;
        }

        if (numericOrderId <= 0) {
            LOGGER.warning("cancelByOrderId: order not found for code: " + orderCode);
            return 0;
        }

        String sql = "UPDATE outbound_orders SET status = 'CANCELLED', note = CONCAT(COALESCE(note, ''), ' [Hủy theo đơn hàng gốc]') "
                   + "WHERE order_id = ? AND status NOT IN ('CANCELLED', 'SHIPPED')";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, numericOrderId);
            int updated = ps.executeUpdate();
            if (updated > 0) {
                LOGGER.info("Cancelled " + updated + " outbound order(s) for orderCode: " + orderCode + " (orderId: " + numericOrderId + ")");
            }
            return updated;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "cancelByOrderId failed: " + orderCode, e);
            return 0;
        }
    }
}
