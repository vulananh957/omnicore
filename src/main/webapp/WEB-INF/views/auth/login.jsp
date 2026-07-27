<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8"/>
    <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
    <title>Đăng nhập — OmniCore WMS Hub</title>
    <meta name="description" content="Đăng nhập vào hệ thống quản lý bán hàng đa kênh OmniCore."/>
    <link rel="preconnect" href="https://fonts.googleapis.com"/>
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin="anonymous"/>
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800;900&display=swap" rel="stylesheet"/>

<link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/auth-login.css"/>
</head>
<body>

<div class="login-root">

    <!-- ══ LEFT PANE ════════════════════════════════════════════════════ -->
    <div class="left-pane">
        <img
            class="left-pane__bg"
            src="https://images.unsplash.com/photo-1684695749267-233af13276d0?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&ixlib=rb-4.1.0&q=80&w=1080"
            alt="Hệ thống quản lý bán hàng đa kênh"
            loading="eager"
        />
        <div class="left-pane__overlay"></div>

        <div class="left-pane__content">
            <!-- Logo -->
            <div class="brand-logo">
                <div class="brand-logo__icon">
                    <!-- Boxes icon (lucide) inline SVG -->
                    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
                         stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"
                         style="width:20px;height:20px;color:#fff">
                        <path d="M2.97 12.92A2 2 0 0 0 2 14.63v3.24a2 2 0 0 0 .97 1.71l3 1.8a2 2 0 0 0 2.06 0L12 19v-5.5l-5-3-4.03 2.42Z"/>
                        <path d="m7 16.5-4.74-2.85"/>
                        <path d="m7 16.5 5-3"/>
                        <path d="M7 16.5v5.17"/>
                        <path d="M12 13.5V19l3.97 2.38a2 2 0 0 0 2.06 0l3-1.8a2 2 0 0 0 .97-1.71v-3.24a2 2 0 0 0-.97-1.71L17 10.5l-5 3Z"/>
                        <path d="m17 16.5-5-3"/>
                        <path d="m17 16.5 4.74-2.85"/>
                        <path d="M17 16.5v5.17"/>
                        <path d="M7.97 4.42A2 2 0 0 0 7 6.13v4.37l5 3 5-3V6.13a2 2 0 0 0-.97-1.71l-3-1.8a2 2 0 0 0-2.06 0l-3 1.8Z"/>
                        <path d="M12 8 7.26 5.15"/>
                        <path d="m12 8 4.74-2.85"/>
                        <path d="M12 13.5V8"/>
                    </svg>
                </div>
                <span class="brand-logo__name">OmniCore</span>
            </div>

            <!-- Bottom hero text -->
            <div class="left-pane__bottom">
                <div class="badge">
                    Hệ Thống Quản Lý Nội Bộ
                </div>
                <h2 class="left-pane__headline">
                    Quản lý bán hàng.<br/>
                    Đa kênh hiệu quả.
                </h2>
                <p class="left-pane__sub">
                    Hệ thống quản lý tồn kho, đơn hàng và bán hàng đa kênh — Lazada, Website Storefront — tập trung tại một nơi.
                </p>
            </div>
        </div>
    </div>

    <!-- ══ RIGHT PANE ═══════════════════════════════════════════════════ -->
    <div class="right-pane">

        <!-- Mobile logo (only visible on small screens) -->
        <div class="mobile-logo">
            <div class="mobile-logo__icon">
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
                     stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"
                     style="width:16px;height:16px;color:#fff">
                    <path d="M2.97 12.92A2 2 0 0 0 2 14.63v3.24a2 2 0 0 0 .97 1.71l3 1.8a2 2 0 0 0 2.06 0L12 19v-5.5l-5-3-4.03 2.42Z"/>
                    <path d="m7 16.5-4.74-2.85"/><path d="m7 16.5 5-3"/><path d="M7 16.5v5.17"/>
                    <path d="M12 13.5V19l3.97 2.38a2 2 0 0 0 2.06 0l3-1.8a2 2 0 0 0 .97-1.71v-3.24a2 2 0 0 0-.97-1.71L17 10.5l-5 3Z"/>
                    <path d="m17 16.5-5-3"/><path d="m17 16.5 4.74-2.85"/><path d="M17 16.5v5.17"/>
                    <path d="M7.97 4.42A2 2 0 0 0 7 6.13v4.37l5 3 5-3V6.13a2 2 0 0 0-.97-1.71l-3-1.8a2 2 0 0 0-2.06 0l-3 1.8Z"/>
                    <path d="M12 8 7.26 5.15"/><path d="m12 8 4.74-2.85"/><path d="M12 13.5V8"/>
                </svg>
            </div>
            <span class="mobile-logo__name">OmniCore</span>
        </div>

        <!-- Center card -->
        <div class="right-pane__center">
            <div class="right-pane__inner">
                <div class="login-card">

                    <!-- Header -->
                    <div class="login-card__hdr">
                        <h1 class="login-card__title">Chào mừng bạn đến với OmniCore!</h1>
                        <p class="login-card__subtitle">Truy cập vào hệ thống quản lý bán hàng đa kênh.</p>
                    </div>

                    <!-- Error message (shown when login fails) -->
                    <c:if test="${not empty errorMessage}">
                        <div class="error-banner">
                            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24"
                                 fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                                <circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/>
                                <line x1="12" y1="16" x2="12.01" y2="16"/>
                            </svg>
                            ${errorMessage}
                        </div>
                    </c:if>

                    <!-- Login Form -->
                    <form class="login-form" action="${pageContext.request.contextPath}/login" method="POST"
                          id="loginForm" novalidate>

                        <!-- Email / Phone -->
                        <div class="form-group">
                            <label class="form-label" for="identity">Email / Số điện thoại</label>
                            <input
                                class="form-input"
                                type="text"
                                id="identity"
                                name="identity"
                                placeholder="Nhập email hoặc số điện thoại"
                                value="${not empty identity ? identity : ''}"
                                autocomplete="username"
                                required
                            />
                        </div>

                        <!-- Password -->
                        <div class="form-group">
                            <label class="form-label" for="password">Mật khẩu</label>
                            <div class="password-wrap">
                                <input
                                    class="form-input"
                                    type="password"
                                    id="password"
                                    name="password"
                                    placeholder="••••••••"
                                    autocomplete="current-password"
                                    required
                                />
                                <button
                                    type="button"
                                    class="password-toggle"
                                    id="togglePwd"
                                    aria-label="Hiện/ẩn mật khẩu"
                                >
                                    <!-- Eye icon (default: show) -->
                                    <svg id="iconEye" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"
                                         fill="none" stroke="currentColor" stroke-width="2"
                                         stroke-linecap="round" stroke-linejoin="round">
                                        <path d="M2 12s3-7 10-7 10 7 10 7-3 7-10 7-10-7-10-7Z"/>
                                        <circle cx="12" cy="12" r="3"/>
                                    </svg>
                                    <!-- EyeOff icon (hidden by default) -->
                                    <svg id="iconEyeOff" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"
                                         fill="none" stroke="currentColor" stroke-width="2"
                                         stroke-linecap="round" stroke-linejoin="round" style="display:none">
                                        <path d="M9.88 9.88a3 3 0 1 0 4.24 4.24"/>
                                        <path d="M10.73 5.08A10.43 10.43 0 0 1 12 5c7 0 10 7 10 7a13.16 13.16 0 0 1-1.67 2.68"/>
                                        <path d="M6.61 6.61A13.526 13.526 0 0 0 2 12s3 7 10 7a9.74 9.74 0 0 0 5.39-1.61"/>
                                        <line x1="2" y1="2" x2="22" y2="22"/>
                                    </svg>
                                </button>
                            </div>
                        </div>

                        <!-- Remember + Forgot -->
                        <div class="meta-row">
                            <label class="remember-label" for="remember">
                                <div class="custom-checkbox" id="checkboxUI" aria-hidden="true"></div>
                                <input class="native-checkbox" type="checkbox" id="remember" name="remember"/>
                                <span class="remember-text">Ghi nhớ đăng nhập</span>
                            </label>
                            <a href="${pageContext.request.contextPath}/forgot-password" class="forgot-link">Quên mật khẩu?</a>
                        </div>

                        <!-- Submit -->
                        <button type="submit" class="btn-submit" id="btnSubmit">
                            Đăng nhập hệ thống
                        </button>

                    </form>

                    <!-- Footer note -->
                    <p class="card-footer-note">Hệ thống dành cho nhân viên nội bộ</p>

                </div><%-- /.login-card --%>
            </div>
        </div>
    </div>

</div><%-- /.login-root --%>

<script>
(function () {
    /* ── Password toggle ─────────────────────── */
    var pwdInput  = document.getElementById('password');
    var toggleBtn = document.getElementById('togglePwd');
    var iconEye   = document.getElementById('iconEye');
    var iconOff   = document.getElementById('iconEyeOff');
    var shown     = false;

    if (toggleBtn) {
        toggleBtn.addEventListener('click', function () {
            shown = !shown;
            pwdInput.type   = shown ? 'text' : 'password';
            iconEye.style.display = shown ? 'none'  : '';
            iconOff.style.display = shown ? ''      : 'none';
        });
    }

    /* ── Custom checkbox ─────────────────────── */
    var nativeChk   = document.getElementById('remember');
    var checkboxUI  = document.getElementById('checkboxUI');

    var checkSVG = '<svg viewBox="0 0 10 8" fill="none" xmlns="http://www.w3.org/2000/svg">' +
                   '<path d="M1 4L3.5 6.5L9 1" stroke="white" stroke-width="1.8" ' +
                   'stroke-linecap="round" stroke-linejoin="round"/></svg>';

    function syncCheckbox() {
        if (nativeChk.checked) {
            checkboxUI.classList.add('checked');
            checkboxUI.innerHTML = checkSVG;
        } else {
            checkboxUI.classList.remove('checked');
            checkboxUI.innerHTML = '';
        }
    }

    if (nativeChk && checkboxUI) {
        /* Clicking the visual box also toggles native */
        checkboxUI.addEventListener('click', function () {
            nativeChk.checked = !nativeChk.checked;
            syncCheckbox();
        });
        nativeChk.addEventListener('change', syncCheckbox);
    }

    /* ── Button loading state ────────────────── */
    var form    = document.getElementById('loginForm');
    var btnSub  = document.getElementById('btnSubmit');

    if (form) {
        form.addEventListener('submit', function () {
            if (btnSub) {
                btnSub.textContent = 'Đang xử lý…';
                btnSub.disabled    = true;
                btnSub.style.opacity = '0.75';
            }
        });
    }
})();
</script>

</body>
</html>
