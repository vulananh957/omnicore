<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>

<div class="channel-create" style="max-width: 48rem; margin: 0 auto; padding-bottom: 2rem;">

    <!-- Back Arrow + Breadcrumb -->
    <div style="display: flex; align-items: center; gap: 0.5rem; margin-bottom: 1rem;">
        <a href="${pageContext.request.contextPath}/admin/channels"
           style="width: 2rem; height: 2rem; background: var(--alice); border-radius: var(--radius-btn); color: var(--navy); text-decoration: none; display: flex; align-items: center; justify-content: center;">
            <svg style="width: 16px; height: 16px;" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                <line x1="19" y1="12" x2="5" y2="12"></line>
                <polyline points="12 19 5 12 12 5"></polyline>
            </svg>
        </a>
        <span style="color: rgba(16,55,92,0.4); font-size: 12px;">Quay lại danh sách kênh</span>
    </div>

    <!-- Main Config Form -->
    <form action="${pageContext.request.contextPath}/admin/channels/create" method="POST" id="channelConfigForm">
        <input type="hidden" name="channelId" value="<c:out value='${channel.channelId}' default=''/>" />
        <input type="hidden" name="accessToken" id="accessToken" value="<c:out value='${channel.accessToken}' default=''/>" />
        <input type="hidden" name="refreshToken" id="refreshToken" value="<c:out value='${channel.refreshToken}' default=''/>" />

        <!-- SECTION 1: BASIC INFO -->
        <div style="background: white; border: 1px solid #E5EAF3; border-radius: var(--radius-card); padding: 1.5rem; margin-bottom: 1rem;">
            <div style="display: flex; align-items: center; gap: 0.75rem; margin-bottom: 1.25rem;">
                <div style="width: 2.5rem; height: 2.5rem; background: var(--alice); display: flex; align-items: center; justify-content: center; border-radius: var(--radius-btn);">
                    <svg style="width: 20px; height: 20px; color: rgba(16,55,92,0.60);" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                        <rect x="2" y="3" width="20" height="14" rx="2" ry="2"></rect>
                        <line x1="8" y1="21" x2="16" y2="21"></line>
                        <line x1="12" y1="17" x2="12" y2="21"></line>
                    </svg>
                </div>
                <div>
                    <h3 style="color: var(--navy); font-size: 15px; font-weight: 700; margin: 0;">Thông tin cơ bản</h3>
                    <p style="color: rgba(16,55,92,0.45); font-size: 12px; margin: 0.125rem 0 0 0;">Lựa chọn nền tảng và thiết lập nhận diện kênh bán</p>
                </div>
            </div>

            <div style="display: flex; flex-direction: column; gap: 1rem;">
                <div style="display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 1rem;">
                    <div>
                        <label style="display: block; color: rgba(16,55,92,0.70); font-size: 12px; font-weight: 600; margin-bottom: 0.375rem;">Sàn thương mại (Platform) <span style="color: #ef4444;">*</span></label>
                        <select id="platform" name="platform" required
                                style="width: 100%; padding: 0.625rem 1rem; background: var(--alice); border: 1px solid #E5EAF3; color: var(--navy); font-size: 13px; outline: none; border-radius: calc(var(--radius-btn) - 2px); cursor: pointer;">
                            <option value="Lazada" ${channel.platform == 'Lazada' ? 'selected' : ''}>Lazada</option>
                            <option value="Website" ${channel.platform == 'Website' ? 'selected' : ''}>Website (Online Shop)</option>
                        </select>
                    </div>
                    <div>
                        <label style="display: block; color: rgba(16,55,92,0.70); font-size: 12px; font-weight: 600; margin-bottom: 0.375rem;">Tên gian hàng (Channel Name) <span style="color: #ef4444;">*</span></label>
                        <input type="text" id="channelName" name="channelName" required placeholder="VD: Lazada Tạp Hóa Official"
                               value="<c:out value='${channel.channelName}' default=''/>"
                               style="width: 100%; padding: 0.625rem 1rem; background: var(--alice); border: 1px solid #E5EAF3; color: var(--navy); font-size: 13px; outline: none; border-radius: calc(var(--radius-btn) - 2px);" />
                    </div>
                </div>

                <div>
                    <label style="display: block; color: rgba(16,55,92,0.70); font-size: 12px; font-weight: 600; margin-bottom: 0.375rem;">API Endpoint URL <span id="apiUrlRequiredMark" style="color: #ef4444;">*</span></label>
                    <input type="url" id="apiUrl" name="apiUrl" placeholder="https://api.lazada.vn/rest"
                           value="<c:out value='${channel.apiUrl}' default=''/>"
                           style="width: 100%; padding: 0.625rem 1rem; background: var(--alice); border: 1px solid #E5EAF3; color: var(--navy); font-size: 13px; outline: none; border-radius: calc(var(--radius-btn) - 2px);" />
                </div>

                <div>
                    <label style="display: block; color: rgba(16,55,92,0.70); font-size: 12px; font-weight: 600; margin-bottom: 0.375rem;">Trạng thái hoạt động</label>
                    <div style="display: flex; gap: 1.5rem; align-items: center; margin-top: 0.25rem;">
                        <label style="display: flex; align-items: center; gap: 0.5rem; font-size: 13px; color: var(--navy); cursor: pointer;">
                            <input type="radio" name="isActive" value="true" ${!channel.active || channel.active ? 'checked' : ''} style="accent-color: var(--orange);" /> Hoạt động
                        </label>
                        <label style="display: flex; align-items: center; gap: 0.5rem; font-size: 13px; color: var(--navy); cursor: pointer;">
                            <input type="radio" name="isActive" value="false" ${channel.active == false ? 'checked' : ''} style="accent-color: var(--orange);" /> Ngừng hoạt động
                        </label>
                    </div>
                </div>
            </div>
        </div>

        <!-- SECTION 2: API CREDENTIALS -->
        <div style="background: white; border: 1px solid #E5EAF3; border-radius: var(--radius-card); padding: 1.5rem; margin-bottom: 1rem;">
            <div style="display: flex; align-items: center; gap: 0.75rem; margin-bottom: 1.25rem;">
                <div style="width: 2.5rem; height: 2.5rem; background: var(--alice); display: flex; align-items: center; justify-content: center; border-radius: var(--radius-btn);">
                    <svg style="width: 20px; height: 20px; color: rgba(16,55,92,0.60);" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                        <rect x="3" y="11" width="18" height="11" rx="2" ry="2"></rect>
                        <path d="M7 11V7a5 5 0 0 1 10 0v4"></path>
                    </svg>
                </div>
                <div>
                    <h3 style="color: var(--navy); font-size: 15px; font-weight: 700; margin: 0;">Xác thực API (API Credentials)</h3>
                    <p style="color: rgba(16,55,92,0.45); font-size: 12px; margin: 0.125rem 0 0 0;">Cung cấp khóa kết nối và mã ủy quyền từ App Console của Sàn</p>
                </div>
            </div>

            <div style="display: flex; flex-direction: column; gap: 1rem;">
                <div id="apiCredGrid" style="display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 1rem;">
                    <!-- App Key (Lazada only — Website authenticates purely via HMAC app_secret, no api_key) -->
                    <div id="appKeyField">
                        <label style="display: block; color: rgba(16,55,92,0.70); font-size: 12px; font-weight: 600; margin-bottom: 0.375rem;">App Key <span id="apiKeyRequiredMark" style="color: #ef4444;">*</span></label>
                        <div style="display: flex; gap: 0.5rem;">
                            <input type="text" id="apiKey" name="apiKey" placeholder="Lấy từ Console"
                                   value="<c:out value='${channel.apiKey}' default=''/>"
                                   style="flex: 1; padding: 0.625rem 1rem; background: var(--alice); border: 1px solid #E5EAF3; color: var(--navy); font-size: 13px; outline: none; border-radius: calc(var(--radius-btn) - 2px);" />
                        </div>
                    </div>

                    <!-- App Secret -->
                    <div>
                        <label style="display: block; color: rgba(16,55,92,0.70); font-size: 12px; font-weight: 600; margin-bottom: 0.375rem;">App Secret <span style="color: #ef4444;">*</span></label>
                        <div style="display: flex; gap: 0.5rem;">
                            <input type="password" id="appSecret" name="appSecret"
                                   placeholder="${isEditMode ? 'Nhập mật khẩu mới để thay đổi, bỏ trống để giữ nguyên' : '••••••••••'}"
                                   <c:if test="${!isEditMode}">required</c:if>
                                   value="<c:out value='${channel.appSecret}' default=''/>"
                                   style="flex: 1; padding: 0.625rem 1rem; background: var(--alice); border: 1px solid #E5EAF3; color: var(--navy); font-size: 13px; outline: none; border-radius: calc(var(--radius-btn) - 2px);" />
                            <button type="button" id="btnViewSecret" title="Hiện / Ẩn App Secret"
                                    style="display: none; align-items: center; justify-content: center; padding: 0 0.75rem; background: var(--alice); border: 1px solid #E5EAF3; color: var(--navy); border-radius: calc(var(--radius-btn) - 2px); cursor: pointer; flex-shrink: 0;">
                                <svg id="iconEyeOpen" style="width:14px;height:14px;" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                                    <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path><circle cx="12" cy="12" r="3"></circle>
                                </svg>
                                <svg id="iconEyeClosed" style="width:14px;height:14px;display:none;" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                                    <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"></path><line x1="1" y1="1" x2="23" y2="23"></line>
                                </svg>
                            </button>
                            <button type="button" id="btnCopySecret" title="Copy App Secret"
                                    style="display: none; align-items: center; justify-content: center; padding: 0 0.75rem; background: var(--alice); border: 1px solid #E5EAF3; color: var(--navy); border-radius: calc(var(--radius-btn) - 2px); cursor: pointer; flex-shrink: 0;">
                                <svg style="width:14px;height:14px;" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                                    <rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect>
                                    <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path>
                                </svg>
                            </button>
                            <button type="button" id="btnRefreshSecret" title="Sinh lại App Secret"
                                    style="display: none; align-items: center; justify-content: center; padding: 0 0.75rem; background: var(--alice); border: 1px solid #E5EAF3; color: var(--navy); border-radius: calc(var(--radius-btn) - 2px); cursor: pointer; flex-shrink: 0;">
                                <svg style="width:14px;height:14px;" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                                    <polyline points="23 4 23 10 17 10"></polyline><polyline points="1 20 1 14 7 14"></polyline>
                                    <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"></path>
                                </svg>
                            </button>
                        </div>
                        <p id="secretRotateWarning" style="display: none; color: #ef4444; font-size: 11px; margin: 0.375rem 0 0 0;">
                            Sinh lại sẽ làm omnicore-web ngừng xác thực được ngay cho đến khi anh cập nhật <code>omnicore.hmac.secret</code> bên đó và khởi động lại/reload.
                        </p>
                    </div>
                </div>

                <!-- Mock shipping — Website only, independent toggle (not part of the channel save form) -->
                <div id="mockShippingSection" style="display: none; margin-top: 1rem; padding-top: 1rem; border-top: 1px dashed #E5EAF3;">
                    <label style="display: flex; align-items: center; gap: 0.5rem; font-size: 13px; color: var(--navy); cursor: pointer;">
                        <input type="checkbox" id="mockShippingEnabled" style="accent-color: var(--orange); width: 16px; height: 16px;" ${mockShippingEnabled ? 'checked' : ''} />
                        Bật mô phỏng đơn vị vận chuyển (mock)
                    </label>
                    <p style="color: rgba(16,55,92,0.45); font-size: 11px; margin: 0.375rem 0 0 1.5rem;">
                        Khi bật: khách chọn hãng + phí vận chuyển giả lúc checkout trên Website, kho in được tem vận đơn giả. Không ảnh hưởng nút "Xác nhận đã giao" — nút đó luôn hoạt động dù tắt mock.
                    </p>
                </div>

                <!-- SUBMIT FOOTER -->
        <div style="background: white; border: 1px solid #E5EAF3; border-radius: var(--radius-card); padding: 1.5rem; margin-bottom: 1.25rem;">
            <div style="display: flex; align-items: center; gap: 0.75rem; margin-bottom: 1.25rem;">
                <div style="width: 2.5rem; height: 2.5rem; background: var(--alice); display: flex; align-items: center; justify-content: center; border-radius: var(--radius-btn);">
                    <svg style="width: 20px; height: 20px; color: rgba(16,55,92,0.60);" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                        <path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"></path>
                        <polyline points="22,6 12,13 2,6"></polyline>
                    </svg>
                </div>
                <div>
                    <h3 style="color: var(--navy); font-size: 15px; font-weight: 700; margin: 0;">Webhook &amp; Đồng bộ tồn kho</h3>
                    <p style="color: rgba(16,55,92,0.45); font-size: 12px; margin: 0.125rem 0 0 0;">Cấu hình nhận sự kiện trực tiếp từ sàn thương mại điện tử</p>
                </div>
            </div>

            <div style="display: flex; flex-direction: column; gap: 1rem;">
                    <div>
                        <label style="display: block; color: rgba(16,55,92,0.70); font-size: 12px; font-weight: 600; margin-bottom: 0.375rem;">Webhook Receiver URL</label>
                        <div style="display: flex; gap: 0.5rem; width: 100%;">
                            <input type="text" id="webhookUrl" readonly
                                   value="${pageContext.request.scheme}://${pageContext.request.serverName}${pageContext.request.contextPath}/webhook/lazada"
                                   style="flex: 1; padding: 0.625rem 1rem; background: #f3f5f8; border: 1px solid #D1D9E6; color: rgba(16,55,92,0.60); font-size: 13px; outline: none; border-radius: calc(var(--radius-btn) - 2px); cursor: not-allowed;" />
                            <button type="button" id="btnCopyWebhook"
                                    style="padding: 0 1rem; background: var(--alice); border: 1px solid #E5EAF3; color: var(--navy); font-size: 13px; font-weight: 600; border-radius: calc(var(--radius-btn) - 2px); cursor: pointer;">Copy</button>
                        </div>
                        <input type="hidden" name="webhookCallbackUrl"
                               value="${pageContext.request.scheme}://${pageContext.request.serverName}${pageContext.request.contextPath}/webhook/lazada" />
                    </div>

                <div style="display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 1rem;">
                    <div>
                        <label style="display: block; color: rgba(16,55,92,0.70); font-size: 12px; font-weight: 600; margin-bottom: 0.375rem;">Webhook Secret Key</label>
                        <input type="text" id="webhookSecret" name="webhookSecret" placeholder="Lấy từ Lazada App Console"
                               value="<c:out value='${channel.webhookSecret}' default=''/>"
                               style="width: 100%; padding: 0.625rem 1rem; background: var(--alice); border: 1px solid #E5EAF3; color: var(--navy); font-size: 13px; outline: none; border-radius: calc(var(--radius-btn) - 2px);" />
                    </div>
                </div>
            </div>
        </div>

        <!-- SUBMIT FOOTER -->
        <div style="display: flex; justify-content: flex-end; gap: 1rem;">
            <a href="${pageContext.request.contextPath}/admin/channels"
               style="display: inline-flex; align-items: center; padding: 0.625rem 1.5rem; background: var(--alice); color: var(--navy); text-decoration: none; font-size: 13px; font-weight: 600; border-radius: calc(var(--radius-btn) - 2px); border: 1px solid #E5EAF3;">Hủy cấu hình</a>
            <button type="submit"
                    style="display: inline-flex; align-items: center; gap: 0.5rem; padding: 0.625rem 1.5rem; background: var(--orange); color: white; border: none; font-size: 13px; font-weight: 600; border-radius: calc(var(--radius-btn) - 2px); cursor: pointer; box-shadow: 0 4px 12px rgba(235,131,23,0.20);">
                <svg style="width: 14px; height: 14px;" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z"></path>
                    <polyline points="17 21 17 13 7 13 7 21"></polyline>
                    <polyline points="7 3 7 8 15 8"></polyline>
                </svg>
                ${isEditMode ? 'Cập nhật cấu hình' : 'Lưu cấu hình'}
            </button>
        </div>
    </form>
</div>

<script>
(function() {
    var ctx = '${pageContext.request.contextPath}';

    // Platform config — defaults for empty form
    var configMap = {
        'Lazada':  { endpoint: 'https://api.lazada.vn/rest',  webhook: 'https://wmshub.vn/webhook/lazada' },
        'Website': { endpoint: '', webhook: '' }
    };

    var platformSelect     = document.getElementById('platform');
    var apiUrlInput        = document.getElementById('apiUrl');
    var apiCredGrid        = document.getElementById('apiCredGrid');
    var appKeyField        = document.getElementById('appKeyField');
    var apiKeyInput        = document.getElementById('apiKey');
    var appSecretInput     = document.getElementById('appSecret');
    var webhookUrlInput    = document.getElementById('webhookUrl');
    var apiUrlRequiredMark = document.getElementById('apiUrlRequiredMark');
    var apiKeyRequiredMark = document.getElementById('apiKeyRequiredMark');
    var btnRefreshSecret   = document.getElementById('btnRefreshSecret');
    var btnViewSecret      = document.getElementById('btnViewSecret');
    var btnCopySecret      = document.getElementById('btnCopySecret');
    var secretRotateWarning = document.getElementById('secretRotateWarning');
    var iconEyeOpen        = document.getElementById('iconEyeOpen');
    var iconEyeClosed      = document.getElementById('iconEyeClosed');
    var mockShippingSection = document.getElementById('mockShippingSection');
    var mockShippingEnabled = document.getElementById('mockShippingEnabled');

    // ── Generate key from server ──────────────────────────────────────────────
    function generateKey(type, targetInput, btn) {
        var origContent = btn.innerHTML;
        btn.disabled = true;
        btn.style.opacity = '0.5';
        fetch(ctx + '/admin/channels/create?action=generateKey&type=' + type, { method: 'POST' })
            .then(function(r) { return r.json(); })
            .then(function(data) {
                if (data.success) {
                    targetInput.value = data.value;
                    // Brief green flash to signal success
                    targetInput.style.borderColor = '#10b981';
                    setTimeout(function() { targetInput.style.borderColor = '#E5EAF3'; }, 1200);
                }
            })
            .catch(function() {})
            .finally(function() {
                btn.disabled = false;
                btn.style.opacity = '1';
            });
    }

    // ── Platform-aware field mode ─────────────────────────────────────────────
    var isEditMode = ${isEditMode ? 'true' : 'false'};

    function applyWebsiteMode() {
        // App Key is unused for Website (auth is pure HMAC via app_secret) — hide entirely
        // and clear any leftover value instead of showing a dead field.
        appKeyField.style.display = 'none';
        apiCredGrid.style.gridTemplateColumns = '1fr';
        apiKeyInput.value = '';
        apiKeyInput.required = false;
        apiKeyRequiredMark.style.display = 'none';

        // App Secret: readonly, server-generated, revealed/copied/rotated via buttons below.
        appSecretInput.readOnly = true;
        appSecretInput.style.background = '#f3f5f8';
        appSecretInput.style.cursor = 'default';
        appSecretInput.required = false;
        apiUrlInput.required    = false;
        apiUrlRequiredMark.style.display = 'none';
        // show action buttons
        btnRefreshSecret.style.display = 'flex';
        btnViewSecret.style.display    = 'flex';
        btnCopySecret.style.display    = 'flex';
        secretRotateWarning.style.display = 'block';
        // Auto-generate if empty (create mode or empty edit) — direct call, no confirm needed
        // since there's nothing working yet to break.
        if (!appSecretInput.value) generateKey('appSecret', appSecretInput, btnRefreshSecret);

        mockShippingSection.style.display = 'block';
    }

    function applyLazadaMode() {
        appKeyField.style.display = '';
        apiCredGrid.style.gridTemplateColumns = 'repeat(2, minmax(0, 1fr))';
        apiKeyInput.readOnly    = false;
        appSecretInput.readOnly = false;
        apiKeyInput.style.background    = 'var(--alice)';
        appSecretInput.style.background = 'var(--alice)';
        apiKeyInput.style.cursor    = '';
        appSecretInput.style.cursor = '';
        apiKeyInput.required    = true;
        apiUrlInput.required    = true;
        apiUrlRequiredMark.style.display = '';
        apiKeyRequiredMark.style.display = '';
        btnRefreshSecret.style.display = 'none';
        btnViewSecret.style.display    = 'none';
        btnCopySecret.style.display    = 'none';
        secretRotateWarning.style.display = 'none';
        // restore password input type
        appSecretInput.type = 'password';
        iconEyeOpen.style.display   = '';
        iconEyeClosed.style.display = 'none';

        mockShippingSection.style.display = 'none';
    }

    function handlePlatformChange() {
        var platform = platformSelect.value;
        var config = configMap[platform] || {};
        if (!apiUrlInput.value ||
            apiUrlInput.value === 'https://api.lazada.vn/rest' ||
            apiUrlInput.value === 'https://api.shopee.vn/api/v2') {
            apiUrlInput.value = config.endpoint || '';
        }
        if (webhookUrlInput) webhookUrlInput.value = config.webhook || '';

        if (platform === 'Website') {
            applyWebsiteMode();
        } else {
            applyLazadaMode();
        }
    }

    platformSelect.addEventListener('change', handlePlatformChange);
    handlePlatformChange(); // run on load

    // ── Refresh App Secret ────────────────────────────────────────────────────
    // Manual click replaces a secret that omnicore-web may already be relying on —
    // confirm first, since the two apps don't sync automatically (see secretRotateWarning).
    btnRefreshSecret.addEventListener('click', function() {
        var hasExisting = !!appSecretInput.value;
        if (hasExisting && !window.confirm(
            'Sinh App Secret mới sẽ làm omnicore-web ngừng xác thực được ngay lập tức, ' +
            'cho đến khi anh cập nhật omnicore.hmac.secret bên đó và khởi động lại/reload. Tiếp tục?'
        )) {
            return;
        }
        generateKey('appSecret', appSecretInput, btnRefreshSecret);
    });

    // ── View / Hide App Secret ────────────────────────────────────────────────
    btnViewSecret.addEventListener('click', function() {
        var isPassword = (appSecretInput.type === 'password');
        appSecretInput.type = isPassword ? 'text' : 'password';
        iconEyeOpen.style.display   = isPassword ? 'none' : '';
        iconEyeClosed.style.display = isPassword ? '' : 'none';
    });

    // ── Mock shipping toggle — saved immediately via its own AJAX call, independent of
    // the channel-save form (it's a system_config value, not a column on this channel row).
    mockShippingEnabled.addEventListener('change', function() {
        var checked = mockShippingEnabled.checked;
        fetch(ctx + '/admin/channels/create?action=toggleMockShipping&enabled=' + checked, { method: 'POST' })
            .then(function(r) { return r.json(); })
            .then(function(data) {
                if (!data.success) {
                    mockShippingEnabled.checked = !checked; // revert on failure
                    alert('Không thể lưu cấu hình mock. Vui lòng thử lại.');
                }
            })
            .catch(function() {
                mockShippingEnabled.checked = !checked;
                alert('Lỗi kết nối. Vui lòng thử lại.');
            });
    });

    // ── Copy App Secret ───────────────────────────────────────────────────────
    btnCopySecret.addEventListener('click', function() {
        navigator.clipboard.writeText(appSecretInput.value).then(function() {
            btnCopySecret.style.background = '#10b981';
            setTimeout(function() { btnCopySecret.style.background = 'var(--alice)'; }, 1200);
        });
    });

    // ── Copy Webhook URL ──────────────────────────────────────────────────────
    var btnCopyWebhook = document.getElementById('btnCopyWebhook');
    if (btnCopyWebhook) {
        btnCopyWebhook.addEventListener('click', function() {
            var text = document.getElementById('webhookUrl').value;
            navigator.clipboard.writeText(text).then(function() {
                btnCopyWebhook.textContent = 'Copied!';
                btnCopyWebhook.style.background = '#10b981';
                btnCopyWebhook.style.color = 'white';
                setTimeout(function() {
                    btnCopyWebhook.textContent = 'Copy';
                    btnCopyWebhook.style.background = 'var(--alice)';
                    btnCopyWebhook.style.color = 'var(--navy)';
                }, 2000);
            });
        });
    }
})();
</script>
