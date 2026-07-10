package com.wms.service.warehouse;

import com.wms.dao.LedgerDAO;
import com.wms.dao.WarehouseDAO;
import com.wms.service.common.NotificationService;
import com.wms.model.PhysicalInventory;
import com.wms.model.Warehouse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * InventoryCheckService — Service layer for Physical Inventory Check (Kiểm kê kho).
 *
 * All inventory mutations flow through here: Controller → InventoryCheckService → DAO.
 * Never call DAO directly from a Controller.
 */
public class InventoryCheckService {

    private static final Logger log = LoggerFactory.getLogger(InventoryCheckService.class);

    private final WarehouseService warehouseService = new WarehouseService();
    private final WarehouseDAO warehouseDAO = new WarehouseDAO();
    private final LedgerDAO ledgerDAO = new LedgerDAO();
    private final NotificationService notificationService = new NotificationService();

    public List<PhysicalInventory> findByWarehouse(int warehouseId) {
        return warehouseService.findInventoryChecksByWarehouse(warehouseId);
    }

    public PhysicalInventory findById(int checkId) {
        return warehouseService.findInventoryCheckById(checkId);
    }

    public List<com.wms.model.PhysicalInventoryDetail> findDetails(int checkId) {
        return warehouseService.findPhysicalInventoryDetails(checkId);
    }

    /**
     * Applies inventory adjustments and writes ledger for an approved stocktake.
     * Called after results have been persisted via WarehouseService.
     *
     * Flow: upserts delta into inventory + writes ADJUSTMENT ledger entry
     *       + sets physical_inventory.status = APPROVED + sends notification.
     */
    public void approveInventoryAdjustments(PhysicalInventory check, int userId) {
        if (check == null || check.getCheckCode() == null) {
            throw new IllegalArgumentException("Phiếu kiểm kê không hợp lệ.");
        }

        boolean ok = ledgerDAO.approveDocument(check.getCheckCode(), "Phiếu Kiểm Kê", userId);
        if (!ok) {
            throw new RuntimeException("Không thể phê duyệt phiếu kiểm kê " + check.getCheckCode());
        }

        String whName;
        try {
            Warehouse wh = warehouseService.findById(check.getWarehouseId());
            whName = wh != null ? wh.getWarehouseName() : String.valueOf(check.getWarehouseId());
        } catch (Exception e) {
            whName = String.valueOf(check.getWarehouseId());
        }
        // Notification suppressed: manager approval notification was removed from the
        // system when the Manager approval workflow was eliminated. The warehouse
        // staff who submitted the check already know the result — no separate
        // broadcast to a non-existent manager approval path is needed.
    }
}
