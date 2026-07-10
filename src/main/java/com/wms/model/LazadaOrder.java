package com.wms.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * LazadaOrder — Domain model for the lazada_orders table.
 *
 * <p>Represents a Lazada order that has been pulled from the Lazada API and
 * stored in the WMS database. Holds both Lazada-native fields (lazadaOrderIdStr,
 * status) and WMS workflow fields (wmsStatus, warehouseId, assignedBy).</p>
 *
 * <p>WMS Status lifecycle: NEW → APPROVED → PACKED → HANDED_OVER → SHIPPING → DELIVERED
 *                              ↘ CANCELLED / RETURNING / EXCEPTION</p>
 */
public class LazadaOrder {

    private int lazadaOrderId;
    /** Lazada's order_id as a string — used as the natural unique key. */
    private String lazadaOrderIdStr;
    private String lazadaOrderNumber;
    private int channelId;
    /** Lazada-native status: pending/unpaid, pending, topack, toship, ready_to_ship, shipped, delivered, canceled, returned, failed */
    private String status;
    /** WMS workflow status: NEW, APPROVED, PACKED, HANDED_OVER, SHIPPING, DELIVERED, CANCELED, RETURNING, EXCEPTION */
    private String wmsStatus;
    /** Lazada's buyer ID (national_registration_number or similar). */
    private String customerId;
    private String customerName;
    private String customerPhone;
    private String shippingAddress;
    private String shippingCity;
    private BigDecimal price;
    private BigDecimal shippingFee;
    private BigDecimal voucherSeller;
    private BigDecimal voucherPlatform;
    private String paymentMethod;
    private String buyerNote;
    private int warehouseId;
    private int assignedBy;
    private LocalDateTime assignedAt;
    /** Lazada package_id from Pack API response. */
    private String packageId;
    /** Tracking number from Pack API response. */
    private String trackingNumber;
    /** Carrier display name, e.g. "Flash Express". */
    private String shipmentProvider;
    /** Carrier code used by Lazada, e.g. "FM49", "JT08". */
    private String shipmentProviderCode;
    private LocalDateTime lazadaCreatedAt;
    private LocalDateTime lazadaUpdatedAt;
    /** Timestamp when Ready-To-Ship was called on Lazada. */
    private LocalDateTime rtsAt;
    private LocalDateTime deliveredAt;
    /** Timestamp of the last successful sync from Lazada API. */
    private LocalDateTime syncedAt;

    /** Joined field — channel name for display purposes. */
    private String channelName;

    /** Order items are stored in lazada_order_items table; lazy-loaded. */
    private List<LazadaOrderItem> items = new ArrayList<>();

    // ── Constructors ──────────────────────────────────────────

    public LazadaOrder() {
    }

    public LazadaOrder(String lazadaOrderIdStr, int channelId, String status) {
        this.lazadaOrderIdStr = lazadaOrderIdStr;
        this.channelId = channelId;
        this.status = status;
        this.wmsStatus = "NEW";
    }

    // ── Getters / Setters ─────────────────────────────────────

    public int getLazadaOrderId() {
        return lazadaOrderId;
    }

    public void setLazadaOrderId(int lazadaOrderId) {
        this.lazadaOrderId = lazadaOrderId;
    }

    public String getLazadaOrderIdStr() {
        return lazadaOrderIdStr;
    }

    public void setLazadaOrderIdStr(String lazadaOrderIdStr) {
        this.lazadaOrderIdStr = lazadaOrderIdStr;
    }

    public String getLazadaOrderNumber() {
        return lazadaOrderNumber;
    }

    public void setLazadaOrderNumber(String lazadaOrderNumber) {
        this.lazadaOrderNumber = lazadaOrderNumber;
    }

    public int getChannelId() {
        return channelId;
    }

    public void setChannelId(int channelId) {
        this.channelId = channelId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getWmsStatus() {
        return wmsStatus;
    }

    public void setWmsStatus(String wmsStatus) {
        this.wmsStatus = wmsStatus;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
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

    public String getShippingAddress() {
        return shippingAddress;
    }

    public void setShippingAddress(String shippingAddress) {
        this.shippingAddress = shippingAddress;
    }

    public String getShippingCity() {
        return shippingCity;
    }

    public void setShippingCity(String shippingCity) {
        this.shippingCity = shippingCity;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getShippingFee() {
        return shippingFee;
    }

    public void setShippingFee(BigDecimal shippingFee) {
        this.shippingFee = shippingFee;
    }

    public BigDecimal getVoucherSeller() {
        return voucherSeller;
    }

    public void setVoucherSeller(BigDecimal voucherSeller) {
        this.voucherSeller = voucherSeller;
    }

    public BigDecimal getVoucherPlatform() {
        return voucherPlatform;
    }

    public void setVoucherPlatform(BigDecimal voucherPlatform) {
        this.voucherPlatform = voucherPlatform;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getBuyerNote() {
        return buyerNote;
    }

    public void setBuyerNote(String buyerNote) {
        this.buyerNote = buyerNote;
    }

    public int getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(int warehouseId) {
        this.warehouseId = warehouseId;
    }

    public int getAssignedBy() {
        return assignedBy;
    }

    public void setAssignedBy(int assignedBy) {
        this.assignedBy = assignedBy;
    }

    public LocalDateTime getAssignedAt() {
        return assignedAt;
    }

    public void setAssignedAt(LocalDateTime assignedAt) {
        this.assignedAt = assignedAt;
    }

    public String getPackageId() {
        return packageId;
    }

    public void setPackageId(String packageId) {
        this.packageId = packageId;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public void setTrackingNumber(String trackingNumber) {
        this.trackingNumber = trackingNumber;
    }

    public String getShipmentProvider() {
        return shipmentProvider;
    }

    public void setShipmentProvider(String shipmentProvider) {
        this.shipmentProvider = shipmentProvider;
    }

    public String getShipmentProviderCode() {
        return shipmentProviderCode;
    }

    public void setShipmentProviderCode(String shipmentProviderCode) {
        this.shipmentProviderCode = shipmentProviderCode;
    }

    public LocalDateTime getLazadaCreatedAt() {
        return lazadaCreatedAt;
    }

    public void setLazadaCreatedAt(LocalDateTime lazadaCreatedAt) {
        this.lazadaCreatedAt = lazadaCreatedAt;
    }

    public LocalDateTime getLazadaUpdatedAt() {
        return lazadaUpdatedAt;
    }

    public void setLazadaUpdatedAt(LocalDateTime lazadaUpdatedAt) {
        this.lazadaUpdatedAt = lazadaUpdatedAt;
    }

    public LocalDateTime getRtsAt() {
        return rtsAt;
    }

    public void setRtsAt(LocalDateTime rtsAt) {
        this.rtsAt = rtsAt;
    }

    public LocalDateTime getDeliveredAt() {
        return deliveredAt;
    }

    public void setDeliveredAt(LocalDateTime deliveredAt) {
        this.deliveredAt = deliveredAt;
    }

    public LocalDateTime getSyncedAt() {
        return syncedAt;
    }

    public void setSyncedAt(LocalDateTime syncedAt) {
        this.syncedAt = syncedAt;
    }

    public String getChannelName() {
        return channelName;
    }

    public void setChannelName(String channelName) {
        this.channelName = channelName;
    }

    public List<LazadaOrderItem> getItems() {
        return items;
    }

    public void setItems(List<LazadaOrderItem> items) {
        this.items = items;
    }

    public void addItem(LazadaOrderItem item) {
        this.items.add(item);
    }

    @Override
    public String toString() {
        return "LazadaOrder{" +
                "lazadaOrderIdStr='" + lazadaOrderIdStr + '\'' +
                ", wmsStatus='" + wmsStatus + '\'' +
                ", status='" + status + '\'' +
                ", customerName='" + customerName + '\'' +
                '}';
    }
}
