package com.wms.controller.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.model.ProductChangeNotification;
import com.wms.service.notification.ProductChangeNotificationQueue;
import com.wms.util.JsonUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * GET /api/notifications/product-changes — Get pending product change notifications
 * POST /api/notifications/{notificationId}/acknowledge — Dismiss notification
 *
 * No auth required (internal admin use only).
 */
public class ProductChangeNotificationServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(ProductChangeNotificationServlet.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String pathInfo = req.getPathInfo();

        // GET /api/notifications/product-changes
        if (pathInfo == null || pathInfo.equals("/product-changes") || pathInfo.equals("/product-changes/")) {
            List<ProductChangeNotification> pending = ProductChangeNotificationQueue.getInstance().getPendingNotifications();

            List<Map<String, Object>> result = new ArrayList<>();
            for (ProductChangeNotification notif : pending) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("notification_id", notif.getNotificationId());
                item.put("product_id", notif.getProductId());
                item.put("sku_code", notif.getSkuCode());
                item.put("product_name", notif.getProductName());
                item.put("change_type", notif.getChangeType()); // CREATE, UPDATE, DELETE
                item.put("affected_channels", notif.getAffectedChannels()); // WEBSITE,LAZADA
                item.put("change_details", notif.getChangeDetails()); // "name, price"
                item.put("status", notif.getStatus());
                item.put("created_at", notif.getCreatedAt().toString());
                result.add(item);
            }

            resp.setContentType("application/json;charset=UTF-8");
            resp.getWriter().write(MAPPER.writeValueAsString(result));
            return;
        }

        // GET /api/notifications/count
        if (pathInfo != null && pathInfo.contains("/count")) {
            int count = ProductChangeNotificationQueue.getInstance().getPendingNotifications().size();
            String json = String.format("{\"pending_count\":%d}", count);
            resp.setContentType("application/json;charset=UTF-8");
            resp.getWriter().write(json);
            return;
        }

        resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        resp.getWriter().write("{\"error\":\"Unknown endpoint\"}");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String pathInfo = req.getPathInfo();

        // POST /api/notifications/{id}/acknowledge
        if (pathInfo != null && pathInfo.contains("/acknowledge")) {
            String[] parts = pathInfo.split("/");
            if (parts.length >= 2) {
                try {
                    int notificationId = Integer.parseInt(parts[1]);
                    boolean success = ProductChangeNotificationQueue.getInstance()
                            .acknowledgeNotification(notificationId);

                    if (success) {
                        resp.setStatus(HttpServletResponse.SC_OK);
                        resp.setContentType("application/json;charset=UTF-8");
                        resp.getWriter().write("{\"success\":true,\"message\":\"Notification acknowledged\"}");
                    } else {
                        resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                        resp.setContentType("application/json;charset=UTF-8");
                        resp.getWriter().write("{\"success\":false,\"message\":\"Notification not found\"}");
                    }
                    return;
                } catch (NumberFormatException e) {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    resp.getWriter().write("{\"error\":\"Invalid notification ID\"}");
                    return;
                }
            }
        }

        resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        resp.getWriter().write("{\"error\":\"Unknown endpoint\"}");
    }
}
