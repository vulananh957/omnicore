package com.wms.dao;

import com.fasterxml.jackson.core.type.TypeReference;
import com.wms.model.Warehouse;
import com.wms.model.Zone;
import com.wms.util.DBConnection;
import com.wms.util.JsonUtil;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * WarehouseDAO — Data Access Object for warehouses and their storage zones.
 *
 * <p>Now extends {@link BaseDAO} so the simple SELECT/INSERT/UPDATE
 * methods use the shared boilerplate helpers. The complex transactional
 * methods (insert with zones, update with zone reconciliation, inventory
 * check apply) keep their own try/rollback logic because they juggle
 * multiple statements on a single connection.</p>
 */
public class WarehouseDAO extends BaseDAO {

    private static final Logger LOGGER = Logger.getLogger(WarehouseDAO.class.getName());

    private static final String SELECT_WH =
        "SELECT warehouse_id, warehouse_code, warehouse_name, address, phone, capacity, active, created_at "
      + "FROM warehouses";

    private static final String SELECT_ZONE =
        "SELECT zone_id, warehouse_id, zone_code, zone_name, zone_type, description, active, is_default, capacity "
      + "FROM zones";

    public List<Warehouse> findAll() {
        List<Warehouse> list = new ArrayList<>();
        for (Warehouse w : queryList(LOGGER,
                SELECT_WH + " ORDER BY warehouse_id ASC",
                this::mapWarehouse)) {
            w.setZones(findZonesByWarehouseId(w.getWarehouseId()));
            list.add(w);
        }
        return list;
    }

    public Warehouse findById(int id) {
        Warehouse w = queryOne(LOGGER,
            SELECT_WH + " WHERE warehouse_id = ?",
            this::mapWarehouse, id);
        if (w != null) {
            w.setZones(findZonesByWarehouseId(id));
        }
        return w;
    }

    /**
     * Helper to retrieve zones of a warehouse.
     */
    public List<Zone> findZonesByWarehouseId(int warehouseId) {
        return queryList(LOGGER,
            SELECT_ZONE + " WHERE warehouse_id = ? ORDER BY zone_id ASC",
            this::mapZone, warehouseId);
    }

    /**
     * Insert a new warehouse and its zones within a transaction.
     * Kept inline because it juggles two generated-key statements.
     */
    public boolean insert(Warehouse w, List<Zone> zones) {
        Connection conn = null;
        PreparedStatement psWH = null;
        PreparedStatement psZone = null;

        try {
            conn = DBConnection.getConnection();
            conn.setAutoCommit(false);

            String sqlWH = "INSERT INTO warehouses (warehouse_code, warehouse_name, address, phone, capacity, active) "
                         + "VALUES (?, ?, ?, ?, ?, ?)";
            psWH = conn.prepareStatement(sqlWH, Statement.RETURN_GENERATED_KEYS);
            psWH.setString(1, w.getWarehouseCode());
            psWH.setString(2, w.getWarehouseName());
            psWH.setString(3, w.getAddress());
            psWH.setString(4, w.getPhone());
            psWH.setInt(5, w.getCapacity());
            psWH.setBoolean(6, w.isActive());

            int affectedRows = psWH.executeUpdate();
            if (affectedRows == 0) {
                conn.rollback();
                return false;
            }

            int warehouseId = -1;
            try (ResultSet generatedKeys = psWH.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    warehouseId = generatedKeys.getInt(1);
                }
            }

            if (warehouseId == -1) {
                conn.rollback();
                return false;
            }

            String sqlZone = "INSERT INTO zones (warehouse_id, zone_code, zone_name, zone_type, description, active, is_default) "
                           + "VALUES (?, ?, ?, ?, ?, ?, ?)";
            psZone = conn.prepareStatement(sqlZone);

            for (Zone z : zones) {
                psZone.setInt(1, warehouseId);
                psZone.setString(2, z.getZoneCode());
                psZone.setString(3, z.getZoneName());
                psZone.setString(4, z.getZoneType() != null ? z.getZoneType() : "NORMAL");
                psZone.setString(5, z.getDescription());
                psZone.setBoolean(6, z.isActive());
                psZone.setBoolean(7, z.isDefault());
                psZone.addBatch();
            }

            psZone.executeBatch();
            conn.commit();
            return true;

        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "WarehouseDAO.insert: failed, rolling back", e);
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    LOGGER.log(Level.SEVERE, "Rollback failed", ex);
                }
            }
            return false;
        } finally {
            DBConnection.closeQuietly(psWH, psZone);
            closeConnectionQuietly(conn);
        }
    }

    /**
     * Update warehouse details and reconcile zones (insert/update/delete) in a transaction.
     */
    public boolean update(Warehouse w, List<Zone> newZones) {
        Connection conn = null;
        PreparedStatement psWH = null;

        try {
            conn = DBConnection.getConnection();
            conn.setAutoCommit(false);

            // 1. Update warehouse basic details
            String sqlWH = "UPDATE warehouses SET warehouse_code = ?, warehouse_name = ?, address = ?, phone = ?, capacity = ?, active = ? "
                         + "WHERE warehouse_id = ?";
            psWH = conn.prepareStatement(sqlWH);
            psWH.setString(1, w.getWarehouseCode());
            psWH.setString(2, w.getWarehouseName());
            psWH.setString(3, w.getAddress());
            psWH.setString(4, w.getPhone());
            psWH.setInt(5, w.getCapacity());
            psWH.setBoolean(6, w.isActive());
            psWH.setInt(7, w.getWarehouseId());
            psWH.executeUpdate();

            // 2. Retrieve current zones (uses the existing connection)
            List<Zone> currentZones;
            try (PreparedStatement psZ = conn.prepareStatement(
                    SELECT_ZONE + " WHERE warehouse_id = ? ORDER BY zone_id ASC")) {
                psZ.setInt(1, w.getWarehouseId());
                try (ResultSet rs = psZ.executeQuery()) {
                    currentZones = new ArrayList<>();
                    while (rs.next()) currentZones.add(mapZone(rs));
                }
            }
            Set<String> newZoneCodes = new HashSet<>();
            for (Zone z : newZones) {
                newZoneCodes.add(z.getZoneCode());
            }

            // 3. Delete or deactivate removed zones
            String sqlDeleteZone = "DELETE FROM zones WHERE zone_id = ?";
            String sqlDeactivateZone = "UPDATE zones SET active = 0 WHERE zone_id = ?";
            try (PreparedStatement psDel = conn.prepareStatement(sqlDeleteZone);
                 PreparedStatement psDeact = conn.prepareStatement(sqlDeactivateZone)) {
                for (Zone cz : currentZones) {
                    if (!newZoneCodes.contains(cz.getZoneCode())) {
                        // Attempt physical delete first
                        try {
                            psDel.setInt(1, cz.getZoneId());
                            psDel.executeUpdate();
                        } catch (SQLException ex) {
                            // If references exist, soft-deactivate instead
                            psDeact.setInt(1, cz.getZoneId());
                            psDeact.executeUpdate();
                        }
                    }
                }
            }

            // 4. Update existing zones or insert new ones
            String sqlInsertZone = "INSERT INTO zones (warehouse_id, zone_code, zone_name, zone_type, description, active, is_default) "
                                 + "VALUES (?, ?, ?, ?, ?, ?, ?)";
            String sqlUpdateZone = "UPDATE zones SET zone_name = ?, zone_type = ?, description = ?, active = ?, is_default = ? "
                                 + "WHERE warehouse_id = ? AND zone_code = ?";

            try (PreparedStatement psIns = conn.prepareStatement(sqlInsertZone);
                 PreparedStatement psUpd = conn.prepareStatement(sqlUpdateZone)) {

                for (Zone nz : newZones) {
                    boolean exists = false;
                    for (Zone cz : currentZones) {
                        if (cz.getZoneCode().equals(nz.getZoneCode())) {
                            exists = true;
                            break;
                        }
                    }

                    if (exists) {
                        psUpd.setString(1, nz.getZoneName());
                        psUpd.setString(2, nz.getZoneType() != null ? nz.getZoneType() : "NORMAL");
                        psUpd.setString(3, nz.getDescription());
                        psUpd.setBoolean(4, nz.isActive());
                        psUpd.setBoolean(5, nz.isDefault());
                        psUpd.setInt(6, w.getWarehouseId());
                        psUpd.setString(7, nz.getZoneCode());
                        psUpd.addBatch();
                    } else {
                        psIns.setInt(1, w.getWarehouseId());
                        psIns.setString(2, nz.getZoneCode());
                        psIns.setString(3, nz.getZoneName());
                        psIns.setString(4, nz.getZoneType() != null ? nz.getZoneType() : "NORMAL");
                        psIns.setString(5, nz.getDescription());
                        psIns.setBoolean(6, nz.isActive());
                        psIns.setBoolean(7, nz.isDefault());
                        psIns.addBatch();
                    }
                }
                psUpd.executeBatch();
                psIns.executeBatch();
            }

            conn.commit();
            return true;

        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "WarehouseDAO.update: failed, rolling back", e);
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    LOGGER.log(Level.SEVERE, "Rollback failed", ex);
                }
            }
            return false;
        } finally {
            DBConnection.closeQuietly(psWH);
            closeConnectionQuietly(conn);
        }
    }

    public boolean toggleStatus(int id, boolean active) {
        return update(LOGGER,
            "UPDATE warehouses SET active = ? WHERE warehouse_id = ?",
            active, id) > 0;
    }

    // ── Single-zone CRUD (Warehouse Information screen) ──────────

    public boolean insertZone(int warehouseId, String code, String name, String type, String description, Integer capacity) {
        return update(LOGGER,
            "INSERT INTO zones (warehouse_id, zone_code, zone_name, zone_type, description, capacity, active, is_default) "
          + "VALUES (?, ?, ?, ?, ?, ?, 1, 0)",
            warehouseId, code, name, type, description, capacity) > 0;
    }

    public boolean updateZone(int zoneId, int warehouseId, String name, String type, String description, Integer capacity) {
        return update(LOGGER,
            "UPDATE zones SET zone_name = ?, zone_type = ?, description = ?, capacity = ? "
          + "WHERE zone_id = ? AND warehouse_id = ?",
            name, type, description, capacity, zoneId, warehouseId) > 0;
    }

    public boolean updateDefaultZone(int zoneId, int warehouseId, String description, Integer capacity) {
        return update(LOGGER,
            "UPDATE zones SET description = ?, capacity = ? "
          + "WHERE zone_id = ? AND warehouse_id = ?",
            description, capacity, zoneId, warehouseId) > 0;
    }

    /** Deletes a non-default zone; falls back to deactivation if the delete is blocked. */
    public boolean deleteZone(int zoneId, int warehouseId) {
        int affected = update(LOGGER,
            "DELETE FROM zones WHERE zone_id = ? AND warehouse_id = ? AND is_default = 0",
            zoneId, warehouseId);
        if (affected > 0) return true;
        // delete was blocked — try soft-deactivate
        LOGGER.log(Level.INFO, "WarehouseDAO.deleteZone: delete blocked, deactivating zone " + zoneId);
        return update(LOGGER,
            "UPDATE zones SET active = 0 WHERE zone_id = ? AND warehouse_id = ? AND is_default = 0",
            zoneId, warehouseId) > 0;
    }

    public boolean isDefaultZone(int zoneId, int warehouseId) {
        Boolean isDefault = queryOne(LOGGER,
            "SELECT is_default FROM zones WHERE zone_id = ? AND warehouse_id = ?",
            rs -> rs.getBoolean("is_default"), zoneId, warehouseId);
        return Boolean.TRUE.equals(isDefault);
    }

    public boolean isZoneCodeExists(int warehouseId, String code) {
        Integer found = queryOne(LOGGER,
            "SELECT 1 FROM zones WHERE warehouse_id = ? AND zone_code = ?",
            rs -> 1, warehouseId, code);
        return found != null;
    }

    public boolean isZoneNameExists(int warehouseId, String name, Integer excludeZoneId) {
        String sql = excludeZoneId != null
            ? "SELECT 1 FROM zones WHERE warehouse_id = ? AND zone_name = ? AND zone_id != ?"
            : "SELECT 1 FROM zones WHERE warehouse_id = ? AND zone_name = ?";
        Integer found = excludeZoneId != null
            ? queryOne(LOGGER, sql, rs -> 1, warehouseId, name.trim(), excludeZoneId)
            : queryOne(LOGGER, sql, rs -> 1, warehouseId, name.trim());
        return found != null;
    }

    private void closeConnectionQuietly(Connection conn) {
        if (conn != null) {
            try {
                conn.setAutoCommit(true);
                conn.close();
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Failed to close connection", e);
            }
        }
    }

    // ── Row mappers ──────────────────────────────────────────────────────

    private Warehouse mapWarehouse(ResultSet rs) throws SQLException {
        Warehouse w = new Warehouse();
        w.setWarehouseId(rs.getInt("warehouse_id"));
        w.setWarehouseCode(rs.getString("warehouse_code"));
        w.setWarehouseName(rs.getString("warehouse_name"));
        w.setAddress(rs.getString("address"));
        w.setPhone(rs.getString("phone"));
        w.setCapacity(rs.getInt("capacity"));
        w.setActive(rs.getBoolean("active"));
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) {
            w.setCreatedAt(ts.toLocalDateTime());
        }
        return w;
    }

    private Zone mapZone(ResultSet rs) throws SQLException {
        Zone z = new Zone();
        z.setZoneId(rs.getInt("zone_id"));
        z.setWarehouseId(rs.getInt("warehouse_id"));
        z.setZoneCode(rs.getString("zone_code"));
        z.setZoneName(rs.getString("zone_name"));
        z.setZoneType(rs.getString("zone_type"));
        z.setDescription(rs.getString("description"));
        z.setCapacity(rs.getInt("capacity"));
        z.setActive(rs.getBoolean("active"));
        z.setDefault(rs.getBoolean("is_default"));
        return z;
    }

    // ── Physical inventory check methods (kept inline — transactional) ──

    public void insertInventoryCheck(String checkCode, int warehouseId, int userId, String note) {
        try {
            update(LOGGER,
                "INSERT INTO physical_inventories (check_code, warehouse_id, created_by, status) VALUES (?, ?, ?, 'DRAFT')",
                checkCode, warehouseId, userId);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "insertInventoryCheck failed", e);
            throw new RuntimeException("insertInventoryCheck failed", e);
        }
    }

    public void insertInventoryCheckItems(String checkCode, String itemsJson) {
        if (itemsJson == null || itemsJson.trim().isEmpty()) return;
        Connection conn = null;
        PreparedStatement psSel = null;
        PreparedStatement psIns = null;
        try {
            conn = DBConnection.getConnection();
            conn.setAutoCommit(false);

            Integer checkId = queryOne(LOGGER,
                "SELECT inventory_check_id FROM physical_inventories WHERE check_code = ?",
                rs -> rs.getInt("inventory_check_id"), checkCode);
            if (checkId == null) {
                conn.rollback();
                return;
            }

            List<?> items = JsonUtil.getMapper().readValue(itemsJson, new TypeReference<List<?>>() {});
            String sqlIns = "INSERT INTO physical_inventory_details (inventory_check_id, product_id, system_qty) VALUES (?, ?, ?)";
            psIns = conn.prepareStatement(sqlIns);
            for (Object item : items) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> m = (java.util.Map<String, Object>) item;
                int productId = ((Number) m.get("productId")).intValue();
                double systemQty = m.get("systemQty") != null ? ((Number) m.get("systemQty")).doubleValue() : 0;
                psIns.setInt(1, checkId);
                psIns.setInt(2, productId);
                psIns.setDouble(3, systemQty);
                psIns.addBatch();
            }
            psIns.executeBatch();
            conn.commit();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "insertInventoryCheckItems failed", e);
            if (conn != null) try { conn.rollback(); } catch (SQLException ex) {}
            throw new RuntimeException("insertInventoryCheckItems failed", e);
        } finally {
            DBConnection.closeQuietly(psSel, psIns);
            closeConnectionQuietly(conn);
        }
    }

    public List<com.wms.model.PhysicalInventory> findAllInventoryChecks() {
        String sql = "SELECT pi.inventory_check_id AS checkId, "
                   + "       pi.check_code AS checkCode, "
                   + "       pi.warehouse_id AS warehouseId, "
                   + "       w.warehouse_name AS warehouseName, "
                   + "       pi.status AS status, "
                   + "       pi.note AS note, "
                   + "       u.full_name AS creatorName, "
                   + "       pi.created_at AS createdAt, "
                   + "       (SELECT COUNT(*) FROM physical_inventory_details pid WHERE pid.inventory_check_id = pi.inventory_check_id) AS totalItems, "
                   + "       (SELECT COUNT(*) FROM physical_inventory_details pid WHERE pid.inventory_check_id = pi.inventory_check_id AND pid.actual_qty IS NOT NULL) AS countedItems, "
                   + "       COALESCE((SELECT SUM(COALESCE(pid.delta_qty, pid.actual_qty - pid.system_qty, 0)) FROM physical_inventory_details pid WHERE pid.inventory_check_id = pi.inventory_check_id AND pid.actual_qty IS NOT NULL), 0) AS totalDelta "
                   + "FROM physical_inventories pi "
                   + "LEFT JOIN warehouses w ON pi.warehouse_id = w.warehouse_id "
                   + "LEFT JOIN users u ON pi.created_by = u.user_id "
                   + "ORDER BY pi.created_at DESC";
        
        return queryList(LOGGER, sql, rs -> {
            com.wms.model.PhysicalInventory pi = new com.wms.model.PhysicalInventory();
            pi.setCheckId(rs.getInt("checkId"));
            pi.setCheckCode(rs.getString("checkCode"));
            pi.setWarehouseId(rs.getInt("warehouseId"));
            pi.setWarehouseName(rs.getString("warehouseName"));
            pi.setStatus(rs.getString("status"));
            pi.setNote(rs.getString("note"));
            pi.setCreatorName(rs.getString("creatorName"));
            Timestamp ts = rs.getTimestamp("createdAt");
            if (ts != null) {
                pi.setCreatedAt(ts.toLocalDateTime());
            }
            pi.setTotalItems(rs.getInt("totalItems"));
            pi.setCountedItems(rs.getInt("countedItems"));
            pi.setTotalDelta(rs.getDouble("totalDelta"));
            return pi;
        });
    }

    public List<com.wms.model.PhysicalInventory> findInventoryChecksByWarehouse(int warehouseId) {
        String sql = "SELECT pi.inventory_check_id AS checkId, "
                   + "       pi.check_code AS checkCode, "
                   + "       pi.warehouse_id AS warehouseId, "
                   + "       w.warehouse_name AS warehouseName, "
                   + "       pi.status AS status, "
                   + "       pi.note AS note, "
                   + "       u.full_name AS creatorName, "
                   + "       pi.created_at AS createdAt, "
                   + "       (SELECT COUNT(*) FROM physical_inventory_details pid WHERE pid.inventory_check_id = pi.inventory_check_id) AS totalItems, "
                   + "       (SELECT COUNT(*) FROM physical_inventory_details pid WHERE pid.inventory_check_id = pi.inventory_check_id AND pid.actual_qty IS NOT NULL) AS countedItems, "
                   + "       COALESCE((SELECT SUM(COALESCE(pid.delta_qty, pid.actual_qty - pid.system_qty, 0)) FROM physical_inventory_details pid WHERE pid.inventory_check_id = pi.inventory_check_id AND pid.actual_qty IS NOT NULL), 0) AS totalDelta "
                   + "FROM physical_inventories pi "
                   + "LEFT JOIN warehouses w ON pi.warehouse_id = w.warehouse_id "
                   + "LEFT JOIN users u ON pi.created_by = u.user_id "
                   + "WHERE pi.warehouse_id = ? "
                   + "ORDER BY pi.created_at DESC";
        
        return queryList(LOGGER, sql, rs -> {
            com.wms.model.PhysicalInventory pi = new com.wms.model.PhysicalInventory();
            pi.setCheckId(rs.getInt("checkId"));
            pi.setCheckCode(rs.getString("checkCode"));
            pi.setWarehouseId(rs.getInt("warehouseId"));
            pi.setWarehouseName(rs.getString("warehouseName"));
            pi.setStatus(rs.getString("status"));
            pi.setNote(rs.getString("note"));
            pi.setCreatorName(rs.getString("creatorName"));
            Timestamp ts = rs.getTimestamp("createdAt");
            if (ts != null) {
                pi.setCreatedAt(ts.toLocalDateTime());
            }
            pi.setTotalItems(rs.getInt("totalItems"));
            pi.setCountedItems(rs.getInt("countedItems"));
            pi.setTotalDelta(rs.getDouble("totalDelta"));
            return pi;
        }, warehouseId);
    }

    public com.wms.model.PhysicalInventory findInventoryCheckById(int checkId) {
        String sql = "SELECT pi.inventory_check_id AS checkId, "
                   + "       pi.check_code AS checkCode, "
                   + "       pi.warehouse_id AS warehouseId, "
                   + "       w.warehouse_name AS warehouseName, "
                   + "       pi.status AS status, "
                   + "       pi.note AS note, "
                   + "       u.full_name AS creatorName, "
                   + "       pi.created_at AS createdAt, "
                   + "       (SELECT COUNT(*) FROM physical_inventory_details pid WHERE pid.inventory_check_id = pi.inventory_check_id) AS totalItems, "
                   + "       (SELECT COUNT(*) FROM physical_inventory_details pid WHERE pid.inventory_check_id = pi.inventory_check_id AND pid.actual_qty IS NOT NULL) AS countedItems, "
                   + "       COALESCE((SELECT SUM(COALESCE(pid.delta_qty, pid.actual_qty - pid.system_qty, 0)) FROM physical_inventory_details pid WHERE pid.inventory_check_id = pi.inventory_check_id AND pid.actual_qty IS NOT NULL), 0) AS totalDelta "
                   + "FROM physical_inventories pi "
                   + "LEFT JOIN warehouses w ON pi.warehouse_id = w.warehouse_id "
                   + "LEFT JOIN users u ON pi.created_by = u.user_id "
                   + "WHERE pi.inventory_check_id = ?";
        
        return queryOne(LOGGER, sql, rs -> {
            com.wms.model.PhysicalInventory pi = new com.wms.model.PhysicalInventory();
            pi.setCheckId(rs.getInt("checkId"));
            pi.setCheckCode(rs.getString("checkCode"));
            pi.setWarehouseId(rs.getInt("warehouseId"));
            pi.setWarehouseName(rs.getString("warehouseName"));
            pi.setStatus(rs.getString("status"));
            pi.setNote(rs.getString("note"));
            pi.setCreatorName(rs.getString("creatorName"));
            Timestamp ts = rs.getTimestamp("createdAt");
            if (ts != null) {
                pi.setCreatedAt(ts.toLocalDateTime());
            }
            pi.setTotalItems(rs.getInt("totalItems"));
            pi.setCountedItems(rs.getInt("countedItems"));
            pi.setTotalDelta(rs.getDouble("totalDelta"));
            return pi;
        }, checkId);
    }

    public List<com.wms.model.PhysicalInventoryDetail> findPhysicalInventoryDetails(int checkId) {
        String sql = "SELECT pid.check_detail_id AS checkDetailId, "
                   + "       p.sku_code AS skuCode, "
                   + "       p.product_name AS productName, "
                   + "       pid.system_qty AS systemQty, "
                   + "       pid.actual_qty AS actualQty, "
                   + "       pid.delta_qty AS deltaQty "
                   + "FROM physical_inventory_details pid "
                   + "LEFT JOIN products p ON pid.product_id = p.product_id "
                   + "WHERE pid.inventory_check_id = ? "
                   + "ORDER BY p.sku_code ASC";
        
        return queryList(LOGGER, sql, rs -> {
            com.wms.model.PhysicalInventoryDetail pid = new com.wms.model.PhysicalInventoryDetail();
            pid.setCheckDetailId(rs.getInt("checkDetailId"));
            pid.setSkuCode(rs.getString("skuCode"));
            pid.setProductName(rs.getString("productName"));
            pid.setSystemQty(rs.getDouble("systemQty"));
            double actual = rs.getDouble("actualQty");
            if (rs.wasNull()) {
                pid.setActualQty(null);
            } else {
                pid.setActualQty(actual);
            }
            double delta = rs.getDouble("deltaQty");
            if (rs.wasNull()) {
                pid.setDeltaQty(null);
            } else {
                pid.setDeltaQty(delta);
            }
            return pid;
        }, checkId);
    }

    public void updateInventoryCheckResults(int checkId, String resultsJson) {
        if (resultsJson == null || resultsJson.trim().isEmpty()) return;
        Connection conn = null;
        try {
            conn = DBConnection.getConnection();
            conn.setAutoCommit(false);

            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE physical_inventories SET status = 'APPROVED' WHERE inventory_check_id = ?")) {
                ps.setInt(1, checkId);
                ps.executeUpdate();
            }

            List<?> items = JsonUtil.getMapper().readValue(resultsJson, new TypeReference<List<?>>() {});

            try (PreparedStatement psUpd = conn.prepareStatement(
                    "UPDATE physical_inventory_details SET actual_qty = ?, delta_qty = ? - system_qty, "
                  + "counted_by = ?, counted_at = CURRENT_TIMESTAMP WHERE check_detail_id = ?")) {
                for (Object item : items) {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> m = (java.util.Map<String, Object>) item;
                    int detailId = ((Number) m.get("checkDetailId")).intValue();
                    double actualQty = m.get("actualQty") != null ? ((Number) m.get("actualQty")).doubleValue() : 0;
                    int countedBy = m.get("countedBy") != null ? ((Number) m.get("countedBy")).intValue() : 1;
                    psUpd.setDouble(1, actualQty);
                    psUpd.setDouble(2, actualQty);
                    psUpd.setInt(3, countedBy);
                    psUpd.setInt(4, detailId);
                    psUpd.addBatch();
                }
                psUpd.executeBatch();
            }

            conn.commit();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "updateInventoryCheckResults failed", e);
            if (conn != null) try { conn.rollback(); } catch (SQLException ex) {}
            throw new RuntimeException("updateInventoryCheckResults failed", e);
        } finally {
            closeConnectionQuietly(conn);
        }
    }

    public void applyInventoryAdjustments(int checkId, String adjustmentsJson, int userId) {
        if (adjustmentsJson == null || adjustmentsJson.trim().isEmpty()) return;
        Connection conn = null;
        try {
            conn = DBConnection.getConnection();
            conn.setAutoCommit(false);

            Integer warehouseId = queryOne(LOGGER,
                "SELECT warehouse_id FROM physical_inventories WHERE inventory_check_id = ?",
                rs -> rs.getInt("warehouse_id"), checkId);
            if (warehouseId == null) {
                throw new RuntimeException("Inventory check not found");
            }

            List<?> items = JsonUtil.getMapper().readValue(adjustmentsJson, new TypeReference<List<?>>() {});

            for (Object item : items) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> m = (java.util.Map<String, Object>) item;
                
                Number checkDetailIdNum = (Number) m.get("checkDetailId");
                Number deltaQtyNum = (Number) m.get("deltaQty");
                
                if (checkDetailIdNum == null) continue;
                int checkDetailId = checkDetailIdNum.intValue();
                double delta = deltaQtyNum != null ? deltaQtyNum.doubleValue() : 0;
                if (delta == 0) continue;

                // Lookup product_id from physical_inventory_details
                Integer productId = queryOne(LOGGER,
                    "SELECT product_id FROM physical_inventory_details WHERE check_detail_id = ?",
                    rs -> rs.getInt("product_id"), checkDetailId);
                if (productId == null) continue;

                // Upsert inventory
                try (PreparedStatement psUpd = conn.prepareStatement(
                        "INSERT INTO inventory (product_id, warehouse_id, qty_on_hand, holding, qty_available) "
                      + "VALUES (?, ?, ?, 0, ?) ON DUPLICATE KEY UPDATE qty_on_hand = qty_on_hand + ?, qty_available = qty_available + ?")) {
                    psUpd.setInt(1, productId);
                    psUpd.setInt(2, warehouseId);
                    psUpd.setDouble(3, delta);
                    psUpd.setDouble(4, delta);
                    psUpd.setDouble(5, delta);
                    psUpd.setDouble(6, delta);
                    psUpd.executeUpdate();
                }

                // Get inventory_id
                Integer invId = queryOne(LOGGER,
                    "SELECT inventory_id FROM inventory WHERE product_id = ? AND warehouse_id = ?",
                    rs -> rs.getInt("inventory_id"), productId, warehouseId);
                if (invId == null) continue;

                // Insert ledger entry
                try (PreparedStatement psLedger = conn.prepareStatement(
                        "INSERT INTO inventory_ledger (inventory_id, product_id, warehouse_id, transaction_type, "
                      + "ref_document_id, qty_change, avail_change, created_by, note) VALUES (?, ?, ?, 'ADJUSTMENT', ?, ?, ?, ?, 'Kiểm kê cân đối tồn kho')")) {
                    psLedger.setInt(1, invId);
                    psLedger.setInt(2, productId);
                    psLedger.setInt(3, warehouseId);
                    psLedger.setInt(4, checkId);
                    psLedger.setDouble(5, delta);
                    psLedger.setDouble(6, delta);
                    psLedger.setInt(7, userId);
                    psLedger.executeUpdate();
                }
            }

            // Update status of check to APPROVED
            try (PreparedStatement psStatus = conn.prepareStatement(
                    "UPDATE physical_inventories SET status = 'APPROVED' WHERE inventory_check_id = ?")) {
                psStatus.setInt(1, checkId);
                psStatus.executeUpdate();
            }

            conn.commit();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "applyInventoryAdjustments failed", e);
            if (conn != null) try { conn.rollback(); } catch (SQLException ex) {}
            throw new RuntimeException("applyInventoryAdjustments failed", e);
        } finally {
            closeConnectionQuietly(conn);
        }
    }

    /**
     * Updates the status of an inventory check.
     * Used by Warehouse staff (submit) and Manager (approve/reject).
     * Does NOT touch inventory / inventory_ledger. LedgerDAO.approveDocument()
     * is the single place that does UPSERT + INSERT ledger when Manager approves.
     */
    public void updateInventoryCheckStatus(int checkId, String newStatus) {
        String sql = "UPDATE physical_inventories SET status = ? WHERE inventory_check_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newStatus);
            ps.setInt(2, checkId);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "updateInventoryCheckStatus failed for checkId=" + checkId, e);
        }
    }
}
