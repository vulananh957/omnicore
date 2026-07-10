package com.wms.model;

import java.time.LocalDateTime;

/**
 * SkuMapping — Domain model for SKU-to-channel mapping records.
 * Maps internal product SKUs to external marketplace channel SKUs.
 */
public class SkuMapping {

    private int mappingId;
    private int skuId;
    private int channelId;
    private String externalSku;
    private String sellerSku;
    private String syncStatus;
    private LocalDateTime lastSyncAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Enriched fields (populated by DAO joins)
    private String channelName;
    private String channelPlatform;
    private String skuCode;
    private String productName;
    private String channelCategory;

    public SkuMapping() {}

    public SkuMapping(int mappingId, int skuId, int channelId, String externalSku,
                      String sellerSku, String syncStatus, LocalDateTime lastSyncAt,
                      LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.mappingId = mappingId;
        this.skuId = skuId;
        this.channelId = channelId;
        this.externalSku = externalSku;
        this.sellerSku = sellerSku;
        this.syncStatus = syncStatus;
        this.lastSyncAt = lastSyncAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public int getMappingId() {
        return mappingId;
    }

    public void setMappingId(int mappingId) {
        this.mappingId = mappingId;
    }

    public int getSkuId() {
        return skuId;
    }

    public void setSkuId(int skuId) {
        this.skuId = skuId;
    }

    public int getChannelId() {
        return channelId;
    }

    public void setChannelId(int channelId) {
        this.channelId = channelId;
    }

    public String getExternalSku() {
        return externalSku;
    }

    public void setExternalSku(String externalSku) {
        this.externalSku = externalSku;
    }

    public String getSellerSku() {
        return sellerSku;
    }

    public void setSellerSku(String sellerSku) {
        this.sellerSku = sellerSku;
    }

    public String getSyncStatus() {
        return syncStatus;
    }

    public void setSyncStatus(String syncStatus) {
        this.syncStatus = syncStatus;
    }

    public LocalDateTime getLastSyncAt() {
        return lastSyncAt;
    }

    public void setLastSyncAt(LocalDateTime lastSyncAt) {
        this.lastSyncAt = lastSyncAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
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

    // ── Enriched Lazada fields (populated by findActiveMappingsByProductIds) ──

    private String lazadaSkuId;
    private String channelItemId;

    public String getLazadaSkuId() {
        return lazadaSkuId;
    }

    public void setLazadaSkuId(String lazadaSkuId) {
        this.lazadaSkuId = lazadaSkuId;
    }

    public String getChannelItemId() {
        return channelItemId;
    }

    public void setChannelItemId(String channelItemId) {
        this.channelItemId = channelItemId;
    }

    public String getChannelCategory() {
        return channelCategory;
    }

    public void setChannelCategory(String channelCategory) {
        this.channelCategory = channelCategory;
    }
}
