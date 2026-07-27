package com.wms.service.product;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.dao.CategoryDAO;
import com.wms.dao.ProductDAO;
import com.wms.model.Category;
import com.wms.model.Product;
import com.wms.service.notification.ProductChangeNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductDAO productDAO = new ProductDAO();
    private final CategoryDAO categoryDAO = new CategoryDAO();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ProductChangeNotificationService notificationService = new ProductChangeNotificationService();

    public List<Product> findAll() {
        return productDAO.findAll();
    }

    public String toJson(List<Product> products) {
        try {
            return objectMapper.writeValueAsString(products);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    public List<Category> findAllCategories() {
        return categoryDAO.findAll();
    }

    public Product findById(int productId) {
        return productDAO.findById(productId);
    }

    public boolean createProduct(Product product, Integer createdByUserId) {
        if (product.getUnit() == null || product.getUnit().trim().isEmpty()) {
            product.setUnit("Cái");
        }
        product.setCreatedBy(createdByUserId);
        boolean success = productDAO.insert(product);
        if (success) {
            log.info("Product created: name={} sku={} userId={}",
                    product.getProductName(), product.getSkuCode(), createdByUserId);
        } else {
            log.error("Product create failed: name={} sku={}", product.getProductName(), product.getSkuCode());
        }
        return success;
    }

    public UpdateResult updateProduct(int productId, Product updates) {
        Product existing = productDAO.findById(productId);
        if (existing == null) {
            log.warn("Update product failed: not found productId={}", productId);
            return UpdateResult.failure("Sản phẩm không tồn tại.");
        }

        if (updates.getProductName() != null && !updates.getProductName().trim().isEmpty()) {
            existing.setProductName(updates.getProductName().trim());
        }
        if (updates.getCategoryId() != null) {
            existing.setCategoryId(updates.getCategoryId());
        }
        if (updates.getAttributesText() != null) {
            existing.setAttributesText(updates.getAttributesText().trim());
        }
        if (updates.getDimensions() != null) {
            existing.setDimensions(updates.getDimensions().trim());
        }
        if (updates.getWeightKg() != null) {
            existing.setWeightKg(updates.getWeightKg());
        }
        if (updates.getBarcode() != null) {
            existing.setBarcode(updates.getBarcode().trim());
        }
        if (updates.getUnit() != null) {
            existing.setUnit(updates.getUnit().trim());
        }
        if (updates.getBasePrice() != null) {
            existing.setBasePrice(updates.getBasePrice());
        }

        boolean success = productDAO.update(existing);
        if (!success) {
            log.error("Product update DAO failed: productId={}", productId);
            return UpdateResult.failure("Không thể cập nhật sản phẩm.");
        }
        log.info("Product updated: productId={}", productId);

        // Emit notification if product is listed on any channel
        String changeDetails = buildChangeDetails(existing, updates);
        notificationService.notifyIfListed(existing, "UPDATE", changeDetails);

        return UpdateResult.success();
    }

    /**
     * Warehouse Staff edit: updates ONLY the min/max stock and the default zone for ONE warehouse.
     *
     * Staff may change ONLY minStock and maxStock on the product master; the zone change is
     * isolated to {@code warehouseId} via {@link ProductDAO#updateZoneForWarehouse}, so other
     * warehouses' zone rows are never touched. Pass {@code zoneId = null} to clear this
     * warehouse's zone. {@code minStock}/{@code maxStock} null = keep current value.
     */
    public UpdateResult updateProductForWarehouse(int productId, Double minStock, Double maxStock,
                                                  int warehouseId, Integer zoneId) {
        Product existing = productDAO.findById(productId);
        if (existing == null) {
            log.warn("Update product failed: not found productId={}", productId);
            return UpdateResult.failure("Sản phẩm không tồn tại.");
        }

        if (minStock != null) existing.setMinStock(minStock);
        if (maxStock != null) existing.setMaxStock(maxStock);

        if (!productDAO.update(existing)) {
            log.error("Product update DAO failed: productId={}", productId);
            return UpdateResult.failure("Không thể cập nhật sản phẩm.");
        }
        if (!productDAO.updateZoneForWarehouse(productId, warehouseId, zoneId)) {
            log.error("Product zone update failed: productId={} warehouseId={}", productId, warehouseId);
            return UpdateResult.failure("Không thể cập nhật khu vực lưu trữ của kho.");
        }
        log.info("Product updated for warehouse: productId={} warehouseId={} zoneId={} min={} max={}",
                productId, warehouseId, zoneId, minStock, maxStock);
        return UpdateResult.success();
    }

    public DeleteResult deleteProduct(int productId) {
        Product existing = productDAO.findById(productId);
        if (existing == null) {
            log.warn("Delete product failed: not found productId={}", productId);
            return DeleteResult.failure("Sản phẩm không tồn tại.");
        }

        // 1. Notify and delete/deactivate product on all connected sales channels (Website storefront / Lazada)
        try {
            com.wms.dao.ChannelProductDAO cpDao = new com.wms.dao.ChannelProductDAO();
            com.wms.dao.ChannelDAO channelDao = new com.wms.dao.ChannelDAO();
            com.wms.service.website.WebsiteProductService wps = new com.wms.service.website.WebsiteProductService();
            com.wms.service.lazada.LazadaProductService lps = new com.wms.service.lazada.LazadaProductService();

            List<com.wms.model.ChannelProduct> cps = cpDao.findByProduct(productId);
            if (cps != null && !cps.isEmpty()) {
                for (com.wms.model.ChannelProduct cp : cps) {
                    com.wms.model.Channel ch = channelDao.findById(cp.getChannelId());
                    if (ch != null) {
                        if ("Website".equalsIgnoreCase(ch.getPlatform())) {
                            log.info("ProductService: deleting product {} from Website channel {}...", productId, ch.getChannelName());
                            wps.deleteProduct(ch, cp.getId());
                        } else if ("Lazada".equalsIgnoreCase(ch.getPlatform())) {
                            log.info("ProductService: deleting product {} from Lazada channel {}...", productId, ch.getChannelName());
                            lps.deleteProduct(ch, cp.getId());
                        }
                    }
                }
            }

            // Also check all active Website channels to send direct storefront delete call for this product ID
            List<com.wms.model.Channel> channels = channelDao.findAll();
            if (channels != null) {
                for (com.wms.model.Channel ch : channels) {
                    if (ch != null && "Website".equalsIgnoreCase(ch.getPlatform())) {
                        wps.deleteProductByProductId(ch, productId);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("ProductService: error notifying channels during deletion for productId={}: {}", productId, e.getMessage());
        }

        // 2. Cascade delete from WMS database
        boolean success = productDAO.delete(productId);
        if (!success) {
            log.error("Product delete DAO failed: productId={}", productId);
            return DeleteResult.failure("Không thể xóa sản phẩm.");
        }

        // 3. Send change notification
        notificationService.notifyIfListed(existing, "DELETE", "Xóa sản phẩm " + productId);
        log.info("Product deleted: productId={}", productId);
        return DeleteResult.success();
    }

    public Integer resolveCategoryId(String categoryName) {
        if (categoryName == null || categoryName.trim().isEmpty()) {
            return null;
        }
        List<Category> all = categoryDAO.findAll();
        for (Category c : all) {
            if (c.getCategoryName().equalsIgnoreCase(categoryName.trim())) {
                return c.getCategoryId();
            }
        }
        return null;
    }

    public static class UpdateResult {
        private final boolean success;
        private final String message;

        private UpdateResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public static UpdateResult success() {
            return new UpdateResult(true, null);
        }

        public static UpdateResult failure(String message) {
            return new UpdateResult(false, message);
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }

    public static class DeleteResult {
        private final boolean success;
        private final String message;

        private DeleteResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public static DeleteResult success() {
            return new DeleteResult(true, null);
        }

        public static DeleteResult failure(String message) {
            return new DeleteResult(false, message);
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }

    private String buildChangeDetails(Product existing, Product updates) {
        java.util.List<String> changes = new java.util.ArrayList<>();
        if (updates.getProductName() != null && !updates.getProductName().equals(existing.getProductName())) {
            changes.add("name");
        }
        if (updates.getBasePrice() != null && updates.getBasePrice() != existing.getBasePrice()) {
            changes.add("price");
        }
        if (updates.getCategoryId() != null && !updates.getCategoryId().equals(existing.getCategoryId())) {
            changes.add("category");
        }
        if (updates.getAttributesText() != null && !updates.getAttributesText().equals(existing.getAttributesText())) {
            changes.add("attributes");
        }
        return String.join(", ", changes.isEmpty() ? java.util.List.of("other") : changes);
    }
}
