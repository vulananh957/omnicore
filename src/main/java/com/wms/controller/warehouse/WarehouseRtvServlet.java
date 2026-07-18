package com.wms.controller.warehouse;

import com.fasterxml.jackson.core.type.TypeReference;
import com.wms.controller.BaseController;
import com.wms.model.RtvOrder;
import com.wms.model.User;
import com.wms.service.warehouse.InboundService;
import com.wms.service.warehouse.RtvService;
import com.wms.util.AppConstants;
import com.wms.util.JsonUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

/**
 * WarehouseRtvServlet — Handles Return-To-Vendor (RTV) operations for Warehouse Staff.
 * Separated from WarehouseOutboundServlet to adhere to the Single Responsibility Principle (SRP).
 */
public class WarehouseRtvServlet extends BaseController {

    private static final String CONTEXT_PATH = "/warehouse/rtv";
    private final RtvService rtvService = new RtvService();
    private final InboundService inboundService = new InboundService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        consumeFlash(req);
        int myWarehouseId = currentWarehouseId(req);

        try {
            List<?> rtvList = rtvService.findByWarehouse(myWarehouseId);
            req.setAttribute("rtvList", rtvList);
            setJsonAttr(req, "rtvListJson", rtvList);
        } catch (Exception e) {
            req.setAttribute("rtvList", List.of());
            setJsonAttr(req, "rtvListJson", "[]");
        }

        // Return JSON response or forward if requested
        String accept = req.getHeader("Accept");
        if (accept != null && accept.contains("application/json")) {
            resp.setContentType("application/json;charset=UTF-8");
            resp.getWriter().write((String) req.getAttribute("rtvListJson"));
        } else {
            // Fallback: forward to layout or default view
            req.setAttribute("pageTitle", "Quản Lý Trả Hàng Nhà Cung Cấp");
            req.setAttribute("pageSubtitle", "Theo dõi và xử lý các phiếu trả hàng NCC (RTV)");
            req.setAttribute("currentPage", "wh-rtv");
            req.setAttribute("contentPage", "/WEB-INF/views/outbound/warehouse-outbound.jsp");
            req.getRequestDispatcher("/WEB-INF/views/layout/warehouse-layout.jsp").forward(req, resp);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String action = req.getParameter("action");

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
                    + ",\"message\":\"" + escapeJson(result.getMessage()) + "\"}");
        } catch (Exception e) {
            resp.getWriter().write("{\"success\":false,\"message\":\"Lỗi: " + escapeJson(e.getMessage()) + "\"}");
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
                    + ",\"message\":\"" + escapeJson(result.getMessage()) + "\"}");
        } catch (Exception e) {
            resp.getWriter().write("{\"success\":false,\"message\":\"Lỗi: " + escapeJson(e.getMessage()) + "\"}");
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
                    + ",\"message\":\"" + escapeJson(result.getMessage()) + "\"}");
        } catch (Exception e) {
            resp.getWriter().write("{\"success\":false,\"message\":\"Lỗi: " + escapeJson(e.getMessage()) + "\"}");
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
                    + ",\"message\":\"" + escapeJson(result.getMessage()) + "\"}");
        } catch (Exception e) {
            resp.getWriter().write("{\"success\":false,\"message\":\"Lỗi: " + escapeJson(e.getMessage()) + "\"}");
        }
    }
}
