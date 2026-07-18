package com.wms.model;

public class InventoryUpdate {
    private String productId;
    private int qtyAvailable;
    private int qtyBefore;

    public InventoryUpdate() {
    }

    public InventoryUpdate(String productId, int qtyAvailable, int qtyBefore) {
        this.productId = productId;
        this.qtyAvailable = qtyAvailable;
        this.qtyBefore = qtyBefore;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public int getQtyAvailable() {
        return qtyAvailable;
    }

    public void setQtyAvailable(int qtyAvailable) {
        this.qtyAvailable = qtyAvailable;
    }

    public int getQtyBefore() {
        return qtyBefore;
    }

    public void setQtyBefore(int qtyBefore) {
        this.qtyBefore = qtyBefore;
    }
}
