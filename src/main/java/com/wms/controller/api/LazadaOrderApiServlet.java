package com.wms.controller.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.dao.ChannelDAO;
import com.wms.dao.LazadaOrderDAO;
import com.wms.dao.LazadaShipmentProviderDAO;
import com.wms.model.Channel;
import com.wms.model.LazadaOrder;
import com.wms.model.LazadaShipmentProvider;
import com.wms.service.lazada.LazadaOrderProcessingService;
import com.wms.service.lazada.LazadaReverseService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * LazadaOrderApiServlet — Public JSON API for Lazada order data.
 *
 * <p>Intentionally excludes the AuthFilter so internal JS can call these endpoints
 * without a session cookie. The WMS auth filter is not applied to /api/lazada/* paths.</p>
 *
 * <p>Endpoints:</p>
 * <ul>
 *   <li>GET  /api/lazada/orders              — all orders with items</li>
 *   <li>GET  /api/lazada/orders/{id}         — single order detail</li>
 *   <li>GET  /api/lazada/orders/{id}/stock-check?warehouseId=X</li>
 *   <li>GET  /api/lazada/providers           — active shipment providers</li>
 *   <li>GET  /api/lazada/order-counts        — pending counts</li>
 * </ul>
 */
public class LazadaOrderApiServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(LazadaOrderApiServlet.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final LazadaOrderDAO orderDAO = new LazadaOrderDAO();
    private final LazadaShipmentProviderDAO providerDAO = new LazadaShipmentProviderDAO();
    private final ChannelDAO channelDAO = new ChannelDAO();
    private final LazadaOrderProcessingService processingService = new LazadaOrderProcessingService();
    private final LazadaReverseService reverseService = new LazadaReverseService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        resp.setContentType("application/json;charset=UTF-8");
        String pathInfo = req.getPathInfo();

        try {
            if (pathInfo == null || pathInfo.equals("/")) {
                handleListOrders(resp);
            } else if (pathInfo.startsWith("/sync")) {
                handleManualSyncOrder(req, resp);
            } else if (pathInfo.startsWith("/providers")) {
                handleProviders(resp);
            } else if (pathInfo.startsWith("/order-counts")) {
                handleOrderCounts(resp);
            } else if (pathInfo.matches("/[^/]+/stock-check.*")) {
                String id = extractOrderIdFromPath(pathInfo);
                handleStockCheck(req, resp, id);
            } else {
                String id = pathInfo.startsWith("/") ? pathInfo.substring(1) : pathInfo;
                handleOrderDetail(resp, id);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "LazadaOrderApiServlet: GET failed path=" + pathInfo, e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            writeError(resp, "Lỗi hệ thống: " + e.getMessage());
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setContentType("application/json;charset=UTF-8");
        String pathInfo = req.getPathInfo();

        try {
            if (pathInfo != null && pathInfo.startsWith("/sync")) {
                handleManualSyncOrder(req, resp);
            } else if (pathInfo != null && pathInfo.startsWith("/cancel-validate")) {
                handleCancelValidate(req, resp);
            } else if (pathInfo != null && pathInfo.startsWith("/cancel-create")) {
                handleCancelCreate(req, resp);
            } else {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                writeError(resp, "Unknown action: " + pathInfo);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "LazadaOrderApiServlet: POST failed path=" + pathInfo, e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            writeError(resp, "Lỗi hệ thống: " + e.getMessage());
        }
    }

    /**
     * POST /api/lazada/cancel-validate
     * Params: orderId, orderItemIds (comma-separated or JSON array string)
     */
    private void handleCancelValidate(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        String orderId = req.getParameter("orderId");
        String orderItemIds = req.getParameter("orderItemIds");
        if (orderId == null || orderId.isBlank()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            writeError(resp, "Thiếu tham số orderId");
            return;
        }
        if (orderItemIds == null || orderItemIds.isBlank()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            writeError(resp, "Thiếu tham số orderItemIds");
            return;
        }

        LazadaOrder order = orderDAO.findByLazadaOrderIdStr(orderId);
        if (order == null || order.getChannelId() <= 0) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            writeError(resp, "Không tìm thấy đơn hàng");
            return;
        }
        Channel channel = channelDAO.findById(order.getChannelId());

        // Ensure orderItemIds is a JSON array string
        String itemListJson = orderItemIds;
        if (!orderItemIds.trim().startsWith("[")) {
            String[] ids = orderItemIds.split(",");
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < ids.length; i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(ids[i].trim()).append("\"");
            }
            sb.append("]");
            itemListJson = sb.toString();
        }

        LazadaReverseService.CancelValidateResult result =
                reverseService.cancelValidate(channel, orderId, itemListJson);
        writeJson(resp, result);
    }

    /**
     * POST /api/lazada/cancel-create
     * Params: orderId, orderItemIds, reasonId
     */
    private void handleCancelCreate(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        String orderId = req.getParameter("orderId");
        String orderItemIds = req.getParameter("orderItemIds");
        String reasonId = req.getParameter("reasonId");
        if (orderId == null || orderId.isBlank()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            writeError(resp, "Thiếu tham số orderId");
            return;
        }
        if (orderItemIds == null || orderItemIds.isBlank()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            writeError(resp, "Thiếu tham số orderItemIds");
            return;
        }
        if (reasonId == null || reasonId.isBlank()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            writeError(resp, "Thiếu tham số reasonId");
            return;
        }

        LazadaOrder order = orderDAO.findByLazadaOrderIdStr(orderId);
        if (order == null || order.getChannelId() <= 0) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            writeError(resp, "Không tìm thấy đơn hàng");
            return;
        }
        Channel channel = channelDAO.findById(order.getChannelId());

        String itemListJson = orderItemIds;
        if (!orderItemIds.trim().startsWith("[")) {
            String[] ids = orderItemIds.split(",");
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < ids.length; i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(ids[i].trim()).append("\"");
            }
            sb.append("]");
            itemListJson = sb.toString();
        }

        LazadaReverseService.CancelCreateResult result =
                reverseService.cancelCreate(channel, orderId, itemListJson, reasonId);

        // On success, update BOTH tables to CANCELLED for consistency
        if (result.success) {
            orderDAO.updateStatus(orderId, "CANCELLED");
            // Also update legacy orders table
            com.wms.dao.OrderDAO legacyDAO = new com.wms.dao.OrderDAO();
            legacyDAO.updateOrderStatus(orderId, "CANCELLED");
            // Also cancel fulfillment request so warehouse staff doesn't see it
            com.wms.dao.FulfillmentRequestDAO frDAO = new com.wms.dao.FulfillmentRequestDAO();
            frDAO.cancelByOrderId(orderId);
            // Also cancel outbound orders so warehouse staff doesn't see it
            com.wms.dao.OutboundDAO outboundDAO = new com.wms.dao.OutboundDAO();
            outboundDAO.cancelByOrderId(orderId);
            LOGGER.info("LazadaOrderApiServlet: order " + orderId + " cancelled by staff, all related tables updated");
        }

        writeJson(resp, result);
    }

    private void handleListOrders(HttpServletResponse resp) throws IOException {
        List<LazadaOrder> orders = orderDAO.findAll();
        for (LazadaOrder order : orders) {
            order.setItems(orderDAO.findItemsByLazadaOrderIdStr(order.getLazadaOrderIdStr()));
        }
        writeJson(resp, orders);
    }

    private void handleOrderDetail(HttpServletResponse resp, String lazadaOrderIdStr) throws IOException {
        LazadaOrder order = orderDAO.findByLazadaOrderIdStr(lazadaOrderIdStr);
        if (order == null) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            writeError(resp, "Không tìm thấy đơn hàng: " + lazadaOrderIdStr);
            return;
        }
        order.setItems(orderDAO.findItemsByLazadaOrderIdStr(lazadaOrderIdStr));
        writeJson(resp, order);
    }

    private void handleStockCheck(HttpServletRequest req, HttpServletResponse resp, String lazadaOrderIdStr)
            throws IOException {
        String warehouseIdStr = req.getParameter("warehouseId");
        if (warehouseIdStr == null || warehouseIdStr.isBlank()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            writeError(resp, "Thiếu tham số warehouseId");
            return;
        }
        int warehouseId;
        try {
            warehouseId = Integer.parseInt(warehouseIdStr);
        } catch (NumberFormatException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            writeError(resp, "warehouseId không hợp lệ");
            return;
        }

        LazadaOrder order = orderDAO.findByLazadaOrderIdStr(lazadaOrderIdStr);
        if (order == null) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            writeError(resp, "Không tìm thấy đơn hàng");
            return;
        }

        Map<String, Object> result = processingService.validateStockForApproval(lazadaOrderIdStr, warehouseId);
        writeJson(resp, result);
    }

    private void handleProviders(HttpServletResponse resp) throws IOException {
        List<LazadaShipmentProvider> providers = providerDAO.findAllActive();
        writeJson(resp, providers);
    }

    private void handleOrderCounts(HttpServletResponse resp) throws IOException {
        Map<String, Integer> counts = new HashMap<>();
        counts.put("pendingApproval", orderDAO.countPendingApproval());
        counts.put("pendingPack", orderDAO.countPendingPack());
        counts.put("pendingRts", orderDAO.countPendingRts());
        writeJson(resp, counts);
    }

    private String extractOrderIdFromPath(String pathInfo) {
        // pathInfo like /123456789/stock-check -> extract 123456789
        String[] parts = pathInfo.split("/");
        if (parts.length >= 2) {
            return parts[1];
        }
        return "";
    }

    private void writeJson(HttpServletResponse resp, Object data) throws IOException {
        try (PrintWriter w = resp.getWriter()) {
            w.print(MAPPER.writeValueAsString(data));
        }
    }

    private void writeError(HttpServletResponse resp, String message) throws IOException {
        Map<String, Object> err = new HashMap<>();
        err.put("success", false);
        err.put("message", message);
        writeJson(resp, err);
    }

    private void handleManualSyncOrder(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String orderId = req.getParameter("orderId");
        if (orderId == null || orderId.trim().isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            writeError(resp, "Thiếu tham số orderId.");
            return;
        }

        try {
            com.wms.service.lazada.LazadaWebhookService webhookService = new com.wms.service.lazada.LazadaWebhookService();
            com.wms.model.Order order = webhookService.upsertOrderFromWebhook(orderId.trim(), MAPPER.createObjectNode());
            if (order != null) {
                resp.setStatus(HttpServletResponse.SC_OK);
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("message", "Đồng bộ đơn hàng " + orderId + " thành công!");
                result.put("orderCode", order.getOrderCode());
                result.put("status", order.getStatus());
                writeJson(resp, result);
            } else {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                writeError(resp, "Không thể đồng bộ đơn hàng từ Lazada. Vui lòng kiểm tra lại ID đơn hàng.");
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Manual sync order failed: " + orderId, e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            writeError(resp, "Lỗi khi đồng bộ đơn hàng: " + e.getMessage());
        }
    }
}
