<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" isELIgnored="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>

<%-- ══════════════════════════════════════════════════════════════════
     Sales Staff — Xử Lý Đơn Hàng (Order Processing)
     JSP port of React: OrderProcessing.tsx
     All logic is pure vanilla JS — no hardcoded data, no seed data.
     ══════════════════════════════════════════════════════════════════ --%>

<link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/sales--order-processing.css"/>

<%-- ── ENTERPRISE TAB SWITCHER BAR ── --%>
<div class="op-tab-bar">
    <button class="op-tab tab-review" id="tabReview" onclick="switchTab('pending_review')">
        Đơn cần duyệt
        <span class="op-tab-badge" id="badgeReview">0</span>
    </button>
    <button class="op-tab tab-waybill" id="tabWaybill" onclick="switchTab('pending_waybill')">
        Chờ in mã vận đơn
        <span class="op-tab-badge" id="badgeWaybill">0</span>
    </button>
    <button class="op-tab tab-rts" id="tabRTS" onclick="switchTab('pending_rts')">
        Chờ bàn giao ĐVVC
        <span class="op-tab-badge" id="badgeRTS">0</span>
    </button>
    <button class="op-tab tab-rma" id="tabRMA" onclick="switchTab('rma_dispute')">
        Hàng Hoàn &amp; Khiếu Nại
        <span class="op-tab-badge" id="badgeRMA">0</span>
    </button>
    <button class="op-tab tab-cancelled" id="tabCancelled" onclick="switchTab('cancelled')">
        Đã hủy
        <span class="op-tab-badge" id="badgeCancelled">0</span>
    </button>
</div>

<%-- ── ENTERPRISE FILTER BAR ── --%>
<div class="op-filter-bar">
    <%-- Search input --%>
    <div class="op-search">
        <svg class="op-search-icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="11" cy="11" r="8"></circle><line x1="21" y1="21" x2="16.65" y2="16.65"></line>
        </svg>
        <input type="text" placeholder="Tìm theo Mã đơn, Mã vận đơn, Tên khách, SKU, Tên sản phẩm..." id="opSearchInput" oninput="onSearchInput(this.value)" />
    </div>

    <%-- Filter 1: Kênh Bán --%>
    <div style="position:relative">
        <button class="op-filter-btn" onclick="toggleDropdown('ddChannel', event)">
            <span style="display:flex;align-items:center;gap:6px">
                <svg class="f-icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><line x1="2" y1="12" x2="22" y2="12"></line><path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"></path></svg>
                Kênh bán: <strong id="lblChannel" style="color:var(--navy)">Tất cả</strong>
            </span>
            <svg class="clear-x" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" onclick="clearFilter('channel', event)"><line x1="18" y1="6" x2="6" y2="18"></line><line x1="6" y1="6" x2="18" y2="18"></line></svg>
        </button>
        <div id="ddChannel" class="op-dropdown">
            <button class="selected" onclick="selectChannel('all')">Tất cả các kênh</button>
            <button onclick="selectChannel('Shopee')">Shopee</button>
            <button onclick="selectChannel('TikTok')">TikTok</button>
            <button onclick="selectChannel('Lazada')">Lazada</button>
            <button onclick="selectChannel('Website')">Website</button>
        </div>
    </div>

    <%-- Filter 2: Sản Phẩm --%>
    <div style="position:relative">
        <button class="op-filter-btn" onclick="toggleDropdown('ddProduct', event)">
            <span style="display:flex;align-items:center;gap:6px">
                <svg class="f-icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M6 2L3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4z"></path><line x1="3" y1="6" x2="21" y2="6"></line><path d="M16 10a4 4 0 0 1-8 0"></path></svg>
                Sản phẩm: <strong id="lblProduct" style="color:var(--navy);max-width:140px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">Tất cả</strong>
            </span>
            <svg class="clear-x" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" onclick="clearFilter('product', event)"><line x1="18" y1="6" x2="6" y2="18"></line><line x1="6" y1="6" x2="18" y2="18"></line></svg>
        </button>
        <div id="ddProduct" class="op-dropdown right" style="min-width:240px">
            <button class="selected" onclick="selectProduct('all')">Tất cả sản phẩm</button>
            <%-- populated dynamically --%>
        </div>
    </div>

    <%-- Filter 3: Đơn vị vận chuyển (populated by JS from shipmentProvidersJson) --%>
    <div style="position:relative">
        <button class="op-filter-btn" onclick="toggleDropdown('ddCarrier', event)">
            <span style="display:flex;align-items:center;gap:6px">
                <svg class="f-icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="1" y="3" width="15" height="13"></rect><polygon points="16 8 20 8 23 11 23 16 16 16 16 8"></polygon><circle cx="5.5" cy="18.5" r="2.5"></circle><circle cx="18.5" cy="18.5" r="2.5"></circle></svg>
                ĐVVC: <strong id="lblCarrier" style="color:var(--navy)">Tất cả</strong>
            </span>
            <svg class="clear-x" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" onclick="clearFilter('carrier', event)"><line x1="18" y1="6" x2="6" y2="18"></line><line x1="6" y1="6" x2="18" y2="18"></line></svg>
        </button>
        <div id="ddCarrier" class="op-dropdown right">
            <button class="selected" onclick="selectCarrier('all')">Tất cả ĐVVC</button>
            <%-- populated dynamically by initCarrierDropdown() --%>
        </div>
    </div>

    <%-- Filter 4: Thời gian đóng gói (Chỉ hiện ở Tab Chờ bàn giao ĐVVC) --%>
    <div style="position:relative;display:none" id="opTimeFilterContainer">
        <button class="op-filter-btn" onclick="toggleDropdown('ddTime', event)">
            <span style="display:flex;align-items:center;gap:6px">
                <svg class="f-icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="4" width="18" height="18" rx="2" ry="2"></rect><line x1="16" y1="2" x2="16" y2="6"></line><line x1="8" y1="2" x2="8" y2="6"></line><line x1="3" y1="10" x2="21" y2="10"></line></svg>
                Đóng gói: <strong id="lblTime" style="color:var(--navy)">Tất cả</strong>
            </span>
            <svg class="clear-x" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" onclick="clearFilter('time', event)"><line x1="18" y1="6" x2="6" y2="18"></line><line x1="6" y1="6" x2="18" y2="18"></line></svg>
        </button>
        <div id="ddTime" class="op-dropdown right">
            <button class="selected" onclick="selectTime('all')">Tất cả thời gian</button>
            <button onclick="selectTime('today')">Hôm nay</button>
            <button onclick="selectTime('yesterday')">Hôm qua</button>
            <button onclick="selectTime('7days')">7 ngày qua</button>
        </div>
    </div>
</div>

<%-- ── ENTERPRISE DATA GRID/TABLE ── --%>
<div class="op-table-card">
    <div class="op-table-scroll">
        <table class="op-table">
            <thead>
                <tr id="opTableHeader">
                    <%-- Populated by JS dynamic header columns --%>
                </tr>
            </thead>
            <tbody id="opTableBody">
                <%-- Populated by JS rows --%>
            </tbody>
        </table>
    </div>
    <div class="op-table-footer" id="opTableFooter">
        Hiển thị 0 / 0 đơn hàng
    </div>
</div>

<%-- ── ENTERPRISE DETAIL MODAL ── --%>
<div class="op-modal-overlay" id="opDetailModalOverlay" onclick="closeDetailModal()">
    <div class="op-modal" onclick="event.stopPropagation()">
        <div class="op-modal-header">
            <div style="display:flex;align-items:center;gap:12px">
                <span class="op-modal-title" id="mdTitle">CHI TIẾT ĐƠN HÀNG</span>
                <span class="badge-channel" id="mdChannel" style="background:#ee4d2d">Shopee</span>
                <span class="op-badge" id="mdWarehouse" style="background:var(--alice);color:var(--navy);border-color:#E5EAF3">
                    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="width:12px;height:12px"><path d="M3 21h18"></path><path d="M5 21V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v16"></path><path d="M9 9h1"></path><path d="M9 13h1"></path><path d="M14 9h1"></path><path d="M14 13h1"></path></svg>
                    <span id="mdWarehouseName">Chưa chỉ định</span>
                </span>
            </div>
            <button class="op-modal-close" onclick="closeDetailModal()">&times;</button>
        </div>

        <div class="op-modal-body">
            <%-- PART 1: CLIENT INFO --%>
            <div class="op-section-box">
                <div class="op-section-title">
                    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path><circle cx="12" cy="7" r="4"></circle></svg>
                    Phần 1: Thông tin khách hàng &amp; Vận chuyển
                </div>
                <div class="op-info-grid">
                    <div>
                        <div class="op-info-row">
                            <span class="op-info-label">Người nhận:</span>
                            <span class="op-info-val" id="mdCustName">-</span>
                        </div>
                        <div class="op-info-row">
                            <span class="op-info-label">Điện thoại:</span>
                            <span class="op-info-val" id="mdCustPhone" style="font-family:monospace">-</span>
                        </div>
                        <div class="op-info-row" style="align-items:flex-start">
                            <span class="op-info-label">Địa chỉ giao:</span>
                            <span class="op-info-val" id="mdCustAddr">-</span>
                        </div>
                    </div>
                    <div>
                        <div class="op-info-row">
                            <span class="op-info-label">Đơn vị vận chuyển:</span>
                            <span class="op-info-val" id="mdCarrierName">-</span>
                        </div>
                        <div class="op-info-row">
                            <span class="op-info-label">Mã vận đơn:</span>
                            <span class="op-info-val" id="mdTrackingNo" style="font-family:monospace">-</span>
                        </div>
                        <div class="op-info-row">
                            <span class="op-info-label">Thời gian đồng bộ:</span>
                            <span class="op-info-val" id="mdSyncTime" style="font-family:monospace">-</span>
                        </div>
                    </div>
                </div>
            </div>

            <%-- PART 2: PRODUCT LIST & WAREHOUSE CROSS-STOCK LOOKUP --%>
            <div>
                <div class="op-section-title" style="margin-bottom:0.75rem">
                    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="2" y="2" width="20" height="8" rx="2" ry="2"></rect><rect x="2" y="14" width="20" height="8" rx="2" ry="2"></rect><line x1="6" y1="6" x2="6.01" y2="6"></line><line x1="6" y1="18" x2="6.01" y2="18"></line></svg>
                    Phần 2: Danh sách sản phẩm &amp; Kiểm tra tồn kho chéo nhánh (Cross-branch Inventory)
                </div>
                <div id="mdProductList">
                    <%-- Rendered by JS --%>
                </div>
            </div>

            <%-- PART 3: ACTION PROGRESS TIMELINE --%>
            <div>
                <div class="op-section-title" style="margin-bottom:1rem">
                    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="22 12 18 12 15 21 9 3 6 12 2 12"></polyline></svg>
                    Phần 3: Tiến trình xử lý (Timeline)
                </div>
                <div class="op-timeline" id="mdTimeline">
                    <%-- Rendered dynamically by JS --%>
                </div>
            </div>

            <%-- PART 4: TAB-SPECIFIC ACTION PANELS --%>
            <div id="mdActionPanelContainer">
                <%-- Rendered dynamically by JS (e.g. Approve Form or RMA Form) --%>
            </div>
        </div>

        <div class="op-modal-footer">
            <button class="op-btn" style="background:#fff;border-color:#E5EAF3;color:rgba(16,55,92,.6)" onclick="closeDetailModal()">Đóng</button>
        </div>
    </div>
</div>



<%-- ── TOAST NOTIFICATIONS POPUP ── --%>
<div class="op-toast" id="opToast">
    <svg id="opToastIcon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" style="width:16px;height:16px"><circle cx="12" cy="12" r="10"></circle><polyline points="12 6 12 12 16 14"></polyline></svg>
    <span id="opToastMsg">Thông báo hệ thống</span>
</div>

<%-- Hidden container holding JSON generated by JSTL to prevent IDE JS parser errors --%>
<div id="orderDataContainer" style="display:none;">[
    <c:forEach var="order" items="${orderList}" varStatus="status">
    <c:set var="totalQty" value="0"/>
    <c:forEach var="item" items="${order.items}">
        <c:set var="totalQty" value="${totalQty + item.quantity}"/>
    </c:forEach>
    {
        "id": "${fn:escapeXml(order.orderCode)}",
        "channel": "${order.channel == 'ONLINE' ? 'Lazada' : fn:escapeXml(order.channel)}",
        "customerName": "${fn:escapeXml(order.customerName)}",
        "customerPhone": "${fn:escapeXml(order.customerPhone)}",
        "customerAddress": "${fn:escapeXml(order.customerAddress)}",
        "totalItems": ${totalQty},
        "totalAmount": ${order.totalAmount},
        "status": "${order.status == 'PENDING' ? 'pending_review' : (order.status == 'CONFIRMED' ? 'confirmed' : (order.status == 'PICKING' ? 'confirmed' : (order.status == 'PACKED' ? 'packed' : (order.status == 'SHIPPED' ? 'shipping' : (order.status == 'DELIVERED' ? 'delivered' : (order.status == 'COMPLETED' ? 'completed' : (order.status == 'RETURNED' ? 'returned' : (order.status == 'DISPUTED' ? 'disputed' : (order.status == 'DISPUTE_SUCCESS' ? 'dispute_success' : (order.status == 'CANCELLED' ? 'cancelled' : order.status.toLowerCase()))))))))))}",
        "warehouse": "${fn:escapeXml(order.warehouseName)}",
        "webOrderRef": "${fn:escapeXml(order.webOrderRef)}",
        "trackingNo": "${fn:escapeXml(order.trackingNo)}",
        "shipmentProvider": "${fn:escapeXml(order.shipmentProvider)}",
        "reviewNote": "${fn:escapeXml(order.reviewNote)}",
        "rmaReason": "${fn:escapeXml(order.rmaReason)}",
        "rmaPhysicalStatus": "${fn:escapeXml(order.rmaPhysicalStatus)}",
        "rmaPlatformStatus": "${fn:escapeXml(order.rmaPlatformStatus)}",
        "disputeEvidenceVideo": "${fn:escapeXml(order.disputeEvidenceVideo)}",
        "disputeNote": "${fn:escapeXml(order.disputeNote)}",
        "createdAt": "${order.createdAt}",
        "updatedAt": "${order.updatedAt}",
        "items": [
            <c:forEach var="item" items="${order.items}" varStatus="itemStatus">
            {
                "sku": "${fn:escapeXml(item.skuCode)}",
                "name": "${fn:escapeXml(item.productName)}",
                "quantity": ${item.quantity},
                "price": ${item.unitPrice}
            }${!itemStatus.last ? ',' : ''}
            </c:forEach>
        ]
    }${!status.last ? ',' : ''}
    </c:forEach>
]</div>

<script>
// ── CONSTANTS & GLOBALS ──────────────────────────────────────────────
const WAREHOUSES = [];
try {
    const rawWarehousesJson = '<c:out value="${warehousesJson}" escapeXml="false"/>';
    if (rawWarehousesJson && rawWarehousesJson.trim() && rawWarehousesJson.indexOf('warehousesJson') === -1) {
        const parsedWarehouses = JSON.parse(rawWarehousesJson);
        parsedWarehouses.forEach(function(warehouse) {
            WAREHOUSES.push({
                name: warehouse.warehouseName,
                code: warehouse.warehouseCode
            });
        });
    }
} catch (e) {}
if (WAREHOUSES.length === 0) {
    WAREHOUSES.push(
        { name: "Kho Hà Nội", code: "WH-HN" },
        { name: "Kho TP.HCM", code: "WH-HCM" },
        { name: "Kho Đà Nẵng", code: "WH-DN" }
    );
}

const STATUS_CONFIG = {
    pending_review: { label: "Chờ duyệt", bg: "background:#fee2e2", text: "color:#dc2626;border-color:#fca5a5", dot: "background:#ef4444" },
    confirmed: { label: "Chờ xử lý", bg: "background:#fef3c7", text: "color:#d97706;border-color:#fcd34d", dot: "background:#f59e0b" },
    packing: { label: "Đang đóng gói", bg: "background:#f3e8ff", text: "color:#7e22ce;border-color:#d8b4fe", dot: "background:#a855f7" },
    packed: { label: "Đã đóng gói", bg: "background:#ccfbf1", text: "color:#0f766e;border-color:#99f6e4", dot: "background:#0d9488" },
    shipping: { label: "Đang giao", bg: "background:#dbeafe", text: "color:#2563eb;border-color:#bfdbfe", dot: "background:#3b82f6" },
    delivered: { label: "Đã giao", bg: "background:#d1fae5", text: "color:#059669;border-color:#6ee7b7", dot: "background:#10b981" },
    completed: { label: "Hoàn thành", bg: "background:#dcfce7", text: "color:#15803d;border-color:#86efac", dot: "background:#16a34a" },
    returned: { label: "Trả hàng", bg: "background:#f3f4f6", text: "color:#4b5563;border-color:#d1d5db", dot: "background:#6b7280" },
    disputed: { label: "Đang khiếu nại", bg: "background:#fef3c7", text: "color:#b45309;border-color:#fcd34d", dot: "background:#f59e0b" },
    dispute_success: { label: "Khiếu nại thành công", bg: "background:#d1fae5", text: "color:#047857;border-color:#6ee7b7", dot: "background:#10b981" },
    cancelled: { label: "Đã hủy", bg: "background:#ffe4e6", text: "color:#be123c;border-color:#fecdd3", dot: "background:#f43f5e" }
};

let allOrders = [];
const orderDataElem = document.getElementById("orderDataContainer");
if (orderDataElem) {
    try {
        allOrders = JSON.parse(orderDataElem.textContent.trim());
    } catch(e) {
        console.error("Failed to parse orders data:", e);
    }
}
let activeTab = "pending_review";
let searchQuery = "";
let selectedChannel = "all";
let selectedProduct = "all";
let selectedCarrier = "all";
let selectedTime = "all";

let activeDetailOrder = null;

// ── INIT DOMContentLoaded ───────────────────────────────────────────
document.addEventListener("DOMContentLoaded", function() {
    loadOrdersFromStorage();

    // __whStockCache is populated automatically by initStockCacheFromServlet() IIFE above.
    // loadInventoryStock() is called by that IIFE as a secondary refresh after DOMContentLoaded.
    // Carriers dropdown is also populated from embedded data.
    initCarrierDropdown();
    
    // React interop sync — NOTE: do NOT call loadOrdersFromStorage() here.
    // loadOrdersFromStorage() reads from the server-rendered DOM (#orderDataContainer),
    // which still holds the original PENDING data. Calling it after an in-memory
    // approve/reject would overwrite allOrders and revert the status change.
    // The event is only used to trigger a re-render for cross-component sync.
    window.addEventListener("ORDER_STORE_UPDATED", function() {
        renderAll();
    });

    // Populate products dropdown based on SKU mapping or present orders
    buildProductDropdown();
    
    // Check initial search params
    const urlParams = new URLSearchParams(window.location.search);
    const tabParam = urlParams.get("tab");
    if (tabParam && ["pending_review", "pending_waybill", "pending_rts", "rma_dispute", "cancelled"].includes(tabParam)) {
        activeTab = tabParam;
    }

    renderAll();
    
    // Global body click to hide dropdowns
    document.addEventListener("click", function() {
        hideAllDropdowns();
    });
});

function loadOrdersFromStorage() {
    const orderDataElem = document.getElementById("orderDataContainer");
    if (orderDataElem) {
        try {
            allOrders = JSON.parse(orderDataElem.textContent.trim());
            // Sync to local storage for any listening components
            localStorage.setItem("b2c_orders_v2", JSON.stringify(allOrders));
        } catch(e) {
            console.error("Failed to parse orders data from JSTL container:", e);
        }
    }
}

function saveOrdersToStorage() {
    localStorage.setItem("b2c_orders_v2", JSON.stringify(allOrders));
    // Trigger event for cross-component sync
    window.dispatchEvent(new CustomEvent("ORDER_STORE_UPDATED"));
}

function postOrderAction(params, callback) {
    const urlParams = new URLSearchParams();
    for (const key in params) {
        urlParams.append(key, params[key]);
    }
    fetch(window.location.origin + "${pageContext.request.contextPath}/sales/order-action", {
        method: "POST",
        headers: {
            "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8"
        },
        body: urlParams.toString()
    })
    .then(response => {
        if (!response.ok) {
            throw new Error("HTTP error " + response.status);
        }
        return response.json();
    })
    .then(data => {
        if (data.success) {
            callback(null, data);
        } else {
            callback(data.message || "Đã xảy ra lỗi khi cập nhật.");
        }
    })
    .catch(error => {
        console.error("Error posting order action:", error);
        callback("Lỗi kết nối tới máy chủ.");
    });
}

function getWarehouseStock(sku, wname) {
    // 1) Prefer the real-time server cache populated by loadInventoryStock()
    if (window.__whStockCache && window.__whStockCache[sku] && window.__whStockCache[sku][wname] !== undefined) {
        return window.__whStockCache[sku][wname];
    }

    // 2) Fallback: legacy localStorage cache populated by channel-products page
    var ps = JSON.parse(localStorage.getItem('wh_pricing_sales') || '[]');
    var record = ps.find(function(p) { return p.sku === sku; });
    if (!record) return 0;
    
    if (record.warehouseStock && record.warehouseStock[wname] !== undefined) {
        return record.warehouseStock[wname];
    }
    
    var totalQty = record.qtyAvailable !== undefined ? record.qtyAvailable : (record.qtyOnHand || 0);
    if (wname === "Kho Hà Nội" || wname.indexOf("Hà Nội") > -1) {
        return Math.floor(totalQty * 0.6);
    }
    if (wname === "Kho TP.HCM" || wname === "Kho TP. Hồ Chí Minh" || wname.indexOf("HCM") > -1) {
        return Math.floor(totalQty * 0.3);
    }
    if (wname === "Kho Đà Nẵng" || wname.indexOf("Đà Nẵng") > -1) {
        return Math.max(0, totalQty - Math.floor(totalQty * 0.6) - Math.floor(totalQty * 0.3));
    }
    return 0;
}

/**
 * Fetches live inventory stock per SKU per warehouse from the server and
 * caches it on window.__whStockCache. The order detail modal uses this cache
 * to display real cross-branch stock instead of zeros.
 *
 * Format on window.__whStockCache:
 *   {
 *     "TSH-NAM-001": { "Kho Hà Nội": 200, "Kho TP. Hồ Chí Minh": 180, ... },
 *     "SUN-SCRE-007": { ... }
 *   }
 */
// Populate __whStockCache from JSP-embedded lazadaOrdersJson so the detail modal
// always has real stock data even if /sales/inventory-stock fetch fails or returns 0.
// The lazadaOrdersJson attribute is set by SalesOrderProcessingServlet.
(function initStockCacheFromServlet() {
    var raw = '<c:out value="${lazadaOrdersJson}" escapeXml="false"/>';
    if (!raw || raw.indexOf('lazadaOrdersJson') > -1 || raw.trim() === '') return;
    try {
        var lazOrders = JSON.parse(raw);
        var cache = {};
        lazOrders.forEach(function(order) {
            if (!order.items) return;
            order.items.forEach(function(item) {
                if (!item.wmsSku) return;
                if (!cache[item.wmsSku]) cache[item.wmsSku] = {};
                // Parse "Kho Hà Nội:150.000, Kho TP.HCM:0.000" style string
                if (item.warehouseStocks) {
                    var parts = item.warehouseStocks.split(',');
                    parts.forEach(function(part) {
                        var colonIdx = part.lastIndexOf(':');
                        if (colonIdx < 0) return;
                        var whName = part.substring(0, colonIdx).trim();
                        var qtyStr = part.substring(colonIdx + 1).trim();
                        var qty = parseFloat(qtyStr);
                        if (!isNaN(qty) && whName) {
                            cache[item.wmsSku][whName] = qty;
                        }
                    });
                }
            });
        });
        window.__whStockCache = cache;
        console.log('[order-processing] Stock cache initialized from lazadaOrdersJson:', cache);
    } catch (e) {
        console.warn('[order-processing] Failed to init stock cache from lazadaOrdersJson:', e);
    }
})();

// ── Carrier dropdown init (populated from JSP-embedded shipmentProvidersJson) ─────
function initCarrierDropdown() {
    var raw = '<c:out value="${shipmentProvidersJson}" escapeXml="false"/>';
    if (!raw || raw.indexOf('shipmentProvidersJson') > -1 || raw.trim() === '') return;
    try {
        var providers = JSON.parse(raw);
        if (!Array.isArray(providers) || providers.length === 0) return;
        var container = document.getElementById('ddCarrier');
        if (!container) return;
        // Keep "Tất cả ĐVVC" button, append provider buttons after it
        var defaultBtn = container.querySelector('button.selected');
        container.innerHTML = '';
        if (defaultBtn) container.appendChild(defaultBtn);
        providers.forEach(function(p) {
            var name = p.providerNameVn || p.providerName || p.providerCode;
            var btn = document.createElement('button');
            btn.textContent = name;
            btn.onclick = (function(n) { return function() { selectCarrier(n); }; })(name);
            container.appendChild(btn);
        });
        console.log('[order-processing] Carrier dropdown initialized:', providers);
    } catch (e) {
        console.warn('[order-processing] Failed to init carrier dropdown:', e);
    }
}

// Fetch live stock from server as secondary update (MERGE into existing cache, don't replace)
(function loadInventoryStock() {
    fetch(window.location.origin + '${pageContext.request.contextPath}/sales/inventory-stock', {
        method: 'GET',
        headers: { 'Accept': 'application/json' }
    })
    .then(function(resp) { return resp.json(); })
    .then(function(data) {
        // Only merge if server returns meaningful data; don't overwrite with empty array
        if (!data || !data.success || !Array.isArray(data.stocks) || data.stocks.length === 0) return;
        data.stocks.forEach(function(s) {
            if (!s.sku || !s.warehouseName) return;
            if (!window.__whStockCache) window.__whStockCache = {};
            if (!window.__whStockCache[s.sku]) window.__whStockCache[s.sku] = {};
            var qty = (s.qtyAvailable !== undefined && s.qtyAvailable !== null)
                ? Number(s.qtyAvailable)
                : Number(s.qtyOnHand || 0);
            window.__whStockCache[s.sku][s.warehouseName] = qty;
        });
        console.log('[order-processing] Inventory stock cache merged from server:', window.__whStockCache);

        // Re-render any currently-open order detail modal so the totals update
        if (typeof renderAll === 'function') {
            try { renderAll(); } catch (e) { /* ignore */ }
        }
    })
    .catch(function(err) {
        console.warn('[order-processing] Failed to load inventory stock from server (using embedded data):', err);
    });
})();

function resolvePhysicalItems(itemSku, itemQuantity) {
    const stored = localStorage.getItem("sku_raw_mappings_v2");
    if (!stored) return [{ sku: itemSku, name: null, quantity: itemQuantity, conversionRate: 1, isComboSplit: false }];
    try {
        const mappings = JSON.parse(stored);
        const relations = mappings.filter(m => m.channelSKU === itemSku);
        if (relations.length === 0) {
            return [{ sku: itemSku, name: null, quantity: itemQuantity, conversionRate: 1, isComboSplit: false }];
        }
        return relations.map(m => ({
            sku: m.masterSKU,
            name: m.masterName,
            quantity: itemQuantity * m.conversionRate,
            conversionRate: m.conversionRate,
            isComboSplit: true
        }));
    } catch (e) {
        console.error(e);
        return [{ sku: itemSku, name: null, quantity: itemQuantity, conversionRate: 1, isComboSplit: false }];
    }
}

function getShippingCarrierOfOrder(order) {
    // Prefer real shipment_provider from DB if present, otherwise derive from channel
    var sp = order.shipmentProvider || order.shipment_provider;
    if (sp && sp.trim()) {
        // Normalize Lazada's provider codes to display names
        if (sp.indexOf('Lazada') > -1 || sp.indexOf('LEX') > -1) return 'Lazada Express';
        if (sp.indexOf('SPX') > -1 || sp.indexOf('Spx') > -1) return 'SPX Express';
        if (sp.indexOf('TikTok') > -1) return 'TikTok Express';
        if (sp.indexOf('Viettel') > -1) return 'Viettel Post';
        return sp;
    }
    // Fallback: derive from channel
    if (order.channel === 'Lazada') return 'Lazada Express';
    if (order.channel === 'Shopee') return 'SPX Express';
    if (order.channel === 'TikTok') return 'TikTok Express';
    return 'Viettel Post';
}

function getOrderShippingCarrier(order) {
    return getShippingCarrierOfOrder(order);
}

// ── TOAST HELPER ─────────────────────────────────────────────────────
function showToast(msg, type = "info") {
    const toast = document.getElementById("opToast");
    const label = document.getElementById("opToastMsg");
    const icon = document.getElementById("opToastIcon");
    
    toast.className = "op-toast " + type;
    label.textContent = msg;
    
    // Update SVG icon
    if (type === "success") {
        icon.innerHTML = `<path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path><polyline points="22 4 12 14.01 9 11.01"></polyline>`;
    } else if (type === "error") {
        icon.innerHTML = `<circle cx="12" cy="12" r="10"></circle><line x1="15" y1="9" x2="9" y2="15"></line><line x1="9" y1="9" x2="15" y2="15"></line>`;
    } else {
        icon.innerHTML = `<circle cx="12" cy="12" r="10"></circle><line x1="12" y1="16" x2="12" y2="12"></line><line x1="12" y1="8" x2="12.01" y2="8"></line>`;
    }
    
    toast.classList.add("open");
    setTimeout(() => {
        toast.classList.remove("open");
    }, 4500);
}

// ── RENDER & SWITCH TAB ──────────────────────────────────────────────
function switchTab(tabId) {
    activeTab = tabId;
    hideAllDropdowns();
    
    // Sync URL parameter
    const url = new URL(window.location);
    url.searchParams.set("tab", tabId);
    window.history.pushState({}, '', url);
    
    // Toggle active tab class
    document.querySelectorAll(".op-tab").forEach(btn => btn.classList.remove("active"));
    if (tabId === "pending_review") document.getElementById("tabReview").classList.add("active");
    if (tabId === "pending_waybill") document.getElementById("tabWaybill").classList.add("active");
    if (tabId === "pending_rts") document.getElementById("tabRTS").classList.add("active");
    if (tabId === "rma_dispute") document.getElementById("tabRMA").classList.add("active");
    if (tabId === "cancelled") document.getElementById("tabCancelled").classList.add("active");
    
    // Toggle time filter visibility (only in RTS tab)
    const timeFilter = document.getElementById("opTimeFilterContainer");
    if (tabId === "pending_rts") {
        timeFilter.style.display = "block";
    } else {
        timeFilter.style.display = "none";
        selectedTime = "all";
        document.getElementById("lblTime").textContent = "Tất cả";
    }
    
    renderAll();
}

function renderAll() {
    renderTabBadges();
    renderTableHeader();
    renderTableBody();
}

function renderTabBadges() {
    const reviewCnt = allOrders.filter(o => o.status === "pending_review").length;
    const waybillCnt = allOrders.filter(o => o.status === "confirmed").length;
    const rtsCnt = allOrders.filter(o => o.status === "packed").length;
    const rmaCnt = allOrders.filter(o => {
        return (o.status === "returned" || o.status === "disputed" || o.status === "dispute_success") 
            && o.rmaPhysicalStatus === "Đã nhập Zone Khiếu Nại";
    }).length;
    const cancelledCnt = allOrders.filter(o => o.status === "cancelled").length;
    
    document.getElementById("badgeReview").textContent = reviewCnt;
    document.getElementById("badgeWaybill").textContent = waybillCnt;
    document.getElementById("badgeRTS").textContent = rtsCnt;
    document.getElementById("badgeRMA").textContent = rmaCnt;
    document.getElementById("badgeCancelled").textContent = cancelledCnt;
}

// ── DROPDOWNS ────────────────────────────────────────────────────────
function toggleDropdown(ddId, event) {
    event.stopPropagation();
    const dropdown = document.getElementById(ddId);
    const isOpen = dropdown.classList.contains("open");
    hideAllDropdowns();
    if (!isOpen) dropdown.classList.add("open");
}

function hideAllDropdowns() {
    document.querySelectorAll(".op-dropdown").forEach(dd => dd.classList.remove("open"));
}

function buildProductDropdown() {
    const drop = document.getElementById("ddProduct");
    // Clear dynamic options
    while (drop.childNodes.length > 2) {
        drop.removeChild(drop.lastChild);
    }
    
    // Find all distinct item names
    const names = [];
    allOrders.forEach(o => {
        if (o.items) {
            o.items.forEach(i => {
                if (i.name && names.indexOf(i.name) === -1) {
                    names.push(i.name);
                }
            });
        }
    });
    
    names.forEach(name => {
        const btn = document.createElement("button");
        btn.textContent = name;
        btn.onclick = () => selectProduct(name);
        drop.appendChild(btn);
    });
}

function selectChannel(val) {
    selectedChannel = val;
    document.getElementById("lblChannel").textContent = val === "all" ? "Tất cả" : val;
    // highlight selected option
    document.querySelectorAll("#ddChannel button").forEach(btn => {
        btn.className = (btn.textContent.indexOf(val) > -1 || (val === "all" && btn.textContent.indexOf("Tất cả") > -1)) ? "selected" : "";
    });
    renderAll();
}

function selectProduct(val) {
    selectedProduct = val;
    const labelText = val === "all" ? "Tất cả" : (val.length > 15 ? val.slice(0, 15) + "..." : val);
    document.getElementById("lblProduct").textContent = labelText;
    document.querySelectorAll("#ddProduct button").forEach(btn => {
        btn.className = (btn.textContent === val || (val === "all" && btn.textContent.indexOf("Tất cả") > -1)) ? "selected" : "";
    });
    renderAll();
}

function selectCarrier(val) {
    selectedCarrier = val;
    document.getElementById("lblCarrier").textContent = val === "all" ? "Tất cả" : val;
    document.querySelectorAll("#ddCarrier button").forEach(btn => {
        btn.className = (btn.textContent.indexOf(val) > -1 || (val === "all" && btn.textContent.indexOf("Tất cả") > -1)) ? "selected" : "";
    });
    renderAll();
}

function selectTime(val) {
    selectedTime = val;
    let label = "Tất cả";
    if (val === "today") label = "Hôm nay";
    if (val === "yesterday") label = "Hôm qua";
    if (val === "7days") label = "7 ngày qua";
    document.getElementById("lblTime").textContent = label;
    document.querySelectorAll("#ddTime button").forEach(btn => {
        btn.className = (btn.textContent.indexOf(label) > -1 || (val === "all" && btn.textContent.indexOf("Tất cả") > -1)) ? "selected" : "";
    });
    renderAll();
}

function clearFilter(type, event) {
    event.stopPropagation();
    if (type === "channel") selectChannel("all");
    if (type === "product") selectProduct("all");
    if (type === "carrier") selectCarrier("all");
    if (type === "time") selectTime("all");
}

function onSearchInput(val) {
    searchQuery = val.trim();
    renderAll();
}

// ── DYNAMIC TABLE RENDER ─────────────────────────────────────────────
function getFilteredOrders() {
    return allOrders.filter(order => {
        // Tab mapping filter
        let matchTab = false;
        if (activeTab === "pending_review") {
            matchTab = order.status === "pending_review";
        } else if (activeTab === "pending_waybill") {
            matchTab = order.status === "confirmed";
        } else if (activeTab === "pending_rts") {
            matchTab = order.status === "packed";
        } else if (activeTab === "rma_dispute") {
            matchTab = (order.status === "returned" || order.status === "disputed" || order.status === "dispute_success") 
                && order.rmaPhysicalStatus === "Đã nhập Zone Khiếu Nại";
        } else if (activeTab === "cancelled") {
            matchTab = order.status === "cancelled";
        }
        
        if (!matchTab) return false;
        
        // Channel filter
        if (selectedChannel !== "all" && order.channel !== selectedChannel) return false;
        
        // Carrier filter
        if (selectedCarrier !== "all" && getShippingCarrierOfOrder(order) !== selectedCarrier) return false;
        
        // Time filter (updatedAt)
        if (activeTab === "pending_rts" && selectedTime !== "all") {
            const todayStr = new Date().toLocaleDateString("sv-SE"); // YYYY-MM-DD
            const yesterday = new Date();
            yesterday.setDate(yesterday.getDate() - 1);
            const yesterdayStr = yesterday.toLocaleDateString("sv-SE");
            
            const orderDateStr = order.updatedAt ? order.updatedAt.slice(0, 10) : "";
            if (selectedTime === "today" && orderDateStr !== todayStr) return false;
            if (selectedTime === "yesterday" && orderDateStr !== yesterdayStr) return false;
            if (selectedTime === "7days") {
                if (!orderDateStr) return false;
                const orderDate = new Date(orderDateStr);
                const diffTime = Math.abs(new Date().getTime() - orderDate.getTime());
                const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));
                if (diffDays > 7) return false;
            }
        }
        
        // Product filter
        if (selectedProduct !== "all") {
            const hasProd = order.items && order.items.some(i => i.name === selectedProduct);
            if (!hasProd) return false;
        }
        
        // Search query
        if (searchQuery) {
            const q = searchQuery.toLowerCase();
            const idMatch = order.id && order.id.toLowerCase().indexOf(q) > -1;
            const trackMatch = order.trackingNo && order.trackingNo.toLowerCase().indexOf(q) > -1;
            const nameMatch = order.customerName && order.customerName.toLowerCase().indexOf(q) > -1;
            const skuMatch = order.items && order.items.some(i => {
                return (i.sku && i.sku.toLowerCase().indexOf(q) > -1) || 
                       (i.name && i.name.toLowerCase().indexOf(q) > -1);
            });
            
            if (!idMatch && !trackMatch && !nameMatch && !skuMatch) return false;
        }
        
        return true;
    });
}

function renderTableHeader() {
    const header = document.getElementById("opTableHeader");
    
    let html = "";
    
    html += `<th style="width: 56px">STT</th>
    <th style="width: 144px">Mã đơn hàng</th>
    <th style="width: 128px">Kênh bán</th>
    <th style="width: 192px">Khách hàng</th>`;
    
    if (activeTab === "pending_waybill") {
        html += `<th style="width: 176px">Trạng thái Tracking</th>`;
    } else if (activeTab === "pending_rts") {
        html += `<th style="width: 144px">ĐVVC</th>
        <th style="width: 176px">Thời gian đóng gói</th>`;
    } else if (activeTab === "rma_dispute") {
        html += `<th style="width: 192px">Lý do khách trả</th>
        <th style="width: 144px">Trạng thái vật lý</th>
        <th style="width: 144px">Trạng thái Sàn</th>`;
    } else { // pending_review
        html += `<th style="width: 96px; text-align: right">Số lượng</th>
        <th style="width: 128px; text-align: right">Tổng tiền</th>
        <th style="width: 144px">Trạng thái</th>`;
    }
    
    html += `<th style="width: 160px">Kho xử lý</th>
    <th style="width: 96px; text-align: center">Chi tiết</th>`;
    
    header.innerHTML = html;
}

function renderTableBody() {
    const tbody = document.getElementById("opTableBody");
    const filtered = getFilteredOrders();
    
    
    if (filtered.length === 0) {
        tbody.innerHTML = '<tr>' +
            '<td colspan="' + (activeTab === 'pending_waybill' ? 8 : activeTab === 'pending_rts' ? 9 : activeTab === 'rma_dispute' ? 9 : 8) + '" class="op-empty">' +
                '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="18" height="18" rx="2" ry="2"></rect><line x1="9" y1="9" x2="15" y2="15"></line><line x1="15" y1="9" x2="9" y2="15"></line></svg>' +
                'Không tìm thấy đơn hàng nào khớp với bộ lọc' +
            '</td>' +
        '</tr>';
        document.getElementById("opTableFooter").textContent = "Hiển thị 0 / " + allOrders.length + " đơn hàng";
        return;
    }
    
    let html = "";
        filtered.forEach((order, idx) => {
        const cfg = STATUS_CONFIG[order.status] || { label: order.status, bg: "background:#e5eaf3", text: "color:#10375c", dot: "background:#10375c" };
        const carrier = getShippingCarrierOfOrder(order);
        const channelColor = order.channelColor || "#10375c";
        
        html += '<tr class="' + (order.status === 'pending_review' ? 'pending-row' : '') + '" onclick="openDetailModal(\'' + order.id + '\')" style="cursor:pointer">';
        
        html += '<td><span style="color:rgba(16,55,92,.4);font-weight:700;font-size:12px">' + (idx + 1) + '</span></td>' +
        '<td>' +
            '<div style="font-weight:700;font-family:monospace">' + order.id + '</div>' +
            (order.trackingNo ? '<div style="font-size:10px;color:rgba(16,55,92,.4);font-family:monospace;margin-top:2px">' + order.trackingNo + '</div>' : '<div style="font-size:9.5px;color:#d97706;font-style:italic;font-weight:700;margin-top:2px">Chưa cấp tracking</div>') +
        '</td>' +
        '<td>' +
            '<span class="badge-channel" style="background:' + channelColor + '">' +
                '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="width:11px;height:11px"><circle cx="12" cy="12" r="10"></circle><line x1="2" y1="12" x2="22" y2="12"></line><path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"></path></svg>' +
                order.channel +
            '</span>' +
        '</td>' +
        '<td>' +
            '<div style="font-weight:600">' + order.customerName + '</div>' +
            '<div style="font-size:11px;color:rgba(16,55,92,.4)">' + order.customerPhone + '</div>' +
        '</td>';
        
        if (activeTab === "pending_waybill") {
            html += '<td>' +
                (order.trackingNo ? 
                    '<span class="op-badge" style="background:#ecfdf5;color:#047857;border-color:#a7f3d0"><span class="op-badge-dot" style="background:#10b981"></span>ĐÃ CÓ: ' + order.trackingNo + '</span>'
                 : 
                    '<span class="op-badge" style="background:#fef2f2;color:#b91c1c;border-color:#fecaca"><span class="op-badge-dot" style="background:#ef4444"></span>YÊU CẦU SINH MÃ</span>'
                ) +
                '<div style="font-size:9.5px;color:rgba(16,55,92,.4);font-weight:700;margin-top:4px;display:flex;align-items:center;gap:3px">' +
                    '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="width:11px;height:11px"><rect x="1" y="3" width="15" height="13"></rect><polygon points="16 8 20 8 23 11 23 16 16 16 16 8"></polygon><circle cx="5.5" cy="18.5" r="2.5"></circle><circle cx="18.5" cy="18.5" r="2.5"></circle></svg>' +
                    'ĐVVC: ' + carrier +
                '</div>' +
            '</td>';
        } else if (activeTab === "pending_rts") {
            html += '<td>' +
                '<span style="font-weight:600;display:flex;align-items:center;gap:4px">' +
                    '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="width:12px;height:12px;color:rgba(16,55,92,.3)"><rect x="1" y="3" width="15" height="13"></rect><polygon points="16 8 20 8 23 11 23 16 16 16 16 8"></polygon><circle cx="5.5" cy="18.5" r="2.5"></circle><circle cx="18.5" cy="18.5" r="2.5"></circle></svg>' +
                    carrier +
                '</span>' +
            '</td>' +
            '<td>' +
                '<span style="font-weight:600;font-size:12px;display:flex;align-items:center;gap:4px">' +
                    '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="width:12px;height:12px;color:rgba(16,55,92,.3)"><circle cx="12" cy="12" r="10"></circle><polyline points="12 6 12 12 16 14"></polyline></svg>' +
                    order.updatedAt +
                '</span>' +
            '</td>';
        } else if (activeTab === "rma_dispute") {
            html += '<td><span style="font-weight:600">' + (order.rmaReason || "Chưa rõ lý do") + '</span></td>' +
            '<td>' +
                '<span class="op-badge" style="background:#fffbeb;color:#b45309;border-color:#fde68a">' +
                    (order.rmaPhysicalStatus || "Đã nhập Zone Khiếu Nại") +
                '</span>' +
            '</td>' +
            '<td>' +
                '<span class="op-badge" style="' + (order.status === 'dispute_success' ? 'background:#ecfdf5;color:#047857;border-color:#a7f3d0' : order.status === 'disputed' ? 'background:#eff6ff;color:#1d4ed8;border-color:#bfdbfe' : 'background:#f9fafb;color:#374151;border-color:#e5e7eb') + '">' +
                    (order.status === 'dispute_success' ? 'Bồi thường thành công' : order.status === 'disputed' ? 'Đang xử lý khiếu nại' : 'Chờ xử lý') +
                '</span>' +
            '</td>';
        } else {
            html += '<td style="text-align:right;font-weight:700">' + order.totalItems + '</td>' +
            '<td style="text-align:right;font-weight:800;font-family:monospace">' + order.totalAmount.toLocaleString() + 'đ</td>' +
            '<td>' +
                '<span class="op-badge" style="' + cfg.bg + ';' + cfg.text + '">' +
                    '<span class="op-badge-dot" style="' + cfg.dot + '"></span>' +
                    cfg.label +
                '</span>' +
            '</td>';
        }
        
        html += '<td>' +
            (order.warehouse ? 
                '<span style="font-weight:600;display:flex;align-items:center;gap:4px"><svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="width:12px;height:12px;color:rgba(16,55,92,.3)"><path d="M3 21h18"></path><path d="M5 21V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v16"></path></svg>' + order.warehouse + '</span>'
             : 
                '<span style="color:#d97706;font-style:italic;font-weight:600">Chưa chỉ định</span>'
            ) +
        '</td>' +
        '<td onclick="event.stopPropagation()">' +
            '<button class="op-btn-detail" onclick="openDetailModal(\'' + order.id + '\')" title="Xem chi tiết">' +
                '<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2"><path stroke-linecap="round" stroke-linejoin="round" d="M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0z"/><path stroke-linecap="round" stroke-linejoin="round" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z"/></svg>' +
            '</button>' +
        '</td>' +
        '</tr>';
    });
    
    tbody.innerHTML = html;
    document.getElementById("opTableFooter").textContent = "Hiển thị " + filtered.length + " / " + allOrders.length + " đơn hàng";
}

// ── DETAIL MODAL FUNCTIONS ───────────────────────────────────────────
function openDetailModal(id) {
    const order = allOrders.find(o => o.id === id);
    if (!order) return;
    
    activeDetailOrder = order;
    renderModal(order);
    document.getElementById("opDetailModalOverlay").classList.add("open");
}

function closeDetailModal() {
    document.getElementById("opDetailModalOverlay").classList.remove("open");
    activeDetailOrder = null;
}

function renderModal(order) {
    const channelColor = order.channelColor || "#10375c";
    
    // Header
    document.getElementById("mdTitle").textContent = "CHI TIẾT ĐƠN HÀNG: #" + order.id;
    
    const mdChan = document.getElementById("mdChannel");
    mdChan.textContent = "Kênh: " + order.channel;
    mdChan.style.background = channelColor;
    
    const mdWhName = document.getElementById("mdWarehouseName");
    mdWhName.textContent = order.warehouse || "Chưa chỉ định";
    
    // Info Columns
    document.getElementById("mdCustName").textContent = order.customerName || "-";
    document.getElementById("mdCustPhone").textContent = order.customerPhone || "-";
    document.getElementById("mdCustAddr").textContent = order.customerAddress || "-";
    
    document.getElementById("mdCarrierName").textContent = getShippingCarrierOfOrder(order);
    document.getElementById("mdTrackingNo").textContent = order.trackingNo || "Chưa cấp";
    document.getElementById("mdSyncTime").textContent = order.createdAt || "-";
    
    // Items & stock chéo
    const container = document.getElementById("mdProductList");
    let itemsHtml = "";
    
    if (order.items) {
        // Group items by SKU (Lazada sends one object per unit, e.g. qty=1 x3 instead of qty=3 x1)
        var groupedItems = {};
        order.items.forEach(function(item) {
            var key = item.sku || item.name || '';
            if (groupedItems[key]) {
                groupedItems[key].quantity += (item.quantity || 1);
            } else {
                groupedItems[key] = Object.assign({}, item, { quantity: item.quantity || 1 });
            }
        });

        Object.values(groupedItems).forEach(item => {
            const resolved = resolvePhysicalItems(item.sku, item.quantity);
            
            itemsHtml += '<div class="op-detail-item">' +
                '<div class="op-detail-item-header">' +
                    '<span class="op-detail-item-name">' +
                        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="width:14px;height:14px"><path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"></path></svg>' +
                        item.name +
                    '</span>' +
                    '<span style="font-size:12px;color:rgba(16,55,92,.6);font-weight:600">SL đặt trên sàn: <strong style="color:var(--navy);font-size:14px">x' + item.quantity + '</strong></span>' +
                '</div>' +
                '<div style="font-size:11px;color:rgba(16,55,92,.4);font-family:monospace;margin-bottom:8px">Mã SKU trên sàn: ' + item.sku + '</div>' +
                
                '<div style="padding-left:12px;border-left:2px solid rgba(16,55,92,.1)">';
            
            resolved.forEach(phy => {
                const totalStock = getWarehouseStock(phy.sku, "Kho Hà Nội") + getWarehouseStock(phy.sku, "Kho TP.HCM") + getWarehouseStock(phy.sku, "Kho Đà Nẵng");
                
                itemsHtml += '<div style="margin-bottom:12px">' +
                    '<div style="display:flex;justify-content:between;align-items:center;margin-bottom:6px;font-size:12.5px">' +
                        '<span style="display:flex;align-items:center;gap:4px">' +
                            '<span style="font-size:9.5px;padding:2px 4px;font-weight:800;border-radius:3px;' + (phy.isComboSplit ? 'background:#fef3c7;color:#d97706' : 'background:#dbeafe;color:#2563eb') + '">' + (phy.isComboSplit ? 'Combo quy đổi' : 'Sản phẩm đơn') + '</span>' +
                            '<strong>' + (phy.name || item.name) + '</strong>' +
                            '<span style="font-family:monospace;font-size:11px;color:rgba(16,55,92,.4)">(' + phy.sku + ')</span>' +
                        '</span>' +
                        '<span style="font-weight:700;margin-left:auto">SL quy đổi: <strong style="color:var(--orange)">' + phy.quantity + '</strong></span>' +
                    '</div>' +
                    
                    '<div class="op-stock-matrix">' +
                        '<div class="op-stock-box ' + (totalStock > 0 ? 'enough' : 'empty') + '">' +
                            '<span class="op-stock-box-label">Tổng khả dụng</span>' +
                            '<span class="op-stock-box-value">' + totalStock + ' chiếc</span>' +
                        '</div>';
                        
                WAREHOUSES.forEach(wh => {
                    const qty = getWarehouseStock(phy.sku, wh.name);
                    const sufficient = qty >= phy.quantity;
                    let boxClass = "empty";
                    if (qty > 0) {
                        boxClass = sufficient ? "enough" : "warning";
                    }
                    
                    itemsHtml += '<div class="op-stock-box ' + boxClass + '">' +
                        '<span class="op-stock-box-label">' + wh.name + '</span>' +
                        '<span class="op-stock-box-value">' +
                            (qty > 0 ? qty + ' chiếc' : 'Hết hàng') +
                            (sufficient ? '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="width:12px;height:12px"><polyline points="20 6 9 17 4 12"></polyline></svg>' : '') +
                        '</span>' +
                    '</div>';
                });
                
                itemsHtml += '</div></div>';
            });
            
            itemsHtml += '</div></div>';
        });
    }
    container.innerHTML = itemsHtml;
    
    // Timeline Action list
    const timeline = document.getElementById("mdTimeline");
    let timeHtml = "";
    
    // Dot Step 1: Synced
    timeHtml += '<div class="op-timeline-step">' +
        '<div class="op-timeline-step-dot active-ok">' +
            '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"></polyline></svg>' +
        '</div>' +
        '<div class="op-timeline-title">Đơn hàng ghi nhận từ sàn (Order Synced)</div>' +
        '<div class="op-timeline-desc">Đồng bộ thành công từ hệ thống kênh bán của ' + order.channel + '. Thời gian: ' + order.createdAt + '</div>' +
    '</div>';
    
    // Dot Step 2: Phê duyệt
    let dot2 = "inactive";
    let statusTitle = "Chờ phê duyệt và chỉ định kho (Duyệt tay)";
    let statusDesc = "Yêu cầu Sales Staff kiểm tra tồn kho chéo nhánh bên trên và chọn kho duyệt đơn";
    let dot2Icon = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><polyline points="12 6 12 12 16 14"></polyline></svg>';
    
    if (order.status !== "pending_review") {
        if (order.status === "cancelled") {
            dot2 = "active-err";
            statusTitle = "Đơn hàng bị từ chối / Hủy duyệt";
            statusDesc = 'Từ chối bởi Sales Staff lúc ' + order.updatedAt + '. Ghi chú: ' + (order.reviewNote || "Không có lý do");
            dot2Icon = '&times;';
        } else {
            dot2 = "active-ok";
            statusTitle = 'Đã duyệt & Phân bổ tồn kho tại ' + (order.warehouse || "Kho xuất hàng");
            statusDesc = 'Phê duyệt bởi Sales Staff lúc ' + order.updatedAt + '. Ghi chú: ' + (order.reviewNote || "Tự động phân bổ tồn kho thành công");
            dot2Icon = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"></polyline></svg>';
        }
    }
    
    timeHtml += '<div class="op-timeline-step">' +
        '<div class="op-timeline-step-dot ' + dot2 + '">' + dot2Icon + '</div>' +
        '<div class="op-timeline-title ' + (order.status === 'cancelled' ? 'error' : '') + '">' + statusTitle + '</div>' +
        '<div class="op-timeline-desc">' + statusDesc + '</div>' +
    '</div>';
    
    // Dot Step 3: Đóng gói
    if (order.status !== "cancelled") {
        const st3 = (order.status || "").toLowerCase();
        let dot3 = "inactive";
        let title3 = "Đóng gói hàng hóa (Pick & Pack)";
        let desc3 = "Chờ duyệt đơn để chuyển lệnh xuống kho";
        let dot3Icon = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><polyline points="12 6 12 12 16 14"></polyline></svg>';
        
        if (st3 === "confirmed") {
            dot3 = "active-warn";
            desc3 = "Đang chờ nhân viên kho nhặt hàng và đóng gói tem in";
        } else if (["packing", "packed", "shipping", "shipped", "delivered", "completed", "returned", "disputed", "dispute_success"].indexOf(st3) > -1) {
            dot3 = "active-ok";
            desc3 = 'Đóng gói hoàn tất lúc ' + order.updatedAt + ' tại ' + order.warehouse;
            dot3Icon = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"></polyline></svg>';
        }
        
        timeHtml += '<div class="op-timeline-step">' +
            '<div class="op-timeline-step-dot ' + dot3 + '">' + dot3Icon + '</div>' +
            '<div class="op-timeline-title">' + title3 + '</div>' +
            '<div class="op-timeline-desc">' + desc3 + '</div>' +
        '</div>';
    }
    
    // Dot Step 4: Giao hàng
    if (order.status !== "cancelled") {
        // Normalize status to lowercase so server-side ENUMs (SHIPPED, PACKED, PICKING...)
        // match client-side webhook flow constants (shipping, packed, packing, delivered...)
        const st = (order.status || "").toLowerCase();
        let dot4 = "inactive";
        let title4 = "Vận chuyển & Bàn giao";
        let desc4 = "Chờ đóng gói xong bàn giao vận chuyển";
        let dot4Icon = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><polyline points="12 6 12 12 16 14"></polyline></svg>';
        
        if (st === "shipping" || st === "shipped") {
            dot4 = "active-warn";
            title4 = "Đang giao hàng";
            desc4 = 'Đơn vị vận chuyển đang phát hàng đến tay khách hàng. Mã vận đơn: ' + (order.trackingNo || '—');
        } else if (st === "delivered") {
            dot4 = "active-warn";
            title4 = "Giao hàng thành công (Đang chờ đối soát ví)";
            desc4 = 'Đơn hàng đã được bưu tá phát thành công. Đang chờ hết thời hạn 3 ngày khiếu nại.';
            dot4Icon = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"></polyline></svg>';
            
        } else if (st === "completed") {
            dot4 = "active-ok";
            title4 = "Đơn hàng hoàn thành (Đã đối soát)";
            desc4 = 'Đơn hàng chính thức hoàn thành. Tiền đã giải ngân thành công vào ví bán hàng của doanh nghiệp.';
            dot4Icon = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"></polyline></svg>';
        } else if (["returned", "disputed", "dispute_success"].indexOf(st) > -1) {
            dot4 = "active-err";
            title4 = "Đơn hàng bị hoàn trả (Return & Refund)";
            desc4 = 'Hàng hoàn đã trả về kho. Trạng thái: "' + (order.rmaPhysicalStatus || 'Đã nhập Zone Khiếu Nại') + '". Lý do: "' + (order.rmaReason || 'Chưa rõ lý do') + '"';
            dot4Icon = '&times;';
        }
        
        timeHtml += '<div class="op-timeline-step">' +
            '<div class="op-timeline-step-dot ' + dot4 + '">' + dot4Icon + '</div>' +
            '<div class="op-timeline-title">' + title4 + '</div>' +
            '<div class="op-timeline-desc">' + desc4 + '</div>' +
        '</div>';
    }
    
    // Dot Step 5: RMA Dispute (Only for RMA/Disputed orders)
    if (order.status === "disputed" || order.status === "dispute_success") {
        timeHtml += '<div class="op-timeline-step">' +
            '<div class="op-timeline-step-dot active-ok">' +
                '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"></polyline></svg>' +
            '</div>' +
            '<div class="op-timeline-title">Đã gửi hồ sơ khiếu nại lên Sàn</div>' +
            '<div class="op-timeline-desc">Shop trích xuất video CCTCC đóng gói và gửi nội dung khiếu nại thành công.</div>' +
        '</div>';
    }
    if (order.status === "dispute_success") {
        timeHtml += '<div class="op-timeline-step">' +
            '<div class="op-timeline-step-dot active-ok">' +
                '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"></polyline></svg>' +
            '</div>' +
            '<div class="op-timeline-title" style="color:#047857">Khiếu nại thành công (Sàn đền bù 100%)</div>' +
            '<div class="op-timeline-desc">Sàn đối soát video và xác định lỗi do đơn vị vận chuyển. Tiền đền bù đã cộng vào ví người bán.</div>' +
        '</div>';
    }
    
    // Realtime Webhook Logs (if any)
    if (order.webhookEvents && order.webhookEvents.length > 0) {
        timeHtml += '<div style="border-top:1px solid #E5EAF3;padding-top:12px;margin-top:16px">' +
            '<div style="font-size:10px;font-weight:900;color:rgba(16,55,92,.4);text-transform:uppercase;margin-bottom:8px;display:flex;align-items:center;gap:6px">' +
                '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="width:12px;height:12px;color:#2563eb"><polyline points="22 12 18 12 15 21 9 3 6 12 2 12"></polyline></svg>' +
                'Nhật ký hành trình ĐVVC (Real-time Webhook Logs)' +
            '</div>' +
            '<div style="display:flex;flex-direction:column;gap:8px">';
            
        order.webhookEvents.forEach(evt => {
            timeHtml += '<div style="font-size:11px;background:var(--alice);border:1px solid rgba(16,55,92,.05);padding:8px;border-radius:4px;display:flex;gap:8px">' +
                '<div style="width:6px;height:6px;border-radius:50%;background:#2563eb;margin-top:5px;flex-shrink:0;animation:pulse 1s infinite"></div>' +
                '<div style="flex:1">' +
                    '<div style="display:flex;justify-content:between">' +
                        '<strong>' + evt.eventName + '</strong>' +
                        '<span style="font-family:monospace;font-size:10px;color:rgba(16,55,92,.4);margin-left:auto">' + evt.time + '</span>' +
                    '</div>' +
                    '<p style="font-size:10.5px;color:rgba(16,55,92,.6);margin-top:2px">' + evt.description + '</p>' +
                '</div>' +
            '</div>';
        });
        
        timeHtml += '</div></div>';
    }
    
    timeline.innerHTML = timeHtml;
    
    // Part 4: Dynamic Action Panel
    const actionContainer = document.getElementById("mdActionPanelContainer");
    let actionHtml = "";
    
    if (order.status === "pending_review") {
        actionHtml += '<div class="op-action-box">' +
            '<div class="op-action-title" style="color:#dc2626">' +
                '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="width:18px;height:18px"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"></path></svg>' +
                'Hành động: Phê duyệt đơn hàng &amp; Chỉ định kho vận hành' +
            '</div>' +
            '<div class="op-action-grid">' +
                '<div>' +
                    '<label class="op-field-label">Chọn Chi nhánh Kho xuất hàng *</label>' +
                    '<select class="op-select" id="actSelectWarehouse">' +
                        '<option value="">-- Chọn kho xuất hàng --</option>';
                        
        var physicalSkuRequired = {};
        if (order.items) {
            order.items.forEach(function(i) {
                var resolvedItems = resolvePhysicalItems(i.sku, i.quantity);
                resolvedItems.forEach(function(phy) {
                    physicalSkuRequired[phy.sku] = (physicalSkuRequired[phy.sku] || 0) + phy.quantity;
                });
            });
        }
                        
        WAREHOUSES.forEach(w => {
            let sufficient = true;
            for (var phySku in physicalSkuRequired) {
                const requiredQty = physicalSkuRequired[phySku];
                if (getWarehouseStock(phySku, w.name) < requiredQty) {
                    sufficient = false;
                    break;
                }
            }
            actionHtml += '<option value="' + w.name + '"' + (sufficient ? '' : ' disabled') + '>' + w.name + ' ' + (sufficient ? '(Đủ hàng)' : '(Thiếu hàng)') + '</option>';
        });
        
        actionHtml += '</select>' +
                '</div>' +
                '<div>' +
                    '<label class="op-field-label">Ghi chú phê duyệt / Từ chối lý do <span style="color:#ef4444">*</span></label>' +
                    '<textarea class="op-input" rows="2" placeholder="Ví dụ: Khách đặt nhầm SKU, yêu cầu hủy trước khi đóng gói..." id="actReviewNote" minlength="10"></textarea>' +
                    '<div style="font-size:0.75rem;color:#94a3b8;margin-top:4px">Tối thiểu 10 ký tự — bắt buộc khi bấm TỪ CHỐI.</div>' +
                '</div>' +
            '</div>' +
            '<div style="display:flex;justify-content:flex-end;gap:10px">' +
                '<button class="op-btn danger" onclick="submitApprove(false)">[ TỪ CHỐI ĐƠN ]</button>' +
                '<button class="op-btn success" onclick="submitApprove(true)">[ DUYỆT ĐƠN &amp; PHÂN BỔ KHO ]</button>' +
            '</div>' +
        '</div>';
    } else if (order.status === "shipping" && order.webOrderRef) {
        // Website order — no external platform webhook; Sales/Kho confirms delivery manually (mock shipper).
        actionHtml += '<div class="op-action-box" style="border-color:#10b981;background:rgba(16,185,129,.03)">' +
            '<div class="op-action-title" style="color:#059669">' +
                '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="width:18px;height:18px"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path><polyline points="22 4 12 14.01 9 11.01"></polyline></svg>' +
                'Đơn Website — Xác nhận giao hàng thủ công' +
            '</div>' +
            '<p style="font-size:12px;color:rgba(16,55,92,.6)">Đơn Website không có webhook từ sàn TMĐT. Khi đơn vị vận chuyển đã giao hàng thành công, bấm xác nhận bên dưới để bắt đầu tính cửa sổ 7 ngày hoàn trả cho khách.</p>' +
            '<div style="display:flex;justify-content:flex-end;margin-top:8px">' +
                '<button class="op-btn success" onclick="submitConfirmDelivered(\'' + order.id + '\')">[ XÁC NHẬN ĐÃ GIAO ]</button>' +
            '</div>' +
        '</div>';
    } else if (order.status === "shipping" || order.status === "delivered" || order.status === "packed") {
        // Render Webhook Simulator Actions
        actionHtml += '<div class="op-action-box" style="border-color:#10b981;background:rgba(16,185,129,.03)">' +
            '<div class="op-action-title" style="color:#059669">' +
                '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="width:18px;height:18px"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path><polyline points="22 4 12 14.01 9 11.01"></polyline></svg>' +
                'Giao hàng thành công — Chờ webhook từ Lazada cập nhật trạng thái' +
            '</div>' +
            '<p style="font-size:12px;color:rgba(16,55,92,.6)">Trạng thái vận chuyển được cập nhật tự động qua Webhook từ Lazada khi đơn hàng được bưu tá giao thành công. Thời gian đối soát ví: 3 ngày kể từ ngày giao hàng.</p>' +
        '</div>';
    } else if (order.rmaPhysicalStatus === "Đã nhập Zone Khiếu Nại") {
        // RMA Dispute form
        actionHtml += '<div class="op-action-box rma">' +
            '<div class="op-action-title" style="color:#d97706">' +
                '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="width:18px;height:18px"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"></path><line x1="12" y1="9" x2="12" y2="13"></line><line x1="12" y1="17" x2="12.01" y2="17"></line></svg>' +
                'Hồ Sơ Khiếu Nại RMA với Sàn TMĐT (Dispute Platform RMA)' +
            '</div>' +
            '<div class="op-info-grid" style="margin-bottom:12px">' +
                '<div style="background:#fff;border:1px solid #E5EAF3;padding:12px;border-radius:4px">' +
                    '<span style="font-size:11px;font-weight:700;color:rgba(16,55,92,.4);text-transform:uppercase;border-bottom:1px solid #F0F3FA;padding-bottom:4px;display:block;margin-bottom:8px">1. Minh chứng từ Khách hàng</span>' +
                    '<div style="font-size:12.5px">' +
                        '<strong>Lý do trả hàng:</strong>' +
                        '<p style="margin-top:4px;color:rgba(16,55,92,.8)">' + (order.rmaReason || 'Chưa có') + '</p>' +
                        (order.rmaCustomerImages && order.rmaCustomerImages.length > 0 ? 
                            '<div style="display:flex;gap:8px;margin-top:8px">' + 
                                order.rmaCustomerImages.map(img => '<img src="' + img + '" style="width:72px;height:72px;object-fit:cover;border:1px solid #E5EAF3;border-radius:3px" />').join('') + 
                            '</div>' 
                         : '') +
                    '</div>' +
                '</div>' +
                '<div style="background:#fff;border:1px solid #E5EAF3;padding:12px;border-radius:4px" id="mdDisputeShopPanel">' +
                    '<span style="font-size:11px;font-weight:700;color:rgba(16,55,92,.4);text-transform:uppercase;border-bottom:1px solid #F0F3FA;padding-bottom:4px;display:block;margin-bottom:8px">2. Hồ sơ khiếu nại của Shop (Video đóng gói)</span>';
                    
        if (order.status === "returned") {
            actionHtml += '<div style="display:flex;flex-direction:column;gap:8px">' +
                '<div>' +
                    '<label class="op-field-label">Tải lên bằng chứng video đóng gói (Video Packing) *</label>' +
                    '<input type="file" id="inpDisputeVideo" accept="video/*" style="font-size:12px;color:rgba(16,55,92,.7);margin-top:4px"/>' +
                    '<span style="font-size:9.5px;color:rgba(16,55,92,.4);margin-top:2px;display:block">* Trích xuất CCTCV tại bàn đóng gói của Warehouse Staff để làm bằng chứng gửi sàn.</span>' +
                '</div>' +
                '<div>' +
                    '<label class="op-field-label">Nội dung khiếu nại *</label>' +
                    '<textarea class="op-input" id="inpDisputeNote" rows="2" placeholder="VD: Shop đóng bọc xốp bóng khí 3 lớp đầy đủ, lỗi nứt vỡ do vận chuyển quăng quật..."></textarea>' +
                '</div>' +
                '<div style="display:flex;justify-content:flex-end;margin-top:4px">' +
                    '<button class="op-btn warning" onclick="submitRMADispute(\'' + order.id + '\')">[ GỬI KHIẾU NẠI LÊN SÀN ]</button>' +
                '</div>' +
            '</div>';
        } else {
            actionHtml += '<div style="font-size:12.5px;display:flex;flex-direction:column;gap:8px">' +
                '<div>' +
                    '<span style="color:rgba(16,55,92,.5)">Tệp video bằng chứng:</span>' +
                    '<div style="background:#ecfdf5;color:#047857;padding:6px;border:1px solid #a7f3d0;border-radius:4px;font-weight:700;display:flex;align-items:center;gap:4px;margin-top:4px">' +
                        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="width:14px;height:14px"><polyline points="20 6 9 17 4 12"></polyline></svg>' +
                        (order.disputeEvidenceVideo || 'cctv_packing_proof.mp4') +
                    '</div>' +
                '</div>' +
                '<div>' +
                    '<span style="color:rgba(16,55,92,.5)">Nội dung khiếu nại đã gửi:</span>' +
                    '<p style="background:var(--alice);padding:6px;border:1px solid #E5EAF3;border-radius:4px;font-style:italic;margin-top:4px;font-weight:600">"' + (order.disputeNote || '') + '"</p>' +
                '</div>' +
                '<div style="border-top:1px solid #F0F3FA;padding-top:6px;margin-top:4px">' +
                    '<span style="color:rgba(16,55,92,.5)">Trạng thái Sàn:</span>' +
                    (order.status === 'dispute_success' ? 
                        '<div style="color:#047857;font-weight:700;display:flex;align-items:center;gap:4px;margin-top:2px"><svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="width:14px;height:14px"><circle cx="12" cy="12" r="10"></circle><polyline points="12 6 12 12 16 14"></polyline></svg>KHIẾU NẠI THÀNH CÔNG — Sàn đã hoàn trả tiền đền bù</div>'
                     : 
                        '<div style="color:#1d4ed8;font-weight:700;display:flex;align-items:center;gap:4px;margin-top:2px"><svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="width:14px;height:14px"><circle cx="12" cy="12" r="10"></circle><polyline points="12 6 12 12 16 14"></polyline></svg>ĐANG XỬ LÝ KHIẾU NẠI — Sàn đang đối soát bằng chứng video</div>'
                    ) +
                '</div>' +
            '</div>';
        }
        
        actionHtml += `</div>
            </div>
        </div>`;
    }
    
    actionContainer.innerHTML = actionHtml;
}

function submitApprove(approve) {
    if (!activeDetailOrder) return;
    
    const note = document.getElementById("actReviewNote").value.trim();
    const whSelect = document.getElementById("actSelectWarehouse");
    const wh = whSelect ? whSelect.value : "";
    
    if (approve && !wh) {
        alert("Vui lòng chọn Kho xuất hàng để duyệt!");
        return;
    }
    if (!approve && note.length < 10) {
        alert("Lý do từ chối phải có ít nhất 10 ký tự (phục vụ truy vết).");
        return;
    }
    
    isSubmitting = true;
    
    const params = {
        action: approve ? "approve" : "reject",
        orderCode: activeDetailOrder.id,
        warehouseName: wh,
        note: note
    };
    
    postOrderAction(params, function(err, resp) {
        isSubmitting = false;
        if (err) {
            alert("Lỗi duyệt đơn: " + err);
            return;
        }
        
        allOrders = allOrders.map(o => {
            if (o.id === activeDetailOrder.id) {
                if (approve) {
                    o.status = "confirmed";
                    o.warehouse = wh;
                    o.reviewedBy = "${fn:escapeXml(loggedInUser.fullName)}";
                    o.reviewNote = note || "Phê duyệt đơn hàng thành công và bàn giao chỉ định kho.";
                    o.qtyAllocated = true;
                } else {
                    o.status = "cancelled";
                    o.reviewedBy = "${fn:escapeXml(loggedInUser.fullName)}";
                    o.reviewNote = note || "Từ chối duyệt đơn do phát hiện dấu hiệu gian lận hoặc thiếu thông tin.";
                    o.qtyAllocated = false;
                }
                o.updatedAt = new Date().toLocaleString("sv-SE").replace("T", " ").slice(0, 16);
            }
            return o;
        });
        
        saveOrdersToStorage();
        closeDetailModal();
        renderAll();
        showToast(approve ? "Đã duyệt đơn và chuyển giao việc kho thành công!" : "Đã từ chối đơn hàng thành công!", "success");
    });
}

// Website order — Sales/Kho xác nhận đã giao (mock shipper, không có webhook thật)
function submitConfirmDelivered(orderId) {
    if (isSubmitting) return;
    isSubmitting = true;

    const params = {
        action: "confirm_delivered",
        orderCode: orderId
    };

    postOrderAction(params, function(err, resp) {
        isSubmitting = false;
        if (err) {
            alert("Lỗi xác nhận giao hàng: " + err);
            return;
        }

        allOrders = allOrders.map(o => {
            if (o.id === orderId) {
                o.status = "delivered";
                o.updatedAt = new Date().toLocaleString("sv-SE").replace("T", " ").slice(0, 16);
            }
            return o;
        });

        saveOrdersToStorage();
        closeDetailModal();
        renderAll();
        showToast("Đã xác nhận giao hàng thành công!", "success");
    });
}

// RMA Dispute form submission
function submitRMADispute(orderId) {
    const videoFile = document.getElementById("inpDisputeVideo");
    const video = videoFile && videoFile.files && videoFile.files[0] ? videoFile.files[0].name : "";
    const note = document.getElementById("inpDisputeNote").value.trim();
    
    if (!video) {
        alert("Vui lòng tải lên video bằng chứng đóng gói để đối soát!");
        return;
    }
    if (!note) {
        alert("Vui lòng nhập nội dung khiếu nại để gửi lên Sàn!");
        return;
    }
    
    const params = {
        action: "dispute",
        orderCode: orderId,
        video: video,
        note: note
    };
    
    postOrderAction(params, function(err, resp) {
        if (err) {
            alert("Lỗi gửi khiếu nại: " + err);
            return;
        }
        
        const nowStr = new Date().toLocaleString("sv-SE").replace("T", " ").slice(0, 16);
        allOrders = allOrders.map(o => {
            if (o.id === orderId) {
                o.status = "dispute_success";
                o.disputeEvidenceVideo = video;
                o.disputeNote = note;
                o.rmaPlatformStatus = "Đã bồi thường";
                o.updatedAt = nowStr;
                if (!o.webhookEvents) o.webhookEvents = [];
                
                o.webhookEvents.push({
                    time: nowStr,
                    eventName: "Khiếu nại RMA lên Sàn",
                    description: "Shop gửi khiếu nại lên sàn kèm video bằng chứng: " + video + ". Nội dung: \"" + note + "\""
                });
                o.webhookEvents.push({
                    time: nowStr,
                    eventName: "Khiếu nại thành công (Sàn hoàn tiền)",
                    description: "Sàn TMĐT đối soát bằng chứng video đóng gói của Shop và xác nhận lỗi do ĐVVC quăng quật gây hư hỏng. Sàn đã duyệt đền bù 100%."
                });
            }
            return o;
        });
        
        saveOrdersToStorage();
        closeDetailModal();
        renderAll();
        showToast("Gửi hồ sơ khiếu nại thành công! Sàn đã duyệt đền bù 100% tiền hàng.", "success");
    });
}

</script>
