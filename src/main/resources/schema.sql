-- MySQL dump 10.13  Distrib 8.0.46, for Linux (x86_64)
--
-- Host: localhost    Database: wms_hub
-- ------------------------------------------------------
-- Server version	8.0.46

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

--
-- Table structure for table `categories`
--

DROP TABLE IF EXISTS `categories`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `categories` (
  `category_id` int NOT NULL AUTO_INCREMENT,
  `parent_id` int DEFAULT NULL,
  `category_code` varchar(10) NOT NULL COMMENT 'Ma dinh danh 3-4 ky tu, viet HOA, bat bien sau khi tao',
  `category_name` varchar(100) NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  `level_depth` int DEFAULT '0',
  `is_immutable` tinyint(1) NOT NULL DEFAULT '0' COMMENT '1 = da lock, khong cho sua category_code',
  `active` tinyint(1) NOT NULL DEFAULT '1' COMMENT '1 = dang hoat dong, 0 = ngung hoat dong',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`category_id`),
  UNIQUE KEY `category_code` (`category_code`),
  KEY `idx_cat_parent` (`parent_id`),
  KEY `idx_cat_active` (`active`),
  KEY `idx_cat_code` (`category_code`),
  CONSTRAINT `categories_ibfk_1` FOREIGN KEY (`parent_id`) REFERENCES `categories` (`category_id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=72 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `category_mappings`
--

DROP TABLE IF EXISTS `category_mappings`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `category_mappings` (
  `mapping_id` int NOT NULL AUTO_INCREMENT,
  `channel_id` int NOT NULL,
  `wms_category_id` int NOT NULL,
  `lazada_category_id` bigint NOT NULL,
  `lazada_name` varchar(255) NOT NULL,
  `is_primary` tinyint(1) NOT NULL DEFAULT '0',
  `created_by` int DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`mapping_id`),
  UNIQUE KEY `uk_mappings` (`channel_id`,`wms_category_id`,`lazada_category_id`),
  KEY `idx_mappings_wms` (`wms_category_id`),
  KEY `idx_mappings_channel` (`channel_id`),
  CONSTRAINT `category_mappings_ibfk_1` FOREIGN KEY (`channel_id`) REFERENCES `channels` (`channel_id`) ON DELETE CASCADE,
  CONSTRAINT `category_mappings_ibfk_2` FOREIGN KEY (`wms_category_id`) REFERENCES `categories` (`category_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `channel_products`
--

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
  `lazada_category_id` bigint DEFAULT NULL,
  `brand_id` bigint DEFAULT NULL COMMENT 'Lazada brand_id from /brand/get (mandatory for create/update)',
  `dimensions` varchar(30) DEFAULT NULL COMMENT 'LxWxH cm (package_size)',
  `weight_kg` decimal(8,3) DEFAULT NULL COMMENT 'Weight in kg',
  `seller_sku` varchar(100) DEFAULT NULL,
  `short_description` text,
  `brand` varchar(100) DEFAULT NULL,
  `description` text,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_channel_product` (`channel_id`,`product_id`),
  KEY `product_id` (`product_id`),
  KEY `idx_cp_external` (`channel_sku_code`),
  CONSTRAINT `channel_products_ibfk_1` FOREIGN KEY (`channel_id`) REFERENCES `channels` (`channel_id`) ON DELETE CASCADE,
  CONSTRAINT `channel_products_ibfk_2` FOREIGN KEY (`product_id`) REFERENCES `products` (`product_id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=18 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `channel_sync_audit`
--

DROP TABLE IF EXISTS `channel_sync_audit`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `channel_sync_audit` (
  `id` int NOT NULL AUTO_INCREMENT,
  `channel_id` int NOT NULL,
  `operation` varchar(50) NOT NULL,
  `ref_code` varchar(100) DEFAULT NULL,
  `request_data` text,
  `response_data` text,
  `success` tinyint(1) NOT NULL DEFAULT '1',
  `error_message` text,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_audit_channel` (`channel_id`),
  KEY `idx_audit_op` (`operation`),
  KEY `idx_audit_created` (`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=1668 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `channels`
--

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
  `webhook_callback_url` varchar(512) DEFAULT NULL COMMENT 'URL Lazada calls on order/update events',
  `buffer_stock` decimal(12,3) DEFAULT '0.000',
  `is_active` tinyint(1) DEFAULT '1',
  `access_token` text,
  `refresh_token` text,
  `token_expires_at` datetime DEFAULT NULL COMMENT 'UTC timestamp when access_token expires. NULL = unknown/never.',
  `last_order_sync_at` datetime DEFAULT NULL COMMENT 'Last successful order sync via scheduler.',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `last_product_sync_at` datetime DEFAULT NULL COMMENT 'Last successful product sync via scheduler.',
  PRIMARY KEY (`channel_id`),
  KEY `idx_channels_platform` (`platform`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `delivery_notes`
--

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
  PRIMARY KEY (`delivery_id`),
  KEY `outbound_id` (`outbound_id`),
  KEY `delivered_by` (`delivered_by`),
  CONSTRAINT `delivery_notes_ibfk_1` FOREIGN KEY (`outbound_id`) REFERENCES `outbound_orders` (`outbound_id`),
  CONSTRAINT `delivery_notes_ibfk_2` FOREIGN KEY (`delivered_by`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `fulfillment_request_items`
--

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

--
-- Table structure for table `fulfillment_requests`
--

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

--
-- Table structure for table `inbound_items`
--

DROP TABLE IF EXISTS `inbound_items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inbound_items` (
  `inbound_item_id` int NOT NULL AUTO_INCREMENT,
  `inbound_id` int NOT NULL,
  `product_id` int NOT NULL,
  `expected_qty` decimal(12,3) NOT NULL DEFAULT '0.000',
  `received_qty` decimal(12,3) NOT NULL DEFAULT '0.000',
  `accepted_qty` decimal(12,3) NOT NULL DEFAULT '0.000',
  `rejected_qty` decimal(12,3) NOT NULL DEFAULT '0.000',
  `unit_cost` decimal(15,2) DEFAULT NULL COMMENT 'ÄÆ¡n giÃ¡ nháº­p',
  `lot_number` varchar(50) DEFAULT NULL,
  `expiry_date` date DEFAULT NULL,
  `notes` varchar(255) DEFAULT NULL,
  `reject_reason` varchar(100) DEFAULT NULL,
  PRIMARY KEY (`inbound_item_id`),
  KEY `inbound_id` (`inbound_id`),
  KEY `product_id` (`product_id`),
  CONSTRAINT `inbound_items_ibfk_1` FOREIGN KEY (`inbound_id`) REFERENCES `inbound_orders` (`inbound_id`) ON DELETE CASCADE,
  CONSTRAINT `inbound_items_ibfk_2` FOREIGN KEY (`product_id`) REFERENCES `products` (`product_id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=42 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `inbound_orders`
--

DROP TABLE IF EXISTS `inbound_orders`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inbound_orders` (
  `inbound_id` int NOT NULL AUTO_INCREMENT,
  `inbound_code` varchar(30) NOT NULL,
  `warehouse_id` int NOT NULL,
  `zone_id` int DEFAULT NULL COMMENT 'Khu vuc nhan hang trong kho',
  `supplier` varchar(100) DEFAULT NULL,
  `supplier_address` varchar(255) DEFAULT NULL,
  `supplier_phone` varchar(50) DEFAULT NULL,
  `po_reference` varchar(50) DEFAULT NULL,
  `doc_ref` varchar(100) DEFAULT NULL,
  `delivery_person` varchar(100) DEFAULT NULL,
  `delivery_phone` varchar(50) DEFAULT NULL COMMENT 'SDT nguoi giao',
  `status` enum('PENDING','IN_PROGRESS','RECEIVED','CANCELLED') NOT NULL DEFAULT 'PENDING',
  `received_by` int DEFAULT NULL,
  `checker_id` int DEFAULT NULL,
  `note` text,
  `expected_date` date DEFAULT NULL,
  `payment_terms` varchar(50) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `received_at` datetime DEFAULT NULL,
  `received_date` datetime DEFAULT NULL,
  `checked_at` datetime DEFAULT NULL,
  `created_by` int DEFAULT NULL,
  `supplier_id` int DEFAULT NULL COMMENT 'FK mem toi suppliers.supplier_id',
  PRIMARY KEY (`inbound_id`),
  UNIQUE KEY `inbound_code` (`inbound_code`),
  KEY `warehouse_id` (`warehouse_id`),
  KEY `received_by` (`received_by`),
  KEY `created_by` (`created_by`),
  KEY `idx_inbound_status` (`status`),
  KEY `idx_inbound_status_date` (`status`,`created_at`),
  CONSTRAINT `inbound_orders_ibfk_1` FOREIGN KEY (`warehouse_id`) REFERENCES `warehouses` (`warehouse_id`),
  CONSTRAINT `inbound_orders_ibfk_2` FOREIGN KEY (`received_by`) REFERENCES `users` (`user_id`),
  CONSTRAINT `inbound_orders_ibfk_3` FOREIGN KEY (`created_by`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=14 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `inventory`
--

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
  `stock_type` varchar(20) NOT NULL DEFAULT 'NORMAL',
  `reorder_point` decimal(12,3) DEFAULT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`inventory_id`),
  UNIQUE KEY `uq_product_warehouse` (`product_id`,`warehouse_id`,`stock_type`),
  KEY `idx_inv_product` (`product_id`),
  KEY `idx_inv_warehouse` (`warehouse_id`),
  KEY `idx_stock_type` (`stock_type`),
  KEY `idx_warehouse_product_type` (`warehouse_id`,`product_id`,`stock_type`),
  CONSTRAINT `inventory_ibfk_1` FOREIGN KEY (`product_id`) REFERENCES `products` (`product_id`) ON DELETE CASCADE,
  CONSTRAINT `inventory_ibfk_2` FOREIGN KEY (`warehouse_id`) REFERENCES `warehouses` (`warehouse_id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=171 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `inventory_ledger`
--

DROP TABLE IF EXISTS `inventory_ledger`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inventory_ledger` (
  `ledger_id` int NOT NULL AUTO_INCREMENT,
  `inventory_id` int NOT NULL,
  `product_id` int NOT NULL,
  `warehouse_id` int NOT NULL,
  `transaction_type` enum('INBOUND','OUTBOUND','ADJUSTMENT','TRANSFER_IN','TRANSFER_OUT') NOT NULL,
  `ledger_type` varchar(20) NOT NULL DEFAULT 'NORMAL',
  `ref_document_id` int DEFAULT NULL,
  `qty_change` decimal(12,3) NOT NULL,
  `avail_change` decimal(12,3) NOT NULL,
  `timestamp` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `created_by` int DEFAULT NULL,
  `note` text,
  PRIMARY KEY (`ledger_id`),
  KEY `warehouse_id` (`warehouse_id`),
  KEY `created_by` (`created_by`),
  KEY `idx_ledger_inventory` (`inventory_id`),
  KEY `idx_ledger_product` (`product_id`),
  KEY `idx_ledger_type` (`transaction_type`),
  KEY `idx_ledger_timestamp` (`timestamp`),
  KEY `idx_ledger_sku_wh_time` (`product_id`,`warehouse_id`,`timestamp`),
  KEY `idx_ledger_sku_type` (`product_id`,`transaction_type`),
  CONSTRAINT `inventory_ledger_ibfk_1` FOREIGN KEY (`inventory_id`) REFERENCES `inventory` (`inventory_id`) ON DELETE CASCADE,
  CONSTRAINT `inventory_ledger_ibfk_2` FOREIGN KEY (`product_id`) REFERENCES `products` (`product_id`) ON DELETE CASCADE,
  CONSTRAINT `inventory_ledger_ibfk_3` FOREIGN KEY (`warehouse_id`) REFERENCES `warehouses` (`warehouse_id`) ON DELETE CASCADE,
  CONSTRAINT `inventory_ledger_ibfk_4` FOREIGN KEY (`created_by`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `issue_details`
--

DROP TABLE IF EXISTS `issue_details`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `issue_details` (
  `detail_id` int NOT NULL AUTO_INCREMENT,
  `issue_id` int NOT NULL,
  `product_id` int NOT NULL,
  `quantity` decimal(12,3) NOT NULL,
  `note` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`detail_id`),
  KEY `product_id` (`product_id`),
  KEY `idx_id_issue` (`issue_id`),
  CONSTRAINT `issue_details_ibfk_1` FOREIGN KEY (`issue_id`) REFERENCES `warehouse_issues` (`issue_id`) ON DELETE CASCADE,
  CONSTRAINT `issue_details_ibfk_2` FOREIGN KEY (`product_id`) REFERENCES `products` (`product_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `lazada_categories`
--

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
) ENGINE=InnoDB AUTO_INCREMENT=13985 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `lazada_order_items`
--

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
) ENGINE=InnoDB AUTO_INCREMENT=485 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `lazada_orders`
--

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
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `lazada_shipment_providers`
--

DROP TABLE IF EXISTS `lazada_shipment_providers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lazada_shipment_providers` (
  `provider_id` int NOT NULL AUTO_INCREMENT,
  `region` varchar(10) NOT NULL DEFAULT 'VN',
  `provider_code` varchar(32) NOT NULL,
  `provider_name` varchar(100) NOT NULL,
  `provider_name_vn` varchar(100) NOT NULL,
  `display_order` int NOT NULL DEFAULT '0',
  `is_active` tinyint(1) NOT NULL DEFAULT '1',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`provider_id`),
  UNIQUE KEY `uk_code_region` (`provider_code`,`region`)
) ENGINE=InnoDB AUTO_INCREMENT=267 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `lazada_stock_push_log`
--

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
  PRIMARY KEY (`log_id`),
  KEY `idx_lspl_channel` (`channel_id`),
  KEY `idx_lspl_sku` (`seller_sku`),
  KEY `idx_lspl_created` (`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=1415 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `lazada_sync_log`
--

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
  PRIMARY KEY (`log_id`),
  KEY `idx_lsl_status` (`status`),
  KEY `idx_lsl_channel` (`channel_id`),
  CONSTRAINT `lazada_sync_log_ibfk_1` FOREIGN KEY (`channel_id`) REFERENCES `channels` (`channel_id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=3881 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `mapping_exceptions`
--

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
) ENGINE=InnoDB AUTO_INCREMENT=158 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `mock_shipping_carriers`
--

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
) ENGINE=InnoDB AUTO_INCREMENT=173 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `notifications`
--

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
  KEY `idx_notif_warehouse` (`warehouse_id`),
  KEY `idx_notif_unread` (`recipient_user_id`,`is_read`),
  KEY `idx_notif_created` (`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=895 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `order_items`
--

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
  PRIMARY KEY (`order_item_id`),
  KEY `product_id` (`product_id`),
  KEY `idx_oi_order` (`order_id`),
  CONSTRAINT `order_items_ibfk_1` FOREIGN KEY (`order_id`) REFERENCES `orders` (`order_id`) ON DELETE CASCADE,
  CONSTRAINT `order_items_ibfk_2` FOREIGN KEY (`product_id`) REFERENCES `products` (`product_id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=489 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `order_shipping_details`
--

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
  UNIQUE KEY `order_id` (`order_id`),
  CONSTRAINT `order_shipping_details_ibfk_1` FOREIGN KEY (`order_id`) REFERENCES `orders` (`order_id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=894 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `orders`
--

DROP TABLE IF EXISTS `orders`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `orders` (
  `order_id` int NOT NULL AUTO_INCREMENT,
  `order_code` varchar(30) NOT NULL,
  `customer_id` int DEFAULT NULL,
  `warehouse_id` int DEFAULT NULL,
  `channel` enum('ONLINE','STORE','B2B','WEBSITE') NOT NULL DEFAULT 'ONLINE',
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
  `channel_id` int DEFAULT NULL,
  `lazada_package_id` varchar(100) DEFAULT NULL,
  `is_pack_requested` tinyint(1) DEFAULT '0',
  `is_rts_pushed` tinyint(1) DEFAULT '0',
  `channel_order_id` varchar(50) DEFAULT NULL COMMENT 'Lazada order_id as string (for cross-referencing lazada_orders table)',
  `shipment_provider` varchar(100) DEFAULT NULL,
  `fee_breakdown_json` text,
  `sync_status` varchar(20) DEFAULT 'PENDING',
  `is_label_printed` tinyint(1) NOT NULL DEFAULT '0',
  `web_order_ref` varchar(100) DEFAULT NULL COMMENT 'Dedup key for orders created by omnicore-web',
  `web_customer_ref` varchar(100) DEFAULT NULL COMMENT 'omnicore-web customers.customer_id — reference only, not a real FK',
  `delivered_at` datetime DEFAULT NULL COMMENT 'Stamped when status becomes DELIVERED — any channel; base for the 7-day website return window',
  `shipping_fee` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT 'Website mock shipping: fee for the carrier chosen at checkout, added to total_amount',
  PRIMARY KEY (`order_id`),
  UNIQUE KEY `order_code` (`order_code`),
  UNIQUE KEY `uq_web_order_ref` (`web_order_ref`),
  KEY `warehouse_id` (`warehouse_id`),
  KEY `created_by` (`created_by`),
  KEY `idx_orders_status` (`status`),
  KEY `idx_orders_channel` (`channel_id`),
  KEY `idx_orders_created` (`created_at`),
  KEY `idx_orders_customer_date` (`customer_id`,`created_at`),
  KEY `idx_orders_status_channel` (`status`,`channel_id`),
  CONSTRAINT `orders_ibfk_1` FOREIGN KEY (`channel_id`) REFERENCES `channels` (`channel_id`) ON DELETE SET NULL,
  CONSTRAINT `orders_ibfk_2` FOREIGN KEY (`warehouse_id`) REFERENCES `warehouses` (`warehouse_id`),
  CONSTRAINT `orders_ibfk_3` FOREIGN KEY (`created_by`) REFERENCES `users` (`user_id`),
  CONSTRAINT `orders_ibfk_4` FOREIGN KEY (`customer_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=164 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `outbound_items`
--

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
  PRIMARY KEY (`outbound_item_id`),
  KEY `product_id` (`product_id`),
  KEY `idx_oi_outbound` (`outbound_id`),
  CONSTRAINT `outbound_items_ibfk_1` FOREIGN KEY (`outbound_id`) REFERENCES `outbound_orders` (`outbound_id`) ON DELETE CASCADE,
  CONSTRAINT `outbound_items_ibfk_2` FOREIGN KEY (`product_id`) REFERENCES `products` (`product_id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=14 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `outbound_orders`
--

DROP TABLE IF EXISTS `outbound_orders`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `outbound_orders` (
  `outbound_id` int NOT NULL AUTO_INCREMENT,
  `order_id` int NOT NULL,
  `warehouse_id` int NOT NULL,
  `created_by` int DEFAULT NULL,
  `status` varchar(50) NOT NULL DEFAULT 'PENDING_PACK',
  `restocked` tinyint(1) NOT NULL DEFAULT '0',
  `picked_by` int DEFAULT NULL,
  `shipped_at` datetime DEFAULT NULL,
  `note` text,
  `outbound_code` varchar(50) DEFAULT NULL,
  `version` int NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `restocked_at` datetime DEFAULT NULL COMMENT 'Stamped once released back to available stock after cancel — guards against releasing the same allocation twice',
  `restocked_by` int DEFAULT NULL,
  PRIMARY KEY (`outbound_id`),
  UNIQUE KEY `uq_outbound_code` (`outbound_code`),
  KEY `order_id` (`order_id`),
  KEY `warehouse_id` (`warehouse_id`),
  KEY `picked_by` (`picked_by`),
  KEY `idx_out_status` (`status`),
  KEY `idx_outbound_status_date` (`status`,`created_at`),
  KEY `idx_created_by` (`created_by`),
  CONSTRAINT `outbound_orders_ibfk_1` FOREIGN KEY (`order_id`) REFERENCES `orders` (`order_id`) ON DELETE CASCADE,
  CONSTRAINT `outbound_orders_ibfk_2` FOREIGN KEY (`warehouse_id`) REFERENCES `warehouses` (`warehouse_id`),
  CONSTRAINT `outbound_orders_ibfk_3` FOREIGN KEY (`picked_by`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pending_otp_verifications`
--

DROP TABLE IF EXISTS `pending_otp_verifications`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pending_otp_verifications` (
  `otp_id` bigint NOT NULL AUTO_INCREMENT,
  `channel` enum('EMAIL','SMS') NOT NULL DEFAULT 'EMAIL',
  `purpose` enum('LOGIN_2FA','PASSWORD_RESET','PASSWORD_CHANGE') NOT NULL,
  `identifier` varchar(150) NOT NULL,
  `user_id` int DEFAULT NULL,
  `payload_json` text,
  `otp_code_hash` char(64) NOT NULL,
  `attempts` int NOT NULL DEFAULT '0',
  `max_attempts` int NOT NULL DEFAULT '5',
  `sent_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `expires_at` timestamp NOT NULL,
  `consumed_at` timestamp NULL DEFAULT NULL,
  `resend_count` int NOT NULL DEFAULT '0',
  `last_resend_at` timestamp NULL DEFAULT NULL,
  `created_ip` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`otp_id`),
  KEY `idx_purpose_identifier` (`purpose`,`identifier`),
  KEY `idx_user` (`user_id`),
  KEY `idx_expires` (`expires_at`)
) ENGINE=InnoDB AUTO_INCREMENT=25 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `physical_inventories`
--

DROP TABLE IF EXISTS `physical_inventories`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `physical_inventories` (
  `inventory_check_id` int NOT NULL AUTO_INCREMENT,
  `check_code` varchar(50) NOT NULL,
  `warehouse_id` int NOT NULL,
  `created_by` int NOT NULL,
  `status` enum('DRAFT','IN_PROGRESS','APPROVED') NOT NULL DEFAULT 'DRAFT',
  `note` text,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`inventory_check_id`),
  UNIQUE KEY `check_code` (`check_code`),
  KEY `warehouse_id` (`warehouse_id`),
  KEY `created_by` (`created_by`),
  KEY `idx_pi_status` (`status`),
  CONSTRAINT `physical_inventories_ibfk_1` FOREIGN KEY (`warehouse_id`) REFERENCES `warehouses` (`warehouse_id`),
  CONSTRAINT `physical_inventories_ibfk_2` FOREIGN KEY (`created_by`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `physical_inventory_details`
--

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
  `variance_reason` varchar(255) DEFAULT NULL,
  `lot_number` varchar(50) DEFAULT NULL,
  `counted_by` int DEFAULT NULL,
  `counted_at` datetime DEFAULT NULL,
  PRIMARY KEY (`check_detail_id`),
  KEY `product_id` (`product_id`),
  KEY `counted_by` (`counted_by`),
  KEY `idx_pid_check` (`inventory_check_id`),
  CONSTRAINT `physical_inventory_details_ibfk_1` FOREIGN KEY (`inventory_check_id`) REFERENCES `physical_inventories` (`inventory_check_id`) ON DELETE CASCADE,
  CONSTRAINT `physical_inventory_details_ibfk_2` FOREIGN KEY (`product_id`) REFERENCES `products` (`product_id`) ON DELETE CASCADE,
  CONSTRAINT `physical_inventory_details_ibfk_3` FOREIGN KEY (`counted_by`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `picking_sheets`
--

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
  PRIMARY KEY (`sheet_id`),
  KEY `outbound_id` (`outbound_id`),
  KEY `picker_id` (`picker_id`),
  CONSTRAINT `picking_sheets_ibfk_1` FOREIGN KEY (`outbound_id`) REFERENCES `outbound_orders` (`outbound_id`),
  CONSTRAINT `picking_sheets_ibfk_2` FOREIGN KEY (`picker_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `product_default_zones`
--

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

--
-- Table structure for table `product_image_migrations`
--

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
) ENGINE=InnoDB AUTO_INCREMENT=14 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `product_images`
--

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
  PRIMARY KEY (`image_id`),
  KEY `idx_img_product_primary` (`product_id`,`is_primary`),
  CONSTRAINT `product_images_ibfk_1` FOREIGN KEY (`product_id`) REFERENCES `products` (`product_id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=182 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `product_rop_log`
--

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
) ENGINE=InnoDB AUTO_INCREMENT=202 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `products`
--

DROP TABLE IF EXISTS `products`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `products` (
  `product_id` int NOT NULL AUTO_INCREMENT,
  `category_id` int DEFAULT NULL,
  `sku_code` varchar(50) NOT NULL,
  `product_name` varchar(255) NOT NULL,
  `base_price` decimal(15,2) NOT NULL DEFAULT '0.00',
  `is_new_arrival` tinyint(1) NOT NULL DEFAULT '0',
  `active` tinyint(1) NOT NULL DEFAULT '1',
  `created_by` int DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `barcode` varchar(50) DEFAULT NULL,
  `unit` varchar(30) DEFAULT 'Cái',
  `min_stock` decimal(12,3) DEFAULT '0.000',
  `max_stock` decimal(12,3) DEFAULT '0.000',
  `attributes_text` varchar(255) DEFAULT NULL,
  `weight_kg` decimal(8,3) DEFAULT NULL,
  `short_description` varchar(255) DEFAULT NULL COMMENT 'Lazada short_description (<=255 chars)',
  `dimensions` varchar(30) DEFAULT NULL COMMENT 'Package dimensions in DxRxC cm format, e.g. 15x10x8',
  `mac_price` decimal(15,4) NOT NULL DEFAULT '0.0000' COMMENT 'Moving Average Cost (Giá vốn bình quân gia quyền)',
  `d_avg` decimal(12,4) NOT NULL DEFAULT '0.0000' COMMENT 'Average daily demand (units/day) over lookback window',
  `d_max` decimal(12,4) NOT NULL DEFAULT '0.0000' COMMENT 'Maximum daily demand observed in lookback window',
  `l_avg` decimal(12,4) NOT NULL DEFAULT '0.0000' COMMENT 'Average lead time in days (PO created → GRN received)',
  `l_max` decimal(12,4) NOT NULL DEFAULT '0.0000' COMMENT 'Maximum lead time in days observed in lookback window',
  `safety_stock` decimal(12,4) NOT NULL DEFAULT '0.0000' COMMENT 'Safety Stock = (D_max×L_max) − (D_avg×L_avg)',
  `rop_calculated` decimal(12,3) NOT NULL DEFAULT '0.000' COMMENT 'Reorder Point = (D_avg×L_avg) + Safety_Stock',
  `is_best_seller` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`product_id`),
  UNIQUE KEY `sku_code` (`sku_code`),
  KEY `created_by` (`created_by`),
  KEY `idx_products_category` (`category_id`),
  KEY `idx_products_active` (`active`),
  CONSTRAINT `products_ibfk_1` FOREIGN KEY (`category_id`) REFERENCES `categories` (`category_id`) ON DELETE SET NULL,
  CONSTRAINT `products_ibfk_2` FOREIGN KEY (`created_by`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=78 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `push_errors`
--

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
) ENGINE=InnoDB AUTO_INCREMENT=74 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `qc_inspections`
--

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
  PRIMARY KEY (`qc_id`),
  KEY `rma_item_id` (`rma_item_id`),
  KEY `inspected_by` (`inspected_by`),
  KEY `good_zone_id` (`good_zone_id`),
  KEY `damaged_zone_id` (`damaged_zone_id`),
  CONSTRAINT `qc_inspections_ibfk_1` FOREIGN KEY (`rma_item_id`) REFERENCES `rma_items` (`rma_item_id`) ON DELETE CASCADE,
  CONSTRAINT `qc_inspections_ibfk_2` FOREIGN KEY (`inspected_by`) REFERENCES `users` (`user_id`),
  CONSTRAINT `qc_inspections_ibfk_3` FOREIGN KEY (`good_zone_id`) REFERENCES `zones` (`zone_id`) ON DELETE SET NULL,
  CONSTRAINT `qc_inspections_ibfk_4` FOREIGN KEY (`damaged_zone_id`) REFERENCES `zones` (`zone_id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `qc_records`
--

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
  PRIMARY KEY (`qc_id`),
  KEY `return_id` (`return_id`),
  KEY `product_id` (`product_id`),
  KEY `qc_by` (`qc_by`),
  CONSTRAINT `qc_records_ibfk_1` FOREIGN KEY (`return_id`) REFERENCES `return_orders` (`return_id`) ON DELETE CASCADE,
  CONSTRAINT `qc_records_ibfk_2` FOREIGN KEY (`product_id`) REFERENCES `products` (`product_id`) ON DELETE SET NULL,
  CONSTRAINT `qc_records_ibfk_3` FOREIGN KEY (`qc_by`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `receipt_details`
--

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
  KEY `product_id` (`product_id`),
  KEY `idx_rd_receipt` (`receipt_id`),
  KEY `idx_rd_cost` (`unit_cost`),
  CONSTRAINT `receipt_details_ibfk_1` FOREIGN KEY (`receipt_id`) REFERENCES `warehouse_receipts` (`receipt_id`) ON DELETE CASCADE,
  CONSTRAINT `receipt_details_ibfk_2` FOREIGN KEY (`product_id`) REFERENCES `products` (`product_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `receipt_notes`
--

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
  PRIMARY KEY (`receipt_id`),
  KEY `inbound_id` (`inbound_id`),
  KEY `warehouse_id` (`warehouse_id`),
  KEY `received_by` (`received_by`),
  CONSTRAINT `receipt_notes_ibfk_1` FOREIGN KEY (`inbound_id`) REFERENCES `inbound_orders` (`inbound_id`) ON DELETE CASCADE,
  CONSTRAINT `receipt_notes_ibfk_2` FOREIGN KEY (`warehouse_id`) REFERENCES `warehouses` (`warehouse_id`),
  CONSTRAINT `receipt_notes_ibfk_3` FOREIGN KEY (`received_by`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `return_items`
--

DROP TABLE IF EXISTS `return_items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `return_items` (
  `return_item_id` int NOT NULL AUTO_INCREMENT,
  `return_id` int NOT NULL,
  `product_id` int NOT NULL,
  `quantity` decimal(12,3) NOT NULL DEFAULT '1.000',
  `unit_price` decimal(15,2) DEFAULT '0.00',
  `return_reason` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`return_item_id`),
  KEY `return_id` (`return_id`),
  KEY `product_id` (`product_id`),
  CONSTRAINT `return_items_ibfk_1` FOREIGN KEY (`return_id`) REFERENCES `return_orders` (`return_id`) ON DELETE CASCADE,
  CONSTRAINT `return_items_ibfk_2` FOREIGN KEY (`product_id`) REFERENCES `products` (`product_id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `return_orders`
--

DROP TABLE IF EXISTS `return_orders`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `return_orders` (
  `return_id` int NOT NULL AUTO_INCREMENT,
  `return_code` varchar(50) DEFAULT NULL,
  `order_id` int DEFAULT NULL,
  `outbound_id` int DEFAULT NULL,
  `customer_name` varchar(100) DEFAULT NULL,
  `customer_phone` varchar(20) DEFAULT NULL,
  `reason` varchar(255) DEFAULT NULL,
  `status` enum('RECEIVED','INSPECTING','PASS','FAIL','RESTOCKED','SCRAPPED') DEFAULT 'RECEIVED',
  `warehouse_id` int NOT NULL,
  `created_by` int DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`return_id`),
  KEY `order_id` (`order_id`),
  KEY `outbound_id` (`outbound_id`),
  KEY `warehouse_id` (`warehouse_id`),
  KEY `idx_ro_status` (`status`),
  KEY `idx_ro_code` (`return_code`),
  CONSTRAINT `return_orders_ibfk_1` FOREIGN KEY (`order_id`) REFERENCES `orders` (`order_id`) ON DELETE SET NULL,
  CONSTRAINT `return_orders_ibfk_2` FOREIGN KEY (`outbound_id`) REFERENCES `outbound_orders` (`outbound_id`) ON DELETE SET NULL,
  CONSTRAINT `return_orders_ibfk_3` FOREIGN KEY (`warehouse_id`) REFERENCES `warehouses` (`warehouse_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `rma_items`
--

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
  PRIMARY KEY (`rma_item_id`),
  KEY `product_id` (`product_id`),
  KEY `idx_rmai_rma` (`rma_id`),
  CONSTRAINT `rma_items_ibfk_1` FOREIGN KEY (`rma_id`) REFERENCES `rma_requests` (`rma_id`) ON DELETE CASCADE,
  CONSTRAINT `rma_items_ibfk_2` FOREIGN KEY (`product_id`) REFERENCES `products` (`product_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `rma_requests`
--

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
  UNIQUE KEY `rma_code` (`rma_code`),
  KEY `zone_id` (`zone_id`),
  KEY `idx_rma_status` (`status`),
  KEY `idx_rma_order` (`order_id`),
  CONSTRAINT `rma_requests_ibfk_1` FOREIGN KEY (`order_id`) REFERENCES `orders` (`order_id`) ON DELETE CASCADE,
  CONSTRAINT `rma_requests_ibfk_2` FOREIGN KEY (`zone_id`) REFERENCES `zones` (`zone_id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `roles`
--

DROP TABLE IF EXISTS `roles`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `roles` (
  `role_id` int NOT NULL AUTO_INCREMENT,
  `role_name` varchar(50) NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`role_id`),
  UNIQUE KEY `role_name` (`role_name`)
) ENGINE=InnoDB AUTO_INCREMENT=8426 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `rtv_items`
--

DROP TABLE IF EXISTS `rtv_items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `rtv_items` (
  `rtv_item_id` int NOT NULL AUTO_INCREMENT,
  `rtv_id` int NOT NULL,
  `product_id` int NOT NULL,
  `qty_return` int NOT NULL DEFAULT '0',
  `unit_cost` decimal(15,2) DEFAULT NULL,
  `uom` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'CÃ¡i',
  PRIMARY KEY (`rtv_item_id`),
  KEY `idx_rtv` (`rtv_id`),
  KEY `idx_product` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `rtv_orders`
--

DROP TABLE IF EXISTS `rtv_orders`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `rtv_orders` (
  `rtv_id` int NOT NULL AUTO_INCREMENT,
  `rtv_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `inbound_id` int NOT NULL,
  `supplier` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `warehouse_id` int NOT NULL,
  `status` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING',
  `reason` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `created_by` int DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `approved_by` int DEFAULT NULL,
  `approved_at` datetime DEFAULT NULL,
  `completed_by` int DEFAULT NULL,
  `completed_at` datetime DEFAULT NULL,
  `note` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `po_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `supplier_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `contact_person` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `proposal` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `evidence_link` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`rtv_id`),
  UNIQUE KEY `rtv_code` (`rtv_code`),
  KEY `idx_status` (`status`),
  KEY `idx_inbound` (`inbound_id`),
  KEY `idx_warehouse` (`warehouse_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `scrap_records`
--

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
  PRIMARY KEY (`scrap_id`),
  KEY `return_id` (`return_id`),
  KEY `product_id` (`product_id`),
  KEY `scrap_by` (`scrap_by`),
  CONSTRAINT `scrap_records_ibfk_1` FOREIGN KEY (`return_id`) REFERENCES `return_orders` (`return_id`) ON DELETE CASCADE,
  CONSTRAINT `scrap_records_ibfk_2` FOREIGN KEY (`product_id`) REFERENCES `products` (`product_id`) ON DELETE SET NULL,
  CONSTRAINT `scrap_records_ibfk_3` FOREIGN KEY (`scrap_by`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `shipping_carriers`
--

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
) ENGINE=InnoDB AUTO_INCREMENT=8418 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `shipping_labels`
--

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
  PRIMARY KEY (`label_id`),
  KEY `idx_sl_order` (`order_id`),
  CONSTRAINT `shipping_labels_ibfk_1` FOREIGN KEY (`order_id`) REFERENCES `orders` (`order_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sku_mappings`
--

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
  UNIQUE KEY `uq_sku_channel` (`sku_id`,`channel_id`),
  KEY `channel_id` (`channel_id`),
  CONSTRAINT `sku_mappings_ibfk_1` FOREIGN KEY (`sku_id`) REFERENCES `products` (`product_id`),
  CONSTRAINT `sku_mappings_ibfk_2` FOREIGN KEY (`channel_id`) REFERENCES `channels` (`channel_id`)
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `skus`
--

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
  `description` text,
  `min_stock` int NOT NULL DEFAULT '0',
  `active` tinyint(1) NOT NULL DEFAULT '1',
  `created_by` int DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`sku_id`),
  UNIQUE KEY `sku_code` (`sku_code`),
  KEY `created_by` (`created_by`),
  CONSTRAINT `skus_ibfk_1` FOREIGN KEY (`created_by`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `stock_transfer_items`
--

DROP TABLE IF EXISTS `stock_transfer_items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stock_transfer_items` (
  `transfer_item_id` int NOT NULL AUTO_INCREMENT,
  `transfer_id` int NOT NULL,
  `product_id` int NOT NULL,
  `shipped_qty` decimal(12,3) NOT NULL,
  `received_qty` decimal(12,3) DEFAULT NULL,
  `lot_number` varchar(50) DEFAULT NULL,
  `notes` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`transfer_item_id`),
  KEY `product_id` (`product_id`),
  KEY `idx_sti_transfer` (`transfer_id`),
  CONSTRAINT `stock_transfer_items_ibfk_1` FOREIGN KEY (`transfer_id`) REFERENCES `stock_transfers` (`transfer_id`) ON DELETE CASCADE,
  CONSTRAINT `stock_transfer_items_ibfk_2` FOREIGN KEY (`product_id`) REFERENCES `products` (`product_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `stock_transfers`
--

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
  UNIQUE KEY `transfer_code` (`transfer_code`),
  KEY `from_warehouse_id` (`from_warehouse_id`),
  KEY `to_warehouse_id` (`to_warehouse_id`),
  KEY `created_by` (`created_by`),
  KEY `approved_by` (`approved_by`),
  KEY `idx_st_status` (`status`),
  CONSTRAINT `stock_transfers_ibfk_1` FOREIGN KEY (`from_warehouse_id`) REFERENCES `warehouses` (`warehouse_id`),
  CONSTRAINT `stock_transfers_ibfk_2` FOREIGN KEY (`to_warehouse_id`) REFERENCES `warehouses` (`warehouse_id`),
  CONSTRAINT `stock_transfers_ibfk_3` FOREIGN KEY (`created_by`) REFERENCES `users` (`user_id`),
  CONSTRAINT `stock_transfers_ibfk_4` FOREIGN KEY (`approved_by`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `stocktake_items`
--

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
  PRIMARY KEY (`item_id`),
  KEY `stocktake_id` (`stocktake_id`),
  KEY `product_id` (`product_id`),
  KEY `counted_by` (`counted_by`),
  CONSTRAINT `stocktake_items_ibfk_1` FOREIGN KEY (`stocktake_id`) REFERENCES `stocktakes` (`stocktake_id`) ON DELETE CASCADE,
  CONSTRAINT `stocktake_items_ibfk_2` FOREIGN KEY (`product_id`) REFERENCES `products` (`product_id`) ON DELETE CASCADE,
  CONSTRAINT `stocktake_items_ibfk_3` FOREIGN KEY (`counted_by`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `stocktakes`
--

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
  UNIQUE KEY `stocktake_code` (`stocktake_code`),
  KEY `warehouse_id` (`warehouse_id`),
  KEY `counted_by` (`counted_by`),
  KEY `approved_by` (`approved_by`),
  KEY `idx_st_status` (`status`),
  CONSTRAINT `stocktakes_ibfk_1` FOREIGN KEY (`warehouse_id`) REFERENCES `warehouses` (`warehouse_id`),
  CONSTRAINT `stocktakes_ibfk_2` FOREIGN KEY (`counted_by`) REFERENCES `users` (`user_id`),
  CONSTRAINT `stocktakes_ibfk_3` FOREIGN KEY (`approved_by`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `suppliers`
--

DROP TABLE IF EXISTS `suppliers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `suppliers` (
  `supplier_id` int NOT NULL AUTO_INCREMENT,
  `supplier_code` varchar(20) NOT NULL COMMENT 'Ma NCC, unique',
  `name` varchar(255) NOT NULL COMMENT 'Ten cong ty',
  `contact_person` varchar(100) DEFAULT NULL COMMENT 'Nguoi lien he',
  `phone` varchar(20) DEFAULT NULL,
  `email` varchar(100) DEFAULT NULL,
  `address` varchar(500) DEFAULT NULL,
  `credit_limit` decimal(15,2) DEFAULT '0.00' COMMENT 'Han muc no',
  `payment_terms` varchar(50) DEFAULT NULL COMMENT 'Thoi han thanh toan',
  `status` enum('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`supplier_id`),
  UNIQUE KEY `supplier_code` (`supplier_code`),
  KEY `idx_supplier_code` (`supplier_code`),
  KEY `idx_supplier_status` (`status`),
  KEY `idx_supplier_name` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `system_config`
--

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
  UNIQUE KEY `config_key` (`config_key`),
  KEY `updated_by` (`updated_by`),
  CONSTRAINT `system_config_ibfk_1` FOREIGN KEY (`updated_by`) REFERENCES `users` (`user_id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=176 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `system_settings`
--

DROP TABLE IF EXISTS `system_settings`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_settings` (
  `setting_key` varchar(64) NOT NULL,
  `setting_value` varchar(255) NOT NULL,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`setting_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `transfer_details`
--

DROP TABLE IF EXISTS `transfer_details`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `transfer_details` (
  `transfer_detail_id` int NOT NULL AUTO_INCREMENT,
  `transfer_id` int NOT NULL,
  `product_id` int NOT NULL,
  `qty` int NOT NULL,
  PRIMARY KEY (`transfer_detail_id`),
  KEY `product_id` (`product_id`),
  KEY `idx_td_transfer` (`transfer_id`),
  CONSTRAINT `transfer_details_ibfk_1` FOREIGN KEY (`transfer_id`) REFERENCES `stock_transfers` (`transfer_id`) ON DELETE CASCADE,
  CONSTRAINT `transfer_details_ibfk_2` FOREIGN KEY (`product_id`) REFERENCES `products` (`product_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user_warehouse_assignments`
--

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
  UNIQUE KEY `uq_user_warehouse` (`user_id`,`warehouse_id`),
  KEY `warehouse_id` (`warehouse_id`),
  CONSTRAINT `user_warehouse_assignments_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`) ON DELETE CASCADE,
  CONSTRAINT `user_warehouse_assignments_ibfk_2` FOREIGN KEY (`warehouse_id`) REFERENCES `warehouses` (`warehouse_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `users`
--

DROP TABLE IF EXISTS `users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `users` (
  `user_id` int NOT NULL AUTO_INCREMENT,
  `username` varchar(50) NOT NULL,
  `password_hash` varchar(255) NOT NULL,
  `full_name` varchar(100) NOT NULL,
  `email` varchar(100) NOT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `otp_preference` varchar(20) DEFAULT 'EMAIL',
  `role` enum('ADMIN','MANAGER','SALES_STAFF','WAREHOUSE_STAFF') NOT NULL DEFAULT 'WAREHOUSE_STAFF',
  `warehouse_id` int NOT NULL DEFAULT '1',
  `active` tinyint(1) NOT NULL DEFAULT '1',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `failed_login_count` int NOT NULL DEFAULT '0',
  `lockout_until` datetime DEFAULT NULL,
  `reset_token_hash` varchar(255) DEFAULT NULL,
  `reset_token_expires_at` datetime DEFAULT NULL,
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `username` (`username`),
  KEY `idx_users_role` (`role`),
  KEY `idx_users_active` (`active`),
  KEY `idx_users_email` (`email`),
  KEY `idx_users_warehouse` (`warehouse_id`)
) ENGINE=InnoDB AUTO_INCREMENT=145 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `warehouse_issues`
--

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
  UNIQUE KEY `issue_code` (`issue_code`),
  KEY `warehouse_id` (`warehouse_id`),
  KEY `created_by` (`created_by`),
  KEY `copied_from_id` (`copied_from_id`),
  KEY `idx_wi_status` (`status`),
  KEY `idx_wi_type` (`issue_type`),
  CONSTRAINT `warehouse_issues_ibfk_1` FOREIGN KEY (`warehouse_id`) REFERENCES `warehouses` (`warehouse_id`),
  CONSTRAINT `warehouse_issues_ibfk_2` FOREIGN KEY (`created_by`) REFERENCES `users` (`user_id`),
  CONSTRAINT `warehouse_issues_ibfk_3` FOREIGN KEY (`copied_from_id`) REFERENCES `warehouse_issues` (`issue_id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `warehouse_receipts`
--

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
  UNIQUE KEY `receipt_code` (`receipt_code`),
  KEY `created_by` (`created_by`),
  KEY `copied_from_id` (`copied_from_id`),
  KEY `idx_wr_status` (`status`),
  KEY `idx_wr_warehouse` (`warehouse_id`),
  KEY `idx_wr_status_supplier` (`status`,`supplier_name`),
  CONSTRAINT `warehouse_receipts_ibfk_1` FOREIGN KEY (`warehouse_id`) REFERENCES `warehouses` (`warehouse_id`),
  CONSTRAINT `warehouse_receipts_ibfk_2` FOREIGN KEY (`created_by`) REFERENCES `users` (`user_id`),
  CONSTRAINT `warehouse_receipts_ibfk_3` FOREIGN KEY (`copied_from_id`) REFERENCES `warehouse_receipts` (`receipt_id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `warehouses`
--

DROP TABLE IF EXISTS `warehouses`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `warehouses` (
  `warehouse_id` int NOT NULL AUTO_INCREMENT,
  `warehouse_code` varchar(20) NOT NULL,
  `warehouse_name` varchar(100) NOT NULL,
  `address` varchar(255) DEFAULT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `capacity` int DEFAULT '0',
  `active` tinyint(1) NOT NULL DEFAULT '1',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`warehouse_id`),
  UNIQUE KEY `warehouse_code` (`warehouse_code`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `webhook_logs`
--

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
  `message_id` varchar(100) DEFAULT NULL,
  `request_ip` varchar(50) DEFAULT NULL,
  `request_signature` varchar(255) DEFAULT NULL,
  `retry_count` int NOT NULL DEFAULT '0',
  `processed_at` datetime DEFAULT NULL,
  PRIMARY KEY (`log_id`),
  KEY `idx_wl_event` (`event_type`),
  KEY `idx_wl_status` (`status`),
  KEY `idx_wl_channel` (`channel_id`),
  KEY `idx_message_id` (`message_id`),
  CONSTRAINT `webhook_logs_ibfk_1` FOREIGN KEY (`channel_id`) REFERENCES `channels` (`channel_id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `zones`
--

DROP TABLE IF EXISTS `zones`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `zones` (
  `zone_id` int NOT NULL AUTO_INCREMENT,
  `warehouse_id` int NOT NULL,
  `zone_code` varchar(50) NOT NULL,
  `zone_name` varchar(100) NOT NULL,
  `zone_type` varchar(30) NOT NULL DEFAULT 'NORMAL',
  `description` text,
  `active` tinyint(1) NOT NULL DEFAULT '1',
  `is_default` tinyint(1) NOT NULL DEFAULT '0',
  `capacity` int DEFAULT '0',
  PRIMARY KEY (`zone_id`),
  UNIQUE KEY `uq_zone_code_wh` (`zone_code`,`warehouse_id`),
  KEY `idx_zones_wh` (`warehouse_id`),
  KEY `idx_zones_type` (`zone_type`),
  CONSTRAINT `zones_ibfk_1` FOREIGN KEY (`warehouse_id`) REFERENCES `warehouses` (`warehouse_id`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-07-16 13:51:48
