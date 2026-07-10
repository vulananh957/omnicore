package com.wms.service.lazada;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Uploads a binary image to a publicly-reachable host and returns the permanent
 * public HTTPS URL. Used as a relay when Lazada cannot reach internal / ngrok
 * server URLs during /images/migrate.
 *
 * Tries three hosts in order:
 *   1. imgBB  (https://api.imgbb.com/1/upload) — free, reliable, requires API key
 *   2. Catbox (https://catbox.moe/user/api.php) — anonymous, rate-limited
 *   3. Litterbox (https://litterbox.catbox.moe)   — temporary host, for testing
 */
public class CatboxImageUploader {

    private static final Logger LOGGER = Logger.getLogger(CatboxImageUploader.class.getName());

    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS    = 30_000;

    private final String imgbbApiKey;

    public CatboxImageUploader() {
        this.imgbbApiKey = resolveImgbbKey();
    }

    /** Constructor for unit testing with a mock key. */
    public CatboxImageUploader(String imgbbApiKey) {
        this.imgbbApiKey = imgbbApiKey;
    }

    private static String resolveImgbbKey() {
        String catalinaBase = System.getProperty("catalina.base", "");
        java.io.File keysFile = new java.io.File(catalinaBase, "conf/lazada-keys.properties");
        if (keysFile.exists()) {
            try (InputStream is = new FileInputStream(keysFile)) {
                java.util.Properties p = new java.util.Properties();
                p.load(is);
                String k = p.getProperty("lazada.imgbb.api_key");
                if (k != null && !k.isBlank()) return k.trim();
            } catch (IOException ignored) { }
        }
        String prop = System.getProperty("lazada.imgbb.key");
        if (prop != null && !prop.isBlank()) return prop.trim();
        String env = System.getenv("IMGBB_API_KEY");
        if (env != null && !env.isBlank()) return env.trim();
        return null;
    }

    /**
     * @param imageBytes raw JPEG/PNG/GIF/WebP bytes
     * @param filename   filename for Content-Disposition header
     * @return public HTTPS URL on success, or null on all failures
     */
    public String upload(byte[] imageBytes, String filename) {
        // 1. Try imgBB (most reliable for programmatic use)
        if (imgbbApiKey != null && !imgbbApiKey.isBlank()) {
            String result = uploadImgbb(imageBytes, filename);
            if (result != null) return result;
        } else {
            LOGGER.warning("CatboxImageUploader: imgBB API key not configured, skipping");
        }

        // 2. Try Catbox permanent host
        String result = uploadCatbox(imageBytes, filename);
        if (result != null) return result;

        // 3. Try Litterbox temporary host (requires time limit)
        result = uploadLitterbox(imageBytes, filename);
        return result;
    }

    // ── imgBB ──────────────────────────────────────────────────────────────────

    private String uploadImgbb(byte[] imageBytes, String filename) {
        HttpURLConnection conn = null;
        try {
            String b64 = Base64.getEncoder().encodeToString(imageBytes);
            String encodedB64 = URLEncoder.encode(b64, StandardCharsets.UTF_8.toString());
            String body = "image=" + encodedB64;

            URL url = new URL("https://api.imgbb.com/1/upload?key=" + imgbbApiKey);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);

            byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
            conn.setRequestProperty("Content-Length", String.valueOf(bodyBytes.length));
            try (OutputStream os = conn.getOutputStream()) {
                os.write(bodyBytes);
                os.flush();
            }

            int code = conn.getResponseCode();
            String resp = readResponse(conn, code);
            if (code >= 200 && code < 300) {
                String imageUrl = extractImgbbUrl(resp);
                if (imageUrl != null) {
                    LOGGER.info("CatboxImageUploader[imgBB]: success => " + imageUrl);
                    return imageUrl;
                }
                LOGGER.warning("CatboxImageUploader[imgBB]: HTTP 200 but no URL in response");
            } else {
                LOGGER.warning("CatboxImageUploader[imgBB]: HTTP " + code + " body=" + truncate(resp, 200));
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "CatboxImageUploader[imgBB]: upload failed", e);
        } finally {
            if (conn != null) conn.disconnect();
        }
        return null;
    }

    private String extractImgbbUrl(String json) {
        int start = json.indexOf("\"url\":\"");
        if (start < 0) return null;
        start += 7;
        int end = json.indexOf("\"", start);
        if (end < 0) return null;
        return json.substring(start, end).replace("\\/", "/");
    }

    // ── Catbox permanent ───────────────────────────────────────────────────────

    private String uploadCatbox(byte[] imageBytes, String filename) {
        // Use the file upload endpoint (not the user/api.php which requires auth)
        return doMultipartUpload("https://catbox.moe/file/api.php", imageBytes, filename);
    }

    // ── Litterbox temporary ───────────────────────────────────────────────────

    private String uploadLitterbox(byte[] imageBytes, String filename) {
        // Litterbox requires a "time" param: 1h, 24h, 72h
        String postBody = "--LitterboxBoundary\r\n"
                + "Content-Disposition: form-data; name=\"reqtype\"\r\n\r\nfileupload\r\n"
                + "--LitterboxBoundary\r\n"
                + "Content-Disposition: form-data; name=\"time\"\r\n\r\n72h\r\n"
                + "--LitterboxBoundary\r\n"
                + "Content-Disposition: form-data; name=\"fileToUpload\"; filename=\""
                + filename + "\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n";
        return doRawUpload("https://litterbox.catbox.moe/resources/internals/api.php",
                postBody, imageBytes);
    }

    // ── Low-level HTTP helpers ────────────────────────────────────────────────

    private String doMultipartUpload(String uploadUrl, byte[] imageBytes, String filename) {
        String boundary = "----Boundary" + System.currentTimeMillis();
        String postBody = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"reqtype\"\r\n\r\nfileupload\r\n"
                + "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"fileToUpload\"; filename=\""
                + filename + "\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n";
        return doRawUpload(uploadUrl, postBody, imageBytes, boundary);
    }

    private String doRawUpload(String uploadUrl, String prefix, byte[] imageBytes) {
        String boundary = "----Boundary" + System.currentTimeMillis();
        return doRawUpload(uploadUrl, prefix, imageBytes, boundary);
    }

    private String doRawUpload(String uploadUrl, String prefix, byte[] imageBytes, String boundary) {
        HttpURLConnection conn = null;
        try {
            // Build body: prefix + image bytes + suffix
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            body.write(prefix.getBytes(StandardCharsets.UTF_8));
            body.write(imageBytes);
            body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            byte[] bodyBytes = body.toByteArray();

            URL url = new URL(uploadUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            conn.setRequestProperty("Content-Length", String.valueOf(bodyBytes.length));
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(bodyBytes);
                os.flush();
            }

            int code = conn.getResponseCode();
            String resp = readResponse(conn, code);
            if (code >= 200 && code < 300 && resp.startsWith("https://")) {
                LOGGER.info("CatboxImageUploader: success => " + resp.trim());
                return resp.trim();
            }
            LOGGER.warning("CatboxImageUploader: " + uploadUrl + " => HTTP " + code
                    + " contentType=" + conn.getContentType() + " body=" + truncate(resp, 200));
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "CatboxImageUploader: upload failed to " + uploadUrl, e);
        } finally {
            if (conn != null) conn.disconnect();
        }
        return null;
    }

    private String readResponse(HttpURLConnection conn, int code) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream(),
                StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line.trim());
            return sb.toString();
        }
    }

    private static String truncate(String s, int n) {
        return s == null || s.length() <= n ? s : s.substring(0, n) + "...";
    }
}
