-- Migration: Audit Logging Tables (Phase 4)
-- Purpose: Track all API calls, deduction attempts, and sync errors for debugging & compliance
-- Database: wms_hub
-- Date: 2026-07-18

USE wms_hub;

-- ── API Authentication Log ────────────────────────────────────────────────────────
-- Logs every API request authentication attempt (success or failure)
CREATE TABLE IF NOT EXISTS api_audit_log (
    log_id              INT AUTO_INCREMENT PRIMARY KEY,
    channel             VARCHAR(50) NOT NULL,
    method              VARCHAR(10) NOT NULL,
    path                VARCHAR(255) NOT NULL,
    remote_ip           VARCHAR(45),
    signature_valid     TINYINT(1) NOT NULL,
    reason              VARCHAR(255),
    logged_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_channel_timestamp (channel, logged_at),
    INDEX idx_signature_valid (signature_valid, logged_at),
    INDEX idx_remote_ip (remote_ip, logged_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ── Deduction Audit Log ────────────────────────────────────────────────────────────
-- Logs every inventory deduction attempt (detects double-deduct, oversell attempts)
CREATE TABLE IF NOT EXISTS deduction_audit_log (
    log_id              INT AUTO_INCREMENT PRIMARY KEY,
    order_id            INT NOT NULL,
    order_ref           VARCHAR(50) NOT NULL,
    product_id          INT NOT NULL,
    channel             VARCHAR(30) NOT NULL,
    qty_requested       INT NOT NULL,
    qty_available       INT NOT NULL,
    deduct_success      TINYINT(1) NOT NULL,
    reason              VARCHAR(255),
    logged_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_order_ref (order_ref),
    INDEX idx_product_id (product_id),
    INDEX idx_deduct_success (deduct_success),
    INDEX idx_logged_at (logged_at),
    INDEX idx_channel_timestamp (channel, logged_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ── Sync Error Log ─────────────────────────────────────────────────────────────────
-- Logs communication errors between Web and Main
CREATE TABLE IF NOT EXISTS sync_error_log (
    log_id              INT AUTO_INCREMENT PRIMARY KEY,
    from_system         VARCHAR(50) NOT NULL,
    to_system           VARCHAR(50) NOT NULL,
    endpoint            VARCHAR(255) NOT NULL,
    error_message       TEXT,
    http_status         INT,
    logged_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_from_to (from_system, to_system),
    INDEX idx_http_status (http_status),
    INDEX idx_logged_at (logged_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ── Cleanup policy (optional) ──────────────────────────────────────────────────────
-- Keep logs for 90 days, archive older logs monthly
ALTER TABLE api_audit_log ADD INDEX idx_cleanup (logged_at);
ALTER TABLE deduction_audit_log ADD INDEX idx_cleanup (logged_at);
ALTER TABLE sync_error_log ADD INDEX idx_cleanup (logged_at);

-- Verify tables created
SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES
WHERE TABLE_SCHEMA = 'wms_hub'
AND TABLE_NAME IN ('api_audit_log', 'deduction_audit_log', 'sync_error_log');

-- Migration log
INSERT INTO _migration_log (migration_name, status, executed_at)
VALUES ('audit_logging_2026-07-18', 'SUCCESS', NOW());
