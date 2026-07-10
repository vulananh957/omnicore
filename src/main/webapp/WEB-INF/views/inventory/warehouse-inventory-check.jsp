<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%
    // Disable JSP/response caching so the browser always gets the latest fragment.
    response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
    response.setHeader("Pragma", "no-cache");
    response.setDateHeader("Expires", 0);
%>

<link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/inventory--warehouse-inventory-check.css"/>

<!-- ═══ STATS ROW ═══ -->
<div class="ic-stats-grid" id="icStatsGrid">
    <!-- Tổng phiếu -->
    <div class="ic-stat-card">
        <div class="ic-stat-icon" style="background: rgba(16,55,92,0.08);">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
                 stroke="rgba(16,55,92,1)" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <rect x="3" y="4" width="18" height="18" rx="2" ry="2"/>
                <line x1="16" y1="2" x2="16" y2="6"/>
                <line x1="8" y1="2" x2="8" y2="6"/>
                <line x1="3" y1="10" x2="21" y2="10"/>
            </svg>
        </div>
        <div>
            <div class="ic-stat-value" id="statTotal">0</div>
            <div class="ic-stat-label">Tổng số phiếu</div>
        </div>
    </div>
    <!-- Đang kiểm đếm -->
    <div class="ic-stat-card">
        <div class="ic-stat-icon" style="background: rgba(255,186,8,0.20);">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
                 stroke="var(--orange)" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <circle cx="12" cy="12" r="10"/>
                <polyline points="12 6 12 12 16 14"/>
            </svg>
        </div>
        <div>
            <div class="ic-stat-value" id="statPending" style="color: var(--orange);">0</div>
            <div class="ic-stat-label">Đang kiểm đếm</div>
        </div>
    </div>
    <!-- Chờ phê duyệt -->
    <div class="ic-stat-card">
        <div class="ic-stat-icon" style="background: rgba(26,115,232,0.08);">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
                 stroke="#1a73e8" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/>
                <polyline points="22 4 12 14.01 9 11.01"/>
            </svg>
        </div>
        <div>
            <div class="ic-stat-value" id="statCompleted" style="color: #1a73e8;">0</div>
            <div class="ic-stat-label">Chờ phê duyệt</div>
        </div>
    </div>
    <!-- Đã duyệt & Cân bằng -->
    <div class="ic-stat-card">
        <div class="ic-stat-icon" style="background: #ecfdf5;">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
                 stroke="#059669" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <polyline points="20 6 9 17 4 12"/>
            </svg>
        </div>
        <div>
            <div class="ic-stat-value" id="statApproved" style="color: #059669;">0</div>
            <div class="ic-stat-label">Đã duyệt & Cân bằng</div>
        </div>
    </div>
</div>

<!-- ═══ TOOLBAR ═══ -->
<div class="ic-toolbar">
    <div class="ic-search-wrap">
        <svg class="ic-search-icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"
             fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="11" cy="11" r="8"/><path d="m21 21-4.35-4.35"/>
        </svg>
        <input class="ic-search-input" type="text" id="icSearch"
               placeholder="Tìm mã phiếu hoặc tiêu đề kiểm kê..."/>
    </div>
    <button class="btn-export-report" id="btnExport">
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
             stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/>
        </svg>
        Xuất báo cáo
    </button>
    <button class="btn-create-check" id="btnOpenCreate">
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
             stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
            <line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>
        </svg>
        Tạo phiếu kiểm kê
    </button>
</div>

<!-- ═══ STATUS TABS ═══ -->
<div class="ic-tabs" id="icTabs">
    <button class="ic-tab active" data-tab="all">
        Tất cả <span class="ic-tab-badge" id="badge-all">0</span>
    </button>
    <button class="ic-tab" data-tab="in_progress">
        Đang kiểm đếm <span class="ic-tab-badge" id="badge-progress">0</span>
    </button>
    <button class="ic-tab" data-tab="approved">
        Đã duyệt & Cân bằng <span class="ic-tab-badge" id="badge-approved">0</span>
    </button>
</div>

<!-- ═══ SHEETS CONTAINER ═══ -->
<div class="ic-list-container" id="sheetsContainer">
    <div class="ic-empty">Đang tải danh sách phiếu kiểm kê...</div>
</div>

<!-- ═══ MODAL: TẠO PHIẾU KIỂM KÊ ═══ -->
<div class="ic-overlay" id="createOverlay" style="display:none;">
    <div class="ic-modal" id="createModal">
        <div class="ic-modal-hd">
            <h2>Tạo Phiếu Kiểm Kê Kho</h2>
            <button class="ic-modal-close" id="btnCloseCreate">×</button>
        </div>
        <form id="frmCreateCheck">
            <div class="ic-modal-body">
                <!-- THÔNG TIN CHUNG -->
                <div class="ic-form-group">
                    <div class="ic-form-section-title">Thông tin chung</div>
                    <div class="ic-form-group">
                        <label class="ic-form-label">Tiêu đề phiếu *</label>
                        <input class="ic-input" type="text" id="formTitle" required placeholder="VD: Kiểm kê định kỳ tháng 6/2026"/>
                    </div>
                    <div class="ic-form-row">
                        <div class="ic-form-group">
                            <label class="ic-form-label">Chi nhánh kho *</label>
                            <select class="ic-select" id="formWarehouse">
                                <option value="">— Chọn chi nhánh kho —</option>
                            </select>
                        </div>
                        <div class="ic-form-group">
                            <label class="ic-form-label">Khu vực kiểm kê (Zone) *</label>
                            <select class="ic-select" id="formZone">
                                <option value="">— Chọn khu vực (Zone) —</option>
                            </select>
                        </div>
                    </div>
                </div>

                <!-- PHẠM VI SẢN PHẨM KIỂM KÊ -->
                <div class="ic-form-group">
                    <div class="ic-form-section-title">Phạm vi sản phẩm kiểm kê</div>
                    <div class="ic-radio-group">
                        <!-- Option 1: All -->
                        <label class="ic-radio-item">
                            <input class="ic-radio-input" type="radio" name="scopeType" value="all" checked/>
                            <div>
                                <div class="ic-radio-title">Tất cả sản phẩm trong Khu vực</div>
                                <div class="ic-radio-desc">Hệ thống tự động chốt tồn kho của tất cả sản phẩm thuộc Zone được chỉ định.</div>
                            </div>
                        </label>

                        <!-- Option 2: Category -->
                        <div style="display:flex; flex-direction:column; gap:8px;">
                            <label class="ic-radio-item">
                                <input class="ic-radio-input" type="radio" name="scopeType" value="category"/>
                                <div>
                                    <div class="ic-radio-title">Kiểm kê theo Danh mục</div>
                                    <div class="ic-radio-desc">Chỉ kiểm kê các sản phẩm thuộc một nhóm ngành hàng cụ thể.</div>
                                </div>
                            </label>
                            <div class="pl-6" id="categorySelectWrap" style="display:none; padding-left:24px;">
                                <select class="ic-select" id="formCategory">
                                    <option value="">— Chọn danh mục sản phẩm —</option>
                                </select>
                            </div>
                        </div>

                        <!-- Option 3: SKU -->
                        <div style="display:flex; flex-direction:column; gap:8px;">
                            <label class="ic-radio-item">
                                <input class="ic-radio-input" type="radio" name="scopeType" value="sku"/>
                                <div>
                                    <div class="ic-radio-title">Kiểm kê theo SKU cụ thể</div>
                                    <div class="ic-radio-desc">Chọn mã sản phẩm SKU chính xác cần đối soát.</div>
                                </div>
                            </label>
                            <div class="pl-6" id="skuSelectWrap" style="display:none; padding-left:24px;">
                                <select class="ic-select" id="formSKU">
                                    <!-- populated by JS -->
                                </select>
                            </div>
                        </div>
                    </div>
                </div>

                <!-- GHI CHÚ -->
                <div class="ic-form-group">
                    <div class="ic-form-section-title">Ghi chú</div>
                    <textarea class="ic-textarea" id="formNote" rows="3" placeholder="Ghi chú lý do kiểm kê, yêu cầu đặc biệt..."></textarea>
                </div>
            </div>
            <div class="ic-modal-ft">
                <button type="button" class="ic-btn ic-btn--cancel" id="btnCancelCreate">HỦY</button>
                <button type="submit" class="ic-btn ic-btn--submit">TẠO PHIẾU</button>
            </div>
        </form>
    </div>
</div>

<%-- Pre-serialised JSON payloads come from WarehouseInventoryCheckServlet. --%>
<div id="warehousesJson" style="display:none">${empty warehousesJson ? '[]' : warehousesJson}</div>
<div id="zonesJson" style="display:none">${empty zonesJson ? '[]' : zonesJson}</div>
<div id="categoriesJson" style="display:none">${empty categoriesJson ? '[]' : categoriesJson}</div>
<div id="productsJson" style="display:none">${empty productsJson ? '[]' : productsJson}</div>

<!-- ═══ JAVASCRIPT ═══ -->
<script>
(function () {
    'use strict';

    // ─── Debug helper ───
    function dbg(msg, data) {
        console.log('[inventory-check] ' + msg, data != null ? JSON.stringify(data) : '');
    }
    function dbgObj(msg, obj) {
        console.log('[inventory-check] ' + msg, obj);
    }

    // ─── Master Data (from server) ───
    var WAREHOUSES = [];
    var ZONES = [];
    var CATEGORIES = [];
    var PRODUCTS = [];

    // Parse server data into arrays
    (function() {
        try {
            var wEl = document.getElementById('warehousesJson');
            dbg('warehousesJson raw', wEl ? wEl.textContent.trim() : 'MISSING');
            if (wEl && wEl.textContent.trim()) {
                var wData = JSON.parse(wEl.textContent);
                dbgObj('warehousesJson parsed', wData);
                WAREHOUSES = wData.map(function(w) {
                    return { id: w.warehouseId, code: w.warehouseCode, name: w.warehouseName };
                });
                dbg('WAREHOUSES loaded', WAREHOUSES.length + ' items');
            } else {
                dbg('WARNING: warehousesJson is empty or missing', null);
            }
        } catch(e) { console.error('[inventory-check] Error parsing warehousesJson:', e); }
        try {
            var zEl = document.getElementById('zonesJson');
            if (zEl && zEl.textContent.trim()) {
                var zData = JSON.parse(zEl.textContent);
                ZONES = zData.map(function(z) {
                    return { id: z.zoneId, warehouseId: z.warehouseId, code: z.zoneCode, name: z.zoneName, type: z.zoneType };
                });
                dbg('ZONES loaded', ZONES.length + ' items');
            } else {
                dbg('WARNING: zonesJson is empty or missing', null);
            }
        } catch(e) { console.error('[inventory-check] Error parsing zonesJson:', e); }
        try {
            var cEl = document.getElementById('categoriesJson');
            if (cEl && cEl.textContent.trim()) {
                var cData = JSON.parse(cEl.textContent);
                dbgObj('categoriesJson raw', cData);
                CATEGORIES = cData.map(function(c) {
                    // Handle both field names from Jackson serialization
                    return { id: c.categoryId || c.id, name: c.categoryName || c.name };
                });
                dbg('CATEGORIES loaded', CATEGORIES.length + ' items');
            } else {
                dbg('WARNING: categoriesJson is empty or missing', null);
            }
        } catch(e) { console.error('[inventory-check] Error parsing categoriesJson:', e); }
        try {
            var pEl = document.getElementById('productsJson');
            if (pEl && pEl.textContent.trim()) {
                PRODUCTS = JSON.parse(pEl.textContent);
                dbg('PRODUCTS loaded', PRODUCTS.length + ' items');
            } else {
                dbg('WARNING: productsJson is empty or missing', null);
            }
        } catch(e) { console.error('[inventory-check] Error parsing productsJson:', e); }
    })();

    // ─── State ───
    var sheets = [];
    var expandedSheetId = null;
    var itemsBySheetId = {};  // checkId -> CheckDetail[] (lazy loaded on expand)
    var activeTab = 'all';
    var searchText = '';

    // ─── DOM refs ───
    var sheetsContainer = document.getElementById('sheetsContainer');
    var icSearch        = document.getElementById('icSearch');
    var icTabs          = document.getElementById('icTabs');
    var createOverlay   = document.getElementById('createOverlay');
    var frmCreateCheck  = document.getElementById('frmCreateCheck');
    var formTitle       = document.getElementById('formTitle');
    var formWarehouse   = document.getElementById('formWarehouse');
    var formZone        = document.getElementById('formZone');
    var formCategory    = document.getElementById('formCategory');
    var formSKU         = document.getElementById('formSKU');
    var formNote        = document.getElementById('formNote');
    var categorySelectWrap = document.getElementById('categorySelectWrap');
    var skuSelectWrap   = document.getElementById('skuSelectWrap');

    // ─── Initialize master selects ───
    function populateWarehouseSelect() {
        formWarehouse.innerHTML = '<option value="">— Chọn chi nhánh kho —</option>';
        WAREHOUSES.forEach(function (w) {
            var opt = document.createElement('option');
            opt.value = w.id; opt.textContent = w.code + ' — ' + w.name;
            formWarehouse.appendChild(opt);
        });
    }
    populateWarehouseSelect();

    function populateZoneSelect() {
        formZone.innerHTML = '<option value="">— Chọn khu vực (Zone) —</option>';
        ZONES.forEach(function (z) {
            var opt = document.createElement('option');
            opt.value = z.id; opt.textContent = z.code + ' — ' + z.name;
            formZone.appendChild(opt);
        });
    }
    populateZoneSelect();

    function populateCategorySelect() {
        formCategory.innerHTML = '<option value="">— Chọn danh mục sản phẩm —</option>';
        CATEGORIES.forEach(function (c) {
            var opt = document.createElement('option');
            opt.value = c.id; opt.textContent = c.name;
            formCategory.appendChild(opt);
        });
    }
    populateCategorySelect();

    function populateSkuDropdown() {
        formSKU.innerHTML = '<option value="">— Chọn sản phẩm (SKU) —</option>';
        PRODUCTS.forEach(function (p) {
            var opt = document.createElement('option');
            opt.value = p.skuCode;
            opt.textContent = p.skuCode + ' - ' + p.productName;
            formSKU.appendChild(opt);
        });
    }
    populateSkuDropdown();

    // ─── Listeners for radio scopes ───
    document.querySelectorAll('input[name="scopeType"]').forEach(function (radio) {
        radio.addEventListener('change', function () {
            var val = this.value;
            categorySelectWrap.style.display = val === 'category' ? 'block' : 'none';
            skuSelectWrap.style.display      = val === 'sku'      ? 'block' : 'none';
        });
    });

    // ─── Open / Close Modal ───
    document.getElementById('btnOpenCreate').addEventListener('click', function () {
        formTitle.value = '';
        formNote.value = '';
        document.querySelector('input[name="scopeType"][value="all"]').click();
        createOverlay.style.display = 'flex';
    });
    document.getElementById('btnCloseCreate').addEventListener('click', function () { createOverlay.style.display = 'none'; });
    document.getElementById('btnCancelCreate').addEventListener('click', function () { createOverlay.style.display = 'none'; });
    createOverlay.addEventListener('click', function (e) { if (e.target === createOverlay) createOverlay.style.display = 'none'; });

    // ─── Form submit (Tạo phiếu) ───
    frmCreateCheck.addEventListener('submit', function (e) {
        e.preventDefault();
        var title = formTitle.value.trim();
        if (!title) { alert('Vui lòng nhập Tiêu đề phiếu'); return; }
        if (!formWarehouse.value) { alert('Vui lòng chọn chi nhánh kho'); return; }

        var scopeType = document.querySelector('input[name="scopeType"]:checked').value;
        var scopeValue = '';
        if (scopeType === 'category') scopeValue = formCategory.value;
        else if (scopeType === 'sku') scopeValue = formSKU.value;

        var payload = {
            action: 'create',
            title: title,
            warehouseId: parseInt(formWarehouse.value, 10),
            zoneId: formZone.value ? parseInt(formZone.value, 10) : null,
            scopeType: scopeType,
            scopeValue: scopeValue,
            note: formNote.value.trim()
        };

        var btnSubmit = frmCreateCheck.querySelector('button[type="submit"]');
        if (btnSubmit) btnSubmit.disabled = true;

        fetch(window.location.pathname, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        })
        .then(function (r) { return r.json(); })
        .then(function (data) {
            if (btnSubmit) btnSubmit.disabled = false;
            if (data && data.success) {
                createOverlay.style.display = 'none';
                frmCreateCheck.reset();
                document.querySelector('input[name="scopeType"][value="all"]').click();
                loadChecks();
            } else {
                alert('Lỗi tạo phiếu: ' + (data && data.message ? data.message : 'Không rõ'));
            }
        })
        .catch(function (err) {
            if (btnSubmit) btnSubmit.disabled = false;
            alert('Lỗi mạng: ' + err.message);
        });
    });

    // ─── Tab switching ───
    icTabs.addEventListener('click', function (e) {
        var btn = e.target.closest('.ic-tab');
        if (!btn) return;
        activeTab = btn.dataset.tab;
        icTabs.querySelectorAll('.ic-tab').forEach(function (t) { t.classList.remove('active'); });
        btn.classList.add('active');
        render();
    });

    // ─── Search ───
    icSearch.addEventListener('input', function () { searchText = this.value; render(); });

    // (Inline edit moved below to handleUpdateCountedQty in the action triggers section)

    function renderDeltaBadge(d) {
        if (d === null) return '<span style="color:rgba(16,55,92,0.30);">—</span>';
        if (d === 0) return '<span class="ic-delta-badge ic-delta-badge--zero"><svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><line x1="5" y1="12" x2="19" y2="12"/></svg>0</span>';
        if (d > 0) return '<span class="ic-delta-badge ic-delta-badge--plus"><svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="23 6 13.5 15.5 8.5 10.5 1 18"/><polyline points="17 6 23 6 23 12"/></svg>+' + d + '</span>';
        return '<span class="ic-delta-badge ic-delta-badge--minus"><svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="23 18 13.5 8.5 8.5 13.5 1 6"/><polyline points="17 18 23 18 23 12"/></svg>' + d + '</span>';
    }

    // ─── Status badge helper ───
    // DB status: DRAFT | IN_PROGRESS | APPROVED
    // Frontend tab keys: all | in_progress | completed | approved
    // (Schema only has 3 statuses — we treat DRAFT as "in_progress" tab for the UI.)
    function statusToTab(status) {
        if (status === 'APPROVED') return 'approved';
        return 'in_progress';  // DRAFT and IN_PROGRESS both go under "Đang kiểm đếm"
    }

    function getStatusConfig(status) {
        var cfg = {
            DRAFT:        { label: "Nháp",            cls: "ic-badge--draft",     icon: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>' },
            IN_PROGRESS:  { label: "Đang kiểm đếm",  cls: "ic-badge--progress",  icon: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>' },
            APPROVED:     { label: "Đã duyệt",        cls: "ic-badge--approved",  icon: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"/></svg>' }
        };
        return cfg[status] || cfg.IN_PROGRESS;
    }

    // ─── Action triggers ───
    window.toggleExpand = function (id) {
        var ck = Number(id);
        if (expandedSheetId === ck) {
            expandedSheetId = null;
            render();
        } else {
            expandedSheetId = ck;
            render();
            if (!itemsBySheetId[ck]) loadDetails(ck);
        }
    };

    window.triggerComplete = function (e, id) {
        e.stopPropagation();
        var ck = Number(id);
        if (!confirm('Xác nhận hoàn tất kiểm đếm và trình duyệt phiếu này?')) return;
        var details = itemsBySheetId[ck] || [];
        var results = details.map(function (d) {
            return { checkDetailId: d.checkDetailId, actualQty: d.actualQty != null ? Number(d.actualQty) : 0 };
        });
        fetch(window.location.pathname, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ action: 'submit', checkId: ck, resultsJson: JSON.stringify(results) })
        })
        .then(function (r) { return r.json(); })
        .then(function (data) {
            if (data && data.success) loadChecks();
            else alert('Lỗi: ' + (data && data.message ? data.message : 'Không rõ'));
        });
    };

    window.triggerAdjust = function (e, id) {
        e.stopPropagation();
        var ck = Number(id);
        if (!confirm('Xác nhận điều chỉnh tồn kho theo kết quả kiểm kê này?')) return;
        var details = itemsBySheetId[ck] || [];
        var adjustments = details
            .filter(function (d) { return d.deltaQty != null && Number(d.deltaQty) !== 0; })
            .map(function (d) {
                return { checkDetailId: d.checkDetailId, deltaQty: Number(d.deltaQty) };
            });
        fetch(window.location.pathname, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ action: 'adjust', checkId: ck, adjustmentsJson: JSON.stringify(adjustments) })
        })
        .then(function (r) { return r.json(); })
        .then(function (data) {
            if (data && data.success) loadChecks();
            else alert('Lỗi: ' + (data && data.message ? data.message : 'Không rõ'));
        });
    };

    // Inline edit quantity: persist the single row update immediately
    window.handleUpdateCountedQty = function (checkId, detailId, value) {
        var ck = Number(checkId);
        var details = itemsBySheetId[ck];
        if (!details) return;
        var d = details.find(function (x) { return x.checkDetailId === Number(detailId); });
        if (!d) return;
        d.actualQty = (value === '' || value == null) ? null : Number(value);
        d.deltaQty = (d.actualQty == null) ? null : (Number(d.actualQty) - Number(d.systemQty));
        fetch(window.location.pathname, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                action: 'submit',
                checkId: ck,
                resultsJson: JSON.stringify([{ checkDetailId: d.checkDetailId, actualQty: d.actualQty != null ? d.actualQty : 0 }])
            })
        });
        render();
    };

    // ─── Data loaders ───
    function loadChecks() {
        var url = window.location.pathname + '?ajax=1&action=checks';
        dbg('loadChecks: fetching', url);
        
        fetch(url, { method: 'GET', headers: { 'Accept': 'application/json' } })
            .then(function (r) {
                dbg('loadChecks: response status', r.status);
                dbg('loadChecks: content-type', r.headers.get('Content-Type'));
                if (!r.ok) {
                    throw new Error('HTTP ' + r.status + ' ' + r.statusText);
                }
                return r.text().then(function(text) {
                    dbg('loadChecks: raw response', text.substring(0, 200) + (text.length > 200 ? '...' : ''));
                    try {
                        return JSON.parse(text);
                    } catch(e) {
                        throw new Error('Invalid JSON: ' + e.message + '\nResponse: ' + text.substring(0, 500));
                    }
                });
            })
            .then(function (data) {
                dbgObj('loadChecks: parsed data', data);
                if (Array.isArray(data)) {
                    sheets = data;
                    dbg('loadChecks: loaded', sheets.length + ' inventory checks');
                } else if (data && typeof data === 'object') {
                    // Handle error response
                    dbg('loadChecks: received object instead of array', data);
                    if (data.success === false) {
                        sheetsContainer.innerHTML = '<div class="ic-empty">Lỗi: ' + (data.message || 'Không rõ') + '</div>';
                        return;
                    }
                    sheets = [];
                } else {
                    dbg('loadChecks: unexpected data type', typeof data);
                    sheets = [];
                }
                itemsBySheetId = {};
                render();
                if (expandedSheetId != null) {
                    loadDetails(expandedSheetId);
                }
            })
            .catch(function (err) {
                console.error('[inventory-check] loadChecks failed:', err);
                sheetsContainer.innerHTML = '<div class="ic-empty">Lỗi tải danh sách: ' + err.message +
                    '<br><small>Xem Console (F12) để biết chi tiết.</small></div>';
            });
    }

    function loadDetails(checkId) {
        var url = window.location.pathname + '?ajax=1&action=checkDetails&checkId=' + encodeURIComponent(checkId);
        dbg('loadDetails: fetching', url);
        fetch(url)
            .then(function (r) {
                dbg('loadDetails: status', r.status);
                if (!r.ok) throw new Error('HTTP ' + r.status);
                return r.json();
            })
            .then(function (data) {
                dbgObj('loadDetails: data', data);
                if (Array.isArray(data)) {
                    itemsBySheetId[checkId] = data;
                } else if (data && data.success === false) {
                    console.error('loadDetails error:', data.message);
                    itemsBySheetId[checkId] = [];
                } else {
                    itemsBySheetId[checkId] = [];
                }
                render();
            })
            .catch(function (err) { console.error('loadDetails failed:', err); });
    }

    // ─── Render ───
    function render() {
        // Stats
        var counts = {
            all:        sheets.length,
            progress:   sheets.filter(function (s) { return s.status === 'DRAFT' || s.status === 'IN_PROGRESS'; }).length,
            approved:   sheets.filter(function (s) { return s.status === 'APPROVED'; }).length
        };
        counts.completed = 0; // No "completed" status in current schema (handled together with progress)

        // Stats - with null checks for safety
        var statTotalEl = document.getElementById('statTotal');
        var statPendingEl = document.getElementById('statPending');
        var statCompletedEl = document.getElementById('statCompleted');
        var statApprovedEl = document.getElementById('statApproved');
        var badgeAllEl = document.getElementById('badge-all');
        var badgeProgressEl = document.getElementById('badge-progress');
        var badgeApprovedEl = document.getElementById('badge-approved');

        if (statTotalEl) statTotalEl.textContent = counts.all;
        if (statPendingEl) statPendingEl.textContent = counts.progress;
        if (statCompletedEl) statCompletedEl.textContent = counts.completed;
        if (statApprovedEl) statApprovedEl.textContent = counts.approved;
        if (badgeAllEl) badgeAllEl.textContent = counts.all;
        if (badgeProgressEl) badgeProgressEl.textContent = counts.progress;
        if (badgeApprovedEl) badgeApprovedEl.textContent = counts.approved;

        // Filter
        var q = searchText.toLowerCase();
        var filtered = sheets.filter(function (s) {
            var matchTab = activeTab === 'all' ||
                (activeTab === 'in_progress' && (s.status === 'DRAFT' || s.status === 'IN_PROGRESS')) ||
                (activeTab === 'completed' && false) ||
                (activeTab === 'approved' && s.status === 'APPROVED');

            var haystack = (s.checkCode + ' ' + (s.warehouseName || '') + ' ' + (s.note || '')).toLowerCase();
            var matchSearch = !q || haystack.indexOf(q) >= 0;
            return matchTab && matchSearch;
        });

        if (filtered.length === 0) {
            sheetsContainer.innerHTML = '<div class="ic-empty">Không tìm thấy phiếu kiểm kê nào phù hợp.</div>';
            return;
        }

        sheetsContainer.innerHTML = filtered.map(function (sheet) {
            var sc = getStatusConfig(sheet.status);
            var sheetKey = sheet.checkId;
            var isExpanded = expandedSheetId === sheetKey;
            var items = itemsBySheetId[sheetKey] || [];
            var totalItems = sheet.totalItems || items.length;
            var countedItems = sheet.countedItems != null ? sheet.countedItems : items.filter(function (i) { return i.actualQty != null; }).length;
            var totalDelta = Number(sheet.totalDelta || 0);
            var hasDiscrepancy = (typeof totalDelta === 'number' && totalDelta !== 0);

            // Action button
            var actionBtn = '';
            if (sheet.status === 'DRAFT' || sheet.status === 'IN_PROGRESS') {
                actionBtn = '<button class="ic-btn-action ic-btn-action--orange" onclick="triggerComplete(event, ' + sheetKey + ')">' +
                            'Hoàn tất</button>';
            }

            // Delta text styling
            var deltaClass = 'ic-sheet-stat-val text-emerald-600';
            if (totalDelta < 0) deltaClass = 'ic-sheet-stat-val text-red-600';
            else if (totalDelta > 0) deltaClass = 'ic-sheet-stat-val text-blue-600';

            // Items table rows
            var tableRows = items.map(function (item) {
                var d = (item.actualQty != null && item.systemQty != null)
                        ? (Number(item.actualQty) - Number(item.systemQty)) : null;
                var rowClass = (d !== null && d !== 0) ? 'row-discrepancy' : '';

                var countField = '';
                if (item.actualQty != null) {
                    countField = '<span style="font-size:13px; font-weight:700;">' + Number(item.actualQty).toLocaleString() + '</span>';
                } else if (sheet.status === 'DRAFT' || sheet.status === 'IN_PROGRESS') {
                    countField = '<input class="ic-input-count" type="number" placeholder="Nhập..." ' +
                                 'onchange="handleUpdateCountedQty(' + sheetKey + ', ' + item.checkDetailId + ', this.value)"/>';
                } else {
                    countField = '<span style="color:rgba(16,55,92,0.30);">—</span>';
                }

                var statusIcon = '';
                if (item.actualQty != null) {
                    if (d === 0) {
                        statusIcon = '<svg class="text-emerald-500 mx-auto" style="width:16px;height:16px;" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"/></svg>';
                    } else {
                        statusIcon = '<svg class="text-orange mx-auto" style="width:16px;height:16px;" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3Z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>';
                    }
                } else {
                    statusIcon = '<div class="w-4 h-4 border-2 border-navy/20 rounded mx-auto"></div>';
                }

                return '<tr id="row-' + sheetKey + '-' + item.checkDetailId + '" class="' + rowClass + '">' +
                       '<td><div class="ic-sku-code"><svg style="width:14px;height:14px;" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/><polyline points="3.27 6.96 12 12.01 20.73 6.96"/><line x1="12" y1="22.08" x2="12" y2="12"/></svg>' + esc(item.skuCode) + '</div></td>' +
                       '<td><div class="ic-sku-name">' + esc(item.productName) + '</div></td>' +
                       '<td class="text-right ic-system-qty">' + Number(item.systemQty).toLocaleString() + '</td>' +
                       '<td class="text-right">' + countField + '</td>' +
                       '<td class="text-right" id="dtcell-' + sheetKey + '-' + item.checkDetailId + '">' + renderDeltaBadge(d) + '</td>' +
                       '<td class="text-center" id="iconcell-' + sheetKey + '-' + item.checkDetailId + '">' + statusIcon + '</td>' +
                       '</tr>';
            }).join('');

            var expandedClass = isExpanded ? 'expanded' : '';
            var expandSection = '';
            if (isExpanded) {
                expandSection = '<div class="ic-sheet-body">' +
                                '<table class="ic-table">' +
                                '<thead>' +
                                '<tr>' +
                                '<th class="text-left">SKU</th>' +
                                '<th class="text-left">Tên sản phẩm</th>' +
                                '<th class="text-right">Hệ thống</th>' +
                                '<th class="text-right">Đếm thực tế</th>' +
                                '<th class="text-right">Delta (Δ)</th>' +
                                '<th class="text-center">Trạng thái</th>' +
                                '</tr>' +
                                '</thead>' +
                                '<tbody>' + (items.length > 0 ? tableRows : '<tr><td colspan="6" style="text-align:center;color:rgba(16,55,92,0.4);padding:20px;">Đang tải chi tiết...</td></tr>') + '</tbody>' +
                                '</table>' +
                                '</div>';
            }

            var createdAtStr = sheet.createdAt ? sheet.createdAt.replace('T', ' ').substring(0, 16) : '';

            return '<div class="ic-sheet-card ' + expandedClass + '">' +
                   '  <div class="ic-sheet-hd" onclick="toggleExpand(' + sheetKey + ')">' +
                   '    <div class="ic-sheet-icon" style="background: rgba(16,55,92,0.05);">' +
                   '      <svg class="text-navy" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 20h9"/><path d="M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4Z"/></svg>' +
                   '    </div>' +
                   '    <div class="ic-sheet-info">' +
                   '      <div class="ic-sheet-info-row">' +
                   '        <span class="ic-sheet-id">' + esc(sheet.checkCode) + '</span>' +
                   '        <span class="ic-badge ' + sc.cls + '"><span class="ic-badge__dot"></span>' + esc(sc.label) + '</span>' +
                   '        <span class="ic-badge ic-badge--discrepancy" id="alert-' + sheetKey + '" style="display:' + (hasDiscrepancy ? 'inline-flex' : 'none') + ';"><svg style="width:10px;height:10px;margin-right:4px;" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3Z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>Có lệch</span>' +
                   '      </div>' +
                   '      <div class="ic-sheet-title">' + esc(sheet.warehouseName || ('Kho #' + sheet.warehouseId)) + ' — ' + esc(sheet.note || 'Kiểm kê định kỳ') + '</div>' +
                   '      <div class="ic-sheet-meta">' + esc(sheet.creatorName || 'N/A') + ' · ' + esc(createdAtStr) + '</div>' +
                   '    </div>' +
                   '    <div class="ic-sheet-stats">' +
                   '      <div class="ic-sheet-stat">' +
                   '        <div class="ic-sheet-stat-lbl">Đã đếm</div>' +
                   '        <div class="ic-sheet-stat-val">' +
                   '          <span id="counted-' + sheetKey + '" class="' + (countedItems === totalItems && totalItems > 0 ? 'text-emerald-600' : 'text-navy') + '">' + countedItems + '</span>' +
                   '          <span style="color:rgba(16,55,92,0.30); font-size:12px;">/' + totalItems + '</span>' +
                   '        </div>' +
                   '      </div>' +
                   '      <div class="ic-sheet-stat">' +
                   '        <div class="ic-sheet-stat-lbl">Tổng lệch</div>' +
                   '        <div id="delta-' + sheetKey + '" class="' + deltaClass + '">' + (totalDelta > 0 ? ('+' + totalDelta) : totalDelta) + '</div>' +
                   '      </div>' +
                   '    </div>' +
                   '    <div style="margin-right:16px; flex-shrink:0;">' + actionBtn + '</div>' +
                   '    <svg class="ic-chevron" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="m6 9 6 6 6-6"/></svg>' +
                   '  </div>' +
                      expandSection +
                   '</div>';
        }).join('');
    }

    function esc(v) {
        if (v == null) return '';
        return String(v)
            .replace(/&/g,'&amp;').replace(/</g,'&lt;')
            .replace(/>/g,'&gt;').replace(/"/g,'&quot;')
            .replace(/'/g,'&#039;');
    }

    // ─── Init ───
    loadChecks();
})();
</script>
