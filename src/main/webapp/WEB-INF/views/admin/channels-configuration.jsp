<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>

<div class="channels-config-container" style="max-width: 72rem; margin: 0 auto; padding-bottom: 2rem;">

    <!-- Status Toast -->
    <div id="statusToast" style="position: fixed; top: 1.5rem; right: 1.5rem; display: none; align-items: center; gap: 0.75rem; padding: 1rem 1.25rem; background: white; border-radius: var(--radius-btn); box-shadow: 0 10px 25px rgba(16,55,92,0.15); z-index: 1000; transition: all 0.3s ease; opacity: 0; transform: translateY(-10px);">
        <span id="toastIcon" style="font-weight: bold; width: 1.5rem; height: 1.5rem; border-radius: 50%; display: flex; align-items: center; justify-content: center; color: white;"></span>
        <span id="toastMsg" style="color: var(--navy); font-size: 13px; font-weight: 600;"></span>
    </div>

    <!-- Actions & Search Bar -->
    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1.25rem; flex-wrap: wrap; gap: 1rem;">
        <div style="position: relative; width: 18rem;">
            <svg style="position: absolute; left: 0.75rem; top: 50%; transform: translateY(-50%); width: 16px; height: 16px; color: rgba(16,55,92,0.35);" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <circle cx="11" cy="11" r="8"></circle>
                <line x1="21" y1="21" x2="16.65" y2="16.65"></line>
            </svg>
            <input type="text" id="channelSearch" placeholder="Tìm kiếm tên kênh..."
                   value="<c:out value='${searchKeyword}' default=''/>"
                   style="width: 100%; padding: 0.625rem 1rem 0.625rem 2.5rem; background: white; border: 1px solid #E5EAF3; color: var(--navy); font-size: 13px; outline: none; border-radius: var(--radius-btn); transition: border-color 0.2s;" />
        </div>

        <a href="${pageContext.request.contextPath}/admin/channels/create"
           style="display: inline-flex; align-items: center; gap: 0.5rem; padding: 0.625rem 1.25rem; background: var(--orange); color: white; border: none; font-size: 13px; font-weight: 700; border-radius: var(--radius-btn); text-decoration: none; cursor: pointer; transition: background 0.2s; box-shadow: 0 4px 12px rgba(235,131,23,0.20);">
            <svg style="width: 16px; height: 16px;" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                <line x1="12" y1="5" x2="12" y2="19"></line>
                <line x1="5" y1="12" x2="19" y2="12"></line>
            </svg>
            Thêm Kênh Kết Nối
        </a>
    </div>

    <!-- Channels Card Grid -->
    <div id="channelsGrid" style="display: grid; grid-template-columns: repeat(auto-fill, minmax(22rem, 1fr)); gap: 1.25rem;">

        <c:forEach var="chan" items="${channelsList}">
            <div class="channel-card"
                 data-name="<c:out value='${chan.channelName}'/>"
                 data-platform="<c:out value='${chan.platform}'/>"
                 style="background: white; border: 1px solid #E5EAF3; border-radius: var(--radius-card); padding: 1.5rem; display: flex; flex-direction: column; justify-content: space-between; transition: box-shadow 0.2s, transform 0.2s;">

                <div>
                    <!-- Card Header -->
                    <div style="display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 1rem;">
                        <div style="display: flex; align-items: center; gap: 0.75rem;">
                            <c:choose>
                                <c:when test="${chan.platform == 'Lazada'}">
                                    <span style="background: rgba(16,115,230,0.1); color: #1073e6; padding: 0.25rem 0.5rem; font-size: 10px; font-weight: 800; border-radius: 4px; border: 1px solid rgba(16,115,230,0.2);">LAZADA</span>
                                </c:when>
                                <c:when test="${chan.platform == 'Shopee'}">
                                    <span style="background: rgba(238,77,45,0.1); color: #ee4d2d; padding: 0.25rem 0.5rem; font-size: 10px; font-weight: 800; border-radius: 4px; border: 1px solid rgba(238,77,45,0.2);">SHOPEE</span>
                                </c:when>
                                <c:when test="${chan.platform == 'Website'}">
                                    <span style="background: rgba(235,131,23,0.1); color: #eb8317; padding: 0.25rem 0.5rem; font-size: 10px; font-weight: 800; border-radius: 4px; border: 1px solid rgba(235,131,23,0.2);">WEBSITE STOREFRONT</span>
                                </c:when>
                                <c:otherwise>
                                    <span style="background: rgba(235,131,23,0.1); color: #eb8317; padding: 0.25rem 0.5rem; font-size: 10px; font-weight: 800; border-radius: 4px; border: 1px solid rgba(235,131,23,0.2);">WEBSITE STOREFRONT</span>
                                </c:otherwise>
                            </c:choose>
                            <h4 style="color: var(--navy); font-size: 15px; font-weight: 700; margin: 0;"><c:out value="${chan.channelName}"/></h4>
                        </div>

                        <c:choose>
                            <c:when test="${chan.active}">
                                <span style="display: inline-flex; align-items: center; gap: 0.25rem; padding: 0.125rem 0.5rem; background: #e6f7ed; color: #10b981; font-size: 11px; font-weight: 700; border-radius: 20px; border: 1px solid rgba(16,185,129,0.2);">Active</span>
                            </c:when>
                            <c:otherwise>
                                <span style="display: inline-flex; align-items: center; gap: 0.25rem; padding: 0.125rem 0.5rem; background: #f3f5f8; color: rgba(16,55,92,0.4); font-size: 11px; font-weight: 700; border-radius: 20px; border: 1px solid #E5EAF3;">Inactive</span>
                            </c:otherwise>
                        </c:choose>
                    </div>

                    <!-- Card Body: Config Details -->
                    <div style="display: flex; flex-direction: column; gap: 0.5rem; margin-bottom: 1.25rem; font-size: 12px; color: rgba(16,55,92,0.70);">
                        <div style="display: flex; justify-content: space-between;">
                            <span>API Endpoint:</span>
                            <span style="font-family: monospace; font-weight: 600; color: var(--navy); text-overflow: ellipsis; overflow: hidden; white-space: nowrap; max-width: 14rem;" title="<c:out value='${chan.apiUrl}'/>"><c:out value="${chan.apiUrl}"/></span>
                        </div>
                        <div style="display: flex; justify-content: space-between;">
                            <span>App Key:</span>
                            <span style="font-family: monospace; font-weight: 600; color: var(--navy);"><c:out value="${chan.apiKey}"/></span>
                        </div>

                        <div style="display: flex; justify-content: space-between; align-items: center;">
                            <span>Webhook:</span>
                            <span style="color: #10b981; font-weight: 700; display: inline-flex; align-items: center; gap: 0.25rem;">
                                <span style="display: inline-block; width: 6px; height: 6px; border-radius: 50%; background: #10b981;"></span>Live Receiver</span>
                        </div>
                    </div>
                </div>

                <!-- Card Footer: OAuth Status + Actions -->
                <div style="border-top: 1px dashed #E5EAF3; padding-top: 0.75rem; margin-top: 0.25rem;">
                    <div style="display: flex; justify-content: space-between; align-items: center;">
                        <div>
                            <span style="font-size: 10px; color: rgba(16,55,92,0.4); text-transform: uppercase; display: block; font-weight: 700;">OAuth 2.0</span>
                            <span style="font-size: 11px; font-weight: 700; color: var(--navy); display: inline-flex; align-items: center; gap: 0.25rem;">
                                <c:choose>
                                    <c:when test="${chan.accessToken != null && chan.accessToken.length() > 0}">
                                        <span style="display: inline-block; width: 6px; height: 6px; border-radius: 50%; background: #10b981;"></span>Token Connected
                                    </c:when>
                                    <c:when test="${chan.apiKey != null && chan.apiKey.length() > 0}">
                                        <span style="display: inline-block; width: 6px; height: 6px; border-radius: 50%; background: #f59e0b;"></span>Chưa xác thực
                                    </c:when>
                                    <c:otherwise>
                                        <span style="display: inline-block; width: 6px; height: 6px; border-radius: 50%; background: rgba(16,55,92,0.3);"></span>Chưa cấu hình
                                    </c:otherwise>
                                </c:choose>
                            </span>
                        </div>

                        <div style="display: flex; align-items: center; gap: 0.375rem;">
                            <c:if test="${chan.platform == 'Lazada'}">
                                <a href="${pageContext.request.contextPath}/admin/channels/authorize/${chan.channelId}"
                                   title="Re-Authorize OAuth"
                                   style="font-size: 11px; font-weight: 700; color: #1073e6; text-decoration: none; display: inline-flex; align-items: center; gap: 0.25rem; border: 1px solid rgba(16,115,230,0.2); padding: 0.25rem 0.5rem; border-radius: 4px; background: rgba(16,115,230,0.05); transition: all 0.2s;">
                                    Re-Auth
                                </a>
                            </c:if>

                            <a href="${pageContext.request.contextPath}/admin/channels/edit/${chan.channelId}"
                               title="Chỉnh sửa"
                               style="display: inline-flex; align-items: center; justify-content: center; width: 1.875rem; height: 1.875rem; color: rgba(16,55,92,0.5); border: 1px solid #E5EAF3; border-radius: 4px; background: var(--alice); transition: all 0.2s; text-decoration: none;">
                                <svg style="width: 13px; height: 13px;" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                                    <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"></path>
                                    <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"></path>
                                </svg>
                            </a>

                            <a href="${pageContext.request.contextPath}/admin/channels/delete/${chan.channelId}"
                               title="Xóa kênh"
                               data-channel-name="<c:out value='${chan.channelName}'/>"
                               onclick="var name = this.getAttribute('data-channel-name'); return confirm('B\u1ea1n c\u00f3 ch\u1eafc mu\u1ed1n x\u00f3a k\u00eanh \u2018' + name + '\u2019 kh\u00f4ng?');"
                               style="display: inline-flex; align-items: center; justify-content: center; width: 1.875rem; height: 1.875rem; color: #ef4444; border: 1px solid #fecaca; border-radius: 4px; background: #fef2f2; transition: all 0.2s; text-decoration: none;">
                                <svg style="width: 13px; height: 13px;" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                                    <polyline points="3 6 5 6 21 6"></polyline>
                                    <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2"></path>
                                </svg>
                            </a>
                        </div>
                    </div>
                </div>
            </div>
        </c:forEach>

        <!-- EMPTY STATE -->
        <c:if test="${empty channelsList}">
            <div style="grid-column: 1 / -1; display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 4rem 2rem; background: white; border: 1px solid #E5EAF3; border-radius: var(--radius-card); text-align: center;">
                <div style="width: 4rem; height: 4rem; background: var(--alice); border-radius: 50%; display: flex; align-items: center; justify-content: center; margin-bottom: 1rem; color: rgba(16,55,92,0.30);">
                    <svg style="width: 2rem; height: 2rem;" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                        <rect x="2" y="3" width="20" height="14" rx="2" ry="2"></rect>
                        <line x1="8" y1="21" x2="16" y2="21"></line>
                        <line x1="12" y1="17" x2="12" y2="21"></line>
                    </svg>
                </div>
                <h4 style="color: var(--navy); font-size: 16px; font-weight: 700; margin: 0 0 0.5rem 0;">Chưa cấu hình kênh bán hàng nào</h4>
                <p style="color: rgba(16,55,92,0.45); font-size: 13px; margin: 0 0 1.5rem 0; max-width: 24rem;">
                    <c:choose>
                        <c:when test="${not empty searchKeyword}">Không tìm thấy kênh nào khớp với từ khóa \u201c<c:out value='${searchKeyword}'/>\u201d.</c:when>
                        <c:otherwise>Hãy thêm kênh bán hàng đầu tiên để đồng bộ thông tin đơn hàng và tồn kho đa sàn với hệ thống OmniCore.</c:otherwise>
                    </c:choose>
                </p>
                <c:if test="${empty searchKeyword}">
                    <a href="${pageContext.request.contextPath}/admin/channels/create"
                       style="display: inline-flex; align-items: center; gap: 0.5rem; padding: 0.625rem 1.5rem; background: var(--orange); color: white; border: none; font-size: 13px; font-weight: 700; border-radius: var(--radius-btn); text-decoration: none; cursor: pointer; box-shadow: 0 4px 12px rgba(235,131,23,0.20);">
                        Thêm Kênh Kết Nối
                    </a>
                </c:if>
            </div>
        </c:if>
    </div>

</div>

<script>
(function() {
    // Client-side search filter with debounce
    var searchInput = document.getElementById('channelSearch');
    var cards = document.querySelectorAll('.channel-card');
    var debounceTimer = null;

    searchInput.addEventListener('input', function() {
        clearTimeout(debounceTimer);
        debounceTimer = setTimeout(function() {
            var query = searchInput.value.toLowerCase().trim();
            if (query.length === 0) {
                // Clear keyword — show all cards
                cards.forEach(function(card) { card.style.display = ''; });
                return;
            }
            cards.forEach(function(card) {
                var name = card.getAttribute('data-name');
                card.style.display = (name && name.toLowerCase().indexOf(query) !== -1) ? '' : 'none';
            });
        }, 250);
    });

    // Status toast from URL parameters
    var urlParams = new URLSearchParams(window.location.search);
    var status = urlParams.get('status');
    var messages = {
        'success':    { text: 'Lưu cấu hình kênh bán hàng thành công!', ok: true },
        'updated':    { text: 'Cập nhật kênh bán hàng thành công!',     ok: true },
        'deleted':    { text: 'Xóa kênh bán hàng thành công!',         ok: true },
        'error':      { text: 'Đã xảy ra lỗi. Vui lòng thử lại.',       ok: false }
    };

    if (status && messages[status]) {
        var info = messages[status];
        var toast = document.getElementById('statusToast');
        var icon  = document.getElementById('toastIcon');
        var msg   = document.getElementById('toastMsg');

        toast.style.border = '1px solid ' + (info.ok ? '#10b981' : '#ef4444');
        toast.style.borderLeft = '4px solid ' + (info.ok ? '#10b981' : '#ef4444');
        icon.textContent = info.ok ? '\u2713' : '\u2717';
        icon.style.background = info.ok ? '#10b981' : '#ef4444';
        msg.textContent = info.text;

        toast.style.display = 'flex';
        toast.offsetHeight;
        toast.style.opacity = '1';
        toast.style.transform = 'translateY(0)';

        setTimeout(function() {
            toast.style.opacity = '0';
            toast.style.transform = 'translateY(-10px)';
            setTimeout(function() { toast.style.display = 'none'; }, 300);
        }, 4000);
    }
})();
</script>
