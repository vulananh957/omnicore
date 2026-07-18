package com.wms.mockshipping;

import java.math.BigDecimal;

/**
 * MockCarrier — a fake shipping carrier option for Website-channel orders only.
 * See package-level notes in {@link MockShippingService} for why this exists as an
 * isolated package instead of touching the real Lazada shipment-provider code.
 */
public class MockCarrier {

    private int carrierId;
    private String carrierName;
    private BigDecimal fee;
    private boolean active;
    private int displayOrder;

    public int getCarrierId() { return carrierId; }
    public void setCarrierId(int carrierId) { this.carrierId = carrierId; }

    public String getCarrierName() { return carrierName; }
    public void setCarrierName(String carrierName) { this.carrierName = carrierName; }

    public BigDecimal getFee() { return fee; }
    public void setFee(BigDecimal fee) { this.fee = fee; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }
}
