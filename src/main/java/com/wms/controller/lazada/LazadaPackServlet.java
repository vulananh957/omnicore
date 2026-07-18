package com.wms.controller.lazada;

import com.wms.dao.OrderDAO;
import com.wms.model.Order;
import com.wms.model.Channel;
import com.wms.service.lazada.LazadaOrderProcessingService;
import com.wms.service.lazada.LazadaOrderProcessingService.ProcessingResult;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * LazadaPackServlet — endpoint to trigger Step 1 manually:
 * {@code POST /lazada/pack?orderCode=...}
 * Response: JSON { success, errorMessage }
 */
@WebServlet(name = "LazadaPackServlet", urlPatterns = {"/lazada/pack"})
public class LazadaPackServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String orderCode = req.getParameter("orderCode");
        resp.setContentType("application/json;charset=UTF-8");
        try (PrintWriter out = resp.getWriter()) {
            if (orderCode == null || orderCode.isBlank()) {
                resp.setStatus(400);
                out.print("{\"success\":false,\"errorMessage\":\"orderCode required\"}");
                return;
            }
            Order o = new OrderDAO().findByOrderCode(orderCode.trim());
            if (o == null) {
                resp.setStatus(404);
                out.print("{\"success\":false,\"errorMessage\":\"Order not found\"}");
                return;
            }
            
            // Resolve channel
            Channel channel = null;
            if (o.getChannelId() > 0) {
                channel = new com.wms.dao.ChannelDAO().findById(o.getChannelId());
            }
            if (channel == null) {
                // Fallback: find any active Lazada channel by platform name
                channel = new com.wms.dao.ChannelDAO().findByPlatform("Lazada");
            }
            if (channel == null) {
                resp.setStatus(400);
                out.print("{\"success\":false,\"errorMessage\":\"Channel not found for order\"}");
                return;
            }
            
            ProcessingResult r = new LazadaOrderProcessingService().autoPackLazadaOrder(o.getOrderCode(), channel);
            out.print("{\"success\":" + r.success
                    + ",\"errorMessage\":\"" + esc(r.message) + "\"}");
        }
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", " ").replace("\r", " ");
    }
}
