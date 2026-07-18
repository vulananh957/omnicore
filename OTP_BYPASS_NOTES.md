# Hướng dẫn Khôi phục Luồng OTP Xác thực Đăng nhập

Tài liệu này ghi lại thay đổi tạm thời để bỏ qua bước xác thực mã OTP 2-Factor Authentication (2FA) khi đăng nhập nhằm mục đích kiểm thử sản phẩm nhanh hơn.

---

## 🛠️ Thay đổi đã thực hiện

**File chỉnh sửa:** [LoginServlet.java](file:///Users/alvin/Desktop/isp392/omnicore-main/src/main/java/com/wms/controller/auth/LoginServlet.java)

Trong method `doPost` xử lý đăng nhập, đoạn logic khởi tạo session và redirect sang trang nhập mã OTP đã được thay thế bằng đoạn bypass đăng nhập trực tiếp:

```java
// TEMPORARY BYPASS: Log in directly without OTP for development/testing
// Set skipOtp to false or uncomment the block below to restore OTP flow
boolean skipOtp = true;
if (skipOtp) {
    session.setAttribute(AppConstants.SESSION_USER, user);
    session.setAttribute(AppConstants.SESSION_ROLE, user.getRole());
    session.setAttribute(AppConstants.SESSION_WAREHOUSE, user.getWarehouseId());
    session.setMaxInactiveInterval(30 * 60);
    redirect(res, req.getContextPath() + getDashboardTarget(user.getRole()));
    return;
}
```

---

## 🔄 Cách khôi phục lại luồng OTP ban đầu

Để khôi phục lại luồng đăng nhập bảo mật 2 lớp OTP, bạn thực hiện một trong hai cách sau:

### Cách 1: Chuyển biến `skipOtp` thành `false`
Mở file [LoginServlet.java](file:///Users/alvin/Desktop/isp392/omnicore-main/src/main/java/com/wms/controller/auth/LoginServlet.java) và đổi giá trị biến ở dòng 76 thành `false`:

```diff
- boolean skipOtp = true;
+ boolean skipOtp = false;
```

### Cách 2: Phục hồi code nguyên bản (Revert)
Khôi phục khối code nguyên bản bằng cách xóa block bypass và bỏ comment block nguyên bản:

```java
// Initialize session and set intermediate 2-Factor Authentication state
HttpSession session = req.getSession(true);
clearPendingOtp(session);

session.setAttribute(AppConstants.SESSION_PENDING_USER, user);
session.setAttribute(AppConstants.SESSION_PENDING_OTP_TARGET, getDashboardTarget(user.getRole()));
session.setMaxInactiveInterval(10 * 60); // 10 min limit to complete 2FA

// Redirect to OTP verification page
redirect(res, req.getContextPath() + "/otp");
```

Hoặc đơn giản bằng git:
```bash
git checkout src/main/java/com/wms/controller/auth/LoginServlet.java
```
