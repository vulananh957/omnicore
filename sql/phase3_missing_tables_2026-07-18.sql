-- Phase 3: Missing audit & tracking tables for inventory sync system
-- Created: 2026-07-18

-- Audit log for deduction attempts (success + failed)
CREATE TABLE IF NOT EXISTS inventory_deduction_log (
  id INT AUTO_INCREMENT PRIMARY KEY,
  product_id INT NOT NULL,
  warehouse_id INT,
  order_id INT NOT NULL,
  order_ref VARCHAR(50) NOT NULL,
  channel VARCHAR(20) NOT NULL,
  qty_deducted INT NOT NULL,
  qty_before INT NOT NULL,
  qty_after INT NOT NULL,
  deduction_status ENUM('SUCCESS', 'FAILED') DEFAULT 'SUCCESS',
  failure_reason VARCHAR(255),
  attempted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (product_id) REFERENCES products(product_id) ON DELETE CASCADE,
  INDEX idx_order (order_ref),
  INDEX idx_product (product_id),
  INDEX idx_channel (channel)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Channel sync audit trail (for realtime inventory push verification)
CREATE TABLE IF NOT EXISTS channel_sync_audit (
  id INT AUTO_INCREMENT PRIMARY KEY,
  channel_product_id INT,
  order_id INT,
  order_ref VARCHAR(50),
  operation ENUM('PUSH', 'PULL', 'UPDATE', 'DELETE', 'RTS') NOT NULL,
  status ENUM('SUCCESS', 'FAILED', 'PENDING') DEFAULT 'PENDING',
  error_message VARCHAR(500),
  sync_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_operation (operation),
  INDEX idx_status (status),
  INDEX idx_order (order_ref)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Lazada RTS (Ready To Ship) audit log
CREATE TABLE IF NOT EXISTS lazada_rts_log (
  id INT AUTO_INCREMENT PRIMARY KEY,
  order_id INT NOT NULL,
  order_ref VARCHAR(50) NOT NULL,
  warehouse_id INT,
  rts_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  status ENUM('INITIATED', 'SUCCESS', 'FAILED') DEFAULT 'INITIATED',
  response_text TEXT,
  INDEX idx_order (order_ref),
  INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Add missing columns to outbound table
ALTER TABLE outbound ADD COLUMN IF NOT EXISTS courier_name VARCHAR(100);

-- Add missing columns to delivery_note (if it exists)
-- Note: delivery_note table may not exist yet; this is conditional
-- ALTER TABLE delivery_note ADD COLUMN IF NOT EXISTS status VARCHAR(50);
