package com.wms.controller.warehouse;

import com.wms.controller.BaseController;
import com.wms.model.Channel;
import com.wms.model.ReturnItem;
import com.wms.model.Warehouse;
import com.wms.service.sales.ChannelService;
import com.wms.service.warehouse.ReturnService;
import com.wms.service.common.NotificationService;
import com.wms.service.warehouse.WarehouseService;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

/**
 * WarehouseReturnsServlet — Handles Returns, RMA & QC (Hàng hoàn & QC) for the Warehouse Staff.
 *
 * Maps to /warehouse/returns.
 */
public class WarehouseReturnsServlet extends BaseController {

    private static final String CONTEXT_PATH = "/warehouse/returns";
    private final ReturnService returnService = new ReturnService();
    private final ChannelService channelService = new ChannelService();
    private final NotificationService notificationService = new NotificationService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        consumeFlash(req);

        int warehouseId = currentWarehouseId(req);
        try {
            List<?> products = returnService.findApprovedProducts();
            List<?> returns = returnService.findByWarehouse(warehouseId);
            List<?> channels = channelService.findAll();
            System.out.println("[WarehouseReturns] DEBUG: products=" + products.size() + ", returns=" + returns.size() + ", channels=" + channels.size());
            req.setAttribute("products", products);
            req.setAttribute("returns", returns);
            req.setAttribute("channels", channels);
        } catch (Exception e) {
            System.out.println("[WarehouseReturns] ERROR: " + e.getClass().getName() + " - " + e.getMessage());
            e.printStackTrace();
            req.setAttribute("products", List.of());
            req.setAttribute("returns", List.of());
            req.setAttribute("channels", List.<Channel>of());
        }

        req.setAttribute("pageTitle",    "Trung Tâm Tiếp Nhận Hàng Hoàn QC");
        req.setAttribute("pageSubtitle", "Phân loại hàng khách trả — nhập lại kho bán hoặc chuyển kho phế phẩm");
        req.setAttribute("currentPage",  "wh-returns");

        req.setAttribute("contentPage", "/WEB-INF/views/returns/warehouse-returns.jsp");

        req.getRequestDispatcher("/WEB-INF/views/layout/warehouse-layout.jsp")
           .forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        req.setCharacterEncoding("UTF-8");
        String action = req.getParameter("action");

        int[] ctx = getUserContext(req);
        int userId = ctx[0];
        int warehouseId = ctx[1];

        if (action == null || action.trim().isEmpty()) {
            setFlashError(req, "Hành động không hợp lệ.");
            redirect(req, resp, CONTEXT_PATH);
            return;
        }

        try {
            switch (action) {
                case "create": {
                    String soRef = req.getParameter("soRef");
                    String customer = req.getParameter("customer");
                    String phone = req.getParameter("phone");
                    String itemsJson = req.getParameter("itemsJson");

                    List<ReturnItem> items = null;
                    if (itemsJson != null && !itemsJson.trim().isEmpty()) {
                        items = parseJson(itemsJson,
                            new com.fasterxml.jackson.core.type.TypeReference<List<ReturnItem>>() {});
                    }

                    ReturnService.ValidationResult validation =
                        returnService.validateForCreate(soRef, customer, phone, items);
                    if (!validation.isSuccess()) {
                        setFlashError(req, validation.getMessage());
                        break;
                    }

                    int returnId = returnService.createReturn(soRef, customer, phone, items, warehouseId);
                    if (returnId > 0) {
                        // Notify warehouse staff of new return order
                        String whName;
                        try {
                            WarehouseService ws = new WarehouseService();
                            Warehouse wh = ws.findById(warehouseId);
                            whName = wh != null ? wh.getWarehouseName() : String.valueOf(warehouseId);
                        } catch (Exception e) {
                            whName = String.valueOf(warehouseId);
                        }
                        notificationService.notifyNewReturn(warehouseId, whName, returnId, soRef);
                        setFlashSuccess(req, "Tạo phiếu hàng hoàn cho đơn hàng " + soRef + " thành công!");
                    } else {
                        setFlashError(req, "Không thể tạo phiếu hàng hoàn. Vui lòng kiểm tra mã SO gốc.");
                    }
                    break;
                }

                case "qc": {
                    String returnIdStr = req.getParameter("returnId");
                    String itemsJson = req.getParameter("itemsJson");

                    if (isNullOrEmpty(returnIdStr) || isNullOrEmpty(itemsJson)) {
                        setFlashError(req, "Thiếu thông tin kết quả kiểm QC.");
                        break;
                    }

                    int returnId = Integer.parseInt(returnIdStr.trim());
                    if (returnService.getWarehouseIdForReturn(returnId) != warehouseId) {
                        setFlashError(req, "Bạn không có quyền cập nhật QC cho phiếu hàng hoàn thuộc kho khác.");
                        break;
                    }
                    List<ReturnItem> items = parseJson(itemsJson,
                        new com.fasterxml.jackson.core.type.TypeReference<List<ReturnItem>>() {});
                    boolean success = returnService.saveQC(returnId, items, userId);
                    if (success) {
                        setFlashSuccess(req, "Cập nhật kết quả QC cho phiếu #" + returnId + " thành công.");
                    } else {
                        setFlashError(req, "Lưu kết quả QC thất bại.");
                    }
                    break;
                }

                case "apply": {
                    String returnIdStr = req.getParameter("returnId");
                    if (isNullOrEmpty(returnIdStr)) {
                        setFlashError(req, "Thiếu ID phiếu hàng hoàn.");
                        break;
                    }

                    int returnId = Integer.parseInt(returnIdStr.trim());
                    if (returnService.getWarehouseIdForReturn(returnId) != warehouseId) {
                        setFlashError(req, "Bạn không có quyền áp dụng phiếu hàng hoàn thuộc kho khác.");
                        break;
                    }

                    // Validate: block apply if any items are still pending QC
                    if (!returnService.isQCComplete(returnId)) {
                        setFlashError(req, "Không thể áp dụng: vẫn còn sản phẩm chưa kiểm tra QC. Vui lòng hoàn tất kiểm tra trước.");
                        break;
                    }

                    boolean success = returnService.applyRestock(returnId, userId);
                    if (success) {
                        setFlashSuccess(req, "Áp dụng kết quả hàng hoàn, cập nhật tồn kho thành công!");
                    } else {
                        setFlashError(req, "Áp dụng kết quả hàng hoàn thất bại.");
                    }
                    break;
                }

                default:
                    setFlashError(req, "Hành động không xác định: " + action);
            }
        } catch (Exception e) {
            setFlashError(req, "Lỗi hệ thống: " + e.getMessage());
        }

        redirect(req, resp, CONTEXT_PATH);
    }

    private int[] getUserContext(HttpServletRequest req) {
        int userId = 1, warehouseId = 1;
        try {
            Object user = req.getSession(false) != null
                ? req.getSession(false).getAttribute("loggedInUser") : null;
            if (user != null) {
                Integer uid = (Integer) user.getClass().getMethod("getUserId").invoke(user);
                Integer wid = (Integer) user.getClass().getMethod("getWarehouseId").invoke(user);
                userId = uid != null ? uid : 1;
                warehouseId = wid != null && wid > 0 ? wid : 1;
            }
        } catch (Exception ignored) {}
        return new int[] { userId, warehouseId };
    }
}
