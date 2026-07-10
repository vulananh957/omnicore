package com.wms.dao;

import com.wms.util.DBConnection;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * PricingConfigDAO — Read/Update pricing-related rows in the {@code system_config} table.
 *
 * <p>Stores editable runtime thresholds (e.g. pricing warning margins).
 * Kept intentionally small — only the pricing keys we currently need.</p>
 */
public class PricingConfigDAO {

    private static final Logger LOGGER = Logger.getLogger(PricingConfigDAO.class.getName());

    public static final String KEY_WARN_MARGIN_LOW       = "pricing.warn_margin_low";
    public static final String KEY_WARN_MARGIN_BREAKEVEN = "pricing.warn_margin_breakeven";
    public static final String KEY_WARN_MARGIN_LOSS      = "pricing.warn_margin_loss_threshold";

    /**
     * Reads a single config value by key. Returns null when missing.
     */
    public String getValue(String key) {
        if (key == null || key.isBlank()) return null;
        String sql = "SELECT config_value FROM system_config WHERE config_key = ? AND is_active = 1";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString(1);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "PricingConfigDAO.getValue failed key=" + key, e);
        }
        return null;
    }

    /**
     * Reads a config value as BigDecimal. Returns {@code fallback} on missing or parse error.
     */
    public BigDecimal getDecimal(String key, BigDecimal fallback) {
        String raw = getValue(key);
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            LOGGER.log(Level.WARNING, "PricingConfigDAO.getDecimal parse error key=" + key + " raw=" + raw, e);
            return fallback;
        }
    }

    /**
     * Reads all pricing warning thresholds in one round-trip.
     */
    public Map<String, BigDecimal> getAllPricingThresholds() {
        Map<String, BigDecimal> result = new HashMap<>();
        result.put(KEY_WARN_MARGIN_LOW,       getDecimal(KEY_WARN_MARGIN_LOW,       new BigDecimal("0.10")));
        result.put(KEY_WARN_MARGIN_BREAKEVEN, getDecimal(KEY_WARN_MARGIN_BREAKEVEN, new BigDecimal("0.00")));
        result.put(KEY_WARN_MARGIN_LOSS,      getDecimal(KEY_WARN_MARGIN_LOSS,      new BigDecimal("-0.05")));
        return result;
    }

    /**
     * Inserts or updates a config value. Returns true on success.
     */
    public boolean upsert(String key, String value, String description, Integer updatedBy) {
        if (key == null || key.isBlank()) return false;
        String sql = "INSERT INTO system_config (config_key, config_value, description, is_active, updated_by) "
                   + "VALUES (?, ?, ?, 1, ?) "
                   + "ON DUPLICATE KEY UPDATE config_value = VALUES(config_value), "
                   + "description = VALUES(description), updated_by = VALUES(updated_by), updated_at = CURRENT_TIMESTAMP";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setString(2, value != null ? value : "");
            ps.setString(3, description);
            if (updatedBy != null) ps.setInt(4, updatedBy); else ps.setNull(4, java.sql.Types.INTEGER);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "PricingConfigDAO.upsert failed key=" + key, e);
            return false;
        }
    }
}
