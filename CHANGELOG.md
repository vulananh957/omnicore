# Changelog — OmniCore WMS Hub

> File này được tạo lại ngày 2026-07-09 (bản cũ đã mất). Lịch sử trước ngày này xem `git log` — không backfill lại từ đầu để tránh bịa nội dung.

## [Unreleased]

### Added
- `com.wms.controller.api.BaseApiServlet`: xác thực HMAC-SHA256 (`X-Timestamp`/`X-Signature`, hash cả body, chống replay 300s) cho API phục vụ `omnicore-web`.
- `CategoryApiServlet`, `ProductApiServlet`, `InventoryApiServlet`, `WebsiteOrderApiServlet` — 6 endpoint REST cho storefront (`GET /api/categories`, `GET /api/products`, `GET /api/products/{id}`, `GET /api/inventory/{productId}`, `POST /api/website/orders`, `GET /api/website/orders/{id}`).
- `ProductDAO.search()`: phân trang + filter theo category/keyword/new_arrival/best_seller.
- `ChannelDAO.findByPlatform()`.
- Cột mới: `orders.web_order_ref`, `orders.web_customer_ref`, `order_shipping_details.recipient_phone`, `products.is_best_seller` (qua `SchemaInitListener`).

### Changed
- `AuthFilter.isPublicPath()`: thêm carve-out cho `/api/categories`, `/api/products`, `/api/inventory`, `/api/website/` (tự xác thực bằng HMAC, không dùng session).
- `OrderDAO`: thêm `sd.recipient_phone` vào chuỗi ưu tiên hiển thị số điện thoại khách (trước đây chỉ có `users`/`lazada_orders`).
