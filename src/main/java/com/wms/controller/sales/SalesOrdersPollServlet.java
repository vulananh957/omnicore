package com.wms.controller.sales;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.dao.LazadaOrderDAO;
import com.wms.dao.WarehouseDAO;
import com.wms.model.Channel;
import com.wms.service.lazada.LazadaOrderSyncService;
import com.wms.dao.ChannelDAO;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SalesOrdersPollServlet — Lightweight JSON polling endpoint for real-time order updates.
 *
 * <p>Mapped to GET /sales/orders/poll. Called by the frontend every 30 seconds
 * to check for new Lazada orders without a full page reload.</p>
 *
 * <p>On each request it:
 * <ol>
 *   <li>Triggers a fast Lazada sync (only new/pending orders from last 2 hours, throttled to max once every 10s)</li>
 *   <li>Returns the full lazadaOrdersJson payload the frontend needs to re-render the table</li>
 * </ol>
 * </p>
 *
 * <p>Access is restricted to SALES_STAFF and MANAGER roles via the session check in BaseController.
 * This servlet extends HttpServlet directly since it returns JSON, not a JSP forward.</p>
 */
public class SalesOrdersPollServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(SalesOrdersPollServlet.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final LazadaOrderDAO lazadaOrderDAO = new LazadaOrderDAO();
    private final WarehouseDAO warehouseDAO = new WarehouseDAO();
    private final ChannelDAO channelDAO = new ChannelDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        // Session / role guard — must be logged in as SALES_STAFF or MANAGER
        Object userObj = req.getSession(false) == null ? null : req.getSession(false).getAttribute("user");
        if (userObj == null) {
            resp.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Not authenticated");
            return;
        }
        String role = "";
        try {
            role = (String) userObj.getClass().getMethod("getRole").invoke(userObj);
        } catch (Exception ignored) {}
        if (!("SALES_STAFF".equals(role) || "MANAGER".equals(role) || "ADMIN".equals(role))) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden");
            return;
        }

        // --- Trigger a fast sync (throttled: max once per 10s per channel via existing lastSyncAt check) ---
        try {
            List<Channel> channels = channelDAO.findAll();
            LazadaOrderSyncService syncService = new LazadaOrderSyncService();
            for (Channel ch : channels) {
                if (!ch.isActive() || !"Lazada".equalsIgnoreCase(ch.getPlatform())) continue;
                java.time.LocalDateTime lastSync = ch.getLastOrderSyncAt();
                java.time.LocalDateTime tenSecAgo = java.time.LocalDateTime.now().minusSeconds(10);
                if (lastSync == null || lastSync.isBefore(tenSecAgo)) {
                    syncService.syncNewOrdersFromApi(ch, 50);
                }
            }
        } catch (Exception e) {
            // Non-fatal: return current DB data even if sync fails
            LOGGER.log(Level.WARNING, "SalesOrdersPollServlet: sync failed, returning cached data", e);
        }

        // --- Build response payload ---
        Map<String, Object> payload = new HashMap<>();
        try {
            List<Map<String, Object>> orders = lazadaOrderDAO.findAllWithItemsAndStock();
            List<Map<String, Object>> warehouses = warehouseDAO.findAll().stream()
                    .filter(w -> w.isActive())
                    .map(w -> {
                        Map<String, Object> m = new HashMap<>();
                        m.put("warehouseId", w.getWarehouseId());
                        m.put("warehouseName", w.getWarehouseName());
                        return m;
                    })
                    .collect(java.util.stream.Collectors.toList());

            payload.put("ok", true);
            payload.put("orders", orders);
            payload.put("warehouses", warehouses);
            payload.put("serverTime", System.currentTimeMillis());
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "SalesOrdersPollServlet: DB query failed", e);
            payload.put("ok", false);
            payload.put("error", e.getMessage());
        }

        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        MAPPER.writeValue(resp.getWriter(), payload);
    }
}
