package com.wms.controller.admin;

import com.wms.controller.BaseController;
import com.wms.mockshipping.MockShippingService;
import com.wms.model.Channel;
import com.wms.service.sales.ChannelService;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * ChannelConfigServlet — Handles UC-SYS03 (Channel Create/Edit) configuration screen.
 *
 * Routing via pathInfo:
 *   GET  /admin/channels/create        → empty form (create mode)
 *   POST /admin/channels/create        → save new channel
 *   GET  /admin/channels/create?id=5   → pre-populated form (edit mode)
 *   POST /admin/channels/create?id=5   → update existing channel
 */
public class ChannelConfigServlet extends BaseController {

    private final ChannelService channelService = new ChannelService();
    private final MockShippingService mockShippingService = new MockShippingService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String channelIdStr = req.getParameter("id");

        if (channelIdStr != null && !channelIdStr.trim().isEmpty()) {
            int channelId = parseId(channelIdStr);
            Channel channel = null;
            try {
                channel = channelService.findById(channelId);
            } catch (Exception e) {
                // fallback
            }
            if (channel != null) {
                req.setAttribute("channel", channel);
                req.setAttribute("isEditMode", true);
                req.setAttribute("pageTitle", "Chỉnh sửa Kênh Kết Nối");
                req.setAttribute("pageSubtitle", "Cập nhật cấu hình kênh: " + channel.getChannelName());
            } else {
                req.setAttribute("isEditMode", false);
                req.setAttribute("pageTitle", "Thêm Kênh Kết Nối Mới");
                req.setAttribute("pageSubtitle", "Thiết lập kết nối API, Xác thực và Đồng bộ tồn kho với Sàn TMĐT");
            }
        } else {
            req.setAttribute("isEditMode", false);
            req.setAttribute("pageTitle", "Thêm Kênh Kết Nối Mới");
            req.setAttribute("pageSubtitle", "Thiết lập kết nối API, Xác thực và Đồng bộ tồn kho với Sàn TMĐT");
        }

        req.setAttribute("mockShippingEnabled", mockShippingService.isEnabled());
        req.setAttribute("currentPage", "admin-channels-create");
        req.setAttribute("contentPage", "/WEB-INF/views/admin/channel-create.jsp");
        req.getRequestDispatcher("/WEB-INF/views/layout/admin-layout.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String action = req.getParameter("action");
        if ("testConnection".equals(action)) {
            handleTestConnection(req, resp);
            return;
        }
        if ("generateKey".equals(action)) {
            handleGenerateKey(req, resp);
            return;
        }
        if ("toggleMockShipping".equals(action)) {
            handleToggleMockShipping(req, resp);
            return;
        }

        String channelIdStr = req.getParameter("channelId");
        boolean isEdit = channelIdStr != null && !channelIdStr.trim().isEmpty();
        int channelId = isEdit ? parseId(channelIdStr) : -1;

        if (isEdit && channelId <= 0) {
            resp.sendRedirect(req.getContextPath() + "/admin/channels?status=error&message=invalid_channel_id");
            return;
        }

        Channel channel = bindChannel(req, isEdit, channelId);

        boolean success;
        try {
            if (isEdit) {
                success = channelService.updateChannel(channel);
            } else {
                success = channelService.createChannel(channel);
            }
        } catch (Exception e) {
            success = false;
        }

        if (success) {
            resp.sendRedirect(req.getContextPath() + "/admin/channels?status=" + (isEdit ? "updated" : "success"));
        } else {
            req.setAttribute("channel", channel);
            req.setAttribute("errorMessage", "Không thể lưu cấu hình kênh bán hàng vào cơ sở dữ liệu.");
            doGet(req, resp);
        }
    }

    /**
     * Generates a cryptographically secure random token for api_key or app_secret.
     * Called via AJAX from channel-create.jsp when platform = Website.
     */
    private void handleGenerateKey(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String type = req.getParameter("type"); // "apiKey" | "appSecret"
        String value = generateSecureToken(type);
        writeJson(resp, "{\"success\":true,\"value\":\"" + value + "\"}");
    }

    private String generateSecureToken(String type) {
        byte[] bytes = new byte[24];
        new java.security.SecureRandom().nextBytes(bytes);
        String hex = java.util.HexFormat.of().formatHex(bytes).toUpperCase();
        String prefix = "appSecret".equals(type) ? "OCW-SEC-" : "OCW-KEY-";
        return prefix + hex;
    }

    /**
     * Toggles website.mock_shipping.enabled — a system_config value, not a column on the
     * channel row, so this is a standalone AJAX action independent of the channel save form.
     */
    private void handleToggleMockShipping(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        boolean enabled = "true".equals(req.getParameter("enabled"));
        boolean ok = mockShippingService.setEnabled(enabled);
        writeJson(resp, "{\"success\":" + ok + "}");
    }

    private void handleTestConnection(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        ChannelService.TestConnectionResult result = channelService.testConnection(
            req.getParameter("apiKey"),
            req.getParameter("appSecret"),
            req.getParameter("authCode")
        );

        if (result.isSuccess()) {
            writeJson(resp, "{\"success\":true,\"message\":\"" + escapeJson(result.getMessage()) + "\","
                + "\"accessToken\":\"" + escapeJson(result.getAccessToken()) + "\","
                + "\"refreshToken\":\"" + escapeJson(result.getRefreshToken()) + "\"}");
        } else {
            writeJson(resp, "{\"success\":false,\"message\":\"" + escapeJson(result.getMessage()) + "\"}");
        }
    }



    private Channel bindChannel(HttpServletRequest req, boolean isEdit, int channelId) {
        Channel channel = new Channel();

        if (isEdit) {
            channel.setChannelId(channelId);
            try {
                channel = channelService.bindChannelFormParams(channel, true);
            } catch (Exception e) {
                // fallback
            }
        } else {
            channel.setBufferStock(5.0);
        }

        channel.setPlatform(req.getParameter("platform"));
        channel.setChannelName(req.getParameter("channelName"));
        channel.setApiUrl(req.getParameter("apiUrl"));

        String activeStr = req.getParameter("isActive");
        channel.setActive("true".equals(activeStr) || "1".equals(activeStr));

        channel.setApiKey(req.getParameter("apiKey"));

        String appSecretParam = req.getParameter("appSecret");
        if (appSecretParam != null && !appSecretParam.trim().isEmpty()) {
            channel.setAppSecret(appSecretParam);
        }

        channel.setWebhookSecret(req.getParameter("webhookSecret"));
        channel.setWebhookCallbackUrl(req.getParameter("webhookCallbackUrl"));

        String accessTokenParam = req.getParameter("accessToken");
        if (accessTokenParam != null && !accessTokenParam.trim().isEmpty()) {
            channel.setAccessToken(accessTokenParam);
        }

        String refreshTokenParam = req.getParameter("refreshToken");
        if (refreshTokenParam != null && !refreshTokenParam.trim().isEmpty()) {
            channel.setRefreshToken(refreshTokenParam);
        }

        // Apply platform-specific normalization (business rule belongs in service)
        channelService.normalizeChannel(channel);

        return channel;
    }

    private int parseId(String idStr) {
        try {
            return Integer.parseInt(idStr.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
