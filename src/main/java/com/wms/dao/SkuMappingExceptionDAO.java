package com.wms.dao;

import com.wms.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SkuMappingExceptionDAO — Truy vấn bảng mapping_exceptions cho Sales Staff.
 * Bảng đã có sẵn trong schema.sql; scheduler (LazadaSyncScheduler) tự ghi khi SKU lạ.
 */
public class SkuMappingExceptionDAO {

    private static final Logger LOGGER = Logger.getLogger(SkuMappingExceptionDAO.class.getName());

    /** Lấy danh sách exception chưa xử lý, join channels để có tên kênh hiển thị. */
    public List<Map<String, Object>> findUnresolved() {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT me.exception_id, me.channel_id, me.external_sku, "
                   + "me.order_code, me.reason, me.created_at, me.resolved, "
                   + "c.channel_name, c.platform, "
                   + "cp.channel_item_id, cp.channel_sku_code, cp.channel_sku_code AS seller_sku "
                   + "FROM mapping_exceptions me "
                   + "LEFT JOIN channels c ON me.channel_id = c.channel_id "
                   + "LEFT JOIN channel_products cp ON cp.channel_id = me.channel_id "
                   + "    AND cp.channel_sku_code = me.external_sku "
                   + "WHERE me.resolved = 0 "
                   + "ORDER BY me.created_at DESC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("exceptionId", rs.getInt("exception_id"));
                row.put("channelId", rs.getInt("channel_id"));
                row.put("channelName", rs.getString("channel_name"));
                row.put("platform", rs.getString("platform"));
                row.put("externalSku", rs.getString("external_sku"));
                row.put("channelItemId", rs.getString("channel_item_id"));
                row.put("channelSkuCode", rs.getString("channel_sku_code"));
                row.put("sellerSku", rs.getString("seller_sku"));
                row.put("orderCode", rs.getString("order_code"));
                row.put("reason", rs.getString("reason"));
                row.put("createdAt", rs.getTimestamp("created_at"));
                row.put("resolved", rs.getInt("resolved"));
                list.add(row);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "SkuMappingExceptionDAO.findUnresolved: failed", e);
        }
        return list;
    }

    /** Đếm số exception chưa xử lý — dùng cho badge cảnh báo trên menu. */
    public int countUnresolved() {
        String sql = "SELECT COUNT(*) FROM mapping_exceptions WHERE resolved = 0";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "SkuMappingExceptionDAO.countUnresolved: failed", e);
        }
        return 0;
    }

    /**
     * Lấy danh sách sản phẩm trong channel_products chưa được ánh xạ vào bất kỳ
     * sku_mappings nào. Dùng để bổ sung vào drawer "Hộp thư" cho các kênh không
     * dùng exception log (ví dụ: Website/Own Website).
     *
     * Trả về cùng cấu trúc Map với findUnresolved() để JSP/JS xử lý đồng nhất.
     * exceptionId = -cp.id (âm) để phân biệt với mapping_exceptions thật.
     */
    public List<Map<String, Object>> findUnmappedChannelProducts() {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT cp.id, cp.channel_id, cp.channel_sku_code, cp.channel_item_id, "
                   + "cp.seller_sku, p.product_name AS cp_product_name, "
                   + "c.channel_name, c.platform "
                   + "FROM channel_products cp "
                   + "JOIN channels c ON cp.channel_id = c.channel_id "
                   + "LEFT JOIN products p ON cp.product_id = p.product_id "
                   + "WHERE c.is_active = 1 "
                   + "  AND NOT EXISTS ( "
                   + "    SELECT 1 FROM sku_mappings sm "
                   + "    WHERE sm.channel_id = cp.channel_id "
                   + "      AND (sm.external_sku = cp.channel_sku_code "
                   + "           OR sm.seller_sku = cp.channel_sku_code) "
                   + "  ) "
                   + "ORDER BY c.channel_name, cp.channel_sku_code";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                // Dùng id âm để phân biệt với mapping_exceptions thật
                row.put("exceptionId", -rs.getInt("id"));
                row.put("channelId",   rs.getInt("channel_id"));
                row.put("channelName", rs.getString("channel_name"));
                row.put("platform",    rs.getString("platform"));

                String sku = rs.getString("channel_sku_code");
                row.put("externalSku",    sku);
                row.put("channelItemId",  rs.getString("channel_item_id"));
                row.put("channelSkuCode", sku);
                row.put("sellerSku",      rs.getString("seller_sku") != null
                                          ? rs.getString("seller_sku") : sku);

                // Tên sản phẩm: ưu tiên tên từ products (nếu đã link product_id), fallback sku
                String cpName = rs.getString("cp_product_name");
                row.put("reason", cpName != null ? cpName : ("Sản phẩm kênh (" + sku + ")"));

                row.put("orderCode", null);
                row.put("createdAt", null);
                row.put("resolved",  0);
                list.add(row);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "SkuMappingExceptionDAO.findUnmappedChannelProducts: failed", e);
        }
        return list;
    }

    /** Đánh dấu đã xử lý (resolved = 1, resolved_at = NOW). */
    public boolean markResolved(int exceptionId) {
        String sql = "UPDATE mapping_exceptions "
                   + "SET resolved = 1, resolved_at = CURRENT_TIMESTAMP "
                   + "WHERE exception_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, exceptionId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING,
                "SkuMappingExceptionDAO.markResolved: failed for id=" + exceptionId, e);
            return false;
        }
    }
}
