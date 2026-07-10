-- Mục đích: đăng ký omnicore-web như 1 channel trong wms_hub, dùng app_secret
-- làm khoá HMAC-SHA256 dùng chung giữa 2 service (com.wms.controller.api.BaseApiServlet
-- verify request từ OmnicoreApiService.java bên omnicore-web).
--
-- Giá trị app_secret bên dưới PHẢI khớp đúng `omnicore.hmac.secret` hiện có trong
-- D:\Omnicore\omnicore-web\src\main\resources\app.properties (không đổi giá trị đó,
-- chỉ copy sang đây để 2 bên tính ra cùng 1 signature).
--
-- Chạy 1 lần, thủ công, sau khi Tomcat đã tạo xong bảng `channels` (qua SchemaInitListener).

INSERT INTO channels (channel_name, platform, api_url, api_key, app_secret, webhook_secret, buffer_stock, is_active)
VALUES ('Own Website', 'OwnWebsite', NULL, NULL, 'OCW-W8SSS2TTNNE52NQESVOP594YZP9X8TCS', NULL, 0, 1);

-- Rollback:
-- DELETE FROM channels WHERE platform = 'OwnWebsite';
