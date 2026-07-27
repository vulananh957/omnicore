<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" isELIgnored="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ page import="com.wms.model.Product" %>
<%@ page import="com.wms.model.SkuMapping" %>
<%@ page import="com.wms.model.Channel" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="com.wms.util.JsonUtil" %>
<%
    List<Product> products = (List<Product>) request.getAttribute("products");
    if (products == null) products = java.util.Collections.emptyList();

    List<SkuMapping> skuMappings = (List<SkuMapping>) request.getAttribute("skuMappings");
    if (skuMappings == null) skuMappings = java.util.Collections.emptyList();

    List<Map<String, Object>> unresolvedExceptions = (List<Map<String, Object>>) request.getAttribute("unresolvedExceptions");
    if (unresolvedExceptions == null) unresolvedExceptions = java.util.Collections.emptyList();

    List<Channel> channels = (List<Channel>) request.getAttribute("channels");
    if (channels == null) channels = java.util.Collections.emptyList();

    String productsJson = JsonUtil.toJson(products);
    String mappingsJson = JsonUtil.toJson(skuMappings);
    String unresolvedJson = JsonUtil.toJson(unresolvedExceptions);
    String channelsJson = JsonUtil.toJson(channels);

    request.setAttribute("productsJson", productsJson);
    request.setAttribute("mappingsJson", mappingsJson);
    request.setAttribute("unresolvedJson", unresolvedJson);
    request.setAttribute("channelsJson", channelsJson);
%>

<link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/sales--sku-mapping.css"/>



<%-- ── ALERT BANNER ── --%>
<div class="sm-alert-banner" id="smAlertBanner" style="display:none">
    <div class="sm-alert-inner">
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>
        <span>Có <strong id="bannerCount">0</strong> sản phẩm mới từ sàn chưa được ánh xạ vào Master SKU.</span>
    </div>
    <button class="sm-alert-btn" onclick="openDrawer()">Xử lý ngay →</button>
</div>

<%-- ── TOOLBAR: chỉ search + kéo sàn ── --%>
<div class="sm-toolbar">
    <div class="sm-search">
        <svg class="sm-search-icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="11" cy="11" r="8"></circle><line x1="21" y1="21" x2="16.65" y2="16.65"></line>
        </svg>
        <input type="text" placeholder="Tìm theo Master SKU, tên sản phẩm..." id="smSearchInput" oninput="onSearch(this.value)" />
    </div>
    <div class="sm-toolbar-right">
        <button class="sm-btn-pull" onclick="pullMarketplaceProducts('lazada')" id="btnPullLazada">
            <svg class="pullSpinnerIcon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21.5 2v6h-6M21.34 15.57a10 10 0 1 1-.57-8.38l5.67-5.67"></path></svg>
            Kéo từ Lazada
        </button>
        <button class="sm-btn-pull" onclick="pullMarketplaceProducts('website')" id="btnPullWebsite">
            <svg class="pullSpinnerIcon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21.5 2v6h-6M21.34 15.57a10 10 0 1 1-.57-8.38l5.67-5.67"></path></svg>
            Kéo từ Website
        </button>
    </div>
</div>

<%-- ── MAIN TABLE — Interactive Empty States ── --%>
<div class="sm-table-card">
    <div class="sm-pull-overlay" id="smPullOverlay">
        <div class="sm-loading-card">
            <div class="sm-loader-spinner">
                <div class="sm-loader-spinner-outer"></div>
            </div>
            <div class="loading-title">Đang kết nối...</div>
            <div class="loading-sub" id="smPullOverlayText">Đang kéo các sản phẩm chưa gán ánh xạ...</div>
        </div>
    </div>
    <div class="sm-table-scroll">
        <table class="sm-table">
            <thead>
                <tr>
                    <th style="width:148px">Master SKU</th>
                    <th>Tên sản phẩm</th>
                    <th style="width:140px">Danh mục</th>
                    <th style="width:120px; text-align:right" title="Giá nhập bình quân (Moving Average Cost)">Giá nhập</th>
                    <th style="width:195px"><span class="sm-col-channel-label lazada-dot">Lazada</span></th>
                    <th style="width:195px"><span class="sm-col-channel-label website-dot">Website</span></th>
                    <th style="width:110px;text-align:right">Tồn kho</th>
                </tr>
            </thead>
            <tbody id="smTableBody"></tbody>
        </table>
    </div>
    <div id="smPagination"></div>
</div>

<%-- ── RIGHT DRAWER — Inbox ── --%>
<div class="sm-drawer-backdrop" id="smDrawerBackdrop" onclick="closeDrawer()"></div>
<div class="sm-drawer" id="smDrawer">
    <div class="sm-drawer-header">
        <div class="sm-drawer-title">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="2" y="3" width="20" height="14" rx="2"/><line x1="8" y1="21" x2="16" y2="21"/><line x1="12" y1="17" x2="12" y2="21"/></svg>
            Hộp thư — Sản phẩm từ Sàn chờ ánh xạ
        </div>
        <div style="display:flex;align-items:center;gap:8px">
            <span class="sm-drawer-badge" id="drawerBadgeCount">0</span>
            <button class="sm-modal-close" onclick="closeDrawer()">
                <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><line x1="18" y1="6" x2="6" y2="18"></line><line x1="6" y1="6" x2="18" y2="18"></line></svg>
            </button>
        </div>
    </div>
    <div class="sm-drawer-search">
        <svg class="sm-search-icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"></circle><line x1="21" y1="21" x2="16.65" y2="16.65"></line></svg>
        <input type="text" placeholder="Lọc theo mã sàn hoặc tên sản phẩm..." id="drawerSearchInput" oninput="onDrawerSearch(this.value)" />
    </div>
    <div class="sm-drawer-body" id="smDrawerBody"></div>
</div>

<%-- ── MAPPING MODAL ── --%>
<div class="sm-modal-overlay" id="smMappingModalOverlay" onclick="closeMappingModal()">
    <div class="sm-modal" onclick="event.stopPropagation()">
        <div class="sm-modal-header">
            <div class="sm-modal-title">
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"></path><path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"></path></svg>
                Cấu hình Ánh xạ sản phẩm đa kênh
            </div>
            <button class="sm-modal-close" onclick="closeMappingModal()">
                <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><line x1="18" y1="6" x2="6" y2="18"></line><line x1="6" y1="6" x2="18" y2="18"></line></svg>
            </button>
        </div>
        <div class="sm-modal-body">
            <div class="sm-channel-box">
                <div class="sm-channel-box-title">1. Sản phẩm trên Sàn (Channel Item)</div>
                <div style="display:grid;grid-template-columns:1fr 1fr;gap:12px;font-size:13px">
                    <div>
                        <div style="color:rgba(16,55,92,.4);font-size:10px">Kênh bán hàng</div>
                        <strong id="mdChannelName">-</strong>
                    </div>
                    <div>
                        <div style="color:rgba(16,55,92,.4);font-size:10px">Mã SKU trên sàn</div>
                        <strong id="mdChannelSKU" style="font-family:monospace">-</strong>
                    </div>
                </div>
                <div style="border-top:1px solid rgba(229,234,243,.6);margin-top:8px;padding-top:8px;font-size:13px">
                    <div style="color:rgba(16,55,92,.4);font-size:10px">Tên SP trên sàn</div>
                    <strong id="mdChannelItemName" style="color:var(--navy)">-</strong>
                </div>
            </div>
            <div>
                <div class="sm-mapping-section-header">
                    <span class="sm-mapping-section-title">2. Liên kết với Master SKU nội bộ (WMS)</span>
                </div>
                <div id="mdLinkedRowsContainer"></div>
            </div>
        </div>
        <div class="sm-modal-footer">
            <button class="sm-btn white" onclick="closeMappingModal()">HỦY</button>
            <button class="sm-btn primary" onclick="saveMappingConfig()" id="btnModalSave">LƯU</button>
        </div>
    </div>
</div>

<%-- ── TOAST ── --%>
<div class="op-toast" id="opToast" style="position:fixed;top:2rem;right:2rem;background:var(--navy);color:#fff;padding:1rem 1.5rem;border-radius:var(--radius-btn);box-shadow:0 10px 25px rgba(0,0,0,.15);z-index:9999;font-size:13px;font-weight:700;display:flex;align-items:center;gap:.75rem;transform:translateY(-20px);opacity:0;pointer-events:none;transition:all .25s ease-out;">
    <svg id="opToastIcon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" style="width:16px;height:16px"><circle cx="12" cy="12" r="10"></circle><polyline points="12 6 12 12 16 14"></polyline></svg>
    <span id="opToastMsg">Thông báo hệ thống</span>
</div>

<div id="productsJsonData" style="display:none;"><c:out value="${productsJson}"/></div>
<div id="mappingsJsonData" style="display:none;"><c:out value="${mappingsJson}"/></div>
<div id="unresolvedJsonData" style="display:none;"><c:out value="${unresolvedJson}"/></div>
<div id="channelsJsonData" style="display:none;"><c:out value="${channelsJson}"/></div>

<script>
// ════════════════════════════════════════════════════════════════
// GLOBALS
// ════════════════════════════════════════════════════════════════
let searchQuery       = "";
let drawerSearchQuery = "";
let drawerChannelFilter = ""; // set khi mở từ nút dashed

let APPROVED_MASTER_SKUS = [];
let unmappedList  = [];
let rawMappings   = [];

let selectedUnmappedProduct = null;
let modalLinkedRows = [];

const CHANNEL_COLORS = { shopee: "#EE4D2D", tiktok: "#00B0AA", lazada: "#0F146D", website: "#EB8317" };

// ════════════════════════════════════════════════════════════════
// INIT
// ════════════════════════════════════════════════════════════════
document.addEventListener("DOMContentLoaded", function () {
    loadDataFromServer();
    renderAll();

    const toastMsg     = "<c:out value='${sessionScope.toastMessage}' />";
    const toastSuccess = "<c:out value='${sessionScope.toastSuccess}' />";
    if (toastMsg) showToast(toastMsg, toastSuccess === "true" ? "success" : "error");
});

function loadDataFromServer() {
    // 1. Mappings
    try {
        const raw = document.getElementById("mappingsJsonData").textContent.trim();
        if (raw) rawMappings = JSON.parse(raw).map(m => ({
            mappingId:     m.mappingId,
            masterSKU:     m.skuCode,
            channel:       m.channelPlatform || m.channelName || "Lazada",
            channelSKU:    m.externalSku,
            channelCategory: m.channelCategory || ""
        }));
    } catch(e) { rawMappings = []; }

    // 2. Unmapped inbox
    try {
        const raw = document.getElementById("unresolvedJsonData").textContent.trim();
        if (raw) unmappedList = JSON.parse(raw).map(ex => {
            const platform = ex.platform || "Lazada";
            return {
                id:              ex.exceptionId,
                channelId:       ex.channelId,
                channel:         platform,
                shopName:        ex.channelName || "",
                channelColor:    CHANNEL_COLORS[platform.toLowerCase()] || "#0f146d",
                channelSKU:      ex.externalSku,
                channelItemId:   ex.channelItemId || ex.externalSku,
                sellerSku:       ex.sellerSku || ex.externalSku,
                channelItemName: ex.reason || ("Sản phẩm sàn (" + ex.externalSku + ")")
            };
        });
    } catch(e) { unmappedList = []; }

    // 3. Master SKUs
    try {
        const raw = document.getElementById("productsJsonData").textContent.trim();
    if (raw) APPROVED_MASTER_SKUS = JSON.parse(raw).map(p => ({
        id:       p.productId,
        sku:      p.sku || p.skuCode || "",
        name:     p.name || p.productName || "",
        category: p.categoryName || "",
        qtyOnHand: p.qtyOnHand || 0,
        macPrice: p.macPrice || 0
    }));
    } catch(e) { APPROVED_MASTER_SKUS = []; }
}

// ════════════════════════════════════════════════════════════════
// RENDER ALL
// ════════════════════════════════════════════════════════════════
function renderAll() {
    renderAlertBanner();
    renderTableBody();
    renderDrawerBody();
}

function renderAlertBanner() {
    const banner = document.getElementById("smAlertBanner");
    if (unmappedList.length > 0) {
        document.getElementById("bannerCount").textContent = unmappedList.length;
        banner.style.display = "flex";
    } else {
        banner.style.display = "none";
    }
}

// ════════════════════════════════════════════════════════════════
// MAIN TABLE — Interactive Empty States
// ════════════════════════════════════════════════════════════════
function onSearch(val) {
    searchQuery = val.trim().toLowerCase();
    renderTableBody();
}

function renderTableBody() {
    OmniPagination.reset('skuMapping');
    renderTableBodyPage();
}

function renderTableBodyPage() {
    const tbody = document.getElementById("smTableBody");
    tbody.innerHTML = "";

    const filtered = APPROVED_MASTER_SKUS.filter(wms => {
        if (!searchQuery) return true;
        return (wms.sku  && wms.sku.toLowerCase().includes(searchQuery)) ||
               (wms.name && wms.name.toLowerCase().includes(searchQuery));
    });

    if (filtered.length === 0) {
        const msg = APPROVED_MASTER_SKUS.length === 0
            ? "Chưa có Master SKU nào trong hệ thống. Vui lòng tạo sản phẩm trước."
            : "Không tìm thấy sản phẩm nào khớp với từ khóa.";
        tbody.innerHTML =
            '<tr><td colspan="7" class="op-empty">' +
            '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">' +
            '<rect x="3" y="3" width="18" height="18" rx="2"></rect>' +
            '<line x1="9" y1="9" x2="15" y2="15"></line><line x1="15" y1="9" x2="9" y2="15"></line></svg>' +
            msg + '</td></tr>';
        document.getElementById("smPagination").innerHTML = "";
        return;
    }

    const paginationResult = OmniPagination.paginate('skuMapping', filtered);

    paginationResult.items.forEach(wms => {
        const rels     = rawMappings.filter(m => m.masterSKU === wms.sku);
        const category = wms.category || (rels[0] && rels[0].channelCategory) || "";
        const qty      = wms.qtyOnHand || 0;

        const tr = document.createElement("tr");
        tr.innerHTML =
            '<td><span style="font-family:monospace;font-weight:700;color:var(--navy)">' + esc(wms.sku)  + '</span></td>' +
            '<td><strong style="color:var(--navy)">'                                     + esc(wms.name) + '</strong></td>' +
            '<td><span class="sm-category-text">'                                        + (category ? esc(category) : '<span style="color:rgba(16,55,92,.2)">—</span>') + '</span></td>' +
            '<td style="text-align:right">' + (wms.macPrice > 0 ? Number(wms.macPrice).toLocaleString('vi-VN') + ' đ' : '<span style="color:rgba(16,55,92,.35);">—</span>') + '</td>' +
            '<td>' + buildChannelCell(rels, 'lazada',  'Lazada')  + '</td>' +
            '<td>' + buildChannelCell(rels, 'website', 'Website') + '</td>' +
            '<td style="text-align:right">' + stockBadge(qty) + '</td>';
        tbody.appendChild(tr);
    });

    OmniPagination.renderControls('smPagination', paginationResult.currentPage, paginationResult.totalPages, function (newPage) {
        OmniPagination.setPage('skuMapping', newPage);
        renderTableBodyPage();
    });
}

/**
 * Ô channel:
 *   - Có mapping  → pill(s) với unlink ×
 *   - Chưa map    → nút dashed "+ Kết nối [Channel]"
 */
function buildChannelCell(rels, chanKey, chanLabel) {
    const matched = rels.filter(r => r.channel.toLowerCase().replace(/\s+/g, "").includes(chanKey));
    const color   = CHANNEL_COLORS[chanKey] || "#64748b";

    if (matched.length > 0) {
        return '<div style="display:flex;flex-wrap:wrap;gap:5px">' +
            matched.map(m =>
                '<div class="sm-mapped-pill" title="' + chanLabel + ': ' + esc(m.channelSKU) + '">' +
                    '<span class="sm-pill-dot" style="background:' + color + '"></span>' +
                    '<span class="sm-mapped-sku">' + esc(m.channelSKU) + '</span>' +
                    '<span class="sm-unlink-btn" onclick="event.stopPropagation();deleteMapping(\'' + m.mappingId + '\')">' +
                        '<svg xmlns="http://www.w3.org/2000/svg" width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><line x1="18" y1="6" x2="6" y2="18"></line><line x1="6" y1="6" x2="18" y2="18"></line></svg>' +
                    '</span>' +
                '</div>'
            ).join('') +
        '</div>';
    }

    // Dashed "Điền vào chỗ trống" button
    return '<button class="sm-connect-btn" onclick="openDrawerForChannel(\'' + chanKey + '\')" title="Nhấn để kết nối ' + chanLabel + '">' +
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><line x1="12" y1="5" x2="12" y2="19"></line><line x1="5" y1="12" x2="19" y2="12"></line></svg>' +
        'Kết nối ' + chanLabel +
    '</button>';
}

function stockBadge(qty) {
    const color = qty > 10 ? "#059669" : qty > 0 ? "#d97706" : "rgba(16,55,92,.3)";
    return '<span style="font-size:13px;font-weight:800;color:' + color + '">' + qty.toLocaleString() + '</span>';
}

function esc(str) {
    if (!str) return "";
    return String(str).replace(/&/g,"&amp;").replace(/</g,"&lt;").replace(/>/g,"&gt;").replace(/"/g,"&quot;");
}

// ════════════════════════════════════════════════════════════════
// RIGHT DRAWER
// ════════════════════════════════════════════════════════════════
function openDrawer() {
    drawerChannelFilter = drawerChannelFilter || "";
    document.getElementById("smDrawer").classList.add("open");
    document.getElementById("smDrawerBackdrop").classList.add("open");
    renderDrawerBody();
}

/** Mở từ nút "+ Kết nối" — drawer đã lọc sẵn theo kênh */
function openDrawerForChannel(chanKey) {
    drawerChannelFilter = chanKey;
    document.getElementById("smDrawer").classList.add("open");
    document.getElementById("smDrawerBackdrop").classList.add("open");
    document.getElementById("drawerSearchInput").value = "";
    drawerSearchQuery = "";
    renderDrawerBody();
}

function closeDrawer() {
    document.getElementById("smDrawer").classList.remove("open");
    document.getElementById("smDrawerBackdrop").classList.remove("open");
    drawerChannelFilter = "";
}

function onDrawerSearch(val) {
    drawerSearchQuery = val.trim().toLowerCase();
    renderDrawerBody();
}

function renderDrawerBody() {
    const body  = document.getElementById("smDrawerBody");
    const badge = document.getElementById("drawerBadgeCount");

    // Pool lọc theo kênh nếu mở từ dashed button
    let pool = unmappedList;
    if (drawerChannelFilter) {
        pool = pool.filter(x => x.channel.toLowerCase().replace(/\s+/g, "").includes(drawerChannelFilter));
    }

    const LABELS = { lazada: "Lazada", website: "Website", shopee: "Shopee", tiktok: "TikTok" };
    badge.textContent = pool.length + (drawerChannelFilter ? " " + (LABELS[drawerChannelFilter] || drawerChannelFilter) : "");

    const filtered = pool.filter(item => {
        if (!drawerSearchQuery) return true;
        return (item.channelSKU      && item.channelSKU.toLowerCase().includes(drawerSearchQuery)) ||
               (item.channelItemName && item.channelItemName.toLowerCase().includes(drawerSearchQuery));
    });

    if (filtered.length === 0) {
        const CHAN = drawerChannelFilter ? (LABELS[drawerChannelFilter] || drawerChannelFilter) : "";
        body.innerHTML =
            '<div class="sm-drawer-empty">' +
            '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>' +
            '<p>' + (pool.length === 0
                ? (CHAN ? "Không có sản phẩm " + CHAN + " nào chờ ánh xạ." : "Tuyệt vời! Tất cả sản phẩm từ sàn đã được ánh xạ.")
                : "Không tìm thấy sản phẩm khớp.") +
            '</p></div>';
        return;
    }

    body.innerHTML = filtered.map(item => {
        const color = CHANNEL_COLORS[item.channel.toLowerCase()] || item.channelColor || "#64748b";
        return '<div class="sm-drawer-item">' +
            '<div class="sm-drawer-item-header">' +
                '<span class="sm-badge-channel" style="background:' + color + '">' + esc(item.channel) + '</span>' +
                (item.shopName ? '<span class="sm-drawer-shop">' + esc(item.shopName) + '</span>' : '') +
            '</div>' +
            '<div class="sm-drawer-sku">'  + esc(item.channelSKU) + '</div>' +
            '<div class="sm-drawer-name">' + esc(item.channelItemName) + '</div>' +
            '<button class="sm-btn-action sm-drawer-map-btn" onclick="openMappingModal(\'' + item.id + '\')">' +
                '<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13.828 10.172a4 4 0 00-5.656 0l-4 4a4 4 0 105.656 5.656l1.102-1.101m-.758-4.899a4 4 0 005.656 0l4-4a4 4 0 00-5.656-5.656l-1.1 1.1"/></svg>' +
                'Ánh Xạ vào Master SKU' +
            '</button>' +
        '</div>';
    }).join('');
}

// ════════════════════════════════════════════════════════════════
// MAPPING MODAL
// ════════════════════════════════════════════════════════════════
function openMappingModal(id) {
    const item = unmappedList.find(x => x.id == id);
    if (!item) return;
    selectedUnmappedProduct = item;

    document.getElementById("mdChannelName").textContent     = item.channel + (item.shopName ? " (" + item.shopName + ")" : "");
    document.getElementById("mdChannelName").style.color     = item.channelColor || "#10375c";
    document.getElementById("mdChannelSKU").textContent      = item.channelSKU;
    document.getElementById("mdChannelItemName").textContent = item.channelItemName;

    if (APPROVED_MASTER_SKUS.length === 0) {
        document.getElementById("btnModalSave").disabled = true;
        document.getElementById("mdLinkedRowsContainer").innerHTML =
            '<div style="padding:1.5rem;text-align:center;color:#dc2626;font-size:12.5px;font-weight:700;background:#fef2f2;border:1px solid #fecaca;border-radius:8px">' +
            'Không tìm thấy WMS Master SKU nào! Vui lòng tạo sản phẩm trước.</div>';
    } else {
        document.getElementById("btnModalSave").disabled = false;
        modalLinkedRows = [{ masterSKU: APPROVED_MASTER_SKUS[0].sku }];
        renderModalRows();
    }
    document.getElementById("smMappingModalOverlay").classList.add("open");
}

function closeMappingModal() {
    document.getElementById("smMappingModalOverlay").classList.remove("open");
    selectedUnmappedProduct = null;
    modalLinkedRows = [];
}

function renderModalRows() {
    const container = document.getElementById("mdLinkedRowsContainer");
    container.innerHTML = "";
    modalLinkedRows.forEach((row, idx) => {
        const div = document.createElement("div");
        div.className = "sm-row-card";
        const opts = APPROVED_MASTER_SKUS.map(s =>
            '<option value="' + s.sku + '"' + (s.sku === row.masterSKU ? ' selected' : '') + '>' + esc(s.name) + ' (' + esc(s.sku) + ')</option>'
        ).join('');
        div.innerHTML =
            '<div style="flex:1;min-width:0">' +
                '<span class="sm-field-label">Chọn sản phẩm gốc kho WMS</span>' +
                '<select class="sm-select" onchange="updateLinkedRow(' + idx + ', this.value)">' + opts + '</select>' +
            '</div>';
        container.appendChild(div);
    });
}

function updateLinkedRow(idx, value) { modalLinkedRows[idx].masterSKU = value; }

// ════════════════════════════════════════════════════════════════
// SAVE / DELETE
// ════════════════════════════════════════════════════════════════
function saveMappingConfig() {
    if (!modalLinkedRows.length) { alert("Vui lòng chọn ít nhất một Master SKU!"); return; }
    const matched = APPROVED_MASTER_SKUS.find(w => w.sku === modalLinkedRows[0].masterSKU);
    if (!matched) { alert("Không tìm thấy Master SKU tương ứng!"); return; }

    submitForm("${pageContext.request.contextPath}/sales/sku-mapping", {
        action:     "create",
        productId:  matched.id,
        channelId:  selectedUnmappedProduct.channelId,
        channelSku: selectedUnmappedProduct.channelItemId || selectedUnmappedProduct.channelSKU,
        sellerSku:  selectedUnmappedProduct.sellerSku || selectedUnmappedProduct.channelSKU,
        ...(selectedUnmappedProduct.id ? { exceptionId: selectedUnmappedProduct.id } : {})
    });
}

function deleteMapping(mappingId) {
    if (confirm("Bạn có chắc chắn muốn hủy liên kết ánh xạ này không?")) {
        submitForm("${pageContext.request.contextPath}/sales/sku-mapping", { action: "delete", mappingId });
    }
}

function submitForm(action, fields) {
    const form = document.createElement("form");
    form.method = "POST"; form.action = action;
    Object.entries(fields).forEach(([n, v]) => {
        const i = document.createElement("input"); i.type = "hidden"; i.name = n; i.value = v; form.appendChild(i);
    });
    document.body.appendChild(form); form.submit();
}

// ════════════════════════════════════════════════════════════════
// PULL FROM MARKETPLACE
// ════════════════════════════════════════════════════════════════
const ALL_CHANNELS = [];
try {
    const rawCh = document.getElementById("channelsJsonData").textContent.trim();
    if (rawCh) {
        JSON.parse(rawCh).forEach(ch => {
            ALL_CHANNELS.push({ id: String(ch.channelId), platform: ch.platform });
        });
    }
} catch (e) {
    console.error("Failed to parse channels JSON", e);
}

const PULL_LABELS = { lazada: "Lazada", website: "Website" };
const PULL_BUTTON_IDS = { lazada: "btnPullLazada", website: "btnPullWebsite" };

async function pullMarketplaceProducts(platformFilter) {
    const label = PULL_LABELS[platformFilter] || platformFilter;
    const channelIds = ALL_CHANNELS
        .filter(ch => (ch.platform || "").toLowerCase() === platformFilter)
        .map(ch => ch.id);

    if (!channelIds.length) { showToast("Không tìm thấy kênh " + label + " nào để kéo sản phẩm.", "error"); return; }

    const overlay   = document.getElementById("smPullOverlay");
    const icons     = document.querySelectorAll(".pullSpinnerIcon");
    const btnLazada = document.getElementById("btnPullLazada");
    const btnWebsite = document.getElementById("btnPullWebsite");
    document.getElementById("smPullOverlayText").textContent = "Đang kéo các sản phẩm chưa gán ánh xạ từ " + label + " API...";
    overlay.classList.add("open");
    icons.forEach(icon => icon.style.animation = "spin 1s linear infinite");
    btnLazada.disabled = true; btnWebsite.disabled = true;

    try {
        let ok = 0, msgs = [];
        for (const id of channelIds) {
            const res = await fetch("${pageContext.request.contextPath}/sales/channel-products", {
                method: "POST",
                headers: { "Content-Type": "application/x-www-form-urlencoded" },
                body: "action=pull&channelId=" + encodeURIComponent(id)
            });
            if (res.ok) { const d = await res.json(); d.success ? ok++ : msgs.push(d.message); }
            else msgs.push("HTTP " + res.status);
        }
        if (ok > 0) { showToast("Kéo thành công từ " + ok + "/" + channelIds.length + " kênh " + label + "!", "success"); setTimeout(() => location.reload(), 1500); }
        else          showToast("Thất bại: " + msgs.join(" | "), "error");
    } catch(e) { showToast("Lỗi: " + e.message, "error"); }
    finally {
        overlay.classList.remove("open");
        icons.forEach(icon => icon.style.animation = "none");
        btnLazada.disabled = false; btnWebsite.disabled = false;
    }
}

// ════════════════════════════════════════════════════════════════
// TOAST
// ════════════════════════════════════════════════════════════════
function showToast(msg, type = "success") {
    const t = document.getElementById("opToast");
    const i = document.getElementById("opToastIcon");
    t.style.background = type === "success" ? "#059669" : "#dc2626";
    i.innerHTML = type === "success"
        ? '<path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path><polyline points="22 4 12 14.01 9 11.01"></polyline>'
        : '<circle cx="12" cy="12" r="10"></circle><line x1="12" y1="16" x2="12" y2="12"></line><line x1="12" y1="8" x2="12.01" y2="8"></line>';
    document.getElementById("opToastMsg").textContent = msg;
    t.style.opacity = 1; t.style.transform = "translateY(0)";
    setTimeout(() => { t.style.opacity = 0; t.style.transform = "translateY(-20px)"; }, 4000);
}

// spin keyframe
const _s = document.createElement("style");
_s.textContent = "@keyframes spin{0%{transform:rotate(0deg)}100%{transform:rotate(360deg)}}";
document.head.appendChild(_s);
</script>

<c:remove var="toastMessage" scope="session" />
<c:remove var="toastSuccess" scope="session" />
