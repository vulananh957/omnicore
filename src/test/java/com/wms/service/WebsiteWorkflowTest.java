package com.wms.service;

import com.wms.dao.ChannelProductDAO;
import com.wms.dao.SkuMappingDAO;
import com.wms.model.Channel;
import com.wms.model.ChannelProduct;
import com.wms.model.SkuMapping;
import com.wms.service.channel.WebsiteHttpClient;
import com.wms.service.website.WebsiteProductService;
import com.wms.util.DBConnection;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the Website channel product workflow:
 *   1. PUSH  — đẩy sản phẩm mới lên Website (POST)
 *   2. UPDATE — cập nhật giá/mô tả (PUT)
 *   3. DELETE — gỡ sản phẩm khỏi Website (DELETE)
 *   4. PUSH FAIL — HTTP lỗi → service trả false, không ghi channel_item_id
 *
 * Design:
 *   - Real DB (local wms_hub), test data dùng ID = 97/98 để tránh xung đột.
 *   - HTTP layer được mock bằng anonymous subclass của WebsiteHttpClient,
 *     inject vào service qua constructor mới (không thay đổi production path).
 *   - Tests chạy theo thứ tự để channelProductId được chuyển tiếp giữa steps.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class WebsiteWorkflowTest {

    // ── Captured HTTP calls from the mock ──────────────────────────────
    private String lastMethod;
    private String lastPath;
    private String lastBody;

    // ── Test fixtures ─────────────────────────────────────────────────
    private Channel websiteChannel;
    private int channelProductId; // populated by test 1, reused by test 2 & 3

    // WebsiteProductService under test (uses mock HTTP client)
    private WebsiteProductService sut;

    // ── Product & channel IDs used for test data ──────────────────────
    private static final int TEST_PRODUCT_ID = 97;
    private static final int TEST_CHANNEL_ID = 97;
    private static final int TEST_WAREHOUSE_ID = 97;

    // ─────────────────────────────────────────────────────────────────────
    // SETUP / TEARDOWN
    // ─────────────────────────────────────────────────────────────────────

    @BeforeAll
    void setupAll() throws Exception {
        // Initialise schema (no-op if already up to date)
        new com.wms.listener.SchemaInitListener().contextInitialized(null);

        // Insert required reference data
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(
                    "INSERT IGNORE INTO warehouses " +
                    "  (warehouse_id, warehouse_code, warehouse_name, address) " +
                    "VALUES (" + TEST_WAREHOUSE_ID + ", 'WH-97', 'Test Warehouse 97', 'Test Address 97')"
                );
                stmt.execute(
                    "INSERT IGNORE INTO products " +
                    "  (product_id, sku_code, product_name, category_id, base_price) " +
                    "VALUES (" + TEST_PRODUCT_ID + ", 'SKU-WEB-97', 'Website Test Product 97', NULL, 250000.00)"
                );
                stmt.execute(
                    "INSERT IGNORE INTO channels " +
                    "  (channel_id, platform, channel_name, api_key, app_secret, access_token, api_url, is_active) " +
                    "VALUES (" + TEST_CHANNEL_ID + ", 'Website', 'Test Website Ch 97', " +
                    "        'test-key-97', 'test-secret-97', 'test-token-97', " +
                    "        'http://localhost:8081/omnicore-web', 1)"
                );
                stmt.execute(
                    "INSERT IGNORE INTO inventory " +
                    "  (warehouse_id, product_id, qty_on_hand, qty_available) " +
                    "VALUES (" + TEST_WAREHOUSE_ID + ", " + TEST_PRODUCT_ID + ", 10, 10)"
                );
            }
            conn.commit();
        }

        websiteChannel = new com.wms.dao.ChannelDAO().findById(TEST_CHANNEL_ID);
        assertNotNull(websiteChannel, "Website test channel (ID=" + TEST_CHANNEL_ID + ") phải tồn tại trong DB");

        // Build SUT with mock HTTP client
        sut = buildMockSut();
    }

    @AfterAll
    void tearDownAll() throws Exception {
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM sku_mappings    WHERE channel_id = " + TEST_CHANNEL_ID);
                stmt.execute("DELETE FROM channel_products WHERE channel_id = " + TEST_CHANNEL_ID);
                stmt.execute("DELETE FROM inventory        WHERE product_id = " + TEST_PRODUCT_ID
                             + " AND warehouse_id = " + TEST_WAREHOUSE_ID);
                stmt.execute("DELETE FROM products         WHERE product_id = " + TEST_PRODUCT_ID);
                stmt.execute("DELETE FROM channels         WHERE channel_id = " + TEST_CHANNEL_ID);
                stmt.execute("DELETE FROM warehouses       WHERE warehouse_id = " + TEST_WAREHOUSE_ID);
            }
            conn.commit();
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // MOCK HTTP CLIENT FACTORY
    // ─────────────────────────────────────────────────────────────────────

    private WebsiteProductService buildMockSut() {
        WebsiteHttpClient mockClient = new WebsiteHttpClient() {
            @Override
            public String post(String apiPath, String jsonBody) {
                lastMethod = "POST";
                lastPath   = apiPath;
                lastBody   = jsonBody;
                System.out.println("[MOCK HTTP] POST " + apiPath + " body=" + jsonBody.substring(0, Math.min(120, jsonBody.length())) + "...");
                // Simulate storefront returning success JSON
                return "{\"success\":true,\"product_id\":" + TEST_PRODUCT_ID + "}";
            }

            @Override
            public String put(String apiPath, String jsonBody) {
                lastMethod = "PUT";
                lastPath   = apiPath;
                lastBody   = jsonBody;
                System.out.println("[MOCK HTTP] PUT " + apiPath);
                return "{\"success\":true}";
            }

            @Override
            public String delete(String apiPath) {
                lastMethod = "DELETE";
                lastPath   = apiPath;
                lastBody   = null;
                System.out.println("[MOCK HTTP] DELETE " + apiPath);
                return "{\"success\":true}";
            }

            @Override
            public String get(String apiPath) {
                lastMethod = "GET";
                lastPath   = apiPath;
                lastBody   = null;
                System.out.println("[MOCK HTTP] GET " + apiPath);
                return "[" +
                        "  {" +
                        "    \"productId\": 97," +
                        "    \"skuCode\": \"SKU-WEB-97\"," +
                        "    \"productName\": \"Website Test Product 97\"," +
                        "    \"basePrice\": 250000.00," +
                        "    \"qtyAvailable\": 10" +
                        "  }," +
                        "  {" +
                        "    \"productId\": 998," +
                        "    \"skuCode\": \"SKU-UNMAPPED-98\"," +
                        "    \"productName\": \"Unmapped Web Product 98\"," +
                        "    \"basePrice\": 150000.00," +
                        "    \"qtyAvailable\": 5" +
                        "  }" +
                        "]";
            }
        };

        return new WebsiteProductService(mockClient);
    }

    // ─────────────────────────────────────────────────────────────────────
    // TEST 1 — PUSH: đẩy sản phẩm mới lên Website
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("PUSH: Đẩy sản phẩm lên Website → tạo channel_product + sku_mapping")
    void test1_PushProduct() {
        // Wrap in try/catch to expose any internal exception
        Exception caughtEx = null;
        boolean result = false;
        try {
            result = sut.pushProduct(websiteChannel, TEST_PRODUCT_ID);
        } catch (Exception e) {
            caughtEx = e;
            System.err.println("[TEST DEBUG] pushProduct threw exception: " + e);
            e.printStackTrace();
        }
        if (caughtEx != null) {
            fail("pushProduct() threw an unexpected exception: " + caughtEx.getMessage());
        }

        // 1a. Service trả true
        assertTrue(result, "pushProduct() phải trả true khi HTTP thành công");

        // 1b. HTTP POST được gọi đúng endpoint
        assertEquals("POST", lastMethod, "Phải gọi HTTP POST");
        assertEquals("/api/v1/products", lastPath, "Endpoint phải là /api/v1/products");
        assertNotNull(lastBody, "Request body không được null");
        assertTrue(lastBody.contains("SKU-WEB-97"), "Payload phải chứa SKU sản phẩm");
        assertTrue(lastBody.contains("250000"), "Payload phải chứa base_price");

        // 1c. channel_products row được tạo và có channel_item_id
        ChannelProductDAO cpDao = new ChannelProductDAO();
        ChannelProduct cp = cpDao.findByProductAndChannel(TEST_PRODUCT_ID, TEST_CHANNEL_ID);
        assertNotNull(cp, "Phải tạo channel_products row sau khi push");
        assertEquals(String.valueOf(TEST_PRODUCT_ID), cp.getChannelItemId(),
                "channel_item_id phải bằng product_id (Website convention)");

        // 1d. sku_mappings row được tạo với status SYNCED
        SkuMappingDAO smDao = new SkuMappingDAO();
        SkuMapping mapping = smDao.findMappingByChannelAndExternalSku(TEST_CHANNEL_ID, String.valueOf(TEST_PRODUCT_ID));
        assertNotNull(mapping, "Phải tạo sku_mappings row sau push");
        assertEquals("SYNCED", mapping.getSyncStatus(), "Trạng thái ánh xạ phải là SYNCED");

        // Save for next tests
        channelProductId = cp.getId();
        System.out.println("[PUSH] PASSED — channelProductId=" + channelProductId);
    }

    // ─────────────────────────────────────────────────────────────────────
    // TEST 2 — UPDATE: cập nhật giá và mô tả sản phẩm
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @Order(2)
    @DisplayName("UPDATE: Sửa giá + mô tả → gọi PUT đúng path, cập nhật DB")
    void test2_UpdateProduct() {
        assertTrue(channelProductId > 0, "test PUSH (Order=1) phải chạy trước");

        BigDecimal newPrice = new BigDecimal("350000");
        String newDesc = "Mô tả cập nhật cho Website test 97";

        boolean result = sut.updateProduct(websiteChannel, channelProductId, newPrice, newDesc);

        // 2a. Service trả true
        assertTrue(result, "updateProduct() phải trả true");

        // 2b. HTTP PUT với đúng path
        assertEquals("PUT", lastMethod, "Phải gọi HTTP PUT");
        assertTrue(lastPath.startsWith("/api/v1/products/"),
                "PUT path phải là /api/v1/products/{id}, actual: " + lastPath);
        assertEquals("/api/v1/products/" + TEST_PRODUCT_ID, lastPath,
                "PUT path phải chứa channel_item_id=" + TEST_PRODUCT_ID);

        // 2c. Payload PUT chứa giá mới
        assertNotNull(lastBody, "PUT body không được null");
        assertTrue(lastBody.contains("350000"), "PUT body phải chứa giá mới");

        // 2d. DB được cập nhật với giá mới
        ChannelProduct cp = new ChannelProductDAO().findById(channelProductId);
        assertNotNull(cp, "channel_products row vẫn phải tồn tại sau update");
        assertEquals(0, newPrice.compareTo(cp.getChannelPrice()),
                "Giá trong DB phải được cập nhật thành 350000");

        System.out.println("[UPDATE] PASSED — path=" + lastPath + " newPrice=" + cp.getChannelPrice());
    }

    // ─────────────────────────────────────────────────────────────────────
    // TEST 3 — DELETE: gỡ sản phẩm khỏi Website
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @Order(3)
    @DisplayName("DELETE: Gỡ sản phẩm khỏi Website → gọi DELETE, xóa DB records")
    void test3_DeleteProduct() {
        assertTrue(channelProductId > 0, "test PUSH (Order=1) phải chạy trước");

        boolean result = sut.deleteProduct(websiteChannel, channelProductId);

        // 3a. Service trả true
        assertTrue(result, "deleteProduct() phải trả true");

        // 3b. HTTP DELETE với đúng path
        assertEquals("DELETE", lastMethod, "Phải gọi HTTP DELETE");
        assertTrue(lastPath.startsWith("/api/v1/products/"),
                "DELETE path phải là /api/v1/products/{id}, actual: " + lastPath);

        // 3c. channel_products row bị xóa
        ChannelProduct cp = new ChannelProductDAO().findById(channelProductId);
        assertNull(cp, "channel_products row phải bị xóa sau deleteProduct()");

        // 3d. sku_mappings row bị xóa
        SkuMapping mapping = new SkuMappingDAO().findMappingByChannelAndExternalSku(
                TEST_CHANNEL_ID, String.valueOf(TEST_PRODUCT_ID));
        assertNull(mapping, "sku_mappings row phải bị xóa sau deleteProduct()");

        System.out.println("[DELETE] PASSED — path=" + lastPath);
    }

    // ─────────────────────────────────────────────────────────────────────
    // TEST 4 — PUSH FAIL: HTTP trả null → service trả false
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @Order(4)
    @DisplayName("PUSH FAIL: HTTP client trả null → pushProduct() trả false, không ghi channel_item_id")
    void test4_PushProductFailWhenHttpReturnsNull() throws Exception {
        // Dọn row cũ (từ test 1-3) để test sạch
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM sku_mappings    WHERE channel_id=" + TEST_CHANNEL_ID + " AND sku_id=" + TEST_PRODUCT_ID);
                stmt.execute("DELETE FROM channel_products WHERE channel_id=" + TEST_CHANNEL_ID + " AND product_id=" + TEST_PRODUCT_ID);
            }
            conn.commit();
        }

        // SUT với HTTP client luôn trả null (mô phỏng lỗi mạng / xác thực thất bại)
        WebsiteHttpClient failingClient = new WebsiteHttpClient() {
            @Override
            public String post(String apiPath, String jsonBody) {
                System.out.println("[MOCK HTTP FAIL] POST " + apiPath + " → null (network error)");
                return null;
            }
        };
        WebsiteProductService failingSut = new WebsiteProductService(failingClient);

        boolean result = failingSut.pushProduct(websiteChannel, TEST_PRODUCT_ID);

        // 4a. Service trả false
        assertFalse(result, "pushProduct() phải trả false khi HTTP thất bại");

        // 4b. channel_item_id không được ghi (dù skeleton row có thể tồn tại)
        ChannelProduct cp = new ChannelProductDAO().findByProductAndChannel(TEST_PRODUCT_ID, TEST_CHANNEL_ID);
        if (cp != null) {
            assertNull(cp.getChannelItemId(),
                    "channel_item_id KHÔNG được set khi push HTTP thất bại");
        }

        System.out.println("[PUSH FAIL] PASSED");
    }

    // ─────────────────────────────────────────────────────────────────────
    // TEST 5 — PUSH WITH CUSTOM IMAGES: đẩy sản phẩm kèm custom images
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @Order(5)
    @DisplayName("PUSH IMAGES: Đẩy sản phẩm lên Website kèm custom images")
    void test5_PushProductWithCustomImages() throws Exception {
        // Dọn dẹp trước khi chạy
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM sku_mappings    WHERE channel_id=" + TEST_CHANNEL_ID + " AND sku_id=" + TEST_PRODUCT_ID);
                stmt.execute("DELETE FROM channel_products WHERE channel_id=" + TEST_CHANNEL_ID + " AND product_id=" + TEST_PRODUCT_ID);
            }
            conn.commit();
        }

        java.util.List<String> customImgs = java.util.List.of("http://example.com/image1.jpg", "http://example.com/image2.jpg");
        boolean result = sut.pushProduct(websiteChannel, TEST_PRODUCT_ID, customImgs);

        // 5a. Service trả true
        assertTrue(result, "pushProduct với custom image list phải trả true");

        // 5b. HTTP POST body chứa link custom images
        assertNotNull(lastBody);
        assertTrue(lastBody.contains("image1.jpg"));
        assertTrue(lastBody.contains("image2.jpg"));

        System.out.println("[PUSH IMAGES] PASSED");
    }

    // ─────────────────────────────────────────────────────────────────────
    // TEST 6 — PULL: kéo danh sách sản phẩm từ Website và khớp nối (mapping)
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @Order(6)
    @DisplayName("PULL: Kéo sản phẩm từ Website -> tự động mapping hoặc tạo mapping exception")
    void test6_PullProducts() throws Exception {
        // Dọn dẹp trước khi chạy
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM sku_mappings    WHERE channel_id=" + TEST_CHANNEL_ID);
                stmt.execute("DELETE FROM channel_products WHERE channel_id=" + TEST_CHANNEL_ID);
                stmt.execute("DELETE FROM mapping_exceptions WHERE channel_id=" + TEST_CHANNEL_ID);
            }
            conn.commit();
        }

        com.wms.service.lazada.LazadaProductService.PullResult result = sut.pullProducts(websiteChannel);

        // 6a. Kiểm tra kết quả kéo
        assertTrue(result.ok, "pullProducts() phải trả ok = true");
        assertEquals(2, result.pulled, "Phải kéo được 2 sản phẩm");
        assertEquals(1, result.upserted, "Phải khớp nối / update được 1 sản phẩm");
        assertEquals(1, result.unmapped, "Phải phát hiện 1 sản phẩm chưa ánh xạ");

        // 6b. Xác nhận sku_mappings và channel_products được tạo cho sản phẩm khớp (SKU-WEB-97)
        SkuMapping mapping = new SkuMappingDAO().findMappingByChannelAndExternalSku(
                TEST_CHANNEL_ID, String.valueOf(TEST_PRODUCT_ID));
        assertNotNull(mapping, "sku_mappings row phải được tạo cho sản phẩm khớp");
        assertEquals("SYNCED", mapping.getSyncStatus());

        ChannelProduct cp = new ChannelProductDAO().findByProductAndChannel(TEST_PRODUCT_ID, TEST_CHANNEL_ID);
        assertNotNull(cp, "channel_products row phải được tạo cho sản phẩm khớp");
        assertEquals("ACTIVE", cp.getStatus());
        assertEquals(String.valueOf(TEST_PRODUCT_ID), cp.getChannelItemId());

        // 6c. Xác nhận mapping exception được tạo cho sản phẩm không khớp (SKU-UNMAPPED-98)
        var unresolved = new com.wms.dao.SkuMappingExceptionDAO().findUnresolved();
        boolean hasUnmappedException = unresolved.stream()
                .anyMatch(ex -> "SKU-UNMAPPED-98".equals(ex.get("externalSku")) && TEST_CHANNEL_ID == (int) ex.get("channelId"));
        assertTrue(hasUnmappedException, "Phải tạo mapping exception cho SKU chưa khớp");

        System.out.println("[PULL PRODUCTS] PASSED");
    }
}
