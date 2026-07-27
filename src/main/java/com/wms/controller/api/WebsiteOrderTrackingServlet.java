package com.wms.controller.api;

import com.wms.util.DBConnection;
import com.wms.util.JsonUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * GET /api/website/orders/{id}/tracking — Trả thông tin tracking cho khách.
 *
 * <p>Response: {tracking_code, shipment_provider, eta, tracking_url}
 * Nếu order chưa ship → trả null cho tracking_code, shipment_provider, eta.
 */
public class WebsiteOrderTrackingServlet extends BaseApiServlet {

    private static final Logger LOGGER = Logger.getLogger(WebsiteOrderTrackingServlet.class.getName());

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        if (authenticateAndReadBody(req, resp) == null) return;

        String pathInfo = req.getPathInfo(); // e.g. /123/tracking
        String[] parts = pathInfo == null ? null : pathInfo.replaceFirst("^/", "").split("/");
        if (parts == null || parts.length != 2 || !"tracking".equals(parts[1])) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Expected path /{orderId}/tracking");
            return;
        }

        int orderId;
        try {
            orderId = Integer.parseInt(parts[0]);
        } catch (NumberFormatException e) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid order id");
            return;
        }

        // Query: lấy tracking_no, shipment_provider, status từ orders table
        // (ETA không có sẵn, có thể mở rộng sau)
        String sql = "SELECT order_id, status, tracking_no, shipment_provider, web_order_ref " +
                     "FROM orders WHERE order_id = ? AND web_order_ref IS NOT NULL";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    sendError(resp, HttpServletResponse.SC_NOT_FOUND, "Order not found");
                    return;
                }

                String status = rs.getString("status");
                String trackingNo = rs.getString("tracking_no");
                String shipmentProvider = rs.getString("shipment_provider");

                Map<String, Object> data = new LinkedHashMap<>();
                data.put("order_id", orderId);
                data.put("status", status);

                // Nếu chưa ship, tracking_code và shipment_provider = null
                if (trackingNo != null && !trackingNo.isBlank()) {
                    data.put("tracking_code", trackingNo);
                    data.put("shipment_provider", shipmentProvider);
                    // ETA: chưa có source dữ liệu, có thể lấy từ carrier API sau
                    data.put("eta", null);
                    // Tracking URL: generate từ provider + code (ví dụ GHN, GHTK)
                    data.put("tracking_url", generateTrackingUrl(shipmentProvider, trackingNo));
                } else {
                    data.put("tracking_code", null);
                    data.put("shipment_provider", null);
                    data.put("eta", null);
                    data.put("tracking_url", null);
                }

                sendJson(resp, HttpServletResponse.SC_OK, data);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "WebsiteOrderTrackingServlet.doGet failed for orderId=" + orderId, e);
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Lỗi hệ thống");
        }
    }

    /**
     * Generate tracking URL dựa vào shipment provider.
     * Ví dụ: GHN → https://khachhang.ghn.vn/tracking/..., GHTK → https://track.ghtk.vn/?status_id=...
     */
    private String generateTrackingUrl(String provider, String trackingCode) {
        if (provider == null || provider.isBlank() || trackingCode == null || trackingCode.isBlank()) {
            return null;
        }

        String providerLower = provider.toLowerCase().trim();
        return switch (providerLower) {
            case "ghn" -> "https://khachhang.ghn.vn/tracking/" + trackingCode;
            case "ghtk" -> "https://track.ghtk.vn/?tracking_number=" + trackingCode;
            case "viettel" -> "https://tracking.viettelpost.vn/en/web/tracking/detail/" + trackingCode;
            case "jt" -> "https://tracker.jne.co.id/tracksolv/Tracking.htm?number=" + trackingCode;
            case "grab" -> "https://grab.com/track/shipments/" + trackingCode;
            case "shopee" -> "https://seller.shopee.vn/"; // Shopee không cung cấp tracking URL public
            default -> null;
        };
    }
}
