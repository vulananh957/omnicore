# Changelog — OmniCore WMS Hub

> File này được tạo lại ngày 2026-07-09 (bản cũ đã mất). Lịch sử trước ngày này xem `git log` — không backfill lại từ đầu để tránh bịa nội dung.

## [Unreleased]

### Changed
- `admin/channel-create.jsp`: field **API Endpoint URL** và **App Key** chỉ bắt buộc (`required`) khi Platform = Lazada. Kênh Website không dùng 2 field này (omnicore-main không gọi ra ngoài cho kênh Website, chỉ nhận request từ omnicore-web qua HMAC bằng `app_secret`) nên bỏ bắt buộc, ẩn dấu `*` tương ứng. Toggle bằng JS theo `platform`, đồng bộ cả lúc load trang (create lẫn edit mode), không sửa validate phía server (`ChannelConfigServlet` vốn không validate required).
- Đổi `platform` của channel storefront từ `'OwnWebsite'` → `'Website'` (`BaseApiServlet`, `WebsiteOrderApiServlet`, seed SQL, dòng dữ liệu thật trong `channels`) — khớp đúng giá trị dropdown có sẵn trong `admin/channel-create.jsp` ("Website (Online Shop)"). Trước khi đổi, form Sửa kênh trong admin sẽ không khớp được tuỳ chọn nào, có nguy cơ ghi đè nhầm platform khi admin lưu form.

### Fixed
- **Bug nghiêm trọng**: trang "Đơn hàng" (Sales Staff, `/sales/orders`) hiện **danh sách rỗng hoàn toàn** — `OrderDAO.getAllOrders()` ném `SQLSyntaxErrorException: Unknown column 'o.shipment_provider'` (servlet nuốt lỗi im lặng, không log ra JSP). Cột `shipment_provider` được code dùng để lưu đơn vị vận chuyển cho **mọi kênh** (không riêng Lazada) nhưng chưa từng được tạo trong `SchemaInitListener.ensureOrdersTable()`. Đã thêm `addColumnIfMissing`. Verify lại: query gốc chạy đúng, trả về đủ 8 đơn hàng.

### Added
- `com.wms.controller.api.BaseApiServlet`: xác thực HMAC-SHA256 (`X-Timestamp`/`X-Signature`, hash cả body, chống replay 300s) cho API phục vụ `omnicore-web`.
- `CategoryApiServlet`, `ProductApiServlet`, `InventoryApiServlet`, `WebsiteOrderApiServlet` — 6 endpoint REST cho storefront (`GET /api/categories`, `GET /api/products`, `GET /api/products/{id}`, `GET /api/inventory/{productId}`, `POST /api/website/orders`, `GET /api/website/orders/{id}`).
- `ProductDAO.search()`: phân trang + filter theo category/keyword/new_arrival/best_seller.
- `ChannelDAO.findByPlatform()`.
- Cột mới: `orders.web_order_ref`, `orders.web_customer_ref`, `order_shipping_details.recipient_phone`, `products.is_best_seller` (qua `SchemaInitListener`).

### Changed
- `AuthFilter.isPublicPath()`: thêm carve-out cho `/api/categories`, `/api/products`, `/api/inventory`, `/api/website/` (tự xác thực bằng HMAC, không dùng session).
- `OrderDAO`: thêm `sd.recipient_phone` vào chuỗi ưu tiên hiển thị số điện thoại khách (trước đây chỉ có `users`/`lazada_orders`).
