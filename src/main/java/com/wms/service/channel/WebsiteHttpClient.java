package com.wms.service.channel;

import com.wms.dao.ChannelDAO;
import com.wms.model.Channel;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * HTTP client for communicating with the omnicore-web storefront API.
 * Handles HMAC-SHA256 request signing using credentials from the Website channel.
 */
public class WebsiteHttpClient {

    private static final Logger LOG = Logger.getLogger(WebsiteHttpClient.class.getName());
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ChannelDAO channelDAO = new ChannelDAO();

    /**
     * Sends a POST request with HMAC-SHA256 signature to the Website storefront.
     *
     * @param apiPath e.g. "/api/v1/products" or "/api/v1/categories"
     * @param jsonBody JSON payload to transmit
     * @return response body if successful, null otherwise
     */
    public String post(String apiPath, String jsonBody) {
        return execute("POST", apiPath, jsonBody);
    }

    /**
     * Sends a PUT request with HMAC-SHA256 signature.
     */
    public String put(String apiPath, String jsonBody) {
        return execute("PUT", apiPath, jsonBody);
    }

    /**
     * Sends a DELETE request with HMAC-SHA256 signature.
     */
    public String delete(String apiPath) {
        return execute("DELETE", apiPath, "");
    }

    /**
     * Sends a GET request with HMAC-SHA256 signature.
     */
    public String get(String apiPath) {
        return execute("GET", apiPath, "");
    }

    private String execute(String method, String apiPath, String body) {
        Channel channel = channelDAO.findByPlatform("Website");
        if (channel == null || !channel.isActive()) {
            LOG.warning("Website storefront channel is not configured or inactive. Skipping sync.");
            return null;
        }

        String rawUrl = channel.getApiUrl();
        if (rawUrl == null || rawUrl.isBlank()) {
            LOG.warning("Website storefront channel API URL is empty.");
            return null;
        }

        // Standardize base URL
        String baseUrl = rawUrl.trim();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        String fullUrl = baseUrl + apiPath;

        String secretKey = channel.getAppSecret();
        if (secretKey == null || secretKey.isBlank()) {
            LOG.warning("Website storefront channel credential (app_secret) is missing.");
            return null;
        }

        long timestamp = System.currentTimeMillis() / 1000L;
        String signature = calculateHmac(secretKey, timestamp, method, apiPath, body);

        if (signature == null) {
            LOG.severe("Failed to calculate HMAC signature for Website request.");
            return null;
        }

        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(fullUrl))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json;charset=UTF-8")
                    .header("X-Timestamp", String.valueOf(timestamp))
                    .header("X-Signature", signature);

            if ("POST".equalsIgnoreCase(method)) {
                builder.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            } else if ("PUT".equalsIgnoreCase(method)) {
                builder.PUT(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            } else if ("DELETE".equalsIgnoreCase(method)) {
                builder.DELETE();
            } else {
                builder.GET();
            }

            HttpRequest request = builder.build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());

            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                return response.body();
            } else {
                LOG.log(Level.WARNING, "Website storefront API returned error status {0} for {1}. Response: {2}",
                        new Object[]{status, fullUrl, response.body()});
                return null;
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to connect to Website storefront API at " + fullUrl, e);
            return null;
        }
    }

    private String calculateHmac(String secret, long timestamp, String method, String path, String body) {
        try {
            String payload = body == null ? "" : body;
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));

            StringBuilder bodyHashHex = new StringBuilder();
            for (byte b : hash) {
                String hexString = Integer.toHexString(0x00FF & b);
                if (hexString.length() == 1) bodyHashHex.append('0');
                bodyHashHex.append(hexString);
            }

            String message = method.toUpperCase() + "\n" + path + "\n" + timestamp + "\n" + bodyHashHex.toString();

            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] rawHmac = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder();
            for (byte b : rawHmac) {
                String hexString = Integer.toHexString(0x00FF & b);
                if (hexString.length() == 1) {
                    hex.append('0');
                }
                hex.append(hexString);
            }
            return hex.toString();
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "HMAC-SHA256 calculation failed", e);
            return null;
        }
    }
}
