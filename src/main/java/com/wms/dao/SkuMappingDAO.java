package com.wms.dao;

import com.wms.model.Product;
import com.wms.model.SkuMapping;
import com.wms.util.DBConnection;

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
 * SkuMappingDAO — Data Access Object for SKU-to-channel mapping records.
 */
public class SkuMappingDAO {

    private static final Logger LOGGER = Logger.getLogger(SkuMappingDAO.class.getName());

    /**
     * Retrieves all SKU mappings with enriched channel and product info.
     */
    public List<SkuMapping> findAll() {
        List<SkuMapping> list = new ArrayList<>();
        String sql = "SELECT sm.mapping_id, sm.sku_id, sm.channel_id, sm.external_sku, "
                   + "sm.seller_sku, sm.sync_status, sm.last_sync_at, sm.created_at, sm.updated_at, "
                   + "c.channel_name, c.platform, s.sku_code, s.product_name, "
                   + "COALESCE(lc.name, cm.lazada_name) AS lazada_category_name "
                   + "FROM sku_mappings sm "
                   + "LEFT JOIN channels c ON sm.channel_id = c.channel_id "
                   + "LEFT JOIN products s ON sm.sku_id = s.product_id "
                   + "LEFT JOIN channel_products cp ON sm.sku_id = cp.product_id AND sm.channel_id = cp.channel_id "
                   + "LEFT JOIN lazada_categories lc ON cp.lazada_category_id = lc.lazada_category_id AND cp.channel_id = lc.channel_id "
                   + "LEFT JOIN category_mappings cm ON s.category_id = cm.wms_category_id AND sm.channel_id = cm.channel_id "
                   + "ORDER BY sm.created_at DESC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapResultSetToSkuMapping(rs));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "SkuMappingDAO: Failed to retrieve all mappings", e);
        }
        return list;
    }

    /**
     * Finds a single SKU mapping by its primary key.
     */
    public SkuMapping findById(int mappingId) {
        String sql = "SELECT sm.mapping_id, sm.sku_id, sm.channel_id, sm.external_sku, "
                   + "sm.seller_sku, sm.sync_status, sm.last_sync_at, sm.created_at, sm.updated_at, "
                   + "c.channel_name, c.platform, s.sku_code, s.product_name "
                   + "FROM sku_mappings sm "
                   + "LEFT JOIN channels c ON sm.channel_id = c.channel_id "
                   + "LEFT JOIN products s ON sm.sku_id = s.product_id "
                   + "WHERE sm.mapping_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, mappingId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToSkuMapping(rs);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "SkuMappingDAO: Failed to find mapping by ID " + mappingId, e);
        }
        return null;
    }

    /**
     * Retrieves all mappings for a given channel.
     */
    public List<SkuMapping> findByChannel(int channelId) {
        List<SkuMapping> list = new ArrayList<>();
        String sql = "SELECT sm.mapping_id, sm.sku_id, sm.channel_id, sm.external_sku, "
                   + "sm.seller_sku, sm.sync_status, sm.last_sync_at, sm.created_at, sm.updated_at, "
                   + "c.channel_name, c.platform, s.sku_code, s.product_name "
                   + "FROM sku_mappings sm "
                   + "LEFT JOIN channels c ON sm.channel_id = c.channel_id "
                   + "LEFT JOIN products s ON sm.sku_id = s.product_id "
                   + "WHERE sm.channel_id = ? "
                   + "ORDER BY sm.created_at DESC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, channelId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToSkuMapping(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "SkuMappingDAO: Failed to find mappings by channel " + channelId, e);
        }
        return list;
    }

    /**
     * Retrieves all mappings for a given product SKU.
     */
    public List<SkuMapping> findBySku(int skuId) {
        List<SkuMapping> list = new ArrayList<>();
        String sql = "SELECT sm.mapping_id, sm.sku_id, sm.channel_id, sm.external_sku, "
                   + "sm.seller_sku, sm.sync_status, sm.last_sync_at, sm.created_at, sm.updated_at, "
                   + "c.channel_name, c.platform, s.sku_code, s.product_name "
                   + "FROM sku_mappings sm "
                   + "LEFT JOIN channels c ON sm.channel_id = c.channel_id "
                   + "LEFT JOIN products s ON sm.sku_id = s.product_id "
                   + "WHERE sm.sku_id = ? "
                   + "ORDER BY sm.created_at DESC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, skuId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToSkuMapping(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "SkuMappingDAO: Failed to find mappings by SKU " + skuId, e);
        }
        return list;
    }

    /**
     * Retrieves all mappings filtered by sync status.
     */
    public List<SkuMapping> findByStatus(String syncStatus) {
        List<SkuMapping> list = new ArrayList<>();
        String sql = "SELECT sm.mapping_id, sm.sku_id, sm.channel_id, sm.external_sku, "
                   + "sm.seller_sku, sm.sync_status, sm.last_sync_at, sm.created_at, sm.updated_at, "
                   + "c.channel_name, c.platform, s.sku_code, s.product_name "
                   + "FROM sku_mappings sm "
                   + "LEFT JOIN channels c ON sm.channel_id = c.channel_id "
                   + "LEFT JOIN products s ON sm.sku_id = s.product_id "
                   + "WHERE sm.sync_status = ? "
                   + "ORDER BY sm.created_at DESC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, syncStatus);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToSkuMapping(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "SkuMappingDAO: Failed to find mappings by status " + syncStatus, e);
        }
        return list;
    }

    /**
     * Inserts a new SKU mapping record.
     */
    public boolean insert(SkuMapping mapping) {
        String sql = "INSERT INTO sku_mappings (sku_id, channel_id, external_sku, seller_sku, sync_status, last_sync_at) "
                   + "VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, mapping.getSkuId());
            ps.setInt(2, mapping.getChannelId());
            ps.setString(3, mapping.getExternalSku());
            ps.setString(4, mapping.getSellerSku());
            ps.setString(5, mapping.getSyncStatus() != null ? mapping.getSyncStatus() : "PENDING");

            Timestamp lastSync = null;
            if (mapping.getLastSyncAt() != null) {
                lastSync = Timestamp.valueOf(mapping.getLastSyncAt());
            }
            ps.setTimestamp(6, lastSync);

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "SkuMappingDAO: Failed to insert mapping", e);
            return false;
        }
    }

    /**
     * Updates an existing SKU mapping record.
     */
    public boolean update(SkuMapping mapping) {
        String sql = "UPDATE sku_mappings SET "
                   + "sku_id = ?, channel_id = ?, external_sku = ?, seller_sku = ?, "
                   + "sync_status = ?, last_sync_at = ?, updated_at = CURRENT_TIMESTAMP "
                   + "WHERE mapping_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, mapping.getSkuId());
            ps.setInt(2, mapping.getChannelId());
            ps.setString(3, mapping.getExternalSku());
            ps.setString(4, mapping.getSellerSku());
            ps.setString(5, mapping.getSyncStatus());
            ps.setTimestamp(6, mapping.getLastSyncAt() != null ? Timestamp.valueOf(mapping.getLastSyncAt()) : null);
            ps.setInt(7, mapping.getMappingId());

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "SkuMappingDAO: Failed to update mapping " + mapping.getMappingId(), e);
            return false;
        }
    }

    /**
     * Deletes a SKU mapping by its primary key.
     */
    public boolean delete(int mappingId) {
        String sql = "DELETE FROM sku_mappings WHERE mapping_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, mappingId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "SkuMappingDAO: Failed to delete mapping " + mappingId, e);
            return false;
        }
    }

    /**
     * Updates the sync status and last sync timestamp for a mapping.
     */
    public boolean updateSyncStatus(int mappingId, String syncStatus) {
        String sql = "UPDATE sku_mappings SET sync_status = ?, last_sync_at = CURRENT_TIMESTAMP, "
                   + "updated_at = CURRENT_TIMESTAMP WHERE mapping_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, syncStatus);
            ps.setInt(2, mappingId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "SkuMappingDAO: Failed to update sync status for " + mappingId, e);
            return false;
        }
    }

    private SkuMapping mapResultSetToSkuMapping(ResultSet rs) throws SQLException {
        SkuMapping m = new SkuMapping();
        m.setMappingId(rs.getInt("mapping_id"));
        m.setSkuId(rs.getInt("sku_id"));
        m.setChannelId(rs.getInt("channel_id"));
        m.setExternalSku(rs.getString("external_sku"));
        m.setSellerSku(rs.getString("seller_sku"));
        m.setSyncStatus(rs.getString("sync_status"));

        Timestamp lastSync = rs.getTimestamp("last_sync_at");
        if (lastSync != null) {
            m.setLastSyncAt(lastSync.toLocalDateTime());
        }

        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            m.setCreatedAt(createdAt.toLocalDateTime());
        }

        Timestamp updatedAt = rs.getTimestamp("updated_at");
        if (updatedAt != null) {
            m.setUpdatedAt(updatedAt.toLocalDateTime());
        }

        m.setChannelName(rs.getString("channel_name"));
        m.setChannelPlatform(rs.getString("platform"));
        m.setSkuCode(rs.getString("sku_code"));
        m.setProductName(rs.getString("product_name"));

        try {
            m.setChannelCategory(rs.getString("lazada_category_name"));
        } catch (SQLException e) {
            // Safe fallback if lazada_category_name is not present in the ResultSet
        }

        return m;
    }

    /**
     * Lấy tất cả Master SKU từ bảng products kèm tổng tồn kho khả dụng.
     *
     * <p>Khả dụng = Tồn vật lý - Tạm giữ - Hàng lỗi (Defective)
     * Vì hàng lỗi được lưu trong row riêng (stock_type='DEFECTIVE') với qty_available=0,
     * ta chỉ cần SUM(qty_available) trên các row NORMAL — kết quả tự động đã trừ defective.
     */
    public List<Product> findAllSkus() {
        List<Product> list = new ArrayList<>();
        String sql = "SELECT p.product_id, p.sku_code, p.product_name, "
                   + "p.base_price, p.mac_price, "
                   + "COALESCE(SUM(CASE WHEN i.stock_type IS NULL OR i.stock_type = 'NORMAL' "
                   + "     THEN i.qty_available ELSE 0 END), 0) AS qty_available "
                   + "FROM products p "
                   + "LEFT JOIN inventory i ON p.product_id = i.product_id "
                   + "GROUP BY p.product_id, p.sku_code, p.product_name, p.base_price, p.mac_price "
                   + "ORDER BY p.sku_code ASC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Product p = new Product();
                p.setProductId(rs.getInt("product_id"));
                p.setSkuCode(rs.getString("sku_code"));
                p.setProductName(rs.getString("product_name"));
                p.setQtyOnHand(rs.getDouble("qty_available")); // Dùng qtyOnHand làm carrier cho qty_available
                double basePrice = rs.getDouble("base_price");
                if (!rs.wasNull()) p.setBasePrice(basePrice);
                double macPrice = rs.getDouble("mac_price");
                if (!rs.wasNull()) p.setMacPrice(macPrice);
                list.add(p);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "SkuMappingDAO: Failed to retrieve all master SKUs", e);
        }
        return list;
    }

    /**
     * Looks up a SKU mapping by channel + external_sku (the marketplace SKU).
     * Returns the mapping only if sync_status = 'SYNCED' (approved for that channel).
     *
     * Replaces the previous behaviour where LazadaSyncScheduler hardcoded a
     * dummy product ID, leaving real internal SKUs disconnected from marketplace orders.
     *
     * @return The active mapping, or null if not found
     */
    public SkuMapping findActiveMapping(int channelId, String externalSku) {
        if (externalSku == null || externalSku.trim().isEmpty()) return null;
        String sql = "SELECT sm.mapping_id, sm.sku_id, sm.channel_id, sm.external_sku, "
                   + "sm.seller_sku, sm.sync_status, sm.last_sync_at, sm.created_at, sm.updated_at, "
                   + "c.channel_name, c.platform, s.sku_code, s.product_name "
                   + "FROM sku_mappings sm "
                   + "LEFT JOIN channels c ON sm.channel_id = c.channel_id "
                   + "LEFT JOIN products s ON sm.sku_id = s.product_id "
                   + "WHERE sm.channel_id = ? AND (sm.external_sku = ? OR sm.seller_sku = ?) AND sm.sync_status = 'SYNCED' "
                   + "LIMIT 1";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, channelId);
            ps.setString(2, externalSku.trim());
            ps.setString(3, externalSku.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToSkuMapping(rs);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING,
                "SkuMappingDAO.findActiveMapping: failed channelId=" + channelId
                    + " externalSku=" + externalSku, e);
        }
        return null;
    }

    /**
     * Tìm bất kỳ mapping nào (kể cả PENDING hay SYNCED) theo channel và external_sku.
     */
    public SkuMapping findMappingByChannelAndExternalSku(int channelId, String externalSku) {
        if (externalSku == null || externalSku.trim().isEmpty()) return null;
        String sql = "SELECT sm.mapping_id, sm.sku_id, sm.channel_id, sm.external_sku, "
                   + "sm.seller_sku, sm.sync_status, sm.last_sync_at, sm.created_at, sm.updated_at, "
                   + "c.channel_name, c.platform, s.sku_code, s.product_name "
                   + "FROM sku_mappings sm "
                   + "LEFT JOIN channels c ON sm.channel_id = c.channel_id "
                   + "LEFT JOIN products s ON sm.sku_id = s.product_id "
                   + "WHERE sm.channel_id = ? AND (sm.external_sku = ? OR sm.seller_sku = ?) "
                   + "LIMIT 1";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, channelId);
            ps.setString(2, externalSku.trim());
            ps.setString(3, externalSku.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToSkuMapping(rs);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING,
                "SkuMappingDAO.findMappingByChannelAndExternalSku: failed channelId=" + channelId
                    + " externalSku=" + externalSku, e);
        }
        return null;
    }

    /**
     * Finds all active (SYNCED) SKU mappings for a list of product IDs on a given channel,
     * enriched with Lazada identifiers from channel_products.
     *
     * Used by MarketplaceSyncService to resolve which SKUs need stock pushed to Lazada
     * after an inbound receipt is confirmed.
     *
     * @param productIds List of internal product IDs to look up.
     * @param channelId The marketplace channel to filter on.
     * @return List of matching SkuMapping objects with enriched Lazada fields.
     */
    public List<SkuMapping> findActiveMappingsByProductIds(List<Integer> productIds, int channelId) {
        List<SkuMapping> list = new ArrayList<>();
        if (productIds == null || productIds.isEmpty()) return list;

        String placeholders = String.join(",", java.util.Collections.nCopies(productIds.size(), "?"));
        String sql = "SELECT sm.mapping_id, sm.sku_id, sm.channel_id, sm.external_sku, "
                   + "sm.seller_sku, sm.sync_status, sm.last_sync_at, sm.created_at, sm.updated_at, "
                   + "c.channel_name, c.platform, s.sku_code, s.product_name, "
                   + "cp.channel_item_id, cp.lazada_sku_id "
                   + "FROM sku_mappings sm "
                   + "LEFT JOIN channels c ON sm.channel_id = c.channel_id "
                   + "LEFT JOIN products s ON sm.sku_id = s.product_id "
                   + "LEFT JOIN channel_products cp ON sm.sku_id = cp.product_id AND sm.channel_id = cp.channel_id "
                   + "WHERE sm.sku_id IN (" + placeholders + ") "
                   + "  AND sm.sync_status IN ('SYNCED','PENDING') "
                   + "  AND cp.channel_item_id IS NOT NULL AND cp.channel_item_id != '' "
                   + "  AND c.is_active = 1";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            for (Integer pid : productIds) {
                ps.setInt(idx++, pid);
            }
            ps.setInt(idx, channelId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    SkuMapping m = mapResultSetToSkuMapping(rs);
                    m.setChannelItemId(rs.getString("channel_item_id"));
                    m.setLazadaSkuId(rs.getString("lazada_sku_id"));
                    list.add(m);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING,
                "SkuMappingDAO.findActiveMappingsByProductIds: failed channelId=" + channelId, e);
        }
        return list;
    }

    /**
     * Batch-fetch channel links (for the "Lazada" column button) for a set of products.
     * Returns rows with product_id, channel_name, platform, lazada_product_id.
     * The lazada_product_id comes from channel_item_id in channel_products and maps
     * to the /products/i{productId}.html URL format on Lazada.
     *
     * @param productIds List of internal product IDs
     * @return List of Object[] rows: [productId, channelName, platform, lazadaProductId]
     */
    public List<Object[]> findChannelLinksForProducts(List<Integer> productIds) {
        List<Object[]> rows = new ArrayList<>();
        if (productIds == null || productIds.isEmpty()) return rows;

        String placeholders = String.join(",", java.util.Collections.nCopies(productIds.size(), "?"));
        // Join sku_mappings → channel_products to get lazada_product_id (channel_item_id)
        String sql = "SELECT sm.sku_id, c.channel_name, c.platform, cp.lazada_sku_id, cp.channel_item_id "
                   + "FROM sku_mappings sm "
                   + "JOIN channels c ON sm.channel_id = c.channel_id "
                   + "LEFT JOIN channel_products cp ON sm.sku_id = cp.product_id AND sm.channel_id = cp.channel_id "
                   + "WHERE sm.sku_id IN (" + placeholders + ") "
                   + "  AND sm.sync_status IN ('SYNCED','PENDING') "
                   + "  AND cp.channel_item_id IS NOT NULL AND cp.channel_item_id != '' "
                   + "  AND c.is_active = 1";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            for (Integer pid : productIds) ps.setInt(idx++, pid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new Object[]{
                        rs.getInt("sku_id"),
                        rs.getString("channel_name"),
                        rs.getString("platform"),
                        rs.getString("lazada_sku_id"),   // r[3] = externalSku
                        rs.getString("channel_item_id")    // r[4] = lazadaProductId
                    });
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING,
                "SkuMappingDAO.findChannelLinksForProducts: failed", e);
        }
        return rows;
    }

    /**
     * Logs an exception when an SKU mapping is missing.
     * Feeds the "Mapping Exception Management" page used by Sales staff.
     */
    public void logMappingException(int channelId, String externalSku,
                                    String orderCode, String reason) {
        String checkSql = "SELECT exception_id FROM mapping_exceptions "
                        + "WHERE channel_id = ? AND external_sku = ? AND resolved = 0";
        if (orderCode != null) {
            checkSql += " AND order_code = ?";
        } else {
            checkSql += " AND order_code IS NULL";
        }
        try (Connection conn = DBConnection.getConnection()) {
            try (PreparedStatement checkPs = conn.prepareStatement(checkSql)) {
                checkPs.setInt(1, channelId);
                checkPs.setString(2, externalSku);
                if (orderCode != null) {
                    checkPs.setString(3, orderCode);
                }
                try (ResultSet rs = checkPs.executeQuery()) {
                    if (rs.next()) {
                        return; // Already exists and unresolved, do not insert duplicate
                    }
                }
            }

            String sql = "INSERT INTO mapping_exceptions "
                       + "(channel_id, external_sku, order_code, reason, created_at) "
                       + "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, channelId);
                ps.setString(2, externalSku);
                ps.setString(3, orderCode);
                ps.setString(4, reason);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING,
                "SkuMappingDAO.logMappingException: failed to log", e);
        }
    }

    /**
     * Resolves all unresolved catalog-level exceptions (where order_code is null) for a channel.
     * Called before pulling to ensure stale/deleted products are cleared from the exceptions list.
     */
    public void resolveCatalogExceptions(int channelId) {
        String sql = "UPDATE mapping_exceptions SET resolved = 1, resolved_at = CURRENT_TIMESTAMP "
                   + "WHERE channel_id = ? AND order_code IS NULL AND resolved = 0";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, channelId);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "SkuMappingDAO.resolveCatalogExceptions: failed for channelId=" + channelId, e);
        }
    }

    public boolean deleteByProductAndChannel(int skuId, int channelId) {
        String sql = "DELETE FROM sku_mappings WHERE sku_id = ? AND channel_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, skuId);
            ps.setInt(2, channelId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "SkuMappingDAO: Failed to delete mapping for sku=" + skuId + " channel=" + channelId, e);
            return false;
        }
    }
}
