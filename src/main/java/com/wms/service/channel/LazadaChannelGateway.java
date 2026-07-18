package com.wms.service.channel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.model.Channel;
import com.wms.model.StockPushItem;
import com.wms.service.lazada.LazadaHttpClient;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * LazadaChannelGateway — Lazada implementation of {@link ChannelGateway}.
 * All actual HTTP work is delegated to {@link LazadaHttpClient}; this
 * class only builds the per-endpoint parameter payload.
 *
 * <p>NOTE: Lazada's public API requires certain params (e.g. item_id for
 * update, order_item_id list for pack) that vary by endpoint. We only
 * pass the data the caller supplied; if Lazada returns an error, the
 * caller maps it to a user-facing message.
 */
public class LazadaChannelGateway implements ChannelGateway {

    private static final Logger LOGGER = Logger.getLogger(LazadaChannelGateway.class.getName());
    private final LazadaHttpClient http = new LazadaHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String platformName() {
        return "Lazada";
    }

    // ── Catalog ──────────────────────────────────────────────

    @Override
    public String listProducts(Channel channel, int pageNumber, int pageSize) {
        Map<String, String> params = new HashMap<>();
        params.put("filter", "all");
        params.put("page_no", String.valueOf(Math.max(pageNumber, 1)));
        params.put("page_size", String.valueOf(Math.max(1, Math.min(pageSize, 50))));
        return http.executeGet("/products/get", params, channel);
    }

    @Override
    public String getProductByItemId(Channel channel, String itemId) {
        Map<String, String> params = new HashMap<>();
        params.put("item_id", itemId);
        return http.executeGet("/product/item/get", params, channel);
    }

    @Override
    public String createProduct(Channel channel, Map<String, String> payload) {
        return http.executePost("/product/create", payload, channel);
    }

    /**
     * UC-B2C09: Lazada {@code POST /brand/get} — returns a list of valid Lazada brands
     * for the seller's country. Lazada VN brand IDs are different from other countries.
     * This is needed because Lazada requires a numeric {@code brand_id} in the
     * /product/create and /product/update payloads (the text {@code brand} field is
     * deprecated and many categories reject products without a valid brand_id).
     *
     * @param channel      the Lazada channel (provides country context via country code)
     * @param countryCode  Lazada country code: "vn", "my", "id", "th", "ph", "sg"
     * @param pageSize     max brands per page (Lazada default 100)
     * @return raw JSON from Lazada /brand/get
     */
    public String getBrands(Channel channel, String countryCode, int pageSize) {
        Map<String, String> params = new HashMap<>();
        params.put("country_code", countryCode);
        params.put("page_size", String.valueOf(pageSize));
        return http.executeGet("/brand/get", params, channel);
    }

    /**
     * UC-B2C09: Lazada {@code POST /images/migrate}.
     *
     * <p>Lazada requires image URLs to live on its CDN before a product can
     * reference them via {@code /product/create}. Up to ~8 URLs can be
     * migrated in a single batch call; the response includes a Lazada-side
     * URL and image id for each input.</p>
     *
     * <p>The endpoint expects a JSON body wrapped in a {@code payload} form
     * parameter — same shape as {@link #updateProductStockBatch}. Lazada's
     * HMAC signature is computed over the form params, not the JSON body.</p>
     */
    @Override
    public String migrateImages(Channel channel, List<String> externalUrls) {
        if (externalUrls == null || externalUrls.isEmpty()) return "{\"code\":\"0\",\"data\":{\"images\":[]}}";
        try {
            Map<String, Object> body = new HashMap<>();
            // Lazada expects comma-separated string in `image_urls` field.
            body.put("image_urls", String.join(",", externalUrls));
            String json = mapper.writeValueAsString(body);

            Map<String, String> params = new HashMap<>();
            params.put("payload", json);
            return http.executePost("/images/migrate", params, channel);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize image migration payload", e);
        }
    }

    @Override
    public String updateProduct(Channel channel, String channelItemId,
                                Map<String, String> payload) {
        if (channelItemId == null || channelItemId.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot update product: channelItemId is null or empty. "
                    + "The product may not have been pushed to Lazada yet. "
                    + "Use createProduct() instead of updateProduct().");
        }
        // Lazada /product/update requires the item identifier to be embedded INSIDE
        // the JSON payload (Request.Product.ItemId), NOT as a separate form param.
        // The previous behaviour of appending item_id=<id> to the form-urlencoded body
        // caused two problems:
        //   (a) NullPointerException on URLEncoder.encode(null) when channelItemId was null
        //   (b) Lazada rejected the request because the JSON body lacked ItemId.
        // LazadaProductPayloadBuilder.buildUpdateJson now reads the id from
        // ChannelProduct.channelItemId and places it correctly in the JSON.
        return http.executePost("/product/update", payload, channel);
    }

    @Override
    public String removeProduct(Channel channel, String sellerSkuListJson, String skuIdListJson) {
        Map<String, String> params = new java.util.HashMap<>();
        if (sellerSkuListJson != null && !sellerSkuListJson.isEmpty()) {
            params.put("seller_sku_list", sellerSkuListJson);
        }
        if (skuIdListJson != null && !skuIdListJson.isEmpty()) {
            params.put("sku_id_list", skuIdListJson);
        }
        return http.executePost("/product/remove", params, channel);
    }

    // ── Stock & Price ────────────────────────────────────────

    @Override
    public String updateProductStock(Channel channel, String sellerSku, int qty) {
        Map<String, String> params = new HashMap<>();
        params.put("seller_sku", sellerSku);
        params.put("qty", String.valueOf(Math.max(qty, 0)));
        return http.executePost("/product/stock/sellable/update", params, channel);
    }

    @Override
    public String updateProductStockBatch(Channel channel, List<StockUpdate> updates) {
        // Lazada's UpdateSellableQuantity endpoint (/product/stock/sellable/update) requires
        // an XML payload wrapped in a <Request> element.
        // IMPORTANT: SellerSku was deprecated after Nov 15, 2023.
        //            All calls must use SkuId (Lazada's internal SKU ID) instead.
        // We retrieve the SkuId from Lazada's API (stored in channel_products or
        // fetched via /product/item/get) and use it here for each SKU.
        // Reference: https://open.lazada.com/apps/doc/doc?nodeId=10450&docId=108068
        StringBuilder xml = new StringBuilder("<Request><Product><Skus>");
        for (StockUpdate u : updates) {
            String skuId = u.skuId;  // Lazada's internal SkuId (required, not SellerSku)
            if (skuId == null || skuId.isEmpty()) {
                LOGGER.warning("updateProductStockBatch: SkuId missing for sellerSku=" + u.sellerSku + " — skipping");
                continue;
            }
            xml.append("<Sku>");
            xml.append("<SkuId>").append(escapeXml(skuId)).append("</SkuId>");
            xml.append("<MultiWarehouseInventories>");
            xml.append("<MultiWarehouseInventory>");
            xml.append("<WarehouseCode>dropshipping</WarehouseCode>");
            xml.append("<Quantity>").append(Math.max(u.qty, 0)).append("</Quantity>");
            xml.append("</MultiWarehouseInventory>");
            xml.append("</MultiWarehouseInventories>");
            xml.append("</Sku>");
        }
        xml.append("</Skus></Product></Request>");

        if (xml.toString().equals("<Request><Product><Skus></Skus></Product></Request>")) {
            LOGGER.warning("updateProductStockBatch: no SKUs with valid SkuId — nothing to push");
            return "{\"code\":\"0\",\"type\":\"ISP\",\"message\":\"No valid SkuIds provided\"}";
        }

        Map<String, String> params = new HashMap<>();
        params.put("payload", xml.toString());
        String resp = http.executePost("/product/stock/sellable/update", params, channel);
        LOGGER.info("updateProductStockBatch: response=" + resp);
        return resp;
    }

    private static String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    /**
     * UC-B2C10 / BR-02: Pushes sellable quantity to Lazada using
     * {@code POST /product/stock/sellable/update}.
     *
     * <p>Unlike {@link #updateProductStockBatch} which sends only
     * {@code seller_sku + qty} in JSON, this method uses Lazada's
     * XML {@code <payload>} format and includes all required identifiers
     * ({@code item_id}, {@code sku_id}, {@code seller_sku}) so the
     * stock is correctly mapped at the SKU level.</p>
     *
     * <p>This method handles rate-limit errors (E901) with exponential
     * backoff retry (max 3 attempts). All other errors are returned
     * as-is for the caller to interpret.</p>
     *
     * @param channel Lazada channel credentials
     * @param items  List of SKU items to push (max recommended: 20)
     * @return Raw Lazada JSON response
     */
    public String updateSellableQuantity(Channel channel, List<StockPushItem> items) {
        if (items == null || items.isEmpty()) {
            return "{\"code\":\"0\",\"data\":{}}";
        }

        // Build XML payload as required by /product/stock/sellable/update
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        xml.append("<Request>");
        xml.append("<Product>");
        xml.append("<Skus>");
        for (StockPushItem item : items) {
            xml.append("<Sku>");
            xml.append("<ItemId>").append(nullSafe(item.getChannelItemId())).append("</ItemId>");
            xml.append("<SkuId>").append(nullSafe(item.getLazadaSkuId())).append("</SkuId>");
            xml.append("<SellerSku>").append(nullSafe(item.getSellerSku())).append("</SellerSku>");
            int qty = item.getPushQty() != null ? item.getPushQty().intValue() : 0;
            xml.append("<SellableQuantity>").append(Math.max(qty, 0)).append("</SellableQuantity>");
            xml.append("</Sku>");
        }
        xml.append("</Skus>");
        xml.append("</Product>");
        xml.append("</Request>");

        Map<String, String> params = new HashMap<>();
        params.put("payload", xml.toString());

        return http.executePost("/product/stock/sellable/update", params, channel);
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }

    // ── Orders ───────────────────────────────────────────────

    @Override
    public String listOrders(Channel channel, String status, Long updatedAfterEpochSec) {
        Map<String, String> params = new HashMap<>();
        if (status != null && !status.isEmpty()) {
            params.put("status", status);
        }
        if (updatedAfterEpochSec != null) {
            params.put("updated_after", String.valueOf(updatedAfterEpochSec));
        }
        params.put("sort_direction", "DESC");
        params.put("limit", "100");
        return http.executeGet("/orders/get", params, channel);
    }

    @Override
    public String getOrder(Channel channel, String orderId) {
        Map<String, String> params = new HashMap<>();
        params.put("order_id", orderId);
        return http.executeGet("/order/get", params, channel);
    }

    @Override
    public String getOrderItems(Channel channel, String orderId) {
        Map<String, String> params = new HashMap<>();
        params.put("order_id", orderId);
        return http.executeGet("/order/items/get", params, channel);
    }

    // ── Fulfillment ──────────────────────────────────────────

    @Override
    public String packOrder(Channel channel, String orderId, String deliveryType) {
        // Build packReq JSON body as required by Lazada /order/fulfill/pack
        Map<String, Object> packReq = new HashMap<>();
        List<Map<String, Object>> packOrderList = new ArrayList<>();
        Map<String, Object> packOrder = new HashMap<>();
        packOrder.put("order_id", orderId);
        packOrder.put("order_item_list", new ArrayList<>()); // caller should override
        packOrderList.add(packOrder);
        packReq.put("pack_order_list", packOrderList);
        packReq.put("delivery_type", (deliveryType == null || deliveryType.isEmpty())
                ? "dropship" : deliveryType);
        packReq.put("shipping_allocate_type", "TFS");
        try {
            String json = mapper.writeValueAsString(packReq);
            return http.executePost("/order/fulfill/pack",
                    Map.of("packReq", json), channel, json);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build packReq JSON", e);
        }
    }

    /**
     * Variant of {@link #packOrder} that accepts caller-supplied params including
     * the order_item_list JSON array of order_item_ids.
     *
     * <p>Builds the correct Lazada {@code packReq} JSON body:</p>
     * <pre>
     * {
     *   "pack_order_list": [{"order_id": "...", "order_item_list": ["100827", "..."]}],
     *   "delivery_type": "dropship",
     *   "shipment_provider_code": "FM49",   // optional
     *   "shipping_allocate_type": "TFS"
     * }
     * </pre>
     */
    public String packOrderWithParams(Channel channel, Map<String, String> params) {
        List<Map<String, Object>> packOrderList = new ArrayList<>();
        Map<String, Object> packOrder = new HashMap<>();
        packOrder.put("order_id", params.get("order_id"));
        // order_item_list may already be a JSON array string like ["100827","..."]
        String itemListStr = params.get("order_item_list");
        if (itemListStr != null && !itemListStr.isEmpty()) {
            try {
                Object parsed = mapper.readValue(itemListStr, Object.class);
                packOrder.put("order_item_list", parsed);
            } catch (Exception e) {
                // Fallback: treat as raw string
                packOrder.put("order_item_list", new ArrayList<>());
            }
        } else {
            packOrder.put("order_item_list", new ArrayList<>());
        }
        packOrderList.add(packOrder);

        Map<String, Object> packReq = new HashMap<>();
        packReq.put("pack_order_list", packOrderList);
        packReq.put("delivery_type",
                params.getOrDefault("delivery_type", "dropship"));
        if (params.containsKey("shipment_provider_code")
                && params.get("shipment_provider_code") != null
                && !params.get("shipment_provider_code").isEmpty()) {
            packReq.put("shipment_provider_code", params.get("shipment_provider_code"));
        }
        packReq.put("shipping_allocate_type",
                params.getOrDefault("shipping_allocate_type", "TFS"));

        try {
            String json = mapper.writeValueAsString(packReq);
            // Auth params in URL query (signed). Business JSON wrapped in packReq form param
            // per Lazada spec: POST /order/fulfill/pack with form-urlencoded body.
            Map<String, String> authParams = new TreeMap<>();
            authParams.put("app_key", channel.getApiKey());
            authParams.put("access_token", channel.getAccessToken());
            authParams.put("timestamp", String.valueOf(System.currentTimeMillis()));
            authParams.put("sign_method", "sha256");
            authParams.put("packReq", json);   // business wrapper — must be in signed params
            return http.executePost("/order/fulfill/pack", authParams, channel, null);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build packReq JSON", e);
        }
    }

    @Override
    public String getShippingLabel(Channel channel, String packageId) {
        // Correct endpoint: POST /order/package/document/get with getDocumentReq body.
        // Auth params go in the URL query string (signed); only the business JSON
        // goes in the POST body.
        Map<String, Object> docReq = new HashMap<>();
        docReq.put("doc_type", "PDF");
        docReq.put("print_item_list", false);
        List<Map<String, String>> packages = new ArrayList<>();
        packages.add(Map.of("package_id", packageId));
        docReq.put("packages", packages);
        try {
            String json = mapper.writeValueAsString(docReq);
            Map<String, String> authParams = new TreeMap<>();
            authParams.put("app_key", channel.getApiKey());
            authParams.put("access_token", channel.getAccessToken());
            authParams.put("timestamp", String.valueOf(System.currentTimeMillis()));
            authParams.put("sign_method", "sha256");
            authParams.put("getDocumentReq", json);  // business wrapper — must be in signed params
            return http.executePost("/order/package/document/get", authParams, channel, null);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build getDocumentReq JSON", e);
        }
    }

    /**
     * Downloads the shipping label as a PDF byte array for the given package.
     *
     * <p>Uses POST /order/package/document/get with doc_type=PDF, then decodes
     * the Base64 file from {@code result.data.file}.</p>
     */
    public byte[] downloadShippingLabelPdf(Channel channel, String packageId) {
        Map<String, Object> docReq = new HashMap<>();
        docReq.put("doc_type", "PDF");
        docReq.put("print_item_list", false);
        List<Map<String, String>> packages = new ArrayList<>();
        packages.add(Map.of("package_id", packageId));
        docReq.put("packages", packages);
        try {
            String json = mapper.writeValueAsString(docReq);
            Map<String, String> authParams = new TreeMap<>();
            authParams.put("app_key", channel.getApiKey());
            authParams.put("access_token", channel.getAccessToken());
            authParams.put("timestamp", String.valueOf(System.currentTimeMillis()));
            authParams.put("sign_method", "sha256");
            authParams.put("getDocumentReq", json);  // business wrapper — must be in signed params
            String body = http.executePost("/order/package/document/get", authParams, channel, null);

            com.fasterxml.jackson.databind.JsonNode root =
                    mapper.readTree(body);
            // Try result.data.file first (standard), fall back to data.file (legacy)
            String fileBase64 = root.path("result").path("data").path("file").asText();
            if (fileBase64.isEmpty()) {
                fileBase64 = root.path("data").path("file").asText();
            }
            if (fileBase64.isEmpty()) {
                // Lazada may return a PDF URL instead
                String pdfUrl = root.path("result").path("data").path("pdf_url").asText();
                if (!pdfUrl.isEmpty()) {
                    LOGGER.warning("LazadaChannelGateway: PDF URL returned instead of Base64: "
                            + pdfUrl + " — cannot auto-download in this context");
                }
                return null;
            }
            return Base64.getDecoder().decode(fileBase64);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING,
                    "downloadShippingLabelPdf failed for packageId=" + packageId, e);
            return null;
        }
    }

    @Override
    public String readyToShip(Channel channel, String packageId) {
        // Auth params go in the URL query string (signed); only the business JSON
        // goes in the POST body.
        Map<String, Object> rtsReq = new HashMap<>();
        List<Map<String, String>> packages = new ArrayList<>();
        packages.add(Map.of("package_id", packageId));
        rtsReq.put("packages", packages);
        try {
            String json = mapper.writeValueAsString(rtsReq);
            Map<String, String> authParams = new TreeMap<>();
            authParams.put("app_key", channel.getApiKey());
            authParams.put("access_token", channel.getAccessToken());
            authParams.put("timestamp", String.valueOf(System.currentTimeMillis()));
            authParams.put("sign_method", "sha256");
            authParams.put("readyToShipReq", json);  // business wrapper — must be in signed params
            return http.executePost("/order/package/rts", authParams, channel, null);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build readyToShipReq JSON", e);
        }
    }

    @Override
    public String getShipmentProviders(Channel channel, String orderId, List<String> orderItemIds) {
        Map<String, Object> req = new HashMap<>();
        List<Map<String, Object>> ordersList = new ArrayList<>();
        Map<String, Object> orderMap = new HashMap<>();
        orderMap.put("order_id", Long.parseLong(orderId));
        
        List<Long> itemIds = new ArrayList<>();
        for (String id : orderItemIds) {
            itemIds.add(Long.parseLong(id));
        }
        orderMap.put("order_item_ids", itemIds);
        ordersList.add(orderMap);
        req.put("orders", ordersList);
        
        try {
            String json = mapper.writeValueAsString(req);
            Map<String, String> authParams = new TreeMap<>();
            authParams.put("app_key", channel.getApiKey());
            authParams.put("access_token", channel.getAccessToken());
            authParams.put("timestamp", String.valueOf(System.currentTimeMillis()));
            authParams.put("sign_method", "sha256");
            authParams.put("getShipmentProvidersReq", json);
            return http.executePost("/order/shipment/providers/get", authParams, channel, null);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build getShipmentProvidersReq JSON", e);
        }
    }

    // ── Tracking ─────────────────────────────────────────────

    @Override
    public String getTrackingTrace(Channel channel, String orderNumber) {
        Map<String, String> params = new HashMap<>();
        params.put("order_id", orderNumber);
        return http.executeGet("/logistic/order/trace", params, channel);
    }

    // ── RMA / Reverse ────────────────────────────────────────

    @Override
    public String listReverseOrders(Channel channel, Long createdAfterEpochSec) {
        Map<String, String> params = new HashMap<>();
        if (createdAfterEpochSec != null) {
            params.put("created_after", String.valueOf(createdAfterEpochSec));
        }
        params.put("limit", "50");
        return http.executeGet("/reverse/getreverseordersforseller", params, channel);
    }

    @Override
    public String updateReverseOrder(Channel channel, String reverseOrderId, String action,
                                     Map<String, String> payload) {
        Map<String, String> params = new HashMap<>();
        if (payload != null) params.putAll(payload);
        params.put("reverse_order_id", reverseOrderId);
        params.put("action", action);
        return http.executePost("/order/reverse/return/update", params, channel);
    }

    // ── Cancel ─────────────────────────────────────────────────

    /**
     * Notifies Lazada that an order has been cancelled.
     *
     * <p>Endpoint: POST /order/cancel</p>
     * <p>Reason IDs: 1=buyer, 2=seller, 3=system, 4=other</p>
     */
    public String cancelOrder(Channel channel, String orderId, String reasonId) {
        Map<String, String> params = new HashMap<>();
        params.put("order_id", orderId);
        params.put("reason_id", reasonId != null ? reasonId : "2"); // default: seller
        return http.executePost("/order/cancel", params, channel);
    }

    /**
     * Step 1 of the cancel flow: validates whether an order can be cancelled
     * and returns the available reason options from Lazada.
     *
     * <p>Endpoint: GET /order/reverse/cancel/validate</p>
     *
     * @param channel         Lazada channel credentials
     * @param orderId         Lazada order ID
     * @param orderItemIdList JSON array string of order_item_ids, e.g. ["100827","..."]
     * @return raw Lazada JSON response
     */
    public String cancelValidate(Channel channel, String orderId, String orderItemIdList) {
        Map<String, String> params = new HashMap<>();
        params.put("order_id", orderId);
        params.put("order_item_id_list", orderItemIdList);
        return http.executeGet("/order/reverse/cancel/validate", params, channel);
    }

    /**
     * Step 2 of the cancel flow: submits the cancel request to Lazada.
     *
     * <p>Endpoint: GET /order/reverse/cancel/create</p>
     *
     * @param channel         Lazada channel credentials
     * @param orderId         Lazada order ID
     * @param orderItemIdList JSON array string of order_item_ids, e.g. ["100827","..."]
     * @param reasonId        reason ID selected by staff from the validate step
     * @return raw Lazada JSON response
     */
    public String cancelCreate(Channel channel, String orderId,
                               String orderItemIdList, String reasonId) {
        Map<String, String> params = new HashMap<>();
        params.put("order_id", orderId);
        params.put("order_item_id_list", orderItemIdList);
        params.put("reason_id", reasonId);
        return http.executeGet("/order/reverse/cancel/create", params, channel);
    }
}
