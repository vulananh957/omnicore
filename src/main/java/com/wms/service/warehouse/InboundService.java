package com.wms.service.warehouse;

import com.wms.dao.InboundDAO;
import com.wms.dao.InventoryDAO;
import com.wms.dao.ProductDAO;
import com.wms.model.InboundOrder;
import com.wms.model.Product;
import com.wms.model.ReceiptNote;
import com.wms.model.Supplier;
import com.wms.service.business.SupplierService;
import com.wms.service.common.NotificationService;
import com.wms.service.marketplace.MarketplaceSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class InboundService {

    private static final Logger log = LoggerFactory.getLogger(InboundService.class);

    private final InboundDAO inboundDAO = new InboundDAO();
    private final InventoryDAO inventoryDAO = new InventoryDAO();
    private final ProductDAO productDAO = new ProductDAO();
    private final MarketplaceSyncService marketplaceSyncService = new MarketplaceSyncService();
    private final SupplierService supplierService = new SupplierService();
    private final NotificationService notificationService = new NotificationService();

    public List<InboundOrder> findAll() {
        return inboundDAO.findAll();
    }

    public List<InboundOrder> findByWarehouse(int warehouseId) {
        return inboundDAO.findByWarehouse(warehouseId);
    }

    public List<InboundOrder> findByStatus(String status) {
        return inboundDAO.findByStatus(status);
    }

    public InboundOrder findById(int inboundId) {
        return inboundDAO.findById(inboundId);
    }

    public ValidationResult validateForCreate(Integer supplierId, Integer warehouseId) {
        if (supplierId == null || supplierId <= 0) {
            return ValidationResult.failure("Vui lòng chọn nhà cung cấp từ danh sách (liên kết với trang quản lý NCC).");
        }
        if (warehouseId == null || warehouseId <= 0) {
            return ValidationResult.failure("Vui lòng chọn kho hàng.");
        }
        Supplier sup = supplierService.getSupplierById(supplierId);
        if (sup == null) {
            return ValidationResult.failure("Nhà cung cấp không tồn tại trong hệ thống (supplierId=" + supplierId + ").");
        }
        if (!sup.isActive()) {
            return ValidationResult.failure("Nhà cung cấp \"" + sup.getName()
                    + "\" hiện không ở trạng thái ACTIVE (" + sup.getStatus() + ") — không thể tạo phiếu mua.");
        }
        return ValidationResult.success();
    }

    /**
     * Tạo phiếu mua hàng (PO) với ràng buộc: phải chọn 1 supplier ACTIVE từ bảng suppliers.
     * @param supplierId  FK -> suppliers.supplier_id (BẮT BUỘC)
     * @param warehouseId FK -> warehouses.warehouse_id
     * @param draftItems  Danh sách SKU dự kiến (do Controller truyền vào từ request)
     */
    public CreateInboundResult createInbound(int supplierId, int warehouseId, LocalDate expectedDate,
                                            String notes, int createdBy, List<DraftItem> draftItems) {
        ValidationResult v = validateForCreate(supplierId, warehouseId);
        if (!v.isSuccess()) {
            return CreateInboundResult.failure(v.getMessage());
        }

        Supplier sup = supplierService.getSupplierById(supplierId);

        InboundOrder order = new InboundOrder();
        order.setSupplierId(supplierId);
        // Supplier name snapshot lấy từ bảng suppliers (ràng buộc tên NCC luôn đồng bộ).
        order.setSupplierName(sup.getName());
        order.setWarehouseId(warehouseId);
        // Workflow: PENDING → PURCHASED → IN_PROGRESS → RECEIVED
        // Phiếu mới tạo sẽ ở trạng thái PENDING (chưa mua hàng).
        // Khi staff xác nhận đã mua → status PURCHASED. Khi bắt đầu nhập → IN_PROGRESS.
        order.setStatus(InboundOrder.STATUS_PENDING);
        order.setCreatedBy(createdBy);
        order.setNotes(notes != null && !notes.trim().isEmpty() ? notes.trim() : null);
        if (expectedDate != null) {
            order.setExpectedDate(expectedDate);
        }
        if (sup != null && sup.getPaymentTerms() != null && !sup.getPaymentTerms().isEmpty()) {
            order.setPaymentTerms(sup.getPaymentTerms());
        }
        int inboundId = inboundDAO.insert(order);
        if (inboundId <= 0) {
            return CreateInboundResult.failure("Không thể tạo phiếu mua hàng (DB insert thất bại).");
        }

        // Persist SKU items: update price + insert inbound_items.
        List<String> warnings = new ArrayList<>();
        if (draftItems != null) {
            for (DraftItem item : draftItems) {
                if (item == null || item.getSkuCode() == null || item.getSkuCode().isBlank()) {
                    continue;
                }
                if (item.getOrderedQty() == null || item.getOrderedQty().compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                Product prod = productDAO.findBySkuCode(item.getSkuCode());
                if (prod == null) {
                    warnings.add("Không tìm thấy SKU: " + item.getSkuCode());
                    continue;
                }
                // Cập nhật base price nếu user nhập giá mới > 0
                if (item.getPrice() != null && item.getPrice().compareTo(BigDecimal.ZERO) > 0) {
                    prod.setBasePrice(item.getPrice().doubleValue());
                    productDAO.update(prod);
                }
                ReceiptNote rn = new ReceiptNote();
                rn.setInboundId(inboundId);
                rn.setProductId(prod.getProductId());
                rn.setExpectedQty(item.getOrderedQty());
                rn.setReceivedQty(BigDecimal.ZERO);
                rn.setAcceptedQty(BigDecimal.ZERO);
                rn.setRejectedQty(BigDecimal.ZERO);
                rn.setUnitCost(item.getPrice() != null ? item.getPrice() : BigDecimal.ZERO);
                boolean inserted = inboundDAO.insertReceipt(rn);
                if (!inserted) {
                    warnings.add("Không thể thêm SKU " + item.getSkuCode() + " vào phiếu.");
                }
            }
        }

        return CreateInboundResult.success(inboundId, warnings);
    }

    /**
     * LEGACY: tạo PO không liên kết với suppliers (chỉ lưu supplier text).
     * Dùng cho dữ liệu cũ hoặc khi supplierId không có trong request.
     */
    public CreateInboundResult createInboundLegacy(String supplierName, int warehouseId, LocalDate expectedDate,
                                                   String notes, int createdBy, List<DraftItem> draftItems) {
        if (supplierName == null || supplierName.trim().isEmpty()) {
            return CreateInboundResult.failure("Vui lòng nhập tên nhà cung cấp.");
        }
        if (warehouseId <= 0) {
            return CreateInboundResult.failure("Vui lòng chọn kho hàng.");
        }

        InboundOrder order = new InboundOrder();
        order.setSupplierName(supplierName.trim());
        order.setWarehouseId(warehouseId);
        order.setStatus(InboundOrder.STATUS_PENDING);
        order.setCreatedBy(createdBy);
        order.setNotes(notes != null && !notes.trim().isEmpty() ? notes.trim() : null);
        if (expectedDate != null) {
            order.setExpectedDate(expectedDate);
        }
        int inboundId = inboundDAO.insert(order);
        if (inboundId <= 0) {
            return CreateInboundResult.failure("Không thể tạo phiếu mua hàng (DB insert thất bại).");
        }

        // Persist items (giống path chính)
        List<String> warnings = new ArrayList<>();
        if (draftItems != null) {
            for (DraftItem item : draftItems) {
                if (item == null || item.getSkuCode() == null || item.getSkuCode().isBlank()) continue;
                if (item.getOrderedQty() == null || item.getOrderedQty().compareTo(BigDecimal.ZERO) <= 0) continue;
                Product prod = productDAO.findBySkuCode(item.getSkuCode());
                if (prod == null) { warnings.add("Không tìm thấy SKU: " + item.getSkuCode()); continue; }
                if (item.getPrice() != null && item.getPrice().compareTo(BigDecimal.ZERO) > 0) {
                    prod.setBasePrice(item.getPrice().doubleValue());
                    productDAO.update(prod);
                }
                ReceiptNote rn = new ReceiptNote();
                rn.setInboundId(inboundId);
                rn.setProductId(prod.getProductId());
                rn.setExpectedQty(item.getOrderedQty());
                rn.setReceivedQty(BigDecimal.ZERO);
                rn.setAcceptedQty(BigDecimal.ZERO);
                rn.setRejectedQty(BigDecimal.ZERO);
                rn.setUnitCost(item.getPrice() != null ? item.getPrice() : BigDecimal.ZERO);
                if (!inboundDAO.insertReceipt(rn)) {
                    warnings.add("Không thể thêm SKU " + item.getSkuCode() + " vào phiếu.");
                }
            }
        }
        return CreateInboundResult.success(inboundId, warnings);
    }

    /**
     * LEGACY validation cho supplier text (không có supplierId).
     */
    public ValidationResult validateForCreateLegacy(String supplierName, Integer warehouseId) {
        if (supplierName == null || supplierName.trim().isEmpty()) {
            return ValidationResult.failure("Vui lòng nhập tên nhà cung cấp.");
        }
        if (warehouseId == null || warehouseId <= 0) {
            return ValidationResult.failure("Vui lòng chọn kho hàng.");
        }
        return ValidationResult.success();
    }

    /**
     * Chuyển phiếu mua hàng từ PENDING → PURCHASED (đã đặt mua NCC, sẵn sàng nhập kho).
     * Validate: chỉ áp dụng khi status hiện tại = PENDING.
     */
    public ValidationResult markPurchased(int inboundId, int userId) {
        InboundOrder existing = inboundDAO.findById(inboundId);
        if (existing == null) {
            return ValidationResult.failure("Phiếu mua hàng không tồn tại.");
        }
        if (!InboundOrder.STATUS_PENDING.equals(existing.getStatus())) {
            return ValidationResult.failure("Chỉ phiếu ở trạng thái 'Chờ' mới có thể xác nhận đã mua.");
        }
        boolean ok = inboundDAO.updateStatusOnly(inboundId, InboundOrder.STATUS_PURCHASED);
        if (!ok) {
            return ValidationResult.failure("Không thể cập nhật trạng thái phiếu.");
        }
        log.info("Marked PO as purchased: inboundId={} userId={}", inboundId, userId);
        return ValidationResult.success();
    }

    /**
     * Hoàn thành phiếu nhập: IN_PROGRESS → RECEIVED.
     * Dùng khi đã nhận đủ hàng từ NCC (1 hoặc nhiều đợt).
     */
    public ValidationResult completeInbound(int inboundId, int userId) {
        InboundOrder existing = inboundDAO.findById(inboundId);
        if (existing == null) {
            return ValidationResult.failure("Phiếu nhập không tồn tại.");
        }
        if (!InboundOrder.STATUS_IN_PROGRESS.equals(existing.getStatus())) {
            return ValidationResult.failure("Chỉ phiếu đang kiểm đếm mới có thể hoàn thành.");
        }
        boolean ok = inboundDAO.updateStatus(inboundId, InboundOrder.STATUS_RECEIVED);
        if (!ok) {
            return ValidationResult.failure("Không thể cập nhật trạng thái phiếu.");
        }
        log.info("Inbound completed: inboundId={} userId={}", inboundId, userId);
        return ValidationResult.success();
    }

    public ReceiveResult receiveGoods(int inboundId, Integer zoneId, List<ReceiptItem> receiptItems,
                                     java.time.LocalDate receivedDate, String deliveryPerson,
                                     String deliveryPhone, int userId) {
        InboundOrder existing = inboundDAO.findById(inboundId);
        if (existing == null) {
            log.warn("Receive goods failed: order not found id={}", inboundId);
            return ReceiveResult.failure("Phiếu nhập không tồn tại.");
        }
        
        // Cập nhật zone cho phiếu nhập kho
        if (zoneId != null && zoneId > 0) {
            inboundDAO.updateZoneId(inboundId, zoneId);
        }

        // Workflow: cho phép nhận hàng khi phiếu ở PENDING (chưa mua), PURCHASED (đã mua) hoặc IN_PROGRESS (đang nhập dở).
        // Khi nhận từ PENDING, tự động chuyển sang IN_PROGRESS luôn (skip bước "Mua phiếu" - phiếu nhập sinh ra từ việc nhận hàng).
        if (!InboundOrder.STATUS_PENDING.equals(existing.getStatus())
                && !InboundOrder.STATUS_PURCHASED.equals(existing.getStatus())
                && !InboundOrder.STATUS_IN_PROGRESS.equals(existing.getStatus())) {
            log.warn("Receive goods failed: wrong status id={} status={}", inboundId, existing.getStatus());
            return ReceiveResult.failure("Phiếu ở trạng thái không hợp lệ để nhận hàng.");
        }

        // Nếu phiếu đang ở PENDING hoặc PURCHASED → chuyển sang IN_PROGRESS ngay khi staff bắt đầu nhận.
        if (!InboundOrder.STATUS_IN_PROGRESS.equals(existing.getStatus())) {
            inboundDAO.updateStatusOnly(inboundId, InboundOrder.STATUS_IN_PROGRESS);
        }

        int successCount = 0;
        int failCount = 0;

        if (receiptItems != null) {
            for (ReceiptItem item : receiptItems) {
                try {
                    BigDecimal received = item.getReceivedQty() != null ? item.getReceivedQty() : BigDecimal.ZERO;
                    if (received.compareTo(BigDecimal.ZERO) <= 0) {
                        continue;
                    }
                    BigDecimal accepted = item.getAcceptedQty() != null ? item.getAcceptedQty() : BigDecimal.ZERO;
                    BigDecimal rejected = item.getRejectedQty() != null ? item.getRejectedQty() : BigDecimal.ZERO;
                    BigDecimal unitCost = item.getUnitCost() != null ? item.getUnitCost() : BigDecimal.ZERO;

                    int productId = item.getProductId();
                    if (productId <= 0) {
                        log.warn("Receive goods skipped: invalid productId={} for inboundId={}", item.getProductId(), inboundId);
                        continue;
                    }

                    // Ghi nhận đầy đủ 3 cột qty: thực nhận / chấp nhận / trả NCC.
                    inboundDAO.updateReceivedQtys(inboundId, productId,
                            received, accepted, rejected, item.getRejectReason(), unitCost);

                    // Cộng SL chấp nhận vào tồn kho + cập nhật MAC (chỉ phần đạt chuẩn mới vào kho).
                    double currentOnHand = 0.0;
                    BigDecimal currentMac = BigDecimal.ZERO;
                    var prod = productDAO.findById(productId);
                    if (prod != null) {
                        currentOnHand = prod.getQtyOnHand() != null ? prod.getQtyOnHand() : 0.0;
                        currentMac = productDAO.findMacPrice(productId);
                    }
                    boolean invAdded = inventoryDAO.addInventory(productId, existing.getWarehouseId(), accepted, userId);
                    if (!invAdded) {
                        failCount++;
                        log.error("Receive goods item error: inboundId={} productId={} addInventory failed — "
                                + "stock NOT updated, skipping MAC update to avoid cost/quantity drift", inboundId, productId);
                        continue;
                    }
                    productDAO.updateMacPrice(
                            productId,
                            BigDecimal.valueOf(currentOnHand),
                            currentMac,
                            accepted,
                            unitCost);

                    // Đồng bộ tổng tồn kho khả dụng mới sang bảng products cho Web/Lazada
                    productDAO.syncStockTotals(productId);

                    // Tự động cấu hình default zone cho sản phẩm tại kho này nếu được chọn
                    if (zoneId != null && zoneId > 0) {
                        productDAO.updateZoneForWarehouse(productId, existing.getWarehouseId(), zoneId);
                    }

                    successCount++;
                } catch (Exception e) {
                    failCount++;
                    log.error("Receive goods item error: inboundId={} productId={} error={}",
                            inboundId, item.getProductId(), e.getMessage());
                }
            }
        }

        // Nếu tất cả items đã nhận đủ → tự động chuyển RECEIVED.
        // Ngược lại giữ IN_PROGRESS để có thể nhập thêm đợt sau.
        String finalStatus = inboundDAO.isAllItemsReceived(inboundId)
                ? InboundOrder.STATUS_RECEIVED
                : InboundOrder.STATUS_IN_PROGRESS;
        inboundDAO.updateStatus(inboundId, finalStatus);
        log.info("Goods receive logged: inboundId={} warehouseId={} userId={} success={} failed={} → status={}",
                inboundId, existing.getWarehouseId(), userId, successCount, failCount, finalStatus);

        // Trigger real-time marketplace stock sync (async — does not block the HTTP response)
        // Push_Qty = SUM(qty_available all warehouses) - bufferStock, batched 20 SKU/call
        if (successCount > 0) {
            List<Integer> receivedProductIds = receiptItems.stream()
                    .filter(item -> item.getReceivedQty() != null
                            && item.getReceivedQty().compareTo(BigDecimal.ZERO) > 0)
                    .map(item -> item.getProductId())
                    .distinct()
                    .toList();
            if (!receivedProductIds.isEmpty()) {
                String finalInboundCode = existing.getInboundCode();
                CompletableFuture.runAsync(() -> {
                    try {
                        marketplaceSyncService.triggerStockSyncAfterInbound(receivedProductIds, finalInboundCode);
                    } catch (Exception e) {
                        log.error("Marketplace stock sync failed after inbound receive: inboundCode={}",
                                finalInboundCode, e);
                    }
                });
            }
        }

        String msg = (failCount == 0)
            ? "Nhập kho phiếu " + existing.getInboundCode() + " thành công! Tồn kho đã được cập nhật."
            : "Nhập kho phiếu " + existing.getInboundCode() + " hoàn tất (một số dòng có lỗi).";



        return ReceiveResult.success(msg);
    }

    public static class ReceiptItem {
        private int productId;
        private BigDecimal receivedQty;  // SL thực nhận (tổng cộng từ NCC, gồm cả hàng lỗi)
        private BigDecimal acceptedQty;  // SL chấp nhận (đạt chuẩn, được cộng vào tồn kho)
        private BigDecimal rejectedQty;  // SL không đạt chuẩn (trả lại NCC tại chỗ)
        private String rejectReason;     // Lý do trả hàng NCC
        private BigDecimal unitCost;     // đơn giá nhập, dùng để tính MAC

        public int getProductId() { return productId; }
        public void setProductId(int productId) { this.productId = productId; }
        public BigDecimal getReceivedQty() { return receivedQty; }
        public void setReceivedQty(BigDecimal receivedQty) { this.receivedQty = receivedQty; }
        public BigDecimal getAcceptedQty() { return acceptedQty; }
        public void setAcceptedQty(BigDecimal acceptedQty) { this.acceptedQty = acceptedQty; }
        public BigDecimal getRejectedQty() { return rejectedQty; }
        public void setRejectedQty(BigDecimal rejectedQty) { this.rejectedQty = rejectedQty; }
        public String getRejectReason() { return rejectReason; }
        public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }
        public BigDecimal getUnitCost() { return unitCost; }
        public void setUnitCost(BigDecimal unitCost) { this.unitCost = unitCost; }
    }

    public static class ValidationResult {
        private final boolean success;
        private final String message;

        private ValidationResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public static ValidationResult success() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult failure(String message) {
            return new ValidationResult(false, message);
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }

    public static class ReceiveResult {
        private final boolean success;
        private final String message;

        private ReceiveResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public static ReceiveResult success(String message) {
            return new ReceiveResult(true, message);
        }

        public static ReceiveResult failure(String message) {
            return new ReceiveResult(false, message);
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }

    /**
     * Kết quả trả về từ createInbound/createInboundLegacy:
     * - success/failure + message
     * - inboundId (khi success)
     * - warnings: danh sách SKU không insert được (vd SKU không tồn tại) — Controller
     *   sẽ hiển thị thêm cho user nhưng vẫn coi như tạo phiếu thành công.
     */
    public static class CreateInboundResult {
        private final boolean success;
        private final String message;
        private final int inboundId;
        private final List<String> warnings;

        private CreateInboundResult(boolean success, String message, int inboundId, List<String> warnings) {
            this.success = success;
            this.message = message;
            this.inboundId = inboundId;
            this.warnings = warnings;
        }

        public static CreateInboundResult success(int inboundId, List<String> warnings) {
            return new CreateInboundResult(true, null, inboundId, warnings != null ? warnings : new ArrayList<>());
        }

        public static CreateInboundResult failure(String message) {
            return new CreateInboundResult(false, message, -1, new ArrayList<>());
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public int getInboundId() { return inboundId; }
        public List<String> getWarnings() { return warnings; }
    }

    /**
     * DTO đầu vào: 1 dòng SKU trong phiếu mua hàng (PO draft).
     * Đứng trong Service layer để Controller chỉ parse JSON rồi truyền vào service.
     */
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public static class DraftItem {
        private String skuCode;
        private BigDecimal orderedQty;
        private BigDecimal price;

        public String getSkuCode() { return skuCode; }
        public void setSkuCode(String skuCode) { this.skuCode = skuCode; }
        public BigDecimal getOrderedQty() { return orderedQty; }
        public void setOrderedQty(BigDecimal orderedQty) { this.orderedQty = orderedQty; }
        public BigDecimal getPrice() { return price; }
        public void setPrice(BigDecimal price) { this.price = price; }
    }
}
