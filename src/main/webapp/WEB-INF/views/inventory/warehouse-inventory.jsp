<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>

<link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/inventory--inventory.css"/>

<!-- ═══ Stats Grid ═══ -->
<div class="stats-grid-3">
    <!-- Critical -->
    <div class="stat-card theme-red">
        <div class="stat-card__inner">
            <div class="stat-card__icon">
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                    <path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3Z"/>
                    <line x1="12" x2="12" y1="9" y2="13"/><line x1="12" x2="12.01" y1="17" y2="17"/>
                </svg>
            </div>
            <div>
                <div class="stat-card__val" id="criticalCountEl">0</div>
                <div class="stat-card__lbl">Cảnh báo nghiêm trọng</div>
            </div>
        </div>
    </div>

    <!-- Warning -->
    <div class="stat-card theme-yellow">
        <div class="stat-card__inner">
            <div class="stat-card__icon">
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                    <path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3Z"/>
                    <line x1="12" x2="12" y1="9" y2="13"/><line x1="12" x2="12.01" y1="17" y2="17"/>
                </svg>
            </div>
            <div>
                <div class="stat-card__val" id="warningCountEl">0</div>
                <div class="stat-card__lbl">Cảnh báo sắp hết</div>
            </div>
        </div>
    </div>

    <!-- Total -->
    <div class="stat-card theme-navy">
        <div class="stat-card__inner">
            <div class="stat-card__icon">
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <path d="m7.5 4.27 9 5.15"/>
                    <path d="M21 8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16Z"/>
                    <path d="m3.3 7 8.7 5 8.7-5"/><path d="M12 22V12"/>
                </svg>
            </div>
            <div>
                <div class="stat-card__val" id="totalCountEl">0</div>
                <div class="stat-card__lbl">Tổng dòng tồn kho</div>
            </div>
        </div>
    </div>
</div>

<!-- ═══ Filters ═══ -->
<div class="filter-bar">
    <div class="search-input-wrap">
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="11" cy="11" r="8"/><path d="m21 21-4.3-4.3"/>
        </svg>
        <input class="search-input" type="text" id="inventorySearch" placeholder="Tìm mã SKU, tên sản phẩm..."/>
    </div>

    <div class="select-wrap">
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <polygon points="22 3 2 3 10 12.46 10 19 14 21 14 12.46 22 3"/>
        </svg>
        <select class="filter-select" id="statusFilter">
            <option value="Tất cả">Tất cả</option>
            <option value="critical">Nghiêm trọng</option>
            <option value="warning">Sắp hết</option>
            <option value="enough">Đủ hàng</option>
        </select>
    </div>
</div>

<!-- ═══ Table ═══ -->
<div class="table-card">
    <div class="table-responsive">
        <table class="wms-table">
            <thead>
                <tr>
                    <th style="text-align: left;">Mã SKU</th>
                    <th style="text-align: left;">Tên sản phẩm</th>
                    <th style="text-align: center;">Phân loại</th>
                    <th style="text-align: right;" title="Tồn vật lý (đếm được trên kệ)">Tồn vật tư</th>
                    <th style="text-align: right;" title="Đã phân bổ cho đơn đang xử lý">Tạm giữ</th>
                    <th style="text-align: right; font-weight:700;" title="On-Hand − Reserved — con số thực sự được bán">Khả dụng</th>
                    <th style="text-align: right;" title="Hàng đang về (PO đã duyệt, chờ nhận)">Nhập về</th>
                    <th style="text-align: right;" title="Giá vốn bình quân (Moving Average Cost)">Giá vốn hiện tại (MAC)</th>
                    <th style="text-align: center;">Min / Max</th>
                    <th style="text-align: center;" title="Available + Inbound — đủ hay thiếu so với ROP">ATP</th>
                </tr>
            </thead>
            <tbody id="inventoryTableBody">
                <!-- Populated by JS -->
            </tbody>
        </table>
    </div>
    <div class="table-footer">
        <span id="showingCountEl">Hiển thị 0 / 0 dòng tồn kho</span>
    </div>
</div>

<!-- ═══ ATP Chip Styles ═══ -->
<style>
.atp-chip {
    display: inline-block;
    padding: 2px 8px;
    border-radius: 20px;
    font-size: 11px;
    font-weight: 700;
    letter-spacing: 0.3px;
}
.chip-shortage {
    background: rgba(220, 38, 38, 0.12);
    color: #dc2626;
    border: 1px solid rgba(220, 38, 38, 0.25);
}
.chip-running-low {
    background: rgba(234, 179, 8, 0.12);
    color: #b45309;
    border: 1px solid rgba(234, 179, 8, 0.3);
}
.chip-enough {
    background: rgba(5, 150, 105, 0.10);
    color: #059669;
    border: 1px solid rgba(5, 150, 105, 0.25);
}
.stock-type-badge {
    display: inline-block;
    padding: 2px 8px;
    font-size: 11px;
    font-weight: 700;
    border-radius: 4px;
    text-align: center;
}
.type-normal {
    background: rgba(16, 185, 129, 0.1);
    color: #10b981;
    border: 1px solid rgba(16, 185, 129, 0.2);
}
.type-defective {
    background: rgba(239, 68, 68, 0.1);
    color: #ef4444;
    border: 1px solid rgba(239, 68, 68, 0.2);
}
</style>

<!-- ═══ JAVASCRIPT ═══ -->
<script>
(function() {
    'use strict';

    var inventoryList = [];
    try {
        var rawInventoryJson = '<c:out value="${inventoryListJson}" escapeXml="false"/>';
        if (rawInventoryJson && rawInventoryJson.trim()) {
            inventoryList = JSON.parse(rawInventoryJson);
        }
    } catch (e) {
        inventoryList = [];
    }

    var criticalCountEl = document.getElementById('criticalCountEl');
    var warningCountEl  = document.getElementById('warningCountEl');
    var totalCountEl    = document.getElementById('totalCountEl');
    var inventorySearch = document.getElementById('inventorySearch');
    var statusFilter    = document.getElementById('statusFilter');
    var tableBody      = document.getElementById('inventoryTableBody');
    var showingCountEl = document.getElementById('showingCountEl');
    var searchText = '';
    var selectedStatus = 'Tất cả';
    var notified = [];

    inventorySearch.addEventListener('input', function(e) {
        searchText = e.target.value;
        render();
    });
    statusFilter.addEventListener('change', function(e) {
        selectedStatus = e.target.value;
        render();
    });

    function fmt(n) {
        return Number(n).toLocaleString('vi-VN');
    }

    function chipClass(status) {
        if (status === 'shortage') return 'chip-shortage';
        if (status === 'running_low') return 'chip-running-low';
        return 'chip-enough';
    }

    function chipLabel(status) {
        if (status === 'shortage') return 'Thiếu';
        if (status === 'running_low') return 'Sắp hết';
        return 'Đủ';
    }

    function filter(items) {
        var t = searchText.toLowerCase();
        return items.filter(function(item) {
            var matchText = !t ||
                (item.skuCode && item.skuCode.toLowerCase().indexOf(t) !== -1) ||
                (item.productName && item.productName.toLowerCase().indexOf(t) !== -1);
            var matchStatus = selectedStatus === 'Tất cả' || item.level === selectedStatus;
            return matchText && matchStatus;
        });
    }

    function render() {
        var filtered = filter(inventoryList);

        var critical = filtered.filter(function(i) { return i.level === 'critical'; }).length;
        var warning  = filtered.filter(function(i) { return i.level === 'warning'; }).length;

        criticalCountEl.textContent = critical;
        warningCountEl.textContent  = warning;
        totalCountEl.textContent    = filtered.length;

        var rows = filtered.map(function(item) {
            var qtyOnHand   = parseFloat(item.qtyOnHand || 0);
            var holding     = parseFloat(item.holding || 0);
            var qtyAvail    = parseFloat(item.qtyAvailable || 0);
            var inboundQty  = parseFloat(item.inboundQty || 0);
            var atp         = qtyAvail + inboundQty;
            var atpLabel    = fmt(atp);
            var atpClass    = chipClass(item.atpStatus || 'enough');

            var stockTypeBadge = '';
            if (item.stockType === 'DEFECTIVE') {
                stockTypeBadge = '<span class="stock-type-badge type-defective">Hàng lỗi</span>';
            } else {
                stockTypeBadge = '<span class="stock-type-badge type-normal">Hàng thường</span>';
            }

            return '<tr>' +
                '<td class="font-mono" style="font-size:13px;font-weight:600;color:var(--primary)">' + (item.skuCode || '') + '</td>' +
                '<td style="max-width:260px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">' +
                    '<span title="' + (item.productName || '') + '">' + (item.productName || '—') + '</span>' +
                '</td>' +
                '<td class="text-center">' + stockTypeBadge + '</td>' +
                '<td class="text-right" style="font-variant-numeric:tabular-nums">' + fmt(qtyOnHand) + '</td>' +
                '<td class="text-right ' + (holding > 0 ? 'text-orange' : '') + '" style="font-variant-numeric:tabular-nums">' +
                    (holding > 0 ? fmt(holding) : '—') +
                '</td>' +
                '<td class="text-right ' + (qtyAvail <= 0 ? 'text-red-bold' : '') + '" style="font-weight:700;font-variant-numeric:tabular-nums">' +
                    fmt(qtyAvail) +
                '</td>' +
                '<td class="text-right ' + (inboundQty > 0 ? 'text-blue' : '') + '" style="font-variant-numeric:tabular-nums">' +
                    (inboundQty > 0 ? '+' + fmt(inboundQty) : '—') +
                '</td>' +
                '<td class="text-right" style="font-variant-numeric:tabular-nums;font-weight:600">' +
                    fmt(item.macPrice || 0) +
                '</td>' +
                '<td class="text-center" style="font-variant-numeric:tabular-nums;font-size:12px">' +
                    (item.minStock > 0 || item.maxStock > 0
                        ? fmt(item.minStock || 0) + ' / ' + fmt(item.maxStock || 0)
                        : '<span style="color:#9ca3af">—</span>') +
                '</td>' +
                '<td class="text-center">' +
                    '<span class="atp-chip ' + atpClass + '">' + atpLabel + '</span>' +
                '</td>' +
            '</tr>';
        });

        tableBody.innerHTML = rows.length
            ? rows.join('')
            : '<tr><td colspan="10" style="text-align:center;padding:32px;color:#9ca3af">Không có dữ liệu tồn kho</td></tr>';

        showingCountEl.textContent = 'Hiển thị ' + rows.length + ' / ' + filtered.length + ' dòng tồn kho';
    }

    render();
})();
</script>

<style>
.text-right { text-align: right !important; }
.text-center { text-align: center !important; }
.font-mono { font-family: 'JetBrains Mono', 'Fira Code', monospace; }
.text-orange { color: #d97706 !important; }
.text-blue { color: #2563eb !important; }
.text-red-bold { color: #dc2626 !important; font-weight: 700 !important; }
</style>
