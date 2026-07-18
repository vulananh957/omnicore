package com.wms.model;

import java.math.BigDecimal;

/**
 * ReturnItem — Domain model / JavaBean representing an item in a return order.
 */
public class ReturnItem {

    private int returnItemId;
    private int returnId;
    private int productId;
    private String skuCode; // Joined from products
    private String skuName; // Joined from products
    private BigDecimal qty;
    private String returnReason;
    private BigDecimal unitPrice;
    
    // QC details joined from qc_records
    private String qcDecision = "pending"; // pending, resalable, defective
    private String qcNote = "";

    public ReturnItem() {}

    // Getters and Setters
    public int getReturnItemId() {
        return returnItemId;
    }

    public void setReturnItemId(int returnItemId) {
        this.returnItemId = returnItemId;
    }

    public int getReturnId() {
        return returnId;
    }

    public void setReturnId(int returnId) {
        this.returnId = returnId;
    }

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

    public String getSkuName() {
        return skuName;
    }

    public void setSkuName(String skuName) {
        this.skuName = skuName;
    }

    public BigDecimal getQty() {
        return qty;
    }

    public void setQty(BigDecimal qty) {
        this.qty = qty;
    }

    public String getReturnReason() {
        return returnReason;
    }

    public void setReturnReason(String returnReason) {
        this.returnReason = returnReason;
    }

    public String getQcDecision() {
        return qcDecision;
    }

    public void setQcDecision(String qcDecision) {
        this.qcDecision = qcDecision;
    }

    public String getQcNote() {
        return qcNote;
    }

    public void setQcNote(String qcNote) {
        this.qcNote = qcNote;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }
}
