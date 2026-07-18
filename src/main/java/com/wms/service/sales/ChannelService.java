package com.wms.service.sales;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lazada.lazop.api.LazopClient;
import com.lazada.lazop.api.LazopRequest;
import com.lazada.lazop.api.LazopResponse;
import com.wms.dao.ChannelDAO;
import com.wms.model.Channel;
import com.wms.service.auth.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

public class ChannelService {

    private static final Logger log = LoggerFactory.getLogger(ChannelService.class);
    private static final String LAZADA_AUTH_URL = "https://auth.lazada.com/rest";

    private final ChannelDAO channelDAO = new ChannelDAO();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AuthService authService = new AuthService();

    public List<Channel> findAll() throws SQLException {
        return channelDAO.findAll();
    }

    public List<Channel> findAll(String keyword) throws SQLException {
        return channelDAO.findAll(keyword);
    }

    public Channel findById(int channelId) throws SQLException {
        return channelDAO.findById(channelId);
    }

    public boolean createChannel(Channel channel) throws SQLException {
        return channelDAO.insert(channel);
    }

    public boolean updateChannel(Channel channel) throws SQLException {
        return channelDAO.update(channel);
    }

    public boolean deleteChannel(int channelId) throws SQLException {
        return channelDAO.delete(channelId);
    }

    public String buildLazadaOAuthUrl(Channel channel, String ngrokUrl) {
        String baseUrl = ngrokUrl != null ? ngrokUrl : "https://omnicore.app";
        return baseUrl + "/lazada/callback?channel_id=" + channel.getChannelId();
    }

    public String getNgrokPublicUrl() {
        try {
            java.net.URL url = new java.net.URL("http://127.0.0.1:4040/api/tunnels");
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            conn.setRequestMethod("GET");
            if (conn.getResponseCode() == 200) {
                try (java.io.BufferedReader br = new java.io.BufferedReader(
                        new java.io.InputStreamReader(conn.getInputStream()))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) response.append(line);
                    JsonNode root = objectMapper.readTree(response.toString());
                    JsonNode tunnels = root.get("tunnels");
                    if (tunnels != null && tunnels.isArray()) {
                        for (JsonNode t : tunnels) {
                            if ("https".equals(t.path("proto").asText())) {
                                return t.path("public_url").asText();
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            log.debug("Could not retrieve ngrok URL: {}", e.getMessage());
        }
        return null;
    }

    public boolean updateLazadaTokens(int channelId, String accessToken, String refreshToken) throws SQLException {
        return channelDAO.updateLazadaTokens(channelId, accessToken, refreshToken);
    }

    /**
     * Updates Lazada tokens and their UTC expiry timestamp.
     */
    public boolean updateLazadaTokens(int channelId, String accessToken, String refreshToken,
                                      java.time.LocalDateTime tokenExpiresAt) throws SQLException {
        return channelDAO.updateLazadaTokens(channelId, accessToken, refreshToken, tokenExpiresAt);
    }

    /**
     * Refreshes the Lazada access token for a channel and persists the new tokens
     * along with the computed expiry timestamp.
     *
     * <p>Lazada access tokens typically expire after 86400 seconds (24 hours).
     * We store the expiry as a UTC timestamp and schedule the refresh to run
     * proactively before expiry, so there should be no disruption to active orders.
     *
     * @param channel The channel with apiKey, appSecret, apiUrl, and refreshToken set.
     * @return true if refresh succeeded and tokens were saved; false otherwise.
     */
    public boolean refreshLazadaToken(Channel channel) {
        if (channel.getRefreshToken() == null || channel.getRefreshToken().trim().isEmpty()) {
            log.warn("Channel '{}': no refresh token available, cannot refresh.", channel.getChannelName());
            return false;
        }
        try {
            log.info("Refreshing Lazada access token for channel '{}' (ID: {})",
                    channel.getChannelName(), channel.getChannelId());

            String jsonResponse = authService.refreshAccessToken(channel);

            com.fasterxml.jackson.databind.JsonNode root =
                    objectMapper.readTree(jsonResponse);

            if (root.has("code") && !root.has("access_token")) {
                log.error("Lazada token refresh API error [{}]: {}",
                        root.path("code").asText(), root.path("message").asText());
                return false;
            }

            String newAccessToken  = root.path("access_token").asText();
            String newRefreshToken = root.path("refresh_token").asText();
            int expiresIn          = root.path("expires_in").asInt(86400);

            java.time.LocalDateTime expiresAt = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC)
                    .plusSeconds(expiresIn);

            if (newAccessToken.isEmpty() || newRefreshToken.isEmpty()) {
                log.error("Token refresh response missing tokens for channel ID {}: {}",
                        channel.getChannelId(), jsonResponse);
                return false;
            }

            boolean saved = channelDAO.updateLazadaTokens(
                    channel.getChannelId(), newAccessToken, newRefreshToken, expiresAt);

            if (saved) {
                log.info("Token refresh SUCCESS for channel '{}'. New token expires at UTC {}.",
                        channel.getChannelName(), expiresAt);
            } else {
                log.error("Token refresh FAILED to persist for channel ID {}.",
                        channel.getChannelId());
            }
            return saved;

        } catch (Exception e) {
            log.error("Unexpected error refreshing token for channel '{}' (ID: {}): {}",
                    channel.getChannelName(), channel.getChannelId(), e.getMessage(), e);
            return false;
        }
    }

    /**
     * Normalises platform-specific fields before persisting a Channel.
     * Business rules:
     * - Website channels authenticate via HMAC (app_secret); api_key is unused
     *   and must be NULL to avoid storing a misleading generated-but-unused value.
     *
     * @param channel the channel to normalise in-place
     */
    public void normalizeChannel(Channel channel) {
        if ("Website".equals(channel.getPlatform())) {
            channel.setApiKey(null);
        }
    }

    public boolean updateBufferStock(int channelId, double bufferStock) throws SQLException {
        Channel channel = channelDAO.findById(channelId);
        if (channel == null) return false;
        channel.setBufferStock(bufferStock);
        return channelDAO.update(channel);
    }

    public TestConnectionResult testConnection(String apiKey, String appSecret, String authCode) {
        if (apiKey == null || apiKey.trim().isEmpty() || appSecret == null || appSecret.trim().isEmpty()) {
            return TestConnectionResult.failure("App Key và App Secret không được để trống!");
        }
        try {
            LazopClient client = new LazopClient(LAZADA_AUTH_URL, apiKey.trim(), appSecret.trim());
            LazopRequest request = new LazopRequest();
            request.setApiName("/auth/token/create");
            request.setHttpMethod("POST");
            request.addApiParameter("code", authCode != null ? authCode.trim() : "");
            LazopResponse response = client.execute(request, null);
            String body = response.getBody();

            if (body == null || body.trim().isEmpty()) {
                return TestConnectionResult.failure("Server Lazada không phản hồi. Vui lòng thử lại sau!");
            }
            if (body.contains("\"code\":\"ISV\"")) {
                return TestConnectionResult.failure("Lỗi hệ thống: thiếu tham số bắt buộc hoặc App Key không đúng!");
            }
            if (body.contains("\"code\":\"IncompleteSignature\"")) {
                return TestConnectionResult.failure("App Secret hoặc App Key không chính xác. Chữ ký bảo mật bị từ chối!");
            }
            if (body.contains("\"code\":\"InvalidCode\"")) {
                return TestConnectionResult.failure("Authorization Code không hợp lệ, đã được sử dụng hoặc đã hết hạn sau 30 phút!");
            }
            if (body.contains("\"code\":\"InvalidParameter\"")) {
                return TestConnectionResult.failure("Tham số không hợp lệ. Vui lòng kiểm tra App Key, App Secret và Authorization Code!");
            }
            if (body.contains("\"code\":\"MissingParameter\"")) {
                return TestConnectionResult.failure("Thiếu tham số bắt buộc (Authorization Code). Vui lòng nhập mã ủy quyền từ Lazada!");
            }
            if (body.contains("\"access_token\"")) {
                JsonNode root = objectMapper.readTree(body);
                String accessToken = root.path("access_token").asText();
                String refreshToken = root.path("refresh_token").asText();
                return TestConnectionResult.successTokens(accessToken, refreshToken,
                    "Kết nối thử nghiệm thành công! Vui lòng lưu cấu hình để hoàn tất.");
            }
            return TestConnectionResult.failure("Phản hồi không xác định từ Lazada. Vui lòng thử lại!");
        } catch (Exception e) {
            log.error("Error testing Lazada connection", e);
            return TestConnectionResult.failure("Lỗi kết nối: " + e.getMessage());
        }
    }

    public Channel bindChannelFormParams(Channel channel, boolean isEdit) throws SQLException {
        if (isEdit && channel.getChannelId() > 0) {
            Channel existing = channelDAO.findById(channel.getChannelId());
            if (existing != null) {
                channel.setBufferStock(existing.getBufferStock());
                channel.setAccessToken(existing.getAccessToken());
                channel.setRefreshToken(existing.getRefreshToken());
                channel.setTokenExpiresAt(existing.getTokenExpiresAt());
                channel.setAppSecret(existing.getAppSecret());
            }
        }
        if (!isEdit) {
            channel.setBufferStock(5.0);
        }
        return channel;
    }

    public static class TestConnectionResult {
        private final boolean success;
        private final String message;
        private final String accessToken;
        private final String refreshToken;

        private TestConnectionResult(boolean success, String message, String accessToken, String refreshToken) {
            this.success = success;
            this.message = message;
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
        }

        public static TestConnectionResult successTokens(String accessToken, String refreshToken, String message) {
            return new TestConnectionResult(true, message, accessToken, refreshToken);
        }

        public static TestConnectionResult failure(String message) {
            return new TestConnectionResult(false, message, null, null);
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public String getAccessToken() { return accessToken; }
        public String getRefreshToken() { return refreshToken; }
    }
}
