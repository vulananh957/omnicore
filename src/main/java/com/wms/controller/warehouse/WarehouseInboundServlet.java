package com.wms.controller.warehouse;

import com.wms.controller.BaseController;
import com.wms.model.InboundOrder;
import com.wms.model.Product;
import com.wms.model.Supplier;
import com.wms.model.Warehouse;
import com.wms.service.business.SupplierService;
import com.wms.service.product.ProductService;
import com.wms.service.warehouse.InboundService;
import com.wms.service.warehouse.WarehouseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;


/**
 * WarehouseInboundServlet — Handles Inbound Receipts (Nhập kho) for the
 * Warehouse Staff.
 *
 * Maps to /warehouse/inbound.
 */
public class WarehouseInboundServlet extends BaseController {

    private static final Logger LOGGER = LoggerFactory.getLogger(WarehouseInboundServlet.class);

    private final InboundService inboundService = new InboundService();
    private final ProductService productService = new ProductService();
    private final WarehouseService warehouseService = new WarehouseService();
    private final SupplierService supplierService = new SupplierService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        consumeFlash(req);
        try {
            int myWarehouseId = currentWarehouseId(req);
            List<InboundOrder> inboundList = inboundService.findByWarehouse(myWarehouseId);
            List<Product> products = productService.findAll();
            List<Warehouse> warehouses = warehouseService.findAllActive();
            List<Supplier> suppliers = supplierService.getAllActiveSuppliers();
            req.setAttribute("inboundList", inboundList);
            req.setAttribute("products", products);
            setJsonAttr(req, "productsJson", products);
            req.setAttribute("warehouses", warehouses);
            req.setAttribute("suppliers", suppliers);
            setJsonAttr(req, "suppliersJson", suppliers);
            req.setAttribute("zones", warehouseService.findZonesByWarehouseId(myWarehouseId));
            req.setAttribute("myWarehouseId", currentWarehouseId(req));
        } catch (Exception e) {
            LOGGER.error("WarehouseInboundServlet.doGet failed", e);
            throw new ServletException("Failed to load warehouse inbound page", e);
        }

        req.setAttribute("pageTitle", "Quản Lý Phiếu Nhập Kho");
        req.setAttribute("pageSubtitle",
                "Xử lý hàng từ nhà cung cấp — ghi nhận tồn kho và tạo ledger entry khi xác nhận");
        req.setAttribute("currentPage", "wh-inbound");
        req.setAttribute("contentPage", "/WEB-INF/views/inbound/warehouse-inbound.jsp");

        req.getRequestDispatcher("/WEB-INF/views/layout/warehouse-layout.jsp")
                .forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String action = req.getParameter("action");
        Integer currentUserId = getCurrentUserId(req);

        if ("create".equals(action) || action == null) {
            String supplierIdStr = req.getParameter("supplierId");
            String supplierNameFallback = req.getParameter("supplierName");
            String expectedDateStr = req.getParameter("expectedDate");
            String notes = req.getParameter("notes");

            int warehouseId = currentWarehouseId(req);

            Integer supplierId = null;
            if (supplierIdStr != null && !supplierIdStr.trim().isEmpty()) {
                try { supplierId = Integer.parseInt(supplierIdStr.trim()); }
                catch (NumberFormatException nfe) { supplierId = null; }
            }

            LocalDate expectedDate = null;
            if (expectedDateStr != null && !expectedDateStr.trim().isEmpty()) {
                try {
                    expectedDate = LocalDate.parse(expectedDateStr);
                } catch (Exception ignored) {
                }
            }

            List<InboundService.DraftItem> items = null;
            String itemsJson = req.getParameter("itemsJson");
            if (itemsJson != null && !itemsJson.trim().isEmpty()) {
                try {
                    items = com.wms.util.JsonUtil.getMapper().readValue(itemsJson,
                            new com.fasterxml.jackson.core.type.TypeReference<List<InboundService.DraftItem>>() {});
                } catch (Exception e) {
                    System.err.println("[ERROR] Failed to parse itemsJson: " + e.getMessage());
                }
            }

            try {
                InboundService.CreateInboundResult result;
                if (supplierId != null) {
                    // Path chính: PO liên kết với suppliers.supplier_id (ràng buộc)
                    result = inboundService.createInbound(
                            supplierId, warehouseId, expectedDate, notes,
                            currentUserId != null ? currentUserId : 1, items);
                } else {
                    // Path fallback: PO cũ không liên kết — tương thích ngược
                    result = inboundService.createInboundLegacy(
                            supplierNameFallback, warehouseId, expectedDate, notes,
                            currentUserId != null ? currentUserId : 1, items);
                }

                if (result.isSuccess()) {
                    InboundOrder order = inboundService.findById(result.getInboundId());
                    StringBuilder msg = new StringBuilder();
                    msg.append("Tạo phiếu nhập ").append(order != null ? order.getInboundCode() : "#" + result.getInboundId())
                       .append(" thành công!");
                    if (!result.getWarnings().isEmpty()) {
                        msg.append(" (Cảnh báo: ").append(String.join("; ", result.getWarnings())).append(")");
                    }
                    setFlashSuccess(req, msg.toString());
                } else {
                    setFlashError(req, result.getMessage());
                }
            } catch (Exception e) {
                setFlashError(req, "Lỗi cơ sở dữ liệu: " + e.getMessage());
            }
            redirect(req, resp, "/warehouse/inbound");
            return;

        } else if ("receive".equals(action)) {
            String inboundIdStr = req.getParameter("inboundId");
            String zoneIdStr = req.getParameter("zoneId");
            String[] productIds = req.getParameterValues("productId");
            String[] receivedQtys = req.getParameterValues("receivedQty");
            String[] acceptedQtys = req.getParameterValues("acceptedQty");
            String[] rejectedQtys = req.getParameterValues("rejectedQty");
            String[] rejectReasons = req.getParameterValues("rejectReason");
            String[] unitCosts = req.getParameterValues("unitCost");
            String receivedDateStr = req.getParameter("receivedDate");
            String deliveryPerson = req.getParameter("deliveryPerson");
            String deliveryPhone = req.getParameter("deliveryPhone");

            if (inboundIdStr == null || inboundIdStr.trim().isEmpty()) {
                setFlashError(req, "Thiếu ID phiếu nhập.");
                redirect(req, resp, "/warehouse/inbound");
                return;
            }

            try {
                int inboundId = Integer.parseInt(inboundIdStr);
                Integer zoneId = null;
                if (zoneIdStr != null && !zoneIdStr.trim().isEmpty()) {
                    try { zoneId = Integer.parseInt(zoneIdStr); } catch (Exception ignored) {}
                }
                
                InboundOrder io = inboundService.findById(inboundId);
                int myWarehouseId = currentWarehouseId(req);
                if (io == null || io.getWarehouseId() != myWarehouseId) {
                    setFlashError(req, "Bạn không có quyền nhận hàng cho phiếu nhập thuộc kho khác.");
                    redirect(req, resp, "/warehouse/inbound");
                    return;
                }
                List<InboundService.ReceiptItem> items = new ArrayList<>();
                if (productIds != null && receivedQtys != null) {
                    for (int i = 0; i < productIds.length; i++) {
                        InboundService.ReceiptItem item = new InboundService.ReceiptItem();
                        item.setProductId(Integer.parseInt(productIds[i]));
                        item.setReceivedQty(parseDecimal(receivedQtys[i]));
                        item.setAcceptedQty(parseDecimal(acceptedQtys != null ? acceptedQtys[i] : null));
                        item.setRejectedQty(parseDecimal(rejectedQtys != null ? rejectedQtys[i] : null));
                        item.setRejectReason(rejectReasons != null && rejectReasons.length > i ? rejectReasons[i] : null);
                        item.setUnitCost(parseDecimal(unitCosts != null ? unitCosts[i] : null));
                        items.add(item);
                    }
                }

                java.time.LocalDate receivedDate = null;
                if (receivedDateStr != null && !receivedDateStr.trim().isEmpty()) {
                    try { receivedDate = java.time.LocalDate.parse(receivedDateStr); } catch (Exception ignored) {}
                }

                InboundService.ReceiveResult result = inboundService.receiveGoods(
                        inboundId, zoneId, items, receivedDate, deliveryPerson, deliveryPhone,
                        currentUserId != null ? currentUserId : 1);

                if (result.isSuccess()) {
                    setFlashSuccess(req, result.getMessage());
                } else {
                    setFlashError(req, result.getMessage());
                }
            } catch (Exception e) {
                setFlashError(req, "Dữ liệu không hợp lệ: " + e.getMessage());
            }
            redirect(req, resp, "/warehouse/inbound");
            return;
        } else if ("markPurchased".equals(action)) {
            // Xác nhận đã mua hàng → chuyển PENDING → PURCHASED.
            String inboundIdStr = req.getParameter("inboundId");
            if (inboundIdStr == null || inboundIdStr.trim().isEmpty()) {
                setFlashError(req, "Thiếu ID phiếu mua hàng.");
                redirect(req, resp, "/warehouse/inbound");
                return;
            }
            try {
                int inboundId = Integer.parseInt(inboundIdStr);
                InboundOrder io = inboundService.findById(inboundId);
                int myWarehouseId = currentWarehouseId(req);
                if (io == null || io.getWarehouseId() != myWarehouseId) {
                    setFlashError(req, "Bạn không có quyền xác nhận phiếu mua hàng thuộc kho khác.");
                    redirect(req, resp, "/warehouse/inbound");
                    return;
                }
                InboundService.ValidationResult result = inboundService.markPurchased(
                        inboundId, currentUserId != null ? currentUserId : 1);
                if (result.isSuccess()) {
                    setFlashSuccess(req, "Đã xác nhận mua phiếu " + io.getInboundCode() + ". Bây giờ có thể tạo phiếu nhập kho.");
                } else {
                    setFlashError(req, result.getMessage());
                }
            } catch (Exception e) {
                setFlashError(req, "Lỗi: " + e.getMessage());
            }
            redirect(req, resp, "/warehouse/inbound");
            return;
        } else if ("complete".equals(action)) {
            // Hoàn thành phiếu nhập: IN_PROGRESS → RECEIVED (khi đã nhận đủ hàng).
            String inboundIdStr = req.getParameter("inboundId");
            if (inboundIdStr == null || inboundIdStr.trim().isEmpty()) {
                setFlashError(req, "Thiếu ID phiếu nhập.");
                redirect(req, resp, "/warehouse/inbound");
                return;
            }
            try {
                int inboundId = Integer.parseInt(inboundIdStr);
                InboundOrder io = inboundService.findById(inboundId);
                int myWarehouseId = currentWarehouseId(req);
                if (io == null || io.getWarehouseId() != myWarehouseId) {
                    setFlashError(req, "Bạn không có quyền thao tác trên phiếu thuộc kho khác.");
                    redirect(req, resp, "/warehouse/inbound");
                    return;
                }
                if (!InboundOrder.STATUS_IN_PROGRESS.equals(io.getStatus())) {
                    setFlashError(req, "Chỉ phiếu đang kiểm đếm mới có thể hoàn thành.");
                    redirect(req, resp, "/warehouse/inbound");
                    return;
                }
                InboundService.ValidationResult result = inboundService.completeInbound(
                        inboundId, currentUserId != null ? currentUserId : 1);
                if (result.isSuccess()) {
                    setFlashSuccess(req, "Phiếu " + io.getInboundCode() + " đã hoàn thành.");
                } else {
                    setFlashError(req, result.getMessage());
                }
            } catch (Exception e) {
                setFlashError(req, "Lỗi: " + e.getMessage());
            }
            redirect(req, resp, "/warehouse/inbound");
            return;
        } else {
            redirect(req, resp, "/warehouse/inbound");
        }
    }

    private Integer getCurrentUserId(HttpServletRequest req) {
        try {
            Object user = req.getSession(false) != null
                    ? req.getSession(false).getAttribute("loggedInUser")
                    : null;
            if (user != null) {
                return (Integer) user.getClass().getMethod("getUserId").invoke(user);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public static BigDecimal parseDecimal(String val) {
        if (val == null || val.trim().isEmpty()) return null;
        try { return new BigDecimal(val.trim()); }
        catch (NumberFormatException e) { return null; }
    }
}
