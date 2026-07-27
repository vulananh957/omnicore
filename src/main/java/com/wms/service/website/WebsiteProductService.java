package com.wms.service.website;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.dao.ChannelProductDAO;
import com.wms.dao.ProductDAO;
import com.wms.dao.ProductImageDAO;
import com.wms.dao.SkuMappingDAO;
import com.wms.model.Channel;
import com.wms.model.ChannelProduct;
import com.wms.model.Product;
import com.wms.model.ProductImage;
import com.wms.model.SkuMapping;
import com.wms.service.channel.WebsiteHttpClient;

import com.wms.service.lazada.LazadaProductService.PullResult;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service handling product synchronization from WMS Hub to the Website storefront.
 * Uses WebsiteHttpClient with HMAC-SHA256 signature authorization.
 */
import com.fasterxml.jackson.databind.JsonNode;
import com.wms.dao.InventoryDAO;
import java.time.LocalDateTime;

public class WebsiteProductService {

    private static final Logger LOG = Logger.getLogger(WebsiteProductService.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final WebsiteHttpClient client;
    private final ProductDAO productDAO = new ProductDAO();
    private final ProductImageDAO productImageDAO = new ProductImageDAO();
    private final ChannelProductDAO channelProductDAO = new ChannelProductDAO();
    private final SkuMappingDAO skuMappingDAO = new SkuMappingDAO();
    private final InventoryDAO inventoryDAO = new InventoryDAO();

    /** Default constructor — uses real HTTP client (production path). */
    public WebsiteProductService() {
        this.client = new WebsiteHttpClient();
    }

    /**
     * Injectable constructor for testing — allows mock WebsiteHttpClient to be passed in.
     *
     * @param client mock or real WebsiteHttpClient instance
     */
    public WebsiteProductService(WebsiteHttpClient client) {
        this.client = client;
    }

    /**
     * Pushes a product and its images to the Website storefront database.
     * Maps to POST /api/v1/products endpoint.
     */
    public boolean pushProduct(Channel channel, int productId) {
        return pushProduct(channel, productId, null);
    }

    /**
     * Pushes a product to the Website storefront, optionally overriding the image set
     * with URLs from the wizard.
     */
    public boolean pushProduct(Channel channel, int productId, List<String> customImageUrls) {
        Product p = productDAO.findById(productId);
        if (p == null) {
            LOG.warning("WebsiteProductService: product not found in WMS for ID " + productId);
            return false;
        }

        ChannelProduct cp = channelProductDAO.findByProductAndChannel(productId, channel.getChannelId());
        if (cp == null) {
            // Stage skeleton row if not already present
            cp = new ChannelProduct();
            cp.setChannelId(channel.getChannelId());
            cp.setProductId(productId);
            cp.setChannelSkuCode(p.getSkuCode());
            cp.setChannelPrice(BigDecimal.valueOf(p.getBasePrice()));
            cp.setChannelStock(BigDecimal.ZERO);
            cp.setStatus("PENDING");
            channelProductDAO.insert(cp);
            cp = channelProductDAO.findByProductAndChannel(productId, channel.getChannelId());
        } else if (cp.getChannelItemId() != null && !cp.getChannelItemId().trim().isEmpty()) {
            LOG.info("WebsiteProductService: product " + p.getSkuCode() + " already exists on Website (channel_item_id=" 
                    + cp.getChannelItemId() + "). Redirecting to updateProduct to prevent duplicate storefront entry.");
            return updateProduct(channel, cp.getId(), null, null);
        }

        List<String> imageUrls = new ArrayList<>();
        if (customImageUrls != null && !customImageUrls.isEmpty()) {
            for (String url : customImageUrls) {
                if (url != null && !url.isBlank()) {
                    imageUrls.add(url.trim());
                }
            }
        } else {
            List<ProductImage> images = productImageDAO.findByProductId(productId);
            for (ProductImage img : images) {
                if (img.getImageUrl() != null && !img.getImageUrl().isBlank()) {
                    imageUrls.add(img.getImageUrl().trim());
                }
            }
        }

        try {
            Map<String, Object> payload = buildPayload(p, cp, imageUrls);
            String json = MAPPER.writeValueAsString(payload);

            LOG.info("WebsiteProductService: pushing product " + p.getSkuCode() + " to Website...");
            String response = client.post("/api/v1/products", json);

            if (response != null) {
                JsonNode resNode = MAPPER.readTree(response);
                if (resNode.path("success").asBoolean(false)) {
                    // Success path
                    int cpId = cp != null && cp.getId() > 0 ? cp.getId() : ensureChannelProductRow(cp, channel, p);
                    LOG.info("WebsiteProductService: cpId=" + cpId + " response=" + response.substring(0, Math.min(80, response.length())));
                    if (cpId > 0) {
                        channelProductDAO.recordPushSuccess(
                                cpId,
                                String.valueOf(productId), // channel_item_id is WMS product_id for Website channel
                                null,                      // no lazada_sku_id
                                cp != null && cp.getChannelStock() != null ? cp.getChannelStock() : BigDecimal.ZERO
                        );
                    }

                    // Add or update mapping
                    SkuMapping mapping = skuMappingDAO.findMappingByChannelAndExternalSku(
                            channel.getChannelId(), String.valueOf(productId));
                    if (mapping == null) {
                        mapping = new SkuMapping();
                        mapping.setSkuId(productId);
                        mapping.setChannelId(channel.getChannelId());
                        mapping.setExternalSku(String.valueOf(productId));
                        mapping.setSellerSku(p.getSkuCode());
                        mapping.setSyncStatus("SYNCED");
                        mapping.setLastSyncAt(LocalDateTime.now());
                        skuMappingDAO.insert(mapping);
                    } else {
                        skuMappingDAO.updateSyncStatus(mapping.getMappingId(), "SYNCED");
                    }

                    skuMappingDAO.resolveCatalogExceptions(channel.getChannelId());

                    LOG.info("WebsiteProductService: product " + p.getSkuCode() + " pushed successfully.");
                    return true;
                } else {
                    LOG.warning("WebsiteProductService: push failed on storefront: " + resNode.path("message").asText());
                }
            } else {
                LOG.warning("WebsiteProductService: HTTP post returned null for product " + p.getSkuCode());
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "WebsiteProductService: push failed for product " + p.getSkuCode(), e);
            System.err.println("[WPS DEBUG] Exception in pushProduct: " + e.getClass().getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Updates an existing product's catalog details on the Website.
     * Maps to PUT /api/v1/products/{id} endpoint.
     */
    public boolean updateProduct(Channel channel, int channelProductId, BigDecimal overridePrice, String overrideDescription) {
        ChannelProduct cp = channelProductDAO.findById(channelProductId);
        if (cp == null) {
            LOG.warning("WebsiteProductService: channel product not found for ID " + channelProductId);
            return false;
        }

        if (cp.getChannelItemId() == null || cp.getChannelItemId().trim().isEmpty()) {
            LOG.info("WebsiteProductService: channel_item_id is null for ID " + channelProductId + ". Redirecting to pushProduct.");
            return pushProduct(channel, cp.getProductId());
        }

        Product p = productDAO.findById(cp.getProductId());
        if (p == null) {
            return false;
        }

        List<ProductImage> images = productImageDAO.findByProductId(cp.getProductId());
        List<String> imageUrls = new ArrayList<>();
        for (ProductImage img : images) {
            if (img.getImageUrl() != null && !img.getImageUrl().isBlank()) {
                imageUrls.add(img.getImageUrl().trim());
            }
        }

        // Apply overrides
        if (overridePrice != null) cp.setChannelPrice(overridePrice);
        if (overrideDescription != null) cp.setDescription(overrideDescription);

        try {
            Map<String, Object> payload = buildPayload(p, cp, imageUrls);
            String json = MAPPER.writeValueAsString(payload);

            String apiPath = "/api/v1/products/" + cp.getChannelItemId();
            LOG.info("WebsiteProductService: updating product " + p.getSkuCode() + " on Website...");
            String response = client.put(apiPath, json);

            if (response != null) {
                JsonNode resNode = MAPPER.readTree(response);
                if (resNode.path("success").asBoolean(false)) {
                    channelProductDAO.update(cp);
                    channelProductDAO.recordPushSuccess(
                            cp.getId(),
                            cp.getChannelItemId(),
                            null,
                            cp.getChannelStock() != null ? cp.getChannelStock() : BigDecimal.ZERO
                    );
                    LOG.info("WebsiteProductService: product " + p.getSkuCode() + " updated successfully.");
                    return true;
                } else {
                    LOG.warning("WebsiteProductService: update failed on storefront: " + resNode.path("message").asText());
                }
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "WebsiteProductService: update failed for product " + p.getSkuCode(), e);
        }
        return false;
    }

    /**
     * Sets a product as INACTIVE (de-listed) on the Website storefront.
     * Maps to DELETE /api/v1/products/{id} endpoint.
     */
    public boolean deleteProduct(Channel channel, int channelProductId) {
        ChannelProduct cp = channelProductDAO.findById(channelProductId);
        if (cp == null) {
            return false;
        }

        if (cp.getChannelItemId() == null || cp.getChannelItemId().trim().isEmpty()) {
            channelProductDAO.delete(channelProductId);
            skuMappingDAO.deleteByProductAndChannel(cp.getProductId(), cp.getChannelId());
            return true;
        }

        try {
            String apiPath = "/api/v1/products/" + cp.getChannelItemId();
            LOG.info("WebsiteProductService: removing/deactivating product " + cp.getChannelSkuCode() + " on Website...");
            String response = client.delete(apiPath);

            if (response != null) {
                JsonNode resNode = MAPPER.readTree(response);
                if (resNode.path("success").asBoolean(false)) {
                    LOG.info("WebsiteProductService: product deactivated on storefront.");
                } else {
                    LOG.warning("WebsiteProductService: delete failed on storefront: " + resNode.path("message").asText());
                }
            } else {
                LOG.warning("WebsiteProductService: delete HTTP call returned null/error for channel_item_id=" + cp.getChannelItemId());
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "WebsiteProductService: delete failed for SKU " + cp.getChannelSkuCode(), e);
        }

        // Always clean up WMS local records so user can delete test/orphaned products
        channelProductDAO.delete(channelProductId);
        skuMappingDAO.deleteByProductAndChannel(cp.getProductId(), cp.getChannelId());
        LOG.info("WebsiteProductService: product removed locally from WMS.");
        return true;
    }

    /**
     * Directly deactivates/removes a product from the Website storefront by WMS product_id.
     */
    public boolean deleteProductByProductId(Channel channel, int productId) {
        try {
            String apiPath = "/api/v1/products/" + productId;
            LOG.info("WebsiteProductService: removing/deactivating product ID " + productId + " on Website storefront...");
            String response = client.delete(apiPath);
            if (response != null) {
                LOG.info("WebsiteProductService: storefront delete response for product " + productId + ": " + response);
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "WebsiteProductService: deleteProductByProductId failed for productId=" + productId, e);
        }
        return true;
    }

    /**
     * Pushes stock quantities directly to the Website storefront.
     * Used when WMS inventory changes (outbound order shipped or inbound received).
     */
    public boolean syncStock(Channel channel, int productId, int qtyAvailable) {
        ChannelProduct cp = channelProductDAO.findByProductAndChannel(productId, channel.getChannelId());
        if (cp == null || cp.getChannelItemId() == null || cp.getChannelItemId().trim().isEmpty()) {
            // Cannot sync stock for a product that hasn't been staging-pushed yet
            return false;
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("product_id", productId);
            payload.put("qty_available", qtyAvailable);

            String json = MAPPER.writeValueAsString(payload);
            String apiPath = "/api/v1/products/" + cp.getChannelItemId() + "/stock";

            LOG.info("WebsiteProductService: syncing stock (" + qtyAvailable + ") for product ID " + productId + "...");
            String response = client.put(apiPath, json);

            if (response != null) {
                JsonNode resNode = MAPPER.readTree(response);
                if (resNode.path("success").asBoolean(false)) {
                    channelProductDAO.syncStock(cp.getId(), BigDecimal.valueOf(qtyAvailable));
                    return true;
                } else {
                    LOG.warning("WebsiteProductService: stock sync failed on storefront: " + resNode.path("message").asText());
                }
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "WebsiteProductService: stock sync failed for product ID " + productId, e);
        }
        return false;
    }

    /**
     * Pulls products from the Website storefront and maps/registers them in WMS.
     */
    public PullResult pullProducts(Channel channel) {
        // Clear/resolve old unresolved catalog exceptions before pulling
        skuMappingDAO.resolveCatalogExceptions(channel.getChannelId());

        int totalPulled = 0;
        int totalUpserted = 0;
        int totalUnmapped = 0;
        String lastError = null;
        boolean ok = false;

        try {
            LOG.info("WebsiteProductService: pulling products from storefront...");
            String response = client.get("/api/v1/products");
            if (response != null) {
                ok = true;
                JsonNode root = MAPPER.readTree(response);
                if (root.isArray()) {
                    totalPulled = root.size();
                    for (JsonNode item : root) {
                        String skuCode = item.path("skuCode").asText("").trim();
                        if (skuCode.isEmpty()) {
                            skuCode = item.path("sku_code").asText("").trim();
                        }
                        int webProductId = item.path("productId").asInt(0);
                        if (webProductId == 0) {
                            webProductId = item.path("product_id").asInt(0);
                        }
                        String productName = item.path("productName").asText("").trim();
                        if (productName.isEmpty()) {
                            productName = item.path("product_name").asText("").trim();
                        }
                        if (productName.isEmpty()) {
                            productName = "Sản phẩm Website (" + skuCode + ")";
                        }

                        if (skuCode.isEmpty() || webProductId == 0) {
                            continue;
                        }

                        // Try to find matching product in WMS by SKU
                        Product matched = productDAO.findBySkuCode(skuCode);
                        if (matched == null) {
                            // Check if mapping exists in sku_mappings table
                            SkuMapping mapping = skuMappingDAO
                                    .findMappingByChannelAndExternalSku(channel.getChannelId(), String.valueOf(webProductId));
                            if (mapping != null) {
                                matched = productDAO.findById(mapping.getSkuId());
                            }
                        }

                        if (matched == null) {
                            // No match — log mapping exception
                            skuMappingDAO.logMappingException(
                                    channel.getChannelId(), skuCode, String.valueOf(webProductId), productName);
                            totalUnmapped++;
                        } else {
                            // Match found — ensure sku_mappings record
                            com.wms.dao.SkuMappingDAO mappingDAO = skuMappingDAO;
                            SkuMapping existingMapping = mappingDAO
                                    .findMappingByChannelAndExternalSku(channel.getChannelId(), String.valueOf(webProductId));
                            if (existingMapping == null) {
                                SkuMapping newMapping = new SkuMapping();
                                newMapping.setSkuId(matched.getProductId());
                                newMapping.setChannelId(channel.getChannelId());
                                newMapping.setExternalSku(String.valueOf(webProductId));
                                newMapping.setSellerSku(skuCode);
                                newMapping.setSyncStatus("SYNCED");
                                newMapping.setLastSyncAt(LocalDateTime.now());
                                skuMappingDAO.insert(newMapping);
                            }

                            // Ensure channel_products record
                            double price = item.path("basePrice").asDouble(0.0);
                            if (price == 0.0) price = item.path("base_price").asDouble(0.0);
                            double stock = item.path("qtyAvailable").asDouble(0.0);
                            if (stock == 0.0) stock = item.path("qty_available").asDouble(0.0);

                            ChannelProduct existing = channelProductDAO.findByProductAndChannel(
                                    matched.getProductId(), channel.getChannelId());
                            if (existing == null) {
                                ChannelProduct cp = new ChannelProduct();
                                cp.setChannelId(channel.getChannelId());
                                cp.setProductId(matched.getProductId());
                                cp.setChannelSkuCode(skuCode);
                                cp.setChannelPrice(BigDecimal.valueOf(price));
                                cp.setChannelStock(BigDecimal.valueOf(stock));
                                cp.setStatus("ACTIVE");
                                cp.setListedAt(LocalDateTime.now());
                                boolean inserted = channelProductDAO.insert(cp);
                                if (inserted) {
                                    ChannelProduct fresh = channelProductDAO.findByProductAndChannel(
                                            matched.getProductId(), channel.getChannelId());
                                    if (fresh != null) {
                                        channelProductDAO.setChannelItemId(fresh.getId(), String.valueOf(webProductId), null);
                                    }
                                }
                                totalUpserted++;
                            } else {
                                existing.setChannelPrice(BigDecimal.valueOf(price));
                                existing.setChannelStock(BigDecimal.valueOf(stock));
                                channelProductDAO.update(existing);
                                channelProductDAO.setChannelItemId(existing.getId(), String.valueOf(webProductId), null);
                                totalUpserted++;
                            }
                        }
                    }
                }
            } else {
                lastError = "GET request to storefront returned empty/null response.";
            }
        } catch (Exception e) {
            lastError = e.getMessage();
            LOG.log(Level.SEVERE, "WebsiteProductService: pull failed", e);
        }

        return new PullResult(ok, totalPulled, totalUpserted, totalUnmapped, lastError);
    }

    private Map<String, Object> buildPayload(Product p, ChannelProduct cp, List<String> images) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("product_id", p.getProductId());
        payload.put("category_id", p.getCategoryId());
        payload.put("sku_code", p.getSkuCode());
        payload.put("product_name", p.getProductName());

        BigDecimal price = cp.getChannelPrice() != null && cp.getChannelPrice().signum() > 0 ? cp.getChannelPrice()
                : BigDecimal.valueOf(p.getBasePrice());
        payload.put("base_price", price);
        payload.put("attributes_text", p.getAttributesText());
        payload.put("weight_kg", p.getWeightKg());

        // Default to WMS actual inventory if stage channel_stock is empty/0
        int stock = cp.getChannelStock() != null && cp.getChannelStock().signum() > 0 ? cp.getChannelStock().intValue()
                : inventoryDAO.getTotalAvailableStock(p.getProductId());
        payload.put("qty_available", stock);

        payload.put("active", 1);
        payload.put("is_new_arrival", 0);
        payload.put("is_best_seller", cp.getStatus() != null && cp.getStatus().contains("BEST") ? 1 : 0);
        payload.put("description", cp.getDescription() != null ? cp.getDescription() : "");
        payload.put("images", images);

        return payload;
    }

    /**
     * Batch push products to Website storefront.
     * Maps to POST /api/v1/products/batch endpoint.
     * Reduces N HTTP calls to 1 call, improves performance.
     *
     * @param channel Website channel
     * @param productIds list of product IDs to push
     * @return success count
     */
    public int pushProductBatch(Channel channel, List<Integer> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return 0;
        }

        List<Map<String, Object>> products = new ArrayList<>();
        for (int productId : productIds) {
            Product p = productDAO.findById(productId);
            if (p == null) {
                LOG.warning("WebsiteProductService.pushProductBatch: product not found for ID " + productId);
                continue;
            }

            ChannelProduct cp = channelProductDAO.findByProductAndChannel(productId, channel.getChannelId());
            if (cp == null) {
                cp = new ChannelProduct();
                cp.setChannelId(channel.getChannelId());
                cp.setProductId(productId);
                cp.setChannelSkuCode(p.getSkuCode());
                cp.setChannelPrice(BigDecimal.valueOf(p.getBasePrice()));
                cp.setChannelStock(BigDecimal.ZERO);
                cp.setStatus("PENDING");
                channelProductDAO.insert(cp);
                cp = channelProductDAO.findByProductAndChannel(productId, channel.getChannelId());
            }

            List<ProductImage> images = productImageDAO.findByProductId(productId);
            List<String> imageUrls = new ArrayList<>();
            for (ProductImage img : images) {
                if (img.getImageUrl() != null && !img.getImageUrl().isBlank()) {
                    imageUrls.add(img.getImageUrl().trim());
                }
            }

            Map<String, Object> payload = buildPayload(p, cp, imageUrls);
            products.add(payload);
        }

        if (products.isEmpty()) {
            return 0;
        }

        try {
            Map<String, Object> batchPayload = new HashMap<>();
            batchPayload.put("products", products);
            String json = MAPPER.writeValueAsString(batchPayload);

            LOG.info("WebsiteProductService: pushing " + products.size() + " products to Website in batch...");
            String response = client.post("/api/v1/products/batch", json);

            if (response != null) {
                JsonNode resNode = MAPPER.readTree(response);
                if (resNode.path("success").asBoolean(false)) {
                    int successCount = resNode.path("success_count").asInt(0);
                    LOG.info("WebsiteProductService: batch push completed. Success: " + successCount + "/" + products.size());

                    // Record success for each product
                    for (int productId : productIds) {
                        ChannelProduct cp = channelProductDAO.findByProductAndChannel(productId, channel.getChannelId());
                        if (cp != null) {
                            channelProductDAO.recordPushSuccess(
                                    cp.getId(),
                                    String.valueOf(productId),
                                    null,
                                    cp.getChannelStock() != null ? cp.getChannelStock() : BigDecimal.ZERO
                            );
                        }
                    }

                    return successCount;
                } else {
                    LOG.warning("WebsiteProductService: batch push failed: " + resNode.path("message").asText());
                }
            } else {
                LOG.warning("WebsiteProductService: batch push returned null response");
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "WebsiteProductService: batch push failed", e);
        }
        return 0;
    }

    private int ensureChannelProductRow(ChannelProduct cp, Channel channel, Product p) {
        ChannelProduct fresh = channelProductDAO.findByProductAndChannel(p.getProductId(), channel.getChannelId());
        if (fresh != null) return fresh.getId();
        ChannelProduct draft = new ChannelProduct();
        draft.setChannelId(channel.getChannelId());
        draft.setProductId(p.getProductId());
        draft.setChannelSkuCode(p.getSkuCode());
        draft.setChannelPrice(cp.getChannelPrice());
        draft.setChannelStock(cp.getChannelStock());
        draft.setStatus("ACTIVE");
        draft.setListedAt(LocalDateTime.now());
        boolean ok = channelProductDAO.insert(draft);
        if (!ok) return -1;
        ChannelProduct just = channelProductDAO.findByProductAndChannel(p.getProductId(), channel.getChannelId());
        return just != null ? just.getId() : -1;
    }

    public int syncAllProductStock(Channel channel) {
        List<ChannelProduct> products = channelProductDAO.findByChannel(channel.getChannelId());
        int count = 0;
        double buffer = channel.getBufferStock();
        InventoryDAO invDao = new InventoryDAO();
        for (ChannelProduct cp : products) {
            if (!"ACTIVE".equalsIgnoreCase(cp.getStatus())) continue;
            int sellable = invDao.getTotalAvailableStock(cp.getProductId());
            int pushQty = Math.max(0, (int) Math.floor(sellable - buffer));
            boolean ok = syncStock(channel, cp.getProductId(), pushQty);
            if (ok) count++;
        }
        LOG.info("WebsiteProductService: synced stock for " + count + "/" + products.size() + " products on channel " + channel.getChannelName());
        return count;
    }
}
