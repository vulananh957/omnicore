package com.wms.service.common;

import com.wms.dao.PricingConfigDAO;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * PricingConfigService — wrapper around {@link PricingConfigDAO} with a tiny in-memory TTL cache
 * so the JSP / AJAX endpoints don't hit MySQL on every keystroke.
 */
public class PricingConfigService {

    private static final long TTL_MILLIS = 60_000L; // 60 seconds

    private final PricingConfigDAO pricingConfigDAO = new PricingConfigDAO();

    private final Map<String, BigDecimal> pricingCache = new HashMap<>();
    private final Object lock = new Object();
    private long cachedAt = 0L;

    public BigDecimal getWarnMarginLow()       { return getPricing().get(PricingConfigDAO.KEY_WARN_MARGIN_LOW); }
    public BigDecimal getWarnMarginBreakeven() { return getPricing().get(PricingConfigDAO.KEY_WARN_MARGIN_BREAKEVEN); }
    public BigDecimal getWarnMarginLoss()      { return getPricing().get(PricingConfigDAO.KEY_WARN_MARGIN_LOSS); }

    public Map<String, BigDecimal> getPricing() {
        synchronized (lock) {
            long now = System.currentTimeMillis();
            if (cachedAt == 0L || (now - cachedAt) > TTL_MILLIS) {
                Map<String, BigDecimal> fresh = pricingConfigDAO.getAllPricingThresholds();
                pricingCache.clear();
                pricingCache.putAll(fresh);
                cachedAt = now;
            }
            return new HashMap<>(pricingCache);
        }
    }

    /**
     * Update all three pricing thresholds atomically.
     *
     * @return true if all three rows were updated successfully
     */
    public boolean updatePricing(BigDecimal marginLow, BigDecimal marginBreakeven,
                                  BigDecimal marginLoss, Integer updatedBy) {
        boolean ok = pricingConfigDAO.upsert(PricingConfigDAO.KEY_WARN_MARGIN_LOW, marginLow.toString(), "Margin dưới ngưỡng này được cảnh báo \"Lãi ít\"", updatedBy)
                  && pricingConfigDAO.upsert(PricingConfigDAO.KEY_WARN_MARGIN_BREAKEVEN, marginBreakeven.toString(), "Margin dưới ngưỡng này được cảnh báo \"Hoà vốn/Lỗ nhẹ\"", updatedBy)
                  && pricingConfigDAO.upsert(PricingConfigDAO.KEY_WARN_MARGIN_LOSS, marginLoss.toString(), "Margin dưới ngưỡng này được cảnh báo \"Bán lỗ\" - đỏ", updatedBy);
        if (ok) {
            // Invalidate cache immediately
            synchronized (lock) { cachedAt = 0L; }
        }
        return ok;
    }
}
