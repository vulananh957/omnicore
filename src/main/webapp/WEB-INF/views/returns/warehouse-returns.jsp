<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>

<link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/returns--warehouse-returns.css"/>

<style>
    /* ─── Stats Grid ─── */
    .ret-stats-grid {
        display: grid;
        grid-template-columns: repeat(4, 1fr);
        gap: 16px;
        margin-bottom: 24px;
    }
    @media (max-width: 900px) {
        .ret-stats-grid { grid-template-columns: repeat(2, 1fr); }
    }
    .ret-stat-card {
        background: #fff;
        border: 1px solid var(--border);
        border-radius: var(--radius-card);
        padding: 16px 20px;
        display: flex;
        align-items: center;
        gap: 16px;
    }
    .ret-stat-icon {
        width: 40px; height: 40px;
        border-radius: var(--radius-btn);
        display: flex; align-items: center; justify-content: center;
        flex-shrink: 0;
    }
    .ret-stat-icon svg { width: 20px; height: 20px; }
    .ret-stat-value {
        font-size: 22px; font-weight: 800; color: var(--navy);
        letter-spacing: -0.03em; line-height: 1;
    }
    .ret-stat-label {
        font-size: 11px; color: rgba(16,55,92,0.50); font-weight: 500; margin-top: 2px;
    }

    /* ─── Attention Banner ─── */
    .ret-alert-banner {
        display: flex; align-items: center; gap: 12px;
        padding: 12px 16px; background: rgba(255,186,8,0.20);
        border: 1px solid rgba(255,186,8,0.5);
        border-radius: var(--radius-card); margin-bottom: 16px;
    }
    .ret-alert-banner svg { width: 16px; height: 16px; color: var(--orange); flex-shrink: 0; }
    .ret-alert-banner p { margin: 0; font-size: 12px; color: var(--navy); font-weight: 500; }

    /* ─── Toolbar ─── */
    .ret-toolbar {
        background: #fff; border: 1px solid var(--border);
        border-radius: var(--radius-card);
        padding: 14px 16px;
        display: flex; align-items: center; gap: 12px;
        margin-bottom: 16px;
    }
    .ret-search-wrap { position: relative; flex: 1; }
    .ret-search-icon {
        position: absolute; left: 12px; top: 50%; transform: translateY(-50%);
        width: 14px; height: 14px; color: rgba(16,55,92,0.30); pointer-events: none;
    }
    .ret-search-input {
        width: 100%; padding: 8px 14px 8px 36px;
        background: var(--alice); border: 1px solid var(--border);
        border-radius: calc(var(--radius-btn) - 2px);
        font-size: 13px; color: var(--navy); outline: none;
    }
    .ret-search-input::placeholder { color: rgba(16,55,92,0.30); }
    .ret-search-input:focus { border-color: rgba(16,55,92,0.30); }
    .btn-filter-ret {
        display: flex; align-items: center; gap: 8px;
        padding: 8px 16px;
        background: var(--alice); color: rgba(16,55,92,0.7);
        border: 1px solid var(--border); border-radius: calc(var(--radius-btn) - 2px);
        font-size: 13px; font-weight: 600; cursor: pointer; white-space: nowrap;
        transition: all .15s;
    }
    .btn-filter-ret:hover { color: var(--navy); background: rgba(16,55,92,0.04); }
    .btn-filter-ret svg { width: 14px; height: 14px; }

    /* ─── Status Tabs ─── */
    .ret-tabs {
        background: #fff; border: 1px solid var(--border);
        border-radius: var(--radius-card);
        padding: 4px; display: flex; gap: 4px;
        margin-bottom: 16px;
    }
    .ret-tab {
        display: flex; align-items: center; gap: 8px;
        padding: 8px 16px;
        border: none; background: none; cursor: pointer;
        font-size: 12px; font-weight: 600; color: rgba(16,55,92,0.50);
        border-radius: calc(var(--radius-btn) - 4px);
        transition: all .15s;
    }
    .ret-tab.active { background: var(--navy); color: #fff; }
    .ret-tab:not(.active):hover { color: var(--navy); }
    .ret-tab-badge {
        padding: 1px 6px; border-radius: 999px;
        font-size: 10px; font-weight: 700;
    }
    .ret-tab.active .ret-tab-badge { background: rgba(255,255,255,.20); color: #fff; }
    .ret-tab:not(.active) .ret-tab-badge { background: rgba(16,55,92,0.08); color: rgba(16,55,92,0.60); }

    /* ─── Collapsible Sheet List ─── */
    .ret-list-container {
        display: flex; flex-direction: column; gap: 12px;
    }
    .ret-sheet-card {
        background: #fff; border: 1px solid var(--border);
        border-radius: var(--radius-card); overflow: hidden;
        transition: border-color .15s;
    }
    .ret-sheet-hd {
        display: flex; align-items: center; gap: 16px;
        padding: 16px 20px; cursor: pointer;
        transition: background .12s;
    }
    .ret-sheet-hd:hover { background: rgba(var(--alice-rgb, 240,245,250), .4); }
    .ret-sheet-icon {
        width: 40px; height: 40px; border-radius: var(--radius-btn);
        display: flex; align-items: center; justify-content: center;
        flex-shrink: 0;
    }
    .ret-sheet-icon svg { width: 18px; height: 18px; }
    .ret-sheet-info { flex: 1; min-width: 0; }
    .ret-sheet-info-row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
    .ret-sheet-id { font-size: 14px; font-weight: 700; color: var(--navy); }
    .ret-sheet-so-ref { font-size: 11px; color: rgba(16,55,92,0.40); }
    .ret-sheet-customer { font-size: 12px; color: rgba(16,55,92,0.60); margin-top: 4px; }
    .ret-sheet-meta { font-size: 11px; color: rgba(16,55,92,0.35); margin-top: 2px; }
    
    .ret-sheet-stats {
        display: flex; align-items: center; gap: 20px; flex-shrink: 0;
        margin-right: 16px;
    }
    .ret-sheet-stat { text-align: right; }
    .ret-sheet-stat-lbl { font-size: 10px; font-weight: 700; text-transform: uppercase; color: rgba(16,55,92,0.40); }
    .ret-sheet-stat-val { font-size: 14px; font-weight: 700; color: var(--navy); margin-top: 2px; }
    
    /* Badges */
    .ret-badge {
        display: inline-flex; align-items: center; gap: 5px;
        padding: 3px 10px; border-radius: 20px;
        font-size: 10px; font-weight: 700;
    }
    .ret-badge__dot { width: 5px; height: 5px; border-radius: 50%; }
    
    .ret-badge--pending_qc { background: rgba(255,186,8,.20); color: #c2410c; }
    .ret-badge--pending_qc .ret-badge__dot { background: #fbbf24; }
    
    .ret-badge--qc_done { background: rgba(26,115,232,0.08); color: #1a73e8; }
    .ret-badge--qc_done .ret-badge__dot { background: #1a73e8; }
    
    .ret-badge--restocked { background: #ecfdf5; color: #065f46; }
    .ret-badge--restocked .ret-badge__dot { background: #10b981; }
    
    .ret-badge--scrapped { background: #fef2f2; color: #991b1b; }
    .ret-badge--scrapped .ret-badge__dot { background: #ef4444; }

    .ret-badge--channel {
        border-radius: 20px; padding: 2px 8px; font-size: 10px; font-weight: 600;
    }

    .ret-badge--resalable { background: #ecfdf5; color: #065f46; border: 1px solid #d1fae5; }
    .ret-badge--defective { background: #fef2f2; color: #991b1b; border: 1px solid #fee2e2; }
    .ret-badge--pending_item { background: rgba(16,55,92,.08); color: rgba(16,55,92,.60); }
    
    .ret-btn-action {
        padding: 6px 12px; border: none; cursor: pointer;
        background: var(--navy); color: #fff;
        font-size: 12px; font-weight: 700;
        border-radius: calc(var(--radius-btn) - 4px);
        transition: opacity .12s;
        white-space: nowrap;
    }
    .ret-btn-action:hover { opacity: .88; }
    .ret-btn-action--orange { background: var(--orange); }
    .ret-btn-action--white { background: #fff; border: 1px solid var(--border); color: rgba(16,55,92,0.7); }
    .ret-btn-action--white:hover { color: var(--navy); background: var(--alice); }
    
    .ret-chevron {
        width: 16px; height: 16px; color: rgba(16,55,92,0.30);
        transition: transform .15s;
    }
    .ret-sheet-card.expanded .ret-chevron { transform: rotate(180deg); }

    /* Collapsible Body / Table */
    .ret-sheet-body { border-top: 1px solid var(--border); display: none; }
    .ret-sheet-card.expanded .ret-sheet-body { display: block; }
    
    .ret-table { width: 100%; border-collapse: collapse; }
    .ret-table thead tr { background: var(--alice); border-bottom: 1px solid var(--border); }
    .ret-table thead th {
        padding: 8px 16px;
        font-size: 10px; font-weight: 700; text-transform: uppercase;
        letter-spacing: .08em; color: rgba(16,55,92,0.40);
        white-space: nowrap;
    }
    .ret-table thead th.text-right { text-align: right; }
    .ret-table tbody tr { border-bottom: 1px solid var(--border); transition: background .12s; }
    .ret-table tbody tr:last-child { border-bottom: none; }
    .ret-table tbody td { padding: 10px 16px; font-size: 12px; color: var(--navy); }
    
    .ret-sku-code { font-family: monospace; font-size: 10px; color: rgba(16,55,92,0.60); display: flex; align-items: center; gap: 6px; }
    .ret-sku-name { font-weight: 600; color: var(--navy); }

    /* ─── Modals ─── */
    .ret-overlay {
        position: fixed; inset: 0;
        background: rgba(16,55,92,0.40);
        backdrop-filter: blur(4px);
        display: flex; align-items: center; justify-content: center;
        z-index: 1000; padding: 16px;
    }
    .ret-modal {
        background: #fff; width: 100%; max-width: 600px;
        border-radius: var(--radius-card);
        box-shadow: 0 25px 50px rgba(16,55,92,.20);
        display: flex; flex-direction: column;
        max-height: 85vh; overflow: hidden;
    }
    .ret-modal-hd {
        display: flex; align-items: center; justify-content: space-between;
        padding: 16px 24px; border-bottom: 1px solid var(--border);
        background: #fff;
    }
    .ret-modal-hd h2 {
        font-size: 16px; font-weight: 800; color: var(--navy); margin: 0;
    }
    .ret-modal-hd p { font-size: 12px; color: rgba(16,55,92,0.40); margin: 2px 0 0; }
    .ret-modal-close {
        background: none; border: none; cursor: pointer;
        font-size: 22px; line-height: 1; color: rgba(16,55,92,0.40);
    }
    .ret-modal-close:hover { color: var(--navy); }
    
    .ret-modal-body {
        padding: 20px 24px; overflow-y: auto; flex: 1;
        display: flex; flex-direction: column; gap: 16px;
    }
    
    .ret-qc-item-card {
        padding: 16px; background: var(--alice);
        border-radius: calc(var(--radius-btn) - 2px);
    }
    .ret-qc-item-meta { font-size: 11px; font-family: monospace; color: rgba(16,55,92,0.60); }
    .ret-qc-item-title { font-size: 13px; font-weight: 700; color: var(--navy); margin-top: 4px; }
    .ret-qc-item-reason { font-size: 11px; color: rgba(16,55,92,0.50); margin-top: 4px; }
    
    .ret-qc-btn-row { display: flex; gap: 10px; margin: 12px 0; }
    .ret-qc-btn {
        flex: 1; display: flex; align-items: center; justify-content: center; gap: 8px;
        padding: 10px; border: 2px solid #E5EAF3; background: #fff;
        font-size: 12px; font-weight: 600; color: rgba(16,55,92,0.50);
        cursor: pointer; border-radius: calc(var(--radius-btn) - 2px);
        transition: all .15s;
    }
    .ret-qc-btn svg { width: 14px; height: 14px; }
    .ret-qc-btn--good.active { border-color: #10b981; background: #ecfdf5; color: #065f46; }
    .ret-qc-btn--good:not(.active):hover { border-color: #a7f3d0; }
    
    .ret-qc-btn--bad.active { border-color: #ef4444; background: #fef2f2; color: #991b1b; }
    .ret-qc-btn--bad:not(.active):hover { border-color: #fca5a5; }
    
    .ret-qc-label { font-size: 11px; font-weight: 600; color: rgba(16,55,92,0.50); margin-bottom: 4px; display: block; }
    .ret-qc-input {
        width: 100%; padding: 8px 12px; border: 1px solid #E5EAF3;
        border-radius: calc(var(--radius-btn) - 4px); font-size: 12px;
        color: var(--navy); outline: none;
    }
    .ret-qc-input:focus { border-color: rgba(16,55,92,0.40); }

    .ret-modal-ft {
        display: flex; align-items: center; justify-content: flex-end;
        gap: 10px; padding: 14px 24px;
        border-top: 1px solid var(--border); background: #fff;
    }

    /* ─── Printed Document Layout ─── */
    .ret-print-modal { max-width: 950px; max-height: 95vh; }
    .ret-print-body { padding: 32px; background: #fff; overflow-y: auto; flex: 1; }
    .ret-print-grid-2 { display: grid; grid-template-columns: 1fr 1fr; gap: 32px; }
    .ret-print-grid-3 { display: grid; grid-template-columns: repeat(3, 1fr); gap: 24px; }
    .ret-print-grid-4 { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; }
    
    .ret-print-label-row { font-size: 11px; font-weight: 700; text-transform: uppercase; color: rgba(16,55,92,0.40); letter-spacing: 0.05em; margin-bottom: 6px; }
    .ret-print-val { font-size: 15px; font-weight: 700; color: var(--navy); }
    
    .ret-print-box { padding: 16px; border-radius: var(--radius-btn); }
    .ret-print-box--red { background: #fef2f2; border: 1px solid #fca5a5; color: #991b1b; }
    
    .ret-print-table { width: 100%; border-collapse: collapse; border: 2px solid rgba(16,55,92,0.20); }
    .ret-print-table th, .ret-print-table td { border: 1px solid rgba(16,55,92,0.20); padding: 8px 12px; font-size: 12px; color: var(--navy); }
    .ret-print-table th { background: var(--alice); font-weight: 700; text-transform: uppercase; font-size: 10px; letter-spacing: 0.08em; color: rgba(16,55,92,0.50); }
    .ret-print-table tr.total-row { background: rgba(16,55,92,0.03); font-weight: 700; }
    .ret-print-table td.bg-good { background: #ecfdf5; color: #065f46; }
    .ret-print-table td.bg-bad { background: #fef2f2; color: #991b1b; }

    .ret-empty {
        text-align: center; padding: 50px 20px;
        font-size: 13px; color: rgba(16,55,92,0.40);
        background: #fff; border: 1px solid var(--border);
        border-radius: var(--radius-card);
    }

    /* ─── Create Return Button ─── */
    .ret-btn-create {
        display: flex; align-items: center; gap: 8px;
        padding: 9px 18px;
        background: linear-gradient(135deg, #f97316 0%, #ea580c 100%);
        color: #fff;
        border: none; border-radius: calc(var(--radius-btn) - 2px);
        font-size: 13px; font-weight: 700; cursor: pointer; white-space: nowrap;
        box-shadow: 0 4px 14px rgba(234,88,12,.35);
        transition: all .18s ease;
        letter-spacing: .01em;
    }
    .ret-btn-create:hover {
        background: linear-gradient(135deg, #ea580c 0%, #c2410c 100%);
        box-shadow: 0 6px 20px rgba(234,88,12,.50);
        transform: translateY(-1px);
    }
    .ret-btn-create:active { transform: translateY(0); box-shadow: 0 2px 8px rgba(234,88,12,.30); }
    .ret-btn-create svg { width: 15px; height: 15px; transition: transform .18s; }
    .ret-btn-create:hover svg { transform: rotate(90deg); }

    .ret-form-section-title {
        font-size: 12px; font-weight: 700; text-transform: uppercase;
        letter-spacing: .06em; color: var(--navy);
        padding-bottom: 8px; border-bottom: 1px solid rgba(16,55,92,0.10);
        margin-bottom: 12px;
    }
    .ret-form-row { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
    .ret-form-group { display: flex; flex-direction: column; gap: 6px; }
    .ret-form-label {
        font-size: 11px; font-weight: 600; text-transform: uppercase;
        color: rgba(16,55,92,0.60); letter-spacing: .04em;
    }
    .ret-input, .ret-select, .ret-textarea {
        padding: 8px 12px;
        border: 1px solid var(--border);
        border-radius: calc(var(--radius-btn) - 2px);
        font-size: 13px; color: var(--navy);
        background: #fff; outline: none; width: 100%;
        font-family: inherit;
    }
    .ret-select { background: var(--alice); cursor: pointer; }
    .ret-input:focus, .ret-select:focus, .ret-textarea:focus { border-color: rgba(16,55,92,0.30); }
</style>

<!-- ═══ STATS ROW ═══ -->
<div class="ret-stats-grid">
    <!-- Chờ kiểm QC -->
    <div class="ret-stat-card">
        <div class="ret-stat-icon" style="background: rgba(255,186,8,0.20);">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
                 stroke="var(--orange)" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/>
            </svg>
        </div>
        <div>
            <div class="ret-stat-value" id="statPendingQC" style="color: var(--orange);">0</div>
            <div class="ret-stat-label">Chờ kiểm QC</div>
        </div>
    </div>
    <!-- Đang QC — Chờ xử lý -->
    <div class="ret-stat-card">
        <div class="ret-stat-icon" style="background: rgba(26,115,232,0.08);">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
                 stroke="#1a73e8" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
            </svg>
        </div>
        <div>
            <div class="ret-stat-value" id="statQCDone" style="color: #1a73e8;">0</div>
            <div class="ret-stat-label">Đang QC — Chờ xử lý</div>
        </div>
    </div>
    <!-- Hàng tốt — Nhập lại -->
    <div class="ret-stat-card">
        <div class="ret-stat-icon" style="background: #ecfdf5;">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
                 stroke="#059669" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <polyline points="17 10 12 15 7 10"/><line x1="12" y1="15" x2="12" y2="3"/><path d="M20 17a2 2 0 0 1 2 2v2a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2v-2a2 2 0 0 1 2-2"/>
            </svg>
        </div>
        <div>
            <div class="ret-stat-value" id="statResalable" style="color: #059669;">0</div>
            <div class="ret-stat-label">Hàng tốt — Nhập lại</div>
        </div>
    </div>
    <!-- Hàng hỏng — Phế phẩm -->
    <div class="ret-stat-card">
        <div class="ret-stat-icon" style="background: #fef2f2;">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
                 stroke="#991b1b" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14H6L5 6"/><path d="M10 11v6"/><path d="M14 11v6"/><path d="M9 6V4h6v2"/>
            </svg>
        </div>
        <div>
            <div class="ret-stat-value" id="statDefective" style="color: #991b1b;">0</div>
            <div class="ret-stat-label">Hàng hỏng — Phế phẩm</div>
        </div>
    </div>
</div>

<!-- ═══ ATTENTION BANNER ═══ -->
<div class="ret-alert-banner" id="attentionBanner" style="display:none;">
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3Z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>
    <p id="attentionText">phiếu hàng hoàn chưa được kiểm QC. Xử lý sớm để cập nhật tồn kho chính xác.</p>
</div>

<!-- ═══ TOOLBAR ═══ -->
<div class="ret-toolbar">
    <div class="ret-search-wrap">
        <svg class="ret-search-icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"
             fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="11" cy="11" r="8"/><path d="m21 21-4.35-4.35"/>
        </svg>
        <input class="ret-search-input" type="text" id="retSearch"
               placeholder="Tìm mã RMA, mã SO, tên khách hàng..."/>
    </div>
    <button class="btn-filter-ret" style="margin-right: 4px;">
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
             stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <polygon points="22 3 2 3 10 12.46 10 19 14 21 14 12.46 22 3"/>
        </svg>
        Bộ lọc
    </button>

</div>

<!-- ═══ STATUS TABS ═══ -->
<div class="ret-tabs" id="retTabs">
    <button class="ret-tab active" data-tab="all">
        Tất cả <span class="ret-tab-badge" id="badge-all">0</span>
    </button>
    <button class="ret-tab" data-tab="pending_qc">
        Chờ kiểm QC <span class="ret-tab-badge" id="badge-pending">0</span>
    </button>
    <button class="ret-tab" data-tab="qc_done">
        Đang QC — Chờ xử lý <span class="ret-tab-badge" id="badge-qcdone">0</span>
    </button>
    <button class="ret-tab" data-tab="restocked">
        Đã nhập lại <span class="ret-tab-badge" id="badge-restocked">0</span>
    </button>
    <button class="ret-tab" data-tab="scrapped">
        Kho hỏng <span class="ret-tab-badge" id="badge-scrapped">0</span>
    </button>
</div>

<!-- ═══ RMA LIST CONTAINER ═══ -->
<div class="ret-list-container" id="returnsContainer">
    <!-- Rendered by JS -->
</div>

<!-- ═══ MODAL: KIỂM QC ═══ -->
<div class="ret-overlay" id="qcOverlay" style="display:none;">
    <div class="ret-modal" id="qcModal">
        <div class="ret-modal-hd sticky-top">
            <div>
                <h2>Kiểm tra chất lượng (QC)</h2>
                <p id="qcModalSubtitle">RMA-2026-XXXX</p>
            </div>
            <button class="ret-modal-close" id="btnCloseQC">×</button>
        </div>
        <div class="ret-modal-body" id="qcModalBody">
            <!-- populated by JS -->
        </div>
        <div class="ret-modal-ft">
            <button class="ret-btn-action ret-btn-action--white" id="btnCancelQC">Hủy</button>
            <button class="ret-btn-action ret-btn-action--orange" id="btnConfirmQC">Xác nhận kết quả QC</button>
        </div>
    </div>
</div>

<!-- ═══ MODAL: CHI TIẾT PHIẾU RMA IN ═══ -->
<div class="ret-overlay" id="printOverlay" style="display:none;" onclick="closePrintModal(event)">
    <div class="ret-modal ret-print-modal" id="printModal" onclick="event.stopPropagation()">
        <div class="ret-modal-hd" style="background: rgba(var(--alice-rgb,240,245,250),.3);">
            <div class="flex items-center gap-3">
                <h3 class="text-navy font-bold text-[16px]" style="margin:0;">Chi tiết Phiếu Nhận Hàng Hoàn / RMA</h3>
            </div>
            <div style="display:flex; align-items:center; gap:8px;">
                <button class="ret-btn-action ret-btn-action--white" onclick="window.print()">
                    In PDF
                </button>
                <button class="ret-btn-action ret-btn-action--white">
                    Xuất Excel
                </button>
                <button class="ret-modal-close" onclick="document.getElementById('printOverlay').style.display='none'">×</button>
            </div>
        </div>
        <div class="ret-print-body" id="printBody">
            <!-- populated by JS -->
        </div>
    </div>
</div>

<!-- ═══ MODAL: TẠO PHIẾU HÀNG HOÀN / RMA ═══ -->
<div class="ret-overlay" id="createReturnOverlay" style="display:none;">
    <div class="ret-modal" style="max-width: 650px;">
        <div class="ret-modal-hd">
            <div>
                <h2 style="font-size: 16px; font-weight: 800; color: var(--navy); margin: 0;">Tạo Phiếu Hàng Hoàn / RMA</h2>
                <p style="font-size: 12px; color: rgba(16,55,92,0.40); margin: 2px 0 0;">Tiếp nhận yêu cầu đổi trả, hoàn hàng từ các kênh bán</p>
            </div>
            <button class="ret-modal-close" id="btnCloseCreateReturn">×</button>
        </div>
        <div class="ret-modal-body">
            <!-- THÔNG TIN CHUNG -->
            <div>
                <div class="ret-form-section-title">Thông tin phiếu</div>
                <div class="ret-form-row">
                    <div class="ret-form-group">
                        <label class="ret-form-label">Mã SO gốc *</label>
                        <input class="ret-input" type="text" id="formSoRef" placeholder="Ví dụ: SO-2026-001390"/>
                    </div>
                    <div class="ret-form-group">
                        <label class="ret-form-label">Kênh bán hàng *</label>
                        <select class="ret-select" id="formChannel">
                            <c:forEach var="ch" items="${channels}">
                                <option value="${ch.channelName}">${ch.channelName}</option>
                            </c:forEach>
                        </select>
                    </div>
                </div>
                <div class="ret-form-row" style="margin-top: 12px;">
                    <div class="ret-form-group">
                        <label class="ret-form-label">Tên khách hàng *</label>
                        <input class="ret-input" type="text" id="formCustomer" placeholder="Tên khách hàng..."/>
                    </div>
                    <div class="ret-form-group">
                        <label class="ret-form-label">Số điện thoại *</label>
                        <input class="ret-input" type="text" id="formPhone" placeholder="Số điện thoại..."/>
                    </div>
                </div>
            </div>

            <!-- THÊM SẢN PHẨM HOÀN TRẢ -->
            <div>
                <div class="ret-form-section-title" style="margin-top: 16px;">Sản phẩm hoàn trả</div>
                <div class="ret-form-row">
                    <div class="ret-form-group">
                        <label class="ret-form-label">Sản phẩm (SKU) *</label>
                        <select class="ret-select" id="formItemSku">
                            <option value="">— Chọn sản phẩm (SKU) —</option>
                        </select>
                    </div>
                    <div class="ret-form-group">
                        <label class="ret-form-label">Số lượng *</label>
                        <input class="ret-input" type="number" id="formItemQty" min="1" value="1"/>
                    </div>
                </div>
                <div class="ret-form-group" style="margin-top: 12px;">
                    <label class="ret-form-label">Lý do hoàn trả *</label>
                    <input class="ret-input" type="text" id="formItemReason" placeholder="Mô tả lý do trả hàng (ví dụ: Nứt móp vỏ)..."/>
                </div>
                <div style="text-align: right; margin-top: 12px;">
                    <button class="ret-btn-action" type="button" id="btnAddItem">Thêm vào danh sách</button>
                </div>

                <!-- BẢNG TẠM CÁC ITEM SẼ HOÀN -->
                <div style="margin-top: 16px; max-height: 200px; overflow-y: auto;">
                    <table class="ret-table" style="border: 1px solid var(--border);">
                        <thead>
                            <tr style="background: var(--alice);">
                                <th style="padding: 6px 12px; font-size: 10px; text-align: left;">SKU</th>
                                <th style="padding: 6px 12px; font-size: 10px; text-align: left;">Tên sản phẩm</th>
                                <th style="padding: 6px 12px; font-size: 10px; text-align: right;">SL</th>
                                <th style="padding: 6px 12px; font-size: 10px; text-align: left;">Lý do</th>
                                <th style="padding: 6px 12px; font-size: 10px; text-align: center; width: 60px;">Xóa</th>
                            </tr>
                        </thead>
                        <tbody id="tempItemsTableBody">
                            <tr>
                                <td colspan="5" style="text-align: center; padding: 12px; color: rgba(16,55,92,0.40);">Chưa có sản phẩm nào được thêm</td>
                            </tr>
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
        <div class="ret-modal-ft">
            <button class="ret-btn-action ret-btn-action--white" id="btnCancelCreateReturn">Hủy</button>
            <button class="ret-btn-action" id="btnSubmitCreateReturn">Tạo phiếu hàng hoàn</button>
        </div>
    </div>
</div>

<script id="products-data" type="application/json">
[
    <c:forEach items="${products}" var="p" varStatus="status">
        { "sku": "${p.skuCode}", "name": "${p.skuName}" }${!status.last ? ',' : ''}
    </c:forEach>
]
</script>

<script id="returns-data" type="application/json">
[
    <c:forEach items="${returns}" var="r" varStatus="status">
        {
            "dbId": ${r.returnId},
            "id": "${r.returnCode}",
            "soRef": "${r.orderCode}",
            "channel": "${r.channel}",
            "customer": "${r.customerName}",
            "phone": "${r.customerPhone}",
            "returnedAt": "${r.createdAt}",
            "evidencePhotos": "${r.evidencePhotos}",
            "evidenceVideo": "${r.evidenceVideo}",
            "status": "${r.status eq 'RECEIVED' or r.status eq 'INSPECTING' ? 'pending_qc' : r.status eq 'PASS' or r.status eq 'FAIL' ? 'qc_done' : r.status eq 'RESTOCKED' ? 'restocked' : 'scrapped'}",
            "qcBy": "",
            "items": [
                <c:forEach items="${r.items}" var="item" varStatus="iStatus">
                    {
                        "productId": ${item.productId},
                        "skuCode": "${item.skuCode}",
                        "skuName": "${item.skuName}",
                        "qty": ${item.qty},
                        "unitPrice": ${item.unitPrice != null ? item.unitPrice : 0},
                        "returnReason": "${item.returnReason}",
                        "qcDecision": "${item.qcDecision}",
                        "qcNote": "${item.qcNote}"
                    }${!iStatus.last ? ',' : ''}
                </c:forEach>
            ]
        }${!status.last ? ',' : ''}
    </c:forEach>
]
</script>

<!-- ═══ JAVASCRIPT ═══ -->
<script>
(function () {
    'use strict';

    function submitPostAction(action, params) {
        var form = document.createElement('form');
        form.method = 'POST';
        form.action = window.location.pathname;

        var actionInput = document.createElement('input');
        actionInput.type = 'hidden';
        actionInput.name = 'action';
        actionInput.value = action;
        form.appendChild(actionInput);

        for (var key in params) {
            if (params.hasOwnProperty(key)) {
                var input = document.createElement('input');
                input.type = 'hidden';
                input.name = key;
                input.value = params[key];
                form.appendChild(input);
            }
        }

        document.body.appendChild(form);
        form.submit();
    }

    // ─── Master Product List (Empty as requested by the user, but populated dynamically from servlet)
    var PRODUCTS = JSON.parse(document.getElementById('products-data').textContent || '[]');

    // ─── Returns Data (Empty initial list, populated from servlet if available) ───
    var returnsDataEl = document.getElementById('returns-data');
    var returns = [];
    try {
        returns = JSON.parse(returnsDataEl.textContent || '[]');
        console.log('[DEBUG] returns loaded:', returns.length, returns);
    } catch (e) {
        console.error('[DEBUG] JSON parse error:', e);
        console.log('[DEBUG] raw data:', returnsDataEl.textContent);
    }

    var activeTab = 'all';
    var searchText = '';
    var expandedRmaId = null;

    // Temporary list for creating a return ticket
    var tempItems = [];

    // QC editing temp state
    var selectedQCId = null;
    var tempDecisions = {};
    var tempNotes = {};

    // ─── DOM Refs ───
    var returnsContainer = document.getElementById('returnsContainer');
    var retSearch        = document.getElementById('retSearch');
    var retTabs          = document.getElementById('retTabs');
    var qcOverlay        = document.getElementById('qcOverlay');
    var qcModalSubtitle  = document.getElementById('qcModalSubtitle');
    var qcModalBody      = document.getElementById('qcModalBody');
    var printOverlay     = document.getElementById('printOverlay');
    var printBody        = document.getElementById('printBody');
    
    var statPendingQC    = document.getElementById('statPendingQC');
    var statQCDone       = document.getElementById('statQCDone');
    var statResalable    = document.getElementById('statResalable');
    var statDefective    = document.getElementById('statDefective');
    var attentionBanner  = document.getElementById('attentionBanner');
    var attentionText    = document.getElementById('attentionText');

    // Create return modal DOM refs
    var createReturnOverlay = document.getElementById('createReturnOverlay');
    var formSoRef           = document.getElementById('formSoRef');
    var formChannel         = document.getElementById('formChannel');
    var formCustomer        = document.getElementById('formCustomer');
    var formPhone           = document.getElementById('formPhone');
    var formItemSku         = document.getElementById('formItemSku');
    var formItemQty         = document.getElementById('formItemQty');
    var formItemReason      = document.getElementById('formItemReason');
    var tempItemsTableBody  = document.getElementById('tempItemsTableBody');

    // ─── Populate Products select for creation modal ───
    function populateProductsSelect() {
        formItemSku.innerHTML = '<option value="">— Chọn sản phẩm (SKU) —</option>';
        PRODUCTS.forEach(function (p) {
            var opt = document.createElement('option');
            opt.value = p.sku;
            opt.textContent = p.sku + ' - ' + p.name;
            formItemSku.appendChild(opt);
        });
    }
    populateProductsSelect();

    // ─── Open / Close Create Return Modal ───
    var btnOpen = document.getElementById('btnOpenCreateReturn');
    if (btnOpen) {
        btnOpen.addEventListener('click', function () {
            formSoRef.value = '';
            formCustomer.value = '';
            formPhone.value = '';
            formItemSku.value = '';
            formItemQty.value = 1;
            formItemReason.value = '';
            tempItems = [];
            renderTempItemsTable();
            createReturnOverlay.style.display = 'flex';
        });
    }
    document.getElementById('btnCloseCreateReturn').addEventListener('click', function () { createReturnOverlay.style.display = 'none'; });
    document.getElementById('btnCancelCreateReturn').addEventListener('click', function () { createReturnOverlay.style.display = 'none'; });
    createReturnOverlay.addEventListener('click', function (e) { if (e.target === createReturnOverlay) createReturnOverlay.style.display = 'none'; });

    // ─── Add item to temporary returns list ───
    document.getElementById('btnAddItem').addEventListener('click', function () {
        var sku = formItemSku.value;
        var qty = parseInt(formItemQty.value, 10) || 0;
        var reason = formItemReason.value.trim();

        if (!sku) { alert('Vui lòng chọn sản phẩm!'); return; }
        if (qty <= 0) { alert('Số lượng phải lớn hơn 0!'); return; }
        if (!reason) { alert('Vui lòng nhập lý do hoàn trả!'); return; }

        var prod = PRODUCTS.find(function (p) { return p.sku === sku; });
        var name = prod ? prod.name : sku;

        // Check if item already added
        var existing = tempItems.find(function (i) { return i.skuCode === sku; });
        if (existing) {
            existing.qty += qty;
        } else {
            tempItems.push({
                skuCode: sku,
                skuName: name,
                qty: qty,
                returnReason: reason,
                qcDecision: 'pending'
            });
        }

        formItemSku.value = '';
        formItemQty.value = 1;
        formItemReason.value = '';
        renderTempItemsTable();
    });

    window.removeTempItem = function (sku) {
        tempItems = tempItems.filter(function (i) { return i.skuCode !== sku; });
        renderTempItemsTable();
    };

    function renderTempItemsTable() {
        if (tempItems.length === 0) {
            tempItemsTableBody.innerHTML = '<tr><td colspan="5" style="text-align: center; padding: 12px; color: rgba(16,55,92,0.40);">Chưa có sản phẩm nào được thêm</td></tr>';
            return;
        }
        tempItemsTableBody.innerHTML = tempItems.map(function (item) {
            return '<tr>' +
                   '  <td style="padding: 8px 12px; font-family: monospace; font-size: 11px;">' + esc(item.skuCode) + '</td>' +
                   '  <td style="padding: 8px 12px; font-weight: 600;">' + esc(item.skuName) + '</td>' +
                   '  <td style="padding: 8px 12px; text-align: right; font-weight: 700;">' + item.qty + '</td>' +
                   '  <td style="padding: 8px 12px; color: rgba(16,55,92,0.60);">' + esc(item.returnReason) + '</td>' +
                   '  <td style="padding: 8px 12px; text-align: center;">' +
                   '    <button type="button" class="ret-btn-action ret-btn-action--white" style="padding: 2px 6px; font-size: 10px;" onclick="removeTempItem(\'' + item.skuCode + '\')">Xóa</button>' +
                   '  </td>' +
                   '</tr>';
        }).join('');
    }

    // ─── Submit Return (RMA) ticket ───
    document.getElementById('btnSubmitCreateReturn').addEventListener('click', function () {
        var soRef = formSoRef.value.trim();
        var customer = formCustomer.value.trim();
        var phone = formPhone.value.trim();
        var channel = formChannel.value;

        if (!soRef) { alert('Vui lòng nhập mã SO gốc!'); return; }
        if (!customer) { alert('Vui lòng nhập tên khách hàng!'); return; }
        if (!phone) { alert('Vui lòng nhập số điện thoại khách hàng!'); return; }
        if (tempItems.length === 0) { alert('Vui lòng thêm ít nhất một sản phẩm hoàn trả!'); return; }

        var itemsPayload = tempItems.map(function(item) {
            return {
                skuCode: item.skuCode,
                qty: item.qty,
                returnReason: item.returnReason
            };
        });

        submitPostAction('create', {
            soRef: soRef,
            customer: customer,
            phone: phone,
            itemsJson: JSON.stringify(itemsPayload)
        });

        createReturnOverlay.style.display = 'none';
    });

    // ─── Modal QC & Confirm QC ───
    document.getElementById('btnCloseQC').addEventListener('click',  function () { qcOverlay.style.display = 'none'; });
    document.getElementById('btnCancelQC').addEventListener('click', function () { qcOverlay.style.display = 'none'; });
    document.getElementById('btnConfirmQC').addEventListener('click', function () {
        if (!selectedQCId) return;

        var rma = returns.find(function (r) { return r.id === selectedQCId; });
        if (!rma) return;

        // Apply decisions from temp state
        rma.items.forEach(function (item) {
            item.qcDecision = tempDecisions[item.skuCode] || 'pending';
            item.qcNote = tempNotes[item.skuCode] || '';
        });

        rma.status = 'qc_done';
        rma.qcBy = 'Nhân viên QC';
        qcOverlay.style.display = 'none';

        // Persist QC results to backend
        submitPostAction('qc', {
            returnId: rma.dbId,
            itemsJson: JSON.stringify(rma.items)
        });

        render();
    });

    // ─── Search & Tab Switching ───
    retSearch.addEventListener('input', function () { searchText = this.value; render(); });
    retTabs.addEventListener('click', function (e) {
        var btn = e.target.closest('.ret-tab');
        if (!btn) return;
        activeTab = btn.dataset.tab;
        retTabs.querySelectorAll('.ret-tab').forEach(function (t) { t.classList.remove('active'); });
        btn.classList.add('active');
        render();
    });

    // ─── Global Event Handling ───
    window.toggleRmaExpand = function (id) {
        expandedRmaId = expandedRmaId === id ? null : id;
        render();
    };

    window.openQCModal = function (e, id) {
        e.stopPropagation();
        var rma = returns.find(function (r) { return r.id === id; });
        if (!rma) return;

        selectedQCId = id;
        qcModalSubtitle.textContent = rma.id + ' · ' + rma.customer;
        
        // Load initial states into temp variables
        tempDecisions = {};
        tempNotes = {};
        rma.items.forEach(function (item) {
            tempDecisions[item.skuCode] = item.qcDecision;
            tempNotes[item.skuCode] = item.qcNote || '';
        });

        renderQCFormItems(rma);
        qcOverlay.style.display = 'flex';
    };

    window.setTempDecision = function (skuCode, decision) {
        tempDecisions[skuCode] = decision;
        
        // Refresh styles on active buttons
        var goodBtn = document.getElementById('qc-btn-good-' + skuCode);
        var badBtn  = document.getElementById('qc-btn-bad-' + skuCode);

        if (goodBtn && badBtn) {
            if (decision === 'resalable') {
                goodBtn.classList.add('active');
                badBtn.classList.remove('active');
            } else if (decision === 'defective') {
                goodBtn.classList.remove('active');
                badBtn.classList.add('active');
            }
        }
    };

    window.setTempNote = function (skuCode, value) {
        tempNotes[skuCode] = value;
    };

    window.applyRMARestock = function (e, id) {
        e.stopPropagation();
        var rma = returns.find(function (r) { return r.id === id; });
        if (!rma) return;

        // Verify if any items are pending decision
        var hasPending = rma.items.some(function (i) { return i.qcDecision === 'pending'; });
        if (hasPending) {
            alert('Vui lòng hoàn tất kiểm tra QC trước khi áp dụng nhập kho!');
            return;
        }

        // Persist apply to backend — backend will validate QC completeness
        submitPostAction('apply', {
            returnId: rma.dbId
        });

        render();
    };

    window.openRMAPrint = function (e, id) {
        e.stopPropagation();
        var rma = returns.find(function (r) { return r.id === id; });
        if (!rma) return;

        renderPrintLayout(rma);
        printOverlay.style.display = 'flex';
    };

    window.closePrintModal = function (e) {
        if (e.target === printOverlay) printOverlay.style.display = 'none';
    };

    // ─── Sub-renderers ───
    function renderQCFormItems(rma) {
        var evidenceHtml = '';
        if ((rma.evidencePhotos && rma.evidencePhotos.trim() !== '') || (rma.evidenceVideo && rma.evidenceVideo.trim() !== '')) {
            evidenceHtml = '<div style="background: rgba(16,55,92,0.02); border: 1px dashed var(--border); border-radius: var(--radius-card); padding: 16px; margin-bottom: 20px;">' +
                           '  <span style="font-size: 11px; font-weight: 700; color: rgba(16,55,92,0.4); text-transform: uppercase;">Ảnh & Video bằng chứng của khách hàng</span>' +
                           '  <div style="display: flex; gap: 12px; margin-top: 10px; flex-wrap: wrap; align-items: flex-start;">';
            
            if (rma.evidencePhotos && rma.evidencePhotos.trim() !== '') {
                var photos = rma.evidencePhotos.split(',');
                photos.forEach(function(photoUrl) {
                    if (photoUrl.trim() !== '') {
                        var fullUrl = window.location.origin + '${pageContext.request.contextPath}' + photoUrl.trim();
                        evidenceHtml += '    <a href="' + fullUrl + '" target="_blank">' +
                                        '      <img src="' + fullUrl + '" style="width: 80px; height: 80px; object-fit: cover; border: 1px solid #E5EAF3; border-radius: calc(var(--radius-btn) - 4px);" />' +
                                        '    </a>';
                    }
                });
            }
            
            if (rma.evidenceVideo && rma.evidenceVideo.trim() !== '') {
                var fullVideoUrl = window.location.origin + '${pageContext.request.contextPath}' + rma.evidenceVideo.trim();
                evidenceHtml += '    <div>' +
                                '      <video src="' + fullVideoUrl + '" controls style="max-height: 80px; max-width: 160px; border: 1px solid #E5EAF3; border-radius: calc(var(--radius-btn) - 4px);"></video>' +
                                '    </div>';
            }
            
            evidenceHtml += '  </div>' +
                            '</div>';
        }

        var itemsHtml = rma.items.map(function (item) {
            var activeGood = tempDecisions[item.skuCode] === 'resalable' ? 'active' : '';
            var activeBad  = tempDecisions[item.skuCode] === 'defective' ? 'active' : '';
            var currentNote = tempNotes[item.skuCode] || '';

            return '<div class="ret-qc-item-card">' +
                   '  <div class="flex items-start gap-3" style="display:flex; align-items:flex-start; gap:12px;">' +
                   '    <svg style="width:16px;height:16px;margin-top:2px;color:rgba(16,55,92,0.4); flex-shrink:0;" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/></svg>' +
                   '    <div>' +
                   '      <div class="ret-qc-item-meta">SKU: ' + esc(item.skuCode) + '</div>' +
                   '      <div class="ret-qc-item-title">' + esc(item.skuName) + '</div>' +
                   '      <div class="ret-qc-item-reason">SL: ' + item.qty + ' · Lý do hoàn: ' + esc(item.returnReason) + '</div>' +
                   '    </div>' +
                   '  </div>' +
                   '  <div class="ret-qc-btn-row">' +
                   '    <button type="button" class="ret-qc-btn ret-qc-btn--good ' + activeGood + '" id="qc-btn-good-' + item.skuCode + '" onclick="setTempDecision(\'' + item.skuCode + '\', \'resalable\')">' +
                   '      <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>' +
                   '      Còn tốt — Nhập lại kho' +
                   '    </button>' +
                   '    <button type="button" class="ret-qc-btn ret-qc-btn--bad ' + activeBad + '" id="qc-btn-bad-' + item.skuCode + '" onclick="setTempDecision(\'' + item.skuCode + '\', \'defective\')">' +
                   '      <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>' +
                   '      Hàng hỏng — Phế phẩm' +
                   '    </button>' +
                   '  </div>' +
                   '  <div>' +
                   '    <label class="ret-qc-label">Ghi chú QC</label>' +
                   '    <input class="ret-qc-input" type="text" placeholder="Mô tả tình trạng hàng hoá thực tế..." value="' + esc(currentNote) + '" oninput="setTempNote(\'' + item.skuCode + '\', this.value)"/>' +
                   '  </div>' +
                   '</div>';
        }).join('');

        qcModalBody.innerHTML = evidenceHtml + itemsHtml;
    }

    function renderPrintLayout(rma) {
        var totalQty = rma.items.reduce(function (sum, i) { return sum + i.qty; }, 0);
        var reuseQty = rma.items.filter(function (i) { return i.qcDecision === 'resalable'; }).reduce(function (sum, i) { return sum + i.qty; }, 0);
        var destQty  = rma.items.filter(function (i) { return i.qcDecision === 'defective'; }).reduce(function (sum, i) { return sum + i.qty; }, 0);
        
        var totalValue = 0;
        var itemRows = rma.items.map(function (item, idx) {
            var unitPrice = item.unitPrice || 0;
            var subtotal = item.qty * unitPrice;
            totalValue += subtotal;

            var reuseVal = item.qcDecision === 'resalable' ? item.qty : 0;
            var destVal  = item.qcDecision === 'defective' ? item.qty : 0;
            var remark = item.qcNote || item.returnReason;

            return '<tr>' +
                   '  <td style="text-align:center;">' + (idx + 1) + '</td>' +
                   '  <td style="font-family:monospace;">' + esc(item.skuCode) + '</td>' +
                   '  <td>' + esc(item.skuName) + '</td>' +
                   '  <td style="text-align:center;">Cái</td>' +
                   '  <td style="text-align:center; font-weight:700;">' + item.qty + '</td>' +
                   '  <td class="bg-good" style="text-align:center; font-weight:700;">' + reuseVal + '</td>' +
                   '  <td class="bg-bad" style="text-align:center; font-weight:700;">' + (destVal > 0 ? destVal : '—') + '</td>' +
                   '  <td style="text-align:right;">' + unitPrice.toLocaleString('vi-VN') + '</td>' +
                   '  <td><span style="font-size:11px; color:rgba(16,55,92,0.7);">' + esc(remark) + '</span></td>' +
                   '</tr>';
        }).join('');

        // Words converter logic (Simple dynamic parser for common WMS test values)
        var wordsVal = convertNumberToVietnameseWords(totalValue);

        printBody.innerHTML =
            '<!-- HEADER SECTION -->' +
            '<div style="margin-bottom: 24px;">' +
            '  <div class="ret-print-grid-2" style="margin-bottom:16px;">' +
            '    <div>' +
            '      <div style="font-size:11px; color:var(--navy); font-weight:500;">Đơn vị: <span style="font-weight:700;">Công ty TNHH Thương Mại ABC</span></div>' +
            '      <div style="font-size:11px; color:rgba(16,55,92,0.6); margin-top:2px;">Bộ phận: Kho / Dịch Vụ Khách Hàng</div>' +
            '    </div>' +
            '    <div style="text-align:right; font-size:10px; color:rgba(16,55,92,0.6);">' +
            '      <div>Mẫu số RMA-VN</div>' +
            '      <div>Return Merchandise Authorization</div>' +
            '    </div>' +
            '  </div>' +
            '  <div style="text-align:center; margin-bottom:20px;">' +
            '    <h1 style="font-size:22px; font-weight:800; color:var(--navy); margin:0; letter-spacing:-0.02em;">PHIẾU NHẬN HÀNG HOÀN / YÊU CẦU RMA</h1>' +
            '    <div style="font-size:12px; color:rgba(16,55,92,0.5); font-weight:600; margin-top:2px;">RETURN MERCHANDISE AUTHORIZATION (RMA)</div>' +
            '  </div>' +
            '  <div class="ret-print-grid-3" style="margin-bottom:20px;">' +
            '    <div>' +
            '      <div class="ret-print-label-row">Số Phiếu RMA</div>' +
            '      <div class="ret-print-val">' + esc(rma.id) + '</div>' +
            '    </div>' +
            '    <div>' +
            '      <div class="ret-print-label-row">Mã Đơn Gốc (SO Ref.)</div>' +
            '      <div class="ret-print-val">' + esc(rma.soRef) + '</div>' +
            '    </div>' +
            '    <div>' +
            '      <div class="ret-print-label-row">Trạng Thái</div>' +
            '      <span class="ret-badge ret-badge--' + rma.status + '" style="margin-top:4px;"><span class="ret-badge__dot"></span>' + esc(getStatusLabel(rma.status)) + '</span>' +
            '    </div>' +
            '  </div>' +
            '  <div class="ret-print-grid-2" style="margin-bottom:20px;">' +
            '    <div class="ret-print-box ret-print-box--red">' +
            '      <div style="font-size:10px; font-weight:700; text-transform:uppercase; margin-bottom:6px;">👤 KHÁCH HÀNG HOÀN TRẢ</div>' +
            '      <div style="font-size:14px; font-weight:700;">' + esc(rma.customer) + '</div>' +
            '      <div style="font-size:11px; margin-top:2px; opacity:0.8;">SĐT: ' + esc(rma.phone) + '</div>' +
            '    </div>' +
            '    <div>' +
            '      <div class="ret-print-label-row">Hướng Xử Lý Đề Xuất</div>' +
            '      <div style="margin-top:4px;"><span class="ret-badge ret-badge--defective">Hoàn tiền + Tiêu hủy hàng lỗi</span></div>' +
            '    </div>' +
            '  </div>' +
            '</div>' +
            '<!-- LINE ITEMS -->' +
            '<div style="margin-bottom:24px;">' +
            '  <h2 style="font-size:14px; font-weight:700; color:var(--navy); margin-bottom:10px;">Danh Sách Hàng Hóa Hoàn Trả (Phân Cấp QC)</h2>' +
            '  <table class="ret-print-table">' +
            '    <thead>' +
            '      <tr>' +
            '        <th>STT</th>' +
            '        <th>Mã SKU</th>' +
            '        <th>Tên Sản Phẩm</th>' +
            '        <th>ĐVT</th>' +
            '        <th>SL Hoàn</th>' +
            '        <th style="background:#ecfdf5; color:#065f46;">SL Dùng Lại</th>' +
            '        <th style="background:#fef2f2; color:#991b1b;">SL Tiêu Hủy</th>' +
            '        <th style="text-align:right;">Giá Trị Hoàn</th>' +
            '        <th>Lý Do / Kết Quả QC</th>' +
            '      </tr>' +
            '    </thead>' +
            '    <tbody>' + itemRows +
            '      <tr class="total-row">' +
            '        <td colspan="4" style="text-align:right;">TỔNG CỘNG:</td>' +
            '        <td style="text-align:center;">' + totalQty + '</td>' +
            '        <td style="text-align:center; background:#ecfdf5; color:#065f46;">' + reuseQty + '</td>' +
            '        <td style="text-align:center; background:#fef2f2; color:#991b1b;">' + destQty + '</td>' +
            '        <td style="text-align:right;">' + totalValue.toLocaleString('vi-VN') + '</td>' +
            '        <td></td>' +
            '      </tr>' +
            '    </tbody>' +
            '  </table>' +
            '  <div style="margin-top:12px; border:1px solid rgba(16,55,92,0.15); padding:10px 14px; border-radius:4px; font-size:12px; color:var(--navy);">' +
            '    <span style="font-weight:700; text-transform:uppercase; font-size:10px; color:rgba(16,55,92,0.50); margin-right:8px;">Tổng Giá Trị Hoàn Trả (Viết bằng chữ):</span>' +
            '    <span style="font-weight:700;">' + wordsVal + '</span>' +
            '  </div>' +
            '</div>' +
            '<!-- SIGNATURE BLOCK -->' +
            '<div class="ret-print-grid-4" style="text-align:center; margin-top:32px;">' +
            '  <div>' +
            '    <div class="ret-print-label-row" style="font-size:9px;">Khách Hàng Ký Nhận</div>' +
            '    <div style="border-bottom:1px solid rgba(16,55,92,0.15); margin:24px 0 8px;"></div>' +
            '    <span style="font-size:10px; color:rgba(16,55,92,0.40);">(Ký, họ tên)</span>' +
            '  </div>' +
            '  <div>' +
            '    <div class="ret-print-label-row" style="font-size:9px;">Nhân Viên Tiếp Nhận</div>' +
            '    <div style="border-bottom:1px solid rgba(16,55,92,0.15); margin:24px 0 8px;"></div>' +
            '    <span style="font-size:10px; color:rgba(16,55,92,0.40);">(Ký, họ tên)</span>' +
            '  </div>' +
            '  <div>' +
            '    <div class="ret-print-label-row" style="font-size:9px;">QC Kiểm Duyệt</div>' +
            '    <div style="border-bottom:1px solid rgba(16,55,92,0.15); margin:24px 0 8px;"></div>' +
            '    <span style="font-size:10px; color:rgba(16,55,92,0.40);">(Ký, họ tên)</span>' +
            '  </div>' +
            '  <div>' +
            '    <div class="ret-print-label-row" style="font-size:9px;">Kế Toán Xác Nhận</div>' +
            '    <div style="border-bottom:1px solid rgba(16,55,92,0.15); margin:24px 0 8px;"></div>' +
            '    <span style="font-size:10px; color:rgba(16,55,92,0.40);">(Ký, họ tên)</span>' +
            '  </div>' +
            '</div>';
    }

    function getStatusLabel(status) {
        var labels = {
            pending_qc: "Chờ kiểm QC",
            qc_done: "Đang QC — Chờ xử lý",
            restocked: "Đã nhập lại kho",
            scrapped: "Chuyển kho hỏng"
        };
        return labels[status] || status;
    }

    function convertNumberToVietnameseWords(num) {
        if (num === 0) return 'Không đồng';
        var units = ["", "một", "hai", "ba", "bốn", "năm", "sáu", "bảy", "tám", "chín"];
        var tens = ["", "mười", "hai mươi", "ba mươi", "bốn mươi", "năm mươi", "sáu mươi", "bảy mươi", "tám mươi", "chín mươi"];
        
        var words = "";
        
        // Hundreds of thousands
        if (num >= 100000) {
            var hundreds = Math.floor(num / 100000);
            words += units[hundreds] + " trăm ";
            num %= 100000;
            if (num < 10000 && num > 0) words += "lẻ ";
        }
        
        // Tens of thousands
        if (num >= 10000) {
            var tenThousands = Math.floor(num / 1000);
            if (tenThousands >= 10) {
                var tenDigit = Math.floor(tenThousands / 10);
                var unitDigit = tenThousands % 10;
                words += tens[tenDigit] + " ";
                if (unitDigit === 5) words += "lăm";
                else if (unitDigit > 0) words += units[unitDigit];
                words += " nghìn ";
            } else {
                words += units[tenThousands] + " nghìn ";
            }
            num %= 1000;
        } else if (num >= 1000) {
            var thousands = Math.floor(num / 1000);
            words += units[thousands] + " nghìn ";
            num %= 1000;
        }

        // Hundreds
        if (num >= 100) {
            var h = Math.floor(num / 100);
            words += units[h] + " trăm ";
            num %= 100;
            if (num < 10 && num > 0) words += "lẻ ";
        }

        // Tens & Units
        if (num >= 10) {
            var t = Math.floor(num / 10);
            var u = num % 10;
            words += tens[t] + " ";
            if (u === 5) words += "lăm";
            else if (u > 0) words += units[u];
        } else if (num > 0) {
            words += units[num];
        }

        return words.trim().charAt(0).toUpperCase() + words.trim().slice(1) + " đồng chẵn";
    }

    // ─── Status badge styling classes ───
    function getStatusConfig(status) {
        var cfg = {
            pending_qc: { label: "Chờ kiểm QC", cls: "ret-badge--pending_qc", dot: "bg-yellow", bg: "rgba(255,186,8,0.20)", text: "var(--orange)", icon: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>' },
            qc_done:    { label: "Đang QC — Chờ xử lý", cls: "ret-badge--qc_done", dot: "bg-blue-400", bg: "rgba(26,115,232,0.08)", text: "#1a73e8", icon: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>' },
            restocked:  { label: "Đã nhập lại kho", cls: "ret-badge--restocked", dot: "bg-emerald-500", bg: "#ecfdf5", text: "#065f46", icon: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="17 10 12 15 7 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>' },
            scrapped:   { label: "Chuyển kho hỏng", cls: "ret-badge--scrapped", dot: "bg-red-500", bg: "#fef2f2", text: "#991b1b", icon: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14H6L5 6"/></svg>' }
        };
        return cfg[status] || cfg.pending_qc;
    }

    function getDecisionConfig(dec) {
        var cfg = {
            resalable: { label: "Còn tốt — Nhập lại", cls: "ret-badge--resalable", icon: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"/></svg>' },
            defective: { label: "Hàng hỏng — Phế phẩm", cls: "ret-badge--defective", icon: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>' },
            pending:   { label: "Chưa kiểm", cls: "ret-badge--pending_item", icon: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>' }
        };
        return cfg[dec] || cfg.pending;
    }

    // ─── Render ───
    function render() {
        // Counts
        var counts = {
            all:        returns.length,
            pending_qc: returns.filter(function (r) { return r.status === 'pending_qc'; }).length,
            qc_done:    returns.filter(function (r) { return r.status === 'qc_done'; }).length,
            restocked:  returns.filter(function (r) { return r.status === 'restocked'; }).length,
            scrapped:   returns.filter(function (r) { return r.status === 'scrapped'; }).length
        };

        var totalResalableItems = returns.filter(function (r) { return r.status === 'restocked'; })
                                         .flatMap(function (r) { return r.items; })
                                         .filter(function (i) { return i.qcDecision === 'resalable'; }).length;
        var totalDefectiveItems = returns.filter(function (r) { return r.status === 'restocked' || r.status === 'scrapped'; })
                                         .flatMap(function (r) { return r.items; })
                                         .filter(function (i) { return i.qcDecision === 'defective'; }).length;

        // Apply count cards
        statPendingQC.textContent = counts.pending_qc;
        statQCDone.textContent    = counts.qc_done;
        statResalable.textContent = totalResalableItems;
        statDefective.textContent = totalDefectiveItems;

        // Banner
        if (counts.pending_qc > 0) {
            attentionBanner.style.display = 'flex';
            attentionText.innerHTML = '<span style="font-weight:700; color:var(--orange);">' + counts.pending_qc + '</span> phiếu hàng hoàn chưa được kiểm QC. Xử lý sớm để cập nhật tồn kho chính xác.';
        } else {
            attentionBanner.style.display = 'none';
        }

        // Tabs badges
        document.getElementById('badge-all').textContent       = counts.all;
        document.getElementById('badge-pending').textContent   = counts.pending_qc;
        document.getElementById('badge-qcdone').textContent    = counts.qc_done;
        document.getElementById('badge-restocked').textContent = counts.restocked;
        document.getElementById('badge-scrapped').textContent  = counts.scrapped;

        // Filter returns
        var q = searchText.toLowerCase();
        var filtered = returns.filter(function (r) {
            var matchTab = activeTab === 'all' || r.status === activeTab;
            var matchSearch = !q ||
                r.id.toLowerCase().includes(q) ||
                r.soRef.toLowerCase().includes(q) ||
                r.customer.toLowerCase().includes(q);
            return matchTab && matchSearch;
        });

        if (filtered.length === 0) {
            returnsContainer.innerHTML = '<div class="ret-empty">Không tìm thấy phiếu hàng hoàn nào phù hợp.</div>';
            return;
        }

        returnsContainer.innerHTML = filtered.map(function (rma) {
            var sc = getStatusConfig(rma.status);
            var isExpanded = expandedRmaId === rma.id;
            var totalQty = rma.items.reduce(function (sum, i) { return sum + i.qty; }, 0);
            var resalable = rma.items.filter(function (i) { return i.qcDecision === 'resalable'; }).reduce(function (sum, i) { return sum + i.qty; }, 0);
            var defective = rma.items.filter(function (i) { return i.qcDecision === 'defective'; }).reduce(function (sum, i) { return sum + i.qty; }, 0);

            // Channel label color mappings
            var chColors = {
                Shopee: '#EE4D2D',
                TikTok: '#000000',
                Website: '#EB8317',
                Lazada: '#0F146D'
            };
            var chCol = chColors[rma.channel] || 'var(--navy)';

            // Actions Buttons
            var actions = '';
            if (rma.status === 'pending_qc') {
                actions = '<button class="ret-btn-action ret-btn-action--orange" onclick="openQCModal(event, \'' + rma.id + '\')">Kiểm QC</button>';
            } else if (rma.status === 'qc_done') {
                actions = '<button class="ret-btn-action" onclick="applyRMARestock(event, \'' + rma.id + '\')">Áp dụng</button>';
            } else {
                actions = '<button class="ret-btn-action ret-btn-action--white" onclick="openRMAPrint(event, \'' + rma.id + '\')">Xem phiếu</button>';
            }

            // Quality results summary
            var qcBadges = '';
            if (resalable > 0 || defective > 0) {
                qcBadges = '<div style="display:flex; align-items:center; gap:8px;">';
                if (resalable > 0) {
                    qcBadges += '<span class="ret-badge ret-badge--resalable"><svg style="width:10px;height:10px;margin-right:4px;" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"/></svg>+' + resalable + '</span>';
                }
                if (defective > 0) {
                    qcBadges += '<span class="ret-badge ret-badge--defective"><svg style="width:10px;height:10px;margin-right:4px;" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>' + defective + ' hỏng</span>';
                }
                qcBadges += '</div>';
            }

            // Collapsible Table rows
            var tableRows = rma.items.map(function (item) {
                var dc = getDecisionConfig(item.qcDecision);
                return '<tr>' +
                       '  <td><div class="ret-sku-code"><svg style="width:14px;height:14px;color:rgba(16,55,92,0.30);" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/></svg>' + esc(item.skuCode) + '</div></td>' +
                       '  <td><div class="ret-sku-name">' + esc(item.skuName) + '</div></td>' +
                       '  <td style="text-align:right; font-weight:700;">' + item.qty + '</td>' +
                       '  <td><span style="font-size:11.5px; color:rgba(16,55,92,0.60);">' + esc(item.returnReason) + '</span></td>' +
                       '  <td>' +
                       '    <span class="ret-badge ' + dc.cls + '"><svg style="width:10px;height:10px;margin-right:4px;" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round">' + dc.icon + '</svg>' + esc(dc.label) + '</span>' +
                       '  </td>' +
                       '  <td><span style="font-size:11px; color:rgba(16,55,92,0.50);">' + esc(item.qcNote || '—') + '</span></td>' +
                       '</tr>';
            }).join('');

            var expandedClass = isExpanded ? 'expanded' : '';
            var expandSection = '';
            if (isExpanded) {
                var evidenceHtml = '';
                if ((rma.evidencePhotos && rma.evidencePhotos.trim() !== '') || (rma.evidenceVideo && rma.evidenceVideo.trim() !== '')) {
                    evidenceHtml = '<div class="ret-evidence-section" style="padding: 16px 20px; background: #fafbfc; border-bottom: 1px solid var(--border);">';
                    evidenceHtml += '  <span style="font-size: 11px; font-weight: 700; color: rgba(16,55,92,0.4); text-transform: uppercase;">Ảnh & Video bằng chứng của khách hàng</span>';
                    evidenceHtml += '  <div style="display: flex; gap: 12px; margin-top: 8px; flex-wrap: wrap; align-items: flex-start;">';
                    
                    if (rma.evidencePhotos && rma.evidencePhotos.trim() !== '') {
                        var photos = rma.evidencePhotos.split(',');
                        photos.forEach(function(photoUrl) {
                            if (photoUrl.trim() !== '') {
                                var fullUrl = window.location.origin + '${pageContext.request.contextPath}' + photoUrl.trim();
                                evidenceHtml += '    <a href="' + fullUrl + '" target="_blank">';
                                evidenceHtml += '      <img src="' + fullUrl + '" style="width: 80px; height: 80px; object-fit: cover; border: 1px solid #E5EAF3; border-radius: calc(var(--radius-btn) - 4px);" />';
                                evidenceHtml += '    </a>';
                            }
                        });
                    }
                    
                    if (rma.evidenceVideo && rma.evidenceVideo.trim() !== '') {
                        var fullVideoUrl = window.location.origin + '${pageContext.request.contextPath}' + rma.evidenceVideo.trim();
                        evidenceHtml += '    <div>';
                        evidenceHtml += '      <video src="' + fullVideoUrl + '" controls style="max-height: 80px; max-width: 160px; border: 1px solid #E5EAF3; border-radius: calc(var(--radius-btn) - 4px);"></video>';
                        evidenceHtml += '    </div>';
                    }
                    
                    evidenceHtml += '  </div>';
                    evidenceHtml += '</div>';
                }

                expandSection = '<div class="ret-sheet-body">' +
                                evidenceHtml +
                                '  <table class="ret-table">' +
                                '    <thead>' +
                                '      <tr>' +
                                '        <th class="text-left">SKU</th>' +
                                '        <th class="text-left">Tên sản phẩm</th>' +
                                '        <th class="text-right">SL trả</th>' +
                                '        <th class="text-left">Lý do hoàn</th>' +
                                '        <th class="text-left">Kết quả QC</th>' +
                                '        <th class="text-left">Ghi chú QC</th>' +
                                '      </tr>' +
                                '    </thead>' +
                                '    <tbody>' + tableRows + '</tbody>' +
                                '  </table>' +
                                '</div>';
            }

            return '<div class="ret-sheet-card ' + expandedClass + '">' +
                   '  <div class="ret-sheet-hd" onclick="toggleRmaExpand(\'' + rma.id + '\')">' +
                   '    <div class="ret-sheet-icon" style="background: ' + sc.bg + '; color: ' + sc.text + ';">' +
                   '      <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M21.5 2v6h-6M21.34 15.57a10 10 0 1 1-.57-8.38l5.67-5.67"/></svg>' +
                   '    </div>' +
                   '    <div class="ret-sheet-info">' +
                   '      <div class="ret-sheet-info-row">' +
                   '        <span class="ret-sheet-id">' + esc(rma.id) + '</span>' +
                   '        <span class="ret-sheet-so-ref">← ' + esc(rma.soRef) + '</span>' +
                   '        <span class="ret-badge ' + sc.cls + '"><span class="ret-badge__dot"></span>' + esc(sc.label) + '</span>' +
                   '        <span class="ret-badge ret-badge--channel" style="background:' + chCol + '15; color:' + chCol + ';">' + esc(rma.channel) + '</span>' +
                   '      </div>' +
                   '      <div class="ret-sheet-customer">' + esc(rma.customer) + ' · ' + esc(rma.phone) + ' · <span style="font-size:10px; color:rgba(16,55,92,0.40);">' + esc(rma.returnedAt) + '</span></div>' +
                   '    </div>' +
                   '    <div class="ret-sheet-stats">' +
                   '      <div class="ret-sheet-stat">' +
                   '        <div class="ret-sheet-stat-lbl">Tổng trả</div>' +
                   '        <div class="ret-sheet-stat-val">' + totalQty + '</div>' +
                   '      </div>' +
                   '      ' + qcBadges +
                   '    </div>' +
                   '    <div style="margin-right:12px; flex-shrink:0;">' + actions + '</div>' +
                   '    <svg class="ret-chevron" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="m6 9 6 6 6-6"/></svg>' +
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
    render();
})();
</script>
