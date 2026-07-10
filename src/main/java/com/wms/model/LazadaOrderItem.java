package com.wms.model;

import java.math.BigDecimal;

/**
 * LazadaOrderItem — Domain model for the lazada_order_items table.
 *
 * <p>Represents a single line item within a Lazada order. Stores both the
 * Lazada-native fields (sku, shopSku, orderItemId) and the WMS mapping
 * (productId, reservedQty, fulfilledQty).</p>
 *
 * <p>The item belongs to exactly one LazadaOrder via lazadaOrderIdStr.</p>
 */
public class LazadaOrderItem {

    private int lazadaOrderItemId;
    /** FK to lazada_orders.lazada_order_id_str */
    private String lazadaOrderIdStr;
    /** Lazada's order_item_id string. */
    private String orderItemId;
    /** SKU code on Lazada's platform (may differ from WMS sku_code). */
    private String sku;
    /** Lazada shop_sku — used for SKU mapping lookups. */
    private String shopSku;
    /** Product name as listed on Lazada. */
    private String productName;
    /** URL or path to the product image on Lazada CDN. */
    private String productImage;
    /** Quantity ordered by the buyer. */
    private int quantity;
    /** Price actually paid per unit (after discounts). */
    private BigDecimal paidPrice;
    /** Lazada listing price before discounts. */
    private BigDecimal itemPrice;
    /** WMS supply/cost price (optional, populated via SKU mapping). */
    private BigDecimal supplyPrice;
    /** Lazada item-level status. */
    private String status;
    /** Mapped WMS product_id (0 if not mapped yet). */
    private int productId;
    /** Quantity soft-allocated (from holding) during WMS approval. */
    private int reservedQty;
    /** Physical quantity actually shipped after RTS. */
    private int fulfilledQty;

    // ── Constructors ──────────────────────────────────────────

    public LazadaOrderItem() {
    }

    public LazadaOrderItem(String lazadaOrderIdStr, String orderItemId, String sku) {
        this.lazadaOrderIdStr = lazadaOrderIdStr;
        this.orderItemId = orderItemId;
        this.sku = sku;
        this.quantity = 1;
        this.reservedQty = 0;
        this.fulfilledQty = 0;
    }

    // ── Getters / Setters ─────────────────────────────────────

    public int getLazadaOrderItemId() {
        return lazadaOrderItemId;
    }

    public void setLazadaOrderItemId(int lazadaOrderItemId) {
        this.lazadaOrderItemId = lazadaOrderItemId;
    }

    public String getLazadaOrderIdStr() {
        return lazadaOrderIdStr;
    }

    public void setLazadaOrderIdStr(String lazadaOrderIdStr) {
        this.lazadaOrderIdStr = lazadaOrderIdStr;
    }

    public String getOrderItemId() {
        return orderItemId;
    }

    public void setOrderItemId(String orderItemId) {
        this.orderItemId = orderItemId;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getShopSku() {
        return shopSku;
    }

    public void setShopSku(String shopSku) {
        this.shopSku = shopSku;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getProductImage() {
        return productImage;
    }

    public void setProductImage(String productImage) {
        this.productImage = productImage;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getPaidPrice() {
        return paidPrice;
    }

    public void setPaidPrice(BigDecimal paidPrice) {
        this.paidPrice = paidPrice;
    }

    public BigDecimal getItemPrice() {
        return itemPrice;
    }

    public void setItemPrice(BigDecimal itemPrice) {
        this.itemPrice = itemPrice;
    }

    public BigDecimal getSupplyPrice() {
        return supplyPrice;
    }

    public void setSupplyPrice(BigDecimal supplyPrice) {
        this.supplyPrice = supplyPrice;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getProductId() {
        return productId;
    }

    public void setProductId(int productId) {
        this.productId = productId;
    }

    public int getReservedQty() {
        return reservedQty;
    }

    public void setReservedQty(int reservedQty) {
        this.reservedQty = reservedQty;
    }

    public int getFulfilledQty() {
        return fulfilledQty;
    }

    public void setFulfilledQty(int fulfilledQty) {
        this.fulfilledQty = fulfilledQty;
    }

    @Override
    public String toString() {
        return "LazadaOrderItem{" +
                "orderItemId='" + orderItemId + '\'' +
                ", sku='" + sku + '\'' +
                ", productName='" + productName + '\'' +
                ", quantity=" + quantity +
                ", productId=" + productId +
                '}';
    }
}
