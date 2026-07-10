package com.wms.service.product;

import com.wms.dao.CategoryDAO;
import com.wms.model.Category;

import java.sql.SQLException;
import java.util.List;
import java.util.regex.Pattern;

public class CategoryService {

    private static final Pattern CATEGORY_CODE_PATTERN = Pattern.compile("^[A-Z0-9]{3,4}$");

    private final CategoryDAO categoryDAO = new CategoryDAO();

    public List<Category> findAll() throws SQLException {
        return categoryDAO.findAll();
    }

    public List<Category> findActiveOnly() throws SQLException {
        return categoryDAO.findActiveOnly();
    }

    public Category findById(int categoryId) throws SQLException {
        return categoryDAO.findById(categoryId);
    }

    /**
     * Creates a new category with required category code.
     *
     * @param name Category display name.
     * @param categoryCode Category code (3-4 uppercase chars).
     * @param parentId Parent category ID (null for root).
     * @return true if successful.
     */
    public boolean createCategory(String name, String categoryCode, Integer parentId) throws SQLException {
        // Validate code format
        if (categoryCode == null || categoryCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Ma dinh danh khong duoc trong.");
        }
        categoryCode = categoryCode.trim().toUpperCase();
        if (!CATEGORY_CODE_PATTERN.matcher(categoryCode).matches()) {
            throw new IllegalArgumentException("Ma dinh danh phai la 3-4 ky tu, chi gom chu hoa va so.");
        }
        if (categoryDAO.existsByCategoryCode(categoryCode, null)) {
            throw new IllegalArgumentException("Ma dinh danh da ton tai.");
        }

        // Reject if parent (or any ancestor) is inactive — invariant: a child cannot be active
        // under an inactive ancestor.
        if (parentId != null) {
            Category parent = categoryDAO.findById(parentId);
            if (parent == null) {
                throw new IllegalArgumentException("Danh muc cha khong ton tai.");
            }
            Category inactiveAncestor = findInactiveAncestor(parentId);
            if (inactiveAncestor != null) {
                throw new IllegalArgumentException(
                    "Khong the tao danh muc con: danh muc cha '" + parent.getCategoryName()
                    + "' dang ngung hoat dong (hoac co to tien dang ngung hoat dong). "
                    + "Hay kich hoat danh muc cha truoc.");
            }
        }

        Category category = new Category();
        category.setCategoryName(name.trim());
        category.setCategoryCode(categoryCode);
        category.setParentId(parentId);
        category.setActive(true);
        // Ma dinh danh bi khoa vinh vien ngay khi tao, khong the sua bat ky luc nao.
        category.setImmutable(true);

        return categoryDAO.insert(category);
    }

    /**
     * Updates a category. Only categoryCode can be updated if not immutable.
     *
     * @param category The category with updated values.
     * @param newCategoryCode New category code (null to keep existing).
     * @return true if successful.
     */
    public boolean updateCategory(Category category, String newCategoryCode) throws SQLException {
        Category existing = categoryDAO.findById(category.getCategoryId());
        if (existing == null) {
            return false;
        }

        // Khong cho phep sua khi danh muc da bi vo hieu hoa
        if (!existing.isActive()) {
            throw new IllegalArgumentException("Danh muc da ngung hoat dong, khong the sua. Hay kich hoat lai truoc.");
        }

        // Reject when the new parent (or any of its ancestors) is inactive.
        if (category.getParentId() != null) {
            if (category.getParentId().equals(category.getCategoryId())) {
                throw new IllegalArgumentException("Danh muc khong the la danh muc cha cua chinh no.");
            }
            Category newParent = categoryDAO.findById(category.getParentId());
            if (newParent == null) {
                throw new IllegalArgumentException("Danh muc cha moi khong ton tai.");
            }
            Category inactiveAncestor = findInactiveAncestor(category.getParentId());
            if (inactiveAncestor != null) {
                throw new IllegalArgumentException(
                    "Khong the chuyen danh muc vao nhanh dang ngung hoat dong ("
                    + inactiveAncestor.getCategoryName()
                    + "). Hay kich hoat danh muc cha truoc.");
            }
        }

        // Ma dinh danh bi khoa vinh vien ngay khi tao, khong the sua. Server-side
        // guard: loi ngay neu form co gui len gia tri code moi (bao ve khi UI loi).
        if (newCategoryCode != null && !newCategoryCode.isEmpty()
                && !newCategoryCode.equalsIgnoreCase(existing.getCategoryCode())) {
            throw new IllegalArgumentException("Ma dinh danh da bi khoa vinh vien, khong the sua.");
        }
        // Re-sync immutable flag in case DB row was migrated.
        category.setCategoryCode(existing.getCategoryCode());
        return categoryDAO.update(category, false);
    }

    public ValidationResult validateCategoryData(String name, Integer categoryId, Integer parentId) {
        if (name == null || name.trim().isEmpty()) {
            return ValidationResult.failure("Ten danh muc khong duoc bo trong.");
        }
        if (parentId != null && parentId.equals(categoryId)) {
            return ValidationResult.failure("Danh muc khong the la danh muc cha cua chinh no.");
        }
        return ValidationResult.success();
    }

    /**
     * Validates category code format.
     *
     * @param code The code to validate.
     * @return ValidationResult with success/failure.
     */
    public ValidationResult validateCategoryCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return ValidationResult.failure("Ma dinh danh khong duoc bo trong.");
        }
        String normalized = code.trim().toUpperCase();
        if (!CATEGORY_CODE_PATTERN.matcher(normalized).matches()) {
            return ValidationResult.failure("Ma dinh danh phai la 3-4 ky tu, chi gom chu hoa va so (VD: EYE, SUN).");
        }
        return ValidationResult.success();
    }

    /**
     * Checks if a category can be hard-deleted (no products).
     *
     * @param categoryId The category ID to check.
     * @return true if can hard delete, false if only deactivate available.
     */
    public boolean canHardDelete(int categoryId) throws SQLException {
        return !categoryDAO.hasProducts(categoryId);
    }

    /**
     * Deletes or deactivates a category based on product presence.
     *
     * @param categoryId The category ID.
     * @return Result indicating success and action taken.
     */
    public DeleteResult deleteCategory(int categoryId) throws SQLException {
        if (categoryDAO.hasProducts(categoryId)) {
            // Soft delete - cascade-deactivate root + descendants
            int affected = categoryDAO.deactivateWithDescendants(categoryId);
            return new DeleteResult(affected > 0, true, "Danh muc da co san pham. Da ngung hoat dong (gom ca danh muc con).");
        } else {
            // Hard delete
            boolean deleted = categoryDAO.delete(categoryId);
            return new DeleteResult(deleted, false, deleted ? "Xoa danh muc thanh cong." : "Xoa danh muc that bai.");
        }
    }

    /**
     * Walks up the parent chain from the given category. Returns the first
     * ancestor that is inactive, or null if every ancestor is active. Useful
     * to enforce the "no active descendant under inactive ancestor" rule.
     */
    public Category findInactiveAncestor(int categoryId) throws SQLException {
        Integer parentId = categoryId;
        // Re-resolve to handle the case where caller passed an already-known id.
        Category current = categoryDAO.findById(categoryId);
        if (current == null) {
            return null;
        }
        parentId = current.getParentId();
        while (parentId != null) {
            Category parent = categoryDAO.findById(parentId);
            if (parent == null) {
                return null;
            }
            if (!parent.isActive()) {
                return parent;
            }
            parentId = parent.getParentId();
        }
        return null;
    }

    /**
     * Repairs inconsistent state where some descendant is active while one
     * of its ancestors is inactive. Sets active = 0 on every such descendant.
     *
     * @return Number of categories that were deactivated by this repair.
     */
    public int ensureCascadeConsistency() throws SQLException {
        List<Category> all = categoryDAO.findAll();
        int repaired = 0;
        for (Category c : all) {
            if (!c.isActive()) {
                continue;
            }
            if (findInactiveAncestor(c.getCategoryId()) != null) {
                categoryDAO.deactivate(c.getCategoryId());
                repaired++;
            }
        }
        return repaired;
    }

    /**
     * Deactivates a category and all its descendants (cascade).
     *
     * @param categoryId The category ID.
     * @return Number of categories deactivated (root + descendants).
     */
    public int deactivateCategory(int categoryId) throws SQLException {
        return categoryDAO.deactivateWithDescendants(categoryId);
    }

    /**
     * Reactivates a previously deactivated category. Does NOT touch descendants
     * because in the current model a parent is only deactivated by an explicit
     * cascade, so children stay in whatever state they were left in.
     *
     * @param categoryId The category ID to reactivate.
     * @return true if successful.
     */
    public boolean reactivateCategory(int categoryId) throws SQLException {
        Category existing = categoryDAO.findById(categoryId);
        if (existing == null) {
            return false;
        }
        if (existing.isActive()) {
            return true;
        }
        return categoryDAO.activate(categoryId);
    }

    public static class ValidationResult {
        private final boolean success;
        private final String message;

        private ValidationResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public static ValidationResult success() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult failure(String message) {
            return new ValidationResult(false, message);
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }

    public static class DeleteResult {
        private final boolean success;
        private final boolean wasSoftDelete;
        private final String message;

        public DeleteResult(boolean success, boolean wasSoftDelete, String message) {
            this.success = success;
            this.wasSoftDelete = wasSoftDelete;
            this.message = message;
        }

        public boolean isSuccess() { return success; }
        public boolean isWasSoftDelete() { return wasSoftDelete; }
        public String getMessage() { return message; }
    }
}
