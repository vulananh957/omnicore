package com.wms.controller.api;

import com.wms.dao.ChannelDAO;
import com.wms.model.Channel;
import com.wms.util.JsonUtil;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * BaseApiServlet — shared base for the storefront-facing API (com.wms.controller.api),
 * consumed by omnicore-web's OmnicoreApiService. Verifies HMAC-SHA256 requests instead of
 * the session auth used everywhere else (see AuthFilter.isPublicPath() carve-out for
 * /api/categories, /api/products, /api/inventory, /api/website/).
 *
 * Signed message format (must match omnicore-web's HmacSigner exactly):
 *   METHOD + "\n" + PATH_WITH_QUERY + "\n" + TIMESTAMP + "\n" + SHA256_HEX(BODY)
 * BODY is the raw request body ("" for GET). Timestamp is epoch seconds, rejected if more
 * than 300s away from server time (anti-replay).
 */
public abstract class BaseApiServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(BaseApiServlet.class.getName());
    private static final String PLATFORM = "OwnWebsite";
    private static final long MAX_CLOCK_SKEW_SECONDS = 300;

    private final ChannelDAO channelDAO = new ChannelDAO();

    /**
     * Verifies X-Timestamp/X-Signature against the channel's app_secret. On success returns
     * the raw request body (empty string for GET) so subclasses can parse it without reading
     * the request stream a second time. On failure, writes the 401 response itself and
     * returns null — callers must check for null and return immediately.
     */
    protected String authenticateAndReadBody(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String timestampHeader = req.getHeader("X-Timestamp");
        String signatureHeader = req.getHeader("X-Signature");
        if (timestampHeader == null || signatureHeader == null) {
            sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Missing X-Timestamp or X-Signature header");
            return null;
        }

        long timestamp;
        try {
            timestamp = Long.parseLong(timestampHeader);
        } catch (NumberFormatException e) {
            sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Invalid X-Timestamp header");
            return null;
        }
        long now = System.currentTimeMillis() / 1000L;
        if (Math.abs(now - timestamp) > MAX_CLOCK_SKEW_SECONDS) {
            sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Request timestamp outside allowed window");
            return null;
        }

        Channel channel = channelDAO.findByPlatform(PLATFORM);
        if (channel == null || channel.getAppSecret() == null || channel.getAppSecret().isBlank()) {
            LOGGER.warning("BaseApiServlet: no active '" + PLATFORM + "' channel with app_secret configured");
            sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Storefront channel not configured");
            return null;
        }

        String body = readBody(req);
        // omnicore-web signs the path relative to its configured base URL (which already
        // includes the context path, e.g. http://localhost:8080/wms-hub) — so we must sign
        // the context-relative path here too, same as AuthFilter's path stripping.
        String contextRelativePath = req.getRequestURI().substring(req.getContextPath().length());
        String pathWithQuery = contextRelativePath
                + (req.getQueryString() != null ? "?" + req.getQueryString() : "");

        String expected;
        try {
            expected = sign(channel.getAppSecret(), req.getMethod(), pathWithQuery, timestamp, body);
        } catch (NoSuchAlgorithmException e) {
            LOGGER.log(Level.SEVERE, "BaseApiServlet: HMAC algorithm unavailable", e);
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Signature verification unavailable");
            return null;
        }

        if (!constantTimeEquals(expected, signatureHeader)) {
            sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Invalid signature");
            return null;
        }

        return body;
    }

    private String readBody(HttpServletRequest req) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            char[] buf = new char[1024];
            int n;
            while ((n = reader.read(buf)) != -1) {
                sb.append(buf, 0, n);
            }
        }
        return sb.toString();
    }

    private String sign(String secret, String method, String pathWithQuery, long timestamp, String body)
            throws NoSuchAlgorithmException {
        String bodyHash = sha256Hex(body);
        String message = method.toUpperCase() + "\n" + pathWithQuery + "\n" + timestamp + "\n" + bodyHash;
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(raw);
        } catch (java.security.InvalidKeyException e) {
            throw new IllegalStateException("BaseApiServlet: invalid HMAC key", e);
        }
    }

    private String sha256Hex(String input) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        return java.util.HexFormat.of().formatHex(hash);
    }

    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }

    protected void sendJson(HttpServletResponse resp, int status, Object data) throws IOException {
        Map<String, Object> envelope = new HashMap<>();
        envelope.put("success", true);
        envelope.put("data", data);
        resp.setStatus(status);
        resp.setContentType("application/json;charset=UTF-8");
        try (PrintWriter w = resp.getWriter()) {
            w.print(JsonUtil.getMapper().writeValueAsString(envelope));
        }
    }

    protected void sendError(HttpServletResponse resp, int status, String message) throws IOException {
        Map<String, Object> envelope = new HashMap<>();
        envelope.put("success", false);
        envelope.put("message", message);
        resp.setStatus(status);
        resp.setContentType("application/json;charset=UTF-8");
        try (PrintWriter w = resp.getWriter()) {
            w.print(JsonUtil.getMapper().writeValueAsString(envelope));
        }
    }
}
