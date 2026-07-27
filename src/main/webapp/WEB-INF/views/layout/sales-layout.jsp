<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8"/>
    <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
    <title>${pageTitle != null ? pageTitle : 'Sales Staff'} — OmniCore</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/dashboard.css"/>
    <c:if test="${currentPage == 'sales-channel-products'}">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/sales--channel-products.css"/>
    </c:if>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/notification.css"/>
    <script src="${pageContext.request.contextPath}/assets/js/pagination-util.js"></script>
</head>
<body>
<div class="app-shell">

    <!-- ══ SIDEBAR ════════════════════════════════════════════════ -->
    <aside class="sidebar" id="sidebar">

        <!-- Logo -->
        <div class="sidebar__logo">
            <div class="sidebar__logo-icon">
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
                     stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/>
                    <polyline points="3.29 7 12 12 20.71 7"/>
                    <line x1="12" x2="12" y1="22" y2="12"/>
                </svg>
            </div>
            <span class="sidebar__logo-name">OmniCore</span>
        </div>

        <!-- Role badge — emerald green for Sales Staff -->
        <div class="sidebar__role">
            <div class="sidebar__role-inner" style="border-color: rgba(52,211,153,0.20);">
                <div class="sidebar__role-dot" style="background: #34d399;"></div>
                <span class="sidebar__role-text" style="color: #34d399;">Sales Staff</span>
            </div>
        </div>

        <!-- Nav -->
        <nav class="sidebar__nav">

            <!-- XỬ LÝ ĐƠN HÀNG -->
            <div class="nav-group">
                <div class="nav-group__label">Xử Lý Đơn Hàng</div>
                <a href="${pageContext.request.contextPath}/sales/orders"
                   class="nav-item ${currentPage == 'sales-orders' ? 'active' : ''}">
                    <svg class="nav-item__icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
                         stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                        <circle cx="8" cy="21" r="1"/><circle cx="19" cy="21" r="1"/>
                        <path d="M2.05 2.05h2l2.66 12.42a2 2 0 0 0 2 1.58h9.78a2 2 0 0 0 1.95-1.57l1.65-7.43H5.12"/>
                    </svg>
                    <span>Tất cả đơn hàng</span>
                </a>
                <a href="${pageContext.request.contextPath}/sales/order-processing"
                   class="nav-item ${currentPage == 'sales-processing' ? 'active' : ''}">
                    <svg class="nav-item__icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
                         stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                        <rect width="8" height="4" x="8" y="2" rx="1" ry="1"/>
                        <path d="M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2"/>
                        <path d="M9 9h6"/>
                        <path d="M9 13h6"/>
                        <path d="M9 17h6"/>
                    </svg>
                    <span>Xử lý đơn hàng</span>
                </a>

            </div>

            <!-- KÊNH BÁN & SẢN PHẨM -->
            <div class="nav-group">
                <div class="nav-group__label">Kênh Bán &amp; Sản Phẩm</div>
                <a href="${pageContext.request.contextPath}/sales/categories"
                   class="nav-item ${currentPage == 'sales-categories' ? 'active' : ''}">
                    <svg class="nav-item__icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
                         stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                        <rect x="3" y="3" width="7" height="9" rx="1"/>
                        <rect x="14" y="3" width="7" height="5" rx="1"/>
                        <rect x="14" y="12" width="7" height="9" rx="1"/>
                        <rect x="3" y="16" width="7" height="5" rx="1"/>
                    </svg>
                    <span>Danh mục</span>
                </a>
                <a href="${pageContext.request.contextPath}/sales/master-sku"
                   class="nav-item ${currentPage == 'sales-master-sku' ? 'active' : ''}">
                    <svg class="nav-item__icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
                         stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                        <path d="M11 21H4a2 2 0 0 1-2-2V5c0-1.1.9-2 2-2h5l2 3h9a2 2 0 0 1 2 2v2"/>
                        <path d="m15 20 3 3 5-5"/><path d="M9 17H7"/><path d="M9 13H7"/>
                    </svg>
                    <span>Master SKU</span>
                </a>
                <a href="${pageContext.request.contextPath}/sales/sku-mapping"
                   class="nav-item ${currentPage == 'sales-sku-mapping' ? 'active' : ''}">
                    <svg class="nav-item__icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
                         stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                        <path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"/>
                        <path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"/>
                    </svg>
                    <span>Ánh xạ SKU</span>
                </a>
                <a href="${pageContext.request.contextPath}/sales/channel-products"
                   class="nav-item ${currentPage == 'sales-channel-products' ? 'active' : ''}">
                    <svg class="nav-item__icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
                         stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                        <path d="m6 14 1.45-2.9A2 2 0 0 1 9.24 10H20a2 2 0 0 1 1.94 2.5l-1.55 6a2 2 0 0 1-1.94 1.5H4a2 2 0 0 1-2-2V5c0-1.1.9-2 2-2h3.93a2 2 0 0 1 1.66.9l.82 1.2a2 2 0 0 0 1.66.9H18a2 2 0 0 1 2 2v2"/>
                    </svg>
                    <span>Sản phẩm kênh</span>
                </a>
            </div>

            <!-- CÀI ĐẶT -->
            <div class="sidebar__settings-divider">
                <div class="nav-group__label sidebar__settings-label">Cài Đặt</div>
                <a href="${pageContext.request.contextPath}/sales/profile"
                   class="nav-item ${currentPage == 'sales-profile' ? 'active' : ''}">
                    <svg class="nav-item__icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
                         stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                        <circle cx="12" cy="8" r="5"/><path d="M20 21a8 8 0 1 0-16 0"/>
                    </svg>
                    <span>Cài đặt tài khoản</span>
                </a>
            </div>
        </nav>

        <!-- Bottom user + logout -->
        <div class="sidebar__bottom">
            <button class="sidebar__user">
                <div class="sidebar__avatar" style="background: linear-gradient(135deg, #34d399, #059669);">SS</div>
                <div class="sidebar__user-info">
                    <div class="sidebar__user-name"><c:out value="${loggedInUser.fullName}"/></div>
                    <div class="sidebar__user-role">Sales Staff</div>
                </div>
            </button>
            <a href="${pageContext.request.contextPath}/logout" class="sidebar__logout">
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
                     stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/>
                    <polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/>
                </svg>
                <span>Đăng xuất</span>
            </a>
        </div>

        <!-- Collapse toggle -->
        <button class="sidebar__toggle" id="sidebarToggle" aria-label="Thu gọn sidebar">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
                 stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                <path d="m15 18-6-6 6-6"/>
            </svg>
        </button>
    </aside>

    <!-- ══ MAIN ═══════════════════════════════════════════════════ -->
    <div class="main-area">

        <!-- Topbar -->
        <header class="topbar">
            <div>
                <div class="topbar__title">${pageTitle != null ? pageTitle : 'Sales Staff'}</div>
                <c:if test="${pageSubtitle != null}">
                    <div class="topbar__subtitle">${pageSubtitle}</div>
                </c:if>
            </div>
            <div class="topbar__right">
                <div class="search-box">
                    <svg class="search-box__icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"
                         fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                        <circle cx="11" cy="11" r="8"/><path d="m21 21-4.35-4.35"/>
                    </svg>
                    <input class="search-box__input" type="text" placeholder="Tìm kiếm..." id="searchInput"/>
                </div>
                <button class="notif-btn" id="notifBtn" aria-label="Thông báo">
                    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
                         stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                        <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/>
                        <path d="M13.73 21a2 2 0 0 1-3.46 0"/>
                    </svg>
                    <span class="notif-btn__badge" id="notifBadge">0</span>
                </button>
                <div class="notif-panel" id="notifPanel">
                    <div class="notif-panel__hdr">
                        <span class="notif-panel__title">Thông báo</span>
                        <button class="notif-panel__markread" id="notifMarkAll">Đánh dấu đã đọc</button>
                    </div>
                    <div class="notif-panel__body" id="notifBody">
                        <!-- Populated by JS -->
                    </div>
                    <div class="notif-panel__ftr">
                        <button class="notif-panel__viewall" onclick="window.location.href='#'">Xem tất cả thông báo</button>
                    </div>
                </div>
                <div class="notif-backdrop" id="notifBackdrop" style="display:none;"></div>
            </div>
        </header>

        <!-- Page body -->
        <main class="page-content" id="pageContent">
            <jsp:include page="${contentPage}"/>
        </main>
    </div>
</div>

<script>
(function () {
    /* ── Sidebar collapse ── */
    var sidebar = document.getElementById('sidebar');
    var toggle  = document.getElementById('sidebarToggle');
    if (toggle) {
        toggle.addEventListener('click', function () {
            sidebar.classList.toggle('collapsed');
            toggle.classList.toggle('collapsed');
        });
    }

    /* ── Dynamic Avatar Initial ── */
    var userNameEl = document.querySelector('.sidebar__user-name');
    var avatarEl   = document.querySelector('.sidebar__avatar');
    if (userNameEl && avatarEl) {
        var name = userNameEl.textContent.trim();
        if (name) {
            var parts = name.split(/\s+/);
            var lastName = parts[parts.length - 1];
            if (lastName) {
                avatarEl.textContent = lastName.charAt(0).toUpperCase();
            }
        }
    }

    /* ── Notification Bell (Unified) ── */
    var notifBtn     = document.getElementById('notifBtn');
    var notifPanel   = document.getElementById('notifPanel');
    var notifBody    = document.getElementById('notifBody');
    var notifBadge   = document.getElementById('notifBadge');
    var notifMarkAll = document.getElementById('notifMarkAll');
    var notifBackdrop = document.getElementById('notifBackdrop');
    var notifLoaded = false;

    function loadNotifications() {
        fetch('/api/notifications?limit=20', { headers: { 'Accept': 'application/json' } })
        .then(function(r) { return r.json(); })
        .then(function(data) {
            if (!data.success) return;
            renderNotifications(data.notifications || []);
            updateBadge(data.unreadCount || 0);
            notifLoaded = true;
        }).catch(function() {});
    }

    function renderNotifications(notifications) {
        if (!notifBody) return;
        if (!notifications || notifications.length === 0) {
            notifBody.innerHTML =
                '<div class="notif-panel__empty">' +
                '<div class="notif-panel__empty-icon">' +
                '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">' +
                '<path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.73 21a2 2 0 0 1-3.46 0"/>' +
                '</svg></div><div class="notif-panel__empty-text">Không có thông báo nào</div></div>';
            return;
        }
        var html = '';
        notifications.forEach(function(n) {
            var iconClass = 'notif-item__icon--' + (n.iconClass || 'default');
            var priorityClass = 'notif-item__priority--' + (n.priority === 'HIGH' || n.priority === 'URGENT' ? 'high' : 'normal');
            var unreadClass = n.isRead ? '' : 'unread';
            html += '<div class="notif-item ' + unreadClass + '" data-id="' + n.id + '" data-ref-type="' + (n.referenceType || '') + '" data-ref-id="' + (n.referenceId || '') + '">' +
                '<div class="notif-item__icon ' + iconClass + '">' +
                '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">' + getNotifIcon(n.notificationType) + '</svg>' +
                '</div>' +
                '<div class="notif-item__body">' +
                '<div class="notif-item__title">' + escHtml(n.title || '') + '</div>' +
                '<div class="notif-item__msg">' + escHtml(n.message || '') + '</div>' +
                '<div class="notif-item__meta">' +
                '<span class="notif-item__priority ' + priorityClass + '"></span>' +
                '<span class="notif-item__time">' + (n.relativeTime || '') + '</span>' +
                (n.warehouseName ? '<span class="notif-item__wh">' + escHtml(n.warehouseName) + '</span>' : '') +
                '</div></div></div>';
        });
        notifBody.innerHTML = html;
        notifBody.querySelectorAll('.notif-item').forEach(function(item) {
            item.addEventListener('click', function() {
                var id = item.getAttribute('data-id');
                if (id && !item.classList.contains('unread')) return;
                fetch('/api/notifications/' + id + '/read', { method: 'POST', headers: { 'Accept': 'application/json' } })
                .then(function(r) { return r.json(); }).then(function(data) {
                    item.classList.remove('unread');
                    updateBadge(data.unreadCount || 0);
                }).catch(function() {});
            });
        });
    }

    function getNotifIcon(type) {
        switch (type) {
            case 'INBOUND':   return '<path d="M12 17V3"/><path d="m6 11 6 6"/><path d="M19 21H5"/>';
            case 'OUTBOUND':  return '<path d="m18 9-6-6-6 6"/><path d="M12 3v14"/><path d="M5 21h14"/>';
            case 'TRANSFER':   return '<path d="M8 3 4 7l4 4"/><path d="M4 7h16"/><path d="m16 21 4-4-4-4"/><path d="M20 17H4"/>';
            case 'RETURN': case 'RMA': return '<path d="M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"/><path d="M3 3v5h5"/>';
            case 'DEFECTIVE': return '<path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3Z"/><line x1="12" x2="12" y1="9" y2="13"/><line x1="12" x2="12.01" y1="17" y2="17"/>';
            case 'INVENTORY': return '<rect width="18" height="18" x="3" y="4" rx="2" ry="2"/><line x1="16" x2="16" y1="2" y2="6"/><line x1="8" x2="8" y1="2" y2="6"/><line x1="3" x2="21" y1="10" y2="10"/><path d="m9 16 2 2 4-4"/>';
            case 'ORDER':     return '<circle cx="8" cy="21" r="1"/><circle cx="19" cy="21" r="1"/><path d="M2.05 2.05h2l2.66 12.42a2 2 0 0 0 2 1.58h9.78a2 2 0 0 0 1.95-1.57l1.65-7.43H5.12"/>';
            case 'APPROVAL':  return '<path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/><path d="m9 12 2 2 4-4"/>';
            default:          return '<path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.73 21a2 2 0 0 1-3.46 0"/>';
        }
    }

    function updateBadge(count) {
        if (!notifBadge) return;
        if (count > 0) {
            notifBadge.textContent = count > 99 ? '99+' : count;
            notifBadge.style.display = 'flex';
        } else {
            notifBadge.textContent = '0';
            notifBadge.style.display = 'none';
        }
    }

    function escHtml(str) {
        var d = document.createElement('div');
        d.textContent = str;
        return d.innerHTML;
    }

    function togglePanel(show) {
        if (!notifPanel) return;
        if (show === undefined) show = !notifPanel.classList.contains('open');
        if (show) {
            notifPanel.classList.add('open');
            notifBackdrop.style.display = 'block';
            if (!notifLoaded) loadNotifications();
        } else {
            notifPanel.classList.remove('open');
            notifBackdrop.style.display = 'none';
        }
    }

    if (notifBtn) notifBtn.addEventListener('click', function(e) { e.stopPropagation(); togglePanel(); });
    if (notifBackdrop) notifBackdrop.addEventListener('click', function() { togglePanel(false); });
    if (notifMarkAll) notifMarkAll.addEventListener('click', function() {
        fetch('/api/notifications/read-all', { method: 'POST', headers: { 'Accept': 'application/json' } })
        .then(function(r) { return r.json(); }).then(function() {
            if (notifBody) notifBody.querySelectorAll('.notif-item.unread').forEach(function(el) { el.classList.remove('unread'); });
            updateBadge(0);
        }).catch(function() {});
    });
    document.addEventListener('keydown', function(e) { if (e.key === 'Escape') togglePanel(false); });
    setInterval(function() { if (notifPanel && notifPanel.classList.contains('open')) loadNotifications(); }, 60000);
    fetch('/api/notifications/count', { headers: { 'Accept': 'application/json' } })
    .then(function(r) { return r.json(); }).then(function(data) { updateBadge(data.unreadCount || 0); }).catch(function() {});

    // Auto-hide notification bell whenever any modal/overlay/sidebar panel is active on screen
    var _isNotifCheckBusy = false;
    var _notifAutoObserver = new MutationObserver(function() {
        if (_isNotifCheckBusy || !notifBtn) return;
        _isNotifCheckBusy = true;

        var modalOpen = document.body.classList.contains('modal-open') ||
            !!document.querySelector('.staff-overlay:not(.hidden-panel)') ||
            !!document.querySelector('.supplier-overlay:not(.hidden-panel)') ||
            !!document.querySelector('.warehouse-overlay:not(.hidden-panel)') ||
            !!document.querySelector('.category-overlay:not(.hidden-panel)') ||
            !!document.querySelector('.ic-modal-overlay.open, .ic-modal-overlay[style*="display: flex"], .ic-modal-overlay[style*="display: block"]') ||
            !!document.querySelector('.op-modal-overlay.open, .op-modal-overlay[style*="display: flex"], .op-modal-overlay[style*="display: block"]') ||
            !!document.querySelector('.zm-overlay.open, .zm-overlay[style*="display: flex"], .zm-overlay[style*="display: block"]') ||
            !!document.querySelector('.modal-overlay.open, .modal-overlay.active, .modal-overlay[style*="display: flex"], .modal-overlay[style*="display: block"]') ||
            !!document.querySelector('.modal.show, .modal[style*="display: flex"], .modal[style*="display: block"]') ||
            !!document.querySelector('[id*="Modal"]:not([style*="display: none"])') ||
            !!document.querySelector('[id*="Overlay"]:not([style*="display: none"]):not(.hidden-panel)');

        var isHidden = notifBtn.classList.contains('notif-hidden-by-modal');
        if (modalOpen && !isHidden) {
            notifBtn.classList.add('notif-hidden-by-modal');
            if (notifPanel) notifPanel.classList.remove('open');
            if (notifBackdrop) notifBackdrop.style.display = 'none';
        } else if (!modalOpen && isHidden) {
            notifBtn.classList.remove('notif-hidden-by-modal');
        }

        _isNotifCheckBusy = false;
    });

    if (document.body) {
        _notifAutoObserver.observe(document.body, { childList: true, subtree: true, attributes: true, attributeFilter: ['class'] });
    }
})();
</script>
</body>
</html>
