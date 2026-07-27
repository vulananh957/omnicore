<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<c:if test="${not empty documentsJson}">
<script type="application/json" id="ledger-docs-data">${documentsJson}</script>
</c:if>
<script>
window.__CURRENT_ROLE__ = 'MANAGER';
window.__COMPANY_NAME__ = '<c:out value="${companyName}" default="Công ty TNHH OmniCore"/>';
window.__COMPANY_ADDRESS__ = '<c:out value="${companyAddress}" default=""/>';
window.__COMPANY_PHONE__ = '<c:out value="${companyPhone}" default=""/>';
window.__COMPANY_TAX_CODE__ = '<c:out value="${companyTaxCode}" default=""/>';
</script>

<link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/warehouse--warehouse-documents.css"/>
<link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/ledger--ledger.css"/>

<style>
    .btn-action-approve {
        background: #10B981;
        color: #fff;
        border: none;
        padding: 6px 12px;
        border-radius: var(--radius-btn);
        font-size: 12px;
        font-weight: 600;
        cursor: pointer;
        display: inline-flex;
        align-items: center;
        gap: 4px;
        transition: background 0.15s;
    }
    .btn-action-approve:hover {
        background: #059669;
    }
    .btn-action-reject {
        background: #EF4444;
        color: #fff;
        border: none;
        padding: 6px 12px;
        border-radius: var(--radius-btn);
        font-size: 12px;
        font-weight: 600;
        cursor: pointer;
        display: inline-flex;
        align-items: center;
        gap: 4px;
        transition: background 0.15s;
    }
    .btn-action-reject:hover {
        background: #DC2626;
    }
    .doc-filter-select {
        padding: 8px 12px;
        border-radius: var(--radius-btn);
        border: 1px solid var(--border);
        background: #fff;
        font-size: 13px;
        font-weight: 500;
        color: var(--navy);
        cursor: pointer;
        outline: none;
        transition: border-color 0.2s;
    }
    .doc-filter-select:focus {
        border-color: var(--navy);
    }
</style>

<!-- Toast Notification Element -->
<div id="ledgerToast" class="toast-notification">
    <span id="ledgerToastIcon">✓</span>
    <span id="ledgerToastMsg">Cập nhật thành công!</span>
</div>

<!-- ══ Alert Banner ══ -->
<div class="doc-alert-banner" id="docAlertBanner">
    <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
        <path stroke-linecap="round" stroke-linejoin="round" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"/>
    </svg>
    <p id="docAlertBannerText">Bạn đang có 0 phiếu chờ duyệt.</p>
</div>

<!-- ══ Tabs ══ -->
<div class="doc-tabs-container" id="docTabsContainer">
    <button class="doc-tab-btn active all" data-tab="all">
        Tất cả
        <span class="doc-tab-count" id="count-all">0</span>
    </button>
    <button class="doc-tab-btn grn" data-tab="Phiếu Nhập Kho">
        <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
            <path stroke-linecap="round" stroke-linejoin="round" d="M19 13l-7 7-7-7m14-6l-7 7-7-7"/>
        </svg>
        Nhập Kho
        <span class="doc-tab-count" id="count-grn">0</span>
    </button>
    <button class="doc-tab-btn gi" data-tab="Phiếu Xuất Kho">
        <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
            <path stroke-linecap="round" stroke-linejoin="round" d="M5 11l7-7 7 7M5 19l7-7 7 7"/>
        </svg>
        Xuất Kho
        <span class="doc-tab-count" id="count-gi">0</span>
    </button>
    <button class="doc-tab-btn kk" data-tab="Phiếu Kiểm Kê">
        <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
            <path stroke-linecap="round" stroke-linejoin="round" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-6 9l2 2 4-4"/>
        </svg>
        Kiểm Kê
        <span class="doc-tab-count" id="count-kk">0</span>
    </button>
    <button class="doc-tab-btn tr" data-tab="Phiếu Chuyển Kho">
        <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
            <path stroke-linecap="round" stroke-linejoin="round" d="M8 7h12m0 0l-4-4m4 4l-4 4m0 6H4m0 0l4 4m-4-4l4-4"/>
        </svg>
        Chuyển Kho
        <span class="doc-tab-count" id="count-tr">0</span>
    </button>
    <button class="doc-tab-btn rma" data-tab="Phiếu Hoàn Hàng">
        <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
            <path stroke-linecap="round" stroke-linejoin="round" d="M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"/>
            <path stroke-linecap="round" stroke-linejoin="round" d="M3 3v5h5"/>
        </svg>
        Hoàn Hàng
        <span class="doc-tab-count" id="count-rma">0</span>
    </button>
</div>

<!-- ══ Search & Toolbar ══ -->
<div class="doc-toolbar">
    <div style="display: flex; gap: 12px; flex: 1; align-items: center;">
        <div class="doc-search-wrap" style="flex: 1;">
            <svg class="doc-search-icon" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                <path stroke-linecap="round" stroke-linejoin="round" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"/>
            </svg>
            <input type="text" class="doc-search-input" id="docSearchInput" placeholder="Tìm mã phiếu, loại chứng từ, người tạo..." />
        </div>
        <div class="doc-filter-wrap">
            <select id="warehouseFilter" class="doc-filter-select">
                <option value="all">Tất cả các kho</option>
                <c:forEach var="wh" items="${warehouses}">
                    <option value="<c:out value="${wh.warehouseName}"/>"><c:out value="${wh.warehouseName}"/></option>
                </c:forEach>
            </select>
        </div>
    </div>
    <div class="doc-count-summary" id="docCountSummary">0 / 0 phiếu</div>
</div>

<!-- ══ Table Section ══ -->
<div class="doc-table-card" id="documentsTableSection">
    <div class="doc-table-wrapper">
        <table class="doc-table">
            <thead>
                <tr>
                    <th style="padding-left: 20px;">Mã Phiếu</th>
                    <th>Loại Chứng Từ</th>
                    <th>Kho Hàng</th>
                    <th>Người Tạo</th>
                    <th>Ngày Tạo</th>
                    <th class="text-right">Số Mặt Hàng</th>
                    <th class="text-center">Trạng Thái</th>
                    <th class="text-center" style="padding-right: 20px;">Thao Tác</th>
                </tr>
            </thead>
            <tbody id="docTableBody">
                <!-- Javascript populated -->
            </tbody>
        </table>
    </div>
    <div class="doc-table-footer" id="docTableFooter">
        Hiển thị 0 / 0 chứng từ
    </div>
    <div id="docPagination"></div>
</div>

<!-- Shipping Label Section -->
<div id="shippingLabelSection" style="display:none;"></div >

<!-- Delivery Note Section -->
<div id="deliveryNoteSection" style="display:none;"></div>

<!-- ════════════════════════════════════════════════════
    CONFIRM DUYỆT MODAL
    ════════════════════════════════════════════════════ -->
<div class="modal-overlay" id="approveModalOverlay">
    <div class="modal-box modal-sm">
        <div class="modal-body" style="padding: 24px;">
            <div style="display: flex; align-items: center; gap: 12px; margin-bottom: 16px;">
                <div style="width: 40px; height: 40px; border-radius: 50%; background: #ECFDF5; display: flex; align-items: center; justify-content: center; color: #059669;">
                    <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><path d="m9 12 2 2 4-4"/></svg>
                </div>
                <div>
                    <h3 class="modal-title" style="font-size: 15px; font-weight: 700; color: var(--navy); margin: 0;">Xác nhận phê duyệt</h3>
                    <p class="modal-subtitle" style="font-size: 12px; color: rgba(16, 55, 92, 0.5); margin: 0;">Thao tác này không thể hoàn tác</p>
                </div>
            </div>
            
            <div style="background: rgba(16, 55, 92, 0.03); border-radius: 8px; padding: 12px 16px; margin-bottom: 16px;">
                <div style="font-size: 11px; font-weight: 700; text-transform: uppercase; color: rgba(16, 55, 92, 0.5); letter-spacing: 0.05em;">Phiếu cần duyệt</div>
                <div id="approveDocId" style="font-size: 15px; font-weight: 700; color: var(--navy); margin-top: 2px;">GRN-2026-0001</div>
                <div id="approveDocSubtext" style="font-size: 12px; color: rgba(16, 55, 92, 0.6); margin-top: 3px;">Phiếu Nhập Kho — 120 mặt hàng</div>
            </div>

            <p style="font-size: 13px; color: rgba(16, 55, 92, 0.70); line-height: 1.5; margin-bottom: 24px;" id="approveExplanationText">
                Sau khi phê duyệt, hệ thống sẽ chính thức cộng số lượng tồn kho vật lý.
            </p>

            <div style="display: flex; gap: 8px;">
                <button class="btn-modal-cancel" style="flex: 1;" id="btnApproveCancel">Hủy</button>
                <button class="btn-modal-save bg-emerald" style="flex: 1; background: #10B981; color:#fff; border:none; border-radius: var(--radius-btn); font-weight:600; cursor:pointer;" id="btnApproveConfirm">Xác nhận duyệt</button>
            </div>
        </div>
    </div>
</div>

<!-- ════════════════════════════════════════════════════
    CONFIRM TỪ CHỐI MODAL
    ════════════════════════════════════════════════════ -->
<div class="modal-overlay" id="rejectModalOverlay">
    <div class="modal-box modal-sm">
        <div class="modal-body" style="padding: 24px;">
            <div style="display: flex; align-items: center; gap: 12px; margin-bottom: 16px;">
                <div style="width: 40px; height: 40px; border-radius: 50%; background: #FEF2F2; display: flex; align-items: center; justify-content: center; color: #DC2626;">
                    <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="12" x2="12" y1="8" y2="12"/><line x1="12" x2="12.01" y1="16" y2="16"/></svg>
                </div>
                <div>
                    <h3 class="modal-title" style="font-size: 15px; font-weight: 700; color: var(--navy); margin: 0;">Từ chối phiếu</h3>
                    <p class="modal-subtitle" style="font-size: 12px; color: rgba(16, 55, 92, 0.5); margin: 0;">Nhân viên kho sẽ cần chỉnh sửa lại</p>
                </div>
            </div>
            
            <div style="background: rgba(16, 55, 92, 0.03); border-radius: 8px; padding: 12px 16px; margin-bottom: 12px;">
                <div style="font-size: 11px; font-weight: 700; text-transform: uppercase; color: rgba(16, 55, 92, 0.5); letter-spacing: 0.05em;">Phiếu bị từ chối</div>
                <div id="rejectDocId" style="font-size: 15px; font-weight: 700; color: var(--navy); margin-top: 2px;">GRN-2026-0001</div>
                <div id="rejectDocSubtext" style="font-size: 12px; color: rgba(16, 55, 92, 0.6); margin-top: 3px;">Phiếu Nhập Kho — 120 mặt hàng</div>
            </div>

            <div style="margin-bottom: 24px;">
                <label style="display:block; font-size: 11.5px; font-weight: 700; color: rgba(16, 55, 92, 0.6); margin-bottom: 6px;">Lý do từ chối</label>
                <textarea class="form-input" id="rejectReasonText" rows="3" placeholder="Nhập lý do từ chối để WH Staff biết cần điều chỉnh..." style="resize: none; width:100%; border: 1px solid var(--border); border-radius: var(--radius-btn); padding: 8px; font-size: 13px; outline:none; box-sizing:border-box;"></textarea>
            </div>

            <div style="display: flex; gap: 8px;">
                <button class="btn-modal-cancel" style="flex: 1;" id="btnRejectCancel">Hủy</button>
                <button class="btn-modal-save bg-red" style="flex: 1; background: #EF4444; color:#fff; border:none; border-radius: var(--radius-btn); font-weight:600; cursor:pointer;" id="btnRejectConfirm">Xác nhận từ chối</button>
            </div>
        </div>
    </div>
</div>

<!-- ════════════════════════════════════════════════════
    DOCUMENT DETAILS VIEW MODAL (PDF-like preview)
    ════════════════════════════════════════════════════ -->
<div class="modal-overlay" id="detailModalOverlay">
    <div class="modal-box modal-lg detail-modal-grn" style="border-radius: var(--radius-card); overflow: hidden; max-width: 1400px; width: 98%;">
        <!-- Header actions -->
        <div class="modal-hdr" style="background: rgba(240, 244, 250, 0.30); border-bottom: 1px solid var(--border); padding: 16px 24px; display: flex; align-items: center; justify-content: space-between;">
            <div id="detailModalTitleArea" style="display: flex; align-items: center; gap: 12px;">
                <h3 class="modal-title" id="detailModalTitle" style="font-size: 16px; font-weight: 700; color: var(--navy); margin: 0;">Chi tiết Phiếu Kho</h3>
            </div>
            <div class="pdf-header-actions" style="display: flex; gap: 8px; align-items: center;">
                <button class="btn-pdf-action" id="btnDetailPrint">
                    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polygon points="6 9 6 2 18 2 18 9 6 9"/><path d="M6 18H4a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-2"/><rect width="12" height="8" x="6" y="14"/></svg>
                    In PDF
                </button>
                <button class="btn-pdf-action" id="btnDetailExcel">
                    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" x2="12" y1="15" y2="3"/></svg>
                    Xuất Excel
                </button>
                <button class="modal-close" id="btnDetailClose" style="border:none; background:none; font-size: 24px; color: rgba(16,55,92,0.4); cursor:pointer; line-height: 1;">&times;</button>
            </div>
        </div>
        
        <!-- Document Content -->
        <div class="modal-body" style="padding: 0; background: #fff; overflow-y: auto; flex: 1;" id="detailModalBody">
             <!-- Dynamically populated PDF area -->
        </div>
        
        <!-- Footer actions inside modal (not printed) -->
        <div class="modal-ftr" style="background: rgba(240, 244, 250, 0.30); border-top: 1px solid var(--border); padding: 16px 24px; display: flex; justify-content: flex-end; gap: 8px;" id="detailModalFtr">
            <button class="btn-modal-cancel" id="btnDetailCloseFooter">Đóng cửa sổ</button>
        </div>
    </div>
</div>

<script>
    (function() {
        'use strict';

        // ─── Data Initialization ───
        function safeJsonParse(raw, fallback) {
            if (!raw || typeof raw !== 'string') return fallback;
            try { return JSON.parse(raw); } catch (e) { return fallback; }
        }
        var rawDocs = document.getElementById('ledger-docs-data');
        var savedDocs = rawDocs ? safeJsonParse(rawDocs.textContent, null) : null;
        var docs = (savedDocs !== null && savedDocs.length > 0) ? savedDocs : [];

        // Constants matching React
        var DOC_TYPE_CONFIG = {
            "Phiếu Nhập Kho": {
                icon: '<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2"><path stroke-linecap="round" stroke-linejoin="round" d="M19 13l-7 7-7-7m14-6l-7 7-7-7"/></svg>',
                color: "text-emerald-700",
                bg: "grn",
                shortName: "Nhập kho"
            },
            "Phiếu Xuất Kho": {
                icon: '<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2"><path stroke-linecap="round" stroke-linejoin="round" d="M5 11l7-7 7 7M5 19l7-7 7 7"/></svg>',
                color: "text-purple-700",
                bg: "gi",
                shortName: "Xuất kho"
            },
            "Phiếu Kiểm Kê": {
                icon: '<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect width="8" height="4" x="8" y="2" rx="1" ry="1"/><path d="M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2"/><path d="m9 14 2 2 4-4"/></svg>',
                color: "text-amber-700",
                bg: "kk",
                shortName: "Kiểm kê"
            },
            "Phiếu Chuyển Kho": {
                icon: '<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2"><path stroke-linecap="round" stroke-linejoin="round" d="M8 7h12m0 0l-4-4m4 4l-4 4m0 6H4m0 0l4 4m-4-4l4-4"/></svg>',
                color: "text-orange-700",
                bg: "tr",
                shortName: "Chuyển kho"
            },
            "Phiếu Hoàn Hàng": {
                icon: '<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2"><path stroke-linecap="round" stroke-linejoin="round" d="M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"/><path stroke-linecap="round" stroke-linejoin="round" d="M3 3v5h5"/></svg>',
                color: "text-red-700",
                bg: "rma",
                shortName: "Hoàn hàng"
            }
        };

        // State variables
        var activeTab = "all";
        var searchQuery = "";
        var selectedWh = "all";
        var selectedConfirmDoc = null;
        var selectedDoc = null;

        // DOM elements
        var docTableBody = document.getElementById('docTableBody');
        var docSearchInput = document.getElementById('docSearchInput');
        var warehouseFilter = document.getElementById('warehouseFilter');
        var docCountSummary = document.getElementById('docCountSummary');
        var docTableFooter = document.getElementById('docTableFooter');
        var docAlertBanner = document.getElementById('docAlertBanner');
        var docAlertBannerText = document.getElementById('docAlertBannerText');

        // Tab badge counters DOM
        var countAll = document.getElementById('count-all');
        var countGrn = document.getElementById('count-grn');
        var countGi = document.getElementById('count-gi');
        var countKk = document.getElementById('count-kk');
        var countTr = document.getElementById('count-tr');
        var countRma = document.getElementById('count-rma');

        // Overlays
        var approveOverlay = document.getElementById('approveModalOverlay');
        var approveDocId = document.getElementById('approveDocId');
        var approveDocSubtext = document.getElementById('approveDocSubtext');
        var approveExplanationText = document.getElementById('approveExplanationText');
        var btnApproveCancel = document.getElementById('btnApproveCancel');
        var btnApproveConfirm = document.getElementById('btnApproveConfirm');

        var rejectOverlay = document.getElementById('rejectModalOverlay');
        var rejectDocId = document.getElementById('rejectDocId');
        var rejectDocSubtext = document.getElementById('rejectDocSubtext');
        var rejectReasonText = document.getElementById('rejectReasonText');
        var btnRejectCancel = document.getElementById('btnRejectCancel');
        var btnRejectConfirm = document.getElementById('btnRejectConfirm');

        var detailModalOverlay = document.getElementById('detailModalOverlay');
        var detailModalTitle = document.getElementById('detailModalTitle');
        var detailModalBody = document.getElementById('detailModalBody');
        var btnDetailPrint = document.getElementById('btnDetailPrint');
        var btnDetailExcel = document.getElementById('btnDetailExcel');
        var btnDetailClose = document.getElementById('btnDetailClose');
        var btnDetailCloseFooter = document.getElementById('btnDetailCloseFooter');

        // ─── Event Listeners ───
        
        // Search Input
        docSearchInput.addEventListener('input', function(e) {
            searchQuery = e.target.value.trim().toLowerCase();
            renderDocs();
        });

        // Warehouse Filter
        if (warehouseFilter) {
            warehouseFilter.addEventListener('change', function(e) {
                selectedWh = e.target.value;
                renderDocs();
            });
        }

        // Tabs Click
        var tabButtons = document.querySelectorAll('.doc-tab-btn');
        tabButtons.forEach(function(btn) {
            btn.addEventListener('click', function() {
                tabButtons.forEach(function(b) { b.classList.remove('active'); });
                btn.classList.add('active');
                activeTab = btn.getAttribute('data-tab');
                renderDocs();
            });
        });

        // Modals Closing Listeners
        btnApproveCancel.addEventListener('click', function() { approveOverlay.classList.remove('active'); selectedConfirmDoc = null; });
        btnRejectCancel.addEventListener('click', function() { rejectOverlay.classList.remove('active'); selectedConfirmDoc = null; });
        btnDetailClose.addEventListener('click', closeDetailModal);
        btnDetailCloseFooter.addEventListener('click', closeDetailModal);

        // Approve execution
        btnApproveConfirm.addEventListener('click', performApproval);
        // Reject execution
        btnRejectConfirm.addEventListener('click', performRejection);

        // Print & Excel listeners
        btnDetailPrint.addEventListener('click', function() {
            var printArea = document.querySelector('#detailModalBody .pdf-print-area') || document.querySelector('.pdf-print-area');
            if (!printArea) {
                window.print();
                return;
            }

            var iframe = document.createElement('iframe');
            iframe.style.position = 'fixed';
            iframe.style.right = '0';
            iframe.style.bottom = '0';
            iframe.style.width = '0';
            iframe.style.height = '0';
            iframe.style.border = '0';
            document.body.appendChild(iframe);

            var doc = iframe.contentWindow.document;
            doc.open();
            doc.write('<!DOCTYPE html><html><head><title>Bản in chứng từ - OmniCore WMS</title>');
            doc.write('<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&display=swap">');
            doc.write('<style>');
            doc.write('@page { size: A4 portrait; margin: 10mm; }');
            doc.write('body { font-family: "Inter", sans-serif; margin: 0; padding: 0; background: #fff; color: #10375C; font-size: 13px; line-height: 1.5; -webkit-print-color-adjust: exact; print-color-adjust: exact; }');
            doc.write('* { box-sizing: border-box; }');
            doc.write('table { width: 100%; border-collapse: collapse; page-break-inside: auto; }');
            doc.write('tr { page-break-inside: avoid; page-break-after: auto; }');
            doc.write('td, th { padding: 8px 10px; }');
            doc.write('.pdf-print-area { width: 100% !important; padding: 0 !important; margin: 0 !important; }');
            doc.write('</style></head><body>');
            doc.write(printArea.outerHTML);
            doc.write('</body></html>');
            doc.close();

            setTimeout(function() {
                iframe.contentWindow.focus();
                iframe.contentWindow.print();
                setTimeout(function() {
                    if (document.body.contains(iframe)) {
                        document.body.removeChild(iframe);
                    }
                }, 1000);
            }, 300);
        });
        btnDetailExcel.addEventListener('click', function() {
            alert('Xuất excel chứng từ thành công!');
        });

        // Backdrop Dismissals for overlays
        approveOverlay.addEventListener('click', function(e) {
            if (e.target === approveOverlay) {
                approveOverlay.classList.remove('active');
                selectedConfirmDoc = null;
            }
        });
        rejectOverlay.addEventListener('click', function(e) {
            if (e.target === rejectOverlay) {
                rejectOverlay.classList.remove('active');
                selectedConfirmDoc = null;
            }
        });
        detailModalOverlay.addEventListener('click', function(e) {
            if (e.target === detailModalOverlay) {
                closeDetailModal();
            }
        });

        function closeDetailModal() {
            detailModalOverlay.classList.remove('active');
            selectedDoc = null;
        }

        // ─── Status Helpers ───
        function isDraft(doc) {
            return doc.status === "Nháp";
        }

        function isAwaitingBM(doc) {
            return doc.status === "Chờ duyệt";
        }

        function isRMAPendingWH(doc) {
            return doc.type === "Phiếu Hoàn Hàng" && doc.status === "Chờ xác nhận WH";
        }

        function isViewable(doc) {
            return true; // Manager can view ALL documents
        }

        function isRejected(doc) {
            return doc.status === "Từ chối";
        }

        // ─── Maker-Checker Flow triggers ───
        window.confirmApprove = function(docId, docType, event) {
            if (event) event.stopPropagation();
            var doc = docs.find(function(d) { return d.id === docId; });
            if (!doc) return;
            selectedConfirmDoc = doc;
            
            approveDocId.textContent = doc.id;
            approveDocSubtext.textContent = doc.type + ' — ' + doc.items + ' mặt hàng';
            approveExplanationText.textContent = 'Sau khi phê duyệt, hệ thống sẽ chính thức cập nhật số lượng tồn kho vật lý và ghi nhận giao dịch vào sổ kho.';
            
            approveOverlay.classList.add('active');
        };

        window.confirmReject = function(docId, docType, event) {
            if (event) event.stopPropagation();
            var doc = docs.find(function(d) { return d.id === docId; });
            if (!doc) return;
            selectedConfirmDoc = doc;
            
            rejectDocId.textContent = doc.id;
            rejectDocSubtext.textContent = doc.type + ' — ' + doc.items + ' mặt hàng';
            rejectReasonText.value = '';
            
            rejectOverlay.classList.add('active');
        };

        function performApproval() {
            if (!selectedConfirmDoc) return;
            
            var form = document.createElement('form');
            form.method = 'POST';
            form.action = '${pageContext.request.contextPath}/business/ledger';

            var actionInput = document.createElement('input');
            actionInput.type = 'hidden';
            actionInput.name = 'action';
            actionInput.value = 'approve';
            form.appendChild(actionInput);

            var typeInput = document.createElement('input');
            typeInput.type = 'hidden';
            typeInput.name = 'docType';
            typeInput.value = selectedConfirmDoc.type;
            form.appendChild(typeInput);

            var idInput = document.createElement('input');
            idInput.type = 'hidden';
            idInput.name = 'docId';
            idInput.value = selectedConfirmDoc.id;
            form.appendChild(idInput);

            document.body.appendChild(form);
            form.submit();
        }

        function performRejection() {
            if (!selectedConfirmDoc) return;
            var reason = rejectReasonText.value.trim() || "Thông tin hàng hóa chưa chính xác";

            var form = document.createElement('form');
            form.method = 'POST';
            form.action = '${pageContext.request.contextPath}/business/ledger';

            var actionInput = document.createElement('input');
            actionInput.type = 'hidden';
            actionInput.name = 'action';
            actionInput.value = 'reject';
            form.appendChild(actionInput);

            var typeInput = document.createElement('input');
            typeInput.type = 'hidden';
            typeInput.name = 'docType';
            typeInput.value = selectedConfirmDoc.type;
            form.appendChild(typeInput);

            var idInput = document.createElement('input');
            idInput.type = 'hidden';
            idInput.name = 'docId';
            idInput.value = selectedConfirmDoc.id;
            form.appendChild(idInput);

            var reasonInput = document.createElement('input');
            reasonInput.type = 'hidden';
            reasonInput.name = 'rejectReason';
            reasonInput.value = reason;
            form.appendChild(reasonInput);

            document.body.appendChild(form);
            form.submit();
        }

        // Open details modal and build layouts
        window.viewDocDetails = function(id, event) {
            if (event) event.stopPropagation();
            var target = docs.find(function(d) { return d.id === id; });
            if (!target) return;

            selectedDoc = target;
            detailModalTitle.textContent = "Chi tiết " + selectedDoc.type + " (" + selectedDoc.id + ")";
            
            // Build body content
            detailModalBody.innerHTML = compileDetailTemplate(selectedDoc);
            
            // Dynamically populate footer action buttons
            var detailModalFtr = document.getElementById('detailModalFtr');
            detailModalFtr.innerHTML = '';
            
            if (isAwaitingBM(selectedDoc)) {
                var approveBtn = document.createElement('button');
                approveBtn.className = 'btn-action-approve';
                approveBtn.style.padding = '8px 16px';
                approveBtn.style.fontSize = '13px';
                approveBtn.textContent = 'Phê duyệt';
                approveBtn.addEventListener('click', function(e) {
                    confirmApprove(selectedDoc.id, selectedDoc.type, e);
                });
                
                var rejectBtn = document.createElement('button');
                rejectBtn.className = 'btn-action-reject';
                rejectBtn.style.padding = '8px 16px';
                rejectBtn.style.fontSize = '13px';
                rejectBtn.textContent = 'Từ chối';
                rejectBtn.addEventListener('click', function(e) {
                    confirmReject(selectedDoc.id, selectedDoc.type, e);
                });
                
                detailModalFtr.appendChild(approveBtn);
                detailModalFtr.appendChild(rejectBtn);
            }
            
            var closeBtn = document.createElement('button');
            closeBtn.className = 'btn-modal-cancel';
            closeBtn.textContent = 'Đóng cửa sổ';
            closeBtn.addEventListener('click', closeDetailModal);
            detailModalFtr.appendChild(closeBtn);

            detailModalOverlay.classList.add('active');
        };

        // ─── Toast Notification Helper ───
        function showToast(msg, isError) {
            var toast = document.getElementById("ledgerToast");
            var msgSpan = document.getElementById("ledgerToastMsg");
            var iconSpan = document.getElementById("ledgerToastIcon");
            if (!toast || !msgSpan || !iconSpan) return;

            msgSpan.textContent = msg;
            iconSpan.textContent = isError ? "✗" : "✓";
            toast.className = "toast-notification show";
            if (isError) {
                toast.classList.add("error");
            } else {
                toast.classList.remove("error");
            }
            setTimeout(function() {
                toast.classList.remove("show");
            }, 4000);
        }

        // Check for flash messages from Servlet
        (function() {
            var errorMsg = "${fn:escapeXml(flashError)}";
            var successMsg = "${fn:escapeXml(flashSuccess)}";
            if (errorMsg && errorMsg.trim() !== "" && errorMsg.indexOf('flashError') === -1) {
                showToast(errorMsg, true);
            } else if (successMsg && successMsg.trim() !== "" && successMsg.indexOf('flashSuccess') === -1) {
                showToast(successMsg, false);
            }
        })();

        // ─── Detail Modals HTML Compilers ───
        function compileDetailTemplate(doc) {
            var items = doc.itemsList || [];
            
            // Build date parts
            var day = "29", month = "05", year = "2026", time = "15:30";
            if (doc.date) {
                var dtParts = doc.date.split(" ");
                if (dtParts[0] && dtParts[0].includes("/")) {
                    var parts = dtParts[0].split("/");
                    day = parts[0]; month = parts[1]; year = parts[2];
                }
                if (dtParts[1]) time = dtParts[1];
            }

            if (doc.type === "Phiếu Nhập Kho") {
                var totalOrdered = 0, totalReceived = 0, totalAccepted = 0, totalRejected = 0, totalVal = 0;
                var rowMarkup = "";
                items.forEach(function(it) {
                    totalOrdered += it.ordered || 0;
                    totalReceived += it.received || 0;
                    totalAccepted += it.accepted || 0;
                    totalRejected += it.rejected || 0;
                    totalVal += (it.accepted || 0) * (it.price || 0);

                    rowMarkup += '<tr style="line-height: 2.0;">' +
                        '<td style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; text-align: center; font-size: 13px;">' + it.stt + '</td>' +
                        '<td style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; font-family: monospace; font-size: 11px;">' + escapeHtml(it.sku) + '</td>' +
                        '<td style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; font-weight: 600; font-size: 13px;">' + escapeHtml(it.name) + '</td>' +
                        '<td style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; text-align: center; font-size: 12.5px;">' + escapeHtml(it.uom) + '</td>' +
                        '<td style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; text-align: center; color: rgba(16,55,92,0.6);">' + it.ordered + '</td>' +
                        '<td style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; text-align: center; font-weight: 600;">' + it.received + '</td>' +
                        '<td style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; text-align: center; font-weight: 800; background: rgba(16, 185, 129, 0.05); color: #059669;">' + it.accepted + '</td>' +
                        '<td style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; text-align: center; background: rgba(239, 68, 68, 0.05); color: ' + (it.rejected > 0 ? '#dc2626' : 'rgba(16, 55, 92, 0.3)') + ';">' + (it.rejected > 0 ? it.rejected : '—') + '</td>' +
                        '<td style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; font-size: 12px; color: rgba(16,55,92,0.6);">' + (it.remarks ? escapeHtml(it.remarks) : '<div style="border-bottom: 1px dashed rgba(16,55,92,0.15); height: 10px;"></div>') + '</td>' +
                    '</tr>';
                });

                var supplier = doc.supplier || "Đối tác bán hàng";

                return '<div class="pdf-print-area" style="padding: 32px; background: #fff; font-family: \'Inter\', sans-serif;">' +
                    '<div style="margin-bottom: 24px;">' +
                        '<h1 style="margin: 0 0 2px; font-size: 24px; font-weight: 850; color: var(--navy); letter-spacing: -0.02em;">PHIẾU NHẬP KHO</h1>' +
                        '<div style="font-size: 13.5px; font-weight: 500; color: rgba(16, 55, 92, 0.50); text-transform: uppercase; letter-spacing: 0.05em;">GOODS RECEIPT NOTE (GRN)</div>' +
                    '</div>' +
                    '<div style="display: flex; align-items: center; gap: 16px; margin-bottom: 24px;">' +
                        '<div>' +
                            '<label style="display: block; font-size: 11px; font-weight: 700; text-transform: uppercase; color: rgba(16, 55, 92, 0.50); letter-spacing: 0.05em; margin-bottom: 4px;">Mã Phiếu Nhập (GRN No.)</label>' +
                            '<span style="font-size: 18px; font-weight: 700; color: var(--navy); font-family: monospace;">' + escapeHtml(doc.id) + '</span>' +
                        '</div>' +
                    '</div>' +
                    '<div style="display: grid; grid-template-columns: 1fr 1fr; gap: 24px; margin-bottom: 24px; border-bottom: 1px solid #E5EAF3; padding-bottom: 24px;">' +
                        '<div style="display: flex; flex-direction: column; gap: 16px;">' +
                            '<div>' +
                                '<label style="display: block; font-size: 11px; font-weight: 700; text-transform: uppercase; color: rgba(16, 55, 92, 0.50); letter-spacing: 0.05em; margin-bottom: 4px;">Nhà Cung Cấp (Supplier)</label>' +
                                '<div style="font-size: 14px; font-weight: 600; color: var(--navy);">' + escapeHtml(supplier) + '</div>' +
                                (doc.supplierCode ? '<div style="font-size: 12px; color: rgba(16, 55, 92, 0.60); margin-top: 2px;">Mã NCC: ' + escapeHtml(doc.supplierCode) + '</div>' : '') +
                                (doc.contactPerson ? '<div style="font-size: 12px; color: rgba(16, 55, 92, 0.60); margin-top: 2px;">Người LH: ' + escapeHtml(doc.contactPerson) + '</div>' : '') +
                                (doc.supplierPhone ? '<div style="font-size: 12px; color: rgba(16, 55, 92, 0.60); margin-top: 2px;">SĐT: ' + escapeHtml(doc.supplierPhone) + '</div>' : '') +
                                (doc.supplierEmail ? '<div style="font-size: 12px; color: rgba(16, 55, 92, 0.60); margin-top: 2px;">Email: ' + escapeHtml(doc.supplierEmail) + '</div>' : '') +
                                (doc.supplierAddress ? '<div style="font-size: 12px; color: rgba(16, 55, 92, 0.60); margin-top: 2px;">Địa chỉ: ' + escapeHtml(doc.supplierAddress) + '</div>' : '') +
                            '</div>' +
                            (doc.paymentTerms ? '<div>' +
                                '<label style="display: block; font-size: 11px; font-weight: 700; text-transform: uppercase; color: rgba(16, 55, 92, 0.50); letter-spacing: 0.05em; margin-bottom: 4px;">Thời Hạn Thanh Toán</label>' +
                                '<div style="font-size: 14px; font-weight: 600; color: var(--navy);">' + escapeHtml(doc.paymentTerms) + '</div>' +
                            '</div>' : '') +
                        '</div>' +
                        '<div style="display: flex; flex-direction: column; gap: 16px;">' +
                            '<div>' +
                                '<label style="display: block; font-size: 11px; font-weight: 700; text-transform: uppercase; color: rgba(16, 55, 92, 0.50); letter-spacing: 0.05em; margin-bottom: 4px;">Ngày Nhập Kho (GRN Date)</label>' +
                                '<div style="font-size: 14px; color: var(--navy);">' + day + '/' + month + '/' + year + ' - ' + time + '</div>' +
                            '</div>' +
                            (doc.expectedDate ? '<div>' +
                                '<label style="display: block; font-size: 11px; font-weight: 700; text-transform: uppercase; color: rgba(16, 55, 92, 0.50); letter-spacing: 0.05em; margin-bottom: 4px;">Ngày Nhận Dự Kiến</label>' +
                                '<div style="font-size: 14px; font-weight: 600; color: var(--navy);">' + escapeHtml(doc.expectedDate) + '</div>' +
                            '</div>' : '') +
                            '<div>' +
                                '<label style="display: block; font-size: 11px; font-weight: 700; text-transform: uppercase; color: rgba(16, 55, 92, 0.50); letter-spacing: 0.05em; margin-bottom: 4px;">Trạng Thái (Status)</label>' +
                                '<span style="display: inline-block; padding: 4px 10px; font-size: 12px; font-weight: 700; color: #047857; background: #ECFDF5; border: 1px solid #A7F3D0; border-radius: var(--radius-btn);">' + escapeHtml(doc.status) + '</span>' +
                            '</div>' +
                            '<div>' +
                                '<label style="display: block; font-size: 11px; font-weight: 700; text-transform: uppercase; color: rgba(16, 55, 92, 0.50); letter-spacing: 0.05em; margin-bottom: 4px;">Giá Trị Lô Hàng (Document Value)</label>' +
                                '<div style="font-size: 15px; font-weight: 700; color: var(--navy);">' + totalVal.toLocaleString('vi-VN') + ' VNĐ</div>' +
                            '</div>' +
                        '</div>' +
                    '</div>' +
                    '<div style="margin-bottom: 24px;">' +
                        '<h2 style="font-size: 15px; font-weight: 700; color: var(--navy); margin-bottom: 16px;">Chi Tiết Hàng Hóa Nhập Kho (Phân Cấp Chất Lượng)</h2>' +
                        '<table style="width: 100%; border-collapse: collapse; border: 2px solid rgba(16, 55, 92, 0.15); margin-bottom: 24px;">' +
                            '<thead>' +
                                '<tr style="background: var(--alice); border-bottom: 2px solid rgba(16, 55, 92, 0.15);">' +
                                    '<th style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; font-size: 10px; font-weight: 700; text-transform: uppercase; color: rgba(16, 55, 92, 0.50); text-align: center; width: 35px;">STT</th>' +
                                    '<th style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; font-size: 10px; font-weight: 700; text-transform: uppercase; color: rgba(16, 55, 92, 0.50); text-align: left;">Mã SKU</th>' +
                                    '<th style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; font-size: 10px; font-weight: 700; text-transform: uppercase; color: rgba(16, 55, 92, 0.50); text-align: left;">Tên Sản Phẩm</th>' +
                                    '<th style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; font-size: 10px; font-weight: 700; text-transform: uppercase; color: rgba(16, 55, 92, 0.50); text-align: center;">ĐVT</th>' +
                                    '<th style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; font-size: 10px; font-weight: 700; text-transform: uppercase; color: rgba(16, 55, 92, 0.50); text-align: center;">SL Đặt Hàng</th>' +
                                    '<th style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; font-size: 10px; font-weight: 700; text-transform: uppercase; color: rgba(16, 55, 92, 0.50); text-align: center;">SL Thực Nhận</th>' +
                                    '<th style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; font-size: 10px; font-weight: 700; text-transform: uppercase; color: rgba(16, 55, 92, 0.50); text-align: center; background: rgba(16, 185, 129, 0.05);">SL Chấp Nhận</th>' +
                                    '<th style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; font-size: 10px; font-weight: 700; text-transform: uppercase; color: rgba(16, 55, 92, 0.50); text-align: center; background: rgba(239, 68, 68, 0.05);">SL Từ Chối</th>' +
                                    '<th style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; font-size: 10px; font-weight: 700; text-transform: uppercase; color: rgba(16, 55, 92, 0.50); text-align: left;">Ghi Chú / Mã Lỗi</th>' +
                                '</tr>' +
                            '</thead>' +
                            '<tbody>' + rowMarkup +
                                '<tr style="background: rgba(240, 244, 250, 0.5); font-weight: 700; border-top: 2px solid rgba(16, 55, 92, 0.3);">' +
                                    '<td colspan="4" style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 12px; text-align: right;">TỔNG CỘNG:</td>' +
                                    '<td style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 12px; text-align: center;">' + totalOrdered + '</td>' +
                                    '<td style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 12px; text-align: center;">' + totalReceived + '</td>' +
                                    '<td style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 12px; text-align: center; color: #059669; background: rgba(16, 185, 129, 0.05);">' + totalAccepted + '</td>' +
                                    '<td style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 12px; text-align: center; color: #dc2626; background: rgba(239, 68, 68, 0.05);">' + totalRejected + '</td>' +
                                    '<td style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 12px;"></td>' +
                                '</tr>' +
                            '</tbody>' +
                        '</table>' +
                    '</div>' +
                    '<div style="margin-top: 24px; display: grid; grid-template-columns: repeat(3, 1fr); gap: 24px; text-align: center;">' +
                        '<div>' +
                            '<div style="font-size: 11px; font-weight: 700; text-transform: uppercase; color: rgba(16,55,92,0.50); margin-bottom: 40px;">Đại Diện Giao Hàng</div>' +
                            '<div style="border-bottom: 2px solid rgba(16,55,92,0.15); width: 80%; margin: 0 auto 4px;"></div>' +
                            '<span style="font-size: 10px; color: rgba(16,55,92,0.40); font-style: italic;">(Ký, ghi rõ họ tên)</span>' +
                        '</div>' +
                        '<div>' +
                            '<div style="font-size: 11px; font-weight: 700; text-transform: uppercase; color: rgba(16,55,92,0.50); margin-bottom: 40px;">Nhân Viên QA/QC</div>' +
                            '<div style="border-bottom: 2px solid rgba(16,55,92,0.15); width: 80%; margin: 0 auto 4px;"></div>' +
                            '<span style="font-size: 10px; color: rgba(16,55,92,0.40); font-style: italic;">(Ký, ghi rõ họ tên)</span>' +
                        '</div>' +
                        '<div>' +
                            '<div style="font-size: 11px; font-weight: 700; text-transform: uppercase; color: rgba(16,55,92,0.50); margin-bottom: 40px;">Thủ Kho Xác Nhận</div>' +
                            '<div style="border-bottom: 2px solid rgba(16,55,92,0.15); width: 80%; margin: 0 auto 4px;"></div>' +
                            '<span style="font-size: 10px; color: rgba(16,55,92,0.40); font-style: italic;">(Ký, ghi rõ họ tên)</span>' +
                        '</div>' +
                    '</div>' +
                '</div>';
            }

            if (doc.type === "Phiếu Xuất Kho") {
                var totalRequested = 0, totalShipped = 0, totalVal = 0;
                var rowMarkup = "";
                items.forEach(function(it) {
                    totalRequested += it.requested || 0;
                    totalShipped += it.shipped || 0;
                    totalVal += (it.shipped || 0) * (it.price || 0);

                    rowMarkup += '<tr style="line-height: 2.0;">' +
                        '<td style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; text-align: center; font-size: 13px;">' + it.stt + '</td>' +
                        '<td style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; font-family: monospace; font-size: 11px;">' + escapeHtml(it.sku) + '</td>' +
                        '<td style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; font-weight: 600; font-size: 13px;">' + escapeHtml(it.name) + '</td>' +
                        '<td style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; text-align: center; font-size: 12.5px;">' + escapeHtml(it.uom) + '</td>' +
                        '<td style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; text-align: center; color: rgba(16,55,92,0.6);">' + it.requested + '</td>' +
                        '<td style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; text-align: center; font-weight: 800; color: #6d28d9; background: rgba(109, 40, 217, 0.05);">' + it.shipped + '</td>' +
                        '<td style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; font-size: 12px; color: rgba(16,55,92,0.6);">' + (it.remarks ? escapeHtml(it.remarks) : '<div style="border-bottom: 1px dashed rgba(16,55,92,0.15); height: 10px;"></div>') + '</td>' +
                    '</tr>';
                });

                var receiver = doc.receiver || "Khách hàng mua lẻ";

                return '<div class="pdf-print-area" style="padding: 32px; background: #fff; font-family: \'Inter\', sans-serif;">' +
                    '<div style="margin-bottom: 24px;">' +
                        '<h1 style="margin: 0 0 2px; font-size: 24px; font-weight: 850; color: var(--navy); letter-spacing: -0.02em;">PHIẾU XUẤT KHO</h1>' +
                        '<div style="font-size: 13.5px; font-weight: 500; color: rgba(16, 55, 92, 0.50); text-transform: uppercase; letter-spacing: 0.05em;">GOODS ISSUE NOTE (GIN)</div>' +
                    '</div>' +
                    '<div style="display: flex; align-items: center; gap: 16px; margin-bottom: 24px;">' +
                        '<div>' +
                            '<label style="display: block; font-size: 11px; font-weight: 700; text-transform: uppercase; color: rgba(16, 55, 92, 0.50); letter-spacing: 0.05em; margin-bottom: 4px;">Mã Phiếu Xuất (GIN No.)</label>' +
                            '<span style="font-size: 18px; font-weight: 700; color: var(--navy); font-family: monospace;">' + escapeHtml(doc.id) + '</span>' +
                        '</div>' +
                        '<div style="border: 1px solid #E5EAF3; border-radius: var(--radius-btn); padding: 8px 16px; background: #fff; display: flex; flex-direction: column; align-items: center;">' +
                            '<div style="height: 48px; width: 180px; background: rgba(16, 55, 92, 0.05); display: flex; align-items: center; justify-content: center; font-family: monospace; font-size: 10px; color: rgba(16, 55, 92, 0.35); margin-top: 4px;">||||| ' + escapeHtml(doc.id) + ' |||||</div>' +
                        '</div>' +
                    '</div>' +
                    '<div style="display: grid; grid-template-columns: 1fr 1fr; gap: 24px; margin-bottom: 24px; border-bottom: 1px solid #E5EAF3; padding-bottom: 24px;">' +
                        '<div style="display: flex; flex-direction: column; gap: 16px;">' +
                            '<div>' +
                                '<label style="display: block; font-size: 11px; font-weight: 700; text-transform: uppercase; color: rgba(16, 55, 92, 0.50); letter-spacing: 0.05em; margin-bottom: 4px;">Người Nhận / Đối Tác (Receiver)</label>' +
                                '<div style="font-size: 14px; font-weight: 600; color: var(--navy);">' + escapeHtml(receiver) + '</div>' +
                                (doc.shippingAddress ? '<div style="font-size: 12px; color: rgba(16, 55, 92, 0.60); margin-top: 2px;">Địa chỉ: ' + escapeHtml(doc.shippingAddress) + '</div>' : '') +
                                (doc.customerPhone ? '<div style="font-size: 12px; color: rgba(16, 55, 92, 0.60); margin-top: 2px;">SĐT: ' + escapeHtml(doc.customerPhone) + '</div>' : '') +
                            '</div>' +
                            '<div>' +
                                '<label style="display: block; font-size: 11px; font-weight: 700; text-transform: uppercase; color: rgba(16, 55, 92, 0.50); letter-spacing: 0.05em; margin-bottom: 4px;">Mã Đơn Đặt Hàng (Sales Order Ref.)</label>' +
                                '<div style="font-size: 16px; font-weight: 700; color: var(--navy);">' + escapeHtml(doc.poReference || "—") + '</div>' +
                            '</div>' +
                        '</div>' +
                        '<div style="display: flex; flex-direction: column; gap: 16px;">' +
                            '<div>' +
                                '<label style="display: block; font-size: 11px; font-weight: 700; text-transform: uppercase; color: rgba(16, 55, 92, 0.50); letter-spacing: 0.05em; margin-bottom: 4px;">Ngày Xuất Kho (GIN Date)</label>' +
                                '<div style="font-size: 14px; color: var(--navy);">' + day + '/' + month + '/' + year + ' - ' + time + '</div>' +
                            '</div>' +
                            '<div>' +
                                '<label style="display: block; font-size: 11px; font-weight: 700; text-transform: uppercase; color: rgba(16, 55, 92, 0.50); letter-spacing: 0.05em; margin-bottom: 4px;">Trạng Thái (Status)</label>' +
                                '<span style="display: inline-block; padding: 4px 10px; font-size: 12px; font-weight: 700; color: #6d28d9; background: #F5F3FF; border: 1px solid #DDD6FE; border-radius: var(--radius-btn);">' + escapeHtml(doc.status) + '</span>' +
                            '</div>' +
                            '<div>' +
                                '<label style="display: block; font-size: 11px; font-weight: 700; text-transform: uppercase; color: rgba(16, 55, 92, 0.50); letter-spacing: 0.05em; margin-bottom: 4px;">Giá Trị Lô Hàng (Document Value)</label>' +
                                '<div style="font-size: 15px; font-weight: 700; color: var(--navy);">' + totalVal.toLocaleString('vi-VN') + ' VNĐ</div>' +
                            '</div>' +
                        '</div>' +
                    '</div>' +
                    '<div style="margin-bottom: 24px;">' +
                        '<h2 style="font-size: 15px; font-weight: 700; color: var(--navy); margin-bottom: 16px;">Chi Tiết Hàng Hóa Xuất Kho</h2>' +
                        '<table style="width: 100%; border-collapse: collapse; border: 2px solid rgba(16, 55, 92, 0.15); margin-bottom: 24px;">' +
                            '<thead>' +
                                '<tr style="background: var(--alice); border-bottom: 2px solid rgba(16, 55, 92, 0.15);">' +
                                    '<th style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; font-size: 10px; font-weight: 700; text-transform: uppercase; color: rgba(16, 55, 92, 0.50); text-align: center; width: 35px;">STT</th>' +
                                    '<th style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; font-size: 10px; font-weight: 700; text-transform: uppercase; color: rgba(16, 55, 92, 0.50); text-align: left;">Mã SKU</th>' +
                                    '<th style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; font-size: 10px; font-weight: 700; text-transform: uppercase; color: rgba(16, 55, 92, 0.50); text-align: left;">Tên Sản Phẩm</th>' +
                                    '<th style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; font-size: 10px; font-weight: 700; text-transform: uppercase; color: rgba(16, 55, 92, 0.50); text-align: center;">ĐVT</th>' +
                                    '<th style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; font-size: 10px; font-weight: 700; text-transform: uppercase; color: rgba(16, 55, 92, 0.50); text-align: center;">SL Yêu Cầu</th>' +
                                    '<th style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; font-size: 10px; font-weight: 700; text-transform: uppercase; color: rgba(16, 55, 92, 0.50); text-align: center; background: rgba(109, 40, 217, 0.05); color: #6d28d9;">SL Thực Xuất</th>' +
                                    '<th style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; font-size: 10px; font-weight: 700; text-transform: uppercase; color: rgba(16, 55, 92, 0.50); text-align: left;">Ghi Chú</th>' +
                                '</tr>' +
                            '</thead>' +
                            '<tbody>' + rowMarkup +
                                '<tr style="background: rgba(240, 244, 250, 0.5); font-weight: 700; border-top: 2px solid rgba(16, 55, 92, 0.3);">' +
                                    '<td colspan="4" style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 12px; text-align: right;">TỔNG CỘNG:</td>' +
                                    '<td style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 12px; text-align: center;">' + totalRequested + '</td>' +
                                    '<td style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 12px; text-align: center; color: #6d28d9; background: rgba(109, 40, 217, 0.05);">' + totalShipped + '</td>' +
                                    '<td style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 12px;"></td>' +
                                '</tr>' +
                            '</tbody>' +
                        '</table>' +
                    '</div>' +
                    '<div style="margin-top: 24px; display: grid; grid-template-columns: repeat(3, 1fr); gap: 24px; text-align: center;">' +
                        '<div>' +
                            '<div style="font-size: 11px; font-weight: 700; text-transform: uppercase; color: rgba(16,55,92,0.50); margin-bottom: 40px;">Người Lập Phiếu</div>' +
                            '<div style="border-bottom: 2px solid rgba(16,55,92,0.15); width: 80%; margin: 0 auto 4px;"></div>' +
                            '<span style="font-size: 10px; color: rgba(16,55,92,0.40); font-style: italic;">(Ký, ghi rõ họ tên)</span>' +
                        '</div>' +
                        '<div>' +
                            '<div style="font-size: 11px; font-weight: 700; text-transform: uppercase; color: rgba(16,55,92,0.50); margin-bottom: 40px;">Thủ Kho Xuất</div>' +
                            '<div style="border-bottom: 2px solid rgba(16,55,92,0.15); width: 80%; margin: 0 auto 4px;"></div>' +
                            '<span style="font-size: 10px; color: rgba(16,55,92,0.40); font-style: italic;">(Ký, ghi rõ họ tên)</span>' +
                        '</div>' +
                        '<div>' +
                            '<div style="font-size: 11px; font-weight: 700; text-transform: uppercase; color: rgba(16,55,92,0.50); margin-bottom: 40px;">Đại Diện Nhận Hàng</div>' +
                            '<div style="border-bottom: 2px solid rgba(16,55,92,0.15); width: 80%; margin: 0 auto 4px;"></div>' +
                            '<span style="font-size: 10px; color: rgba(16,55,92,0.40); font-style: italic;">(Ký, ghi rõ họ tên)</span>' +
                        '</div>' +
                    '</div>' +
                '</div>';
            }

            if (doc.type === "Phiếu Kiểm Kê") {
                var totalSystem = 0, totalPhysical = 0, totalDiff = 0;
                var rowMarkup = "";
                items.forEach(function(it) {
                    totalSystem += it.systemQty || 0;
                    totalPhysical += it.physicalQty || 0;
                    var diff = (it.physicalQty || 0) - (it.systemQty || 0);
                    totalDiff += diff;

                    var diffColor = diff === 0 ? "rgba(16,55,92,0.4)" : (diff > 0 ? "#059669" : "#dc2626");
                    var diffText = diff === 0 ? "—" : (diff > 0 ? "+" + diff : diff);

                    rowMarkup += '<tr style="line-height: 2.0;">' +
                        '<td style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; text-align: center; font-size: 13px;">' + it.stt + '</td>' +
                        '<td style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; font-family: monospace; font-size: 11px;">' + escapeHtml(it.sku) + '</td>' +
                        '<td style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; font-weight: 600; font-size: 13px;">' + escapeHtml(it.name) + '</td>' +
                        '<td style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; text-align: center; font-size: 12.5px;">' + escapeHtml(it.uom) + '</td>' +
                        '<td style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; text-align: center; color: rgba(16,55,92,0.6);">' + it.systemQty + '</td>' +
                        '<td style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; text-align: center; font-weight: 700; color: var(--navy);">' + it.physicalQty + '</td>' +
                        '<td style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; text-align: center; font-weight: 800; color: ' + diffColor + '; background: ' + (diff !== 0 ? 'rgba(16, 55, 92, 0.02)' : 'none') + ';">' + diffText + '</td>' +
                        '<td style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; font-size: 12px; color: rgba(16,55,92,0.6);">' + (it.remarks ? escapeHtml(it.remarks) : '<div style="border-bottom: 1px dashed rgba(16,55,92,0.15); height: 10px;"></div>') + '</td>' +
                    '</tr>';
                });

                return '<div class="pdf-print-area" style="padding: 32px; background: #fff; font-family: \'Inter\', sans-serif;">' +
                    '<div style="margin-bottom: 24px;">' +
                        '<h1 style="margin: 0 0 2px; font-size: 24px; font-weight: 850; color: var(--navy); letter-spacing: -0.02em;">BIÊN BẢN KIỂM KÊ KHO</h1>' +
                        '<div style="font-size: 13.5px; font-weight: 500; color: rgba(16, 55, 92, 0.50); text-transform: uppercase; letter-spacing: 0.05em;">PHYSICAL INVENTORY AUDIT (PIA)</div>' +
                    '</div>' +
                    '<div style="display: flex; align-items: center; gap: 16px; margin-bottom: 24px;">' +
                        '<div>' +
                            '<label style="display: block; font-size: 11px; font-weight: 700; text-transform: uppercase; color: rgba(16, 55, 92, 0.50); letter-spacing: 0.05em; margin-bottom: 4px;">Mã Biên Bản (Audit PIA No.)</label>' +
                            '<span style="font-size: 18px; font-weight: 700; color: var(--navy); font-family: monospace;">' + escapeHtml(doc.id) + '</span>' +
                        '</div>' +
                    '</div>' +
                    '<div style="display: grid; grid-template-columns: 1fr 1fr; gap: 24px; margin-bottom: 24px; border-bottom: 1px solid #E5EAF3; padding-bottom: 24px;">' +
                        '<div style="display: flex; flex-direction: column; gap: 16px;">' +
                            '<div>' +
                                '<label style="display: block; font-size: 11px; font-weight: 700; text-transform: uppercase; color: rgba(16, 55, 92, 0.50); letter-spacing: 0.05em; margin-bottom: 4px;">Địa điểm kiểm kê (Warehouse Location)</label>' +
                                '<div style="font-size: 14px; font-weight: 600; color: var(--navy);">' + escapeHtml(doc.warehouse) + '</div>' +
                            '</div>' +
                            '<div>' +
                                '<label style="display: block; font-size: 11px; font-weight: 700; text-transform: uppercase; color: rgba(16, 55, 92, 0.50); letter-spacing: 0.05em; margin-bottom: 4px;">Trưởng Ban Kiểm Kê / Người Lập</label>' +
                                '<div style="font-size: 14px; font-weight: 600; color: var(--navy);">' + escapeHtml(doc.createdBy || '—') + '</div>' +
                            '</div>' +
                        '</div>' +
                        '<div style="display: flex; flex-direction: column; gap: 16px;">' +
                            '<div>' +
                                '<label style="display: block; font-size: 11px; font-weight: 700; text-transform: uppercase; color: rgba(16, 55, 92, 0.50); letter-spacing: 0.05em; margin-bottom: 4px;">Thời Điểm Kiểm Kê (Audit Date & Time)</label>' +
                                '<div style="font-size: 14px; color: var(--navy);">' + day + '/' + month + '/' + year + ' - ' + time + '</div>' +
                            '</div>' +
                            '<div>' +
                                '<label style="display: block; font-size: 11px; font-weight: 700; text-transform: uppercase; color: rgba(16, 55, 92, 0.50); letter-spacing: 0.05em; margin-bottom: 4px;">Trạng Thái (Status)</label>' +
                                '<span style="display: inline-block; padding: 4px 10px; font-size: 12px; font-weight: 700; color: #b45309; background: #fffbeb; border: 1px solid #fde68a; border-radius: var(--radius-btn);">' + escapeHtml(doc.status) + '</span>' +
                            '</div>' +
                        '</div>' +
                    '</div>' +
                    '<div style="margin-bottom: 24px;">' +
                        '<h2 style="font-size: 15px; font-weight: 700; color: var(--navy); margin-bottom: 16px;">Kết Quả Kiểm Kê Sản Phẩm Chi Tiết</h2>' +
                        '<table style="width: 100%; border-collapse: collapse; border: 2px solid rgba(16, 55, 92, 0.15); margin-bottom: 24px;">' +
                            '<thead>' +
                                '<tr style="background: var(--alice); border-bottom: 2px solid rgba(16, 55, 92, 0.15);">' +
                                    '<th style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; font-size: 10px; font-weight: 700; text-transform: uppercase; color: rgba(16, 55, 92, 0.50); text-align: center; width: 35px;">STT</th>' +
                                    '<th style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; font-size: 10px; font-weight: 700; text-transform: uppercase; color: rgba(16, 55, 92, 0.50); text-align: left;">Mã SKU</th>' +
                                    '<th style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; font-size: 10px; font-weight: 700; text-transform: uppercase; color: rgba(16, 55, 92, 0.50); text-align: left;">Tên Sản Phẩm</th>' +
                                    '<th style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; font-size: 10px; font-weight: 700; text-transform: uppercase; color: rgba(16, 55, 92, 0.50); text-align: center;">ĐVT</th>' +
                                    '<th style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; font-size: 10px; font-weight: 700; text-transform: uppercase; color: rgba(16, 55, 92, 0.50); text-align: center;">SL Hệ Thống</th>' +
                                    '<th style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; font-size: 10px; font-weight: 700; text-transform: uppercase; color: rgba(16, 55, 92, 0.50); text-align: center; background: rgba(16, 55, 92, 0.03);">SL Thực Tế</th>' +
                                    '<th style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; font-size: 10px; font-weight: 700; text-transform: uppercase; color: rgba(16, 55, 92, 0.50); text-align: center;">Chênh Lệch</th>' +
                                    '<th style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; font-size: 10px; font-weight: 700; text-transform: uppercase; color: rgba(16, 55, 92, 0.50); text-align: left;">Nguyên Nhân & Xử Lý</th>' +
                                '</tr>' +
                            '</thead>' +
                            '<tbody>' + rowMarkup +
                                '<tr style="background: rgba(240, 244, 250, 0.5); font-weight: 700; border-top: 2px solid rgba(16, 55, 92, 0.3);">' +
                                    '<td colspan="4" style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 12px; text-align: right;">TỔNG CỘNG:</td>' +
                                    '<td style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 12px; text-align: center;">' + totalSystem + '</td>' +
                                    '<td style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 12px; text-align: center;">' + totalPhysical + '</td>' +
                                    '<td style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 12px; text-align: center; color: ' + (totalDiff === 0 ? 'inherit' : (totalDiff > 0 ? '#059669' : '#dc2626')) + ';">' + (totalDiff > 0 ? "+" + totalDiff : totalDiff) + '</td>' +
                                    '<td style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 12px;"></td>' +
                                '</tr>' +
                            '</tbody>' +
                        '</table>' +
                    '</div>' +
                    '<div style="margin-top: 24px; display: grid; grid-template-columns: repeat(3, 1fr); gap: 24px; text-align: center;">' +
                        '<div>' +
                            '<div style="font-size: 11px; font-weight: 700; text-transform: uppercase; color: rgba(16,55,92,0.50); margin-bottom: 40px;">Thành Viên Ban Kiểm Kê</div>' +
                            '<div style="border-bottom: 2px solid rgba(16,55,92,0.15); width: 80%; margin: 0 auto 4px;"></div>' +
                            '<span style="font-size: 10px; color: rgba(16,55,92,0.40); font-style: italic;">(Ký, ghi rõ họ tên)</span>' +
                        '</div>' +
                        '<div>' +
                            '<div style="font-size: 11px; font-weight: 700; text-transform: uppercase; color: rgba(16,55,92,0.50); margin-bottom: 40px;">Đại Diện Kiểm Toán/Kế Toán</div>' +
                            '<div style="border-bottom: 2px solid rgba(16,55,92,0.15); width: 80%; margin: 0 auto 4px;"></div>' +
                            '<span style="font-size: 10px; color: rgba(16,55,92,0.40); font-style: italic;">(Ký, ghi rõ họ tên)</span>' +
                        '</div>' +
                        '<div>' +
                            '<div style="font-size: 11px; font-weight: 700; text-transform: uppercase; color: rgba(16,55,92,0.50); margin-bottom: 40px;">Giám Đốc Chi Nhánh / Thủ Kho</div>' +
                            '<div style="border-bottom: 2px solid rgba(16,55,92,0.15); width: 80%; margin: 0 auto 4px;"></div>' +
                            '<span style="font-size: 10px; color: rgba(16,55,92,0.40); font-style: italic;">(Ký, ghi rõ họ tên)</span>' +
                        '</div>' +
                    '</div>' +
                '</div>';
            }

            if (doc.type === "Phiếu Chuyển Kho") {
                var totalQty = 0;
                var rowMarkup = "";
                items.forEach(function(it) {
                    totalQty += it.qty || 0;

                    rowMarkup += '<tr style="line-height: 2.0;">' +
                        '<td style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; text-align: center; font-size: 13px;">' + it.stt + '</td>' +
                        '<td style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; font-family: monospace; font-size: 11px;">' + escapeHtml(it.sku) + '</td>' +
                        '<td style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; font-weight: 600; font-size: 13px;">' + escapeHtml(it.name) + '</td>' +
                        '<td style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; text-align: center; font-size: 12.5px;">' + escapeHtml(it.uom) + '</td>' +
                        '<td style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; text-align: center; font-weight: 700; color: #ea580c; background: rgba(234, 88, 12, 0.05);">' + it.qty + '</td>' +
                        '<td style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; font-size: 12px; color: rgba(16,55,92,0.6);">' + (it.remarks ? escapeHtml(it.remarks) : '<div style="border-bottom: 1px dashed rgba(16,55,92,0.15); height: 10px;"></div>') + '</td>' +
                    '</tr>';
                });

                var fromWh = doc.warehouse || "Kho gửi";
                var toWh = doc.toWarehouse || "Kho nhận";

                return '<div class="pdf-print-area" style="padding: 32px; background: #fff; font-family: \'Inter\', sans-serif;">' +
                    '<div style="margin-bottom: 24px;">' +
                        '<h1 style="margin: 0 0 2px; font-size: 24px; font-weight: 850; color: var(--navy); letter-spacing: -0.02em;">PHIẾU CHUYỂN KHO NỘI BỘ</h1>' +
                        '<div style="font-size: 13.5px; font-weight: 500; color: rgba(16, 55, 92, 0.50); text-transform: uppercase; letter-spacing: 0.05em;">STOCK TRANSFER NOTE (STN)</div>' +
                    '</div>' +
                    '<div style="display: flex; align-items: center; gap: 16px; margin-bottom: 24px;">' +
                        '<div>' +
                            '<label style="display: block; font-size: 11px; font-weight: 700; text-transform: uppercase; color: rgba(16, 55, 92, 0.50); letter-spacing: 0.05em; margin-bottom: 4px;">Mã Phiếu Chuyển (STN No.)</label>' +
                            '<span style="font-size: 18px; font-weight: 700; color: var(--navy); font-family: monospace;">' + escapeHtml(doc.id) + '</span>' +
                        '</div>' +
                    '</div>' +
                    '<div style="display: grid; grid-template-columns: 1fr 1fr; gap: 24px; margin-bottom: 24px; border-bottom: 1px solid #E5EAF3; padding-bottom: 24px;">' +
                        '<div style="display: flex; flex-direction: column; gap: 16px;">' +
                            '<div>' +
                                '<label style="display: block; font-size: 11px; font-weight: 700; text-transform: uppercase; color: rgba(16, 55, 92, 0.50); letter-spacing: 0.05em; margin-bottom: 4px;">Kho Xuất Phát (Source WH)</label>' +
                                '<div style="font-size: 14px; font-weight: 600; color: var(--navy);">' + escapeHtml(fromWh) + '</div>' +
                            '</div>' +
                            '<div>' +
                                '<label style="display: block; font-size: 11px; font-weight: 700; text-transform: uppercase; color: rgba(16, 55, 92, 0.50); letter-spacing: 0.05em; margin-bottom: 4px;">Kho Đích Đến (Destination WH)</label>' +
                                '<div style="font-size: 14px; font-weight: 600; color: var(--navy);">' + escapeHtml(toWh) + '</div>' +
                            '</div>' +
                        '</div>' +
                        '<div style="display: flex; flex-direction: column; gap: 16px;">' +
                            '<div>' +
                                '<label style="display: block; font-size: 11px; font-weight: 700; text-transform: uppercase; color: rgba(16, 55, 92, 0.50); letter-spacing: 0.05em; margin-bottom: 4px;">Ngày Chuyển (STN Date)</label>' +
                                '<div style="font-size: 14px; color: var(--navy);">' + day + '/' + month + '/' + year + ' - ' + time + '</div>' +
                            '</div>' +
                            '<div>' +
                                '<label style="display: block; font-size: 11px; font-weight: 700; text-transform: uppercase; color: rgba(16, 55, 92, 0.50); letter-spacing: 0.05em; margin-bottom: 4px;">Trạng Thái (Status)</label>' +
                                '<span style="display: inline-block; padding: 4px 10px; font-size: 12px; font-weight: 700; color: #c2410c; background: #fff7ed; border: 1px solid #ffedd5; border-radius: var(--radius-btn);">' + escapeHtml(doc.status) + '</span>' +
                            '</div>' +
                            '<div>' +
                                '<label style="display: block; font-size: 11px; font-weight: 700; text-transform: uppercase; color: rgba(16, 55, 92, 0.50); letter-spacing: 0.05em; margin-bottom: 4px;">Người Lập Lệnh Chuyển</label>' +
                                '<div style="font-size: 14px; font-weight: 600; color: var(--navy);">' + escapeHtml(doc.createdBy || '—') + '</div>' +
                            '</div>' +
                        '</div>' +
                    '</div>' +
                    '<div style="margin-bottom: 24px;">' +
                        '<h2 style="font-size: 15px; font-weight: 700; color: var(--navy); margin-bottom: 16px;">Danh Mục Sản Phẩm Chuyển Kho</h2>' +
                        '<table style="width: 100%; border-collapse: collapse; border: 2px solid rgba(16, 55, 92, 0.15); margin-bottom: 24px;">' +
                            '<thead>' +
                                '<tr style="background: var(--alice); border-bottom: 2px solid rgba(16, 55, 92, 0.15);">' +
                                    '<th style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; font-size: 10px; font-weight: 700; text-transform: uppercase; color: rgba(16, 55, 92, 0.50); text-align: center; width: 35px;">STT</th>' +
                                    '<th style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; font-size: 10px; font-weight: 700; text-transform: uppercase; color: rgba(16, 55, 92, 0.50); text-align: left;">Mã SKU</th>' +
                                    '<th style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; font-size: 10px; font-weight: 700; text-transform: uppercase; color: rgba(16, 55, 92, 0.50); text-align: left;">Tên Sản Phẩm</th>' +
                                    '<th style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; font-size: 10px; font-weight: 700; text-transform: uppercase; color: rgba(16, 55, 92, 0.50); text-align: center;">ĐVT</th>' +
                                    '<th style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; font-size: 10px; font-weight: 700; text-transform: uppercase; color: rgba(16, 55, 92, 0.50); text-align: center; background: rgba(234, 88, 12, 0.05); color: #ea580c;">SL Chuyển</th>' +
                                    '<th style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; font-size: 10px; font-weight: 700; text-transform: uppercase; color: rgba(16, 55, 92, 0.50); text-align: left;">Ghi Chú</th>' +
                                '</tr>' +
                            '</thead>' +
                            '<tbody>' + rowMarkup +
                                '<tr style="background: rgba(240, 244, 250, 0.5); font-weight: 700; border-top: 2px solid rgba(16, 55, 92, 0.3);">' +
                                    '<td colspan="4" style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 12px; text-align: right;">TỔNG CỘNG:</td>' +
                                    '<td style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 12px; text-align: center; color: #ea580c; background: rgba(234, 88, 12, 0.05);">' + totalQty + '</td>' +
                                    '<td style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 12px;"></td>' +
                                '</tr>' +
                            '</tbody>' +
                        '</table>' +
                    '</div>' +
                    '<div style="margin-top: 24px; display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; text-align: center;">' +
                        '<div>' +
                            '<div style="font-size: 10px; font-weight: 700; text-transform: uppercase; color: rgba(16,55,92,0.60); margin-bottom: 30px;">Người Lập Lệnh</div>' +
                            '<div style="border-bottom: 1.5px solid rgba(16,55,92,0.15); width: 85%; margin: 0 auto 4px;"></div>' +
                            '<span style="font-size: 9px; color: rgba(16,55,92,0.40);">(Ký, họ tên)</span>' +
                        '</div>' +
                        '<div>' +
                            '<div style="font-size: 10px; font-weight: 700; text-transform: uppercase; color: rgba(16,55,92,0.60); margin-bottom: 30px;">Thủ Kho Xuất</div>' +
                            '<div style="border-bottom: 1.5px solid rgba(16,55,92,0.15); width: 85%; margin: 0 auto 4px;"></div>' +
                            '<span style="font-size: 9px; color: rgba(16,55,92,0.40);">(Ký, họ tên)</span>' +
                        '</div>' +
                        '<div>' +
                            '<div style="font-size: 10px; font-weight: 700; text-transform: uppercase; color: rgba(16,55,92,0.60); margin-bottom: 30px;">Người Vận Chuyển</div>' +
                            '<div style="border-bottom: 1.5px solid rgba(16,55,92,0.15); width: 85%; margin: 0 auto 4px;"></div>' +
                            '<span style="font-size: 9px; color: rgba(16,55,92,0.40);">(Ký, họ tên)</span>' +
                        '</div>' +
                        '<div>' +
                            '<div style="font-size: 10px; font-weight: 700; text-transform: uppercase; color: rgba(16,55,92,0.60); margin-bottom: 30px;">Thủ Kho Nhận</div>' +
                            '<div style="border-bottom: 1.5px solid rgba(16,55,92,0.15); width: 85%; margin: 0 auto 4px;"></div>' +
                            '<span style="font-size: 9px; color: rgba(16,55,92,0.40);">(Ký, họ tên)</span>' +
                        '</div>' +
                    '</div>' +
                '</div>';
            }

            if (doc.type === "Phiếu Hoàn Hàng") {
                var totalReturned = 0, totalReuse = 0, totalDestroy = 0, totalVal = 0;
                var rowMarkup = "";
                items.forEach(function(it) {
                    totalReturned += it.returnedQty || 0;
                    totalReuse += it.reuseQty || 0;
                    totalDestroy += it.destroyQty || 0;
                    totalVal += (it.returnedQty || 0) * (it.price || 0);

                    rowMarkup += '<tr style="line-height: 2.0;">' +
                        '<td style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; text-align: center; font-size: 13px;">' + it.stt + '</td>' +
                        '<td style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; font-family: monospace; font-size: 11px;">' + escapeHtml(it.sku) + '</td>' +
                        '<td style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; font-weight: 600; font-size: 13px;">' + escapeHtml(it.name) + '</td>' +
                        '<td style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; text-align: center; font-size: 12.5px;">' + escapeHtml(it.uom) + '</td>' +
                        '<td style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; text-align: center; color: rgba(16,55,92,0.6);">' + it.returnedQty + '</td>' +
                        '<td style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; text-align: center; font-weight: 800; color: #059669; background: rgba(16, 185, 129, 0.05);">' + it.reuseQty + '</td>' +
                        '<td style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; text-align: center; font-weight: 800; color: #dc2626; background: rgba(239, 68, 68, 0.05);">' + it.destroyQty + '</td>' +
                        '<td style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; font-size: 12px; color: rgba(16,55,92,0.6);">' + (it.remarks ? escapeHtml(it.remarks) : '<div style="border-bottom: 1px dashed rgba(16,55,92,0.15); height: 10px;"></div>') + '</td>' +
                    '</tr>';
                });

                var customer = doc.customer || "Khách hàng gửi trả";

                return '<div class="pdf-print-area" style="padding: 32px; background: #fff; font-family: \'Inter\', sans-serif;">' +
                    '<div style="margin-bottom: 24px;">' +
                        '<h1 style="margin: 0 0 2px; font-size: 24px; font-weight: 850; color: var(--navy); letter-spacing: -0.02em;">PHIẾU TIẾP NHẬN HÀNG HOÀN (RMA)</h1>' +
                        '<div style="font-size: 13.5px; font-weight: 500; color: rgba(16, 55, 92, 0.50); text-transform: uppercase; letter-spacing: 0.05em;">RETURN MERCHANDISE AUTHORIZATION (RMA)</div>' +
                    '</div>' +
                    '<div style="display: flex; align-items: center; gap: 16px; margin-bottom: 24px;">' +
                        '<div>' +
                            '<label style="display: block; font-size: 11px; font-weight: 700; text-transform: uppercase; color: rgba(16, 55, 92, 0.50); letter-spacing: 0.05em; margin-bottom: 4px;">Mã RMA (RMA Ref No.)</label>' +
                            '<span style="font-size: 18px; font-weight: 700; color: var(--navy); font-family: monospace;">' + escapeHtml(doc.id) + '</span>' +
                        '</div>' +
                    '</div>' +
                    '<div style="display: grid; grid-template-columns: 1fr 1fr; gap: 24px; margin-bottom: 24px; border-bottom: 1px solid #E5EAF3; padding-bottom: 24px;">' +
                        '<div style="display: flex; flex-direction: column; gap: 16px;">' +
                            '<div>' +
                                '<label style="display: block; font-size: 11px; font-weight: 700; text-transform: uppercase; color: rgba(16, 55, 92, 0.50); letter-spacing: 0.05em; margin-bottom: 4px;">Khách Hàng Trả Lại (Customer)</label>' +
                                '<div style="font-size: 14px; font-weight: 600; color: var(--navy);">' + escapeHtml(customer) + '</div>' +
                            '</div>' +
                            '<div>' +
                                '<label style="display: block; font-size: 11px; font-weight: 700; text-transform: uppercase; color: rgba(16, 55, 92, 0.50); letter-spacing: 0.05em; margin-bottom: 4px;">Hóa Đơn Tham Chiếu (Order Reference)</label>' +
                                '<div style="font-size: 16px; font-weight: 700; color: var(--navy);">' + escapeHtml(doc.poReference || "—") + '</div>' +
                            '</div>' +
                        '</div>' +
                        '<div style="display: flex; flex-direction: column; gap: 16px;">' +
                            '<div>' +
                                '<label style="display: block; font-size: 11px; font-weight: 700; text-transform: uppercase; color: rgba(16, 55, 92, 0.50); letter-spacing: 0.05em; margin-bottom: 4px;">Ngày Tiếp Nhận (Returned Date)</label>' +
                                '<div style="font-size: 14px; color: var(--navy);">' + day + '/' + month + '/' + year + ' - ' + time + '</div>' +
                            '</div>' +
                            '<div>' +
                                '<label style="display: block; font-size: 11px; font-weight: 700; text-transform: uppercase; color: rgba(16, 55, 92, 0.50); letter-spacing: 0.05em; margin-bottom: 4px;">Trạng Thái (Status)</label>' +
                                '<span style="display: inline-block; padding: 4px 10px; font-size: 12px; font-weight: 700; color: #dc2626; background: #fef2f2; border: 1px solid #fca5a5; border-radius: var(--radius-btn);">' + escapeHtml(doc.status) + '</span>' +
                            '</div>' +
                            '<div>' +
                                '<label style="display: block; font-size: 11px; font-weight: 700; text-transform: uppercase; color: rgba(16, 55, 92, 0.50); letter-spacing: 0.05em; margin-bottom: 4px;">Trị Giá Đổi Trả Ước Tính</label>' +
                                '<div style="font-size: 15px; font-weight: 700; color: var(--navy);">' + totalVal.toLocaleString('vi-VN') + ' VNĐ</div>' +
                            '</div>' +
                        '</div>' +
                    '</div>' +
                    '<div style="margin-bottom: 24px;">' +
                        '<h2 style="font-size: 15px; font-weight: 700; color: var(--navy); margin-bottom: 16px;">Chi Tiết Phân Phối Hàng Trả Về (Phân Loại Chất Lượng)</h2>' +
                        '<table style="width: 100%; border-collapse: collapse; border: 2px solid rgba(16, 55, 92, 0.15); margin-bottom: 24px;">' +
                            '<thead>' +
                                '<tr style="background: var(--alice); border-bottom: 2px solid rgba(16, 55, 92, 0.15);">' +
                                    '<th style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; font-size: 10px; font-weight: 700; text-transform: uppercase; color: rgba(16, 55, 92, 0.50); text-align: center; width: 35px;">STT</th>' +
                                    '<th style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; font-size: 10px; font-weight: 700; text-transform: uppercase; color: rgba(16, 55, 92, 0.50); text-align: left;">Mã SKU</th>' +
                                    '<th style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; font-size: 10px; font-weight: 700; text-transform: uppercase; color: rgba(16, 55, 92, 0.50); text-align: left;">Tên Sản Phẩm</th>' +
                                    '<th style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; font-size: 10px; font-weight: 700; text-transform: uppercase; color: rgba(16, 55, 92, 0.50); text-align: center;">ĐVT</th>' +
                                    '<th style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; font-size: 10px; font-weight: 700; text-transform: uppercase; color: rgba(16, 55, 92, 0.50); text-align: center;">SL Nhận Trả</th>' +
                                    '<th style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; font-size: 10px; font-weight: 700; text-transform: uppercase; color: rgba(16, 55, 92, 0.50); text-align: center; background: rgba(16, 185, 129, 0.05); color: #059669;">Tái Sử Dụng (A)</th>' +
                                    '<th style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; font-size: 10px; font-weight: 700; text-transform: uppercase; color: rgba(16, 55, 92, 0.50); text-align: center; background: rgba(239, 68, 68, 0.05); color: #dc2626;">Hủy Bỏ (C)</th>' +
                                    '<th style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 10px 12px; font-size: 10px; font-weight: 700; text-transform: uppercase; color: rgba(16, 55, 92, 0.50); text-align: left;">Nguyên Nhân Trả Hàng</th>' +
                                '</tr>' +
                            '</thead>' +
                            '<tbody>' + rowMarkup +
                                '<tr style="background: rgba(240, 244, 250, 0.5); font-weight: 700; border-top: 2px solid rgba(16, 55, 92, 0.3);">' +
                                    '<td colspan="4" style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 12px; text-align: right;">TỔNG CỘNG:</td>' +
                                    '<td style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 12px; text-align: center;">' + totalReturned + '</td>' +
                                    '<td style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 12px; text-align: center; color: #059669; background: rgba(16, 185, 129, 0.05);">' + totalReuse + '</td>' +
                                    '<td style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 12px; text-align: center; color: #dc2626; background: rgba(239, 68, 68, 0.05);">' + totalDestroy + '</td>' +
                                    '<td style="border: 1px solid rgba(16, 55, 92, 0.15); padding: 12px;"></td>' +
                                '</tr>' +
                            '</tbody>' +
                        '</table>' +
                    '</div>' +
                    '<div style="margin-top: 24px; display: grid; grid-template-columns: repeat(3, 1fr); gap: 24px; text-align: center;">' +
                        '<div>' +
                            '<div style="font-size: 11px; font-weight: 700; text-transform: uppercase; color: rgba(16,55,92,0.50); margin-bottom: 40px;">Khách Hàng Đồng Ý Trả</div>' +
                            '<div style="border-bottom: 2px solid rgba(16,55,92,0.15); width: 80%; margin: 0 auto 4px;"></div>' +
                            '<span style="font-size: 10px; color: rgba(16,55,92,0.40); font-style: italic;">(Ký, ghi rõ họ tên)</span>' +
                        '</div>' +
                        '<div>' +
                            '<div style="font-size: 11px; font-weight: 700; text-transform: uppercase; color: rgba(16,55,92,0.50); margin-bottom: 40px;">Kiểm Đếm Viên (QC)</div>' +
                            '<div style="border-bottom: 2px solid rgba(16,55,92,0.15); width: 80%; margin: 0 auto 4px;"></div>' +
                            '<span style="font-size: 10px; color: rgba(16,55,92,0.40); font-style: italic;">(Ký, ghi rõ họ tên)</span>' +
                        '</div>' +
                        '<div>' +
                            '<div style="font-size: 11px; font-weight: 700; text-transform: uppercase; color: rgba(16,55,92,0.50); margin-bottom: 40px;">Quản Trị Kho Nhận</div>' +
                            '<div style="border-bottom: 2px solid rgba(16,55,92,0.15); width: 80%; margin: 0 auto 4px;"></div>' +
                            '<span style="font-size: 10px; color: rgba(16,55,92,0.40); font-style: italic;">(Ký, ghi rõ họ tên)</span>' +
                        '</div>' +
                    '</div>' +
                '</div>';
            }

            return "";
        }

        // Render documents table
        function renderDocs() {
            OmniPagination.reset('ledgerDocs');
            renderDocsPage();
        }

        function renderDocsPage() {
            var html = "";

            // ─── Filter Logic ───
            var filtered = docs.filter(function(d) {
                var matchesSearch = d.id.toLowerCase().indexOf(searchQuery) !== -1 ||
                                    d.type.toLowerCase().indexOf(searchQuery) !== -1 ||
                                    d.createdBy.toLowerCase().indexOf(searchQuery) !== -1;
                var matchesTab = activeTab === "all" || d.type === activeTab;
                var matchesWarehouse = selectedWh === "all" || d.warehouse === selectedWh;
                return matchesSearch && matchesTab && matchesWarehouse;
            });

            // Count tab badges
            var totalAll = 0, totalGrn = 0, totalGi = 0, totalKk = 0, totalTr = 0, totalRma = 0;
            docs.forEach(function(d) {
                var matchesWarehouse = selectedWh === "all" || d.warehouse === selectedWh;
                if (matchesWarehouse) {
                    totalAll++;
                    if (d.type === "Phiếu Nhập Kho") totalGrn++;
                    else if (d.type === "Phiếu Xuất Kho") totalGi++;
                    else if (d.type === "Phiếu Kiểm Kê") totalKk++;
                    else if (d.type === "Phiếu Chuyển Kho") totalTr++;
                    else if (d.type === "Phiếu Hoàn Hàng") totalRma++;
                }
            });
            countAll.textContent = totalAll;
            countGrn.textContent = totalGrn;
            countGi.textContent = totalGi;
            countKk.textContent = totalKk;
            countTr.textContent = totalTr;
            countRma.textContent = totalRma;

            // Summary text
            docCountSummary.textContent = filtered.length + " / " + totalAll + " phiếu";
            docTableFooter.textContent = "Hiển thị " + filtered.length + " / " + totalAll + " chứng từ";

            // Count pending approval alerts
            var actionablePending = 0;
            docs.forEach(function(d) {
                var matchesWarehouse = selectedWh === "all" || d.warehouse === selectedWh;
                if (matchesWarehouse && isAwaitingBM(d)) {
                    actionablePending++;
                }
            });
            if (actionablePending > 0) {
                docAlertBannerText.textContent = "Bạn đang có " + actionablePending + " phiếu đang chờ phê duyệt.";
                docAlertBanner.style.display = 'flex';
            } else {
                docAlertBanner.style.display = 'none';
            }

            // Table rows generation
            if (filtered.length === 0) {
                html = '<tr>' +
                    '<td colspan="8" style="padding: 48px 0; text-align: center;">' +
                        '<div style="display: flex; flex-direction: column; align-items: center; gap: 12px; color: rgba(16, 55, 92, 0.2);">' +
                            '<svg style="width: 48px; height: 48px;" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.5">' +
                                '<path stroke-linecap="round" stroke-linejoin="round" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"/>' +
                            '</svg>' +
                            '<div style="font-size: 14px; color: rgba(16, 55, 92, 0.40);">' +
                                (searchQuery ? "Không tìm thấy phiếu nào phù hợp" : "Chưa có phiếu nào") +
                            '</div>' +
                        '</div>' +
                    '</td>' +
                '</tr>';
                document.getElementById('docPagination').innerHTML = '';
            } else {
                var paginationResult = OmniPagination.paginate('ledgerDocs', filtered);
                var pageDocs = paginationResult.items;
                pageDocs.forEach(function(d) {
                    var cfg = DOC_TYPE_CONFIG[d.type] || { bg: "grn", icon: "", shortName: d.type };
                    var draft = isDraft(d);
                    var awaitingBM = isAwaitingBM(d);
                    var rmaPending = isRMAPendingWH(d);
                    var viewable = isViewable(d);
                    var rejected = isRejected(d);

                    var rowClass = "row-normal";
                    if (viewable) rowClass = "row-viewable";
                    else if (draft) rowClass = "row-draft";
                    else if (awaitingBM) rowClass = "row-pending";
                    else if (rejected) rowClass = "row-rejected";

                    var rowOnClickAttr = viewable ? 'onclick="viewDocDetails(\'' + d.id + '\')"' : '';

                    // Build operations markup
                    var actionHtml = "";
                    if (awaitingBM) {
                        actionHtml = '<div style="display:flex; justify-content:center; gap:8px;">' +
                            '<button class="btn-action-approve" onclick="confirmApprove(\'' + d.id + '\', \'' + d.type + '\', event)">Duyệt</button>' +
                            '<button class="btn-action-reject" onclick="confirmReject(\'' + d.id + '\', \'' + d.type + '\', event)">Từ chối</button>' +
                            '</div>';
                    } else if (viewable || rejected || draft || rmaPending) {
                        actionHtml = '<div class="btn-action-view-eye" title="Xem chứng từ chi tiết" onclick="viewDocDetails(\'' + d.id + '\', event)" style="cursor:pointer;">' +
                            '<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">' +
                                '<path stroke-linecap="round" stroke-linejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"/>' +
                                '<path stroke-linecap="round" stroke-linejoin="round" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z"/>' +
                            '</svg>' +
                            '</div>';
                    } else {
                        actionHtml = '<span style="color: rgba(16, 55, 92, 0.20);">—</span>';
                    }

                    html += '<tr class="' + rowClass + '" ' + rowOnClickAttr + '>' +
                        '<td style="padding-left: 20px;">' +
                            '<div style="display: flex; align-items: center; gap: 10px;">' +
                                '<div class="doc-type-icon-wrapper ' + cfg.bg + '">' + cfg.icon + '</div>' +
                                '<span class="doc-id-text">' + escapeHtml(d.id) + '</span>' +
                            '</div>' +
                        '</td>' +
                        '<td>' +
                            '<span class="doc-type-badge ' + cfg.bg + '">' + cfg.shortName + '</span>' +
                        '</td>' +
                        '<td>' +
                            '<div style="display: flex; align-items: center; gap: 6px; color: rgba(16,55,92,0.70); max-width: 220px; text-overflow: ellipsis; overflow: hidden; white-space: nowrap;">' +
                                '<svg style="width: 14px; height: 14px; flex-shrink: 0;" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">' +
                                    '<path stroke-linecap="round" stroke-linejoin="round" d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4"/>' +
                                '</svg>' +
                                '<span title="' + escapeHtml(d.warehouse) + '">' + escapeHtml(d.warehouse) + '</span>' +
                            '</div>' +
                        '</td>' +
                        '<td>' + escapeHtml(d.createdBy) + '</td>' +
                        '<td>' +
                            '<div style="display: flex; align-items: center; gap: 6px; color: rgba(16,55,92,0.70);">' +
                                '<svg style="width: 14px; height: 14px;" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">' +
                                    '<path stroke-linecap="round" stroke-linejoin="round" d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z"/>' +
                                '</svg>' +
                                '<span>' + escapeHtml(d.date) + '</span>' +
                            '</div>' +
                        '</td>' +
                        '<td class="text-right" style="font-weight: 700;">' + d.items + '</td>' +
                        '<td class="text-center">' +
                            '<span class="doc-status-badge" style="background: color-mix(in srgb, ' + d.statusColor + ' 12%, transparent); color: ' + d.statusColor + ';">' +
                                '<div class="doc-status-dot" style="background: ' + d.statusColor + ';"></div>' +
                                escapeHtml(d.status) +
                            '</span>' +
                        '</td>' +
                        '<td class="text-center" style="padding-right: 20px;" onclick="event.stopPropagation()">' + actionHtml + '</td>' +
                    '</tr>';
                });

                OmniPagination.renderControls('docPagination', paginationResult.currentPage, paginationResult.totalPages, function (newPage) {
                    OmniPagination.setPage('ledgerDocs', newPage);
                    renderDocsPage();
                });
            }

            docTableBody.innerHTML = html;
        }

        function escapeHtml(text) {
            if (!text) return "";
            return text.toString()
                .replace(/&/g, "&amp;")
                .replace(/</g, "&lt;")
                .replace(/>/g, "&gt;")
                .replace(/"/g, "&quot;")
                .replace(/'/g, "&#039;");
        }

        // Initial render
        renderDocs();

    })();
</script>
