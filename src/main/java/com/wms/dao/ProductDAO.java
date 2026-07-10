package com.wms.dao;

import com.wms.model.Product;
import com.wms.util.DBConnection;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ProductDAO — Data Access Object for managing master SKU product records.
 *
 * Optimizations applied:
 * - All SELECTs share a single private builder method to eliminate code duplication.
 * - Default zones are fetched in a single batch query (instead of per-product),
 *   eliminating the N+1 query problem when loading product lists.
 */
public class ProductDAO {

    private static final Logger LOGGER = Logger.getLogger(ProductDAO.class.getName());

    private static final String SELECT_CORE =
        "SELECT p.product_id, p.sku_code, p.product_name, p.category_id, p.barcode, p.unit, "
        + "p.min_stock, p.max_stock, p.attributes_text, p.weight_kg, p.base_price, p.mac_price, "
        + "p.d_avg, p.d_max, p.l_avg, p.l_max, p.safety_stock, p.rop_calculated, "
        + "p.created_by, p.created_at, p.updated_at, "
        + "p.short_description, "
        + "c.category_name, u.full_name AS creator_name, "
        + "COALESCE(i.qty_on_hand, 0) AS qty_on_hand "
        + "FROM products p "
        + "LEFT JOIN categories c ON p.category_id = c.category_id "
        + "LEFT JOIN users u ON p.created_by = u.user_id "
        + "LEFT JOIN (SELECT product_id, SUM(qty_on_hand) AS qty_on_hand FROM inventory GROUP BY product_id) i ON p.product_id = i.product_id";

    public ProductDAO() {
    }

    /**
     * Retrieves all products ordered by product_id descending.
     * Zones are fetched in a single batch query — no N+1 problem.
     */
    public List<Product> findAll() {
        Map<Integer, List<Product.LocationConfig>> zonesMap = batchFindDefaultZones();
        List<Product> list = new ArrayList<>();
        String sql = SELECT_CORE + " ORDER BY p.product_id DESC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs, zonesMap.getOrDefault(getProductId(rs), List.of())));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "ProductDAO: Failed to find all products", e);
        }
        return list;
    }

    /**
     * Finds a single product by its primary key.
     */
    public Product findById(int productId) {
        List<Product.LocationConfig> zones = findDefaultZonesByProductId(productId);
        String sql = SELECT_CORE + " WHERE p.product_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs, zones);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "ProductDAO: Failed to find product by ID " + productId, e);
        }
        return null;
    }

    public Product findBySkuCode(String skuCode) {
        if (skuCode == null || skuCode.isBlank()) {
            return null;
        }
        String sql = SELECT_CORE + " WHERE p.sku_code = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, skuCode.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs, List.of());
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "ProductDAO: Failed to find product by SKU " + skuCode, e);
        }
        return null;
    }

    /**
     * Simple (total, items) pair returned by {@link #search}.
     * NOTE: items are plain maps (snake_case keys), NOT {@link Product} — the storefront
     * API contract (OmnicoreApiService.java in omnicore-web) expects field names like
     * product_id/sku_code/qty_available/is_best_seller which don't exist on the internal
     * Product model (that model is warehouse-oriented: zones, ROP, MAC price, no
     * is_new_arrival/is_best_seller/active). Do not change this back to List<Product> —
     * a previous version briefly did, and it silently broke the website integration
     * (wrong field names, no images/qty_available) without any compile error.
     */
    public static class SearchResult {
        public final List<Map<String, Object>> items;
        public final int total;
        public SearchResult(List<Map<String, Object>> items, int total) {
            this.items = items;
            this.total = total;
        }
    }

    /**
     * Storefront product search — paginated, filterable by category/keyword/flag.
     *
     * @param filter "new_arrival", "best_seller", or null/blank for no flag filter.
     */
    public SearchResult search(int page, int size, Integer categoryId, String keyword, String filter) {
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE p.active = 1 ");
        if (categoryId != null) {
            where.append(" AND p.category_id = ? ");
            params.add(categoryId);
        }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (p.product_name LIKE ? OR p.sku_code LIKE ?) ");
            String kw = "%" + keyword.trim() + "%";
            params.add(kw);
            params.add(kw);
        }
        if ("new_arrival".equals(filter)) {
            where.append(" AND p.is_new_arrival = 1 ");
        } else if ("best_seller".equals(filter)) {
            where.append(" AND p.is_best_seller = 1 ");
        }

        int total = 0;
        String countSql = "SELECT COUNT(*) FROM products p" + where;
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(countSql)) {
            bindSearchParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) total = rs.getInt(1);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "ProductDAO.search: count query failed", e);
        }

        List<Map<String, Object>> items = new ArrayList<>();
        String dataSql = "SELECT p.product_id, p.sku_code, p.product_name, p.category_id, c.category_name, "
                + "p.base_price, p.attributes_text, p.barcode, p.weight_kg, p.is_new_arrival, p.is_best_seller, "
                + "(SELECT pi.image_url FROM product_images pi WHERE pi.product_id = p.product_id "
                + " AND pi.is_primary = 1 LIMIT 1) AS primary_image "
                + "FROM products p LEFT JOIN categories c ON p.category_id = c.category_id"
                + where + " ORDER BY p.product_id DESC LIMIT ? OFFSET ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(dataSql)) {
            List<Object> dataParams = new ArrayList<>(params);
            dataParams.add(Math.max(size, 1));
            dataParams.add(Math.max(page - 1, 0) * Math.max(size, 1));
            bindSearchParams(ps, dataParams);
            try (ResultSet rs = ps.executeQuery()) {
                InventoryDAO inventoryDAO = new InventoryDAO();
                while (rs.next()) {
                    items.add(mapSearchRow(rs, inventoryDAO));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "ProductDAO.search: data query failed", e);
        }

        return new SearchResult(items, total);
    }

    /**
     * Storefront product detail — same field set as {@link #search} but with the full
     * image list instead of just the primary image, and no pagination.
     */
    public Map<String, Object> findDetailById(int productId) {
        String sql = "SELECT p.product_id, p.sku_code, p.product_name, p.category_id, c.category_name, "
                + "p.base_price, p.attributes_text, p.barcode, p.weight_kg, p.is_new_arrival, p.is_best_seller "
                + "FROM products p LEFT JOIN categories c ON p.category_id = c.category_id "
                + "WHERE p.product_id = ? AND p.active = 1";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Map<String, Object> data = mapSearchRow(rs, null);
                data.put("images", findImagesByProductId(conn, productId));
                data.put("qty_available", new InventoryDAO().getTotalAvailableStock(productId));
                return data;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "ProductDAO.findDetailById: failed for productId=" + productId, e);
            return null;
        }
    }

    private List<Map<String, Object>> findImagesByProductId(Connection conn, int productId) throws SQLException {
        List<Map<String, Object>> images = new ArrayList<>();
        String sql = "SELECT image_url, is_primary FROM product_images "
                + "WHERE product_id = ? ORDER BY is_primary DESC, sort_order ASC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> img = new HashMap<>();
                    img.put("image_url", rs.getString("image_url"));
                    img.put("is_primary", rs.getInt("is_primary") == 1);
                    images.add(img);
                }
            }
        }
        return images;
    }

    /** Shared row → map builder for {@link #search} and {@link #findDetailById}. */
    private Map<String, Object> mapSearchRow(ResultSet rs, InventoryDAO inventoryDAO) throws SQLException {
        Map<String, Object> row = new HashMap<>();
        int productId = rs.getInt("product_id");
        row.put("product_id", productId);
        row.put("sku_code", rs.getString("sku_code"));
        row.put("product_name", rs.getString("product_name"));
        int categoryId = rs.getInt("category_id");
        row.put("category_id", rs.wasNull() ? null : categoryId);
        row.put("category_name", rs.getString("category_name"));
        row.put("base_price", rs.getBigDecimal("base_price"));
        row.put("attributes_text", rs.getString("attributes_text"));
        row.put("barcode", rs.getString("barcode"));
        row.put("weight_kg", rs.getBigDecimal("weight_kg"));
        row.put("is_new_arrival", rs.getInt("is_new_arrival") == 1);
        row.put("is_best_seller", rs.getInt("is_best_seller") == 1);
        if (inventoryDAO != null) {
            row.put("qty_available", inventoryDAO.getTotalAvailableStock(productId));
            row.put("primary_image", getString(rs, "primary_image"));
        }
        return row;
    }

    private void bindSearchParams(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            ps.setObject(i + 1, params.get(i));
        }
    }

    /**
     * Finds all products belonging to a specific category.
     */
    public List<Product> findByCategory(Integer categoryId) {
        Map<Integer, List<Product.LocationConfig>> zonesMap = batchFindDefaultZones();
        List<Product> list = new ArrayList<>();
        String whereClause;
        if (categoryId == null) {
            whereClause = " WHERE p.category_id IS NULL";
        } else {
            whereClause = " WHERE p.category_id = ?";
        }
        String sql = SELECT_CORE + whereClause + " ORDER BY p.product_id DESC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (categoryId != null) {
                ps.setInt(1, categoryId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs, zonesMap.getOrDefault(getProductId(rs), List.of())));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "ProductDAO: Failed to find products by category " + categoryId, e);
        }
        return list;
    }

    /**
     * Batch-fetches all product-to-zone mappings in a single query,
     * then builds a Map(productId -> zones list). This eliminates N queries
     * when loading N products — only 1 extra query is needed regardless of list size.
     */
    private Map<Integer, List<Product.LocationConfig>> batchFindDefaultZones() {
        Map<Integer, List<Product.LocationConfig>> map = new HashMap<>();
        String sql = "SELECT product_id, warehouse_id, zone_id FROM product_default_zones";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int pid = rs.getInt("product_id");
                String locId = String.valueOf(rs.getInt("warehouse_id"));
                String zoneId = String.valueOf(rs.getInt("zone_id"));
                map.computeIfAbsent(pid, k -> new ArrayList<>())
                   .add(new Product.LocationConfig(locId, zoneId));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "ProductDAO: batchFindDefaultZones failed", e);
        }
        return map;
    }

    // ── Mutating operations ────────────────────────────────────────

    public boolean insert(Product product) {
        String sql = "INSERT INTO products (sku_code, product_name, category_id, barcode, unit, "
                + "min_stock, max_stock, attributes_text, weight_kg, base_price, created_by) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, product.getSkuCode());
            ps.setString(2, product.getProductName());
            if (product.getCategoryId() != null) {
                ps.setInt(3, product.getCategoryId());
            } else {
                ps.setNull(3, java.sql.Types.INTEGER);
            }
            ps.setString(4, product.getBarcode());
            ps.setString(5, product.getUnit());
            ps.setDouble(6, product.getMinStock() != null ? product.getMinStock() : 0.0);
            ps.setDouble(7, product.getMaxStock() != null ? product.getMaxStock() : 0.0);
            ps.setString(8, product.getAttributesText());
            if (product.getWeightKg() != null) {
                ps.setDouble(9, product.getWeightKg());
            } else {
                ps.setNull(9, java.sql.Types.DECIMAL);
            }
            ps.setDouble(10, product.getBasePrice() != null ? product.getBasePrice() : 0.0);
            if (product.getCreatedBy() != null) {
                ps.setInt(11, product.getCreatedBy());
            } else {
                ps.setNull(11, java.sql.Types.INTEGER);
            }
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "ProductDAO: Failed to insert product " + product.getSkuCode(), e);
            return false;
        }
    }

    public boolean update(Product product) {
        String sql = "UPDATE products SET "
                + "sku_code = ?, product_name = ?, category_id = ?, barcode = ?, unit = ?, "
                + "min_stock = ?, max_stock = ?, attributes_text = ?, weight_kg = ?, base_price = ?, "
                + "updated_at = CURRENT_TIMESTAMP "
                + "WHERE product_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, product.getSkuCode());
            ps.setString(2, product.getProductName());
            if (product.getCategoryId() != null) {
                ps.setInt(3, product.getCategoryId());
            } else {
                ps.setNull(3, java.sql.Types.INTEGER);
            }
            ps.setString(4, product.getBarcode());
            ps.setString(5, product.getUnit());
            ps.setDouble(6, product.getMinStock() != null ? product.getMinStock() : 0.0);
            ps.setDouble(7, product.getMaxStock() != null ? product.getMaxStock() : 0.0);
            ps.setString(8, product.getAttributesText());
            if (product.getWeightKg() != null) {
                ps.setDouble(9, product.getWeightKg());
            } else {
                ps.setNull(9, java.sql.Types.DECIMAL);
            }
            ps.setDouble(10, product.getBasePrice() != null ? product.getBasePrice() : 0.0);
            ps.setInt(11, product.getProductId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "ProductDAO: Failed to update product " + product.getProductId(), e);
            return false;
        }
    }

    /**
     * Updates the Moving Average Cost for a product.
     * Called by InboundService after each inbound receipt.
     *
     * MAC formula (Moving Average Cost):
     *   MAC_new = (current_on_hand × MAC_current + accepted_qty × unit_cost)
     *             / (current_on_hand + accepted_qty)
     *
     * @param productId    The product ID
     * @param currentOnHand Current total qty_on_hand (across all warehouses)
     * @param macCurrent   Current mac_price in products table
     * @param acceptedQty  Quantity accepted in this inbound lot
     * @param unitCost     Unit cost of this inbound lot
     * @return true if updated, false otherwise
     */
    public boolean updateMacPrice(int productId, BigDecimal currentOnHand,
                                   BigDecimal macCurrent, BigDecimal acceptedQty, BigDecimal unitCost) {
        if (productId <= 0 || currentOnHand == null || macCurrent == null
                || acceptedQty == null || unitCost == null) {
            return false;
        }
        if (acceptedQty.compareTo(BigDecimal.ZERO) <= 0 || unitCost.signum() < 0) {
            return false; // reject invalid inputs
        }

        BigDecimal totalOnHand = currentOnHand.add(acceptedQty);
        if (totalOnHand.signum() <= 0) {
            return false;
        }

        BigDecimal totalValue = currentOnHand.multiply(macCurrent)
                .add(acceptedQty.multiply(unitCost));
        BigDecimal newMac = totalValue.divide(totalOnHand, 4, RoundingMode.HALF_UP);

        String sql = "UPDATE products SET mac_price = ? WHERE product_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBigDecimal(1, newMac);
            ps.setInt(2, productId);
            int rows = ps.executeUpdate();
            if (rows > 0) {
                LOGGER.info("MAC updated: productId=" + productId + " newMAC=" + newMac
                        + " (on_hand=" + currentOnHand + " mac=" + macCurrent
                        + " accepted=" + acceptedQty + " unit_cost=" + unitCost + ")");
            }
            return rows > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "ProductDAO.updateMacPrice failed productId=" + productId, e);
            return false;
        }
    }

    /**
     * Returns the current mac_price for a product. Returns 0.0 if not found.
     */
    public BigDecimal findMacPrice(int productId) {
        String sql = "SELECT mac_price FROM products WHERE product_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBigDecimal("mac_price");
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "ProductDAO.findMacPrice failed productId=" + productId, e);
        }
        return BigDecimal.ZERO;
    }

    // ══ ROP (Reorder Point) Methods ══════════════════════════════════════

    /**
     * Batch update ROP metrics + rop_calculated for a product.
     * Called by RopCalculationService after computing from history.
     *
     * @param productId  The product ID
     * @param dAvg       Average daily demand
     * @param dMax       Maximum daily demand
     * @param lAvg       Average lead time in days
     * @param lMax       Maximum lead time in days
     * @param safetyStock Safety Stock = (D_max×L_max) − (D_avg×L_avg)
     * @param rop        Reorder Point = (D_avg×L_avg) + SS
     * @return true if updated, false otherwise
     */
    public boolean updateRopMetrics(int productId, BigDecimal dAvg, BigDecimal dMax,
                                   BigDecimal lAvg, BigDecimal lMax,
                                   BigDecimal safetyStock, BigDecimal rop) {
        String sql = "UPDATE products SET "
                + "d_avg = ?, d_max = ?, l_avg = ?, l_max = ?, "
                + "safety_stock = ?, rop_calculated = ?, updated_at = NOW() "
                + "WHERE product_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBigDecimal(1, dAvg != null ? dAvg : BigDecimal.ZERO);
            ps.setBigDecimal(2, dMax != null ? dMax : BigDecimal.ZERO);
            ps.setBigDecimal(3, lAvg != null ? lAvg : BigDecimal.ZERO);
            ps.setBigDecimal(4, lMax != null ? lMax : BigDecimal.ZERO);
            ps.setBigDecimal(5, safetyStock != null ? safetyStock : BigDecimal.ZERO);
            ps.setBigDecimal(6, rop != null ? rop : BigDecimal.ZERO);
            ps.setInt(7, productId);
            int rows = ps.executeUpdate();
            if (rows > 0) {
                LOGGER.info("ROP updated: productId=" + productId + " dAvg=" + dAvg + " dMax=" + dMax
                        + " lAvg=" + lAvg + " lMax=" + lMax + " SS=" + safetyStock + " ROP=" + rop);
            }
            return rows > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "ProductDAO.updateRopMetrics failed productId=" + productId, e);
            return false;
        }
    }

    /**
     * Logs a ROP computation run to the audit trail table.
     */
    public void insertRopLog(int productId, int lookbackDays,
                             BigDecimal dAvg, BigDecimal dMax,
                             BigDecimal lAvg, BigDecimal lMax,
                             BigDecimal safetyStock,
                             BigDecimal ropBefore, BigDecimal ropAfter,
                             Integer triggeredBy) {
        String sql = "INSERT INTO product_rop_log "
                + "(product_id, lookback_days, d_avg, d_max, l_avg, l_max, safety_stock, rop_before, rop_after, triggered_by) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            ps.setInt(2, lookbackDays);
            ps.setBigDecimal(3, dAvg != null ? dAvg : BigDecimal.ZERO);
            ps.setBigDecimal(4, dMax != null ? dMax : BigDecimal.ZERO);
            ps.setBigDecimal(5, lAvg != null ? lAvg : BigDecimal.ZERO);
            ps.setBigDecimal(6, lMax != null ? lMax : BigDecimal.ZERO);
            ps.setBigDecimal(7, safetyStock != null ? safetyStock : BigDecimal.ZERO);
            ps.setBigDecimal(8, ropBefore != null ? ropBefore : BigDecimal.ZERO);
            ps.setBigDecimal(9, ropAfter != null ? ropAfter : BigDecimal.ZERO);
            ps.setObject(10, triggeredBy);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "ProductDAO.insertRopLog failed productId=" + productId, e);
        }
    }

    /**
     * Returns demand metrics (d_avg, d_max) for a product over a lookback window.
     * d_avg = total outbound qty / number of distinct shipping days
     * d_max = max daily outbound qty in any single day
     *
     * @param productId   The product ID
     * @param lookbackDays Number of past days to analyse
     * @return BigDecimal[2] where [0]=dAvg, [1]=dMax, or null if no data
     */
    public BigDecimal[] findDemandMetrics(int productId, int lookbackDays) {
        // Daily demand: sum picked qty per day (from outbound_items joined to outbound_orders shipped)
        String sql =
            "SELECT "
            + "  COALESCE(AVG(daily_qty), 0) AS d_avg, "
            + "  COALESCE(MAX(daily_qty), 0) AS d_max "
            + "FROM ("
            + "  SELECT DATE(oo.shipped_at) AS ship_day, SUM(oi.picked_qty) AS daily_qty "
            + "  FROM outbound_orders oo "
            + "  JOIN outbound_items oi ON oo.outbound_id = oi.outbound_id "
            + "  WHERE oi.product_id = ? "
            + "    AND oo.status IN ('SHIPPED','DELIVERED') "
            + "    AND oo.shipped_at >= DATE_SUB(CURDATE(), INTERVAL ? DAY) "
            + "  GROUP BY DATE(oo.shipped_at)"
            + ") daily";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            ps.setInt(2, lookbackDays);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new BigDecimal[]{
                        rs.getBigDecimal("d_avg"),
                        rs.getBigDecimal("d_max")
                    };
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "ProductDAO.findDemandMetrics failed productId=" + productId, e);
        }
        return null;
    }

    /**
     * Returns lead-time metrics (l_avg, l_max) for a product over a lookback window.
     * l = days between inbound order creation (PO) and GRN received_at.
     *
     * @param productId   The product ID
     * @param lookbackDays Number of past days to analyse
     * @return BigDecimal[2] where [0]=lAvg, [1]=lMax, or null if no data
     */
    public BigDecimal[] findLeadTimeMetrics(int productId, int lookbackDays) {
        // Lead time: DATEDIFF(received_at, created_at) per inbound order
        String sql =
            "SELECT "
            + "  COALESCE(AVG(lead_days), 0) AS l_avg, "
            + "  COALESCE(MAX(lead_days), 0) AS l_max "
            + "FROM ("
            + "  SELECT DATEDIFF(io.received_at, io.created_at) AS lead_days "
            + "  FROM inbound_orders io "
            + "  JOIN inbound_items ii ON io.inbound_id = ii.inbound_id "
            + "  WHERE ii.product_id = ? "
            + "    AND io.status = 'RECEIVED' "
            + "    AND io.received_at IS NOT NULL "
            + "    AND io.created_at >= DATE_SUB(CURDATE(), INTERVAL ? DAY) "
            + "    AND DATEDIFF(io.received_at, io.created_at) >= 0 "
            + ") lt";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            ps.setInt(2, lookbackDays);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new BigDecimal[]{
                        rs.getBigDecimal("l_avg"),
                        rs.getBigDecimal("l_max")
                    };
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "ProductDAO.findLeadTimeMetrics failed productId=" + productId, e);
        }
        return null;
    }

    /**
     * Convenience method for single-product zone lookup.
     * Prefer {@link #batchFindDefaultZones()} when loading multiple products.
     */
    public List<Product.LocationConfig> findDefaultZonesByProductId(int productId) {
        List<Product.LocationConfig> list = new ArrayList<>();
        String sql = "SELECT warehouse_id, zone_id FROM product_default_zones WHERE product_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Product.LocationConfig(
                        String.valueOf(rs.getInt("warehouse_id")),
                        String.valueOf(rs.getInt("zone_id"))));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "ProductDAO: Failed to find default zones for product " + productId, e);
        }
        return list;
    }

    public boolean delete(int productId) {
        String sql = "DELETE FROM products WHERE product_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "ProductDAO: Failed to delete product " + productId, e);
            return false;
        }
    }

    // ── Row mapping helpers ─────────────────────────────────────────

    /**
     * Isolated zone update for a single (product, warehouse) pair. Used by warehouse staff
     * to set/clear the default storage zone of their own warehouse without touching other
     * warehouses' zone rows. Toggles active=1 for the chosen row; all other rows for the
     * same product keep their state.
     */
    public boolean updateZoneForWarehouse(int productId, int warehouseId, Integer zoneId) {
        if (zoneId == null) {
            String sql = "DELETE FROM product_default_zones "
                       + "WHERE product_id = ? AND warehouse_id = ?";
            try (Connection conn = DBConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, productId);
                ps.setInt(2, warehouseId);
                return ps.executeUpdate() >= 0;
            } catch (SQLException e) {
                LOGGER.log(Level.SEVERE, "ProductDAO.updateZoneForWarehouse (clear) failed", e);
                return false;
            }
        }
        String upsert = "INSERT INTO product_default_zones (product_id, warehouse_id, zone_id) "
                      + "VALUES (?, ?, ?) "
                      + "ON DUPLICATE KEY UPDATE zone_id = VALUES(zone_id)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(upsert)) {
            ps.setInt(1, productId);
            ps.setInt(2, warehouseId);
            ps.setInt(3, zoneId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "ProductDAO.updateZoneForWarehouse failed", e);
            return false;
        }
    }

    private int getProductId(ResultSet rs) throws SQLException {
        return rs.getInt("product_id");
    }

    /**
     * Maps a ResultSet row to a Product. Default zones are injected from the
     * pre-built map (passed in) instead of being fetched per-row — eliminating N+1.
     */
    private Product mapRow(ResultSet rs, List<Product.LocationConfig> zones) throws SQLException {
        Product product = new Product();
        product.setProductId(rs.getInt("product_id"));
        product.setSkuCode(rs.getString("sku_code"));
        product.setProductName(rs.getString("product_name"));
        int categoryId = rs.getInt("category_id");
        product.setCategoryId(rs.wasNull() ? null : categoryId);
        product.setBarcode(rs.getString("barcode"));
        product.setUnit(rs.getString("unit"));
        product.setMinStock(rs.getDouble("min_stock"));
        product.setMaxStock(rs.getDouble("max_stock"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            product.setCreatedAt(createdAt.toLocalDateTime());
        }
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        if (updatedAt != null) {
            product.setUpdatedAt(updatedAt.toLocalDateTime());
        }

        // Joined and transient fields — use safe getters to avoid SQLException on missing columns
        product.setCategoryName(getString(rs, "category_name"));
        product.setCreatorName(getString(rs, "creator_name"));
        product.setAttributesText(getString(rs, "attributes_text"));
        product.setShortDescription(getString(rs, "short_description"));

        double w = rs.getDouble("weight_kg");
        product.setWeightKg(rs.wasNull() ? null : w);

        double qoh = rs.getDouble("qty_on_hand");
        product.setQtyOnHand(qoh);

        double bp = rs.getDouble("base_price");
        product.setBasePrice(rs.wasNull() ? 0.0 : bp);

        double mac = rs.getDouble("mac_price");
        product.setMacPrice(rs.wasNull() ? 0.0 : mac);

        product.setDAvg(rs.getDouble("d_avg"));
        product.setDMax(rs.getDouble("d_max"));
        product.setLAvg(rs.getDouble("l_avg"));
        product.setLMax(rs.getDouble("l_max"));
        product.setSafetyStock(rs.getDouble("safety_stock"));
        product.setRopCalculated(rs.getDouble("rop_calculated"));

        int createdBy = rs.getInt("created_by");
        product.setCreatedBy(rs.wasNull() ? null : createdBy);

        // Inject pre-fetched zones — no extra DB query here
        product.setLocationConfigs(zones);

        return product;
    }

    private String getString(ResultSet rs, String col) {
        try {
            return rs.getString(col);
        } catch (SQLException e) {
            return null;
        }
    }

    /**
     * Gets the next sequence number for a category and prefix.
     * Searches for existing SKUs with format: {PREFIX}-{SEQ:03d}
     * E.g. EYE-CON-001 or EYE-001
     *
     * @param categoryId The category ID.
     * @param prefix The SKU prefix (MÃ CHA-MÃ CON or MÃ CON).
     * @return Next sequence number (1-based).
     */
    public int getNextSequence(int categoryId, String prefix) throws SQLException {
        String sql = "SELECT sku_code FROM products WHERE category_id = ?";
        int maxSeq = 0;
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, categoryId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String sku = rs.getString("sku_code");
                    if (sku != null && sku.startsWith(prefix + "-")) {
                        String seqPart = sku.substring(prefix.length() + 1);
                        if (seqPart.matches("^\\d{3}$")) {
                            try {
                                int seq = Integer.parseInt(seqPart);
                                if (seq > maxSeq) {
                                    maxSeq = seq;
                                }
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                }
            }
        }
        return maxSeq + 1;
    }
}
