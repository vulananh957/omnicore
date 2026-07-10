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
