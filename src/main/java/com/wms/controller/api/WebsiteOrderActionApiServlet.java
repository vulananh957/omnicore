package com.wms.controller.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.wms.dao.OrderDAO;
import com.wms.dao.RmaDAO;
import com.wms.util.JsonUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * POST /api/website/order-actions/{orderId}/confirm-received — customer confirms receipt
 *      early (before the 7-day auto-complete window closes).
 * POST /api/website/order-actions/{orderId}/return — customer requests a return within the
 *      7-day window (reason + base64 photo/video evidence) → creates a pending rma_requests
 *      row for Sales to review.
 *
 * Both actions are gated to orders with web_order_ref IS NOT NULL — see OrderDAO's
 * *ByOrderId methods. Lazada/Shopee/TikTok orders are unaffected.
 */
public class WebsiteOrderActionApiServlet extends BaseApiServlet {

    private static final Logger LOGGER = Logger.getLogger(WebsiteOrderActionApiServlet.class.getName());

    /** Same convention as PublishImageServlet — outside webapp/, survives redeploy. */
    private static final Path EVIDENCE_ROOT =
            Paths.get(System.getProperty("user.home"), "wms-uploads", "return-evidence");
    private static final long MAX_TOTAL_EVIDENCE_BYTES = 20L * 1024 * 1024; // 20MB decoded total
    private static final int RETURN_WINDOW_DAYS = 7;

    private final OrderDAO orderDAO = new OrderDAO();
    private final RmaDAO rmaDAO = new RmaDAO();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String body = authenticateAndReadBody(req, resp);
        if (body == null) return;

        String pathInfo = req.getPathInfo(); // e.g. /123/confirm-received
        String[] parts = pathInfo == null ? null : pathInfo.replaceFirst("^/", "").split("/");
        if (parts == null || parts.length != 2) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST,
                    "Expected path /{orderId}/confirm-received or /{orderId}/return");
            return;
        }

        int orderId;
        try {
            orderId = Integer.parseInt(parts[0]);
        } catch (NumberFormatException e) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid order id");
            return;
        }
        String subAction = parts[1];

        switch (subAction) {
            case "confirm-received" -> handleConfirmReceived(resp, orderId);
            case "return" -> handleReturn(resp, orderId, body);
            default -> sendError(resp, HttpServletResponse.SC_NOT_FOUND, "Unknown action: " + subAction);
        }
    }

    private void handleConfirmReceived(HttpServletResponse resp, int orderId) throws IOException {
        Map<String, Object> info = orderDAO.findDeliveryInfoById(orderId);
        if (info == null) {
            sendError(resp, HttpServletResponse.SC_NOT_FOUND, "Order not found");
            return;
        }
        if (!"DELIVERED".equals(info.get("status"))) {
            sendError(resp, HttpServletResponse.SC_CONFLICT,
                    "Chỉ xác nhận đã nhận khi đơn đang ở trạng thái Vận chuyển thành công.");
            return;
        }
        if (!orderDAO.markCompletedByOrderId(orderId)) {
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Không thể cập nhật trạng thái");
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("order_id", orderId);
        data.put("status", "COMPLETED");
        sendJson(resp, HttpServletResponse.SC_OK, data);
    }

    private void handleReturn(HttpServletResponse resp, int orderId, String body) throws IOException {
        Map<String, Object> info = orderDAO.findDeliveryInfoById(orderId);
        if (info == null) {
            sendError(resp, HttpServletResponse.SC_NOT_FOUND, "Order not found");
            return;
        }
        String status = (String) info.get("status");
        if (!"DELIVERED".equals(status) && !"COMPLETED".equals(status)) {
            sendError(resp, HttpServletResponse.SC_CONFLICT,
                    "Chỉ yêu cầu hoàn trả khi đơn đã giao hoặc đã nhận.");
            return;
        }
        LocalDateTime deliveredAt = (LocalDateTime) info.get("deliveredAt");
        if (deliveredAt == null || LocalDateTime.now().isAfter(deliveredAt.plusDays(RETURN_WINDOW_DAYS))) {
            sendError(resp, HttpServletResponse.SC_CONFLICT,
                    "Đã quá hạn 7 ngày kể từ khi giao hàng, không thể yêu cầu hoàn trả.");
            return;
        }
        if (rmaDAO.hasPendingRequest(orderId)) {
            sendError(resp, HttpServletResponse.SC_CONFLICT, "Đơn đã có yêu cầu hoàn trả đang chờ duyệt.");
            return;
        }

        JsonNode node;
        try {
            node = JsonUtil.getMapper().readTree(body);
        } catch (Exception e) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid JSON body");
            return;
        }
        String reason = node.path("reason").asText(null);
        if (reason == null || reason.isBlank()) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "reason is required");
            return;
        }
        JsonNode photosNode = node.path("photos");
        String videoBase64 = node.path("video").asText(null);

        List<String> photoBase64List = new ArrayList<>();
        if (photosNode.isArray()) {
            for (JsonNode p : photosNode) photoBase64List.add(p.asText(""));
        }

        long totalBytes = 0;
        for (String p : photoBase64List) totalBytes += approxDecodedSize(p);
        if (videoBase64 != null) totalBytes += approxDecodedSize(videoBase64);
        if (totalBytes > MAX_TOTAL_EVIDENCE_BYTES) {
            sendError(resp, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                    "Tổng dung lượng ảnh/video vượt quá 20MB");
            return;
        }

        List<String> photoUrls;
        String videoUrl;
        try {
            photoUrls = saveEvidenceFiles(photoBase64List, "jpg");
            videoUrl = videoBase64 == null || videoBase64.isBlank()
                    ? null : saveEvidenceFiles(List.of(videoBase64), "mp4").get(0);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "WebsiteOrderActionApiServlet: failed to save evidence orderId=" + orderId, e);
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Không thể lưu ảnh/video bằng chứng");
            return;
        }

        String rmaCode = "RMA-" + orderId + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        boolean inserted = rmaDAO.insert(orderId, rmaCode, reason, String.join(",", photoUrls), videoUrl);
        if (!inserted) {
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Không thể tạo yêu cầu hoàn trả");
            return;
        }
        if (!orderDAO.markDisputedByOrderId(orderId)) {
            LOGGER.warning("WebsiteOrderActionApiServlet: rma_requests inserted but order status update failed, orderId=" + orderId);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("order_id", orderId);
        data.put("rma_code", rmaCode);
        data.put("status", "DISPUTED");
        sendJson(resp, HttpServletResponse.SC_CREATED, data);
    }

    private long approxDecodedSize(String base64) {
        if (base64 == null || base64.isBlank()) return 0;
        int comma = base64.indexOf(',');
        String raw = comma >= 0 ? base64.substring(comma + 1) : base64; // strip a data: URL prefix if present
        return (long) raw.length() * 3 / 4;
    }

    private List<String> saveEvidenceFiles(List<String> base64List, String ext) throws IOException {
        List<String> urls = new ArrayList<>();
        Files.createDirectories(EVIDENCE_ROOT);
        for (String base64 : base64List) {
            if (base64 == null || base64.isBlank()) continue;
            int comma = base64.indexOf(',');
            String raw = comma >= 0 ? base64.substring(comma + 1) : base64;
            byte[] bytes = Base64.getDecoder().decode(raw);
            String name = UUID.randomUUID().toString().replace("-", "") + "." + ext;
            Path target = EVIDENCE_ROOT.resolve(name);
            Files.write(target, bytes);
            urls.add("/return-evidence/" + name);
        }
        return urls;
    }
}
