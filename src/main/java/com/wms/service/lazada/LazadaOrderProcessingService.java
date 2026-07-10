package com.wms.service.lazada;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.dao.ChannelDAO;
import com.wms.dao.InventoryDAO;
import com.wms.dao.LazadaOrderDAO;
import com.wms.dao.LazadaShipmentProviderDAO;
import com.wms.dao.OrderDAO;
import com.wms.dao.SkuMappingDAO;
import com.wms.model.Channel;
import com.wms.model.LazadaOrder;
import com.wms.model.LazadaOrderItem;
import com.wms.model.LazadaShipmentProvider;
import com.wms.service.channel.ChannelGateway;
import com.wms.service.channel.ChannelRegistry;
import com.wms.service.channel.ChannelSyncAudit;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * LazadaOrderProcessingService — WMS workflow for Lazada orders.
 *
 * <p>Implements the end-to-end WMS lifecycle after an order has been synced
 * from Lazada API:</p>
 *
 * <ol>
 *   <li><b>approveOrder:</b> validates stock, soft-allocates, updates to APPROVED</li>
 *   <li><b>packOrder:</b> calls Lazada Pack API, persists package_id + tracking_number</li>
 *   <li><b>markRts:</b> calls Lazada RTS API, deducts shipped inventory, updates to HANDED_OVER</li>
 *   <li><b>cancelOrder:</b> releases holding inventory, updates to CANCELED</li>
 * </ol>
 *
 * <p>Stock validation happens against {@code qty_available} (ATP without inbound buffer)
 * to prevent approving orders against stock that hasn't arrived yet.</p>
 */
public class LazadaOrderProcessingService {

    private static final Logger LOGGER = Logger.getLogger(LazadaOrderProcessingService.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final LazadaOrderDAO orderDAO = new LazadaOrderDAO();
    private final OrderDAO legacyOrderDAO = new OrderDAO();
    private final InventoryDAO inventoryDAO = new InventoryDAO();
    private final ChannelDAO channelDAO = new ChannelDAO();
    private final LazadaShipmentProviderDAO providerDAO = new LazadaShipmentProviderDAO();
    private final SkuMappingDAO skuMappingDAO = new SkuMappingDAO();
    private final ShipmentProviderMappingService providerMapping = new ShipmentProviderMappingService();

    // ══ INNER RESULT TYPE ═══════════════════════════════════════════════════════

    /**
     * Encapsulates the outcome of a workflow step.
     */
    public static class ProcessingResult {
        public final boolean success;
        public final String message;
        public final Map<String, Object> data;

        private ProcessingResult(boolean success, String message, Map<String, Object> data) {
            this.success = success;
            this.message = message;
            this.data = data != null ? data : new HashMap<>();
        }

        public static ProcessingResult ok(String message) {
            return new ProcessingResult(true, message, null);
        }

        public static ProcessingResult ok(String message, Map<String, Object> data) {
            return new ProcessingResult(true, message, data);
        }

        public static ProcessingResult fail(String message) {
            return new ProcessingResult(false, message, null);
        }

        public static ProcessingResult fail(String message, Map<String, Object> data) {
            return new ProcessingResult(false, message, data);
        }
    }

    // ══ APPROVE ════════════════════════════════════════════════════════════════

    /**
     * Approves a Lazada order: validates stock, soft-allocates, assigns warehouse.
     *
     * <p>Steps:</p>
     * <ol>
     *   <li>Load order + items</li>
     *   <li>Resolve SKU mappings for each item</li>
     *   <li>Validate qty_available for each item at the chosen warehouse</li>
     *   <li>Call {@link InventoryDAO#softAllocateInventory} for each item</li>
     *   <li>Update order status to APPROVED with warehouse assignment</li>
     * </ol>
     *
     * @param lazadaOrderIdStr      The Lazada order ID string.
     * @param warehouseId            The warehouse to assign the order to.
     * @param userId                The user performing the approval.
     * @param shipmentProviderCode   The carrier code (e.g. "FM49"). Optional.
     * @return ProcessingResult indicating success or failure with details.
     */
    public ProcessingResult approveOrder(String lazadaOrderIdStr, int warehouseId,
                                         int userId, String shipmentProviderCode) {
        LOGGER.info("approveOrder: orderId=" + lazadaOrderIdStr + " warehouseId=" + warehouseId);

        // 1. Load order
        LazadaOrder order = orderDAO.findByLazadaOrderIdStr(lazadaOrderIdStr);
        if (order == null) {
            return ProcessingResult.fail("Không tìm thấy đơn hàng: " + lazadaOrderIdStr);
        }

        // 2. Validate current WMS status
        if (!"NEW".equals(order.getWmsStatus())) {
            return ProcessingResult.fail("Đơn hàng hiện ở trạng thái [" + order.getWmsStatus()
                    + "], chỉ có thể duyệt đơn ở trạng thái [NEW].");
        }

        // 3. Load items
        List<LazadaOrderItem> items = orderDAO.findItemsByLazadaOrderIdStr(lazadaOrderIdStr);
        if (items.isEmpty()) {
            return ProcessingResult.fail("Đơn hàng không có sản phẩm nào.");
        }

        // 4. Validate stock and soft-allocate atomically
        int allocatedCount = 0;
        int insufficientCount = 0;
        StringBuilder insufficientItems = new StringBuilder();

        for (LazadaOrderItem item : items) {
            // Resolve WMS product_id via SKU mapping
            int productId = resolveProductId(item, order.getChannelId());
            item.setProductId(productId);

            if (productId <= 0) {
                LOGGER.warning("approveOrder: SKU not mapped orderId=" + lazadaOrderIdStr
                        + " sku=" + item.getSku() + " channelId=" + order.getChannelId());
                insufficientItems.append("- SKU ")
                        .append(item.getSku())
                        .append(": chưa được ánh xạ với sản phẩm WMS.\n");
                insufficientCount++;
                continue;
            }

            int available = inventoryDAO.getAvailableStock(productId, warehouseId);
            LOGGER.info("approveOrder: checking orderId=" + lazadaOrderIdStr
                    + " sku=" + item.getSku() + " productId=" + productId
                    + " warehouseId=" + warehouseId + " available=" + available
                    + " needed=" + item.getQuantity());
            if (available < item.getQuantity()) {
                insufficientItems.append("- SKU ")
                        .append(item.getSku())
                        .append(" (").append(item.getProductName()).append("): ")
                        .append("cần ").append(item.getQuantity())
                        .append(", kho chỉ còn ").append(available).append(".\n");
                insufficientCount++;
                continue;
            }

            // Soft-allocate
            boolean ok = inventoryDAO.softAllocateInventory(
                    productId, warehouseId, item.getQuantity());
            if (ok) {
                allocatedCount++;
                // Update reserved_qty on the item row
                orderDAO.updateItemReservedQty(lazadaOrderIdStr, item.getQuantity());
            } else {
                insufficientItems.append("- SKU ")
                        .append(item.getSku())
                        .append(": không thể giữ chỗ tồn kho (lỗi hệ thống).\n");
                insufficientCount++;
            }
        }

        if (insufficientCount > 0) {
            // Rollback any allocations we already made
            rollbackAllocations(items, warehouseId);
            return ProcessingResult.fail(
                    "Không đủ tồn kho cho " + insufficientCount + " sản phẩm:\n"
                            + insufficientItems,
                    Map.of("insufficientCount", insufficientCount,
                            "allocatedCount", allocatedCount));
        }

        // 5. Resolve provider code and persist assignment
        String resolvedProviderCode = providerMapping.resolveProviderCode(shipmentProviderCode);
        boolean updated;
        if (resolvedProviderCode != null && !resolvedProviderCode.isEmpty()) {
            updated = orderDAO.updateAssignWarehouseAndProvider(
                    lazadaOrderIdStr, warehouseId, userId, resolvedProviderCode);
        } else {
            updated = orderDAO.updateAssignWarehouse(lazadaOrderIdStr, warehouseId, userId);
        }
        if (!updated) {
            rollbackAllocations(items, warehouseId);
            return ProcessingResult.fail("Không thể cập nhật kho cho đơn hàng.");
        }

        // 6. Update status to APPROVED
        boolean statusUpdated = orderDAO.updateStatus(lazadaOrderIdStr, "APPROVED");
        if (!statusUpdated) {
            rollbackAllocations(items, warehouseId);
            return ProcessingResult.fail("Không thể cập nhật trạng thái đơn hàng.");
        }

        LOGGER.info("approveOrder: success orderId=" + lazadaOrderIdStr
                + " warehouseId=" + warehouseId + " providerCode=" + resolvedProviderCode + " allocated=" + allocatedCount);

        return ProcessingResult.ok("Duyệt đơn hàng thành công. Kho: " + warehouseId,
                Map.of("wmsStatus", "APPROVED",
                        "warehouseId", warehouseId,
                        "providerCode", resolvedProviderCode != null ? resolvedProviderCode : "",
                        "allocatedCount", allocatedCount));
    }

    private void rollbackAllocations(List<LazadaOrderItem> items, int warehouseId) {
        for (LazadaOrderItem item : items) {
            if (item.getProductId() <= 0 || item.getReservedQty() <= 0) continue;
            try {
                inventoryDAO.releaseSoftAllocateInventory(
                        item.getProductId(), warehouseId,
                        BigDecimal.valueOf(item.getReservedQty()));
            } catch (Exception e) {
                LOGGER.log(Level.WARNING,
                        "rollbackAllocations: failed for productId=" + item.getProductId(), e);
            }
        }
    }

    /**
     * Resolves a WMS product_id from the SKU mapping table.
     */
    private int resolveProductId(LazadaOrderItem item, int channelId) {
        if (item.getSku() == null || item.getSku().isEmpty()) return 0;
        try {
            // Try SYNCED mapping first, then any mapping
            var mapping = skuMappingDAO.findActiveMapping(channelId, item.getSku());
            if (mapping == null) {
                mapping = skuMappingDAO.findMappingByChannelAndExternalSku(channelId, item.getSku());
            }
            if (mapping != null && mapping.getSkuId() > 0) {
                return mapping.getSkuId();
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING,
                    "resolveProductId: SKU mapping lookup failed for " + item.getSku(), e);
        }
        return 0;
    }

    // ══ PACK ════════════════════════════════════════════════════════════════════

    /**
     * Calls Lazada's Pack API for the order and persists the package_id + tracking_number.
     *
     * <p>After a successful pack call:</p>
     * <ul>
     *   <li>package_id and tracking_number are saved to lazada_orders</li>
     *   <li>WMS status is updated to PACKED</li>
     * </ul>
     *
     * @param lazadaOrderIdStr The Lazada order ID.
     * @return ProcessingResult with packageId and trackingNumber on success.
     */
    public ProcessingResult packOrder(String lazadaOrderIdStr) {
        LOGGER.info("packOrder: orderId=" + lazadaOrderIdStr);

        LazadaOrder order = orderDAO.findByLazadaOrderIdStr(lazadaOrderIdStr);
        if (order == null) {
            return ProcessingResult.fail("Không tìm thấy đơn hàng: " + lazadaOrderIdStr);
        }

        if (!"APPROVED".equals(order.getWmsStatus())) {
            return ProcessingResult.fail("Đơn hàng hiện ở trạng thái [" + order.getWmsStatus()
                    + "], cần ở trạng thái [APPROVED] để đóng gói.");
        }

        Channel channel = channelDAO.findById(order.getChannelId());
        if (channel == null) {
            return ProcessingResult.fail("Không tìm thấy cấu hình kênh cho đơn hàng.");
        }

        // Resolve carrier code from WMS provider code stored on the order
        String providerCode = order.getShipmentProviderCode();
        if (providerCode == null || providerCode.isEmpty()) {
            // Default to first active provider
            List<LazadaShipmentProvider> providers = providerDAO.findAllActive();
            if (!providers.isEmpty()) {
                providerCode = providers.get(0).getProviderCode();
            }
        }

        try {
            ChannelGateway gateway = ChannelRegistry.get("Lazada");
            if (gateway == null) {
                return ProcessingResult.fail("Không tìm được Lazada gateway.");
            }

            // Build order item IDs list
            List<LazadaOrderItem> items = orderDAO.findItemsByLazadaOrderIdStr(lazadaOrderIdStr);
            StringBuilder itemListJson = new StringBuilder("[");
            for (int i = 0; i < items.size(); i++) {
                if (i > 0) itemListJson.append(',');
                itemListJson.append('"').append(items.get(i).getOrderItemId()).append('"');
            }
            itemListJson.append(']');

            Map<String, String> params = new HashMap<>();
            params.put("order_id", lazadaOrderIdStr);
            params.put("delivery_type", "dropship");
            params.put("order_item_list", itemListJson.toString());
            if (providerCode != null && !providerCode.isEmpty()) {
                params.put("shipment_provider_code", providerCode);
            }

            String body;
            if (gateway instanceof com.wms.service.channel.LazadaChannelGateway lcg) {
                body = lcg.packOrderWithParams(channel, params);
            } else {
                body = gateway.packOrder(channel, lazadaOrderIdStr, "dropship");
            }

            ChannelSyncAudit.logSuccess(channel.getChannelId(), "PACK",
                    lazadaOrderIdStr, 200, params.toString(), body, 0L);

            JsonNode root = MAPPER.readTree(body);
            JsonNode result = root.path("result");
            boolean requestOk = "0".equals(root.path("code").asText());
            boolean resultSuccess = result.path("success").asBoolean(false);

            // Check batch-level and per-item errors before trusting any package_id/tracking
            if (!requestOk || !resultSuccess) {
                String errCode = result.path("error_code").asText();
                String errMsg = result.path("error_msg").asText();
                if (errMsg.isEmpty()) errMsg = root.path("message").asText();
                if (errMsg.isEmpty()) errMsg = "Lazada từ chối đóng gói.";
                if (!errCode.isEmpty() && !"0".equals(errCode)) {
                    errMsg = "Lazada E" + errCode + ": " + errMsg;
                }
                ChannelSyncAudit.logFailure(channel.getChannelId(), "PACK",
                        lazadaOrderIdStr, 200, params.toString(), errMsg);
                return ProcessingResult.fail(errMsg);
            }

            // Per-item error check
            JsonNode packOrderList = result.path("data").path("pack_order_list");
            List<String> failedItems = new java.util.ArrayList<>();
            String packageId = "";
            String trackingNumber = "";

            if (packOrderList.isArray()) {
                for (JsonNode orderNode : packOrderList) {
                    JsonNode itemList = orderNode.path("order_item_list");
                    if (!itemList.isArray()) continue;
                    for (JsonNode item : itemList) {
                        String errCode = item.path("item_err_code").asText();
                        if (errCode.isEmpty()) errCode = "0";
                        if (!"0".equals(errCode)) {
                            String itemId = item.path("order_item_id").asText();
                            String itemMsg = item.path("msg").asText();
                            if (itemMsg.isEmpty()) itemMsg = "Lỗi không xác định";
                            failedItems.add("item=" + itemId + " err=" + errCode + " msg=" + itemMsg);
                        } else {
                            // First successful item gives us package_id + tracking + shipment_provider
                            if (packageId.isEmpty()) {
                                packageId = item.path("package_id").asText();
                                trackingNumber = item.path("tracking_number").asText();
                                // shipment_provider is the canonical carrier name returned by Lazada — save it
                                String sp = item.path("shipment_provider").asText();
                                if (!sp.isEmpty()) {
                                    orderDAO.updateTrackingInfo(lazadaOrderIdStr, packageId, trackingNumber, sp);
                                } else {
                                    orderDAO.updateTrackingInfo(lazadaOrderIdStr, packageId, trackingNumber, providerCode);
                                }
                                if (!packageId.isEmpty() && !trackingNumber.isEmpty()) break;
                            }
                        }
                    }
                    if (!packageId.isEmpty() && !trackingNumber.isEmpty()) break;
                }
            }

            if (!failedItems.isEmpty()) {
                String failMsg = "Lazada pack lỗi: " + String.join("; ", failedItems);
                ChannelSyncAudit.logFailure(channel.getChannelId(), "PACK",
                        lazadaOrderIdStr, 200, params.toString(), failMsg);
                return ProcessingResult.fail(failMsg);
            }

            // Persist tracking info
            orderDAO.updateTrackingInfo(lazadaOrderIdStr, packageId, trackingNumber, providerCode);
            orderDAO.updateStatus(lazadaOrderIdStr, "PACKED");

            LOGGER.info("packOrder: success orderId=" + lazadaOrderIdStr
                    + " packageId=" + packageId + " tracking=" + trackingNumber);

            return ProcessingResult.ok(
                    "Đóng gói thành công. Mã vận đơn: " + trackingNumber,
                    Map.of("packageId", packageId,
                            "trackingNumber", trackingNumber,
                            "providerCode", providerCode != null ? providerCode : ""));

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "packOrder: failed for " + lazadaOrderIdStr, e);
            ChannelSyncAudit.logFailure(channel.getChannelId(), "PACK",
                    lazadaOrderIdStr, 500, null, e.getMessage());
            return ProcessingResult.fail("Lỗi khi gọi API đóng gói Lazada: " + e.getMessage());
        }
    }

    /**
     * Batch version of packOrder: processes multiple orders sequentially.
     * Returns individual results for each order so the caller can report partial success.
     *
     * @param lazadaOrderIdStrs List of order IDs to pack.
     * @return ProcessingResult where data contains a list of per-order results.
     */
    public ProcessingResult packOrders(List<String> lazadaOrderIdStrs) {
        if (lazadaOrderIdStrs == null || lazadaOrderIdStrs.isEmpty()) {
            return ProcessingResult.fail("Danh sách đơn hàng trống.");
        }

        List<Map<String, Object>> results = new java.util.ArrayList<>();
        int successCount = 0;
        int failCount = 0;
        StringBuilder errors = new StringBuilder();

        for (String orderId : lazadaOrderIdStrs) {
            ProcessingResult r = packOrder(orderId);
            Map<String, Object> itemResult = new java.util.LinkedHashMap<>();
            itemResult.put("orderId", orderId);
            itemResult.put("success", r.success);
            itemResult.put("message", r.message);
            if (r.data != null) {
                itemResult.putAll(r.data);
            }
            results.add(itemResult);
            if (r.success) {
                successCount++;
            } else {
                failCount++;
                if (errors.length() > 0) errors.append("; ");
                errors.append(orderId).append(": ").append(r.message);
            }
        }

        String summary = String.format("Hoàn tất: %d thành công, %d thất bại.",
                successCount, failCount);
        if (failCount > 0) {
            summary += " " + errors;
        }

        return new ProcessingResult(true, summary,
                Map.of("results", results,
                        "successCount", successCount,
                        "failCount", failCount));
    }

    // ══ RTS ═════════════════════════════════════════════════════════════════════

    /**
     * Calls Lazada's Ready-To-Ship API and deducts shipped inventory.
     *
     * <p>After a successful RTS call:</p>
     * <ul>
     *   <li>WMS status is updated to HANDED_OVER</li>
     *   <li>Physical inventory is deducted (qty_on_hand) for each item</li>
     *   <li>rts_at timestamp is recorded</li>
     * </ul>
     *
     * @param lazadaOrderIdStr The Lazada order ID.
     * @return ProcessingResult with RTS confirmation on success.
     */
    public ProcessingResult markRts(String lazadaOrderIdStr) {
        LOGGER.info("markRts: orderId=" + lazadaOrderIdStr);

        LazadaOrder order = orderDAO.findByLazadaOrderIdStr(lazadaOrderIdStr);
        if (order == null) {
            return ProcessingResult.fail("Không tìm thấy đơn hàng: " + lazadaOrderIdStr);
        }

        if (!"PACKED".equals(order.getWmsStatus())) {
            return ProcessingResult.fail("Đơn hàng hiện ở trạng thái [" + order.getWmsStatus()
                    + "], cần ở trạng thái [PACKED] để bàn giao.");
        }

        String packageId = order.getPackageId();
        if (packageId == null || packageId.isEmpty()) {
            return ProcessingResult.fail(
                    "Đơn hàng chưa được đóng gói (chưa có package_id). Vui lòng gọi [Đóng gói] trước.");
        }

        Channel channel = channelDAO.findById(order.getChannelId());
        if (channel == null) {
            return ProcessingResult.fail("Không tìm thấy cấu hình kênh.");
        }

        try {
            ChannelGateway gateway = ChannelRegistry.get("Lazada");
            if (gateway == null) {
                return ProcessingResult.fail("Không tìm được Lazada gateway.");
            }

            String body = gateway.readyToShip(channel, packageId);

            JsonNode root = MAPPER.readTree(body);
            JsonNode result = root.path("result");

            boolean requestOk = "0".equals(root.path("code").asText());
            boolean resultSuccess = result.path("success").asBoolean(false);

            if (!requestOk && !resultSuccess) {
                String msg = result.path("error_msg").asText();
                if (msg.isEmpty()) {
                    msg = root.path("message").asText();
                }
                if (msg.isEmpty()) {
                    msg = "Lazada từ chối RTS.";
                }
                ChannelSyncAudit.logFailure(channel.getChannelId(), "RTS",
                        lazadaOrderIdStr, 200, "package_id=" + packageId, msg);
                return ProcessingResult.fail(msg);
            }

            // Check per-package errors (e.g. "package already cancelled")
            JsonNode packages = result.path("data").path("packages");
            List<String> failedPackages = new java.util.ArrayList<>();
            StringBuilder errMsg = new StringBuilder();
            if (packages.isArray()) {
                for (JsonNode pkg : packages) {
                    String errCode = pkg.path("item_err_code").asText();
                    if (errCode.isEmpty()) errCode = "0";
                    if (!"0".equals(errCode) && !"true".equalsIgnoreCase(resultSuccess ? "true" : "")) {
                        String pkgId = pkg.path("package_id").asText();
                        if (pkgId.isEmpty()) pkgId = packageId;
                        String pkgMsg = pkg.path("msg").asText();
                        if (pkgMsg.isEmpty()) pkgMsg = "Lỗi không xác định";
                        failedPackages.add(pkgId + "(" + errCode + ": " + pkgMsg + ")");
                        errMsg.append(pkgMsg).append("; ");
                    }
                }
            }

            if (!failedPackages.isEmpty()) {
                String msg = "Lỗi gói hàng: " + String.join(", ", failedPackages);
                ChannelSyncAudit.logFailure(channel.getChannelId(), "RTS",
                        lazadaOrderIdStr, 200, "package_id=" + packageId, msg);
                return ProcessingResult.fail(msg);
            }

            // Persist RTS timestamp and update status
            orderDAO.updateRtsAt(lazadaOrderIdStr);
            orderDAO.updateStatus(lazadaOrderIdStr, "HANDED_OVER");

            // Deduct shipped inventory (BR-04)
            deductShippedInventory(order);

            ChannelSyncAudit.logSuccess(channel.getChannelId(), "RTS",
                    lazadaOrderIdStr, 200, "package_id=" + packageId, body, 0L);

            LOGGER.info("markRts: success orderId=" + lazadaOrderIdStr + " packageId=" + packageId);

            return ProcessingResult.ok(
                    "Bàn giao ĐVVC thành công. Đơn hàng đang được vận chuyển.",
                    Map.of("packageId", packageId,
                            "trackingNumber", order.getTrackingNumber() != null ? order.getTrackingNumber() : ""));

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "markRts: failed for " + lazadaOrderIdStr, e);
            ChannelSyncAudit.logFailure(channel.getChannelId(), "RTS",
                    lazadaOrderIdStr, 500, "package_id=" + packageId, e.getMessage());
            return ProcessingResult.fail("Lỗi khi gọi API RTS Lazada: " + e.getMessage());
        }
    }

    /**
     * Notifies Lazada of an order cancellation via the Cancel API,
     * then releases soft-allocated inventory and updates WMS status.
     *
     * <p>Lazada's cancel endpoint: POST /order/cancel</p>
     *
     * <p>Cancellation is only possible for orders in NEW, APPROVED, or PACKED status
     * (not yet HANDED_OVER or beyond).</p>
     *
     * @param lazadaOrderIdStr The Lazada order ID.
     * @param reasonId        Lazada reason_id (1=buyer, 2=seller, 3=system, 4=other)
     * @param reasonText      Human-readable reason for audit log.
     * @return ProcessingResult indicating success or failure.
     */
    public ProcessingResult cancelOrderOnLazada(String lazadaOrderIdStr, String reasonId, String reasonText) {
        LOGGER.info("cancelOrderOnLazada: orderId=" + lazadaOrderIdStr + " reason=" + reasonText);

        LazadaOrder order = orderDAO.findByLazadaOrderIdStr(lazadaOrderIdStr);
        if (order == null) {
            return ProcessingResult.fail("Không tìm thấy đơn hàng: " + lazadaOrderIdStr);
        }

        String currentStatus = order.getWmsStatus();
        if ("HANDED_OVER".equals(currentStatus) || "SHIPPING".equals(currentStatus)
                || "DELIVERED".equals(currentStatus)) {
            return ProcessingResult.fail(
                    "Đơn hàng đã bàn giao hoặc đang vận chuyển. Không thể hủy.");
        }

        Channel channel = channelDAO.findById(order.getChannelId());
        if (channel == null) {
            return ProcessingResult.fail("Không tìm thấy cấu hình kênh.");
        }

        try {
            ChannelGateway gateway = ChannelRegistry.get("Lazada");
            if (gateway == null || !(gateway instanceof com.wms.service.channel.LazadaChannelGateway lcg)) {
                return ProcessingResult.fail("Không tìm được Lazada gateway.");
            }

            // Call Lazada cancel API
            String body = lcg.cancelOrder(channel, lazadaOrderIdStr, reasonId);

            JsonNode root = MAPPER.readTree(body);
            String code = root.path("code").asText();

            if (!"0".equals(code)) {
                String msg = root.path("message").asText();
                if (msg.isEmpty()) msg = "Lazada từ chối hủy đơn.";
                ChannelSyncAudit.logFailure(channel.getChannelId(), "CANCEL",
                        lazadaOrderIdStr, 200, "reasonId=" + reasonId, msg);
                // Fall through to local cancel (WMS status) even if Lazada fails
                LOGGER.warning("cancelOrderOnLazada: Lazada rejected cancel for "
                        + lazadaOrderIdStr + " — " + msg);
            } else {
                ChannelSyncAudit.logSuccess(channel.getChannelId(), "CANCEL",
                        lazadaOrderIdStr, 200, "reasonId=" + reasonId + " reason=" + reasonText, body, 0L);
            }

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "cancelOrderOnLazada: API call failed for " + lazadaOrderIdStr, e);
            // Proceed with local cancel even if Lazada API call fails
        }

        // Release holding inventory
        if ("APPROVED".equals(currentStatus) || "PACKED".equals(currentStatus)) {
            releaseAllocations(order);
        }

        // Update both tables for consistency
        orderDAO.updateStatus(lazadaOrderIdStr, "CANCELED");
        legacyOrderDAO.updateOrderStatus(lazadaOrderIdStr, "CANCELED");
        // Also cancel fulfillment request so warehouse staff doesn't see it
        com.wms.dao.FulfillmentRequestDAO frDAO = new com.wms.dao.FulfillmentRequestDAO();
        frDAO.cancelByOrderId(lazadaOrderIdStr);
        // Also cancel outbound orders so warehouse staff doesn't see it
        com.wms.dao.OutboundDAO outboundDAO = new com.wms.dao.OutboundDAO();
        outboundDAO.cancelByOrderId(lazadaOrderIdStr);
        LOGGER.info("cancelOrderOnLazada: success orderId=" + lazadaOrderIdStr);

        return ProcessingResult.ok("Đơn hàng đã được hủy trên WMS."
                + (channel != null ? " Đã thông báo hủy cho Lazada." : ""));
    }

    private void deductShippedInventory(LazadaOrder order) {
        if (order.getWarehouseId() <= 0) {
            LOGGER.warning("deductShippedInventory: order " + order.getLazadaOrderIdStr()
                    + " has no warehouse — skipping");
            return;
        }

        List<LazadaOrderItem> items = orderDAO.findItemsByLazadaOrderIdStr(order.getLazadaOrderIdStr());
        int deducted = 0;
        int failed = 0;

        for (LazadaOrderItem item : items) {
            if (item.getProductId() <= 0 || item.getQuantity() <= 0) continue;
            try {
                boolean ok = inventoryDAO.deductShippedInventory(
                        item.getProductId(),
                        order.getWarehouseId(),
                        BigDecimal.valueOf(item.getQuantity()));
                if (ok) {
                    deducted++;
                    orderDAO.updateItemFulfilledQty(order.getLazadaOrderIdStr(), item.getQuantity());
                } else {
                    failed++;
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING,
                        "deductShippedInventory: failed for productId=" + item.getProductId(), e);
                failed++;
            }
        }

        LOGGER.info("deductShippedInventory: orderId=" + order.getLazadaOrderIdStr()
                + " deducted=" + deducted + " failed=" + failed);
    }

    /**
     * Cancels a Lazada order: notifies Lazada, releases soft-allocated inventory and updates status.
     *
     * <p>Calls Lazada Cancel API (POST /order/cancel) before updating local status.</p>
     *
     * @param lazadaOrderIdStr The Lazada order ID.
     * @return ProcessingResult indicating success or failure.
     */
    public ProcessingResult cancelOrder(String lazadaOrderIdStr) {
        return cancelOrderOnLazada(lazadaOrderIdStr, "2", "Seller cancelled");
    }

    private void releaseAllocations(LazadaOrder order) {
        if (order.getWarehouseId() <= 0) return;

        List<LazadaOrderItem> items = orderDAO.findItemsByLazadaOrderIdStr(order.getLazadaOrderIdStr());
        for (LazadaOrderItem item : items) {
            if (item.getProductId() <= 0 || item.getReservedQty() <= 0) continue;
            try {
                inventoryDAO.releaseSoftAllocateInventory(
                        item.getProductId(),
                        order.getWarehouseId(),
                        BigDecimal.valueOf(item.getReservedQty()));
                orderDAO.updateItemReservedQty(order.getLazadaOrderIdStr(), 0);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING,
                        "releaseAllocations: failed for productId=" + item.getProductId(), e);
            }
        }
    }

    // ══ STOCK VALIDATION ═══════════════════════════════════════════════════════

    /**
     * Validates whether a warehouse has sufficient stock for all items in an order.
     * Does NOT perform allocation — only checks.
     *
     * @param lazadaOrderIdStr The Lazada order ID.
     * @param warehouseId       The warehouse to check against.
     * @return Map with "sufficient" (boolean) and "details" (list of insufficient items).
     */
    public Map<String, Object> validateStockForApproval(String lazadaOrderIdStr, int warehouseId) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> details = new java.util.ArrayList<>();

        LazadaOrder order = orderDAO.findByLazadaOrderIdStr(lazadaOrderIdStr);
        if (order == null) {
            result.put("sufficient", false);
            result.put("details", details);
            result.put("error", "Không tìm thấy đơn hàng");
            return result;
        }

        List<LazadaOrderItem> items = orderDAO.findItemsByLazadaOrderIdStr(lazadaOrderIdStr);
        boolean allSufficient = true;

        for (LazadaOrderItem item : items) {
            int productId = resolveProductId(item, order.getChannelId());
            item.setProductId(productId);

            int available = (productId > 0)
                    ? inventoryDAO.getAvailableStock(productId, warehouseId)
                    : 0;

            boolean sufficient = available >= item.getQuantity();

            Map<String, Object> detail = new HashMap<>();
            detail.put("sku", item.getSku());
            detail.put("productName", item.getProductName());
            detail.put("required", item.getQuantity());
            detail.put("available", available);
            detail.put("sufficient", sufficient);
            detail.put("productId", productId);
            details.add(detail);

            if (!sufficient) allSufficient = false;
        }

        result.put("sufficient", allSufficient);
        result.put("details", details);
        return result;
    }

    // ══ GET ORDER WITH ITEMS ════════════════════════════════════════════════════

    /**
     * Loads a LazadaOrder and its items from the database.
     *
     * @param lazadaOrderIdStr The Lazada order ID.
     * @return Map with "order" and "items" keys.
     */
    public Map<String, Object> getOrderWithItems(String lazadaOrderIdStr) {
        Map<String, Object> result = new HashMap<>();
        LazadaOrder order = orderDAO.findByLazadaOrderIdStr(lazadaOrderIdStr);
        if (order != null) {
            List<LazadaOrderItem> items = orderDAO.findItemsByLazadaOrderIdStr(lazadaOrderIdStr);
            order.setItems(items);
        }
        result.put("order", order);
        result.put("items", order != null ? order.getItems() : List.of());
        return result;
    }
}
