package com.wms.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * ReturnOrder — Domain model / JavaBean for the return_orders table.
 */
public class ReturnOrder {

    private int returnId;
    private Integer orderId;
    private String orderCode; // Joined from orders table
    private Integer outboundId;
    private String customerName;
    private String customerPhone;
    private String reason;
    private String status; // RECEIVED, INSPECTING, PASS, FAIL, RESTOCKED, SCRAPPED
    private int warehouseId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String channel; // Joined from orders table
    private String returnCode;
    private String evidencePhotos;
    private String evidenceVideo;
    private List<ReturnItem> items = new ArrayList<>();

    public ReturnOrder() {}

    // Getters and Setters
    public int getReturnId() {
        return returnId;
    }

    public void setReturnId(int returnId) {
        this.returnId = returnId;
    }

    public Integer getOrderId() {
        return orderId;
    }

    public void setOrderId(Integer orderId) {
        this.orderId = orderId;
    }

    public String getOrderCode() {
        return orderCode;
    }

    public void setOrderCode(String orderCode) {
        this.orderCode = orderCode;
    }

    public Integer getOutboundId() {
        return outboundId;
    }

    public void setOutboundId(Integer outboundId) {
        this.outboundId = outboundId;
    }

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

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(int warehouseId) {
        this.warehouseId = warehouseId;
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

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public List<ReturnItem> getItems() {
        return items;
    }

    public void setItems(List<ReturnItem> items) {
        this.items = items;
    }

    public String getReturnCode() {
        return returnCode;
    }

    public void setReturnCode(String returnCode) {
        this.returnCode = returnCode;
    }

    public String getEvidencePhotos() {
        return evidencePhotos;
    }

    public void setEvidencePhotos(String evidencePhotos) {
        this.evidencePhotos = evidencePhotos;
    }

    public String getEvidenceVideo() {
        return evidenceVideo;
    }

    public void setEvidenceVideo(String evidenceVideo) {
        this.evidenceVideo = evidenceVideo;
    }
}
