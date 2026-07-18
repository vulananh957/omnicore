package com.wms.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * OrderItem — Represents an item in an order, mapped from the order_items and skus tables.
 */
public class OrderItem {

    @JsonProperty("id")
    private int productId;
    private String skuCode;
    private String productName;
    private int quantity;
    private double unitPrice;
    private String warehouseStocks;
    private int qtyAvailable;

    // Constructors
    public OrderItem() {}

    public OrderItem(String skuCode, String productName, int quantity, double unitPrice) {
        this.skuCode = skuCode;
        this.productName = productName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    // Getters and Setters
    public int getProductId() { return productId; }
    public void setProductId(int productId) { this.productId = productId; }

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

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public double getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(double unitPrice) {
        this.unitPrice = unitPrice;
    }

    public String getWarehouseStocks() {
        return warehouseStocks;
    }

    public void setWarehouseStocks(String warehouseStocks) {
        this.warehouseStocks = warehouseStocks;
    }

    public int getQtyAvailable() {
        return qtyAvailable;
    }

    public void setQtyAvailable(int qtyAvailable) {
        this.qtyAvailable = qtyAvailable;
    }
}
