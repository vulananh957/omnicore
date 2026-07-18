package com.wms.service;

import com.wms.dao.OrderDAO;
import com.wms.dao.OutboundDAO;
import com.wms.model.Order;
import com.wms.model.Channel;
import com.wms.model.OutboundOrder;
import com.wms.service.channel.ChannelGateway;
import com.wms.service.channel.ChannelRegistry;
import com.wms.service.lazada.LazadaOrderProcessingService;
import com.wms.service.lazada.LazadaOrderProcessingService.ProcessingResult;
import com.wms.service.lazada.LazadaShipmentService;
import com.wms.service.lazada.LazadaShipmentService.ShipmentResult;
import com.wms.util.DBConnection;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class LazadaWorkflowTest {

    private ChannelGateway originalLazadaGateway;
    private MockLazadaGateway mockLazadaGateway;
    private Channel testChannel;

    @BeforeAll
    public void setupAll() throws Exception {
        new com.wms.listener.SchemaInitListener().contextInitialized(null);

        originalLazadaGateway = ChannelRegistry.get("Lazada");
        mockLazadaGateway = new MockLazadaGateway();
        ChannelRegistry.register("Lazada", mockLazadaGateway);

        // Insert static dependencies
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                // Check if warehouse exists or insert
                stmt.execute("INSERT IGNORE INTO warehouses (warehouse_id, warehouse_code, warehouse_name, address) " +
                        "VALUES (99, 'WH-99', 'Test Warehouse', 'Test Address')");
                
                // Insert channel
                stmt.execute("INSERT IGNORE INTO channels (channel_id, platform, channel_name, api_key, app_secret, access_token) " +
                        "VALUES (99, 'Lazada', 'Test Lazada Channel', 'key', 'secret', 'token')");
                
                // Insert user/customer
                stmt.execute("INSERT IGNORE INTO users (user_id, username, password_hash, full_name, role) " +
                        "VALUES (99, 'test_wms_user', 'pwd', 'Test WMS User', 'WAREHOUSE_STAFF')");
                
                // Insert product
                stmt.execute("INSERT IGNORE INTO products (product_id, sku_code, product_name, category_id, base_price) " +
                        "VALUES (99, 'SKU-TEST-LAZADA', 'Test Product', NULL, 100.00)");
                
                // Insert SKU mapping
                stmt.execute("INSERT IGNORE INTO sku_mappings (sku_id, channel_id, external_sku, sync_status) " +
                        "VALUES (99, 99, 'SKU-TEST-LAZADA', 'SYNCED')");
                
                // Insert inventory
                stmt.execute("INSERT IGNORE INTO inventory (warehouse_id, product_id, qty_on_hand, qty_available) " +
                        "VALUES (99, 99, 10, 10)");
            }
            conn.commit();
        }

        testChannel = new com.wms.dao.ChannelDAO().findById(99);
    }

    @AfterAll
    public void tearDownAll() throws Exception {
        ChannelRegistry.register("Lazada", originalLazadaGateway);

        // Delete dependencies in reverse order
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM sku_mappings WHERE sku_id = 99 AND channel_id = 99");
                stmt.execute("DELETE FROM inventory WHERE warehouse_id = 99 AND product_id = 99");
                stmt.execute("DELETE FROM products WHERE product_id = 99");
                stmt.execute("DELETE FROM users WHERE user_id = 99");
                stmt.execute("DELETE FROM channels WHERE channel_id = 99");
                stmt.execute("DELETE FROM warehouses WHERE warehouse_id = 99");
            }
            conn.commit();
        }
    }

    @BeforeEach
    public void setupEach() throws Exception {
        cleanupTestData();
        
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                // Insert lazada_orders (NEW)
                stmt.execute("INSERT INTO lazada_orders (lazada_order_id_str, channel_id, wms_status, customer_name, price) " +
                        "VALUES ('LAZ-999-TEST', 99, 'NEW', 'Customer 99', 120.00)");
                
                // Insert lazada_order_items
                stmt.execute("INSERT INTO lazada_order_items (order_item_id, lazada_order_id_str, sku, quantity, item_price, paid_price) " +
                        "VALUES ('ITEM-LAZ-999', 'LAZ-999-TEST', 'SKU-TEST-LAZADA', 1, 120.00, 120.00)");
                
                // Insert orders (main sales order table)
                stmt.execute("INSERT INTO orders (order_id, order_code, channel_id, channel_order_id, warehouse_id, status) " +
                        "VALUES (999, 'LAZ-999-TEST', 99, 'LAZ-999-TEST', 99, 'PENDING')");
                
                // Insert order_items
                stmt.execute("INSERT INTO order_items (order_id, product_id, qty, unit_price) " +
                        "VALUES (999, 99, 1, 120.00)");
                
                // Insert order shipping details
                stmt.execute("INSERT INTO order_shipping_details (order_id, recipient_name, shipping_address) " +
                        "VALUES (999, 'Recipient 99', '123 Test Street')");
                
                // Reset inventory state
                stmt.execute("UPDATE inventory SET qty_on_hand = 10, qty_available = 10, holding = 0 " +
                        "WHERE product_id = 99 AND warehouse_id = 99");
            }
            conn.commit();
        }
    }

    @AfterEach
    public void tearDownEach() throws Exception {
        cleanupTestData();
    }

    private void cleanupTestData() throws Exception {
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM outbound_items WHERE outbound_id IN (SELECT outbound_id FROM outbound_orders WHERE order_id = 999)");
                stmt.execute("DELETE FROM outbound_orders WHERE order_id = 999");
                stmt.execute("DELETE FROM order_shipping_details WHERE order_id = 999");
                stmt.execute("DELETE FROM order_items WHERE order_id = 999");
                stmt.execute("DELETE FROM orders WHERE order_id = 999");
                stmt.execute("DELETE FROM lazada_order_items WHERE lazada_order_id_str = 'LAZ-999-TEST'");
                stmt.execute("DELETE FROM lazada_orders WHERE lazada_order_id_str = 'LAZ-999-TEST'");
            }
            conn.commit();
        }
    }

    @Test
    public void testFullLazadaOutboundWorkflow() throws Exception {
        LazadaOrderProcessingService processingService = new LazadaOrderProcessingService();
        LazadaShipmentService shipmentService = new LazadaShipmentService();
        OutboundDAO outboundDAO = new OutboundDAO();
        OrderDAO orderDAO = new OrderDAO();

        // ----------------------------------------------------
        // BƯỚC 1: Khởi tạo đơn hàng đồng bộ (PENDING)
        // ----------------------------------------------------
        ProcessingResult initResult = processingService.syncInitializeLazadaOrder("LAZ-999-TEST", testChannel);
        assertTrue(initResult.success);

        // Verify order status in DB is PENDING and outbound order is PENDING
        Order mainOrder = orderDAO.findByOrderCode("LAZ-999-TEST");
        assertNotNull(mainOrder);
        assertEquals("PENDING", mainOrder.getStatus());
        assertFalse(mainOrder.isPackRequested());
        assertEquals("Auto-prepared by background sync", mainOrder.getReviewNote());

        int outboundId = outboundDAO.findActiveOutboundIdByOrderCode("LAZ-999-TEST");
        assertTrue(outboundId > 0);
        OutboundOrder outboundOrder = outboundDAO.findById(outboundId);
        assertNotNull(outboundOrder);
        assertEquals("PENDING_PACK", outboundOrder.getStatus());
        assertEquals("Auto-prepared by background sync", outboundOrder.getReviewNote());

        // ----------------------------------------------------
        // BƯỚC 1 (Nhánh thất bại): Gọi Pack bị lỗi API
        // ----------------------------------------------------
        mockLazadaGateway.setShouldFail(true);
        ProcessingResult packFailResult = processingService.autoPackLazadaOrder("LAZ-999-TEST", testChannel);
        assertFalse(packFailResult.success);

        // Verify error note is saved in database and starts with "Lỗi Pack: "
        mainOrder = orderDAO.findByOrderCode("LAZ-999-TEST");
        assertNotNull(mainOrder.getReviewNote());
        assertTrue(mainOrder.getReviewNote().startsWith("Lỗi Pack: "));
        assertEquals("PICKING", mainOrder.getStatus());

        // ----------------------------------------------------
        // BƯỚC 1 (Nhánh thành công): Thử lại gọi Pack thành công
        // ----------------------------------------------------
        mockLazadaGateway.setShouldFail(false);
        ProcessingResult packSuccessResult = processingService.autoPackLazadaOrder("LAZ-999-TEST", testChannel);
        assertTrue(packSuccessResult.success);

        // Verify review note is cleared, tracking number is saved, status is PICKING
        mainOrder = orderDAO.findByOrderCode("LAZ-999-TEST");
        assertNull(mainOrder.getReviewNote());
        assertEquals("PICKING", mainOrder.getStatus());
        assertEquals("PKG-999", mainOrder.getLazadaPackageId());
        assertEquals("TRK-999", mainOrder.getTrackingNo());

        outboundOrder = outboundDAO.findById(outboundId);
        assertEquals("PACKED", outboundOrder.getStatus());

        // ----------------------------------------------------
        // BƯỚC 2: In tem và mở khóa nút Xác nhận đóng gói
        // ----------------------------------------------------
        boolean printMarked = orderDAO.markLabelPrinted("LAZ-999-TEST");
        assertTrue(printMarked);
        mainOrder = orderDAO.findByOrderCode("LAZ-999-TEST");
        assertTrue(mainOrder.isLabelPrinted());

        // ----------------------------------------------------
        // BƯỚC 3: Xác nhận đóng gói -> ReadyToShip (không trừ kho)
        // ----------------------------------------------------
        ShipmentResult rtsResult = shipmentService.readyToShip(mainOrder);
        assertTrue(rtsResult.success);

        // Verify RTS flag is set, status in WMS becomes HANDED_OVER
        outboundOrder = outboundDAO.findById(outboundId);
        assertEquals("HANDED_OVER", outboundOrder.getStatus());
        assertTrue(outboundOrder.isRtsPushed());

        mainOrder = orderDAO.findByOrderCode("LAZ-999-TEST");
        assertEquals("PACKED", mainOrder.getStatus());

        // Check that physical stock is still 10 (not deducted)
        int stockPhysical = new com.wms.dao.InventoryDAO().getPhysicalStock(99, 99);
        assertEquals(10, stockPhysical);

        // ----------------------------------------------------
        // BƯỚC 4: Duyệt và Xuất kho nháp (Deduct stock, status = SHIPPED)
        // ----------------------------------------------------
        boolean noteUpdated = outboundDAO.updateNote(outboundId, "Xuất thực tế 1 cái");
        assertTrue(noteUpdated);
        boolean qtyUpdated = outboundDAO.updateItemQty(outboundId, 99, new BigDecimal("1.000"));
        assertTrue(qtyUpdated);

        // Complete the dispatch
        com.wms.service.warehouse.OutboundService outboundService = new com.wms.service.warehouse.OutboundService();
        com.wms.service.warehouse.OutboundService.StatusUpdateResult dispatchResult = 
                outboundService.updateStatus(outboundId, "SHIPPED", 99);
        assertTrue(dispatchResult.isSuccess());

        // Verify status is SHIPPED and inventory is deducted
        outboundOrder = outboundDAO.findById(outboundId);
        assertEquals("SHIPPED", outboundOrder.getStatus());
        assertEquals("Xuất thực tế 1 cái", outboundOrder.getNotes());
        
        int finalStockOnHand = new com.wms.dao.InventoryDAO().getPhysicalStock(99, 99);
        assertEquals(9, finalStockOnHand); // 10 - 1 = 9
    }

    private static class MockLazadaGateway implements ChannelGateway {
        private boolean shouldFail = false;

        public void setShouldFail(boolean fail) {
            this.shouldFail = fail;
        }

        @Override
        public String platformName() {
            return "Lazada";
        }

        @Override
        public String listProducts(Channel channel, int pageNumber, int pageSize) {
            return "{}";
        }

        @Override
        public String getProductByItemId(Channel channel, String itemId) {
            return "{}";
        }

        @Override
        public String createProduct(Channel channel, Map<String, String> payload) {
            return "{}";
        }

        @Override
        public String updateProduct(Channel channel, String channelItemId, Map<String, String> payload) {
            return "{}";
        }

        @Override
        public String migrateImages(Channel channel, List<String> externalUrls) {
            return "{}";
        }

        @Override
        public String updateProductStock(Channel channel, String sellerSku, int qty) {
            return "{}";
        }

        @Override
        public String updateProductStockBatch(Channel channel, List<StockUpdate> updates) {
            return "{}";
        }

        @Override
        public String listOrders(Channel channel, String status, Long updatedAfterEpochSec) {
            return "[]";
        }

        @Override
        public String getOrder(Channel channel, String orderId) {
            return "{}";
        }

        @Override
        public String getOrderItems(Channel channel, String orderId) {
            return "[]";
        }

        @Override
        public String getShipmentProviders(Channel channel, String orderId, List<String> orderItemIds) {
            if (shouldFail) throw new RuntimeException("Lỗi kết nối API Lazada");
            return "{\"result\":{\"data\":{\"shipment_providers\":[{\"provider_code\":\"LEX\"}],\"shipping_allocate_type\":\"TFS\"}}}";
        }

        @Override
        public String packOrder(Channel channel, String orderId, String deliveryType) {
            return packOrderWithParams(channel, Collections.emptyMap());
        }

        public String packOrderWithParams(Channel channel, Map<String, String> params) {
            if (shouldFail) throw new RuntimeException("Lỗi hệ thống Lazada");
            return "{\"code\":\"0\",\"result\":{\"success\":true,\"data\":{\"pack_order_list\":[{\"order_item_list\":[{\"item_err_code\":\"0\",\"package_id\":\"PKG-999\",\"tracking_number\":\"TRK-999\"}]}]}}}";
        }

        @Override
        public String getShippingLabel(Channel channel, String packageId) {
            return "{}";
        }

        @Override
        public String readyToShip(Channel channel, String packageId) {
            return "{\"code\":\"0\",\"result\":{\"success\":true}}";
        }

        @Override
        public String getTrackingTrace(Channel channel, String orderNumber) {
            return "[]";
        }

        @Override
        public String listReverseOrders(Channel channel, Long createdAfterEpochSec) {
            return "[]";
        }

        @Override
        public String updateReverseOrder(Channel channel, String reverseOrderId, String action, Map<String, String> payload) {
            return "{}";
        }
    }
}
