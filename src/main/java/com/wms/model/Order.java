package com.wms.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Order — Domain model / JavaBean for the orders table.
 */
public class Order {

    @JsonProperty("id")
    private int orderId;
    private String orderCode;
    private Integer customerId;
    private int warehouseId;
    private String channel;
    private String status;
    private double totalAmount;
    private String note;
    private Integer createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<OrderItem> items = new ArrayList<>();

    // Custom WMS fields
    private String trackingNo;
    private String shipmentProvider;
    private String reviewNote;
    private String rmaReason;
    private String rmaPhysicalStatus;
    private String rmaPlatformStatus;
    private String disputeEvidenceVideo;
    private String disputeNote;
    // Set only for orders created by omnicore-web — the channel-agnostic marker
    // used to gate Website-only actions (manual delivery confirm, return window).
    private String webOrderRef;

    // Customer Info fields
    private String customerName;
    private String customerPhone;
    private String customerAddress;

    @JsonProperty("warehouse")
    private String warehouseName;

    // Lazada end-to-end: foreign key to channels table.
    // Distinct from {@link #channel} which is the high-level enum
    // (ONLINE / STORE / B2B).
    private int channelId;
    // Lazada's package_id from /order/fulfill/pack. Used to call RTS.
    private String lazadaPackageId;
    // True after a successful /order/fulfill/pack call.
    private boolean packRequested;
    // True after a successful /order/package/rts call.
    private boolean rtsPushed;
    // True after the Lazada AWB label PDF has been fetched and sent to the printer.
    private boolean labelPrinted;

    // Constructors
    public Order() {}

    public Order(int orderId, String orderCode, Integer customerId, int warehouseId,
                 String channel, String status, double totalAmount, String note,
                 Integer createdBy, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.orderId = orderId;
        this.orderCode = orderCode;
        this.customerId = customerId;
        this.warehouseId = warehouseId;
        this.channel = channel;
        this.status = status;
        this.totalAmount = totalAmount;
        this.note = note;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // Getters and Setters
    public int getOrderId() {
        return orderId;
    }

    public void setOrderId(int orderId) {
        this.orderId = orderId;
    }

    public String getOrderCode() {
        return orderCode;
    }

    public void setOrderCode(String orderCode) {
        this.orderCode = orderCode;
    }

    public Integer getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Integer customerId) {
        this.customerId = customerId;
    }

    public int getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(int warehouseId) {
        this.warehouseId = warehouseId;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public double getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(double totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Integer getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Integer createdBy) {
        this.createdBy = createdBy;
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

    public String getWarehouseName() {
        return warehouseName;
    }

    public void setWarehouseName(String warehouseName) {
        this.warehouseName = warehouseName;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public void setItems(List<OrderItem> items) {
        this.items = items;
    }

    public String getTrackingNo() {
        return trackingNo;
    }

    public void setTrackingNo(String trackingNo) {
        this.trackingNo = trackingNo;
    }

    public String getShipmentProvider() {
        return shipmentProvider;
    }

    public void setShipmentProvider(String shipmentProvider) {
        this.shipmentProvider = shipmentProvider;
    }

    public String getReviewNote() {
        return reviewNote;
    }

    public void setReviewNote(String reviewNote) {
        this.reviewNote = reviewNote;
    }

    public String getRmaReason() {
        return rmaReason;
    }

    public void setRmaReason(String rmaReason) {
        this.rmaReason = rmaReason;
    }

    public String getRmaPhysicalStatus() {
        return rmaPhysicalStatus;
    }

    public void setRmaPhysicalStatus(String rmaPhysicalStatus) {
        this.rmaPhysicalStatus = rmaPhysicalStatus;
    }

    public String getRmaPlatformStatus() {
        return rmaPlatformStatus;
    }

    public void setRmaPlatformStatus(String rmaPlatformStatus) {
        this.rmaPlatformStatus = rmaPlatformStatus;
    }

    public String getDisputeEvidenceVideo() {
        return disputeEvidenceVideo;
    }

    public void setDisputeEvidenceVideo(String disputeEvidenceVideo) {
        this.disputeEvidenceVideo = disputeEvidenceVideo;
    }

    public String getDisputeNote() {
        return disputeNote;
    }

    public void setDisputeNote(String disputeNote) {
        this.disputeNote = disputeNote;
    }

    public int getChannelId() { return channelId; }
    public void setChannelId(int channelId) { this.channelId = channelId; }

    public String getLazadaPackageId() { return lazadaPackageId; }
    public void setLazadaPackageId(String lazadaPackageId) { this.lazadaPackageId = lazadaPackageId; }

    public boolean isPackRequested() { return packRequested; }
    public void setPackRequested(boolean packRequested) { this.packRequested = packRequested; }

    public boolean isRtsPushed() { return rtsPushed; }
    public void setRtsPushed(boolean rtsPushed) { this.rtsPushed = rtsPushed; }

    public boolean isLabelPrinted() { return labelPrinted; }
    public void setLabelPrinted(boolean labelPrinted) { this.labelPrinted = labelPrinted; }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public void setCustomerPhone(String customerPhone) {
        this.customerPhone = customerPhone;
    }

    public String getCustomerAddress() {
        return customerAddress;
    }

    public void setCustomerAddress(String customerAddress) {
        this.customerAddress = customerAddress;
    }

    public String getWebOrderRef() { return webOrderRef; }
    public void setWebOrderRef(String webOrderRef) { this.webOrderRef = webOrderRef; }
}

