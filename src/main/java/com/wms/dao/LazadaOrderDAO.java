package com.wms.dao;

import com.wms.model.LazadaOrder;
import com.wms.model.LazadaOrderItem;
import com.wms.util.DBConnection;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * LazadaOrderDAO — Data Access Object for lazada_orders and lazada_order_items tables.
 *
 * <p>Provides CRUD operations for Lazada orders synced from the Lazada API,
 * supporting the WMS order workflow: NEW → APPROVED → PACKED → HANDED_OVER → SHIPPING → DELIVERED.</p>
 *
 * <p>Note: does not extend BaseDAO — uses direct JDBC for complex multi-table
 * operations (upsert, item batch inserts) that are clearer as standalone methods.</p>
 */
public class LazadaOrderDAO {

    private static final Logger LOGGER = Logger.getLogger(LazadaOrderDAO.class.getName());

    // ══ QUERY HELPERS ════════════════════════════════════════════════════

    private LazadaOrder mapRow(ResultSet rs) throws SQLException {
        LazadaOrder o = new LazadaOrder();
        o.setLazadaOrderId(rs.getInt("lazada_order_id"));
        o.setLazadaOrderIdStr(rs.getString("lazada_order_id_str"));
        o.setLazadaOrderNumber(rs.getString("lazada_order_number"));
        o.setChannelId(rs.getInt("channel_id"));
        o.setChannelName(rs.getString("channel_name"));
        o.setStatus(rs.getString("status"));
        o.setWmsStatus(rs.getString("wms_status"));
        o.setCustomerName(rs.getString("customer_name"));
        o.setCustomerPhone(rs.getString("customer_phone"));
        o.setShippingAddress(rs.getString("shipping_address"));
        o.setShippingCity(rs.getString("shipping_city"));
        o.setPrice(rs.getBigDecimal("price"));
        o.setShippingFee(rs.getBigDecimal("shipping_fee"));
        o.setVoucherSeller(toBigDecimal(rs, "voucher_seller"));
        o.setVoucherPlatform(toBigDecimal(rs, "voucher_platform"));
        o.setPaymentMethod(rs.getString("payment_method"));
        o.setBuyerNote(rs.getString("buyer_note"));
        o.setWarehouseId(rs.getInt("warehouse_id"));
        o.setAssignedBy(rs.getInt("assigned_by"));
        Timestamp assignedAt = rs.getTimestamp("assigned_at");
        if (assignedAt != null) o.setAssignedAt(assignedAt.toLocalDateTime());
        o.setPackageId(rs.getString("package_id"));
        o.setTrackingNumber(rs.getString("tracking_number"));
        o.setShipmentProvider(rs.getString("shipment_provider"));
        o.setShipmentProviderCode(rs.getString("shipment_provider_code"));
        Timestamp createdAt = rs.getTimestamp("lazada_created_at");
        if (createdAt != null) o.setLazadaCreatedAt(createdAt.toLocalDateTime());
        Timestamp updatedAt = rs.getTimestamp("lazada_updated_at");
        if (updatedAt != null) o.setLazadaUpdatedAt(updatedAt.toLocalDateTime());
        Timestamp rtsAt = rs.getTimestamp("rts_at");
        if (rtsAt != null) o.setRtsAt(rtsAt.toLocalDateTime());
        Timestamp deliveredAt = rs.getTimestamp("delivered_at");
        if (deliveredAt != null) o.setDeliveredAt(deliveredAt.toLocalDateTime());
        Timestamp syncedAt = rs.getTimestamp("synced_at");
        if (syncedAt != null) o.setSyncedAt(syncedAt.toLocalDateTime());
        return o;
    }

    private LazadaOrderItem mapItemRow(ResultSet rs) throws SQLException {
        LazadaOrderItem i = new LazadaOrderItem();
        i.setLazadaOrderItemId(rs.getInt("item_id"));
        i.setLazadaOrderIdStr(rs.getString("lazada_order_id_str"));
        i.setOrderItemId(rs.getString("order_item_id"));
        i.setSku(rs.getString("sku"));
        i.setShopSku(rs.getString("shop_sku"));
        i.setProductName(rs.getString("product_name"));
        i.setProductImage(rs.getString("product_image"));
        i.setQuantity(rs.getInt("quantity"));
        i.setPaidPrice(rs.getBigDecimal("paid_price"));
        i.setItemPrice(rs.getBigDecimal("item_price"));
        i.setSupplyPrice(toBigDecimal(rs, "supply_price"));
        i.setStatus(rs.getString("status"));
        i.setProductId(rs.getInt("product_id"));
        i.setReservedQty(rs.getInt("reserved_qty"));
        i.setFulfilledQty(rs.getInt("fulfilled_qty"));
        return i;
    }

    private static BigDecimal toBigDecimal(ResultSet rs, String col) {
        try { return rs.getBigDecimal(col); } catch (SQLException e) { return null; }
    }

    // ══ ORDER QUERIES ════════════════════════════════════════════════════

    /**
     * Returns all LazadaOrder records joined with channel_name, ordered by synced_at DESC.
     */
    public List<LazadaOrder> findAll() {
        String sql =
            "SELECT lo.*, c.channel_name "
          + "FROM lazada_orders lo "
          + "LEFT JOIN channels c ON lo.channel_id = c.channel_id "
          + "ORDER BY lo.synced_at DESC";
        List<LazadaOrder> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "findAll failed", e);
        }
        return list;
    }

    /**
     * Finds a LazadaOrder by its Lazada order_id string.
     */
    public LazadaOrder findByLazadaOrderIdStr(String lazadaOrderIdStr) {
        String sql =
            "SELECT lo.*, c.channel_name "
          + "FROM lazada_orders lo "
          + "LEFT JOIN channels c ON lo.channel_id = c.channel_id "
          + "WHERE lo.lazada_order_id_str = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, lazadaOrderIdStr);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "findByLazadaOrderIdStr failed: " + lazadaOrderIdStr, e);
        }
        return null;
    }

    /**
     * Finds a LazadaOrder by lazada_order_id_str AND channel_id.
     */
    public LazadaOrder findByLazadaOrderIdStrForChannel(String lazadaOrderIdStr, int channelId) {
        String sql =
            "SELECT lo.*, c.channel_name "
          + "FROM lazada_orders lo "
          + "LEFT JOIN channels c ON lo.channel_id = c.channel_id "
          + "WHERE lo.lazada_order_id_str = ? AND lo.channel_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, lazadaOrderIdStr);
            ps.setInt(2, channelId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "findByLazadaOrderIdStrForChannel failed", e);
        }
        return null;
    }

    /**
     * Finds orders by WMS status.
     */
    public List<LazadaOrder> findByWmsStatus(String wmsStatus) {
        String sql =
            "SELECT lo.*, c.channel_name "
          + "FROM lazada_orders lo "
          + "LEFT JOIN channels c ON lo.channel_id = c.channel_id "
          + "WHERE lo.wms_status = ? "
          + "ORDER BY lo.synced_at DESC";
        List<LazadaOrder> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, wmsStatus);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "findByWmsStatus failed: " + wmsStatus, e);
        }
        return list;
    }

    /**
     * Finds orders by channel ID.
     */
    public List<LazadaOrder> findByChannelId(int channelId) {
        String sql =
            "SELECT lo.*, c.channel_name "
          + "FROM lazada_orders lo "
          + "LEFT JOIN channels c ON lo.channel_id = c.channel_id "
          + "WHERE lo.channel_id = ? "
          + "ORDER BY lo.synced_at DESC";
        List<LazadaOrder> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, channelId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "findByChannelId failed: " + channelId, e);
        }
        return list;
    }

    /**
     * Finds orders by Lazada native status.
     */
    public List<LazadaOrder> findByLazadaStatus(String status) {
        String sql =
            "SELECT lo.*, c.channel_name "
          + "FROM lazada_orders lo "
          + "LEFT JOIN channels c ON lo.channel_id = c.channel_id "
          + "WHERE lo.status = ? "
          + "ORDER BY lo.synced_at DESC";
        List<LazadaOrder> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "findByLazadaStatus failed: " + status, e);
        }
        return list;
    }

    /**
     * Returns NEW orders pending sales approval, limited.
     */
    public List<LazadaOrder> findPendingApproval(int limit) {
        String sql =
            "SELECT lo.*, c.channel_name "
          + "FROM lazada_orders lo "
          + "LEFT JOIN channels c ON lo.channel_id = c.channel_id "
          + "WHERE lo.wms_status = 'NEW' "
          + "ORDER BY lo.lazada_created_at ASC "
          + "LIMIT ?";
        List<LazadaOrder> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "findPendingApproval failed", e);
        }
        return list;
    }

    /**
     * Returns APPROVED orders waiting for pack.
     */
    public List<LazadaOrder> findPendingPack(int limit) {
        String sql =
            "SELECT lo.*, c.channel_name "
          + "FROM lazada_orders lo "
          + "LEFT JOIN channels c ON lo.channel_id = c.channel_id "
          + "WHERE lo.wms_status = 'APPROVED' "
          + "ORDER BY lo.assigned_at ASC "
          + "LIMIT ?";
        List<LazadaOrder> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "findPendingPack failed", e);
        }
        return list;
    }

    /**
     * Returns PACKED orders waiting for RTS (Ready To Ship).
     */
    public List<LazadaOrder> findPendingRts(int limit) {
        String sql =
            "SELECT lo.*, c.channel_name "
          + "FROM lazada_orders lo "
          + "LEFT JOIN channels c ON lo.channel_id = c.channel_id "
          + "WHERE lo.wms_status = 'PACKED' "
          + "ORDER BY lo.assigned_at ASC "
          + "LIMIT ?";
        List<LazadaOrder> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "findPendingRts failed", e);
        }
        return list;
    }

    // ══ INSERT / UPDATE ════════════════════════════════════════════════════

    /**
     * Inserts a new LazadaOrder. Returns the generated key or -1 on failure.
     */
    public int insert(LazadaOrder order) {
        String sql =
            "INSERT INTO lazada_orders "
          + "(lazada_order_id_str, lazada_order_number, channel_id, status, wms_status, "
          + " customer_name, customer_phone, shipping_address, shipping_city, "
          + " price, shipping_fee, voucher_seller, voucher_platform, payment_method, buyer_note, "
          + " warehouse_id, assigned_by, assigned_at, "
          + " package_id, tracking_number, shipment_provider, shipment_provider_code, "
          + " lazada_created_at, lazada_updated_at, rts_at, delivered_at, synced_at) "
          + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1,  order.getLazadaOrderIdStr());
            ps.setString(2,  order.getLazadaOrderNumber());
            ps.setInt(3,     order.getChannelId());
            ps.setString(4,  order.getStatus());
            ps.setString(5,  order.getWmsStatus());
            ps.setString(6,  order.getCustomerName());
            ps.setString(7,  order.getCustomerPhone());
            ps.setString(8,  order.getShippingAddress());
            ps.setString(9,  order.getShippingCity());
            ps.setBigDecimal(10, order.getPrice());
            ps.setBigDecimal(11, order.getShippingFee());
            ps.setBigDecimal(12, order.getVoucherSeller());
            ps.setBigDecimal(13, order.getVoucherPlatform());
            ps.setString(14, order.getPaymentMethod());
            ps.setString(15, order.getBuyerNote());
            ps.setInt(16,    order.getWarehouseId());
            ps.setInt(17,    order.getAssignedBy());
            ps.setTimestamp(18, order.getAssignedAt() != null ? Timestamp.valueOf(order.getAssignedAt()) : null);
            ps.setString(19, order.getPackageId());
            ps.setString(20, order.getTrackingNumber());
            ps.setString(21, order.getShipmentProvider());
            ps.setString(22, order.getShipmentProviderCode());
            ps.setTimestamp(23, order.getLazadaCreatedAt() != null ? Timestamp.valueOf(order.getLazadaCreatedAt()) : null);
            ps.setTimestamp(24, order.getLazadaUpdatedAt() != null ? Timestamp.valueOf(order.getLazadaUpdatedAt()) : null);
            ps.setTimestamp(25, order.getRtsAt() != null ? Timestamp.valueOf(order.getRtsAt()) : null);
            ps.setTimestamp(26, order.getDeliveredAt() != null ? Timestamp.valueOf(order.getDeliveredAt()) : null);
            ps.setTimestamp(27, order.getSyncedAt() != null ? Timestamp.valueOf(order.getSyncedAt()) : null);
            int rows = ps.executeUpdate();
            if (rows > 0) {
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) return keys.getInt(1);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "insert failed for " + order.getLazadaOrderIdStr(), e);
        }
        return -1;
    }

    /**
     * Updates the WMS status of an order.
     */
    public boolean updateStatus(String lazadaOrderIdStr, String wmsStatus) {
        String sql = "UPDATE lazada_orders SET wms_status = ? WHERE lazada_order_id_str = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, wmsStatus);
            ps.setString(2, lazadaOrderIdStr);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "updateStatus failed: " + lazadaOrderIdStr, e);
            return false;
        }
    }

    /**
     * Assigns a warehouse to an order (called during approval).
     */
    public boolean updateAssignWarehouse(String lazadaOrderIdStr, int warehouseId, int assignedBy) {
        return updateAssignWarehouseAndProvider(lazadaOrderIdStr, warehouseId, assignedBy, null);
    }

    /**
     * Assigns a warehouse and shipment provider to an order (called during approval).
     */
    public boolean updateAssignWarehouseAndProvider(String lazadaOrderIdStr, int warehouseId,
                                                  int assignedBy, String providerCode) {
        String sql =
            "UPDATE lazada_orders "
          + "SET warehouse_id = ?, assigned_by = ?, assigned_at = NOW()"
          + (providerCode != null && !providerCode.isEmpty() ? ", shipment_provider_code = ?" : "")
          + " WHERE lazada_order_id_str = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, warehouseId);
            ps.setInt(2, assignedBy);
            if (providerCode != null && !providerCode.isEmpty()) {
                ps.setString(3, providerCode);
                ps.setString(4, lazadaOrderIdStr);
            } else {
                ps.setString(3, lazadaOrderIdStr);
            }
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "updateAssignWarehouseAndProvider failed: " + lazadaOrderIdStr, e);
            return false;
        }
    }

    /**
     * Persists package_id, tracking_number, and carrier info from the Pack API.
     */
    public boolean updateTrackingInfo(String lazadaOrderIdStr, String packageId,
                                      String trackingNumber, String provider) {
        String sql =
            "UPDATE lazada_orders "
          + "SET package_id = ?, tracking_number = ?, shipment_provider = ? "
          + "WHERE lazada_order_id_str = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, packageId);
            ps.setString(2, trackingNumber);
            ps.setString(3, provider);
            ps.setString(4, lazadaOrderIdStr);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "updateTrackingInfo failed: " + lazadaOrderIdStr, e);
            return false;
        }
    }

    /**
     * Records the RTS timestamp.
     */
    public boolean updateRtsAt(String lazadaOrderIdStr) {
        String sql = "UPDATE lazada_orders SET rts_at = NOW() WHERE lazada_order_id_str = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, lazadaOrderIdStr);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "updateRtsAt failed: " + lazadaOrderIdStr, e);
            return false;
        }
    }

    /**
     * Records the delivery timestamp when Lazada confirms the order was delivered.
     */
    public boolean updateDeliveredAt(String lazadaOrderIdStr) {
        String sql = "UPDATE lazada_orders SET delivered_at = NOW() WHERE lazada_order_id_str = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, lazadaOrderIdStr);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "updateDeliveredAt failed: " + lazadaOrderIdStr, e);
            return false;
        }
    }

    // ══ UPSERT FROM API ════════════════════════════════════════════════════

    /**
     * Inserts or updates a LazadaOrder from the Lazada API response.
     * Uses lazada_order_id_str as the natural unique key.
     * Returns the existing record if found, or the newly inserted record.
     */
    public LazadaOrder upsertFromApi(LazadaOrder order) {
        // Try UPDATE first
        String updateSql =
            "UPDATE lazada_orders SET "
          + "lazada_order_number = ?, status = ?, wms_status = COALESCE(wms_status, 'NEW'), "
          + "customer_name = ?, customer_phone = ?, shipping_address = ?, shipping_city = ?, "
          + "price = ?, shipping_fee = ?, voucher_seller = ?, voucher_platform = ?, "
          + "payment_method = ?, buyer_note = ?, "
          + "lazada_created_at = ?, lazada_updated_at = ?, "
          + "delivered_at = CASE WHEN ? = 'delivered' THEN NOW() ELSE delivered_at END, "
          + "synced_at = NOW() "
          + "WHERE lazada_order_id_str = ? AND channel_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(updateSql)) {
            ps.setString(1,  order.getLazadaOrderNumber());
            ps.setString(2,  order.getStatus());
            ps.setString(3,  order.getCustomerName());
            ps.setString(4,  order.getCustomerPhone());
            ps.setString(5,  order.getShippingAddress());
            ps.setString(6,  order.getShippingCity());
            ps.setBigDecimal(7,  order.getPrice());
            ps.setBigDecimal(8,  order.getShippingFee());
            ps.setBigDecimal(9,  order.getVoucherSeller());
            ps.setBigDecimal(10, order.getVoucherPlatform());
            ps.setString(11, order.getPaymentMethod());
            ps.setString(12, order.getBuyerNote());
            ps.setTimestamp(13, order.getLazadaCreatedAt() != null ? Timestamp.valueOf(order.getLazadaCreatedAt()) : null);
            ps.setTimestamp(14, order.getLazadaUpdatedAt() != null ? Timestamp.valueOf(order.getLazadaUpdatedAt()) : null);
            ps.setString(15, order.getStatus());
            ps.setString(16, order.getLazadaOrderIdStr());
            ps.setInt(17,    order.getChannelId());
            int updated = ps.executeUpdate();
            if (updated > 0) {
                LOGGER.info("upsertFromApi: updated order " + order.getLazadaOrderIdStr());
                return findByLazadaOrderIdStrForChannel(order.getLazadaOrderIdStr(), order.getChannelId());
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "upsertFromApi update failed: " + order.getLazadaOrderIdStr(), e);
        }

        // Fall back to INSERT
        int newId = insert(order);
        if (newId > 0) {
            LOGGER.info("upsertFromApi: inserted order " + order.getLazadaOrderIdStr());
            order.setLazadaOrderId(newId);
            return order;
        }
        return null;
    }

    // ══ COUNTS ════════════════════════════════════════════════════════════

    public int countByWmsStatus(String wmsStatus) {
        String sql = "SELECT COUNT(*) FROM lazada_orders WHERE wms_status = ?";
        return count(sql, wmsStatus);
    }

    public int countByLazadaStatus(String status) {
        String sql = "SELECT COUNT(*) FROM lazada_orders WHERE status = ?";
        return count(sql, status);
    }

    public int countPendingApproval() {
        return countByWmsStatus("NEW");
    }

    public int countPendingPack() {
        return countByWmsStatus("APPROVED");
    }

    public int countPendingRts() {
        return countByWmsStatus("PACKED");
    }

    private int count(String sql, String param) {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "count failed: " + sql, e);
        }
        return 0;
    }

    // ══ ORDER ITEMS ════════════════════════════════════════════════════════════

    /**
     * Returns all items for a given Lazada order.
     */
    public List<LazadaOrderItem> findItemsByLazadaOrderIdStr(String lazadaOrderIdStr) {
        String sql = "SELECT * FROM lazada_order_items WHERE lazada_order_id_str = ?";
        List<LazadaOrderItem> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, lazadaOrderIdStr);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapItemRow(rs));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "findItemsByLazadaOrderIdStr failed: " + lazadaOrderIdStr, e);
        }
        return list;
    }

    /**
     * Returns all Lazada-originated orders with their line items and WMS stock available.
     * Each map contains: order fields + "items" (List of map with item + stock fields).
     * Used by SalesOrdersServlet to seed the page with full order + inventory data.
     */
    public List<java.util.Map<String, Object>> findAllWithItemsAndStock() {
        List<java.util.Map<String, Object>> result = new ArrayList<>();
        String ordersSql =
            "SELECT lo.lazada_order_id_str, lo.lazada_order_number, lo.channel_id, lo.status AS lazada_status, lo.wms_status, "
            + "  lo.customer_name, lo.customer_phone, lo.shipping_address, lo.shipping_city, "
            + "  lo.price, lo.shipping_fee, lo.voucher_seller, lo.voucher_platform, lo.payment_method, "
            + "  lo.warehouse_id, lo.package_id, lo.tracking_number, lo.shipment_provider, "
            + "  lo.lazada_created_at, lo.lazada_updated_at, lo.synced_at, "
            + "  o.order_id, o.order_code, o.channel AS order_channel, o.status AS order_status, "
            + "  o.total_amount, o.created_at AS order_created_at, o.customer_id, o.channel_order_id, "
            + "  sd.recipient_name, sd.shipping_address AS ship_address, sd.courier_name, "
            + "  c.channel_name "
            + "FROM lazada_orders lo "
            + "LEFT JOIN orders o ON o.order_code = lo.lazada_order_id_str "
            + "LEFT JOIN channels c ON lo.channel_id = c.channel_id "
            + "LEFT JOIN order_shipping_details sd ON o.order_id = sd.order_id "
            + "WHERE o.order_id IS NOT NULL "
            + "ORDER BY lo.lazada_created_at DESC "
            + "LIMIT 200";

        // Items query: each lazada_order_item is matched to WMS product,
        // then aggregated across ALL warehouses that hold that product.
        // We GROUP BY item_id so one row per line item,
        // with sum of stock across warehouses.
        String itemsSql =
            "SELECT loi.item_id, loi.order_item_id, loi.sku, loi.shop_sku, "
            + "  loi.product_name, loi.product_image, loi.quantity, loi.paid_price, "
            + "  loi.item_price, loi.supply_price, loi.status, "
            + "  sm.sku_id AS mapped_sku_id, sm.seller_sku, "
            + "  p.product_id AS wms_product_id, p.sku_code AS wms_sku, p.product_name AS wms_product_name, "
            + "  COALESCE(inv_agg.total_qty_on_hand, 0) AS total_qty_on_hand, "
            + "  COALESCE(inv_agg.total_qty_available, 0) AS total_qty_available, "
            + "  COALESCE(inv_agg.total_holding, 0) AS total_holding, "
            + "  inv_agg.warehouse_stocks AS warehouse_stocks "
            + "FROM lazada_order_items loi "
            + "LEFT JOIN sku_mappings sm ON sm.channel_id = ? AND sm.seller_sku = loi.sku "
            + "LEFT JOIN products p ON p.product_id = sm.sku_id "
            + "LEFT JOIN ("
            + "  SELECT product_id, "
            + "    SUM(total_qty_on_hand) AS total_qty_on_hand, "
            + "    SUM(total_qty_available) AS total_qty_available, "
            + "    SUM(total_holding) AS total_holding, "
            + "    GROUP_CONCAT(warehouse_stocks_entry SEPARATOR ', ') AS warehouse_stocks "
            + "  FROM ("
            + "    SELECT inv2.product_id, inv2.warehouse_id, "
            + "      SUM(COALESCE(inv2.qty_on_hand, 0)) AS total_qty_on_hand, "
            + "      SUM(COALESCE(inv2.qty_available, 0)) AS total_qty_available, "
            + "      SUM(COALESCE(inv2.holding, 0)) AS total_holding, "
            + "      CONCAT(w2.warehouse_name, ':', COALESCE(SUM(inv2.qty_available),0)) AS warehouse_stocks_entry "
            + "    FROM inventory inv2 "
            + "    LEFT JOIN warehouses w2 ON w2.warehouse_id = inv2.warehouse_id "
            + "    WHERE (inv2.stock_type IS NULL OR inv2.stock_type = 'NORMAL') "
            + "    GROUP BY inv2.product_id, inv2.warehouse_id, w2.warehouse_name"
            + "  ) AS per_wh "
            + "  GROUP BY product_id"
            + ") inv_agg ON inv_agg.product_id = p.product_id "
            + "WHERE loi.lazada_order_id_str = ? "
            + "ORDER BY loi.item_id";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement psOrders = conn.prepareStatement(ordersSql);
             PreparedStatement psItems = conn.prepareStatement(itemsSql)) {

            try (ResultSet rsOrders = psOrders.executeQuery()) {
                while (rsOrders.next()) {
                    java.util.Map<String, Object> orderMap = new java.util.LinkedHashMap<>();
                    orderMap.put("lazadaOrderIdStr", rsOrders.getString("lazada_order_id_str"));
                    orderMap.put("lazadaOrderNumber", rsOrders.getString("lazada_order_number"));
                    orderMap.put("lazadaStatus", rsOrders.getString("lazada_status"));
                    orderMap.put("wmsStatus", rsOrders.getString("wms_status"));
                    orderMap.put("customerName", rsOrders.getString("customer_name"));
                    orderMap.put("customerPhone", rsOrders.getString("customer_phone"));
                    orderMap.put("shippingAddress", rsOrders.getString("shipping_address"));
                    orderMap.put("shippingCity", rsOrders.getString("shipping_city"));
                    orderMap.put("price", rsOrders.getBigDecimal("price"));
                    orderMap.put("shippingFee", rsOrders.getBigDecimal("shipping_fee"));
                    orderMap.put("voucherSeller", rsOrders.getBigDecimal("voucher_seller"));
                    orderMap.put("voucherPlatform", rsOrders.getBigDecimal("voucher_platform"));
                    orderMap.put("paymentMethod", rsOrders.getString("payment_method"));
                    orderMap.put("warehouseId", rsOrders.getInt("warehouse_id"));
                    orderMap.put("packageId", rsOrders.getString("package_id"));
                    orderMap.put("trackingNumber", rsOrders.getString("tracking_number"));
                    orderMap.put("shipmentProvider", rsOrders.getString("shipment_provider"));
                    orderMap.put("lazadaCreatedAt", rsOrders.getTimestamp("lazada_created_at"));
                    orderMap.put("lazadaUpdatedAt", rsOrders.getTimestamp("lazada_updated_at"));
                    orderMap.put("syncedAt", rsOrders.getTimestamp("synced_at"));
                    orderMap.put("orderId", rsOrders.getInt("order_id"));
                    orderMap.put("orderCode", rsOrders.getString("order_code"));
                    orderMap.put("orderChannel", rsOrders.getString("order_channel"));
                    orderMap.put("orderStatus", rsOrders.getString("order_status"));
                    orderMap.put("totalAmount", rsOrders.getBigDecimal("total_amount"));
                    orderMap.put("orderCreatedAt", rsOrders.getTimestamp("order_created_at"));
                    orderMap.put("customerId", rsOrders.getString("customer_id"));
                    orderMap.put("channelOrderId", rsOrders.getString("channel_order_id"));
                    orderMap.put("recipientName", rsOrders.getString("recipient_name"));
                    orderMap.put("shipAddress", rsOrders.getString("ship_address"));
                    orderMap.put("courierName", rsOrders.getString("courier_name"));
                    orderMap.put("channelName", rsOrders.getString("channel_name"));

                    // Load items for this order
                    int channelId = rsOrders.getInt("channel_id");
                    String lazadaOrderIdStr = rsOrders.getString("lazada_order_id_str");
                    java.util.List<java.util.Map<String, Object>> items = new java.util.ArrayList<>();
                    psItems.setInt(1, channelId);
                    psItems.setString(2, lazadaOrderIdStr);
                    try (ResultSet rsItems = psItems.executeQuery()) {
                        while (rsItems.next()) {
                            java.util.Map<String, Object> itemMap = new java.util.LinkedHashMap<>();
                            itemMap.put("itemId", rsItems.getInt("item_id"));
                            itemMap.put("orderItemId", rsItems.getString("order_item_id"));
                            itemMap.put("sku", rsItems.getString("sku"));
                            itemMap.put("shopSku", rsItems.getString("shop_sku"));
                            itemMap.put("productName", rsItems.getString("product_name"));
                            itemMap.put("productImage", rsItems.getString("product_image"));
                            itemMap.put("quantity", rsItems.getInt("quantity"));
                            itemMap.put("paidPrice", rsItems.getBigDecimal("paid_price"));
                            itemMap.put("itemPrice", rsItems.getBigDecimal("item_price"));
                            itemMap.put("supplyPrice", rsItems.getBigDecimal("supply_price"));
                            itemMap.put("status", rsItems.getString("status"));
                            itemMap.put("sellerSku", rsItems.getString("seller_sku"));
                            itemMap.put("wmsProductId", rsItems.getInt("wms_product_id"));
                            itemMap.put("wmsSku", rsItems.getString("wms_sku"));
                            itemMap.put("wmsProductName", rsItems.getString("wms_product_name"));
                            itemMap.put("qtyOnHand", rsItems.getBigDecimal("total_qty_on_hand"));
                            itemMap.put("qtyAvailable", rsItems.getBigDecimal("total_qty_available"));
                            itemMap.put("holding", rsItems.getBigDecimal("total_holding"));
                            itemMap.put("warehouseStocks", rsItems.getString("warehouse_stocks"));
                            items.add(itemMap);
                        }
                    }
                    psItems.clearParameters();
                    orderMap.put("items", items);
                    result.add(orderMap);
                }
            }
            LOGGER.info("findAllWithItemsAndStock: found " + result.size() + " orders");
            for (var om : result) {
                LOGGER.info("  order: " + om.get("orderCode") + " items=" + ((List<?>)om.get("items")).size());
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "findAllWithItemsAndStock failed", e);
        }
        return result;
    }

    /**
     * Inserts an order item. Returns generated key or -1.
     */
    public int insertItem(LazadaOrderItem item) {
        String sql =
            "INSERT INTO lazada_order_items "
          + "(lazada_order_id_str, order_item_id, sku, shop_sku, product_name, product_image, "
          + "quantity, paid_price, item_price, supply_price, status, product_id, reserved_qty, fulfilled_qty) "
          + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1,  item.getLazadaOrderIdStr());
            ps.setString(2,  item.getOrderItemId());
            ps.setString(3,  item.getSku());
            ps.setString(4,  item.getShopSku());
            ps.setString(5,  item.getProductName());
            ps.setString(6,  item.getProductImage());
            ps.setInt(7,     item.getQuantity());
            ps.setBigDecimal(8,  item.getPaidPrice());
            ps.setBigDecimal(9,  item.getItemPrice());
            ps.setBigDecimal(10, item.getSupplyPrice());
            ps.setString(11, item.getStatus());
            ps.setInt(12,    item.getProductId());
            ps.setInt(13,    item.getReservedQty());
            ps.setInt(14,    item.getFulfilledQty());
            int rows = ps.executeUpdate();
            if (rows > 0) {
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) return keys.getInt(1);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "insertItem failed for " + item.getLazadaOrderIdStr(), e);
        }
        return -1;
    }

    /**
     * Updates the reserved (soft-allocated) quantity for an order item.
     */
    public boolean updateItemReservedQty(String lazadaOrderIdStr, int reservedQty) {
        String sql = "UPDATE lazada_order_items SET reserved_qty = ? WHERE lazada_order_id_str = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, reservedQty);
            ps.setString(2, lazadaOrderIdStr);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "updateItemReservedQty failed: " + lazadaOrderIdStr, e);
            return false;
        }
    }

    /**
     * Updates the fulfilled (actually shipped) quantity for an order item.
     */
    public boolean updateItemFulfilledQty(String lazadaOrderIdStr, int fulfilledQty) {
        String sql = "UPDATE lazada_order_items SET fulfilled_qty = ? WHERE lazada_order_id_str = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, fulfilledQty);
            ps.setString(2, lazadaOrderIdStr);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "updateItemFulfilledQty failed: " + lazadaOrderIdStr, e);
            return false;
        }
    }

    /**
     * Batch-upserts items for an order. Replaces existing items.
     * Clears old items and re-inserts from fresh API data.
     */
    public void upsertItems(String lazadaOrderIdStr, List<LazadaOrderItem> items) {
        String delSql = "DELETE FROM lazada_order_items WHERE lazada_order_id_str = ?";
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement psDel = conn.prepareStatement(delSql)) {
                psDel.setString(1, lazadaOrderIdStr);
                psDel.executeUpdate();
            }
            for (LazadaOrderItem item : items) {
                item.setLazadaOrderIdStr(lazadaOrderIdStr);
                try (PreparedStatement psIns = conn.prepareStatement(
                        "INSERT INTO lazada_order_items "
                      + "(lazada_order_id_str, order_item_id, sku, shop_sku, product_name, product_image, "
                      + "quantity, paid_price, item_price, supply_price, status, product_id, reserved_qty, fulfilled_qty) "
                      + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                    psIns.setString(1,  item.getLazadaOrderIdStr());
                    psIns.setString(2,  item.getOrderItemId());
                    psIns.setString(3,  item.getSku());
                    psIns.setString(4,  item.getShopSku());
                    psIns.setString(5,  item.getProductName());
                    psIns.setString(6,  item.getProductImage());
                    psIns.setInt(7,     item.getQuantity());
                    psIns.setBigDecimal(8,  item.getPaidPrice());
                    psIns.setBigDecimal(9,  item.getItemPrice());
                    psIns.setBigDecimal(10, item.getSupplyPrice());
                    psIns.setString(11, item.getStatus());
                    psIns.setInt(12,    item.getProductId());
                    psIns.setInt(13,    item.getReservedQty());
                    psIns.setInt(14,    item.getFulfilledQty());
                    psIns.executeUpdate();
                }
            }
            conn.commit();
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "upsertItems failed for " + lazadaOrderIdStr, e);
        }
    }
}
