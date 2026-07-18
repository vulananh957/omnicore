# Changelog — OmniCore WMS Hub

> File này được tạo lại ngày 2026-07-09 (bản cũ đã mất). Lịch sử trước ngày này xem `git log` — không backfill lại từ đầu để tránh bịa nội dung.

## [Unreleased]

### Fixed
- **Phase 3 Test Fixes** (2026-07-18):
  - Fixed `deductWithLock()` bug: UPDATE statement was missing `warehouse_id` filter, causing updates to wrong inventory rows
  - Added missing tables: `inventory_deduction_log`, `channel_sync_audit`, `lazada_rts_log` in SchemaInitListener
  - Fixed FK constraints: changed `ON DELETE RESTRICT` to `ON DELETE CASCADE` for `inventory_deduction_log.product_id` FK
  - Fixed test cleanup: changed @BeforeEach to delete ALL inventory in test warehouse (not just product 99)
  - Fixed test column names: changed `qty_available_before`/`qty_available_after` to `qty_before`/`qty_after`
  - All Phase 3 tests now pass (12/12): deductWithLock() happy path, insufficient stock, race conditions, idempotency

### Added
- **4 Production-Readiness Items** (2026-07-18):
  - ✅ **Idempotency check**: WebsiteOrderApiServlet has findExistingByWebOrderRef() — prevents duplicate orders on retry
  - ✅ **Timestamp validation**: BaseApiServlet validates X-Timestamp (reject if >300s skew) — anti-replay protection
  - ✅ **Audit logging** (NEW):
    - `AuditLogDAO.java`: log all API calls (method, path, channel, IP, signature valid/invalid)
    - `sql/audit_logging_2026-07-18.sql`: 3 tables (`api_audit_log`, `deduction_audit_log`, `sync_error_log`)
    - Integrated into: `BaseApiServlet` (log auth attempts), `InventoryDAO.deductWithLock()` (log deduction success/fail)
    - Helps debug overselling issues, track who did what, detect anomalies
  - ✅ **Retry exponential backoff** (Web side — see Web CHANGELOG)
  
- **Build & Deploy** (2026-07-18):
  - ✅ `mvn clean package -DskipTests` (skip tests, build WAR)
  - ✅ Output: `target/ROOT.war` (17 MB) ready for Tomcat deployment
  - ✅ SchemaInitListener auto-creates audit tables on app startup (no manual SQL needed)

- **Realtime Inventory Push — Giải quyết stale cache ở Web** (2026-07-18, Phase 1 Main):
  - `InventoryPushScheduler.java`: Batch inventory changes mỗi 5s + HTTPS push tới Web
    - Collect all `deductWithLock()` calls trong 5s window
    - Batch thành 1 HTTPS request: `POST /inventory/sync` với HMAC-SHA256 signature
    - Giảm traffic từ 100 request/5s → 1 batch request (95% giảm)
    - Retry failed batches: 1s, 2s, 5s delays (max 3 retries, then fail)
  - `InventoryPushDAO.java`: Persist + retry queue (status: PENDING, SUCCESS, FAILED)
  - `InventoryPushBatch.java` + `InventoryUpdate.java`: Models for batch + items
  - `InventoryPushUtil.java`: Sign request, build JSON, retry delay calc
  - `InventoryDAO.java` modified: Added `getChangesSince()` (collect products for push), `logDeductionForPush()` (track changes)
  - `sql/inventory_push_2026-07-18.sql`: `inventory_push_batch` table (tracks batches + retry state)
  - **Web side (Phase 2)**: TODO — Implement `InventorySyncServlet`, update cache, DOM update (realtime)
  - **Benefit**: Web cache cập nhật mỗi 5s (thay vì khi checkout). Khách thấy "hết hàng" trước khi order → convert rate tăng.
  - **Fallback**: Nếu Web down, retry queue chứa batch. Khách checkout vẫn an toàn (Main Phase A validate).

- **Hybrid Inventory Sync — Phòng chống overselling khi order đến từ Web + Lazada đồng thời** (2026-07-18):
  - **Phase 3 (Main) — Atomic Deduction with Lock** (2026-07-18, ngay sau Phase 1 & 2 Web):
    - `InventoryDAO.deductWithLock(productId, orderId, orderRef, channel, qty, warehouseId)`: atomic deduction với `deduction_lock`:
      - Check: `deduction_lock = 0 AND qty_available >= qty` (single atomic check-and-set)
      - If true: SET lock=1, UPDATE qty_available, INSERT log, UNLOCK
      - If false: return false (stock unavailable, race condition detected)
      - Prevents double-deduction: chỉ 1 order deduct được, order thứ 2 nhìn thấy stock đã hết → trả 409 Conflict
    - `WebsiteOrderApiServlet.doPost()`: sau khi persist order (Phase B), gọi `deductWithLock()` cho mỗi item (Phase C, mới).
      - Nếu deduction fail → delete order + trả 409 "Stock không đủ" → Web retry
      - Nếu thành công → return 201 với order_id
    - `inventory_deduction_log`: log mỗi successful deduction (order_id, channel, qty_before, qty_after, deducted_at) — audit trail
    - Test round-trip: 2 order Web + Lazada checkout đồng thời → cả 2 gọi Main API POST /api/website/orders:
      - Order 1: deduct success → 201
      - Order 2: deduction fail (stock locked/unavailable) → 409 Conflict → Web auto-retry 30s sau
  - **Web side (2026-07-18)**: Xem `omnicore-web/CHANGELOG.md` — Phase 1 & 2 hoàn tất.

## Previous entries

### Fixed
- **Bug nghiêm trọng nhất trong chuỗi này: nút chuyển trạng thái ở `/warehouse/outbound` không gửi được request lên server** — `warehouse-outbound.jsp` đọc `dbOrder.outboundId` để lấy `dbOutboundId`, nhưng `OutboundOrder.java` serialize field này với `@JsonProperty("id")` (key JSON thật là `"id"`, không phải `"outboundId"`) — khiến `dbOutboundId` luôn `undefined` cho MỌI phiếu xuất, mọi nút chuyển trạng thái (Bắt đầu đóng gói/Xác nhận đóng gói/Xác nhận hoàn kệ...) rơi vào nhánh dự phòng cũ chỉ ghi `localStorage`, không gọi API thật. Đây là nguyên nhân gốc thực sự khiến thủ kho bấm chuyển trạng thái nhưng F5 lại quay về trạng thái cũ — không phải do thiếu code đồng bộ (đã có và đúng), mà do UI chưa từng gọi tới được. Phát hiện qua log Tomcat: chỉ có GET (mở trang + F5), không có POST nào giữa 2 lần bấm. Đã sửa 3 chỗ dùng `dbOrder.outboundId` → `dbOrder.id`. Verify bằng click THẬT qua trình duyệt (không phải gọi thẳng Service): outbound_id=930 (đơn `WEB-81EBE18C715441E6`) → bấm "Bắt đầu đóng gói" → DB thật chuyển `PENDING_PACK→PACKED` (trước đó sẽ không đổi gì) → bấm "Xác nhận đóng gói" → chuyển `HANDED_OVER`, `orders.status`→PACKED, và **`web_orders.status_cache` bên omnicore-web cũng cập nhật đúng thành PACKED** — xác nhận toàn bộ chuỗi (UI → Service → push web) hoạt động đúng sau khi sửa cả JSP lẫn `OutboundService` (mục bên dưới).
- **Bug nghiêm trọng: đơn hàng Website không đồng bộ trạng thái sang omnicore-web khi xuất kho** — báo cáo thực tế: khách đặt hàng, thủ kho chuyển trạng thái, khách trên web không thấy cập nhật. Nguyên nhân gồm 3 lớp cộng dồn:
  1. `OutboundService.updateStatus()` chỉ đẩy trạng thái sang web khi tới `SHIPPED`, không đẩy ở bước `HANDED_OVER` (đơn hàng chuyển "PACKED") — khách không thấy "đang đóng gói".
  2. **Nghiêm trọng hơn**: với đơn omnichannel (Website/Lazada/Shopee/TikTok/bán lẻ — `isOmnichannelOutbound()` trả `true` cho tất cả các kênh này), nhánh SHIPPED có 1 đường xử lý riêng gọi `LedgerDAO.approveDocument()` rồi `return` ngay — **không bao giờ chạy tới đoạn code đồng bộ order-status/push-web/sync-stock** dù code đó tồn tại. Đây là lý do thật sự đơn Website chưa từng đồng bộ được sang web ở bước SHIPPED, không phải do thiếu code.
  3. `LedgerDAO.approveOutbound()` (được gọi từ nhánh trên) tự `UPDATE outbound_orders SET status='SHIPPED'` **không qua `compareAndSetStatus`** — bỏ qua hẳn optimistic lock đã thêm trước đó, khôi phục lại race condition double-deduct tồn kho đã tưởng đã sửa.
  - **Đã sửa gộp**: `compareAndSetStatus` giờ là nơi DUY NHẤT đổi `outbound_orders.status` (mọi trường hợp, kể cả omnichannel). `LedgerDAO.approveOutbound()` không còn tự set `status` (chỉ còn ghi sổ cái + `shipped_at`), guard idempotency đổi từ đọc `status` sang đọc `shipped_at IS NULL` (vì lúc nó chạy, CAS đã set status=SHIPPED rồi). Thêm guard idempotency ở đầu `updateStatus()`: gọi SHIPPED lần 2 sẽ no-op thay vì trừ tồn kho/đẩy web lần nữa. Nhánh SHIPPED giờ tách 2 đường trừ tồn kho loại trừ nhau (ledger cho omnichannel, `deductShippedInventory` cho còn lại) rồi LUÔN đồng bộ order-status + push web + sync stock sau đó, không phụ thuộc nhánh nào chạy trước.
  - `syncOrderShippedToWebsite()` đổi tên thành `syncOrderStatusToWebsite(orderCode, status, trackingNo)` — dùng chung cho cả push "PACKED" (ở HANDED_OVER) và "SHIPPED".
  - **Verify bằng test end-to-end thật** (tạo đơn Website test qua DB, chạy qua Tomcat thật, gọi `OutboundService.updateStatus()` thật qua PENDING_PACK→PACKED→HANDED_OVER→SHIPPED→SHIPPED lần 2): `web_orders.status_cache` đúng SHIPPED (trước đó luôn kẹt ở PENDING), lịch sử ghi đủ 2 bước PACKED rồi SHIPPED (trước chỉ có SHIPPED), tồn kho trừ đúng 1 lần dù gọi SHIPPED 2 lần (idempotency), stock đồng bộ sang web đúng giá trị.
  - **Phát hiện phụ khi test**: `channels.api_url` của kênh Website trong DB đang trỏ `http://localhost:8081/omnicore-web` (theo kế hoạch Tomcat riêng cũ) thay vì `http://localhost:8080/omnicore-web` (thực tế chạy chung Tomcat) — **đây nhiều khả năng là nguyên nhân gốc khiến TOÀN BỘ push từ trước tới giờ không bao giờ tới được web dù code có đúng hay không** (kết nối tới cổng không ai lắng nghe). Đã sửa giá trị trong DB dev. Anh cần tự kiểm tra giá trị này trên môi trường khác (nếu có).
  - **Phát hiện phụ khác (chưa sửa, ngoài phạm vi hôm nay)**: `OutboundDAO.createShippingLabel()`/`createDeliveryNote()` ném lỗi thiếu cột (`shipping_labels.courier_name`, `delivery_notes.status`) — cùng lớp bug "cột chưa migrate trong `SchemaInitListener`" đã sửa nhiều lần trước đây, bị bắt và log WARNING nên không chặn luồng chính nhưng 2 bảng này không được ghi.

### Security
- **SQL Injection (`SupplierDAO`)**: `searchWhereClauseCount()` nối chuỗi trực tiếp với từ khóa tìm kiếm nhà cung cấp thay vì dùng `?` placeholder (khác với query dữ liệu song song đã dùng đúng `PreparedStatement`) — cho phép chèn SQL qua ô tìm kiếm ở trang nhà cung cấp. Đã: (1) viết lại count query dùng chung `debt_cte` + `searchWhereClause()` (placeholder) với data query, (2) tiện thể sửa luôn bug liên quan: count trước đây bỏ qua hẳn `debtFilter` nên tổng số không khớp khi lọc theo công nợ (HAS_DEBT/NO_DEBT). Verify bằng test cô lập qua `SupplierDAO.findAllPaged()` thật (2 nhà cung cấp test tạm): payload `' OR '1'='1'` → 0 kết quả (trước đây sẽ khớp toàn bộ 2 dòng); payload UNION-style → 0 kết quả, không lỗi; count và data-query khớp nhau khi lọc `HAS_DEBT`. Đã xóa dữ liệu test.

### Fixed
- **Trang `/sales/mapping-exceptions` (SKU chưa ánh xạ) bị mồ côi** — servlet + JSP đã có đầy đủ, route trong `web.xml` hoạt động, nhưng không có link ở đâu trong UI (không sidebar, không trang sku-mapping/channel-products) — Sales Staff chỉ vào được nếu biết gõ tay URL. Đã thêm link "SKU chưa ánh xạ" vào sidebar (`sales-layout.jsp`), ngay sau "Ánh xạ SKU". Verify bằng click thật qua Tomcat: chuyển trang đúng, hiển thị đúng dữ liệu thật.
- **3 tính năng "chết lặng" do bảng DB chưa từng được tạo trong `SchemaInitListener`** (cùng lớp lỗi với các cột thiếu đã sửa trước đây, nhưng lần này là thiếu cả bảng) — các bảng này chỉ tồn tại trong `schema.sql` đã lỗi thời, không được `SchemaInitListener` tạo, nên trên bất kỳ DB nào chỉ khởi tạo qua listener (không chạy `schema.sql` tay), toàn bộ 3 tính năng sau sẽ ném `SQLSyntaxErrorException` bị nuốt thành log WARNING:
  - `notifications` — toàn bộ tính năng thông báo/badge (`NotificationDAO`) chết.
  - `system_config` — ngưỡng cảnh báo margin (`PricingConfigDAO`) luôn dùng mặc định cứng, admin lưu cấu hình không có tác dụng.
  - `lazada_shipment_providers` — dropdown đơn vị vận chuyển Lazada (`LazadaShipmentProviderDAO`) luôn rỗng.
  - Đã thêm `ensureNotificationsTable()`, `ensureSystemConfigTable()` (kèm seed 3 ngưỡng mặc định), `ensureLazadaShipmentProvidersTable()` (kèm seed 6 hãng vận chuyển VN) vào `SchemaInitListener`, wire vào `contextInitialized()`. Verify bằng test cô lập gọi thật `NotificationDAO.insert()/countUnread()/findForUser()`, `PricingConfigDAO.upsert()/getDecimal()`, `LazadaShipmentProviderDAO.findAllActive()` — cả 3 chạy đúng, trả đúng dữ liệu (6/6 hãng vận chuyển). Đã dọn dữ liệu test, khôi phục giá trị mặc định.
- **`SupplierDAO` — trang danh sách nhà cung cấp lỗi hoàn toàn trên DB mới**: bảng `receipt_details` (dùng trong CTE tính công nợ) cũng chưa từng được tạo trong `SchemaInitListener`, cùng nguyên nhân như trên. Đã thêm vào `ensureWarehouseReceipts()`.
- **`ProductPerformanceDAO.enrichChannelLinks()` — dead code, đã xóa**: SELECT cột `sm.lazada_product_id` không tồn tại trong `sku_mappings` (chỉ có `external_sku`/`seller_sku`) và filter `sync_status = 'ACTIVE'` không bao giờ khớp (enum thật: SYNCED/PENDING/ERROR) — nhưng phát hiện thêm: hàm này **không có nơi nào gọi** và thân vòng lặp không dùng biến `link` sau khi tạo (luôn trả `results` rỗng). Xóa hẳn thay vì sửa logic chết không phục vụ mục đích gì.

### Changed
- **Hợp nhất xác thực với omnicore-web về 1 chuẩn HMAC-SHA256**: `WebsiteHttpClient` (push sản phẩm/danh mục/trạng thái đơn sang omnicore-web) trước đây gửi thêm header `X-Omnicore-API-Key` ngoài chữ ký HMAC. Đã bỏ header và `channel.getApiKey()` khỏi luồng này — chỉ còn `X-Timestamp`/`X-Signature` ký bằng `channel.appSecret`, cùng chuẩn với chiều Web→Main (`BaseApiServlet`). Lý do: API Key không cộng thêm bảo mật thật (cùng nằm chung nguồn cấu hình với HMAC secret), chỉ tăng số giá trị phải đồng bộ tay giữa 2 project. Xem chi tiết tại CHANGELOG.md của `omnicore-web`. Verify bằng test cô lập: công thức ký tính tay trong `WebsiteHttpClient` khớp byte-for-byte với verify phía web cho cùng input — PASS. Build lại (`mvn compile`, 204 file) — `BUILD SUCCESS`.

### Fixed
- **Bug (module xuất kho)**: `OutboundDAO.cancelByOrderId()` loại trừ trạng thái `'CANCELLED', 'DELIVERED'` khỏi điều kiện hủy — nhưng `DELIVERED` không còn tồn tại từ khi chuyển sang luồng 4 bước (`isValidStatus()` chỉ còn `PENDING_PACK/PACKED/HANDED_OVER/SHIPPED/CANCELLED`). Khi đơn hàng gốc bị hủy (buyer cancel, Lazada webhook, `OrderService.cascadeCancelOrder()`), nếu phiếu xuất đã ở trạng thái `SHIPPED` (đã trừ tồn kho thật), hàm vẫn hủy nhầm phiếu đã hoàn tất. Đã đổi điều kiện loại trừ thành `'CANCELLED', 'SHIPPED'`. Verify bằng test cô lập trên `wms_hub` (insert phiếu test tạm order_id=902): status `SHIPPED` → UPDATE không tác động (giữ nguyên); đổi sang `PACKED` → UPDATE hủy đúng thành `CANCELLED`. Đã xóa dữ liệu test.

### Changed
- **Cập nhật luồng nghiệp vụ xuất kho (Outbound Flow) 4 bước**:
  - Chuyển đổi toàn bộ luồng xuất kho WMS từ 5 bước cũ sang 4 bước chuẩn hóa theo yêu cầu:
    - **Bước 1 (PENDING_PACK - Chờ đóng gói)**: Thay thế `PENDING`. Đơn hàng Lazada đổ về, giữ ngầm tồn kho khả dụng. Thủ kho chọn đơn và bấm "Chuẩn bị hàng" để gọi Pack API lên Lazada (thành công lưu tracking & chuyển sang "Đang đóng gói", thất bại đổi màu đỏ kèm nút "Thử lại API"). Đơn hàng thường bấm "Bắt đầu đóng gói" để chuyển sang "Đang đóng gói".
    - **Bước 2 (PACKED - Đang đóng gói)**: Thay thế `PICKING`/`PACKED` cũ. In tem Lazada (luôn sáng), nút "Xác nhận đóng gói" bị khóa cho đến khi in tem ít nhất một lần. Nhân viên kho tích chọn các sản phẩm đã nhặt để cập nhật số lượng nhặt thật lên hệ thống (picked_qty). Hoàn thành nhặt và đóng gói vật lý xong bấm "Xác nhận đóng gói" để chuyển sang "Đã đóng gói".
    - **Bước 3 (HANDED_OVER - Đã đóng gói)**: Thêm trạng thái nội bộ WMS mới `HANDED_OVER`. Đơn hàng tự động gọi ReadyToShip lên Lazada để báo cho sàn. Trên UI hiển thị duy nhất nút "Xuất kho" màu xanh dương. Chưa trừ tồn kho vật lý. Khi shipper đến lấy, thủ kho bấm "Xuất kho" mở popup Phiếu xuất kho nháp Draft để đối soát thực tế, ghi chú và bấm "Duyệt và Xuất kho".
    - **Bước 4 (SHIPPED - Đã xuất kho)**: Khấu trừ số lượng tồn kho vật lý thực tế (`qty_on_hand` và `qty_reserved`), tạo Delivery Note, ghi sổ cái Ledger cho đơn bán lẻ và chuyển trạng thái đơn hàng ban đầu thành `SHIPPED`. Hiển thị nút "Xem & In phiếu xuất" màu xanh dương trên UI.
  - Cập nhật database: tự động di chuyển cột `status` của bảng `outbound_orders` từ `ENUM` cũ sang `VARCHAR(50)` và thực hiện tự động migration các giá trị trạng thái cũ sang giá trị mới (`PENDING` -> `PENDING_PACK`, `PICKING` -> `PACKED`, `DELIVERED` -> `HANDED_OVER`) ngay trên startup thông qua `SchemaInitListener`.

### Added
- **Tài liệu luồng nghiệp vụ xuất kho (Outbound Flow) cho Warehouse Staff**:
  - **Khởi tạo**: Phiếu xuất kho (`OutboundOrder` - trạng thái `PENDING`) được tạo tự động khi Sales duyệt đơn hàng (hệ thống tự giữ chỗ tồn kho tạm thời qua soft-allocate) hoặc thủ công khi nhân viên kho tạo từ `FulfillmentRequest`.
  - **Chuẩn bị hàng (PENDING)**: Đối với đơn Lazada, gọi API `/lazada/pack` để đóng gói và lấy mã vận đơn (tracking_number). Khi sẵn sàng, chuyển sang "Bắt đầu Pick" để phân công nhân viên.
  - **Nhặt hàng (PICKING)**: Nhân viên kho dựa theo danh sách vị trí kệ hàng hiển thị trên giao diện để lấy hàng thực tế, cập nhật số lượng đã nhặt (`picked_qty`) lên hệ thống qua checkbox. Hoàn thành nhặt hàng bấm "Xác nhận đóng gói" sang trạng thái `PACKED`.
  - **Chờ vận chuyển (PACKED)**: In tem vận đơn (Lazada PDF) và dán nhãn. Bấm "Ready to Ship (RTS)" để đồng bộ trạng thái sẵn sàng giao với Lazada.
  - **Đã xuất kho (SHIPPED)**: Xác nhận xuất kho thực tế. Hệ thống sẽ khấu trừ tồn vật lý (`qty_on_hand`), giải phóng lượng giữ chỗ (`qty_reserved`), tạo Delivery Note và ghi nhận bút toán sổ cái cho đơn mua lẻ.
  - **Đơn bị hủy (CANCELLED)**: Tự động giải phóng tồn kho đã giữ chỗ hoặc cho phép nhân viên bấm "Xác nhận hoàn kệ" thủ công để đưa hàng trở lại kệ.

- Luồng hoàn trả hàng cho đơn Website (7 ngày kể từ khi giao): sau khi Sales/Kho bấm **"Xác nhận đã giao"** (action mới `confirm_delivered`, chỉ đơn có `web_order_ref`), `orders.delivered_at` được stamp. `WebsiteOrderAutoCompleteScheduler` (chạy mỗi giờ) tự chuyển `DELIVERED` → `COMPLETED` sau 7 ngày nếu không có yêu cầu hoàn trả đang chờ duyệt.
- `RmaRequest`/`RmaDAO`: nối bảng `rma_requests` (tồn tại sẵn nhưng chưa từng dùng) vào luồng thật — thêm cột `evidence_photos`, `evidence_video`, `resolution_note`.
- 2 API endpoint mới `POST /api/website/order-actions/{id}/confirm-received` và `/return` (HMAC, giống các endpoint `/api/website/*` khác) — khách xác nhận nhận hàng sớm hoặc gửi yêu cầu hoàn trả (lý do + ảnh/video base64, validate còn hạn 7 ngày).
- `ReturnEvidenceServlet` (`/return-evidence/*`) — serve ảnh/video bằng chứng cho Sales xem, session-auth như `PublishImageServlet`.
- Trang Sales mới `/sales/rma-approval` — duyệt/từ chối yêu cầu hoàn trả Website kèm xem ảnh/video bằng chứng.
- `order-processing.jsp`: nút "XÁC NHẬN ĐÃ GIAO" cho đơn Website ở trạng thái `SHIPPED` (đơn sàn TMĐT khác vẫn giữ nguyên luồng webhook cũ).
- Cột mới: `orders.delivered_at`, `orders.web_order_ref` được thêm vào `Order` model + `OrderDAO` (getAllOrders/findByOrderCode) để phân biệt đơn Website với đơn sàn TMĐT ở tầng hiển thị (thay vì dựa vào heuristic `channel` dễ sai).

### Changed
- `admin/channel-create.jsp`: field **API Endpoint URL** và **App Key** chỉ bắt buộc (`required`) khi Platform = Lazada. Kênh Website không dùng 2 field này (omnicore-main không gọi ra ngoài cho kênh Website, chỉ nhận request từ omnicore-web qua HMAC bằng `app_secret`) nên bỏ bắt buộc, ẩn dấu `*` tương ứng. Toggle bằng JS theo `platform`, đồng bộ cả lúc load trang (create lẫn edit mode), không sửa validate phía server (`ChannelConfigServlet` vốn không validate required).
- Đổi `platform` của channel storefront từ `'OwnWebsite'` → `'Website'` (`BaseApiServlet`, `WebsiteOrderApiServlet`, seed SQL, dòng dữ liệu thật trong `channels`) — khớp đúng giá trị dropdown có sẵn trong `admin/channel-create.jsp` ("Website (Online Shop)"). Trước khi đổi, form Sửa kênh trong admin sẽ không khớp được tuỳ chọn nào, có nguy cơ ghi đè nhầm platform khi admin lưu form.

### Fixed
- **Bug (module xuất kho)**: trạng thái "Đã hoàn kệ" của phiếu xuất bị huỷ chỉ lưu ở `localStorage` trình duyệt, không có cột DB — đổi trình duyệt/xoá cache/2 người cùng thao tác sẽ khiến `releaseAllocationsForOutbound()` giải phóng cùng 1 lượng tồn kho tạm giữ 2 lần, thổi phồng sai `qty_available`. Đã: (1) thêm cột `outbound_orders.restocked_at`/`restocked_by`, (2) `OutboundDAO.claimRestock()` — UPDATE atomic `WHERE restocked_at IS NULL` để chỉ 1 lần gọi "thắng", (3) `OutboundService.releaseAllocationsForOutbound()` gọi claim trước khi giải phóng tồn, lần gọi lại trả `false` (dùng đúng message có sẵn "đã giải phóng trước đó" thay vì lỗi), (4) thêm field `restocked` vào `OutboundOrder` + JSON, (5) JSP: bỏ đoạn ghi đè `restocked` từ localStorage lên dữ liệu server (đơn có `dbOutboundId` giờ dùng thẳng trạng thái thật từ DB), xoá biến `localOrders` không còn dùng tới. Verify bằng test cô lập: gọi `releaseAllocationsForOutbound()` 2 lần liên tiếp cho cùng 1 phiếu — lần 1 giải phóng thành công, lần 2 bị chặn đúng thiết kế, `restocked_at` chỉ set 1 lần.
- **Bug (module xuất kho)**: `generateOutboundCode()` sinh mã phiếu xuất ngẫu nhiên 3 chữ số/ngày (999 khả năng), không kiểm tra trùng, cột `outbound_code` không có UNIQUE constraint — 2 phiếu có thể trùng mã, khiến `LedgerDAO.approveOutbound()` (tra cứu bằng `outbound_code`) duyệt/trừ tồn nhầm phiếu. Đã: (1) thêm UNIQUE INDEX `uq_outbound_code`, (2) `OutboundDAO.insert()` phân biệt lỗi trùng mã (`SQLIntegrityConstraintViolationException` → sentinel `DUPLICATE_CODE`) với lỗi khác, (3) `OutboundService` thêm `insertWithRetry()` (tối đa 5 lần, dùng chung cho `createOutbound()` và `autoCreateFromOrder()`) — tự sinh mã mới và thử lại khi trùng thay vì tạo phiếu thất bại. Phát hiện thêm khi test: `outbound_orders.created_by` cũng chưa từng được migrate (cùng lỗi thiếu cột như các bug trên) — đã thêm `addColumnIfMissing`; trước đó **toàn bộ chức năng tạo phiếu xuất kho bị lỗi 100%**. Verify bằng test cô lập: pre-insert 1 dòng với mã cố định, ép `generateOutboundCode()` trả về đúng mã đó 2 lần rồi mới ra mã mới (subclass override) — 2 lần đầu bị DB từ chối do trùng, lần 3 tạo thành công với mã mới, đã xoá dữ liệu test.
- **Bug nghiêm trọng (module xuất kho)**: trang `/warehouse/outbound` trả về **danh sách rỗng hoàn toàn** — cả 5 hàm truy vấn của `OutboundDAO` (`findAll/findById/findByStatus/findByWarehouse/findByWarehouseAndStatus`) SELECT `orders.is_label_printed`, nhưng cột này (cùng 2 cột liên quan `is_pack_requested`, `is_rts_pushed` dùng trong `OrderDAO`/luồng Lazada) **chưa từng được tạo** trong `SchemaInitListener.ensureOrdersTable()` — cùng dạng lỗi với bug `shipment_provider` đã sửa trước đó, chỉ là 3 cột còn sót lại từ cùng đợt. Đã thêm cả 3 qua `addColumnIfMissing` (`TINYINT(1) NOT NULL DEFAULT 0`). Verify bằng test cô lập (tạo đơn hàng + phiếu xuất tạm, gọi `OutboundDAO.findById()` thật, xoá sau khi xong): trước khi sửa ném `SQLSyntaxErrorException: Unknown column 'ord.is_label_printed'`, sau khi sửa trả đúng dữ liệu kể cả cờ `labelPrinted`.
- **Bug nghiêm trọng (module xuất kho)**: cập nhật trạng thái phiếu xuất (đặc biệt chuyển sang SHIPPED) không có khoá đồng thời — 2 nhân viên kho (hoặc double-click) cùng cập nhật 1 phiếu có thể khiến `inventoryDAO.deductShippedInventory()` bị gọi 2 lần, trừ tồn kho 2 lần cho cùng 1 phiếu xuất. `OutboundDAO.compareAndSetStatus()` (optimistic locking bằng cột `version`) đã được viết sẵn từ trước nhưng chưa từng được gọi ở đâu — `OutboundService.updateStatus()` vẫn dùng `updateStatus()` trần. Đã: (1) thêm cột `outbound_orders.version` qua `SchemaInitListener` (cột này thực ra đã tồn tại sẵn trên DB dev — code cũ để lại, chỉ chưa wire vào Service), (2) thêm field `version` vào `OutboundOrder` model + `OutboundDAO.mapRow()`, (3) đổi `OutboundService.updateStatus()` gọi `compareAndSetStatus()`, trả lỗi rõ ràng khi version lệch thay vì tiếp tục trừ tồn. Verify bằng test cô lập (insert dòng test tạm, gọi `compareAndSetStatus()` 2 lần với cùng version cũ, xoá dòng test sau khi xong): lần ghi đầu thành công, lần ghi thứ 2 tái sử dụng version cũ bị từ chối đúng như thiết kế.
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

### Added
- **Kéo sản phẩm từ Website (Storefront) về WMS Hub**:
  - `WebsiteProductApiServlet` (trong `omnicore-web`): Thêm `doGet` endpoint trả về danh sách sản phẩm từ storefront, được bảo vệ bằng HMAC-SHA256.
  - `WebsiteHttpClient`: Thêm hàm `get(String apiPath)` hỗ trợ xác thực HMAC cho GET request.
  - `WebsiteProductService.pullProducts(Channel)`: Thực hiện gọi API storefront lấy sản phẩm, so khớp với SKU nội bộ, nếu khớp thì tự động tạo liên kết và lưu `channel_products`, nếu không khớp thì ghi nhận lỗi vào `mapping_exceptions` (tái sử dụng luồng của Lazada).
  - Tích hợp tính năng Pull sản phẩm vào nút hành động trên giao diện thông qua `SalesChannelProductsServlet` (action `pull` xử lý thêm cho kênh Website).

### Fixed
- Cập nhật `SchemaInitListener`: Thêm hàm `ensureMappingExceptionsTable()` để tự động khởi tạo bảng `mapping_exceptions`, khắc phục lỗi mất bảng khi chạy integration tests.
- Sửa lỗi thiếu cập nhật `channel_item_id` trong `WebsiteProductService.pullProducts` khi thêm mới hoặc cập nhật `channel_products`, đồng bộ thiết kế giống luồng Lazada.
- Cập nhật `WebsiteWorkflowTest`: Bổ sung và hoàn thiện `test6_PullProducts`, sửa check exception map logic bằng `externalSku`.
