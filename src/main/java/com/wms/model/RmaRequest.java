package com.wms.model;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * RmaRequest — a customer-submitted return request (reason + evidence),
 * pending Sales approval. Currently only populated by the Website channel.
 */
public class RmaRequest {

    private int rmaId;
    private int orderId;
    private String orderCode;
    private String rmaCode;
    private String status;
    private String returnReason;
    private String evidencePhotos;
    private String evidenceVideo;
    private String resolutionNote;
    private LocalDateTime requestedAt;
    private LocalDateTime returnedAt;

    public int getRmaId() { return rmaId; }
    public void setRmaId(int rmaId) { this.rmaId = rmaId; }

    public int getOrderId() { return orderId; }
    public void setOrderId(int orderId) { this.orderId = orderId; }

    public String getOrderCode() { return orderCode; }
    public void setOrderCode(String orderCode) { this.orderCode = orderCode; }

    public String getRmaCode() { return rmaCode; }
    public void setRmaCode(String rmaCode) { this.rmaCode = rmaCode; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getReturnReason() { return returnReason; }
    public void setReturnReason(String returnReason) { this.returnReason = returnReason; }

    public String getEvidencePhotos() { return evidencePhotos; }
    public void setEvidencePhotos(String evidencePhotos) { this.evidencePhotos = evidencePhotos; }

    public String getEvidenceVideo() { return evidenceVideo; }
    public void setEvidenceVideo(String evidenceVideo) { this.evidenceVideo = evidenceVideo; }

    public String getResolutionNote() { return resolutionNote; }
    public void setResolutionNote(String resolutionNote) { this.resolutionNote = resolutionNote; }

    public LocalDateTime getRequestedAt() { return requestedAt; }
    public void setRequestedAt(LocalDateTime requestedAt) { this.requestedAt = requestedAt; }

    /** JSTL's fmt:formatDate needs java.util.Date, not LocalDateTime — helper for JSPs. */
    public Date getRequestedAtAsDate() {
        return requestedAt == null ? null : Date.from(requestedAt.atZone(ZoneId.systemDefault()).toInstant());
    }

    public LocalDateTime getReturnedAt() { return returnedAt; }
    public void setReturnedAt(LocalDateTime returnedAt) { this.returnedAt = returnedAt; }
}
