package com.wms.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

/**
 * ProductPerformance — Domain model for the Product Performance Dashboard.
 * Represents aggregated financial and inventory metrics for a single SKU.
 *
 * <p>This is a read-only analytics model computed by joining Products,
 * Inventory (for MAC), and Order_Items (for sales metrics) over a time period.</p>
 */
public class ProductPerformance {

    // ── Core Product Info ──────────────────────────────────────
    private int productId;
    private String skuCode;
    private String productName;
    private String categoryName;
    private String thumbnailUrl;

    // ── Inventory Metrics ───────────────────────────────────────
    private BigDecimal qtyOnHand;
    private BigDecimal qtyAvailable;
    private BigDecimal macPrice;           // Moving Average Cost (Giá vốn)

    // ── Sales Metrics (per selected time period) ────────────────
    private int totalSold;                  // Lượt bán thành công
    private BigDecimal totalRevenue;        // Tổng doanh thu thực tế
    private BigDecimal minSellingPrice;     // Giá bán thấp nhất
    private BigDecimal maxSellingPrice;     // Giá bán cao nhất

    // ── Financial Metrics (computed) ────────────────────────────
    private BigDecimal grossProfit;         // Biên lợi nhuận = Revenue - (Sold × MAC)
    private double grossMarginPercent;      // Biên lợi nhuận %
    private BigDecimal tiedUpCapital;       // Đọng vốn = qtyAvailable × MAC

    // ── Inventory Thresholds (for health status) ─────────────────────
    private BigDecimal minStock;   // from products.min_stock
    private BigDecimal maxStock;   // from products.max_stock

    // ── Health Indicators ───────────────────────────────────────
    private String healthStatus;            // NORMAL, LOW_STOCK, OVERSTOCKED, DEAD_STOCK
    private int daysWithoutMovement;        // Số ngày không phát sinh phiếu xuất

    // ── Channel Mapping (for external links) ────────────────────
    private List<ChannelLink> channelLinks;

    // ── Nested class for channel links ──────────────────────────
    public static class ChannelLink {
        private String channelName;
        private String channelPlatform;
        private String externalSku;
        private String lazadaProductId;     // For building Lazada URL

        public ChannelLink() {}

        public ChannelLink(String channelName, String channelPlatform, String externalSku, String lazadaProductId) {
            this.channelName = channelName;
            this.channelPlatform = channelPlatform;
            this.externalSku = externalSku;
            this.lazadaProductId = lazadaProductId;
        }

        public String getChannelName() { return channelName; }
        public void setChannelName(String channelName) { this.channelName = channelName; }

        public String getChannelPlatform() { return channelPlatform; }
        public void setChannelPlatform(String channelPlatform) { this.channelPlatform = channelPlatform; }

        public String getExternalSku() { return externalSku; }
        public void setExternalSku(String externalSku) { this.externalSku = externalSku; }

        public String getLazadaProductId() { return lazadaProductId; }
        public void setLazadaProductId(String lazadaProductId) { this.lazadaProductId = lazadaProductId; }

        /**
         * Build Lazada product URL from Lazada_Product_ID.
         * Format: https://www.lazada.vn/products/i{productId}.html
         */
        public String getExternalUrl() {
            if ("Lazada".equalsIgnoreCase(channelPlatform) && lazadaProductId != null && !lazadaProductId.isEmpty()) {
                return "https://www.lazada.vn/products/i" + lazadaProductId + ".html";
            }
            // Can extend for other platforms here
            return null;
        }
    }

    // ── Constructors ───────────────────────────────────────────

    public ProductPerformance() {
        this.qtyOnHand = BigDecimal.ZERO;
        this.qtyAvailable = BigDecimal.ZERO;
        this.macPrice = BigDecimal.ZERO;
        this.totalSold = 0;
        this.totalRevenue = BigDecimal.ZERO;
        this.minSellingPrice = BigDecimal.ZERO;
        this.maxSellingPrice = BigDecimal.ZERO;
        this.grossProfit = BigDecimal.ZERO;
        this.grossMarginPercent = 0.0;
        this.tiedUpCapital = BigDecimal.ZERO;
        this.healthStatus = "NORMAL";
        this.daysWithoutMovement = 0;
    }

    // ── Computed Setters (called by DAO/Service) ────────────────

    /**
     * Compute grossProfit = totalRevenue - (totalSold × macPrice)
     * and grossMarginPercent = (grossProfit / totalRevenue) × 100
     */
    public void computeGrossProfit() {
        if (macPrice != null && totalSold > 0) {
            BigDecimal costOfGoodsSold = macPrice.multiply(BigDecimal.valueOf(totalSold));
            this.grossProfit = totalRevenue.subtract(costOfGoodsSold);
            if (totalRevenue.compareTo(BigDecimal.ZERO) > 0) {
                this.grossMarginPercent = grossProfit.doubleValue() / totalRevenue.doubleValue() * 100.0;
            }
        } else {
            this.grossProfit = BigDecimal.ZERO;
            this.grossMarginPercent = 0.0;
        }
    }

    /**
     * Compute tiedUpCapital = qtyAvailable × macPrice
     */
    public void computeTiedUpCapital() {
        if (qtyAvailable != null && macPrice != null) {
            this.tiedUpCapital = qtyAvailable.multiply(macPrice);
        } else {
            this.tiedUpCapital = BigDecimal.ZERO;
        }
    }

    /**
     * Determine health status based on qty_on_hand vs product min/max thresholds.
     * <ul>
     *   <li>qty_on_hand &lt; min_stock  → LOW_STOCK (Sắp hết)</li>
     *   <li>qty_on_hand &gt; max_stock  → OVERSTOCKED (Dư stock)</li>
     *   <li>daysWithoutMovement &gt; 90 → DEAD_STOCK (Tồn đọng)</li>
     *   <li>otherwise                  → NORMAL (Bình thường)</li>
     * </ul>
     *
     * <p>NOTE: qty_on_hand from the DAO already joins inventory across ALL warehouses
     * (SUM per product), so it represents total system stock.</p>
     */
    public void computeHealthStatus(BigDecimal minStock, BigDecimal maxStock) {
        // DEAD_STOCK: no outbound movement for more than 90 days
        if (daysWithoutMovement > 90) {
            this.healthStatus = "DEAD_STOCK";
            return;
        }

        // Resolve defaults
        BigDecimal effectiveMin = (minStock != null && minStock.compareTo(BigDecimal.ZERO) > 0) ? minStock : BigDecimal.ZERO;
        BigDecimal effectiveMax = (maxStock != null && maxStock.compareTo(BigDecimal.ZERO) > 0) ? maxStock : null;
        BigDecimal onHand = (qtyOnHand != null) ? qtyOnHand : BigDecimal.ZERO;

        if (effectiveMax != null && effectiveMax.compareTo(BigDecimal.ZERO) > 0) {
            // Stock below minimum
            if (onHand.compareTo(effectiveMin) < 0) {
                this.healthStatus = "LOW_STOCK";
            // Stock above maximum
            } else if (onHand.compareTo(effectiveMax) > 0) {
                this.healthStatus = "OVERSTOCKED";
            } else {
                this.healthStatus = "NORMAL";
            }
        } else {
            // No max configured — only check minimum
            if (onHand.compareTo(effectiveMin) < 0) {
                this.healthStatus = "LOW_STOCK";
            } else {
                this.healthStatus = "NORMAL";
            }
        }
    }

    // ── Getters / Setters ───────────────────────────────────────

    public int getProductId() { return productId; }
    public void setProductId(int productId) { this.productId = productId; }

    public String getSkuCode() { return skuCode; }
    public void setSkuCode(String skuCode) { this.skuCode = skuCode; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public String getThumbnailUrl() { return thumbnailUrl; }
    public void setThumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; }

    public BigDecimal getQtyOnHand() { return qtyOnHand; }
    public void setQtyOnHand(BigDecimal qtyOnHand) { this.qtyOnHand = qtyOnHand != null ? qtyOnHand : BigDecimal.ZERO; }

    public BigDecimal getQtyAvailable() { return qtyAvailable; }
    public void setQtyAvailable(BigDecimal qtyAvailable) { this.qtyAvailable = qtyAvailable != null ? qtyAvailable : BigDecimal.ZERO; }

    public BigDecimal getMacPrice() { return macPrice; }
    public void setMacPrice(BigDecimal macPrice) { this.macPrice = macPrice != null ? macPrice : BigDecimal.ZERO; }

    public BigDecimal getMinStock() { return minStock; }
    public void setMinStock(BigDecimal minStock) { this.minStock = minStock; }

    public BigDecimal getMaxStock() { return maxStock; }
    public void setMaxStock(BigDecimal maxStock) { this.maxStock = maxStock; }

    public int getTotalSold() { return totalSold; }
    public void setTotalSold(int totalSold) { this.totalSold = totalSold; }

    public BigDecimal getTotalRevenue() { return totalRevenue; }
    public void setTotalRevenue(BigDecimal totalRevenue) { this.totalRevenue = totalRevenue != null ? totalRevenue : BigDecimal.ZERO; }

    public BigDecimal getMinSellingPrice() { return minSellingPrice; }
    public void setMinSellingPrice(BigDecimal minSellingPrice) { this.minSellingPrice = minSellingPrice != null ? minSellingPrice : BigDecimal.ZERO; }

    public BigDecimal getMaxSellingPrice() { return maxSellingPrice; }
    public void setMaxSellingPrice(BigDecimal maxSellingPrice) { this.maxSellingPrice = maxSellingPrice != null ? maxSellingPrice : BigDecimal.ZERO; }

    public BigDecimal getGrossProfit() { return grossProfit; }
    public void setGrossProfit(BigDecimal grossProfit) { this.grossProfit = grossProfit != null ? grossProfit : BigDecimal.ZERO; }

    public double getGrossMarginPercent() { return grossMarginPercent; }
    public void setGrossMarginPercent(double grossMarginPercent) { this.grossMarginPercent = grossMarginPercent; }

    public BigDecimal getTiedUpCapital() { return tiedUpCapital; }
    public void setTiedUpCapital(BigDecimal tiedUpCapital) { this.tiedUpCapital = tiedUpCapital != null ? tiedUpCapital : BigDecimal.ZERO; }

    public String getHealthStatus() { return healthStatus; }
    public void setHealthStatus(String healthStatus) { this.healthStatus = healthStatus; }

    public int getDaysWithoutMovement() { return daysWithoutMovement; }
    public void setDaysWithoutMovement(int daysWithoutMovement) { this.daysWithoutMovement = daysWithoutMovement; }

    public List<ChannelLink> getChannelLinks() { return channelLinks; }
    public void setChannelLinks(List<ChannelLink> channelLinks) { this.channelLinks = channelLinks; }

    // ── JSON-friendly accessors ─────────────────────────────────

    @JsonProperty("isLowMargin")
    public boolean isLowMargin() {
        return grossMarginPercent > 0 && grossMarginPercent < 10;
    }

    @JsonProperty("isLossMaking")
    public boolean isLossMaking() {
        return grossProfit != null && grossProfit.compareTo(BigDecimal.ZERO) < 0;
    }

    @JsonProperty("hasExternalLinks")
    public boolean hasExternalLinks() {
        return channelLinks != null && !channelLinks.isEmpty();
    }

    @Override
    public String toString() {
        return "ProductPerformance{" +
                "productId=" + productId +
                ", skuCode='" + skuCode + '\'' +
                ", productName='" + productName + '\'' +
                ", totalSold=" + totalSold +
                ", grossProfit=" + grossProfit +
                ", grossMarginPercent=" + grossMarginPercent +
                '}';
    }
}
