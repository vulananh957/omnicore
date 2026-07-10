package com.wms.service.lazada;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Uploads a binary image to imgBB (https://api.imgbb.com) and returns the
 * permanent public HTTPS image URL.
 *
 * API: POST https://api.imgbb.com/1/image
 *      key=YOUR_API_KEY&image=BASE64_OR_URL
 *
 * Free tier: 1.5MB/image, no rate-limit for moderate usage.
 * imgBB URLs are publicly accessible and work with Lazada /images/migrate.
 *
 * The API key is read from the "imgbb_api_key" init parameter in web.xml,
 * falling back to the system property "lazada.imgbb.key".
 */
public class ImgbbImageUploader {

    private static final Logger LOGGER = Logger.getLogger(ImgbbImageUploader.class.getName());
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS    = 30_000;

    // imgBB allows up to 32MB on free tier, but we cap at 10MB for safety
    private static final int MAX_IMAGE_BYTES = 10 * 1024 * 1024;

    private final String apiKey;

    public ImgbbImageUploader() {
        this.apiKey = resolveApiKey();
    }

    /** Visible constructor for unit testing with a mock key. */
    public ImgbbImageUploader(String apiKey) {
        this.apiKey = apiKey;
    }

    private static String resolveApiKey() {
        // 1. conf/lazada-keys.properties (CATALINA_BASE/conf/) — persistent, survives redeploys
        String catalinaBase = System.getProperty("catalina.base", "");
        File keysFile = new File(catalinaBase, "conf/lazada-keys.properties");
        if (keysFile.exists()) {
            try (InputStream is = new FileInputStream(keysFile)) {
                Properties p = new Properties();
                p.load(is);
                String k = p.getProperty("lazada.imgbb.api_key");
                if (k != null && !k.isBlank()) return k.trim();
            } catch (IOException ignored) { }
        }
        // 2. System property (set via -Dlazada.imgbb.key=... on Tomcat startup)
        String prop = System.getProperty("lazada.imgbb.key");
        if (prop != null && !prop.isBlank()) return prop.trim();
        // 3. Environment variable
        String env = System.getenv("IMGBB_API_KEY");
        if (env != null && !env.isBlank()) return env.trim();
        return null;
    }

    /**
     * @param imageBytes raw JPEG/PNG/GIF/WebP bytes
     * @param filename   filename for logging clarity
     * @return public HTTPS image URL on success, or null on failure
     */
    public String upload(byte[] imageBytes, String filename) {
        if (apiKey == null || apiKey.isBlank()) {
            LOGGER.warning("ImgbbImageUploader: no API key configured (set -Dlazada.imgbb.key=... or IMGBB_API_KEY env var)");
            return null;
        }
        if (imageBytes == null || imageBytes.length == 0) {
            LOGGER.warning("ImgbbImageUploader: empty image bytes for " + filename);
            return null;
        }
        if (imageBytes.length > MAX_IMAGE_BYTES) {
            LOGGER.warning("ImgbbImageUploader: image too large " + imageBytes.length + " bytes for " + filename);
            return null;
        }

        LOGGER.info("ImgbbImageUploader: uploading " + imageBytes.length + " bytes for " + filename);

        HttpURLConnection conn = null;
        try {
            URL url = new URL("https://api.imgbb.com/1/upload?key=" + apiKey);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);

            // Encode image as standard base64 (no line wraps)
            String b64 = java.util.Base64.getEncoder().encodeToString(imageBytes);

            String body = "image=" + URLEncoder.encode(b64, StandardCharsets.UTF_8.toString());
            byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
            conn.setRequestProperty("Content-Length", String.valueOf(bodyBytes.length));

            try (OutputStream os = conn.getOutputStream()) {
                os.write(bodyBytes);
                os.flush();
            }

            int code = conn.getResponseCode();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(
                    code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream(),
                    StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                String resp = sb.toString();

                if (code >= 200 && code < 300) {
                    String imageUrl = extractUrl(resp);
                    if (imageUrl != null && !imageUrl.isBlank()) {
                        LOGGER.info("ImgbbImageUploader: success => " + imageUrl);
                        return imageUrl;
                    }
                    LOGGER.warning("ImgbbImageUploader: HTTP 200 but no URL in response: " + truncate(resp, 300));
                    return null;
                }
                LOGGER.warning("ImgbbImageUploader: HTTP " + code + " body=" + truncate(resp, 300));
                return null;
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "ImgbbImageUploader: upload failed for " + filename, e);
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String extractUrl(String json) {
        // Manual extraction to avoid adding a Jackson dependency to this class
        int urlStart = json.indexOf("\"url\":\"");
        if (urlStart < 0) return null;
        urlStart += 7; // skip past "url":""
        int urlEnd = json.indexOf("\"", urlStart);
        if (urlEnd < 0) return null;
        String rawUrl = json.substring(urlStart, urlEnd);
        return rawUrl.replace("\\/", "/");
    }

    private static String truncate(String s, int n) {
        return s == null || s.length() <= n ? s : s.substring(0, n) + "...";
    }
}
