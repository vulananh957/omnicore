package com.wms.service.sales;

import com.wms.dao.SkuMappingDAO;
import com.wms.model.Product;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * SkuMappingSuggestService — Heuristic auto-suggestion for SKU mapping
 * (UC-B2C01, seller_sku → master sku_code).
 *
 * Lazada's seller_sku frequently equals our sku_code (or differs by a
 * trivial prefix/suffix). The Sales UI calls {@link #suggestForChannel(int)}
 * to get a ranked list of candidate matches; staff picks one and the system
 * persists it through the existing {@link SkuMappingService} flow.
 *
 * <p>Heuristics, in priority order:
 * <ol>
 *   <li>Exact case-insensitive equality (confidence 100%)</li>
 *   <li>One contains the other and length delta is small (70-95%)</li>
 *   <li>Same alphanumeric prefix of length >= 4 (50-80%)</li>
 *   <li>Edit distance <= 2 (40-60%)</li>
 * </ol>
 * Anything with confidence &lt; 40% is dropped.
 */
public class SkuMappingSuggestService {


    private final SkuMappingDAO skuMappingDAO = new SkuMappingDAO();

    /**
     * Generates suggestions for every unmapped seller_sku on this channel.
     *
     * @param channelId the channel whose exceptions we resolve
     * @return list of (external_sku, [suggestions]). Each external_sku appears
     *         at most once with its top suggestions.
     */
    public List<UnmappedWithSuggestions> suggestForChannel(int channelId) {
        // 1) Read unmapped exceptions
        List<Map<String, Object>> exceptions =
                new com.wms.dao.SkuMappingExceptionDAO().findUnresolved();

        // 2) Load all master SKUs once
        List<Product> masterSkus = skuMappingDAO.findAllSkus();

        List<UnmappedWithSuggestions> out = new ArrayList<>();
        for (Map<String, Object> exc : exceptions) {
            int excChannelId = (Integer) exc.get("channelId");
            if (excChannelId != channelId) continue;
            String external = (String) exc.get("externalSku");
            int    excId    = (Integer) exc.get("exceptionId");
            String orderCode= (String) exc.get("orderCode");

            List<Suggestion> suggestions = score(external, masterSkus);
            out.add(new UnmappedWithSuggestions(excId, external, orderCode, suggestions));
        }
        out.sort(Comparator.comparingInt(u -> u.externalSku == null ? 0 : -u.externalSku.length()));
        return out;
    }

    /** Generates suggestions for a single external SKU. */
    public List<Suggestion> suggestForSku(String externalSku, int limit) {
        List<Product> masterSkus = skuMappingDAO.findAllSkus();
        List<Suggestion> all = score(externalSku, masterSkus);
        if (all.size() > limit) return all.subList(0, limit);
        return all;
    }

    // ── Scoring ────────────────────────────────────────────────

    private List<Suggestion> score(String external, List<Product> candidates) {
        if (external == null || external.isEmpty() || candidates == null) {
            return java.util.Collections.emptyList();
        }
        String ext = external.trim().toLowerCase(Locale.ROOT);
        List<Suggestion> ranked = new ArrayList<>();
        for (Product p : candidates) {
            if (p.getSkuCode() == null) continue;
            String sku = p.getSkuCode().trim().toLowerCase(Locale.ROOT);
            int score = 0;
            String reason = "";
            if (ext.equals(sku)) {
                score = 100; reason = "exact match";
            } else if (ext.contains(sku) || sku.contains(ext)) {
                int overlap = Math.min(ext.length(), sku.length());
                int span    = Math.max(ext.length(), sku.length());
                score = 70 + (int) (30.0 * overlap / span);
                reason = "substring match";
            } else if (ext.length() >= 4 && sku.length() >= 4
                    && ext.substring(0, 4).equals(sku.substring(0, 4))) {
                score = 55 + (int) (25.0 * (1.0 - Math.abs(ext.length() - sku.length()) / 12.0));
                reason = "shared 4-char prefix";
            } else {
                int d = levenshtein(ext, sku);
                if (d <= 2) {
                    score = 40 + (20 - 10 * d);
                    reason = "edit distance " + d;
                }
            }
            if (score >= 40) {
                ranked.add(new Suggestion(p.getProductId(), p.getSkuCode(),
                        p.getProductName(), score, reason));
            }
        }
        ranked.sort((a, b) -> Integer.compare(b.confidence, a.confidence));
        return ranked;
    }

    /** Standard Levenshtein distance. */
    private static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur  = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev; prev = cur; cur = tmp;
        }
        return prev[b.length()];
    }

    // ── DTOs ──────────────────────────────────────────────────

    public static final class UnmappedWithSuggestions {
        public final int exceptionId;
        public final String externalSku;
        public final String orderCode;
        public final List<Suggestion> suggestions;
        public UnmappedWithSuggestions(int exceptionId, String externalSku,
                                       String orderCode, List<Suggestion> suggestions) {
            this.exceptionId = exceptionId;
            this.externalSku = externalSku;
            this.orderCode = orderCode;
            this.suggestions = suggestions;
        }
    }

    public static final class Suggestion {
        public final int productId;
        public final String skuCode;
        public final String productName;
        public final int confidence;   // 0..100
        public final String reason;
        public Suggestion(int productId, String skuCode, String productName,
                          int confidence, String reason) {
            this.productId = productId;
            this.skuCode = skuCode;
            this.productName = productName;
            this.confidence = confidence;
            this.reason = reason;
        }
    }
}
