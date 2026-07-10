package com.wms.model;

/**
 * LazadaShipmentProvider — Domain model for the lazada_shipment_providers table.
 *
 * <p>Stores the Lazada-recognised shipping carriers for Vietnam (VN region).
 * These codes are used when calling the Pack and RTS APIs.</p>
 *
 * <p>Typical Lazada Vietnam providers: FM49 (Flash Express), J&T, GHTK,
 * GHN, NJV (NinjaVan), SPX (SPX Express).</p>
 */
public class LazadaShipmentProvider {

    private int id;
    private String region = "VN";
    /** Lazada carrier code, e.g. "FM49", "J&T", "GHTK". */
    private String providerCode;
    /** English display name. */
    private String providerName;
    /** Vietnamese display name. */
    private String providerNameVn;
    private boolean isActive = true;
    /** Controls display order in dropdowns. */
    private int displayOrder;

    // ── Constructors ──────────────────────────────────────────

    public LazadaShipmentProvider() {
    }

    public LazadaShipmentProvider(String providerCode, String providerName, String providerNameVn) {
        this.providerCode = providerCode;
        this.providerName = providerName;
        this.providerNameVn = providerNameVn;
    }

    // ── Getters / Setters ─────────────────────────────────────

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getProviderCode() {
        return providerCode;
    }

    public void setProviderCode(String providerCode) {
        this.providerCode = providerCode;
    }

    public String getProviderName() {
        return providerName;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    public String getProviderNameVn() {
        return providerNameVn;
    }

    public void setProviderNameVn(String providerNameVn) {
        this.providerNameVn = providerNameVn;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    @Override
    public String toString() {
        return "LazadaShipmentProvider{" +
                "providerCode='" + providerCode + '\'' +
                ", providerName='" + providerName + '\'' +
                ", isActive=" + isActive +
                '}';
    }
}
