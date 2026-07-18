-- Inventory Push Batch Table
-- Purpose: Track realtime push attempts to Web (batch + retry)

CREATE TABLE IF NOT EXISTS inventory_push_batch (
  batch_id VARCHAR(50) PRIMARY KEY COMMENT 'UUID, format: PUSH-{timestamp}-{uuid}',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT 'When batch was created',
  retry_count INT DEFAULT 0 COMMENT 'Number of push attempts',
  next_retry_time TIMESTAMP NULL COMMENT 'When to retry next',
  status ENUM('PENDING', 'SUCCESS', 'FAILED') DEFAULT 'PENDING' COMMENT 'Current status',
  payload LONGTEXT COMMENT 'JSON: [{product_id, qty_available, qty_before}, ...]',
  last_error_message VARCHAR(500) COMMENT 'Error message from last failed push',
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  INDEX idx_status_retry (status, next_retry_time),
  INDEX idx_created (created_at DESC)
);

-- Rollback: DROP TABLE IF EXISTS inventory_push_batch;
