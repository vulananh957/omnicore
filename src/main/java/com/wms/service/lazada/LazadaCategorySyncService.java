package com.wms.service.lazada;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.dao.LazadaCategoryDAO;
import com.wms.model.Channel;
import com.wms.model.LazadaCategory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * LazadaCategorySyncService — calls /category/tree/get and mirrors the
 * tree into the local DB. Lazada caps nested levels arbitrarily; we recurse.
 */
public class LazadaCategorySyncService {
    private static final Logger LOGGER = Logger.getLogger(LazadaCategorySyncService.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final LazadaCategoryDAO dao = new LazadaCategoryDAO();

    public SyncResult syncCategories(Channel channel) {
        try {
            LazadaHttpClient http = new LazadaHttpClient();
            String body = http.executeGet("/category/tree/get",
                    Map.of("language_code", "vi_VN"), channel);
            JsonNode root = MAPPER.readTree(body);
            String code = root.path("code").asText();
            if (!"0".equals(code)) {
                return new SyncResult(false, "Lazada từ chối: " + body, 0);
            }
            JsonNode data = root.path("data");
            if (!data.isArray()) {
                return new SyncResult(false, "Response không có data[]: " + body, 0);
            }
            List<LazadaCategory> flat = new ArrayList<>();
            for (JsonNode top : data) walk(top, null, 0, flat);

            boolean ok = dao.replaceAll(channel.getChannelId(), flat);
            LOGGER.info("LazadaCategorySyncService: stored " + flat.size()
                    + " categories for channel " + channel.getChannelId());
            return new SyncResult(ok, ok ? "OK" : "DB write failed", flat.size());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "LazadaCategorySyncService failed", e);
            return new SyncResult(false, e.toString(), 0);
        }
    }

    private void walk(JsonNode node, Long parentId, int depth, List<LazadaCategory> out) {
        if (node == null || !node.isObject()) return;
        String name = node.path("name").asText();
        if (name.isBlank()) return; // skip empty names (occasional gaps in Lazada tree)
        LazadaCategory c = new LazadaCategory();
        c.setLazadaCategoryId(node.path("category_id").asLong(0));
        c.setParentId(parentId);
        c.setName(name);
        c.setLeaf(node.path("leaf").asBoolean(false));
        c.setHasVariation(node.path("var").asBoolean(false));
        c.setDepth(depth);
        out.add(c);

        JsonNode children = node.path("children");
        if (children.isArray()) {
            for (JsonNode ch : children) walk(ch, c.getLazadaCategoryId(), depth + 1, out);
        }
    }

    public static class SyncResult {
        public final boolean success;
        public final String message;
        public final int count;
        public SyncResult(boolean s, String m, int c) {
            this.success = s; this.message = m; this.count = c;
        }
    }
}