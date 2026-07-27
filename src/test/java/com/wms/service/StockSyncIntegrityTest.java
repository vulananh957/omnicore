package com.wms.service;

import com.wms.dao.ChannelDAO;
import com.wms.dao.ChannelProductDAO;
import com.wms.dao.InventoryDAO;
import com.wms.dao.ProductDAO;
import com.wms.model.Channel;
import com.wms.model.ChannelProduct;
import com.wms.model.Product;
import com.wms.util.DBConnection;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class StockSyncIntegrityTest {

    private static final int TEST_PRODUCT_ID = 8888;
    private static final int TEST_WAREHOUSE_ID = 8888;
    private static final int TEST_CHANNEL_ID = 8888;

    private ProductDAO productDAO;
    private InventoryDAO inventoryDAO;
    private ChannelProductDAO channelProductDAO;

    @BeforeAll
    void setupAll() throws Exception {
        new com.wms.listener.SchemaInitListener().contextInitialized(null);
        productDAO = new ProductDAO();
        inventoryDAO = new InventoryDAO();
        channelProductDAO = new ChannelProductDAO();

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("INSERT IGNORE INTO warehouses (warehouse_id, warehouse_code, warehouse_name, address) " +
                        "VALUES (" + TEST_WAREHOUSE_ID + ", 'WH-8888', 'Test Warehouse 8888', 'Address 8888')");
                stmt.execute("INSERT IGNORE INTO products (product_id, sku_code, product_name, category_id, base_price, active) " +
                        "VALUES (" + TEST_PRODUCT_ID + ", 'SKU-SYNC-8888', 'Sync Test Product 8888', 1, 300000.00, 1)");
                stmt.execute("INSERT IGNORE INTO channels (channel_id, platform, channel_name, is_active) " +
                        "VALUES (" + TEST_CHANNEL_ID + ", 'Website', 'Sync Test Channel 8888', 1)");
                stmt.execute("DELETE FROM inventory WHERE product_id = " + TEST_PRODUCT_ID);
                stmt.execute("INSERT INTO inventory (warehouse_id, product_id, qty_on_hand, qty_available) " +
                        "VALUES (" + TEST_WAREHOUSE_ID + ", " + TEST_PRODUCT_ID + ", 60, 60)");
                stmt.execute("DELETE FROM channel_products WHERE channel_id = " + TEST_CHANNEL_ID + " AND product_id = " + TEST_PRODUCT_ID);
                stmt.execute("INSERT INTO channel_products (channel_id, product_id, channel_sku_code, channel_price, channel_stock, status) " +
                        "VALUES (" + TEST_CHANNEL_ID + ", " + TEST_PRODUCT_ID + ", 'SKU-SYNC-8888', 300000.00, 60, 'ACTIVE')");
            }
            conn.commit();
        }

        productDAO.syncStockTotals(TEST_PRODUCT_ID);
    }

    @AfterAll
    void tearDownAll() throws Exception {
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM channel_products WHERE channel_id = " + TEST_CHANNEL_ID);
                stmt.execute("DELETE FROM inventory WHERE product_id = " + TEST_PRODUCT_ID);
                stmt.execute("DELETE FROM products WHERE product_id = " + TEST_PRODUCT_ID);
                stmt.execute("DELETE FROM channels WHERE channel_id = " + TEST_CHANNEL_ID);
                stmt.execute("DELETE FROM warehouses WHERE warehouse_id = " + TEST_WAREHOUSE_ID);
            }
            conn.commit();
        }
    }

    @Test
    @Order(1)
    @DisplayName("Initial stock: Physical = 60, Available = 60")
    void testInitialStock() {
        Product p = productDAO.findById(TEST_PRODUCT_ID);
        assertNotNull(p, "Product 8888 must exist");
        assertEquals(60.0, p.getQtyOnHand(), "Physical stock qty_on_hand should be 60");
        assertEquals(60.0, p.getQtyAvailable(), "Available stock qty_available should be 60");
    }

    @Test
    @Order(2)
    @DisplayName("Deduct stock by 10 (Order placed): Available becomes 50, Physical remains 60")
    void testDeductStock() {
        boolean deducted = inventoryDAO.deductWithLock(TEST_PRODUCT_ID, 99999, "WEB-REF-8888", "Website", 10, TEST_WAREHOUSE_ID);
        assertTrue(deducted, "Deduction of 10 items must succeed");

        // Sync stock totals to products table
        productDAO.syncStockTotals(TEST_PRODUCT_ID);

        Product p = productDAO.findById(TEST_PRODUCT_ID);
        assertNotNull(p);
        assertEquals(60.0, p.getQtyOnHand(), "Physical stock must stay 60 before outbound shipment");
        assertEquals(50.0, p.getQtyAvailable(), "Available stock must decrease to 50");

        ChannelProduct cp = channelProductDAO.findByProductAndChannel(TEST_PRODUCT_ID, TEST_CHANNEL_ID);
        assertNotNull(cp);
        assertEquals(50.0, cp.getChannelStock().doubleValue(), "Channel stock in channel_products table must be synced to 50");
    }

    @Test
    @Order(3)
    @DisplayName("Cancel order: Restore stock back to 60")
    void testRestoreStock() {
        boolean restored = inventoryDAO.restoreDeductedStock(99999);
        assertTrue(restored, "Restoring stock for order 99999 must succeed");

        Product p = productDAO.findById(TEST_PRODUCT_ID);
        assertNotNull(p);
        assertEquals(60.0, p.getQtyOnHand(), "Physical stock remains 60");
        assertEquals(60.0, p.getQtyAvailable(), "Available stock must restore back to 60");

        ChannelProduct cp = channelProductDAO.findByProductAndChannel(TEST_PRODUCT_ID, TEST_CHANNEL_ID);
        assertNotNull(cp);
        assertEquals(60.0, cp.getChannelStock().doubleValue(), "Channel stock in channel_products must restore back to 60");
    }
}
