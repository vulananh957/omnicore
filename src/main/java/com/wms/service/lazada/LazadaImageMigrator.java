package com.wms.service.lazada;

import com.wms.dao.ImageMigrationDAO;
import com.wms.model.Channel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * LazadaImageMigrator — UC-B2C09 helper that turns external image URLs into
 * Lazada-CDN URLs via {@code POST /images/migrate}, with a cache layer over
 * {@code product_image_migrations}.
 *
 * <p>Pure helper (no DB connection, no HTTP) — calls {@link ChannelGateway}
 * for the HTTP request and {@link ImageMigrationDAO} for the cache. Lazada
 * caps a single batch at ~8 URLs; this helper chunks larger inputs.</p>
 *
 * <p>Returns image URLs in the same order as the input. Cached hits skip
 * the HTTP call entirely.</p>
 */
public class LazadaImageMigrator {

    /** Lazada recommends ≤8 URLs per {@code /images/migrate} call. */
    public static final int MAX_BATCH_SIZE = 8;

    private static final Logger LOGGER = Logger.getLogger(LazadaImageMigrator.class.getName());

    private final ImageMigrationDAO migrationDAO = new ImageMigrationDAO();

    /**
     * Migrates a single URL to Lazada CDN, bypassing the cache.
     * Used for freshly-uploaded images (e.g. catbox relay URLs) where caching
     * is not beneficial.
     *
     * @return Lazada CDN URL on success, null on failure
     */
    public String migrateSingle(Channel channel, String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isBlank()) return null;
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>"
                   + "<Request><Image><Url>" + xmlEscape(sourceUrl) + "</Url></Image></Request>";
        Map<String, String> params = Map.of("payload", xml);
        LazadaHttpClient http = new LazadaHttpClient();
        String response = http.executePost("/image/migrate", params, channel);
        String lazadaUrl = extractSingleImageUrl(response);
        if (lazadaUrl != null && !lazadaUrl.isBlank()) {
            return lazadaUrl;
        }
        LOGGER.warning("LazadaImageMigrator.migrateSingle failed for " + sourceUrl + " => " + truncate(response, 200));
        return null;
    }

    /**
     * Migrates the given external URLs to Lazada's CDN. Returns a list of
     * Lazada image URLs in the same order as {@code externalUrls}. URLs that
     * fail to migrate surface as empty strings (the caller decides whether
     * to abort or proceed).
     *
     * @param channel      the marketplace channel (for OAuth + signing)
     * @param externalUrls source image URLs (HTTPS only)
     * @return Lazada image URLs in matching order
     */
    public List<String> migrateImages(Channel channel, List<String> externalUrls) {
        List<String> out = new ArrayList<>();
        if (externalUrls == null || externalUrls.isEmpty()) return out;

        // Step 1: filter out blanks
        List<String> cleaned = new ArrayList<>();
        for (String u : externalUrls) {
            if (u != null && !u.isBlank()) cleaned.add(u.trim());
        }
        if (cleaned.isEmpty()) return out;

        // Step 2: check cache — already-migrated URLs skip the API call
        Map<String, ImageMigrationDAO.MigrationRecord> cached =
                migrationDAO.findCachedUrls(channel.getChannelId(), cleaned);

        // Build result skeleton (preserve input order)
        // Use LinkedHashMap for stable order.
        Map<String, String> resultBySource = new LinkedHashMap<>();
        for (String u : cleaned) resultBySource.put(u, "");

        List<String> toMigrate = new ArrayList<>();
        for (String u : cleaned) {
            ImageMigrationDAO.MigrationRecord r = cached.get(u);
            if (r != null && r.lazadaImageUrl != null && !r.lazadaImageUrl.isBlank()) {
                resultBySource.put(u, r.lazadaImageUrl);
            } else {
                toMigrate.add(u);
            }
        }

        if (toMigrate.isEmpty()) {
            out.addAll(resultBySource.values());
            return out;
        }

        // Step 3: migrate one URL at a time via POST /image/migrate (XML payload)
        // Lazada VN endpoint returns data.image.url inline — no polling needed.
        com.wms.service.lazada.LazadaHttpClient http = new com.wms.service.lazada.LazadaHttpClient();
        for (int i = 0; i < toMigrate.size(); i++) {
            String source = toMigrate.get(i);
            String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>"
                       + "<Request><Image><Url>" + xmlEscape(source) + "</Url></Image></Request>";
            Map<String, String> params = java.util.Map.of("payload", xml);
            String response = http.executePost("/image/migrate", params, channel);
            String lazadaUrl = extractSingleImageUrl(response);
            String imageId = extractSingleImageHash(response);
            if (lazadaUrl != null && !lazadaUrl.isBlank()) {
                resultBySource.put(source, lazadaUrl);
                migrationDAO.upsert(channel.getChannelId(), source, lazadaUrl, imageId);
            } else {
                LOGGER.warning("LazadaImageMigrator: /image/migrate failed for "
                        + source + " => " + truncate(response, 200));
            }
        }

        out.addAll(resultBySource.values());
        return out;
    }

    private static String xmlEscape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static String extractSingleImageUrl(String response) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper m = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode n = m.readTree(response);
            if (!"0".equals(n.path("code").asText())) return null;
            String url = n.path("data").path("image").path("url").asText();
            return url.isEmpty() ? null : url;
        } catch (Exception e) { return null; }
    }

    private static String extractSingleImageHash(String response) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper m = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode n = m.readTree(response);
            String hash = n.path("data").path("image").path("hash_code").asText();
            return hash.isEmpty() ? null : hash;
        } catch (Exception e) { return null; }
    }

    private static String truncate(String s, int n) {
        return s == null || s.length() <= n ? s : s.substring(0, n) + "...";
    }
}
