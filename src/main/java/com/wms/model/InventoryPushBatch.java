package com.wms.model;

import java.time.LocalDateTime;
import java.util.List;

public class InventoryPushBatch {
    private String batchId;
    private List<InventoryUpdate> items;
    private LocalDateTime createdAt;
    private int retryCount;
    private LocalDateTime nextRetryTime;
    private PushStatus status;
    private String lastErrorMessage;
    private LocalDateTime updatedAt;

    public enum PushStatus {
        PENDING, SUCCESS, FAILED
    }

    public InventoryPushBatch() {
    }

    public InventoryPushBatch(String batchId, List<InventoryUpdate> items) {
        this.batchId = batchId;
        this.items = items;
        this.createdAt = LocalDateTime.now();
        this.retryCount = 0;
        this.status = PushStatus.PENDING;
        this.updatedAt = LocalDateTime.now();
    }

    // Getters & Setters
    public String getBatchId() {
        return batchId;
    }

    public void setBatchId(String batchId) {
        this.batchId = batchId;
    }

    public List<InventoryUpdate> getItems() {
        return items;
    }

    public void setItems(List<InventoryUpdate> items) {
        this.items = items;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public LocalDateTime getNextRetryTime() {
        return nextRetryTime;
    }

    public void setNextRetryTime(LocalDateTime nextRetryTime) {
        this.nextRetryTime = nextRetryTime;
    }

    public PushStatus getStatus() {
        return status;
    }

    public void setStatus(PushStatus status) {
        this.status = status;
    }

    public String getLastErrorMessage() {
        return lastErrorMessage;
    }

    public void setLastErrorMessage(String lastErrorMessage) {
        this.lastErrorMessage = lastErrorMessage;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
