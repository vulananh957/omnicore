-- Migration: Thêm tracking cho inventory deduction (Cách 2 Hybrid)
-- Purpose: Support atomic deduction khi order từ Web/Lazada đến
-- Database: wms_hub
-- Date: 2026-07-18

-- Rollback:
-- DROP TABLE IF EXISTS inventory_deduction_log;
-- ALTER TABLE inventory DROP COLUMN last_deducted_at;
-- ALTER TABLE inventory DROP COLUMN deduction_lock;

USE wms_hub;

-- ── Add tracking fields ───────────────────────────────────────────────────
-- last_deducted_at: Last time qty was deducted
-- deduction_lock: Lock column for atomic operations
ALTER TABLE inventory
ADD COLUMN last_deducted_at DATETIME NULL AFTER qty_available,
ADD COLUMN deduction_lock TINYINT(1) NOT NULL DEFAULT 0 AFTER last_deducted_at;

ALTER TABLE inventory
ADD INDEX idx_inventory_deduction (product_id, warehouse_id, last_deducted_at);

-- ── Tạo audit log table ────────────────────────────────────────────────────
-- Log mỗi lần deduction để track nguyên nhân, detect oversell
CREATE TABLE IF NOT EXISTS inventory_deduction_log (
    log_id              INT AUTO_INCREMENT PRIMARY KEY,
    product_id          INT NOT NULL,
    warehouse_id        INT NOT NULL,
    order_id            INT NOT NULL,
    order_ref           VARCHAR(50) NOT NULL,
    channel             VARCHAR(30) NOT NULL COMMENT 'WEB|LAZADA|SHOPEE',
    qty_deducted        INT NOT NULL,
    qty_available_before INT,
    qty_available_after  INT,
    deducted_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          VARCHAR(100),
    FOREIGN KEY (product_id) REFERENCES products(product_id) ON DELETE RESTRICT,
    FOREIGN KEY (warehouse_id) REFERENCES warehouses(warehouse_id) ON DELETE RESTRICT,
    INDEX idx_deduction_log_order (order_id),
    INDEX idx_deduction_log_channel (channel, deducted_at),
    INDEX idx_deduction_log_product (product_id, warehouse_id, deducted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ── Verify ─────────────────────────────────────────────────────────────────
SELECT
    product_id,
    warehouse_id,
    qty_available,
    qty_on_hand,
    holding,
    last_deducted_at,
    deduction_lock
FROM inventory
WHERE product_id = 1
LIMIT 1;

-- ── Done ───────────────────────────────────────────────────────────────────
COMMIT;

-- Log migration
INSERT INTO migration_log (migration_name, executed_at)
VALUES ('inventory_deduction_tracking_2026-07-18', NOW());
