package com.wms.service.channel;

import com.wms.model.Channel;

import java.util.List;
import java.util.Map;

/**
 * ChannelGateway — Channel-agnostic abstraction over a marketplace
 * (Lazada, Shopee, TikTok, ...). The runtime resolves a concrete
 * implementation through {@link ChannelRegistry}.
 *
 * <p>Every method returns the raw JSON response from the platform,
 * leaving the caller to parse what it needs. Higher-level domain
 * services (LazadaOrderService, LazadaProductService, ...) parse
 * Lazada-specific responses; new platforms implement this interface
 * with their own JSON shape.
 */
public interface ChannelGateway {

    /** Lazada, Shopee, TikTok — matches {@code channels.platform}. */
    String platformName();

    // ── Catalog ──────────────────────────────────────────────

    /** Fetches the platform's product list (paginated). */
    String listProducts(Channel channel, int pageNumber, int pageSize);

    /** Fetches a single product by its platform item_id (no pagination needed). */
    String getProductByItemId(Channel channel, String itemId);

    /** Creates a product on the channel. */
    String createProduct(Channel channel, Map<String, String> payload);

    /** Updates an existing channel product. */
    String updateProduct(Channel channel, String channelItemId,
                         Map<String, String> payload);

    /**
     * UC-B2C09: Lazada {@code /category/tree/get} — returns the full category
     * tree for the seller's region. Not all channels expose this; default
     * implementation returns {@code "{}"}.
     */
    default String getCategoryTree(Channel channel, String languageCode) { return "{}"; }

    /**
     * UC-B2C09: Migrates external image URLs to the channel's CDN.
     * Lazada's {@code POST /images/migrate} takes up to ~8 URLs and returns
     * the channel-side image URL + id for each input.
     *
     * <p>Returns raw JSON response; higher-level service parses it.</p>
     *
     * @param channel the marketplace channel (must have valid OAuth token)
     * @param externalUrls public HTTPS URLs of the source images
     * @return raw JSON response from the channel
     */
    String migrateImages(Channel channel, List<String> externalUrls);

    /** Removes a product or specific SKUs from the channel. */
    default String removeProduct(Channel channel, String sellerSkuListJson, String skuIdListJson) {
        return "{}";
    }

    // ── Stock & Price sync (BR-02 buffer stock rule) ────────

    /** Pushes the sellable stock for a single SKU. */
    String updateProductStock(Channel channel, String sellerSku, int qty);

    /** Pushes the sellable stock for many SKUs at once. */
    String updateProductStockBatch(Channel channel, List<StockUpdate> updates);

    // ── Orders ───────────────────────────────────────────────

    /** Lists orders in the given status, updated since the given epoch seconds. */
    String listOrders(Channel channel, String status, Long updatedAfterEpochSec);

    /** Returns the order detail (full payload). */
    String getOrder(Channel channel, String orderId);

    /** Returns the order items. */
    String getOrderItems(Channel channel, String orderId);

    // ── Fulfillment ──────────────────────────────────────────

    /**
     * 1. /order/fulfill/pack — Lazada returns tracking number + package id.
     * payload: order_item_list, delivery_type
     */
    String packOrder(Channel channel, String orderId, String deliveryType);

    /** 2. /order/package/document/get — returns base64 PDF label. */
    String getShippingLabel(Channel channel, String packageId);

    /** 3. /order/package/rts — Ready-To-Ship notification. */
    String readyToShip(Channel channel, String packageId);

    // ── Tracking ─────────────────────────────────────────────

    /** /logistic/order/trace — returns tracking events array. */
    String getTrackingTrace(Channel channel, String orderNumber);

    // ── RMA / Reverse ────────────────────────────────────────

    /** GET /reverse/getreverseordersforseller */
    String listReverseOrders(Channel channel, Long createdAfterEpochSec);

    /**
     * GET /order/reverse/return/update — confirms / refuses a return.
     * action: "acceptReturn" | "refuseReturn" | "refuseRefund"
     */
    String updateReverseOrder(Channel channel, String reverseOrderId, String action,
                              Map<String, String> payload);

    // ── Cancel ────────────────────────────────────────────────

    /**
     * Step 1 of the cancel flow: validates whether an order can be cancelled
     * and returns the available reason options.
     *
     * <p>Endpoint: GET /order/reverse/cancel/validate</p>
     *
     * @param orderId         Lazada order ID
     * @param orderItemIdList JSON array string of order_item_ids, e.g. ["100827","..."]
     * @return raw Lazada JSON response
     */
    default String cancelValidate(Channel channel, String orderId, String orderItemIdList) {
        throw new UnsupportedOperationException(
                "cancelValidate is not supported by " + platformName());
    }

    /**
     * Step 2 of the cancel flow: submits the cancel request.
     *
     * <p>Endpoint: GET /order/reverse/cancel/create</p>
     *
     * @param orderId         Lazada order ID
     * @param orderItemIdList JSON array string of order_item_ids, e.g. ["100827","..."]
     * @param reasonId        reason ID selected by staff
     * @return raw Lazada JSON response
     */
    default String cancelCreate(Channel channel, String orderId,
                               String orderItemIdList, String reasonId) {
        throw new UnsupportedOperationException(
                "cancelCreate is not supported by " + platformName());
    }

    // ── DTOs ─────────────────────────────────────────────────

    final class StockUpdate {
        public final String sellerSku;
        public final String skuId;   // Lazada's internal SkuId (required for stock updates post-Nov 2023)
        public final int qty;
        public StockUpdate(String sellerSku, String skuId, int qty) {
            this.sellerSku = sellerSku;
            this.skuId = skuId;
            this.qty = qty;
        }
    }
}
