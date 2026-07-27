package com.wms.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ReceiptNote — Domain model representing a receipt line item for an inbound order.
 * Tracks expected vs. actual received quantities for each SKU in a goods-receipt note.
 */
public class ReceiptNote {

    private int receiptId;
    private int inboundId;
    private int productId;
    private String skuCode;
    private String productName;
    private BigDecimal expectedQty;
    private BigDecimal receivedQty;
    private BigDecimal acceptedQty;
    private BigDecimal rejectedQty;
    private BigDecimal unitCost;
    private String note;
    private String rejectReason;  // Lý do trả hàng NCC: Hàng móp méo, Sai màu/size, Hết hạn, Hỏng vận chuyển
    private LocalDateTime receivedAt;

    // ── Constructors ──────────────────────────────────────────

    public ReceiptNote() {
    }

    public ReceiptNote(int receiptId, int inboundId, int productId, BigDecimal expectedQty) {
        this.receiptId = receiptId;
        this.inboundId = inboundId;
        this.productId = productId;
        this.expectedQty = expectedQty;
    }

    // ── Getters / Setters ───────────────────────────────────

    public int getReceiptId() {
        return receiptId;
    }

    public void setReceiptId(int receiptId) {
        this.receiptId = receiptId;
    }

    public int getInboundId() {
        return inboundId;
    }

    public void setInboundId(int inboundId) {
        this.inboundId = inboundId;
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

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public BigDecimal getExpectedQty() {
        return expectedQty;
    }

    public void setExpectedQty(BigDecimal expectedQty) {
        this.expectedQty = expectedQty;
    }

    public BigDecimal getReceivedQty() {
        return receivedQty;
    }

    public void setReceivedQty(BigDecimal receivedQty) {
        this.receivedQty = receivedQty;
    }

    public BigDecimal getAcceptedQty() {
        return acceptedQty;
    }

    public void setAcceptedQty(BigDecimal acceptedQty) {
        this.acceptedQty = acceptedQty;
    }

    public BigDecimal getRejectedQty() {
        return rejectedQty;
    }

    public void setRejectedQty(BigDecimal rejectedQty) {
        this.rejectedQty = rejectedQty;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public void setUnitCost(BigDecimal unitCost) {
        this.unitCost = unitCost;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public void setRejectReason(String rejectReason) {
        this.rejectReason = rejectReason;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(LocalDateTime receivedAt) {
        this.receivedAt = receivedAt;
    }

    @Override
    public String toString() {
        return "ReceiptNote{" +
                "receiptId=" + receiptId +
                ", inboundId=" + inboundId +
                ", productId=" + productId +
                ", expectedQty=" + expectedQty +
                ", receivedQty=" + receivedQty +
                '}';
    }
}
