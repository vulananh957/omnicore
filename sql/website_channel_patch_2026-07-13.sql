-- =============================================================================
-- Patch: Cập nhật api_url và api_key cho channel 'Website' trong WMS Hub
-- Ngày: 2026-07-13
-- Mục đích:
--   - api_url : URL gốc của omnicore-web Tomcat (không có trailing slash, không có context path —
--               WebsiteHttpClient sẽ tự nối thêm "/api/v1/products" v.v.)
--   - api_key : Phải khớp CHÍNH XÁC với omnicore.api.key trong
--               D:\Omnicore\omnicore-web\src\main\resources\app.properties
--               (WebsiteProductApiServlet đọc key đó để validate header X-Omnicore-API-Key)
--   - app_secret đã có từ seed 2026-07-09, KHÔNG thay đổi ở đây.
-- =============================================================================

UPDATE channels
SET
    api_url = 'http://localhost:8081/omnicore-web',
    api_key  = 'OCW-APIKEY-7F3K9MXPQZ2RVNTH'
WHERE platform = 'Website';

-- Kiểm tra kết quả:
SELECT channel_id, channel_name, platform, api_url, api_key,
       LEFT(app_secret, 10) AS app_secret_prefix, is_active
FROM channels
WHERE platform = 'Website';

-- Rollback:
-- UPDATE channels SET api_url = NULL, api_key = NULL WHERE platform = 'Website';
