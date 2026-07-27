<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ page import="com.wms.model.OutboundOrder" %>
<%@ page import="com.wms.model.OutboundItem" %>
<%@ page import="com.wms.model.FulfillmentRequest" %>
<%@ page import="com.wms.model.FulfillmentRequestItem" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.HashMap" %>
<%
    List<OutboundOrder> outboundOrders = (List<OutboundOrder>) request.getAttribute("outboundOrders");
    if (outboundOrders == null) outboundOrders = java.util.Collections.emptyList();

    List<FulfillmentRequest> fulfillmentRequests = (List<FulfillmentRequest>) request.getAttribute("fulfillmentRequests");
    if (fulfillmentRequests == null) fulfillmentRequests = java.util.Collections.emptyList();

    Map<String, Integer> statusCounts = new HashMap<>();
    statusCounts.put("ALL", outboundOrders.size());
    statusCounts.put("PENDING_PACK", 0);
    statusCounts.put("PACKED", 0);
    statusCounts.put("HANDED_OVER", 0);
    statusCounts.put("SHIPPED", 0);
    statusCounts.put("CANCELLED", 0);
    for (OutboundOrder o : outboundOrders) {
        String s = o.getStatus();
        if (s != null) {
            statusCounts.put(s, statusCounts.getOrDefault(s, 0) + 1);
        }
    }

    com.fasterxml.jackson.databind.ObjectMapper mapper = com.wms.util.JsonUtil.getMapper();
    request.setAttribute("outboundOrdersJson", mapper.valueToTree(outboundOrders).toString());
    request.setAttribute("statusCountsJson", mapper.valueToTree(statusCounts).toString());
    request.setAttribute("fulfillmentRequestsJson", mapper.valueToTree(fulfillmentRequests).toString());
%>

<!-- Server-side JSON script tags (prevents JS syntax errors from unescaped quotes/newlines in Vietnamese text) -->
<script id="db-outbound-orders-data" type="application/json">${outboundOrdersJson}</script>
<script id="db-fulfillment-requests-data" type="application/json">${fulfillmentRequestsJson}</script>
<script id="db-products-data" type="application/json">${productsJson}</script>
<script id="db-scrap-products-data" type="application/json">${scrapProductsJson}</script>
<script id="db-inventory-stock-data" type="application/json">${inventoryStockJson}</script>

<link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/outbound--warehouse-outbound.css"/>

<!-- ══ TOAST NOTIFICATION ═══════════════════════════════════ -->
<c:if test="${not empty successMessage}">
<div class="wh-toast wh-toast-success" id="whToast">
    <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2.5">
        <path stroke-linecap="round" stroke-linejoin="round" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
    </svg>
    <span>${successMessage}</span>
</div>
</c:if>
<c:if test="${not empty errorMessage}">
<div class="wh-toast wh-toast-error" id="whToast">
    <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2.5">
        <path stroke-linecap="round" stroke-linejoin="round" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
    </svg>
    <span>${errorMessage}</span>
</div>
</c:if>

<div id="view-orders">
<!-- ══ SUMMARY STATS CARDS ════════════════════════════════════ -->
<div class="outbound-stats-grid-4">
    <!-- Pending Prepare -->
    <div class="outbound-kpi-card tone-blue">
        <div class="outbound-kpi-card__icon-box">
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/>
            </svg>
        </div>
        <div class="outbound-kpi-card__info">
            <div class="outbound-kpi-card__val" id="stat-pending-pick">0</div>
            <div class="outbound-kpi-card__lbl">Chờ đóng gói</div>
        </div>
    </div>
    
    <!-- Picking -->
    <div class="outbound-kpi-card tone-orange">
        <div class="outbound-kpi-card__icon-box">
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                <path d="M12 3v12m-4-4 4 4 4-4"/>
            </svg>
        </div>
        <div class="outbound-kpi-card__info">
            <div class="outbound-kpi-card__val" id="stat-picking-pack">0</div>
            <div class="outbound-kpi-card__lbl">Đang đóng gói</div>
        </div>
    </div>
    
    <!-- Packed -->
    <div class="outbound-kpi-card tone-purple">
        <div class="outbound-kpi-card__icon-box">
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/>
                <polyline points="3.27 6.96 12 12.01 20.73 6.96"/><line x1="12" y1="22.08" x2="12" y2="12"/>
            </svg>
        </div>
        <div class="outbound-kpi-card__info">
            <div class="outbound-kpi-card__val" id="stat-packed">0</div>
            <div class="outbound-kpi-card__lbl">Đã đóng gói</div>
        </div>
    </div>

    <!-- Dispatched -->
    <div class="outbound-kpi-card tone-emerald">
        <div class="outbound-kpi-card__icon-box">
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                <rect x="1" y="3" width="15" height="13"/><polygon points="16 8 20 8 23 11 23 16 16 16 16 8"/>
                <circle cx="5.5" cy="18.5" r="2.5"/><circle cx="18.5" cy="18.5" r="2.5"/>
            </svg>
        </div>
        <div class="outbound-kpi-card__info">
            <div class="outbound-kpi-card__val" id="stat-dispatched">0</div>
            <div class="outbound-kpi-card__lbl">Đã xuất hôm nay</div>
        </div>
    </div>
</div>

<!-- ══ DYNAMIC ALERTS BANNER ══════════════════════════════════ -->
<!-- Auto-created Fulfillment alert -->
<div id="fulfillment-alert-banner" class="outbound-alert-banner success" style="display:none;">
    <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
        <path stroke-linecap="round" stroke-linejoin="round" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
    </svg>
    <p>
        <span id="fulfillment-alert-count">0 lệnh xuất mới</span> vừa được Sales duyệt và đẩy xuống tự động. Nhấn <strong>"Tạo phiếu xuất"</strong> để xử lý ngay.
    </p>
</div>

<!-- Pending pick alert -->
<div id="pending-pick-alert-banner" class="outbound-alert-banner warning" style="display:none;">
    <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
        <path stroke-linecap="round" stroke-linejoin="round" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
    </svg>
    <p>
        Có <span id="pending-pick-alert-count">0</span> phiếu xuất đang chờ được xử lý. Vui lòng phân công ngay.
    </p>
</div>

<!-- ══ TOOLBAR ════════════════════════════════════════════════ -->
<div class="toolbar">
    <div class="search-wrap">
        <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
            <circle cx="11" cy="11" r="8"/><path d="m21 21-4.35-4.35"/>
        </svg>
        <input type="text" id="outboundSearch" placeholder="Tìm mã phiếu xuất, mã SO..." oninput="window.handleSearch()"/>
    </div>
    
    <button id="btn-open-draft-creator" onclick="window.openDraftModal()" class="btn-action-primary">
        <svg style="width: 14px; height: 14px;" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2.5">
            <path stroke-linecap="round" stroke-linejoin="round" d="M12 4v16m8-8H4" />
        </svg>
        Tạo phiếu xuất
    </button>
    
    <button onclick="window.openDisposalModal()" class="btn-action-red">
        <svg style="width: 14px; height: 14px;" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2.5">
            <path stroke-linecap="round" stroke-linejoin="round" d="M12 4v16m8-8H4" />
        </svg>
        Tạo Phiếu Xuất Hủy
    </button>
    

</div>

<!-- ══ STATUS FILTER TABS ═════════════════════════════════════ -->
<div class="status-tabs-wrap" id="statusTabsContainer">
    <!-- Rendered dynamically -->
</div>

<!-- ══ PICKING SUB-TABS (chỉ hiện khi activeTab === 'picking') ══ -->
<div class="pick-subtabs-wrap" id="pickSubTabsContainer" style="display:none;">
    <!-- Rendered dynamically -->
</div>

<!-- ══ OUTBOUND RECEIPTS LIST ═════════════════════════════════ -->
<div class="outbound-list" id="outboundOrdersContainer">
    <!-- Rendered dynamically -->
</div>
<div id="outboundPagination"></div>
</div>

<!-- ══════════════════════════════════════════════════════════
     MODALS SECTION
     ══════════════════════════════════════════════════════════ -->

<div class="overlay-backdrop" id="confirmDispatchOverlay">
    <div class="modal-shell modal-size-md">
        <div class="modal-header-section" style="background: rgba(240, 244, 250, 0.3); border-bottom:1px solid #e2e8f0; padding:16px 24px;">
            <h3 class="modal-hdr-title" style="font-size: 16px; font-weight:800; color:var(--navy);">Phiếu xuất kho nháp (Draft)</h3>
            <button onclick="window.closeConfirmDispatch()" class="btn-modal-close-icon" style="border:none; background:none; font-size:24px; cursor:pointer;">&times;</button>
        </div>
        <div class="modal-body-section" style="padding: 24px;">
            <input type="hidden" id="confirm-dispatch-order-id"/>
            <div style="display:grid; grid-template-columns: 1fr 1fr; gap:12px; margin-bottom: 16px; font-size:12px; background:#f8fafc; padding:12px; border-radius:6px; border:1px solid #e2e8f0;">
                <div>Mã DO: <strong id="confirm-do-id" style="color:var(--navy);">DO-2026-XXXX</strong></div>
                <div>Đơn hàng: <strong id="confirm-so-id">SO-XXXX</strong></div>
                <div style="grid-column: span 2;">Khách hàng: <strong id="confirm-customer-id">Khách hàng</strong></div>
            </div>
            
            <h4 style="font-size:12px; font-weight:700; margin-bottom:8px; color:var(--navy);">Danh sách SKU bàn giao thực tế:</h4>
            <div style="max-height: 180px; overflow-y: auto; border: 1px solid #e2e8f0; border-radius: 6px; margin-bottom: 16px; background:#fff;">
                <table class="wms-table" style="margin: 0; width: 100%; font-size:12px; border-collapse:collapse;">
                    <thead style="background:#f1f5f9; position:sticky; top:0; z-index:10;">
                        <tr>
                            <th style="padding:8px; text-align:left; border-bottom:1px solid #e2e8f0;">SKU</th>
                            <th style="padding:8px; text-align:left; border-bottom:1px solid #e2e8f0;">Tên sản phẩm</th>
                            <th style="padding:8px; text-align:right; width:70px; border-bottom:1px solid #e2e8f0;">Yêu cầu</th>
                            <th style="padding:8px; text-align:center; width:90px; border-bottom:1px solid #e2e8f0;">Thực tế</th>
                        </tr>
                    </thead>
                    <tbody id="confirm-dispatch-items-list">
                        <!-- Filled dynamically -->
                    </tbody>
                </table>
            </div>

            <div style="margin-bottom: 16px;">
                <label style="font-size:12px; font-weight:600; color:var(--navy); display:block; margin-bottom:4px;">Ghi chú bàn giao cho shipper:</label>
                <textarea id="confirm-dispatch-note" class="form-control" style="width:100%; height:60px; font-size:12px; resize:none; padding:8px; border:1px solid #cbd5e1; border-radius:6px; box-sizing:border-box;" placeholder="Nhập ghi chú xuất kho tại đây..."></textarea>
            </div>

            <div style="background: #fef3c7; border: 1px solid #fcd34d; padding: 10px; border-radius: 6px; display:flex; gap:8px; align-items:flex-start;">
                <svg style="width: 16px; height: 16px; color:#b45309; flex-shrink:0; margin-top:2px;" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                    <path stroke-linecap="round" stroke-linejoin="round" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
                </svg>
                <p style="font-size:11px; color:#b45309; line-height: 1.4; margin: 0; font-weight:500;">
                    Vui lòng đối soát thực tế bàn giao. Khi bấm <strong>"Duyệt và Xuất kho"</strong>, hệ thống sẽ chốt chứng từ và khấu trừ tồn kho vật lý.
                </p>
            </div>
        </div>
        <div class="modal-footer-section" style="padding: 12px 24px; border-top:1px solid #e2e8f0; display:flex; gap:12px;">
            <button onclick="window.closeConfirmDispatch()" class="btn-action-secondary" style="flex:1; justify-content:center; padding:10px;">Hủy</button>
            <button onclick="window.submitConfirmDispatch()" class="btn-action-primary" style="flex:1; justify-content:center; background: var(--orange); padding:10px; color:#fff; border:none; border-radius:6px; font-weight:700; cursor:pointer;">Duyệt và Xuất kho</button>
        </div>
    </div>
</div>

<!-- 2. Draft Outbound Creator Modal (xl side-by-side select) -->
<div class="overlay-backdrop" id="draftCreatorOverlay">
    <div class="modal-shell modal-size-xl">
        <div class="modal-header-section">
            <div>
                <h2 class="modal-hdr-title">Tạo phiếu xuất</h2>
                <p class="modal-hdr-desc">Chọn một yêu cầu xuất từ Sales để map SKU và tự động duyệt chuyển sang Chờ chuẩn bị hàng.</p>
            </div>
            <button onclick="window.closeDraftModal()" class="btn-modal-close-icon">&times;</button>
        </div>

        <div style="display:grid; grid-template-columns: 1.1fr 1.4fr; flex:1; min-height:0; overflow:hidden;">
            <!-- Left pane: Fulfillment Requests list -->
            <div style="border-right:1px solid var(--border); background:rgba(240,244,250,0.13); overflow-y:auto; padding:16px;">
                <div id="fulfillmentRequestsListContainer"></div>
            </div>

            <!-- Right pane: selected request details -->
            <div style="overflow-y:auto; padding:24px; background:#fff;">
                <div id="selectedRequestDetailsBox" style="display:none;">
                    <!-- Request ID header + DRAFT badge -->
                    <div style="display:flex; align-items:flex-start; justify-content:space-between; margin-bottom:20px; gap:12px;">
                        <div>
                            <div style="font-size:13px; color:rgba(16,55,92,0.4); margin-bottom:4px;">Order gốc: <span id="selected-req-order-source" style="font-weight:700; color:var(--navy);"></span></div>
                            <div style="font-size:15px; font-weight:800; color:var(--navy);" id="selected-req-id">FR-2026-XXXX</div>
                            <div style="font-size:12px; color:rgba(16,55,92,0.4); margin-top:2px;">Tạo phiếu xuất và lưu `mappedOrderId` để truy vết.</div>
                        </div>
                        <span style="background:rgba(16,185,129,0.1); color:#10b981; font-size:11px; font-weight:700; padding:5px 12px; border-radius:20px; white-space:nowrap; margin-top:4px;">
                            Duyệt tự động (Auto-approve)
                        </span>
                    </div>

                    <!-- Ghi chú -->
                    <div style="margin-bottom:20px;">
                        <label style="display:block; color:rgba(16,55,92,0.60); font-size:11px; font-weight:700; text-transform:uppercase; letter-spacing:0.06em; margin-bottom:6px;">Ghi chú</label>
                        <textarea id="draft-memo" rows="3"
                                  style="width:100%; padding:10px 12px; border:1px solid var(--border); background:var(--alice); font-size:13px; color:var(--navy); outline:none; resize:none; border-radius:6px;"
                                  placeholder="Ghi chú cho phiếu xuất"></textarea>
                    </div>

                    <!-- SKU table preview -->
                    <div style="border:1px solid var(--border); border-radius:var(--radius-card); overflow:hidden; margin-bottom:20px;">
                        <div style="background:rgba(240,244,250,0.4); padding:10px 16px; font-size:13px; font-weight:600; color:var(--navy); border-bottom:1px solid var(--border);">
                            SKU sẽ được map tự động
                        </div>
                        <table style="width:100%;">
                            <thead>
                                <tr style="background:#fff; border-bottom:1px solid var(--border);">
                                    <th style="text-align:left; padding:8px 16px; font-size:10px; color:rgba(16,55,92,0.4); font-weight:700; text-transform:uppercase; letter-spacing:0.06em;">SKU</th>
                                    <th style="text-align:left; padding:8px 16px; font-size:10px; color:rgba(16,55,92,0.4); font-weight:700; text-transform:uppercase; letter-spacing:0.06em;">Tên sản phẩm</th>
                                    <th style="text-align:right; padding:8px 16px; font-size:10px; color:rgba(16,55,92,0.4); font-weight:700; text-transform:uppercase; letter-spacing:0.06em;">SL</th>
                                </tr>
                            </thead>
                            <tbody id="draftPreviewItemsTableBody"></tbody>
                        </table>
                    </div>

                    <!-- Meta info: Issue Doc ID + Mapped Order ID -->
                    <div style="background:var(--alice); padding:16px; border-radius:6px;">
                        <div style="display:grid; grid-template-columns:1fr 1fr; gap:16px;">
                            <div>
                                <div style="font-size:10px; color:rgba(16,55,92,0.4); font-weight:700; text-transform:uppercase; letter-spacing:0.06em; margin-bottom:4px;">Issue Document ID</div>
                                <div style="font-family:monospace; font-weight:700; font-size:13px; color:var(--navy);">Sẽ sinh khi lưu</div>
                            </div>
                            <div>
                                <div style="font-size:10px; color:rgba(16,55,92,0.4); font-weight:700; text-transform:uppercase; letter-spacing:0.06em; margin-bottom:4px;">Mapped Order ID</div>
                                <div style="font-family:monospace; font-weight:700; font-size:13px; color:var(--navy);" id="selected-mapped-order-id">SO-XXXX</div>
                            </div>
                        </div>
                    </div>
                </div>

                <div id="noSelectedRequestBox" style="height:100%; display:flex; align-items:center; justify-content:center; color:rgba(16,55,92,0.4); font-size:13px;">
                    Không còn request nào để tạo phiếu xuất.
                </div>
            </div>
        </div>

        <div class="modal-footer-section">
            <button onclick="window.closeDraftModal()" class="btn-action-secondary">Hủy</button>
            <button id="btn-submit-draft-do" onclick="window.submitDraftDO()" disabled
                    style="padding:9px 20px; background:var(--navy); color:rgba(16,55,92,0.3); font-size:13px; font-weight:700; border-radius:6px; border:none; cursor:not-allowed;">
                Xác nhận tạo
            </button>
        </div>
    </div>
</div>

<!-- 3. Disposal Note Creator Modal -->
<div class="overlay-backdrop" id="disposalModalOverlay">
    <div class="modal-shell modal-size-md">
        <div class="modal-header-section">
            <div>
                <h2 class="modal-hdr-title">➕ Tạo Phiếu Xuất Hủy</h2>
                <p class="modal-hdr-desc">Lập phiếu tiêu hủy sản phẩm bị hỏng trong quá trình lưu trữ.</p>
            </div>
            <button onclick="window.closeDisposalModal()" class="btn-modal-close-icon">&times;</button>
        </div>
        <div class="modal-body-section" style="display:flex; flex-direction:column; gap:14px; max-height:60vh;">
            <!-- Select SKU -->
            <div class="outbound-form-group">
                <label class="outbound-form-label">Mã sản phẩm (SKU) *</label>
                <select id="disposal-sku" class="outbound-form-input" style="font-weight:600; cursor:pointer;" onchange="window.handleDisposalSkuSelect(this.value)">
                    <option value="" disabled selected>-- Chọn sản phẩm hỏng --</option>
                    <!-- Populated dynamically from SKUs -->
                </select>
            </div>
            
            <div style="display:grid; grid-template-columns: 1.2fr 0.8fr; gap:16px;">
                <!-- Pick Area -->
                <div class="outbound-form-group" style="margin-bottom:0;">
                    <label class="outbound-form-label">Khu vực lấy hàng *</label>
                    <input type="text" value="Zone Hàng Hỏng" disabled class="outbound-form-input" style="font-weight:700; background:rgba(16, 55, 92, 0.05); color:var(--navy);"/>
                </div>
                
                <!-- Qty -->
                <div class="outbound-form-group" style="margin-bottom:0;">
                    <label class="outbound-form-label">Số lượng tiêu hủy * <span id="disposal-qty-limit" style="font-size:11px;color:#dc2626;font-weight:500;"></span></label>
                    <input type="number" id="disposal-qty" min="1" value="1" class="outbound-form-input" style="font-weight:700; text-align:right;"/>
                </div>
            </div>

            <!-- Reason -->
            <div class="outbound-form-group">
                <label class="outbound-form-label">Lý do tiêu hủy *</label>
                <input type="text" id="disposal-reason" placeholder="Ví dụ: Sản phẩm bị ngấm nước, trầy xước nặng..." class="outbound-form-input" style="font-weight: 600;"/>
            </div>

            <!-- Evidence Picture Upload -->
            <div class="outbound-form-group">
                <label class="outbound-form-label">Bằng chứng hình ảnh *</label>
                <div class="scrap-upload-box" id="scrapUploadBox">
                    <!-- Dynamic state based on file -->
                </div>
            </div>
        </div>
        <div class="modal-footer-section">
            <button onclick="window.closeDisposalModal()" class="btn-action-secondary">Hủy</button>
            <button id="btn-submit-disposal" onclick="window.submitDisposalNote()" class="btn-action-red" style="padding:10px 20px;">Trình quản lý duyệt</button>
        </div>
    </div>
</div>

<!-- 4. Print-Ready Goods Issue Note Modal (VAT Template) -->
<div class="overlay-backdrop" id="receiptDetailOverlay">
    <div class="modal-shell modal-size-xl">
        <div class="modal-header-section" style="background: rgba(240, 244, 250, 0.3);">
            <h3 class="modal-hdr-title" style="font-size: 16px;">Chi tiết Phiếu Xuất Kho</h3>
            <div style="display:flex; align-items:center; gap:8px;">
                <button onclick="window.printPDF()" class="btn-action-secondary" style="padding: 6px 12px; font-size:12px;">
                    <svg style="width: 14px; height: 14px; margin-right: 4px; vertical-align:middle;" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                        <polyline points="6 9 6 2 18 2 18 9"/><path d="M6 18H4a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-2"/><rect x="6" y="14" width="12" height="8"/>
                    </svg>In PDF
                </button>
                <button class="btn-action-secondary" style="padding: 6px 12px; font-size:12px;">
                    <svg style="width: 14px; height: 14px; margin-right: 4px; vertical-align:middle;" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                        <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/>
                    </svg>Xuất Excel
                </button>
                <button onclick="window.closeReceiptDetail()" class="btn-modal-close-icon" style="margin-left: 8px;">&times;</button>
            </div>
        </div>
        <div class="modal-body-section" style="padding: 40px; background:#fff;">
            <div class="print-receipt-container" id="receiptPrintArea">
                <!-- Header details -->
                <div style="display:grid; grid-template-columns: 1fr 1fr; gap:16px; margin-bottom: 24px;">
                    <div>
                        <div style="font-size: 11px; font-weight:500;">
                            Đơn vị: <strong style="font-size: 12px;">Hệ thống Bán hàng Đa kênh ABC</strong>
                        </div>
                        <div style="font-size: 11px; color:rgba(16,55,92,0.6); margin-top:2px;">
                            Bộ phận: Kho Trung Tâm
                        </div>
                    </div>
                    <div style="text-align: right; font-size: 10px; color:rgba(16,55,92,0.6);">
                        <div>Mẫu số 02-VT</div>
                        <div style="margin-top:2px;">(Ban hành theo Thông tư số 200/2014/TT-BTC)</div>
                    </div>
                </div>

                <!-- Central Title -->
                <div style="text-align: center; margin-bottom: 24px;">
                    <h1 style="font-size: 20px; font-weight:900; margin:0; letter-spacing: -0.01em;">PHIẾU XUẤT KHO</h1>
                    <div style="font-size:13px; color:rgba(16,55,92,0.6); font-weight:500; margin-top:2px;">GOODS ISSUE NOTE</div>
                    <div style="font-size: 12px; color:rgba(16,55,92,0.6); margin-top:8px;">
                        Ngày <span id="print-date-day" style="border-bottom: 1px dashed rgba(16,55,92,0.3); padding:0 8px;">28</span> 
                        tháng <span id="print-date-month" style="border-bottom: 1px dashed rgba(16,55,92,0.3); padding:0 8px;">05</span> 
                        năm <span id="print-date-year" style="border-bottom: 1px dashed rgba(16,55,92,0.3); padding:0 8px;">2026</span>
                    </div>
                </div>

                <!-- Ledger Accounts info -->
                <div style="display:grid; grid-template-columns: repeat(3, 1fr); gap:20px; margin-bottom: 24px;">
                    <div>
                        <div class="outbound-form-label" style="font-size: 10px; color:rgba(16,55,92,0.5);">Số Phiếu (No.)</div>
                        <div style="display:flex; align-items:center; gap:12px; margin-top:4px;">
                            <span style="font-size: 16px; font-weight:800;" id="print-receipt-id">DO-2026-XXXX</span>
                            <div style="border: 1px solid var(--border); padding: 4px 8px; background:rgba(16,55,92,0.02); border-radius: 2px;">
                                <div style="height: 24px; width:80px; font-family:monospace; font-size:9px; display:flex; align-items:center; justify-content:center; color:rgba(16,55,92,0.4); border: 1px solid rgba(16,55,92,0.1);">
                                    ||||||||||||||
                                </div>
                            </div>
                        </div>
                    </div>
                    <div>
                        <div class="outbound-form-label" style="font-size: 10px; color:rgba(16,55,92,0.5);">Nợ (Debit)</div>
                        <div style="border-bottom: 2px dashed rgba(16,55,92,0.2); padding-bottom:4px; margin-top:6px;">
                            <strong style="font-size:12px;">TK 632</strong>
                        </div>
                    </div>
                    <div>
                        <div class="outbound-form-label" style="font-size: 10px; color:rgba(16,55,92,0.5);">Có (Credit)</div>
                        <div style="border-bottom: 2px dashed rgba(16,55,92,0.2); padding-bottom:4px; margin-top:6px;">
                            <strong style="font-size:12px;">TK 156</strong>
                        </div>
                    </div>
                </div>

                <!-- Background profile info -->
                <div style="display:grid; grid-template-columns: 1fr 1fr; gap:16px 24px; margin-bottom: 24px;">
                    <div>
                        <div class="outbound-form-label" style="font-size:10px; color:rgba(16,55,92,0.5);">Họ Tên Người Nhận Hàng</div>
                        <div style="border-bottom:1px solid rgba(16,55,92,0.2); padding-bottom:2px; margin-top:4px;">
                            <span style="font-size:13px; font-weight:600;" id="print-customer">Khách hàng</span>
                        </div>
                    </div>
                    <div>
                        <div class="outbound-form-label" style="font-size:10px; color:rgba(16,55,92,0.5);">Địa Chỉ / Đơn Vị Người Nhận</div>
                        <div style="border-bottom:1px solid rgba(16,55,92,0.2); padding-bottom:2px; margin-top:4px;">
                            <span style="font-size:12px; color:var(--navy);" class="truncate block" id="print-address">Địa chỉ giao hàng</span>
                        </div>
                    </div>
                    <div>
                        <div class="outbound-form-label" style="font-size:10px; color:rgba(16,55,92,0.5);">Lý Do Xuất Kho</div>
                        <div style="border-bottom:1px solid rgba(16,55,92,0.2); padding-bottom:2px; margin-top:4px;">
                            <span style="font-size:13px; font-weight:500;" id="print-reason">Xuất bán hàng theo đơn hàng SO-XXXX</span>
                        </div>
                    </div>
                    <div>
                        <div class="outbound-form-label" style="font-size:10px; color:rgba(16,55,92,0.5);">Xuất Tại Kho</div>
                        <div style="border-bottom:1px solid rgba(16,55,92,0.2); padding-bottom:2px; margin-top:4px;">
                            <span style="font-size:13px;" id="print-warehouse">Kho HCM - Quận 1 → Khu Hàng Thường</span>
                        </div>
                    </div>
                </div>

                <!-- Print line items table -->
                <table class="print-table">
                    <thead>
                        <tr>
                            <th style="width:40px; text-align:center;">STT</th>
                            <th style="text-align:left;">Tên/Quy Cách Vật Tư</th>
                            <th style="width:120px; text-align:left;">Mã Số (SKU)</th>
                            <th style="width:50px; text-align:center;">ĐVT</th>
                            <th style="width:130px; text-align:center;">Số Lô / HSD</th>
                            <th style="width:80px; text-align:center;">SL Yêu Cầu</th>
                            <th style="width:80px; text-align:center; background:rgba(16,55,92,0.03);">SL Thực Xuất</th>
                            <th style="width:90px; text-align:right;">Đơn Giá</th>
                            <th style="width:110px; text-align:right;">Thành Tiền</th>
                        </tr>
                    </thead>
                    <tbody id="printTableItemsBody">
                        <!-- Dynamic items -->
                    </tbody>
                </table>

                <!-- Verbal Currency Statement -->
                <div style="margin-top: 16px; border:1px solid rgba(16,55,92,0.2); padding:10px 16px; border-radius: 4px;">
                    <span style="font-size:11px; font-weight:700; text-transform:uppercase; color:rgba(16,55,92,0.5);">Tổng Số Tiền (Viết bằng chữ): </span>
                    <strong style="font-size:13px; margin-left:4px;" id="print-amount-words">Không đồng chẵn</strong>
                </div>

                <!-- Signatures -->
                <div class="print-sign-grid">
                    <div>
                        <div class="print-sign-title">Người Lập Phiếu</div>
                        <div style="border-bottom: 1px dashed rgba(16,55,92,0.2); margin-bottom: 8px; height: 35px;"></div>
                        <div class="print-sign-desc">(Ký, họ tên)</div>
                    </div>
                    <div>
                        <div class="print-sign-title">Người Nhận Hàng</div>
                        <div style="border-bottom: 1px dashed rgba(16,55,92,0.2); margin-bottom: 8px; height: 35px;"></div>
                        <div class="print-sign-desc">(Ký, họ tên)</div>
                    </div>
                    <div>
                        <div class="print-sign-title">Thủ Kho</div>
                        <div style="border-bottom: 1px dashed rgba(16,55,92,0.2); margin-bottom: 8px; height: 35px;"></div>
                        <div class="print-sign-desc">(Ký, họ tên)</div>
                    </div>
                    <div>
                        <div class="print-sign-title">Kế Toán Trưởng</div>
                        <div style="border-bottom: 1px dashed rgba(16,55,92,0.2); margin-bottom: 8px; height: 35px;"></div>
                        <div class="print-sign-desc">(Ký, họ tên)</div>
                    </div>
                    <div>
                        <div class="print-sign-title">Giám Đốc</div>
                        <div style="border-bottom: 1px dashed rgba(16,55,92,0.2); margin-bottom: 8px; height: 35px;"></div>
                        <div class="print-sign-desc">(Ký, họ tên)</div>
                    </div>
                </div>
            </div>
        </div>
    </div>
</div>

<script id="db-inbound-list-data" type="application/json">[
<c:forEach items="${inboundList}" var="io" varStatus="s">{"inboundId":${io.inboundId},"inboundCode":"<c:out value='${io.inboundCode}'/>","supplierName":"<c:out value='${io.supplierName}'/>","warehouseName":"<c:out value='${io.warehouseName}'/>","status":"<c:out value='${io.status}'/>","createdAt":"<c:out value='${io.createdAt}'/>","items":${io.itemsJson}}${!s.last ? ',' : ''}
</c:forEach>]
</script>

<!-- ══════════════════════════════════════════════════════════
     DYNAMIC JAVASCRIPT STATE CONTROLLER
     ══════════════════════════════════════════════════════════ -->
<script>
(function() {
    // Shared WMS logged-in user profile
    window.WMS_USER = {
        fullName: '${loggedInUser != null ? loggedInUser.fullName : "Nhân viên kho"}',
        role: '${loggedInUser != null ? loggedInUser.role : ""}'
    };

    // Constant keys for state syncing
    var DO_STORAGE_KEY = "wh_outbound_dos";
    var FULFILLMENT_STORAGE_KEY = "wh_fulfillment_requests";
    var SKUS_STORAGE_KEY = "wms_skus";
    var LEDGER_STORAGE_KEY = "wh_inventory_ledger";
    var PRICING_WAREHOUSE_KEY = "wh_pricing_warehouse";
    var PRICING_SALES_KEY = "wh_pricing_sales";

    // Parse GRN list from server
    function safeJsonParse(rawValue, fallbackValue) {
        if (!rawValue) return fallbackValue;
        try { return JSON.parse(rawValue); } catch (error) { return fallbackValue; }
    }

    var serverInboundList = safeJsonParse(document.getElementById('db-inbound-list-data') && document.getElementById('db-inbound-list-data').textContent, []);
    if (serverInboundList && serverInboundList.length > 0) {
        grns = serverInboundList.map(function(o) {
            var mappedStatus = o.status;
            if (o.status === 'PENDING') mappedStatus = 'pending';
            else if (o.status === 'IN_PROGRESS') mappedStatus = 'in_progress';
            else if (o.status === 'RECEIVED') mappedStatus = 'completed';
            else if (o.status === 'CANCELLED') mappedStatus = 'cancelled';
            else mappedStatus = 'draft';

            return {
                id: o.inboundId,
                inboundCode: o.inboundCode,
                supplier: o.supplierName,
                warehouseName: o.warehouseName,
                status: mappedStatus,
                createdAt: o.createdAt,
                items: (o.items || []).map(function(item) {
                    return {
                        productId: item.productId || 0,
                        skuCode: item.skuCode || item.sku || '',
                        skuName: item.skuName || item.productName || '',
                        orderedQty: parseFloat(item.orderedQty || item.expectedQty || 0),
                        receivedQty: parseFloat(item.receivedQty || 0),
                        acceptedQty: parseFloat(item.acceptedQty || 0),
                        rejectedQty: parseFloat(item.rejectedQty || 0),
                        price: parseFloat(item.price || 0)
                    };
                })
            };
        });
    }

    function escapeHtml(string) {
        if (!string) return '';
        var map = {
            '&': '&amp;',
            '<': '&lt;',
            '>': '&gt;',
            '"': '&quot;',
            "'": '&#039;'
        };
        return string.toString().replace(/[&<>"']/g, function(m) { return map[m]; });
    }

    // Main Tab Switching
    window.switchMainTab = function(tabId) {
        renderOrders();
    };

    // Products list loaded dynamically from local storage / database
    var AVAILABLE_SKUS = [];
    var savedSkus = localStorage.getItem(SKUS_STORAGE_KEY);
    if (savedSkus) {
        try {
            var parsedSkus = JSON.parse(savedSkus);
            AVAILABLE_SKUS = parsedSkus.map(function(s) {
                return { sku: s.skuCode || s.sku, name: s.productName || s.name };
            });
        } catch(e) {
            console.error(e);
        }
    }

    function safeGetJsonFromScript(elementId, fallbackValue) {
        var el = document.getElementById(elementId);
        if (!el || !el.textContent || !el.textContent.trim()) return fallbackValue;
        var content = el.textContent.trim();
        if (content.indexOf('\x24{') === 0 || content.indexOf('$' + '{') === 0) return fallbackValue;
        try {
            return JSON.parse(content);
        } catch (e) {
            console.warn('warehouse-outbound: Failed to parse JSON from #' + elementId, e);
            return fallbackValue;
        }
    }

    // Real-time inventory stock from DB (authoritative source for stock validation)
    var DB_INVENTORY_STOCK = safeGetJsonFromScript('db-inventory-stock-data', []);

    // Seed data for first bootstrap
    var pickOrders = [];

    // Status Tab mapping configuration
    var STATUS_CONFIG = {
        draft: { label: "Bản nháp", iconName: "ClipboardList", bg: "rgba(16, 55, 92, 0.08)", text: "rgba(16, 55, 92, 0.6)", dot: "rgba(16, 55, 92, 0.3)", color: "#64748b" },
        pending_bm: { label: "Chờ duyệt", iconName: "Lock", bg: "#fef3c7", text: "#b45309", dot: "#f59e0b", color: "#d97706" },
        pending_pick: { label: "Chờ đóng gói", iconName: "Clock", bg: "#eff6ff", text: "#1d4ed8", dot: "#3b82f6", color: "#2563eb" },
        packed: { label: "Đang đóng gói", iconName: "Package", bg: "#f3e8ff", text: "#7e22ce", dot: "#a855f7", color: "#9333ea" },
        handed_over: { label: "Đã đóng gói", iconName: "ArrowUpFromLine", bg: "rgba(245, 200, 66, 0.15)", text: "#d97706", dot: "#f5c842", color: "#eb8317" },
        dispatched: { label: "Đã xuất kho", iconName: "Truck", bg: "#ecfdf5", text: "#047857", dot: "#10b981", color: "#059669" },
        cancelled: { label: "Đã hủy", iconName: "XCircle", bg: "#fee2e2", text: "#991b1b", dot: "#ef4444", color: "#dc2626" }
    };

    var STATUS_TABS = [
        { id: "all", label: "Tất cả" },
        { id: "pending_pick", label: "Chờ đóng gói" },
        { id: "packed", label: "Đang đóng gói" },
        { id: "handed_over", label: "Đã đóng gói" },
        { id: "dispatched", label: "Đã xuất kho" },
        { id: "cancelled", label: "Đã hủy" }
    ];

    var activePickSubTab = "picking";

    // Local controller states
    var pickOrders = [];
    var fulfillmentRequests = [];
    var activeTab = "all";
    var searchStr = "";
    var expandedOrderId = null;

    // Draft creator state
    var selectedRequestId = null;
    var newRequestIds = new Set(); // tracks IDs that arrived since last page load

    // Locked warehouse id (the staff's own warehouse) — set server-side via EL outside <script>.
    window.WAREHOUSE_ID = <c:choose><c:when test="${not empty warehouses}">${warehouses[0].warehouseId}</c:when><c:otherwise>1</c:otherwise></c:choose>;

    // Disposal Creator state
    var selectedDisposalSku = "";
    var disposalEvidence = "";
    var disposalEvidenceName = "";

    // Product list from the database (for the disposal SKU dropdown)
    var DB_PRODUCTS = safeGetJsonFromScript('db-products-data', []).map(function(p) {
        return { sku: p.sku || p.skuCode || '', name: p.name || p.productName || '' };
    });

    var SCRAP_PRODUCTS = safeGetJsonFromScript('db-scrap-products-data', []).map(function(p) {
        return {
            sku: p.skuCode || '',
            name: p.productName || '',
            scrapQty: p.scrapQty || 0
        };
    });

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

    function mapDbOrderToFrontend(dbOrder) {
        var status = 'draft';
        var statusLower = (dbOrder.status || '').toLowerCase();
        if (statusLower === 'pending' || statusLower === 'pending_pack') status = 'pending_pick';
        else if (statusLower === 'picking' || statusLower === 'packed') status = 'packed';
        else if (statusLower === 'handed_over') status = 'handed_over';
        else if (statusLower === 'shipped') status = 'dispatched';
        else if (statusLower === 'cancelled') status = 'cancelled';

        var totalQty = 0;
        var totalPicked = 0;
        var grouped = {};
        (dbOrder.items || []).forEach(function(item) {
            var itemQty = item.qty || 0;
            var itemPicked = item.pickedQty || 0;
            totalQty += itemQty;
            totalPicked += itemPicked;
            
            var key = item.skuCode || ('PROD-' + item.productId);
            if (grouped[key]) {
                grouped[key].qty += itemQty;
                grouped[key].pickedQty += itemPicked;
            } else {
                grouped[key] = {
                    productId: item.productId,
                    skuCode: key,
                    skuName: item.skuName || 'Sản phẩm #' + item.productId,
                    qty: itemQty,
                    pickedQty: itemPicked,
                    location: item.shelfLocation || "—"
                };
            }
        });
        
        var itemsMapped = Object.values(grouped).map(function(item) {
            item.picked = item.pickedQty >= item.qty;
            return item;
        });

        var allPicked = totalQty > 0 && totalPicked >= totalQty;
        var trackingNo = dbOrder.trackingNo || '';
        var hasTracking = trackingNo && trackingNo.trim().length > 0;
        var channelName = dbOrder.channelName || 'Sales';
        var isLazada = (channelName || '').toLowerCase() === 'lazada';
        var isWebsite = (channelName || '').toLowerCase() === 'website';

        // Channel palette (chip + halo) so card vẫn có màu sắc đa kênh
        var channelColors = {
            'lazada': '#f57224', 'shopee': '#ee4d2d', 'tiktok': '#161823',
            'sales': '#3b82f6', 'default': '#64748b'
        };
        var channelLower = (channelName || '').toLowerCase();
        var channelColor = channelColors[channelLower] || channelColors.default;

        return {
            // OutboundOrder.outboundId is serialized as "id" (@JsonProperty("id")), not
            // "outboundId" — reading dbOrder.outboundId here was always undefined, so every
            // status-transition button silently fell back to a localStorage-only stub instead
            // of calling the real backend API. No warehouse click ever persisted.
            id: dbOrder.code || ('DB-' + dbOrder.id),
            dbOutboundId: dbOrder.id,
            issueDocumentId: dbOrder.code || ('DB-' + dbOrder.id),
            mappedOrderId: dbOrder.orderId,
            orderCode: dbOrder.orderCode,
            soRef: dbOrder.orderCode || ('SO-' + dbOrder.orderId),
            channel: channelName,
            channelColor: channelColor,
            isLazada: isLazada,
            isWebsite: isWebsite,
            customer: dbOrder.recipientName || ("Khách hàng từ đơn #" + dbOrder.orderId),
            address: dbOrder.shippingAddress || dbOrder.notes || "Khu vực hàng thường",
            status: status,
            courier: dbOrder.courierName || "Giao hàng nhanh",
            createdAt: dbOrder.createdAt ? dbOrder.createdAt.replace('T', ' ').substring(0, 16) : '',
            assignedTo: dbOrder.pickerName || '',
            note: dbOrder.notes,
            trackingNo: trackingNo,
            hasTracking: hasTracking,
            allPicked: allPicked,
            labelPrinted: dbOrder.labelPrinted || false,
            restocked: dbOrder.restocked || false,
            rtsPushed: dbOrder.rtsPushed || false,
            reviewNote: dbOrder.reviewNote || '',
            items: itemsMapped
        };
    }

    // Bootstrap data initialization
    function initLocalStorageData() {
        // Bind server-side outbound orders if available from servlet
        var SERVER_OUTBOUND_ORDERS = safeGetJsonFromScript('db-outbound-orders-data', []);
        var mappedServerOrders = SERVER_OUTBOUND_ORDERS.map(mapDbOrderToFrontend);

        // Picked state and restocked state both come from the DB now (outbound_items.picked_qty,
        // outbound_orders.restocked_at) — server is authoritative for any order with a dbOutboundId.
        // localStorage is only consulted for local-only mock orders that have no DB record at all.
        pickOrders = mappedServerOrders;

        // Load fulfillment requests from servlet (already fetched server-side)
        var SERVER_FULFILLMENT = safeGetJsonFromScript('db-fulfillment-requests-data', []);

        fulfillmentRequests = SERVER_FULFILLMENT.map(function(fr) {
            return {
                requestId: fr.requestId,
                orderId: fr.orderId,
                warehouseId: fr.warehouseId,
                status: fr.status,
                autoCreated: fr.autoCreated,
                createdAt: fr.createdAt ? new Date(fr.createdAt).toLocaleString('vi-VN') : null,
                items: (fr.items || []).map(function(item) {
                    return {
                        skuCode: item.skuCode,
                        skuName: item.skuName,
                        qty: item.qty
                    };
                })
            };
        });
    }

    initLocalStorageData();
    renderStatistics();
    renderOrders();

    // Poll server every 30s to catch seller-cancelled orders and sync local state
    function pollServerForCancelledOrders() {
        var url = '${pageContext.request.contextPath}/api/lazada/orders?nocache=' + Date.now();
        fetch(url)
            .then(function(r) { return r.ok ? r.json() : []; })
            .then(function(serverOrders) {
                if (!Array.isArray(serverOrders)) return;
                var cancelledMap = {};
                serverOrders.forEach(function(so) {
                    var isCancelled = (so.wms_status && so.wms_status.toUpperCase() === 'CANCELLED')
                        || (so.channel_status && (so.channel_status.toUpperCase() === 'CANCELLED'
                        || so.channel_status.toUpperCase() === 'CANCELED'));
                    if (isCancelled) {
                        cancelledMap[so.lazada_order_id_str || so.lazadaOrderIdStr || ''] = true;
                    }
                });
                var changed = false;
                pickOrders.forEach(function(o) {
                    if (o.soRef && cancelledMap[o.soRef] && o.status !== 'cancelled') {
                        o.status = 'cancelled';
                        changed = true;
                        console.log('[pollServer] Order', o.id, 'cancelled on Lazada → local status set to cancelled');
                    }
                });
                if (changed) {
                    localStorage.setItem(DO_STORAGE_KEY, JSON.stringify(pickOrders));
                    renderStatistics();
                    renderOrders();
                }
            })
            .catch(function(e) { console.warn('[pollServer] Failed to fetch orders:', e); });
    }

    setInterval(pollServerForCancelledOrders, 30000);
    pollServerForCancelledOrders(); // run immediately on load

    if (fulfillmentRequests.length > 0) {
        var faCount = document.getElementById('fulfillment-alert-count');
        if (faCount) {
            faCount.textContent = fulfillmentRequests.length + " lệnh xuất mới";
        }
    }


    // Render counts and update UI statistics
    function renderStatistics() {
        var counts = {
            draft: 0, pending_bm: 0, pending_pick: 0, packed: 0, handed_over: 0, dispatched: 0, cancelled: 0
        };
        var now = new Date();
        var todayStr = now.getFullYear() + '-'
            + String(now.getMonth() + 1).padStart(2, '0') + '-'
            + String(now.getDate()).padStart(2, '0');
        var dispatchedToday = 0;
        pickOrders.forEach(function(o) {
            if (counts[o.status] !== undefined) {
                counts[o.status]++;
            }
            if (o.status === 'dispatched' && o.createdAt && o.createdAt.indexOf(todayStr) === 0) {
                dispatchedToday++;
            }
        });

        var el;
        if ((el = document.getElementById('stat-draft'))) el.textContent = counts.draft;
        if ((el = document.getElementById('stat-pending-bm'))) el.textContent = counts.pending_bm;
        if ((el = document.getElementById('stat-pending-pick'))) el.textContent = counts.pending_pick;
        if ((el = document.getElementById('stat-picking-pack'))) el.textContent = counts.packed;
        if ((el = document.getElementById('stat-packed'))) el.textContent = counts.handed_over;
        if ((el = document.getElementById('stat-dispatched'))) el.textContent = dispatchedToday;

        // Alerts banners visibility (mutually exclusive)
        var newAlert = document.getElementById('fulfillment-alert-banner');
        var pickAlert = document.getElementById('pending-pick-alert-banner');
        if (fulfillmentRequests.length > 0) {
            if (newAlert) newAlert.style.display = 'flex';
            var faCount2 = document.getElementById('fulfillment-alert-count');
            if (faCount2) faCount2.textContent = fulfillmentRequests.length + " lệnh xuất mới";
            if (pickAlert) pickAlert.style.display = 'none';
        } else {
            if (newAlert) newAlert.style.display = 'none';
            if (counts.pending_pick > 0) {
                if (pickAlert) pickAlert.style.display = 'flex';
                var paCount = document.getElementById('pending-pick-alert-count');
                if (paCount) paCount.textContent = counts.pending_pick;
            } else {
                if (pickAlert) pickAlert.style.display = 'none';
            }
        }

        // Render Tabs bar counts
        var tabsContainer = document.getElementById('statusTabsContainer');
        var tabsHtml = STATUS_TABS.map(function(tab) {
            var countVal = tab.id === 'all' ? pickOrders.length : (counts[tab.id] || 0);
            var activeClass = activeTab === tab.id ? 'active' : '';
            return '<button class="status-tab-btn ' + activeClass + '" onclick="window.handleSelectTab(\'' + tab.id + '\')">' +
                tab.label +
                '<span class="status-tab-badge">' + countVal + '</span>' +
            '</button>';
        }).join('');
        tabsContainer.innerHTML = tabsHtml;

        // ── Sub-tabs cho "Đang pick": Đang nhặt hàng vs Chờ cấp mã ──
        var pickSubContainer = document.getElementById('pickSubTabsContainer');
        if (pickSubContainer) {
            pickSubContainer.style.display = 'none';
        }
    }

    // Main orders list render
    function renderOrders() {
        OmniPagination.reset('outboundOrders');
        renderOrdersPage();
    }

    function renderOrdersPage() {
        var container = document.getElementById('outboundOrdersContainer');

        var filtered = pickOrders.filter(function(o) {
            var matchTab = activeTab === 'all' || o.status === activeTab;
            var matchSearch = o.id.toLowerCase().indexOf(searchStr.toLowerCase()) > -1 ||
                              o.soRef.toLowerCase().indexOf(searchStr.toLowerCase()) > -1;
            return matchTab && matchSearch;
        });



        if (filtered.length === 0) {
            container.innerHTML = '<div style="background:#fff; border: 1px solid var(--border); padding: 48px; text-align:center; color:rgba(16,55,92,0.4); font-size:13px; border-radius:var(--radius-card);">' +
                'Không tìm thấy phiếu xuất kho phù hợp.' +
            '</div>';
            document.getElementById('outboundPagination').innerHTML = '';
            return;
        }

        var paginationResult = OmniPagination.paginate('outboundOrders', filtered);

        var html = paginationResult.items.map(function(order) {
            var sc = STATUS_CONFIG[order.status] || STATUS_CONFIG.draft;
            var isExpanded = expandedOrderId === order.id;
            var totalQty = 0;
            var pickedQty = 0;
            order.items.forEach(function(i) {
                totalQty += i.qty;
                if (i.picked) pickedQty += i.qty;
            });

            // Action button state machine
            var actionBtnHtml = '';
            var packError = order.isLazada && order.status === 'pending_pick' && order.reviewNote && order.reviewNote.indexOf('Lỗi Pack: ') === 0;
            
            if (order.status === 'pending_pick') {
                if (order.isLazada) {
                    var oc = (order.orderCode || order.soRef || order.id).replace(/'/g, "\\'");
                    if (!order.hasTracking) {
                        var btnText = packError ? "Thử lại API" : "Chuẩn bị hàng";
                        var btnColor = packError ? "background:#ef4444; color:#fff;" : "background:#6366f1; color:#fff;";
                        actionBtnHtml = '<button class="btn-workflow-step" style="' + btnColor + '" onclick="window.triggerLazadaPack(\'' + oc + '\', event)">' + btnText + '</button>';
                    } else {
                        actionBtnHtml = '<button class="btn-workflow-step blue" onclick="window.handleStartPacking(\'' + order.id + '\', event)">Bắt đầu đóng gói</button>';
                    }
                } else {
                    actionBtnHtml = '<button class="btn-workflow-step blue" onclick="window.handleStartPacking(\'' + order.id + '\', event)">Bắt đầu đóng gói</button>';
                }
            } else if (order.status === 'packed') {
                var oc = (order.orderCode || order.soRef || order.id).replace(/'/g, "\\'");
                if (order.isLazada) {
                    var tnLabel = order.hasTracking
                        ? '<span class="tracking-no-pill" style="margin-right: 8px;"><svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2"><path d="M9 12l2 2 4-4"/><circle cx="12" cy="12" r="10"/></svg>' + order.trackingNo + '</span>'
                        : '';
                    
                    var printBtnText = order.labelPrinted ? 'In lại tem' : 'In tem Lazada';
                    var printBtn = '<button class="btn-workflow-step orange" style="margin-right: 8px;" onclick="window.printLazadaLabel(\'' + oc + '\', event)" title="In tem vận đơn Lazada">' +
                            '<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2" style="width:14px;height:14px;margin-right:4px;vertical-align:-2px;">' +
                                '<polyline points="6 9 6 2 18 2 18 9"/><path d="M6 18H4a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-2"/>' +
                                '<rect x="6" y="14" width="12" height="8"/>' +
                            '</svg>' + printBtnText + '</button>';
                    
                    var disabledAttr = order.labelPrinted ? '' : 'disabled';
                    var opacityStyle = order.labelPrinted ? '' : 'opacity: 0.5; cursor: not-allowed;';
                    actionBtnHtml = tnLabel + printBtn +
                        '<button class="btn-workflow-step purple" ' + disabledAttr + ' style="' + opacityStyle + '" onclick="window.triggerLazadaRts(\'' + oc + '\', event)">Xác nhận đóng gói</button>';
                } else if (order.isWebsite && order.hasTracking) {
                    // Mock shipping (com.wms.mockshipping) — carrier + waybill already assigned
                    // (checkout choice + auto-generated at "Bắt đầu đóng gói"). Same print-then-
                    // unlock pattern as Lazada, but the label is our own generated mock, not a
                    // real courier's PDF.
                    var tnLabelWeb = '<span class="tracking-no-pill" style="margin-right: 8px;"><svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2"><path d="M9 12l2 2 4-4"/><circle cx="12" cy="12" r="10"/></svg>' + order.trackingNo + '</span>';
                    var printBtnTextWeb = order.labelPrinted ? 'In lại tem' : 'In tem';
                    var printBtnWeb = '<button class="btn-workflow-step orange" style="margin-right: 8px;" onclick="window.printMockLabel(\'' + oc + '\', event)" title="In tem vận đơn (mock)">' +
                            '<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2" style="width:14px;height:14px;margin-right:4px;vertical-align:-2px;">' +
                                '<polyline points="6 9 6 2 18 2 18 9"/><path d="M6 18H4a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-2"/>' +
                                '<rect x="6" y="14" width="12" height="8"/>' +
                            '</svg>' + printBtnTextWeb + '</button>';
                    var disabledAttrWeb = order.labelPrinted ? '' : 'disabled';
                    var opacityStyleWeb = order.labelPrinted ? '' : 'opacity: 0.5; cursor: not-allowed;';
                    actionBtnHtml = tnLabelWeb + printBtnWeb +
                        '<button class="btn-workflow-step purple" ' + disabledAttrWeb + ' style="' + opacityStyleWeb + '" onclick="window.handleConfirmPacking(\'' + order.id + '\', event)">Xác nhận đóng gói</button>';
                } else {
                    actionBtnHtml = '<button class="btn-workflow-step purple" onclick="window.handleConfirmPacking(\'' + order.id + '\', event)">Xác nhận đóng gói</button>';
                }
            } else if (order.status === 'handed_over') {
                actionBtnHtml = '<button class="btn-workflow-step blue" style="background:#2563eb; color:#fff;" onclick="window.openConfirmDispatch(\'' + order.id + '\', event)">Xuất kho</button>';
            } else if (order.status === 'dispatched') {
                actionBtnHtml = '<button class="btn-workflow-step blue" style="background:#2563eb; color:#fff;" onclick="window.openReceiptDetail(\'' + order.id + '\', event)">Xem &amp; In phiếu xuất</button>';
            } else if (order.status === 'draft') {
                actionBtnHtml = '<button class="btn-workflow-step amber" onclick="window.handleSubmitForBM(\'' + order.id + '\', event)">Trình duyệt BM</button>';
            } else if (order.status === 'pending_bm') {
                actionBtnHtml = '<span class="label-checker-status">Chờ duyệt</span>';
            } else if (order.status === 'cancelled') {
                if (!order.restocked) {
                    actionBtnHtml = '<button class="btn-workflow-step green" onclick="window.handleRestockOrder(\'' + order.id + '\', event)">Xác nhận hoàn kệ</button>';
                } else {
                    actionBtnHtml = '<span style="color:#10b981; font-weight:700; font-size:13px;">Đã hoàn kệ</span>';
                }
            }

            // Document details viewer icon (removed/merged to main button action)
            var detailBtnHtml = '';

            // Collapsible items rows
            var itemsRows = order.items.map(function(item) {
                var locationHtml = '';
                if (item.location === "—" || !item.location) {
                    locationHtml = '<span style="color:rgba(16, 55, 92, 0.3);">—</span>';
                } else {
                    var parts = item.location.split('→');
                    var leftPart = parts[0] ? parts[0].trim() : '';
                    var rightPart = parts[1] ? parts[1].trim() : item.location;
                    locationHtml = '<div class="sku-loc-badges">' +
                        '<span class="sku-loc-left">' + leftPart + '</span>' +
                        '<span class="sku-loc-divider">→</span>' +
                        '<span class="sku-loc-right">' + rightPart + '</span>' +
                    '</div>';
                }

                // Make the checkbox interactive
                var cursorStyle = order.status === 'picking' ? 'cursor:pointer;' : 'cursor:not-allowed; opacity:0.6;';
                var onClickAttr = 'onclick="window.handleTogglePickItem(\'' + order.id + '\', \'' + item.skuCode + '\', event)"';
                
                var checkedIcon = item.picked ? 
                    '<svg style="width:18px; height:18px; color:#10b981; margin:0 auto; ' + cursorStyle + '" ' + onClickAttr + ' xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2.5"><path stroke-linecap="round" stroke-linejoin="round" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" /></svg>' :
                    '<div style="width:16px; height:16px; border:2px solid rgba(16, 55, 92, 0.2); border-radius:3px; margin:0 auto; ' + cursorStyle + '" ' + onClickAttr + '></div>';

                return '<tr>' +
                    '<td>' +
                        '<div style="display:flex; align-items:center; gap:8px;">' +
                            '<svg style="width:14px; height:14px; color:rgba(16, 55, 92, 0.3);" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2"><path stroke-linecap="round" stroke-linejoin="round" d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4" /></svg>' +
                            '<span style="font-family:monospace; color:rgba(16, 55, 92, 0.6); font-size:10px;">' + item.skuCode + '</span>' +
                        '</div>' +
                    '</td>' +
                    '<td><span style="font-weight:600; color:var(--navy);">' + item.skuName + '</span></td>' +
                    '<td style="text-align:center;">' + locationHtml + '</td>' +
                    '<td style="text-align:right; font-weight:800; font-size:13px; color:var(--navy);">' + item.qty + '</td>' +
                    '<td style="text-align:center;">' + checkedIcon + '</td>' +
                '</tr>';
            }).join('');

            // Render tags/badges on order ID row
            var badgesHtml = '';
            if (order.isDisposal) {
                badgesHtml += '<span class="outbound-extra-badge disposal">🗑️ Phiếu Xuất Hủy</span> ';
            }
            
            badgesHtml += '<span class="pill-badge ' + order.status + '">' +
                '<span class="pill-badge__dot"></span>' +
                sc.label +
            '</span>';
            
            if (order.status === 'draft' && !order.isDisposal) {
                badgesHtml += ' <span class="outbound-extra-badge draft-unapproved">📋 Chưa duyệt</span>';
            }
            if (order.isDisposal && order.status === 'draft' && order.disposalRejectReason) {
                badgesHtml += ' <span class="outbound-extra-badge rejected">⚠️ Bị từ chối</span>';
            }
            if (order.status === 'cancelled') {
                if (order.restocked) {
                    badgesHtml += ' <span class="outbound-extra-badge restocked" style="background:#e6f4ea; color:#137333; font-weight:700; border:1px solid #137333; padding:2px 8px; border-radius:12px; font-size:11px;">Đã hoàn kệ</span>';
                } else {
                    badgesHtml += ' <span class="outbound-extra-badge restocking" style="background:#fef7e0; color:#b06000; font-weight:700; border:1px solid #b06000; padding:2px 8px; border-radius:12px; font-size:11px;">Chưa hoàn kệ</span>';
                }
            }

            var assignedHtml = order.assignedTo ?
                '<span>Phụ trách: <strong style="color:rgba(16, 55, 92, 0.7);">' + order.assignedTo + '</strong></span>' : '';

            var createdHtml = order.createdAt ?
                '<span>Ngày tạo: <strong style="color:rgba(16, 55, 92, 0.7);">' + order.createdAt + '</strong></span>' : '';

            var disposalDetails = '';
            if (order.isDisposal) {
                disposalDetails = '<span>Lý do hỏng: <strong class="outbound-disposal-reason">' + (order.disposalReason || '') + ' (' + (order.disposalZone || '') + ')</strong></span>';
            }

            var rejectionAlert = '';
            if (order.isDisposal && order.status === 'draft' && order.disposalRejectReason) {
                rejectionAlert = '<div class="outbound-reject-reason-box">' +
                    '<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">' +
                        '<path stroke-linecap="round" stroke-linejoin="round" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />' +
                    '</svg>' +
                    '<div><strong>Quản lý từ chối duyệt:</strong> "' + order.disposalRejectReason + '" (Nhân viên vui lòng kiểm tra lại)</div>' +
                '</div>';
            }
            
            var packErrorAlert = '';
            if (packError) {
                var errorMsg = order.reviewNote.substring(10);
                packErrorAlert = '<div class="outbound-reject-reason-box" style="border-color:#fca5a5; background:#fef2f2; color:#b91c1c;">' +
                    '<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2" style="color:#ef4444; width:18px; height:18px;">' +
                        '<path stroke-linecap="round" stroke-linejoin="round" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />' +
                    '</svg>' +
                    '<div><strong>Lỗi chuẩn bị hàng (API Pack):</strong> "' + errorMsg + '" (Vui lòng bấm Thử lại API hoặc kiểm tra cấu hình)</div>' +
                '</div>';
            }

            var cancelSimulationHtml = '';
            var itemStyle = packError ? 'border: 2px solid #ef4444;' : '';
            var hdrClickAction = order.status === 'dispatched' 
                ? 'window.openReceiptDetail(\'' + order.id + '\', event)' 
                : 'window.handleToggleExpand(\'' + order.id + '\')';

            return '<div class="outbound-item ' + (isExpanded ? 'expanded' : '') + '" style="' + itemStyle + '">' +
                '<!-- Header -->' +
                '<div class="outbound-hdr" onclick="' + hdrClickAction + '">' +
                    '<div class="outbound-channel-badge" style="background:' + (order.channelColor || '#64748b') + '">' +
                        order.channel.slice(0, 2).toUpperCase() +
                    '</div>' +
                    '<div class="outbound-hdr__info">' +
                        '<div class="outbound-meta-row">' +
                            '<span class="outbound-id">' + order.id + '</span>' +
                            '<span class="outbound-ref">← ' + (order.mappedOrderId || order.soRef) + '</span>' +
                            badgesHtml +
                        '</div>' +
                        '<div class="outbound-courier-row">' +
                            '<span class="outbound-courier-cell">' +
                                '<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2"><path stroke-linecap="round" stroke-linejoin="round" d="M8 7h12m0 0l-4-4m4 4l-4 4m0 6H4m0 0l4 4m-4-4l4-4" /></svg>' +
                                order.courier +
                            '</span>' +
                            createdHtml +
                            assignedHtml +
                            disposalDetails +
                        '</div>' +
                        rejectionAlert +
                        packErrorAlert +
                    '</div>' +
                    '<div class="outbound-actions-row">' +
                        detailBtnHtml +
                        '<div class="outbound-stat">' +
                            '<div class="outbound-stat__lbl">Pick</div>' +
                            '<div class="outbound-stat__val">' +
                                '<span style="' + (pickedQty === totalQty ? 'color:#10b981;' : '') + '">' + pickedQty + '</span>' +
                                '<span style="font-size:11px; color:rgba(16, 55, 92, 0.3);">/' + totalQty + '</span>' +
                            '</div>' +
                        '</div>' +
                        actionBtnHtml +
                        '<svg class="chevron-arrow" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2.5">' +
                            '<polyline points="6 9 12 15 18 9"/>' +
                        '</svg>' +
                    '</div>' +
                '</div>' +

                '<!-- Expanded body -->' +
                '<div class="outbound-body">' +
                    '<div class="outbound-address-bar">' +
                        '<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">' +
                            '<path stroke-linecap="round" stroke-linejoin="round" d="M17.657 16.657L13.414 20.9a1.998 1.998 0 01-2.827 0l-4.244-4.243a8 8 0 1111.314 0z" /><path stroke-linecap="round" stroke-linejoin="round" d="M15 11a3 3 0 11-6 0 3 3 0 016 0z" />' +
                        '</svg>' +
                        '<span style="flex:1;">' + order.address + '</span>' +
                        cancelSimulationHtml +
                    '</div>' +
                    '<table class="outbound-table">' +
                        '<thead>' +
                            '<tr>' +
                                '<th style="text-align: left;">SKU</th>' +
                                '<th style="text-align: left;">Tên sản phẩm</th>' +
                                '<th style="text-align: center;">Vị trí kho</th>' +
                                '<th style="text-align: right;">SL cần pick</th>' +
                                '<th style="text-align: center;">Đã pick</th>' +
                            '</tr>' +
                        '</thead>' +
                        '<tbody>' +
                            itemsRows +
                        '</tbody>' +
                    '</table>' +
                '</div>' +
            '</div>';
        }).join('');

        container.innerHTML = html;

        OmniPagination.renderControls('outboundPagination', paginationResult.currentPage, paginationResult.totalPages, function (newPage) {
            OmniPagination.setPage('outboundOrders', newPage);
            renderOrdersPage();
        });
    }

    function saveState() {
        localStorage.setItem(DO_STORAGE_KEY, JSON.stringify(pickOrders));
        localStorage.setItem(FULFILLMENT_STORAGE_KEY, JSON.stringify(fulfillmentRequests));
        renderStatistics();
        renderOrders();
    }

    // Toggle list collapse state
    window.handleToggleExpand = function(orderId) {
        if (expandedOrderId === orderId) {
            expandedOrderId = null;
        } else {
            expandedOrderId = orderId;
        }
        renderOrdersPage();
    };

    // Toolbar search triggers
    window.handleSearch = function() {
        searchStr = document.getElementById('outboundSearch').value.trim();
        renderOrders();
    };

    // Filter tabs click
    window.handleSelectTab = function(tabId) {
        activeTab = tabId;
        // Reset về sub-tab "Đang nhặt hàng" mỗi khi đổi sang tab Picking
        if (tabId === 'picking') activePickSubTab = 'picking';
        renderOrders();
        renderStatistics();
    };

    // Sub-tab bên trong tab "Đang pick"
    window.handleSelectPickSubTab = function(subTabId) {
        activePickSubTab = subTabId;
        renderOrders();
        renderStatistics();
    };

    // Toggle picked status of an item during picking
    window.handleTogglePickItem = function(orderId, skuCode, event) {
        if (event) event.stopPropagation();
        var order = pickOrders.find(function(o) { return o.id === orderId; });
        if (!order) return;
        if (order.status !== 'packed') {
            alert('Vui lòng bấm "Bắt đầu đóng gói" trước khi xác nhận đã pick sản phẩm!');
            return;
        }
        var item = order.items.find(function(i) { return i.skuCode === skuCode; });
        if (item) {
            item.picked = !item.picked;
            item.pickedQty = item.picked ? item.qty : 0;
            saveState();
            renderOrders();
            // Persist picked state to DB (outbound_items.picked_qty)
            if (order.dbOutboundId && item.productId) {
                var body = 'action=pickItem'
                    + '&outboundId=' + encodeURIComponent(order.dbOutboundId)
                    + '&productId=' + encodeURIComponent(item.productId)
                    + '&picked=' + (item.picked ? 'true' : 'false');
                fetch(window.location.pathname, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                    body: body
                }).catch(function() { /* keep local state on network error */ });
            }
        }
    };

    // Restock a cancelled order
    window.handleRestockOrder = function(orderId, event) {
        if (event) event.stopPropagation();
        var order = pickOrders.find(function(o) { return o.id === orderId; });
        if (!order) return;

        // Submit to backend to release inventory allocation
        if (order.dbOutboundId) {
            submitPostAction('restock', { outboundId: order.dbOutboundId });
        } else {
            // Local-only: no DB record, just mark as restocked
            order.restocked = true;
            saveState();
            alert('Đã xác nhận hoàn kệ cho đơn ' + orderId);
        }
    };

    // Transition: Pending Pick -> Packing
    window.handleStartPacking = function(orderId, event) {
        if (event) event.stopPropagation();
        var order = pickOrders.find(function(o) { return o.id === orderId; });
        if (!order) return;
        if (order.dbOutboundId) {
            submitPostAction('updateStatus', { outboundId: order.dbOutboundId, status: 'PACKED' });
        } else {
            order.status = 'packed';
            order.assignedTo = window.WMS_USER.fullName || 'Nhân viên kho';
            saveState();
        }
    };
    window.handleStartPicking = window.handleStartPacking;

    // Transition: Packing -> Packed (HANDED_OVER)
    window.handleConfirmPacking = function(orderId, event) {
        if (event) event.stopPropagation();
        var order = pickOrders.find(function(o) { return o.id === orderId; });
        if (!order) return;
        if (order.dbOutboundId) {
            submitPostAction('updateStatus', { outboundId: order.dbOutboundId, status: 'HANDED_OVER' });
        } else {
            order.status = 'handed_over';

            // Mark all items as picked
            order.items.forEach(function(item) {
                item.picked = true;
            });
            saveState();
        }
    };

    /**
     * Sinh mã vận đơn cho đơn đã pick xong — gọi servlet generate_tracking.
     * Sau khi tracking được cấp (DB ghi orders.tracking_no + auto-create outbound),
     * reload trang để trạng thái đồng bộ lại.
     */
    window.handleGenerateTracking = function(orderCode, orderId, event) {
        if (event) event.stopPropagation();
        if (!orderCode) {
            alert('Thiếu mã đơn hàng.');
            return;
        }
        if (!confirm('Cấp mã vận đơn cho đơn ' + orderCode + '?\n\nHệ thống sẽ gọi ĐVVC sinh tracking và chuyển đơn sang PACKED.')) {
            return;
        }

        var order = pickOrders.find(function(o) { return o.id === orderId; });
        var ctx = (typeof window.location !== 'undefined' && document.body)
            ? document.body.getAttribute('data-context-path') || ''
            : '';

        var btn = event && event.currentTarget ? event.currentTarget : null;
        var originalHtml = btn ? btn.innerHTML : '';
        if (btn) {
            btn.disabled = true;
            btn.classList.add('is-loading');
            btn.innerHTML = '<span class="pt-spinner"></span>Đang cấp mã...';
        }

        var formData = new URLSearchParams();
        formData.append('action', 'generateTracking');
        formData.append('orderCode', orderCode);

        fetch(window.location.pathname, {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: formData.toString()
        })
        .then(function(r) { return r.json(); })
        .then(function(data) {
            if (data.success) {
                // Cập nhật local state, đánh dấu đơn đã có tracking
                if (order) {
                    order.hasTracking = true;
                    order.trackingNo = data.trackingNo || order.trackingNo || '';
                    // Server đã tạo outbound mới (PACKED) cho đơn Lazada / non-Lazada,
                    // reload để đồng bộ và đẩy đơn sang tab "Đã đóng gói".
                }
                showTrackingToast(true, '✓ Đã cấp mã vận đơn' + (data.trackingNo ? ' ' + data.trackingNo : '') + '. Đơn chuyển sang PACKED.');
                setTimeout(function() { location.reload(); }, 1200);
            } else {
                showTrackingToast(false, '✗ Lỗi: ' + (data.message || 'Không xác định'));
                if (btn) {
                    btn.disabled = false;
                    btn.classList.remove('is-loading');
                    btn.innerHTML = originalHtml;
                }
            }
        })
        .catch(function(err) {
            showTrackingToast(false, '✗ Lỗi kết nối: ' + err.message);
            if (btn) {
                btn.disabled = false;
                btn.classList.remove('is-loading');
                btn.innerHTML = originalHtml;
            }
        });
    };

    // Mở tab mới in tem vận đơn Lazada (PDF) — gọi servlet /lazada/label
    window.printLazadaLabel = function(orderCode, event) {
        if (event) event.stopPropagation();
        if (!orderCode) return;
        var ctx = document.body.getAttribute('data-context-path') || '';
        window.open(ctx + '/lazada/label?orderCode=' + encodeURIComponent(orderCode), '_blank');
        // Reload after 1.5s to refresh printed state in UI
        setTimeout(function() {
            location.reload();
        }, 1500);
    };

    // com.wms.mockshipping — same open-tab-then-refresh pattern as Lazada's real label,
    // but /mockshipping/label renders our own generated HTML instead of a real courier PDF.
    window.printMockLabel = function(orderCode, event) {
        if (event) event.stopPropagation();
        if (!orderCode) return;
        var ctx = document.body.getAttribute('data-context-path') || '';
        window.open(ctx + '/mockshipping/label?orderCode=' + encodeURIComponent(orderCode), '_blank');
        setTimeout(function() {
            location.reload();
        }, 1500);
    };

    // Gọi API Pack thủ công cho đơn Lazada (Chuẩn bị hàng / Thử lại API)
    window.triggerLazadaPack = function(orderCode, event) {
        if (event) event.stopPropagation();
        if (!orderCode) return;

        var btn = event && event.currentTarget ? event.currentTarget : null;
        var originalHtml = btn ? btn.innerHTML : '';
        if (btn) {
            btn.disabled = true;
            btn.classList.add('is-loading');
            btn.innerHTML = '<span class="pt-spinner"></span>Đang chuẩn bị...';
        }

        var ctx = document.body.getAttribute('data-context-path') || '';
        fetch(ctx + '/lazada/pack?orderCode=' + encodeURIComponent(orderCode), {
            method: 'POST'
        })
        .then(function(r) { return r.json(); })
        .then(function(data) {
            if (data.success) {
                showTrackingToast(true, '✓ Chuẩn bị hàng thành công cho đơn ' + orderCode + '.');
                setTimeout(function() { window.location.reload(); }, 1200);
            } else {
                showTrackingToast(false, '✗ Lỗi: ' + (data.errorMessage || 'Không xác định'));
                setTimeout(function() { window.location.reload(); }, 2000);
            }
        })
        .catch(function(err) {
            showTrackingToast(false, '✗ Lỗi kết nối: ' + err.message);
            if (btn) {
                btn.disabled = false;
                btn.classList.remove('is-loading');
                btn.innerHTML = originalHtml;
            }
        });
    };

    // Kích hoạt Ready-To-Ship cho đơn Lazada — gọi servlet /lazada/rts
    window.triggerLazadaRts = function(orderCode, event) {
        if (event) event.stopPropagation();
        if (!orderCode) return;
        if (!confirm('Kích hoạt RTS cho đơn Lazada ' + orderCode + '?\n\nĐVVC sẽ được phép tới lấy hàng.')) return;

        var btn = event && event.currentTarget ? event.currentTarget : null;
        var originalHtml = btn ? btn.innerHTML : '';
        if (btn) {
            btn.disabled = true;
            btn.classList.add('is-loading');
            btn.innerHTML = '<span class="pt-spinner"></span>Đang kích hoạt RTS...';
        }

        fetch(window.location.pathname.replace(/\/warehouse\/outbound.*/, '') + '/lazada/rts?orderCode=' + encodeURIComponent(orderCode), {
            method: 'POST'
        })
        .then(function(r) { return r.json(); })
        .then(function(data) {
            if (data.success) {
                showTrackingToast(true, '✓ Đã kích hoạt RTS cho ' + orderCode + '.');
                setTimeout(function() { location.reload(); }, 1200);
            } else {
                showTrackingToast(false, '✗ RTS lỗi: ' + (data.errorMessage || 'Không xác định'));
                if (btn) {
                    btn.disabled = false;
                    btn.classList.remove('is-loading');
                    btn.innerHTML = originalHtml;
                }
            }
        })
        .catch(function(err) {
            showTrackingToast(false, '✗ Lỗi kết nối: ' + err.message);
            if (btn) {
                btn.disabled = false;
                btn.classList.remove('is-loading');
                btn.innerHTML = originalHtml;
            }
        });
    };

    function showTrackingToast(success, message) {
        // Tái sử dụng toast container có sẵn nếu có, ngược lại tạo
        var toast = document.getElementById('whToast');
        if (!toast) {
            toast = document.createElement('div');
            toast.id = 'whToast';
            toast.className = 'wh-toast';
            document.body.appendChild(toast);
        }
        toast.className = 'wh-toast ' + (success ? 'wh-toast-success' : 'wh-toast-error');
        toast.innerHTML = '<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2.5"><path stroke-linecap="round" stroke-linejoin="round" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" /></svg><span>' + message + '</span>';
        toast.style.display = 'flex';
        setTimeout(function() { toast.style.display = 'none'; }, 4000);
    }

    // Transition: Draft -> Pending BM
    window.handleSubmitForBM = function(orderId, event) {
        if (event) event.stopPropagation();
        var index = pickOrders.findIndex(function(o) { return o.id === orderId; });
        if (index > -1) {
            pickOrders[index].status = 'pending_bm';
            saveState();
            alert('Đã gửi phiếu xuất ' + orderId + ' lên Business Manager phê duyệt!');
        }
    };

    // ─── STOCKS VALIDATION RULES ───
    // inventoryStock: array of {sku_code, qty_on_hand, ...} from DB (authoritative)
    // Falls back to localStorage wms_skus if DB data is not available.
    function validateStockAvailability(items, inventoryStock) {
        var errors = [];
        var stockMap = {};

        if (inventoryStock && inventoryStock.length > 0) {
            // Authoritative: use real-time DB stock keyed by sku_code
            inventoryStock.forEach(function(row) {
                var sk = row.sku_code || row.skuCode || '';
                stockMap[sk] = row.qty_on_hand || row.qtyOnHand || 0;
            });
        } else {
            // Fallback: localStorage wms_skus
            var currentSKUs = JSON.parse(localStorage.getItem(SKUS_STORAGE_KEY) || '[]');
            currentSKUs.forEach(function(s) {
                stockMap[s.skuCode || s.sku || ''] = s.qtyOnHand || 0;
            });
        }

        items.forEach(function(item) {
            var skuKey = item.skuCode || '';
            var qtyAvailable = stockMap[skuKey] || 0;

            if (qtyAvailable < item.qty) {
                errors.push(item.skuName + ': Tồn kho không đủ (cần ' + item.qty + ', có ' + qtyAvailable + ')');
            }
        });

        return {
            valid: errors.length === 0,
            errors: errors
        };
    }

    // ─── OUTBOUND LEAVE LEDGER WRITERS ───
    function logOutboundInventoryLedger(sku, quantity, referenceId, customerName) {
        var ledgerStr = localStorage.getItem(LEDGER_STORAGE_KEY);
        var ledger = ledgerStr ? JSON.parse(ledgerStr) : [];
        
        var entry = {
            id: 'LEG-' + Date.now() + '-' + Math.random().toString(36).substring(2, 9),
            sku: sku,
            type: 'outbound',
            quantity: -quantity, // Negative for dispatch
            warehouseId: 'WH001',
            zone: '',
            location: '',
            referenceId: referenceId,
            referenceType: 'DO',
            notes: 'Xuất cho khách hàng: ' + customerName,
            createdAt: new Date().toISOString(),
            createdBy: window.WMS_USER.fullName || 'Nhân viên kho'
        };
        
        ledger.push(entry);
        localStorage.setItem(LEDGER_STORAGE_KEY, JSON.stringify(ledger));
    }

    function decrementMasterSkuQty(skuCode, qty) {
        var currentSKUs = JSON.parse(localStorage.getItem(SKUS_STORAGE_KEY) || '[]');
        var index = currentSKUs.findIndex(function(s) { return s.sku === skuCode; });
        if (index > -1) {
            currentSKUs[index].qtyOnHand = Math.max(0, (currentSKUs[index].qtyOnHand || 0) - qty);
            currentSKUs[index].lastUpdated = new Date().toISOString().slice(0, 16).replace("T", " ");
            currentSKUs[index].updatedBy = window.WMS_USER.fullName || 'Nhân viên kho';
            localStorage.setItem(SKUS_STORAGE_KEY, JSON.stringify(currentSKUs));
        }
    }

    function decrementPricingQty(skuCode, qty) {
        [PRICING_WAREHOUSE_KEY, PRICING_SALES_KEY].forEach(function(key) {
            var stored = localStorage.getItem(key);
            if (stored) {
                var records = JSON.parse(stored);
                var updated = records.map(function(r) {
                    if (r.sku === skuCode) {
                        var nextQty = Math.max(0, (r.qtyOnHand || 0) - qty);
                        var importPriceVal = r.importPrice || 0;
                        return Object.assign({}, r, {
                            qtyOnHand: nextQty,
                            costOfGoodsSold: nextQty * importPriceVal
                        });
                    }
                    return r;
                });
                localStorage.setItem(key, JSON.stringify(updated));
            }
        });
    }

    // ─── CONFIRM DISPATCH MODAL ───
    var confirmOverlay = document.getElementById('confirmDispatchOverlay');

    window.openConfirmDispatch = function(orderId, event) {
        if (event) event.stopPropagation();
        var order = pickOrders.find(function(o) { return o.id === orderId; });
        if (!order) return;

        document.getElementById('confirm-dispatch-order-id').value = order.id;
        document.getElementById('confirm-do-id').textContent = order.id;
        document.getElementById('confirm-so-id').textContent = order.mappedOrderId || order.soRef;
        document.getElementById('confirm-customer-id').textContent = order.customer;
        document.getElementById('confirm-dispatch-note').value = order.note || '';

        // Populate items table
        var listContainer = document.getElementById('confirm-dispatch-items-list');
        listContainer.innerHTML = '';
        (order.items || []).forEach(function(item) {
            var tr = document.createElement('tr');
            tr.innerHTML = 
                '<td style="padding:8px; font-family:monospace; font-size:11px; border-bottom:1px solid #e2e8f0;">' + item.skuCode + '</td>' +
                '<td style="padding:8px; font-weight:600; border-bottom:1px solid #e2e8f0;">' + item.skuName + '</td>' +
                '<td style="padding:8px; text-align:right; font-weight:700; border-bottom:1px solid #e2e8f0;">' + item.qty + '</td>' +
                '<td style="padding:8px; text-align:center; border-bottom:1px solid #e2e8f0;">' +
                    '<input type="number" class="confirm-item-qty-input" data-product-id="' + item.productId + '" min="0" max="' + item.qty + '" value="' + item.qty + '" style="width:60px; text-align:center; padding:3px; font-size:12px; border:1px solid #cbd5e1; border-radius:4px; font-weight:700;"/>' +
                '</td>';
            listContainer.appendChild(tr);
        });

        confirmOverlay.classList.add('active');
    };

    window.closeConfirmDispatch = function() {
        confirmOverlay.classList.remove('active');
    };

    window.submitConfirmDispatch = function() {
        var orderId = document.getElementById('confirm-dispatch-order-id').value;
        var order = pickOrders.find(function(o) { return o.id === orderId; });
        if (!order) return;

        if (order.dbOutboundId) {
            var params = {
                outboundId: order.dbOutboundId,
                status: 'SHIPPED',
                note: document.getElementById('confirm-dispatch-note').value
            };
            
            // Gather quantity parameters
            var inputs = document.querySelectorAll('.confirm-item-qty-input');
            inputs.forEach(function(input) {
                var productId = input.getAttribute('data-product-id');
                var val = input.value;
                params['qty_' + productId] = val;
            });
            
            submitPostAction('updateStatus', params);
        } else {
            // Perform stock availability verification
            var validation = validateStockAvailability(order.items, DB_INVENTORY_STOCK);
            if (!validation.valid) {
                alert('❌ Không thể xuất kho do thiếu hụt tồn vật lý:\n\n' + validation.errors.join('\n'));
                confirmOverlay.classList.remove('active');
                return;
            }

            // Apply dynamic stock updates and ledger entries
            order.items.forEach(function(item) {
                // 1. Ledger Entry
                logOutboundInventoryLedger(item.skuCode, item.qty, order.id, order.customer);
                
                // 2. Decrement from physical wms_skus stock
                decrementMasterSkuQty(item.skuCode, item.qty);
                
                // 3. Decrement asset balances from wh_pricing
                decrementPricingQty(item.skuCode, item.qty);
            });

            // Update DO status to dispatched
            order.status = 'dispatched';
            order.assignedTo = order.assignedTo || window.WMS_USER.fullName || 'Nhân viên kho';

            closeConfirmDispatch();
            saveState();
            alert('🎉 Đã xác nhận xuất kho thành công cho phiếu ' + orderId + '!');
        }
    };

    // ─── DRAFT OUTBOUND DO CREATOR MODAL ───
    var draftOverlay = document.getElementById('draftCreatorOverlay');

    window.openDraftModal = function() {
        // Re-render fulfillment requests panel list
        renderFulfillmentRequestsList();
        
        // Auto select first request if exists
        if (fulfillmentRequests.length > 0) {
            window.handleSelectFulfillmentRequest(fulfillmentRequests[0].requestId);
        } else {
            selectedRequestId = null;
            document.getElementById('selectedRequestDetailsBox').style.display = 'none';
            document.getElementById('noSelectedRequestBox').style.display = 'flex';
            var btn = document.getElementById('btn-submit-draft-do');
            btn.disabled = true;
            btn.style.background = 'var(--navy)';
            btn.style.color = 'rgba(16,55,92,0.3)';
            btn.style.cursor = 'not-allowed';
        }

        document.getElementById('draft-memo').value = '';
        draftOverlay.classList.add('active');
    };

    window.closeDraftModal = function() {
        draftOverlay.classList.remove('active');
        // Reset button to disabled state for next open
        var btn = document.getElementById('btn-submit-draft-do');
        btn.disabled = true;
        btn.style.background = 'var(--navy)';
        btn.style.color = 'rgba(16,55,92,0.3)';
        btn.style.cursor = 'not-allowed';
    };

    function renderFulfillmentRequestsList() {
        var container = document.getElementById('fulfillmentRequestsListContainer');

        if (fulfillmentRequests.length === 0) {
            container.innerHTML = '<div style="text-align:center; color:rgba(16,55,92,0.4); font-size:12px; padding:16px;">Không có yêu cầu xuất hàng từ Sales.</div>';
            return;
        }

        var listHtml = fulfillmentRequests.map(function(req) {
            var isActive = selectedRequestId === req.requestId;
            var isNew = newRequestIds.has(req.requestId);
            var isAuto = req.autoCreated;

            var itemStyle = 'width:100%; text-align:left; padding:14px; border:1px solid var(--border); background:#fff; border-radius:var(--radius-card); cursor:pointer; transition:all 0.15s; margin-bottom:10px; display:block;';
            if (isActive) {
                itemStyle = 'width:100%; text-align:left; padding:14px; border:1px solid var(--orange); background:#fff; border-radius:var(--radius-card); cursor:pointer; transition:all 0.15s; margin-bottom:10px; display:block; box-shadow:0 4px 6px -1px rgba(235,131,23,0.1);';
            } else if (isNew) {
                itemStyle = 'width:100%; text-align:left; padding:14px; border:1px solid #a7f3d0; background:#ecfdf5; border-radius:var(--radius-card); cursor:pointer; transition:all 0.15s; margin-bottom:10px; display:block;';
            }

            var badgesHtml = '';
            if (isAuto && isNew) {
                badgesHtml = '<span style="padding:2px 6px; background:#10b981; color:#fff; font-size:9px; font-weight:700; border-radius:20px; animation:pulse 1.5s infinite; margin-right:4px;">TỰ ĐỘNG</span>' +
                             '<span style="padding:2px 6px; background:#ef4444; color:#fff; font-size:9px; font-weight:700; border-radius:20px; animation:pulse 1.5s infinite;">MỚI</span>';
            } else if (isNew) {
                badgesHtml = '<span style="padding:2px 6px; background:#ef4444; color:#fff; font-size:9px; font-weight:700; border-radius:20px; animation:pulse 1.5s infinite;">MỚI</span>';
            } else if (isAuto) {
                badgesHtml = '<span style="padding:2px 6px; background:#10b981; color:#fff; font-size:9px; font-weight:700; border-radius:20px;">TỰ ĐỘNG</span>';
            }

            return '<button style="' + itemStyle + '" onclick="window.handleSelectFulfillmentRequest(\'' + req.requestId + '\')">' +
                '<div style="display:flex; align-items:flex-start; justify-content:space-between; gap:12px;">' +
                    '<div style="flex:1;">' +
                        '<div style="display:flex; align-items:center; gap:6px; flex-wrap:wrap; margin-bottom:4px;">' +
                            '<span style="font-weight:800; color:var(--navy); font-size:13px;">' + req.requestId + '</span>' +
                            badgesHtml +
                        '</div>' +
                        '<div style="font-size:11px; color:rgba(16,55,92,0.4);">Order gốc: ' + req.orderId + '</div>' +
                        (req.createdAt ? '<div style="font-size:10px; color:rgba(16,55,92,0.3); margin-top:2px;">' + req.createdAt + '</div>' : '') +
                    '</div>' +
                    '<span style="background:rgba(16,55,92,0.08); color:rgba(16,55,92,0.6); font-size:10px; font-weight:700; padding:3px 8px; border-radius:20px; white-space:nowrap; margin-top:2px; flex-shrink:0;">' +
                        req.items.length + ' SKU' +
                    '</span>' +
                '</div>' +
            '</button>';
        }).join('');

        container.innerHTML = '<div style="color:var(--navy); font-size:13px; font-weight:600; margin-bottom:4px;">Fulfillment Requests</div>' + listHtml;
    }

    window.handleSelectFulfillmentRequest = function(reqId) {
        selectedRequestId = reqId;
        
        // Re-render select state highlights
        renderFulfillmentRequestsList();

        var req = fulfillmentRequests.find(function(r) { return r.requestId === reqId; });
        if (!req) return;

        document.getElementById('noSelectedRequestBox').style.display = 'none';
        document.getElementById('selectedRequestDetailsBox').style.display = 'block';
        document.getElementById('btn-submit-draft-do').disabled = false;

        document.getElementById('selected-req-id').textContent = req.requestId;
        document.getElementById('selected-req-order-source').textContent = req.orderId;
        document.getElementById('selected-mapped-order-id').textContent = req.orderId;

        // Render preview lines
        var tbody = document.getElementById('draftPreviewItemsTableBody');
        var previewHtml = req.items.map(function(item) {
            return '<tr style="border-top:1px solid var(--border);">' +
                '<td style="padding:10px 16px; font-family:monospace; font-size:11px; color:rgba(16,55,92,0.7);">' + item.skuCode + '</td>' +
                '<td style="padding:10px 16px; font-size:13px; color:var(--navy);">' + item.skuName + '</td>' +
                '<td style="padding:10px 16px; text-align:right; font-size:13px; font-weight:700; color:var(--navy);">' + item.qty + '</td>' +
            '</tr>';
        }).join('');
        tbody.innerHTML = previewHtml;

        // Enable Lưu nháp button with correct styling
        var btn = document.getElementById('btn-submit-draft-do');
        btn.disabled = false;
        btn.style.background = 'var(--orange)';
        btn.style.color = '#fff';
        btn.style.cursor = 'pointer';
    };

    window.submitDraftDO = function() {
        if (!selectedRequestId) return;
        var req = fulfillmentRequests.find(function(r) { return r.requestId === selectedRequestId; });
        if (!req) return;

        var reqIdToRemove = selectedRequestId;
        var btn = document.getElementById('btn-submit-draft-do');
        if (btn) btn.disabled = true;

        fetch('${pageContext.request.contextPath}/warehouse/fulfillment?action=convert&requestId=' + encodeURIComponent(reqIdToRemove), { method: 'POST' })
            .then(function(res) { return res.json(); })
            .then(function(data) {
                if (data.success) {
                    alert('Tạo phiếu xuất thành công! Phiếu đã tự động duyệt (Auto-approve) và chuyển sang Chờ chuẩn bị hàng.');
                    closeDraftModal();
                    window.location.reload();
                } else {
                    alert('Lỗi: ' + data.message);
                    if (btn) btn.disabled = false;
                }
            })
            .catch(function(err) {
                console.error('Failed to convert fulfillment request in DB', err);
                alert('Có lỗi xảy ra khi tạo phiếu xuất hàng.');
                if (btn) btn.disabled = false;
            });
    };

    // ─── DISPOSAL note CREATOR MODAL ───
    var disposalOverlay = document.getElementById('disposalModalOverlay');

    window.openDisposalModal = function() {
        // Populate SKU options from the SCRAP_PRODUCTS list.
        var skuSelect = document.getElementById('disposal-sku');
        var selectOptions = SCRAP_PRODUCTS.map(function(s) {
            return '<option value="' + s.sku + '">' + s.name + ' (' + s.sku + ') - Hỏng: ' + s.scrapQty + ' Cái</option>';
        }).join('');
        skuSelect.innerHTML = SCRAP_PRODUCTS.length
            ? '<option value="" disabled selected>-- Chọn sản phẩm hỏng --</option>' + selectOptions
            : '<option value="" disabled selected>(Không có sản phẩm nào bị hỏng/cần tiêu hủy)</option>';

        selectedDisposalSku = "";
        disposalEvidence = "";
        disposalEvidenceName = "";

        document.getElementById('disposal-qty').value = "1";
        document.getElementById('disposal-qty').removeAttribute('max');
        document.getElementById('disposal-qty-limit').innerText = "";
        document.getElementById('disposal-reason').value = "";
        
        renderEvidenceUploadBox();
        disposalOverlay.classList.add('active');
    };

    window.closeDisposalModal = function() {
        disposalOverlay.classList.remove('active');
    };

    window.handleDisposalSkuSelect = function(sku) {
        selectedDisposalSku = sku;
        var p = SCRAP_PRODUCTS.find(function(s) { return s.sku === sku; });
        var limitSpan = document.getElementById('disposal-qty-limit');
        var qtyInput = document.getElementById('disposal-qty');
        if (p) {
            var limitVal = p.scrapQty;
            limitSpan.innerText = "(Tối đa: " + limitVal + ")";
            qtyInput.setAttribute('max', limitVal);
            var currentVal = parseInt(qtyInput.value) || 1;
            if (currentVal > limitVal) {
                qtyInput.value = limitVal;
            } else if (currentVal < 1) {
                qtyInput.value = 1;
            }
        } else {
            limitSpan.innerText = "";
            qtyInput.removeAttribute('max');
        }
    };

    // Add event listener to clamp disposal quantity input
    document.addEventListener("DOMContentLoaded", function() {
        var qtyInput = document.getElementById('disposal-qty');
        if (qtyInput) {
            qtyInput.addEventListener('input', function() {
                if (selectedDisposalSku) {
                    var p = SCRAP_PRODUCTS.find(function(s) { return s.sku === selectedDisposalSku; });
                    if (p) {
                        var limitVal = p.scrapQty;
                        var val = parseInt(qtyInput.value) || 0;
                        if (val > limitVal) {
                            qtyInput.value = limitVal;
                        } else if (val < 1 && qtyInput.value !== "") {
                            qtyInput.value = 1;
                        }
                    }
                }
            });
            qtyInput.addEventListener('blur', function() {
                var val = parseInt(qtyInput.value) || 1;
                if (val < 1) qtyInput.value = 1;
            });
        }
    });

    function renderEvidenceUploadBox() {
        var box = document.getElementById('scrapUploadBox');
        if (disposalEvidence) {
            box.innerHTML = '<div style="width:100%; display:flex; flex-direction:column; gap:8px;">' +
                '<div class="scrap-evidence-preview">' +
                    '<img src="' + disposalEvidence + '" alt="Bằng chứng hỏng"/>' +
                    '<button class="btn-remove-evidence" onclick="window.removeDisposalEvidence(event)">&times;</button>' +
                '</div>' +
                '<div style="font-size:11px; color:rgba(16,55,92,0.6); display:flex; align-items:center; justify-content:space-between;">' +
                    '<span>📷 ' + (disposalEvidenceName || 'Ảnh bằng chứng') + '</span>' +
                    '<span style="color:#059669; font-weight:600;">Đã tải lên</span>' +
                '</div>' +
            '</div>';
        } else {
            box.innerHTML = '<div class="scrap-upload-icon">📷</div>' +
                '<div>' +
                    '<div style="font-size:12px; font-weight:800; color:var(--navy);">Chưa có ảnh bằng chứng thực tế</div>' +
                    '<div style="font-size:10px; color:rgba(16,55,92,0.4); margin-top:2px;">Bắt buộc để chống gian lận tiêu hủy</div>' +
                '</div>' +
                '<button onclick="window.triggerUpload(event)" class="btn-action-primary" style="padding:6px 12px; font-size:11px;">' +
                    'Tải ảnh bằng chứng hỏng' +
                '</button>';
        }
    }

    // Real file picker — reads the chosen image from the machine as a data URL (client-side preview).
    window.triggerUpload = function(event) {
        if (event) event.preventDefault();
        var input = document.createElement('input');
        input.type = 'file';
        input.accept = 'image/*';
        input.onchange = function() {
            var file = input.files && input.files[0];
            if (!file) return;
            if (file.size > 5 * 1024 * 1024) {
                alert('Ảnh quá lớn (tối đa 5MB).');
                return;
            }
            var reader = new FileReader();
            reader.onload = function(e) {
                disposalEvidence = e.target.result;
                disposalEvidenceName = file.name;
                renderEvidenceUploadBox();
            };
            reader.readAsDataURL(file);
        };
        input.click();
    };

    window.removeDisposalEvidence = function(event) {
        if (event) event.stopPropagation();
        disposalEvidence = "";
        renderEvidenceUploadBox();
    };

    window.submitDisposalNote = function() {
        if (!selectedDisposalSku) {
            alert('Vui lòng chọn sản phẩm cần xuất hủy!');
            return;
        }
        var qty = parseInt(document.getElementById('disposal-qty').value) || 0;
        var p = SCRAP_PRODUCTS.find(function(s) { return s.sku === selectedDisposalSku; });
        if (!p) {
            alert('Lỗi: Sản phẩm được chọn không nằm trong danh sách hàng hỏng.');
            return;
        }
        var limitVal = p.scrapQty;
        if (qty <= 0) {
            alert('Số lượng tiêu hủy phải lớn hơn 0!');
            return;
        }
        if (qty > limitVal) {
            alert('Số lượng tiêu hủy (' + qty + ') không được vượt quá số lượng hàng hỏng hiện có trong kho (' + limitVal + ')!');
            return;
        }
        var reason = document.getElementById('disposal-reason').value.trim();
        if (!reason) {
            alert('Vui lòng nhập lý do tiêu hủy!');
            return;
        }
        if (!disposalEvidence) {
            alert('Vui lòng tải lên ảnh bằng chứng thực tế!');
            return;
        }

        // Persist disposal note to DB (warehouse_issues SCRAP, APPROVED — recorded to ledger & auto-approved).
        closeDisposalModal();
        submitPostAction('disposal', {
            sku: selectedDisposalSku,
            qty: qty,
            reason: reason,
            warehouseId: window.WAREHOUSE_ID || 1
        });
    };

    // ─── PRINT-READY Goods Issue Note Modal ───
    var receiptDetailOverlay = document.getElementById('receiptDetailOverlay');
    var activePrintNote = null;

    window.openReceiptDetail = function(orderId, event) {
        if (event) event.stopPropagation();
        var order = pickOrders.find(function(o) { return o.id === orderId; });
        if (!order) return;

        activePrintNote = order;

        // Parse date details
        var dateVal = order.createdAt || '';
        var day = '28', month = '05', year = '2026';
        if (dateVal) {
            var splitted = dateVal.split(' ')[0];
            if (splitted.indexOf('/') > -1) {
                var pts = splitted.split('/');
                if (pts.length === 3) { day = pts[0]; month = pts[1]; year = pts[2]; }
            } else if (splitted.indexOf('-') > -1) {
                var pts = splitted.split('-');
                if (pts.length === 3) { year = pts[0]; month = pts[1]; day = pts[2]; }
            }
        }

        document.getElementById('print-date-day').textContent = day;
        document.getElementById('print-date-month').textContent = month;
        document.getElementById('print-date-year').textContent = year;
        
        document.getElementById('print-receipt-id').textContent = order.id;
        document.getElementById('print-customer').textContent = order.customer;
        document.getElementById('print-address').textContent = order.address;
        document.getElementById('print-reason').textContent = order.note || ('Xuất bán hàng theo đơn hàng ' + (order.mappedOrderId || order.soRef));
        document.getElementById('print-warehouse').textContent = order.items[0]?.location || 'Kho HCM - Quận 1 → Khu Hàng Thường';

        // Render table rows and sum amounts
        var totalAmount = 0;
        var totalQty = 0;

        var itemsHtml = order.items.map(function(item, idx) {
            totalQty += item.qty;
            // Get price from wh_pricing_warehouse dynamic storage or fallback
            var unitPrice = 15000;
            var pricingList = JSON.parse(localStorage.getItem(PRICING_WAREHOUSE_KEY) || '[]');
            var matchedPrice = pricingList.find(function(p) { return p.sku === item.skuCode; });
            if (matchedPrice && matchedPrice.importPrice) {
                unitPrice = matchedPrice.importPrice;
            }
            var subtotal = item.qty * unitPrice;
            totalAmount += subtotal;

            var lotCode = 'LOT-2026-' + String(idx + 1).padStart(2, '0');

            return '<tr>' +
                '<td style="text-align:center;">' + (idx + 1) + '</td>' +
                '<td><strong>' + item.skuName + '</strong></td>' +
                '<td><span style="font-family:monospace;">' + item.skuCode + '</span></td>' +
                '<td style="text-align:center;">Cái</td>' +
                '<td style="text-align:center;"><div style="font-family:monospace; font-size:10px;">' + lotCode + '</div><div style="font-size:9px; color:rgba(16,55,92,0.4)">HSD: 31/12/2028</div></td>' +
                '<td style="text-align:center; color:rgba(16,55,92,0.5);">' + item.qty + '</td>' +
                '<td style="text-align:center; font-weight:800; background:rgba(16,55,92,0.03);">' + item.qty + '</td>' +
                '<td style="text-align:right;">' + unitPrice.toLocaleString('vi-VN') + '</td>' +
                '<td style="text-align:right; font-weight:600;">' + subtotal.toLocaleString('vi-VN') + '</td>' +
            '</tr>';
        }).join('');

        // Sum row append
        itemsHtml += '<tr style="background:rgba(240,244,250,0.4); font-weight:bold; border-top:2px solid rgba(16,55,92,0.3);">' +
            '<td colspan="5" style="text-align:right;">CỘNG:</td>' +
            '<td style="text-align:center; color:rgba(16,55,92,0.5);">' + totalQty + '</td>' +
            '<td style="text-align:center; background:rgba(16,55,92,0.03); font-size:14px; font-weight:900;">' + totalQty + '</td>' +
            '<td></td>' +
            '<td style="text-align:right; font-size:13px;">' + totalAmount.toLocaleString('vi-VN') + '</td>' +
        '</tr>';

        document.getElementById('printTableItemsBody').innerHTML = itemsHtml;
        document.getElementById('print-amount-words').textContent = numberToVietnameseWords(totalAmount);

        receiptDetailOverlay.classList.add('active');
    };

    window.closeReceiptDetail = function() {
        receiptDetailOverlay.classList.remove('active');
    };

    window.printPDF = function() {
        var printContents = document.getElementById('receiptPrintArea').innerHTML;
        var originalContents = document.body.innerHTML;
        
        // Open print window safely
        var printWin = window.open('', '_blank');
        printWin.document.write('<html><head><title>Phiếu Xuất Kho - ' + (activePrintNote ? activePrintNote.id : '') + '</title>');
        printWin.document.write('<link rel="stylesheet" href="' + window.location.origin + '${pageContext.request.contextPath}/assets/css/dashboard.css" type="text/css" />');
        printWin.document.write('<style>body { padding:40px; background:#fff; color:#10375c; } .print-table { width:100%; border-collapse:collapse; border:2px solid rgba(16,55,92,0.2); margin-top:16px; } .print-table th { background:#f4f7f6; border:1px solid rgba(16,55,92,0.2); padding:10px 8px; font-size:10px; font-weight:700; text-transform:uppercase; } .print-table td { border:1px solid rgba(16,55,92,0.2); padding:8px 10px; font-size:12px; } .print-sign-grid { display:grid; grid-template-columns:repeat(5, 1fr); gap:12px; text-align:center; margin-top:32px; } .print-sign-title { font-size:10px; font-weight:700; text-transform:uppercase; } .print-sign-desc { font-size:10px; font-style:italic; color:rgba(16,55,92,0.4); }</style>');
        printWin.document.write('</head><body>');
        printWin.document.write(printContents);
        printWin.document.write('</body></html>');
        printWin.document.close();
        printWin.print();
    };

    // ─── HELPERS ───
    function padZero(n) { return n < 10 ? '0' + n : n; }

    function numberToVietnameseWords(num) {
        if (num === 0) return "Không đồng";
        var units = ["", "một", "hai", "ba", "bốn", "năm", "sáu", "bảy", "tám", "chín"];
        var tens = ["", "mười", "hai mươi", "ba mươi", "bốn mươi", "năm mươi", "sáu mươi", "bảy mươi", "tám mươi", "chín mươi"];
        var blocks = ["", "nghìn", "triệu", "tỷ"];

        var readThreeDigits = function(n, showZero) {
            var hundred = Math.floor(n / 100);
            var ten = Math.floor((n % 100) / 10);
            var unit = n % 10;
            var res = "";

            if (hundred > 0 || showZero) {
                res += units[hundred] + " trăm ";
            }

            if (ten > 0) {
                if (ten === 1) {
                    res += "mười ";
                } else {
                    res += tens[ten] + " ";
                }
            } else if (unit > 0 && (hundred > 0 || showZero)) {
                res += "lẻ ";
            }

            if (unit > 0) {
                if (unit === 1 && ten > 1) {
                    res += "mốt ";
                } else if (unit === 5 && ten > 0) {
                    res += "lăm ";
                } else {
                    res += units[unit] + " ";
                }
            }
            return res;
        };

        var str = "";
        var blockIdx = 0;
        var temp = num;

        while (temp > 0) {
            var part = temp % 1000;
            if (part > 0) {
                var partStr = readThreeDigits(part, temp > 1000).trim();
                str = partStr + " " + blocks[blockIdx] + " " + str;
            }
            temp = Math.floor(temp / 1000);
            blockIdx++;
        }

        str = str.replace(/\s+/g, " ").trim();
        if (str) {
            str = str.charAt(0).toUpperCase() + str.slice(1);
        }
        return str + " đồng chẵn";
    }

    [confirmOverlay, draftOverlay, disposalOverlay, receiptDetailOverlay].forEach(function(ov) {
        if (ov) {
            ov.addEventListener('click', function(e) {
                if (e.target === ov) {
                    ov.classList.remove('active');
                }
            });
        }
    });

    // ─── TOAST AUTO-DISMISS ───
    var toast = document.getElementById('whToast');
    if (toast) {
        setTimeout(function() {
            toast.style.animation = 'toastFadeOut 0.4s ease forwards';
            setTimeout(function() {
                if (toast.parentNode) toast.parentNode.removeChild(toast);
            }, 400);
        }, 4000);
    }

    // ─── BOOTSTRAP ───
    renderStatistics();
    renderOrders();
})();
</script>
