-- ============================================================================
-- OmniCore WMS Hub — Complete database schema + seed data
-- Database: wms_hub
-- Regenerated: 2026-07-19 from the live working database (mysqldump)
--
-- Purpose: single source of truth to bootstrap a fully working wms_hub from
-- scratch. Replaces the previous src/main/resources/schema.sql, which had
-- drifted from reality — missing 4 tables actively used by the app
-- (customers, password_reset_tokens, web_sync_log, inventory_push_batch)
-- and 3 stale table definitions no longer used (category_mappings,
-- pending_otp_verifications, system_settings).
--
-- Contents:
--   1. Full DDL for all 71 tables (DROP TABLE IF EXISTS + CREATE — safe to
--      re-run on a fresh or existing wms_hub database)
--   2. Seed data for master/config tables: roles, warehouses, zones,
--      channels, system_config, shipping_carriers, mock_shipping_carriers,
--      lazada_shipment_providers, categories, skus
--   3. ONE demo admin account (see below) — real staff accounts were
--      intentionally NOT included to avoid committing employee PII
--
-- Login after running this script:
--   Email:    admin@example.com
--   Password: Admin@12345
--   Role:     ADMIN (full access) — change this password after first login.
--
-- NOT seeded (intentionally empty — app is meant to be populated fresh):
--   products, product_images, product_default_zones, and all transactional
--   tables (orders, inventory, lazada_orders, rma_requests, stocktakes, ...)
--
-- Usage:
--   mysql -u root -p < schema.sql
--   (creates database wms_hub if it doesn't exist, or resets it if it does)
--
-- Rollback: DROP DATABASE IF EXISTS wms_hub;
-- ============================================================================

CREATE DATABASE IF NOT EXISTS wms_hub
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE wms_hub;

SET FOREIGN_KEY_CHECKS = 0;


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
DROP TABLE IF EXISTS `categories`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `categories` (
  `category_id` int NOT NULL AUTO_INCREMENT,
  `parent_id` int DEFAULT NULL,
  `category_name` varchar(100) NOT NULL,
  `level_depth` int DEFAULT '0',
  `active` tinyint(1) NOT NULL DEFAULT '1',
  `description` varchar(255) DEFAULT NULL,
  `category_code` varchar(10) DEFAULT NULL,
  `is_immutable` tinyint(1) NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`category_id`)
) ENGINE=InnoDB AUTO_INCREMENT=72 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `channel_products`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `channel_products` (
  `id` int NOT NULL AUTO_INCREMENT,
  `channel_id` int NOT NULL,
  `product_id` int NOT NULL,
  `channel_sku_code` varchar(100) DEFAULT NULL,
  `channel_price` decimal(15,2) NOT NULL DEFAULT '0.00',
  `channel_stock` decimal(12,3) NOT NULL DEFAULT '0.000',
  `status` enum('ACTIVE','INACTIVE','PENDING') DEFAULT 'ACTIVE',
  `listed_at` datetime DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `channel_item_id` varchar(100) DEFAULT NULL COMMENT 'Lazada item_id returned by /product/create',
  `lazada_sku_id` varchar(100) DEFAULT NULL COMMENT 'Lazada sku_id returned by /product/create',
  `last_push_qty` decimal(12,3) DEFAULT NULL COMMENT 'Stock quantity at last successful push',
  `last_push_at` datetime DEFAULT NULL COMMENT 'Timestamp of last successful push',
  `last_error_code` varchar(50) DEFAULT NULL COMMENT 'Last push error code from Lazada',
  `last_error_message` varchar(500) DEFAULT NULL COMMENT 'Last push error message (translated to VI)',
  `lazada_category_id` bigint DEFAULT NULL COMMENT 'Lazada leaf category id (mirrored from /category/tree/get)',
  `brand_id` bigint DEFAULT NULL COMMENT 'Lazada brand_id',
  `dimensions` varchar(50) DEFAULT NULL COMMENT 'Package dimensions DxWxH cm',
  `weight_kg` double DEFAULT NULL COMMENT 'Package weight in kg',
  `seller_sku` varchar(100) DEFAULT NULL COMMENT 'Seller SKU code on marketplace',
  `short_description` text COMMENT 'Short description shown on listing',
  `brand` varchar(200) DEFAULT NULL COMMENT 'Brand name text',
  `description` text COMMENT 'Full product description on marketplace',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_channel_product` (`channel_id`,`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `channel_sync_audit`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `channel_sync_audit` (
  `id` int NOT NULL AUTO_INCREMENT,
  `channel_product_id` int DEFAULT NULL,
  `order_id` int DEFAULT NULL,
  `order_ref` varchar(50) DEFAULT NULL,
  `operation` enum('PUSH','PULL','UPDATE','DELETE','RTS') NOT NULL,
  `status` enum('SUCCESS','FAILED','PENDING') DEFAULT 'PENDING',
  `error_message` varchar(500) DEFAULT NULL,
  `sync_timestamp` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_operation` (`operation`),
  KEY `idx_status` (`status`),
  KEY `idx_order` (`order_ref`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `channels`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `channels` (
  `channel_id` int NOT NULL AUTO_INCREMENT,
  `channel_name` varchar(100) NOT NULL,
  `platform` varchar(50) NOT NULL,
  `api_url` varchar(255) DEFAULT NULL,
  `api_key` varchar(255) DEFAULT NULL,
  `app_secret` varchar(255) DEFAULT NULL,
  `webhook_secret` varchar(255) DEFAULT NULL,
  `buffer_stock` decimal(12,3) DEFAULT '0.000',
  `is_active` tinyint(1) DEFAULT '1',
  `access_token` text,
  `refresh_token` text,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `token_expires_at` datetime DEFAULT NULL COMMENT 'UTC timestamp when access_token expires. NULL = unknown/never.',
  `last_order_sync_at` datetime DEFAULT NULL COMMENT 'Last successful order sync via scheduler.',
  `webhook_callback_url` varchar(255) DEFAULT NULL COMMENT 'Callback URL registered with the channel platform.',
  PRIMARY KEY (`channel_id`),
  KEY `idx_channels_platform` (`platform`)
) ENGINE=InnoDB AUTO_INCREMENT=100 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `customers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `customers` (
  `customer_id` int NOT NULL AUTO_INCREMENT,
  `full_name` varchar(255) NOT NULL,
  `email` varchar(255) DEFAULT NULL,
  `phone` varchar(30) NOT NULL,
  `default_address` text,
  `source` enum('WEBSITE','STORE','LAZADA','B2B') NOT NULL DEFAULT 'WEBSITE',
  `web_customer_ref` varchar(50) DEFAULT NULL COMMENT 'customer_id from omnicore_web.customers',
  `total_orders` int NOT NULL DEFAULT '0',
  `total_spent` decimal(15,2) NOT NULL DEFAULT '0.00',
  `first_order_at` datetime DEFAULT NULL,
  `last_order_at` datetime DEFAULT NULL,
  `active` tinyint(1) NOT NULL DEFAULT '1',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`customer_id`),
  UNIQUE KEY `uk_customers_phone` (`phone`),
  UNIQUE KEY `uk_customers_email` (`email`),
  KEY `idx_customers_source` (`source`),
  KEY `idx_customers_web_ref` (`web_customer_ref`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `delivery_notes`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `delivery_notes` (
  `delivery_id` int NOT NULL AUTO_INCREMENT,
  `outbound_id` int NOT NULL,
  `delivered_by` int DEFAULT NULL,
  `delivery_date` datetime DEFAULT NULL,
  `recipient_name` varchar(100) DEFAULT NULL,
  `recipient_note` text,
  PRIMARY KEY (`delivery_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `fulfillment_request_items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `fulfillment_request_items` (
  `item_id` int NOT NULL AUTO_INCREMENT,
  `request_id` varchar(50) NOT NULL,
  `sku_code` varchar(50) NOT NULL,
  `sku_name` varchar(200) NOT NULL,
  `qty` int NOT NULL DEFAULT '1',
  PRIMARY KEY (`item_id`),
  KEY `request_id` (`request_id`),
  CONSTRAINT `fulfillment_request_items_ibfk_1` FOREIGN KEY (`request_id`) REFERENCES `fulfillment_requests` (`request_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `fulfillment_requests`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `fulfillment_requests` (
  `request_id` varchar(50) NOT NULL,
  `order_id` varchar(50) NOT NULL,
  `warehouse_id` int NOT NULL DEFAULT '1',
  `status` enum('PENDING','CONVERTED','CANCELLED') NOT NULL DEFAULT 'PENDING',
  `auto_created` tinyint(1) NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`request_id`),
  KEY `idx_fr_status` (`status`),
  KEY `idx_fr_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `inbound_items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inbound_items` (
  `inbound_item_id` int NOT NULL AUTO_INCREMENT,
  `inbound_id` int NOT NULL,
  `product_id` int NOT NULL,
  `expected_qty` decimal(12,3) NOT NULL DEFAULT '0.000',
  `received_qty` decimal(12,3) NOT NULL DEFAULT '0.000',
  `unit_cost` decimal(15,4) NOT NULL DEFAULT '0.0000' COMMENT 'Unit cost at time of inbound receipt (used for MAC recalculation)',
  `accepted_qty` decimal(12,3) NOT NULL DEFAULT '0.000' COMMENT 'Accepted quantity used for MAC',
  `rejected_qty` decimal(12,3) NOT NULL DEFAULT '0.000',
  `reject_reason` varchar(255) DEFAULT NULL COMMENT 'Ly do tra hang NCC: Hang mop meo, Sai mau/size, Het han, Hong van chuyen',
  `lot_number` varchar(50) DEFAULT NULL,
  `expiry_date` date DEFAULT NULL,
  `notes` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`inbound_item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `inbound_orders`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inbound_orders` (
  `inbound_id` int NOT NULL AUTO_INCREMENT,
  `inbound_code` varchar(30) NOT NULL,
  `warehouse_id` int NOT NULL,
  `supplier` varchar(100) DEFAULT NULL,
  `status` enum('PENDING','IN_PROGRESS','RECEIVED','CANCELLED') NOT NULL DEFAULT 'PENDING',
  `received_by` int DEFAULT NULL,
  `note` text,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `received_at` datetime DEFAULT NULL,
  `created_by` int DEFAULT NULL,
  `supplier_id` int DEFAULT NULL COMMENT 'FK mem toi suppliers.supplier_id',
  `supplier_address` varchar(255) DEFAULT NULL,
  `supplier_phone` varchar(50) DEFAULT NULL,
  `po_reference` varchar(50) DEFAULT NULL,
  `zone_id` int DEFAULT NULL COMMENT 'Khu vuc nhan hang trong kho (zones.zone_id)',
  `delivery_person` varchar(100) DEFAULT NULL COMMENT 'Ten nguoi giao hang / tai xe',
  `delivery_phone` varchar(50) DEFAULT NULL COMMENT 'SDT nguoi giao',
  `expected_date` date DEFAULT NULL COMMENT 'Ngay du kien nhan hang (PO)',
  `received_date` date DEFAULT NULL COMMENT 'Ngay nhap hang thuc te',
  `payment_terms` varchar(50) DEFAULT NULL,
  PRIMARY KEY (`inbound_id`),
  UNIQUE KEY `inbound_code` (`inbound_code`),
  KEY `idx_inbound_status_date` (`status`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `inventory`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inventory` (
  `inventory_id` int NOT NULL AUTO_INCREMENT,
  `product_id` int NOT NULL,
  `warehouse_id` int NOT NULL,
  `qty_on_hand` decimal(12,3) NOT NULL DEFAULT '0.000',
  `holding` decimal(12,3) NOT NULL DEFAULT '0.000',
  `qty_available` decimal(12,3) NOT NULL DEFAULT '0.000',
  `last_deducted_at` datetime DEFAULT NULL,
  `deduction_lock` tinyint(1) NOT NULL DEFAULT '0',
  `reorder_point` decimal(12,3) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `stock_type` varchar(20) NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL or DEFECTIVE stock pool',
  PRIMARY KEY (`inventory_id`),
  UNIQUE KEY `uq_product_warehouse` (`product_id`,`warehouse_id`),
  KEY `idx_inventory_deduction` (`product_id`,`warehouse_id`,`last_deducted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `inventory_change_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inventory_change_log` (
  `id` int NOT NULL AUTO_INCREMENT,
  `product_id` int NOT NULL,
  `qty_before` int NOT NULL,
  `qty_after` int NOT NULL,
  `changed_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_changed_at` (`changed_at`),
  KEY `idx_product` (`product_id`),
  CONSTRAINT `inventory_change_log_ibfk_1` FOREIGN KEY (`product_id`) REFERENCES `products` (`product_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `inventory_deduction_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inventory_deduction_log` (
  `id` int NOT NULL AUTO_INCREMENT,
  `product_id` int NOT NULL,
  `warehouse_id` int DEFAULT NULL,
  `order_id` int NOT NULL,
  `order_ref` varchar(50) NOT NULL,
  `channel` varchar(20) NOT NULL,
  `qty_deducted` int NOT NULL,
  `qty_before` int NOT NULL,
  `qty_after` int NOT NULL,
  `deduction_status` enum('SUCCESS','FAILED') DEFAULT 'SUCCESS',
  `failure_reason` varchar(255) DEFAULT NULL,
  `attempted_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_order` (`order_ref`),
  KEY `idx_product` (`product_id`),
  KEY `idx_channel` (`channel`),
  CONSTRAINT `inventory_deduction_log_ibfk_1` FOREIGN KEY (`product_id`) REFERENCES `products` (`product_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `inventory_ledger`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inventory_ledger` (
  `ledger_id` int NOT NULL AUTO_INCREMENT,
  `inventory_id` int NOT NULL,
  `product_id` int NOT NULL,
  `warehouse_id` int NOT NULL,
  `transaction_type` enum('INBOUND','OUTBOUND','ADJUSTMENT','TRANSFER_IN','TRANSFER_OUT') NOT NULL,
  `ref_document_id` int DEFAULT NULL,
  `qty_change` decimal(12,3) NOT NULL,
  `avail_change` decimal(12,3) NOT NULL,
  `timestamp` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `created_by` int DEFAULT NULL,
  `note` text,
  `ledger_type` varchar(20) DEFAULT 'NORMAL' COMMENT 'NORMAL or DEFECTIVE — mirrors inventory.stock_type',
  PRIMARY KEY (`ledger_id`),
  KEY `idx_ledger_sku_wh_time` (`product_id`,`warehouse_id`,`timestamp`),
  KEY `idx_ledger_sku_type` (`product_id`,`transaction_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `inventory_push_batch`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inventory_push_batch` (
  `batch_id` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'UUID, format: PUSH-{timestamp}-{uuid}',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'When batch was created',
  `retry_count` int DEFAULT '0' COMMENT 'Number of push attempts',
  `next_retry_time` timestamp NULL DEFAULT NULL COMMENT 'When to retry next',
  `status` enum('PENDING','SUCCESS','FAILED') COLLATE utf8mb4_unicode_ci DEFAULT 'PENDING' COMMENT 'Current status',
  `payload` longtext COLLATE utf8mb4_unicode_ci COMMENT 'JSON: [{product_id, qty_available, qty_before}, ...]',
  `last_error_message` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Error message from last failed push',
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`batch_id`),
  KEY `idx_status_retry` (`status`,`next_retry_time`),
  KEY `idx_created` (`created_at` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `issue_details`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `issue_details` (
  `detail_id` int NOT NULL AUTO_INCREMENT,
  `issue_id` int NOT NULL,
  `product_id` int NOT NULL,
  `quantity` decimal(12,3) NOT NULL,
  `note` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`detail_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `lazada_categories`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lazada_categories` (
  `id` int NOT NULL AUTO_INCREMENT,
  `channel_id` int NOT NULL,
  `lazada_category_id` bigint NOT NULL,
  `parent_id` bigint DEFAULT NULL,
  `name` varchar(255) NOT NULL,
  `is_leaf` tinyint(1) NOT NULL DEFAULT '0',
  `has_variation` tinyint(1) NOT NULL DEFAULT '0',
  `depth` int NOT NULL DEFAULT '0',
  `synced_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_lazada_cat_channel` (`channel_id`,`lazada_category_id`),
  KEY `idx_lazada_cat_parent` (`parent_id`),
  KEY `idx_lazada_cat_leaf` (`channel_id`,`is_leaf`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `lazada_order_items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lazada_order_items` (
  `item_id` int NOT NULL AUTO_INCREMENT,
  `lazada_order_id_str` varchar(50) NOT NULL,
  `order_item_id` varchar(50) DEFAULT NULL,
  `sku` varchar(100) DEFAULT NULL,
  `shop_sku` varchar(100) DEFAULT NULL,
  `product_name` varchar(500) DEFAULT NULL,
  `product_image` varchar(500) DEFAULT NULL,
  `quantity` int NOT NULL DEFAULT '1',
  `paid_price` decimal(15,2) DEFAULT NULL,
  `item_price` decimal(15,2) DEFAULT NULL,
  `supply_price` decimal(15,4) DEFAULT NULL,
  `status` varchar(50) DEFAULT NULL,
  `product_id` int DEFAULT '0',
  `reserved_qty` int DEFAULT '0',
  `fulfilled_qty` int DEFAULT '0',
  PRIMARY KEY (`item_id`),
  KEY `idx_loi_order` (`lazada_order_id_str`),
  KEY `idx_loi_sku` (`sku`),
  KEY `idx_loi_product` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `lazada_orders`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lazada_orders` (
  `lazada_order_id` int NOT NULL AUTO_INCREMENT,
  `lazada_order_id_str` varchar(50) NOT NULL COMMENT 'Lazada order_id as string (natural key)',
  `lazada_order_number` varchar(50) DEFAULT NULL,
  `channel_id` int NOT NULL,
  `status` varchar(50) DEFAULT NULL,
  `wms_status` varchar(30) DEFAULT 'NEW',
  `customer_name` varchar(255) DEFAULT NULL,
  `customer_phone` varchar(50) DEFAULT NULL,
  `shipping_address` text,
  `shipping_city` varchar(100) DEFAULT NULL,
  `price` decimal(15,2) DEFAULT NULL,
  `shipping_fee` decimal(15,2) DEFAULT NULL,
  `voucher_seller` decimal(15,2) DEFAULT NULL,
  `voucher_platform` decimal(15,2) DEFAULT NULL,
  `payment_method` varchar(50) DEFAULT NULL,
  `buyer_note` text,
  `warehouse_id` int DEFAULT '0',
  `assigned_by` int DEFAULT '0',
  `assigned_at` datetime DEFAULT NULL,
  `package_id` varchar(100) DEFAULT NULL,
  `tracking_number` varchar(100) DEFAULT NULL,
  `shipment_provider` varchar(100) DEFAULT NULL,
  `shipment_provider_code` varchar(50) DEFAULT NULL,
  `lazada_created_at` datetime DEFAULT NULL,
  `lazada_updated_at` datetime DEFAULT NULL,
  `rts_at` datetime DEFAULT NULL,
  `delivered_at` datetime DEFAULT NULL,
  `synced_at` datetime DEFAULT NULL,
  PRIMARY KEY (`lazada_order_id`),
  UNIQUE KEY `lazada_order_id_str` (`lazada_order_id_str`),
  KEY `idx_lo_channel` (`channel_id`),
  KEY `idx_lo_status` (`status`),
  KEY `idx_lo_wms_status` (`wms_status`),
  KEY `idx_lo_synced` (`synced_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `lazada_rts_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lazada_rts_log` (
  `id` int NOT NULL AUTO_INCREMENT,
  `order_id` int NOT NULL,
  `order_ref` varchar(50) NOT NULL,
  `warehouse_id` int DEFAULT NULL,
  `rts_timestamp` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `status` enum('INITIATED','SUCCESS','FAILED') DEFAULT 'INITIATED',
  `response_text` text,
  PRIMARY KEY (`id`),
  KEY `idx_order` (`order_ref`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `lazada_shipment_providers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lazada_shipment_providers` (
  `provider_id` int NOT NULL AUTO_INCREMENT,
  `region` varchar(10) NOT NULL DEFAULT 'VN',
  `provider_code` varchar(32) NOT NULL,
  `provider_name` varchar(100) NOT NULL,
  `provider_name_vn` varchar(100) DEFAULT NULL,
  `is_active` tinyint(1) NOT NULL DEFAULT '1',
  `display_order` int DEFAULT '0',
  PRIMARY KEY (`provider_id`),
  UNIQUE KEY `uk_region_code` (`region`,`provider_code`)
) ENGINE=InnoDB AUTO_INCREMENT=783 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `lazada_stock_push_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lazada_stock_push_log` (
  `log_id` int NOT NULL AUTO_INCREMENT,
  `channel_id` int DEFAULT NULL,
  `product_id` int DEFAULT NULL,
  `seller_sku` varchar(100) DEFAULT NULL,
  `qty_on_hand` decimal(12,3) DEFAULT NULL,
  `qty_available` decimal(12,3) DEFAULT NULL,
  `holding` decimal(12,3) DEFAULT NULL,
  `buffer_stock` decimal(12,3) DEFAULT NULL,
  `push_qty` decimal(12,3) DEFAULT NULL,
  `status` varchar(20) DEFAULT NULL,
  `error_message` text,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `error_code` varchar(50) DEFAULT NULL COMMENT 'Lazada error code (e.g. E501, E901)',
  `inbound_receipt_code` varchar(50) DEFAULT NULL COMMENT 'Inbound receipt that triggered this push',
  `pushed_at` datetime DEFAULT NULL COMMENT 'Timestamp when push was attempted',
  PRIMARY KEY (`log_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `lazada_sync_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lazada_sync_log` (
  `log_id` int NOT NULL AUTO_INCREMENT,
  `channel_id` int DEFAULT NULL,
  `sync_type` varchar(50) DEFAULT NULL,
  `status` enum('SUCCESS','FAILED') NOT NULL,
  `request_data` text,
  `response_data` text,
  `error_msg` text,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`log_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `mapping_exceptions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mapping_exceptions` (
  `exception_id` int NOT NULL AUTO_INCREMENT,
  `channel_id` int NOT NULL,
  `external_sku` varchar(100) NOT NULL,
  `order_code` varchar(100) DEFAULT NULL,
  `reason` varchar(255) DEFAULT NULL,
  `resolved` tinyint(1) NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `resolved_at` datetime DEFAULT NULL,
  PRIMARY KEY (`exception_id`),
  KEY `idx_me_channel` (`channel_id`),
  KEY `idx_me_resolved` (`resolved`),
  CONSTRAINT `mapping_exceptions_ibfk_1` FOREIGN KEY (`channel_id`) REFERENCES `channels` (`channel_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `mock_shipping_carriers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mock_shipping_carriers` (
  `carrier_id` int NOT NULL AUTO_INCREMENT,
  `carrier_name` varchar(100) NOT NULL,
  `fee` decimal(12,2) NOT NULL DEFAULT '0.00',
  `is_active` tinyint(1) NOT NULL DEFAULT '1',
  `display_order` int DEFAULT '0',
  PRIMARY KEY (`carrier_id`)
) ENGINE=InnoDB AUTO_INCREMENT=405 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `notifications`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `notifications` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `recipient_user_id` int NOT NULL DEFAULT '0',
  `recipient_role` varchar(50) NOT NULL,
  `warehouse_id` int DEFAULT NULL,
  `notification_type` varchar(50) NOT NULL,
  `title` varchar(255) NOT NULL,
  `message` text NOT NULL,
  `reference_type` varchar(50) DEFAULT NULL,
  `reference_id` bigint DEFAULT NULL,
  `priority` varchar(20) NOT NULL DEFAULT 'NORMAL',
  `is_read` tinyint(1) NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `read_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_notif_recipient` (`recipient_user_id`,`recipient_role`),
  KEY `idx_notif_warehouse` (`warehouse_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `order_items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `order_items` (
  `order_item_id` int NOT NULL AUTO_INCREMENT,
  `order_id` int NOT NULL,
  `product_id` int NOT NULL,
  `qty` int NOT NULL DEFAULT '1',
  `unit_price` decimal(12,2) NOT NULL DEFAULT '0.00',
  `actual_price` decimal(15,2) NOT NULL DEFAULT '0.00',
  PRIMARY KEY (`order_item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `order_shipping_details`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `order_shipping_details` (
  `shipping_id` int NOT NULL AUTO_INCREMENT,
  `order_id` int NOT NULL,
  `recipient_name` varchar(100) NOT NULL,
  `shipping_address` text NOT NULL,
  `courier_name` varchar(50) DEFAULT NULL,
  `waybill_code` varchar(100) DEFAULT NULL,
  `shipping_status` enum('PENDING','PICKED_UP','IN_TRANSIT','OUT_FOR_DELIVERY','DELIVERED','RETURNED') NOT NULL DEFAULT 'PENDING',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `recipient_phone` varchar(20) DEFAULT NULL,
  PRIMARY KEY (`shipping_id`),
  UNIQUE KEY `order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `orders`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `orders` (
  `order_id` int NOT NULL AUTO_INCREMENT,
  `order_code` varchar(30) NOT NULL,
  `customer_id` int DEFAULT NULL,
  `warehouse_id` int DEFAULT NULL,
  `channel` enum('ONLINE','STORE','B2B','WEBSITE','LAZADA') NOT NULL DEFAULT 'ONLINE',
  `status` enum('PENDING','PICKING','PACKED','SHIPPED','DELIVERED','CANCELLED','RETURNED','DISPUTED','DISPUTE_SUCCESS','COMPLETED') NOT NULL DEFAULT 'PENDING',
  `total_amount` decimal(15,2) NOT NULL DEFAULT '0.00',
  `note` text,
  `created_by` int DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `tracking_no` varchar(100) DEFAULT NULL,
  `review_note` varchar(255) DEFAULT NULL,
  `rma_reason` varchar(255) DEFAULT NULL,
  `rma_physical_status` varchar(100) DEFAULT NULL,
  `rma_platform_status` varchar(100) DEFAULT NULL,
  `dispute_evidence_video` varchar(255) DEFAULT NULL,
  `dispute_note` varchar(255) DEFAULT NULL,
  `web_order_ref` varchar(100) DEFAULT NULL,
  `customer_name` varchar(255) DEFAULT NULL,
  `customer_phone` varchar(30) DEFAULT NULL,
  `customer_address` text,
  `channel_id` int DEFAULT NULL COMMENT 'FK to channels table — set for Lazada and website orders',
  `channel_order_id` varchar(50) DEFAULT NULL COMMENT 'Lazada order_id as string (for cross-referencing lazada_orders table)',
  `fee_breakdown_json` text COMMENT 'Detailed fee breakdown from channel',
  `sync_status` varchar(20) DEFAULT 'PENDING' COMMENT 'Sync status of the order',
  `web_customer_ref` varchar(100) DEFAULT NULL COMMENT 'omnicore-web customers.customer_id — reference only, not a real FK',
  `shipment_provider` varchar(100) DEFAULT NULL COMMENT 'Assigned shipping carrier — any channel, not Lazada-specific',
  `delivered_at` datetime DEFAULT NULL COMMENT 'Stamped when status becomes DELIVERED — any channel; base for the 7-day website return window',
  `is_pack_requested` tinyint(1) NOT NULL DEFAULT '0',
  `is_rts_pushed` tinyint(1) NOT NULL DEFAULT '0',
  `is_label_printed` tinyint(1) NOT NULL DEFAULT '0',
  `lazada_package_id` varchar(100) DEFAULT NULL COMMENT 'Lazada: package ID returned by Pack API',
  `shipping_fee` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT 'Website mock shipping: fee for the carrier chosen at checkout, added to total_amount',
  PRIMARY KEY (`order_id`),
  UNIQUE KEY `order_code` (`order_code`),
  UNIQUE KEY `web_order_ref` (`web_order_ref`),
  UNIQUE KEY `uq_web_order_ref` (`web_order_ref`),
  KEY `idx_orders_customer_date` (`customer_id`,`created_at`),
  CONSTRAINT `fk_orders_customer_id` FOREIGN KEY (`customer_id`) REFERENCES `customers` (`customer_id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `outbound_items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `outbound_items` (
  `outbound_item_id` int NOT NULL AUTO_INCREMENT,
  `outbound_id` int NOT NULL,
  `product_id` int NOT NULL,
  `qty` decimal(12,3) NOT NULL DEFAULT '1.000',
  `picked_qty` decimal(12,3) NOT NULL DEFAULT '0.000',
  `shelf_location` varchar(100) DEFAULT NULL,
  PRIMARY KEY (`outbound_item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `outbound_orders`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `outbound_orders` (
  `outbound_id` int NOT NULL AUTO_INCREMENT,
  `order_id` int NOT NULL,
  `warehouse_id` int NOT NULL,
  `status` varchar(50) NOT NULL DEFAULT 'PENDING_PACK',
  `picked_by` int DEFAULT NULL,
  `shipped_at` datetime DEFAULT NULL,
  `note` text,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `outbound_code` varchar(50) DEFAULT NULL,
  `version` int NOT NULL DEFAULT '0',
  `created_by` int DEFAULT NULL,
  `restocked_at` datetime DEFAULT NULL,
  `restocked_by` int DEFAULT NULL,
  PRIMARY KEY (`outbound_id`),
  UNIQUE KEY `uq_outbound_code` (`outbound_code`),
  KEY `idx_outbound_status_date` (`status`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `password_reset_tokens`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `password_reset_tokens` (
  `token_id` int NOT NULL AUTO_INCREMENT,
  `customer_id` int NOT NULL,
  `token` varchar(64) NOT NULL,
  `expires_at` datetime NOT NULL,
  `used` tinyint(1) NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`token_id`),
  UNIQUE KEY `token` (`token`),
  KEY `fk_prt_customer` (`customer_id`),
  CONSTRAINT `fk_prt_customer` FOREIGN KEY (`customer_id`) REFERENCES `customers` (`customer_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `physical_inventories`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `physical_inventories` (
  `inventory_check_id` int NOT NULL AUTO_INCREMENT,
  `check_code` varchar(50) NOT NULL,
  `warehouse_id` int NOT NULL,
  `created_by` int NOT NULL,
  `status` enum('DRAFT','IN_PROGRESS','APPROVED') NOT NULL DEFAULT 'DRAFT',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `note` text,
  PRIMARY KEY (`inventory_check_id`),
  UNIQUE KEY `check_code` (`check_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `physical_inventory_details`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `physical_inventory_details` (
  `check_detail_id` int NOT NULL AUTO_INCREMENT,
  `inventory_check_id` int NOT NULL,
  `product_id` int NOT NULL,
  `system_qty` decimal(12,3) NOT NULL DEFAULT '0.000',
  `actual_qty` decimal(12,3) DEFAULT NULL,
  `delta_qty` decimal(12,3) DEFAULT NULL,
  `counted_by` int DEFAULT NULL,
  `counted_at` datetime DEFAULT NULL,
  PRIMARY KEY (`check_detail_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `picking_sheets`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `picking_sheets` (
  `sheet_id` int NOT NULL AUTO_INCREMENT,
  `outbound_id` int NOT NULL,
  `picker_id` int DEFAULT NULL,
  `status` enum('PENDING','IN_PROGRESS','COMPLETED') DEFAULT 'PENDING',
  `started_at` datetime DEFAULT NULL,
  `completed_at` datetime DEFAULT NULL,
  PRIMARY KEY (`sheet_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `product_default_zones`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product_default_zones` (
  `product_id` int NOT NULL,
  `warehouse_id` int NOT NULL,
  `zone_id` int NOT NULL,
  PRIMARY KEY (`product_id`,`warehouse_id`),
  KEY `warehouse_id` (`warehouse_id`),
  KEY `zone_id` (`zone_id`),
  KEY `idx_pdz_product` (`product_id`),
  CONSTRAINT `product_default_zones_ibfk_1` FOREIGN KEY (`product_id`) REFERENCES `products` (`product_id`) ON DELETE CASCADE,
  CONSTRAINT `product_default_zones_ibfk_2` FOREIGN KEY (`warehouse_id`) REFERENCES `warehouses` (`warehouse_id`) ON DELETE CASCADE,
  CONSTRAINT `product_default_zones_ibfk_3` FOREIGN KEY (`zone_id`) REFERENCES `zones` (`zone_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `product_image_migrations`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product_image_migrations` (
  `id` int NOT NULL AUTO_INCREMENT,
  `channel_id` int NOT NULL,
  `source_url` varchar(500) NOT NULL,
  `lazada_image_url` varchar(500) DEFAULT NULL,
  `lazada_image_id` varchar(100) DEFAULT NULL,
  `migrated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_migration_channel_url` (`channel_id`,`source_url`(255)),
  KEY `idx_migration_channel` (`channel_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `product_images`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product_images` (
  `image_id` int NOT NULL AUTO_INCREMENT,
  `product_id` int NOT NULL,
  `image_url` varchar(500) NOT NULL,
  `is_primary` tinyint(1) NOT NULL DEFAULT '0',
  `sort_order` int NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`image_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `product_rop_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product_rop_log` (
  `log_id` int NOT NULL AUTO_INCREMENT,
  `product_id` int NOT NULL,
  `run_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `lookback_days` int NOT NULL DEFAULT '30',
  `d_avg` decimal(12,4) NOT NULL DEFAULT '0.0000',
  `d_max` decimal(12,4) NOT NULL DEFAULT '0.0000',
  `l_avg` decimal(12,4) NOT NULL DEFAULT '0.0000',
  `l_max` decimal(12,4) NOT NULL DEFAULT '0.0000',
  `safety_stock` decimal(12,4) NOT NULL DEFAULT '0.0000',
  `rop_before` decimal(12,3) NOT NULL DEFAULT '0.000',
  `rop_after` decimal(12,3) NOT NULL DEFAULT '0.000',
  `triggered_by` int DEFAULT NULL COMMENT 'userId if manually triggered, 0 if scheduled',
  PRIMARY KEY (`log_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `products`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `products` (
  `product_id` int NOT NULL AUTO_INCREMENT,
  `category_id` int DEFAULT NULL,
  `sku_code` varchar(50) NOT NULL,
  `product_name` varchar(255) NOT NULL,
  `base_price` decimal(15,2) NOT NULL DEFAULT '0.00',
  `attributes_text` varchar(255) DEFAULT NULL,
  `weight_kg` decimal(8,3) DEFAULT NULL,
  `is_new_arrival` tinyint(1) NOT NULL DEFAULT '0',
  `active` tinyint(1) NOT NULL DEFAULT '1',
  `created_by` int DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `barcode` varchar(50) DEFAULT NULL,
  `unit` varchar(30) DEFAULT 'Cái',
  `min_stock` decimal(12,3) DEFAULT '0.000',
  `max_stock` decimal(12,3) DEFAULT '0.000',
  `short_description` varchar(255) DEFAULT NULL COMMENT 'Lazada short_description (<=255 chars)',
  `is_best_seller` tinyint(1) NOT NULL DEFAULT '0',
  `mac_price` decimal(15,4) NOT NULL DEFAULT '0.0000' COMMENT 'Moving Average Cost (Giá vốn bình quân gia quyền)',
  `d_avg` decimal(12,4) NOT NULL DEFAULT '0.0000' COMMENT 'Average daily demand (units/day) over lookback window',
  `d_max` decimal(12,4) NOT NULL DEFAULT '0.0000' COMMENT 'Maximum daily demand observed in lookback window',
  `l_avg` decimal(12,4) NOT NULL DEFAULT '0.0000' COMMENT 'Average lead time in days (PO created → GRN received)',
  `l_max` decimal(12,4) NOT NULL DEFAULT '0.0000' COMMENT 'Maximum lead time in days observed in lookback window',
  `safety_stock` decimal(12,4) NOT NULL DEFAULT '0.0000' COMMENT 'Safety Stock = (D_max×L_max) − (D_avg×L_avg)',
  `rop_calculated` decimal(12,3) NOT NULL DEFAULT '0.000' COMMENT 'Reorder Point = (D_avg×L_avg) + Safety_Stock',
  PRIMARY KEY (`product_id`),
  UNIQUE KEY `sku_code` (`sku_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `push_errors`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `push_errors` (
  `id` int NOT NULL AUTO_INCREMENT,
  `channel_product_id` int DEFAULT NULL,
  `channel_id` int NOT NULL,
  `sku_code` varchar(100) DEFAULT NULL,
  `error_code` varchar(50) DEFAULT NULL,
  `error_message` varchar(500) DEFAULT NULL,
  `field_errors_json` text,
  `raw_response` text,
  `occurred_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_pe_channel` (`channel_id`),
  KEY `idx_pe_occurred` (`occurred_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `qc_inspections`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qc_inspections` (
  `qc_id` int NOT NULL AUTO_INCREMENT,
  `rma_item_id` int NOT NULL,
  `inspected_by` int NOT NULL,
  `good_quantity` decimal(12,3) NOT NULL DEFAULT '0.000',
  `good_zone_id` int DEFAULT NULL,
  `damaged_quantity` decimal(12,3) NOT NULL DEFAULT '0.000',
  `damaged_zone_id` int DEFAULT NULL,
  `notes` text,
  `inspected_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`qc_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `qc_records`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qc_records` (
  `qc_id` int NOT NULL AUTO_INCREMENT,
  `return_id` int NOT NULL,
  `product_id` int DEFAULT NULL,
  `decision` enum('PASS','FAIL') NOT NULL,
  `qc_notes` text,
  `qc_by` int DEFAULT NULL,
  `qc_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`qc_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `receipt_details`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `receipt_details` (
  `detail_id` int NOT NULL AUTO_INCREMENT,
  `receipt_id` int NOT NULL,
  `product_id` int NOT NULL,
  `quantity` decimal(12,3) NOT NULL,
  `unit_cost` decimal(15,2) DEFAULT NULL,
  `note` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`detail_id`),
  KEY `idx_rd_receipt` (`receipt_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `receipt_notes`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `receipt_notes` (
  `receipt_id` int NOT NULL AUTO_INCREMENT,
  `inbound_id` int NOT NULL,
  `warehouse_id` int NOT NULL,
  `received_by` int DEFAULT NULL,
  `note` text,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`receipt_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `return_items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `return_items` (
  `return_item_id` int NOT NULL AUTO_INCREMENT,
  `return_id` int NOT NULL,
  `product_id` int NOT NULL,
  `quantity` decimal(12,3) NOT NULL DEFAULT '1.000',
  `return_reason` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`return_item_id`),
  KEY `return_id` (`return_id`),
  KEY `product_id` (`product_id`),
  CONSTRAINT `return_items_ibfk_1` FOREIGN KEY (`return_id`) REFERENCES `return_orders` (`return_id`) ON DELETE CASCADE,
  CONSTRAINT `return_items_ibfk_2` FOREIGN KEY (`product_id`) REFERENCES `products` (`product_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `return_orders`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `return_orders` (
  `return_id` int NOT NULL AUTO_INCREMENT,
  `order_id` int DEFAULT NULL,
  `outbound_id` int DEFAULT NULL,
  `customer_name` varchar(100) DEFAULT NULL,
  `customer_phone` varchar(20) DEFAULT NULL,
  `reason` varchar(255) DEFAULT NULL,
  `status` enum('RECEIVED','INSPECTING','PASS','FAIL','RESTOCKED','SCRAPPED') DEFAULT 'RECEIVED',
  `warehouse_id` int NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `return_code` varchar(50) DEFAULT NULL,
  PRIMARY KEY (`return_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `rma_items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `rma_items` (
  `rma_item_id` int NOT NULL AUTO_INCREMENT,
  `rma_id` int NOT NULL,
  `product_id` int NOT NULL,
  `channel_return_item_id` varchar(100) DEFAULT NULL,
  `quantity` decimal(12,3) NOT NULL DEFAULT '1.000',
  `refund_amount` decimal(15,2) DEFAULT NULL,
  PRIMARY KEY (`rma_item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `rma_requests`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `rma_requests` (
  `rma_id` int NOT NULL AUTO_INCREMENT,
  `order_id` int NOT NULL,
  `channel_return_id` varchar(100) DEFAULT NULL,
  `return_waybill` varchar(100) DEFAULT NULL,
  `rma_code` varchar(50) NOT NULL,
  `status` enum('PENDING','APPROVED','DISPUTED','RESOLVED') NOT NULL DEFAULT 'PENDING',
  `return_reason` varchar(255) NOT NULL,
  `zone_id` int DEFAULT NULL,
  `requested_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `returned_at` datetime DEFAULT NULL,
  `evidence_photos` text COMMENT 'Comma-separated URLs of customer-uploaded return photos',
  `evidence_video` varchar(255) DEFAULT NULL COMMENT 'URL of customer-uploaded return video',
  `resolution_note` varchar(255) DEFAULT NULL COMMENT 'Sales note when approving/rejecting the return request',
  PRIMARY KEY (`rma_id`),
  UNIQUE KEY `rma_code` (`rma_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `roles`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `roles` (
  `role_id` int NOT NULL AUTO_INCREMENT,
  `role_name` varchar(50) NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`role_id`),
  UNIQUE KEY `role_name` (`role_name`)
) ENGINE=InnoDB AUTO_INCREMENT=2108 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `rtv_items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `rtv_items` (
  `rtv_item_id` int NOT NULL AUTO_INCREMENT,
  `rtv_id` int NOT NULL,
  `product_id` int NOT NULL,
  `qty_return` decimal(12,3) NOT NULL,
  `unit_cost` decimal(12,3) NOT NULL,
  PRIMARY KEY (`rtv_item_id`),
  KEY `rtv_id` (`rtv_id`),
  CONSTRAINT `rtv_items_ibfk_1` FOREIGN KEY (`rtv_id`) REFERENCES `rtv_orders` (`rtv_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `rtv_orders`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `rtv_orders` (
  `rtv_id` int NOT NULL AUTO_INCREMENT,
  `rtv_code` varchar(50) NOT NULL,
  `inbound_id` int NOT NULL,
  `warehouse_id` int NOT NULL,
  `supplier` varchar(255) NOT NULL,
  `status` varchar(30) NOT NULL DEFAULT 'PENDING',
  `reason` text,
  `note` text,
  `created_by` int DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `po_code` varchar(50) DEFAULT NULL,
  `supplier_code` varchar(50) DEFAULT NULL,
  `contact_person` varchar(100) DEFAULT NULL,
  `proposal` varchar(100) DEFAULT NULL,
  `evidence_link` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`rtv_id`),
  UNIQUE KEY `rtv_code` (`rtv_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `scrap_records`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `scrap_records` (
  `scrap_id` int NOT NULL AUTO_INCREMENT,
  `return_id` int NOT NULL,
  `product_id` int DEFAULT NULL,
  `qty` decimal(12,3) NOT NULL DEFAULT '1.000',
  `reason` varchar(255) DEFAULT NULL,
  `scrap_by` int DEFAULT NULL,
  `scrap_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`scrap_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `shipping_carriers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `shipping_carriers` (
  `carrier_id` int NOT NULL AUTO_INCREMENT,
  `carrier_code` varchar(50) NOT NULL,
  `carrier_name` varchar(100) NOT NULL,
  `platform` varchar(50) DEFAULT NULL,
  `priority` int NOT NULL DEFAULT '0',
  `is_active` tinyint(1) NOT NULL DEFAULT '1',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`carrier_id`),
  UNIQUE KEY `carrier_code` (`carrier_code`),
  KEY `idx_carriers_active_priority` (`is_active`,`priority`),
  KEY `idx_carriers_platform` (`platform`)
) ENGINE=InnoDB AUTO_INCREMENT=1961 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `shipping_labels`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `shipping_labels` (
  `label_id` int NOT NULL AUTO_INCREMENT,
  `order_id` int NOT NULL,
  `outbound_id` int DEFAULT NULL,
  `carrier` varchar(50) DEFAULT NULL,
  `tracking_no` varchar(100) DEFAULT NULL,
  `label_url` varchar(255) DEFAULT NULL,
  `printed` tinyint(1) DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`label_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `sku_mappings`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sku_mappings` (
  `mapping_id` int NOT NULL AUTO_INCREMENT,
  `sku_id` int NOT NULL,
  `channel_id` int NOT NULL,
  `external_sku` varchar(100) DEFAULT NULL,
  `seller_sku` varchar(100) DEFAULT NULL,
  `sync_status` enum('SYNCED','PENDING','ERROR') DEFAULT 'PENDING',
  `last_sync_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`mapping_id`),
  UNIQUE KEY `uq_sku_channel` (`sku_id`,`channel_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `skus`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `skus` (
  `sku_id` int NOT NULL AUTO_INCREMENT,
  `sku_code` varchar(50) NOT NULL,
  `product_name` varchar(150) NOT NULL,
  `category` varchar(80) DEFAULT NULL,
  `unit` varchar(30) NOT NULL DEFAULT 'Cái',
  `barcode` varchar(50) DEFAULT NULL,
  `weight_kg` decimal(8,3) DEFAULT NULL,
  `description` text,
  `min_stock` int NOT NULL DEFAULT '0',
  `active` tinyint(1) NOT NULL DEFAULT '1',
  `created_by` int DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`sku_id`),
  UNIQUE KEY `sku_code` (`sku_code`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `stock_transfer_items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stock_transfer_items` (
  `transfer_item_id` int NOT NULL AUTO_INCREMENT,
  `transfer_id` int NOT NULL,
  `product_id` int NOT NULL,
  `shipped_qty` decimal(12,3) NOT NULL,
  `received_qty` decimal(12,3) DEFAULT NULL,
  PRIMARY KEY (`transfer_item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `stock_transfers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stock_transfers` (
  `transfer_id` int NOT NULL AUTO_INCREMENT,
  `transfer_code` varchar(50) NOT NULL,
  `from_warehouse_id` int NOT NULL,
  `to_warehouse_id` int NOT NULL,
  `created_by` int NOT NULL,
  `approved_by` int DEFAULT NULL,
  `status` enum('DRAFT','IN_TRANSIT','RECEIVED','CANCELLED') NOT NULL DEFAULT 'DRAFT',
  `note` text,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `completed_at` datetime DEFAULT NULL,
  PRIMARY KEY (`transfer_id`),
  UNIQUE KEY `transfer_code` (`transfer_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `stocktake_items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stocktake_items` (
  `item_id` int NOT NULL AUTO_INCREMENT,
  `stocktake_id` int NOT NULL,
  `product_id` int NOT NULL,
  `system_qty` int NOT NULL DEFAULT '0',
  `counted_qty` int DEFAULT NULL,
  `variance` int DEFAULT NULL,
  `counted_by` int DEFAULT NULL,
  `counted_at` datetime DEFAULT NULL,
  PRIMARY KEY (`item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `stocktakes`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stocktakes` (
  `stocktake_id` int NOT NULL AUTO_INCREMENT,
  `stocktake_code` varchar(30) NOT NULL,
  `warehouse_id` int NOT NULL,
  `status` enum('PLANNED','IN_PROGRESS','COMPLETED','CANCELLED') DEFAULT 'PLANNED',
  `counted_by` int DEFAULT NULL,
  `approved_by` int DEFAULT NULL,
  `note` text,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `completed_at` datetime DEFAULT NULL,
  PRIMARY KEY (`stocktake_id`),
  UNIQUE KEY `stocktake_code` (`stocktake_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `suppliers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `suppliers` (
  `supplier_id` int NOT NULL AUTO_INCREMENT,
  `supplier_code` varchar(20) NOT NULL,
  `name` varchar(255) NOT NULL,
  `contact_person` varchar(100) DEFAULT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `email` varchar(100) DEFAULT NULL,
  `address` varchar(500) DEFAULT NULL,
  `credit_limit` decimal(15,2) DEFAULT '0.00',
  `payment_terms` varchar(50) DEFAULT NULL,
  `status` enum('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`supplier_id`),
  UNIQUE KEY `supplier_code` (`supplier_code`),
  KEY `idx_supplier_code` (`supplier_code`),
  KEY `idx_supplier_status` (`status`),
  KEY `idx_supplier_name` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `system_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_config` (
  `config_id` int NOT NULL AUTO_INCREMENT,
  `config_key` varchar(100) NOT NULL,
  `config_value` varchar(500) NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  `is_active` tinyint DEFAULT '1',
  `updated_by` int DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`config_id`),
  UNIQUE KEY `config_key` (`config_key`)
) ENGINE=InnoDB AUTO_INCREMENT=500 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `transfer_details`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `transfer_details` (
  `transfer_detail_id` int NOT NULL AUTO_INCREMENT,
  `transfer_id` int NOT NULL,
  `product_id` int NOT NULL,
  `qty` int NOT NULL,
  PRIMARY KEY (`transfer_detail_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `user_warehouse_assignments`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_warehouse_assignments` (
  `assignment_id` int NOT NULL AUTO_INCREMENT,
  `user_id` int NOT NULL,
  `warehouse_id` int NOT NULL,
  `is_primary` tinyint(1) NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`assignment_id`),
  UNIQUE KEY `uq_user_warehouse` (`user_id`,`warehouse_id`)
) ENGINE=InnoDB AUTO_INCREMENT=53 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `users` (
  `user_id` int NOT NULL AUTO_INCREMENT,
  `username` varchar(50) NOT NULL,
  `password_hash` varchar(255) NOT NULL,
  `full_name` varchar(100) NOT NULL,
  `email` varchar(100) NOT NULL,
  `role` enum('ADMIN','MANAGER','SALES_STAFF','WAREHOUSE_STAFF') NOT NULL DEFAULT 'WAREHOUSE_STAFF',
  `active` tinyint(1) NOT NULL DEFAULT '1',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `phone` varchar(20) DEFAULT NULL,
  `otp_preference` varchar(20) DEFAULT 'EMAIL',
  `warehouse_id` int DEFAULT NULL,
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `username` (`username`)
) ENGINE=InnoDB AUTO_INCREMENT=187 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `warehouse_issues`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `warehouse_issues` (
  `issue_id` int NOT NULL AUTO_INCREMENT,
  `issue_code` varchar(50) NOT NULL,
  `warehouse_id` int NOT NULL,
  `issue_type` enum('ORDER','SCRAP','TRANSFER') NOT NULL,
  `ref_order_id` int DEFAULT NULL,
  `transfer_id` int DEFAULT NULL,
  `dest_zone_id` int DEFAULT NULL,
  `created_by` int NOT NULL,
  `copied_from_id` int DEFAULT NULL,
  `status` enum('DRAFT','APPROVED','CANCELLED') NOT NULL DEFAULT 'DRAFT',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`issue_id`),
  UNIQUE KEY `issue_code` (`issue_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `warehouse_receipts`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `warehouse_receipts` (
  `receipt_id` int NOT NULL AUTO_INCREMENT,
  `receipt_code` varchar(50) NOT NULL,
  `warehouse_id` int NOT NULL,
  `receipt_type` enum('PURCHASE','RETURN','TRANSFER') NOT NULL DEFAULT 'PURCHASE',
  `supplier_name` varchar(255) DEFAULT NULL,
  `created_by` int NOT NULL,
  `copied_from_id` int DEFAULT NULL,
  `status` enum('DRAFT','APPROVED','CANCELLED') NOT NULL DEFAULT 'DRAFT',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`receipt_id`),
  UNIQUE KEY `receipt_code` (`receipt_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `warehouses`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `warehouses` (
  `warehouse_id` int NOT NULL AUTO_INCREMENT,
  `warehouse_code` varchar(20) NOT NULL,
  `warehouse_name` varchar(100) NOT NULL,
  `address` varchar(255) DEFAULT NULL,
  `capacity` int DEFAULT '0',
  `active` tinyint(1) NOT NULL DEFAULT '1',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `phone` varchar(20) DEFAULT NULL,
  PRIMARY KEY (`warehouse_id`),
  UNIQUE KEY `warehouse_code` (`warehouse_code`)
) ENGINE=InnoDB AUTO_INCREMENT=100 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `web_sync_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `web_sync_log` (
  `log_id` int NOT NULL AUTO_INCREMENT,
  `channel_id` int DEFAULT NULL,
  `sync_type` varchar(50) NOT NULL,
  `status` enum('SUCCESS','FAILED') NOT NULL,
  `request_data` text,
  `response_data` text,
  `error_msg` text,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`log_id`),
  KEY `idx_wsl_status` (`status`),
  KEY `idx_wsl_channel` (`channel_id`),
  CONSTRAINT `web_sync_log_ibfk_1` FOREIGN KEY (`channel_id`) REFERENCES `channels` (`channel_id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `webhook_logs`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `webhook_logs` (
  `log_id` int NOT NULL AUTO_INCREMENT,
  `channel_id` int DEFAULT NULL,
  `event_type` varchar(50) NOT NULL,
  `payload` text,
  `status` enum('SUCCESS','FAILED','PENDING') NOT NULL DEFAULT 'PENDING',
  `error_trace` text,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`log_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `zones`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `zones` (
  `zone_id` int NOT NULL AUTO_INCREMENT,
  `warehouse_id` int NOT NULL,
  `zone_code` varchar(50) NOT NULL,
  `zone_name` varchar(100) NOT NULL,
  `zone_type` enum('NORMAL','RETURN','DAMAGED','DESTROY') NOT NULL DEFAULT 'NORMAL',
  `description` text,
  `active` tinyint(1) NOT NULL DEFAULT '1',
  `is_default` tinyint(1) NOT NULL DEFAULT '0',
  `capacity` int DEFAULT '0',
  PRIMARY KEY (`zone_id`),
  UNIQUE KEY `uq_zone_code_wh` (`zone_code`,`warehouse_id`)
) ENGINE=InnoDB AUTO_INCREMENT=154 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;


-- ── Seed data: master/config tables ─────────────────────────────────────
SET FOREIGN_KEY_CHECKS = 0;

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

LOCK TABLES `roles` WRITE;
/*!40000 ALTER TABLE `roles` DISABLE KEYS */;
INSERT INTO `roles` (`role_id`, `role_name`, `description`) VALUES (1,'ADMIN','Quan tri he thong'),(2,'MANAGER','Quan ly kinh doanh'),(3,'SALES_STAFF','Nhan vien ban hang'),(4,'WAREHOUSE_STAFF','Nhan vien kho');
/*!40000 ALTER TABLE `roles` ENABLE KEYS */;
UNLOCK TABLES;

LOCK TABLES `warehouses` WRITE;
/*!40000 ALTER TABLE `warehouses` DISABLE KEYS */;
INSERT INTO `warehouses` (`warehouse_id`, `warehouse_code`, `warehouse_name`, `address`, `capacity`, `active`, `created_at`, `phone`) VALUES (1,'WH-01','Kho Hà Nội','Số 1 Đường ABC, Phường Cầu Giấy, Quận Cầu Giấy, Hà Nội',10000,1,'2026-06-13 22:29:35','02412345678'),(2,'WH-02','Kho TP. Hồ Chí Minh','Số 120 Đường XYZ, Phường Bến Nghé, Quận 1, TP. HCM',8000,1,'2026-06-13 22:29:35','02812345678');
/*!40000 ALTER TABLE `warehouses` ENABLE KEYS */;
UNLOCK TABLES;

LOCK TABLES `zones` WRITE;
/*!40000 ALTER TABLE `zones` DISABLE KEYS */;
INSERT INTO `zones` (`zone_id`, `warehouse_id`, `zone_code`, `zone_name`, `zone_type`, `description`, `active`, `is_default`, `capacity`) VALUES (1,1,'WH01-NORMAL','Khu Thường WH012','NORMAL','Khu vực lưu trữ hàng tốt WH01',1,1,0),(2,1,'WH01-RETURN','Khu Trả Hàng WH01  02','RETURN','Khu vực tạm giữ hàng',1,0,0),(3,1,'WH01-DAMAGED','Khu Hỏng WH01','DAMAGED','Khu vực hàng hỏng WH01',1,0,0),(5,2,'WH02-NORMAL','Khu Thường WH02','NORMAL','Khu vực lưu trữ hàng tốt WH02',1,1,0),(6,2,'WH02-RETURN','Khu Trả Hàng WH02','RETURN','Khu vực tạm giữ hàng trả WH02',1,0,0),(7,2,'WH02-DAMAGED','Khu Hỏng WH02','DAMAGED','Khu vực hàng hỏng WH02',1,0,0),(8,2,'WH02-DESTROY','Khu Tiêu Hủy WH02','DESTROY','Khu vực tiêu hủy WH02',1,0,0),(119,1,'WH01 - NORMAL','Khu Thường WH012','NORMAL','',1,0,0),(120,1,'NORMAL','Khu Thuong','NORMAL','Khu vuc luu tru hang tot',1,0,0),(121,1,'RETURN','Khu Tra Hang','RETURN','Khu vuc tam giu hang tra',1,0,0),(122,1,'DAMAGED','Khu Hong','DAMAGED','Khu vuc hang hong',1,0,0);
/*!40000 ALTER TABLE `zones` ENABLE KEYS */;
UNLOCK TABLES;

LOCK TABLES `channels` WRITE;
/*!40000 ALTER TABLE `channels` DISABLE KEYS */;
INSERT INTO `channels` (`channel_id`, `channel_name`, `platform`, `api_url`, `api_key`, `app_secret`, `webhook_secret`, `buffer_stock`, `is_active`, `access_token`, `refresh_token`, `created_at`, `updated_at`, `token_expires_at`, `last_order_sync_at`, `webhook_callback_url`) VALUES (38,'Own Website','Website','http://localhost:8080/omnicore-web','OCW-APIKEY-7F3K9MXPQZ2RVNTH','OCW-W8SSS2TTNNE52NQESVOP594YZP9X8TCS',NULL,0.000,1,NULL,NULL,'2026-07-10 11:42:52','2026-07-14 00:14:40',NULL,NULL,NULL);
/*!40000 ALTER TABLE `channels` ENABLE KEYS */;
UNLOCK TABLES;

LOCK TABLES `system_config` WRITE;
/*!40000 ALTER TABLE `system_config` DISABLE KEYS */;
INSERT INTO `system_config` (`config_id`, `config_key`, `config_value`, `description`, `is_active`, `updated_by`, `updated_at`) VALUES (1,'pricing.warn_margin_low','0.10','Margin duoi nguong nay duoc canh bao Lai it (mac dinh 10%)',1,NULL,'2026-07-13 23:22:20'),(2,'pricing.warn_margin_breakeven','0.00','Margin duoi nguong nay duoc canh bao Hoa von/Lo nhe (mac dinh 0%)',1,NULL,'2026-07-13 23:13:46'),(3,'pricing.warn_margin_loss_threshold','-0.05','Margin duoi nguong nay duoc canh bao Ban lo (mac dinh -5%)',1,NULL,'2026-07-13 23:13:46'),(99,'website.mock_shipping.enabled','1','Bat/tat mo phong don vi van chuyen cho kenh Website (chon hang o checkout, tem van don gia)',1,NULL,'2026-07-14 14:40:57');
/*!40000 ALTER TABLE `system_config` ENABLE KEYS */;
UNLOCK TABLES;

LOCK TABLES `shipping_carriers` WRITE;
/*!40000 ALTER TABLE `shipping_carriers` DISABLE KEYS */;
INSERT INTO `shipping_carriers` (`carrier_id`, `carrier_code`, `carrier_name`, `platform`, `priority`, `is_active`, `created_at`, `updated_at`) VALUES (1,'SPX','SPX Express','Shopee',10,1,'2026-06-18 10:37:54','2026-06-18 10:37:54'),(2,'LZE','Lazada Express','Lazada',20,1,'2026-06-18 10:37:54','2026-06-18 10:37:54'),(3,'TKT','TikTok Express','TikTok',30,1,'2026-06-18 10:37:54','2026-06-18 10:37:54'),(4,'VTP','Viettel Post',NULL,40,1,'2026-06-18 10:37:54','2026-06-18 10:37:54');
/*!40000 ALTER TABLE `shipping_carriers` ENABLE KEYS */;
UNLOCK TABLES;

LOCK TABLES `mock_shipping_carriers` WRITE;
/*!40000 ALTER TABLE `mock_shipping_carriers` DISABLE KEYS */;
INSERT INTO `mock_shipping_carriers` (`carrier_id`, `carrier_name`, `fee`, `is_active`, `display_order`) VALUES (1,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(2,'Giao Hang Nhanh Mock',20000.00,1,2),(3,'Viettel Post Mock',22000.00,1,3),(4,'J&T Express Mock',18000.00,1,4),(5,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(6,'Giao Hang Nhanh Mock',20000.00,1,2),(7,'Viettel Post Mock',22000.00,1,3),(8,'J&T Express Mock',18000.00,1,4),(9,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(10,'Giao Hang Nhanh Mock',20000.00,1,2),(11,'Viettel Post Mock',22000.00,1,3),(12,'J&T Express Mock',18000.00,1,4),(13,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(14,'Giao Hang Nhanh Mock',20000.00,1,2),(15,'Viettel Post Mock',22000.00,1,3),(16,'J&T Express Mock',18000.00,1,4),(17,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(18,'Giao Hang Nhanh Mock',20000.00,1,2),(19,'Viettel Post Mock',22000.00,1,3),(20,'J&T Express Mock',18000.00,1,4),(21,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(22,'Giao Hang Nhanh Mock',20000.00,1,2),(23,'Viettel Post Mock',22000.00,1,3),(24,'J&T Express Mock',18000.00,1,4),(25,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(26,'Giao Hang Nhanh Mock',20000.00,1,2),(27,'Viettel Post Mock',22000.00,1,3),(28,'J&T Express Mock',18000.00,1,4),(29,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(30,'Giao Hang Nhanh Mock',20000.00,1,2),(31,'Viettel Post Mock',22000.00,1,3),(32,'J&T Express Mock',18000.00,1,4),(33,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(34,'Giao Hang Nhanh Mock',20000.00,1,2),(35,'Viettel Post Mock',22000.00,1,3),(36,'J&T Express Mock',18000.00,1,4),(37,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(38,'Giao Hang Nhanh Mock',20000.00,1,2),(39,'Viettel Post Mock',22000.00,1,3),(40,'J&T Express Mock',18000.00,1,4),(41,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(42,'Giao Hang Nhanh Mock',20000.00,1,2),(43,'Viettel Post Mock',22000.00,1,3),(44,'J&T Express Mock',18000.00,1,4),(45,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(46,'Giao Hang Nhanh Mock',20000.00,1,2),(47,'Viettel Post Mock',22000.00,1,3),(48,'J&T Express Mock',18000.00,1,4),(49,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(50,'Giao Hang Nhanh Mock',20000.00,1,2),(51,'Viettel Post Mock',22000.00,1,3),(52,'J&T Express Mock',18000.00,1,4),(53,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(54,'Giao Hang Nhanh Mock',20000.00,1,2),(55,'Viettel Post Mock',22000.00,1,3),(56,'J&T Express Mock',18000.00,1,4),(57,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(58,'Giao Hang Nhanh Mock',20000.00,1,2),(59,'Viettel Post Mock',22000.00,1,3),(60,'J&T Express Mock',18000.00,1,4),(61,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(62,'Giao Hang Nhanh Mock',20000.00,1,2),(63,'Viettel Post Mock',22000.00,1,3),(64,'J&T Express Mock',18000.00,1,4),(65,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(66,'Giao Hang Nhanh Mock',20000.00,1,2),(67,'Viettel Post Mock',22000.00,1,3),(68,'J&T Express Mock',18000.00,1,4),(69,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(70,'Giao Hang Nhanh Mock',20000.00,1,2),(71,'Viettel Post Mock',22000.00,1,3),(72,'J&T Express Mock',18000.00,1,4),(73,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(74,'Giao Hang Nhanh Mock',20000.00,1,2),(75,'Viettel Post Mock',22000.00,1,3),(76,'J&T Express Mock',18000.00,1,4),(77,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(78,'Giao Hang Nhanh Mock',20000.00,1,2),(79,'Viettel Post Mock',22000.00,1,3),(80,'J&T Express Mock',18000.00,1,4),(81,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(82,'Giao Hang Nhanh Mock',20000.00,1,2),(83,'Viettel Post Mock',22000.00,1,3),(84,'J&T Express Mock',18000.00,1,4),(85,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(86,'Giao Hang Nhanh Mock',20000.00,1,2),(87,'Viettel Post Mock',22000.00,1,3),(88,'J&T Express Mock',18000.00,1,4),(89,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(90,'Giao Hang Nhanh Mock',20000.00,1,2),(91,'Viettel Post Mock',22000.00,1,3),(92,'J&T Express Mock',18000.00,1,4),(93,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(94,'Giao Hang Nhanh Mock',20000.00,1,2),(95,'Viettel Post Mock',22000.00,1,3),(96,'J&T Express Mock',18000.00,1,4),(97,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(98,'Giao Hang Nhanh Mock',20000.00,1,2),(99,'Viettel Post Mock',22000.00,1,3),(100,'J&T Express Mock',18000.00,1,4),(101,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(102,'Giao Hang Nhanh Mock',20000.00,1,2),(103,'Viettel Post Mock',22000.00,1,3),(104,'J&T Express Mock',18000.00,1,4),(105,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(106,'Giao Hang Nhanh Mock',20000.00,1,2),(107,'Viettel Post Mock',22000.00,1,3),(108,'J&T Express Mock',18000.00,1,4),(109,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(110,'Giao Hang Nhanh Mock',20000.00,1,2),(111,'Viettel Post Mock',22000.00,1,3),(112,'J&T Express Mock',18000.00,1,4),(113,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(114,'Giao Hang Nhanh Mock',20000.00,1,2),(115,'Viettel Post Mock',22000.00,1,3),(116,'J&T Express Mock',18000.00,1,4),(117,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(118,'Giao Hang Nhanh Mock',20000.00,1,2),(119,'Viettel Post Mock',22000.00,1,3),(120,'J&T Express Mock',18000.00,1,4),(121,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(122,'Giao Hang Nhanh Mock',20000.00,1,2),(123,'Viettel Post Mock',22000.00,1,3),(124,'J&T Express Mock',18000.00,1,4),(125,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(126,'Giao Hang Nhanh Mock',20000.00,1,2),(127,'Viettel Post Mock',22000.00,1,3),(128,'J&T Express Mock',18000.00,1,4),(129,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(130,'Giao Hang Nhanh Mock',20000.00,1,2),(131,'Viettel Post Mock',22000.00,1,3),(132,'J&T Express Mock',18000.00,1,4),(133,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(134,'Giao Hang Nhanh Mock',20000.00,1,2),(135,'Viettel Post Mock',22000.00,1,3),(136,'J&T Express Mock',18000.00,1,4),(137,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(138,'Giao Hang Nhanh Mock',20000.00,1,2),(139,'Viettel Post Mock',22000.00,1,3),(140,'J&T Express Mock',18000.00,1,4),(141,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(142,'Giao Hang Nhanh Mock',20000.00,1,2),(143,'Viettel Post Mock',22000.00,1,3),(144,'J&T Express Mock',18000.00,1,4),(145,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(146,'Giao Hang Nhanh Mock',20000.00,1,2),(147,'Viettel Post Mock',22000.00,1,3),(148,'J&T Express Mock',18000.00,1,4),(149,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(150,'Giao Hang Nhanh Mock',20000.00,1,2),(151,'Viettel Post Mock',22000.00,1,3),(152,'J&T Express Mock',18000.00,1,4),(153,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(154,'Giao Hang Nhanh Mock',20000.00,1,2),(155,'Viettel Post Mock',22000.00,1,3),(156,'J&T Express Mock',18000.00,1,4),(157,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(158,'Giao Hang Nhanh Mock',20000.00,1,2),(159,'Viettel Post Mock',22000.00,1,3),(160,'J&T Express Mock',18000.00,1,4),(161,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(162,'Giao Hang Nhanh Mock',20000.00,1,2),(163,'Viettel Post Mock',22000.00,1,3),(164,'J&T Express Mock',18000.00,1,4),(165,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(166,'Giao Hang Nhanh Mock',20000.00,1,2),(167,'Viettel Post Mock',22000.00,1,3),(168,'J&T Express Mock',18000.00,1,4),(169,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(170,'Giao Hang Nhanh Mock',20000.00,1,2),(171,'Viettel Post Mock',22000.00,1,3),(172,'J&T Express Mock',18000.00,1,4),(173,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(174,'Giao Hang Nhanh Mock',20000.00,1,2),(175,'Viettel Post Mock',22000.00,1,3),(176,'J&T Express Mock',18000.00,1,4),(177,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(178,'Giao Hang Nhanh Mock',20000.00,1,2),(179,'Viettel Post Mock',22000.00,1,3),(180,'J&T Express Mock',18000.00,1,4),(181,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(182,'Giao Hang Nhanh Mock',20000.00,1,2),(183,'Viettel Post Mock',22000.00,1,3),(184,'J&T Express Mock',18000.00,1,4),(185,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(186,'Giao Hang Nhanh Mock',20000.00,1,2),(187,'Viettel Post Mock',22000.00,1,3),(188,'J&T Express Mock',18000.00,1,4),(189,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(190,'Giao Hang Nhanh Mock',20000.00,1,2),(191,'Viettel Post Mock',22000.00,1,3),(192,'J&T Express Mock',18000.00,1,4),(193,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(194,'Giao Hang Nhanh Mock',20000.00,1,2),(195,'Viettel Post Mock',22000.00,1,3),(196,'J&T Express Mock',18000.00,1,4),(197,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(198,'Giao Hang Nhanh Mock',20000.00,1,2),(199,'Viettel Post Mock',22000.00,1,3),(200,'J&T Express Mock',18000.00,1,4),(201,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(202,'Giao Hang Nhanh Mock',20000.00,1,2),(203,'Viettel Post Mock',22000.00,1,3),(204,'J&T Express Mock',18000.00,1,4),(205,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(206,'Giao Hang Nhanh Mock',20000.00,1,2),(207,'Viettel Post Mock',22000.00,1,3),(208,'J&T Express Mock',18000.00,1,4),(209,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(210,'Giao Hang Nhanh Mock',20000.00,1,2),(211,'Viettel Post Mock',22000.00,1,3),(212,'J&T Express Mock',18000.00,1,4),(213,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(214,'Giao Hang Nhanh Mock',20000.00,1,2),(215,'Viettel Post Mock',22000.00,1,3),(216,'J&T Express Mock',18000.00,1,4),(217,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(218,'Giao Hang Nhanh Mock',20000.00,1,2),(219,'Viettel Post Mock',22000.00,1,3),(220,'J&T Express Mock',18000.00,1,4),(221,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(222,'Giao Hang Nhanh Mock',20000.00,1,2),(223,'Viettel Post Mock',22000.00,1,3),(224,'J&T Express Mock',18000.00,1,4),(225,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(226,'Giao Hang Nhanh Mock',20000.00,1,2),(227,'Viettel Post Mock',22000.00,1,3),(228,'J&T Express Mock',18000.00,1,4),(229,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(230,'Giao Hang Nhanh Mock',20000.00,1,2),(231,'Viettel Post Mock',22000.00,1,3),(232,'J&T Express Mock',18000.00,1,4),(233,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(234,'Giao Hang Nhanh Mock',20000.00,1,2),(235,'Viettel Post Mock',22000.00,1,3),(236,'J&T Express Mock',18000.00,1,4),(237,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(238,'Giao Hang Nhanh Mock',20000.00,1,2),(239,'Viettel Post Mock',22000.00,1,3),(240,'J&T Express Mock',18000.00,1,4),(241,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(242,'Giao Hang Nhanh Mock',20000.00,1,2),(243,'Viettel Post Mock',22000.00,1,3),(244,'J&T Express Mock',18000.00,1,4),(245,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(246,'Giao Hang Nhanh Mock',20000.00,1,2),(247,'Viettel Post Mock',22000.00,1,3),(248,'J&T Express Mock',18000.00,1,4),(249,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(250,'Giao Hang Nhanh Mock',20000.00,1,2),(251,'Viettel Post Mock',22000.00,1,3),(252,'J&T Express Mock',18000.00,1,4),(253,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(254,'Giao Hang Nhanh Mock',20000.00,1,2),(255,'Viettel Post Mock',22000.00,1,3),(256,'J&T Express Mock',18000.00,1,4),(257,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(258,'Giao Hang Nhanh Mock',20000.00,1,2),(259,'Viettel Post Mock',22000.00,1,3),(260,'J&T Express Mock',18000.00,1,4),(261,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(262,'Giao Hang Nhanh Mock',20000.00,1,2),(263,'Viettel Post Mock',22000.00,1,3),(264,'J&T Express Mock',18000.00,1,4),(265,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(266,'Giao Hang Nhanh Mock',20000.00,1,2),(267,'Viettel Post Mock',22000.00,1,3),(268,'J&T Express Mock',18000.00,1,4),(269,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(270,'Giao Hang Nhanh Mock',20000.00,1,2),(271,'Viettel Post Mock',22000.00,1,3),(272,'J&T Express Mock',18000.00,1,4),(273,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(274,'Giao Hang Nhanh Mock',20000.00,1,2),(275,'Viettel Post Mock',22000.00,1,3),(276,'J&T Express Mock',18000.00,1,4),(277,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(278,'Giao Hang Nhanh Mock',20000.00,1,2),(279,'Viettel Post Mock',22000.00,1,3),(280,'J&T Express Mock',18000.00,1,4),(281,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(282,'Giao Hang Nhanh Mock',20000.00,1,2),(283,'Viettel Post Mock',22000.00,1,3),(284,'J&T Express Mock',18000.00,1,4),(285,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(286,'Giao Hang Nhanh Mock',20000.00,1,2),(287,'Viettel Post Mock',22000.00,1,3),(288,'J&T Express Mock',18000.00,1,4),(289,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(290,'Giao Hang Nhanh Mock',20000.00,1,2),(291,'Viettel Post Mock',22000.00,1,3),(292,'J&T Express Mock',18000.00,1,4),(293,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(294,'Giao Hang Nhanh Mock',20000.00,1,2),(295,'Viettel Post Mock',22000.00,1,3),(296,'J&T Express Mock',18000.00,1,4),(297,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(298,'Giao Hang Nhanh Mock',20000.00,1,2),(299,'Viettel Post Mock',22000.00,1,3),(300,'J&T Express Mock',18000.00,1,4),(301,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(302,'Giao Hang Nhanh Mock',20000.00,1,2),(303,'Viettel Post Mock',22000.00,1,3),(304,'J&T Express Mock',18000.00,1,4),(305,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(306,'Giao Hang Nhanh Mock',20000.00,1,2),(307,'Viettel Post Mock',22000.00,1,3),(308,'J&T Express Mock',18000.00,1,4),(309,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(310,'Giao Hang Nhanh Mock',20000.00,1,2),(311,'Viettel Post Mock',22000.00,1,3),(312,'J&T Express Mock',18000.00,1,4),(313,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(314,'Giao Hang Nhanh Mock',20000.00,1,2),(315,'Viettel Post Mock',22000.00,1,3),(316,'J&T Express Mock',18000.00,1,4),(317,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(318,'Giao Hang Nhanh Mock',20000.00,1,2),(319,'Viettel Post Mock',22000.00,1,3),(320,'J&T Express Mock',18000.00,1,4),(321,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(322,'Giao Hang Nhanh Mock',20000.00,1,2),(323,'Viettel Post Mock',22000.00,1,3),(324,'J&T Express Mock',18000.00,1,4),(325,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(326,'Giao Hang Nhanh Mock',20000.00,1,2),(327,'Viettel Post Mock',22000.00,1,3),(328,'J&T Express Mock',18000.00,1,4),(329,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(330,'Giao Hang Nhanh Mock',20000.00,1,2),(331,'Viettel Post Mock',22000.00,1,3),(332,'J&T Express Mock',18000.00,1,4),(333,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(334,'Giao Hang Nhanh Mock',20000.00,1,2),(335,'Viettel Post Mock',22000.00,1,3),(336,'J&T Express Mock',18000.00,1,4),(337,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(338,'Giao Hang Nhanh Mock',20000.00,1,2),(339,'Viettel Post Mock',22000.00,1,3),(340,'J&T Express Mock',18000.00,1,4),(341,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(342,'Giao Hang Nhanh Mock',20000.00,1,2),(343,'Viettel Post Mock',22000.00,1,3),(344,'J&T Express Mock',18000.00,1,4),(345,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(346,'Giao Hang Nhanh Mock',20000.00,1,2),(347,'Viettel Post Mock',22000.00,1,3),(348,'J&T Express Mock',18000.00,1,4),(349,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(350,'Giao Hang Nhanh Mock',20000.00,1,2),(351,'Viettel Post Mock',22000.00,1,3),(352,'J&T Express Mock',18000.00,1,4),(353,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(354,'Giao Hang Nhanh Mock',20000.00,1,2),(355,'Viettel Post Mock',22000.00,1,3),(356,'J&T Express Mock',18000.00,1,4),(357,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(358,'Giao Hang Nhanh Mock',20000.00,1,2),(359,'Viettel Post Mock',22000.00,1,3),(360,'J&T Express Mock',18000.00,1,4),(361,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(362,'Giao Hang Nhanh Mock',20000.00,1,2),(363,'Viettel Post Mock',22000.00,1,3),(364,'J&T Express Mock',18000.00,1,4),(365,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(366,'Giao Hang Nhanh Mock',20000.00,1,2),(367,'Viettel Post Mock',22000.00,1,3),(368,'J&T Express Mock',18000.00,1,4),(369,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(370,'Giao Hang Nhanh Mock',20000.00,1,2),(371,'Viettel Post Mock',22000.00,1,3),(372,'J&T Express Mock',18000.00,1,4),(373,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(374,'Giao Hang Nhanh Mock',20000.00,1,2),(375,'Viettel Post Mock',22000.00,1,3),(376,'J&T Express Mock',18000.00,1,4),(377,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(378,'Giao Hang Nhanh Mock',20000.00,1,2),(379,'Viettel Post Mock',22000.00,1,3),(380,'J&T Express Mock',18000.00,1,4),(381,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(382,'Giao Hang Nhanh Mock',20000.00,1,2),(383,'Viettel Post Mock',22000.00,1,3),(384,'J&T Express Mock',18000.00,1,4),(385,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(386,'Giao Hang Nhanh Mock',20000.00,1,2),(387,'Viettel Post Mock',22000.00,1,3),(388,'J&T Express Mock',18000.00,1,4),(389,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(390,'Giao Hang Nhanh Mock',20000.00,1,2),(391,'Viettel Post Mock',22000.00,1,3),(392,'J&T Express Mock',18000.00,1,4),(393,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(394,'Giao Hang Nhanh Mock',20000.00,1,2),(395,'Viettel Post Mock',22000.00,1,3),(396,'J&T Express Mock',18000.00,1,4),(397,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(398,'Giao Hang Nhanh Mock',20000.00,1,2),(399,'Viettel Post Mock',22000.00,1,3),(400,'J&T Express Mock',18000.00,1,4),(401,'Giao Hang Tiet Kiem Mock',15000.00,1,1),(402,'Giao Hang Nhanh Mock',20000.00,1,2),(403,'Viettel Post Mock',22000.00,1,3),(404,'J&T Express Mock',18000.00,1,4);
/*!40000 ALTER TABLE `mock_shipping_carriers` ENABLE KEYS */;
UNLOCK TABLES;

LOCK TABLES `lazada_shipment_providers` WRITE;
/*!40000 ALTER TABLE `lazada_shipment_providers` DISABLE KEYS */;
INSERT INTO `lazada_shipment_providers` (`provider_id`, `region`, `provider_code`, `provider_name`, `provider_name_vn`, `is_active`, `display_order`) VALUES (1,'VN','FM49','Flash Express','Flash Express',1,1),(2,'VN','J&T','J&T Express','J&T Express',1,2),(3,'VN','GHTK','Giao Hang Tiet Kiem','GHTK',1,3),(4,'VN','GHN','Giao Hang Nhanh','GHN',1,4),(5,'VN','NJV','NinjaVan','NinjaVan',1,5),(6,'VN','SPX','SPX Express','SPX Express',1,6);
/*!40000 ALTER TABLE `lazada_shipment_providers` ENABLE KEYS */;
UNLOCK TABLES;

LOCK TABLES `categories` WRITE;
/*!40000 ALTER TABLE `categories` DISABLE KEYS */;
INSERT INTO `categories` (`category_id`, `parent_id`, `category_name`, `level_depth`, `active`, `description`, `category_code`, `is_immutable`, `created_at`, `updated_at`) VALUES (1,NULL,'Kính mắt',0,1,NULL,'eyewear',1,'2026-06-19 08:09:50','2026-06-21 16:45:07'),(2,NULL,'Phụ kiện cổ',0,1,NULL,'neckwear',1,'2026-06-19 08:09:50','2026-06-21 16:45:07'),(3,NULL,'Phụ kiện tóc',0,1,NULL,'hair-acc',1,'2026-06-19 08:09:50','2026-06-21 16:45:07'),(4,NULL,'Trang sức mĩ ký',0,1,NULL,'fash-jwl',1,'2026-06-19 08:09:50','2026-06-21 16:45:07'),(5,NULL,'Thắt lưng & Dây nịt',0,1,NULL,'belts',1,'2026-06-19 08:09:50','2026-06-21 16:45:07'),(6,NULL,'Mũ nón',0,1,NULL,'headwear',1,'2026-06-19 08:09:50','2026-06-21 16:45:07'),(7,NULL,'Phụ kiện chống nắng/mùa đông',0,1,NULL,'seasonal',1,'2026-06-19 08:09:50','2026-06-21 16:45:07'),(10,1,'Kính râm',1,1,NULL,'sunglasses',1,'2026-06-19 08:09:50','2026-06-21 16:45:07'),(11,1,'Kính chống ánh sáng xanh',1,1,NULL,'bluelight',1,'2026-06-19 08:09:50','2026-06-21 16:45:07'),(12,1,'Gọng kính thời trang',1,1,NULL,'frames',1,'2026-06-19 08:09:50','2026-06-21 16:45:07'),(20,2,'Cà vạt (Ties)',1,1,NULL,'ties',1,'2026-06-19 08:09:50','2026-06-21 16:45:07'),(21,2,'Nơ (Bowties)',1,1,NULL,'bowties',1,'2026-06-19 08:09:50','2026-06-21 16:45:07'),(22,2,'Khăn lụa',1,1,NULL,'silk-scarf',1,'2026-06-19 08:09:50','2026-06-21 16:45:07'),(23,2,'Khăn choàng len',1,1,NULL,'wool-scarf',1,'2026-06-19 08:09:50','2026-06-21 16:45:07'),(30,3,'Băng đô',1,1,NULL,'headbands',1,'2026-06-19 08:09:50','2026-06-21 16:45:07'),(31,3,'Kẹp tóc',1,1,NULL,'hair-clips',1,'2026-06-19 08:09:50','2026-06-21 16:45:07'),(32,3,'Dây buộc tóc scrunchies',1,1,NULL,'scrunchies',1,'2026-06-19 08:09:50','2026-06-21 16:45:07'),(40,4,'Vòng cổ',1,1,NULL,'necklaces',1,'2026-06-19 08:09:50','2026-06-21 16:45:07'),(41,4,'Bông tai',1,1,NULL,'earrings',1,'2026-06-19 08:09:50','2026-06-21 16:45:07'),(42,4,'Nhẫn freesize',1,1,NULL,'rings',1,'2026-06-19 08:09:50','2026-06-21 16:45:07'),(43,4,'Vòng tay',1,1,NULL,'bracelets',1,'2026-06-19 08:09:50','2026-06-21 16:45:07'),(50,5,'Thắt lưng da',1,1,NULL,'lthr-belts',1,'2026-06-19 08:09:50','2026-06-21 16:45:07'),(51,5,'Thắt lưng vải canvas',1,1,NULL,'cnvs-belts',1,'2026-06-19 08:09:50','2026-06-21 16:45:07'),(60,6,'Mũ lưỡi trai',1,1,NULL,'caps',1,'2026-06-19 08:09:50','2026-06-21 16:45:07'),(61,6,'Mũ vành',1,1,NULL,'brim-hats',1,'2026-06-19 08:09:50','2026-06-21 16:45:07'),(62,6,'Mũ len',1,1,NULL,'beanies',1,'2026-06-19 08:09:50','2026-06-21 16:45:07'),(70,7,'Găng tay',1,1,NULL,'gloves',1,'2026-06-19 08:09:50','2026-06-21 16:45:07'),(71,7,'Tất/Vớ',1,1,NULL,'socks',1,'2026-06-19 08:09:50','2026-06-21 16:45:07');
/*!40000 ALTER TABLE `categories` ENABLE KEYS */;
UNLOCK TABLES;

LOCK TABLES `skus` WRITE;
/*!40000 ALTER TABLE `skus` DISABLE KEYS */;
INSERT INTO `skus` (`sku_id`, `sku_code`, `product_name`, `category`, `unit`, `barcode`, `weight_kg`, `description`, `min_stock`, `active`, `created_by`, `created_at`, `updated_at`) VALUES (1,'TSH-NAM-001','Áo Thun Nam Cotton Organic Coolmate','Thời trang','Cái',NULL,NULL,NULL,20,1,NULL,'2026-06-13 22:29:35','2026-06-13 22:29:35'),(2,'JEAN-SLIM-002','Quần Jeans Nam Slim Fit Co Giãn','Thời trang','Cái',NULL,NULL,NULL,15,1,NULL,'2026-06-13 22:29:35','2026-06-13 22:29:35'),(3,'SUN-CLASS-003','Kính Râm Nam Polarized Chống UV','Thời trang','Cái',NULL,NULL,NULL,10,1,NULL,'2026-06-13 22:29:35','2026-06-13 22:29:35'),(4,'MOU-WIRE-004','Chuột Không Dây Logitech Pebble M350','Điện tử','Cái',NULL,NULL,NULL,5,1,NULL,'2026-06-13 22:29:35','2026-06-13 22:29:35'),(5,'KEY-MECH-005','Bàn Phím Cơ Không Dây Logitech Signature K650','Điện tử','Cái',NULL,NULL,NULL,5,1,NULL,'2026-06-13 22:29:35','2026-06-13 22:29:35'),(6,'BOT-THER-006','Bình Giữ Nhiệt LocknLock 480ml','Gia dụng','Cái',NULL,NULL,NULL,10,1,NULL,'2026-06-13 22:29:35','2026-06-13 22:29:35'),(7,'SUN-SCRE-007','Kem Chống Nắng La Roche-Posay 50ml','Mỹ phẩm','Hộp',NULL,NULL,NULL,15,1,NULL,'2026-06-13 22:29:35','2026-06-13 22:29:35');
/*!40000 ALTER TABLE `skus` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;


-- ── Seed data: 1 demo admin account ─────────────────────────────────────
INSERT INTO `users` (`username`, `password_hash`, `full_name`, `email`, `role`, `active`, `phone`, `otp_preference`, `warehouse_id`) VALUES
('admin', '$2a$12$RwbliBgfMdZC4c/AaPh3ZeZOTVKh4KuWoAcsiDvFz1crQJNpHsZh6', 'Admin Demo', 'admin@example.com', 'ADMIN', 1, NULL, 'EMAIL', NULL);

SET FOREIGN_KEY_CHECKS = 1;
