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
 * InventoryDAO — Data Access Object for database operations on inventory levels.
 *
 * Implements core inventory business rules (e.g., Soft-Allocation / Giữ chỗ tồn kho).
 */
public class InventoryDAO {

    private static final Logger LOGGER = Logger.getLogger(InventoryDAO.class.getName());


    /**
     * Executes soft-allocation to hold inventory for an order (Rule BR-04).
     * Increases holding and decreases qty_available atomically:
     * <ul>
     *   <li>holding       += quantityToHold</li>
     *   <li>qty_available -= quantityToHold</li>
     * </ul>
     * The check qty_available >= quantityToHold prevents overselling.
     *
     * @param productId      The ID of the product.
     * @param warehouseId    The ID of the warehouse.
     * @param quantityToHold The quantity to reserve.
     * @return true if soft-allocation succeeded (sufficient stock was available), false otherwise.
     */
    public boolean softAllocateInventory(int productId, int warehouseId, int quantityToHold) {
        if (quantityToHold <= 0) {
            throw new IllegalArgumentException("Quantity to hold must be greater than zero.");
        }
        String sql = "UPDATE inventory SET holding = holding + ?, qty_available = qty_available - ? "
                   + "WHERE product_id = ? AND warehouse_id = ? AND qty_available >= ?";
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, quantityToHold);
                ps.setInt(2, quantityToHold);
                ps.setInt(3, productId);
                ps.setInt(4, warehouseId);
                ps.setInt(5, quantityToHold);
                int rows = ps.executeUpdate();
                if (rows > 0) {
                    conn.commit();
                    LOGGER.info("Soft-allocated " + quantityToHold + " units of product ID " + productId
                            + " at warehouse ID " + warehouseId);
                    return true;
                } else {
                    conn.rollback();
                    LOGGER.warning("Soft-allocation failed: insufficient qty_available for product ID "
                            + productId + " at warehouse ID " + warehouseId);
                    return false;
                }
            } catch (SQLException e) {
                try { conn.rollback(); } catch (SQLException ignored) {}
                LOGGER.log(Level.SEVERE, "Database error during soft-allocation", e);
                return false;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "DB connection failed for soft-allocation", e);
            return false;
        }
    }

    /**
     * Returns the currently-available (un-allocated) stock for a given
     * product/warehouse pair. Returns 0 if no inventory row exists.
     *
     * <p>Used by Sales order approval to validate that the chosen warehouse
     * has enough stock for the requested quantities BEFORE performing the
     * soft-allocate call. This prevents the bug where Sales could approve
     * an order against an out-of-stock warehouse.
     *
     * @param productId    Product primary key
     * @param warehouseId  Warehouse primary key
     * @return qty_available (0 if no inventory row exists or on error)
     */
    public int getAvailableStock(int productId, int warehouseId) {
        String sql = "SELECT qty_available FROM inventory "
                   + "WHERE product_id = ? AND warehouse_id = ? "
                   + "  AND (stock_type IS NULL OR stock_type = 'NORMAL')";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            ps.setInt(2, warehouseId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING,
                "getAvailableStock failed productId=" + productId + " warehouseId=" + warehouseId, e);
        }
        return 0;
    }

    /**
     * Returns the physical stock (qty_on_hand) for a given product/warehouse pair.
     * Returns 0 if no inventory row exists.
     */
    public int getPhysicalStock(int productId, int warehouseId) {
        String sql = "SELECT qty_on_hand FROM inventory "
                   + "WHERE product_id = ? AND warehouse_id = ? "
                   + "  AND (stock_type IS NULL OR stock_type = 'NORMAL')";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            ps.setInt(2, warehouseId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING,
                "getPhysicalStock failed productId=" + productId + " warehouseId=" + warehouseId, e);
        }
        return 0;
    }

    /**
     * Returns total qty_available for a product across ALL active warehouses.
     * Used by the Lazada inventory push scheduler to reflect total stock.
     */
    public int getTotalAvailableStock(int productId) {
        String sql = "SELECT COALESCE(SUM(qty_available), 0) FROM inventory i "
                   + "JOIN warehouses w ON i.warehouse_id = w.warehouse_id "
                   + "WHERE i.product_id = ? "
                   + "  AND w.active = 1 "
                   + "  AND (i.stock_type IS NULL OR i.stock_type = 'NORMAL')";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING,
                "getTotalAvailableStock failed productId=" + productId, e);
        }
        return 0;
    }

    /**
     * Returns a map of productId → qty_on_hand (normal stock only) for a specific warehouse.
     * Used by WarehouseMasterSKUServlet so each warehouse manager only sees their own stock.
     */
    public java.util.Map<Integer, BigDecimal> findStockByWarehouse(int warehouseId) {
        java.util.Map<Integer, BigDecimal> map = new java.util.HashMap<>();
        String sql = "SELECT product_id, qty_on_hand FROM inventory "
                   + "WHERE warehouse_id = ? AND (stock_type IS NULL OR stock_type = 'NORMAL')";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, warehouseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    map.put(rs.getInt("product_id"), rs.getBigDecimal("qty_on_hand"));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "findStockByWarehouse failed warehouseId=" + warehouseId, e);
        }
        return map;
    }

    /**
     * Adds quantity to inventory (for inbound receives).
     *
     * <p>Increments qty_on_hand (physical stock arrived) but does NOT touch
     * holding or qty_available — because holding represents goods already
     * soft-reserved for incoming orders, which is a separate allocation concern.
     *
     * <p>Creates exactly ONE inventory_ledger entry per call:
     *   qty_change = +quantity  (on_hand increased)
     *   avail_change = +quantity (available increased — goods are now sellable)
     *
     * @param productId    The ID of the product.
     * @param warehouseId The ID of the warehouse.
     * @param quantity    The quantity to add (must be > 0).
     * @param userId      The ID of the user performing the operation.
     * @return true if the inventory was updated successfully, false otherwise.
     */
    public boolean addInventory(int productId, int warehouseId, BigDecimal quantity, int userId) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Quantity to add must be greater than zero.");
        }

        String sqlUpsert =
            "INSERT INTO inventory (product_id, warehouse_id, qty_on_hand, holding, qty_available) " +
            "VALUES (?, ?, ?, 0, ?) " +
            "ON DUPLICATE KEY UPDATE " +
            "qty_on_hand = qty_on_hand + ?, qty_available = qty_available + ?";

        String sqlGetInvId =
            "SELECT inventory_id FROM inventory WHERE product_id = ? AND warehouse_id = ? LIMIT 1";

        String sqlLedger =
            "INSERT INTO inventory_ledger (inventory_id, product_id, warehouse_id, transaction_type, " +
            "qty_change, avail_change, created_by, note) " +
            "VALUES (?, ?, ?, 'INBOUND', ?, ?, ?, 'Nhập kho Inbound')";

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement psUpdate = conn.prepareStatement(sqlUpsert);
                 PreparedStatement psGet = conn.prepareStatement(sqlGetInvId);
                 PreparedStatement psLedger = conn.prepareStatement(sqlLedger)) {

                // Upsert inventory row: +qty to both on_hand and available
                psUpdate.setInt(1, productId);
                psUpdate.setInt(2, warehouseId);
                psUpdate.setBigDecimal(3, quantity);  // qty_on_hand initial value
                psUpdate.setBigDecimal(4, quantity);  // qty_available initial value
                psUpdate.setBigDecimal(5, quantity);  // qty_on_hand increment
                psUpdate.setBigDecimal(6, quantity);  // qty_available increment
                psUpdate.executeUpdate();

                // Get the inventory_id for the ledger entry
                int inventoryId = -1;
                psGet.setInt(1, productId);
                psGet.setInt(2, warehouseId);
                try (ResultSet rs = psGet.executeQuery()) {
                    if (rs.next()) {
                        inventoryId = rs.getInt("inventory_id");
                    }
                }

                if (inventoryId <= 0) {
                    conn.rollback();
                    LOGGER.severe("addInventory: inventory row not found for productId=" + productId
                            + " warehouseId=" + warehouseId);
                    return false;
                }

                // Insert ledger entry: qty_change = +quantity, avail_change = +quantity
                psLedger.setInt(1, inventoryId);
                psLedger.setInt(2, productId);
                psLedger.setInt(3, warehouseId);
                psLedger.setBigDecimal(4, quantity);  // qty_change = +quantity
                psLedger.setBigDecimal(5, quantity);  // avail_change = +quantity (goods now available to sell)
                psLedger.setInt(6, userId);
                psLedger.executeUpdate();

                conn.commit();
                LOGGER.info("addInventory: added " + quantity + " units of product ID " + productId
                        + " at warehouse ID " + warehouseId);
                return true;

            } catch (SQLException e) {
                try { conn.rollback(); } catch (SQLException ignored) {}
                LOGGER.log(Level.SEVERE, "Database error during addInventory", e);
                return false;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "DB connection failed for addInventory", e);
            return false;
        }
    }

    /**
     * Deducts qty_on_hand and qty_available at source warehouse for a transfer out.
     * Does NOT write a ledger entry — caller (TransferService) handles that.
     * Used by TransferService.createTransfer() to immediately reflect stock movement.
     *
     * @param productId    The product being transferred out
     * @param warehouseId  The source warehouse
     * @param quantity     The quantity to deduct
     * @return true if deducted; false if insufficient stock or row missing
     */
    public boolean deductTransferOut(int productId, int warehouseId, BigDecimal quantity) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) return false;
        String sql = "UPDATE inventory "
                   + "SET qty_on_hand = qty_on_hand - ?, qty_available = qty_available - ? "
                   + "WHERE product_id = ? AND warehouse_id = ? AND stock_type = 'NORMAL' "
                   + "  AND qty_available >= ?";
        try (Connection conn = DBConnection.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setBigDecimal(1, quantity);
                ps.setBigDecimal(2, quantity);
                ps.setInt(3, productId);
                ps.setInt(4, warehouseId);
                ps.setBigDecimal(5, quantity);
                int rows = ps.executeUpdate();
                if (rows == 0) {
                    LOGGER.warning("deductTransferOut: no row updated for productId=" + productId
                            + " warehouseId=" + warehouseId + " qty=" + quantity);
                    return false;
                }
                LOGGER.info("deductTransferOut: deducted " + quantity + " of productId=" + productId
                        + " from warehouseId=" + warehouseId);
                return true;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "deductTransferOut: DB error", e);
            return false;
        }
    }

    /**
     * Adds qty_on_hand and qty_available at destination warehouse for a transfer in.
     * Creates an inventory row if none exists.
     * Does NOT write a ledger entry — caller handles that.
     *
     * @param productId    The product being transferred in
     * @param warehouseId  The destination warehouse
     * @param quantity     The quantity to add
     * @return true if updated/inserted
     */
    public boolean addTransferIn(int productId, int warehouseId, BigDecimal quantity) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) return false;
        String sql = "INSERT INTO inventory (product_id, warehouse_id, qty_on_hand, holding, qty_available, stock_type) "
                   + "VALUES (?, ?, ?, 0, ?, 'NORMAL') "
                   + "ON DUPLICATE KEY UPDATE qty_on_hand = qty_on_hand + ?, qty_available = qty_available + ?";
        try (Connection conn = DBConnection.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, productId);
                ps.setInt(2, warehouseId);
                ps.setBigDecimal(3, quantity);
                ps.setBigDecimal(4, quantity);
                ps.setBigDecimal(5, quantity);
                ps.setBigDecimal(6, quantity);
                ps.executeUpdate();
                LOGGER.info("addTransferIn: added " + quantity + " of productId=" + productId
                        + " to warehouseId=" + warehouseId);
                return true;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "addTransferIn: DB error", e);
            return false;
        }
    }

    /**
     * Returns the total qty_available for a product across ALL warehouses
     * and ALL stock types (NORMAL + DEFECTIVE), since the Lazada push
     * uses the grand-total system inventory.
     *
     * <p>Used by {@link com.wms.service.marketplace.MarketplaceSyncService} to compute
     * Push_Qty = sumAvailable - bufferStock for each SKU after an inbound receipt.</p>
     *
     * @param productId The internal product ID.
     * @return Total available quantity across all warehouses, or 0 if none.
     */
    public BigDecimal sumAvailableByProductId(int productId) {
        String sql = "SELECT COALESCE(SUM(qty_available), 0) AS total_available "
                   + "FROM inventory WHERE product_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBigDecimal("total_available");
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING,
                "InventoryDAO.sumAvailableByProductId: failed productId=" + productId, e);
        }
        return BigDecimal.ZERO;
    }

    /**
     * Load current inventory across all warehouses (joined with product + warehouse).
     * Includes soft-allocation columns (on_hand, holding, qty_available) and
     * inbound quantities from pending/in-progress PO.
     * Returns List<Map<String,Object>> for easy JSP/Jackson consumption.
     *
     * <p>ATP formula: atp = qty_available + inbound_qty
     *    (outbound forecast excluded for SME scope — add when PO forecasting is in place)
     * <p>ATP status:
     *    - shortage : atp <= 0
     *    - running_low : 0 < atp < rop_calculated
     *    - enough : atp >= rop_calculated
     */
    public List<java.util.Map<String, Object>> findAllInventorySummary() {
        List<java.util.Map<String, Object>> result = new ArrayList<>();
        String sql =
            "SELECT inv.inventory_id, inv.product_id, p.sku_code, p.product_name, "
            + "inv.warehouse_id, w.warehouse_name, w.warehouse_code, "
            + "inv.qty_on_hand, inv.holding, inv.qty_available, inv.updated_at, "
            + "p.min_stock, p.max_stock, p.rop_calculated, "
            + "COALESCE(inb.inbound_qty, 0) AS inbound_qty, "
            + "COALESCE(ROUND(p.mac_price), p.base_price, 0) AS mac_price "
            + "FROM inventory inv "
            + "LEFT JOIN products p ON inv.product_id = p.product_id "
            + "LEFT JOIN warehouses w ON inv.warehouse_id = w.warehouse_id "
            + "LEFT JOIN ("
            + "    SELECT ii.product_id, io.warehouse_id, "
            + "           SUM(COALESCE(ii.accepted_qty, ii.received_qty, 0)) AS inbound_qty "
            + "    FROM inbound_orders io "
            + "    JOIN inbound_items ii ON io.inbound_id = ii.inbound_id "
            + "    WHERE io.status IN ('PENDING','IN_PROGRESS') "
            + "    GROUP BY ii.product_id, io.warehouse_id"
            + ") inb ON inv.product_id = inb.product_id AND inv.warehouse_id = inb.warehouse_id "
            + "WHERE (inv.stock_type IS NULL OR inv.stock_type = 'NORMAL') "
            + "ORDER BY p.sku_code, w.warehouse_name "
            + "LIMIT 500";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                java.util.Map<String, Object> row = new java.util.HashMap<>();
                row.put("inventoryId", rs.getInt("inventory_id"));
                row.put("productId", rs.getInt("product_id"));
                row.put("skuCode", rs.getString("sku_code"));
                row.put("productName", rs.getString("product_name"));
                row.put("warehouseId", rs.getInt("warehouse_id"));
                row.put("warehouseCode", rs.getString("warehouse_code"));
                row.put("warehouseName", rs.getString("warehouse_name"));
                row.put("qtyOnHand", rs.getBigDecimal("qty_on_hand"));
                row.put("holding", rs.getBigDecimal("holding"));
                row.put("qtyAvailable", rs.getBigDecimal("qty_available"));
                java.sql.Timestamp updated = rs.getTimestamp("updated_at");
                row.put("updatedAt", updated != null ? updated.toLocalDateTime().toString() : "");
                row.put("inboundQty", rs.getBigDecimal("inbound_qty"));
                row.put("macPrice", rs.getBigDecimal("mac_price"));

                // ATP: available + inbound (SME scope — outbound forecast deferred)
                java.math.BigDecimal available = rs.getBigDecimal("qty_available") != null
                        ? rs.getBigDecimal("qty_available") : java.math.BigDecimal.ZERO;
                java.math.BigDecimal inboundQty = rs.getBigDecimal("inbound_qty") != null
                        ? rs.getBigDecimal("inbound_qty") : java.math.BigDecimal.ZERO;
                java.math.BigDecimal ropCalc = rs.getBigDecimal("rop_calculated") != null
                        ? rs.getBigDecimal("rop_calculated") : java.math.BigDecimal.ZERO;
                java.math.BigDecimal atp = available.add(inboundQty);

                row.put("atp", atp);
                row.put("atpStatus",
                    atp.compareTo(java.math.BigDecimal.ZERO) <= 0 ? "shortage" :
                    atp.compareTo(ropCalc) < 0  ? "running_low" : "enough");

                // Stock level alert: based on on_hand vs min_stock
                java.math.BigDecimal onHand = rs.getBigDecimal("qty_on_hand") != null
                        ? rs.getBigDecimal("qty_on_hand") : java.math.BigDecimal.ZERO;
                java.math.BigDecimal minStock = rs.getBigDecimal("min_stock") != null
                        ? rs.getBigDecimal("min_stock") : java.math.BigDecimal.ZERO;
                String level;
                if (onHand.compareTo(minStock) <= 0) {
                    level = "critical";
                } else if (onHand.compareTo(minStock.multiply(java.math.BigDecimal.valueOf(1.3))) < 0) {
                    level = "warning";
                } else {
                    level = "safe";
                }
                row.put("level", level);
                row.put("minStock", minStock);
                row.put("maxStock", rs.getBigDecimal("max_stock") != null ? rs.getBigDecimal("max_stock") : java.math.BigDecimal.ZERO);

                result.add(row);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "findAllInventorySummary failed", e);
        }
        return result;
    }

    /**
     * Returns inventory summary rows scoped to a single warehouse.
     * Used by Warehouse Inventory (warehouse staff view).
     */
    public List<java.util.Map<String, Object>> findInventorySummaryByWarehouse(int warehouseId) {
        List<java.util.Map<String, Object>> result = new ArrayList<>();
        String sql =
            "SELECT inv.inventory_id, inv.product_id, p.sku_code, p.product_name, "
            + "inv.warehouse_id, w.warehouse_name, w.warehouse_code, "
            + "inv.qty_on_hand, inv.holding, inv.qty_available, inv.updated_at, "
            + "p.min_stock, p.max_stock, p.rop_calculated, inv.stock_type, "
            + "COALESCE(inb.inbound_qty, 0) AS inbound_qty, "
            + "COALESCE(ROUND(p.mac_price), p.base_price, 0) AS mac_price "
            + "FROM inventory inv "
            + "LEFT JOIN products p ON inv.product_id = p.product_id "
            + "LEFT JOIN warehouses w ON inv.warehouse_id = w.warehouse_id "
            + "LEFT JOIN ("
            + "    SELECT ii.product_id, io.warehouse_id, "
            + "           SUM(COALESCE(ii.accepted_qty, ii.received_qty, 0)) AS inbound_qty "
            + "    FROM inbound_orders io "
            + "    JOIN inbound_items ii ON io.inbound_id = ii.inbound_id "
            + "    WHERE io.status IN ('PENDING','IN_PROGRESS') "
            + "    GROUP BY ii.product_id, io.warehouse_id"
            + ") inb ON inv.product_id = inb.product_id AND inv.warehouse_id = inb.warehouse_id "
            + "WHERE inv.warehouse_id = ? AND (inv.stock_type IS NULL OR inv.stock_type != 'DEFECTIVE') "
            + "ORDER BY p.sku_code ";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, warehouseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    java.util.Map<String, Object> row = new java.util.HashMap<>();
                    row.put("inventoryId", rs.getInt("inventory_id"));
                    row.put("productId", rs.getInt("product_id"));
                    row.put("skuCode", rs.getString("sku_code"));
                    row.put("productName", rs.getString("product_name"));
                    row.put("warehouseId", rs.getInt("warehouse_id"));
                    row.put("warehouseCode", rs.getString("warehouse_code"));
                    row.put("warehouseName", rs.getString("warehouse_name"));
                    row.put("qtyOnHand", rs.getBigDecimal("qty_on_hand"));
                    row.put("holding", rs.getBigDecimal("holding"));
                    row.put("qtyAvailable", rs.getBigDecimal("qty_available"));
                    row.put("stockType", rs.getString("stock_type"));
                    java.sql.Timestamp updated = rs.getTimestamp("updated_at");
                    row.put("updatedAt", updated != null ? updated.toLocalDateTime().toString() : "");
                    row.put("inboundQty", rs.getBigDecimal("inbound_qty"));
                    row.put("macPrice", rs.getBigDecimal("mac_price"));

                    java.math.BigDecimal available = rs.getBigDecimal("qty_available") != null
                            ? rs.getBigDecimal("qty_available") : java.math.BigDecimal.ZERO;
                    java.math.BigDecimal inboundQty = rs.getBigDecimal("inbound_qty") != null
                            ? rs.getBigDecimal("inbound_qty") : java.math.BigDecimal.ZERO;
                    java.math.BigDecimal ropCalc = rs.getBigDecimal("rop_calculated") != null
                            ? rs.getBigDecimal("rop_calculated") : java.math.BigDecimal.ZERO;
                    java.math.BigDecimal atp = available.add(inboundQty);

                    row.put("atp", atp);
                    row.put("atpStatus",
                        atp.compareTo(java.math.BigDecimal.ZERO) <= 0 ? "shortage" :
                        atp.compareTo(ropCalc) < 0  ? "running_low" : "enough");

                    java.math.BigDecimal onHand = rs.getBigDecimal("qty_on_hand") != null
                            ? rs.getBigDecimal("qty_on_hand") : java.math.BigDecimal.ZERO;
                    java.math.BigDecimal minStock = rs.getBigDecimal("min_stock") != null
                            ? rs.getBigDecimal("min_stock") : java.math.BigDecimal.ZERO;
                    String level;
                    if (onHand.compareTo(minStock) <= 0) {
                        level = "critical";
                    } else if (onHand.compareTo(minStock.multiply(java.math.BigDecimal.valueOf(1.3))) < 0) {
                        level = "warning";
                    } else {
                        level = "safe";
                    }
                    row.put("level", level);
                    row.put("minStock", minStock);
                    row.put("maxStock", rs.getBigDecimal("max_stock") != null ? rs.getBigDecimal("max_stock") : java.math.BigDecimal.ZERO);

                    result.add(row);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "findInventorySummaryByWarehouse failed whId=" + warehouseId, e);
        }
        return result;
    }

    // ── Release Soft-Allocation (used when an order is cancelled) ──
    //
    // softAllocateInventory() only decrements qty_available. Without a release
    // counterpart, qty_available keeps dropping over time even though the goods
    // are still in the warehouse. The two methods below close the loop:
    // cancel → restore available; SHIPPED → decrement on_hand.
    // ──────────────────────────────────────────────────────────────

    /**
     * Releases a previously soft-allocated quantity (used on cancel).
     * Adds back to qty_available. Guards against negative values.
     *
     * @param productId    Product to release
     * @param warehouseId  Warehouse
     * @param quantity     Quantity to return
     * @return true if release succeeded, false otherwise
     */
    public boolean releaseSoftAllocateInventory(int productId, int warehouseId, BigDecimal quantity) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Quantity to release must be greater than zero.");
        }

        String sql = "UPDATE inventory "
                   + "SET qty_available = qty_available + LEAST(holding, ?), "
                   + "    holding = GREATEST(holding - ?, 0) "
                   + "WHERE product_id = ? AND warehouse_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setBigDecimal(1, quantity);
            ps.setBigDecimal(2, quantity);
            ps.setInt(3, productId);
            ps.setInt(4, warehouseId);

            int rows = ps.executeUpdate();
            boolean ok = rows > 0;

            if (ok) {
                LOGGER.info("releaseSoftAllocateInventory: released " + quantity
                        + " units of productId=" + productId
                        + " at warehouseId=" + warehouseId);
            } else {
                LOGGER.warning("releaseSoftAllocateInventory: no inventory row found for productId="
                        + productId + " warehouseId=" + warehouseId);
            }
            return ok;

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "releaseSoftAllocateInventory: SQL error", e);
            return false;
        }
    }

    /**
     * Decrements on-hand stock when an order is successfully shipped.
     *
     * Two-step atomic update inside one transaction:
     *   Step 1 — Release holding: holding -= qty, qty_available += qty
     *             (frees up the soft-reserved quantity so it no longer blocks new sales)
     *   Step 2 — Deduct physical: qty_on_hand -= qty
     *             (reflects that the goods have left the warehouse)
     *
     * The WHERE clause on Step 2 (qty_on_hand >= qty) guards against over-deduction.
     * If Step 1 fails, Step 2 is never executed.
     *
     * @param productId    Product
     * @param warehouseId  Warehouse
     * @param quantity     Quantity to deduct
     * @return true if deducted, false if stock is insufficient
     */
    public boolean deductShippedInventory(int productId, int warehouseId, BigDecimal quantity) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Quantity to deduct must be greater than zero.");
        }

        String sqlRelease = "UPDATE inventory "
                          + "SET qty_available = qty_available + LEAST(holding, ?), "
                          + "    holding = GREATEST(holding - ?, 0) "
                          + "WHERE product_id = ? AND warehouse_id = ?";
        String sqlDeduct = "UPDATE inventory "
                          + "SET qty_on_hand = qty_on_hand - ?, "
                          + "    qty_available = GREATEST(qty_available - ?, 0) "
                          + "WHERE product_id = ? AND warehouse_id = ? "
                          + "  AND qty_on_hand >= ?";

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Step 1 — release holding
                try (PreparedStatement ps = conn.prepareStatement(sqlRelease)) {
                    ps.setBigDecimal(1, quantity);
                    ps.setBigDecimal(2, quantity);
                    ps.setInt(3, productId);
                    ps.setInt(4, warehouseId);
                    ps.executeUpdate();
                }
                // Step 2 — deduct physical stock
                try (PreparedStatement ps = conn.prepareStatement(sqlDeduct)) {
                    ps.setBigDecimal(1, quantity);
                    ps.setBigDecimal(2, quantity);
                    ps.setInt(3, productId);
                    ps.setInt(4, warehouseId);
                    ps.setBigDecimal(5, quantity);
                    int rows = ps.executeUpdate();
                    if (rows == 0) {
                        // on_hand was insufficient — already partially released, log and report
                        conn.rollback();
                        LOGGER.warning("deductShippedInventory: insufficient qty_on_hand for productId="
                                + productId + " warehouseId=" + warehouseId + " qty=" + quantity);
                        return false;
                    }
                    conn.commit();
                    LOGGER.info("deductShippedInventory: released holding and deducted " + quantity
                            + " units of productId=" + productId
                            + " at warehouseId=" + warehouseId);
                    return true;
                }
            } catch (SQLException e) {
                try { conn.rollback(); } catch (SQLException ignored) {}
                LOGGER.log(Level.SEVERE, "deductShippedInventory: SQL error", e);
                return false;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "deductShippedInventory: DB connection error", e);
            return false;
        }
    }

    // ══ DEFECTIVE / LOCKED STOCK ════════════════════════════════

    public boolean addDefectiveInventory(int productId, int warehouseId, BigDecimal quantity, int userId, String note) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) return false;

        String sqlUpsert =
            "INSERT INTO inventory (product_id, warehouse_id, qty_on_hand, holding, qty_available, stock_type) " +
            "VALUES (?, ?, ?, 0, 0, 'DEFECTIVE') " +
            "ON DUPLICATE KEY UPDATE qty_on_hand = qty_on_hand + ?";

        String sqlLedger =
            "INSERT INTO inventory_ledger (inventory_id, product_id, warehouse_id, transaction_type, ledger_type, " +
            "qty_change, avail_change, created_by, note) " +
            "VALUES (?, ?, ?, 'INBOUND', 'DEFECTIVE', ?, 0, ?, ?)";

        String sqlGetInvId = "SELECT inventory_id FROM inventory WHERE product_id = ? AND warehouse_id = ? AND stock_type = 'DEFECTIVE'";

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement psUpsert = conn.prepareStatement(sqlUpsert);
                 PreparedStatement psGet = conn.prepareStatement(sqlGetInvId);
                 PreparedStatement psLedger = conn.prepareStatement(sqlLedger)) {

                psUpsert.setInt(1, productId);
                psUpsert.setInt(2, warehouseId);
                psUpsert.setBigDecimal(3, quantity);
                psUpsert.setBigDecimal(4, quantity);
                psUpsert.executeUpdate();

                int inventoryId = -1;
                psGet.setInt(1, productId);
                psGet.setInt(2, warehouseId);
                try (ResultSet rs = psGet.executeQuery()) {
                    if (rs.next()) inventoryId = rs.getInt("inventory_id");
                }

                if (inventoryId <= 0) { conn.rollback(); return false; }

                psLedger.setInt(1, inventoryId);
                psLedger.setInt(2, productId);
                psLedger.setInt(3, warehouseId);
                psLedger.setBigDecimal(4, quantity);
                psLedger.setInt(5, userId);
                psLedger.setString(6, note != null ? note : "Hàng lỗi từ RTV");
                psLedger.executeUpdate();

                conn.commit();
                return true;
            } catch (SQLException e) {
                try { conn.rollback(); } catch (SQLException ignored) {}
                LOGGER.log(Level.SEVERE, "addDefectiveInventory error", e);
                return false;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "DB connection error for addDefectiveInventory", e);
            return false;
        }
    }

    public boolean deductDefectiveInventory(int productId, int warehouseId, BigDecimal quantity, int userId, String note) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) return false;

        String sql = "UPDATE inventory " +
                     "SET qty_on_hand = qty_on_hand - ? " +
                     "WHERE product_id = ? AND warehouse_id = ? AND stock_type = 'DEFECTIVE' AND qty_on_hand >= ?";

        String sqlGetInvId = "SELECT inventory_id FROM inventory WHERE product_id = ? AND warehouse_id = ? AND stock_type = 'DEFECTIVE'";

        String sqlLedger =
            "INSERT INTO inventory_ledger (inventory_id, product_id, warehouse_id, transaction_type, ledger_type, " +
            "qty_change, avail_change, created_by, note) " +
            "VALUES (?, ?, ?, 'OUTBOUND', 'DEFECTIVE', ?, 0, ?, ?)";

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement psDed = conn.prepareStatement(sql);
                 PreparedStatement psGet = conn.prepareStatement(sqlGetInvId);
                 PreparedStatement psLedger = conn.prepareStatement(sqlLedger)) {

                psDed.setBigDecimal(1, quantity);
                psDed.setInt(2, productId);
                psDed.setInt(3, warehouseId);
                psDed.setBigDecimal(4, quantity);
                int rows = psDed.executeUpdate();
                if (rows == 0) { conn.rollback(); return false; }

                int inventoryId = -1;
                psGet.setInt(1, productId);
                psGet.setInt(2, warehouseId);
                try (ResultSet rs = psGet.executeQuery()) {
                    if (rs.next()) inventoryId = rs.getInt("inventory_id");
                }

                if (inventoryId > 0) {
                    psLedger.setInt(1, inventoryId);
                    psLedger.setInt(2, productId);
                    psLedger.setInt(3, warehouseId);
                    psLedger.setBigDecimal(4, quantity.negate());
                    psLedger.setInt(5, userId);
                    psLedger.setString(6, note != null ? note : "Xuất trả NCC (RTV)");
                    psLedger.executeUpdate();
                }

                conn.commit();
                return true;
            } catch (SQLException e) {
                try { conn.rollback(); } catch (SQLException ignored) {}
                LOGGER.log(Level.SEVERE, "deductDefectiveInventory error", e);
                return false;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "DB connection error for deductDefectiveInventory", e);
            return false;
        }
    }

    public BigDecimal getDefectiveQty(int productId, int warehouseId) {
        String sql = "SELECT qty_on_hand FROM inventory WHERE product_id = ? AND warehouse_id = ? AND stock_type = 'DEFECTIVE'";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            ps.setInt(2, warehouseId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getBigDecimal("qty_on_hand");
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "getDefectiveQty error", e);
        }
        return BigDecimal.ZERO;
    }

    /**
     * Returns the count of distinct SKUs that have inventory records in a given warehouse.
     */
    public int countDistinctSkuByWarehouse(int warehouseId) {
        String sql = "SELECT COUNT(DISTINCT product_id) FROM inventory WHERE warehouse_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, warehouseId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "countDistinctSkuByWarehouse failed whId=" + warehouseId, e);
        }
        return 0;
    }

    /**
     * Returns the total physical on-hand quantity across all SKUs in a warehouse.
     */
    public double sumPhysicalByWarehouse(int warehouseId) {
        String sql = "SELECT COALESCE(SUM(qty_on_hand), 0) FROM inventory "
                   + "WHERE warehouse_id = ? AND (stock_type IS NULL OR stock_type = 'NORMAL')";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, warehouseId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getDouble(1);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "sumPhysicalByWarehouse failed whId=" + warehouseId, e);
        }
        return 0;
    }

    /**
     * Returns the count of distinct SKUs where qty_available is at or below the safety threshold.
     * Alerts = SKUs that are at risk of stockout.
     */
    public int countLowStockByWarehouse(int warehouseId) {
        String sql =
            "SELECT COUNT(DISTINCT i.product_id) FROM inventory i "
          + "JOIN products p ON i.product_id = p.product_id "
          + "WHERE i.warehouse_id = ? "
          + "  AND (i.stock_type IS NULL OR i.stock_type = 'NORMAL') "
          + "  AND p.safety_stock IS NOT NULL "
          + "  AND p.safety_stock > 0 "
          + "  AND i.qty_available <= p.safety_stock";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, warehouseId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "countLowStockByWarehouse failed whId=" + warehouseId, e);
        }
        return 0;
    }

    /**
     * Atomic inventory deduction with lock for Website orders (Phase 3 — Hybrid Sync).
     * Ensures exactly 1 order per product can deduct stock, preventing double-deduction.
     *
     * Algorithm:
     * 1. Check if deduction_lock = 0 AND qty_available >= qty (atomic check-and-set)
     * 2. If true: SET deduction_lock = 1, UPDATE qty_available -= qty, log to inventory_deduction_log, UNLOCK
     * 3. If false: return false (stock became unavailable or already locked)
     *
     * @param productId  The product being deducted
     * @param orderId    The order ID (for audit log)
     * @param orderRef   The order reference code
     * @param channel    Channel name: WEB|LAZADA|SHOPEE
     * @param qty        Quantity to deduct
     * @param warehouseId Warehouse ID (if null, use primary warehouse)
     * @return true if deducted; false if stock unavailable or lock failed
     */
    public boolean deductWithLock(int productId, int orderId, String orderRef, String channel, int qty, Integer warehouseId) {
        if (qty <= 0) return false;
        if (channel == null || channel.isBlank()) channel = "WEB";
        if (warehouseId == null) warehouseId = 1; // Default to warehouse 1

        String sqlDeduct = "UPDATE inventory SET deduction_lock = 1, qty_available = qty_available - ?, "
                         + "last_deducted_at = NOW() "
                         + "WHERE product_id = ? AND warehouse_id = ? AND deduction_lock = 0 AND qty_available >= ?";

        String sqlLog = "INSERT INTO inventory_deduction_log "
                      + "(product_id, warehouse_id, order_id, order_ref, channel, qty_deducted, "
                      + "qty_before, qty_after, deduction_status, attempted_at) "
                      + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'SUCCESS', NOW())";

        String sqlGetInventory = "SELECT warehouse_id, qty_available FROM inventory "
                               + "WHERE product_id = ? AND warehouse_id = ? AND deduction_lock = 1 "
                               + "LIMIT 1";

        // Get current qty_available for audit log
        int qtyAvailableNow = getAvailableStock(productId, warehouseId);

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement psDeduct = conn.prepareStatement(sqlDeduct)) {
                // Attempt atomic deduction
                psDeduct.setInt(1, qty);
                psDeduct.setInt(2, productId);
                psDeduct.setInt(3, warehouseId);
                psDeduct.setInt(4, qty);

                int rowsUpdated = psDeduct.executeUpdate();
                if (rowsUpdated == 0) {
                    conn.rollback();
                    // Audit log: deduction failed
                    AuditLogDAO auditLog = new AuditLogDAO();
                    auditLog.logDeductionAttempt(orderId, orderRef, productId, channel, qty, qtyAvailableNow, false,
                            "Insufficient stock or lock held by another order");
                    LOGGER.warning("deductWithLock: failed (stock unavailable or locked) for product " + productId
                            + " order " + orderRef);
                    return false;
                }

                // Get updated values for logging
                int qtyBefore = 0;
                String sqlGetBefore = "SELECT qty_available FROM inventory WHERE product_id = ? AND warehouse_id = ? AND deduction_lock = 1";
                try (PreparedStatement psGet = conn.prepareStatement(sqlGetBefore)) {
                    psGet.setInt(1, productId);
                    psGet.setInt(2, warehouseId);
                    try (ResultSet rs = psGet.executeQuery()) {
                        if (rs.next()) {
                            // qty_available AFTER deduction (since we already updated it)
                            qtyBefore = rs.getInt(1) + qty; // Add back qty to get "before" value
                        }
                    }
                }

                // Log deduction
                try (PreparedStatement psLog = conn.prepareStatement(sqlLog)) {
                    psLog.setInt(1, productId);
                    psLog.setInt(2, warehouseId);
                    psLog.setInt(3, orderId);
                    psLog.setString(4, orderRef);
                    psLog.setString(5, channel);
                    psLog.setInt(6, qty);
                    psLog.setInt(7, qtyBefore);
                    psLog.setInt(8, qtyBefore - qty); // qty_available_after
                    psLog.executeUpdate();
                }

                // Release lock
                String sqlUnlock = "UPDATE inventory SET deduction_lock = 0 WHERE product_id = ? AND warehouse_id = ? AND deduction_lock = 1";
                try (PreparedStatement psUnlock = conn.prepareStatement(sqlUnlock)) {
                    psUnlock.setInt(1, productId);
                    psUnlock.setInt(2, warehouseId);
                    psUnlock.executeUpdate();
                }

                conn.commit();
                // Audit log: deduction succeeded
                AuditLogDAO auditLog = new AuditLogDAO();
                auditLog.logDeductionAttempt(orderId, orderRef, productId, channel, qty, qtyAvailableNow, true, null);
                LOGGER.info("deductWithLock: successfully deducted " + qty + " units of product " + productId
                        + " for order " + orderRef + " (channel=" + channel + ")");
                return true;

            } catch (SQLException e) {
                try { conn.rollback(); } catch (SQLException ignored) {}
                LOGGER.log(Level.SEVERE, "Database error during deductWithLock for order " + orderRef, e);
                return false;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "DB connection failed for deductWithLock order " + orderRef, e);
            return false;
        }
    }

    /**
     * Tracks inventory changes for realtime push batch collection.
     * Called after each deduction to log what changed.
     *
     * @param productId Product ID
     * @param qtyBefore Quantity before deduction
     * @param qtyAfter  Quantity after deduction
     */
    public void logDeductionForPush(int productId, int qtyBefore, int qtyAfter) {
        // This method is optional for now; can be used later to track push queue
        // For now, we rely on getTotalAvailableStock() which queries DB directly
        LOGGER.finest("Tracked deduction for push: product=" + productId + " before=" + qtyBefore + " after=" + qtyAfter);
    }

    /**
     * Retrieves total available inventory (sum across all warehouses) for a product.
     * Used by realtime push scheduler to batch inventory updates.
     *
     * @param productId Product ID
     * @return Total qty_available across all active warehouses
     */
    public int getTotalAvailableStockInt(int productId) {
        BigDecimal result = sumAvailableByProductId(productId);
        return result != null ? result.intValue() : 0;
    }

    /**
     * Retrieves list of all products that have changed (deducted) since given timestamp.
     * Used by realtime push scheduler to batch collect changes every 5s.
     *
     * Note: For Phase 1 simplicity, we return all products with current inventory.
     * In Phase 2, can add a changelog table for precise change tracking.
     *
     * @return List of InventoryUpdate objects
     */
    public List<com.wms.model.InventoryUpdate> getChangesSince() {
        List<com.wms.model.InventoryUpdate> updates = new ArrayList<>();

        String sql = "SELECT i.product_id, COALESCE(SUM(i.qty_available), 0) AS total_qty " +
                     "FROM inventory i " +
                     "JOIN warehouses w ON i.warehouse_id = w.warehouse_id " +
                     "WHERE w.active = 1 AND (i.stock_type IS NULL OR i.stock_type = 'NORMAL') " +
                     "GROUP BY i.product_id " +
                     "ORDER BY i.product_id";

        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String productId = String.valueOf(rs.getInt("product_id"));
                int qtyAvailable = rs.getInt("total_qty");

                // qtyBefore is not tracked in current schema, so we use 0 as placeholder
                // In production, this should come from a changelog table
                updates.add(new com.wms.model.InventoryUpdate(productId, qtyAvailable, 0));
            }

            LOGGER.log(Level.INFO, "Collected {0} products for inventory push", updates.size());
            return updates;

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error collecting changes for push: " + e.getMessage(), e);
            return new ArrayList<>();
        }
    }
}
