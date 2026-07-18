package com.wms.mockshipping;

import java.math.BigDecimal;
import java.util.List;

/**
 * MockShippingService — simulates a shipping carrier for Website-channel orders only.
 *
 * <p>Real channels (Lazada/Shopee/TikTok) have real carrier integrations
 * ({@code com.wms.dao.LazadaShipmentProviderDAO}, Pack/RTS APIs) — this package never
 * touches that code. Scope, by design (confirmed with the business owner 2026-07-14):</p>
 * <ul>
 *   <li>Gated entirely by the 'website.mock_shipping.enabled' toggle (Admin, channel config)
 *       — OFF by default.</li>
 *   <li>Carrier + fee are chosen by the CUSTOMER at checkout (not the warehouse) — mirrors
 *       how real storefronts work, and lets a shipping fee be shown before payment.</li>
 *   <li>Warehouse only displays the pre-chosen carrier and prints a label — no picker there.</li>
 *   <li>No granular shipping-status simulation (PICKED_UP/IN_TRANSIT/...) — the existing
 *       "Xác nhận đã giao" action (Sales/Manager/Admin, independent of this toggle) is the
 *       single source of truth for delivery, and always works regardless of the mock toggle
 *       so real Website orders never get stranded if mock is off.</li>
 * </ul>
 */
public class MockShippingService {

    private final MockShippingDAO dao = new MockShippingDAO();

    public boolean isEnabled() {
        return dao.isEnabled();
    }

    public boolean setEnabled(boolean enabled) {
        return dao.setEnabled(enabled);
    }

    public List<MockCarrier> findActiveCarriers() {
        return dao.findActive();
    }

    public MockCarrier findById(int carrierId) {
        return dao.findById(carrierId);
    }

    /**
     * Assigns the customer's chosen carrier + fee to a newly-created order.
     * Called from WebsiteOrderApiServlet right after the order row is inserted.
     */
    public boolean assignCarrier(int orderId, int carrierId) {
        MockCarrier carrier = dao.findById(carrierId);
        if (carrier == null || !carrier.isActive()) return false;
        return dao.assignCarrierToOrder(orderId, carrier.getCarrierName(), carrier.getFee());
    }

    /** Generates a fake tracking/waybill code, prefixed by the carrier so it reads as plausible. */
    public String generateWaybillCode(String carrierName) {
        String prefix = carrierName == null || carrierName.isBlank()
                ? "MOCK" : carrierName.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
        if (prefix.length() > 12) prefix = prefix.substring(0, 12);
        return prefix + "-" + System.currentTimeMillis() + "-" + (int) (Math.random() * 100);
    }

    /** Fee for a specific active carrier, or zero if not found/inactive. */
    public BigDecimal feeFor(int carrierId) {
        MockCarrier c = dao.findById(carrierId);
        return (c != null && c.isActive()) ? c.getFee() : BigDecimal.ZERO;
    }
}
