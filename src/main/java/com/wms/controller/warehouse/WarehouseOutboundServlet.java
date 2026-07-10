package com.wms.controller.warehouse;

import com.wms.controller.BaseController;
import com.wms.dao.FulfillmentRequestDAO;
import com.wms.dao.InventoryDAO;
import com.wms.model.FulfillmentRequest;
import com.wms.model.OutboundOrder;
import com.wms.model.User;
import com.wms.model.Warehouse;
import com.wms.model.RtvOrder;
import com.wms.service.product.ProductService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.wms.service.warehouse.InboundService;
import com.wms.service.warehouse.OutboundService;
import com.wms.service.warehouse.RtvService;
import com.wms.service.warehouse.WarehouseService;
import com.wms.util.AppConstants;
import com.wms.util.JsonUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.annotation.MultipartConfig;
import java.io.IOException;
import java.util.List;

/**
 * WarehouseOutboundServlet — Handles Outbound Dispatch (Xuất kho) for the Warehouse Staff.
 *
 * Maps to /warehouse/outbound.
 * All queries are scoped to the staff's own warehouse. The warehouse dropdown is locked
 * to that single warehouse, and the staff may create disposal notes that are saved
 * (not deducted) for BM approval.
 */
@MultipartConfig
public class WarehouseOutboundServlet extends BaseController {

    private static final String CONTEXT_PATH = "/warehouse/outbound";
    private final OutboundService outboundService = new OutboundService();
    private final WarehouseService warehouseService = new WarehouseService();
    private final InventoryDAO inventoryDAO = new InventoryDAO();
    private final FulfillmentRequestDAO fulfillmentDAO = new FulfillmentRequestDAO();
    private final ProductService productService = new ProductService();
    private final InboundService inboundService = new InboundService();
    private final RtvService rtvService = new RtvService();
    private final com.wms.service.sales.OrderService orderService = new com.wms.service.sales.OrderService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        consumeFlash(req);
        int myWarehouseId = currentWarehouseId(req);
        String statusFilter = req.getParameter("status");
        List<OutboundOrder> outboundOrders;

        try {
            if (statusFilter != null && !statusFilter.trim().isEmpty()) {
                outboundOrders = outboundService.findByWarehouseAndStatus(myWarehouseId, statusFilter);
            } else {
                outboundOrders = outboundService.findByWarehouse(myWarehouseId);
            }
            // Lock the warehouse list to the staff's own warehouse (used by create/disposal dropdowns)
            Warehouse myWarehouse = warehouseService.findById(myWarehouseId);
            List<Warehouse> warehouses = (myWarehouse != null) ? List.of(myWarehouse) : List.<Warehouse>of();
            req.setAttribute("warehouses", warehouses);
            setJsonAttr(req, "warehousesJson", warehouses);

            List<FulfillmentRequest> fulfillmentRequests = fulfillmentDAO.findPendingByWarehouse(myWarehouseId);
            req.setAttribute("fulfillmentRequests", fulfillmentRequests);
            setJsonAttr(req, "fulfillmentRequestsJson", fulfillmentRequests);

            setJsonAttr(req, "productsJson", productService.findAll());

            // Real-time inventory stock for stock validation on dispatch
            try {
                var stockRows = inventoryDAO.findInventorySummaryByWarehouse(myWarehouseId);
                setJsonAttr(req, "inventoryStockJson", stockRows);
            } catch (Exception ex) {
                setJsonAttr(req, "inventoryStockJson", List.of());
            }

            List<com.wms.model.InboundOrder> inboundList = inboundService.findByWarehouse(myWarehouseId);
            req.setAttribute("inboundList", inboundList);

            List<?> rtvList = rtvService.findByWarehouse(myWarehouseId);
            req.setAttribute("rtvList", rtvList);
            setJsonAttr(req, "rtvListJson", rtvList);
        } catch (Exception e) {
            outboundOrders = List.of();
            req.setAttribute("warehouses", List.<Warehouse>of());
            req.setAttribute("warehousesJson", "[]");
            req.setAttribute("fulfillmentRequests", List.<FulfillmentRequest>of());
            req.setAttribute("fulfillmentRequestsJson", "[]");
            req.setAttribute("productsJson", "[]");
            req.setAttribute("inventoryStockJson", "[]");
            req.setAttribute("inboundList", List.of());
            req.setAttribute("rtvList", List.of());
            setJsonAttr(req, "rtvListJson", "[]");
        }

        req.setAttribute("outboundOrders", outboundOrders);
        setJsonAttr(req, "outboundOrdersJson", outboundOrders);
        req.setAttribute("pageTitle",    "Điều Phối Phiếu Xuất Kho");
        req.setAttribute("pageSubtitle", "Nhận lệnh từ Sales Staff — kiểm tra tồn kho, pick, pack và xuất hàng");
        req.setAttribute("currentPage",  "wh-outbound");
        req.setAttribute("contentPage", "/WEB-INF/views/outbound/warehouse-outbound.jsp");

        req.getRequestDispatcher("/WEB-INF/views/layout/warehouse-layout.jsp")
           .forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String action = req.getParameter("action");

        if ("create".equals(action) || action == null) {
            handleCreate(req, resp);
            return;
        }

        if ("updateStatus".equals(action)) {
            handleUpdateStatus(req, resp);
            return;
        }

        if ("generateTracking".equals(action)) {
            handleGenerateTracking(req, resp);
            return;
        }

        if ("cancel".equals(action)) {
            handleCancel(req, resp);
            return;
        }

        if ("pickItem".equals(action)) {
            handlePickItem(req, resp);
            return;
        }

        if ("disposal".equals(action)) {
            handleDisposal(req, resp);
            return;
        }

        if ("restock".equals(action)) {
            handleRestock(req, resp);
            return;
        }

        if ("createRtv".equals(action)) {
            handleCreateRtv(req, resp);
            return;
        }

        if ("approveRtv".equals(action)) {
            handleApproveRtv(req, resp);
            return;
        }

        if ("completeRtv".equals(action)) {
            handleCompleteRtv(req, resp);
            return;
        }

        if ("cancelRtv".equals(action)) {
            handleCancelRtv(req, resp);
            return;
        }

        setFlashError(req, "Hành động không hợp lệ: " + action);
        redirect(resp, req.getContextPath() + CONTEXT_PATH);
    }

    private void handleCreate(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String orderIdStr = req.getParameter("orderId");
        String notes = req.getParameter("notes");

        int warehouseId = currentWarehouseId(req);
        int orderId = 0;
        try {
            orderId = Integer.parseInt(orderIdStr.trim());
        } catch (Exception e) {
            setFlashError(req, "Dữ liệu không hợp lệ: orderId phải là số.");
            redirect(resp, req.getContextPath() + CONTEXT_PATH);
            return;
        }

        OutboundService.ValidationResult validation = outboundService.validateForCreate(orderId, warehouseId);
        if (!validation.isSuccess()) {
            setFlashError(req, validation.getMessage());
            redirect(resp, req.getContextPath() + CONTEXT_PATH);
            return;
        }

        try {
            int newId = outboundService.createOutbound(orderId, warehouseId, notes, currentUserId(req));
            if (newId > 0) {
                setFlashSuccess(req, "Tạo phiếu xuất kho thành công!");
            } else {
                setFlashError(req, "Không thể tạo phiếu xuất kho. Vui lòng thử lại.");
            }
        } catch (Exception e) {
            setFlashError(req, "Lỗi cơ sở dữ liệu: " + e.getMessage());
        }

        redirect(resp, req.getContextPath() + CONTEXT_PATH);
    }

    private void handleUpdateStatus(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String outboundIdStr = req.getParameter("outboundId");
        String newStatus = req.getParameter("status");

        if (outboundIdStr == null || outboundIdStr.trim().isEmpty()
            || newStatus == null || newStatus.trim().isEmpty()) {
            setFlashError(req, "Thiếu thông tin cần thiết để cập nhật trạng thái.");
            redirect(resp, req.getContextPath() + CONTEXT_PATH);
            return;
        }

        try {
            int outboundId = Integer.parseInt(outboundIdStr.trim());
            OutboundOrder oo = outboundService.findById(outboundId);
            int myWarehouseId = currentWarehouseId(req);
            if (oo == null || oo.getWarehouseId() != myWarehouseId) {
                setFlashError(req, "Bạn không có quyền cập nhật trạng thái phiếu xuất thuộc kho khác.");
                redirect(resp, req.getContextPath() + CONTEXT_PATH);
                return;
            }
            OutboundService.StatusUpdateResult result = outboundService.updateStatus(outboundId, newStatus, currentUserId(req));
            if (result.isSuccess()) {
                setFlashSuccess(req, result.getMessage());
            } else {
                setFlashError(req, result.getMessage());
            }
        } catch (NumberFormatException e) {
            setFlashError(req, "ID phiếu xuất không hợp lệ.");
        }

        redirect(resp, req.getContextPath() + CONTEXT_PATH);
    }

    /** AJAX: sinh mã vận đơn cho đơn đã pick xong (sub-tab "Chờ cấp mã"). */
    private void handleGenerateTracking(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        String orderCode = req.getParameter("orderCode");
        if (orderCode == null || orderCode.trim().isEmpty()) {
            resp.getWriter().write("{\"success\":false,\"message\":\"Thiếu mã đơn hàng\"}");
            return;
        }

        int myWarehouseId = currentWarehouseId(req);

        com.wms.dao.OrderDAO orderDAO = new com.wms.dao.OrderDAO();
        com.wms.model.Order order = orderDAO.findByOrderCode(orderCode);
        if (order == null) {
            resp.getWriter().write("{\"success\":false,\"message\":\"Không tìm thấy đơn hàng: " + escapeJson(orderCode) + "\"}");
            return;
        }
        if (order.getWarehouseId() != myWarehouseId) {
            resp.getWriter().write("{\"success\":false,\"message\":\"Bạn không có quyền cấp mã cho đơn thuộc kho khác.\"}");
            return;
        }

        try {
            // Bước 1 — sinh tracking_no (Lazada API hoặc local). Bê nguyên logic
            // từ PendingTrackingServlet cũ (đã xoá); service đã có sẵn idempotency.
            com.wms.service.sales.OrderService.ActionResult result =
                orderService.handleAction("generate_tracking", orderCode,
                    null, null, null, null, null, null, null, null, null, 1, "SYSTEM");
            if (!result.isSuccess()) {
                resp.getWriter().write("{\"success\":false,\"message\":\""
                    + escapeJson(result.getMessage()) + "\"}");
                return;
            }

            // Bước 2 — cập nhật PACKED (giống PendingTrackingServlet cũ).
            // Nếu chỉ cấp tracking mà không PACKED thì đơn kẹt ở PICKING mãi
            // và không xuất hiện ở tab "Đã đóng gói".
            com.wms.service.sales.OrderService.ActionResult packResult =
                orderService.handleAction("print_shipping", orderCode,
                    null, null, null, null, null, null, null, null, null, 1, "SYSTEM");
            if (!packResult.isSuccess()) {
                resp.getWriter().write("{\"success\":false,\"message\":\""
                    + escapeJson("Đã sinh tracking nhưng cập nhật PACKED lỗi: " + packResult.getMessage())
                    + "\"}");
                return;
            }

            String trackingNo = "";
            if (result.getData() instanceof java.util.Map) {
                Object t = ((java.util.Map<?, ?>) result.getData()).get("trackingNo");
                if (t != null) trackingNo = t.toString();
            }
            resp.getWriter().write("{\"success\":true,\"message\":\"Đã cấp mã vận đơn và in tem thành công\",\"trackingNo\":\""
                + escapeJson(trackingNo) + "\"}");
        } catch (Exception e) {
            resp.getWriter().write("{\"success\":false,\"message\":\"Lỗi: "
                + escapeJson(e.getMessage()) + "\"}");
        }
    }

    @Override
    protected String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "");
    }

    private void handleCancel(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String outboundIdStr = req.getParameter("outboundId");

        if (outboundIdStr == null || outboundIdStr.trim().isEmpty()) {
            setFlashError(req, "Thiếu ID phiếu xuất cần hủy.");
            redirect(resp, req.getContextPath() + CONTEXT_PATH);
            return;
        }

        try {
            int outboundId = Integer.parseInt(outboundIdStr.trim());
            OutboundOrder oo = outboundService.findById(outboundId);
            int myWarehouseId = currentWarehouseId(req);
            if (oo == null || oo.getWarehouseId() != myWarehouseId) {
                setFlashError(req, "Bạn không có quyền hủy phiếu xuất thuộc kho khác.");
                redirect(resp, req.getContextPath() + CONTEXT_PATH);
                return;
            }
            OutboundService.CancelResult result = outboundService.cancel(outboundId);
            if (result.isSuccess()) {
                setFlashSuccess(req, result.getMessage());
            } else {
                setFlashError(req, result.getMessage());
            }
        } catch (NumberFormatException e) {
            setFlashError(req, "ID phiếu xuất không hợp lệ.");
        }

        redirect(resp, req.getContextPath() + CONTEXT_PATH);
    }

    /**
     * Handles "Hoàn kệ" action for cancelled outbound orders.
     * Releases the temporary inventory allocation back to available stock.
     */
    private void handleRestock(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String outboundIdStr = req.getParameter("outboundId");

        if (outboundIdStr == null || outboundIdStr.trim().isEmpty()) {
            setFlashError(req, "Thiếu ID phiếu xuất cần hoàn kệ.");
            redirect(resp, req.getContextPath() + CONTEXT_PATH);
            return;
        }

        try {
            int outboundId = Integer.parseInt(outboundIdStr.trim());
            OutboundOrder oo = outboundService.findById(outboundId);
            int myWarehouseId = currentWarehouseId(req);
            if (oo == null || oo.getWarehouseId() != myWarehouseId) {
                setFlashError(req, "Bạn không có quyền hoàn kệ phiếu xuất thuộc kho khác.");
                redirect(resp, req.getContextPath() + CONTEXT_PATH);
                return;
            }

            // Release inventory allocation for this outbound
            boolean released = outboundService.releaseAllocationsForOutbound(outboundId);
            if (released) {
                setFlashSuccess(req, "Đã hoàn kệ thành công. Tồn kho đã được giải phóng.");
            } else {
                setFlashSuccess(req, "Đã xác nhận hoàn kệ (hoặc tồn kho đã được giải phóng trước đó).");
            }
        } catch (NumberFormatException e) {
            setFlashError(req, "ID phiếu xuất không hợp lệ.");
        }

        redirect(resp, req.getContextPath() + CONTEXT_PATH);
    }

    /** AJAX: persist a single line item's picked state during picking. */
    private void handlePickItem(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        try {
            int outboundId = Integer.parseInt(req.getParameter("outboundId").trim());
            OutboundOrder oo = outboundService.findById(outboundId);
            int myWarehouseId = currentWarehouseId(req);
            if (oo == null || oo.getWarehouseId() != myWarehouseId) {
                resp.getWriter().write("{\"success\":false,\"message\":\"Bạn không có quyền pick hàng cho phiếu xuất thuộc kho khác.\"}");
                return;
            }
            int productId = Integer.parseInt(req.getParameter("productId").trim());
            boolean picked = "true".equalsIgnoreCase(req.getParameter("picked"));
            boolean ok = outboundService.updateItemPicked(outboundId, productId, picked);
            resp.getWriter().write("{\"success\":" + ok + "}");
        } catch (Exception e) {
            resp.getWriter().write("{\"success\":false}");
        }
    }

    /** Creates a disposal (SCRAP) issue note. Saves only — no stock deduction. */
    private void handleDisposal(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String sku = req.getParameter("sku");
        String reason = req.getParameter("reason");
        int warehouseId = currentWarehouseId(req);
        java.math.BigDecimal qty;
        try {
            qty = new java.math.BigDecimal(req.getParameter("qty").trim());
        } catch (Exception e) {
            setFlashError(req, "Dữ liệu phiếu xuất huỷ không hợp lệ.");
            redirect(resp, req.getContextPath() + CONTEXT_PATH);
            return;
        }
        OutboundService.StatusUpdateResult r =
            outboundService.createDisposal(sku, qty, reason, warehouseId, currentUserId(req));
        if (r.isSuccess()) {
            setFlashSuccess(req, r.getMessage());
        } else {
            setFlashError(req, r.getMessage());
        }
        redirect(resp, req.getContextPath() + CONTEXT_PATH);
    }

    private void handleCreateRtv(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        try {
            int inboundId = Integer.parseInt(req.getParameter("inboundId"));
            com.wms.model.InboundOrder io = inboundService.findById(inboundId);
            int myWarehouseId = currentWarehouseId(req);
            if (io == null || io.getWarehouseId() != myWarehouseId) {
                resp.getWriter().write("{\"success\":false,\"message\":\"Bạn không có quyền tạo phiếu trả hàng NCC từ phiếu nhập thuộc kho khác.\"}");
                return;
            }
            String reason = req.getParameter("reason");
            String note = req.getParameter("note");
            String poCode = req.getParameter("poCode");
            String supplierCode = req.getParameter("supplierCode");
            String contactPerson = req.getParameter("contactPerson");
            String proposal = req.getParameter("proposal");
            String itemsJson = req.getParameter("itemsJson");
            List<RtvService.RtvItemRequest> itemRequests = null;
            if (itemsJson != null && !itemsJson.trim().isEmpty()) {
                itemRequests = JsonUtil.getMapper().readValue(itemsJson,
                        new TypeReference<List<RtvService.RtvItemRequest>>() {});
            }
            Integer currentUserId = currentUserId(req);
            int uid = currentUserId != null ? currentUserId : 1;
            RtvService.RtvResult result = rtvService.createRtv(inboundId, itemRequests, reason, note, uid, poCode, supplierCode, contactPerson, proposal);
            resp.getWriter().write("{\"success\":" + result.isSuccess()
                    + ",\"message\":\"" + rtvEscapeJson(result.getMessage()) + "\"}");
        } catch (Exception e) {
            resp.getWriter().write("{\"success\":false,\"message\":\"Lỗi: " + rtvEscapeJson(e.getMessage()) + "\"}");
        }
    }

    private void handleApproveRtv(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        try {
            int rtvId = Integer.parseInt(req.getParameter("rtvId"));
            RtvOrder rtv = rtvService.findById(rtvId);
            int myWarehouseId = currentWarehouseId(req);
            if (rtv == null || rtv.getWarehouseId() != myWarehouseId) {
                resp.getWriter().write("{\"success\":false,\"message\":\"Bạn không có quyền duyệt phiếu trả hàng NCC thuộc kho khác.\"}");
                return;
            }
            Integer currentUserId = currentUserId(req);
            int uid = currentUserId != null ? currentUserId : 1;

            Object u = req.getSession().getAttribute(AppConstants.SESSION_USER);
            if (u instanceof User) {
                User user = (User) u;
                if (!"MANAGER".equals(user.getRole())) {
                    resp.getWriter().write("{\"success\":false,\"message\":\"Chỉ cấp quản lý (Manager) mới có quyền duyệt phiếu trả hàng NCC.\"}");
                    return;
                }
            } else {
                resp.getWriter().write("{\"success\":false,\"message\":\"Yêu cầu đăng nhập.\"}");
                return;
            }

            RtvService.RtvResult result = rtvService.approveRtv(rtvId, uid);
            resp.getWriter().write("{\"success\":" + result.isSuccess()
                    + ",\"message\":\"" + rtvEscapeJson(result.getMessage()) + "\"}");
        } catch (Exception e) {
            resp.getWriter().write("{\"success\":false,\"message\":\"Lỗi: " + rtvEscapeJson(e.getMessage()) + "\"}");
        }
    }

    private void handleCompleteRtv(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        try {
            int rtvId = Integer.parseInt(req.getParameter("rtvId"));
            RtvOrder rtv = rtvService.findById(rtvId);
            int myWarehouseId = currentWarehouseId(req);
            if (rtv == null || rtv.getWarehouseId() != myWarehouseId) {
                resp.getWriter().write("{\"success\":false,\"message\":\"Bạn không có quyền hoàn thành phiếu trả hàng NCC thuộc kho khác.\"}");
                return;
            }
            Integer currentUserId = currentUserId(req);
            int uid = currentUserId != null ? currentUserId : 1;
            RtvService.RtvResult result = rtvService.completeRtv(rtvId, uid);
            resp.getWriter().write("{\"success\":" + result.isSuccess()
                    + ",\"message\":\"" + rtvEscapeJson(result.getMessage()) + "\"}");
        } catch (Exception e) {
            resp.getWriter().write("{\"success\":false,\"message\":\"Lỗi: " + rtvEscapeJson(e.getMessage()) + "\"}");
        }
    }

    private void handleCancelRtv(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        try {
            int rtvId = Integer.parseInt(req.getParameter("rtvId"));
            RtvOrder rtv = rtvService.findById(rtvId);
            int myWarehouseId = currentWarehouseId(req);
            if (rtv == null || rtv.getWarehouseId() != myWarehouseId) {
                resp.getWriter().write("{\"success\":false,\"message\":\"Bạn không có quyền hủy phiếu trả hàng NCC thuộc kho khác.\"}");
                return;
            }
            Integer currentUserId = currentUserId(req);
            int uid = currentUserId != null ? currentUserId : 1;

            Object u = req.getSession().getAttribute(AppConstants.SESSION_USER);
            if (u instanceof User) {
                User user = (User) u;
                if (!"MANAGER".equals(user.getRole())) {
                    resp.getWriter().write("{\"success\":false,\"message\":\"Chỉ cấp quản lý (Manager) mới có quyền hủy phiếu trả hàng NCC.\"}");
                    return;
                }
            } else {
                resp.getWriter().write("{\"success\":false,\"message\":\"Yêu cầu đăng nhập.\"}");
                return;
            }

            RtvService.RtvResult result = rtvService.cancelRtv(rtvId, uid);
            resp.getWriter().write("{\"success\":" + result.isSuccess()
                    + ",\"message\":\"" + rtvEscapeJson(result.getMessage()) + "\"}");
        } catch (Exception e) {
            resp.getWriter().write("{\"success\":false,\"message\":\"Lỗi: " + rtvEscapeJson(e.getMessage()) + "\"}");
        }
    }

    private String rtvEscapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "");
    }
}
