package com.wms.model;

import java.time.LocalDateTime;

/**
 * ProductChangeNotification — Alert admin when a listed product (on Lazada/Website) is modified.
 * Tracks change type (CREATE/UPDATE/DELETE) + affected channels.
 */
public class ProductChangeNotification {

    private int notificationId;
    private int productId;
    private String skuCode;
    private String productName;
    private String changeType; // CREATE, UPDATE, DELETE
    private String affectedChannels; // comma-separated: "WEBSITE,LAZADA"
    private String changeDetails; // What changed: "price, stock, name"
    private String status; // PENDING, ACKNOWLEDGED, DISMISSED
    private LocalDateTime createdAt;
    private LocalDateTime acknowledgedAt;

    public ProductChangeNotification() {}

    public ProductChangeNotification(int productId, String skuCode, String productName,
                                     String changeType, String affectedChannels, String changeDetails) {
        this.productId = productId;
        this.skuCode = skuCode;
        this.productName = productName;
        this.changeType = changeType;
        this.affectedChannels = affectedChannels;
        this.changeDetails = changeDetails;
        this.status = "PENDING";
        this.createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public int getNotificationId() { return notificationId; }
    public void setNotificationId(int notificationId) { this.notificationId = notificationId; }

    public int getProductId() { return productId; }
    public void setProductId(int productId) { this.productId = productId; }

    public String getSkuCode() { return skuCode; }
    public void setSkuCode(String skuCode) { this.skuCode = skuCode; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public String getChangeType() { return changeType; }
    public void setChangeType(String changeType) { this.changeType = changeType; }

    public String getAffectedChannels() { return affectedChannels; }
    public void setAffectedChannels(String affectedChannels) { this.affectedChannels = affectedChannels; }

    public String getChangeDetails() { return changeDetails; }
    public void setChangeDetails(String changeDetails) { this.changeDetails = changeDetails; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getAcknowledgedAt() { return acknowledgedAt; }
    public void setAcknowledgedAt(LocalDateTime acknowledgedAt) { this.acknowledgedAt = acknowledgedAt; }
}
