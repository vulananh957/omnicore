package com.wms.service.product;

import com.wms.dao.CategoryDAO;
import com.wms.dao.ProductDAO;
import com.wms.model.Category;

import java.sql.SQLException;
import java.util.List;
import java.util.regex.Pattern;

/**
 * SkuGeneratorService — Business logic for auto-generating Master SKUs.
 * 
 * SKU format: {CATEGORY_CODE}-{YYYYMMDD}-{SEQ:03d}
 * Example: EYE-20250611-001
 * 
 * Rules:
 * - Category code must be uppercase 3-4 chars
 * - Date is current date at generation time
 * - Sequence is auto-incremented per category per day (001-999)
 * - Category must be active
 */
public class SkuGeneratorService {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z0-9]{3,4}$");

    private final CategoryDAO categoryDAO = new CategoryDAO();
    private final ProductDAO productDAO = new ProductDAO();

    /**
     * Generates the next SKU for a given category.
     * 
     * @param categoryId The category ID.
     * @return Generated SKU string.
     * @throws IllegalArgumentException if category not found or invalid.
     */
    public String generateNextSku(int categoryId) throws SQLException {
        Category category = categoryDAO.findByIdWithParent(categoryId);
        if (category == null) {
            throw new IllegalArgumentException("Danh muc khong ton tai.");
        }
        if (!category.isActive()) {
            throw new IllegalArgumentException("Danh muc da ngung hoat dong.");
        }

        String categoryCode = category.getCategoryCode();
        if (categoryCode == null || !CODE_PATTERN.matcher(categoryCode).matches()) {
            throw new IllegalArgumentException("Ma danh muc khong hop le.");
        }

        String parentCode = category.getParentCode();
        String prefix = (parentCode != null && !parentCode.isEmpty())
                ? (parentCode + "-" + categoryCode)
                : categoryCode;

        int nextSeq = productDAO.getNextSequence(categoryId, prefix);
        
        if (nextSeq > 999) {
            throw new IllegalStateException("Da vuot qua gioi han 999 san pham cho danh muc nay.");
        }

        return String.format("%s-%03d", prefix, nextSeq);
    }

    /**
     * Validates a SKU format.
     * 
     * @param sku The SKU to validate.
     * @return true if valid format.
     */
    public boolean isValidSkuFormat(String sku) {
        if (sku == null || sku.isEmpty()) return false;
        // Format: EYE-001 or EYE-CON-001
        return sku.matches("^[A-Z0-9]{3,4}(-[A-Z0-9]{3,4})?-\\d{3}$");
    }

    /**
     * Extracts category code from SKU.
     * 
     * @param sku The SKU.
     * @return Category code or null if invalid.
     */
    public String extractCategoryCode(String sku) {
        if (!isValidSkuFormat(sku)) return null;
        String[] parts = sku.split("-");
        if (parts.length == 3) {
            return parts[1]; // Mã con is the second part (e.g. EYE-CON-001 -> CON)
        } else {
            return parts[0]; // Mã con is the first part (e.g. EYE-001 -> EYE)
        }
    }

    /**
     * Gets all active categories for SKU generation dropdown.
     */
    public List<Category> getActiveCategories() throws SQLException {
        return categoryDAO.findActiveOnly();
    }

    /**
     * Gets category info for SKU preview.
     */
    public Category getCategoryWithParent(int categoryId) throws SQLException {
        return categoryDAO.findByIdWithParent(categoryId);
    }
}
