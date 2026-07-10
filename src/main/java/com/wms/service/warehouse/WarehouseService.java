package com.wms.service.warehouse;

import com.wms.dao.InventoryDAO;
import com.wms.dao.UserDAO;
import com.wms.dao.WarehouseDAO;
import com.wms.model.User;
import com.wms.model.Warehouse;
import com.wms.model.Zone;
import com.wms.model.DashboardMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;

public class WarehouseService {

    private static final Logger log = LoggerFactory.getLogger(WarehouseService.class);

    private final WarehouseDAO warehouseDAO = new WarehouseDAO();
    private final UserDAO userDAO = new UserDAO();
    private final InventoryDAO inventoryDAO = new InventoryDAO();

    public List<Warehouse> findAll() throws SQLException {
        return warehouseDAO.findAll();
    }

    public List<Warehouse> findAllActive() throws SQLException {
        List<Warehouse> all = warehouseDAO.findAll();
        return all.stream()
                .filter(Warehouse::isActive)
                .collect(java.util.stream.Collectors.toList());
    }

    public List<Zone> findAllZones() throws SQLException {
        List<Warehouse> warehouses = warehouseDAO.findAll();
        return warehouses.stream()
                .flatMap(w -> w.getZones() != null ? w.getZones().stream() : java.util.stream.Stream.empty())
                .collect(java.util.stream.Collectors.toList());
    }

    public List<Zone> findZonesByWarehouseId(int warehouseId) throws SQLException {
        Warehouse w = warehouseDAO.findById(warehouseId);
        if (w != null && w.getZones() != null) {
            return w.getZones();
        }
        return java.util.Collections.emptyList();
    }

    public List<User> findByRoles(String... roles) {
        try {
            return userDAO.findByRoles(roles);
        } catch (SQLException e) {
            log.warn("findByRoles failed for roles={}", java.util.Arrays.toString(roles), e);
            return List.of();
        }
    }

    // ── Zone CRUD (Warehouse Information screen) ───────────────

    /** Creates one zone inside the given warehouse. */
    public SaveResult createZone(int warehouseId, String code, String name, String type, String description, Integer capacity) {
        try {
            if (name == null || name.trim().isEmpty()
                    || type == null || type.trim().isEmpty()) {
                return SaveResult.failure("Vui lòng nhập tên khu và loại khu.");
            }
            if (warehouseDAO.isZoneNameExists(warehouseId, name.trim(), null)) {
                return SaveResult.failure("Tên khu '" + name.trim() + "' đã tồn tại trong kho này.");
            }
            String generatedCode = "ZONE-" + System.currentTimeMillis();
            boolean ok = warehouseDAO.insertZone(warehouseId, generatedCode,
                    name.trim(), type.trim().toUpperCase(),
                    description != null ? description.trim() : null, capacity);
            if (!ok) return SaveResult.failure("Không thể tạo phân khu.");
            return SaveResult.success();
        } catch (Exception e) {
            log.error("createZone failed", e);
            return SaveResult.failure("Lỗi khi tạo phân khu: " + e.getMessage());
        }
    }

    /** Updates an existing zone's editable fields. */
    public SaveResult updateZone(int zoneId, int warehouseId, String name, String type, String description, Integer capacity) {
        try {
            boolean isDefault = warehouseDAO.isDefaultZone(zoneId, warehouseId);
            if (isDefault) {
                boolean ok = warehouseDAO.updateDefaultZone(zoneId, warehouseId,
                        description != null ? description.trim() : null, capacity);
                if (!ok) return SaveResult.failure("Không thể cập nhật phân khu.");
                return SaveResult.success();
            }

            if (name == null || name.trim().isEmpty() || type == null || type.trim().isEmpty()) {
                return SaveResult.failure("Vui lòng nhập tên khu và loại khu.");
            }
            if (warehouseDAO.isZoneNameExists(warehouseId, name.trim(), zoneId)) {
                return SaveResult.failure("Tên khu '" + name.trim() + "' đã tồn tại trong kho này.");
            }
            boolean ok = warehouseDAO.updateZone(zoneId, warehouseId,
                    name.trim(), type.trim().toUpperCase(),
                    description != null ? description.trim() : null, capacity);
            if (!ok) return SaveResult.failure("Không thể cập nhật phân khu.");
            return SaveResult.success();
        } catch (Exception e) {
            log.error("updateZone failed", e);
            return SaveResult.failure("Lỗi khi cập nhật phân khu: " + e.getMessage());
        }
    }

    /** Deletes a non-default zone, falls back to deactivation if deletion is blocked. */
    public SaveResult deleteZone(int zoneId, int warehouseId) {
        try {
            if (warehouseDAO.isDefaultZone(zoneId, warehouseId)) {
                return SaveResult.failure("Không thể xóa khu mặc định của hệ thống.");
            }
            boolean ok = warehouseDAO.deleteZone(zoneId, warehouseId);
            if (!ok) return SaveResult.failure("Không thể xóa phân khu.");
            return SaveResult.success();
        } catch (Exception e) {
            log.error("deleteZone failed", e);
            return SaveResult.failure("Lỗi khi xóa phân khu: " + e.getMessage());
        }
    }

    public void createInventoryCheck(String checkCode, int warehouseId, int userId, String note, String itemsJson) {
        try {
            com.wms.dao.WarehouseDAO dao = new com.wms.dao.WarehouseDAO();
            dao.insertInventoryCheck(checkCode, warehouseId, userId, note);
            if (itemsJson != null && !itemsJson.trim().isEmpty()) {
                dao.insertInventoryCheckItems(checkCode, itemsJson);
            }
        } catch (Exception e) {
            throw new RuntimeException("Không thể tạo phiếu kiểm kê: " + e.getMessage(), e);
        }
    }

    public void submitInventoryCheckResults(int checkId, String resultsJson) {
        try {
            com.wms.dao.WarehouseDAO dao = new com.wms.dao.WarehouseDAO();
            dao.updateInventoryCheckResults(checkId, resultsJson);
        } catch (Exception e) {
            throw new RuntimeException("Không thể cập nhật kết quả kiểm kê: " + e.getMessage(), e);
        }
    }

    public List<com.wms.model.PhysicalInventory> findAllInventoryChecks() {
        return warehouseDAO.findAllInventoryChecks();
    }

    public List<com.wms.model.PhysicalInventory> findInventoryChecksByWarehouse(int warehouseId) {
        return warehouseDAO.findInventoryChecksByWarehouse(warehouseId);
    }

    public com.wms.model.PhysicalInventory findInventoryCheckById(int checkId) {
        return warehouseDAO.findInventoryCheckById(checkId);
    }

    public List<com.wms.model.PhysicalInventoryDetail> findPhysicalInventoryDetails(int checkId) {
        return warehouseDAO.findPhysicalInventoryDetails(checkId);
    }

    /**
     * Submits inventory check results for Manager approval.
     * Previously named "adjustInventoryFromCheck", it self-inserted inventory
     * + ledger entries, which led to double-counting when Manager also
     * clicked approve afterwards.
     *
     * Now: this method ONLY updates status to PENDING_APPROVAL. All UPSERT
     * inventory + INSERT ledger is delegated to LedgerDAO.approveDocument()
     * when Manager clicks "Approve" in /business/ledger.
     */
    public void submitInventoryCheckForApproval(int checkId, String adjustmentsJson) {
        try {
            com.wms.dao.WarehouseDAO dao = new com.wms.dao.WarehouseDAO();
            // 1. Ghi actual_qty / delta_qty vào physical_inventory_details
            dao.updateInventoryCheckResults(checkId, adjustmentsJson);
            // 2. Set the check status to PENDING_APPROVAL (waiting for Manager)
            dao.updateInventoryCheckStatus(checkId, "PENDING_APPROVAL");
        } catch (Exception e) {
            throw new RuntimeException("Không thể gửi phiếu kiểm kê chờ duyệt: " + e.getMessage(), e);
        }
    }

    /** Backward-compat alias: gọi tới submitInventoryCheckForApproval. */
    public void adjustInventoryFromCheck(int checkId, String adjustmentsJson, int userId) {
        submitInventoryCheckForApproval(checkId, adjustmentsJson);
    }
    public Warehouse findById(int warehouseId) throws SQLException {
        return warehouseDAO.findById(warehouseId);
    }

    /**
     * Returns live operational KPIs for the warehouse information header.
     */
    public DashboardMetrics getDashboardMetrics(int warehouseId) {
        try {
            int totalSku = inventoryDAO.countDistinctSkuByWarehouse(warehouseId);
            double totalPhysical = inventoryDAO.sumPhysicalByWarehouse(warehouseId);
            int alertCount = inventoryDAO.countLowStockByWarehouse(warehouseId);
            return new DashboardMetrics(totalSku, totalPhysical, alertCount);
        } catch (Exception e) {
            log.error("getDashboardMetrics failed for warehouseId={}", warehouseId, e);
            return new DashboardMetrics(0, 0, 0);
        }
    }

    public SaveResult saveWarehouse(Warehouse warehouse) {
        try {
            if (warehouse == null
                || warehouse.getWarehouseCode() == null || warehouse.getWarehouseCode().trim().isEmpty()
                || warehouse.getWarehouseName() == null || warehouse.getWarehouseName().trim().isEmpty()) {
                return SaveResult.failure("Thiếu thông tin bắt buộc (mã kho, tên kho).");
            }

            warehouse.setWarehouseCode(warehouse.getWarehouseCode().trim().toUpperCase());
            warehouse.setWarehouseName(warehouse.getWarehouseName().trim());
            warehouse.setAddress(warehouse.getAddress() != null ? warehouse.getAddress().trim() : "");
            warehouse.setPhone(warehouse.getPhone() != null ? warehouse.getPhone().trim() : "");

            List<Zone> zones = warehouse.getZones();
            if (zones != null) {
                for (Zone z : zones) {
                    if (z.getZoneCode() == null || z.getZoneCode().trim().isEmpty()) {
                        z.setZoneCode(warehouse.getWarehouseCode()
                            + "-" + z.getZoneType().substring(0, Math.min(4, z.getZoneType().length())).toUpperCase());
                    } else {
                        z.setZoneCode(z.getZoneCode().trim().toUpperCase());
                    }
                    z.setZoneName(z.getZoneName() != null ? z.getZoneName().trim() : "");
                    z.setActive(true);
                }
            }

            boolean success;
            if (warehouse.getWarehouseId() > 0) {
                log.info("[saveWarehouse] Updating warehouseId={} code={} zones={}",
                    warehouse.getWarehouseId(), warehouse.getWarehouseCode(),
                    zones != null ? zones.stream().map(z -> z.getZoneCode()).collect(java.util.stream.Collectors.joining(",")) : "null");
                success = warehouseDAO.update(warehouse, zones);
                if (!success) {
                    return SaveResult.failure("Không thể cập nhật thông tin kho. Vui lòng kiểm tra lại mã zone có bị trùng không.");
                }
            } else {
                success = warehouseDAO.insert(warehouse, zones);
                if (!success) {
                    return SaveResult.failure("Không thể tạo kho mới. Vui lòng kiểm tra trùng mã kho.");
                }
            }
            return SaveResult.success();
        } catch (Exception e) {
            log.error("Error saving warehouse", e);
            return SaveResult.failure("Lỗi định dạng dữ liệu: " + e.getMessage());
        }
    }

    public boolean toggleStatus(int warehouseId, boolean active) throws SQLException {
        return warehouseDAO.toggleStatus(warehouseId, active);
    }

    public static class SaveResult {
        private final boolean success;
        private final String message;

        private SaveResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public static SaveResult success() {
            return new SaveResult(true, null);
        }

        public static SaveResult failure(String message) {
            return new SaveResult(false, message);
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }
}
