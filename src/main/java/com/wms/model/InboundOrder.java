package com.wms.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * InboundOrder — Domain model representing an inbound purchase order / goods receipt note.
 * Status workflow: PENDING → CONFIRMED → RECEIVED / CANCELLED
 */
public class InboundOrder {

    public static final String STATUS_PENDING    = "PENDING";
    public static final String STATUS_PURCHASED  = "PURCHASED";
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_CONFIRMED  = "CONFIRMED";
    public static final String STATUS_RECEIVED   = "RECEIVED";
    public static final String STATUS_CANCELLED  = "CANCELLED";

    /**
     * Workflow: PENDING → PURCHASED → IN_PROGRESS → RECEIVED.
     * - PENDING: phiếu mua hàng vừa tạo (chưa đặt mua).
     * - PURCHASED: đã mua hàng từ NCC, sẵn sàng để nhập kho.
     * - IN_PROGRESS: đang trong quá trình nhập kho (chưa hoàn tất).
     * - RECEIVED: đã nhập kho xong.
     * - CANCELLED: huỷ phiếu.
     */

    private int inboundId;
    private String inboundCode;
    private String supplierName;
    private Integer supplierId;       // FK -> suppliers.supplier_id (NULL nếu PO cũ chưa liên kết)
    private String supplierCode;      // NCC-YYMM-NNN từ suppliers
    private String supplierContact;   // Người liên hệ từ suppliers
    private String supplierPhone;     // SĐT từ suppliers
    private String supplierAddress;   // Địa chỉ từ suppliers
    private String supplierEmail;     // Email từ suppliers
    private String paymentTerms;      // Kỳ hạn thanh toán
    private int warehouseId;
    private String warehouseName;
    private Integer zoneId;           // Khu vực nhận hàng trong kho (zones.zone_id)
    private String zoneName;          // Tên zone
    private String status;
    private LocalDate expectedDate;
    private LocalDate receivedDate;
    private String deliveryPerson;    // Tên người giao hàng / tài xế
    private String deliveryPhone;     // SĐT người giao hàng
    private int createdBy;
    private String notes;
    private LocalDateTime createdAt;
    private List<ReceiptNote> items = new ArrayList<>();

    // ── Constructors ──────────────────────────────────────────

    public InboundOrder() {
    }

    public InboundOrder(int inboundId, String inboundCode, String supplierName,
                       int warehouseId, String status) {
        this.inboundId = inboundId;
        this.inboundCode = inboundCode;
        this.supplierName = supplierName;
        this.warehouseId = warehouseId;
        this.status = status;
    }

    // ── Items management ────────────────────────────────────

    public List<ReceiptNote> getItems() {
        return items;
    }

    public void setItems(List<ReceiptNote> items) {
        this.items = items;
    }

    public void addItem(ReceiptNote item) {
        if (this.items == null) this.items = new ArrayList<>();
        this.items.add(item);
    }

    // ── Getters / Setters ───────────────────────────────────

    public int getInboundId() {
        return inboundId;
    }

    public void setInboundId(int inboundId) {
        this.inboundId = inboundId;
    }

    public String getInboundCode() {
        return inboundCode;
    }

    public void setInboundCode(String inboundCode) {
        this.inboundCode = inboundCode;
    }

    public String getSupplierName() {
        return supplierName;
    }

    public void setSupplierName(String supplierName) {
        this.supplierName = supplierName;
    }

    public Integer getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(Integer supplierId) {
        this.supplierId = supplierId;
    }

    public String getSupplierCode() {
        return supplierCode;
    }

    public void setSupplierCode(String supplierCode) {
        this.supplierCode = supplierCode;
    }

    public String getSupplierContact() {
        return supplierContact;
    }

    public void setSupplierContact(String supplierContact) {
        this.supplierContact = supplierContact;
    }

    public String getSupplierPhone() {
        return supplierPhone;
    }

    public void setSupplierPhone(String supplierPhone) {
        this.supplierPhone = supplierPhone;
    }

    public String getSupplierAddress() {
        return supplierAddress;
    }

    public void setSupplierAddress(String supplierAddress) {
        this.supplierAddress = supplierAddress;
    }

    public String getSupplierEmail() {
        return supplierEmail;
    }

    public void setSupplierEmail(String supplierEmail) {
        this.supplierEmail = supplierEmail;
    }

    public String getPaymentTerms() {
        return paymentTerms;
    }

    public void setPaymentTerms(String paymentTerms) {
        this.paymentTerms = paymentTerms;
    }

    public int getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(int warehouseId) {
        this.warehouseId = warehouseId;
    }

    public String getWarehouseName() {
        return warehouseName;
    }

    public void setWarehouseName(String warehouseName) {
        this.warehouseName = warehouseName;
    }

    public Integer getZoneId() {
        return zoneId;
    }

    public void setZoneId(Integer zoneId) {
        this.zoneId = zoneId;
    }

    public String getZoneName() {
        return zoneName;
    }

    public void setZoneName(String zoneName) {
        this.zoneName = zoneName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDate getExpectedDate() {
        return expectedDate;
    }

    public void setExpectedDate(LocalDate expectedDate) {
        this.expectedDate = expectedDate;
    }

    public LocalDate getReceivedDate() {
        return receivedDate;
    }

    public void setReceivedDate(LocalDate receivedDate) {
        this.receivedDate = receivedDate;
    }

    public int getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(int createdBy) {
        this.createdBy = createdBy;
    }

    public String getDeliveryPerson() {
        return deliveryPerson;
    }

    public void setDeliveryPerson(String deliveryPerson) {
        this.deliveryPerson = deliveryPerson;
    }

    public String getDeliveryPhone() {
        return deliveryPhone;
    }

    public void setDeliveryPhone(String deliveryPhone) {
        this.deliveryPhone = deliveryPhone;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Returns items as JSON array string for JSP embedding.
     * Uses both field name variants so JS can always find the values.
     */
    public String getItemsJson() {
        if (items == null || items.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            ReceiptNote it = items.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"productId\":").append(it.getProductId())
              .append(",\"skuCode\":\"").append(escapeJson(it.getSkuCode()))
              .append("\",\"sku\":\"").append(escapeJson(it.getSkuCode()))
              .append("\",\"skuName\":\"").append(escapeJson(it.getProductName()))
              .append("\",\"productName\":\"").append(escapeJson(it.getProductName()))
              .append("\",\"orderedQty\":").append(it.getExpectedQty())
              .append(",\"expectedQty\":").append(it.getExpectedQty())
              .append(",\"receivedQty\":").append(it.getReceivedQty())
              .append(",\"acceptedQty\":").append(it.getAcceptedQty() != null ? it.getAcceptedQty() : 0)
              .append(",\"rejectedQty\":").append(it.getRejectedQty() != null ? it.getRejectedQty() : 0)
              .append(",\"rejectReason\":\"").append(escapeJson(it.getRejectReason() != null ? it.getRejectReason() : ""))
              .append("\",\"price\":").append(it.getUnitCost() != null ? it.getUnitCost() : 0)
              .append(",\"note\":\"").append(escapeJson(it.getNote() != null ? it.getNote() : ""))
              .append("\"}");
        }
        sb.append("]");
        return sb.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    @Override
    public String toString() {
        return "InboundOrder{" +
                "inboundId=" + inboundId +
                ", inboundCode='" + inboundCode + '\'' +
                ", supplierName='" + supplierName + '\'' +
                ", warehouseId=" + warehouseId +
                ", status='" + status + '\'' +
                '}';
    }
}
