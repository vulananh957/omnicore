package com.wms.controller.sales;

import com.wms.controller.BaseController;
import com.wms.service.sales.OrderService;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * OrderActionServlet — Handles POST requests for order lifecycle status
 * modifications.
 * Maps to /sales/order-action.
 */
public class OrderActionServlet extends BaseController {

    private final OrderService orderService = new OrderService();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json;charset=UTF-8");
        PrintWriter out = resp.getWriter();
        String action = req.getParameter("action");
        String orderCode = req.getParameter("orderCode");
        if (action == null || orderCode == null) {
            out.write("{\"success\":false,\"message\":\"Missing action or orderCode\"}");
            return;
        }

        String userRole = currentUserRole(req);
        OrderService.ActionResult result = orderService.handleAction(
                action, orderCode,
                req.getParameter("warehouseName"),
                req.getParameter("note"),
                req.getParameter("trackingNo"),
                req.getParameter("video"),
                req.getParameter("rmaReason"),
                req.getParameter("rmaPhysicalStatus"),
                req.getParameter("rmaPlatformStatus"),
                req.getParameter("platformStatus"),
                req.getParameter("disputeNote"),
                currentUserId(req) != null ? currentUserId(req) : 1,
                userRole != null ? userRole : "");

        if (result.isSuccess()) {
            // ActionResult có thể mang theo data (vd: trackingNo sau khi server sinh)
            java.util.Map<String, Object> data = result.getData();
            if (data != null && data.containsKey("trackingNo")) {
                Object tracking = data.get("trackingNo");
                // JSON-safe escape: chỉ cho phép string, loại bỏ ký tự đặc biệt
                String trackingEscaped = tracking == null ? ""
                    : tracking.toString().replace("\\", "\\\\").replace("\"", "\\\"");
                out.write("{\"success\":true,\"trackingNo\":\"" + trackingEscaped + "\"}");
            } else {
                out.write("{\"success\":true}");
            }
        } else {
            // JSON-safe escape message
            String message = result.getMessage() == null ? ""
                : result.getMessage().replace("\\", "\\\\").replace("\"", "\\\"")
                       .replace("\n", "\\n").replace("\r", "\\r");
            out.write("{\"success\":false,\"message\":\"" + message + "\"}");
        }
    }
}
