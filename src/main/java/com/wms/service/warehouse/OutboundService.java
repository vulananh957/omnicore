package com.wms.service.warehouse;

import com.wms.dao.InventoryDAO;
import com.wms.dao.FulfillmentRequestDAO;
import com.wms.dao.OutboundDAO;
import com.wms.dao.OrderDAO;
import com.wms.dao.ProductDAO;
import com.wms.dao.UserDAO;
import com.wms.dao.WarehouseIssueDAO;
import com.wms.model.FulfillmentRequest;
import com.wms.model.Order;
import com.wms.model.Product;
import com.wms.model.User;
import com.wms.model.OutboundOrder;
import com.wms.model.OutboundItem;
import com.wms.model.OrderItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import com.wms.dao.LedgerDAO;
import com.wms.mockshipping.MockShippingService;
import com.wms.service.channel.WebsiteHttpClient;

public class OutboundService {

    private static final Logger log = LoggerFactory.getLogger(OutboundService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final OutboundDAO outboundDAO = new OutboundDAO();
    private final OrderDAO orderDAO = new OrderDAO();
    private final InventoryDAO inventoryDAO = new InventoryDAO();
    private final FulfillmentRequestDAO fulfillmentRequestDAO = new FulfillmentRequestDAO();
    private final ProductDAO productDAO = new ProductDAO();
    private final WarehouseIssueDAO warehouseIssueDAO = new WarehouseIssueDAO();
    private final UserDAO userDAO = new UserDAO();
    private final MockShippingService mockShippingService = new MockShippingService();

    public List<OutboundOrder> findAll() {
        return outboundDAO.findAll();
    }

    public List<OutboundOrder> findByStatus(String status) {
        return outboundDAO.findByStatus(status.trim().toUpperCase());
    }

    /**
     * Returns pending fulfillment requests scoped to a warehouse.
     * Delegating method so controllers do not need a direct FulfillmentRequestDAO reference.
     */
    public List<FulfillmentRequest> findPendingFulfillmentsByWarehouse(int warehouseId) {
        return fulfillmentRequestDAO.findPendingByWarehouse(warehouseId);
    }

    /**
     * Returns the inventory stock summary for a warehouse.
     * Delegating method so controllers do not need a direct InventoryDAO reference.
     */
    public List<Map<String, Object>> findInventorySummaryByWarehouse(int warehouseId) {
        return inventoryDAO.findInventorySummaryByWarehouse(warehouseId);
    }

    /**
     * Looks up an Order by its order code.
     * Delegating method so controllers do not need a direct OrderDAO reference.
     */
    public Order findOrderByCode(String orderCode) {
        return orderDAO.findByOrderCode(orderCode);
    }

    /** Outbound orders for one warehouse (warehouse-scoped list). */
    public List<OutboundOrder> findByWarehouse(int warehouseId) {
        List<OutboundOrder> list = outboundDAO.findByWarehouse(warehouseId);
        syncOutboundStatuses(list);
        return list;
    }

    /** Outbound orders for one warehouse filtered by status. */
    public List<OutboundOrder> findByWarehouseAndStatus(int warehouseId, String status) {
        List<OutboundOrder> list = outboundDAO.findByWarehouseAndStatus(warehouseId, status.trim().toUpperCase());
        syncOutboundStatuses(list);
        return list;
    }

    private void syncOutboundStatuses(List<OutboundOrder> list) {
        if (list == null || list.isEmpty()) return;
        for (OutboundOrder oo : list) {
            String code = oo.getOrderCode();
            if (code != null && !code.trim().isEmpty()) {
                try {
                    Order order = orderDAO.findByOrderCode(code);
                    if (order != null) {
                        String orderStatus = order.getStatus();
                        if (orderStatus != null) {
                            String normalized = orderStatus.trim().toUpperCase();
                            String currentOutboundStatus = oo.getStatus();
                            
                            // Check if status needs update
                            String targetStatus = null;
                            if ("CANCELLED".equals(normalized) || "CANCELED".equals(normalized)) {
                                if (!"CANCELLED".equals(currentOutboundStatus)) {
                                    targetStatus = "CANCELLED";
                                }
                            } else if ("PACKED".equals(normalized)) {
                                if (!"PACKED".equals(currentOutboundStatus) && !"HANDED_OVER".equals(currentOutboundStatus) && !"SHIPPED".equals(currentOutboundStatus)) {
                                    targetStatus = "PACKED";
                                }
                            } else if ("READY_TO_SHIP".equals(normalized) || "HANDED_OVER".equals(normalized)) {
                                if (!"HANDED_OVER".equals(currentOutboundStatus) && !"SHIPPED".equals(currentOutboundStatus)) {
                                    targetStatus = "HANDED_OVER";
                                }
                            } else if ("SHIPPED".equals(normalized) || "DELIVERED".equals(normalized) || "COMPLETED".equals(normalized)) {
                                if (!"SHIPPED".equals(currentOutboundStatus)) {
                                    targetStatus = "SHIPPED";
                                }
                            }
                            
                            if (targetStatus != null) {
                                log.info("syncOutboundStatuses: Syncing status of outboundId={} to {} to match order status={}", 
                                    oo.getOutboundId(), targetStatus, normalized);
                                outboundDAO.updateStatus(oo.getOutboundId(), targetStatus);
                                oo.setStatus(targetStatus); // update local object too
                                
                                // Additional lifecycle updates if transitioning to HANDED_OVER
                                if ("HANDED_OVER".equals(targetStatus)) {
                                    outboundDAO.markAllPicked(oo.getOutboundId());
                                    outboundDAO.completePickingSheet(oo.getOutboundId());
                                    outboundDAO.createShippingLabel(oo.getOutboundId());
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("syncOutboundStatuses failed for outboundId=" + oo.getOutboundId(), e);
                }
            }
        }
    }

    public OutboundOrder findById(int outboundId) {
        return outboundDAO.findById(outboundId);
    }

    public String generateOutboundCode() {
        String today = LocalDate.now().format(DATE_FMT);
        int seq = (int)(Math.random() * 999);
        return "SOUT-" + today + "-" + String.format("%03d", seq);
    }

    private static final int MAX_CODE_ATTEMPTS = 5;

    /**
     * Inserts an outbound order, generating a fresh outbound_code on each attempt.
     * generateOutboundCode()'s 3-digit random suffix collides often enough on busy days
     * (uq_outbound_code enforces it at the DB level) — retry instead of failing the create.
     */
    private int insertWithRetry(OutboundOrder order) {
        for (int attempt = 1; attempt <= MAX_CODE_ATTEMPTS; attempt++) {
            order.setOutboundCode(generateOutboundCode());
            int result = outboundDAO.insert(order);
            if (result != OutboundDAO.DUPLICATE_CODE) {
                return result;
            }
            log.warn("insertWithRetry: outbound_code collision on attempt {}/{}, retrying",
                    attempt, MAX_CODE_ATTEMPTS);
        }
        log.error("insertWithRetry: failed after {} attempts due to repeated outbound_code collisions",
                MAX_CODE_ATTEMPTS);
        return -1;
    }

    public ValidationResult validateForCreate(Integer orderId, Integer warehouseId) {
        if (orderId == null || orderId <= 0) {
            return ValidationResult.failure("Thiếu thông tin bắt buộc: orderId.");
        }
        if (warehouseId == null || warehouseId <= 0) {
            return ValidationResult.failure("Thiếu thông tin bắt buộc: warehouseId.");
        }
        return ValidationResult.success();
    }

    public int createOutbound(int orderId, int warehouseId, String notes, Integer userId) {
        OutboundOrder order = new OutboundOrder();
        order.setOrderId(orderId);
        order.setWarehouseId(warehouseId);
        order.setCreatedBy(userId);
        order.setStatus(OutboundOrder.STATUS_PENDING);
        order.setNotes(notes != null ? notes.trim() : null);
        order.setCreatedAt(LocalDateTime.now());
        return insertWithRetry(order);
    }

    /**
     * Creates a disposal (SCRAP) issue note. Saves the note only — no stock deduction
     * (deduction is deferred to BM approval).
     */
    public StatusUpdateResult createDisposal(String sku, java.math.BigDecimal qty, String reason,
                                             int warehouseId, Integer userId) {
        if (sku == null || sku.trim().isEmpty()) {
            return StatusUpdateResult.failure("Vui lòng chọn SKU cần xuất huỷ.");
        }
        if (qty == null || qty.compareTo(java.math.BigDecimal.ZERO) <= 0) {
            return StatusUpdateResult.failure("Số lượng tiêu huỷ phải lớn hơn 0.");
        }
        Product p = productDAO.findBySkuCode(sku.trim());
        if (p == null) {
            return StatusUpdateResult.failure("Không tìm thấy sản phẩm với SKU: " + sku);
        }
        int creator = (userId != null) ? userId : 1;
        String code = warehouseIssueDAO.createScrapIssue(warehouseId, creator, p.getProductId(), qty, reason);
        if (code == null) {
            return StatusUpdateResult.failure("Không thể lưu phiếu xuất huỷ. Vui lòng thử lại.");
        }
        return StatusUpdateResult.success("Đã lưu phiếu xuất huỷ " + code + " (chờ duyệt, chưa trừ tồn).");
    }

    public List<Map<String, Object>> findScrapProductsWithQty(int warehouseId) {
        return warehouseIssueDAO.findScrapProductsWithQty(warehouseId);
    }

    public StatusUpdateResult updateStatus(int outboundId, String newStatus) {
        return updateStatus(outboundId, newStatus, null);
    }

    /** Persists a single line item's picked state. */
    public boolean updateItemPicked(int outboundId, int productId, boolean picked) {
        return outboundDAO.updateItemPicked(outboundId, productId, picked);
    }

    /** Updates the note/comment on an outbound order. */
    public boolean updateNote(int outboundId, String note) {
        return outboundDAO.updateNote(outboundId, note);
    }

    /** Updates the quantity of a single line item on an outbound order. */
    public boolean updateItemQty(int outboundId, int productId, java.math.BigDecimal qty) {
        return outboundDAO.updateItemQty(outboundId, productId, qty);
    }

    public StatusUpdateResult updateStatus(int outboundId, String newStatus, Integer userId) {
        if (!isValidStatus(newStatus)) {
            log.warn("Outbound status update rejected: invalid status outboundId={} status={}", outboundId, newStatus);
            return StatusUpdateResult.failure("Trạng thái '" + newStatus + "' không hợp lệ hoặc không thể chuyển đổi.");
        }

        OutboundOrder current = outboundDAO.findById(outboundId);
        if (current == null) {
            log.warn("Outbound status update rejected: not found outboundId={}", outboundId);
            return StatusUpdateResult.failure("Phiếu xuất không tồn tại.");
        }

        String normalized = newStatus.trim().toUpperCase();
        if ("HANDED_OVER".equals(normalized) || "SHIPPED".equals(normalized)) {
            OutboundOrder outbound = outboundDAO.findById(outboundId);
            if (outbound != null && outbound.getOrderCode() != null) {
                Order order = orderDAO.findByOrderCode(outbound.getOrderCode());
                if (order != null && ("LAZADA".equalsIgnoreCase(order.getChannel())
                        || "SHOPEE".equalsIgnoreCase(order.getChannel())
                        || "TIKTOK".equalsIgnoreCase(order.getChannel()))) {
                    if (order.getTrackingNo() == null || order.getTrackingNo().trim().isEmpty()) {
                        return StatusUpdateResult.failure("Không thể đóng gói/xuất kho: Đơn hàng trên sàn chưa được cấp mã vận đơn (tracking). Vui lòng cấp mã vận đơn trước.");
                    }
                }
            }
        }

        // Idempotency: repeat SHIPPED call (double-click, retried request) on an outbound
        // already shipped must no-op instead of re-deducting inventory / re-pushing to Website.
        if ("SHIPPED".equals(normalized) && OutboundOrder.STATUS_SHIPPED.equals(current.getStatus())) {
            outboundDAO.createDeliveryNote(outboundId, userId);
            return StatusUpdateResult.success("Đơn đã xuất kho trước đó.");
        }

        // Atomic transition — the ONLY place outbound_orders.status changes for this call.
        // Previously, SHIPPED for an omnichannel order (Website/Lazada/Shopee/TikTok/retail —
        // isOmnichannelOutbound() returns true for all of these) took an early-return branch
        // that called LedgerDAO.approveDocument(), which did its own UNGUARDED
        // "UPDATE outbound_orders SET status='SHIPPED'" bypassing this optimistic lock
        // entirely (two concurrent SHIPPED calls could both pass, both deduct inventory via
        // the ledger, double-counting it) — and then returned immediately, so the order-status
        // sync / Website push / stock sync below never ran for these orders at all.
        boolean updated = outboundDAO.compareAndSetStatus(outboundId, normalized, current.getVersion());
        if (!updated) {
            log.warn("Outbound status update rejected: version conflict (concurrent update) outboundId={} status={} expectedVersion={}",
                outboundId, newStatus, current.getVersion());
            return StatusUpdateResult.failure("Phiếu xuất vừa được cập nhật bởi người khác. Vui lòng tải lại trang và thử lại.");
        }

        // Picking-sheet lifecycle: start sheet + assign picker on PACKED; complete on HANDED_OVER.
        if (OutboundOrder.STATUS_PACKED.equals(normalized)) {
            if (userId != null) outboundDAO.assignPicker(outboundId, userId);
            outboundDAO.createPickingSheet(outboundId, userId);

            // Generate a mock waybill for the carrier the CUSTOMER already chose at checkout
            // (com.wms.mockshipping — orders.shipment_provider). Warehouse doesn't pick a
            // carrier here anymore; if the order has none (mock was off at checkout, or this
            // is an order from before this feature existed), leave tracking empty — mock is
            // opt-in, not force-applied.
            OutboundOrder oo = outboundDAO.findById(outboundId);
            if (oo != null && oo.getOrderCode() != null) {
                Order order = orderDAO.findByOrderCode(oo.getOrderCode());
                if (order != null && "WEBSITE".equalsIgnoreCase(order.getChannel())
                        && order.getShipmentProvider() != null && !order.getShipmentProvider().isBlank()
                        && (order.getTrackingNo() == null || order.getTrackingNo().trim().isEmpty())) {
                    String mockTracking = mockShippingService.generateWaybillCode(order.getShipmentProvider());
                    orderDAO.updateOrderTrackingNo(order.getOrderCode(), mockTracking);
                    updateMockWaybillCode(order.getOrderId(), mockTracking, order.getShipmentProvider());
                }
            }
        } else if (OutboundOrder.STATUS_HANDED_OVER.equals(normalized)) {
            outboundDAO.markAllPicked(outboundId);
            outboundDAO.completePickingSheet(outboundId);
            outboundDAO.createShippingLabel(outboundId);
            // Sync sales order status to PACKED
            OutboundOrder order = outboundDAO.findById(outboundId);
            if (order != null && order.getOrderCode() != null) {
                new com.wms.dao.OrderDAO().updateOrderStatus(order.getOrderCode(), "PACKED");

                // Push to Website storefront too — previously only SHIPPED pushed, so a
                // customer looking at their order-history list (which doesn't live-refresh,
                // unlike the order-detail page) never saw the "đang đóng gói" transition.
                Order salesOrder = orderDAO.findByOrderCode(order.getOrderCode());
                if (salesOrder != null && "WEBSITE".equalsIgnoreCase(salesOrder.getChannel())) {
                    syncOrderStatusToWebsite(salesOrder.getOrderCode(), "PACKED", salesOrder.getTrackingNo());
                }
            }
        } else if (OutboundOrder.STATUS_SHIPPED.equals(normalized)) {
            // On SHIPPED, deduct actual on-hand stock. Two mutually-exclusive deduction paths
            // (never both, to avoid double-deducting): omnichannel orders (Website/Lazada/
            // Shopee/TikTok/retail — see isOmnichannelOutbound) go through LedgerDAO so the
            // deduction is paired with a proper inventory_ledger entry; anything else uses the
            // plain deductShippedInventory path that existed before.
            OutboundOrder order = outboundDAO.findById(outboundId);
            if (order != null) {
                boolean isOmni = isOmnichannelOutbound(outboundId);
                if (isOmni) {
                    String outboundCode = order.getOutboundCode() != null ? order.getOutboundCode() : "SOUT-OUT-" + outboundId;
                    boolean ledgerOk = new LedgerDAO().approveDocument(outboundCode, "Phiếu Xuất Kho", 1);
                    if (!ledgerOk) {
                        log.error("SHIPPED: LedgerDAO.approveDocument failed outboundId={} code={} — status already SHIPPED, "
                            + "inventory/ledger entry NOT recorded, needs manual reconciliation via /business/ledger",
                            outboundId, outboundCode);
                    }
                } else if (order.getItems() != null) {
                    for (OutboundItem item : order.getItems()) {
                        java.math.BigDecimal qty = item.getQty();
                        if (qty != null && qty.compareTo(java.math.BigDecimal.ZERO) > 0) {
                            boolean ok = inventoryDAO.deductShippedInventory(
                                item.getProductId(), order.getWarehouseId(), qty);
                            if (!ok) {
                                log.warn("SHIPPED: deduct thất bại cho productId={} qty={} (tồn không đủ)",
                                    item.getProductId(), qty);
                            }
                        }
                    }
                }

                // Sync sales order status to SHIPPED
                if (order.getOrderCode() != null) {
                    new com.wms.dao.OrderDAO().updateOrderStatus(order.getOrderCode(), "SHIPPED");

                    // Sync status and stock to Website storefront — previously unreachable for
                    // Website orders because isOmnichannelOutbound() short-circuited this whole
                    // method into an early return before it ever got here (see compareAndSetStatus
                    // comment above).
                    Order salesOrder = orderDAO.findByOrderCode(order.getOrderCode());
                    if (salesOrder != null && "WEBSITE".equalsIgnoreCase(salesOrder.getChannel())) {
                        syncOrderStatusToWebsite(salesOrder.getOrderCode(), "SHIPPED", salesOrder.getTrackingNo());

                        com.wms.service.website.WebsiteProductService webProdService = new com.wms.service.website.WebsiteProductService();
                        com.wms.model.Channel webChannel = new com.wms.dao.ChannelDAO().findByPlatform("Website");
                        if (webChannel != null && webChannel.isActive() && order.getItems() != null) {
                            for (OutboundItem item : order.getItems()) {
                                int currentStock = inventoryDAO.getTotalAvailableStock(item.getProductId());
                                webProdService.syncStock(webChannel, item.getProductId(), currentStock);
                            }
                        }
                    }
                }
            }
            outboundDAO.createDeliveryNote(outboundId, userId);
        }

        log.info("Outbound status updated: outboundId={} status={}", outboundId, newStatus);
        return StatusUpdateResult.success("Cập nhật trạng thái phiếu xuất thành '" + newStatus + "' thành công!");
    }

    /**
     * Delegates to OutboundDAO to check whether the outbound order's associated
     * sales channel is an omni-channel platform (Lazada, Shopee, TikTok, Website).
     * Used to decide whether to auto-approve the ledger entry on SHIPPED.
     */
    public boolean isOmnichannelOutbound(int outboundId) {
        try {
            return outboundDAO.isOmnichannelOutbound(outboundId);
        } catch (Exception e) {
            log.warn("isOmnichannelOutbound check failed for outboundId={}: {}", outboundId, e.getMessage());
            return false;
        }
    }

    public CancelResult cancel(int outboundId) {
        OutboundOrder existing = outboundDAO.findById(outboundId);
        if (existing == null) {
            log.warn("Outbound cancel failed: not found outboundId={}", outboundId);
            return CancelResult.failure("Phiếu xuất không tồn tại.");
        }
        if (OutboundOrder.STATUS_SHIPPED.equals(existing.getStatus())
            || OutboundOrder.STATUS_CANCELLED.equals(existing.getStatus())) {
            log.warn("Outbound cancel rejected: bad status outboundId={} currentStatus={}", outboundId, existing.getStatus());
            return CancelResult.failure("Không thể hủy phiếu ở trạng thái '" + existing.getStatus() + "'.");
        }

        // Release soft-allocate for each item before cancelling.
        // Without this, qty_available stays decremented and stock appears "stuck".
        if (existing.getItems() != null) {
            for (OutboundItem item : existing.getItems()) {
                java.math.BigDecimal qty = item.getQty();
                if (qty != null && qty.compareTo(java.math.BigDecimal.ZERO) > 0) {
                    boolean released = inventoryDAO.releaseSoftAllocateInventory(
                        item.getProductId(), existing.getWarehouseId(), qty);
                    if (!released) {
                        log.warn("cancel: releaseSoftAllocate thất bại cho productId={} qty={}",
                            item.getProductId(), qty);
                        // Không fail cả cancel, chỉ log để staff kiểm tra tay
                    }
                }
            }
        }

        boolean cancelled = outboundDAO.updateStatus(outboundId, OutboundOrder.STATUS_CANCELLED);
        return cancelled ? CancelResult.success("Đã hủy phiếu xuất kho.") : CancelResult.failure("Không thể cập nhật trạng thái hủy.");
    }

    private void updateMockWaybillCode(int orderId, String trackingNo, String carrierName) {
        String sql = "UPDATE order_shipping_details SET waybill_code = ?, courier_name = ?, shipping_status = 'PENDING', updated_at = CURRENT_TIMESTAMP WHERE order_id = ?";
        try (java.sql.Connection conn = com.wms.util.DBConnection.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, trackingNo);
            ps.setString(2, carrierName);
            ps.setInt(3, orderId);
            ps.executeUpdate();
        } catch (java.sql.SQLException e) {
            log.error("Failed to update waybill_code in order_shipping_details for orderId=" + orderId, e);
        }
    }

    /**
     * Pushes an order status change to the Website storefront (PUT /api/v1/orders/{ref}/status).
     * Called at every outbound transition that changes the sales order's status for a
     * WEBSITE-channel order (HANDED_OVER → "PACKED", SHIPPED → "SHIPPED") — previously only
     * SHIPPED pushed, so the customer's order-history list (which reads a local cache and
     * doesn't live-refresh, unlike the order-detail page) never reflected "đang đóng gói".
     */
    private void syncOrderStatusToWebsite(String orderCode, String status, String trackingNo) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            java.util.Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("status", status);
            payload.put("tracking_number", trackingNo);
            String json = mapper.writeValueAsString(payload);

            WebsiteHttpClient client = new WebsiteHttpClient();
            client.put("/api/v1/orders/" + orderCode + "/status", json);
        } catch (Exception e) {
            log.error("Failed to sync order status '" + status + "' to Website for orderCode=" + orderCode, e);
        }
    }

    public boolean isValidStatus(String status) {
        return OutboundOrder.STATUS_PENDING.equals(status)
            || OutboundOrder.STATUS_PACKED.equals(status)
            || OutboundOrder.STATUS_HANDED_OVER.equals(status)
            || OutboundOrder.STATUS_SHIPPED.equals(status)
            || OutboundOrder.STATUS_CANCELLED.equals(status);
    }

    /**
     * Auto-creates an outbound order from an approved sales order.
     * Also soft-allocates inventory for each order item.
     *
     * @param orderCode    mã đơn hàng sales
     * @param warehouseId  kho nhận xử lý
     * @param userId       userId của người thao tác (từ session)
     * @param userRole     role của người thao tác ("SALES_STAFF" | "WAREHOUSE_STAFF" | ...)
     */
    public void autoCreateFromOrder(String orderCode, int warehouseId, int userId, String userRole) {
        Order order = orderDAO.findByOrderCode(orderCode);
        if (order == null) {
            log.warn("autoCreateFromOrder: order not found orderCode={}", orderCode);
            return;
        }

        // Xác định ai là người tạo phiếu xuất:
        //   - WAREHOUSE_STAFF thao tác trực tiếp → dùng chính họ
        //   - SALES_STAFF (hoặc role khác) approve đơn → giao cho warehouse staff của kho
        int resolvedUserId;
        if ("WAREHOUSE_STAFF".equals(userRole) && userId > 0 && userId != 1) {
            resolvedUserId = userId;
            log.info("autoCreateFromOrder: warehouse staff direct action userId={}", resolvedUserId);
        } else {
            try {
                User whStaff = userDAO.findPrimaryWarehouseStaff(warehouseId);
                if (whStaff != null) {
                    resolvedUserId = whStaff.getUserId();
                    log.info("autoCreateFromOrder: resolved warehouse staff userId={} for warehouseId={} (role={})",
                            resolvedUserId, warehouseId, userRole);
                } else {
                    resolvedUserId = (userId > 0 && userId != 1) ? userId : 1;
                    log.warn("autoCreateFromOrder: no warehouse staff for warehouseId={}, fallback userId={}",
                            warehouseId, resolvedUserId);
                }
            } catch (Exception ex) {
                resolvedUserId = (userId > 0 && userId != 1) ? userId : 1;
                log.error("autoCreateFromOrder: failed to resolve warehouse staff for warehouseId={}", warehouseId, ex);
            }
        }

        // Idempotency guard: skip if a non-cancelled outbound already exists for this order.
        // Prevents duplicate outbound sheets when Sales Staff approves the same order more than once
        // (e.g. due to a UI bug that kept showing it as pending after the first approval).
        if (outboundDAO.hasActiveOutboundForOrder(order.getOrderId())) {
            log.warn("autoCreateFromOrder: skipped — active outbound already exists for orderCode={} orderId={}",
                    orderCode, order.getOrderId());
            return;
        }

        OutboundOrder outbound = new OutboundOrder();
        outbound.setOrderId(order.getOrderId());
        outbound.setWarehouseId(warehouseId);
        outbound.setStatus(OutboundOrder.STATUS_PENDING);
        outbound.setNotes("Tạo tự động từ đơn hàng " + orderCode);
        outbound.setCreatedAt(java.time.LocalDateTime.now());

        outbound.setCreatedBy(resolvedUserId);
        int outboundId = insertWithRetry(outbound);
        if (outboundId <= 0) {
            log.error("autoCreateFromOrder: failed to insert outbound for orderCode={}", orderCode);
            return;
        }

        List<OrderItem> items = orderDAO.findItemsByOrderId(order.getOrderId());
        for (OrderItem item : items) {
            OutboundItem oi = new OutboundItem();
            oi.setOutboundId(outboundId);
            oi.setProductId(item.getProductId());
            oi.setQty(java.math.BigDecimal.valueOf(item.getQuantity()));
            oi.setPickedQty(java.math.BigDecimal.ZERO);
            outboundDAO.insertItem(oi);

            // Soft-allocate inventory for this item
            if (warehouseId > 0) {
                inventoryDAO.softAllocateInventory(item.getProductId(), warehouseId, item.getQuantity());
            }
        }

        log.info("autoCreateFromOrder: created outboundId={} from orderCode={}", outboundId, orderCode);
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

    public static class StatusUpdateResult {
        private final boolean success;
        private final String message;

        private StatusUpdateResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public static StatusUpdateResult success(String message) {
            return new StatusUpdateResult(true, message);
        }

        public static StatusUpdateResult failure(String message) {
            return new StatusUpdateResult(false, message);
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }

    public static class CancelResult {
        private final boolean success;
        private final String message;

        private CancelResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public static CancelResult success(String message) {
            return new CancelResult(true, message);
        }

        public static CancelResult failure(String message) {
            return new CancelResult(false, message);
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }

    /**
     * Releases inventory allocations for a cancelled outbound order.
     * This restores the temporarily held stock back to available inventory.
     *
     * Idempotent: claimRestock() atomically claims the restock (restocked_at NULL -> set).
     * A repeat call (stale UI, cleared localStorage, another browser) finds it already claimed
     * and returns false instead of releasing the same allocation a second time.
     */
    public boolean releaseAllocationsForOutbound(int outboundId, Integer userId) {
        try {
            OutboundOrder outbound = outboundDAO.findById(outboundId);
            if (outbound == null) {
                log.warn("releaseAllocationsForOutbound: outbound not found: {}", outboundId);
                return false;
            }

            if (!outboundDAO.claimRestock(outboundId, userId)) {
                log.info("releaseAllocationsForOutbound: already restocked, skipping duplicate release outboundId={}", outboundId);
                return false;
            }

            List<OutboundItem> items = outboundDAO.findItemsByOutboundId(outboundId);
            for (OutboundItem item : items) {
                // Release the full allocated quantity (not just picked qty)
                double qtyToRelease = item.getQty().doubleValue();
                if (qtyToRelease > 0) {
                    inventoryDAO.releaseSoftAllocateInventory(item.getProductId(), outbound.getWarehouseId(), java.math.BigDecimal.valueOf(qtyToRelease));
                    log.info("Released {} units of product {} from warehouse {} for cancelled outbound {}",
                            qtyToRelease, item.getProductId(), outbound.getWarehouseId(), outboundId);
                }
            }
            return true;
        } catch (Exception e) {
            log.error("releaseAllocationsForOutbound failed for outbound {}: {}", outboundId, e.getMessage(), e);
            return false;
        }
    }
}
