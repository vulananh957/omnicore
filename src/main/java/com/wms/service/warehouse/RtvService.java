package com.wms.service.warehouse;

import com.wms.dao.InboundDAO;
import com.wms.dao.InventoryDAO;
import com.wms.dao.RtvDAO;
import com.wms.model.InboundOrder;
import com.wms.model.RtvItem;
import com.wms.model.RtvOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * RtvService — Business logic for Return-To-Vendor (RTV / Trả hàng nhà cung cấp).
 *
 * Lifecycle: PENDING → APPROVED → COMPLETED | CANCELLED
 */
public class RtvService {

    private static final Logger log = LoggerFactory.getLogger(RtvService.class);

    private final RtvDAO rtvDAO = new RtvDAO();
    private final InboundDAO inboundDAO = new InboundDAO();
    private final InventoryDAO inventoryDAO = new InventoryDAO();

    // -----------------------------------------------------------------------
    // Queries
    // -----------------------------------------------------------------------

    public List<RtvOrder> findByWarehouse(int warehouseId) {
        return rtvDAO.findByWarehouse(warehouseId);
    }

    public RtvOrder findById(int rtvId) {
        return rtvDAO.findById(rtvId);
    }

    // -----------------------------------------------------------------------
    // Commands
    // -----------------------------------------------------------------------

    /**
     * Create a new RTV order tied to an inbound order.
     */
    public RtvResult createRtv(int inboundId, List<RtvItemRequest> itemRequests,
                                String reason, String note, int createdBy,
                                String poCode, String supplierCode, String contactPerson, String proposal) {
        InboundOrder inbound = inboundDAO.findById(inboundId);
        if (inbound == null) {
            log.warn("createRtv failed: inbound not found id={}", inboundId);
            return RtvResult.failure("Phiếu nhập #" + inboundId + " không tồn tại.");
        }

        if (itemRequests == null || itemRequests.isEmpty()) {
            return RtvResult.failure("Vui lòng chọn ít nhất một sản phẩm để trả hàng.");
        }

        RtvOrder rtv = new RtvOrder();
        rtv.setInboundId(inboundId);
        rtv.setWarehouseId(inbound.getWarehouseId());
        rtv.setSupplier(inbound.getSupplierName());
        rtv.setStatus("PENDING");
        rtv.setReason(reason != null ? reason.trim() : null);
        rtv.setNote(note != null ? note.trim() : null);
        rtv.setPoCode(poCode != null ? poCode.trim() : null);
        rtv.setSupplierCode(supplierCode != null ? supplierCode.trim() : null);
        rtv.setContactPerson(contactPerson != null ? contactPerson.trim() : null);
        rtv.setProposal(proposal != null ? proposal.trim() : null);

        List<RtvItem> items = new ArrayList<>();
        for (RtvItemRequest req : itemRequests) {
            if (req.getProductId() <= 0) continue;
            BigDecimal qty = req.getQtyReturn() != null ? req.getQtyReturn() : BigDecimal.ZERO;
            if (qty.compareTo(BigDecimal.ZERO) <= 0) continue;

            RtvItem item = new RtvItem();
            item.setProductId(req.getProductId());
            item.setQtyReturn(qty);
            item.setUnitCost(req.getUnitCost() != null ? req.getUnitCost() : BigDecimal.ZERO);
            items.add(item);
        }
        rtv.setItems(items);

        int newId = rtvDAO.insert(rtv);
        if (newId <= 0) {
            log.error("createRtv failed: DAO insert returned id={}", newId);
            return RtvResult.failure("Không thể tạo phiếu RTV. Vui lòng thử lại.");
        }

        log.info("RTV created: rtvId={} inboundId={} warehouseId={} by={}",
                newId, inboundId, inbound.getWarehouseId(), createdBy);
        return RtvResult.success("Tạo phiếu trả hàng thành công! Mã: " + rtv.getCode(), newId);
    }

    /**
     * Approve a PENDING RTV → moves to APPROVED.
     */
    public RtvResult approveRtv(int rtvId, int userId) {
        RtvOrder rtv = rtvDAO.findById(rtvId);
        if (rtv == null) {
            return RtvResult.failure("Phiếu RTV #" + rtvId + " không tồn tại.");
        }
        if (!"PENDING".equals(rtv.getStatus())) {
            return RtvResult.failure("Chỉ phiếu RTV đang chờ duyệt mới có thể được duyệt (trạng thái hiện tại: "
                    + rtv.getStatus() + ").");
        }
        boolean ok = rtvDAO.updateStatus(rtvId, "APPROVED");
        if (!ok) {
            return RtvResult.failure("Không thể duyệt phiếu RTV. Vui lòng thử lại.");
        }
        log.info("RTV approved: rtvId={} by={}", rtvId, userId);
        return RtvResult.success("Duyệt phiếu trả hàng " + rtv.getCode() + " thành công!", rtvId);
    }

    /**
     * Complete an APPROVED RTV → moves to COMPLETED.
     */
    public RtvResult completeRtv(int rtvId, int userId) {
        RtvOrder rtv = rtvDAO.findById(rtvId);
        if (rtv == null) {
            return RtvResult.failure("Phiếu RTV #" + rtvId + " không tồn tại.");
        }
        if (!"APPROVED".equals(rtv.getStatus())) {
            return RtvResult.failure("Chỉ phiếu RTV đã duyệt mới có thể hoàn thành (trạng thái hiện tại: "
                    + rtv.getStatus() + ").");
        }
        boolean ok = rtvDAO.updateStatus(rtvId, "COMPLETED");
        if (!ok) {
            return RtvResult.failure("Không thể hoàn thành phiếu RTV. Vui lòng thử lại.");
        }

        // Deduct defective inventory
        if (rtv.getItems() != null) {
            for (RtvItem item : rtv.getItems()) {
                boolean deducted = inventoryDAO.deductDefectiveInventory(
                    item.getProductId(), rtv.getWarehouseId(), item.getQtyReturn(), userId, "Xuất trả NCC (" + rtv.getCode() + ")");
                if (!deducted) {
                    log.warn("Failed to deduct defective inventory for product ID={} in warehouse ID={}",
                        item.getProductId(), rtv.getWarehouseId());
                }
            }
        }

        log.info("RTV completed: rtvId={} by={}", rtvId, userId);
        return RtvResult.success("Hoàn thành phiếu trả hàng " + rtv.getCode() + "!", rtvId);
    }

    /**
     * Cancel a PENDING or APPROVED RTV → moves to CANCELLED.
     */
    public RtvResult cancelRtv(int rtvId, int userId) {
        RtvOrder rtv = rtvDAO.findById(rtvId);
        if (rtv == null) {
            return RtvResult.failure("Phiếu RTV #" + rtvId + " không tồn tại.");
        }
        if ("COMPLETED".equals(rtv.getStatus()) || "CANCELLED".equals(rtv.getStatus())) {
            return RtvResult.failure("Phiếu RTV đã hoàn thành hoặc đã bị hủy, không thể hủy thêm.");
        }
        boolean ok = rtvDAO.updateStatus(rtvId, "CANCELLED");
        if (!ok) {
            return RtvResult.failure("Không thể hủy phiếu RTV. Vui lòng thử lại.");
        }
        log.info("RTV cancelled: rtvId={} by={}", rtvId, userId);
        return RtvResult.success("Đã hủy phiếu trả hàng " + rtv.getCode() + ".", rtvId);
    }

    // -----------------------------------------------------------------------
    // Inner DTOs / Result types
    // -----------------------------------------------------------------------

    /** JSON-deserialisable DTO for items sent from the browser. */
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public static class RtvItemRequest {
        private int productId;
        private BigDecimal qtyReturn;
        private BigDecimal unitCost;

        public int getProductId() { return productId; }
        public void setProductId(int productId) { this.productId = productId; }

        public BigDecimal getQtyReturn() { return qtyReturn; }
        public void setQtyReturn(BigDecimal qtyReturn) { this.qtyReturn = qtyReturn; }

        public BigDecimal getUnitCost() { return unitCost; }
        public void setUnitCost(BigDecimal unitCost) { this.unitCost = unitCost; }
    }

    /** Unified result wrapper returned to the servlet. */
    public static class RtvResult {
        private final boolean success;
        private final String message;
        private final int rtvId;

        private RtvResult(boolean success, String message, int rtvId) {
            this.success = success;
            this.message = message;
            this.rtvId = rtvId;
        }

        public static RtvResult success(String message, int rtvId) {
            return new RtvResult(true, message, rtvId);
        }

        public static RtvResult failure(String message) {
            return new RtvResult(false, message, 0);
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public int getRtvId() { return rtvId; }
    }
}
