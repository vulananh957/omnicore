package com.wms.dao;

import com.wms.model.ChannelProduct;
import com.wms.util.DBConnection;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ChannelProductDAO — Data Access Object for channel-specific product listings.
 */
public class ChannelProductDAO {

    private static final Logger LOGGER = Logger.getLogger(ChannelProductDAO.class.getName());

    private static final String SELECT_BASE =
        "SELECT cp.id, cp.channel_id, cp.product_id, cp.channel_sku_code, "
        + "cp.channel_price, cp.channel_stock, cp.status, cp.listed_at, cp.updated_at, "
        + "cp.channel_item_id, cp.lazada_sku_id, cp.last_push_qty, cp.last_push_at, "
        + "cp.last_error_code, cp.last_error_message, cp.lazada_category_id, cp.brand_id, "
        + "cp.dimensions AS cp_dimensions, cp.weight_kg AS cp_weight_kg, "
        + "cp.seller_sku, cp.short_description, cp.brand, cp.description, "
        + "c.channel_name, c.platform, "
        + "p.sku_code, p.product_name, p.attributes_text AS product_dimensions, p.weight_kg AS product_weight_kg "
        + "FROM channel_products cp "
        + "LEFT JOIN channels c ON cp.channel_id = c.channel_id "
        + "LEFT JOIN products p ON cp.product_id = p.product_id";

    /**
     * Retrieves all channel products with enriched channel and product info.
     */
    public List<ChannelProduct> findAll() {
        List<ChannelProduct> list = new ArrayList<>();
        String sql = SELECT_BASE + " ORDER BY cp.updated_at DESC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapResultSetToChannelProduct(rs));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "ChannelProductDAO: Failed to retrieve all channel products", e);
        }
        return list;
    }

    /**
     * Finds a single channel product by its primary key.
     */
    public ChannelProduct findById(int id) {
        String sql = SELECT_BASE + " WHERE cp.id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToChannelProduct(rs);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "ChannelProductDAO: Failed to find channel product by ID " + id, e);
        }
        return null;
    }

    /**
     * Retrieves all channel products for a given channel.
     */
    public List<ChannelProduct> findByChannel(int channelId) {
        List<ChannelProduct> list = new ArrayList<>();
        String sql = SELECT_BASE + " WHERE cp.channel_id = ? ORDER BY cp.updated_at DESC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, channelId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToChannelProduct(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "ChannelProductDAO: Failed to find channel products by channel " + channelId, e);
        }
        return list;
    }

    /**
     * Retrieves all channel products for a given product.
     */
    public List<ChannelProduct> findByProduct(int productId) {
        List<ChannelProduct> list = new ArrayList<>();
        String sql = SELECT_BASE + " WHERE cp.product_id = ? ORDER BY cp.updated_at DESC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToChannelProduct(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "ChannelProductDAO: Failed to find channel products by product " + productId, e);
        }
        return list;
    }

    /**
     * Retrieves all channel products filtered by status.
     */
    public List<ChannelProduct> findByStatus(String status) {
        List<ChannelProduct> list = new ArrayList<>();
        String sql = SELECT_BASE + " WHERE cp.status = ? ORDER BY cp.updated_at DESC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToChannelProduct(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "ChannelProductDAO: Failed to find channel products by status " + status, e);
        }
        return list;
    }

    /**
     * Inserts a new channel product record.
     */
    public boolean insert(ChannelProduct cp) {
        String sql = "INSERT INTO channel_products (channel_id, product_id, channel_sku_code, "
                   + "channel_price, channel_stock, status, listed_at, lazada_category_id, brand_id, "
                   + "dimensions, weight_kg, seller_sku, short_description, brand, description) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, cp.getChannelId());
            ps.setInt(2, cp.getProductId());
            ps.setString(3, cp.getChannelSkuCode());
            ps.setBigDecimal(4, cp.getChannelPrice() != null ? cp.getChannelPrice() : BigDecimal.ZERO);
            ps.setBigDecimal(5, cp.getChannelStock() != null ? cp.getChannelStock() : BigDecimal.ZERO);
            ps.setString(6, cp.getStatus() != null ? cp.getStatus() : "ACTIVE");
            ps.setTimestamp(7, cp.getListedAt() != null ? Timestamp.valueOf(cp.getListedAt()) : null);
            ps.setObject(8, cp.getLazadaCategoryId());
            ps.setObject(9, cp.getBrandId());
            ps.setString(10, cp.getDimensions());
            ps.setObject(11, cp.getWeightKg());
            ps.setString(12, cp.getSellerSku());
            ps.setString(13, cp.getShortDescription());
            ps.setString(14, cp.getBrand());
            ps.setString(15, cp.getDescription());

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "ChannelProductDAO: Failed to insert channel product", e);
            return false;
        }
    }

    /**
     * Updates an existing channel product record.
     */
    public boolean update(ChannelProduct cp) {
        String sql = "UPDATE channel_products SET "
                   + "channel_id = ?, product_id = ?, channel_sku_code = ?, "
                   + "channel_price = ?, channel_stock = ?, status = ?, updated_at = CURRENT_TIMESTAMP, "
                   + "lazada_category_id = ?, brand_id = ?, dimensions = ?, weight_kg = ?, "
                   + "seller_sku = ?, short_description = ?, brand = ?, description = ? "
                   + "WHERE id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, cp.getChannelId());
            ps.setInt(2, cp.getProductId());
            ps.setString(3, cp.getChannelSkuCode());
            ps.setBigDecimal(4, cp.getChannelPrice() != null ? cp.getChannelPrice() : BigDecimal.ZERO);
            ps.setBigDecimal(5, cp.getChannelStock() != null ? cp.getChannelStock() : BigDecimal.ZERO);
            ps.setString(6, cp.getStatus());
            ps.setObject(7, cp.getLazadaCategoryId());
            ps.setObject(8, cp.getBrandId());
            ps.setString(9, cp.getDimensions());
            ps.setObject(10, cp.getWeightKg());
            ps.setString(11, cp.getSellerSku());
            ps.setString(12, cp.getShortDescription());
            ps.setString(13, cp.getBrand());
            ps.setString(14, cp.getDescription());
            ps.setInt(15, cp.getId());

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "ChannelProductDAO: Failed to update channel product " + cp.getId(), e);
            return false;
        }
    }

    /**
     * Syncs the channel product price from WMS.
     */
    public boolean syncPrice(int channelProductId, BigDecimal newPrice) {
        String sql = "UPDATE channel_products SET "
                   + "channel_price = ?, status = 'ACTIVE', updated_at = CURRENT_TIMESTAMP "
                   + "WHERE id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBigDecimal(1, newPrice);
            ps.setInt(2, channelProductId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "ChannelProductDAO: Failed to sync price for " + channelProductId, e);
            return false;
        }
    }

    /**
     * Syncs the channel product stock from WMS.
     */
    public boolean syncStock(int channelProductId, BigDecimal newStock) {
        String sql = "UPDATE channel_products SET "
                   + "channel_stock = ?, status = 'ACTIVE', updated_at = CURRENT_TIMESTAMP "
                   + "WHERE id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBigDecimal(1, newStock);
            ps.setInt(2, channelProductId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "ChannelProductDAO: Failed to sync stock for " + channelProductId, e);
            return false;
        }
    }

    /**
     * Updates the last_synced timestamp for a channel product.
     */
    public boolean updateLastSynced(int channelProductId) {
        String sql = "UPDATE channel_products SET updated_at = CURRENT_TIMESTAMP WHERE id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, channelProductId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "ChannelProductDAO: Failed to update last synced for " + channelProductId, e);
            return false;
        }
    }

    /**
     * Lazada end-to-end: looks up a channel product row by the platform-side
     * SKU string (Lazada's seller_sku / item_id). Used during order sync to
     * map marketplace items back to our internal product_id.
     */
    public ChannelProduct findByChannelSku(int channelId, String channelSkuCode) {
        if (channelSkuCode == null || channelSkuCode.trim().isEmpty()) return null;
        String sql = SELECT_BASE + " WHERE cp.channel_id = ? AND cp.channel_sku_code = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, channelId);
            ps.setString(2, channelSkuCode.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapResultSetToChannelProduct(rs);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "ChannelProductDAO.findByChannelSku failed", e);
        }
        return null;
    }

    public ChannelProduct findByProductAndChannel(int productId, int channelId) {
        String sql = SELECT_BASE + " WHERE cp.product_id = ? AND cp.channel_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            ps.setInt(2, channelId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapResultSetToChannelProduct(rs);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "ChannelProductDAO.findByProductAndChannel failed", e);
        }
        return null;
    }

    /** Store Lazada item_id after a successful product push. Idempotent. */
    public boolean setChannelItemId(int channelProductId, String channelItemId,
                                    String lazadaSkuId) {
        String sql = "UPDATE channel_products SET "
                   + "channel_item_id = COALESCE(NULLIF(?, ''), channel_item_id), "
                   + "lazada_sku_id   = COALESCE(NULLIF(?, ''), lazada_sku_id), "
                   + "updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, channelItemId);
            ps.setString(2, lazadaSkuId);
            ps.setInt(3, channelProductId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "ChannelProductDAO.setChannelItemId failed", e);
            return false;
        }
    }

    /** Record a stock-push outcome (qty + timestamp). */
    public boolean recordStockPush(int channelProductId, BigDecimal pushedQty) {
        String sql = "UPDATE channel_products SET "
                   + "last_push_qty = ?, last_push_at = CURRENT_TIMESTAMP, "
                   + "channel_stock = ?, status = 'ACTIVE', "
                   + "updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBigDecimal(1, pushedQty);
            ps.setBigDecimal(2, pushedQty);
            ps.setInt(3, channelProductId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "ChannelProductDAO.recordStockPush failed", e);
            return false;
        }
    }

    private ChannelProduct mapResultSetToChannelProduct(ResultSet rs) throws SQLException {
        ChannelProduct cp = new ChannelProduct();
        cp.setId(rs.getInt("id"));
        cp.setChannelId(rs.getInt("channel_id"));
        cp.setProductId(rs.getInt("product_id"));
        cp.setChannelSkuCode(rs.getString("channel_sku_code"));
        cp.setChannelPrice(rs.getBigDecimal("channel_price"));
        cp.setChannelStock(rs.getBigDecimal("channel_stock"));
        cp.setStatus(rs.getString("status"));

        Timestamp listedAt = rs.getTimestamp("listed_at");
        if (listedAt != null) {
            cp.setListedAt(listedAt.toLocalDateTime());
        }

        Timestamp updatedAt = rs.getTimestamp("updated_at");
        if (updatedAt != null) {
            cp.setUpdatedAt(updatedAt.toLocalDateTime());
        }

        cp.setChannelName(rs.getString("channel_name"));
        cp.setChannelPlatform(rs.getString("platform"));
        cp.setSkuCode(rs.getString("sku_code"));
        cp.setProductName(rs.getString("product_name"));

        // UC-B2C09: Lazada push tracking fields (use wasNull to preserve NULL semantics)
        try {
            cp.setChannelItemId(rs.getString("channel_item_id"));
            cp.setLazadaSkuId(rs.getString("lazada_sku_id"));
            cp.setLastPushQty(rs.getBigDecimal("last_push_qty"));
            Timestamp lastPushAt = rs.getTimestamp("last_push_at");
            if (lastPushAt != null) cp.setLastPushAt(lastPushAt.toLocalDateTime());
            cp.setLastErrorCode(rs.getString("last_error_code"));
            cp.setLastErrorMessage(rs.getString("last_error_message"));
            long lzCat = rs.getLong("lazada_category_id");
            cp.setLazadaCategoryId(rs.wasNull() ? null : lzCat);
            long bid = rs.getLong("brand_id");
            cp.setBrandId(rs.wasNull() ? null : bid);
            // Prefer channel-level overrides; fall back to product master data
            cp.setDimensions(rs.getString("cp_dimensions"));
            if (cp.getDimensions() == null || cp.getDimensions().isBlank()) {
                cp.setDimensions(rs.getString("product_dimensions"));
            }
            double w = rs.getDouble("cp_weight_kg");
            if (!rs.wasNull()) {
                cp.setWeightKg(w);
            } else {
                double pw = rs.getDouble("product_weight_kg");
                if (!rs.wasNull()) cp.setWeightKg(pw);
            }
            cp.setSellerSku(rs.getString("seller_sku"));
            cp.setShortDescription(rs.getString("short_description"));
            cp.setBrand(rs.getString("brand"));
            cp.setDescription(rs.getString("description"));
        } catch (SQLException e) {
            // Columns may not exist on very old DB before migration; tolerate silently.
            LOGGER.log(Level.FINE, "ChannelProductDAO.mapRow: Lazada push columns not yet migrated", e);
        }

        return cp;
    }

    // ── UC-B2C09: Push success / failure tracking ────────────────────────

    /** Record a successful push (item_id + sku_id + qty + last_push_at). */
    public boolean recordPushSuccess(int channelProductId, String channelItemId,
                                     String lazadaSkuId, BigDecimal pushedQty) {
        String sql = "UPDATE channel_products SET "
                   + "channel_item_id = COALESCE(NULLIF(?, ''), channel_item_id), "
                   + "lazada_sku_id   = COALESCE(NULLIF(?, ''), lazada_sku_id), "
                   + "last_push_qty = ?, last_push_at = CURRENT_TIMESTAMP, "
                   + "last_error_code = NULL, last_error_message = NULL, "
                   + "status = 'ACTIVE', updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, channelItemId);
            ps.setString(2, lazadaSkuId);
            ps.setBigDecimal(3, pushedQty);
            ps.setInt(4, channelProductId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "ChannelProductDAO.recordPushSuccess failed for id=" + channelProductId, e);
            return false;
        }
    }

    /** Record a push failure (store error code + translated VI message). */
    public boolean recordPushFailure(int channelProductId, String errorCode, String errorMessage) {
        String sql = "UPDATE channel_products SET "
                   + "last_error_code = ?, last_error_message = ?, "
                   + "updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, errorCode != null && errorCode.length() > 50
                    ? errorCode.substring(0, 50) : errorCode);
            ps.setString(2, errorMessage != null && errorMessage.length() > 500
                    ? errorMessage.substring(0, 500) : errorMessage);
            ps.setInt(3, channelProductId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "ChannelProductDAO.recordPushFailure failed for id=" + channelProductId, e);
            return false;
        }
    }

    /**
     * UC-B2C09: sets the Lazada leaf category chosen by the wizard so
     * pushProduct builds a payload with a valid Lazada PrimaryCategory.
     * Idempotent — safe to call repeatedly.
     */
    public boolean updateLazadaCategoryId(int productId, int channelId, long lazadaCategoryId) {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                "UPDATE channel_products SET lazada_category_id = ? "
                + "WHERE product_id = ? AND channel_id = ?")) {
            ps.setLong(1, lazadaCategoryId);
            ps.setInt(2, productId);
            ps.setInt(3, channelId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING,
                "ChannelProductDAO.updateLazadaCategoryId failed product=" + productId
                + " channel=" + channelId, e);
            return false;
        }
    }

    public boolean updateBrandId(int productId, int channelId, long brandId) {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                "UPDATE channel_products SET brand_id = ? "
                + "WHERE product_id = ? AND channel_id = ?")) {
            ps.setLong(1, brandId);
            ps.setInt(2, productId);
            ps.setInt(3, channelId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING,
                "ChannelProductDAO.updateBrandId failed product=" + productId
                + " channel=" + channelId, e);
            return false;
        }
    }

    /**
     * Deletes a channel product record by its primary key.
     */
    public boolean delete(int id) {
        String sql = "DELETE FROM channel_products WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "ChannelProductDAO: Failed to delete channel product by ID " + id, e);
            return false;
        }
    }
}
