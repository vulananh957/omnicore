-- Fix BUG-01: InventoryPushScheduler push toan bo catalog moi 5s thay vi chi san pham thay doi.
-- getChangesSince() truoc day khong nhan tham so thoi gian va khong co bang de tra cuu delta —
-- luon SELECT toan bo inventory. Bang nay la nguon du lieu that cho "cai gi thay doi kem theo
-- luc nao", ghi boi deductWithLock() va restoreDeductedStock() trong cung transaction voi thay
-- doi ton kho, de khong bao gio lech giua so that va log push.
-- Database: wms_hub
-- Rollback: DROP TABLE IF EXISTS inventory_change_log;

CREATE TABLE IF NOT EXISTS inventory_change_log (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    product_id  INT NOT NULL,
    qty_before  INT NOT NULL,
    qty_after   INT NOT NULL,
    changed_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (product_id) REFERENCES products(product_id) ON DELETE CASCADE,
    INDEX idx_changed_at (changed_at),
    INDEX idx_product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
