package com.wms.controller.api;

import com.wms.dao.RmaDAO;
import com.wms.model.RmaRequest;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * GET /api/website/order-actions/{orderId}/return-status — Trả RMA status cho khách.
 *
 * <p>Response: {rma_code, status, resolution_note, requested_at, returned_at}
 * Nếu chưa submit return request → 404.
 */
public class WebsiteOrderReturnStatusServlet extends BaseApiServlet {

    private static final Logger LOGGER = Logger.getLogger(WebsiteOrderReturnStatusServlet.class.getName());
    private final RmaDAO rmaDAO = new RmaDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        if (authenticateAndReadBody(req, resp) == null) return;

        String pathInfo = req.getPathInfo(); // e.g. /123/return-status
        String[] parts = pathInfo == null ? null : pathInfo.replaceFirst("^/", "").split("/");
        if (parts == null || parts.length != 2 || !"return-status".equals(parts[1])) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Expected path /{orderId}/return-status");
            return;
        }

        int orderId;
        try {
            orderId = Integer.parseInt(parts[0]);
        } catch (NumberFormatException e) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid order id");
            return;
        }

        try {
            // Tìm RMA request cho order này
            RmaRequest rma = rmaDAO.findByOrderId(orderId);
            if (rma == null) {
                sendError(resp, HttpServletResponse.SC_NOT_FOUND, "No return request found for this order");
                return;
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("rma_code", rma.getRmaCode());
            data.put("order_id", orderId);
            data.put("status", rma.getStatus()); // PENDING / APPROVED / DISPUTED / RESOLVED
            data.put("return_reason", rma.getReturnReason());
            data.put("resolution_note", rma.getResolutionNote());
            data.put("requested_at", rma.getRequestedAt() != null ? rma.getRequestedAt().toString() : null);
            data.put("returned_at", rma.getReturnedAt() != null ? rma.getReturnedAt().toString() : null);

            sendJson(resp, HttpServletResponse.SC_OK, data);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "WebsiteOrderReturnStatusServlet.doGet failed for orderId=" + orderId, e);
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Lỗi hệ thống");
        }
    }
}
