package com.wms.listener;

import com.wms.util.DBConnection;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SchemaInitListener — Runs once on application startup to ensure the database schema
 * is fully initialised (tables and columns exist).
 *
 * This replaces the fragile "self-modifying DAO constructor" pattern where DDL was
 * executed every time a DAO was instantiated.
 *
 * All schema migrations run ONCE per deployment. If a column already exists, it is skipped.
 */
@WebListener
public class SchemaInitListener implements ServletContextListener {

    private static final Logger LOGGER = Logger.getLogger(SchemaInitListener.class.getName());

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        LOGGER.info("SchemaInitListener: Starting one-time schema initialisation...");
        try {
            ensureRolesTable();
            ensureUsersTableColumns();
            ensureWarehousesTable();
            ensureUserWarehouseAssignments();
            ensureZonesTable();
            ensureCategoriesTable();
            ensureSkusTable();
            ensureProductsTable();
            ensureProductDefaultZonesTable();
            ensureProductImagesTable();
            ensureChannelsTable();
            ensureShippingCarriersTable();
            ensureChannelProductsTable();
            ensureProductImageMigrationsTable();
            ensurePushErrorsTable();
            ensureWebhookLogsTable();
            ensureLazadaSyncLogTable();
            ensureLazadaStockPushLogTable();
            ensureSkuMappingsTable();
            ensureInventoryTable();
            ensureInventoryLedgerTable();
            ensureOrdersTable();
            ensureOrderItemsTable();
            ensureOrderShippingDetailsTable();
            ensureShippingLabelsTable();
            ensureSuppliersTable();
            ensureWarehouseReceipts();
            ensureInboundTables();
            ensureWarehouseIssues();
            ensureRmaTables();
            ensureScrapRecordsTable();
            ensureStockTransfers();
            ensureStocktakes();
            ensureFulfillmentRequestTables();
            ensureLazadaOrdersTable();
            ensureLazadaOrderItemsTable();
            ensureLazadaCategoriesTable();
            ensureProductRopLogTable();
            migrateChannelsColumns();
            ensureIndexes();
            seedDefaultData();
            LOGGER.info("SchemaInitListener: Schema initialisation completed successfully.");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "SchemaInitListener: FAILED to initialise schema. "
                    + "The application may not function correctly.", e);
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        DBConnection.shutdown();
        LOGGER.info("SchemaInitListener: Connection pool shut down.");
    }

    // ── Helper: create table if not exists ──

    private void createTableIfNotExists(Connection conn, String tableName, String createSql) throws SQLException {
        DatabaseMetaData md = conn.getMetaData();
        try (ResultSet rs = md.getTables(null, null, tableName, new String[]{"TABLE"})) {
            if (!rs.next()) {
                try (Statement st = conn.createStatement()) {
                    st.executeUpdate(createSql);
                    LOGGER.info("SchemaInitListener: Created '" + tableName + "' table.");
                }
            }
        }
    }

    // ── Helper: create index if not exists ──
    // MySQL does not support CREATE INDEX IF NOT EXISTS, so we check via SHOW INDEX first.

    private void createIndexIfNotExists(Connection conn, String tableName, String indexName, String createSql) {
        try (ResultSet rs = conn.createStatement().executeQuery(
                "SHOW INDEX FROM " + tableName + " WHERE Key_name = '" + indexName + "'")) {
            if (!rs.next()) {
                try (Statement st = conn.createStatement()) {
                    st.executeUpdate(createSql);
                    LOGGER.info("SchemaInitListener: Created index '" + indexName + "' on '" + tableName + "'.");
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "SchemaInitListener: Could not create index '" + indexName + "': " + e.getMessage());
        }
    }

    /**
     * Migrates channels table columns added for UC-B2C07 (auto token refresh).
     * Runs idempotently using addColumnIfMissing.
     */
    private void migrateChannelsColumns() throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            DatabaseMetaData md = conn.getMetaData();
            addColumnIfMissing(conn, md, "channels", "token_expires_at",
                    "DATETIME DEFAULT NULL COMMENT 'UTC timestamp when access_token expires. NULL = unknown/never.'");
            addColumnIfMissing(conn, md, "channels", "last_order_sync_at",
                    "DATETIME DEFAULT NULL COMMENT 'Last successful order sync via scheduler.'");
        }
    }

    /**
     * UC-B2C09: Lazada category tree (mirrored from /category/tree/get).
     * Used to constrain product pushes to leaf categories Lazada accepts.
     */
    private void ensureLazadaCategoriesTable() throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            createTableIfNotExists(conn, "lazada_categories",
                "CREATE TABLE lazada_categories ("
                + "id INT AUTO_INCREMENT PRIMARY KEY, "
                + "channel_id INT NOT NULL, "
                + "lazada_category_id BIGINT NOT NULL, "
                + "parent_id BIGINT DEFAULT NULL, "
                + "name VARCHAR(255) NOT NULL, "
                + "is_leaf TINYINT(1) NOT NULL DEFAULT 0, "
                + "has_variation TINYINT(1) NOT NULL DEFAULT 0, "
                + "depth INT NOT NULL DEFAULT 0, "
                + "synced_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                + "UNIQUE KEY uq_lazada_cat_channel (channel_id, lazada_category_id), "
                + "INDEX idx_lazada_cat_parent (parent_id), "
                + "INDEX idx_lazada_cat_leaf (channel_id, is_leaf)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        }
    }

    /**
     * Creates performance indexes for commonly-queried columns.
     * Runs idempotently — skips any index that already exists.
     */
    private void ensureIndexes() throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            createIndexIfNotExists(conn, "inventory_ledger", "idx_ledger_sku_wh_time",
                "CREATE INDEX idx_ledger_sku_wh_time ON inventory_ledger (product_id, warehouse_id, timestamp)");
            createIndexIfNotExists(conn, "inventory_ledger", "idx_ledger_sku_type",
                "CREATE INDEX idx_ledger_sku_type ON inventory_ledger (product_id, transaction_type)");
            createIndexIfNotExists(conn, "orders", "idx_orders_customer_date",
                "CREATE INDEX idx_orders_customer_date ON orders (customer_id, created_at)");
            createIndexIfNotExists(conn, "orders", "idx_orders_status_channel",
                "CREATE INDEX idx_orders_status_channel ON orders (order_status, channel_id)");
            createIndexIfNotExists(conn, "inbound_orders", "idx_inbound_status_date",
                "CREATE INDEX idx_inbound_status_date ON inbound_orders (status, created_at)");
            createIndexIfNotExists(conn, "outbound_orders", "idx_outbound_status_date",
                "CREATE INDEX idx_outbound_status_date ON outbound_orders (status, created_at)");
            createIndexIfNotExists(conn, "product_default_zones", "idx_pdz_product",
                "CREATE INDEX idx_pdz_product ON product_default_zones (product_id)");
            createIndexIfNotExists(conn, "channels", "idx_channels_platform",
                "CREATE INDEX idx_channels_platform ON channels (platform)");
        }
    }

    private void addColumnIfMissing(Connection conn, DatabaseMetaData md,
                                   String table, String column, String definition)
            throws SQLException {
        try (ResultSet rs = md.getColumns(null, null, table, column)) {
            if (!rs.next()) {
                try (Statement st = conn.createStatement()) {
                    st.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
                    LOGGER.info("SchemaInitListener: Added column '" + column + "' to '" + table + "'.");
                }
            }
        }
    }

    // ── Seed default data ──

    private void seedDefaultData() throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            Statement st = conn.createStatement();

            // Default roles
            st.executeUpdate("INSERT IGNORE INTO roles (role_name, description) VALUES "
                    + "('ADMIN','Quan tri he thong'),"
                    + "('MANAGER','Quan ly kinh doanh'),"
                    + "('SALES_STAFF','Nhan vien ban hang'),"
                    + "('WAREHOUSE_STAFF','Nhan vien kho')");

            // Default admin user — password_hash loaded from env var to avoid committing secrets
            String defaultAdminHash = System.getenv("WMS_ADMIN_DEFAULT_HASH");
            if (defaultAdminHash != null && !defaultAdminHash.isBlank()) {
                st.executeUpdate("INSERT IGNORE INTO users (username, password_hash, full_name, email, phone, role) "
                        + "VALUES ('quanpm',?, 'Phạm Minh Quân','pmq07072005@gmail.com','0987654321','ADMIN')");
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE users SET password_hash=? WHERE username='quanpm'")) {
                    ps.setString(1, defaultAdminHash);
                    ps.executeUpdate();
                }
            } else {
                LOGGER.warning("SchemaInitListener: WMS_ADMIN_DEFAULT_HASH env var not set — "
                        + "default admin account 'quanpm' was NOT created or updated. "
                        + "Set WMS_ADMIN_DEFAULT_HASH to a BCrypt hash to provision the admin.");
            }


            LOGGER.info("SchemaInitListener: Seed data applied.");
        }
    }

    // ── Table initialisers ──

    private void ensureRolesTable() throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            createTableIfNotExists(conn, "roles",
                "CREATE TABLE roles (role_id INT AUTO_INCREMENT PRIMARY KEY, role_name VARCHAR(50) NOT NULL UNIQUE, description VARCHAR(255)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        }
    }

    private void ensureUsersTableColumns() throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            DatabaseMetaData md = conn.getMetaData();
            addColumnIfMissing(conn, md, "users", "phone", "VARCHAR(20) DEFAULT NULL");
            addColumnIfMissing(conn, md, "users", "otp_preference", "VARCHAR(20) DEFAULT 'EMAIL'");
            addColumnIfMissing(conn, md, "users", "warehouse_id", "INT DEFAULT NULL");
        }
    }

    private void ensureWarehousesTable() throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            createTableIfNotExists(conn, "warehouses",
                "CREATE TABLE warehouses (warehouse_id INT AUTO_INCREMENT PRIMARY KEY, warehouse_code VARCHAR(20) NOT NULL UNIQUE, warehouse_name VARCHAR(100) NOT NULL, address VARCHAR(255), phone VARCHAR(20), capacity INT DEFAULT 0, active TINYINT(1) NOT NULL DEFAULT 1, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            DatabaseMetaData md = conn.getMetaData();
            addColumnIfMissing(conn, md, "warehouses", "phone", "VARCHAR(20) DEFAULT NULL");
        }
    }

    private void ensureUserWarehouseAssignments() throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            createTableIfNotExists(conn, "user_warehouse_assignments",
                "CREATE TABLE user_warehouse_assignments (assignment_id INT AUTO_INCREMENT PRIMARY KEY, user_id INT NOT NULL, warehouse_id INT NOT NULL, is_primary TINYINT(1) NOT NULL DEFAULT 0, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE KEY uq_user_warehouse (user_id, warehouse_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        }
    }

    private void ensureZonesTable() throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            createTableIfNotExists(conn, "zones",
                "CREATE TABLE zones (zone_id INT AUTO_INCREMENT PRIMARY KEY, warehouse_id INT NOT NULL, zone_code VARCHAR(50) NOT NULL, zone_name VARCHAR(100) NOT NULL, zone_type ENUM('NORMAL','RETURN','DAMAGED','DESTROY') NOT NULL DEFAULT 'NORMAL', description TEXT, capacity INT DEFAULT 0, active TINYINT(1) NOT NULL DEFAULT 1, UNIQUE KEY uq_zone_code_wh (zone_code, warehouse_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            DatabaseMetaData md = conn.getMetaData();
            addColumnIfMissing(conn, md, "zones", "is_default", "TINYINT(1) NOT NULL DEFAULT 0");
            addColumnIfMissing(conn, md, "zones", "capacity", "INT DEFAULT 0");
        }
    }

    private void ensureCategoriesTable() throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            createTableIfNotExists(conn, "categories",
                "CREATE TABLE categories (category_id INT AUTO_INCREMENT PRIMARY KEY, parent_id INT DEFAULT NULL, category_name VARCHAR(100) NOT NULL, level_depth INT DEFAULT 0, active TINYINT(1) NOT NULL DEFAULT 1) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            DatabaseMetaData md = conn.getMetaData();
            addColumnIfMissing(conn, md, "categories", "description", "VARCHAR(255) DEFAULT NULL");
            addColumnIfMissing(conn, md, "categories", "category_code", "VARCHAR(10) DEFAULT NULL");
            addColumnIfMissing(conn, md, "categories", "is_immutable", "TINYINT(1) NOT NULL DEFAULT 0");
            addColumnIfMissing(conn, md, "categories", "created_at", "DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP");
            addColumnIfMissing(conn, md, "categories", "updated_at", "DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP");
            // One-time migration: lock all existing rows so the new "code is
            // permanently immutable from creation" rule applies retroactively.
            lockAllExistingCategoryCodes();
        }
    }

    /**
     * One-time data migration: set is_immutable=1 for every category that has
     * a non-null category_code. Runs at every startup but is idempotent —
     * rows that are already locked are simply re-asserted (no harm done).
     */
    private void lockAllExistingCategoryCodes() throws SQLException {
        try (Connection conn = DBConnection.getConnection();
             Statement st = conn.createStatement()) {
            int updated = st.executeUpdate(
                "UPDATE categories SET is_immutable = 1 " +
                "WHERE category_code IS NOT NULL AND is_immutable = 0");
            if (updated > 0) {
                LOGGER.info("SchemaInitListener: Locked " + updated
                    + " existing category code(s) (set is_immutable=1).");
            }
        }
    }

    private void ensureProductsTable() throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            createTableIfNotExists(conn, "products",
                "CREATE TABLE products (product_id INT AUTO_INCREMENT PRIMARY KEY, category_id INT, sku_code VARCHAR(50) NOT NULL UNIQUE, product_name VARCHAR(255) NOT NULL, base_price DECIMAL(15,2) NOT NULL DEFAULT 0, attributes_text VARCHAR(255), weight_kg DECIMAL(8,3), is_new_arrival TINYINT(1) NOT NULL DEFAULT 0, active TINYINT(1) NOT NULL DEFAULT 1, created_by INT, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, barcode VARCHAR(50) DEFAULT NULL, unit VARCHAR(30) DEFAULT 'Cái', min_stock DECIMAL(12,3) DEFAULT 0, max_stock DECIMAL(12,3) DEFAULT 0) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            DatabaseMetaData md = conn.getMetaData();
            addColumnIfMissing(conn, md, "products", "attributes_text", "VARCHAR(255) DEFAULT NULL");
            addColumnIfMissing(conn, md, "products", "weight_kg", "DECIMAL(8,3) DEFAULT NULL");
            addColumnIfMissing(conn, md, "products", "barcode", "VARCHAR(50) DEFAULT NULL");
            addColumnIfMissing(conn, md, "products", "unit", "VARCHAR(30) DEFAULT 'Cái'");
            addColumnIfMissing(conn, md, "products", "min_stock", "DECIMAL(12,3) DEFAULT 0");
            addColumnIfMissing(conn, md, "products", "max_stock", "DECIMAL(12,3) DEFAULT 0");
            addColumnIfMissing(conn, md, "products", "is_best_seller", "TINYINT(1) NOT NULL DEFAULT 0");
            // UC-B2C09: Lazada short_description (max 255 chars per Lazada spec)
            addColumnIfMissing(conn, md, "products", "short_description",
                "VARCHAR(255) DEFAULT NULL COMMENT 'Lazada short_description (<=255 chars)'");
            // MAC: Moving Average Cost — recalculated every time a new lot is received.
            // Formula: MAC = (current_on_hand × current_mac + accepted_qty × unit_cost) / (current_on_hand + accepted_qty)
            addColumnIfMissing(conn, md, "products", "mac_price",
                "DECIMAL(15,4) NOT NULL DEFAULT 0 COMMENT 'Moving Average Cost (Giá vốn bình quân gia quyền)'");
            // ROP: Reorder Point — auto-calculated nightly by RopScheduler.
            // SS = (D_max × L_max) - (D_avg × L_avg);  ROP = (D_avg × L_avg) + SS
            addColumnIfMissing(conn, md, "products", "d_avg",
                "DECIMAL(12,4) NOT NULL DEFAULT 0 COMMENT 'Average daily demand (units/day) over lookback window'");
            addColumnIfMissing(conn, md, "products", "d_max",
                "DECIMAL(12,4) NOT NULL DEFAULT 0 COMMENT 'Maximum daily demand observed in lookback window'");
            addColumnIfMissing(conn, md, "products", "l_avg",
                "DECIMAL(12,4) NOT NULL DEFAULT 0 COMMENT 'Average lead time in days (PO created → GRN received)'");
            addColumnIfMissing(conn, md, "products", "l_max",
                "DECIMAL(12,4) NOT NULL DEFAULT 0 COMMENT 'Maximum lead time in days observed in lookback window'");
            addColumnIfMissing(conn, md, "products", "safety_stock",
                "DECIMAL(12,4) NOT NULL DEFAULT 0 COMMENT 'Safety Stock = (D_max×L_max) − (D_avg×L_avg)'");
            addColumnIfMissing(conn, md, "products", "rop_calculated",
                "DECIMAL(12,3) NOT NULL DEFAULT 0 COMMENT 'Reorder Point = (D_avg×L_avg) + Safety_Stock'");
            // Status workflow is gone: drop the legacy columns if they still exist.
            dropProductApprovalColumnsIfExist(conn, md);
        }
    }

    /**
     * One-time migration: drop the legacy approval-workflow columns
     * (status, approved_at, approved_by, review_note) once we've confirmed
     * no rows are still in PENDING/REJECTED. Idempotent — re-running is a no-op.
     */
    private void dropProductApprovalColumnsIfExist(Connection conn, DatabaseMetaData md) throws SQLException {
        // Only run the safety UPDATE if the column still exists (first-time migration).
        // On subsequent boots the column is already gone — skip the UPDATE to avoid SQL error.
        boolean statusColExists = false;
        try (java.sql.ResultSet rs = md.getColumns(null, null, "products", "status")) {
            statusColExists = rs.next();
        }
        if (statusColExists) {
            try (java.sql.Statement st = conn.createStatement()) {
                st.executeUpdate(
                    "UPDATE products SET status = 'APPROVED' " +
                    "WHERE status IN ('PENDING', 'REJECTED') OR status IS NULL");
            }
        }

        dropColumnIfExists(conn, md, "products", "status");
        dropColumnIfExists(conn, md, "products", "approved_at");
        dropColumnIfExists(conn, md, "products", "approved_by");
        dropColumnIfExists(conn, md, "products", "review_note");
    }

    private void dropColumnIfExists(Connection conn, DatabaseMetaData md, String table, String column) {
        try (java.sql.Statement st = conn.createStatement()) {
            java.sql.ResultSet rs = md.getColumns(null, null, table, column);
            boolean exists = rs.next();
            rs.close();
            if (exists) {
                st.executeUpdate("ALTER TABLE " + table + " DROP COLUMN " + column);
                LOGGER.info("SchemaInitListener: Dropped column " + table + "." + column);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "SchemaInitListener: Failed to drop column " + table + "." + column, e);
        }
    }

    private void ensureProductDefaultZonesTable() throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            createTableIfNotExists(conn, "product_default_zones",
                "CREATE TABLE IF NOT EXISTS product_default_zones ("
                + "product_id INT NOT NULL, "
                + "warehouse_id INT NOT NULL, "
                + "zone_id INT NOT NULL, "
                + "PRIMARY KEY (product_id, warehouse_id), "
                + "FOREIGN KEY (product_id) REFERENCES products(product_id) ON DELETE CASCADE, "
                + "FOREIGN KEY (warehouse_id) REFERENCES warehouses(warehouse_id) ON DELETE CASCADE, "
                + "FOREIGN KEY (zone_id) REFERENCES zones(zone_id) ON DELETE CASCADE"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        }
    }

    private void ensureProductImagesTable() throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            createTableIfNotExists(conn, "product_images",
                "CREATE TABLE product_images (image_id INT AUTO_INCREMENT PRIMARY KEY, product_id INT NOT NULL, image_url VARCHAR(500) NOT NULL, is_primary TINYINT(1) NOT NULL DEFAULT 0, sort_order INT NOT NULL DEFAULT 0, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        }
    }

    private void ensureChannelsTable() throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            createTableIfNotExists(conn, "channels",
                "CREATE TABLE channels (channel_id INT AUTO_INCREMENT PRIMARY KEY, channel_name VARCHAR(100) NOT NULL, platform VARCHAR(50) NOT NULL, api_url VARCHAR(255), api_key VARCHAR(255), app_secret VARCHAR(255), webhook_secret VARCHAR(255), buffer_stock DECIMAL(12,3) DEFAULT 0.00, is_active TINYINT(1) DEFAULT 1, access_token TEXT, refresh_token TEXT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        }
    }

    private void ensureShippingCarriersTable() throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            createTableIfNotExists(conn, "shipping_carriers",
                "CREATE TABLE shipping_carriers (carrier_id INT AUTO_INCREMENT PRIMARY KEY, carrier_code VARCHAR(50) NOT NULL UNIQUE, carrier_name VARCHAR(100) NOT NULL, platform VARCHAR(50) DEFAULT NULL, priority INT NOT NULL DEFAULT 0, is_active TINYINT(1) NOT NULL DEFAULT 1, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, INDEX idx_carriers_active_priority (is_active, priority), INDEX idx_carriers_platform (platform)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            // Idempotent seed: 4 default carriers
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("INSERT IGNORE INTO shipping_carriers (carrier_code, carrier_name, platform, priority) VALUES "
                    + "('SPX','SPX Express','Shopee',10),"
                    + "('LZE','Lazada Express','Lazada',20),"
                    + "('TKT','TikTok Express','TikTok',30),"
                    + "('VTP','Viettel Post',NULL,40)");
            }
        }
    }

    private void ensureChannelProductsTable() throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            createTableIfNotExists(conn, "channel_products",
                "CREATE TABLE channel_products (id INT AUTO_INCREMENT PRIMARY KEY, channel_id INT NOT NULL, product_id INT NOT NULL, channel_sku_code VARCHAR(100), channel_price DECIMAL(15,2) NOT NULL DEFAULT 0, channel_stock DECIMAL(12,3) NOT NULL DEFAULT 0, status ENUM('ACTIVE','INACTIVE','PENDING') DEFAULT 'ACTIVE', listed_at DATETIME, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, UNIQUE KEY uq_channel_product (channel_id, product_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            // UC-B2C09: Lazada push tracking columns (6 cols added idempotently)
            DatabaseMetaData md = conn.getMetaData();
            addColumnIfMissing(conn, md, "channel_products", "channel_item_id",
                "VARCHAR(100) DEFAULT NULL COMMENT 'Lazada item_id returned by /product/create'");
            addColumnIfMissing(conn, md, "channel_products", "lazada_sku_id",
                "VARCHAR(100) DEFAULT NULL COMMENT 'Lazada sku_id returned by /product/create'");
            addColumnIfMissing(conn, md, "channel_products", "last_push_qty",
                "DECIMAL(12,3) DEFAULT NULL COMMENT 'Stock quantity at last successful push'");
            addColumnIfMissing(conn, md, "channel_products", "last_push_at",
                "DATETIME DEFAULT NULL COMMENT 'Timestamp of last successful push'");
            addColumnIfMissing(conn, md, "channel_products", "last_error_code",
                "VARCHAR(50) DEFAULT NULL COMMENT 'Last push error code from Lazada'");
            addColumnIfMissing(conn, md, "channel_products", "last_error_message",
                "VARCHAR(500) DEFAULT NULL COMMENT 'Last push error message (translated to VI)'");
            // UC-B2C09: Lazada leaf category chosen for the product push
            addColumnIfMissing(conn, md, "channel_products", "lazada_category_id",
                "BIGINT DEFAULT NULL COMMENT 'Lazada leaf category id (mirrored from /category/tree/get)'");
        }
    }

    private void ensureProductImageMigrationsTable() throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            createTableIfNotExists(conn, "product_image_migrations",
                "CREATE TABLE product_image_migrations ("
                + "id INT AUTO_INCREMENT PRIMARY KEY, "
                + "channel_id INT NOT NULL, "
                + "source_url VARCHAR(500) NOT NULL, "
                + "lazada_image_url VARCHAR(500) DEFAULT NULL, "
                + "lazada_image_id VARCHAR(100) DEFAULT NULL, "
                + "migrated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                + "UNIQUE KEY uq_migration_channel_url (channel_id, source_url(255)), "
                + "INDEX idx_migration_channel (channel_id)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        }
    }

    private void ensurePushErrorsTable() throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            createTableIfNotExists(conn, "push_errors",
                "CREATE TABLE push_errors ("
                + "id INT AUTO_INCREMENT PRIMARY KEY, "
                + "channel_product_id INT DEFAULT NULL, "
                + "channel_id INT NOT NULL, "
                + "sku_code VARCHAR(100) DEFAULT NULL, "
                + "error_code VARCHAR(50) DEFAULT NULL, "
                + "error_message VARCHAR(500) DEFAULT NULL, "
                + "field_errors_json TEXT, "
                + "raw_response TEXT, "
                + "occurred_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                + "INDEX idx_pe_channel (channel_id), "
                + "INDEX idx_pe_occurred (occurred_at)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        }
    }

    private void ensureWebhookLogsTable() throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            createTableIfNotExists(conn, "webhook_logs",
                "CREATE TABLE webhook_logs (log_id INT AUTO_INCREMENT PRIMARY KEY, channel_id INT, event_type VARCHAR(50) NOT NULL, payload TEXT, status ENUM('SUCCESS','FAILED','PENDING') NOT NULL DEFAULT 'PENDING', error_trace TEXT, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, message_id VARCHAR(100) NULL, request_ip VARCHAR(50) NULL, request_signature VARCHAR(255) NULL, retry_count INT NOT NULL DEFAULT 0, processed_at DATETIME NULL, INDEX idx_message_id (message_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        }
    }

    private void ensureLazadaSyncLogTable() throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            createTableIfNotExists(conn, "lazada_sync_log",
                "CREATE TABLE lazada_sync_log (log_id INT AUTO_INCREMENT PRIMARY KEY, channel_id INT, sync_type VARCHAR(50), status ENUM('SUCCESS','FAILED') NOT NULL, request_data TEXT, response_data TEXT, error_msg TEXT, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        }
    }

    private void ensureLazadaStockPushLogTable() throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            DatabaseMetaData md = conn.getMetaData();
            createTableIfNotExists(conn, "lazada_stock_push_log",
                "CREATE TABLE lazada_stock_push_log ("
                + "log_id INT AUTO_INCREMENT PRIMARY KEY, "
                + "channel_id INT, product_id INT, seller_sku VARCHAR(100), "
                + "qty_on_hand DECIMAL(12,3), qty_available DECIMAL(12,3), "
                + "holding DECIMAL(12,3), buffer_stock DECIMAL(12,3), push_qty DECIMAL(12,3), "
                + "status VARCHAR(20), error_code VARCHAR(50), error_message TEXT, "
                + "inbound_receipt_code VARCHAR(50), pushed_at DATETIME, "
                + "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            // Idempotent migration: add new columns if they were missing from a prior version
            addColumnIfMissing(conn, md, "lazada_stock_push_log", "error_code",
                "VARCHAR(50) DEFAULT NULL COMMENT 'Lazada error code (e.g. E501, E901)'");
            addColumnIfMissing(conn, md, "lazada_stock_push_log", "inbound_receipt_code",
                "VARCHAR(50) DEFAULT NULL COMMENT 'Inbound receipt that triggered this push'");
            addColumnIfMissing(conn, md, "lazada_stock_push_log", "pushed_at",
                "DATETIME DEFAULT NULL COMMENT 'Timestamp when push was attempted'");
        }
    }

    private void ensureSkuMappingsTable() throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            createTableIfNotExists(conn, "sku_mappings",
                "CREATE TABLE sku_mappings (mapping_id INT AUTO_INCREMENT PRIMARY KEY, sku_id INT NOT NULL, channel_id INT NOT NULL, external_sku VARCHAR(100), seller_sku VARCHAR(100), sync_status ENUM('SYNCED','PENDING','ERROR') DEFAULT 'PENDING', last_sync_at DATETIME, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, UNIQUE KEY uq_sku_channel (sku_id, channel_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        }
    }

    private void ensureInventoryTable() throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            DatabaseMetaData md = conn.getMetaData();
            createTableIfNotExists(conn, "inventory",
                "CREATE TABLE inventory (inventory_id INT AUTO_INCREMENT PRIMARY KEY, product_id INT NOT NULL, warehouse_id INT NOT NULL, qty_on_hand DECIMAL(12,3) NOT NULL DEFAULT 0, holding DECIMAL(12,3) NOT NULL DEFAULT 0, qty_available DECIMAL(12,3) NOT NULL DEFAULT 0, reorder_point DECIMAL(12,3) DEFAULT NULL, stock_type VARCHAR(20) NOT NULL DEFAULT 'NORMAL', updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, UNIQUE KEY uq_product_warehouse (product_id, warehouse_id, stock_type)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            addColumnIfMissing(conn, md, "inventory", "stock_type",
                "VARCHAR(20) NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL or DEFECTIVE stock pool'");
        }
    }

    private void ensureInventoryLedgerTable() throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            DatabaseMetaData md = conn.getMetaData();
            createTableIfNotExists(conn, "inventory_ledger",
                "CREATE TABLE inventory_ledger (ledger_id INT AUTO_INCREMENT PRIMARY KEY, inventory_id INT NOT NULL, product_id INT NOT NULL, warehouse_id INT NOT NULL, transaction_type ENUM('INBOUND','OUTBOUND','ADJUSTMENT','TRANSFER_IN','TRANSFER_OUT') NOT NULL, ref_document_id INT, qty_change DECIMAL(12,3) NOT NULL, avail_change DECIMAL(12,3) NOT NULL, timestamp DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, created_by INT, note TEXT) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            addColumnIfMissing(conn, md, "inventory_ledger", "ledger_type",
                "VARCHAR(20) DEFAULT 'NORMAL' COMMENT 'NORMAL or DEFECTIVE — mirrors inventory.stock_type'");
        }
    }

    private void ensureSkusTable() throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            createTableIfNotExists(conn, "skus",
                "CREATE TABLE skus (sku_id INT AUTO_INCREMENT PRIMARY KEY, sku_code VARCHAR(50) NOT NULL UNIQUE, product_name VARCHAR(150) NOT NULL, category VARCHAR(80), unit VARCHAR(30) NOT NULL DEFAULT 'Cái', barcode VARCHAR(50), weight_kg DECIMAL(8,3), description TEXT, min_stock INT NOT NULL DEFAULT 0, active TINYINT(1) NOT NULL DEFAULT 1, created_by INT, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        }
    }

    private void ensureOrdersTable() throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            createTableIfNotExists(conn, "orders",
                "CREATE TABLE orders (order_id INT AUTO_INCREMENT PRIMARY KEY, order_code VARCHAR(30) NOT NULL UNIQUE, customer_id INT, warehouse_id INT, channel ENUM('ONLINE','STORE','B2B') NOT NULL DEFAULT 'ONLINE', status ENUM('PENDING','PICKING','PACKED','SHIPPED','DELIVERED','CANCELLED','RETURNED','DISPUTED','DISPUTE_SUCCESS','COMPLETED') NOT NULL DEFAULT 'PENDING', total_amount DECIMAL(15,2) NOT NULL DEFAULT 0, note TEXT, created_by INT, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, tracking_no VARCHAR(100), review_note VARCHAR(255), rma_reason VARCHAR(255), rma_physical_status VARCHAR(100), rma_platform_status VARCHAR(100), dispute_evidence_video VARCHAR(255), dispute_note VARCHAR(255)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            DatabaseMetaData md = conn.getMetaData();
            addColumnIfMissing(conn, md, "orders", "tracking_no", "VARCHAR(100) DEFAULT NULL");
            addColumnIfMissing(conn, md, "orders", "review_note", "VARCHAR(255) DEFAULT NULL");
            addColumnIfMissing(conn, md, "orders", "rma_reason", "VARCHAR(255) DEFAULT NULL");
            addColumnIfMissing(conn, md, "orders", "rma_physical_status", "VARCHAR(100) DEFAULT NULL");
            addColumnIfMissing(conn, md, "orders", "rma_platform_status", "VARCHAR(100) DEFAULT NULL");
            addColumnIfMissing(conn, md, "orders", "dispute_evidence_video", "VARCHAR(255) DEFAULT NULL");
            addColumnIfMissing(conn, md, "orders", "dispute_note", "VARCHAR(255) DEFAULT NULL");
            addColumnIfMissing(conn, md, "orders", "channel_id",
                    "INT DEFAULT NULL COMMENT 'FK to channels.channel_id — links Lazada orders to their channel config'");
            addColumnIfMissing(conn, md, "orders", "channel_order_id",
                    "VARCHAR(50) DEFAULT NULL COMMENT 'Lazada order_id as string (for cross-referencing lazada_orders table)'");
            addColumnIfMissing(conn, md, "orders", "fee_breakdown_json",
                    "TEXT DEFAULT NULL COMMENT 'Detailed fee breakdown from channel'");
            addColumnIfMissing(conn, md, "orders", "sync_status",
                    "VARCHAR(20) DEFAULT 'PENDING' COMMENT 'Sync status of the order'");
            addColumnIfMissing(conn, md, "orders", "web_order_ref",
                    "VARCHAR(100) DEFAULT NULL COMMENT 'Dedup key for orders created by omnicore-web'");
            addColumnIfMissing(conn, md, "orders", "web_customer_ref",
                    "VARCHAR(100) DEFAULT NULL COMMENT 'omnicore-web customers.customer_id — reference only, not a real FK'");
            addColumnIfMissing(conn, md, "orders", "shipment_provider",
                    "VARCHAR(100) DEFAULT NULL COMMENT 'Assigned shipping carrier — any channel, not Lazada-specific'");
            createIndexIfNotExists(conn, "orders", "uq_web_order_ref",
                    "CREATE UNIQUE INDEX uq_web_order_ref ON orders (web_order_ref)");
        }
    }

    private void ensureOrderItemsTable() throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            createTableIfNotExists(conn, "order_items",
                "CREATE TABLE order_items (order_item_id INT AUTO_INCREMENT PRIMARY KEY, order_id INT NOT NULL, product_id INT NOT NULL, qty INT NOT NULL DEFAULT 1, unit_price DECIMAL(12,2) NOT NULL DEFAULT 0.00) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            DatabaseMetaData md = conn.getMetaData();
            addColumnIfMissing(conn, md, "order_items", "actual_price", "DECIMAL(15,2) NOT NULL DEFAULT 0.00");
        }
    }

    private void ensureOrderShippingDetailsTable() throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            createTableIfNotExists(conn, "order_shipping_details",
                "CREATE TABLE order_shipping_details (shipping_id INT AUTO_INCREMENT PRIMARY KEY, order_id INT NOT NULL UNIQUE, recipient_name VARCHAR(100) NOT NULL, shipping_address TEXT NOT NULL, courier_name VARCHAR(50), waybill_code VARCHAR(100), shipping_status ENUM('PENDING','PICKED_UP','IN_TRANSIT','OUT_FOR_DELIVERY','DELIVERED','RETURNED') NOT NULL DEFAULT 'PENDING', created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            DatabaseMetaData md = conn.getMetaData();
            addColumnIfMissing(conn, md, "order_shipping_details", "recipient_phone", "VARCHAR(20) DEFAULT NULL");
        }
    }

    private void ensureShippingLabelsTable() throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            createTableIfNotExists(conn, "shipping_labels",
                "CREATE TABLE shipping_labels (label_id INT AUTO_INCREMENT PRIMARY KEY, order_id INT NOT NULL, outbound_id INT, carrier VARCHAR(50), tracking_no VARCHAR(100), label_url VARCHAR(255), printed TINYINT(1) DEFAULT 0, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        }
    }

    private void ensureSuppliersTable() throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            createTableIfNotExists(conn, "suppliers",
                "CREATE TABLE suppliers (supplier_id INT AUTO_INCREMENT PRIMARY KEY, supplier_code VARCHAR(20) NOT NULL UNIQUE, name VARCHAR(255) NOT NULL, contact_person VARCHAR(100) DEFAULT NULL, phone VARCHAR(20) DEFAULT NULL, email VARCHAR(100) DEFAULT NULL, address VARCHAR(500) DEFAULT NULL, credit_limit DECIMAL(15,2) DEFAULT 0.00, payment_terms VARCHAR(50) DEFAULT NULL, status ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE', created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, INDEX idx_supplier_code (supplier_code), INDEX idx_supplier_status (status), INDEX idx_supplier_name (name)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        }
    }

    private void ensureWarehouseReceipts() throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            createTableIfNotExists(conn, "warehouse_receipts",
                "CREATE TABLE warehouse_receipts (receipt_id INT AUTO_INCREMENT PRIMARY KEY, receipt_code VARCHAR(50) NOT NULL UNIQUE, warehouse_id INT NOT NULL, receipt_type ENUM('PURCHASE','RETURN','TRANSFER') NOT NULL DEFAULT 'PURCHASE', supplier_name VARCHAR(255), created_by INT NOT NULL, copied_from_id INT, status ENUM('DRAFT','APPROVED','CANCELLED') NOT NULL DEFAULT 'DRAFT', created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        }
    }

    private void ensureInboundTables() throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            createTableIfNotExists(conn, "inbound_orders",
                "CREATE TABLE inbound_orders (inbound_id INT AUTO_INCREMENT PRIMARY KEY, inbound_code VARCHAR(30) NOT NULL UNIQUE, warehouse_id INT NOT NULL, supplier VARCHAR(100), status ENUM('PENDING','IN_PROGRESS','RECEIVED','CANCELLED') NOT NULL DEFAULT 'PENDING', received_by INT, note TEXT, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, received_at DATETIME) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            DatabaseMetaData md = conn.getMetaData();
            addColumnIfMissing(conn, md, "inbound_orders", "created_by", "INT DEFAULT NULL");
            addColumnIfMissing(conn, md, "inbound_orders", "supplier_id", "INT DEFAULT NULL COMMENT 'FK mem toi suppliers.supplier_id'");
            addColumnIfMissing(conn, md, "inbound_orders", "supplier_address", "VARCHAR(255) DEFAULT NULL");
            addColumnIfMissing(conn, md, "inbound_orders", "supplier_phone", "VARCHAR(50) DEFAULT NULL");
            addColumnIfMissing(conn, md, "inbound_orders", "po_reference", "VARCHAR(50) DEFAULT NULL");
            addColumnIfMissing(conn, md, "inbound_orders", "zone_id", "INT DEFAULT NULL COMMENT 'Khu vuc nhan hang trong kho (zones.zone_id)'");
            addColumnIfMissing(conn, md, "inbound_orders", "delivery_person", "VARCHAR(100) DEFAULT NULL COMMENT 'Ten nguoi giao hang / tai xe'");
            addColumnIfMissing(conn, md, "inbound_orders", "delivery_phone", "VARCHAR(50) DEFAULT NULL COMMENT 'SDT nguoi giao'");
            addColumnIfMissing(conn, md, "inbound_orders", "expected_date", "DATE DEFAULT NULL COMMENT 'Ngay du kien nhan hang (PO)'");
            addColumnIfMissing(conn, md, "inbound_orders", "received_date", "DATE DEFAULT NULL COMMENT 'Ngay nhap hang thuc te'");
            addColumnIfMissing(conn, md, "inbound_orders", "payment_terms", "VARCHAR(50) DEFAULT NULL");
            createTableIfNotExists(conn, "inbound_items",
                "CREATE TABLE inbound_items (inbound_item_id INT AUTO_INCREMENT PRIMARY KEY, inbound_id INT NOT NULL, product_id INT NOT NULL, expected_qty DECIMAL(12,3) NOT NULL DEFAULT 0, received_qty DECIMAL(12,3) NOT NULL DEFAULT 0) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            addColumnIfMissing(conn, md, "inbound_items", "unit_cost",
                "DECIMAL(15,4) NOT NULL DEFAULT 0 COMMENT 'Unit cost at time of inbound receipt (used for MAC recalculation)'");
            addColumnIfMissing(conn, md, "inbound_items", "accepted_qty",
                "DECIMAL(12,3) NOT NULL DEFAULT 0 COMMENT 'Accepted quantity used for MAC'");
            addColumnIfMissing(conn, md, "inbound_items", "rejected_qty",
                "DECIMAL(12,3) NOT NULL DEFAULT 0");
            addColumnIfMissing(conn, md, "inbound_items", "reject_reason",
                "VARCHAR(255) DEFAULT NULL COMMENT 'Ly do tra hang NCC: Hang mop meo, Sai mau/size, Het han, Hong van chuyen'");
            addColumnIfMissing(conn, md, "inbound_items", "lot_number",
                "VARCHAR(50) DEFAULT NULL");
            addColumnIfMissing(conn, md, "inbound_items", "expiry_date",
                "DATE DEFAULT NULL");
            addColumnIfMissing(conn, md, "inbound_items", "notes",
                "VARCHAR(255) DEFAULT NULL");
            createTableIfNotExists(conn, "receipt_notes",
                "CREATE TABLE receipt_notes (receipt_id INT AUTO_INCREMENT PRIMARY KEY, inbound_id INT NOT NULL, warehouse_id INT NOT NULL, received_by INT, note TEXT, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        }
    }

    private void ensureWarehouseIssues() throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            createTableIfNotExists(conn, "warehouse_issues",
                "CREATE TABLE warehouse_issues (issue_id INT AUTO_INCREMENT PRIMARY KEY, issue_code VARCHAR(50) NOT NULL UNIQUE, warehouse_id INT NOT NULL, issue_type ENUM('ORDER','SCRAP','TRANSFER') NOT NULL, ref_order_id INT, transfer_id INT, dest_zone_id INT, created_by INT NOT NULL, copied_from_id INT, status ENUM('DRAFT','APPROVED','CANCELLED') NOT NULL DEFAULT 'DRAFT', created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            createTableIfNotExists(conn, "issue_details",
                "CREATE TABLE issue_details (detail_id INT AUTO_INCREMENT PRIMARY KEY, issue_id INT NOT NULL, product_id INT NOT NULL, quantity DECIMAL(12,3) NOT NULL, note VARCHAR(255)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            createTableIfNotExists(conn, "outbound_orders",
                "CREATE TABLE outbound_orders (outbound_id INT AUTO_INCREMENT PRIMARY KEY, order_id INT NOT NULL, warehouse_id INT NOT NULL, status ENUM('PENDING','PICKING','PACKED','SHIPPED','DELIVERED','CANCELLED') NOT NULL DEFAULT 'PENDING', picked_by INT, shipped_at DATETIME, note TEXT, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            createTableIfNotExists(conn, "outbound_items",
                "CREATE TABLE outbound_items (outbound_item_id INT AUTO_INCREMENT PRIMARY KEY, outbound_id INT NOT NULL, product_id INT NOT NULL, qty DECIMAL(12,3) NOT NULL DEFAULT 1, picked_qty DECIMAL(12,3) NOT NULL DEFAULT 0) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            DatabaseMetaData md = conn.getMetaData();
            addColumnIfMissing(conn, md, "outbound_items", "shelf_location", "VARCHAR(100) DEFAULT NULL");
            addColumnIfMissing(conn, md, "outbound_orders", "outbound_code", "VARCHAR(50) DEFAULT NULL");
            createTableIfNotExists(conn, "picking_sheets",
                "CREATE TABLE picking_sheets (sheet_id INT AUTO_INCREMENT PRIMARY KEY, outbound_id INT NOT NULL, picker_id INT, status ENUM('PENDING','IN_PROGRESS','COMPLETED') DEFAULT 'PENDING', started_at DATETIME, completed_at DATETIME) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            createTableIfNotExists(conn, "delivery_notes",
                "CREATE TABLE delivery_notes (delivery_id INT AUTO_INCREMENT PRIMARY KEY, outbound_id INT NOT NULL, delivered_by INT, delivery_date DATETIME, recipient_name VARCHAR(100), recipient_note TEXT) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        }
    }

    private void ensureRmaTables() throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            createTableIfNotExists(conn, "rma_requests",
                "CREATE TABLE rma_requests (rma_id INT AUTO_INCREMENT PRIMARY KEY, order_id INT NOT NULL, channel_return_id VARCHAR(100), return_waybill VARCHAR(100), rma_code VARCHAR(50) NOT NULL UNIQUE, status ENUM('PENDING','APPROVED','DISPUTED','RESOLVED') NOT NULL DEFAULT 'PENDING', return_reason VARCHAR(255) NOT NULL, zone_id INT, requested_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, returned_at DATETIME) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            createTableIfNotExists(conn, "rma_items",
                "CREATE TABLE rma_items (rma_item_id INT AUTO_INCREMENT PRIMARY KEY, rma_id INT NOT NULL, product_id INT NOT NULL, channel_return_item_id VARCHAR(100), quantity DECIMAL(12,3) NOT NULL DEFAULT 1, refund_amount DECIMAL(15,2)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            createTableIfNotExists(conn, "qc_inspections",
                "CREATE TABLE qc_inspections (qc_id INT AUTO_INCREMENT PRIMARY KEY, rma_item_id INT NOT NULL, inspected_by INT NOT NULL, good_quantity DECIMAL(12,3) NOT NULL DEFAULT 0, good_zone_id INT, damaged_quantity DECIMAL(12,3) NOT NULL DEFAULT 0, damaged_zone_id INT, notes TEXT, inspected_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            createTableIfNotExists(conn, "return_orders",
                "CREATE TABLE return_orders (return_id INT AUTO_INCREMENT PRIMARY KEY, return_code VARCHAR(50), order_id INT, outbound_id INT, customer_name VARCHAR(100), customer_phone VARCHAR(20), reason VARCHAR(255), status ENUM('RECEIVED','INSPECTING','PASS','FAIL','RESTOCKED','SCRAPPED') DEFAULT 'RECEIVED', warehouse_id INT NOT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            DatabaseMetaData md = conn.getMetaData();
            addColumnIfMissing(conn, md, "return_orders", "customer_phone", "VARCHAR(20) DEFAULT NULL");
            addColumnIfMissing(conn, md, "return_orders", "return_code", "VARCHAR(50) DEFAULT NULL");
            createTableIfNotExists(conn, "qc_records",
                "CREATE TABLE qc_records (qc_id INT AUTO_INCREMENT PRIMARY KEY, return_id INT NOT NULL, product_id INT, decision ENUM('PASS','FAIL') NOT NULL, qc_notes TEXT, qc_by INT, qc_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            createTableIfNotExists(conn, "return_items",
                "CREATE TABLE return_items (return_item_id INT AUTO_INCREMENT PRIMARY KEY, return_id INT NOT NULL, product_id INT NOT NULL, quantity DECIMAL(12,3) NOT NULL DEFAULT 1, return_reason VARCHAR(255), FOREIGN KEY (return_id) REFERENCES return_orders(return_id) ON DELETE CASCADE, FOREIGN KEY (product_id) REFERENCES products(product_id) ON DELETE CASCADE) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        }
    }

    private void ensureScrapRecordsTable() throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            createTableIfNotExists(conn, "scrap_records",
                "CREATE TABLE scrap_records (scrap_id INT AUTO_INCREMENT PRIMARY KEY, return_id INT NOT NULL, product_id INT, qty DECIMAL(12,3) NOT NULL DEFAULT 1, reason VARCHAR(255), scrap_by INT, scrap_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        }
    }

    private void ensureStockTransfers() throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            createTableIfNotExists(conn, "stock_transfers",
                "CREATE TABLE stock_transfers (transfer_id INT AUTO_INCREMENT PRIMARY KEY, transfer_code VARCHAR(50) NOT NULL UNIQUE, from_warehouse_id INT NOT NULL, to_warehouse_id INT NOT NULL, created_by INT NOT NULL, approved_by INT, status ENUM('DRAFT','IN_TRANSIT','RECEIVED','CANCELLED') NOT NULL DEFAULT 'DRAFT', note TEXT, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, completed_at DATETIME) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            createTableIfNotExists(conn, "stock_transfer_items",
                "CREATE TABLE stock_transfer_items (transfer_item_id INT AUTO_INCREMENT PRIMARY KEY, transfer_id INT NOT NULL, product_id INT NOT NULL, shipped_qty DECIMAL(12,3) NOT NULL, received_qty DECIMAL(12,3)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            createTableIfNotExists(conn, "transfer_details",
                "CREATE TABLE transfer_details (transfer_detail_id INT AUTO_INCREMENT PRIMARY KEY, transfer_id INT NOT NULL, product_id INT NOT NULL, qty INT NOT NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        }
    }

    private void ensureStocktakes() throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            createTableIfNotExists(conn, "physical_inventories",
                "CREATE TABLE physical_inventories (inventory_check_id INT AUTO_INCREMENT PRIMARY KEY, check_code VARCHAR(50) NOT NULL UNIQUE, warehouse_id INT NOT NULL, created_by INT NOT NULL, status ENUM('DRAFT','IN_PROGRESS','APPROVED') NOT NULL DEFAULT 'DRAFT', created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            DatabaseMetaData md = conn.getMetaData();
            addColumnIfMissing(conn, md, "physical_inventories", "note", "TEXT DEFAULT NULL");
            createTableIfNotExists(conn, "physical_inventory_details",
                "CREATE TABLE physical_inventory_details (check_detail_id INT AUTO_INCREMENT PRIMARY KEY, inventory_check_id INT NOT NULL, product_id INT NOT NULL, system_qty DECIMAL(12,3) NOT NULL DEFAULT 0, actual_qty DECIMAL(12,3) DEFAULT NULL, delta_qty DECIMAL(12,3) DEFAULT NULL, counted_by INT, counted_at DATETIME) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            createTableIfNotExists(conn, "stocktakes",
                "CREATE TABLE stocktakes (stocktake_id INT AUTO_INCREMENT PRIMARY KEY, stocktake_code VARCHAR(30) NOT NULL UNIQUE, warehouse_id INT NOT NULL, status ENUM('PLANNED','IN_PROGRESS','COMPLETED','CANCELLED') DEFAULT 'PLANNED', counted_by INT, approved_by INT, note TEXT, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, completed_at DATETIME) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            createTableIfNotExists(conn, "stocktake_items",
                "CREATE TABLE stocktake_items (item_id INT AUTO_INCREMENT PRIMARY KEY, stocktake_id INT NOT NULL, product_id INT NOT NULL, system_qty INT NOT NULL DEFAULT 0, counted_qty INT DEFAULT NULL, variance INT DEFAULT NULL, counted_by INT, counted_at DATETIME) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        }
    }

    // ── Fulfillment Requests (test seed) ──

    private void ensureFulfillmentRequestTables() throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            createTableIfNotExists(conn, "fulfillment_requests",
                "CREATE TABLE fulfillment_requests ("
                    + "request_id VARCHAR(50) PRIMARY KEY,"
                    + "order_id VARCHAR(50) NOT NULL,"
                    + "warehouse_id INT NOT NULL DEFAULT 1,"
                    + "status ENUM('PENDING','CONVERTED','CANCELLED') NOT NULL DEFAULT 'PENDING',"
                    + "auto_created TINYINT(1) NOT NULL DEFAULT 0,"
                    + "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                    + "updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
                    + "INDEX idx_fr_status (status),"
                    + "INDEX idx_fr_order (order_id)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            createTableIfNotExists(conn, "fulfillment_request_items",
                "CREATE TABLE fulfillment_request_items ("
                    + "item_id INT AUTO_INCREMENT PRIMARY KEY,"
                    + "request_id VARCHAR(50) NOT NULL,"
                    + "sku_code VARCHAR(50) NOT NULL,"
                    + "sku_name VARCHAR(200) NOT NULL,"
                    + "qty INT NOT NULL DEFAULT 1,"
                    + "FOREIGN KEY (request_id) REFERENCES fulfillment_requests(request_id) ON DELETE CASCADE"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        }
    }


    // seedFulfillmentTestData() removed — production uses real data, no auto-seeding

    // ── Lazada Orders ──────────────────────────────────────────────

    private void ensureLazadaOrdersTable() throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            createTableIfNotExists(conn, "lazada_orders",
                "CREATE TABLE lazada_orders ("
                    + "lazada_order_id INT AUTO_INCREMENT PRIMARY KEY, "
                    + "lazada_order_id_str VARCHAR(50) NOT NULL UNIQUE COMMENT 'Lazada order_id as string (natural key)', "
                    + "lazada_order_number VARCHAR(50), "
                    + "channel_id INT NOT NULL, "
                    + "status VARCHAR(50), "
                    + "wms_status VARCHAR(30) DEFAULT 'NEW', "
                    + "customer_name VARCHAR(255), "
                    + "customer_phone VARCHAR(50), "
                    + "shipping_address TEXT, "
                    + "shipping_city VARCHAR(100), "
                    + "price DECIMAL(15,2), "
                    + "shipping_fee DECIMAL(15,2), "
                    + "voucher_seller DECIMAL(15,2), "
                    + "voucher_platform DECIMAL(15,2), "
                    + "payment_method VARCHAR(50), "
                    + "buyer_note TEXT, "
                    + "warehouse_id INT DEFAULT 0, "
                    + "assigned_by INT DEFAULT 0, "
                    + "assigned_at DATETIME, "
                    + "package_id VARCHAR(100), "
                    + "tracking_number VARCHAR(100), "
                    + "shipment_provider VARCHAR(100), "
                    + "shipment_provider_code VARCHAR(50), "
                    + "lazada_created_at DATETIME, "
                    + "lazada_updated_at DATETIME, "
                    + "rts_at DATETIME, "
                    + "delivered_at DATETIME, "
                    + "synced_at DATETIME, "
                    + "INDEX idx_lo_channel (channel_id), "
                    + "INDEX idx_lo_status (status), "
                    + "INDEX idx_lo_wms_status (wms_status), "
                    + "INDEX idx_lo_synced (synced_at)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        }
    }

    private void ensureLazadaOrderItemsTable() throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            createTableIfNotExists(conn, "lazada_order_items",
                "CREATE TABLE lazada_order_items ("
                    + "item_id INT AUTO_INCREMENT PRIMARY KEY, "
                    + "lazada_order_id_str VARCHAR(50) NOT NULL, "
                    + "order_item_id VARCHAR(50), "
                    + "sku VARCHAR(100), "
                    + "shop_sku VARCHAR(100), "
                    + "product_name VARCHAR(500), "
                    + "product_image VARCHAR(500), "
                    + "quantity INT NOT NULL DEFAULT 1, "
                    + "paid_price DECIMAL(15,2), "
                    + "item_price DECIMAL(15,2), "
                    + "supply_price DECIMAL(15,4), "
                    + "status VARCHAR(50), "
                    + "product_id INT DEFAULT 0, "
                    + "reserved_qty INT DEFAULT 0, "
                    + "fulfilled_qty INT DEFAULT 0, "
                    + "INDEX idx_loi_order (lazada_order_id_str), "
                    + "INDEX idx_loi_sku (sku), "
                    + "INDEX idx_loi_product (product_id)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        }
    }

    // ── ROP Log (Reorder Point audit trail) ─────────────────────────
    private void ensureProductRopLogTable() throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            createTableIfNotExists(conn, "product_rop_log",
                "CREATE TABLE product_rop_log ("
                    + "log_id INT AUTO_INCREMENT PRIMARY KEY,"
                    + "product_id INT NOT NULL,"
                    + "run_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                    + "lookback_days INT NOT NULL DEFAULT 30,"
                    + "d_avg DECIMAL(12,4) NOT NULL DEFAULT 0,"
                    + "d_max DECIMAL(12,4) NOT NULL DEFAULT 0,"
                    + "l_avg DECIMAL(12,4) NOT NULL DEFAULT 0,"
                    + "l_max DECIMAL(12,4) NOT NULL DEFAULT 0,"
                    + "safety_stock DECIMAL(12,4) NOT NULL DEFAULT 0,"
                    + "rop_before DECIMAL(12,3) NOT NULL DEFAULT 0,"
                    + "rop_after DECIMAL(12,3) NOT NULL DEFAULT 0,"
                    + "triggered_by INT DEFAULT NULL COMMENT 'userId if manually triggered, 0 if scheduled'"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        }
    }
}
