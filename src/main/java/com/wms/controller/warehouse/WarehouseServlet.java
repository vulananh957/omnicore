package com.wms.controller.warehouse;

import com.wms.controller.BaseController;
import com.wms.model.Warehouse;
import com.wms.service.warehouse.WarehouseService;
import com.wms.util.JsonUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

/**
 * WarehouseServlet — Handles requests for the Warehouse List page and AJAX
 * APIs.
 *
 * Maps to /business/warehouses.
 */
public class WarehouseServlet extends BaseController {

    private final WarehouseService warehouseService = new WarehouseService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String action = req.getParameter("action");
        // controller tiếp nhận hành động tải danh sách kho
        if ("list".equals(action)) {
            handleList(req, resp); // chuyển sang hàm xử lý json
            return;
        }

        req.setAttribute("pageTitle", "Danh Sách Kho Hàng");
        req.setAttribute("pageSubtitle", "Quản lý thông tin, phân khu lưu trữ và trạng thái các chi nhánh kho");
        req.setAttribute("currentPage", "warehouses");
        req.setAttribute("contentPage", "/WEB-INF/views/warehouse/warehouses.jsp");

        req.getRequestDispatcher("/WEB-INF/views/layout/dashboard-layout.jsp")
                .forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String action = req.getParameter("action");

        try {
            if ("save".equals(action)) {
                handleSave(req, resp);
            } else if ("toggleStatus".equals(action)) {
                handleToggleStatus(req, resp);
            } else {
                writeJson(resp, "{\"success\":false,\"message\":\"Hành động không hợp lệ.\"}");
            }
        } catch (Exception e) {
            writeJson(resp,
                    "{\"success\":false,\"message\":\"Đã xảy ra lỗi hệ thống: " + escapeJson(e.getMessage()) + "\"}");
        }
    }

    // chuyển danh sách warehouse thành chuỗi json trả về cho browser
    private void handleList(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            List<Warehouse> list = warehouseService.findAll();
            writeJson(resp, JsonUtil.toJson(list));
        } catch (Exception e) {
            writeJson(resp, "[]");
        }
    }

    // nhận json từ browser, chuyển thành warehouse object, gọi service lưu xuống db
    private void handleSave(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            Warehouse w = parseJson(req.getReader().lines().reduce("", (a, b) -> a + b), Warehouse.class);
            WarehouseService.SaveResult result = warehouseService.saveWarehouse(w);

            if (result.isSuccess()) {
                writeJson(resp, "{\"success\":true}");
            } else {
                writeJson(resp, "{\"success\":false,\"message\":\"" + escapeJson(result.getMessage()) + "\"}");
            }
        } catch (Exception e) {
            writeJson(resp,
                    "{\"success\":false,\"message\":\"Lỗi định dạng dữ liệu: " + escapeJson(e.getMessage()) + "\"}");
        }
    }

    // xử lý bật tắt trạng thái kho, nhận id và active từ browser, gọi service lưu
    // xuống db
    private void handleToggleStatus(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String idStr = req.getParameter("id");
        String activeStr = req.getParameter("active");

        if (idStr == null || idStr.trim().isEmpty() || activeStr == null || activeStr.trim().isEmpty()) {
            writeJson(resp, "{\"success\":false,\"message\":\"Thiếu tham số ID hoặc trạng thái.\"}");
            return;
        }

        try {
            // nhận yêu cầu post với tham số id và active
            int id = Integer.parseInt(idStr);
            boolean active = Boolean.parseBoolean(activeStr);
            // gọi service
            boolean success = warehouseService.toggleStatus(id, active);
            // trả về kết quả
            if (success) {
                writeJson(resp, "{\"success\":true}");
            } else {
                writeJson(resp, "{\"success\":false,\"message\":\"Không thể thay đổi trạng thái kho.\"}");
            }
        } catch (NumberFormatException e) {
            writeJson(resp, "{\"success\":false,\"message\":\"ID không hợp lệ.\"}");
        } catch (Exception e) {
            writeJson(resp, "{\"success\":false,\"message\":\"Lỗi: " + escapeJson(e.getMessage()) + "\"}");
        }
    }
}
