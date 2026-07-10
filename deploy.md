# Hướng dẫn chi tiết Deployment (OmniCore WMS Hub)

Dưới đây là các bước chi tiết để tự build và deploy dự án lên máy chủ thử nghiệm.

## Quy trình triển khai (4 bước)

### Bước 1 — Build & Upload (trên máy cá nhân - Local)
Mở terminal trên máy local của bạn và chạy tuần tự các lệnh sau:
```bash
cd ~/Desktop/omnicore-main
mvn package -q
scp -i ~/.ssh/id_ed25519 target/ROOT.war opc@161.118.245.162:/tmp/ROOT.war
```

### Bước 2 — Restart Tomcat (trên Server qua SSH)
Kết nối SSH tới server:
```bash
ssh -i ~/.ssh/id_ed25519 opc@161.118.245.162
```
Sau đó, chạy lệnh sau trên server để tắt Tomcat, giải phóng thư mục cũ, copy file WAR mới vào và khởi động lại:
```bash
sudo bash -c '/opt/tomcat10/bin/shutdown.sh && sleep 3 && rm -rf /opt/tomcat10/webapps/ROOT && cp /tmp/ROOT.war /opt/tomcat10/webapps/ROOT.war && /opt/tomcat10/bin/startup.sh'
```

### Bước 3 — Đợi ~15-20 giây
Chờ khoảng 15-20 giây để máy chủ Tomcat 10 hoàn thành việc giải nén file `ROOT.war` và khởi tạo ứng dụng servlet container.

### Bước 4 — Hard Refresh trình duyệt
Truy cập trang web và thực hiện làm mới hoàn toàn bộ nhớ cache của trình duyệt:
- **Windows (Chrome/Edge/Firefox)**: `Ctrl` + `Shift` + `R` hoặc `Ctrl` + `F5`
- **Mac (Chrome/Safari)**: `Cmd` + `Shift` + `R`

---

## Các lưu ý quan trọng ⚠️

* **Thư mục Webapps của Tomcat 10**:
  Server đang chạy Tomcat 10 tại `/opt/tomcat10/webapps/`. **Tuyệt đối không** deploy vào thư mục `/var/lib/tomcat/` vì đó là Tomcat 9 (không tương thích với gói `jakarta.servlet` của dự án).

* **Tên File WAR**:
  Tên file WAR tải lên bắt buộc phải là `ROOT.war` để ứng dụng được phân phối tại root URL (không có tiền tố path, truy cập qua `isp392.click/`).

* **Khắc phục lỗi treo Tomcat**:
  Nếu tiến trình tắt Tomcat bị treo hoặc không phản hồi, thực hiện cưỡng bức dừng tiến trình Java chạy Tomcat bằng lệnh:
  ```bash
  sudo pkill -9 -f tomcat
  ```

---

## Theo dõi OTP Real-time

Khi SMTP email gặp sự cố (không gửi được), hệ thống sẽ log mã OTP ra file để debug. Để theo dõi OTP real-time:

```bash
ssh -i ~/.ssh/id_ed25519 opc@161.118.245.162 'sudo tail -f /opt/tomcat10/logs/catalina.$(date +%Y-%m-%d).log' 2>&1 | grep --line-buffered "OTP="
```

Hoặc filter với nhiều keywords hơn:

```bash
ssh -i ~/.ssh/id_ed25519 opc@161.118.245.162 'sudo tail -f /opt/tomcat10/logs/catalina.$(date +%Y-%m-%d).log' 2>&1 | grep --line-buffered -E "OTP=|EmailService|ERROR"
```

**Tìm kiếm lịch sử OTP** (trong log của ngày hôm nay):

```bash
ssh -i ~/.ssh/id_ed25519 opc@161.118.245.162 'sudo grep -i "OTP=" /opt/tomcat10/logs/catalina.$(date +%Y-%m-%d).log'
```

**Log file path**: `/opt/tomcat10/logs/catalina.YYYY-MM-DD.log`

**Log format**:
```
02-Jul-2026 16:37:21.051 INFO [...] com.wms.service.auth.EmailService.sendOtpCode EmailService: OTP email sent to xxx@gmail.com | OTP=893595
```
