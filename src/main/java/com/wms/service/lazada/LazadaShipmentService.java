package com.wms.service.lazada;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.dao.ChannelDAO;
import com.wms.dao.InventoryDAO;
import com.wms.dao.OrderDAO;
import com.wms.model.Channel;
import com.wms.model.Order;
import com.wms.model.OrderItem;
import com.wms.service.channel.ChannelGateway;
import com.wms.service.channel.ChannelRegistry;
import com.wms.service.channel.ChannelSyncAudit;
import com.wms.service.channel.LazadaChannelGateway;

import java.math.BigDecimal;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * LazadaShipmentService — Lazada fulfillment workflow (UC-B2C04, UC-B2C06).
 *
 * Two-call pipeline triggered when Sales clicks "Generate tracking"
 * on a Lazada order:
 *   1. packAndAllocate(): POST /order/fulfill/pack — Lazada allocates a
 *      package_id + tracking_number. We persist the tracking_no on the
 *      order row and mark is_pack_requested=1.
 *   2. getShippingLabel(): GET /order/package/document/get — Lazada returns
 *      a Base64 PDF. We decode it to a byte[] the servlet streams back
 *      for the browser to print.
 *
 * {@link #readyToShip(Order)} is a separate call (UC-B2C06) invoked from
 * the warehouse "Ready To Ship" button after the box is sealed.
 */
public class LazadaShipmentService {

    private static final Logger LOGGER = Logger.getLogger(LazadaShipmentService.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ChannelGateway gateway;
    private final ChannelDAO channelDAO = new ChannelDAO();
    private final OrderDAO orderDAO = new OrderDAO();
    private final InventoryDAO inventoryDAO = new InventoryDAO();
    private final com.wms.dao.LazadaOrderDAO lazadaOrderDAO = new com.wms.dao.LazadaOrderDAO();

    public LazadaShipmentService() {
        this.gateway = ChannelRegistry.get("Lazada");
    }

    /** Lazada-allocated waybill for an order. */
    public static final class ShipmentResult {
        public final boolean success;
        public final String trackingNo;
        public final String packageId;
        public final String errorMessage;
        private ShipmentResult(boolean s, String tracking, String pkg, String err) {
            this.success = s; this.trackingNo = tracking;
            this.packageId = pkg; this.errorMessage = err;
        }
        public static ShipmentResult ok(String tracking, String pkg) {
            return new ShipmentResult(true, tracking, pkg, null);
        }
        public static ShipmentResult fail(String error) {
            return new ShipmentResult(false, null, null, error);
        }
    }

    /**
     * Calls Lazada pack API. Lazada returns package_id and tracking_number
     * which we persist to the local order row.
     */
    public ShipmentResult packAndAllocate(Order order) {
        Channel ch = resolveChannel(order);
        if (ch == null) {
            return ShipmentResult.fail("Channel not found for order " + order.getOrderCode());
        }
        // Lazada requires order_item_id list. We pull it from the
        // getOrderItems call. If we have no items, fall back to an
        // empty list — Lazada will reject, but the error is useful.
        String itemsJson = "[]";
        try {
            String body = gateway.getOrderItems(ch, order.getOrderCode());
            JsonNode root = MAPPER.readTree(body);
            JsonNode data = root.path("data");
            JsonNode items = data.isArray() ? data : data.path("order_items");
            if (items.isArray() && items.size() > 0) {
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < items.size(); i++) {
                    if (i > 0) sb.append(',');
                    String oiid = items.get(i).path("order_item_id").isMissingNode() ? "" : items.get(i).path("order_item_id").asText();
                    sb.append("\"").append(oiid).append("\"");
                }
                sb.append("]");
                itemsJson = sb.toString();
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING,
                    "packAndAllocate: failed to fetch order items for " + order.getOrderCode(), e);
        }
        Map<String, String> params = new HashMap<>();
        params.put("order_id", order.getOrderCode());
        params.put("delivery_type", "dropship");
        params.put("order_item_list", itemsJson);

        long t0 = System.currentTimeMillis();
        try {
            // Lazada's gateway.packOrder already adds the order_item_list param.
            // We pre-built it; we instead build our own pack call to control the param.
            if (!(gateway instanceof LazadaChannelGateway lazadaGateway)) {
                ChannelSyncAudit.logFailure(ch.getChannelId(), "PACK",
                        order.getOrderCode(), 500, params.toString(),
                        "Gateway is not LazadaChannelGateway — fulfillment not supported");
                return ShipmentResult.fail("Channel gateway is not Lazada — fulfillment not supported");
            }
            String body = lazadaGateway.packOrderWithParams(ch, params);
            long dt = System.currentTimeMillis() - t0;
            LOGGER.info("packAndAllocate raw response: " + body);
            ChannelSyncAudit.logSuccess(ch.getChannelId(), "PACK",
                    order.getOrderCode(), 200, params.toString(), body, dt);

            JsonNode root = MAPPER.readTree(body);
            // Pack response can come in two shapes:
            //   a) {"result": {"data": {...}}, "code": "0"}  ← most common (this app)
            //   b) {"data": {...}, "code": "0"}             ← flat (some Lazada endpoints)
            JsonNode data = root.path("result").path("data");
            if (data.isMissingNode()) {
                data = root.path("data");
            }
            // Pack response: data.pack_order_list[].order_item_list[].package_id / tracking_number
            String packageId   = "";
            String trackingNo  = "";
            JsonNode packOrderList = data.path("pack_order_list");
            if (packOrderList.isArray() && packOrderList.size() > 0) {
                JsonNode firstOrder = packOrderList.get(0);
                JsonNode itemList = firstOrder.path("order_item_list");
                if (itemList.isArray() && itemList.size() > 0) {
                    JsonNode firstItem = itemList.get(0);
                    packageId  = firstItem.path("package_id").asText("");
                    trackingNo = firstItem.path("tracking_number").asText("");
                }
            }
            // Extract shipment_provider from Pack response — this is the canonical carrier name from Lazada
            String shipmentProvider = extractShipmentProvider(data);
            if (packageId.isEmpty() || trackingNo.isEmpty()) {
                // Surface more detail: check per-item error codes
                StringBuilder detail = new StringBuilder();
                if (packOrderList.isArray()) {
                    for (JsonNode orderNode : packOrderList) {
                        JsonNode items = orderNode.path("order_item_list");
                        if (items.isArray()) {
                            for (JsonNode item : items) {
                                String err = item.path("item_err_code").asText("0");
                                String msg = item.path("msg").asText();
                                if (!"0".equals(err) && !msg.isEmpty()) {
                                    if (detail.length() > 0) detail.append("; ");
                                    detail.append("item=").append(item.path("order_item_id").asText())
                                          .append(" err=").append(err).append(" ").append(msg);
                                }
                            }
                        }
                    }
                }
                String err = detail.length() > 0 ? detail.toString()
                    : (root.path("message").isMissingNode() ? "No package_id / tracking_number"
                                                           : root.path("message").asText());
                return ShipmentResult.fail(err);
            }
            // Persist into the orders row + shipping details
            orderDAO.updateLazadaPackage(order.getOrderCode(), packageId, true, false);
            orderDAO.updateOrderTrackingNo(order.getOrderCode(), trackingNo);
            if (shipmentProvider != null && !shipmentProvider.isEmpty()) {
                orderDAO.updateShipmentProvider(order.getOrderCode(), shipmentProvider);
            }
            return ShipmentResult.ok(trackingNo, packageId);
        } catch (Exception e) {
            ChannelSyncAudit.logFailure(ch.getChannelId(), "PACK",
                    order.getOrderCode(), 500, params.toString(), e.getMessage());
            return ShipmentResult.fail(e.getMessage());
        }
    }

    /**
     * Step 3 of the fulfillment pipeline: tell Lazada "carrier has been
     * notified, package is ready to be picked up" (UC-B2C06).
     */
    public ShipmentResult readyToShip(Order order) {
        Channel ch = resolveChannel(order);
        if (ch == null) return ShipmentResult.fail("Channel not found for order " + order.getOrderCode());
        String packageId = order.getLazadaPackageId();
        if (packageId == null || packageId.isEmpty()) {
            return ShipmentResult.fail(
                    "Order " + order.getOrderCode() + " has no Lazada package_id — call pack first");
        }
        long t0 = System.currentTimeMillis();
        try {
            String body = gateway.readyToShip(ch, packageId);
            long dt = System.currentTimeMillis() - t0;
            // Parse RTS response per Lazada Open Platform spec:
            //   code "0" + item_err_code "0" = true success
            //   success="true" in body is NOT sufficient — per-package errors must be checked
            JsonNode root = MAPPER.readTree(body);
            JsonNode result = root.path("result");
            boolean requestOk = "0".equals(root.path("code").asText());
            boolean resultSuccess = result.path("success").asBoolean(false);

            if (!requestOk && !resultSuccess) {
                String msg = result.path("error_msg").asText();
                if (msg.isEmpty()) msg = root.path("message").asText();
                if (msg.isEmpty()) msg = "Lazada từ chối RTS.";
                ChannelSyncAudit.log(ch.getChannelId(), "RTS",
                        order.getOrderCode(), 200, "package_id=" + packageId, null, msg, dt);
                lazadaOrderDAO.insertRtsLog(ch.getChannelId(), order.getOrderId(), order.getOrderCode(), packageId,
                        "FAILED", msg);
                return ShipmentResult.fail(msg);
            }

            // Per-package error check
            JsonNode packages = result.path("data").path("packages");
            if (packages.isArray()) {
                for (JsonNode pkg : packages) {
                    String errCode = pkg.path("item_err_code").asText();
                    if (errCode.isEmpty()) errCode = "0";
                    if (!"0".equals(errCode)) {
                        String pkgId  = pkg.path("package_id").asText();
                        if (pkgId.isEmpty()) pkgId = packageId;
                        String pkgMsg = pkg.path("msg").asText();
                        if (pkgMsg.isEmpty()) pkgMsg = "Lỗi không xác định";
                        String failMsg = "package_id=" + pkgId + " err=" + errCode + " msg=" + pkgMsg;
                        ChannelSyncAudit.log(ch.getChannelId(), "RTS",
                                order.getOrderCode(), 200, failMsg, null, failMsg, dt);
                        lazadaOrderDAO.insertRtsLog(ch.getChannelId(), order.getOrderId(), order.getOrderCode(),
                                packageId, "FAILED", failMsg);
                        return ShipmentResult.fail("Lazada RTS lỗi cho gói " + pkgId
                                + ": [" + errCode + "] " + pkgMsg);
                    }
                }
            }

            // RTS confirmed — update flags, and update lazada_orders table
            ChannelSyncAudit.logSuccess(ch.getChannelId(), "RTS",
                    order.getOrderCode(), 200, "package_id=" + packageId, body, dt);
            orderDAO.updateLazadaPackage(order.getOrderCode(), packageId, true, true);
            lazadaOrderDAO.insertRtsLog(ch.getChannelId(), order.getOrderId(), order.getOrderCode(), packageId,
                    "SUCCESS", body);
            
            // Mark RTS timestamp and wms_status in lazada_orders table
            com.wms.dao.LazadaOrderDAO lazadaOrderDAO = new com.wms.dao.LazadaOrderDAO();
            lazadaOrderDAO.updateRtsAt(order.getOrderCode());
            lazadaOrderDAO.updateStatus(order.getOrderCode(), "HANDED_OVER");

            // Update WMS outbound order status to HANDED_OVER
            com.wms.dao.OutboundDAO outboundDAO = new com.wms.dao.OutboundDAO();
            int outboundId = outboundDAO.findActiveOutboundIdByOrderCode(order.getOrderCode());
            if (outboundId > 0) {
                new com.wms.service.warehouse.OutboundService().updateStatus(outboundId, "HANDED_OVER");
            }
            
            return ShipmentResult.ok(order.getTrackingNo(), packageId);
        } catch (Exception e) {
            ChannelSyncAudit.logFailure(ch.getChannelId(), "RTS",
                    order.getOrderCode(), 500, "package_id=" + packageId, e.getMessage());
            lazadaOrderDAO.insertRtsLog(ch.getChannelId(), order.getOrderId(), order.getOrderCode(), packageId,
                    "FAILED", e.getMessage());
            return ShipmentResult.fail(e.getMessage());
        }
    }

    /** Decodes Lazada's Base64 shipping label into a PDF byte array. */
    public byte[] getShippingLabel(Order order) {
        Channel ch = resolveChannel(order);
        if (ch == null) return null;
        String packageId = order.getLazadaPackageId();
        if (packageId == null || packageId.isEmpty()) {
            throw new IllegalStateException("Order has no package_id; pack first");
        }
        try {
            String body = gateway.getShippingLabel(ch, packageId);
            ChannelSyncAudit.logSuccess(ch.getChannelId(), "LABEL",
                    order.getOrderCode(), 200, "package_id=" + packageId, body, 0L);
            JsonNode root = MAPPER.readTree(body);
            // Response path: result.data.file per Lazada Open Platform spec
            JsonNode dataNode = root.path("result").path("data");
            String fileBase64 = dataNode.path("file").asText();
            
            if (!fileBase64.isEmpty()) {
                byte[] decoded = Base64.getDecoder().decode(fileBase64);
                String decodedStr = new String(decoded, java.nio.charset.StandardCharsets.UTF_8).trim();
                if (decodedStr.startsWith("<iframe") || decodedStr.startsWith("<html") || decodedStr.startsWith("<!DOCTYPE")) {
                    LOGGER.info("Decoded shipping label is HTML/iframe, falling back to pdf_url...");
                    String pdfUrl = dataNode.path("pdf_url").asText();
                    if (!pdfUrl.isEmpty()) {
                        byte[] downloadedPdf = downloadPdf(pdfUrl);
                        if (downloadedPdf != null && downloadedPdf.length > 0) {
                            return downloadedPdf;
                        }
                    }
                } else {
                    return decoded;
                }
            }
            
            // Fallback: check pdf_url directly if file is empty
            String pdfUrl = dataNode.path("pdf_url").asText();
            if (!pdfUrl.isEmpty()) {
                byte[] downloadedPdf = downloadPdf(pdfUrl);
                if (downloadedPdf != null && downloadedPdf.length > 0) {
                    return downloadedPdf;
                }
            }
            
            LOGGER.warning("Lazada getShippingLabel: no valid PDF/file data found for "
                    + order.getOrderCode() + " body=" + body);
            return null;
        } catch (Exception e) {
            ChannelSyncAudit.logFailure(ch.getChannelId(), "LABEL",
                    order.getOrderCode(), 500, "package_id=" + packageId, e.getMessage());
            LOGGER.log(Level.WARNING, "getShippingLabel failed for " + order.getOrderCode(), e);
            return null;
        }
    }

    private byte[] downloadPdf(String pdfUrlString) {
        try {
            java.net.URL url = new java.net.URL(pdfUrlString);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            conn.setUseCaches(false);
            
            int responseCode = conn.getResponseCode();
            if (responseCode == java.net.HttpURLConnection.HTTP_OK) {
                try (java.io.InputStream in = conn.getInputStream();
                     java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                    return out.toByteArray();
                }
            } else {
                LOGGER.warning("downloadPdf failed with response code: " + responseCode + " for URL: " + pdfUrlString);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error downloading PDF from " + pdfUrlString, e);
        }
        return null;
    }

    // ── Helpers ────────────────────────────────────────────────

    private Channel resolveChannel(Order order) {
        int channelId = order.getChannelId();
        if (channelId <= 0) return null;
        return channelDAO.findById(channelId);
    }

    /**
     * Extracts the canonical carrier name from a Pack API response.
     * Lazada returns shipment_provider per item inside pack_order_list.
     * Falls back to top-level data.shipment_provider if present.
     */
    private String extractShipmentProvider(JsonNode data) {
        // Try top-level first (sometimes Lazada puts it here)
        String sp = data.path("shipment_provider").asText();
        if (!sp.isEmpty()) return sp;
        // Try inside pack_order_list
        JsonNode packOrderList = data.path("pack_order_list");
        if (packOrderList.isArray()) {
            for (JsonNode orderNode : packOrderList) {
                JsonNode items = orderNode.path("order_item_list");
                if (items.isArray()) {
                    for (JsonNode item : items) {
                        String itemSp = item.path("shipment_provider").asText();
                        if (!itemSp.isEmpty()) return itemSp;
                    }
                }
            }
        }
        return null;
    }


    /**
     * Deducts physical inventory for every item in the given order (BR-04 final step).
     * Called after Lazada confirms RTS, when the package leaves the warehouse.
     * We deduct from qty_on_hand (physical) and qty_available (after soft-alloc was done earlier).
     */
    private void deductShippedInventoryForOrder(Order order) {
        if (order.getWarehouseId() <= 0) {
            LOGGER.warning("deductShippedInventoryForOrder: order " + order.getOrderCode()
                    + " has no warehouse_id, cannot deduct inventory");
            return;
        }
        var items = orderDAO.findItemsByOrderId(order.getOrderId());
        if (items.isEmpty()) {
            LOGGER.warning("deductShippedInventoryForOrder: no items found for order " + order.getOrderCode());
            return;
        }
        int deducted = 0;
        int failed = 0;
        for (OrderItem item : items) {
            if (item.getProductId() <= 0 || item.getQuantity() <= 0) continue;
            try {
                boolean ok = inventoryDAO.deductShippedInventory(
                        item.getProductId(),
                        order.getWarehouseId(),
                        BigDecimal.valueOf(item.getQuantity()));
                if (ok) {
                    deducted++;
                } else {
                    LOGGER.warning("deductShippedInventoryForOrder: insufficient qty_on_hand for productId="
                            + item.getProductId() + " warehouseId=" + order.getWarehouseId()
                            + " qty=" + item.getQuantity());
                    failed++;
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "deductShippedInventoryForOrder: exception for productId="
                        + item.getProductId(), e);
                failed++;
            }
        }
        LOGGER.info("deductShippedInventoryForOrder: order=" + order.getOrderCode()
                + " warehouseId=" + order.getWarehouseId()
                + " deducted=" + deducted + " failed=" + failed);
    }
}
