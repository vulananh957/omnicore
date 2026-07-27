<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>

<link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/inventory--inventory.css"/>

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
        <select class="filter-select" id="whFilter">
            <option value="Tất cả">Tất cả kho</option>
            <c:forEach var="w" items="${warehouses}">
                <option value="${w.warehouseCode}">${w.warehouseCode}</option>
            </c:forEach>
        </select>
    </div>

    <div class="select-wrap">
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <polygon points="22 3 2 3 10 12.46 10 19 14 21 14 12.46 22 3"/>
        </svg>
        <select class="filter-select" id="statusFilter">
            <option value="Tất cả">Tất cả trạng thái</option>
            <option value="Nghiêm trọng">Nghiêm trọng</option>
            <option value="Sắp hết">Sắp hết</option>
            <option value="An toàn">An toàn</option>
        </select>
    </div>

    <button class="btn-sort" id="btnSortPhysical" title="Tồn ít → nhiều">
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="m3 16 4 4 4-4"/><path d="M7 20V4"/>
            <path d="m21 8-4-4-4 4"/><path d="M17 4v16"/>
        </svg>
        Tồn vật lý
    </button>
</div>

<!-- ═══ Table ═══ -->
<div class="table-card">
    <div class="table-responsive">
        <table class="wms-table">
            <thead>
                <tr>
                    <th style="text-align: left;">Mã SKU</th>
                    <th style="text-align: left;">Tên sản phẩm</th>
                    <th style="text-align: center;">Mã kho</th>
                    <th style="text-align: left;">Tên kho</th>
                    <th style="text-align: right;" title="Tồn vật lý (đếm được trên kệ)">Tồn vật tư</th>
                    <th style="text-align: right;" title="Đã phân bổ cho đơn đang xử lý">Tạm giữ</th>
                    <th style="text-align: right; font-weight:700;" title="On-Hand − Reserved — con số thực sự được bán">Khả dụng</th>
                    <th style="text-align: right;" title="Hàng đang về (PO đã duyệt, chờ nhận)">Nhập về</th>
                    <th style="text-align: right;" title="Giá nhập bình quân (Moving Average Cost)">Giá vốn</th>
                    <th style="text-align: center;">Định mức</th>
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
    <div id="inventoryPagination"></div>
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

    var criticalCountEl = null;
    var warningCountEl  = null;
    var totalCountEl    = null;
    var inventorySearch = document.getElementById('inventorySearch');
    var whFilter       = document.getElementById('whFilter');
    var statusFilter   = document.getElementById('statusFilter');
    var btnSortPhysical = document.getElementById('btnSortPhysical');
    var tableBody      = document.getElementById('inventoryTableBody');
    var showingCountEl = document.getElementById('showingCountEl');

    var sortAsc = true;
    var searchText = '';
    var selectedWarehouse = 'Tất cả';
    var selectedStatus = 'Tất cả';

    inventorySearch.addEventListener('input', function(e) {
        searchText = e.target.value;
        render();
    });
    whFilter.addEventListener('change', function(e) {
        selectedWarehouse = e.target.value;
        render();
    });
    statusFilter.addEventListener('change', function(e) {
        selectedStatus = e.target.value;
        render();
    });
    btnSortPhysical.addEventListener('click', function() {
        sortAsc = !sortAsc;
        btnSortPhysical.title = sortAsc ? 'Tồn ít → nhiều' : 'Tồn nhiều → ít';
        render();
    });

    function render() {
        OmniPagination.reset('salesInventory');
        renderPage();
    }

    function renderPage() {
        var filtered = inventoryList.filter(function(item) {
            var sku = item.skuCode || item.sku || '';
            var name = item.productName || item.name || '';
            var matchSearch = sku.toLowerCase().indexOf(searchText.toLowerCase()) !== -1 ||
                              name.toLowerCase().indexOf(searchText.toLowerCase()) !== -1;
            var matchWh = selectedWarehouse === 'Tất cả' || item.warehouseCode === selectedWarehouse;
            var atpStatus = item.atpStatus || 'enough';
            var matchStatus = selectedStatus === 'Tất cả' ||
                              (selectedStatus === 'Nghiêm trọng' && atpStatus === 'shortage') ||
                              (selectedStatus === 'Sắp hết' && atpStatus === 'running_low') ||
                              (selectedStatus === 'An toàn' && atpStatus === 'enough');
            return matchSearch && matchWh && matchStatus;
        });

        filtered.sort(function(a, b) {
            return sortAsc ? a.qtyOnHand - b.qtyOnHand : b.qtyOnHand - a.qtyOnHand;
        });

        showingCountEl.textContent = 'Hiển thị ' + filtered.length + ' / ' + inventoryList.length + ' dòng tồn kho';

        tableBody.innerHTML = '';

        if (filtered.length === 0) {
            var tr = document.createElement('tr');
            var td = document.createElement('td');
            td.colSpan = 11;
            td.style.cssText = 'padding:40px 24px;text-align:center;color:rgba(16,55,92,.4);font-size:13px';
            td.textContent = 'Không tìm thấy dòng tồn kho nào phù hợp';
            tr.appendChild(td);
            tableBody.appendChild(tr);
            document.getElementById('inventoryPagination').innerHTML = '';
            return;
        }

        var paginationResult = OmniPagination.paginate('salesInventory', filtered);

        paginationResult.items.forEach(function(item) {
            var tr = document.createElement('tr');

            // ── SKU ──
            var td1 = document.createElement('td');
            td1.innerHTML = '<span class="sku-code">' + esc(item.skuCode || item.sku || '') + '</span>';
            tr.appendChild(td1);

            // ── Name ──
            var td2 = document.createElement('td');
            td2.style.maxWidth = '240px';
            var name = item.productName || item.name || '';
            td2.innerHTML = '<div class="product-name" title="' + esc(name) + '">' + esc(name) + '</div>';
            tr.appendChild(td2);

            // ── Warehouse Code ──
            var td3 = document.createElement('td');
            td3.style.textAlign = 'center';
            td3.innerHTML = '<span class="warehouse-code">' + esc(item.warehouseCode || '') + '</span>';
            tr.appendChild(td3);

            // ── Warehouse Name ──
            var td4 = document.createElement('td');
            td4.innerHTML = '<span class="warehouse-name">' + esc(item.warehouseName || '') + '</span>';
            tr.appendChild(td4);

            // ── On-Hand ──
            var td5 = document.createElement('td');
            td5.style.textAlign = 'right';
            var level = item.level || 'safe';
            var onHand = Number(item.qtyOnHand) || 0;
            var isAlert = level === 'critical' || level === 'warning';
            var onHandHtml = '<div class="qty-cell">';
            if (isAlert) {
                onHandHtml += '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="width:13px;height:13px;margin-right:3px;vertical-align:middle"><path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3Z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>';
            }
            onHandHtml += '<span class="qty-number ' + level + '">' + onHand.toLocaleString() + '</span></div>';
            td5.innerHTML = onHandHtml;
            tr.appendChild(td5);

            // ── Reserved (holding) ──
            var td6 = document.createElement('td');
            td6.style.textAlign = 'right';
            var holding = Number(item.holding) || 0;
            td6.innerHTML = '<span style="color:' + (holding > 0 ? '#b45309' : 'rgba(16,55,92,.35)') + ';font-weight:600;">' + holding.toLocaleString() + '</span>';
            tr.appendChild(td6);

            // ── Available ★ ──
            var td7 = document.createElement('td');
            td7.style.textAlign = 'right';
            var available = Number(item.qtyAvailable) || 0;
            var ropVal = Number(item.ropCalculated) || 0;
            var availClass = available <= 0 ? 'critical'
                : (ropVal > 0 && available < ropVal ? 'warning' : 'safe');
            td7.innerHTML = '<span class="qty-number ' + availClass + '" style="font-weight:700;">' + available.toLocaleString() + '</span>';
            tr.appendChild(td7);

            // ── Inbound ──
            var td8 = document.createElement('td');
            td8.style.textAlign = 'right';
            var inbound = Number(item.inboundQty) || 0;
            td8.innerHTML = '<span style="color:' + (inbound > 0 ? '#059669' : 'rgba(16,55,92,.35)') + ';font-weight:600;">' + inbound.toLocaleString() + '</span>';
            tr.appendChild(td8);

            // ── Giá vốn (MAC) ──
            var tdMac = document.createElement('td');
            tdMac.style.textAlign = 'right';
            var macPrice = Number(item.macPrice) || 0;
            tdMac.innerHTML = '<span style="font-size:12px; color:' + (macPrice > 0 ? 'rgba(16,55,92,.75)' : 'rgba(16,55,92,.35)') + '; font-weight:600;">' +
                (macPrice > 0 ? macPrice.toLocaleString('vi-VN') + ' đ' : '—') +
                '</span>';
            tr.appendChild(tdMac);

            // ── Định mức (Min / Max) ──
            var tdMinMax = document.createElement('td');
            tdMinMax.style.textAlign = 'center';
            tdMinMax.style.fontSize = '12px';
            var minS = Number(item.minStock) || 0;
            var maxS = Number(item.maxStock) || 0;
            tdMinMax.innerHTML = (minS > 0 || maxS > 0)
                ? (minS.toLocaleString() + ' / ' + maxS.toLocaleString())
                : '<span style="color:#9ca3af">—</span>';
            tr.appendChild(tdMinMax);

            // ── ATP Chip ──
            var td9 = document.createElement('td');
            td9.style.textAlign = 'center';
            var atp = Number(item.atp) || 0;
            var atpStatus = item.atpStatus || 'enough';
            var atpLabelMap = {
                shortage: { text: 'Thiếu hụt', cls: 'chip-shortage' },
                running_low: { text: 'Sắp hết', cls: 'chip-running-low' },
                enough: { text: 'Đủ hàng', cls: 'chip-enough' }
            };
            var chip = atpLabelMap[atpStatus] || atpLabelMap.enough;
            td9.innerHTML = '<span class="atp-chip ' + chip.cls + '">' + chip.text + '</span>'
                + '<div style="font-size:10px;color:rgba(16,55,92,.4);margin-top:2px;">'
                + atp.toLocaleString() + ' / ROP ' + (ropVal != null && !isNaN(ropVal) ? ropVal.toLocaleString() : '—')
                + '</div>';
            tr.appendChild(td9);

            tableBody.appendChild(tr);
        });

        OmniPagination.renderControls('inventoryPagination', paginationResult.currentPage, paginationResult.totalPages, function (newPage) {
            OmniPagination.setPage('salesInventory', newPage);
            renderPage();
        });
    }

    function esc(text) {
        if (!text) return '';
        return text.toString()
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }

    render();
})();
</script>
