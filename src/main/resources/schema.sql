-- ============================================================
-- WMS Hub — MySQL Schema (Synced with ERD in dbdiagram)
-- Database: wms_hub
-- Charset:  utf8mb4 (full Vietnamese + emoji support)
-- ============================================================

CREATE DATABASE IF NOT EXISTS wms_hub
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE wms_hub;

-- ── NHOM 1: Users & RBAC ───────────────────────────────

CREATE TABLE IF NOT EXISTS roles (
    role_id      INT AUTO_INCREMENT PRIMARY KEY,
    role_name    VARCHAR(50)  NOT NULL UNIQUE,
    description  VARCHAR(255)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO roles (role_name, description) VALUES
    ('ADMIN',           'Quan tri he thong'),
    ('MANAGER',         'Quan ly kinh doanh'),
    ('SALES_STAFF',      'Nhan vien ban hang'),
    ('WAREHOUSE_STAFF', 'Nhan vien kho');

CREATE TABLE IF NOT EXISTS users (
    user_id         INT AUTO_INCREMENT PRIMARY KEY,
    username        VARCHAR(50)  NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    full_name       VARCHAR(100) NOT NULL,
    email           VARCHAR(100) NOT NULL,
    phone           VARCHAR(20)  DEFAULT NULL,
    role            ENUM('ADMIN','MANAGER','SALES_STAFF','WAREHOUSE_STAFF') NOT NULL DEFAULT 'WAREHOUSE_STAFF',
    warehouse_id    INT NOT NULL DEFAULT 1,
    active          TINYINT(1)   NOT NULL DEFAULT 1,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_users_role (role),
    INDEX idx_users_active (active),
    INDEX idx_users_email (email),
    INDEX idx_users_warehouse (warehouse_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ── NHOM 2: Warehouses (Branches) ─────────────────────

CREATE TABLE IF NOT EXISTS warehouses (
    warehouse_id   INT AUTO_INCREMENT PRIMARY KEY,
    warehouse_code VARCHAR(20)  NOT NULL UNIQUE,
    warehouse_name VARCHAR(100) NOT NULL,
    address        VARCHAR(255),
    phone          VARCHAR(20)  DEFAULT NULL,
    capacity       INT          DEFAULT 0,
    active         TINYINT(1)   NOT NULL DEFAULT 1,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Zones within a warehouse (ERD: zones)
CREATE TABLE IF NOT EXISTS zones (
    zone_id     INT AUTO_INCREMENT PRIMARY KEY,
    warehouse_id INT NOT NULL,
    zone_code   VARCHAR(50)  NOT NULL,
    zone_name   VARCHAR(100) NOT NULL,
    zone_type   VARCHAR(30) NOT NULL DEFAULT 'NORMAL',
    description TEXT,
    capacity    INT DEFAULT 0,
    active      TINYINT(1) NOT NULL DEFAULT 1,
    is_default  TINYINT(1) NOT NULL DEFAULT 0,
    FOREIGN KEY (warehouse_id) REFERENCES warehouses(warehouse_id),
    UNIQUE KEY uq_zone_code_wh (zone_code, warehouse_id),
    INDEX idx_zones_wh (warehouse_id),
    INDEX idx_zones_type (zone_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- user-warehouse assignments: N-N via join table (ERD: user_branch_assignments)
-- Moved after warehouses table to resolve FK dependency
CREATE TABLE IF NOT EXISTS user_warehouse_assignments (
    assignment_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id       INT NOT NULL,
    warehouse_id  INT NOT NULL,
    is_primary    TINYINT(1) NOT NULL DEFAULT 0,
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_user_warehouse (user_id, warehouse_id),
    FOREIGN KEY (user_id)      REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (warehouse_id) REFERENCES warehouses(warehouse_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ── NHOM 3: Products & Categories ──────────────────────

CREATE TABLE IF NOT EXISTS categories (
    category_id   INT AUTO_INCREMENT PRIMARY KEY,
    parent_id     INT DEFAULT NULL,
    category_code VARCHAR(10)  NOT NULL UNIQUE COMMENT 'Ma dinh danh 3-4 ky tu, viet HOA, bat bien sau khi tao',
    category_name VARCHAR(100) NOT NULL,
    description   VARCHAR(255) DEFAULT NULL,
    level_depth   INT DEFAULT 0,
    is_immutable  TINYINT(1) NOT NULL DEFAULT 0 COMMENT '1 = da lock, khong cho sua category_code',
    active        TINYINT(1) NOT NULL DEFAULT 1 COMMENT '1 = dang hoat dong, 0 = ngung hoat dong',
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (parent_id) REFERENCES categories(category_id) ON DELETE SET NULL,
    INDEX idx_cat_parent (parent_id),
    INDEX idx_cat_active (active),
    INDEX idx_cat_code (category_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Products (ERD: products)
CREATE TABLE IF NOT EXISTS products (
    product_id      INT AUTO_INCREMENT PRIMARY KEY,
    category_id     INT,
    sku_code        VARCHAR(50)  NOT NULL UNIQUE,
    product_name    VARCHAR(255) NOT NULL,
    base_price     DECIMAL(15,2) NOT NULL DEFAULT 0,
    attributes_text VARCHAR(255),
    weight_kg       DECIMAL(8,3),
    is_new_arrival  TINYINT(1) NOT NULL DEFAULT 0,
    active          TINYINT(1)   NOT NULL DEFAULT 1,
    created_by      INT,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (category_id) REFERENCES categories(category_id) ON DELETE SET NULL,
    FOREIGN KEY (created_by)  REFERENCES users(user_id),
    INDEX idx_products_category (category_id),
    INDEX idx_products_active (active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Product images (ERD: product_images)
CREATE TABLE IF NOT EXISTS product_images (
    image_id    INT AUTO_INCREMENT PRIMARY KEY,
    product_id  INT NOT NULL,
    image_url   VARCHAR(500) NOT NULL,
    is_primary  TINYINT(1) NOT NULL DEFAULT 0,
    sort_order  INT NOT NULL DEFAULT 0,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (product_id) REFERENCES products(product_id) ON DELETE CASCADE,
    INDEX idx_img_product_primary (product_id, is_primary)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ── NHOM 4: Sales Channels ──────────────────────────────

CREATE TABLE IF NOT EXISTS channels (
    channel_id     INT AUTO_INCREMENT PRIMARY KEY,
    channel_name   VARCHAR(100) NOT NULL,
    platform       VARCHAR(50)  NOT NULL,
    api_url        VARCHAR(255),
    api_key        VARCHAR(255),
    app_secret     VARCHAR(255),
    webhook_secret VARCHAR(255),
    webhook_callback_url VARCHAR(512) DEFAULT NULL COMMENT 'URL Lazada calls on order/update events',
    buffer_stock   DECIMAL(12,3) DEFAULT 0.00,
    is_active      TINYINT(1)  DEFAULT 1,
    access_token   TEXT,
    refresh_token  TEXT,
    token_expires_at DATETIME DEFAULT NULL COMMENT 'UTC timestamp when access_token expires. NULL = unknown/never.',
    last_order_sync_at    DATETIME DEFAULT NULL COMMENT 'Last successful order sync via scheduler.',
    last_product_sync_at  DATETIME DEFAULT NULL COMMENT 'Last successful product sync via scheduler.',
    created_at     DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Migration: add token_expires_at column for auto token refresh (UC-B2C07)
-- Safe to re-run on existing databases — MariaDB/MySQL ignores duplicate columns
-- Split into separate statements for MySQL 8 compatibility
SET @dbname = DATABASE();
SET @tablename = 'channels';
SET @columnname = 'token_expires_at';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) > 0,
    'SELECT 1',
    'ALTER TABLE channels ADD COLUMN token_expires_at DATETIME DEFAULT NULL COMMENT ''UTC timestamp when access_token expires. NULL = unknown/never.'''
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

SET @columnname = 'last_order_sync_at';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) > 0,
    'SELECT 1',
    'ALTER TABLE channels ADD COLUMN last_order_sync_at DATETIME DEFAULT NULL COMMENT ''Last successful order sync via scheduler.'''
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- Migration: add last_product_sync_at column (UC-B2C07 product sync)
SET @tablename = 'channels';
SET @columnname = 'last_product_sync_at';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @columnname) > 0,
    'SELECT 1',
    'ALTER TABLE channels ADD COLUMN last_product_sync_at DATETIME DEFAULT NULL COMMENT ''Last successful product sync via scheduler.'''
));
PREPARE alterIfNotExists2 FROM @preparedStatement;
EXECUTE alterIfNotExists2;
DEALLOCATE PREPARE alterIfNotExists2;

-- Shipping carriers (dynamic, used by Sales filters and order processing)
CREATE TABLE IF NOT EXISTS shipping_carriers (
    carrier_id    INT AUTO_INCREMENT PRIMARY KEY,
    carrier_code  VARCHAR(50) NOT NULL UNIQUE,
    carrier_name  VARCHAR(100) NOT NULL,
    platform      VARCHAR(50) DEFAULT NULL,
    priority      INT NOT NULL DEFAULT 0,
    is_active     TINYINT(1) NOT NULL DEFAULT 1,
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_carriers_active_priority (is_active, priority),
    INDEX idx_carriers_platform (platform)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Seed default carriers (idempotent)
INSERT IGNORE INTO shipping_carriers (carrier_code, carrier_name, platform, priority) VALUES
    ('SPX',  'SPX Express',    'Shopee', 10),
    ('LZE',  'Lazada Express', 'Lazada', 20),
    ('TKT',  'TikTok Express', 'TikTok', 30),
    ('VTP',  'Viettel Post',   NULL,     40);

-- Channel-specific products (ERD: channel_products)
CREATE TABLE IF NOT EXISTS channel_products (
    id               INT AUTO_INCREMENT PRIMARY KEY,
    channel_id       INT NOT NULL,
    product_id       INT NOT NULL,
    channel_sku_code VARCHAR(100),
    channel_price    DECIMAL(15,2) NOT NULL DEFAULT 0,
    channel_stock    DECIMAL(12,3) NOT NULL DEFAULT 0,
    status           ENUM('ACTIVE','INACTIVE','PENDING') DEFAULT 'ACTIVE',
    listed_at        DATETIME,
    updated_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (channel_id) REFERENCES channels(channel_id) ON DELETE CASCADE,
    FOREIGN KEY (product_id)  REFERENCES products(product_id) ON DELETE CASCADE,
    UNIQUE KEY uq_channel_product (channel_id, product_id),
    INDEX idx_cp_external (channel_sku_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- SKU-to-channel mapping (ERD: sku_mappings)
CREATE TABLE IF NOT EXISTS sku_mappings (
    mapping_id    INT AUTO_INCREMENT PRIMARY KEY,
    sku_id        INT NOT NULL,
    channel_id    INT NOT NULL,
    external_sku  VARCHAR(100),
    seller_sku    VARCHAR(100),
    sync_status   ENUM('SYNCED','PENDING','ERROR') DEFAULT 'PENDING',
    last_sync_at  DATETIME,
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (sku_id)    REFERENCES products(product_id),
    FOREIGN KEY (channel_id) REFERENCES channels(channel_id),
    UNIQUE KEY uq_sku_channel (sku_id, channel_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Webhook event log (ERD: webhook_logs)
CREATE TABLE IF NOT EXISTS webhook_logs (
    log_id      INT AUTO_INCREMENT PRIMARY KEY,
    channel_id  INT,
    event_type  VARCHAR(50) NOT NULL,
    payload     TEXT,
    status      ENUM('SUCCESS','FAILED','PENDING') NOT NULL DEFAULT 'PENDING',
    error_trace TEXT,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    message_id  VARCHAR(100) NULL,
    request_ip  VARCHAR(50) NULL,
    request_signature VARCHAR(255) NULL,
    retry_count INT NOT NULL DEFAULT 0,
    processed_at DATETIME NULL,
    FOREIGN KEY (channel_id) REFERENCES channels(channel_id) ON DELETE SET NULL,
    INDEX idx_wl_event (event_type),
    INDEX idx_wl_status (status),
    INDEX idx_wl_channel (channel_id),
    INDEX idx_message_id (message_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Stores marketplace orders that could not be matched to an internal SKU.
-- Sales staff review this table to create mappings for unknown SKUs.
CREATE TABLE IF NOT EXISTS mapping_exceptions (
    exception_id  INT AUTO_INCREMENT PRIMARY KEY,
    channel_id    INT NOT NULL,
    external_sku  VARCHAR(100) NOT NULL,
    order_code    VARCHAR(100),
    reason        VARCHAR(255),
    resolved      TINYINT(1) NOT NULL DEFAULT 0,
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at   DATETIME,
    FOREIGN KEY (channel_id) REFERENCES channels(channel_id) ON DELETE CASCADE,
    INDEX idx_me_channel (channel_id),
    INDEX idx_me_resolved (resolved)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Lazada sync log
CREATE TABLE IF NOT EXISTS lazada_sync_log (
    log_id         INT AUTO_INCREMENT PRIMARY KEY,
    channel_id     INT,
    sync_type      VARCHAR(50),
    status         ENUM('SUCCESS','FAILED') NOT NULL,
    request_data   TEXT,
    response_data  TEXT,
    error_msg      TEXT,
    created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (channel_id) REFERENCES channels(channel_id) ON DELETE SET NULL,
    INDEX idx_lsl_status (status),
    INDEX idx_lsl_channel (channel_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Lazada's category tree (/category/tree/get) mirrored locally for product-push wizard.
-- Used by SalesChannelProductsServlet.loadLazadaLeaves (UC-B2C09).
CREATE TABLE IF NOT EXISTS lazada_categories (
    id                    INT AUTO_INCREMENT PRIMARY KEY,
    channel_id            INT NOT NULL,
    lazada_category_id    BIGINT NOT NULL,
    parent_id             BIGINT,
    name                  VARCHAR(255) NOT NULL,
    is_leaf               TINYINT(1) NOT NULL DEFAULT 0,
    has_variation         TINYINT(1) NOT NULL DEFAULT 0,
    depth                 INT NOT NULL DEFAULT 0,
    FOREIGN KEY (channel_id) REFERENCES channels(channel_id) ON DELETE CASCADE,
    INDEX idx_lc_channel (channel_id),
    INDEX idx_lc_leaf     (channel_id, is_leaf)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Maps a WMS category to one or more Lazada leaf categories. Lets sales staff
-- pre-define which Lazada leaves each WMS category corresponds to, so the
-- publish wizard can auto-suggest a leaf rather than forcing the user to
-- search 2890 leaves every push.
CREATE TABLE IF NOT EXISTS category_mappings (
    mapping_id        INT AUTO_INCREMENT PRIMARY KEY,
    channel_id        INT NOT NULL,
    wms_category_id   INT NOT NULL,
    lazada_category_id BIGINT NOT NULL,
    lazada_name       VARCHAR(255) NOT NULL,
    is_primary        TINYINT(1) NOT NULL DEFAULT 0,
    created_by        INT,
    created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (channel_id) REFERENCES channels(channel_id) ON DELETE CASCADE,
    FOREIGN KEY (wms_category_id) REFERENCES categories(category_id) ON DELETE CASCADE,
    UNIQUE KEY uk_mappings_channel_wms_lazada (channel_id, wms_category_id, lazada_category_id),
    INDEX idx_mappings_wms (wms_category_id),
    INDEX idx_mappings_channel (channel_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Audit log for every stock push to Lazada (BR-02 / LazadaInventoryPushScheduler)
CREATE TABLE IF NOT EXISTS lazada_stock_push_log (
    log_id         INT AUTO_INCREMENT PRIMARY KEY,
    channel_id     INT,
    product_id     INT,
    seller_sku     VARCHAR(100),
    qty_on_hand    DECIMAL(12,3),
    qty_available  DECIMAL(12,3),
    holding        DECIMAL(12,3),
    buffer_stock   DECIMAL(12,3),
    push_qty       DECIMAL(12,3),
    status         VARCHAR(20),
    error_message  TEXT,
    created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_lspl_channel (channel_id),
    INDEX idx_lspl_sku (seller_sku),
    INDEX idx_lspl_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ── NHOM 5: Inventory ───────────────────────────────────

CREATE TABLE IF NOT EXISTS inventory (
    inventory_id   INT AUTO_INCREMENT PRIMARY KEY,
    product_id     INT NOT NULL,
    warehouse_id   INT NOT NULL,
    qty_on_hand   DECIMAL(12,3) NOT NULL DEFAULT 0,
    holding        DECIMAL(12,3) NOT NULL DEFAULT 0,
    qty_available  DECIMAL(12,3) NOT NULL DEFAULT 0,
    reorder_point  DECIMAL(12,3) DEFAULT NULL,
    updated_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_product_warehouse (product_id, warehouse_id),
    FOREIGN KEY (product_id)   REFERENCES products(product_id) ON DELETE CASCADE,
    FOREIGN KEY (warehouse_id) REFERENCES warehouses(warehouse_id) ON DELETE CASCADE,
    INDEX idx_inv_product (product_id),
    INDEX idx_inv_warehouse (warehouse_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS inventory_ledger (
    ledger_id        INT AUTO_INCREMENT PRIMARY KEY,
    inventory_id     INT NOT NULL,
    product_id       INT NOT NULL,
    warehouse_id     INT NOT NULL,
    transaction_type ENUM('INBOUND','OUTBOUND','ADJUSTMENT','TRANSFER_IN','TRANSFER_OUT') NOT NULL,
    ref_document_id  INT,
    qty_change       DECIMAL(12,3) NOT NULL,
    avail_change     DECIMAL(12,3) NOT NULL,
    timestamp        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by       INT,
    note             TEXT,
    FOREIGN KEY (inventory_id)  REFERENCES inventory(inventory_id) ON DELETE CASCADE,
    FOREIGN KEY (product_id)    REFERENCES products(product_id) ON DELETE CASCADE,
    FOREIGN KEY (warehouse_id)  REFERENCES warehouses(warehouse_id) ON DELETE CASCADE,
    FOREIGN KEY (created_by)    REFERENCES users(user_id),
    INDEX idx_ledger_inventory (inventory_id),
    INDEX idx_ledger_product (product_id),
    INDEX idx_ledger_type (transaction_type),
    INDEX idx_ledger_timestamp (timestamp)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ── NHOM 6: Orders ─────────────────────────────────────

CREATE TABLE IF NOT EXISTS orders (
    order_id           INT AUTO_INCREMENT PRIMARY KEY,
    channel_id         INT,
    channel_order_id   VARCHAR(100),
    warehouse_id       INT NOT NULL,
    order_status       ENUM('PENDING','CONFIRMED','PICKING','PACKED','SHIPPED','DELIVERED','CANCELLED','RETURNED')
                       NOT NULL DEFAULT 'PENDING',
    total_actual_paid  DECIMAL(15,2) NOT NULL DEFAULT 0,
    fee_breakdown_json TEXT,
    sync_status        ENUM('PENDING','SYNCED','FAILED') DEFAULT 'PENDING',
    created_by         INT,
    customer_id        INT,
    created_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (channel_id)   REFERENCES channels(channel_id) ON DELETE SET NULL,
    FOREIGN KEY (warehouse_id)  REFERENCES warehouses(warehouse_id),
    FOREIGN KEY (created_by)    REFERENCES users(user_id),
    FOREIGN KEY (customer_id)   REFERENCES users(user_id),
    INDEX idx_orders_status (order_status),
    INDEX idx_orders_channel (channel_id),
    INDEX idx_orders_channel_oid (channel_order_id),
    INDEX idx_orders_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS order_items (
    order_item_id      INT AUTO_INCREMENT PRIMARY KEY,
    order_id           INT NOT NULL,
    product_id         INT NOT NULL,
    quantity           DECIMAL(12,3) NOT NULL DEFAULT 1,
    unit_price        DECIMAL(15,2) NOT NULL DEFAULT 0,
    seller_discount    DECIMAL(15,2) DEFAULT 0,
    platform_discount  DECIMAL(15,2) DEFAULT 0,
    item_shipping_fee  DECIMAL(15,2) DEFAULT 0,
    actual_price       DECIMAL(15,2) NOT NULL DEFAULT 0,
    FOREIGN KEY (order_id)  REFERENCES orders(order_id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES products(product_id) ON DELETE CASCADE,
    INDEX idx_oi_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Order shipping details (ERD: order_shipping_details)
CREATE TABLE IF NOT EXISTS order_shipping_details (
    shipping_id      INT AUTO_INCREMENT PRIMARY KEY,
    order_id         INT NOT NULL UNIQUE,
    recipient_name   VARCHAR(100) NOT NULL,
    shipping_address TEXT NOT NULL,
    courier_name     VARCHAR(50),
    waybill_code     VARCHAR(100),
    shipping_status  ENUM('PENDING','PICKED_UP','IN_TRANSIT','OUT_FOR_DELIVERY','DELIVERED','RETURNED')
                     NOT NULL DEFAULT 'PENDING',
    created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (order_id) REFERENCES orders(order_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Shipping labels
CREATE TABLE IF NOT EXISTS shipping_labels (
    label_id     INT AUTO_INCREMENT PRIMARY KEY,
    order_id     INT NOT NULL,
    outbound_id  INT,
    carrier      VARCHAR(50),
    tracking_no  VARCHAR(100),
    label_url    VARCHAR(255),
    printed      TINYINT(1) DEFAULT 0,
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (order_id)  REFERENCES orders(order_id) ON DELETE CASCADE,
    INDEX idx_sl_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ── NHOM 7: Inbound / Warehouse Receipts ────────────────

CREATE TABLE IF NOT EXISTS warehouse_receipts (
    receipt_id    INT AUTO_INCREMENT PRIMARY KEY,
    receipt_code  VARCHAR(50) NOT NULL UNIQUE,
    warehouse_id  INT NOT NULL,
    receipt_type  ENUM('PURCHASE','RETURN','TRANSFER') NOT NULL DEFAULT 'PURCHASE',
    supplier_name VARCHAR(255),
    created_by    INT NOT NULL,
    copied_from_id INT,
    status        ENUM('DRAFT','APPROVED','CANCELLED') NOT NULL DEFAULT 'DRAFT',
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (warehouse_id)  REFERENCES warehouses(warehouse_id),
    FOREIGN KEY (created_by)     REFERENCES users(user_id),
    FOREIGN KEY (copied_from_id) REFERENCES warehouse_receipts(receipt_id) ON DELETE SET NULL,
    INDEX idx_wr_status (status),
    INDEX idx_wr_warehouse (warehouse_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS receipt_details (
    detail_id   INT AUTO_INCREMENT PRIMARY KEY,
    receipt_id  INT NOT NULL,
    product_id  INT NOT NULL,
    quantity    DECIMAL(12,3) NOT NULL,
    unit_cost   DECIMAL(15,2) DEFAULT NULL,
    note        VARCHAR(255),
    FOREIGN KEY (receipt_id) REFERENCES warehouse_receipts(receipt_id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES products(product_id) ON DELETE CASCADE,
    INDEX idx_rd_receipt (receipt_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ── NHÓM 15: Suppliers ───────────────────────────────

CREATE TABLE IF NOT EXISTS suppliers (
    supplier_id       INT AUTO_INCREMENT PRIMARY KEY,
    supplier_code     VARCHAR(20) NOT NULL UNIQUE COMMENT 'Ma NCC, unique',
    name              VARCHAR(255) NOT NULL COMMENT 'Ten cong ty',
    contact_person    VARCHAR(100) DEFAULT NULL COMMENT 'Nguoi lien he',
    phone             VARCHAR(20) DEFAULT NULL,
    email             VARCHAR(100) DEFAULT NULL,
    address           VARCHAR(500) DEFAULT NULL,
    credit_limit      DECIMAL(15,2) DEFAULT 0.00 COMMENT 'Han muc no',
    payment_terms     VARCHAR(50) DEFAULT NULL COMMENT 'Thoi han thanh toan, vd: "Net 30", "COD", "CIA", "Immediate"',
    status            ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_supplier_code (supplier_code),
    INDEX idx_supplier_status (status),
    INDEX idx_supplier_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Legacy alias (for existing code compatibility)
CREATE TABLE IF NOT EXISTS inbound_orders (
    inbound_id        INT AUTO_INCREMENT PRIMARY KEY,
    inbound_code      VARCHAR(30) NOT NULL UNIQUE,
    warehouse_id      INT NOT NULL,
    zone_id           INT DEFAULT NULL COMMENT 'FK den zones.zone_id (khu vuc nhan hang trong kho)',
    supplier          VARCHAR(100),
    supplier_id       INT DEFAULT NULL COMMENT 'FK mem toi suppliers.supplier_id (NULL neu chua lien ket)',
    supplier_address  VARCHAR(255),
    supplier_phone    VARCHAR(50),
    po_reference      VARCHAR(50),
    status            ENUM('PENDING','PURCHASED','IN_PROGRESS','CONFIRMED','RECEIVED','CANCELLED') NOT NULL DEFAULT 'PENDING',
    received_by       INT,
    delivery_person   VARCHAR(100) COMMENT 'Ten nguoi giao hang / tai xe',
    delivery_phone    VARCHAR(50) COMMENT 'SDT nguoi giao',
    note              TEXT,
    expected_date     DATE,
    received_date     DATE COMMENT 'Ngay nhap hang thuc te (auto-fill luc tao phieu)',
    payment_terms     VARCHAR(50),
    created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    received_at       DATETIME,
    FOREIGN KEY (warehouse_id) REFERENCES warehouses(warehouse_id),
    FOREIGN KEY (received_by)  REFERENCES users(user_id),
    FOREIGN KEY (zone_id)      REFERENCES zones(zone_id) ON DELETE SET NULL,
    INDEX idx_inbound_status (status),
    INDEX idx_inbound_supplier (supplier_id),
    CONSTRAINT fk_inbound_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers(supplier_id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS inbound_items (
    inbound_item_id INT AUTO_INCREMENT PRIMARY KEY,
    inbound_id      INT NOT NULL,
    product_id      INT NOT NULL,
    expected_qty    DECIMAL(12,3) NOT NULL DEFAULT 0,
    received_qty    DECIMAL(12,3) NOT NULL DEFAULT 0,
    accepted_qty    DECIMAL(12,3) NOT NULL DEFAULT 0,
    rejected_qty    DECIMAL(12,3) NOT NULL DEFAULT 0,
    reject_reason   VARCHAR(255) COMMENT 'Ly do tra hang NCC: Hang mop meo, Sai mau/size, Het han, Hong van chuyen',
    unit_cost       DECIMAL(15,2) NOT NULL DEFAULT 0,
    lot_number      VARCHAR(50),
    expiry_date     DATE,
    notes           VARCHAR(255),
    FOREIGN KEY (inbound_id) REFERENCES inbound_orders(inbound_id) ON DELETE CASCADE,
    FOREIGN KEY (product_id)  REFERENCES products(product_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS receipt_notes (
    receipt_id   INT AUTO_INCREMENT PRIMARY KEY,
    inbound_id   INT NOT NULL,
    warehouse_id INT NOT NULL,
    received_by  INT,
    note         TEXT,
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (inbound_id)  REFERENCES inbound_orders(inbound_id) ON DELETE CASCADE,
    FOREIGN KEY (warehouse_id) REFERENCES warehouses(warehouse_id),
    FOREIGN KEY (received_by) REFERENCES users(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ── NHOM 8: Outbound / Warehouse Issues ─────────────────

CREATE TABLE IF NOT EXISTS warehouse_issues (
    issue_id       INT AUTO_INCREMENT PRIMARY KEY,
    issue_code     VARCHAR(50) NOT NULL UNIQUE,
    warehouse_id   INT NOT NULL,
    issue_type     ENUM('ORDER','SCRAP','TRANSFER') NOT NULL,
    ref_order_id   INT,
    transfer_id    INT,
    dest_zone_id   INT,
    created_by     INT NOT NULL,
    copied_from_id INT,
    status         ENUM('DRAFT','APPROVED','CANCELLED') NOT NULL DEFAULT 'DRAFT',
    created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (warehouse_id)   REFERENCES warehouses(warehouse_id),
    FOREIGN KEY (created_by)     REFERENCES users(user_id),
    FOREIGN KEY (copied_from_id) REFERENCES warehouse_issues(issue_id) ON DELETE SET NULL,
    INDEX idx_wi_status (status),
    INDEX idx_wi_type (issue_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS issue_details (
    detail_id   INT AUTO_INCREMENT PRIMARY KEY,
    issue_id    INT NOT NULL,
    product_id  INT NOT NULL,
    quantity    DECIMAL(12,3) NOT NULL,
    note        VARCHAR(255),
    FOREIGN KEY (issue_id)   REFERENCES warehouse_issues(issue_id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES products(product_id) ON DELETE CASCADE,
    INDEX idx_id_issue (issue_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Legacy alias (for existing code compatibility)
CREATE TABLE IF NOT EXISTS outbound_orders (
    outbound_id   INT AUTO_INCREMENT PRIMARY KEY,
    order_id      INT NOT NULL,
    warehouse_id   INT NOT NULL,
    status        ENUM('PENDING','PICKING','PACKED','SHIPPED','DELIVERED','CANCELLED')
                  NOT NULL DEFAULT 'PENDING',
    picked_by     INT,
    shipped_at    DATETIME,
    note          TEXT,
    version       INT NOT NULL DEFAULT 0,
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (order_id)    REFERENCES orders(order_id) ON DELETE CASCADE,
    FOREIGN KEY (warehouse_id) REFERENCES warehouses(warehouse_id),
    FOREIGN KEY (picked_by)   REFERENCES users(user_id),
    INDEX idx_out_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS outbound_items (
    outbound_item_id INT AUTO_INCREMENT PRIMARY KEY,
    outbound_id      INT NOT NULL,
    product_id       INT NOT NULL,
    qty              DECIMAL(12,3) NOT NULL DEFAULT 1,
    picked_qty       DECIMAL(12,3) NOT NULL DEFAULT 0,
    FOREIGN KEY (outbound_id) REFERENCES outbound_orders(outbound_id) ON DELETE CASCADE,
    FOREIGN KEY (product_id)  REFERENCES products(product_id) ON DELETE CASCADE,
    INDEX idx_oi_outbound (outbound_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS picking_sheets (
    sheet_id      INT AUTO_INCREMENT PRIMARY KEY,
    outbound_id   INT NOT NULL,
    picker_id     INT,
    status        ENUM('PENDING','IN_PROGRESS','COMPLETED') DEFAULT 'PENDING',
    started_at   DATETIME,
    completed_at DATETIME,
    FOREIGN KEY (outbound_id) REFERENCES outbound_orders(outbound_id),
    FOREIGN KEY (picker_id)   REFERENCES users(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS delivery_notes (
    delivery_id     INT AUTO_INCREMENT PRIMARY KEY,
    outbound_id     INT NOT NULL,
    delivered_by    INT,
    delivery_date   DATETIME,
    recipient_name  VARCHAR(100),
    recipient_note  TEXT,
    FOREIGN KEY (outbound_id)  REFERENCES outbound_orders(outbound_id),
    FOREIGN KEY (delivered_by) REFERENCES users(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ── NHOM 9: RMA / Returns ─────────────────────────────

CREATE TABLE IF NOT EXISTS rma_requests (
    rma_id            INT AUTO_INCREMENT PRIMARY KEY,
    order_id          INT NOT NULL,
    channel_return_id VARCHAR(100),
    return_waybill    VARCHAR(100),
    rma_code          VARCHAR(50) NOT NULL UNIQUE,
    status            ENUM('PENDING','APPROVED','DISPUTED','RESOLVED') NOT NULL DEFAULT 'PENDING',
    return_reason     VARCHAR(255) NOT NULL,
    zone_id           INT,
    requested_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    returned_at       DATETIME,
    FOREIGN KEY (order_id) REFERENCES orders(order_id) ON DELETE CASCADE,
    FOREIGN KEY (zone_id)   REFERENCES zones(zone_id) ON DELETE SET NULL,
    INDEX idx_rma_status (status),
    INDEX idx_rma_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS rma_items (
    rma_item_id            INT AUTO_INCREMENT PRIMARY KEY,
    rma_id                 INT NOT NULL,
    product_id             INT NOT NULL,
    channel_return_item_id VARCHAR(100),
    quantity               DECIMAL(12,3) NOT NULL DEFAULT 1,
    refund_amount          DECIMAL(15,2),
    FOREIGN KEY (rma_id)    REFERENCES rma_requests(rma_id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES products(product_id) ON DELETE CASCADE,
    INDEX idx_rmai_rma (rma_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS qc_inspections (
    qc_id             INT AUTO_INCREMENT PRIMARY KEY,
    rma_item_id       INT NOT NULL,
    inspected_by      INT NOT NULL,
    good_quantity     DECIMAL(12,3) NOT NULL DEFAULT 0,
    good_zone_id      INT,
    damaged_quantity  DECIMAL(12,3) NOT NULL DEFAULT 0,
    damaged_zone_id   INT,
    notes             TEXT,
    inspected_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (rma_item_id)  REFERENCES rma_items(rma_item_id) ON DELETE CASCADE,
    FOREIGN KEY (inspected_by) REFERENCES users(user_id),
    FOREIGN KEY (good_zone_id)  REFERENCES zones(zone_id) ON DELETE SET NULL,
    FOREIGN KEY (damaged_zone_id) REFERENCES zones(zone_id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Legacy alias (for existing code compatibility)
CREATE TABLE IF NOT EXISTS return_orders (
    return_id     INT AUTO_INCREMENT PRIMARY KEY,
    return_code   VARCHAR(50),
    order_id       INT,
    outbound_id   INT,
    customer_name  VARCHAR(100),
    reason         VARCHAR(255),
    status         ENUM('RECEIVED','INSPECTING','PASS','FAIL','RESTOCKED','SCRAPPED') DEFAULT 'RECEIVED',
    warehouse_id   INT NOT NULL,
    created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (order_id)    REFERENCES orders(order_id) ON DELETE SET NULL,
    FOREIGN KEY (outbound_id) REFERENCES outbound_orders(outbound_id) ON DELETE SET NULL,
    FOREIGN KEY (warehouse_id) REFERENCES warehouses(warehouse_id),
    INDEX idx_ro_status (status),
    INDEX idx_ro_code (return_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS qc_records (
    qc_id       INT AUTO_INCREMENT PRIMARY KEY,
    return_id   INT NOT NULL,
    product_id  INT,
    decision    ENUM('PASS','FAIL') NOT NULL,
    qc_notes    TEXT,
    qc_by       INT,
    qc_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (return_id) REFERENCES return_orders(return_id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES products(product_id) ON DELETE SET NULL,
    FOREIGN KEY (qc_by)      REFERENCES users(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS scrap_records (
    scrap_id    INT AUTO_INCREMENT PRIMARY KEY,
    return_id   INT NOT NULL,
    product_id  INT,
    qty         DECIMAL(12,3) NOT NULL DEFAULT 1,
    reason      VARCHAR(255),
    scrap_by    INT,
    scrap_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (return_id) REFERENCES return_orders(return_id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES products(product_id) ON DELETE SET NULL,
    FOREIGN KEY (scrap_by)   REFERENCES users(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ── NHOM 10: Stocktakes ────────────────────────────────

CREATE TABLE IF NOT EXISTS physical_inventories (
    inventory_check_id INT AUTO_INCREMENT PRIMARY KEY,
    check_code         VARCHAR(50) NOT NULL UNIQUE,
    warehouse_id       INT NOT NULL,
    created_by         INT NOT NULL,
    status             ENUM('DRAFT','IN_PROGRESS','APPROVED') NOT NULL DEFAULT 'DRAFT',
    note               TEXT,
    created_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (warehouse_id) REFERENCES warehouses(warehouse_id),
    FOREIGN KEY (created_by)   REFERENCES users(user_id),
    INDEX idx_pi_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS physical_inventory_details (
    check_detail_id    INT AUTO_INCREMENT PRIMARY KEY,
    inventory_check_id INT NOT NULL,
    product_id         INT NOT NULL,
    system_qty         DECIMAL(12,3) NOT NULL DEFAULT 0,
    actual_qty         DECIMAL(12,3) DEFAULT NULL,
    delta_qty          DECIMAL(12,3) DEFAULT NULL,
    variance_reason    VARCHAR(255),
    lot_number         VARCHAR(50),
    counted_by         INT,
    counted_at         DATETIME,
    FOREIGN KEY (inventory_check_id) REFERENCES physical_inventories(inventory_check_id) ON DELETE CASCADE,
    FOREIGN KEY (product_id)        REFERENCES products(product_id) ON DELETE CASCADE,
    FOREIGN KEY (counted_by)        REFERENCES users(user_id),
    INDEX idx_pid_check (inventory_check_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Legacy aliases (for existing code compatibility)
CREATE TABLE IF NOT EXISTS stocktakes (
    stocktake_id    INT AUTO_INCREMENT PRIMARY KEY,
    stocktake_code  VARCHAR(30) NOT NULL UNIQUE,
    warehouse_id     INT NOT NULL,
    status          ENUM('PLANNED','IN_PROGRESS','COMPLETED','CANCELLED') DEFAULT 'PLANNED',
    counted_by      INT,
    approved_by     INT,
    note            TEXT,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at    DATETIME,
    FOREIGN KEY (warehouse_id) REFERENCES warehouses(warehouse_id),
    FOREIGN KEY (counted_by)  REFERENCES users(user_id),
    FOREIGN KEY (approved_by) REFERENCES users(user_id),
    INDEX idx_st_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS stocktake_items (
    item_id      INT AUTO_INCREMENT PRIMARY KEY,
    stocktake_id INT NOT NULL,
    product_id   INT NOT NULL,
    system_qty   INT NOT NULL DEFAULT 0,
    counted_qty INT DEFAULT NULL,
    variance     INT DEFAULT NULL,
    counted_by   INT,
    counted_at   DATETIME,
    FOREIGN KEY (stocktake_id) REFERENCES stocktakes(stocktake_id) ON DELETE CASCADE,
    FOREIGN KEY (product_id)   REFERENCES products(product_id) ON DELETE CASCADE,
    FOREIGN KEY (counted_by)   REFERENCES users(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ── NHOM 11: Stock Transfers ──────────────────────────

CREATE TABLE IF NOT EXISTS stock_transfers (
    transfer_id      INT AUTO_INCREMENT PRIMARY KEY,
    transfer_code    VARCHAR(50) NOT NULL UNIQUE,
    from_warehouse_id INT NOT NULL,
    to_warehouse_id   INT NOT NULL,
    created_by       INT NOT NULL,
    approved_by      INT,
    status           ENUM('DRAFT','IN_TRANSIT','RECEIVED','CANCELLED') NOT NULL DEFAULT 'DRAFT',
    note             TEXT,
    created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at     DATETIME,
    FOREIGN KEY (from_warehouse_id) REFERENCES warehouses(warehouse_id),
    FOREIGN KEY (to_warehouse_id)   REFERENCES warehouses(warehouse_id),
    FOREIGN KEY (created_by)       REFERENCES users(user_id),
    FOREIGN KEY (approved_by)      REFERENCES users(user_id),
    INDEX idx_st_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS stock_transfer_items (
    transfer_item_id INT AUTO_INCREMENT PRIMARY KEY,
    transfer_id      INT NOT NULL,
    product_id       INT NOT NULL,
    shipped_qty      DECIMAL(12,3) NOT NULL,
    received_qty     DECIMAL(12,3) DEFAULT NULL,
    lot_number       VARCHAR(50),
    notes            VARCHAR(255),
    FOREIGN KEY (transfer_id) REFERENCES stock_transfers(transfer_id) ON DELETE CASCADE,
    FOREIGN KEY (product_id)  REFERENCES products(product_id) ON DELETE CASCADE,
    INDEX idx_sti_transfer (transfer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Legacy alias (for existing code compatibility)
CREATE TABLE IF NOT EXISTS transfer_details (
    transfer_detail_id INT AUTO_INCREMENT PRIMARY KEY,
    transfer_id       INT NOT NULL,
    product_id       INT NOT NULL,
    qty              INT NOT NULL,
    FOREIGN KEY (transfer_id) REFERENCES stock_transfers(transfer_id) ON DELETE CASCADE,
    FOREIGN KEY (product_id)  REFERENCES products(product_id) ON DELETE CASCADE,
    INDEX idx_td_transfer (transfer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Default admin user: quanpm / Admin@123 (BCrypt hash)
INSERT IGNORE INTO users (username, password_hash, full_name, email, phone, role)
VALUES ('quanpm',
        '$2a$12$ezv1v4fjwnwMSYQ4DvPHN./NuNfVdwEzGbHuUvlbsabeCZqrLkzxe',
        'Phạm Minh Quân', 'pmq07072005@gmail.com', '0987654321', 'ADMIN');


-- Categories: managed via the app UI at /sales/categories

-- ============================================================
-- Indexes for Performance Optimization
-- ============================================================

-- inventory_ledger: range scans by sku+warehouse+time (inventory dashboard, stock history)
CREATE INDEX idx_ledger_sku_wh_time
    ON inventory_ledger (product_id, warehouse_id, timestamp);

-- inventory_ledger: filter by transaction type per sku (e.g., all INBOUND for a SKU)
CREATE INDEX idx_ledger_sku_type
    ON inventory_ledger (product_id, transaction_type);

-- orders: filter by customer + date range (customer order history)
CREATE INDEX idx_orders_customer_date
    ON orders (customer_id, created_at);

-- orders: filter by status + channel (dashboard KPIs, order queue)
CREATE INDEX idx_orders_status_channel
    ON orders (order_status, channel_id);

-- inbound_orders: filter by status + date (inbound processing queue)
CREATE INDEX idx_inbound_status_date
    ON inbound_orders (status, created_at);

-- outbound_orders: filter by status + date (outbound processing queue)
CREATE INDEX idx_outbound_status_date
    ON outbound_orders (status, created_at);

-- channels: quick lookup by platform (Lazada/Shopee filter)
CREATE INDEX idx_channels_platform
    ON channels (platform);

-- ── NHOM 12: Notifications ─────────────────────────────

CREATE TABLE IF NOT EXISTS system_settings (
    setting_key   VARCHAR(64) NOT NULL PRIMARY KEY,
    setting_value VARCHAR(255) NOT NULL,
    updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS notifications (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    recipient_user_id  INT NOT NULL DEFAULT 0 COMMENT '0 = role broadcast (all users with that role)',
    recipient_role     VARCHAR(50) NOT NULL,
    warehouse_id       INT DEFAULT NULL COMMENT 'NULL = all warehouses; set for WH-staff scoped alerts',
    notification_type  VARCHAR(50) NOT NULL COMMENT 'INBOUND, OUTBOUND, TRANSFER, RETURN, DEFECTIVE, INVENTORY, ORDER, APPROVAL, SYSTEM',
    title              VARCHAR(255) NOT NULL,
    message            TEXT NOT NULL,
    reference_type     VARCHAR(50) DEFAULT NULL COMMENT 'GRN, GI, KK, TR, RMA, ORDER',
    reference_id       BIGINT DEFAULT NULL,
    priority           VARCHAR(20) NOT NULL DEFAULT 'NORMAL' COMMENT 'LOW, NORMAL, HIGH, URGENT',
    is_read            TINYINT(1) NOT NULL DEFAULT 0,
    created_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at           DATETIME DEFAULT NULL,
    INDEX idx_notif_recipient (recipient_user_id, recipient_role),
    INDEX idx_notif_warehouse (warehouse_id),
    INDEX idx_notif_unread (recipient_user_id, is_read),
    INDEX idx_notif_created (created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ── NHOM 13: System Configuration (Manager-editable thresholds) ────
-- Stores configurable warning thresholds for pricing/margin checks.
-- Keys use dotted naming: <category>.<threshold_name>.
-- Edits are managed by Manager via /manager/config/pricing page.

CREATE TABLE IF NOT EXISTS system_config (
    config_id    INT AUTO_INCREMENT PRIMARY KEY,
    config_key   VARCHAR(100) NOT NULL UNIQUE,
    config_value VARCHAR(500) NOT NULL,
    description  VARCHAR(255) DEFAULT NULL,
    is_active    TINYINT DEFAULT 1,
    updated_by   INT DEFAULT NULL,
    updated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (updated_by) REFERENCES users(user_id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Seed the default pricing-warning thresholds (Manager can override via /manager/config/pricing)
INSERT IGNORE INTO system_config (config_key, config_value, description, is_active) VALUES
  ('pricing.warn_margin_low',         '0.10',  'Margin dưới ngưỡng này được cảnh báo "Lãi ít" (mặc định 10%)', 1),
  ('pricing.warn_margin_breakeven',    '0.00',  'Margin dưới ngưỡng này được cảnh báo "Hoà vốn/Lỗ nhẹ" (mặc định 0%)', 1),
  ('pricing.warn_margin_loss_threshold','-0.05','Margin dưới ngưỡng này được cảnh báo "Bán lỗ" - đỏ (mặc định -5%)', 1);

-- ── NHOM 14: Lazada Order Management ──────────────────────────────

CREATE TABLE IF NOT EXISTS lazada_orders (
    lazada_order_id INT AUTO_INCREMENT PRIMARY KEY,
    lazada_order_id_str VARCHAR(32) NOT NULL UNIQUE,
    lazada_order_number VARCHAR(32),
    channel_id INT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'pending',
    wms_status VARCHAR(32) NOT NULL DEFAULT 'NEW',
    customer_name VARCHAR(200),
    customer_phone VARCHAR(20),
    shipping_address VARCHAR(500),
    shipping_city VARCHAR(100),
    price DECIMAL(15,2) DEFAULT 0,
    shipping_fee DECIMAL(10,2) DEFAULT 0,
    voucher_seller DECIMAL(10,2) DEFAULT 0,
    voucher_platform DECIMAL(10,2) DEFAULT 0,
    payment_method VARCHAR(50),
    buyer_note TEXT,
    warehouse_id INT DEFAULT 0,
    assigned_by INT DEFAULT 0,
    assigned_at DATETIME,
    package_id VARCHAR(64),
    tracking_number VARCHAR(64),
    shipment_provider VARCHAR(64),
    shipment_provider_code VARCHAR(32),
    lazada_created_at DATETIME,
    lazada_updated_at DATETIME,
    synced_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    rts_at DATETIME,
    delivered_at DATETIME,
    FOREIGN KEY (channel_id) REFERENCES channels(channel_id),
    INDEX idx_lo_wms_status (wms_status),
    INDEX idx_lo_lazada_status (status),
    INDEX idx_lo_channel (channel_id),
    INDEX idx_lo_updated (lazada_updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS lazada_order_items (
    item_id INT AUTO_INCREMENT PRIMARY KEY,
    lazada_order_id_str VARCHAR(32) NOT NULL,
    order_item_id VARCHAR(32) NOT NULL,
    sku VARCHAR(100),
    shop_sku VARCHAR(100),
    product_name VARCHAR(500),
    product_image VARCHAR(1000),
    quantity INT DEFAULT 1,
    paid_price DECIMAL(15,2) DEFAULT 0,
    item_price DECIMAL(15,2) DEFAULT 0,
    supply_price DECIMAL(15,2) DEFAULT 0,
    status VARCHAR(32),
    product_id INT DEFAULT 0,
    reserved_qty INT DEFAULT 0,
    fulfilled_qty INT DEFAULT 0,
    FOREIGN KEY (lazada_order_id_str) REFERENCES lazada_orders(lazada_order_id_str),
    UNIQUE KEY uk_order_item (lazada_order_id_str, order_item_id),
    INDEX idx_li_sku (sku)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS lazada_shipment_providers (
    provider_id INT AUTO_INCREMENT PRIMARY KEY,
    region VARCHAR(10) NOT NULL DEFAULT 'VN',
    provider_code VARCHAR(32) NOT NULL,
    provider_name VARCHAR(100) NOT NULL,
    provider_name_vn VARCHAR(100),
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    display_order INT DEFAULT 0,
    UNIQUE KEY uk_region_code (region, provider_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Seed Lazada Vietnam shipment providers (idempotent)
INSERT IGNORE INTO lazada_shipment_providers (region, provider_code, provider_name, provider_name_vn, display_order) VALUES
    ('VN', 'FM49', 'Flash Express',    'Flash Express', 1),
    ('VN', 'J&T',  'J&T Express',    'J&T Express', 2),
    ('VN', 'GHTK', 'Giao Hàng Tiết Kiệm', 'GHTK', 3),
    ('VN', 'GHN',  'Giao Hàng Nhanh', 'GHN', 4),
    ('VN', 'NJV',  'NinjaVan',       'NinjaVan', 5),
    ('VN', 'SPX',  'SPX Express',   'SPX Express', 6);


