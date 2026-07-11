package com.wms.service.sales;

import com.wms.dao.FulfillmentRequestDAO;
import com.wms.dao.InventoryDAO;
import com.wms.dao.OrderDAO;
import com.wms.dao.WarehouseDAO;
import com.wms.model.FulfillmentRequest;
import com.wms.model.Order;
import com.wms.model.OrderItem;
import com.wms.model.Product;
import com.wms.model.Warehouse;
import com.wms.service.lazada.LazadaShipmentService;
import com.wms.service.warehouse.OutboundService;
import com.wms.service.common.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.wms.dao.CategoryDAO;
import com.wms.model.Category;
import java.util.ArrayList;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderDAO orderDAO = new OrderDAO();
    private final WarehouseDAO warehouseDAO = new WarehouseDAO();
    private final OutboundService outboundService = new OutboundService();
    private final InventoryDAO inventoryDAO = new InventoryDAO();
    private final NotificationService notificationService = new NotificationService();

    public List<Order> findAllOrders() {
        return orderDAO.getAllOrders();
    }

    public boolean updateOrderStatusAndWarehouse(String orderCode, String status,
                                                  int warehouseId, String reviewNote) {
        return orderDAO.updateOrderStatusAndWarehouse(orderCode, status, warehouseId, reviewNote);
    }

    public boolean updateOrderTrackingNo(String orderCode, String trackingNo) {
        return orderDAO.updateOrderTrackingNo(orderCode, trackingNo);
    }

    public boolean updateOrderStatus(String orderCode, String status) {
        return orderDAO.updateOrderStatus(orderCode, status);
    }

    public boolean updateOrderRMA(String orderCode, String status, String rmaReason,
                                   String rmaPhysicalStatus, String rmaPlatformStatus) {
        return orderDAO.updateOrderRMA(orderCode, status, rmaReason, rmaPhysicalStatus, rmaPlatformStatus);
    }

    public boolean updateOrderDispute(String orderCode, String status, String video,
                                      String note, String platformStatus) {
        return orderDAO.updateOrderDispute(orderCode, status, video, note, platformStatus);
    }

    public ActionResult handleAction(String action, String orderCode, String warehouseName,
                                      String note, String trackingNo, String video,
                                      String rmaReason, String rmaPhysicalStatus,
                                      String rmaPlatformStatus, String platformStatus,
                                      String disputeNote, int userId, String userRole) {
        log.info("Order action: action={} orderCode={}", action, orderCode);
        switch (action) {
            case "approve": {
                int warehouseId = resolveWarehouseNameToId(warehouseName);
                if (warehouseId <= 0) {
                    log.warn("Approve order failed: warehouse not found orderCode={} warehouse={}", orderCode, warehouseName);
                    return ActionResult.failure("Không tìm thấy kho: " + warehouseName);
                }
                // Validate tồn kho trước khi duyệt (fix bug "hết hàng vẫn chỉ định kho được")
                Order order = orderDAO.findByOrderCode(orderCode);
                if (order == null) {
                    log.warn("Approve order failed: order not found orderCode={}", orderCode);
                    return ActionResult.failure("Không tìm thấy đơn hàng: " + orderCode);
                }
                List<OrderItem> items = orderDAO.findItemsByOrderId(order.getOrderId());
                StringBuilder insufficientSkus = new StringBuilder();
                int insufficientCount = 0;
                for (OrderItem item : items) {
                    int available = inventoryDAO.getAvailableStock(item.getProductId(), warehouseId);
                    if (available < item.getQuantity()) {
                        insufficientCount++;
                        insufficientSkus.append(String.format(
                            "%n  - SKU %s: cần %d, chỉ còn %d",
                            item.getSkuCode(), item.getQuantity(), available));
                    }
                }
                if (insufficientSkus.length() > 0) {
                    // Bài 3: chỉ fail khi TẤT CẢ SKU đều hết; nếu chỉ một vài SKU thiếu thì tách đơn
                    if (insufficientCount >= items.size()) {
                        log.warn("Approve order failed: ALL items out of stock orderCode={} warehouseId={} details={}",
                            orderCode, warehouseId, insufficientSkus);
                        return ActionResult.failure(
                            "Không thể duyệt đơn: tất cả SKU đều hết hàng tại kho đã chọn." + insufficientSkus);
                    }
                    log.info("Approve order with partial stock: orderCode={} warehouseId={} insufficient={}/{} details={}",
                        orderCode, warehouseId, insufficientCount, items.size(), insufficientSkus);
                    String partialNote = "Đơn tách: " + insufficientCount + "/" + items.size()
                        + " SKU hết hàng chờ nhập. Chi tiết:" + insufficientSkus;
                    boolean ok = orderDAO.updateOrderStatusAndWarehouse(orderCode, "PICKING", warehouseId, partialNote);
                    if (ok) {
                        autoCreateFulfillmentRequest(orderCode, warehouseId);
                        return ActionResult.success(
                            "Đã duyệt phần có hàng. " + insufficientCount
                            + " SKU hết hàng, chờ nhập thêm (xem ghi chú đơn).");
                    } else {
                        log.error("Order approve DAO failed: orderCode={}", orderCode);
                        return ActionResult.failure("Không thể duyệt đơn hàng.");
                    }
                }
                String defaultNote = (note == null || note.trim().isEmpty())
                    ? "Đơn hàng đã được duyệt và chuyển về kho." : note.trim();
                boolean ok = orderDAO.updateOrderStatusAndWarehouse(orderCode, "PICKING", warehouseId, defaultNote);
                if (ok) {
                    log.info("Order approved: orderCode={} warehouseId={}", orderCode, warehouseId);
                    // Tự động tạo FulfillmentRequest để kho thấy lệnh xuất ngay
                    autoCreateFulfillmentRequest(orderCode, warehouseId);
                    // Notify sales staff who owns the order
                    if (order != null && order.getCreatedBy() != null) {
                        notificationService.notifyOrderStatus(order.getCreatedBy(),
                                order.getOrderId(), orderCode, "PENDING", "PICKING");
                    }
                    return ActionResult.success("Duyệt đơn hàng thành công.");
                } else {
                    log.error("Order approve DAO failed: orderCode={}", orderCode);
                    return ActionResult.failure("Không thể duyệt đơn hàng.");
                }
            }
            case "reject": {
                // Bắt buộc lý do từ chối (fix xung đột giữa notification và DB)
                if (note == null || note.trim().isEmpty()) {
                    log.warn("Reject order failed: empty reason orderCode={}", orderCode);
                    return ActionResult.failure("Vui lòng nhập lý do từ chối đơn hàng.");
                }
                if (note.trim().length() < 10) {
                    log.warn("Reject order failed: reason too short ({} chars) orderCode={}", note.trim().length(), orderCode);
                    return ActionResult.failure("Lý do từ chối phải có ít nhất 10 ký tự để phục vụ truy vết.");
                }
                String trimmedNote = note.trim();
                // orders.order_status ENUM only allows: PENDING, CONFIRMED, PICKING,
                // PACKED, SHIPPED, DELIVERED, CANCELLED, RETURNED. "REJECTED" is not
                // in the ENUM and would cause MySQL 1265 Data truncated.
                // Use CANCELLED + persist reason in review_note.
                boolean ok = orderDAO.updateOrderStatusAndWarehouse(orderCode, "CANCELLED", 0, trimmedNote);
                if (ok) {
                    log.info("Order rejected: orderCode={} reason={}", orderCode, trimmedNote);
                    // Hủy fulfillment + outbound để warehouse staff không còn thấy phiếu.
                    cascadeCancelOrder(orderCode);
                    return ActionResult.success("Từ chối đơn hàng thành công.");
                } else {
                    log.error("Order reject DAO failed: orderCode={}", orderCode);
                    return ActionResult.failure("Không thể từ chối đơn hàng.");
                }
            }
            case "generate_tracking": {
                Order current = orderDAO.findByOrderCode(orderCode);
                if (current == null) {
                    log.warn("Generate tracking failed: order not found orderCode={}", orderCode);
                    return ActionResult.failure("Không tìm thấy đơn hàng: " + orderCode);
                }

                // Lazada end-to-end: if the order belongs to a Lazada channel,
                // call Lazada's /order/fulfill/pack so the marketplace itself
                // allocates the tracking number. For other channels (Shopee,
                // TikTok, local) we keep the local generation flow.
                if ("LAZADA".equalsIgnoreCase(current.getChannel())
                        && current.getChannelId() > 0) {
                    LazadaShipmentService.ShipmentResult r =
                            new LazadaShipmentService().packAndAllocate(current);
                    if (!r.success) {
                        log.error("Lazada pack failed: orderCode={} err={}",
                                orderCode, r.errorMessage);
                        return ActionResult.failure(
                                "Lazada cấp vận đơn thất bại: " + r.errorMessage);
                    }
                    // Auto-create outbound order upon successful Lazada tracking generation
                    try {
                        outboundService.autoCreateFromOrder(orderCode, current.getWarehouseId(), userId, userRole);
                    } catch (Exception ex) {
                        log.error("Failed to auto-create outbound for Lazada order " + orderCode, ex);
                    }
                    Map<String, Object> data = new HashMap<>();
                    data.put("trackingNo", r.trackingNo);
                    data.put("packageId", r.packageId);
                    return ActionResult.success(
                            "Lazada đã cấp vận đơn " + r.trackingNo
                                    + " (package " + r.packageId + ")", data);
                }

                // Server-side generate nếu client không truyền trackingNo
                String finalTracking = (trackingNo == null || trackingNo.trim().isEmpty())
                    ? generateTrackingNo(current)
                    : trackingNo.trim();
                if (orderDAO.existsByTrackingNo(finalTracking)) {
                    log.warn("Generate tracking failed: duplicate trackingNo={} orderCode={}", finalTracking, orderCode);
                    return ActionResult.failure("Mã vận đơn đã tồn tại: " + finalTracking);
                }
                boolean ok = orderDAO.updateOrderTrackingNo(orderCode, finalTracking);
                if (ok) {
                    log.info("Tracking generated: orderCode={} trackingNo={}", orderCode, finalTracking);
                    // Auto-create outbound order upon successful non-Lazada tracking generation
                    try {
                        outboundService.autoCreateFromOrder(orderCode, current.getWarehouseId(), userId, userRole);
                    } catch (Exception ex) {
                        log.error("Failed to auto-create outbound for order " + orderCode, ex);
                    }
                    Map<String, Object> data = new HashMap<>();
                    data.put("trackingNo", finalTracking);
                    return ActionResult.success("Cập nhật tracking thành công.", data);
                } else {
                    log.error("Generate tracking DAO failed: orderCode={}", orderCode);
                    return ActionResult.failure("Không thể cập nhật tracking.");
                }
            }
            case "print_shipping": {
                boolean ok = orderDAO.updateOrderStatus(orderCode, "PACKED");
                if (ok) {
                    log.info("Shipping label printed: orderCode={}", orderCode);
                    return ActionResult.success("Cập nhật trạng thái đóng gói thành công.");
                } else {
                    log.error("Print shipping DAO failed: orderCode={}", orderCode);
                    return ActionResult.failure("Không thể cập nhật trạng thái.");
                }
            }
            case "confirm_delivered": {
                Order current = orderDAO.findByOrderCode(orderCode);
                if (current == null) {
                    log.warn("Confirm delivered failed: order not found orderCode={}", orderCode);
                    return ActionResult.failure("Không tìm thấy đơn hàng: " + orderCode);
                }
                if (current.getWebOrderRef() == null || current.getWebOrderRef().isBlank()) {
                    return ActionResult.failure("Xác nhận giao hàng thủ công chỉ áp dụng cho đơn Website. Đơn sàn TMĐT tự cập nhật qua webhook.");
                }
                if (!"SHIPPED".equalsIgnoreCase(current.getStatus())) {
                    return ActionResult.failure("Chỉ xác nhận giao hàng khi đơn đang ở trạng thái Đang vận chuyển.");
                }
                boolean ok = orderDAO.markDelivered(orderCode);
                if (ok) {
                    log.info("Order marked delivered manually: orderCode={} userId={}", orderCode, userId);
                    if (current.getCreatedBy() != null) {
                        notificationService.notifyOrderStatus(current.getCreatedBy(),
                                current.getOrderId(), orderCode, "SHIPPED", "DELIVERED");
                    }
                }
                return ok ? ActionResult.success("Xác nhận giao hàng thành công.")
                          : ActionResult.failure("Không thể cập nhật trạng thái.");
            }
            case "rts": {
                Order current = orderDAO.findByOrderCode(orderCode);
                if (current == null) {
                    log.warn("RTS failed: order not found orderCode={}", orderCode);
                    return ActionResult.failure("Không tìm thấy đơn hàng: " + orderCode);
                }
                if (!"LAZADA".equalsIgnoreCase(current.getChannel())) {
                    return ActionResult.failure("RTS chỉ hỗ trợ đơn Lazada.");
                }
                LazadaShipmentService.ShipmentResult r =
                        new LazadaShipmentService().readyToShip(current);
                if (!r.success) {
                    log.error("Lazada RTS failed: orderCode={} err={}", orderCode, r.errorMessage);
                    return ActionResult.failure("Lazada RTS thất bại: " + r.errorMessage);
                }
                log.info("Lazada RTS success: orderCode={} trackingNo={} packageId={}",
                        orderCode, r.trackingNo, r.packageId);
                Map<String, Object> data = new HashMap<>();
                data.put("trackingNo", r.trackingNo);
                data.put("packageId", r.packageId);
                return ActionResult.success(
                        "Lazada đã xác nhận giao hàng cho đơn vị vận chuyển (RTS) cho vận đơn " + r.trackingNo, data);
            }
            case "webhook": {
                String status = determineWebhookStatus(platformStatus);
                if (status == null) {
                    log.warn("Webhook status unknown: orderCode={} platformStatus={}", orderCode, platformStatus);
                    return ActionResult.failure("Trạng thái webhook không xác định: " + platformStatus);
                }
                log.info("Webhook processing: orderCode={} platformStatus={} mappedStatus={}", orderCode, platformStatus, status);
                // Bài 5: xử lý khách hủy đơn từ sàn (tránh kho đóng gói đơn ảo)
                if ("BUYER_CANCELLED".equalsIgnoreCase(status)) {
                    return handleBuyerCancellation(orderCode);
                }
                if ("PICKED_UP".equals(status) || "IN_TRANSIT".equals(status)) {
                    boolean ok = orderDAO.updateOrderStatus(orderCode, "SHIPPED");
                    return ok ? ActionResult.success("Cập nhật trạng thái vận chuyển thành công.")
                              : ActionResult.failure("Không thể cập nhật trạng thái.");
                } else if ("DELIVERED".equals(status)) {
                    boolean ok = orderDAO.updateOrderStatus(orderCode, "DELIVERED");
                    if (ok) {
                        // Notify sales staff: order delivered
                        Order o = orderDAO.findByOrderCode(orderCode);
                        if (o != null && o.getCreatedBy() != null) {
                            notificationService.notifyOrderStatus(o.getCreatedBy(),
                                    o.getOrderId(), orderCode, "SHIPPED", "DELIVERED");
                        }
                    }
                    return ok ? ActionResult.success("Cập nhật trạng thái giao hàng thành công.")
                              : ActionResult.failure("Không thể cập nhật trạng thái.");
                } else if ("RETURNED".equals(status)) {
                    // ENUM không có "RMA" → dùng "RETURNED" (đã có sẵn trong ENUM).
                    // Lưu ý: trạng thái RMA nghiệp vụ thể hiện qua note/rma_reason trong DB.
                    boolean ok = orderDAO.updateOrderRMA(orderCode, "RETURNED",
                        "Yêu cầu trả hàng qua webhook", "Chưa kiểm tra", "Đã hoàn trả");
                    return ok ? ActionResult.success("Xử lý trả hàng thành công.")
                              : ActionResult.failure("Không thể xử lý trả hàng.");
                } else if ("COMPLETED".equals(status)) {
                    // ENUM không có "COMPLETED" → dùng "DELIVERED" (đã có sẵn).
                    // Completed = delivered, nghiệp vụ tương đương.
                    boolean ok = orderDAO.updateOrderStatus(orderCode, "DELIVERED");
                    return ok ? ActionResult.success("Đơn hàng hoàn tất.")
                              : ActionResult.failure("Không thể cập nhật trạng thái.");
                }
                return ActionResult.failure("Không xử lý được trạng thái: " + status);
            }
            default:
                log.warn("Unknown order action: action={} orderCode={}", action, orderCode);
                return ActionResult.failure("Hành động không xác định: " + action);
        }
    }

    public int resolveWarehouseNameToId(String warehouseName) {
        if (warehouseName == null || warehouseName.trim().isEmpty()) {
            return 0;
        }
        List<Warehouse> warehouses = warehouseDAO.findAll();
        for (Warehouse w : warehouses) {
            if (w.getWarehouseName().equalsIgnoreCase(warehouseName.trim())) {
                return w.getWarehouseId();
            }
        }
        return 0;
    }

    private String determineWebhookStatus(String platformStatus) {
        if (platformStatus == null) return null;
        switch (platformStatus.toUpperCase()) {
            case "PICKUP":       return "PICKED_UP";
            case "PICKED_UP":    return "PICKED_UP";
            case "TRANSIT":
            case "IN_TRANSIT":   return "IN_TRANSIT";
            case "DELIVERED":    return "DELIVERED";
            case "RETURN":
            case "RETURNED":     return "RETURNED";
            case "COMPLETED":    return "COMPLETED";
            // Bài 5: TikTok gửi "BUYER_CANCELLED", Shopee/Lazada gửi "CANCELLED"
            case "CANCELLED":
                return "BUYER_CANCELLED";
            default: return null;
        }
    }

    /**
     * Bài 5: xử lý khi khách hủy đơn từ sàn.
     * - Đã SHIPPED/DELIVERED: từ chối (hàng đã đi)
     * - Còn lại: hủy đơn + release soft-allocate (cộng lại qty_available đã trừ khi duyệt)
     */
    private ActionResult handleBuyerCancellation(String orderCode) {
        Order order = orderDAO.findByOrderCode(orderCode);
        if (order == null) {
            log.warn("handleBuyerCancellation: order not found orderCode={}", orderCode);
            return ActionResult.failure("Không tìm thấy đơn: " + orderCode);
        }

        String currentStatus = order.getStatus();
        if ("SHIPPED".equalsIgnoreCase(currentStatus)
            || "DELIVERED".equalsIgnoreCase(currentStatus)) {
            log.warn("handleBuyerCancellation: order already shipped orderCode={} status={}",
                orderCode, currentStatus);
            return ActionResult.failure(
                "Đơn đã xuất kho (trạng thái " + currentStatus + "), không thể hủy qua webhook. Liên hệ kho.");
        }

        String reason = "Khách hàng hủy đơn qua webhook sàn.";
        boolean ok = orderDAO.updateOrderStatusAndWarehouse(
            orderCode, "CANCELLED", 0, reason);
        if (!ok) {
            log.error("handleBuyerCancellation: DAO update failed orderCode={}", orderCode);
            return ActionResult.failure("Không thể hủy đơn trong database.");
        }

        if (order.getWarehouseId() > 0) {
            List<OrderItem> items = orderDAO.findItemsByOrderId(order.getOrderId());
            int released = 0;
            for (OrderItem item : items) {
                try {
                    boolean releasedOk = inventoryDAO.releaseSoftAllocateInventory(
                        item.getProductId(),
                        order.getWarehouseId(),
                        BigDecimal.valueOf(item.getQuantity()));
                    if (releasedOk) released++;
                } catch (Exception e) {
                    log.warn("handleBuyerCancellation: release stock failed orderCode={} productId={} qty={}: {}",
                        orderCode, item.getProductId(), item.getQuantity(), e.getMessage());
                }
            }
            log.info("handleBuyerCancellation: released {}/{} items for order {}",
                released, items.size(), orderCode);
        }

        // Hủy luôn fulfillment + outbound để warehouse staff không còn thấy phiếu.
        cascadeCancelOrder(orderCode);

        log.info("Order cancelled by buyer via webhook: orderCode={}", orderCode);
        // Notify sales staff: order cancelled by buyer
        if (order.getCreatedBy() != null) {
            notificationService.notifyOrderStatus(order.getCreatedBy(),
                    order.getOrderId(), orderCode, order.getStatus(), "CANCELLED");
        }
        return ActionResult.success("Đã hủy đơn theo yêu cầu khách hàng, tồn kho đã được trả lại.");
    }

    /**
     * Hàm chung: hủy mọi thứ liên quan đến đơn hàng (fulfillment + outbound).
     * Gọi từ reject(), handleBuyerCancellation(), và các luồng Lazada/webhook.
     */
    private void cascadeCancelOrder(String orderCode) {
        com.wms.dao.FulfillmentRequestDAO frDAO = new com.wms.dao.FulfillmentRequestDAO();
        frDAO.cancelByOrderId(orderCode);
        com.wms.dao.OutboundDAO outboundDAO = new com.wms.dao.OutboundDAO();
        outboundDAO.cancelByOrderId(orderCode);
    }

    /**
     * Tạo FulfillmentRequest tự động khi Sales duyệt đơn,
     * sau đó ngay lập tức convert thành OutboundOrder để kho thấy trong tabs.
     * Idempotent: nếu đã có OutboundOrder active thì bỏ qua.
     */
    private void autoCreateFulfillmentRequest(String orderCode, int warehouseId) {
        try {
            FulfillmentRequestDAO frDAO = new FulfillmentRequestDAO();

            // Tạo FulfillmentRequest (ghi log lịch sử), idempotent
            if (!frDAO.existsPendingForOrder(orderCode)) {
                String requestId = "FR-" + System.currentTimeMillis() + "-" + orderCode;
                FulfillmentRequest fr = new FulfillmentRequest();
                fr.setRequestId(requestId);
                fr.setOrderId(orderCode);
                fr.setWarehouseId(warehouseId);
                fr.setStatus(FulfillmentRequest.STATUS_PENDING);
                fr.setAutoCreated(true);
                boolean created = frDAO.insert(fr);
                if (created) {
                    log.info("FulfillmentRequest created: requestId={} orderCode={} warehouseId={}", requestId, orderCode, warehouseId);
                    // Ngay lập tức convert → tạo OutboundOrder để kho thấy trong tabs
                    frDAO.updateStatus(requestId, FulfillmentRequest.STATUS_CONVERTED);
                } else {
                    log.warn("Failed to create FulfillmentRequest for orderCode={}", orderCode);
                    return;
                }
            } else {
                log.info("FulfillmentRequest already exists for orderCode={}", orderCode);
            }

            // Tạo OutboundOrder (idempotent — OutboundService tự guard trùng lặp)
            new OutboundService().autoCreateFromOrder(orderCode, warehouseId, 1, "SALES_STAFF");
            log.info("OutboundOrder auto-created for orderCode={} warehouseId={}", orderCode, warehouseId);

        } catch (Exception e) {
            log.error("autoCreateFulfillmentRequest failed for orderCode={}", orderCode, e);
        }
    }

    /**
     * Sinh mã vận đơn phía server (idempotent kết hợp existsByTrackingNo).
     * Format: {PREFIX}-{YYYYMMDD}-{9 chữ số ngẫu nhiên}
     * PREFIX theo channel: Shopee→SPX, Lazada→LZE, TikTok→TKT, khác→VTP
     */
    private String generateTrackingNo(Order order) {
        String channel = order.getChannel() == null ? "" : order.getChannel();
        String prefix;
        if (channel.equalsIgnoreCase("Shopee")) {
            prefix = "SPX";
        } else if (channel.equalsIgnoreCase("Lazada")) {
            prefix = "LZE";
        } else if (channel.equalsIgnoreCase("TikTok")) {
            prefix = "TKT";
        } else {
            prefix = "VTP";
        }
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        // 9 chữ số từ currentTimeMillis (cực hiếm trùng; thêm existsByTrackingNo check)
        long rand = (System.nanoTime() ^ System.currentTimeMillis()) % 1_000_000_000L;
        if (rand < 0) rand = -rand;
        return String.format("%s-%s-%09d", prefix, datePart, rand);
    }

    // ── Dashboard KPI methods ──────────────────────────────────

    public BigDecimal getTotalRevenue(String period) {
        LocalDateRange range = parsePeriod(period);
        if (range == null) return BigDecimal.ZERO;
        String sql = "SELECT COALESCE(SUM(total_amount), 0) FROM orders WHERE created_at >= ? AND created_at < ? AND status != 'CANCELLED'";
        try (Connection conn = com.wms.util.DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(range.start));
            ps.setTimestamp(2, Timestamp.valueOf(range.end));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getBigDecimal(1);
            }
        } catch (SQLException e) {
            log.warn("getTotalRevenue failed for period={}", period, e);
        }
        return BigDecimal.ZERO;
    }

    public int getTotalOrders(String period) {
        LocalDateRange range = parsePeriod(period);
        if (range == null) return 0;
        String sql = "SELECT COUNT(*) FROM orders WHERE created_at >= ? AND created_at < ? AND status != 'CANCELLED'";
        try (Connection conn = com.wms.util.DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(range.start));
            ps.setTimestamp(2, Timestamp.valueOf(range.end));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            log.warn("getTotalOrders failed for period={}", period, e);
        }
        return 0;
    }

    public BigDecimal getAvgOrderValue(String period) {
        BigDecimal total = getTotalRevenue(period);
        int count = getTotalOrders(period);
        if (count == 0) return BigDecimal.ZERO;
        return total.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }

    public BigDecimal getReturnRate(String period) {
        LocalDateRange range = parsePeriod(period);
        if (range == null) return BigDecimal.ZERO;
        String sql = "SELECT COUNT(*) FROM orders WHERE created_at >= ? AND created_at < ? AND status IN ('RETURNED','RMA') AND status != 'CANCELLED'";
        String totalSql = "SELECT COUNT(*) FROM orders WHERE created_at >= ? AND created_at < ? AND status != 'CANCELLED'";
        try (Connection conn = com.wms.util.DBConnection.getConnection()) {
            int returned = 0, total = 0;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setTimestamp(1, Timestamp.valueOf(range.start));
                ps.setTimestamp(2, Timestamp.valueOf(range.end));
                try (ResultSet rs = ps.executeQuery()) { if (rs.next()) returned = rs.getInt(1); }
            }
            try (PreparedStatement ps = conn.prepareStatement(totalSql)) {
                ps.setTimestamp(1, Timestamp.valueOf(range.start));
                ps.setTimestamp(2, Timestamp.valueOf(range.end));
                try (ResultSet rs = ps.executeQuery()) { if (rs.next()) total = rs.getInt(1); }
            }
            if (total == 0) return BigDecimal.ZERO;
            return BigDecimal.valueOf(returned).multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
        } catch (SQLException e) {
            log.warn("getReturnRate failed for period={}", period, e);
        }
        return BigDecimal.ZERO;
    }

    public BigDecimal getRevenueGrowth(String period) {
        LocalDateRange range = parsePeriod(period);
        if (range == null) return BigDecimal.ZERO;
        long days = java.time.temporal.ChronoUnit.DAYS.between(range.start.toLocalDate(), range.end.toLocalDate());
        LocalDateTime prevStart = range.start.minusDays(days);
        LocalDateTime prevEnd = range.start;

        BigDecimal thisRev = getTotalRevenueForRange(range.start, range.end);
        BigDecimal prevRev = getTotalRevenueForRange(prevStart, prevEnd);

        if (prevRev.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return thisRev.subtract(prevRev)
                .divide(prevRev, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal getOrdersGrowth(String period) {
        LocalDateRange range = parsePeriod(period);
        if (range == null) return BigDecimal.ZERO;
        long days = java.time.temporal.ChronoUnit.DAYS.between(range.start.toLocalDate(), range.end.toLocalDate());
        LocalDateTime prevStart = range.start.minusDays(days);
        LocalDateTime prevEnd = range.start;

        int thisCount = getOrdersCountForRange(range.start, range.end);
        int prevCount = getOrdersCountForRange(prevStart, prevEnd);

        if (prevCount == 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(thisCount - prevCount)
                .divide(BigDecimal.valueOf(prevCount), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal getAvgOrderGrowth(String period) {
        LocalDateRange range = parsePeriod(period);
        if (range == null) return BigDecimal.ZERO;
        long days = java.time.temporal.ChronoUnit.DAYS.between(range.start.toLocalDate(), range.end.toLocalDate());
        LocalDateTime prevStart = range.start.minusDays(days);
        LocalDateTime prevEnd = range.start;

        BigDecimal thisRev = getTotalRevenueForRange(range.start, range.end);
        int thisCount = getOrdersCountForRange(range.start, range.end);
        BigDecimal thisAvg = thisCount == 0 ? BigDecimal.ZERO : thisRev.divide(BigDecimal.valueOf(thisCount), 4, RoundingMode.HALF_UP);

        BigDecimal prevRev = getTotalRevenueForRange(prevStart, prevEnd);
        int prevCount = getOrdersCountForRange(prevStart, prevEnd);
        BigDecimal prevAvg = prevCount == 0 ? BigDecimal.ZERO : prevRev.divide(BigDecimal.valueOf(prevCount), 4, RoundingMode.HALF_UP);

        if (prevAvg.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return thisAvg.subtract(prevAvg)
                .divide(prevAvg, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal getReturnRateGrowth(String period) {
        LocalDateRange range = parsePeriod(period);
        if (range == null) return BigDecimal.ZERO;
        long days = java.time.temporal.ChronoUnit.DAYS.between(range.start.toLocalDate(), range.end.toLocalDate());
        LocalDateTime prevStart = range.start.minusDays(days);
        LocalDateTime prevEnd = range.start;

        BigDecimal thisRate = getReturnRate(period);

        int prevReturned = getReturnedCountForRange(prevStart, prevEnd);
        int prevTotal = getOrdersCountForRange(prevStart, prevEnd);
        BigDecimal prevRate = prevTotal == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(prevReturned).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(prevTotal), 4, RoundingMode.HALF_UP);

        if (prevRate.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return thisRate.subtract(prevRate)
                .divide(prevRate, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private int getOrdersCountForRange(LocalDateTime start, LocalDateTime end) {
        String sql = "SELECT COUNT(*) FROM orders WHERE created_at >= ? AND created_at < ? AND status != 'CANCELLED'";
        try (Connection conn = com.wms.util.DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(start));
            ps.setTimestamp(2, Timestamp.valueOf(end));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            log.warn("getOrdersCountForRange failed", e);
        }
        return 0;
    }

    private int getReturnedCountForRange(LocalDateTime start, LocalDateTime end) {
        String sql = "SELECT COUNT(*) FROM orders WHERE created_at >= ? AND created_at < ? AND status IN ('RETURNED','RMA') AND status != 'CANCELLED'";
        try (Connection conn = com.wms.util.DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(start));
            ps.setTimestamp(2, Timestamp.valueOf(end));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            log.warn("getReturnedCountForRange failed", e);
        }
        return 0;
    }

    private BigDecimal getTotalRevenueForRange(LocalDateTime start, LocalDateTime end) {
        String sql = "SELECT COALESCE(SUM(total_amount), 0) FROM orders WHERE created_at >= ? AND created_at < ? AND status != 'CANCELLED'";
        try (Connection conn = com.wms.util.DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(start));
            ps.setTimestamp(2, Timestamp.valueOf(end));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getBigDecimal(1);
            }
        } catch (SQLException e) {
            log.warn("getTotalRevenueForRange failed", e);
        }
        return BigDecimal.ZERO;
    }

    public List<Map<String, Object>> getDailyRevenueData(String period) {
        List<Map<String, Object>> list = new ArrayList<>();
        LocalDateRange range = parsePeriod(period);
        if (range == null) return list;

        // 1. Fetch active channel names
        List<String> activeChannels = new ArrayList<>();
        String channelSql = "SELECT channel_name FROM channels WHERE is_active = 1";
        try (Connection conn = com.wms.util.DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(channelSql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String name = rs.getString("channel_name");
                if (name != null && !activeChannels.contains(name)) {
                    activeChannels.add(name);
                }
            }
        } catch (SQLException e) {
            log.warn("Failed to fetch active channels for daily revenue alignment", e);
        }
        
        // Add fallback default channels if none are found in DB
        if (activeChannels.isEmpty()) {
            activeChannels.add("ONLINE");
            activeChannels.add("STORE");
            activeChannels.add("B2B");
        }
        if (!activeChannels.contains("Khác")) {
            activeChannels.add("Khác");
        }

        // 2. Query the date-channel grouped data
        String sql = "SELECT DATE(o.created_at) AS day, COALESCE(c.channel_name, o.channel, 'Khác') AS channel_name, SUM(o.total_amount) AS revenue "
                   + "FROM orders o "
                   + "LEFT JOIN channels c ON o.channel_id = c.channel_id "
                   + "WHERE o.created_at >= ? AND o.created_at < ? AND o.status != 'CANCELLED' "
                   + "GROUP BY DATE(o.created_at), COALESCE(c.channel_name, o.channel, 'Khác') "
                   + "ORDER BY day";

        Map<String, Map<String, BigDecimal>> dayChannelMap = new LinkedHashMap<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM");

        // Populate all dates in range with empty maps to ensure continuous timeline on chart
        LocalDate startLocalDate = range.start.toLocalDate();
        LocalDate endLocalDate = range.end.toLocalDate();
        for (LocalDate date = startLocalDate; date.isBefore(endLocalDate); date = date.plusDays(1)) {
            dayChannelMap.put(date.format(fmt), new HashMap<>());
        }

        try (Connection conn = com.wms.util.DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(range.start));
            ps.setTimestamp(2, Timestamp.valueOf(range.end));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Date d = rs.getDate("day");
                    if (d != null) {
                        String dateStr = d.toLocalDate().format(fmt);
                        String chName = rs.getString("channel_name");
                        BigDecimal rev = rs.getBigDecimal("revenue");
                        if (rev == null) rev = BigDecimal.ZERO;

                        Map<String, BigDecimal> chMap = dayChannelMap.computeIfAbsent(dateStr, k -> new HashMap<>());
                        chMap.put(chName, rev);
                    }
                }
            }
        } catch (SQLException e) {
            log.warn("getDailyRevenueData failed for period={}", period, e);
        }

        for (Map.Entry<String, Map<String, BigDecimal>> entry : dayChannelMap.entrySet()) {
            Map<String, Object> dayRow = new LinkedHashMap<>();
            dayRow.put("date", entry.getKey());
            Map<String, BigDecimal> chMap = entry.getValue();
            for (String ch : activeChannels) {
                dayRow.put(ch, chMap.getOrDefault(ch, BigDecimal.ZERO));
            }
            list.add(dayRow);
        }
        return list;
    }

    public Map<String, BigDecimal> getChannelRevenueData(String period) {
        Map<String, BigDecimal> data = new LinkedHashMap<>();
        LocalDateRange range = parsePeriod(period);
        if (range == null) return data;
        String sql = "SELECT COALESCE(c.channel_name, o.channel, 'Khác') AS channel_name, SUM(o.total_amount) AS revenue "
                   + "FROM orders o "
                   + "LEFT JOIN channels c ON o.channel_id = c.channel_id "
                   + "WHERE o.created_at >= ? AND o.created_at < ? AND o.status != 'CANCELLED' "
                   + "GROUP BY COALESCE(c.channel_name, o.channel, 'Khác') "
                   + "ORDER BY revenue DESC";
        try (Connection conn = com.wms.util.DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(range.start));
            ps.setTimestamp(2, Timestamp.valueOf(range.end));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    data.put(rs.getString("channel_name"), rs.getBigDecimal("revenue"));
                }
            }
        } catch (SQLException e) {
            log.warn("getChannelRevenueData failed for period={}", period, e);
        }
        return data;
    }

    public Map<String, BigDecimal> getCategoryRevenueData(String period) {
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        LocalDateRange range = parsePeriod(period);
        if (range == null) return result;

        Map<Integer, Category> categoryMap = new HashMap<>();
        try {
            CategoryDAO categoryDAO = new CategoryDAO();
            List<Category> allCategories = categoryDAO.findAll();
            for (Category c : allCategories) {
                categoryMap.put(c.getCategoryId(), c);
            }
        } catch (Exception e) {
            log.warn("Failed to load categories for root category resolution", e);
        }

        String sql = "SELECT p.category_id, SUM(oi.qty * oi.unit_price) AS revenue "
                   + "FROM order_items oi "
                   + "JOIN products p ON oi.product_id = p.product_id "
                   + "JOIN orders o ON oi.order_id = o.order_id "
                   + "WHERE o.created_at >= ? AND o.created_at < ? AND o.status != 'CANCELLED' "
                   + "GROUP BY p.category_id";

        Map<String, BigDecimal> aggregatedRevenue = new HashMap<>();
        for (Category c : categoryMap.values()) {
            if (c.getParentId() == null && c.getCategoryName() != null) {
                aggregatedRevenue.put(c.getCategoryName(), BigDecimal.ZERO);
            }
        }

        try (Connection conn = com.wms.util.DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(range.start));
            ps.setTimestamp(2, Timestamp.valueOf(range.end));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Integer catId = rs.getObject("category_id") != null ? rs.getInt("category_id") : null;
                    BigDecimal rev = rs.getBigDecimal("revenue");
                    if (rev == null) rev = BigDecimal.ZERO;

                    String rootName = getRootCategoryName(catId, categoryMap);
                    BigDecimal existing = aggregatedRevenue.getOrDefault(rootName, BigDecimal.ZERO);
                    aggregatedRevenue.put(rootName, existing.add(rev));
                }
            }
        } catch (SQLException e) {
            log.warn("getCategoryRevenueData failed for period={}", period, e);
        }

        List<Map.Entry<String, BigDecimal>> entryList = new ArrayList<>(aggregatedRevenue.entrySet());
        entryList.sort((e1, e2) -> e2.getValue().compareTo(e1.getValue()));
        for (Map.Entry<String, BigDecimal> entry : entryList) {
            result.put(entry.getKey(), entry.getValue());
        }

        return result;
    }

    private String getRootCategoryName(Integer categoryId, Map<Integer, Category> categoryMap) {
        if (categoryId == null) {
            return "Khác";
        }
        Category current = categoryMap.get(categoryId);
        if (current == null) {
            return "Khác";
        }
        int safety = 0;
        while (current.getParentId() != null && safety < 100) {
            Category parent = categoryMap.get(current.getParentId());
            if (parent == null) {
                break;
            }
            current = parent;
            safety++;
        }
        return current.getCategoryName() != null ? current.getCategoryName() : "Khác";
    }

    public Map<String, Integer> getOrderStatusCounts(String period) {
        Map<String, Integer> data = new LinkedHashMap<>();
        LocalDateRange range = parsePeriod(period);
        if (range == null) return data;
        String sql = "SELECT status, COUNT(*) AS cnt FROM orders WHERE created_at >= ? AND created_at < ? GROUP BY status ORDER BY cnt DESC";
        try (Connection conn = com.wms.util.DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(range.start));
            ps.setTimestamp(2, Timestamp.valueOf(range.end));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    data.put(rs.getString("status"), rs.getInt("cnt"));
                }
            }
        } catch (SQLException e) {
            log.warn("getOrderStatusCounts failed for period={}", period, e);
        }
        return data;
    }

    public List<Map<String, Object>> getOrderStatusBreakdown(String period) {
        List<Map<String, Object>> breakdown = new ArrayList<>();
        Map<String, Integer> counts = getOrderStatusCounts(period);
        int total = counts.values().stream().mapToInt(Integer::intValue).sum();

        int delivered = counts.getOrDefault("COMPLETED", 0) + counts.getOrDefault("DELIVERED", 0);
        int shipping = counts.getOrDefault("SHIPPED", 0) + counts.getOrDefault("PICKING", 0) + counts.getOrDefault("PACKED", 0);
        int pending = counts.getOrDefault("PENDING", 0);
        int cancelled = counts.getOrDefault("CANCELLED", 0);
        int returned = counts.getOrDefault("RETURNED", 0);

        breakdown.add(createStatusMap("Đã giao", total > 0 ? (double) delivered * 100 / total : 0, delivered, "#10b981"));
        breakdown.add(createStatusMap("Đang giao", total > 0 ? (double) shipping * 100 / total : 0, shipping, "#EB8317"));
        breakdown.add(createStatusMap("Chờ xử lý", total > 0 ? (double) pending * 100 / total : 0, pending, "#F3C623"));
        breakdown.add(createStatusMap("Đã huỷ", total > 0 ? (double) cancelled * 100 / total : 0, cancelled, "#ef4444"));
        breakdown.add(createStatusMap("Hoàn hàng", total > 0 ? (double) returned * 100 / total : 0, returned, "#8b5cf6"));
        return breakdown;
    }

    private Map<String, Object> createStatusMap(String name, double val, int count, String color) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("value", Math.round(val * 10.0) / 10.0);
        m.put("count", count);
        m.put("color", color);
        return m;
    }

    public List<Product> getTopProducts(int limit) {
        return orderDAO.getTopProducts(limit);
    }

    public List<Map<String, Object>> getTopProductsDetailed(String period, int limit) {
        List<Map<String, Object>> list = new ArrayList<>();
        LocalDateRange range = parsePeriod(period);
        if (range == null) return list;

        String sql = "SELECT p.product_id, p.sku_code AS sku, p.product_name AS name, "
                   + "SUM(oi.qty) AS totalQuantity, "
                   + "SUM(oi.qty * oi.unit_price) AS totalRevenue, "
                   + "GROUP_CONCAT(DISTINCT COALESCE(c.channel_name, o.channel)) AS channels "
                   + "FROM order_items oi "
                   + "JOIN products p ON oi.product_id = p.product_id "
                   + "JOIN orders o ON oi.order_id = o.order_id "
                   + "LEFT JOIN channels c ON o.channel_id = c.channel_id "
                   + "WHERE o.status NOT IN ('CANCELLED') "
                   + "AND o.created_at >= ? AND o.created_at < ? "
                   + "GROUP BY p.product_id, p.sku_code, p.product_name "
                   + "ORDER BY totalRevenue DESC "
                   + "LIMIT ?";

        long days = java.time.temporal.ChronoUnit.DAYS.between(range.start.toLocalDate(), range.end.toLocalDate());
        LocalDateTime prevStart = range.start.minusDays(days);
        LocalDateTime prevEnd = range.start;

        try (Connection conn = com.wms.util.DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(range.start));
            ps.setTimestamp(2, Timestamp.valueOf(range.end));
            ps.setInt(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int productId = rs.getInt("product_id");
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("sku", rs.getString("sku"));
                    map.put("name", rs.getString("name"));
                    map.put("totalQuantity", rs.getBigDecimal("totalQuantity"));
                    BigDecimal thisRev = rs.getBigDecimal("totalRevenue");
                    map.put("totalRevenue", thisRev);

                    String chStr = rs.getString("channels");
                    List<String> chList = new ArrayList<>();
                    if (chStr != null) {
                        for (String s : chStr.split(",")) {
                            if (!s.trim().isEmpty()) chList.add(s.trim());
                        }
                    }
                    if (chList.isEmpty()) {
                        chList.add("ONLINE");
                    }
                    map.put("channels", chList);

                    // Compute growth for this product
                    BigDecimal prevRev = getProductRevenueForRange(conn, productId, prevStart, prevEnd);
                    double growth = 0.0;
                    if (prevRev.compareTo(BigDecimal.ZERO) > 0) {
                        growth = thisRev.subtract(prevRev)
                                .divide(prevRev, 4, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100))
                                .doubleValue();
                    } else if (thisRev.compareTo(BigDecimal.ZERO) > 0) {
                        growth = 100.0;
                    }
                    map.put("growth", Math.round(growth * 10.0) / 10.0);

                    list.add(map);
                }
            }
        } catch (SQLException e) {
            log.warn("getTopProductsDetailed failed for period=" + period, e);
        }
        return list;
    }

    private BigDecimal getProductRevenueForRange(Connection conn, int productId, LocalDateTime start, LocalDateTime end) {
        String sql = "SELECT COALESCE(SUM(oi.qty * oi.unit_price), 0) "
                   + "FROM order_items oi "
                   + "JOIN orders o ON oi.order_id = o.order_id "
                   + "WHERE o.status NOT IN ('CANCELLED') "
                   + "AND o.created_at >= ? AND o.created_at < ? "
                   + "AND oi.product_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(start));
            ps.setTimestamp(2, Timestamp.valueOf(end));
            ps.setInt(3, productId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getBigDecimal(1);
            }
        } catch (SQLException e) {
            log.warn("getProductRevenueForRange failed for productId=" + productId, e);
        }
        return BigDecimal.ZERO;
    }

    private LocalDateRange parsePeriod(String period) {
        if (period == null) period = "30ngay";
        LocalDate today = LocalDate.now();
        switch (period) {
            case "today":   return new LocalDateRange(today.atStartOfDay(), today.plusDays(1).atStartOfDay());
            case "7ngay":
            case "week":    return new LocalDateRange(today.minusDays(7).atStartOfDay(), today.plusDays(1).atStartOfDay());
            case "30ngay":
            case "month":   return new LocalDateRange(today.minusDays(30).atStartOfDay(), today.plusDays(1).atStartOfDay());
            case "3thang":
            case "quarter": return new LocalDateRange(today.minusDays(90).atStartOfDay(), today.plusDays(1).atStartOfDay());
            case "6thang":  return new LocalDateRange(today.minusDays(180).atStartOfDay(), today.plusDays(1).atStartOfDay());
            case "1nam":    return new LocalDateRange(today.minusDays(365).atStartOfDay(), today.plusDays(1).atStartOfDay());
            default:        return new LocalDateRange(today.minusDays(30).atStartOfDay(), today.plusDays(1).atStartOfDay());
        }
    }

    private static class LocalDateRange {
        final LocalDateTime start;
        final LocalDateTime end;
        LocalDateRange(LocalDateTime start, LocalDateTime end) { this.start = start; this.end = end; }
    }

    public static class ActionResult {
        private final boolean success;
        private final String message;
        private final Map<String, Object> data;

        private ActionResult(boolean success, String message) {
            this(success, message, null);
        }

        private ActionResult(boolean success, String message, Map<String, Object> data) {
            this.success = success;
            this.message = message;
            this.data = data;
        }

        public static ActionResult success(String message) {
            return new ActionResult(true, message, null);
        }

        public static ActionResult success(String message, Map<String, Object> data) {
            return new ActionResult(true, message, data);
        }

        public static ActionResult failure(String message) {
            return new ActionResult(false, message, null);
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public Map<String, Object> getData() { return data; }
    }
}
