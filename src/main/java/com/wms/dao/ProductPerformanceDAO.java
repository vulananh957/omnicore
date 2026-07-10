package com.wms.dao;

import com.wms.model.ProductPerformance;
import com.wms.model.ProductPerformance.ChannelLink;
import com.wms.util.DBConnection;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ProductPerformanceDAO — Data Access Object for product performance analytics.
 *
 * <p>Joins Products + Inventory (MAC) + Order_Items to compute per-SKU financial metrics
 * over a configurable time period. Read-only analytics — no INSERT/UPDATE/DELETE.</p>
 */
public class ProductPerformanceDAO {

    private static final Logger LOGGER = Logger.getLogger(ProductPerformanceDAO.class.getName());

    // ── Query: Performance Metrics by Time Period ─────────────────────────────

    /**
     * Fetch aggregated performance metrics for all products within a time range.
     *
     * @param startDate Start of period (inclusive)
     * @param endDate   End of period (inclusive)
     * @param categoryId Filter by category (null = all)
     * @param channelId  Filter by channel (null = all)
     * @param healthFilter Filter by health status: null/empty = all, "LOW_STOCK", "DEAD_STOCK", "NORMAL"
     * @param searchQuery Search by SKU code or product name
     * @return List of ProductPerformance with computed financials
     */
    public List<ProductPerformance> findPerformanceByPeriod(
            Date startDate,
            Date endDate,
            Integer categoryId,
            Integer channelId,
            String healthFilter,
            String searchQuery) {

        List<ProductPerformance> results = new ArrayList<>();

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ");
        sql.append("  p.product_id, ");
        sql.append("  p.sku_code, ");
        sql.append("  p.product_name, ");
        sql.append("  c.category_name, ");
        sql.append("  COALESCE(inv.qty_on_hand, 0) AS qty_on_hand, ");
        sql.append("  COALESCE(inv.qty_available, 0) AS qty_available, ");
        sql.append("  COALESCE(p.mac_price, p.base_price, 0) AS mac_price, ");
        sql.append("  COALESCE(sales.total_sold, 0) AS total_sold, ");
        sql.append("  COALESCE(sales.total_revenue, 0) AS total_revenue, ");
        sql.append("  COALESCE(sales.min_price, 0) AS min_selling_price, ");
        sql.append("  COALESCE(sales.max_price, 0) AS max_selling_price, ");
        sql.append("  COALESCE(sales.last_ship_date, p.updated_at) AS last_ship_date, ");
        sql.append("  DATEDIFF(CURRENT_DATE, COALESCE(sales.last_ship_date, p.updated_at)) AS days_without_movement ");
        sql.append("FROM products p ");
        sql.append("LEFT JOIN categories c ON p.category_id = c.category_id ");
        sql.append("LEFT JOIN ( ");
        sql.append("  SELECT product_id, SUM(qty_on_hand) AS qty_on_hand, SUM(qty_available) AS qty_available ");
        sql.append("  FROM inventory WHERE stock_type = 'NORMAL' GROUP BY product_id ");
        sql.append(") inv ON p.product_id = inv.product_id ");
        sql.append("LEFT JOIN ( ");
        sql.append("  SELECT ");
        sql.append("    oi.product_id, ");
        sql.append("    SUM(oi.qty) AS total_sold, ");
        sql.append("    SUM(oi.qty * oi.unit_price) AS total_revenue, ");
        sql.append("    MIN(oi.unit_price) AS min_price, ");
        sql.append("    MAX(oi.unit_price) AS max_price, ");
        sql.append("    MAX(o.created_at) AS last_ship_date ");
        sql.append("  FROM order_items oi ");
        sql.append("  JOIN orders o ON oi.order_id = o.order_id ");
        sql.append("  WHERE o.status != 'CANCELLED' ");
        if (startDate != null && endDate != null) {
            sql.append("    AND DATE(o.created_at) BETWEEN ? AND ? ");
        }
        if (channelId != null && channelId > 0) {
            sql.append("    AND o.channel_id = ? ");
        }
        sql.append("  GROUP BY oi.product_id ");
        sql.append(") sales ON p.product_id = sales.product_id ");

        // WHERE clause
        List<String> whereClauses = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        if (categoryId != null && categoryId > 0) {
            whereClauses.add("p.category_id = ?");
            params.add(categoryId);
        }

        if (searchQuery != null && !searchQuery.trim().isEmpty()) {
            whereClauses.add("(p.sku_code LIKE ? OR p.product_name LIKE ?)");
            String searchPattern = "%" + searchQuery.trim() + "%";
            params.add(searchPattern);
            params.add(searchPattern);
        }

        if (!whereClauses.isEmpty()) {
            sql.append(" WHERE ");
            sql.append(String.join(" AND ", whereClauses));
        }

        sql.append(" ORDER BY p.sku_code ASC");

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {

            int paramIndex = 1;

            // Set date params
            if (startDate != null && endDate != null) {
                ps.setDate(paramIndex++, startDate);
                ps.setDate(paramIndex++, endDate);
            }

            // Set channel param
            if (channelId != null && channelId > 0) {
                ps.setInt(paramIndex++, channelId);
            }

            // Set category param
            if (categoryId != null && categoryId > 0) {
                ps.setInt(paramIndex++, categoryId);
            }

            // Set search params
            if (searchQuery != null && !searchQuery.trim().isEmpty()) {
                String searchPattern = "%" + searchQuery.trim() + "%";
                ps.setString(paramIndex++, searchPattern);
                ps.setString(paramIndex++, searchPattern);
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ProductPerformance pp = mapRow(rs);

                    // Compute financials
                    pp.computeGrossProfit();
                    pp.computeTiedUpCapital();
                    pp.computeHealthStatus(pp.getMinStock(), pp.getMaxStock());

                    // Apply health filter
                    if (healthFilter != null && !healthFilter.isEmpty() && !healthFilter.equalsIgnoreCase("ALL")) {
                        if (!healthFilter.equalsIgnoreCase(pp.getHealthStatus())) {
                            continue;
                        }
                    }

                    results.add(pp);
                }
            }

        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "findPerformanceByPeriod: failed", e);
        }

        return results;
    }

    /**
     * Find all performance data without time period filter.
     * Returns all products with their financial metrics computed.
     */
    public List<ProductPerformance> findAllPerformance(
            Integer categoryId,
            Integer channelId,
            String healthFilter,
            String searchQuery) {

        List<ProductPerformance> results = new ArrayList<>();

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ");
        sql.append("  p.product_id, p.sku_code, p.product_name, ");
        sql.append("  p.category_id, c.category_name, ");
        sql.append("  COALESCE(p.mac_price, p.base_price, 0) AS mac_price, ");
        sql.append("  COALESCE(p.min_stock, 0) AS min_stock, ");
        sql.append("  COALESCE(p.max_stock, 0) AS max_stock, ");
        sql.append("  COALESCE(inv.qty_on_hand, 0) AS qty_on_hand, ");
        sql.append("  COALESCE(inv.qty_available, 0) AS qty_available, ");
        sql.append("  COALESCE(sales.total_sold, 0) AS total_sold, ");
        sql.append("  COALESCE(sales.total_revenue, 0) AS total_revenue, ");
        sql.append("  COALESCE(sales.min_price, 0) AS min_selling_price, ");
        sql.append("  COALESCE(sales.max_price, 0) AS max_selling_price, ");
        sql.append("  COALESCE(sales.last_ship_date, p.updated_at) AS last_ship_date, ");
        sql.append("  DATEDIFF(CURRENT_DATE, COALESCE(sales.last_ship_date, p.updated_at)) AS days_without_movement ");
        sql.append("FROM products p ");
        sql.append("LEFT JOIN categories c ON p.category_id = c.category_id ");
        sql.append("LEFT JOIN ( ");
        sql.append("  SELECT product_id, SUM(qty_on_hand) AS qty_on_hand, SUM(qty_available) AS qty_available ");
        sql.append("  FROM inventory WHERE stock_type = 'NORMAL' GROUP BY product_id ");
        sql.append(") inv ON p.product_id = inv.product_id ");
        sql.append("LEFT JOIN ( ");
        sql.append("  SELECT ");
        sql.append("    oi.product_id, ");
        sql.append("    SUM(oi.qty) AS total_sold, ");
        sql.append("    SUM(oi.qty * oi.unit_price) AS total_revenue, ");
        sql.append("    MIN(oi.unit_price) AS min_price, ");
        sql.append("    MAX(oi.unit_price) AS max_price, ");
        sql.append("    MAX(o.created_at) AS last_ship_date ");
        sql.append("  FROM order_items oi ");
        sql.append("  JOIN orders o ON oi.order_id = o.order_id ");
        sql.append("  WHERE o.status != 'CANCELLED' ");
        if (channelId != null && channelId > 0) {
            sql.append("    AND o.channel_id = ? ");
        }
        sql.append("  GROUP BY oi.product_id ");
        sql.append(") sales ON p.product_id = sales.product_id ");

        // WHERE clause
        List<String> whereClauses = new ArrayList<>();

        if (categoryId != null && categoryId > 0) {
            whereClauses.add("p.category_id = ?");
        }

        if (searchQuery != null && !searchQuery.trim().isEmpty()) {
            whereClauses.add("(p.sku_code LIKE ? OR p.product_name LIKE ?)");
        }

        if (!whereClauses.isEmpty()) {
            sql.append(" WHERE ");
            sql.append(String.join(" AND ", whereClauses));
        }

        sql.append(" ORDER BY p.sku_code ASC");

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {

            int paramIndex = 1;

            // Set channel param
            if (channelId != null && channelId > 0) {
                ps.setInt(paramIndex++, channelId);
            }

            // Set category param
            if (categoryId != null && categoryId > 0) {
                ps.setInt(paramIndex++, categoryId);
            }

            // Set search params
            if (searchQuery != null && !searchQuery.trim().isEmpty()) {
                String searchPattern = "%" + searchQuery.trim() + "%";
                ps.setString(paramIndex++, searchPattern);
                ps.setString(paramIndex++, searchPattern);
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ProductPerformance pp = mapRow(rs);

                    // Compute financials
                    pp.computeGrossProfit();
                    pp.computeTiedUpCapital();
                    pp.computeHealthStatus(pp.getMinStock(), pp.getMaxStock());

                    // Apply health filter
                    if (healthFilter != null && !healthFilter.isEmpty() && !healthFilter.equalsIgnoreCase("ALL")) {
                        if (!healthFilter.equalsIgnoreCase(pp.getHealthStatus())) {
                            continue;
                        }
                    }

                    results.add(pp);
                }
            }

        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "findAllPerformance: failed", e);
        }

        return results;
    }

    // ── Query: Channel Links for External URLs ─────────────────────────────────

    /**
     * Fetch channel mapping links for given product IDs.
     * Used to populate the external marketplace links.
     */
    public List<ProductPerformance> enrichChannelLinks(List<Integer> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return new ArrayList<>();
        }

        List<ProductPerformance> results = new ArrayList<>();

        String placeholders = String.join(",", java.util.Collections.nCopies(productIds.size(), "?"));
        String sql = String.format(
            "SELECT sm.sku_id, sm.channel_id, ch.channel_name, ch.platform, sm.external_sku, sm.lazada_product_id " +
            "FROM sku_mappings sm " +
            "JOIN channels ch ON sm.channel_id = ch.channel_id " +
            "WHERE sm.sku_id IN (%s) AND sm.sync_status = 'ACTIVE'",
            placeholders);

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            for (int i = 0; i < productIds.size(); i++) {
                ps.setInt(i + 1, productIds.get(i));
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int skuId = rs.getInt("sku_id");
                    ChannelLink link = new ChannelLink(
                        rs.getString("channel_name"),
                        rs.getString("platform"),
                        rs.getString("external_sku"),
                        rs.getString("lazada_product_id")
                    );

                    // For simplicity, we'll build a map in the service layer
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "enrichChannelLinks: failed", e);
        }

        return results;
    }

    /**
     * Get channel links for a single product.
     */
    public List<ChannelLink> findChannelLinksByProductId(int productId) {
        List<ChannelLink> links = new ArrayList<>();

        // Join sku_mappings → channel_products to get channel_item_id (Lazada item_id)
        // for building product URLs like https://www.lazada.vn/products/i{itemId}.html
        String sql =
            "SELECT ch.channel_name, ch.platform, cp.channel_item_id, cp.lazada_sku_id "
          + "FROM sku_mappings sm "
          + "JOIN channels ch ON sm.channel_id = ch.channel_id "
          + "LEFT JOIN channel_products cp ON sm.sku_id = cp.product_id AND sm.channel_id = cp.channel_id "
          + "WHERE sm.sku_id = ? "
          + "  AND sm.sync_status IN ('SYNCED','PENDING') "
          + "  AND cp.channel_item_id IS NOT NULL AND cp.channel_item_id != '' "
          + "  AND ch.is_active = 1";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, productId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ChannelLink link = new ChannelLink(
                        rs.getString("channel_name"),
                        rs.getString("platform"),
                        rs.getString("lazada_sku_id"),   // externalSku (seller_sku)
                        rs.getString("channel_item_id")    // lazadaProductId (item_id)
                    );
                    links.add(link);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "findChannelLinksByProductId: failed productId=" + productId, e);
        }

        return links;
    }

    // ── Query: Revenue Trend for Charts ────────────────────────────────────────

    /**
     * Fetch daily revenue for a product within a time range (for line chart popup).
     *
     * @param productId Product ID
     * @param startDate Start date
     * @param endDate   End date
     * @return List of {date, revenue} maps
     */
    public List<java.util.Map<String, Object>> findRevenueTrend(int productId, Date startDate, Date endDate) {
        List<java.util.Map<String, Object>> trend = new ArrayList<>();

        String sql =
            "SELECT DATE(o.created_at) AS sale_date, " +
            "       SUM(oi.qty * oi.unit_price) AS daily_revenue, " +
            "       SUM(oi.qty) AS daily_qty " +
            "FROM order_items oi " +
            "JOIN orders o ON oi.order_id = o.order_id " +
            "WHERE oi.product_id = ? " +
            "  AND o.status IN ('SHIPPED', 'DELIVERED') " +
            "  AND DATE(o.created_at) BETWEEN ? AND ? " +
            "GROUP BY DATE(o.created_at) " +
            "ORDER BY sale_date ASC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, productId);
            ps.setDate(2, startDate);
            ps.setDate(3, endDate);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    java.util.Map<String, Object> row = new java.util.HashMap<>();
                    row.put("date", rs.getDate("sale_date").toString());
                    row.put("revenue", rs.getBigDecimal("daily_revenue"));
                    row.put("quantity", rs.getInt("daily_qty"));
                    trend.add(row);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "findRevenueTrend: failed productId=" + productId, e);
        }

        return trend;
    }

    // ── Query: Categories (for filter dropdown) ─────────────────────────────────

    /**
     * Get all active categories for filter dropdown.
     */
    public List<java.util.Map<String, Object>> findAllCategories() {
        List<java.util.Map<String, Object>> categories = new ArrayList<>();

        // Get child categories grouped by parent, with parent name
        String sql = """
            SELECT
                child.category_id,
                child.category_name,
                child.parent_id,
                parent.category_name AS parent_name
            FROM categories child
            LEFT JOIN categories parent ON child.parent_id = parent.category_id
            WHERE child.active = 1 AND child.parent_id IS NOT NULL
            ORDER BY parent_name ASC, child.category_name ASC
            """;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                java.util.Map<String, Object> cat = new java.util.HashMap<>();
                cat.put("categoryId", rs.getInt("category_id"));
                cat.put("categoryName", rs.getString("category_name"));
                cat.put("parentId", rs.getObject("parent_id"));
                cat.put("parentName", rs.getString("parent_name"));
                categories.add(cat);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "findAllCategories: failed", e);
        }

        return categories;
    }

    // ── Query: Channels (for filter dropdown) ───────────────────────────────────

    /**
     * Get all active channels for filter dropdown.
     */
    public List<java.util.Map<String, Object>> findAllChannels() {
        List<java.util.Map<String, Object>> channels = new ArrayList<>();

        String sql = "SELECT channel_id, channel_name, platform FROM channels WHERE is_active = 1 ORDER BY channel_name ASC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                java.util.Map<String, Object> ch = new java.util.HashMap<>();
                ch.put("channelId", rs.getInt("channel_id"));
                ch.put("channelName", rs.getString("channel_name"));
                ch.put("platform", rs.getString("platform"));
                channels.add(ch);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "findAllChannels: failed", e);
        }

        return channels;
    }

    // ── Row Mapper ───────────────────────────────────────────────────────────────

    private ProductPerformance mapRow(ResultSet rs) throws SQLException {
        ProductPerformance pp = new ProductPerformance();

        pp.setProductId(rs.getInt("product_id"));
        pp.setSkuCode(rs.getString("sku_code"));
        pp.setProductName(rs.getString("product_name"));
        pp.setCategoryName(rs.getString("category_name"));

        pp.setQtyOnHand(toDecimal(rs.getBigDecimal("qty_on_hand")));
        pp.setQtyAvailable(toDecimal(rs.getBigDecimal("qty_available")));
        pp.setMacPrice(toDecimal(rs.getBigDecimal("mac_price")));
        pp.setMinStock(toDecimal(rs.getBigDecimal("min_stock")));
        pp.setMaxStock(toDecimal(rs.getBigDecimal("max_stock")));

        pp.setTotalSold(rs.getInt("total_sold"));
        pp.setTotalRevenue(toDecimal(rs.getBigDecimal("total_revenue")));
        pp.setMinSellingPrice(toDecimal(rs.getBigDecimal("min_selling_price")));
        pp.setMaxSellingPrice(toDecimal(rs.getBigDecimal("max_selling_price")));

        int daysWithoutMovement = rs.getInt("days_without_movement");
        if (!rs.wasNull()) {
            pp.setDaysWithoutMovement(daysWithoutMovement);
        }

        return pp;
    }

    private BigDecimal toDecimal(BigDecimal value) {
        if (value == null) return BigDecimal.ZERO;
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
