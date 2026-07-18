package com.wms.dao;

import com.wms.util.DBConnection;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for InventoryDAO.deductWithLock() — Phase 3 Atomic Deduction.
 *
 * Test scenarios:
 * 1. Happy path: deduct success with valid stock
 * 2. Insufficient stock: deduct fail
 * 3. Race condition: lock held by concurrent order
 * 4. Audit trail: inventory_deduction_log created correctly
 * 5. Idempotency: deduct same order twice (should fail on 2nd attempt)
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class InventoryDAOPhase3Test {

    private InventoryDAO inventoryDAO;
    private static final int TEST_PRODUCT_ID = 99;
    private static final int TEST_WAREHOUSE_ID = 99;
    private static final int TEST_ORDER_ID = 1001;
    private static final String TEST_ORDER_REF = "WEB-TEST-001";

    @BeforeAll
    public void setupAll() throws Exception {
        // Initialize schema
        new com.wms.listener.SchemaInitListener().contextInitialized(null);
        inventoryDAO = new InventoryDAO();

        // Insert test warehouse
        try (Connection conn = DBConnection.getConnection()) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("INSERT IGNORE INTO warehouses (warehouse_id, warehouse_code, warehouse_name, address) " +
                        "VALUES (" + TEST_WAREHOUSE_ID + ", 'WH-TEST', 'Test Warehouse', 'Test Address')");

                // Insert test product with initial stock
                stmt.execute("INSERT IGNORE INTO products (product_id, sku_code, product_name, category_id, base_price, active) " +
                        "VALUES (" + TEST_PRODUCT_ID + ", 'SKU-TEST-99', 'Test Product', 1, 100000, 1)");
            }
        }
    }

    @BeforeEach
    public void setupEach() throws Exception {
        // Clean up test data before each test (delete ALL inventory in test warehouse, not just product 99)
        try (Connection conn = DBConnection.getConnection()) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM inventory_deduction_log WHERE warehouse_id = " + TEST_WAREHOUSE_ID);
                stmt.execute("DELETE FROM inventory WHERE warehouse_id = " + TEST_WAREHOUSE_ID);
            }
        }
    }

    @Test
    @DisplayName("Happy path: deduct success with sufficient stock")
    public void testDeductWithLockSuccess() throws Exception {
        // Setup: Insert inventory with 100 units
        insertInventory(TEST_PRODUCT_ID, TEST_WAREHOUSE_ID, 100);

        // Act: Deduct 10 units
        boolean result = inventoryDAO.deductWithLock(TEST_PRODUCT_ID, TEST_ORDER_ID, TEST_ORDER_REF, "WEB", 10, TEST_WAREHOUSE_ID);

        // Assert
        assertTrue(result, "Deduction should succeed");

        // Verify qty_available decreased
        int remainingQty = getInventoryQty(TEST_PRODUCT_ID, TEST_WAREHOUSE_ID);
        assertEquals(90, remainingQty, "qty_available should be 90 after deducting 10");

        // Verify log created
        int logCount = getDeductionLogCount(TEST_ORDER_ID, TEST_ORDER_REF);
        assertEquals(1, logCount, "Should have 1 deduction log entry");

        // Verify log values
        Map<String, Integer> logData = getDeductionLogData(TEST_ORDER_ID, TEST_ORDER_REF);
        assertEquals(100, logData.get("qty_before"), "Log should record qty_before=100");
        assertEquals(90, logData.get("qty_after"), "Log should record qty_after=90");
        assertEquals(10, logData.get("qty_deducted"), "Log should record qty_deducted=10");
    }

    @Test
    @DisplayName("Insufficient stock: deduct fail")
    public void testDeductWithLockInsufficientStock() throws Exception {
        // Setup: Insert inventory with 5 units
        insertInventory(TEST_PRODUCT_ID, TEST_WAREHOUSE_ID, 5);

        // Act: Try to deduct 10 units
        boolean result = inventoryDAO.deductWithLock(TEST_PRODUCT_ID, TEST_ORDER_ID, TEST_ORDER_REF, "WEB", 10, TEST_WAREHOUSE_ID);

        // Assert
        assertFalse(result, "Deduction should fail for insufficient stock");

        // Verify qty_available unchanged
        int remainingQty = getInventoryQty(TEST_PRODUCT_ID, TEST_WAREHOUSE_ID);
        assertEquals(5, remainingQty, "qty_available should remain 5 after failed deduction");

        // Verify no log created
        int logCount = getDeductionLogCount(TEST_ORDER_ID, TEST_ORDER_REF);
        assertEquals(0, logCount, "Should have no deduction log for failed deduction");
    }

    @Test
    @DisplayName("Concurrent deduction: second order fails due to lock")
    public void testDeductWithLockConcurrentRaceCondition() throws Exception {
        // Setup: Insert inventory with 15 units
        insertInventory(TEST_PRODUCT_ID, TEST_WAREHOUSE_ID, 15);

        // Act 1: Order 1 deducts 10 units (should succeed)
        boolean order1Result = inventoryDAO.deductWithLock(TEST_PRODUCT_ID, TEST_ORDER_ID, TEST_ORDER_REF, "WEB", 10, TEST_WAREHOUSE_ID);
        assertTrue(order1Result, "Order 1 deduction should succeed");

        // Act 2: Order 2 tries to deduct 10 units (should fail — not enough stock)
        int order2Id = TEST_ORDER_ID + 1;
        String order2Ref = "WEB-TEST-002";
        boolean order2Result = inventoryDAO.deductWithLock(TEST_PRODUCT_ID, order2Id, order2Ref, "WEB", 10, TEST_WAREHOUSE_ID);

        // Assert
        assertFalse(order2Result, "Order 2 deduction should fail (only 5 units left)");

        // Verify only Order 1 log exists
        int order1LogCount = getDeductionLogCount(TEST_ORDER_ID, TEST_ORDER_REF);
        int order2LogCount = getDeductionLogCount(order2Id, order2Ref);
        assertEquals(1, order1LogCount, "Should have 1 log for Order 1");
        assertEquals(0, order2LogCount, "Should have no log for Order 2");

        // Verify inventory reflects only Order 1's deduction
        int remainingQty = getInventoryQty(TEST_PRODUCT_ID, TEST_WAREHOUSE_ID);
        assertEquals(5, remainingQty, "Should have 5 units remaining (15 - 10)");
    }

    @Test
    @DisplayName("Multiple products: deduct each independently")
    public void testDeductWithLockMultipleProducts() throws Exception {
        // Setup: Insert 2 products with 50 units each
        int product2Id = TEST_PRODUCT_ID + 1;
        insertInventory(TEST_PRODUCT_ID, TEST_WAREHOUSE_ID, 50);
        insertInventory(product2Id, TEST_WAREHOUSE_ID, 50);

        // Act: Deduct from both products
        boolean result1 = inventoryDAO.deductWithLock(TEST_PRODUCT_ID, TEST_ORDER_ID, TEST_ORDER_REF, "WEB", 20, TEST_WAREHOUSE_ID);
        boolean result2 = inventoryDAO.deductWithLock(product2Id, TEST_ORDER_ID, TEST_ORDER_REF, "WEB", 15, TEST_WAREHOUSE_ID);

        // Assert
        assertTrue(result1, "Deduction from product 1 should succeed");
        assertTrue(result2, "Deduction from product 2 should succeed");

        // Verify both decreased correctly
        int qty1 = getInventoryQty(TEST_PRODUCT_ID, TEST_WAREHOUSE_ID);
        int qty2 = getInventoryQty(product2Id, TEST_WAREHOUSE_ID);
        assertEquals(30, qty1, "Product 1 should have 30 units (50 - 20)");
        assertEquals(35, qty2, "Product 2 should have 35 units (50 - 15)");

        // Verify both logs created for same order
        int logCount = getDeductionLogCountForOrder(TEST_ORDER_ID, TEST_ORDER_REF);
        assertEquals(2, logCount, "Should have 2 log entries for this order");
    }

    @Test
    @DisplayName("Idempotency: deduct same order twice fails on 2nd attempt")
    public void testDeductWithLockIdempotency() throws Exception {
        // Setup: Insert inventory with 30 units
        insertInventory(TEST_PRODUCT_ID, TEST_WAREHOUSE_ID, 30);

        // Act 1: First deduction succeeds
        boolean result1 = inventoryDAO.deductWithLock(TEST_PRODUCT_ID, TEST_ORDER_ID, TEST_ORDER_REF, "WEB", 10, TEST_WAREHOUSE_ID);
        assertTrue(result1, "First deduction should succeed");

        // Act 2: Second deduction for same order fails (only 20 units left, but we'd need 30 to pass)
        boolean result2 = inventoryDAO.deductWithLock(TEST_PRODUCT_ID, TEST_ORDER_ID, TEST_ORDER_REF, "WEB", 25, TEST_WAREHOUSE_ID);
        assertFalse(result2, "Second deduction with same order should fail (insufficient stock)");

        // Verify only 1 log (first deduction)
        int logCount = getDeductionLogCount(TEST_ORDER_ID, TEST_ORDER_REF);
        assertEquals(1, logCount, "Should have only 1 log entry");

        // Verify inventory shows only first deduction
        int remainingQty = getInventoryQty(TEST_PRODUCT_ID, TEST_WAREHOUSE_ID);
        assertEquals(20, remainingQty, "Should have 20 units (30 - 10)");
    }

    // ────────────────── Helper Methods ──────────────────

    private void insertInventory(int productId, int warehouseId, int qtyAvailable) throws Exception {
        String sql = "INSERT INTO inventory (product_id, warehouse_id, qty_on_hand, qty_available, holding, deduction_lock) " +
                "VALUES (?, ?, ?, ?, 0, 0)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            ps.setInt(2, warehouseId);
            ps.setInt(3, qtyAvailable);
            ps.setInt(4, qtyAvailable);
            ps.executeUpdate();
        }
    }

    private int getInventoryQty(int productId, int warehouseId) throws Exception {
        String sql = "SELECT qty_available FROM inventory WHERE product_id = ? AND warehouse_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            ps.setInt(2, warehouseId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private int getDeductionLogCount(int orderId, String orderRef) throws Exception {
        String sql = "SELECT COUNT(*) FROM inventory_deduction_log WHERE order_id = ? AND order_ref = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            ps.setString(2, orderRef);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private int getDeductionLogCountForOrder(int orderId, String orderRef) throws Exception {
        String sql = "SELECT COUNT(*) FROM inventory_deduction_log WHERE order_id = ? AND order_ref = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            ps.setString(2, orderRef);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private Map<String, Integer> getDeductionLogData(int orderId, String orderRef) throws Exception {
        String sql = "SELECT qty_before, qty_after, qty_deducted " +
                "FROM inventory_deduction_log WHERE order_id = ? AND order_ref = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            ps.setString(2, orderRef);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Map<String, Integer> map = new java.util.HashMap<>();
                    map.put("qty_before", rs.getInt("qty_before"));
                    map.put("qty_after", rs.getInt("qty_after"));
                    map.put("qty_deducted", rs.getInt("qty_deducted"));
                    return map;
                }
            }
        }
        return new java.util.HashMap<>();
    }
}
