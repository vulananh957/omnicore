package com.wms.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Product — Domain model representing a master SKU product.
 *
 * Manager-created SKUs are immediately active — there is no PENDING/APPROVED/REJECTED
 * workflow. Approval gating only exists for non-manager sales roles if/when added.
 */
public class Product {

    private int productId;
    private String skuCode;
    private String productName;
    private Integer categoryId;
    private String barcode;
    private String unit;
    private Double minStock;
    private Double maxStock;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String categoryName;
    private Integer createdBy;
    private String creatorName;
    private String dimensions;
    private String attributesText;
    private Double weightKg;
    private Double qtyOnHand = 0.0;
    private Double qtyAvailable = 0.0;
    private Double qtyHolding = 0.0;
    private Double qtyPending = 0.0;
    private Double basePrice = 0.0;
    private Double macPrice = 0.0;  // Moving Average Cost — recalculated on each inbound receipt
    // ROP: Reorder Point — auto-calculated nightly by RopScheduler.
    // SS = (D_max × L_max) − (D_avg × L_avg);  ROP = (D_avg × L_avg) + SS
    private Double dAvg = 0.0;        // Average daily demand (units/day)
    private Double dMax = 0.0;        // Maximum daily demand observed
    private Double lAvg = 0.0;        // Average lead time in days (PO → GRN)
    private Double lMax = 0.0;        // Maximum lead time in days observed
    private Double safetyStock = 0.0; // Safety Stock = (D_max×L_max) − (D_avg×L_avg)
    private Double ropCalculated = 0.0; // Reorder Point = (D_avg×L_avg) + Safety_Stock
    private List<LocationConfig> locationConfigs = new ArrayList<>();
    private String shortDescription;  // UC-B2C09: Lazada payload (max 255 chars)

    public static class LocationConfig {
        private String locationId;
        private String zoneId;

        public LocationConfig() {}

        public LocationConfig(String locationId, String zoneId) {
            this.locationId = locationId;
            this.zoneId = zoneId;
        }

        public String getLocationId() {
            return locationId;
        }

        public void setLocationId(String locationId) {
            this.locationId = locationId;
        }

        public String getZoneId() {
            return zoneId;
        }

        public void setZoneId(String zoneId) {
            this.zoneId = zoneId;
        }
    }


    // ── Constructors ──────────────────────────────────────────

    public Product() {
    }

    public Product(int productId, String skuCode, String productName, Integer categoryId) {
        this.productId = productId;
        this.skuCode = skuCode;
        this.productName = productName;
        this.categoryId = categoryId;
    }

    // ── Getters / Setters ─────────────────────────────────────

    public int getProductId() {
        return productId;
    }

    public void setProductId(int productId) {
        this.productId = productId;
    }

    public String getSkuCode() {
        return skuCode;
    }

    public void setSkuCode(String skuCode) {
        this.skuCode = skuCode;
    }

    @JsonProperty("sku")
    public String getSku() {
        return skuCode;
    }

    public String getSkuName() {
        return productName;
    }

    @JsonProperty("name")
    public String getName() {
        return productName;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public Integer getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Integer categoryId) {
        this.categoryId = categoryId;
    }

    public String getBarcode() {
        return barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public Double getMinStock() {
        return minStock;
    }

    public void setMinStock(Double minStock) {
        this.minStock = minStock;
    }

    public Double getMaxStock() {
        return maxStock;
    }

    public void setMaxStock(Double maxStock) {
        this.maxStock = maxStock;
    }

    @JsonIgnore
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    @JsonProperty("createdAt")
    public String getCreatedAtAsString() {
        if (createdAt == null) return "";
        return createdAt.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @JsonIgnore
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    @JsonProperty("lastUpdated")
    public String getUpdatedAtAsString() {
        if (updatedAt == null) return "";
        return updatedAt.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    @JsonProperty("updatedAt")
    public String getUpdatedAtAsString2() {
        return getUpdatedAtAsString();
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    @JsonIgnore
    public Integer getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Integer createdBy) {
        this.createdBy = createdBy;
    }

    public String getCreatorName() {
        return creatorName;
    }

    public void setCreatorName(String creatorName) {
        this.creatorName = creatorName;
    }

    @JsonProperty("attributesText")
    public String getAttributesText() {
        return attributesText;
    }

    public void setAttributesText(String attributesText) {
        this.attributesText = attributesText;
    }

    public Double getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(Double weightKg) {
        this.weightKg = weightKg;
    }

    public Double getQtyOnHand() {
        return qtyOnHand;
    }

    public void setQtyOnHand(Double qtyOnHand) {
        this.qtyOnHand = qtyOnHand;
    }

    @JsonProperty("qtyAvailable")
    public Double getQtyAvailable() {
        return qtyAvailable != null ? qtyAvailable : qtyOnHand;
    }

    public void setQtyAvailable(Double qtyAvailable) {
        this.qtyAvailable = qtyAvailable;
    }

    public List<LocationConfig> getLocationConfigs() {
        return locationConfigs;
    }

    public void setLocationConfigs(List<LocationConfig> locationConfigs) {
        this.locationConfigs = locationConfigs;
    }

    public String getShortDescription() {
        return shortDescription;
    }

    public void setShortDescription(String shortDescription) {
        this.shortDescription = shortDescription;
    }

    // Helper Getters for Jackson to match JSP property expectations
    @JsonProperty("id")
    public String getIdAsString() {
        return "p-" + productId;
    }

    @JsonProperty("category")
    public String getCategory() {
        return categoryName != null ? categoryName : "";
    }

    @JsonProperty("dimensions")
    public String getDimensions() {
        if (dimensions != null && !dimensions.isBlank()) {
            return dimensions;
        }
        return attributesText != null ? attributesText : "N/A";
    }

    public void setDimensions(String dimensions) {
        this.dimensions = dimensions;
    }



    @JsonProperty("weight")
    public String getWeight() {
        return weightKg != null ? weightKg + " kg" : "N/A";
    }

    public Double getQtyHolding() {
        return qtyHolding;
    }

    public void setQtyHolding(Double qtyHolding) {
        this.qtyHolding = qtyHolding != null ? qtyHolding : 0.0;
    }

    public Double getQtyPending() {
        return qtyPending;
    }

    public void setQtyPending(Double qtyPending) {
        this.qtyPending = qtyPending != null ? qtyPending : 0.0;
    }

    public Double getBasePrice() {
        return basePrice;
    }

    public void setBasePrice(Double basePrice) {
        this.basePrice = basePrice != null ? basePrice : 0.0;
    }

    // Moving Average Cost: (current_on_hand × current_mac + accepted_qty × unit_cost) / (current_on_hand + accepted_qty)
    public Double getMacPrice() {
        return macPrice;
    }

    public void setMacPrice(Double macPrice) {
        this.macPrice = macPrice != null ? macPrice : 0.0;
    }

    // ROP metrics — auto-calculated by RopScheduler
    public Double getDAvg() { return dAvg; }
    public void setDAvg(Double dAvg) { this.dAvg = dAvg != null ? dAvg : 0.0; }
    public Double getDMax() { return dMax; }
    public void setDMax(Double dMax) { this.dMax = dMax != null ? dMax : 0.0; }
    public Double getLAvg() { return lAvg; }
    public void setLAvg(Double lAvg) { this.lAvg = lAvg != null ? lAvg : 0.0; }
    public Double getLMax() { return lMax; }
    public void setLMax(Double lMax) { this.lMax = lMax != null ? lMax : 0.0; }
    public Double getSafetyStock() { return safetyStock; }
    public void setSafetyStock(Double safetyStock) { this.safetyStock = safetyStock != null ? safetyStock : 0.0; }
    public Double getRopCalculated() { return ropCalculated; }
    public void setRopCalculated(Double ropCalculated) { this.ropCalculated = ropCalculated != null ? ropCalculated : 0.0; }

    // Manager-created SKUs are always approved; kept for backward compat with
    // views that filter on approvalStatus to build "active" SKU lists.
    @JsonProperty("approvalStatus")
    public String getApprovalStatus() {
        return "approved";
    }

    @JsonProperty("createdBy")
    public String getCreatedByName() {
        return creatorName != null ? creatorName : (createdBy != null ? String.valueOf(createdBy) : "");
    }

    @JsonProperty("updatedBy")
    public String getUpdatedBy() {
        return creatorName != null ? creatorName : "";
    }


    @Override
    public String toString() {
        return "Product{" +
                "productId=" + productId +
                ", skuCode='" + skuCode + '\'' +
                ", productName='" + productName + '\'' +
                '}';
    }
}
