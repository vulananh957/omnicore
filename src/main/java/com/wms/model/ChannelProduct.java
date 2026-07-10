package com.wms.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ChannelProduct — Domain model for channel-specific product listings.
 * Represents a product published on a marketplace channel (Lazada/Shopee/TikTok).
 */
public class ChannelProduct {

    private int id;
    private int channelId;
    private int productId;
    private String channelSkuCode;
    private BigDecimal channelPrice;
    private BigDecimal channelStock;
    private String status;
    private LocalDateTime listedAt;
    private LocalDateTime updatedAt;

    // UC-B2C09: Lazada push tracking fields (6 cols added in SchemaInitListener)
    private String channelItemId;       // Lazada item_id returned by /product/create
    private String lazadaSkuId;         // Lazada sku_id returned by /product/create
    private BigDecimal lastPushQty;     // Stock qty at last successful push
    private LocalDateTime lastPushAt;   // Timestamp of last successful push
    private String lastErrorCode;       // Last push error code from Lazada
    private String lastErrorMessage;    // Last push error message (translated to VI)

    // Lazada payload overrides (ChannelProduct is the staging table for the Lazada draft)
    private String sellerSku;           // SKU code sent to Lazada (defaults to skuCode)
    private String shortDescription;    // Lazada short_description (<=255 chars)
    private BigDecimal specialPrice;    // Lazada special_price (must be < price)
    private Double weightKg;            // Lazada package_weight (kg, <=40)
    private String dimensions;          // Lazada "LxWxH" cm (sum <=300)
    private Long lazadaCategoryId;      // Lazada leaf category (mirrored from /category/tree/get)
    private Long brandId;               // Lazada brand_id (from /brand/get — mandatory for create/update)
    private String brand;               // Lazada brand text name override
    private String description;         // Lazada description override

    // Enriched fields (populated by DAO joins)
    private String channelName;
    private String channelPlatform;
    private String skuCode;
    private String productName;
    private String categoryName;

    public ChannelProduct() {}

    public ChannelProduct(int id, int channelId, int productId, String channelSkuCode,
                         BigDecimal channelPrice, BigDecimal channelStock, String status,
                         LocalDateTime listedAt, LocalDateTime updatedAt) {
        this.id = id;
        this.channelId = channelId;
        this.productId = productId;
        this.channelSkuCode = channelSkuCode;
        this.channelPrice = channelPrice;
        this.channelStock = channelStock;
        this.status = status;
        this.listedAt = listedAt;
        this.updatedAt = updatedAt;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getChannelId() {
        return channelId;
    }

    public void setChannelId(int channelId) {
        this.channelId = channelId;
    }

    public int getProductId() {
        return productId;
    }

    public void setProductId(int productId) {
        this.productId = productId;
    }

    public String getChannelSkuCode() {
        return channelSkuCode;
    }

    public void setChannelSkuCode(String channelSkuCode) {
        this.channelSkuCode = channelSkuCode;
    }

    public BigDecimal getChannelPrice() {
        return channelPrice;
    }

    public void setChannelPrice(BigDecimal channelPrice) {
        this.channelPrice = channelPrice;
    }

    public BigDecimal getChannelStock() {
        return channelStock;
    }

    public void setChannelStock(BigDecimal channelStock) {
        this.channelStock = channelStock;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getListedAt() {
        return listedAt;
    }

    public void setListedAt(LocalDateTime listedAt) {
        this.listedAt = listedAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getChannelName() {
        return channelName;
    }

    public void setChannelName(String channelName) {
        this.channelName = channelName;
    }

    public String getChannelPlatform() {
        return channelPlatform;
    }

    public void setChannelPlatform(String channelPlatform) {
        this.channelPlatform = channelPlatform;
    }

    public String getSkuCode() {
        return skuCode;
    }

    public void setSkuCode(String skuCode) {
        this.skuCode = skuCode;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    // ── Lazada push tracking getters/setters ──

    public String getChannelItemId() {
        return channelItemId;
    }

    public void setChannelItemId(String channelItemId) {
        this.channelItemId = channelItemId;
    }

    public String getLazadaSkuId() {
        return lazadaSkuId;
    }

    public void setLazadaSkuId(String lazadaSkuId) {
        this.lazadaSkuId = lazadaSkuId;
    }

    public BigDecimal getLastPushQty() {
        return lastPushQty;
    }

    public void setLastPushQty(BigDecimal lastPushQty) {
        this.lastPushQty = lastPushQty;
    }

    public LocalDateTime getLastPushAt() {
        return lastPushAt;
    }

    public void setLastPushAt(LocalDateTime lastPushAt) {
        this.lastPushAt = lastPushAt;
    }

    public String getLastErrorCode() {
        return lastErrorCode;
    }

    public void setLastErrorCode(String lastErrorCode) {
        this.lastErrorCode = lastErrorCode;
    }

    public String getLastErrorMessage() {
        return lastErrorMessage;
    }

    public void setLastErrorMessage(String lastErrorMessage) {
        this.lastErrorMessage = lastErrorMessage;
    }

    // ── Lazada payload overrides getters/setters ──

    public String getSellerSku() {
        return sellerSku;
    }

    public void setSellerSku(String sellerSku) {
        this.sellerSku = sellerSku;
    }

    public String getShortDescription() {
        return shortDescription;
    }

    public void setShortDescription(String shortDescription) {
        this.shortDescription = shortDescription;
    }

    public BigDecimal getSpecialPrice() {
        return specialPrice;
    }

    public void setSpecialPrice(BigDecimal specialPrice) {
        this.specialPrice = specialPrice;
    }

    public Double getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(Double weightKg) {
        this.weightKg = weightKg;
    }

    public String getDimensions() {
        return dimensions;
    }

    public void setDimensions(String dimensions) {
        this.dimensions = dimensions;
    }

    public Long getLazadaCategoryId() {
        return lazadaCategoryId;
    }

    public void setLazadaCategoryId(Long lazadaCategoryId) {
        this.lazadaCategoryId = lazadaCategoryId;
    }

    public Long getBrandId() {
        return brandId;
    }

    public void setBrandId(Long brandId) {
        this.brandId = brandId;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
