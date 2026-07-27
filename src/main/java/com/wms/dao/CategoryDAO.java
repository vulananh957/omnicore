package com.wms.dao;

import com.wms.model.Category;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * CategoryDAO — Data Access Object for managing product category records.
 *
 * <p>Now extends {@link BaseDAO} so all the boilerplate connection / try /
 * catch / bind / map noise lives in one place. Method signatures and SQL
 * strings are unchanged from the pre-refactor version.</p>
 *
 * Business rules:
 * - categoryCode: ma dinh danh 3-4 ky tu, UPPERCASE, bat bien sau khi co san pham
 * - isImmutable: true = da co san pham, khong cho sua categoryCode
 * - active: true = dang hoat dong, false = ngung hoat dong (khong xoa)
 */
public class CategoryDAO extends BaseDAO {

    private static final Logger LOGGER = Logger.getLogger(CategoryDAO.class.getName());

    /** Shared row mapper used by every SELECT in this DAO. */
    private static final RowMapper<Category> MAP_CATEGORY = CategoryDAO::mapResultSetToCategory;

    public CategoryDAO() {
    }

    public List<Category> findAll() {
        return queryList(LOGGER, "SELECT * FROM categories ORDER BY category_id ASC", MAP_CATEGORY);
    }

    public List<Category> findActiveOnly() {
        return queryList(LOGGER,
            "SELECT * FROM categories WHERE active = 1 ORDER BY category_id ASC", MAP_CATEGORY);
    }

    /** Returns category_id → count of active products, for storefront category listings. */
    public java.util.Map<Integer, Integer> countActiveProductsByCategory() {
        List<java.util.Map.Entry<Integer, Integer>> rows = queryList(LOGGER,
            "SELECT category_id, COUNT(*) AS cnt FROM products "
                + "WHERE active = 1 AND category_id IS NOT NULL GROUP BY category_id",
            rs -> java.util.Map.entry(rs.getInt("category_id"), rs.getInt("cnt")));
        java.util.Map<Integer, Integer> result = new java.util.HashMap<>();
        for (java.util.Map.Entry<Integer, Integer> row : rows) {
            result.put(row.getKey(), row.getValue());
        }
        return result;
    }

    public Category findById(int categoryId) {
        return queryOne(LOGGER, "SELECT * FROM categories WHERE category_id = ?", MAP_CATEGORY, categoryId);
    }

    public Category findByIdWithParent(int categoryId) {
        String sql = "SELECT c.*, p.category_code AS parent_code " +
                     "FROM categories c " +
                     "LEFT JOIN categories p ON c.parent_id = p.category_id " +
                     "WHERE c.category_id = ?";
        return queryOne(LOGGER, sql, rs -> {
            Category cat = mapResultSetToCategory(rs);
            cat.setParentCode(rs.getString("parent_code"));
            return cat;
        }, categoryId);
    }

    public Category findByCategoryCode(String categoryCode) {
        String code = categoryCode == null ? null : categoryCode.toUpperCase();
        return queryOne(LOGGER, "SELECT * FROM categories WHERE category_code = ?", MAP_CATEGORY, code);
    }

    public List<Category> findByParentId(Integer parentId, boolean activeOnly) {
        String sql;
        if (parentId == null) {
            sql = activeOnly
                ? "SELECT * FROM categories WHERE parent_id IS NULL AND active = 1 ORDER BY category_id ASC"
                : "SELECT * FROM categories WHERE parent_id IS NULL ORDER BY category_id ASC";
            return queryList(LOGGER, sql, MAP_CATEGORY);
        }
        sql = activeOnly
            ? "SELECT * FROM categories WHERE parent_id = ? AND active = 1 ORDER BY category_id ASC"
            : "SELECT * FROM categories WHERE parent_id = ? ORDER BY category_id ASC";
        return queryList(LOGGER, sql, MAP_CATEGORY, parentId);
    }

    public List<Category> findByParentId(Integer parentId) {
        return findByParentId(parentId, false);
    }

    public boolean existsByCategoryCode(String categoryCode, Integer excludeId) {
        String code = categoryCode == null ? null : categoryCode.toUpperCase();
        String sql = (excludeId != null)
            ? "SELECT COUNT(*) FROM categories WHERE category_code = ? AND category_id != ?"
            : "SELECT COUNT(*) FROM categories WHERE category_code = ?";
        Integer count = queryOne(LOGGER, sql, rs -> rs.getInt(1), code, excludeId);
        return count != null && count > 0;
    }

    public boolean insert(Category category) {
        String sql = "INSERT INTO categories (category_code, category_name, parent_id, description, level_depth, is_immutable, active) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        String code = category.getCategoryCode() != null ? category.getCategoryCode().toUpperCase() : null;
        int levelDepth = levelDepthOf(category);
        int rows = update(LOGGER, sql,
            code,
            category.getCategoryName(),
            category.getParentId(),                       // setObject handles null
            category.getDescription(),
            levelDepth,
            category.isImmutable() ? 1 : 0,
            category.isActive() ? 1 : 0);
        return rows > 0;
    }

    public boolean update(Category category, boolean updateCode) {
        // The two update shapes have a different parameter order, so they
        // can't be folded into the simple update() helper. We still use
        // BaseDAO for connection + logging consistency.
        String sql;
        if (updateCode) {
            sql = "UPDATE categories SET category_code = ?, category_name = ?, parent_id = ?, description = ?, level_depth = ? WHERE category_id = ?";
        } else {
            sql = "UPDATE categories SET category_name = ?, parent_id = ?, description = ?, level_depth = ? WHERE category_id = ?";
        }
        int levelDepth = levelDepthOf(category);
        try (Connection conn = openConnection(LOGGER);
             PreparedStatement ps = conn == null ? null : conn.prepareStatement(sql)) {
            if (ps == null) return false;
            int i = 1;
            if (updateCode) {
                String code = category.getCategoryCode() != null ? category.getCategoryCode().toUpperCase() : null;
                ps.setString(i++, code);
            }
            ps.setString(i++, category.getCategoryName());
            setNullableInt(ps, i++, category.getParentId());
            ps.setString(i++, category.getDescription());
            ps.setInt(i++, levelDepth);
            ps.setInt(i, category.getCategoryId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "CategoryDAO: Failed to update category " + category.getCategoryId(), e);
            return false;
        }
    }

    public boolean update(Category category) {
        return update(category, false);
    }

    public boolean setImmutable(int categoryId, boolean immutable) {
        return update(LOGGER, "UPDATE categories SET is_immutable = ? WHERE category_id = ?",
            immutable ? 1 : 0, categoryId) > 0;
    }

    public boolean hasProducts(int categoryId) {
        Integer count = queryOne(LOGGER,
            "SELECT COUNT(*) FROM products WHERE category_id = ?",
            rs -> rs.getInt(1), categoryId);
        return count != null && count > 0;
    }

    public boolean deactivate(int categoryId) {
        return update(LOGGER, "UPDATE categories SET active = 0 WHERE category_id = ?", categoryId) > 0;
    }

    public boolean activate(int categoryId) {
        return update(LOGGER, "UPDATE categories SET active = 1 WHERE category_id = ?", categoryId) > 0;
    }

    public int deactivateWithDescendants(int rootCategoryId) {
        List<Integer> toDeactivate = new ArrayList<>();
        java.util.Deque<Integer> queue = new java.util.ArrayDeque<>();
        queue.add(rootCategoryId);
        while (!queue.isEmpty()) {
            int current = queue.poll();
            toDeactivate.add(current);
            for (Category child : findByParentId(current)) {
                queue.add(child.getCategoryId());
            }
        }
        if (toDeactivate.isEmpty()) return 0;

        StringBuilder sql = new StringBuilder("UPDATE categories SET active = 0 WHERE category_id IN (");
        for (int i = 0; i < toDeactivate.size(); i++) {
            if (i > 0) sql.append(",");
            sql.append("?");
        }
        sql.append(")");

        return update(LOGGER, sql.toString(), toDeactivate.toArray());
    }

    public boolean delete(int categoryId) {
        return update(LOGGER, "DELETE FROM categories WHERE category_id = ?", categoryId) > 0;
    }

    // ── Internal helpers ─────────────────────────────────────────────────

    /** Map a ResultSet row to a Category object. */
    private static Category mapResultSetToCategory(ResultSet rs) throws SQLException {
        Category category = new Category();
        category.setCategoryId(rs.getInt("category_id"));
        category.setCategoryCode(rs.getString("category_code"));
        category.setCategoryName(rs.getString("category_name"));
        int parentId = rs.getInt("parent_id");
        category.setParentId(rs.wasNull() ? null : parentId);
        category.setDescription(rs.getString("description"));
        category.setLevelDepth(rs.getInt("level_depth"));
        category.setImmutable(rs.getInt("is_immutable") == 1);
        int active = rs.getInt("active");
        category.setActive(active == 1);
        return category;
    }

    /** Compute level depth for a category by walking up to the parent. */
    private int levelDepthOf(Category category) {
        if (category.getParentId() == null) return 0;
        Category parent = findById(category.getParentId());
        return (parent == null) ? 0 : parent.getLevelDepth() + 1;
    }

    private void setNullableInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) ps.setNull(index, Types.INTEGER);
        else ps.setInt(index, value);
    }
}
