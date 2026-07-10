<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>

<link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/inbound--warehouse-inbound.css?v=2"/>
<style>
    .draft-row {
        grid-template-columns: 1.2fr 1.6fr 110px 110px 40px !important;
    }
</style>


<!-- ══ VIEW 1: RECEIPTS TAB ══════════════════════════════════ -->
<div id="view-receipts">
    <!-- GRN List Container (rendered by JavaScript) -->
    <!-- Toolbar -->
    <div class="toolbar" style="margin-bottom:12px;">
        <div class="search-wrap">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"></svg>
            <input type="text" placeholder="Tìm mã phiếu hoặc nhà cung cấp..." id="grnSearchInput"/>
        </div>
        <button class="btn-create btn-create--emerald" id="btnCreatePOTrigger">
            Tạo phiếu mua hàng
        </button>
    </div>

    <!-- Status Filter Tabs -->
    <div class="status-tabs" id="statusTabsContainer"></div>

    <!-- GRN Table (unified) -->
    <div class="grn-list" id="grnListContainer"></div>
</div>



<!-- ══ MODAL: CREATE / EDIT PURCHASE ORDER (PHIẾU MUA HÀNG) ═══ -->
<div class="modal-overlay" id="draftModalOverlay">
    <div class="modal-box" style="max-width: 800px;">
        <div class="modal-hdr">
            <div>
                <h2 class="modal-title" id="draftModalTitle">Tạo phiếu mua hàng</h2>
                <p class="modal-subtitle">Tạo phiếu mua → đặt hàng từ NCC → chờ hàng về → nhập kho</p>
            </div>
            <button class="modal-close" onclick="closeDraftModal()">&times;</button>
        </div>
        <div class="modal-body" style="background: rgba(240, 244, 250, 0.25);">
            <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 16px;">
                <div class="form-group">
                    <label class="form-label" for="draft-supplier">Nhà cung cấp *</label>
                    <select class="form-input" style="background:#fff;" id="draft-supplier">
                        <option value="">— Chọn nhà cung cấp —</option>
                    </select>
                    <small style="font-size:10px; color:rgba(16,55,92,0.50);">Chọn từ danh sách NCC do Manager tạo</small>
                </div>
                <div class="form-group">
                    <label class="form-label" for="draft-date">Ngày dự kiến *</label>
                    <input class="form-input" style="background:#fff;" type="date" id="draft-date"/>
                </div>
            </div>
            <div class="form-group">
                <label class="form-label" for="draft-note">Ghi chú</label>
                <textarea class="form-textarea" style="background:#fff;" id="draft-note" rows="3" placeholder="Ghi chú cho phiếu mua..."></textarea>
            </div>

            <!-- Draft Items grid -->
            <div class="draft-items-box">
                <div class="draft-items-hdr">
                    <span class="draft-items-title">Danh sách SKU đặt mua</span>
                    <button class="btn-add-row" onclick="addDraftItemRow()">Thêm dòng</button>
                </div>
                <div id="draftRowsContainer">
                    <!-- Rendered dynamically -->
                </div>
            </div>
        </div>
        <div class="modal-ftr" style="background:#fff;">
            <button class="modal-btn-cancel" onclick="closeDraftModal()">Hủy</button>
            <button class="modal-btn-submit modal-btn-blue" id="btnSubmitGRN" onclick="submitDraftGRN()">Tạo phiếu mua hàng</button>
        </div>
    </div>
</div>

<!-- ══ MODAL: CREATE RECEIPT NOTE (TẠO PHIẾU NHẬP KHO) ═══════ -->
<div class="modal-overlay" id="receiptModalOverlay">
    <div class="modal-box" style="max-width: 960px;">
        <div class="modal-hdr">
            <div>
                <h2 class="modal-title">Tạo phiếu nhập kho</h2>
                <p class="modal-subtitle" id="receiptModalSubtitle">Chọn phiếu mua hàng đã mua để tạo phiếu nhập kho</p>
            </div>
            <button class="modal-close" onclick="closeReceiptModal()">&times;</button>
        </div>
        <form method="POST" action="${pageContext.request.contextPath}/warehouse/inbound" id="receiptForm">
            <input type="hidden" name="action" value="receive"/>
            <div class="modal-body">
                <!-- Bước 1: Chọn phiếu mua hàng đã mua (PURCHASED) -->
                <div id="receiptSelectStep">
                    <p style="font-size:12px; color:rgba(16,55,92,0.60); margin-bottom:8px;">
                        Chỉ tạo được phiếu nhập kho khi phiếu mua hàng đã được mua (trạng thái "Đã mua").
                    </p>
                    <div class="form-group">
                        <label class="form-label" for="receipt-po-select">Phiếu mua hàng *</label>
                        <select class="form-input" style="background:#fff;" id="receipt-po-select" required>
                            <option value="">— Chọn phiếu mua hàng đã mua —</option>
                        </select>
                    </div>
                    <div id="receiptEmptyMsg" style="display:none; padding:24px; text-align:center; color:rgba(16,55,92,0.40); background:var(--alice); border-radius:8px; font-size:12px;">
                        Chưa có phiếu mua hàng nào ở trạng thái "Đã mua". Hãy mua phiếu trước rồi quay lại.
                    </div>
                </div>

                <!-- Bước 2: Form chi tiết (auto fill từ PO) -->
                <div id="receiptDetailStep" style="display:none;">
                    <input type="hidden" id="receipt-po-id" name="inboundId"/>
                    <!-- Header info row -->
                    <div style="display:grid; grid-template-columns:1fr 1fr 1fr; gap:10px; padding:10px 14px; background:#f8fafc; border:1px solid var(--border); border-radius:8px; margin-bottom:12px;">
                        <div class="form-group" style="margin:0;">
                            <label class="form-label" style="font-size:10px;">Zone nhận hàng</label>
                            <select class="form-input" style="background:#fff; font-size:12px;" name="zoneId" id="receipt-zone">
                                <option value="">— Chọn zone —</option>
                                <c:forEach items="${zones}" var="z">
                                    <option value="${z.zoneId}">${z.zoneName} (${z.zoneType})</option>
                                </c:forEach>
                            </select>
                        </div>
                        <div class="form-group" style="margin:0;">
                            <label class="form-label" style="font-size:10px;">Người giao hàng</label>
                            <input class="form-input" style="background:#fff; font-size:12px;" type="text" name="deliveryPerson" id="receipt-delivery-person" placeholder="Tên tài xế / shipper..."/>
                        </div>
                        <div class="form-group" style="margin:0;">
                            <label class="form-label" style="font-size:10px;">Ngày nhập hàng</label>
                            <input class="form-input" style="background:#fff; font-size:12px;" type="date" name="receivedDate" id="receipt-received-date"/>
                        </div>
                    </div>
                    <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom:12px;">
                        <div class="form-group">
                            <label class="form-label">Nhà cung cấp</label>
                            <input class="form-input" style="background:#f1f5f9;" type="text" id="receipt-supplier-name" readonly/>
                        </div>
                        <div class="form-group">
                            <label class="form-label" for="receipt-note">Ghi chú</label>
                            <textarea class="form-textarea" style="background:#fff;" id="receipt-note" name="notes" rows="2" placeholder="Ghi chú phiếu nhập..."></textarea>
                        </div>
                    </div>

                    <!-- Danh sách SKU nhập kho -->
                    <div class="draft-items-box">
                        <div class="draft-items-hdr">
                            <span class="draft-items-title">Danh sách SKU nhập kho</span>
                            <span style="font-size:11px; color:rgba(16,55,92,0.50);">SL Thực nhận = SL Chấp nhận + SL Trả NCC</span>
                        </div>
                        <div style="overflow-x:auto;">
                            <table class="grn-body-table" style="width:100%;">
                                <thead>
                                    <tr>
                                        <th style="padding-left:16px; min-width:90px;">SKU</th>
                                        <th style="min-width:140px;">Sản phẩm</th>
                                        <th style="text-align:right; width:60px;">Đặt mua</th>
                                        <th style="text-align:right; width:80px; color:#1d4ed8;">SL Thực nhận</th>
                                        <th style="text-align:right; width:80px; color:#059669;">SL Chấp nhận</th>
                                        <th style="text-align:right; width:80px; color:#b45309;">SL Trả NCC</th>
                                        <th style="min-width:140px; font-size:10px; color:rgba(16,55,92,0.60);">Lý do trả / Từ chối</th>
                                    </tr>
                                </thead>
                                <tbody id="receiptItemsTableBody">
                                    <!-- Populated by JS -->
                                </tbody>
                            </table>
                        </div>
                    </div>
                </div>
            </div>
            <div class="modal-ftr" style="background:#fff;">
                <button type="button" class="modal-btn-cancel" onclick="closeReceiptModal()">Hủy</button>
                <button type="submit" class="modal-btn-submit modal-btn-emerald" id="btnSubmitReceipt" disabled>Xác nhận nhập kho</button>
            </div>
        </form>
    </div>
</div>

<!-- ══ MODAL: RECEIVE GOODS (XÁC NHẬN NHẬP KHO) ══════════════ -->
<div class="modal-overlay" id="receiveModalOverlay">
    <div class="modal-box">
        <div class="modal-hdr">
            <div>
                <h2 class="modal-title">Nhập hàng thực tế</h2>
                <p class="modal-subtitle" id="receiveModalSubtitle">Mã phiếu nhập kho: ...</p>
            </div>
            <button class="modal-close" onclick="closeReceiveModal()">&times;</button>
        </div>
        <div class="modal-body">
            <p style="font-size: 12px; color: rgba(16, 55, 92, 0.6); margin-bottom: 8px;">Nhập SL thực nhận cho từng SKU. Phần chênh lệch (PO đặt − SL thực nhận) sẽ tự động ghi nhận là "Đã trả NCC tại chỗ" và không lưu vào tồn kho.</p>
            <input type="hidden" id="receive-grn-id"/>
            <div id="receiveItemsContainer" style="display:flex; flex-direction:column; gap:12px;">
                <!-- Populate items with 3 inputs: received / accepted / rejected -->
            </div>
        </div>
        <div class="modal-ftr">
            <button class="modal-btn-cancel" onclick="closeReceiveModal()">Hủy</button>
            <button class="modal-btn-submit modal-btn-emerald" onclick="submitConfirmReceive()">Xác nhận nhập kho</button>
        </div>
    </div>
</div>

<!-- ══ MODAL: CREATE PO (Server-side) ════════════════════════════════ -->
<div class="modal-overlay" id="createPOModal">
    <div class="modal-box" style="max-width:600px;">
        <div class="modal-hdr">
            <div>
                <h2 class="modal-title">Tạo phiếu mua hàng mới</h2>
                <p class="modal-subtitle">Tạo đơn mua hàng từ nhà cung cấp</p>
            </div>
            <button class="modal-close" onclick="closeCreatePOModal()">&times;</button>
        </div>
        <form method="POST" action="${pageContext.request.contextPath}/warehouse/inbound" id="createPOForm">
            <input type="hidden" name="action" value="create"/>
            <div class="modal-body">
                <div class="form-group">
                    <label class="form-label" for="po-supplier">Nhà cung cấp *</label>
                    <select class="form-input" id="po-supplier" name="supplierId" required>
                        <option value="">-- Chọn nhà cung cấp --</option>
                    </select>
                    <small class="form-hint">Danh sách lấy từ <a href="${pageContext.request.contextPath}/business/suppliers" target="_blank">trang quản lý NCC</a> (chỉ NCC ACTIVE).</small>
                </div>
                <div style="display:grid; grid-template-columns:1fr 1fr; gap:12px;">
                    <div class="form-group">
                        <label class="form-label" for="po-warehouse">Kho nhập *</label>
                        <select class="form-input" style="background:#f0f4fa;" id="po-warehouse" name="warehouseId" required>
                            <option value="">— Chọn kho —</option>
                            <c:forEach items="${warehouses}" var="w">
                                <option value="${w.warehouseId}">${w.warehouseName}</option>
                            </c:forEach>
                        </select>
                    </div>
                    <div class="form-group">
                        <label class="form-label" for="po-zone">Zone nhận hàng</label>
                        <select class="form-input" style="background:#fff;" id="po-zone" name="zoneId">
                            <option value="">— Chọn zone —</option>
                            <c:forEach items="${zones}" var="z">
                                <option value="${z.zoneId}">${z.zoneName} (${z.zoneType})</option>
                            </c:forEach>
                        </select>
                    </div>
                </div>
                <div class="form-group">
                    <label class="form-label">Ngày dự kiến nhận hàng</label>
                    <input class="form-input" style="background:#fff;" type="date" id="po-date" name="expectedDate"/>
                </div>
                <div style="display:grid; grid-template-columns:1fr 1fr; gap:12px;">
                    <div class="form-group">
                        <label class="form-label" for="po-delivery-person">Người giao hàng</label>
                        <input class="form-input" style="background:#fff;" type="text" id="po-delivery-person" name="deliveryPerson" placeholder="Tên tài xế / shipper..."/>
                    </div>
                    <div class="form-group">
                        <label class="form-label" for="po-delivery-phone">SĐT người giao</label>
                        <input class="form-input" style="background:#fff;" type="text" id="po-delivery-phone" name="deliveryPhone" placeholder="0xxx-xxx-xxx"/>
                    </div>
                </div>
                <div class="form-group">
                    <label class="form-label" for="po-notes">Ghi chú</label>
                    <textarea class="form-textarea" style="background:#fff;" id="po-notes" name="notes" rows="3" placeholder="Ghi chú thêm (nếu có)..."></textarea>
                </div>
            </div>
            <div class="modal-ftr" style="background:#fff;">
                <button type="button" class="modal-btn-cancel" onclick="closeCreatePOModal()">Hủy</button>
                <button type="submit" class="modal-btn-submit modal-btn-blue">Tạo phiếu mua hàng</button>
            </div>
        </form>
    </div>
</div>

<!-- ══ MODAL: CONFIRM RECEIVE (Server-side) ═════════════════════════ -->
<div class="modal-overlay" id="receiveDBModal">
    <div class="modal-box" style="max-width:600px;">
        <div class="modal-hdr">
            <div>
                <h2 class="modal-title">Nhập hàng thực tế</h2>
                <p class="modal-subtitle" id="receiveDB-subtitle">Mã phiếu nhập kho: ...</p>
            </div>
            <button class="modal-close" onclick="closeReceiveDBModal()">&times;</button>
        </div>
        <form method="POST" action="${pageContext.request.contextPath}/warehouse/inbound" id="receiveDBForm">
            <input type="hidden" name="action" value="receive"/>
            <input type="hidden" name="inboundId" id="receiveDB-inboundId"/>
            <div class="modal-body">
                <!-- Header: Zone / Người giao / Ngày giờ -->
                <div style="display:grid; grid-template-columns:1fr 1fr 1fr; gap:10px; padding:12px 16px; background:#f8fafc; border:1px solid var(--border); border-radius:8px; margin-bottom:14px;">
                    <div>
                        <label style="font-size:10px; font-weight:700; text-transform:uppercase; color:rgba(16,55,92,0.50); letter-spacing:0.04em; display:block; margin-bottom:4px;">Zone nhận hàng</label>
                        <select class="form-input" style="background:#fff; font-size:12px;" name="zoneId" id="receiveDB-zone">
                            <option value="">— Chọn zone —</option>
                            <c:forEach items="${zones}" var="z">
                                <option value="${z.zoneId}">${z.zoneName} (${z.zoneType})</option>
                            </c:forEach>
                        </select>
                    </div>
                    <div>
                        <label style="font-size:10px; font-weight:700; text-transform:uppercase; color:rgba(16,55,92,0.50); letter-spacing:0.04em; display:block; margin-bottom:4px;">Người giao hàng</label>
                        <input class="form-input" style="background:#fff; font-size:12px;" type="text" name="deliveryPerson" id="receiveDB-deliveryPerson" placeholder="Tên tài xế / shipper..."/>
                    </div>
                    <div>
                        <label style="font-size:10px; font-weight:700; text-transform:uppercase; color:rgba(16,55,92,0.50); letter-spacing:0.04em; display:block; margin-bottom:4px;">Ngày nhập hàng</label>
                        <input class="form-input" style="background:#fff; font-size:12px;" type="date" name="receivedDate" id="receiveDB-receivedDate"/>
                    </div>
                </div>
                <!-- Table: chi tiết kiểm đếm -->
                <div style="margin-bottom:8px; display:flex; align-items:center; justify-content:space-between;">
                    <span style="font-size:12px; font-weight:700; color:var(--navy); text-transform:uppercase; letter-spacing:0.04em;">Chi tiết kiểm đếm</span>
                    <span style="font-size:11px; color:rgba(16,55,92,0.50);">SL Thực nhận = Chấp nhận + Trả NCC</span>
                </div>
                <div style="border:1px solid var(--border); border-radius:8px; overflow:hidden;">
                    <table style="width:100%; border-collapse:collapse; font-size:12px;">
                        <thead>
                            <tr style="background:#f0f4fa;">
                                <th style="padding:8px 10px; text-align:left; font-size:10px; font-weight:700; text-transform:uppercase; color:rgba(16,55,92,0.60); letter-spacing:0.04em; border-bottom:1px solid var(--border);">SKU / Sản phẩm</th>
                                <th style="padding:8px 6px; text-align:center; font-size:10px; font-weight:700; text-transform:uppercase; color:rgba(16,55,92,0.60); letter-spacing:0.04em; border-bottom:1px solid var(--border); width:60px;">Đặt mua</th>
                                <th style="padding:8px 6px; text-align:center; font-size:10px; font-weight:700; text-transform:uppercase; color:#1d4ed8; border-bottom:1px solid var(--border); width:70px;">SL Thực nhận</th>
                                <th style="padding:8px 6px; text-align:center; font-size:10px; font-weight:700; text-transform:uppercase; color:#059669; border-bottom:1px solid var(--border); width:70px;">SL Chấp nhận</th>
                                <th style="padding:8px 6px; text-align:center; font-size:10px; font-weight:700; text-transform:uppercase; color:#b45309; border-bottom:1px solid var(--border); width:70px;">SL Trả NCC</th>
                                <th style="padding:8px 6px; text-align:left; font-size:10px; font-weight:700; text-transform:uppercase; color:rgba(16,55,92,0.60); border-bottom:1px solid var(--border); min-width:140px;">Lý do trả / Từ chối</th>
                            </tr>
                        </thead>
                        <tbody id="receiveDBItemsContainer">
                            <!-- Dynamic rows -->
                        </tbody>
                    </table>
                </div>
            </div>
            <div class="modal-ftr" style="background:#fff;">
                <button type="button" class="modal-btn-cancel" onclick="closeReceiveDBModal()">Hủy</button>
                <button type="submit" class="modal-btn-submit modal-btn-emerald">Xác nhận nhập hàng</button>
            </div>
        </form>
    </div>
</div>

<!-- ══ MODAL: DETAIL VIEW ═══════════════════════════════════ -->
<div class="modal-overlay" id="detailModalOverlay">
    <div class="modal-box" style="max-width: 960px;">
        <div class="modal-hdr">
            <div>
                <h2 class="modal-title">Chi tiết Phiếu mua hàng</h2>
                <p class="modal-subtitle" id="detailModalSubtitle">GRN-XXXX</p>
            </div>
            <button class="modal-close" onclick="closeDetailModal()">&times;</button>
        </div>
        <div class="modal-body" style="gap: 16px;">

            <!-- Metadata 2-column grid -->
            <div style="display:grid; grid-template-columns:1fr 1fr; gap:12px 24px; font-size:13px; color:var(--navy); background:#f8fafc; border:1px solid var(--border); border-radius:8px; padding:16px;">
                <div><strong>Mã NCC:</strong> <span id="detail-supplier-code">—</span></div>
                <div><strong>Trạng thái:</strong> <span id="detail-status-badge">—</span></div>
                <div><strong>Nhà cung cấp:</strong> <span id="detail-supplier">—</span></div>
                <div><strong>Kho nhận hàng:</strong> <span id="detail-warehouse">—</span></div>
                <div><strong>Kỳ hạn thanh toán:</strong> <span id="detail-payment-terms">—</span></div>
                <div><strong>Ngày tạo:</strong> <span id="detail-created-at">—</span></div>
                <div><strong>Ngày nhận dự kiến:</strong> <span id="detail-expected-date">—</span></div>
                <div style="grid-column:1/-1; border-top:1px solid var(--border); padding-top:10px; margin-top:4px;">
                    <strong>Chi tiết liên hệ:</strong>
                    <span id="detail-contact">—</span> &nbsp;|&nbsp;
                    <span id="detail-phone">—</span> &nbsp;|&nbsp;
                    <span id="detail-email">—</span> &nbsp;|&nbsp;
                    <span id="detail-address">—</span>
                </div>
            </div>

            <!-- Items table -->
            <div class="draft-items-box">
                <div class="draft-items-hdr" style="background:var(--alice);">
                    <span class="draft-items-title" style="font-weight:700;">Danh sách mặt hàng đặt mua</span>
                </div>
                <table class="grn-body-table">
                    <thead>
                        <tr style="background:#fff;">
                            <th style="padding-left:16px; min-width:90px;">SKU</th>
                            <th style="min-width:140px;">Sản phẩm</th>
                            <th style="text-align:right; width:90px; white-space:nowrap;">Đơn giá</th>
                            <th style="text-align:right; width:70px; white-space:nowrap;">Đặt mua</th>
                            <th style="text-align:right; width:110px; white-space:nowrap; padding-right:16px;">Thành tiền</th>
                        </tr>
                    </thead>
                    <tbody id="detailItemsTableBody"><!-- Populate --></tbody>
                    <tfoot id="detailItemsFooter" style="background:#f1f5f9; font-size:12px; font-weight:700; color:var(--navy);">
                        <tr>
                            <td colspan="3" style="padding:10px 16px; text-align:right; border-top:2px solid var(--border);">
                                Tổng: <span id="detail-total-items">0</span> mặt hàng · <span id="detail-total-qty">0</span> sản phẩm
                            </td>
                            <td style="text-align:right; padding:10px 0 10px 16px; border-top:2px solid var(--border);">
                                &nbsp;
                            </td>
                            <td style="text-align:right; padding:10px 16px 10px 0; border-top:2px solid var(--border);">
                                <span id="detail-grand-total">—</span>
                            </td>
                        </tr>
                        <tr>
                            <td colspan="5" style="padding:3px 16px 8px; text-align:right; color:#b45309; font-size:11px; border-top:1px solid var(--border);">
                                (Chưa bao gồm VAT)
                            </td>
                        </tr>
                    </tfoot>
                </table>
            </div>

            <!-- Notes -->
            <div class="modal-note" id="detail-notes-box" style="display:none; padding:12px; border:1px solid #ffebc2; background:#fffcf5; border-radius:6px; font-size:12px; color:rgba(16, 55, 92, 0.75);">
                <strong>Ghi chú:</strong> <span id="detail-note-content">—</span>
            </div>
        </div>
        <div class="modal-ftr">
            <button class="modal-btn-cancel" onclick="closeDetailModal()">Đóng</button>
        </div>
    </div>
</div>

<script id="db-products-data" type="application/json"><c:out value="${productsJson}" escapeXml="false"/></script>
<script id="db-suppliers-data" type="application/json"><c:out value="${suppliersJson}" escapeXml="false"/></script>
<script id="db-page-flags-data" type="application/json">{"hasInboundList": ${not empty inboundList ? 'true' : 'false'}, "myWarehouseId": ${myWarehouseId}}</script>
<script id="db-user-data" type="application/json">{"fullName":"<c:out value='${loggedInUser.fullName}'/>","role":"<c:out value='${loggedInUser.role}'/>"}</script>
<script id="db-inbound-list-data" type="application/json">[
<c:forEach items="${inboundList}" var="io" varStatus="s">{"inboundId":${io.inboundId},"inboundCode":"<c:out value="${io.inboundCode}"/>","supplierName":"<c:out value="${io.supplierName}"/>","supplierId":<c:out value="${io.supplierId != null ? io.supplierId : 'null'}"/>,"supplierCode":"<c:out value="${io.supplierCode != null ? io.supplierCode : ''}"/>","supplierContact":"<c:out value="${io.supplierContact != null ? io.supplierContact : ''}"/>","supplierPhone":"<c:out value="${io.supplierPhone != null ? io.supplierPhone : ''}"/>","supplierAddress":"<c:out value="${io.supplierAddress != null ? io.supplierAddress : ''}"/>","supplierEmail":"<c:out value="${io.supplierEmail != null ? io.supplierEmail : ''}"/>","warehouseName":"<c:out value="${io.warehouseName}"/>","zoneId":<c:out value="${io.zoneId != null ? io.zoneId : 'null'}"/>,"zoneName":"<c:out value="${io.zoneName != null ? io.zoneName : ''}"/>","deliveryPerson":"<c:out value="${io.deliveryPerson != null ? io.deliveryPerson : ''}"/>","deliveryPhone":"<c:out value="${io.deliveryPhone != null ? io.deliveryPhone : ''}"/>","paymentTerms":"<c:out value="${io.paymentTerms != null ? io.paymentTerms : ''}"/>","status":"<c:out value="${io.status}"/>","createdAt":"<c:out value="${io.createdAt}"/>","expectedDate":"<c:out value="${io.expectedDate}"/>","receivedDate":"<c:out value="${io.receivedDate != null ? io.receivedDate : ''}"/>","note":"<c:out value="${io.notes != null ? io.notes : ''}"/>","items":${io.itemsJson}}${!s.last ? ',' : ''}
</c:forEach>]
</script>

<script>
(function () {
'use strict';

function safeJsonParse(rawValue, fallbackValue) {
    if (!rawValue) {
        return fallbackValue;
    }
    try {
        return JSON.parse(rawValue);
    } catch (error) {
        return fallbackValue;
    }
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

var WMS_USER_DATA = safeJsonParse(document.getElementById('db-user-data') && document.getElementById('db-user-data').textContent, {});
var PAGE_FLAGS = safeJsonParse(document.getElementById('db-page-flags-data') && document.getElementById('db-page-flags-data').textContent, {});
var DB_PRODUCTS = safeJsonParse(document.getElementById('db-products-data') && document.getElementById('db-products-data').textContent, []);
var DB_SUPPLIERS = safeJsonParse(document.getElementById('db-suppliers-data') && document.getElementById('db-suppliers-data').textContent, []);
window.myWarehouseId = PAGE_FLAGS.myWarehouseId;

window.WMS_USER = {
    fullName: WMS_USER_DATA.fullName || 'Guest',
    role: WMS_USER_DATA.role || 'Guest',
    myWarehouseId: parseInt("${myWarehouseId}") || 1
};

// Inbound Receipts — server data is single source of truth.
// Local drafts are no longer used; all GRNs are created directly on the server.
var serverInboundList = safeJsonParse(document.getElementById('db-inbound-list-data') && document.getElementById('db-inbound-list-data').textContent, []);
console.log('[INBOUND] serverInboundList:', serverInboundList.length, serverInboundList);
var grns = (serverInboundList || []).map(function(o) {
    var mappedStatus = o.status;
    if (o.status === 'PENDING')     mappedStatus = 'pending';
    else if (o.status === 'PURCHASED')  mappedStatus = 'purchased';
    else if (o.status === 'IN_PROGRESS') mappedStatus = 'in_progress';
    else if (o.status === 'RECEIVED')    mappedStatus = 'completed';
    else if (o.status === 'CANCELLED')   mappedStatus = 'cancelled';
    else mappedStatus = o.status || 'pending';

    return {
        id: o.inboundId,
        inboundCode: o.inboundCode,
        supplier: o.supplierName,
        supplierId: o.supplierId,
        supplierCode: o.supplierCode,
        supplierCode: o.supplierCode || '',
        supplierContact: o.supplierContact,
        supplierPhone: o.supplierPhone,
        supplierAddress: o.supplierAddress || '',
        supplierEmail: o.supplierEmail || '',
        warehouseName: o.warehouseName,
        paymentTerms: o.paymentTerms || '',
        status: mappedStatus,
        rawStatus: o.status,
        createdAt: o.createdAt,
        expectedDate: o.expectedDate || '',
        note: o.note || '',
        items: (o.items || []).map(function(item) {
            return {
                productId: item.productId || 0,
                skuCode: item.skuCode || item.sku || '',
                skuName: item.skuName || item.productName || '',
                orderedQty: parseFloat(item.orderedQty || item.expectedQty || 0),
                receivedQty: parseFloat(item.receivedQty || 0),
                acceptedQty: parseFloat(item.acceptedQty || 0),
                rejectedQty: parseFloat(item.rejectedQty || 0),
                price: parseFloat(item.price || 0),
                note: item.note || ''
            };
        })
    };
});
console.log('[INBOUND] grns loaded:', grns.length, grns);

// Master SKUs
var savedSKUs = localStorage.getItem('wms_skus');
var skus = safeJsonParse(savedSKUs, []);
if ((!skus || skus.length === 0) && DB_PRODUCTS.length > 0) {
    skus = DB_PRODUCTS.map(function(p) {
        return {
            id: p.productId || p.id,
            sku: p.skuCode || p.sku,
            name: p.productName || p.name,
            category: p.categoryName || p.category || 'Chưa phân loại',
            status: p.status || 'PENDING',
            qtyOnHand: p.qtyOnHand || 0,
            approvalStatus: p.approvalStatus || 'approved'
        };
    });
}

// Pricing configuration — removed (base_price managed by Manager in master-sku)

// ─── STATE VARIABLES ───
var activeStatusTab = 'all'; // 'all', 'pending', 'in_progress', 'completed', 'cancelled'
var searchKeyword = '';
var expandedGrnId = null;

// Modal forms state
var draftMode = 'create'; // 'create' or 'duplicate'
var draftSourceId = null;
var draftForm = {
    supplierId: null,
    expectedDate: '',
    note: '',
    items: []
};


// ─── VIEW 1: RECEIPTS LOGIC ───
// Search handler
var searchInput = document.getElementById('grnSearchInput');
if (searchInput) {
    searchInput.addEventListener('input', function(e) {
        searchKeyword = e.target.value;
        renderReceipts();
    });
}

function getFilteredGRNs() {
    // Tab "pending" (Chờ nhập) bao gồm cả PENDING (chưa mua) + PURCHASED (đã mua) - phiếu chưa bắt đầu nhập kho
    return grns.filter(function (g) {
        var matchTab;
        if (activeStatusTab === 'all') {
            matchTab = true;
        } else if (activeStatusTab === 'pending') {
            matchTab = g.status === 'pending' || g.status === 'purchased';
        } else {
            matchTab = g.status === activeStatusTab;
        }
        var matchSearch = g.id.toString().toLowerCase().indexOf(searchKeyword.toLowerCase()) > -1 ||
                          (g.supplier && g.supplier.toLowerCase().indexOf(searchKeyword.toLowerCase()) > -1) ||
                          (g.inboundCode && g.inboundCode.toLowerCase().indexOf(searchKeyword.toLowerCase()) > -1);
        return matchTab && matchSearch;
    });
}

function updateReceiptsKPIs() {
    // KPI cards removed
}

function renderStatusTabs() {
    var counts = {
        all: grns.length,
        pending: grns.filter(function(g) { return g.status === 'pending'; }).length,
        purchased: grns.filter(function(g) { return g.status === 'purchased'; }).length,
        in_progress: grns.filter(function(g) { return g.status === 'in_progress'; }).length,
        completed: grns.filter(function(g) { return g.status === 'completed'; }).length,
        cancelled: grns.filter(function(g) { return g.status === 'cancelled'; }).length
    };

    var tabsData = [
        { id: 'all', label: 'Tất cả' },
        { id: 'pending', label: 'Chờ nhập' },
        { id: 'in_progress', label: 'Đang nhập' },
        { id: 'completed', label: 'Đã nhập' },
        { id: 'cancelled', label: 'Đã hủy' }
    ];

    var html = tabsData.map(function(tab) {
        var act = tab.id === activeStatusTab ? 'active' : '';
        return '<button class="status-tab-btn ' + act + '" onclick="window.selectStatusTab(\'' + tab.id + '\')">' +
            tab.label +
            '<span class="status-tab-badge">' + counts[tab.id] + '</span>' +
            '</button>';
    }).join('');

    var tabsContainer = document.getElementById('statusTabsContainer');
    if (tabsContainer) tabsContainer.innerHTML = html;
}

window.selectStatusTab = function(statusId) {
    activeStatusTab = statusId;
    renderReceipts();
};

window.viewGRNDetail = function(grnId, event) {
    if (event) event.stopPropagation();
    expandedGrnId = expandedGrnId == grnId ? null : grnId;
    renderReceipts();
};

window.toggleGrnExpand = function(grnId) {
    expandedGrnId = expandedGrnId == grnId ? null : grnId;
    renderReceipts();
};

window.toggleDropdownMenu = function(grnId, event) {
    if (event) event.stopPropagation();

    var menu = document.getElementById('dropdown-' + grnId);
    if (!menu) return;

    var wasOpen = menu.dataset.open === '1';

    // Close all menus first
    var allMenus = document.querySelectorAll('.dropdown-menu');
    allMenus.forEach(function(m) {
        m.classList.remove('active');
        m.removeAttribute('style'); // clear fixed positioning
        m.dataset.open = '0';
    });

    // If it was already active, just close it and stop
    if (wasOpen) {
        return;
    }

    // Position the menu using fixed coordinates relative to the trigger button
    var btn = event ? event.currentTarget : null;
    
    // Set display block first so we can measure offsetHeight
    menu.classList.add('active');
    
    if (btn) {
        var rect = btn.getBoundingClientRect();
        menu.style.position = 'fixed';
        menu.style.zIndex = '9999';
        
        var menuHeight = menu.offsetHeight || 120;
        var spaceBelow = window.innerHeight - rect.bottom;
        
        if (spaceBelow < menuHeight + 10 && rect.top > menuHeight + 10) {
            // Position above the button
            menu.style.top = 'auto';
            menu.style.bottom = (window.innerHeight - rect.top + 8) + 'px';
        } else {
            // Position below the button
            menu.style.top = (rect.bottom + 8) + 'px';
            menu.style.bottom = 'auto';
        }
        
        menu.style.right = (window.innerWidth - rect.right) + 'px';
        menu.style.left = 'auto';
    }

    menu.dataset.open = '1';
};


// Close menus when clicking outside
document.addEventListener('click', function(e) {
    if (!e.target.closest('.dropdown-wrap') && !e.target.closest('.dropdown-menu')) {
        var allMenus = document.querySelectorAll('.dropdown-menu');
        allMenus.forEach(function(m) {
            m.classList.remove('active');
            m.removeAttribute('style');
            m.dataset.open = '0';
        });
    }
});


function getStatusConfig(status) {
    var configs = {
        pending:    { label: "Chờ nhập hàng", bg: "pending", tone: "blue",
                     icon: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>' },
        purchased:  { label: "Đã mua hàng", bg: "purchased", tone: "violet",
                     icon: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M6 2 3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4Z"/><path d="M3 6h18"/><path d="M16 10a4 4 0 0 1-8 0"/></svg>' },
        in_progress:{ label: "Đang nhập kho", bg: "in_progress", tone: "orange",
                     icon: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 17V3"/><path d="m6 11 6 6 6-6"/><path d="M19 21H5"/></svg>' },
        completed:  { label: "Hoàn thành", bg: "completed", tone: "emerald",
                     icon: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>' },
        cancelled:  { label: "Đã hủy", bg: "cancelled", tone: "rose",
                     icon: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>' }
    };
    return configs[status] || configs.pending;
}

function renderReceipts() {
    localStorage.setItem('wh_inbound_grns', JSON.stringify(grns));
    updateReceiptsKPIs();
    renderStatusTabs();

    var filtered = getFilteredGRNs();
    var listContainer = document.getElementById('grnListContainer');
    
    if (filtered.length === 0) {
        listContainer.innerHTML = '<div style="background:#fff; border:1px dashed var(--border); border-radius:12px; padding:48px; text-align:center; color:rgba(16, 55, 92, 0.40); font-weight:500; font-size:13px;">Không tìm thấy phiếu nhập kho nào.</div>';
        return;
    }

    var html = filtered.map(function(grn) {
        var sc = getStatusConfig(grn.status);
        var isExpanded = expandedGrnId == grn.id;
        var totalOrdered = grn.items.reduce(function(sum, i) { return sum + i.orderedQty; }, 0);
        var totalReceived = grn.items.reduce(function(sum, i) { return sum + i.receivedQty; }, 0);

        var expandedClass = isExpanded ? 'expanded' : '';
        
        // Locked indicator
        var lockHtml = (grn.isLocked && grn.status === 'draft') ?
            '<span class="pill-badge draft" style="margin-left:8px;">' +
            '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="width:10px;height:10px;margin-right:2px;"><rect width="18" height="11" x="3" y="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>' +
            'Khóa</span>' : '';

        // Inbound action buttons by status
        var purchaseBtn = '';
        var receiveBtn = '';
        var isDbBacked = (typeof grn.id === 'number') || /^\d+$/.test(String(grn.id));
        if (isDbBacked) {
            // PENDING hoặc PURCHASED → "Nhập hàng" (bỏ bước "Mua phiếu" riêng - phiếu nhập sinh ra từ việc nhận hàng)
            if (grn.status === 'pending' || grn.status === 'purchased') {
                receiveBtn = '<button class="btn-action-grn" onclick="window.openCreateReceiptFromPo(\'' + grn.id + '\', event)">Nhập hàng</button>';
            } else if (grn.status === 'in_progress') {
                var allReceived = grn.items.every(function(item) {
                    return (item.receivedQty || 0) >= item.orderedQty;
                });
                if (!allReceived) {
                    receiveBtn =
                        '<button class="btn-action-grn" onclick="window.dbOpenReceiveModal(\'' + grn.id + '\', \'' + (grn.inboundCode || grn.id) + '\', event)">Nhập thêm</button>';
                }
                receiveBtn +=
                    '<button class="btn-action-grn btn-action-blue" onclick="window.completeInboundOrder(\'' + grn.id + '\', event)">Hoàn thành</button>';
            }
        } else {
            if (grn.status === 'pending' || grn.status === 'in_progress' || grn.status === 'confirmed') {
                receiveBtn = '<button class="btn-action-grn" onclick="openReceiveModal(\'' + grn.id + '\', event)">Nhập kho</button>';
            }
        }

        // Eye action button (view detail - for all statuses)
        var detailBtn =
            '<button class="btn-action-icon" onclick="openDetailModal(\'' + grn.id + '\', event)" title="Xem chi tiết">' +
            '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M2 12s3-7 10-7 10 7 10 7-3 7-10 7-10-7-10-7Z"/><circle cx="12" cy="12" r="3"/></svg>' +
            '</button>';

        // Dropdown actions menu (đã bỏ "Trình duyệt BM" — phiếu mới thẳng IN_PROGRESS)
        var dropdownHtml = '';
        if (grn.status === 'pending' || grn.status === 'in_progress') {
            dropdownHtml =
                '<button class="dropdown-btn" onclick="openDetailModal(\'' + grn.id + '\', event)">' +
                    '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M2 12s3-7 10-7 10 7 10 7-3 7-10 7-10-7-10-7Z"/><circle cx="12" cy="12" r="3"/></svg>' +
                    'Xem chi tiết' +
                '</button>';
        }


        var menuActions = '';
        if (grn.status === 'draft' && !grn.isLocked) {
            menuActions = 
                '<div class="dropdown-wrap">' +
                    '<button class="btn-action-icon" onclick="window.toggleDropdownMenu(\'' + grn.id + '\', event)" title="Thao tác">' +
                        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="1"/><circle cx="12" cy="5" r="1"/><circle cx="12" cy="19" r="1"/></svg>' +
                    '</button>' +
                    '<div class="dropdown-menu" id="dropdown-' + grn.id + '">' +
                        '<button class="dropdown-btn" onclick="window.editDraftGRN(\'' + grn.id + '\', event)">' +
                            '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 1 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>' +
                            'Chỉnh sửa' +
                        '</button>' +
                        '<button class="dropdown-btn" style="color:#dc2626;" onclick="window.deleteDraftGRN(\'' + grn.id + '\', event)">' +
                            '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="#dc2626" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 6h18"/><path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6"/><path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2"/><line x1="10" y1="11" x2="10" y2="17"/><line x1="14" y1="11" x2="14" y2="17"/></svg>' +
                            'Xoá nháp' +
                        '</button>' +
                        dropdownHtml +
                    '</div>' +
                '</div>';
        }


        // Expanded table rows
        var itemsRows = grn.items.map(function(item) {
            var accepted = item.acceptedQty || 0;
            var remaining = item.orderedQty - accepted;
            var pct = item.orderedQty > 0 ? Math.round((accepted / item.orderedQty) * 100) : 0;
            var barColor = pct === 100 ? '#10b981' : pct > 0 ? '#F5C842' : 'var(--border)';
            var remainingClass = remaining > 0 ? 'color: var(--orange); font-weight:600;' : 'color: #047857; font-weight:600;';
            var priceHtml = item.price ? item.price.toLocaleString('vi-VN') + ' đ' : '—';

            return '<tr>' +
                '<td><span style="font-family:monospace; color:rgba(16, 55, 92, 0.6);">' + item.skuCode + '</span></td>' +
                '<td><span style="color:var(--navy); font-weight:600;">' + item.skuName + '</span></td>' +
                '<td style="text-align:right; font-weight:600; color:var(--navy); white-space:nowrap;">' + priceHtml + '</td>' +
                '<td style="text-align:right; font-weight:600; color:var(--navy);">' + item.orderedQty + '</td>' +
                '<td style="text-align:right; font-weight:600; color:#047857;">' + accepted + '</td>' +
                '<td style="text-align:right; ' + remainingClass + '">' + remaining + '</td>' +
                '<td><div style="display:flex; align-items:center; gap:8px;">' +
                    '<div class="progress-bar-wrap"><div class="progress-bar-fill" style="width:' + pct + '%; background:' + barColor + ';"></div></div>' +
                    '<span style="font-size:10px; color:rgba(16, 55, 92, 0.4); min-width:24px; text-align:right;">' + pct + '%</span>' +
                '</div></td>' +
            '</tr>';
        }).join('');

        // Notes box
        var notesBox = grn.note ? 
            '<div class="grn-notes-bar">' +
                '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="4" x2="20" y1="9" y2="9"/><line x1="4" x2="20" y1="15" y2="15"/><line x1="10" x2="8" y1="3" y2="21"/><line x1="16" x2="14" y1="3" y2="21"/></svg>' +
                '<span>' + grn.note + '</span>' +
            '</div>' : '';

        // Receiver bar
        var receiverBox = grn.receivedBy ?
            '<div class="grn-user-bar">' +
                '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/></svg>' +
                '<span>Người nhập kho: <span style="font-weight:600; color:var(--navy);">' + grn.receivedBy + '</span></span>' +
            '</div>' : '';

        var toneClass = grn.status === 'completed' ? 'emerald'
                            : grn.status === 'in_progress' ? 'orange'
                            : grn.status === 'purchased' ? 'violet'
                            : grn.status === 'pending' ? 'blue'
                            : 'navy';

        var supplierBadge = grn.supplierCode ? '<small style="color:#6b7280;font-weight:500;margin-left:6px;font-size:11px;">[' + escapeHtml(grn.supplierCode) + ']</small>' : '';

        return '<div class="grn-item ' + expandedClass + '">' +
            '<!-- Header -->' +
            '<div class="grn-hdr" onclick="window.toggleGrnExpand(\'' + grn.id + '\')">' +
                '<div class="grn-hdr__icon tone-' + toneClass + '">' +
                    sc.icon +
                '</div>' +
                '<div class="grn-hdr__info">' +
                    '<div class="grn-meta-row">' +
                        '<span class="grn-id">' + (grn.inboundCode || grn.id) + '</span>' +
                        '<span class="pill-badge ' + grn.status + '"><span class="pill-badge__dot"></span>' + sc.label + '</span>' +
                        lockHtml +
                    '</div>' +
                    '<div class="grn-supplier-row">' +
                        '<span class="grn-supplier-cell">' +
                            '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M6 22V4a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v18Z"/><path d="M6 12H4a2 2 0 0 0-2 2v6a2 2 0 0 0 2 2h2Z"/><path d="M18 9h2a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2h-2Z"/></svg>' +
                            escapeHtml(grn.supplier || '') + supplierBadge +
                        '</span>' +
                        '<span>' + grn.createdAt + '</span>' +
                    '</div>' +
                '</div>' +
                '<div class="grn-stats-row">' +
                    '<div class="grn-stat">' +
                        '<div class="grn-stat__lbl">SKU</div>' +
                        '<div class="grn-stat__val">' + grn.items.length + '</div>' +
                    '</div>' +
                    '<div class="grn-stat">' +
                        '<div class="grn-stat__lbl">Nhập / Đặt</div>' +
                        '<div class="grn-stat__val">' +
                            '<span style="' + (totalReceived === totalOrdered ? 'color:#059669;' : '') + '">' + totalReceived + '</span>' +
                            '<span style="font-size:11px; color:rgba(16, 55, 92, 0.3);">/' + totalOrdered + '</span>' +
                        '</div>' +
                    '</div>' +
                    detailBtn +
                    purchaseBtn +
                    receiveBtn +
                    menuActions +
                    '<svg class="grn-chevron" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="6 9 12 15 18 9"/></svg>' +
                '</div>' +
            '</div>' +
            
            '<!-- Expanded Body -->' +
            '<div class="grn-body">' +
                '<table class="grn-body-table">' +
                    '<thead>' +
                        '<tr>' +
                            '<th style="width: 140px;">SKU</th>' +
                            '<th>Tên sản phẩm</th>' +
                            '<th style="width: 100px; text-align: right;">Đơn giá</th>' +
                            '<th style="width: 80px; text-align: right;">Đặt</th>' +
                            '<th style="width: 80px; text-align: right;">Chấp nhận</th>' +
                            '<th style="width: 80px; text-align: right;">Còn lại</th>' +
                            '<th style="width: 140px;">Tiến độ</th>' +
                        '</tr>' +
                    '</thead>' +
                    '<tbody>' +
                        itemsRows +
                    '</tbody>' +
                '</table>' +
                notesBox +
                receiverBox +
            '</div>' +
        '</div>';
    }).join('');

    listContainer.innerHTML = html;
}

// ─── DRAFT MODAL ACTIONS ───
var draftOverlay = document.getElementById('draftModalOverlay');

/**
 * Populate dropdown nhà cung cấp từ danh sách suppliers do Manager tạo (DB).
 */
function populateSupplierDropdown(selectEl, selectedValue) {
    if (!selectEl) return;
    var html = '<option value="">— Chọn nhà cung cấp —</option>';
    if (DB_SUPPLIERS && DB_SUPPLIERS.length > 0) {
        DB_SUPPLIERS.forEach(function(s) {
            if (!s || !s.name) return;
            var sel = (selectedValue && (selectedValue === s.name || selectedValue === String(s.supplierId))) ? 'selected' : '';
            var label = s.supplierCode ? (s.supplierCode + ' — ' + s.name) : s.name;
            // value = supplierId (FK) để backend áp dụng ràng buộc
            html += '<option value="' + escapeHtml(String(s.supplierId)) + '" ' + sel + '>' + escapeHtml(label) + '</option>';
        });
    }
    selectEl.innerHTML = html;
}

window.openDraftModal = function(mode, sourceId) {
    draftMode = mode;
    draftSourceId = sourceId || null;

    var titleEl = document.getElementById('draftModalTitle');
    var supplierSelect = document.getElementById('draft-supplier');
    var dateInput = document.getElementById('draft-date');
    var noteInput = document.getElementById('draft-note');

    if (mode === 'create') {
        titleEl.textContent = 'Tạo phiếu mua hàng';
        draftForm = {
            supplierId: null,
            expectedDate: '',
            note: '',
            items: [{ skuCode: '', skuName: '', orderedQty: 10, price: 0 }]
        };
    } else { // duplicate
        var source = grns.find(function(g) { return g.id == sourceId; });
        titleEl.textContent = 'Nhân bản phiếu mua hàng';
        draftForm = {
            supplierId: source ? source.supplierId : null,
            expectedDate: source ? source.expectedDate : '',
            note: source ? (source.note || '') : '',
            items: source ? source.items.map(function(item) {
                return { skuCode: item.skuCode, skuName: item.skuName, orderedQty: item.orderedQty, price: item.price || 0 };
            }) : []
        };
    }

    populateSupplierDropdown(supplierSelect, draftForm.supplierId);
    dateInput.value = draftForm.expectedDate;
    noteInput.value = draftForm.note;

    renderDraftRows();
    draftOverlay.classList.add('active');
};

window.closeDraftModal = function() {
    draftOverlay.classList.remove('active');
};

window.duplicateGRN = function(grnId, event) {
    if (event) event.stopPropagation();
    openDraftModal('duplicate', grnId);
};

window.editDraftGRN = function(grnId, event) {
    if (event) event.stopPropagation();
    var grn = grns.find(function(g) { return g.id == grnId; });
    if (!grn) return;

    // Close any open dropdown
    var allMenus = document.querySelectorAll('.dropdown-menu');
    allMenus.forEach(function(m) { m.classList.remove('active'); });

    draftMode = 'edit';
    draftSourceId = grnId;

    var titleEl = document.getElementById('draftModalTitle');
    titleEl.textContent = 'Chỉnh sửa phiếu mua hàng';
    draftForm = {
        supplierId: grn.supplierId || null,
        expectedDate: grn.expectedDate || '',
        note: grn.note || '',
        items: grn.items.map(function(item) {
            return { skuCode: item.skuCode, skuName: item.skuName, orderedQty: item.orderedQty, price: item.price || 0, receivedQty: item.receivedQty || 0 };
        })
    };
    if (draftForm.items.length === 0) {
        draftForm.items.push({ skuCode: '', skuName: '', orderedQty: 10, price: 0 });
    }

    var supplierSelect = document.getElementById('draft-supplier');
    populateSupplierDropdown(supplierSelect, draftForm.supplierId);
    document.getElementById('draft-date').value = draftForm.expectedDate;
    document.getElementById('draft-note').value = draftForm.note;

    renderDraftRows();
    draftOverlay.classList.add('active');

    // Override submit to update existing draft instead of creating new
    var submitBtn = document.querySelector('#draftModalOverlay .modal-btn-submit');
    if (submitBtn) {
        submitBtn.onclick = function() {
            // Update the draft in grns array
            var idx = grns.findIndex(function(g) { return g.id == grnId; });
            if (idx > -1) {
                var supplierVal = (document.getElementById('draft-supplier').value || '').trim();
                var supplierIdNum = parseInt(supplierVal, 10);
                var dateVal = document.getElementById('draft-date').value;
                var noteVal = document.getElementById('draft-note').value.trim();
                if (!supplierVal || !dateVal) {
                    alert('Vui lòng chọn Nhà cung cấp và Ngày dự kiến!');
                    return;
                }
                if (!supplierIdNum || supplierIdNum <= 0) {
                    alert('Vui lòng chọn nhà cung cấp từ danh sách (không nhập text tự do).');
                    return;
                }
                // Lưu supplierId vào GRN local để khi submitForBMAvailability sẽ gửi kèm.
                grns[idx].supplierId = supplierIdNum;
                var validItems = draftForm.items.filter(function(i) { return i.skuCode && i.orderedQty > 0; }).map(function(i) {
                    return {
                        skuCode: i.skuCode,
                        skuName: i.skuName,
                        orderedQty: parseFloat(i.orderedQty || 0),
                        receivedQty: parseFloat(i.receivedQty || 0),
                        price: parseFloat(i.price || 0)
                    };
                });
                if (validItems.length === 0) {
                    alert('Vui lòng chọn ít nhất một SKU hợp lệ!');
                    return;
                }
                grns[idx].supplierId = supplierIdNum;
                // Lưu text name để hiển thị UI (lấy từ option đã chọn)
                var selEl = document.getElementById('draft-supplier');
                grns[idx].supplier = selEl.options[selEl.selectedIndex] ? selEl.options[selEl.selectedIndex].text : '';
                grns[idx].supplierCode = selEl.options[selEl.selectedIndex] ? (selEl.options[selEl.selectedIndex].text.split(' — ')[0] || '') : '';
                grns[idx].expectedDate = dateVal;
                grns[idx].note = noteVal;
                grns[idx].items = validItems;
                closeDraftModal();
                renderReceipts();
                alert('Đã cập nhật phiếu mua hàng!');
            }
            // Restore default submit
            submitBtn.onclick = submitDraftGRN;
        };
    }
};

window.deleteDraftGRN = function(grnId, event) {
    if (event) event.stopPropagation();
    // Close any open dropdown
    var allMenus = document.querySelectorAll('.dropdown-menu');
    allMenus.forEach(function(m) { m.classList.remove('active'); m.dataset.open = '0'; });
    if (!confirm('Bạn có chắc chắn muốn xoá bản nháp "' + grnId + '" không?')) return;
    grns = grns.filter(function(g) { return g.id != grnId; });
    localStorage.setItem('wh_inbound_grns', JSON.stringify(grns));
    renderReceipts();
};

window.addDraftItemRow = function() {
    draftForm.items.push({ skuCode: '', skuName: '', orderedQty: 10, price: 0 });
    renderDraftRows();
};

window.removeDraftItemRow = function(index) {
    if (draftForm.items.length > 1) {
        draftForm.items.splice(index, 1);
        renderDraftRows();
    }
};

window.updateDraftRowSku = function(index, skuCode) {
    var item = skus.find(function(s) { return s.sku === skuCode; });
    draftForm.items[index].skuCode = skuCode;
    draftForm.items[index].skuName = item ? item.name : '';
    
    // Re-render only inputs names to prevent focus loss, or re-render fully
    renderDraftRows();
};

window.updateDraftRowQty = function(index, qty) {
    draftForm.items[index].orderedQty = parseInt(qty) || 0;
};

window.updateDraftRowPrice = function(index, price) {
    draftForm.items[index].price = parseFloat(price) || 0;
};

function renderDraftRows() {
    var container = document.getElementById('draftRowsContainer');
    
    // Build select options of approved SKUs
    var approvedSkus = skus.filter(function(s) { return !s.approvalStatus || s.approvalStatus === 'approved'; });
    
    var html = draftForm.items.map(function(rowItem, index) {
        var skuOptions = approvedSkus.map(function(s) {
            var selected = rowItem.skuCode === s.sku ? 'selected' : '';
            return '<option value="' + s.sku + '" ' + selected + '>' + s.sku + ' (' + s.name.substring(0, 20) + '...)</option>';
        }).join('');
        
        var selectHtml = 
            '<select class="sku-select-input" onchange="window.updateDraftRowSku(' + index + ', this.value)">' +
                '<option value="">-- Chọn SKU --</option>' +
                skuOptions +
            '</select>';

        return '<div class="draft-row">' +
            '<div>' + selectHtml + '</div>' +
            '<div><input class="form-input" style="background:#f1f5f9; border-color:var(--border);" type="text" readonly value="' + rowItem.skuName + '" placeholder="Tên sản phẩm tự điền"/></div>' +
            '<div><input class="form-input" style="text-align:right; background:#fff;" type="number" min="0" placeholder="Giá nhập..." value="' + (rowItem.price || '') + '" onchange="window.updateDraftRowPrice(' + index + ', this.value)"/></div>' +
            '<div><input class="form-input" style="text-align:center; background:#fff;" type="number" min="1" value="' + rowItem.orderedQty + '" onchange="window.updateDraftRowQty(' + index + ', this.value)"/></div>' +
            '<div style="text-align:right;"><button class="btn-del-row" onclick="window.removeDraftItemRow(' + index + ')">&times;</button></div>' +
        '</div>';
    }).join('');
    
    container.innerHTML = html;
}

window.submitDraftGRN = function() {
    var supplierInput = (document.getElementById('draft-supplier').value || '').trim();
    var dateInput = document.getElementById('draft-date').value;
    var noteInput = (document.getElementById('draft-note').value || '').trim();

    if (!supplierInput || !dateInput) {
        alert('Vui lòng chọn Nhà cung cấp và Ngày dự kiến!');
        return;
    }

    // Ràng buộc: supplierId bắt buộc phải được chọn từ dropdown (liên kết suppliers)
    var supplierId = parseInt(supplierInput, 10);
    if (!supplierId || supplierId <= 0) {
        alert('Vui lòng chọn nhà cung cấp từ danh sách quản lý (không nhập text tự do).');
        return;
    }

    var validItems = draftForm.items.filter(function(i) {
        return i.skuCode && i.orderedQty > 0;
    }).map(function(i) {
        return {
            skuCode: i.skuCode,
            skuName: i.skuName,
            orderedQty: i.orderedQty,
            receivedQty: 0,
            price: i.price || 0
        };
    });

    if (validItems.length === 0) {
        alert('Vui lòng chọn ít nhất một SKU hợp lệ với số lượng lớn hơn 0!');
        return;
    }

    var btn = document.getElementById('btnSubmitGRN');
    btn.disabled = true;
    btn.textContent = 'Đang lưu...';

    var form = document.createElement('form');
    form.method = 'POST';
    form.action = '${pageContext.request.contextPath}/warehouse/inbound';

    var fields = {
        action: 'create',
        supplierId: supplierInput,
        supplierName: supplierInput,
        expectedDate: dateInput,
        notes: noteInput || '',
        itemsJson: JSON.stringify(validItems)
    };

    for (var key in fields) {
        var input = document.createElement('input');
        input.type = 'hidden';
        input.name = key;
        input.value = fields[key];
        form.appendChild(input);
    }

    document.body.appendChild(form);
    form.submit();
};

// ─── ACTION: MARK PO AS COMPLETED (IN_PROGRESS → RECEIVED) ───
window.completeInboundOrder = function(grnId, event) {
    if (event) event.stopPropagation();
    if (!confirm('Xác nhận hoàn thành phiếu nhập này? Hàng đã được kiểm đếm đủ, không thể nhập thêm sau bước này.')) return;

    var form = document.createElement('form');
    form.method = 'POST';
    form.action = '${pageContext.request.contextPath}/warehouse/inbound';
    var actionInput = document.createElement('input');
    actionInput.type = 'hidden';
    actionInput.name = 'action';
    actionInput.value = 'complete';
    form.appendChild(actionInput);
    var idInput = document.createElement('input');
    idInput.type = 'hidden';
    idInput.name = 'inboundId';
    idInput.value = grnId;
    form.appendChild(idInput);
    document.body.appendChild(form);
    form.submit();
};

// ─── ACTION: OPEN CREATE RECEIPT MODAL FROM A PO ────────────
window.openCreateReceiptFromPo = function(grnId, event) {
    if (event) event.stopPropagation();
    var grn = grns.find(function(g) { return g.id == grnId; });
    if (!grn) return;

    // Auto-select this PO in the modal
    openReceiptModal(function() {
        var select = document.getElementById('receipt-po-select');
        select.value = grnId;
        // Trigger change to populate detail
        var evt = document.createEvent('HTMLEvents');
        evt.initEvent('change', true, true);
        select.dispatchEvent(evt);
    });
};

window.submitForBMAvailability = function(grnId, event) {
    if (event) event.stopPropagation();
    var grn = grns.find(function(g) { return g.id == grnId; });
    if (grn) {
        // Remove from local storage drafts to prevent duplicate on reload
        grns = grns.filter(function(g) { return g.id != grnId; });
        localStorage.setItem('wh_inbound_grns', JSON.stringify(grns));

        var form = document.createElement('form');
        form.method = 'POST';
        form.action = '${pageContext.request.contextPath}/warehouse/inbound';

        var actionInput = document.createElement('input');
        actionInput.type = 'hidden';
        actionInput.name = 'action';
        actionInput.value = 'create';
        form.appendChild(actionInput);

        var supplierInput = document.createElement('input');
        supplierInput.type = 'hidden';
        supplierInput.name = 'supplierName';
        supplierInput.value = grn.supplier || '';
        form.appendChild(supplierInput);

        // Nếu GRN local draft có lưu supplierId, gửi kèm để áp dụng ràng buộc FK.
        if (grn.supplierId) {
            var supIdInput = document.createElement('input');
            supIdInput.type = 'hidden';
            supIdInput.name = 'supplierId';
            supIdInput.value = grn.supplierId;
            form.appendChild(supIdInput);
        }

        var whInput = document.createElement('input');
        whInput.type = 'hidden';
        whInput.name = 'warehouseId';
        whInput.value = window.WMS_USER.myWarehouseId || 1;
        form.appendChild(whInput);

        var dateInput = document.createElement('input');
        dateInput.type = 'hidden';
        dateInput.name = 'expectedDate';
        dateInput.value = grn.expectedDate || '';
        form.appendChild(dateInput);

        var noteInput = document.createElement('input');
        noteInput.type = 'hidden';
        noteInput.name = 'notes';
        noteInput.value = grn.note || '';
        form.appendChild(noteInput);

        var itemsInput = document.createElement('input');
        itemsInput.type = 'hidden';
        itemsInput.name = 'itemsJson';
        itemsInput.value = JSON.stringify(grn.items);
        form.appendChild(itemsInput);

        document.body.appendChild(form);
        form.submit();
    }
};

window.cancelDraftGRN = function(grnId, event) {
    if (event) event.stopPropagation();
    if (confirm('Bạn có chắc chắn muốn hủy bản nháp phiếu nhập này không?')) {
        var grn = grns.find(function(g) { return g.id == grnId; });
        if (grn) {
            grn.status = 'cancelled';
            grn.isLocked = true;
            renderReceipts();
        }
    }
};

// ─── CREATE RECEIPT MODAL (TẠO PHIẾU NHẬP KHO TỪ PO ĐÃ MUA) ───
var receiptOverlay = document.getElementById('receiptModalOverlay');

window.openReceiptModal = function(afterShow) {
    // Populate select với các phiếu mua hàng có thể nhận: PENDING (chưa mua), PURCHASED (đã mua)
    var select = document.getElementById('receipt-po-select');
    var receivable = grns.filter(function(g) { return g.status === 'pending' || g.status === 'purchased'; });
    var html = '<option value="">— Chọn phiếu mua hàng —</option>';
    receivable.forEach(function(g) {
        var label = (g.inboundCode || g.id) + ' — ' + g.supplier + ' (' + (g.items ? g.items.length : 0) + ' SKU)';
        html += '<option value="' + g.id + '">' + escapeHtml(label) + '</option>';
    });
    select.innerHTML = html;

    // Show/hide empty message
    var emptyMsg = document.getElementById('receiptEmptyMsg');
    if (receivable.length === 0) {
        emptyMsg.style.display = 'block';
    } else {
        emptyMsg.style.display = 'none';
    }

    // Reset detail step
    document.getElementById('receiptSelectStep').style.display = 'block';
    document.getElementById('receiptDetailStep').style.display = 'none';
    document.getElementById('btnSubmitReceipt').disabled = true;

    receiptOverlay.classList.add('active');

    if (typeof afterShow === 'function') {
        afterShow();
    }
};

window.closeReceiptModal = function() {
    if (!receiptOverlay) return;
    receiptOverlay.classList.remove('active');
};

// Change handler trên dropdown chọn PO
(function() {
    var sel = document.getElementById('receipt-po-select');
    if (!sel) return;
    sel.addEventListener('change', function() {
        var poId = this.value;
        var detailStep = document.getElementById('receiptDetailStep');
        var btn = document.getElementById('btnSubmitReceipt');
        if (!poId) {
            detailStep.style.display = 'none';
            btn.disabled = true;
            return;
        }
        var po = grns.find(function(g) { return String(g.id) === String(poId); });
        if (!po) return;

        // Auto fill từ PO
        document.getElementById('receipt-po-id').value = poId;
        document.getElementById('receipt-supplier-name').value = po.supplier || '';
        document.getElementById('receipt-note').value = po.note || '';
        // Header fields
        var zoneSelect = document.getElementById('receipt-zone');
        if (zoneSelect) zoneSelect.value = po.zoneId || '';
        var deliveryPersonInput = document.getElementById('receipt-delivery-person');
        if (deliveryPersonInput) deliveryPersonInput.value = po.deliveryPerson || '';
        var receivedDateInput = document.getElementById('receipt-received-date');
        if (receivedDateInput) receivedDateInput.value = po.receivedDate || new Date().toISOString().split('T')[0];

        // Render items table
        var tbody = document.getElementById('receiptItemsTableBody');
        var rowsHtml = po.items.map(function(item, idx) {
            var skuId = 'rcv-' + poId + '-' + (item.productId || item.skuCode.replace(/[^a-zA-Z0-9]/g, '_'));
            var reasonOptions = ['— Chọn lý do —','Hàng móp méo','Sai màu / Sai size','Hết hạn sử dụng','Hỏng do vận chuyển','Sai sản phẩm','Khác'].map(function(r) {
                return '<option value="' + esc(r) + '">' + esc(r) + '</option>';
            }).join('');
            return '<tr>' +
                '<td style="white-space:nowrap;"><span style="font-family:monospace; font-size:11px; color:rgba(16,55,92,0.6);">' + esc(item.skuCode) + '</span></td>' +
                '<td><span style="font-weight:600; color:var(--navy);">' + esc(item.skuName) + '</span></td>' +
                '<td style="text-align:right; font-weight:600; color:var(--navy);">' + item.orderedQty + '</td>' +
                '<td style="text-align:right;">' +
                    '<input type="hidden" name="productId" value="' + item.productId + '"/>' +
                    '<input type="hidden" name="unitCost" value="' + (item.price || 0) + '"/>' +
                    '<input class="price-input" style="width:72px; padding:4px 6px; border:1px solid #bfdbfe; border-radius:4px; text-align:center; font-size:12px; font-weight:700; color:#1d4ed8; background:#eff6ff;" type="number" name="receivedQty" min="0" max="' + item.orderedQty + '" value="' + item.orderedQty + '" data-sku-id="' + skuId + '" data-row-idx="' + idx + '" data-ordered="' + item.orderedQty + '" onchange="window.syncReceiptRow(this)"/>' +
                '</td>' +
                '<td style="text-align:right;">' +
                    '<input class="price-input" style="width:72px; padding:4px 6px; border:1px solid #a7f3d0; border-radius:4px; text-align:center; font-size:12px; font-weight:700; color:#059669; background:#f0fdf4;" type="number" name="acceptedQty" min="0" max="' + item.orderedQty + '" value="' + item.orderedQty + '" data-sku-id="' + skuId + '" data-row-idx="' + idx + '" onchange="window.syncReceiptRow(this)"/>' +
                '</td>' +
                '<td style="text-align:right;">' +
                    '<input class="price-input" style="width:72px; padding:4px 6px; border:1px solid #fde68a; border-radius:4px; text-align:center; font-size:12px; font-weight:700; color:#b45309; background:#fffbeb;" type="number" name="rejectedQty" min="0" max="' + item.orderedQty + '" value="0" data-sku-id="' + skuId + '" data-row-idx="' + idx + '" data-ordered="' + item.orderedQty + '" onchange="window.syncReceiptRow(this)"/>' +
                '</td>' +
                '<td>' +
                    '<select style="width:100%; padding:4px 6px; border:1px solid rgba(16,55,92,0.2); border-radius:4px; font-size:11px; color:var(--navy); background:#fff;" name="rejectReason">' +
                        reasonOptions +
                    '</select>' +
                '</td>' +
            '</tr>';
        }).join('');
        tbody.innerHTML = rowsHtml;

        detailStep.style.display = 'block';
        btn.disabled = false;
    });
})();

/**
 * Đồng bộ 3 trường qty trên 1 dòng SKU:
 * - receivedQty: SL thực nhận (nhập tay)
 * - acceptedQty: SL chấp nhận (mặc định = receivedQty khi received thay đổi, nhưng có thể chỉnh)
 * - rejectedQty: SL trả NCC = receivedQty - acceptedQty
 * Đảm bảo accepted + rejected ≤ received, và 3 giá trị đều ≥ 0.
 */
window.syncReceiptRow = function(inputEl) {
    var skuId = inputEl.getAttribute('data-sku-id');
    if (!skuId) return;
    var ordered = parseFloat(inputEl.getAttribute('data-ordered')) || 0;
    var row = inputEl.closest('tr');
    if (!row) return;
    var receivedEl = row.querySelector('input[name="receivedQty"]');
    var acceptedEl = row.querySelector('input[name="acceptedQty"]');
    var rejectedEl = row.querySelector('input[name="rejectedQty"]');
    var reasonSel = row.querySelector('select[name="rejectReason"]');

    var received = parseFloat(receivedEl.value) || 0;
    var accepted = parseFloat(acceptedEl.value) || 0;
    var rejected = parseFloat(rejectedEl.value) || 0;

    // Clamp received không vượt quá ordered
    if (inputEl === receivedEl && received > ordered) {
        received = ordered;
        receivedEl.value = ordered;
    }
    if (inputEl === receivedEl) {
        accepted = received;
        rejected = 0;
        acceptedEl.value = accepted;
        rejectedEl.value = rejected;
        if (reasonSel) reasonSel.value = reasonSel.options[0].value;
    } else if (inputEl === acceptedEl) {
        if (accepted > received) { accepted = received; acceptedEl.value = accepted; }
        rejected = Math.max(0, received - accepted);
        rejectedEl.value = rejected;
        if (rejected > 0 && reasonSel && reasonSel.value === reasonSel.options[0].value) {
            reasonSel.value = 'Hỏng do vận chuyển';
        }
    } else if (inputEl === rejectedEl) {
        if (rejected > received) { rejected = received; rejectedEl.value = rejected; }
        accepted = Math.max(0, received - rejected);
        acceptedEl.value = accepted;
        if (rejected > 0 && reasonSel && reasonSel.value === reasonSel.options[0].value) {
            reasonSel.value = 'Hỏng do vận chuyển';
        }
    }
};

// ─── RECEIVE GOODS MODAL ───
var receiveOverlay = document.getElementById('receiveModalOverlay');
var receiveQuantities = {};

window.openReceiveModal = function(grnId, event) {
    if (event) event.stopPropagation();
    var grn = grns.find(function(g) { return g.id == grnId; });
    if (!grn) return;
    
    document.getElementById('receive-grn-id').value = grnId;
    document.getElementById('receiveModalSubtitle').textContent = (grn.inboundCode || grnId) + ' · ' + grn.supplier;
    
    receiveQuantities = {};
    
    var container = document.getElementById('receiveItemsContainer');
    var html = grn.items.map(function(item) {
        // Default to remaining ordered quantity
        var defaultReceived = item.orderedQty - item.receivedQty;
        var defaultAccepted = defaultReceived;
        var defaultRejected = 0;
        var safeId = item.skuCode.replace(/[^a-zA-Z0-9]/g, '_');

        receiveQuantities[item.skuCode] = {
            received: defaultReceived,
            accepted: defaultAccepted,
            rejected: defaultRejected
        };
        
        return '<div class="receive-item-card">' +
            '<div style="flex:1;">' +
                '<div style="font-weight:700; color:var(--navy); font-size:13px;">' + item.skuName + '</div>' +
                '<div style="font-family:monospace; color:rgba(16, 55, 92, 0.5); font-size:11px;">' + item.skuCode + '</div>' +
                '<div style="font-size:11px; color:rgba(16, 55, 92, 0.4); margin-top:2px;">Số lượng đặt: <strong style="color:var(--navy);">' + item.orderedQty + '</strong> (Đã nhận: ' + item.receivedQty + ')</div>' +
            '</div>' +
            '<div style="display:flex; align-items:center; gap:8px;">' +
                '<div style="display:flex;flex-direction:column;align-items:center;gap:2px;">' +
                    '<label style="font-size:10px;font-weight:700;color:rgba(16,55,92,0.55);">THỰC NHẬN</label>' +
                    '<input class="price-input" style="width:75px;" type="number" min="0" max="' + defaultReceived + '" value="' + defaultReceived + '" ' +
                        'id="recv-received-' + safeId + '" ' +
                        'onchange="window.updateReceiveQty(\'' + item.skuCode + '\', \'received\', this.value)"/>' +
                '</div>' +
                '<div style="display:flex;flex-direction:column;align-items:center;gap:2px;">' +
                    '<label style="font-size:10px;font-weight:700;color:#10b981;">CHẤP NHẬN</label>' +
                    '<input class="price-input" style="width:75px;" type="number" min="0" max="' + defaultReceived + '" value="' + defaultAccepted + '" ' +
                        'id="recv-accepted-' + safeId + '" ' +
                        'onchange="window.updateReceiveQty(\'' + item.skuCode + '\', \'accepted\', this.value)"/>' +
                '</div>' +
                '<div style="display:flex;flex-direction:column;align-items:center;gap:2px;">' +
                    '<label style="font-size:10px;font-weight:700;color:#dc2626;">TỪ CHỐI</label>' +
                    '<div id="recv-rejected-' + safeId + '" style="width:75px;padding:6px 8px;background:#fef2f2;border:1px solid #fca5a5;border-radius:6px;text-align:center;font-size:13px;font-weight:600;color:#dc2626;">' + defaultRejected + '</div>' +
                '</div>' +
            '</div>' +
        '</div>';
    }).join('');
    
    container.innerHTML = html;
    receiveOverlay.classList.add('active');
};

window.updateReceiveQty = function(skuCode, field, value) {
    var safeId = skuCode.replace(/[^a-zA-Z0-9]/g, '_');
    var num = parseInt(value) || 0;

    if (!receiveQuantities[skuCode]) {
        receiveQuantities[skuCode] = { received: 0, accepted: 0, rejected: 0 };
    }
    receiveQuantities[skuCode][field] = num;

    var maxAccepted = receiveQuantities[skuCode].received;
    if (field === 'received') {
        // Adjust accepted/rejected accordingly
        var accepted = parseInt(document.getElementById('recv-accepted-' + safeId).value) || 0;
        if (accepted > num) {
            receiveQuantities[skuCode].accepted = num;
            document.getElementById('recv-accepted-' + safeId).value = num;
            accepted = num;
        }
        receiveQuantities[skuCode].rejected = 0;
        document.getElementById('recv-rejected-' + safeId).textContent = 0;
    } else if (field === 'accepted') {
        var received = receiveQuantities[skuCode].received;
        var rejected = Math.max(0, received - num);
        receiveQuantities[skuCode].rejected = rejected;
        document.getElementById('recv-rejected-' + safeId).textContent = rejected;
        if (num > received) {
            receiveQuantities[skuCode].accepted = received;
            document.getElementById('recv-accepted-' + safeId).value = received;
        }
    }

    // Update rejected display color
    var rejected = receiveQuantities[skuCode].rejected;
    var rejectedDiv = document.getElementById('recv-rejected-' + safeId);
    if (rejectedDiv) {
        rejectedDiv.style.color = rejected > 0 ? '#dc2626' : '#6b7280';
    }
};

window.closeReceiveModal = function() {
    receiveOverlay.classList.remove('active');
};

window.closeReceiveDBModal = function() {
    document.getElementById('receiveDBModal').classList.remove('active');
};

window.submitConfirmReceive = function() {
    var grnId = document.getElementById('receive-grn-id').value;
    var grn = grns.find(function(g) { return g.id == grnId; });
    if (!grn) return;
    
    var now = new Date();
    var timeStr = now.getFullYear() + '-' +
                       padZero(now.getMonth()+1) + '-' +
                       padZero(now.getDate()) + ' ' +
                       padZero(now.getHours()) + ':' +
                       padZero(now.getMinutes());

    // Update quantities on items
    grn.items.forEach(function(item) {
        var qtyData = receiveQuantities[item.skuCode] || { received: 0, accepted: 0, rejected: 0 };
        var addedReceived = qtyData.received;
        var addedAccepted = qtyData.accepted;
        var addedRejected = qtyData.rejected;

        item.receivedQty = (item.receivedQty || 0) + addedReceived;
        item.acceptedQty = (item.acceptedQty || 0) + addedAccepted;
        item.rejectedQty = (item.rejectedQty || 0) + addedRejected;

        if (addedAccepted > 0) {
            // Only accepted quantity goes to stock
            logInventoryLedger(item.skuCode, 'inbound', addedAccepted, grnId, 'GRN', 'Nhập thực tế từ ' + grn.supplier);
            updateMasterSkuQty(item.skuCode, addedAccepted);
        }
    });
    
    // Check if fully received
    var allCompleted = grn.items.every(function(item) {
        return item.receivedQty >= item.orderedQty;
    });
    
    grn.status = allCompleted ? 'completed' : 'in_progress';
    grn.receivedBy = window.WMS_USER.fullName || 'Nhân viên kho';
    
    closeReceiveModal();
    renderReceipts();
    alert('Xác nhận nhập kho phiếu ' + grnId + ' thành công!');
};

function updateMasterSkuQty(skuCode, addedQty) {
    var currentSKUs = safeJsonParse(localStorage.getItem('wms_skus'), []);
    var index = currentSKUs.findIndex(function(s) { return s.sku === skuCode; });
    if (index > -1) {
        currentSKUs[index].qtyOnHand = (currentSKUs[index].qtyOnHand || 0) + addedQty;
        currentSKUs[index].lastUpdated = new Date().toISOString().slice(0, 16).replace("T", " ");
        currentSKUs[index].updatedBy = window.WMS_USER.fullName || 'Nhân viên kho';
        localStorage.setItem('wms_skus', JSON.stringify(currentSKUs));
        skus = currentSKUs; // sync local variable
    }
}

// ─── HELPERS ───
function logInventoryLedger(sku, type, quantity, referenceId, referenceType, notes) {
    var ledgerStr = localStorage.getItem('wh_inventory_ledger');
    var ledger = safeJsonParse(ledgerStr, []);

    var entry = {
        id: 'LEG-' + Date.now() + '-' + Math.random().toString(36).substring(2, 9),
        sku: sku,
        type: type,
        quantity: quantity,
        warehouseId: 'WH001',
        zone: '',
        location: '',
        referenceId: referenceId,
        referenceType: referenceType,
        notes: notes,
        createdAt: new Date().toISOString(),
        createdBy: window.WMS_USER.fullName || 'Nhân viên kho'
    };

    ledger.push(entry);
    localStorage.setItem('wh_inventory_ledger', JSON.stringify(ledger));
}

// ─── DETAIL MODAL VIEW ───
var detailOverlay = document.getElementById('detailModalOverlay');

window.openDetailModal = function(grnId, event) {
    if (event) event.stopPropagation();
    var grn = grns.find(function(g) { return g.id == grnId; });
    if (!grn) return;

    document.getElementById('detailModalSubtitle').textContent = grn.inboundCode || grn.id;
    document.getElementById('detail-supplier-code').textContent = grn.supplierCode || '—';
    document.getElementById('detail-supplier').textContent      = grn.supplier || '—';
    document.getElementById('detail-warehouse').textContent     = grn.warehouseName || '—';
    document.getElementById('detail-payment-terms').textContent = grn.paymentTerms || '—';
    document.getElementById('detail-contact').textContent       = grn.supplierContact || '—';
    document.getElementById('detail-phone').textContent         = grn.supplierPhone || '—';
    document.getElementById('detail-email').textContent         = grn.supplierEmail || '—';
    document.getElementById('detail-address').textContent      = grn.supplierAddress || '—';
    document.getElementById('detail-created-at').textContent    = grn.createdAt || '—';
    document.getElementById('detail-expected-date').textContent = grn.expectedDate || '—';

    // Status badge
    var sc = getStatusConfig(grn.status);
    document.getElementById('detail-status-badge').innerHTML =
        '<span class="pill-badge ' + grn.status + '"><span class="pill-badge__dot"></span>' + sc.label + '</span>';

    // Notes block
    var notesBox = document.getElementById('detail-notes-box');
    if (grn.note) {
        document.getElementById('detail-note-content').textContent = grn.note;
        notesBox.style.display = 'block';
    } else {
        notesBox.style.display = 'none';
    }

    // Items table + footer totals
    var tbody = document.getElementById('detailItemsTableBody');
    var totalItems = grn.items.length;
    var totalQty = 0;
    var grandTotal = 0;

    var html = grn.items.map(function(item) {
        var priceHtml = item.price ? item.price.toLocaleString('vi-VN') + ' đ' : '—';
        var ordered = parseFloat(item.orderedQty || 0);
        var lineTotal = (item.price && ordered > 0) ? (item.price * ordered) : 0;
        totalQty += ordered;
        grandTotal += lineTotal;
        return '<tr>' +
            '<td style="white-space:nowrap;"><span style="font-family:monospace; font-size:11px; color:rgba(16,55,92,0.6);">' + escapeHtml(item.skuCode) + '</span></td>' +
            '<td><span style="font-weight:600; color:var(--navy);">' + escapeHtml(item.skuName) + '</span></td>' +
            '<td style="text-align:right; font-weight:600; color:var(--navy); white-space:nowrap;">' + priceHtml + '</td>' +
            '<td style="text-align:right; font-weight:600; white-space:nowrap;">' + ordered + '</td>' +
            '<td style="text-align:right; font-weight:700; color:var(--navy); white-space:nowrap; padding-right:16px;">' + (lineTotal > 0 ? lineTotal.toLocaleString('vi-VN') + ' đ' : '—') + '</td>' +
        '</tr>';
    }).join('');
    tbody.innerHTML = html;

    document.getElementById('detail-total-items').textContent = totalItems;
    document.getElementById('detail-total-qty').textContent   = totalQty;
    document.getElementById('detail-grand-total').textContent  = grandTotal > 0
        ? grandTotal.toLocaleString('vi-VN') + ' đ'
        : '—';

    detailOverlay.classList.add('active');
};

window.closeDetailModal = function() {
    detailOverlay.classList.remove('active');
};

// Close detail and other overlays when clicking background
[draftOverlay, receiveOverlay, detailOverlay, receiptOverlay].forEach(function(ov) {
    if (ov) {
        ov.addEventListener('click', function(e) {
            if (e.target === ov) {
                ov.classList.remove('active');
            }
        });
    }
});

// ─── HELPERS ───
function padZero(n) { return n < 10 ? '0' + n : n; }

// ─── INIT ───
renderReceipts();

// ─── DB-SIDE INBOUND TABLE (from WarehouseInboundServlet) ───
function dbStatusLabel(status) {
    var m = {
        'PENDING':    { label: 'Chờ nhập',     cls: 'pending' },
        'PURCHASED':  { label: 'Đã mua',      cls: 'purchased' },
        'IN_PROGRESS':{ label: 'Đang nhập',   cls: 'confirmed' },
        'CONFIRMED':  { label: 'Đã xác nhận', cls: 'confirmed' },
        'RECEIVED':   { label: 'Đã nhập',     cls: 'received' },
        'CANCELLED':  { label: 'Đã hủy',      cls: 'cancelled' }
    };
    var c = m[status] || { label: status, cls: 'pending' };
    return '<span class="status-pill ' + c.cls + '"><span class="status-pill__dot"></span>' + c.label + '</span>';
}

// DB Inbound counts (for filter tabs)
function dbCounts() {
    return {
        all:         grns.length,
        pending:     grns.filter(function(o){ return o.status === 'pending'; }).length,
        purchased:   grns.filter(function(o){ return o.status === 'purchased'; }).length,
        in_progress: grns.filter(function(o){ return o.status === 'in_progress'; }).length,
        completed:   grns.filter(function(o){ return o.status === 'completed'; }).length,
        cancelled:   grns.filter(function(o){ return o.status === 'cancelled'; }).length
    };
}

// Update filter tab counts if elements exist
var counts = dbCounts();
var dbCountAll = document.getElementById('db-count-all');
if (dbCountAll) {
    dbCountAll.textContent = counts.all;
    document.getElementById('db-count-pending').textContent = counts.pending;
    document.getElementById('db-count-in_progress').textContent = counts.in_progress;
    document.getElementById('db-count-completed').textContent = counts.completed;
    document.getElementById('db-count-cancelled').textContent = counts.cancelled;
}

function esc(v) {
    if (v == null) return '';
    return String(v).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}

var tabs = document.getElementById('dbFilterTabs');
if (tabs) {
    tabs.addEventListener('click', function(e) {
        var btn = e.target.closest('.db-filter-btn');
        if (!btn) return;
        activeStatusTab = btn.dataset.filter;
        tabs.querySelectorAll('.db-filter-btn').forEach(function(b) { b.classList.remove('active'); });
        btn.classList.add('active');
        renderReceipts();
    });
}

window.openCreatePOModal = function() {
    var modal = document.getElementById('createPOModal');
    modal.classList.add('active');
    // Auto-select current warehouse + populate zone dropdown
    var whSelect = document.getElementById('po-warehouse');
    if (whSelect && window.myWarehouseId) {
        whSelect.value = window.myWarehouseId;
        // Trigger zone refresh if zone population is dynamic
        var evt = new Event('change');
        whSelect.dispatchEvent(evt);
    }
};
window.closeCreatePOModal = function() {
    document.getElementById('createPOModal').classList.remove('active');
};

window.dbOpenReceiveModal = function(id, code, event) {
    if (event) event.stopPropagation();
    var grn = grns.find(function(g) { return g.id == id; });
    if (!grn) return;

    document.getElementById('receiveDB-inboundId').value = id;
    document.getElementById('receiveDB-subtitle').textContent = 'Mã phiếu nhập kho: ' + code;

    // Auto-fill header fields from existing GRN data
    var zoneSelect = document.getElementById('receiveDB-zone');
    if (zoneSelect) zoneSelect.value = grn.zoneId || '';
    var deliveryPersonInput = document.getElementById('receiveDB-deliveryPerson');
    if (deliveryPersonInput) deliveryPersonInput.value = grn.deliveryPerson || '';
    var receivedDateInput = document.getElementById('receiveDB-receivedDate');
    if (receivedDateInput) receivedDateInput.value = grn.receivedDate || new Date().toISOString().split('T')[0];

    var container = document.getElementById('receiveDBItemsContainer');
    var REJECT_REASONS = ['— Chọn lý do —','Hàng móp méo','Sai màu / Sai size','Hết hạn sử dụng','Hỏng do vận chuyển','Sai sản phẩm','Khác'];
    var html = grn.items.map(function(item, idx) {
        var remaining = item.orderedQty - item.receivedQty;
        if (remaining < 0) remaining = 0;
        var currentReason = item.rejectReason || '';
        var reasonOptions = REJECT_REASONS.map(function(r) {
            return '<option value="' + esc(r) + '"' + (r === currentReason ? ' selected' : '') + '>' + esc(r) + '</option>';
        }).join('');
        var rowBg = idx % 2 === 0 ? '#fff' : '#f8fafc';
        return '<tr style="background:' + rowBg + ';">' +
            '<td style="padding:8px 10px; border-bottom:1px solid rgba(16,55,92,0.12);">' +
                '<div style="font-weight:700; font-size:12px; color:var(--navy);">' + esc(item.skuName) + '</div>' +
                '<div style="font-family:monospace; color:rgba(16,55,92,0.45); font-size:10px;">' + esc(item.skuCode) + '</div>' +
            '</td>' +
            '<td style="padding:8px 6px; text-align:center; font-weight:700; font-size:12px; color:var(--navy); border-bottom:1px solid rgba(16,55,92,0.12);">' + item.orderedQty + '</td>' +
            '<td style="padding:6px; text-align:center; border-bottom:1px solid rgba(16,55,92,0.12);">' +
                '<input type="hidden" name="productId" value="' + item.productId + '"/>' +
                '<input type="hidden" name="unitCost" value="' + (item.price || 0) + '"/>' +
                '<input style="width:64px; padding:4px 6px; border:1px solid #bfdbfe; border-radius:4px; text-align:center; font-size:12px; font-weight:700; color:#1d4ed8; background:#eff6ff;" type="number" name="receivedQty" min="0" value="' + remaining + '" data-row-idx="' + idx + '" data-remaining="' + remaining + '" onchange="window.syncDbReceiptRow(this)"/>' +
            '</td>' +
            '<td style="padding:6px; text-align:center; border-bottom:1px solid rgba(16,55,92,0.12);">' +
                '<input style="width:64px; padding:4px 6px; border:1px solid #a7f3d0; border-radius:4px; text-align:center; font-size:12px; font-weight:700; color:#059669; background:#f0fdf4;" type="number" name="acceptedQty" min="0" value="' + remaining + '" data-row-idx="' + idx + '" onchange="window.syncDbReceiptRow(this)"/>' +
            '</td>' +
            '<td style="padding:6px; text-align:center; border-bottom:1px solid rgba(16,55,92,0.12);">' +
                '<input style="width:64px; padding:4px 6px; border:1px solid #fde68a; border-radius:4px; text-align:center; font-size:12px; font-weight:700; color:#b45309; background:#fffbeb;" type="number" name="rejectedQty" min="0" value="0" data-row-idx="' + idx + '" data-remaining="' + remaining + '" onchange="window.syncDbReceiptRow(this)"/>' +
            '</td>' +
            '<td style="padding:6px; border-bottom:1px solid rgba(16,55,92,0.12);">' +
                '<select style="width:100%; padding:4px 6px; border:1px solid rgba(16,55,92,0.20); border-radius:4px; font-size:11px; color:var(--navy); background:#fff;" name="rejectReason">' +
                    reasonOptions +
                '</select>' +
            '</td>' +
        '</tr>';
    }).join('');

    container.innerHTML = html;
    document.getElementById('receiveDBModal').classList.add('active');
};

/**
 * Đồng bộ 3 trường qty: received = accepted + rejected.
 * Khi accepted thay đổi → rejected tự tính; tự chọn lý do nếu có trả hàng.
 */
window.syncDbReceiptRow = function(inputEl) {
    var row = inputEl.closest('tr');
    if (!row) {
        row = inputEl.closest('.receive-item-card');
    }
    if (!row) return;
    var receivedEl = row.querySelector('input[name="receivedQty"]');
    var acceptedEl = row.querySelector('input[name="acceptedQty"]');
    var rejectedEl = row.querySelector('input[name="rejectedQty"]');
    var reasonSel = row.querySelector('select[name="rejectReason"]');
    if (!receivedEl || !acceptedEl || !rejectedEl) return;

    var received = parseFloat(receivedEl.value) || 0;
    var accepted = parseFloat(acceptedEl.value) || 0;
    var rejected = parseFloat(rejectedEl.value) || 0;

    if (inputEl === receivedEl) {
        accepted = received;
        rejected = 0;
        acceptedEl.value = accepted;
        rejectedEl.value = rejected;
        if (reasonSel) reasonSel.value = reasonSel.options[0].value;
    } else if (inputEl === acceptedEl) {
        if (accepted > received) { accepted = received; acceptedEl.value = accepted; }
        rejected = Math.max(0, received - accepted);
        rejectedEl.value = rejected;
        if (rejected > 0 && reasonSel && reasonSel.value === reasonSel.options[0].value) {
            reasonSel.value = 'Hỏng do vận chuyển';
        }
    } else if (inputEl === rejectedEl) {
        if (rejected > received) { rejected = received; rejectedEl.value = rejected; }
        accepted = Math.max(0, received - rejected);
        acceptedEl.value = accepted;
        if (rejected > 0 && reasonSel && reasonSel.value === reasonSel.options[0].value) {
            reasonSel.value = 'Hỏng do vận chuyển';
        }
    }
};

['createPOModal', 'receiveDBModal', 'receiveModalOverlay'].forEach(function(id) {
    var el = document.getElementById(id);
    if (el) {
        el.addEventListener('click', function(e) {
            if (e.target === el) {
                el.classList.remove('active');
            }
        });
    }
});

// renderDbTable() removed since it is not defined and merged into renderReceipts()

var createBtn = document.getElementById('btnCreatePOTrigger');
if (createBtn) {
    createBtn.addEventListener('click', function() {
        openDraftModal('create');
    });
}

var createReceiptBtn = document.getElementById('btnCreateReceiptTrigger');
if (createReceiptBtn) {
    createReceiptBtn.addEventListener('click', function() {
        openReceiptModal();
    });
}

// Auto-open create modal if action=create parameter is found
var params = new URLSearchParams(window.location.search);
if (params.get('action') === 'create') {
    var prefilledSku = params.get('sku') || '';
    setTimeout(function() {
        if (typeof openDraftModal === 'function') {
            openDraftModal('create');
            if (prefilledSku && draftForm && draftForm.items && draftForm.items[0]) {
                var foundItem = skus.find(function(s) { return s.sku === prefilledSku; });
                draftForm.items[0].skuCode = prefilledSku;
                draftForm.items[0].skuName = foundItem ? foundItem.name : '';
                if (typeof renderDraftRows === 'function') renderDraftRows();
            }
        }
    }, 300);
}
})();

</script>
