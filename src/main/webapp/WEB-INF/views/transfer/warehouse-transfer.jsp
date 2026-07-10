<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>

<link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/transfer--warehouse-transfer.css?v=2"/>

<!-- ═══ PAGE HEADER ═══ -->
<div class="wt-page-header">
    <div>
        <div class="wt-page-title">Điều Chuyển Kho</div>
        <div class="wt-page-sub">Quản lý lệnh điều chuyển hàng hóa giữa các kho và khu vực</div>
    </div>
</div>

<!-- ═══ STATS ═══ -->
<div class="wt-stats">
    <div class="wt-stat">
        <div class="wt-stat-icon" style="background:rgba(16,55,92,.08);">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
                 stroke="rgba(16,55,92,1)" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <rect x="2" y="3" width="20" height="14" rx="2"/><path d="M8 21h8M12 17v4"/>
            </svg>
        </div>
        <div>
            <div class="wt-stat-val" id="wtStatTotal">0</div>
            <div class="wt-stat-lbl">Tổng phiếu</div>
        </div>
    </div>
    <div class="wt-stat">
        <div class="wt-stat-icon" style="background:rgba(245,158,11,.15);">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
                 stroke="#f59e0b" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/>
            </svg>
        </div>
        <div>
            <div class="wt-stat-val" id="wtStatTransit" style="color:#b45309;">0</div>
            <div class="wt-stat-lbl">Đang chuyển</div>
        </div>
    </div>
    <div class="wt-stat">
        <div class="wt-stat-icon" style="background:#ecfdf5;">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
                 stroke="#059669" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/>
            </svg>
        </div>
        <div>
            <div class="wt-stat-val" id="wtStatReceived" style="color:#059669;">0</div>
            <div class="wt-stat-lbl">Đã nhận</div>
        </div>
    </div>
    <div class="wt-stat">
        <div class="wt-stat-icon" style="background:rgba(16,55,92,.05);">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
                 stroke="rgba(16,55,92,.50)" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                <polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/>
            </svg>
        </div>
        <div>
            <div class="wt-stat-val" id="wtStatCancelled" style="color:rgba(16,55,92,.60);">0</div>
            <div class="wt-stat-lbl">Đã hủy</div>
        </div>
    </div>
</div>

<!-- ═══ TOOLBAR ═══ -->
<div class="wt-toolbar">
    <div class="wt-search-wrap">
        <svg class="wt-search-icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"
             fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="11" cy="11" r="8"/><path d="m21 21-4.35-4.35"/>
        </svg>
        <input class="wt-search-input" type="text" id="wtSearch"
               placeholder="Tìm mã phiếu, kho nguồn, kho đích…"/>
    </div>
    <button class="wt-btn-create" id="wtBtnCreate">
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
             stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
            <line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>
        </svg>
        Tạo phiếu chuyển kho
    </button>
</div>

<!-- ═══ TABS ═══ -->
<div class="wt-tabs" id="wtTabs">
    <button class="wt-tab active" data-tab="all">
        Tất cả <span class="wt-tab-badge" id="wtBadge-all">0</span>
    </button>
    <button class="wt-tab" data-tab="IN_TRANSIT">
        Đang chuyển <span class="wt-tab-badge" id="wtBadge-IN_TRANSIT">0</span>
    </button>
    <button class="wt-tab" data-tab="RECEIVED">
        Đã nhận <span class="wt-tab-badge" id="wtBadge-RECEIVED">0</span>
    </button>
    <button class="wt-tab" data-tab="CANCELLED">
        Đã hủy <span class="wt-tab-badge" id="wtBadge-CANCELLED">0</span>
    </button>
</div>

<!-- ═══ TABLE ═══ -->
<div class="wt-card">
    <div class="wt-table-scroll">
        <table class="wt-table">
            <thead>
                <tr>
                    <th>Mã phiếu</th>
                    <th>Từ kho</th>
                    <th>Đến kho</th>
                    <th>Ngày tạo</th>
                    <th>Trạng thái</th>
                    <th class="ta-r">Thao tác</th>
                </tr>
            </thead>
            <tbody id="wtTableBody">
                <tr><td colspan="6" class="wt-empty">Đang tải dữ liệu…</td></tr>
            </tbody>
        </table>
    </div>
</div>

<!-- ═══ MODAL: TẠO PHIẾU ═══ -->
<div class="wt-overlay" id="wtCreateOverlay" style="display:none;">
    <div class="wt-modal">
        <div class="wt-modal-hd">
            <div>
                <h2>Tạo Phiếu Chuyển Kho</h2>
                <p>Điều chuyển hàng hóa giữa kho hoặc khu vực lưu trữ</p>
            </div>
            <button class="wt-modal-close" id="wtBtnCloseCreate">×</button>
        </div>
        <div class="wt-modal-body">
            <!-- Sản phẩm -->
            <div>
                <div class="wt-sec-title">Thông tin sản phẩm</div>
                <div style="display:flex;flex-direction:column;gap:10px;">
                    <div class="wt-form-group">
                        <label class="wt-label">Mã sản phẩm (SKU) *</label>
                        <select class="wt-input" id="wtFormSku" style="background:#fff;cursor:pointer;">
                            <option value="">— Chọn sản phẩm —</option>
                        </select>
                    </div>
                    <div class="wt-form-group">
                        <label class="wt-label">Tên sản phẩm</label>
                        <input class="wt-input" type="text" id="wtFormSkuName" readonly
                               style="background:var(--alice);cursor:not-allowed;" placeholder="Chọn SKU để tự điền"/>
                    </div>
                    <div class="wt-form-group">
                        <div class="wt-label-row">
                            <label class="wt-label">Số lượng chuyển *</label>
                            <span class="wt-avail" id="wtAvail">— sp</span>
                        </div>
                        <input class="wt-input" type="number" id="wtFormQty" min="1" value="1"/>
                    </div>
                </div>
            </div>
            <!-- Điều chuyển -->
            <div>
                <div class="wt-sec-title">Thông tin điều chuyển</div>
                <div style="display:flex;flex-direction:column;gap:10px;">
                    <div class="wt-zone-box">
                        <div class="wt-zone-box-lbl">
                            <span class="wt-zone-bar" style="background:var(--orange);"></span>
                            Từ (Nguồn xuất)
                        </div>
                        <div class="wt-zone-cols">
                            <div>
                                <div class="wt-sub-label">Chi nhánh kho</div>
                                <select class="wt-select-sm" id="wtFormSrcWH">
                                    <option value="">— Chọn kho —</option>
                                </select>
                            </div>
                            <div>
                                <div class="wt-sub-label">Khu vực (Zone)</div>
                                <select class="wt-select-sm" id="wtFormSrcZone">
                                    <option value="">— Chọn zone —</option>
                                </select>
                            </div>
                        </div>
                    </div>
                    <div class="wt-zone-box">
                        <div class="wt-zone-box-lbl">
                            <span class="wt-zone-bar" style="background:var(--navy);"></span>
                            Đến (Đích nhập)
                        </div>
                        <div class="wt-zone-cols">
                            <div>
                                <div class="wt-sub-label">Chi nhánh kho</div>
                                <select class="wt-select-sm" id="wtFormDstWH">
                                    <option value="">— Chọn kho —</option>
                                </select>
                            </div>
                            <div>
                                <div class="wt-sub-label">Khu vực (Zone)</div>
                                <select class="wt-select-sm" id="wtFormDstZone">
                                    <option value="">— Chọn zone —</option>
                                </select>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
            <!-- Ghi chú -->
            <div class="wt-form-group">
                <label class="wt-label">Ghi chú / Lý do điều chuyển</label>
                <textarea class="wt-textarea" id="wtFormNote" rows="3"
                    placeholder="Ví dụ: Chuyển hàng từ Zone A sang Zone Hàng Hỏng để kiểm tra…"></textarea>
            </div>
        </div>
        <div class="wt-modal-ft">
            <button class="wt-btn cancel" id="wtBtnCancelCreate">Hủy</button>
            <button class="wt-btn orange" id="wtBtnSubmit">Tạo &amp; Xác nhận chuyển</button>
        </div>
    </div>
</div>

<!-- ═══ MODAL: CHI TIẾT ═══ -->
<div class="wt-overlay" id="wtDetailOverlay" style="display:none;" onclick="wtCloseDetail(event)">
    <div class="wt-modal wt-modal--wide" onclick="event.stopPropagation()">
        <div class="wt-modal-hd" style="background:rgba(240,245,250,.4);">
            <h2 style="text-transform:none;font-size:14px;">Chi tiết Phiếu Chuyển Kho</h2>
            <button class="wt-modal-close"
                    onclick="document.getElementById('wtDetailOverlay').style.display='none'">×</button>
        </div>
        <div class="wt-modal-body" id="wtDetailBody"></div>
        <div class="wt-modal-ft" id="wtDetailFooter">
            <button class="wt-btn navy"
                    onclick="document.getElementById('wtDetailOverlay').style.display='none'">Đóng</button>
        </div>
    </div>
</div>

<script id="db-transfers-data" type="application/json">
[
    <c:forEach items="${transfers}" var="t" varStatus="s">
        {
            "id": ${t.transferId},
            "code": "<c:out value='${t.transferCode}'/>",
            "fromWH": "<c:out value='${t.fromWarehouseName}'/>",
            "fromWarehouseId": ${t.fromWarehouseId},
            "toWH": "<c:out value='${t.toWarehouseName}'/>",
            "toWarehouseId": ${t.toWarehouseId},
            "status": "<c:out value='${t.status}'/>",
            "createdAt": "<c:out value='${t.createdAt}'/>",
            "completedAt": "<c:out value='${t.completedAt}'/>",
            "note": "<c:out value='${t.note}'/>",
            "createdBy": ${t.createdBy},
            "creatorName": "<c:out value='${t.creatorName}'/>",
            "approvedBy": ${t.approvedBy != null ? t.approvedBy : 'null'},
            "approverName": "<c:out value='${t.approverName}'/>",
            "items": [
                <c:forEach items="${transferItemsMap[t.transferId]}" var="item" varStatus="itemStatus">
                    {
                        "sku": "<c:out value='${item.skuCode}'/>",
                        "name": "<c:out value='${item.productName}'/>",
                        "shippedQty": "<c:out value='${item.shippedQty}'/>",
                        "receivedQty": "<c:out value='${item.receivedQty}'/>"
                    }${!itemStatus.last ? ',' : ''}
                </c:forEach>
            ]
        }${!s.last ? ',' : ''}
    </c:forEach>
]
</script>

<script id="db-warehouses-data" type="application/json">
[
    <c:forEach items="${warehouses}" var="w" varStatus="s">
        { "id": ${w.warehouseId}, "name": "<c:out value='${w.warehouseName}'/>" }${!s.last ? ',' : ''}
    </c:forEach>
]
</script>

<script id="db-products-data" type="application/json">
[
    <c:forEach items="${products}" var="p" varStatus="s">
        { "sku": "<c:out value='${p.sku}'/>", "name": "<c:out value='${p.name}'/>" }${!s.last ? ',' : ''}
    </c:forEach>
]
</script>

<!-- ═══ JAVASCRIPT ═══ -->
<script>
(function () {
    'use strict';

    /* ─── Data injected from servlet ─── */
    var DB_TRANSFERS = JSON.parse(document.getElementById('db-transfers-data').textContent || '[]');
    var DB_WAREHOUSES = JSON.parse(document.getElementById('db-warehouses-data').textContent || '[]');
    var DB_PRODUCTS = JSON.parse(document.getElementById('db-products-data').textContent || '[]');

    /* ─── State ─── */
    var activeTab  = 'all';
    var searchTxt  = '';
    var detailDoc  = null;

    /* ─── DOM refs ─── */
    var tbody      = document.getElementById('wtTableBody');
    var tabs       = document.getElementById('wtTabs');
    var searchEl   = document.getElementById('wtSearch');
    var createOvl  = document.getElementById('wtCreateOverlay');
    var detailOvl  = document.getElementById('wtDetailOverlay');
    var detailBody = document.getElementById('wtDetailBody');
    var fSku       = document.getElementById('wtFormSku');
    var fSkuName   = document.getElementById('wtFormSkuName');
    var fQty       = document.getElementById('wtFormQty');
    var fSrcWH     = document.getElementById('wtFormSrcWH');
    var fSrcZone   = document.getElementById('wtFormSrcZone');
    var fDstWH     = document.getElementById('wtFormDstWH');
    var fDstZone   = document.getElementById('wtFormDstZone');
    var fNote      = document.getElementById('wtFormNote');

    /* ─── Populate form selects ─── */
    function buildWhOption(sel, ph) {
        sel.innerHTML = '<option value="">' + ph + '</option>';
        DB_WAREHOUSES.forEach(function (w) {
            var o = document.createElement('option');
            o.value = w.id; o.textContent = w.name;
            sel.appendChild(o);
        });
    }
    buildWhOption(fSrcWH, '— Chọn kho xuất —');
    buildWhOption(fDstWH, '— Chọn kho nhập —');

    // Pre-select and lock the source warehouse to the logged-in user's warehouse
    var myWarehouseId = "${MY_WAREHOUSE_ID}";
    if (myWarehouseId) {
        fSrcWH.value = myWarehouseId;
        fSrcWH.disabled = true;
    }

    if (DB_PRODUCTS.length > 0) {
        fSku.innerHTML = '<option value="">— Chọn sản phẩm —</option>';
        DB_PRODUCTS.forEach(function (p) {
            var o = document.createElement('option');
            o.value = p.sku; o.textContent = p.sku + ' – ' + p.name;
            fSku.appendChild(o);
        });
    }

    fSku.addEventListener('change', function () {
        var p = DB_PRODUCTS.find(function (x) { return x.sku === fSku.value; });
        fSkuName.value = p ? p.name : '';
    });

    function populateZones(whSel, zoneSel) {
        // Zone info not available server-side yet; keep placeholder
        zoneSel.innerHTML = '<option value="">— Chọn zone —</option>';
    }
    fSrcWH.addEventListener('change', function () { populateZones(fSrcWH, fSrcZone); });
    fDstWH.addEventListener('change', function () { populateZones(fDstWH, fDstZone); });

    /* ─── Status config ─── */
    function statusCfg(s) {
        var m = {
            'IN_TRANSIT': { cls: 'in-transit',  lbl: 'Đang chuyển' },
            'RECEIVED':   { cls: 'received',    lbl: 'Đã nhận' },
            'CANCELLED':  { cls: 'cancelled',   lbl: 'Đã hủy' }
        };
        return m[s] || { cls: 'draft', lbl: s };
    }

    function pill(s) {
        var c = statusCfg(s);
        return '<span class="wt-pill ' + c.cls + '">' +
               '<span class="wt-pill__dot"></span>' + esc(c.lbl) + '</span>';
    }

    /* ─── Render ─── */
    function render() {
        // Counts
        function cnt(key) {
            if (key === 'all') return DB_TRANSFERS.length;
            return DB_TRANSFERS.filter(function (t) { return t.status === key; }).length;
        }
        document.getElementById('wtStatTotal').textContent    = DB_TRANSFERS.length;
        document.getElementById('wtStatTransit').textContent  = cnt('IN_TRANSIT');
        document.getElementById('wtStatReceived').textContent = cnt('RECEIVED');
        document.getElementById('wtStatCancelled').textContent = cnt('CANCELLED');

        ['all','IN_TRANSIT','RECEIVED','CANCELLED'].forEach(function (k) {
            var el = document.getElementById('wtBadge-' + k);
            if (el) el.textContent = cnt(k);
        });

        // Filter
        var q = searchTxt.toLowerCase();
        var filtered = DB_TRANSFERS.filter(function (t) {
            var matchTab = activeTab === 'all' || t.status === activeTab;
            var matchQ   = !q ||
                (t.code   && t.code.toLowerCase().includes(q)) ||
                (t.fromWH && t.fromWH.toLowerCase().includes(q)) ||
                (t.toWH   && t.toWH.toLowerCase().includes(q));
            return matchTab && matchQ;
        });

        if (filtered.length === 0) {
            tbody.innerHTML = '<tr><td colspan="6" class="wt-empty">Không có phiếu chuyển kho nào phù hợp.</td></tr>';
            return;
        }

        tbody.innerHTML = filtered.map(function (t) {
            var acts = '<button class="wt-btn-icon" data-action="view" data-id="' + esc(t.code) + '" title="Xem chi tiết">' + eyeSVG() + '</button>';
            return '<tr>' +
                '<td><span class="wt-code">' + esc(t.code) + '</span></td>' +
                '<td><div class="wt-nm">' + esc(t.fromWH || '—') + '</div></td>' +
                '<td><div class="wt-nm">' + esc(t.toWH || '—') + '</div></td>' +
                '<td><span class="wt-date">' + esc(t.createdAt || '—') + '</span></td>' +
                '<td>' + pill(t.status) + '</td>' +
                '<td class="ta-r"><div class="wt-row-actions">' + acts + '</div></td>' +
                '</tr>';
        }).join('');
    }

    /* ─── Table events ─── */
    tbody.addEventListener('click', function (e) {
        var btn = e.target.closest('[data-action]');
        if (!btn) return;
        var action = btn.dataset.action;
        var id     = btn.dataset.id;

        if (action === 'view') {
            var t = DB_TRANSFERS.find(function (x) { return x.code === id; });
            if (t) openDetail(t);
        }
    });

    /* ─── Tabs ─── */
    tabs.addEventListener('click', function (e) {
        var btn = e.target.closest('.wt-tab');
        if (!btn) return;
        activeTab = btn.dataset.tab;
        tabs.querySelectorAll('.wt-tab').forEach(function (t) { t.classList.remove('active'); });
        btn.classList.add('active');
        render();
    });

    /* ─── Search ─── */
    searchEl.addEventListener('input', function () { searchTxt = this.value; render(); });

    /* ─── Create modal ─── */
    document.getElementById('wtBtnCreate').addEventListener('click', function () {
        fSku.value = ''; fSkuName.value = ''; fQty.value = 1; fNote.value = '';
        createOvl.style.display = 'flex';
    });
    document.getElementById('wtBtnCloseCreate').addEventListener('click', function () { createOvl.style.display = 'none'; });
    document.getElementById('wtBtnCancelCreate').addEventListener('click', function () { createOvl.style.display = 'none'; });
    createOvl.addEventListener('click', function (e) { if (e.target === createOvl) createOvl.style.display = 'none'; });

    function doCreate() {
        var sku = fSku.value.trim();
        if (!sku) { alert('Vui lòng chọn sản phẩm (SKU)!'); return; }
        var qty = parseInt(fQty.value, 10) || 0;
        if (qty <= 0) { alert('Số lượng phải lớn hơn 0!'); return; }

        var srcWHId  = fSrcWH.value;
        var dstWHId  = fDstWH.value;
        if (!srcWHId) { alert('Vui lòng chọn kho xuất!'); return; }
        if (!dstWHId) { alert('Vui lòng chọn kho nhận!'); return; }
        if (srcWHId === dstWHId) { alert('Kho xuất và kho nhận phải khác nhau!'); return; }

        var payload = {
            action: 'create',
            fromWarehouseId: parseInt(srcWHId, 10),
            toWarehouseId:   parseInt(dstWHId, 10),
            sku:             sku,
            qty:             qty,
            note:            fNote.value.trim()
        };

        var btn = document.getElementById('wtBtnSubmit');
        btn.disabled = true;

        fetch(window.location.pathname, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        })
        .then(function(res) { return res.json(); })
        .then(function(data) {
            btn.disabled = false;
            if (data.success) {
                createOvl.style.display = 'none';
                fSku.value = ''; fSkuName.value = ''; fQty.value = 1; fNote.value = '';
                location.reload();
            } else {
                alert('Lỗi: ' + (data.message || 'Không thể tạo phiếu chuyển kho.'));
            }
        })
        .catch(function(err) {
            btn.disabled = false;
            alert('Có lỗi mạng xảy ra khi tạo phiếu chuyển kho.');
        });
    }

    document.getElementById('wtBtnSubmit').addEventListener('click', function () { doCreate(); });

    /* ─── Detail modal ─── */
    function openDetail(t) {
        detailDoc = t;
        var r = t;
        var items;

        if (Array.isArray(r.items) && r.items.length > 0) {
            items = r.items;
        } else if (r.sku) {
            items = [{
                sku:         r.sku,
                name:        r.skuName || r.sku,
                shippedQty:  r.qty,
                receivedQty: 0
            }];
        } else {
            items = [];
        }
        var totalRequested = 0;
        var totalReceived = 0;

        var rowsHtml = items.length ? items.map(function (item, index) {
            var shippedQty = Number(item.shippedQty || 0);
            var receivedQty = Number(item.receivedQty || 0);
            totalRequested += shippedQty;
            totalReceived += receivedQty;

            return '<tr>' +
                '<td style="padding:10px 12px;border:1px solid var(--border);text-align:center;">' + (index + 1) + '</td>' +
                '<td style="padding:10px 12px;border:1px solid var(--border);font-family:monospace;font-size:12px;">' + esc(item.sku || '—') + '</td>' +
                '<td style="padding:10px 12px;border:1px solid var(--border);font-weight:600;">' + esc(item.name || '—') + '</td>' +
                '<td style="padding:10px 12px;border:1px solid var(--border);text-align:center;background:rgba(59,130,246,.06);color:#1d4ed8;font-weight:700;">' + esc(item.shippedQty || '0') + '</td>' +
                '<td style="padding:10px 12px;border:1px solid var(--border);text-align:center;background:rgba(16,185,129,.06);color:#047857;font-weight:700;">' + esc(item.receivedQty || '0') + '</td>' +
                '</tr>';
        }).join('') : '<tr><td colspan="5" style="padding:14px;border:1px solid var(--border);text-align:center;color:rgba(16,55,92,.55);">Chưa có dòng hàng hóa chi tiết cho phiếu chuyển kho này.</td></tr>';

        var noteHtml = r.note ?
            '<div class="wt-detail-sep">' +
                '<div class="wt-dl-lbl" style="margin-bottom:6px;">Lý do / ghi chú điều chuyển</div>' +
                '<div class="wt-detail-note">' + esc(r.note) + '</div>' +
            '</div>' : '';

        var completedHtml = r.completedAt ?
            '<div><div class="wt-dl-lbl">Hoàn tất lúc</div><div class="wt-dl-val muted">' + esc(r.completedAt) + '</div></div>' :
            '<div><div class="wt-dl-lbl">Hoàn tất lúc</div><div class="wt-dl-val muted">Chưa hoàn tất</div></div>';

        detailBody.innerHTML =
            '<div style="display:flex;justify-content:space-between;align-items:flex-start;gap:16px;padding-bottom:16px;border-bottom:1px solid var(--border);">' +
                '<div>' +
                    '<div class="wt-dl-lbl">Mã phiếu chuyển kho</div>' +
                    '<div style="font-weight:800;font-size:18px;color:var(--navy);margin-top:4px;font-family:monospace;">' + esc(t.code) + '</div>' +
                    '<div style="font-size:12px;color:rgba(16,55,92,.55);margin-top:6px;">Internal Stock Transfer Note</div>' +
                '</div>' +
                '<div style="text-align:right;">' +
                    '<div class="wt-dl-lbl">Trạng thái</div>' +
                    '<div style="margin-top:6px;">' + pill(t.status) + '</div>' +
                '</div>' +
            '</div>' +
            '<div class="wt-flow">' +
                '<div style="flex:1;background:#f0f7ff;border:1px solid #cfe3ff;border-radius:12px;padding:12px 14px;">' +
                    '<div class="wt-dl-lbl" style="font-size:10px;color:#1d4ed8;">KHO NGUỒN</div>' +
                    '<div style="font-size:14px;font-weight:800;color:var(--navy);margin-top:4px;">' + esc(t.fromWH || '—') + '</div>' +
                '</div>' +
                '<div class="wt-flow-arrow"><svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><line x1="5" y1="12" x2="19" y2="12"/><polyline points="12 5 19 12 12 19"/></svg></div>' +
                '<div style="flex:1;background:#ecfdf5;border:1px solid #b7ebcf;border-radius:12px;padding:12px 14px;">' +
                    '<div class="wt-dl-lbl" style="font-size:10px;color:#047857;">KHO ĐÍCH</div>' +
                    '<div style="font-size:14px;font-weight:800;color:var(--navy);margin-top:4px;">' + esc(t.toWH || '—') + '</div>' +
                '</div>' +
            '</div>' +
            '<div class="wt-detail-sep">' +
                '<div class="wt-detail-grid" style="grid-template-columns:repeat(3,minmax(0,1fr));gap:16px 20px;">' +
                    '<div><div class="wt-dl-lbl">Người lập phiếu</div><div class="wt-dl-val">' + esc(r.creatorName || 'Nhân viên kho') + '</div></div>' +
                    '<div><div class="wt-dl-lbl">Ngày tạo</div><div class="wt-dl-val muted">' + esc(t.createdAt || '—') + '</div></div>' +
                    completedHtml +
                '</div>' +
            '</div>' +
            noteHtml +
            '<div class="wt-detail-sep">' +
                '<div style="display:flex;justify-content:space-between;align-items:center;gap:12px;margin-bottom:10px;">' +
                    '<div class="wt-dl-lbl" style="font-size:12px;">Danh sách hàng hóa điều chuyển</div>' +
                    '<div style="display:flex;gap:8px;flex-wrap:wrap;">' +
                        '<span style="padding:6px 10px;border-radius:999px;background:rgba(59,130,246,.08);color:#1d4ed8;font-size:12px;font-weight:700;">SL yêu cầu: ' + totalRequested + '</span>' +
                        '<span style="padding:6px 10px;border-radius:999px;background:rgba(16,185,129,.08);color:#047857;font-size:12px;font-weight:700;">SL thực chuyển: ' + totalReceived + '</span>' +
                    '</div>' +
                '</div>' +
                '<div style="overflow:auto;border:1px solid var(--border);border-radius:12px;">' +
                    '<table style="width:100%;border-collapse:collapse;background:#fff;">' +
                        '<thead>' +
                            '<tr style="background:var(--alice);">' +
                                '<th style="padding:10px 12px;border:1px solid var(--border);text-align:center;width:56px;">STT</th>' +
                                '<th style="padding:10px 12px;border:1px solid var(--border);text-align:left;">Mã SKU</th>' +
                                '<th style="padding:10px 12px;border:1px solid var(--border);text-align:left;">Tên sản phẩm</th>' +
                                '<th style="padding:10px 12px;border:1px solid var(--border);text-align:center;">SL yêu cầu</th>' +
                                '<th style="padding:10px 12px;border:1px solid var(--border);text-align:center;">SL thực chuyển</th>' +
                            '</tr>' +
                        '</thead>' +
                        '<tbody>' + rowsHtml + '</tbody>' +
                    '</table>' +
                '</div>' +
            '</div>' +
            '<div class="wt-detail-sep" style="padding-top:4px;">' +
                '<div style="display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:14px;text-align:center;">' +
                    '<div><div class="wt-dl-lbl">Người lập phiếu</div><div style="height:42px;"></div><div style="border-top:1px dashed var(--border);padding-top:6px;font-size:12px;">' + esc(r.creatorName || 'Nhân viên kho') + '</div></div>' +
                    '<div><div class="wt-dl-lbl">Thủ kho xuất</div><div style="height:42px;"></div><div style="border-top:1px dashed var(--border);padding-top:6px;font-size:12px;">' + esc(t.fromWH || '—') + '</div></div>' +
                    '<div><div class="wt-dl-lbl">Thủ kho nhận</div><div style="height:42px;"></div><div style="border-top:1px dashed var(--border);padding-top:6px;font-size:12px;">' + esc(t.toWH || '—') + '</div></div>' +
                '</div>' +
            '</div>';

        var footerEl = document.getElementById('wtDetailFooter');
        if (t.status === 'IN_TRANSIT' && parseInt(myWarehouseId, 10) === t.toWarehouseId) {
            footerEl.innerHTML = 
                '<button class="wt-btn green" onclick="confirmReceive(' + t.id + ', this)">Xác nhận nhận hàng</button>' +
                '<button class="wt-btn navy" onclick="document.getElementById(\'wtDetailOverlay\').style.display=\'none\'">Đóng</button>';
        } else {
            footerEl.innerHTML = 
                '<button class="wt-btn navy" onclick="document.getElementById(\'wtDetailOverlay\').style.display=\'none\'">Đóng</button>';
        }

        detailOvl.style.display = 'flex';
    }

    window.confirmReceive = function(transferId, btn) {
        if (!confirm('Bạn có chắc chắn xác nhận đã nhận đủ hàng cho phiếu điều chuyển này?')) return;
        btn.disabled = true;
        var originalText = btn.textContent;
        btn.textContent = 'Đang xử lý...';

        fetch('${pageContext.request.contextPath}/warehouse/transfer', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ action: 'receive', transferId: transferId })
        })
        .then(function (r) { return r.json(); })
        .then(function (res) {
            if (res.success) {
                alert('Xác nhận nhận hàng điều chuyển thành công!');
                window.location.reload();
            } else {
                alert(res.message || 'Xác nhận thất bại.');
                btn.disabled = false;
                btn.textContent = originalText;
            }
        })
        .catch(function (err) {
            alert('Có lỗi mạng xảy ra khi xác nhận nhận hàng.');
            btn.disabled = false;
            btn.textContent = originalText;
        });
    };

    window.wtCloseDetail = function (e) {
        if (e.target === detailOvl) detailOvl.style.display = 'none';
    };

    /* ─── SVG helpers ─── */
    function eyeSVG() {
        return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>';
    }
    function esc(v) {
        if (v == null) return '';
        return String(v)
            .replace(/&/g,'&amp;').replace(/</g,'&lt;')
            .replace(/>/g,'&gt;').replace(/"/g,'&quot;')
            .replace(/'/g,'&#039;');
    }

    /* ─── Init ─── */
    render();

})();
</script>
