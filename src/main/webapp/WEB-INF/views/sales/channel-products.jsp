<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" isELIgnored="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>

<%-- Pricing warning chip — lãi ít / hoà vốn / bán lỗ --%>
<style>
.price-warn-chip {
    display: inline-block;
    padding: 2px 8px;
    border-radius: 999px;
    font-size: 11px;
    font-weight: 700;
    letter-spacing: 0.3px;
}
.price-warn-chip--low {
    background: rgba(234, 179, 8, 0.15);
    color: #b45309;
    border: 1px solid rgba(234, 179, 8, 0.35);
}
.price-warn-chip--breakeven {
    background: rgba(249, 115, 22, 0.12);
    color: #c2410c;
    border: 1px solid rgba(249, 115, 22, 0.30);
}
.price-warn-chip--loss {
    background: rgba(220, 38, 38, 0.12);
    color: #dc2626;
    border: 1px solid rgba(220, 38, 38, 0.30);
}
</style>

<%-- ══════════════════════════════════════════════════════════════════
     Sales Staff — Quản Lý Sản Phẩm Theo Kênh & Cấu Hình Giá Bán
     JSP port of React: ChannelProducts.tsx & PricingConfiguration.tsx
     All logic is pure vanilla JS — no hardcoded data, no seed data.

     CSS (sales--channel-products.css) loaded in <head> via sales-layout.jsp
     based on currentPage='sales-channel-products'.
     ══════════════════════════════════════════════════════════════════ --%>

<%-- ── SUB-TABS NAVIGATION BAR ── --%>
<div class="cp-tab-bar">
    <button class="cp-tab-btn active" id="tabProductsBtn" onclick="switchMainTab('products')">Sản phẩm theo kênh</button>
    <button class="cp-tab-btn" id="tabChannelsBtn" onclick="switchMainTab('channels')">Cấu hình SL tồn đệm</button>
</div>

<%-- ══════════════════════════════════════════════════════════════════
     TAB 1: SẢN PHẨM THEO KÊNH
     ══════════════════════════════════════════════════════════════════ --%>
<div id="tabProductsContent">


    <%-- Filters Toolbar --%>
    <div class="cp-filter-bar">
        <div class="cp-filter-left">
            <div class="cp-select-wrapper">
                <select class="cp-select" id="filterChannelSelect" onchange="onChannelFilterChange(this.value)">
                    <option value="all">Tat ca kenh</option>
                    <c:forEach var="ch" items="${channelsList}">
                        <option value="${ch.channelName}">${ch.channelName}</option>
                    </c:forEach>
                </select>
                <svg class="cp-select-arrow" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M19 9l-7 7-7-7" /></svg>
            </div>

            <div class="cp-search">
                <svg class="cp-search-icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"></circle><line x1="21" y1="21" x2="16.65" y2="16.65"></line></svg>
                <input type="text" class="cp-search-input" placeholder="Tìm theo tên, SKU, SKU kênh..." id="cpSearchInp" oninput="onProductSearch(this.value)" />
            </div>
        </div>

        <button class="cp-btn-push" onclick="openPublishWizard()">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><line x1="12" y1="5" x2="12" y2="19"></line><line x1="5" y1="12" x2="19" y2="12"></line></svg>
            Đẩy sản phẩm lên sàn
        </button>
    </div>

    <%-- Data table list --%>
    <div class="cp-table-card">
        <div class="cp-table-scroll">
            <table class="cp-table">
                <thead>
                    <tr>
                        <th style="width: 12%;">Mã SKU</th>
                        <th style="width: 15%;">SKU kênh</th>
                        <th style="width: 37%;">Tên sản phẩm</th>
                        <th style="width: 8%;">Kênh</th>
                        <th style="width: 9%; text-align: right;">Giá sàn</th>
                        <th style="width: 8%; text-align: right;">Tồn vật lý</th>
                        <th style="width: 8%; text-align: right;">Tồn trên sàn</th>
                        <th style="width: 10%;">Đồng bộ</th>
                        <th style="width: 8%;">Trạng thái</th>
                        <th style="width: 5%; text-align: center;">Thao tác</th>
                    </tr>
                </thead>
                <tbody id="cpProductsTableBody">
                    <%-- Populated by JS --%>
                </tbody>
            </table>
        </div>
    </div>
</div>

<%-- ══════════════════════════════════════════════════════════════════
     TAB 2: QUẢN LÝ KÊNH BÁN
     ══════════════════════════════════════════════════════════════════ --%>
<div id="tabChannelsContent" style="display:none">


    <!-- Channels List Card Grid -->
    <div id="channelsGrid" style="display: grid; grid-template-columns: repeat(auto-fill, minmax(22rem, 1fr)); gap: 1.25rem;">
        <!-- Rendered dynamically by JavaScript -->
    </div>
</div>

<%-- ══════════════════════════════════════════════════════════════════
     MODAL: ĐẨY SẢN PHẨM LÊN SÀN (3-STEP PUBLISHING WIZARD)
     ══════════════════════════════════════════════════════════════════ --%>
<div class="cp-modal-overlay" id="publishWizardOverlay" onclick="closePublishWizard()">
    <div class="cp-modal" onclick="event.stopPropagation()">
        <%-- API Sandbox integration overlay --%>
        <div class="cp-sandbox-overlay" id="sandboxOverlay">
            <div class="cp-sandbox-spinner-container">
                <div class="cp-sandbox-spinner"></div>
                <div class="cp-sandbox-spinner-lbl">API</div>
            </div>

            <h3 style="font-weight: 800; font-size: 18px; color: var(--navy);">Đồng bộ Sandbox đa kênh bán hàng</h3>
            <p style="font-size: 12px; color: rgba(16,55,92,.5); text-align: center; max-width: 400px; margin-top: 0.25rem">
                Đang đẩy sản phẩm <strong id="sandboxProductTitle">-</strong> sang môi trường sandbox của các sàn TMĐT đã chọn...
            </p>

            <div class="cp-sandbox-steps-card">
                <div class="cp-sandbox-step-row" id="sStep1">
                    <div class="cp-sandbox-step-check">
                        <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="3"><path stroke-linecap="round" stroke-linejoin="round" d="M5 13l4 4L19 7" /></svg>
                        <div class="cp-sandbox-step-check-spinner"></div>
                    </div>
                    <div>
                        <div class="cp-sandbox-step-title">Đóng gói Payload sản phẩm gốc...</div>
                        <div class="cp-sandbox-step-desc">Tổng hợp dữ liệu tên, cân nặng, kích thước từ Master SKU</div>
                    </div>
                </div>

                <div class="cp-sandbox-step-row" id="sStep2">
                    <div class="cp-sandbox-step-check">
                        <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="3"><path stroke-linecap="round" stroke-linejoin="round" d="M5 13l4 4L19 7" /></svg>
                        <div class="cp-sandbox-step-check-spinner"></div>
                    </div>
                    <div>
                        <div class="cp-sandbox-step-title">Xác thực API Gateway Sandbox...</div>
                        <div class="cp-sandbox-step-desc">Chứng thực tài khoản của Sales Staff trên môi trường thử nghiệm</div>
                    </div>
                </div>

                <div class="cp-sandbox-step-row" id="sStep3">
                    <div class="cp-sandbox-step-check">
                        <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="3"><path stroke-linecap="round" stroke-linejoin="round" d="M5 13l4 4L19 7" /></svg>
                        <div class="cp-sandbox-step-check-spinner"></div>
                    </div>
                    <div>
                        <div class="cp-sandbox-step-title">Đồng bộ hóa hình ảnh lên máy chủ sàn...</div>
                        <div class="cp-sandbox-step-desc">Truyền tải và tối ưu hóa tài nguyên ảnh mô tả</div>
                    </div>
                </div>

                <div class="cp-sandbox-step-row" id="sStep4">
                    <div class="cp-sandbox-step-check">
                        <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="3"><path stroke-linecap="round" stroke-linejoin="round" d="M5 13l4 4L19 7" /></svg>
                        <div class="cp-sandbox-step-check-spinner"></div>
                    </div>
                    <div>
                        <div class="cp-sandbox-step-title">Truyền tải cấu hình đặc thù sàn...</div>
                        <div class="cp-sandbox-step-desc">Bắn dữ liệu phân loại danh mục và biểu phí giá sàn vừa cấu hình</div>
                    </div>
                </div>

                <div class="cp-sandbox-step-row" id="sStep5">
                    <div class="cp-sandbox-step-check">
                        <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="3"><path stroke-linecap="round" stroke-linejoin="round" d="M5 13l4 4L19 7" /></svg>
                        <div class="cp-sandbox-step-check-spinner"></div>
                    </div>
                    <div>
                        <div class="cp-sandbox-step-title">Sandbox API trả về mã thành công (200 OK)...</div>
                        <div class="cp-sandbox-step-desc">Xác thực sàn TMĐT đã duyệt và khởi tạo sản phẩm thành công</div>
                    </div>
                </div>

                <div class="cp-sandbox-step-row" id="sStep6">
                    <div class="cp-sandbox-step-check">
                        <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="3"><path stroke-linecap="round" stroke-linejoin="round" d="M5 13l4 4L19 7" /></svg>
                        <div class="cp-sandbox-step-check-spinner"></div>
                    </div>
                    <div>
                        <div class="cp-sandbox-step-title">Nhận ID và ghi nhận Ánh xạ Nhiều-Nhiều...</div>
                        <div class="cp-sandbox-step-desc">Tự động ghi nhận ánh xạ SKU và ID của sàn vào cơ sở dữ liệu</div>
                    </div>
                </div>
            </div>
        </div>

        <div class="cp-modal-header">
            <div class="cp-modal-title">
                <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2.5"><path stroke-linecap="round" stroke-linejoin="round" d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4" /></svg>
                Đồng bộ sản phẩm lên kênh bán (Publish to Channel)
            </div>
            <button class="cp-modal-close" onclick="closePublishWizard()">
                <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2.5"><path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12" /></svg>
            </button>
        </div>

        <div class="cp-modal-body">
            <%-- Steps indicator --%>
            <div class="cp-wiz-steps">
                <div class="cp-wiz-step active" id="wizInd1">
                    <span class="cp-wiz-step-num" id="wizIndNum1">1</span>
                    <span>Chọn SKU gốc</span>
                </div>
                <svg class="cp-wiz-step-icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"></polyline></svg>

                <div class="cp-wiz-step" id="wizInd2">
                    <span class="cp-wiz-step-num" id="wizIndNum2">2</span>
                    <span>Chọn Sàn bán</span>
                </div>
                <svg class="cp-wiz-step-icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"></polyline></svg>

                <div class="cp-wiz-step" id="wizInd3">
                    <span class="cp-wiz-step-num" id="wizIndNum3">3</span>
                    <span>Cấu hình sàn</span>
                </div>
            </div>

            <%-- STEP 1: Chọn SKU gốc --%>
            <div id="wizStep1Content" class="cp-form-group">
                <div class="cp-form-group" style="position: relative">
                    <label class="cp-form-label">Tìm kiếm Master SKU đã duyệt (Active)</label>
                    <div style="position: relative">
                        <svg class="cp-search-icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"></circle><line x1="21" y1="21" x2="16.65" y2="16.65"></line></svg>
                        <input type="text" class="cp-search-input" style="width: 100%" placeholder="Nhập tên sản phẩm hoặc mã SKU..." id="wizStep1SearchInp" oninput="onWizMasterSearch(this.value)" />
                    </div>
                </div>

                <div class="cp-wiz-list" id="wizMasterSKUList">
                    <%-- Loaded by JS --%>
                </div>

                <div class="cp-spec-card" id="wizMasterSpecCard" style="display:none">
                    <div class="cp-spec-title">Thông tin trích xuất sản phẩm gốc (Chỉ xem)</div>
                    <div class="cp-spec-grid">
                        <div>
                            <div class="cp-spec-label">Tên SKU gốc</div>
                            <div class="cp-spec-val" id="wizSpecName">-</div>
                        </div>
                        <div>
                            <div class="cp-spec-label">Mã SKU gốc</div>
                            <div class="cp-spec-val cp-font-mono" id="wizSpecCode">-</div>
                        </div>
                        <div>
                            <div class="cp-spec-label">Khối lượng</div>
                            <div class="cp-spec-val" id="wizSpecWeight">-</div>
                        </div>
                        <div>
                            <div class="cp-spec-label">Kích thước (D×R×C)</div>
                            <div class="cp-spec-val" id="wizSpecDimensions">-</div>
                        </div>
                    </div>
                </div>
            </div>

            <%-- STEP 2: Chọn Sàn bán --%>
            <div id="wizStep2Content" style="display:none" class="cp-form-group">
                <div style="background: rgba(240, 245, 255, 0.4); border: 1px solid #E5EAF3; padding: 0.75rem; border-radius: 8px; margin-bottom: 1rem; display: flex; align-items: center; gap: 0.5rem">
                    <svg style="width:18px;height:18px;color:var(--navy)" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"></path><polyline points="3.27 6.96 12 12.01 20.73 6.96"></polyline><line x1="12" y1="22.08" x2="12" y2="12"></line></svg>
                    <div>
                        <div style="font-size:12px;font-weight:700">Sản phẩm chọn đồng bộ</div>
                        <div style="font-size:11px;color:rgba(16,55,92,.6)" id="wizStep2ProductLabel">-</div>
                    </div>
                </div>

                <label class="cp-form-label" style="font-weight:700;margin-bottom:0.75rem">Chọn sàn thương mại điện tử đích *</label>
                <div class="cp-platforms-grid" style="display: grid; grid-template-columns: repeat(2, 1fr); gap: 1rem;">
                    <div class="cp-platform-card lazada" onclick="toggleWizChannel('lazada')" id="pCardLazada">
                        <div class="cp-platform-header">
                            <span class="cp-platform-badge" style="background:#0F146D">Lazada</span>
                            <div class="cp-platform-chk">
                                <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="3.5"><path stroke-linecap="round" stroke-linejoin="round" d="M5 13l4 4L19 7" /></svg>
                            </div>
                        </div>
                        <p class="cp-platform-desc">Kết nối cổng Lazada Sandbox Open Platform. Đồng bộ giá sàn bán lẻ tùy biến.</p>
                    </div>
                    <div class="cp-platform-card website" onclick="toggleWizChannel('website')" id="pCardWebsite">
                        <div class="cp-platform-header">
                            <span class="cp-platform-badge" style="background:#EB8317">Website</span>
                            <div class="cp-platform-chk">
                                <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="3.5"><path stroke-linecap="round" stroke-linejoin="round" d="M5 13l4 4L19 7" /></svg>
                            </div>
                        </div>
                        <p class="cp-platform-desc">Đồng bộ sản phẩm, tồn kho và hình ảnh sang hệ thống Website bán hàng Storefront.</p>
                    </div>
                </div>
            </div>

            <%-- STEP 3: Cấu hình sàn --%>
            <div id="wizStep3Content" style="display:none" class="cp-form-group">
                <div style="background: rgba(240, 245, 255, 0.4); border: 1px solid #E5EAF3; padding: 0.5rem 0.75rem; border-radius: 8px; margin-bottom: 1.25rem; font-size: 12px">
                    <span style="color:rgba(16,55,92,.6)">Sản phẩm đồng bộ: </span><strong id="wizStep3ProductLabel">-</strong>
                </div>

                <div id="wizStep3ChannelsContainer">
                    <%-- Populated by JS depending on selected platforms --%>
                </div>
            </div>
        </div>

        <div class="cp-modal-footer">
            <button class="cp-btn-edit" style="width:auto;padding:0.5rem 1rem;border:1px solid #E5EAF3" onclick="closePublishWizard()">HỦY BỎ</button>
            <div style="display:flex;gap:0.5rem">
                <button class="cp-btn-edit" style="width:auto;padding:0.5rem 1rem;border:1px solid #E5EAF3;display:none" id="btnWizPrev" onclick="wizNavigate(-1)">Quay lại</button>
                <button class="cp-btn-push" id="btnWizNext" onclick="wizNavigate(1)">Tiếp tục</button>
            </div>
        </div>
    </div>
</div>

<%-- ══════════════════════════════════════════════════════════════════
     MODAL: CẤU HÌNH LẠI SẢN PHẨM KÊNH (EDIT PRODUCT MODAL)
     ══════════════════════════════════════════════════════════════════ --%>
<div class="cp-modal-overlay" id="editProductOverlay" onclick="closeEditModal()">
    <div class="cp-modal" onclick="event.stopPropagation()" style="max-width: 600px">
        <div class="cp-modal-header">
            <div class="cp-modal-title">
                <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2.5"><path stroke-linecap="round" stroke-linejoin="round" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" /></svg>
                Cấu hình lại sản phẩm kênh
            </div>
            <button class="cp-modal-close" onclick="closeEditModal()">
                <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2.5"><path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12" /></svg>
            </button>
        </div>

        <div class="cp-modal-body" style="display:flex; flex-direction:column; gap:1rem">
            <div style="background: var(--alice); border: 1px solid #E5EAF3; padding: 0.75rem; border-radius: 6px; display:flex; justify-content:space-between; font-size:13px">
                <div>
                    <div style="color:rgba(16,55,92,.5);font-size:11px">Sản phẩm gốc</div>
                    <strong id="editProductMasterName">-</strong>
                </div>
                <div style="text-align:right">
                    <div style="color:rgba(16,55,92,.5);font-size:11px">Mã SKU gốc</div>
                    <strong class="cp-font-mono" id="editProductMasterSKU">-</strong>
                </div>
            </div>

            <%-- Image uploader --%>
            <div style="border-bottom:1px solid #F0F3FA; padding-bottom:1rem">
                <label class="cp-form-label" style="font-weight:700">Hình ảnh sản phẩm trên sàn *</label>
                <div class="cp-upload-grid" id="editImagesGrid">
                    <%-- Populated by JS --%>
                </div>
                <div style="font-size: 10px; color: rgba(16,55,92,.45); margin-top: 0.5rem">
                    * Khuyên dùng ảnh vuông 800x800px. Hỗ trợ JPG/PNG. Ảnh đầu tiên được dùng làm ảnh bìa khi đẩy lên kênh.
                </div>
            </div>

            <div class="cp-form-group">
                <label class="cp-form-label" style="font-weight:700">Danh mục Lazada *</label>
                <select id="editLazadaCategory" class="cp-input-text" style="padding:0.5rem; appearance: auto;" onchange="onEditLazadaCategoryChange(this)">
                    <option value="">-- Chọn danh mục Lazada --</option>
                </select>
                <input type="hidden" id="editLazadaCategoryId" value="" />
                <div style="font-size:11px; color:rgba(16,55,92,.45); margin-top:2px">
                    Bắt buộc: Lazada yêu cầu danh mục leaf (cấp cuối). Chọn đúng danh mục phù hợp với sản phẩm.
                </div>
            </div>

            <div class="cp-form-group">
                <label class="cp-form-label" style="font-weight:700">Thương hiệu Lazada</label>
                <div style="display:flex; align-items:center; gap:0.5rem; padding:0.5rem; background:#f0f4fa; border-radius:8px; border:1px solid #dde4ee">
                    <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="#059669" stroke-width="2" style="flex-shrink:0; width:18px; height:18px"><path stroke-linecap="round" stroke-linejoin="round" d="M5 13l4 4L19 7" /></svg>
                    <span style="font-size:13px; font-weight:700; color:#1a3a5c">No Brand</span>
                    <input type="hidden" id="editLazadaBrandId" value="30768" />
            </div>

            <div class="cp-form-group">
                <label class="cp-form-label" style="font-weight:700">Giá bán trên sàn (VNĐ) *</label>
                <input type="number" class="cp-input-text" id="editProductPrice" min="0" required />
            </div>

            <div class="cp-form-group">
                <label class="cp-form-label" style="font-weight:700">Mô tả sản phẩm trên sàn</label>
                <textarea class="cp-input-text" style="height: 100px; resize: none" id="editProductDesc" required></textarea>
            </div>
        </div>

        <div class="cp-modal-footer">
            <span style="font-size:11px;color:rgba(16,55,92,.4)">* Bắt buộc</span>
            <div style="display:flex;gap:0.5rem">
                <button class="cp-btn-edit" style="width:auto;padding:0.5rem 1rem;border:1px solid #E5EAF3" onclick="closeEditModal()">Hủy</button>
                <button class="cp-btn-push" onclick="submitEditProduct()">Cập nhật</button>
            </div>
        </div>
    </div>
</div>

<%-- ── NOTIFICATION TOAST POPUP ── --%>
<div class="op-toast" id="opToast" style="position: fixed; top: 2rem; right: 2rem; background: var(--navy); color: #fff; padding: 1rem 1.5rem; border-radius: var(--radius-btn); box-shadow: 0 10px 25px rgba(0,0,0,.15); z-index: 150; font-size: 13px; font-weight: 700; display: flex; align-items: center; gap: 0.75rem; transform: translateY(-20px); opacity: 0; pointer-events: none; transition: all .25s ease-out;">
    <svg id="opToastIcon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" style="width:16px;height:16px"><circle cx="12" cy="12" r="10"></circle><polyline points="12 6 12 12 16 14"></polyline></svg>
    <span id="opToastMsg">Thông báo hệ thống</span>
</div>

<%-- Channel list is serialised to JSON by SalesChannelProductsServlet.
     Reading ${channelsJson} avoids a scriptlet loop in the JSP (MVC rule). --%>
<div id="channelsDataContainer" style="display: none;">${channelsJson}</div>
<div id="productsDataContainer" style="display: none;">${productsJson}</div>
<div id="categoriesDataContainer" style="display: none;">${categoriesJson}</div>
<%-- Channel products loaded from DB — source of truth, replaces localStorage dependency --%>
<div id="channelProductsDataContainer" style="display: none;">${channelProductsJson}</div>

<script>
// ── GLOBALS & STORE ──────────────────────────────────────────────────
let channelsList = [];
const channelsDataElem = document.getElementById("channelsDataContainer");
if (channelsDataElem) {
    try {
        channelsList = JSON.parse(channelsDataElem.textContent.trim());
    } catch (e) {
        console.error("Failed to parse channels data:", e);
    }
}
let DB_CATEGORIES = [];
const categoriesDataElem = document.getElementById("categoriesDataContainer");
if (categoriesDataElem && categoriesDataElem.textContent.trim()) {
    try {
        DB_CATEGORIES = JSON.parse(categoriesDataElem.textContent.trim()).map(function(c) {
            return { categoryId: c.id, categoryName: c.name, parentId: c.parentId };
        });
    } catch (e) {
        console.error("Failed to parse categories data:", e);
    }
}
let mainTab = "products"; // products | pricing | channels
let filterChannel = "all";
let productSearchVal = "";

let channelProducts = [];
let pricingRecords = [];
let wmsSKUs = [];

// Wizard states
let wizStep = 1; // 1 | 2 | 3
let wizSelectedMasterSKU = null;
/** Array of Channel objects from DB — must use .platform/.channelId, NOT string comparisons. */
let wizSelectedChannels = [];
let wizMasterSearchQuery = "";
let wizChannelConfigs = {
    lazada: { category: "Văn phòng phẩm > Phụ kiện học sinh", lazadaCategoryId: null, lazadaCategoryName: "", price: 0, brand: "No Brand", brandId: "30768" }
};
let wizChannelImages = { lazada: [] };
// Lazada leaf categories loaded from /sales/lazada-categories/sync on demand
let LAZADA_LEAVES = [];
let LAZADA_BRANDS = [];

// Edit states
let editTargetProductId = null;
let editImagesList = [];

// High-quality default covers to prevent placeholder look
const DYNAMIC_PRODUCT_COVERS = {
    "Vở": "https://images.unsplash.com/photo-1531346878377-a5be20888e57?auto=format&fit=crop&q=80&w=400",
    "Gương": "https://images.unsplash.com/photo-1595959183075-c1d09e7a9cf1?auto=format&fit=crop&q=80&w=400",
    "Lược": "https://images.unsplash.com/photo-1590156546746-c58d08593010?auto=format&fit=crop&q=80&w=400",
    "Bút": "https://images.unsplash.com/photo-1583485088034-697b5bc54ccd?auto=format&fit=crop&q=80&w=400",
    "Thước": "https://images.unsplash.com/photo-1513542789411-b6a5d4f31634?auto=format&fit=crop&q=80&w=400",
    "Kéo": "https://images.unsplash.com/photo-1543002588-bfa74002ed7e?auto=format&fit=crop&q=80&w=400"
};

function getDynamicCover(name) {
    for (let key in DYNAMIC_PRODUCT_COVERS) {
        if (name.toLowerCase().includes(key.toLowerCase())) {
            return DYNAMIC_PRODUCT_COVERS[key];
        }
    }
    return "https://images.unsplash.com/photo-1543002588-bfa74002ed7e?auto=format&fit=crop&q=80&w=400";
}

// ── INIT DOMContentLoaded ──────────────────────────────────────────
document.addEventListener("DOMContentLoaded", function() {
    loadData();
    renderAll();
});

function loadData() {
    // 1. Approved Master SKUs from database productsDataContainer (fallback to local storage)
    const prodElem = document.getElementById("productsDataContainer");
    if (prodElem && prodElem.textContent.trim()) {
        try {
            const parsed = JSON.parse(prodElem.textContent.trim());
            wmsSKUs = parsed.map(p => {
                return {
                    id: p.productId,
                    sku: p.sku || p.skuCode || '',
                    name: p.name || p.productName || '',
                    category: p.categoryName || '',
                    categoryId: p.categoryId || null,
                    qtyOnHand: p.qtyOnHand || 0,
                    weight: p.weight,
                    dimensions: p.dimensions,
                    macPrice:  p.macPrice || 0,
                    basePrice: p.basePrice || p.price || 0
                };
            });
        } catch(e) {
            console.error("Failed to parse database products data:", e);
            wmsSKUs = [];
        }
    } else {
        const storedWMS = localStorage.getItem("wms_skus");
        if (storedWMS) {
            try {
                wmsSKUs = JSON.parse(storedWMS).map(p => ({
                    productId: p.productId || p.id,
                    id: p.id || p.productId,
                    sku: p.sku || p.skuCode || '',
                    name: p.name || p.productName || '',
                    category: p.categoryName || p.category || '',
                    categoryId: p.categoryId || null,
                    qtyOnHand: p.qtyOnHand || 0,
                    weight: p.weight, dimensions: p.dimensions,
                    macPrice:  p.macPrice || 0,
                    basePrice: p.basePrice || p.price || 0
                }));
            } catch(e) { wmsSKUs = []; }
        } else {
            wmsSKUs = [];
        }
    }

    // 2. Channel Products — prefer DB data from server (always fresh), fall back to localStorage
    const cpServerElem = document.getElementById("channelProductsDataContainer");
    let serverCPs = [];
    if (cpServerElem && cpServerElem.textContent.trim() && cpServerElem.textContent.trim() !== '[]') {
        try { serverCPs = JSON.parse(cpServerElem.textContent.trim()); } catch(e) { serverCPs = []; }
    }
    
    // Read old cache to preserve local bufferStock values if any
    let localBufferMap = {};
    try {
        const storedCP = localStorage.getItem("channel_products_v2");
        if (storedCP) {
            JSON.parse(storedCP).forEach(function(item) {
                if (item && item.id) {
                    localBufferMap[item.id] = item.bufferStock || 0;
                }
            });
        }
    } catch(e) {}

    if (serverCPs.length > 0) {
        channelProducts = serverCPs.map(function(cp) {
            const uiId = cp.id;
            // Lookup product from WMS inventory to show accurate physical stock
            const masterProd = wmsSKUs.find(w => w.id === cp.productId || w.sku === cp.skuCode);
            const physicalStock = masterProd ? (masterProd.qtyOnHand || 0) : (cp.channelStock || 0);
            const activePrice = (cp.channelPrice && cp.channelPrice > 0) ? cp.channelPrice : (masterProd ? (masterProd.basePrice || 0) : 0);
            
            return {
                id:            uiId,
                productId:     cp.productId,
                channelId:     cp.channelId,
                masterSKU:     cp.skuCode || '',
                channelSKU:    cp.channelSkuCode || '',
                productName:   cp.productName || '',
                channel:       (cp.channelPlatform || '').toLowerCase(),
                channelName:   cp.channelName || '',
                // Description shown in the products table — prefer cp.description
                // (the value the operator typed in the wizard / saved to DB).
                // Fall back to cp.shortDescription, then leave blank so the UI
                // can prompt the operator to fill it in instead of fabricating
                // a fake template like "productName - Đồng bộ bán trên sàn LAZADA".
                description:   cp.description || cp.shortDescription || "",
                price:         activePrice,
                stock:         physicalStock,
                bufferStock:   localBufferMap[uiId] || 0,
                syncStatus:    cp.lastErrorCode ? 'failed' : 'success',
                status:        (cp.status || 'active').toLowerCase(),
                channelItemId: cp.channelItemId || '',
                lazadaSkuId:   cp.lazadaSkuId || '',
                lazadaCategoryId: cp.lazadaCategoryId || null,
                brandId:       cp.brandId || null,
                lastPushAt:    cp.lastPushAt || null
            };
        });
        // Also sync back to localStorage so other pages (order-processing) can read
        localStorage.setItem("channel_products_v2", JSON.stringify(channelProducts));
    } else {
        // Fallback: localStorage (for offline / cached sessions)
        const storedCP = localStorage.getItem("channel_products_v2");
        if (storedCP) {
            try { channelProducts = JSON.parse(storedCP); } catch(e) { channelProducts = []; }
        } else {
            channelProducts = [];
        }
    }

}

function saveData() {
    localStorage.setItem("channel_products_v2", JSON.stringify(channelProducts));
    window.dispatchEvent(new CustomEvent("ORDER_STORE_UPDATED"));
}

// ── TOAST NOTIFICATIONS ──
function showToast(msg, type = "success") {
    const toast = document.getElementById("opToast");
    const label = document.getElementById("opToastMsg");
    const icon = document.getElementById("opToastIcon");
    
    if (type === "success") {
        toast.style.background = "#059669";
        icon.innerHTML = `<path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path><polyline points="22 4 12 14.01 9 11.01"></polyline>`;
    } else {
        toast.style.background = "#dc2626";
        icon.innerHTML = `<circle cx="12" cy="12" r="10"></circle><line x1="12" y1="16" x2="12" y2="12"></line><line x1="12" y1="8" x2="12.01" y2="8"></line>`;
    }
    label.textContent = msg;
    
    toast.style.opacity = 1;
    toast.style.transform = "translateY(0)";
    setTimeout(() => {
        toast.style.opacity = 0;
        toast.style.transform = "translateY(-20px)";
    }, 4000);
}

// ── RENDER ROOT CONTROL ──────────────────────────────────────────────
function renderAll() {
    if (mainTab === "products") {
        renderProductsTab();
    } else if (mainTab === "channels") {
        renderChannelsTab();
    }
}

function switchMainTab(tab) {
    mainTab = tab;
    document.getElementById("tabProductsBtn").classList.remove("active");
    document.getElementById("tabChannelsBtn").classList.remove("active");
    
    document.getElementById("tabProductsContent").style.display = "none";
    document.getElementById("tabChannelsContent").style.display = "none";
    
    if (tab === "products") {
        document.getElementById("tabProductsBtn").classList.add("active");
        document.getElementById("tabProductsContent").style.display = "block";
    } else {
        document.getElementById("tabChannelsBtn").classList.add("active");
        document.getElementById("tabChannelsContent").style.display = "block";
    }
    renderAll();
}

function escapeHtml(str) {
    if (!str) return "";
    return str.replace(/&/g, "&amp;")
              .replace(/</g, "&lt;")
              .replace(/>/g, "&gt;")
              .replace(/"/g, "&quot;")
              .replace(/'/g, "&#039;");
}

function buildCategorySelectOptions(categories, selectedValue) {
    let html = "";
    function recurse(parentId, prefix) {
        categories.filter(function(c) {
            const p = c.parentId;
            return (parentId === null) ? (p === null || p === 0 || p === 'null') : (p == parentId);
        }).forEach(function(node) {
            const isSel = (node.categoryName === selectedValue) ? "selected" : "";
            html += '<option value="' + escapeHtml(node.categoryName) + '" ' + isSel + '>' + prefix + escapeHtml(node.categoryName) + '</option>';
            recurse(node.categoryId, prefix + '    ');
        });
    }
    recurse(null, '');
    return html;
}

function renderChannelsTab() {
    const grid = document.getElementById("channelsGrid");
    if (!grid) return;
    
    if (!channelsList || channelsList.length === 0) {
        grid.innerHTML = `
            <div style="grid-column: 1 / -1; display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 4rem 2rem; background: white; border: 1px solid #E5EAF3; border-radius: var(--radius-card); text-align: center;">
                <h4 style="color: var(--navy); font-size: 15px; font-weight: 700; margin: 0 0 0.5rem 0;">Chưa cấu hình kênh bán hàng nào</h4>
                <p style="color: rgba(16,55,92,0.45); font-size: 13px; margin: 0;">Vui lòng liên hệ Administrator để cấu hình kết nối kênh bán hàng.</p>
            </div>
        `;
        return;
    }
    
    grid.innerHTML = channelsList.map(chan => {
        const platformBadge = chan.platform === 'Lazada' 
            ? `<span style="background: rgba(16,115,230,0.1); color: #1073e6; padding: 0.25rem 0.5rem; font-size: 10px; font-weight: 800; border-radius: 4px; border: 1px solid rgba(16,115,230,0.2);">LAZADA</span>`
            : chan.platform === 'Shopee'
                ? `<span style="background: rgba(238,77,45,0.1); color: #ee4d2d; padding: 0.25rem 0.5rem; font-size: 10px; font-weight: 800; border-radius: 4px; border: 1px solid rgba(238,77,45,0.2);">SHOPEE</span>`
                : `<span style="background: rgba(0,0,0,0.08); color: #000000; padding: 0.25rem 0.5rem; font-size: 10px; font-weight: 800; border-radius: 4px; border: 1px solid rgba(0,0,0,0.15);">TIKTOK SHOP</span>`;
                
        const statusBadge = chan.active
            ? `<span style="display: inline-flex; align-items: center; gap: 0.25rem; padding: 0.125rem 0.5rem; background: #e6f7ed; color: #10b981; font-size: 11px; font-weight: 700; border-radius: 20px; border: 1px solid rgba(16,185,129,0.2);">Active</span>`
            : `<span style="display: inline-flex; align-items: center; gap: 0.25rem; padding: 0.125rem 0.5rem; background: #f3f5f8; color: rgba(16,55,92,0.4); font-size: 11px; font-weight: 700; border-radius: 20px; border: 1px solid #E5EAF3;">Inactive</span>`;

        return `
            <div style="background: white; border: 1px solid #E5EAF3; border-radius: var(--radius-card); padding: 1.5rem; display: flex; flex-direction: column; justify-content: space-between; transition: box-shadow 0.2s, transform 0.2s;">
                <div>
                    <!-- Header -->
                    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem;">
                        <div style="display: flex; align-items: center; gap: 0.75rem;">
                            \${platformBadge}
                            <h4 style="color: var(--navy); font-size: 15px; font-weight: 700; margin: 0;">\${escapeHtml(chan.channelName)}</h4>
                        </div>
                        \${statusBadge}
                    </div>

                    <!-- Config Details -->
                    <div style="font-size: 12px; color: rgba(16,55,92,0.70); margin-bottom: 1.25rem; display: flex; flex-direction: column; gap: 0.5rem;">
                        <div style="display: flex; justify-content: space-between;">
                            <span>API Endpoint:</span>
                            <span style="font-family: monospace; font-weight: 600; color: var(--navy); text-overflow: ellipsis; overflow: hidden; white-space: nowrap; max-width: 14rem;">\${escapeHtml(chan.apiUrl)}</span>
                        </div>
                        <div style="display: flex; justify-content: space-between; align-items: center;">
                            <span>Webhook Status:</span>
                            <span style="color: #10b981; font-weight: 700; display: inline-flex; align-items: center; gap: 0.25rem;">
                                <span style="display: inline-block; width: 6px; height: 6px; border-radius: 50%; background: #10b981;"></span>Live
                            </span>
                        </div>
                    </div>
                </div>

                <!-- Buffer Stock Edit Form -->
                <div style="border-top: 1px dashed #E5EAF3; padding-top: 0.75rem; display: flex; flex-direction: column; gap: 0.5rem;">
                    <label style="font-size: 12px; font-weight: 600; color: rgba(16,55,92,0.7);">Số lượng tồn đệm (Buffer Stock):</label>
                    <div style="display: flex; gap: 0.5rem; align-items: center;">
                        <input type="number" id="bufferStock_\${chan.channelId}" value="\${chan.bufferStock}" min="0" step="0.5"
                               style="flex: 1; padding: 0.5rem; background: var(--alice); border: 1px solid #E5EAF3; color: var(--navy); font-size: 13px; outline: none; border-radius: 4px;" />
                        <button type="button" onclick="updateChannelBufferStock('\${chan.channelId}')"
                                style="padding: 0.5rem 1rem; background: var(--orange); color: white; border: none; font-size: 12px; font-weight: 700; border-radius: 4px; cursor: pointer; box-shadow: 0 4px 10px rgba(235,131,23,0.15);">
                            Cập nhật
                        </button>
                    </div>
                </div>
            </div>
        `;
    }).join("");
}

function updateChannelBufferStock(channelId) {
    const bufferStockInput = document.getElementById("bufferStock_" + channelId);
    if (!bufferStockInput) return;
    const value = parseFloat(bufferStockInput.value);
    if (isNaN(value) || value < 0) {
        showToast("Vui lòng nhập số lượng tồn đệm hợp lệ!", "error");
        return;
    }

    const params = new URLSearchParams();
    params.append("action", "updateBufferStock");
    params.append("channelId", channelId);
    params.append("bufferStock", value);

    fetch('${pageContext.request.contextPath}/sales/channel-products', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: params.toString()
    })
    .then(r => r.json())
    .then(data => {
        if (data.success) {
            showToast("Đã cập nhật tồn đệm của kênh thành công!");
            const chan = channelsList.find(c => c.channelId == channelId);
            if (chan) {
                chan.bufferStock = value;
            }
            renderChannelsTab();
        } else {
            showToast("Cập nhật thất bại: " + data.message, "error");
        }
    })
    .catch(err => {
        console.error(err);
        showToast("Không thể kết nối đến server. Thử lại sau!", "error");
    });
}

// ── TAB 1: PRODUCTS LISTING & HANDLERS ────────────────────────────────
function renderProductsTab() {
    // 1. Filter products
    const filtered = channelProducts.filter(p => {
        const matchChannel = filterChannel === "all" || p.channel.toLowerCase() === filterChannel.toLowerCase();
        const matchSearch = !productSearchVal ||
            p.productName.toLowerCase().includes(productSearchVal.toLowerCase()) ||
            p.masterSKU.toLowerCase().includes(productSearchVal.toLowerCase()) ||
            p.channelSKU.toLowerCase().includes(productSearchVal.toLowerCase());
        return matchChannel && matchSearch;
    });

    // 2. Calculate Stats
    const statsTotal = filtered.length;
    const statsActive = filtered.filter(p => p.status === "active").length;
    const statsOOS = filtered.filter(p => p.stock - (p.bufferStock || 0) <= 0).length;
    const totalValVal = filtered.reduce((sum, p) => sum + (p.price * p.stock), 0);



    // 3. Render Table
    const tbody = document.getElementById("cpProductsTableBody");
    tbody.innerHTML = "";

    if (filtered.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="11" class="op-empty">
                    <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.5"><path stroke-linecap="round" stroke-linejoin="round" d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4" /></svg>
                    Chưa có sản phẩm nào được đồng bộ lên sàn. Bấm 'Đẩy sản phẩm lên sàn' để bắt đầu.
                </td>
            </tr>
        `;
        return;
    }

    filtered.forEach(p => {
        const tr = document.createElement("tr");

        // Sync connection state UI
        let syncHtml = "";
        if (p.syncStatus === "syncing") {
            syncHtml = `<span class="cp-sync-status syncing">Đang đồng bộ...</span>`;
        } else if (p.syncStatus === "failed") {
            syncHtml = `<span class="cp-sync-status failed" onclick="alert('LỖI ĐỒNG BỘ: Mất kết nối API Gateway đến sàn TMĐT hoặc Token của Shop đã hết hạn. Vui lòng kiểm tra lại cấu hình kết nối sàn ở cài đặt hệ thống.')">Lỗi kết nối</span>`;
        } else {
            syncHtml = `<span class="cp-sync-status success">Thành công</span>`;
        }

        // Status badge config
        let statusClass = "active";
        let statusLabel = "Đang bán";
        if (p.status === "out_of_stock" || p.stock === 0) {
            statusClass = "out_of_stock";
            statusLabel = "Hết hàng";
        } else if (p.status === "inactive") {
            statusClass = "inactive";
            statusLabel = "Ngừng bán";
        }

        // Channel styling color map
        const chColors = { shopee: "#EE4D2D", tiktok: "#69C9D0", lazada: "#0F146D", website: "#EB8317" };
        const chNames = { shopee: "Shopee", tiktok: "TikTok", lazada: "Lazada", website: "Website" };
        const chCol = chColors[p.channel] || "#64748b";
        const chName = chNames[p.channel] || p.channel;

        tr.innerHTML = `
            <td><span class="cp-font-mono" style="color:rgba(16, 55, 92, 0.7)">\${p.masterSKU}</span></td>
            <td><span class="cp-font-mono" style="color:var(--navy);font-weight:700">\${p.channelSKU}</span></td>
            <td>
                <div style="min-width: 220px; max-width: 320px;">
                    <div style="font-weight: 700; color: var(--navy); font-size: 13px">\${p.productName}</div>
                    <div class="cp-p-desc">\${p.description || ""}</div>
                    \${p.channelItemId ? '<div class="cp-p-id">ID Sàn: ' + p.channelItemId + '</div>' : ""}
                </div>
            </td>
            <td>
                <span class="cp-badge-channel" style="background:\${chCol}">\${chName}</span>
            </td>
            <td style="text-align: right; font-weight: 700; font-size: 13px; white-space:nowrap">\${Number(p.price).toLocaleString()}đ</td>
            <td style="text-align: right; font-weight: 600; color: \${p.stock === 0 ? "#ef4444" : "#059669"}">\${p.stock}</td>
            <td style="text-align: right; font-weight: 700; font-family: monospace; font-size: 13px">
                \${Math.max(0, p.stock - (p.bufferStock || 0))}
            </td>
            <td>\${syncHtml}</td>
            <td>
                <span class="cp-status-pill \${statusClass}">\${statusLabel}</span>
            </td>
            <td>
                <div style="display:flex; justify-content:center; gap:0.25rem">
                    <button class="cp-btn-edit" onclick="openEditModal('\${p.id}')" title="Sửa sản phẩm">
                        <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2.5"><path stroke-linecap="round" stroke-linejoin="round" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" /></svg>
                    </button>
                    <button class="cp-btn-delete" onclick="deleteChannelProduct('\${p.id}', '\${p.masterSKU}')" title="Xóa sản phẩm khỏi kênh">
                        <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2.5"><path stroke-linecap="round" stroke-linejoin="round" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" /></svg>
                    </button>
                </div>
            </td>
        `;
        tbody.appendChild(tr);
    });
}

function onChannelFilterChange(val) {
    filterChannel = val;
    renderProductsTab();
}

function onProductSearch(val) {
    productSearchVal = val;
    renderProductsTab();
}

// ── BUFFER STOCK CHANGE ────────────────────────────────────────────────
function onBufferStockInput(productId, value) {
    const nextVal = Math.max(0, parseInt(value) || 0);

    channelProducts = channelProducts.map(p => {
        if (p.id == productId) {
            return { ...p, bufferStock: nextVal };
        }
        return p;
    });
    saveData();
    renderProductsTab();
    showToast("Đã cập nhật hàng đệm an toàn. Tính năng đồng bộ tự động đang chờ tích hợp API.", "info");
}

// ── DELETE CHANNEL PRODUCT ────────────────────────────────────────────
function deleteChannelProduct(productId, masterSKU) {
    if (!confirm('Bạn có chắc muốn xóa sản phẩm "' + masterSKU + '" khỏi kênh bán hàng?\nThao tác này không thể hoàn tác.')) return;
    
    showToast("Đang gửi yêu cầu xóa sản phẩm lên Lazada...", "info");
    
    fetchJson("${pageContext.request.contextPath}/sales/channel-products", {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: "action=delete&id=" + encodeURIComponent(productId)
    }).then(res => {
        if (res.success) {
            channelProducts = channelProducts.filter(p => p.id != productId);
            saveData();
            renderProductsTab();
            showToast(res.message || 'Đã xóa sản phẩm ' + masterSKU + ' khỏi kênh bán hàng.', 'success');
        } else {
            showToast(res.message || 'Xóa sản phẩm thất bại.', 'error');
        }
    }).catch(err => {
        showToast(err.message || 'Lỗi kết nối khi xóa sản phẩm.', 'error');
    });
}

// ── MANUAL INVENTORY SYNC ──────────────────────────────────────────────
function syncInventoryToChannel() {
    showToast("Tính năng đồng bộ tồn kho tự động đang chờ tích hợp Lazada Inventory API.", "info");
}

// ── PRODUCT PUBLISH ────────────────────────────────────────────────────
function executePublishProduct() {
    console.log("[executePublishProduct] CALLED at", new Date().toISOString());
    console.log("[executePublishProduct] wizSelectedChannels:", JSON.stringify(wizSelectedChannels));

    if (wizSelectedChannels.length === 0) {
        showToast("Vui lòng chọn ít nhất một kênh bán hàng để đẩy sản phẩm.", "error");
        return;
    }

    const productId = wizSelectedMasterSKU
        ? (wizSelectedMasterSKU.productId || wizSelectedMasterSKU.id)
        : null;
    if (productId === null || productId === undefined || productId === "" || Number.isNaN(Number(productId))) {
        showToast("Không xác định được sản phẩm cần đẩy. Hãy chọn lại SKU.", "error");
        return;
    }

    const pushBtn = document.getElementById("btnWizNext");
    if (pushBtn) {
        pushBtn.disabled = true;
        pushBtn.textContent = "Đang đẩy sản phẩm...";
    }

    // Validate images and description for each selected channel
    for (const ch of wizSelectedChannels) {
        const platform = ch.platform.toLowerCase();
        const cfg = wizChannelConfigs[platform] || {};
        const imgs = wizChannelImages[platform] || [];
        
        // 1. Pending images validation
        const pending = imgs.filter(img => {
            const url = (img && typeof img === "object") ? img.url : img;
            return url && typeof url === "string" && url.startsWith("data:");
        });
        if (pending.length > 0) {
            showToast(`Vui lòng chờ ảnh của kênh \${ch.channelName} upload xong rồi thử lại.`, "error");
            if (pushBtn) { pushBtn.disabled = false; renderWizProgress(); }
            return;
        }

        // 2. Description validation
        const desc = (cfg.description || "").trim();
        if (desc.length < 5) {
            showToast(`Vui lòng nhập mô tả sản phẩm (tối thiểu 5 ký tự) cho kênh \${ch.channelName}.`, "error");
            if (pushBtn) { pushBtn.disabled = false; renderWizProgress(); }
            return;
        }
    }

    // Run parallel pushes for all selected channels
    const promises = wizSelectedChannels.map(ch => {
        const platform = ch.platform.toLowerCase();
        if (platform === "lazada") {
            return pushToLazada(ch.channelId, productId).then(res => ({ channel: ch, result: res }));
        } else if (platform === "website") {
            return pushToWebsite(ch.channelId, productId).then(res => ({ channel: ch, result: res }));
        }
        return Promise.resolve({ channel: ch, result: { success: false, message: "Kênh chưa hỗ trợ." } });
    });

    Promise.all(promises).then(outcomes => {
        let successCount = 0;
        let errors = [];
        let realLazadaItemId = null;
        let realLazadaSkuId = null;

        outcomes.forEach(o => {
            const res = o.result;
            const ch = o.channel;
            if (res.success) {
                successCount++;
                if (ch.platform.toLowerCase() === "lazada") {
                    realLazadaItemId = res.itemId;
                    realLazadaSkuId = res.skuId;
                }
            } else {
                const msg = res.message || res.code || "Lỗi không xác định";
                errors.push(`[\${ch.channelName}] \${msg}`);
            }
        });

        if (successCount === outcomes.length) {
            showToast("Đồng bộ sản phẩm lên tất cả các kênh thành công!", "success");
            finalizePublishData(realLazadaItemId, realLazadaSkuId);
        } else if (successCount > 0) {
            showToast(`Đồng bộ thành công \${successCount}/\${outcomes.length} kênh. Lỗi:\n\${errors.join('\n')}`, "warning", 8000);
            finalizePublishData(realLazadaItemId, realLazadaSkuId);
        } else {
            showToast(`Đồng bộ thất bại:\n\${errors.join('\n')}`, "error", 8000);
        }
    }).catch(err => {
        console.error("[executePublishProduct] fetch error:", err);
        showToast("Lỗi kết nối: " + err.message, "error");
    }).finally(() => {
        if (pushBtn) {
            pushBtn.disabled = false;
            renderWizProgress();
        }
    });
}

function pushToWebsite(channelId, productId) {
    const cfg = wizChannelConfigs.website || {};
    const imgs = wizChannelImages.website || [];
    const params = new URLSearchParams();
    params.set("action", "push");
    params.set("channelId", String(channelId));
    params.set("productId", String(productId));
    params.set("price", String(cfg.price || 0));
    params.set("quantity", String(cfg.quantity || 0));
    params.set("description", cfg.description || "");
    if (imgs.length > 0) {
        const urls = [];
        imgs.forEach(function(img) {
            const itemUrl = (img && typeof img === "object") ? img.url : (typeof img === "string" ? img : "");
            if (itemUrl && !itemUrl.startsWith("data:")) {
                urls.push(itemUrl);
            }
        });
        params.set("imageUrls", urls.join("|"));
    }
    return fetchJson(window.location.pathname, {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: params.toString()
    });
}

function pushToLazada(channelId, productId) {
    const cfg = wizChannelConfigs.lazada || {};
    const imgs = wizChannelImages.lazada || [];
    const params = new URLSearchParams();
    params.set("action", "push");
    params.set("channelId", String(channelId));
    params.set("productId", String(productId));
    params.set("lazadaCategoryId", cfg.lazadaCategoryId || "");
    params.set("price", String(cfg.price || 0));
    params.set("quantity", String(cfg.quantity || 0));
    params.set("description", cfg.description || "");
    params.set("shortDescription", cfg.shortDescription || "");
    params.set("brand", cfg.brand || "No brand");
    // Lazada brand_id is mandatory per Open Platform docs (brand text is deprecated).
    params.set("brandId", cfg.brandId || "");
    params.set("weight", String(cfg.weight || 0.2));
    params.set("dimensions", cfg.dimensions || "10x10x10");
    params.set("sellerSku", cfg.sellerSku || "");
    if (imgs.length > 0) {
        const urls = [];
        const base64s = [];
        imgs.forEach(function(img) {
            const itemUrl = (img && typeof img === "object") ? img.url : (typeof img === "string" ? img : "");
            const itemB64 = (img && typeof img === "object") ? (img.base64 || "") : (typeof img === "string" && img.startsWith("data:") ? img : "");
            if (itemUrl && !itemUrl.startsWith("data:")) {
                urls.push(itemUrl);
                base64s.push("");
            } else {
                urls.push("");
                base64s.push(itemB64 || "");
            }
        });
        params.set("imageUrls", urls.join("|"));
        params.set("imageBase64s", base64s.join("|"));
    }
    return fetchJson(window.location.pathname, {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: params.toString()
    });
}

// Lazada leaf category handlers
function onLazadaLeafChange(sel) {
    const id = sel.value;
    const opt = sel.options[sel.selectedIndex];
    wizChannelConfigs.lazada.lazadaCategoryId = id || null;
    wizChannelConfigs.lazada.lazadaCategoryName = opt && id ? opt.text : "";
    wizChannelConfigs.lazada.category = wizChannelConfigs.lazada.lazadaCategoryName;
}

function loadLazadaLeaves(channelId) {
    return fetchJson(window.location.pathname, {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: `action=loadLazadaLeaves&channelId=\${channelId}`
    }).then(data => {
        if (data.success) {
            LAZADA_LEAVES = data.leaves || [];
            // Auto-sync if DB is empty — no need to manual "Đồng bộ" button
            if (LAZADA_LEAVES.length === 0) {
                syncLazadaLeaves(channelId);
            }
        }
        return data;
    });
}

function syncLazadaLeaves(channelId) {
    fetchJson("${pageContext.request.contextPath}/sales/channel-products", {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: "action=syncLazadaCategories&channelId=" + channelId
    }).then(data => {
        return loadLazadaLeaves(channelId);
    }).then(() => {
        // Only re-render Step 3 if the wizard is already there
        if (wizStep === 3) {
            renderWizStep3();
        }
    }).catch(function(err) {
        console.warn("[syncLeaves] failed:", err.message);
    });
}

/**
 * Lazada brand selector — Lazada requires a numeric brand_id in the
 * product/create and /product/update payloads. Per Lazada Open Platform
 * docs, the text "brand" field is deprecated and many categories reject
 * products without a valid brand_id.
 *
 * Loads Lazada's brand list via /brand/get (proxied through the servlet
 * action=getBrands) and renders it into the wizard's brand select.
 */
function loadLazadaBrands(channelId) {
    const statusEl = document.getElementById("wizBrandStatus");
    if (statusEl) statusEl.textContent = "Đang tải danh sách brand từ Lazada…";
    return fetchJson(window.location.pathname, {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: "action=getBrands&channelId=" + channelId
    }).then(data => {
        if (!data || !data.success || !data.brandsResponse) {
            throw new Error("Không tải được danh sách brand từ Lazada.");
        }
        // Lazada's /brand/get returns: { code:"0", data: { brands: [ { brand_id, name, ... } ] } }
        const inner = (data.brandsResponse.data && data.brandsResponse.data.brands)
                   || data.brandsResponse.brands || [];
        // Cache globally so edit modal can reuse
        LAZADA_BRANDS = inner.map(function(b) {
            return { brandId: b.brand_id || b.brandId || b.id, name: b.name || b.brand_name || ("Brand " + (b.brand_id || b.brandId || b.id)) };
        });
        const sel = document.getElementById("wizBrandSelect");
        if (!sel) return data;
        const current = wizChannelConfigs.lazada.brandId || "";
        let html = '<option value="">-- Chọn brand Lazada --</option>';
        for (let i = 0; i < inner.length; i++) {
            const b = inner[i];
            const bid = b.brand_id || b.brandId || b.id;
            const bname = b.name || b.brand_name || ("Brand " + bid);
            if (!bid) continue;
            const selAttr = String(bid) === String(current) ? "selected" : "";
            html += '<option value="' + escapeHtml(String(bid)) + '" ' + selAttr + '>' + escapeHtml(bname) + ' (ID: ' + bid + ')</option>';
        }
        sel.innerHTML = html;
        if (statusEl) {
            statusEl.textContent = "Đã tải " + inner.length + " brand. Chọn 1 brand_id để đẩy lên Lazada.";
            statusEl.style.color = "#059669";
        }
        return data;
    }).catch(function(err) {
        if (statusEl) {
            statusEl.textContent = "Không tải được: " + err.message + " — Hãy nhập tên brand thủ công bên dưới.";
            statusEl.style.color = "#dc2626";
        }
        console.warn("[loadBrands] failed:", err.message);
    });
}

function onLazadaBrandChange(sel) {
    const opt = sel.options[sel.selectedIndex];
    wizChannelConfigs.lazada.brandId = sel.value || null;
    // Also store the display name so we have a fallback text "brand" if needed
    wizChannelConfigs.lazada.brand = (opt && sel.value) ? opt.text.replace(/\s*\(ID:.*\)$/, "") : wizChannelConfigs.lazada.brand;
}

/**
 * fetchJson — POST a request and parse the response as JSON safely.
 *
 * The server-side AuthFilter redirects unauthenticated requests to /login
 * (302) which the browser then follows, returning the login HTML page. The
 * old `r.json()` would throw "Unexpected token '<'" on that HTML. This
 * helper detects the HTML case and surfaces a friendly error so the caller
 * can show "Phiên đăng nhập hết hạn" instead of crashing.
 */
async function fetchJson(url, options) {
    options = options || {};
    options.headers = Object.assign(
        { "Accept": "application/json", "X-Requested-With": "XMLHttpRequest" },
        options.headers || {}
    );
    let r;
    try {
        r = await fetch(url, options);
    } catch (e) {
        throw new Error("Lỗi mạng: " + e.message);
    }
    // 401 from AuthFilter means session expired — server already returned JSON.
    if (r.status === 401) {
        showSessionExpired();
        throw new Error("Phiên đăng nhập đã hết hạn (401).");
    }
    // After fetch follows redirects, a 200 with text/html means the server
    // bounced us to the login page. Surface that explicitly.
    const ct = (r.headers.get("content-type") || "").toLowerCase();
    if (ct.indexOf("application/json") === -1) {
        let snippet = "";
        try { snippet = (await r.text()).slice(0, 120); } catch (e) {}
        throw new Error("Server trả về HTML (phiên đăng nhập có thể đã hết hạn). Status " + r.status + ". " + snippet);
    }
    try {
        return await r.json();
    } catch (e) {
        throw new Error("Phản hồi từ server không đúng định dạng. Vui lòng thử lại.");
    }
}

/** Convenience: show a "session expired" toast and offer re-login. */
function showSessionExpired() {
    showToast("Phiên đăng nhập đã hết hạn. Đang chuyển về trang đăng nhập...", "warning", 4000);
    setTimeout(() => { window.location.href = (window.ctx || "") + "/login"; }, 1500);
}

function showValidationErrors(errs) {
    const lines = errs.map(e => `• \${e.message}`).join("\n");
    showToast(`Vui lòng sửa các lỗi sau:\n\${lines}`, "error", 6000);
}

function showFieldErrors(errs) {
    const lines = errs.map(e => `• [\${e.field}] \${e.message}`).join("\n");
    showToast(`Lazada từ chối:\n\${lines}`, "error", 6000);
}

// ── TAB 1 MODAL: PUBLISH NEW WIZARD ──────────────────────────────────
function openPublishWizard() {
    console.log("[openPublishWizard] wmsSKUs count:", wmsSKUs.length, "productsDataContainer:",
        document.getElementById("productsDataContainer")?.textContent?.trim().length || 0);
    if (wmsSKUs.length === 0) {
        // If we have data in the container but wmsSKUs is empty, reload
        const prodElem = document.getElementById("productsDataContainer");
        if (prodElem && prodElem.textContent.trim()) {
            try {
                const parsed = JSON.parse(prodElem.textContent.trim());
                wmsSKUs = parsed.map(p => ({
                    productId: p.productId,
                    id: p.productId,
                    sku: p.sku || p.skuCode || '',
                    name: p.name || p.productName || '',
                    category: p.categoryName || '',
                    categoryId: p.categoryId || null,
                    qtyOnHand: p.qtyOnHand || 0,
                    weight: p.weight, dimensions: p.dimensions
                }));
                console.log("[openPublishWizard] reloaded wmsSKUs:", wmsSKUs.length);
            } catch(e) { console.error("[openPublishWizard] parse error:", e); }
        }
    }
    if (wmsSKUs.length === 0) {
        showToast("Không tìm thấy Master SKU nào đang hoạt động. Hãy tạo + duyệt sản phẩm trước ở /products.", "error", 6000);
        return;
    }

    wizStep = 1;
    wizSelectedMasterSKU = null;
    wizSelectedChannels = []; // will hold Channel objects: { channelId, platform, channelName, ... }
    wizMasterSearchQuery = "";
    const defaultCat = (DB_CATEGORIES.length > 0) ? DB_CATEGORIES[0].categoryName : "General";
    wizChannelConfigs = {
        lazada: { category: defaultCat, lazadaCategoryId: null, lazadaCategoryName: "", price: 0, brand: "No Brand", brandId: "30768" },
        website: { price: 0, quantity: 0, description: "" }
    };

    // Clear wizard image states
    wizChannelImages = { lazada: [], website: [] };

    document.getElementById("publishWizardOverlay").classList.add("open");
    document.getElementById("wizStep1SearchInp").value = "";

    renderWizStep1();
    renderWizProgress();

    // Eagerly load Lazada leaves from the first Lazada channel so step 3 has data
    const firstLazada = (channelsList || []).find(c => c.platform && c.platform.toLowerCase() === 'lazada');
    if (firstLazada) {
        loadLazadaLeaves(firstLazada.channelId);
        // Pre-fetch all category mappings so SKU selection gets instant auto-fill
        preFetchCategoryMappings(firstLazada.channelId);
    }
}

function closePublishWizard() {
    document.getElementById("publishWizardOverlay").classList.remove("open");
    document.getElementById("sandboxOverlay").classList.remove("open");
}

function renderWizProgress() {
    const list = [1, 2, 3];
    list.forEach(s => {
        const ind = document.getElementById("wizInd" + s);
        const num = document.getElementById("wizIndNum" + s);

        ind.classList.remove("active", "done");
        if (s < wizStep) {
            ind.classList.add("done");
            num.textContent = "✓";
        } else if (s === wizStep) {
            ind.classList.add("active");
            num.textContent = s;
        } else {
            num.textContent = s;
        }
    });

    // Toggle navigation buttons
    document.getElementById("btnWizPrev").style.display = (wizStep > 1) ? "inline-flex" : "none";
    document.getElementById("btnWizNext").textContent = (wizStep < 3) ? "Tiếp tục" : "ĐỒNG BỘ LÊN SÀN (PUBLISH)";
}

function onWizMasterSearch(val) {
    wizMasterSearchQuery = val;
    renderWizMasterList();
}

function renderWizMasterList() {
    const listDiv = document.getElementById("wizMasterSKUList");
    listDiv.innerHTML = "";

    const query = wizMasterSearchQuery.toLowerCase().trim();
    const filtered = wmsSKUs.filter(s =>
        s.name.toLowerCase().includes(query) ||
        s.sku.toLowerCase().includes(query)
    );

    if (filtered.length === 0) {
        listDiv.innerHTML = `<div style="padding: 1.5rem; text-align: center; color: rgba(16, 55, 92, 0.45); font-size:13px">Không tìm thấy Master SKU nào khớp với từ khóa</div>`;
        return;
    }

    filtered.forEach(sku => {
        const isSelected = wizSelectedMasterSKU && wizSelectedMasterSKU.sku === sku.sku;
        const row = document.createElement("div");
        row.className = "cp-wiz-row" + (isSelected ? " selected" : "");
        row.onclick = () => {
            selectWizMasterSKU(sku);
        };
        row.innerHTML = `
            <div style="flex: 1">
                <div style="font-weight:700;font-size:13px;color:var(--navy)">\${sku.name}</div>
                <div style="font-size:11px;color:rgba(16, 55, 92, 0.45);margin-top:2px;font-family:monospace">
                    SKU: \${sku.sku} | Phân loại: \${sku.category || "Chưa phân loại"}
                </div>
            </div>
            <div style="display:flex;align-items:center;gap:0.75rem">
                <span class="cp-status-pill active" style="font-size:10px">Đã duyệt</span>
                <div class="cp-wiz-row-radio">
                    <div class="cp-wiz-row-radio-inner"></div>
                </div>
            </div>
        `;
        listDiv.appendChild(row);
    });
}

function selectWizMasterSKU(sku) {
    wizSelectedMasterSKU = sku;
    renderWizMasterList();

    // Pre-fill wizard fields with SKU defaults.
    // macPrice = moving average cost; nếu chưa có MAC thì fallback basePrice.
    // BR-PRICE-01: đã bỏ hard block — Manager cấu hình ngưỡng warning qua /manager/config/pricing
    const mac = (sku.macPrice && sku.macPrice > 0) ? sku.macPrice : (sku.basePrice && sku.basePrice > 0 ? sku.basePrice : 0);
    const defaultPrice = mac > 0 ? Math.round(mac * 1.30) : (sku.basePrice && sku.basePrice > 0 ? sku.basePrice : 100000);
    const qty = (sku.qtyOnHand && sku.qtyOnHand > 0) ? Math.floor(sku.qtyOnHand) : 0;
    wizChannelConfigs.lazada.price = defaultPrice;
    wizChannelConfigs.lazada.macPrice = mac; // lưu MAC để warning chip tính margin
    wizChannelConfigs.lazada.quantity = qty;
    wizChannelConfigs.lazada.weight = parseFloat(String(sku.weightKg || sku.weight || 0.2).replace(/[^0-9.]/g, '')) || 0.2;
    wizChannelConfigs.lazada.dimensions = sku.dimensions || "10x10x10";
    wizChannelConfigs.lazada.sellerSku = (sku.sku || "").trim();
    wizChannelConfigs.lazada.description = "";
    wizChannelConfigs.lazada.shortDescription = "";

    wizChannelConfigs.website = {
        price: defaultPrice,
        quantity: qty,
        description: sku.description || "Sản phẩm chất lượng cao chính hãng."
    };

    // Fill specifications details card
    document.getElementById("wizSpecName").textContent = sku.name;
    document.getElementById("wizSpecCode").textContent = sku.sku;
    document.getElementById("wizSpecWeight").textContent = sku.weight || "0.1 kg";
    document.getElementById("wizSpecDimensions").textContent = sku.dimensions || "10x10x10 cm";
    document.getElementById("wizMasterSpecCard").style.display = "block";

    // Set default covers in Step 3 based on name mapping
    const defaultImg = getDynamicCover(sku.name);
    wizChannelImages = {
        lazada: [{ url: defaultImg, base64: defaultImg }],
        website: [{ url: defaultImg, base64: defaultImg }]
    };

    // Auto-fill Lazada category from WMS → Lazada mapping (UC-B2C09).
    // Use channelsList (available globally from page load) — NOT wizSelectedChannels,
    // which is empty at Step 1 and only populated after Step 2 channel selection.
    const lazadaChan = (channelsList || []).find(c => c.platform && c.platform.toLowerCase() === "lazada");
    if (lazadaChan) {
        tryAutoFillLazadaLeaf(lazadaChan.channelId);
    }
}

function renderWizStep1() {
    document.getElementById("wizStep1Content").style.display = "block";
    document.getElementById("wizStep2Content").style.display = "none";
    document.getElementById("wizStep3Content").style.display = "none";
    
    document.getElementById("wizMasterSpecCard").style.display = wizSelectedMasterSKU ? "block" : "none";
    renderWizMasterList();
}

function renderWizStep2() {
    document.getElementById("wizStep1Content").style.display = "none";
    document.getElementById("wizStep2Content").style.display = "block";
    document.getElementById("wizStep3Content").style.display = "none";

    document.getElementById("wizStep2ProductLabel").innerHTML = `
        <strong>\${wizSelectedMasterSKU.name}</strong> (SKU: \${wizSelectedMasterSKU.sku})
    `;

    // Highlight selected platform cards — check by platform name
    const chs = ["lazada", "website"];
    chs.forEach(ch => {
        const card = document.getElementById("pCard" + ch.charAt(0).toUpperCase() + ch.slice(1));
        if (!card) return;
        card.classList.remove("selected");
        if (wizSelectedChannels.some(c => c.platform && c.platform.toLowerCase() === ch)) {
            card.classList.add("selected");
        }
    });
}

function toggleWizChannel(channelName) {
    // channelName is a string like 'lazada'; find the real Channel object from DB
    const existingIdx = wizSelectedChannels.findIndex(c =>
        c.platform && c.platform.toLowerCase() === channelName.toLowerCase());
    if (existingIdx > -1) {
        wizSelectedChannels.splice(existingIdx, 1);
    } else {
        // Find Channel from DB that matches this platform name
        const dbChannel = channelsList.find(c =>
            c.platform && c.platform.toLowerCase() === channelName.toLowerCase());
        if (dbChannel) {
            wizSelectedChannels.push(dbChannel);
        }
    }
    renderWizStep2();
}

// ── AUTO-FILL LAZADA LEAF FROM CATEGORY MAPPING ───────────────────────
const MAPPING_CACHE = new Map();
const MAPPING_CACHE_KEY = "lazada_mapping_cache_v1";

function getCachedMapping(channelId, wmsCategoryId) {
    if (MAPPING_CACHE.has(channelId + ":" + wmsCategoryId)) return MAPPING_CACHE.get(channelId + ":" + wmsCategoryId);
    try {
        const stored = JSON.parse(localStorage.getItem(MAPPING_CACHE_KEY) || "{}");
        const key = channelId + ":" + wmsCategoryId;
        if (stored[key]) { MAPPING_CACHE.set(key, stored[key]); return stored[key]; }
    } catch (e) {}
    return null;
}

function setCachedMapping(channelId, wmsCategoryId, data) {
    const key = channelId + ":" + wmsCategoryId;
    MAPPING_CACHE.set(key, data);
    try {
        const stored = JSON.parse(localStorage.getItem(MAPPING_CACHE_KEY) || "{}");
        stored[key] = data;
        localStorage.setItem(MAPPING_CACHE_KEY, JSON.stringify(stored));
    } catch (e) {}
}

// Pre-fetches category mappings for all WMS categories so mapping lookups are instant
// when a SKU is selected — no dependency on LAZADA_LEAVES being loaded yet.
function preFetchCategoryMappings(channelId) {
    if (!wmsSKUs || wmsSKUs.length === 0) return;
    wmsSKUs.forEach(sku => {
        if (!sku.categoryId) return;
        const key = channelId + ":" + sku.categoryId;
        if (MAPPING_CACHE.has(key)) return;
        fetchJson(window.location.pathname, {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded" },
            body: "action=getCategoryMapping&channelId=" + channelId + "&wmsCategoryId=" + sku.categoryId
        }).then(data => { setCachedMapping(channelId, sku.categoryId, data); }).catch(() => {});
    });
}

// Runs entirely in the background — loads Lazada leaves + mapping, then auto-fills the config.
// Updates wizChannelConfigs directly so the value is ready even before Step 3 renders.
function tryAutoFillLazadaLeaf(channelId) {
    if (!wizSelectedMasterSKU || !wizSelectedMasterSKU.categoryId) return;
    const wmsCategoryId = wizSelectedMasterSKU.categoryId;

    // Always try to apply cached mapping immediately (no network needed)
    applyMappingToConfig(channelId, wmsCategoryId);

    // Always fetch mapping if not cached — parallel-safe, no-op if already cached
    const cachedMapping = getCachedMapping(channelId, wmsCategoryId);
    if (!cachedMapping) {
        fetchJson(window.location.pathname, {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded" },
            body: "action=getCategoryMapping&channelId=" + channelId + "&wmsCategoryId=" + wmsCategoryId
        }).then(data => {
            setCachedMapping(channelId, wmsCategoryId, data);
            applyMappingToConfig(channelId, wmsCategoryId);
        }).catch(function(err) {
            console.warn("[autoFill] getCategoryMapping failed:", err.message);
        });
    }

    // Load LAZADA_LEAVES if not yet loaded (needed for applyMappingToConfig to pass its guard)
    if (!LAZADA_LEAVES || LAZADA_LEAVES.length === 0) {
        loadLazadaLeaves(channelId).then(() => {
            applyMappingToConfig(channelId, wmsCategoryId);
        });
    }
}

// Updates wizChannelConfigs directly — no DOM required.
function applyMappingToConfig(channelId, wmsCategoryId) {
    if (!LAZADA_LEAVES || LAZADA_LEAVES.length === 0) return;
    const mappingData = getCachedMapping(channelId, wmsCategoryId);
    if (!mappingData || !mappingData.success || !mappingData.found || !mappingData.mappings || mappingData.mappings.length === 0) return;
    const m = mappingData.mappings[0];
    wizChannelConfigs.lazada.lazadaCategoryId = m.lazadaCategoryId;
    wizChannelConfigs.lazada.lazadaCategoryName = m.name;
    wizChannelConfigs.lazada.category = m.name;
}

function applyMappingNow(channelId, wmsCategoryId, select) {
    if (!LAZADA_LEAVES || LAZADA_LEAVES.length === 0) return;

    const mappingData = getCachedMapping(channelId, wmsCategoryId);
    if (!mappingData || !mappingData.success || !mappingData.found || !mappingData.mappings || mappingData.mappings.length === 0) return;

    const m = mappingData.mappings[0];
    wizChannelConfigs.lazada.lazadaCategoryId = m.lazadaCategoryId;
    wizChannelConfigs.lazada.lazadaCategoryName = m.name;
    wizChannelConfigs.lazada.category = m.name;

    if (!select) return;
    const targetValue = String(m.lazadaCategoryId);
    const opt = Array.from(select.options).find(function(o) { return String(o.value) === targetValue; });
    if (opt) {
        select.value = targetValue;
    }
}


function renderWizStep3() {
    document.getElementById("wizStep1Content").style.display = "none";
    document.getElementById("wizStep2Content").style.display = "none";
    document.getElementById("wizStep3Content").style.display = "block";

    document.getElementById("wizStep3ProductLabel").innerHTML = `
        <strong>\${wizSelectedMasterSKU.name}</strong> (SKU: \${wizSelectedMasterSKU.sku})
    `;

    const container = document.getElementById("wizStep3ChannelsContainer");
    container.innerHTML = "";

    // Lazada section — check by platform name in the object array
    if (wizSelectedChannels.some(c => c.platform && c.platform.toLowerCase() === "lazada")) {
        const lazadaChan = wizSelectedChannels.find(c => c.platform && c.platform.toLowerCase() === "lazada");

        // ─── Dropdown: always show LAZADA_LEAVES that were pre-loaded when the wizard opened.
        //    The placeholder is dynamic: shows "(Đã gợi ý)" when a mapping pre-filled the value,
        //    otherwise the generic "-- Chọn danh mục Lazada --".
        const selectedLazadaId = wizChannelConfigs.lazada.lazadaCategoryId;
        const hasPreselected = selectedLazadaId && String(selectedLazadaId).length > 0;

        const lazadaLeafOptions = LAZADA_LEAVES.map(c => {
            const isSel = String(c.lazadaCategoryId) === String(selectedLazadaId) ? 'selected' : '';
            return `<option value="\${c.lazadaCategoryId}" \${isSel}>\${escapeHtml(c.name)}</option>`;
        }).join('');

        const box = document.createElement("div");
        box.className = "cp-platform-config-box lazada";
        box.innerHTML = `
            <div class="cp-platform-config-hdr lazada">
                <span>CẤU HÌNH TRÊN KÊNH: LAZADA</span>
                <span>Open Platform API</span>
            </div>
            <div class="cp-platform-config-body">
                <div style="display:grid; grid-template-columns: repeat(2, 1fr); gap: 0.75rem">
                    <div class="cp-form-group">
                        <label class="cp-form-label">Danh mục Lazada (leaf) *</label>
                        <select class="cp-input-text" style="padding:0.5rem" onchange="onLazadaLeafChange(this)">
                            <option value="">\${hasPreselected ? '-- Thay đổi danh mục Lazada --' : '-- Chọn danh mục Lazada --'}</option>
                            \${lazadaLeafOptions}
                        </select>
                        <div style="display:flex;gap:0.5rem;margin-top:0.35rem;align-items:center;min-height:22px">
                            <button type="button" id="lazadaLeafSyncBtn" onclick="syncLazadaLeaves(\${lazadaChan.channelId})" style="font-size:11px;padding:0.25rem 0.75rem;display:inline-flex;align-items:center;gap:0.3rem;background:rgba(16,115,230,0.08);color:#1073e6;border:1px solid rgba(16,115,230,0.25);border-radius:6px;cursor:pointer;font-weight:700;transition:all 0.15s;white-space:nowrap">
                                <svg xmlns="http://www.w3.org/2000/svg" width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="23 4 23 10 17 10"></polyline><polyline points="1 20 1 14 7 14"></polyline><path d="M3.51 9a9 9 0 0114.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0020.49 15"></path></svg>
                                Đồng bộ danh mục Lazada
                            </button>
                            <span id="lazadaLeafStatus" style="font-size:11px;color:rgba(16,55,92,.5)">\${LAZADA_LEAVES.length > 0 ? (hasPreselected ? '✓ Đã gợi ý từ ánh xạ WMS.' : 'Sẵn sàng để chọn danh mục Lazada.') : 'Chưa có danh mục Lazada — bấm "Đồng bộ" để tải về.'}</span>
                        </div>
                    </div>
                    <div class="cp-form-group">
                        <label class="cp-form-label">Giá bán lẻ (Retail Price) *</label>
                        <input id="wizPriceInput" type="number" class="cp-input-text" style="padding:0.5rem; text-align:right" value="\${wizChannelConfigs.lazada.price}" oninput="wizChannelConfigs.lazada.price = Math.max(0, Number(this.value) || 0); window.updatePriceWarningChip(this);" />
                        <div id="wizPriceHint" style="font-size:11px; color:rgba(16,55,92,.5); margin-top:2px">
                            <span id="wizPriceMac">Giá vốn (MAC): \${(wizChannelConfigs.lazada.macPrice || 0).toLocaleString()}đ</span>
                            <span id="wizPriceChip" class="price-warn-chip" style="display:none; margin-left:8px;"></span>
                        </div>
                    </div>
                    <div class="cp-form-group">
                        <label class="cp-form-label">Số lượng tồn (Quantity) *</label>
                        <input type="number" class="cp-input-text" style="padding:0.5rem; text-align:right" value="\${wizChannelConfigs.lazada.quantity || 0}" oninput="wizChannelConfigs.lazada.quantity = Math.max(0, Number(this.value) || 0);" />
                    </div>
                    <div class="cp-form-group" style="grid-column: 1 / -1">
                        <label class="cp-form-label">Mô tả sàn (Description) *</label>
                        <textarea class="cp-input-text" style="padding:0.5rem; min-height:80px" oninput="wizChannelConfigs.lazada.description = this.value; wizChannelConfigs.lazada.shortDescription = this.value">\${wizChannelConfigs.lazada.description || ""}</textarea>
                    </div>
                    <div class="cp-form-group">
                        <label class="cp-form-label">Cân nặng (kg)</label>
                        <input type="number" step="0.01" class="cp-input-text" style="padding:0.5rem" value="\${wizChannelConfigs.lazada.weight || 0.2}" oninput="wizChannelConfigs.lazada.weight = parseFloat(this.value.replace(/[^0-9.]/g,'')) || 0.2" />
                    </div>
                    <div class="cp-form-group">
                        <label class="cp-form-label">Kích thước (DxRxC cm)</label>
                        <input type="text" class="cp-input-text" style="padding:0.5rem" value="\${wizChannelConfigs.lazada.dimensions || '10x10x10'}" oninput="wizChannelConfigs.lazada.dimensions = this.value" />
                    </div>
                    <div class="cp-form-group">
                        <label class="cp-form-label">Thương hiệu (Brand)</label>
                        <div style="display:flex; align-items:center; gap:0.5rem; padding:0.5rem; background:#f0f4fa; border-radius:8px; border:1px solid #dde4ee">
                            <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="#059669" stroke-width="2" style="flex-shrink:0; width:18px; height:18px"><path stroke-linecap="round" stroke-linejoin="round" d="M5 13l4 4L19 7" /></svg>
                            <span style="font-size:13px; font-weight:700; color:#1a3a5c">No Brand</span>
                            <span style="font-size:11px; color:rgba(16,55,92,.4); margin-left:auto">brand_id: 30768</span>
                        </div>
                    </div>
                </div>
                <div style="margin-top: 0.5rem" id="uploaderWizLazada">
                    <%-- Render image uploader dynamically --%>
                </div>
            </div>
        `;
        container.appendChild(box);
        renderWizUploader("lazada");
        tryAutoFillLazadaLeaf(lazadaChan.channelId);
    }

    // Website section — check by platform name in the object array
    if (wizSelectedChannels.some(c => c.platform && c.platform.toLowerCase() === "website")) {
        const websiteChan = wizSelectedChannels.find(c => c.platform && c.platform.toLowerCase() === "website");

        const box = document.createElement("div");
        box.className = "cp-platform-config-box website";
        box.style.borderColor = "#EB8317";
        box.style.marginTop = "1rem";
        box.innerHTML = `
            <div class="cp-platform-config-hdr website" style="background:#EB8317">
                <span>CẤU HÌNH TRÊN KÊNH: WEBSITE STOREFRONT</span>
                <span>REST API Sync</span>
            </div>
            <div class="cp-platform-config-body">
                <div style="display:grid; grid-template-columns: repeat(2, 1fr); gap: 0.75rem">
                    <div class="cp-form-group">
                        <label class="cp-form-label">Giá bán lẻ Website (Retail Price) *</label>
                        <input type="number" class="cp-input-text" style="padding:0.5rem; text-align:right" value="\${wizChannelConfigs.website.price}" oninput="wizChannelConfigs.website.price = Math.max(0, Number(this.value) || 0);" />
                    </div>
                    <div class="cp-form-group">
                        <label class="cp-form-label">Số lượng tồn Website (Quantity) *</label>
                        <input type="number" class="cp-input-text" style="padding:0.5rem; text-align:right" value="\${wizChannelConfigs.website.quantity || 0}" oninput="wizChannelConfigs.website.quantity = Math.max(0, Number(this.value) || 0);" />
                    </div>
                    <div class="cp-form-group" style="grid-column: 1 / -1">
                        <label class="cp-form-label">Mô tả sản phẩm (Description) *</label>
                        <textarea class="cp-input-text" style="padding:0.5rem; min-height:80px" oninput="wizChannelConfigs.website.description = this.value">\${wizChannelConfigs.website.description || ""}</textarea>
                    </div>
                </div>
                <div style="margin-top: 0.5rem" id="uploaderWizWebsite">
                    <%-- Render image uploader dynamically --%>
                </div>
            </div>
        `;
        container.appendChild(box);
        renderWizUploader("website");
    }
}

function renderWizUploader(channel) {
    const parent = document.getElementById("uploaderWiz" + channel.charAt(0).toUpperCase() + channel.slice(1));
    if (!parent) return;

    const currentImgs = wizChannelImages[channel] || [];

    let gridHtml = `<div class="cp-upload-grid">`;
    currentImgs.forEach((img, idx) => {
        const imgSrc = (img && typeof img === "object") ? img.url : img;
        gridHtml += `
            <div class="cp-upload-box">
                <img src="\${imgSrc}" />
                <div class="cp-upload-box-trash" onclick="removeWizImage('\${channel}', \${idx})">
                    <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" /></svg>
                </div>
                \${idx === 0 ? '<span class="cp-upload-box-label">Ảnh bìa</span>' : ""}
            </div>
        `;
    });

    if (currentImgs.length < 5) {
        gridHtml += `
            <label class="cp-upload-btn-card">
                <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-8l-4-4m0 0L8 8m4-4v12" /></svg>
                <span class="main">Thêm ảnh</span>
                <span class="sub">(Tối đa 5)</span>
                <input type="file" accept="image/*" multiple style="display:none" onchange="uploadWizImage('\${channel}', this.files)" />
            </label>
        `;
    }
    gridHtml += `</div>`;
    parent.innerHTML = `<label class="cp-form-label">Hình ảnh sản phẩm trên sàn (\${channel.toUpperCase()}) *</label>` + gridHtml;
}

function convertToJpeg(file, callback) {
    const reader = new FileReader();
    reader.onload = function(e) {
        const img = new Image();
        img.onload = function() {
            const canvas = document.createElement("canvas");
            canvas.width = img.width;
            canvas.height = img.height;
            const ctx = canvas.getContext("2d");
            // Fill background with white in case of transparency
            ctx.fillStyle = "#FFFFFF";
            ctx.fillRect(0, 0, canvas.width, canvas.height);
            ctx.drawImage(img, 0, 0);
            const jpegDataUrl = canvas.toDataURL("image/jpeg", 0.9);
            callback(jpegDataUrl);
        };
        img.onerror = function() {
            callback(e.target.result);
        };
        img.src = e.target.result;
    };
    reader.readAsDataURL(file);
}

function uploadWizImage(channel, files) {
    if (!files || files.length === 0) return;
    Array.from(files).forEach(file => {
        const currentList = wizChannelImages[channel] || [];
        if (currentList.length >= 5) return;

        convertToJpeg(file, function(jpegDataUrl) {
            const item = { url: jpegDataUrl, base64: jpegDataUrl };
            currentList.push(item);
            wizChannelImages[channel] = currentList;
            renderWizUploader(channel);

            // Normalize filename extension to .jpg
            let name = file.name || "image.jpg";
            const dotIdx = name.lastIndexOf(".");
            if (dotIdx >= 0) {
                name = name.substring(0, dotIdx) + ".jpg";
            } else {
                name = name + ".jpg";
            }

            // Upload to server so /image/migrate gets a public URL.
            // If migration fails, base64 is used as fallback.
            fetch("/sales/channel-products", {
                method: "POST",
                headers: { "Content-Type": "application/x-www-form-urlencoded" },
                body: "action=uploadImageBase64&filename=" + encodeURIComponent(name)
                    + "&base64=" + encodeURIComponent(jpegDataUrl)
            }).then(r => r.json()).then(data => {
                if (data.success && data.url) {
                    const idx = currentList.indexOf(item);
                    if (idx >= 0) {
                        // Store server URL + preserve original base64 for fallback
                        currentList[idx] = { url: data.url, base64: jpegDataUrl };
                        wizChannelImages[channel] = currentList;
                        renderWizUploader(channel);
                    }
                } else {
                    console.warn("[uploadWizImage] server upload failed:", data.message);
                }
            }).catch(function(err) {
                console.warn("[uploadWizImage] fetch error:", err.message);
            });
        });
    });
}

function removeWizImage(channel, idx) {
    wizChannelImages[channel] = wizChannelImages[channel].filter((_, i) => i !== idx);
    renderWizUploader(channel);
}

function wizNavigate(dir) {
    if (dir === 1) {
        if (wizStep === 1) {
            if (!wizSelectedMasterSKU) {
                alert("Vui lòng chọn một Master SKU gốc để tiếp tục!");
                return;
            }
            wizStep = 2;
            renderWizStep2();
        } else if (wizStep === 2) {
            if (wizSelectedChannels.length === 0) {
                alert("Vui lòng chọn ít nhất một sàn thương mại điện tử để tiếp tục!");
                return;
            }
            // Ensure LAZADA_LEAVES is loaded before rendering step 3 (async category lookup)
            var lazadaChan = wizSelectedChannels.find(function(c){ return c.platform && c.platform.toLowerCase() === "lazada"; });
            var loadDone = function() {
                wizStep = 3;
                renderWizStep3();
                renderWizProgress();
            };
            if (lazadaChan && (!LAZADA_LEAVES || LAZADA_LEAVES.length === 0)) {
                loadLazadaLeaves(lazadaChan.channelId).then(function() { loadDone(); }).catch(function() { loadDone(); });
            } else {
                loadDone();
            }
        } else {
            // Clicked Publish trigger real Lazada API
            executePublishProduct();
        }
    } else {
        wizStep--;
        if (wizStep === 1) renderWizStep1();
        if (wizStep === 2) renderWizStep2();
    }
    renderWizProgress();
}

function finalizePublishData(realLazadaItemId, realLazadaSkuId) {
    const today = new Date().toISOString().slice(0, 10);
    const hasRealLazadaId = realLazadaItemId ? String(realLazadaItemId).trim().length > 0 : false;

    // Many-to-many list update helper matching React's updateSKUMappings logic
    let storedMapList = [];
    const savedMapStr = localStorage.getItem("sku_mappings_v2");
    if (savedMapStr) {
        try { storedMapList = JSON.parse(savedMapStr); } catch(e) { storedMapList = []; }
    }

    let rawMappings = [];
    const savedRawStr = localStorage.getItem("sku_raw_mappings_v2");
    if (savedRawStr) {
        try { rawMappings = JSON.parse(savedRawStr); } catch(e) { rawMappings = []; }
    }

    // Loop through selected platforms (ch is a Channel object from DB)
    wizSelectedChannels.forEach(ch => {
        const platform = ch.platform ? ch.platform.toLowerCase() : '';
        const config = wizChannelConfigs[platform];
        const uniqueSuffix = Math.floor(100 + Math.random() * 900);
        const skuNoDash = wizSelectedMasterSKU.sku.replace(/-/g, "");
        const chName = ch.channelName || platform;
        const chSKU = chName.toUpperCase() + "-" + skuNoDash + "-" + uniqueSuffix;

        // Use real itemId returned from Lazada API; fall back to placeholder only for non-Lazada channels
        const isLazadaChannel = platform === 'lazada';
        let itemId;
        if (isLazadaChannel) {
            itemId = hasRealLazadaId ? realLazadaItemId
                : (chName.slice(0, 3) + "-ITEM-" + Math.floor(100000 + Math.random() * 900000));
        } else {
            itemId = chName.slice(0, 3).toUpperCase() + "-ITEM-" + Math.floor(100000 + Math.random() * 900000);
        }

        const defaultCover = getDynamicCover(wizSelectedMasterSKU.name);
        const rawImgs = wizChannelImages[platform] && wizChannelImages[platform].length > 0
            ? wizChannelImages[platform]
            : [{ url: defaultCover, base64: defaultCover }];
        // Store as URL strings for grid display (base64 → url for render)
        const imagesList = rawImgs.map(function(img) {
            return (img && typeof img === "object") ? img.url : img;
        });

        // Determine syncStatus based on whether we have a real ID from the platform
        const syncStatus = isLazadaChannel
            ? (hasRealLazadaId ? "success" : "pending")
            : "success";

        // 1. Add to Channel Products list
        channelProducts.push({
            id: "p_" + Date.now() + "_" + platform,
            masterSKU: wizSelectedMasterSKU.sku,
            channelSKU: chSKU,
            channel: platform,
            channelName: chName,
            channelColor: platform === "shopee" ? "#EE4D2D" : platform === "lazada" ? "#0F146D" : "#69C9D0",
            productName: wizSelectedMasterSKU.name,
            // Persist whatever the operator typed in the wizard. If they left it
            // blank, store an empty string — the next view/edit will surface a
            // clear "missing description" state instead of fabricating a template.
            description: (config.description || "").trim(),
            images: imagesList,
            price: Number(config.price) || 0,
            status: "active",
            stock: wizSelectedMasterSKU.qtyOnHand || 0,
            channelItemId: itemId,
            bufferStock: 0,
            syncStatus: syncStatus,
            lazadaItemId: isLazadaChannel ? realLazadaItemId : undefined,
            lazadaSkuId: isLazadaChannel ? realLazadaSkuId : undefined
        });

        // 2. Add to raw mappings v2 table (unlinked items helper in sku-mapping)
        rawMappings.push({
            id: "raw_" + Date.now() + "_" + platform,
            channelItemId: itemId,
            channelSKU: chSKU,
            channelItemName: wizSelectedMasterSKU.name,
            channel: chName,
            syncStatus: syncStatus
        });

        // 3. Update or append in sku_mappings_v2 (linked table)
        const existingIdx = storedMapList.findIndex(m => m.masterSKU === wizSelectedMasterSKU.sku);
        if (existingIdx > -1) {
            const chanMappings = storedMapList[existingIdx].channelMappings || [];
            const idx = chanMappings.findIndex(cm => cm.channel.toLowerCase() === platform);
            if (idx > -1) {
                chanMappings[idx].channelSKU = chSKU;
                chanMappings[idx].status = "mapped";
            } else {
                const chColors = { Shopee: "#EE4D2D", TikTok: "#69C9D0", Lazada: "#0F146D", Website: "#EB8317" };
                chanMappings.push({
                    channel: chName,
                    channelSKU: chSKU,
                    status: "mapped",
                    channelColor: chColors[chName] || "#64748b"
                });
            }
            storedMapList[existingIdx].channelMappings = chanMappings;
            storedMapList[existingIdx].stock = wizSelectedMasterSKU.qtyOnHand || 0;
        } else {
            const defaultChans = [
                { channel: "Lazada", channelSKU: "", status: "unmapped", channelColor: "#0F146D" },
                { channel: "Website", channelSKU: "", status: "unmapped", channelColor: "#EB8317" }
            ];
            const idx = defaultChans.findIndex(cm => cm.channel.toLowerCase() === platform);
            if (idx > -1) {
                defaultChans[idx].channelSKU = chSKU;
                defaultChans[idx].status = "mapped";
            }
            storedMapList.push({
                id: String(storedMapList.length + 1),
                masterSKU: wizSelectedMasterSKU.sku,
                masterName: wizSelectedMasterSKU.name,
                channelMappings: defaultChans,
                stock: wizSelectedMasterSKU.qtyOnHand || 0
            });
        }

        localStorage.setItem("sku_mappings_v2", JSON.stringify(storedMapList));
        localStorage.setItem("sku_raw_mappings_v2", JSON.stringify(rawMappings));
    });

    saveData();
    closePublishWizard();
    renderProductsTab();
    showToast(`Đã lưu mapping sản phẩm lên sàn. Lazada item_id=\${realLazadaItemId}`, "success");
}

// ── TAB 1 MODAL: EDIT PRODUCT DETAILS ───────────────────────────────

function populateEditCategoryDropdown(selectedId) {
    const catSelect = document.getElementById("editLazadaCategory");
    const catHidden = document.getElementById("editLazadaCategoryId");
    if (!catSelect || !catHidden) return;
    let html = '<option value="">-- Chọn danh mục Lazada --</option>';
    for (let i = 0; i < LAZADA_LEAVES.length; i++) {
        const c = LAZADA_LEAVES[i];
        const isSel = String(c.lazadaCategoryId) === String(selectedId) ? 'selected' : '';
        html += '<option value="' + escapeHtml(String(c.lazadaCategoryId)) + '" ' + isSel + '>' + escapeHtml(c.name || 'Cat ' + c.lazadaCategoryId) + '</option>';
    }
    catSelect.innerHTML = html;
    catHidden.value = selectedId ? String(selectedId) : '';
}

function populateEditBrandDropdown(selectedId) {
    const brandSelect = document.getElementById("editLazadaBrand");
    const brandHidden = document.getElementById("editLazadaBrandId");
    if (!brandSelect || !brandHidden) return;
    let html = '<option value="">-- Chọn thương hiệu --</option>';
    for (let i = 0; i < LAZADA_BRANDS.length; i++) {
        const b = LAZADA_BRANDS[i];
        const isSel = String(b.brandId) === String(selectedId) ? 'selected' : '';
        html += '<option value="' + escapeHtml(String(b.brandId)) + '" ' + isSel + '>' + escapeHtml(b.name || 'Brand ' + b.brandId) + ' (ID: ' + b.brandId + ')</option>';
    }
    brandSelect.innerHTML = html;
    brandHidden.value = selectedId ? String(selectedId) : '';
}

function openEditModal(productId) {
    const p = channelProducts.find(item => item.id == productId);
    if (!p) return;

    editTargetProductId = productId;
    // Show a loading state immediately so the operator never sees a fake
    // cover image before the real product_images arrive from the server.
    editImagesList = [];
    document.getElementById("editProductMasterName").textContent = p.productName;
    document.getElementById("editProductMasterSKU").textContent = p.masterSKU;
    document.getElementById("editProductPrice").value = p.price;
    document.getElementById("editProductDesc").value = p.description || "";
    document.getElementById("editProductOverlay").classList.add("open");
    renderEditUploader();

    // Lazada category dropdown — async-loaded if not yet cached
    const catSelect = document.getElementById("editLazadaCategory");
    const catHidden = document.getElementById("editLazadaCategoryId");
    if (catSelect && catHidden) {
        catSelect.innerHTML = '<option value="">-- Đang tải danh mục... --</option>';
        if (!LAZADA_LEAVES || LAZADA_LEAVES.length === 0) {
            const ch = (channelsList || []).find(c => c.platform && c.platform.toLowerCase() === 'lazada');
            if (ch) {
                loadLazadaLeaves(ch.channelId).then(() => {
                    populateEditCategoryDropdown(p.lazadaCategoryId);
                });
            } else {
                populateEditCategoryDropdown(p.lazadaCategoryId);
            }
        } else {
            populateEditCategoryDropdown(p.lazadaCategoryId);
        }
    }

    // Brand always "No Brand" (brand_id=30768) by default; user can change in dropdown.
    document.getElementById("editLazadaBrandId").value = "30768";

    // Fetch fresh description and images from database/master product BEFORE
    // rendering the uploader so the operator never edits against a fake cover.
    if (p.productId) {
        fetchJson("${pageContext.request.contextPath}/sales/channel-products?action=getProductDetail&productId=" + encodeURIComponent(p.productId) + "&channelProductId=" + encodeURIComponent(p.id))
            .then(res => {
                if (res && res.success) {
                    if (res.description) {
                        document.getElementById("editProductDesc").value = res.description;
                    }
                    if (res.images && res.images.length > 0) {
                        // Each image becomes {url, base64} so the submitEditProduct
                        // uploadEditImage helper handles both legacy strings and objects.
                        editImagesList = res.images.map(function(u) {
                            return { url: u, base64: "" };
                        });
                    } else if (editImagesList.length === 0) {
                        editImagesList = [getDynamicCover(p.productName)];
                    }
                } else if (editImagesList.length === 0) {
                    editImagesList = [getDynamicCover(p.productName)];
                }
                renderEditUploader();
            })
            .catch(err => {
                console.warn("Failed to load product detail:", err);
                if (/HTML|hết hạn/i.test(err.message)) {
                    showSessionExpired();
                    return;
                }
                if (editImagesList.length === 0) {
                    editImagesList = [getDynamicCover(p.productName)];
                    renderEditUploader();
                }
            });
    } else if (editImagesList.length === 0) {
        editImagesList = [getDynamicCover(p.productName)];
        renderEditUploader();
    }
}

function closeEditModal() {
    document.getElementById("editProductOverlay").classList.remove("open");
    editTargetProductId = null;
    editImagesList = [];
}

function renderEditUploader() {
    const parent = document.getElementById("editImagesGrid");
    if (!parent) return;

    let gridHtml = "";
    editImagesList.forEach((img, idx) => {
        const srcUrl = (img && typeof img === "object") ? (img.url || img.base64) : img;
        gridHtml += `
            <div class="cp-upload-box">
                <img src="\${srcUrl}" />
                <div class="cp-upload-box-trash" onclick="removeEditImage(\${idx})">
                    <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" /></svg>
                </div>
                \${idx === 0 ? '<span class="cp-upload-box-label">Ảnh bìa</span>' : ""}
            </div>
        `;
    });

    if (editImagesList.length < 5) {
        gridHtml += `
            <label class="cp-upload-btn-card">
                <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-8l-4-4m0 0L8 8m4-4v12" /></svg>
                <span class="main">Thêm ảnh</span>
                <span class="sub">(Tối đa 5)</span>
                <input type="file" accept="image/*" multiple style="display:none" onchange="uploadEditImage(this.files)" />
            </label>
        `;
    }
    parent.innerHTML = gridHtml;
}

function uploadEditImage(files) {
    if (!files || files.length === 0) return;
    Array.from(files).forEach(file => {
        if (editImagesList.length >= 5) return;
        convertToJpeg(file, function(jpegDataUrl) {
            const tempIndex = editImagesList.length;
            const tempItem = { url: jpegDataUrl, base64: jpegDataUrl };
            editImagesList.push(tempItem);
            renderEditUploader();

            let name = file.name || "image.jpg";
            const dotIdx = name.lastIndexOf(".");
            if (dotIdx >= 0) name = name.substring(0, dotIdx) + ".jpg";
            else name = name + ".jpg";

            fetch("${pageContext.request.contextPath}/sales/channel-products", {
                method: "POST",
                headers: { "Content-Type": "application/x-www-form-urlencoded" },
                body: "action=uploadImageBase64&filename=" + encodeURIComponent(name)
                    + "&base64=" + encodeURIComponent(jpegDataUrl)
            }).then(r => r.json()).then(data => {
                if (data.success && data.url) {
                    const idx = editImagesList.indexOf(tempItem);
                    if (idx >= 0) {
                        editImagesList[idx] = { url: data.url, base64: jpegDataUrl };
                        renderEditUploader();
                    }
                }
            }).catch(err => {
                console.warn("[uploadEditImage] upload failed:", err);
            });
        });
    });
}

function removeEditImage(idx) {
    editImagesList = editImagesList.filter((_, i) => i !== idx);
    renderEditUploader();
}

function onEditLazadaCategoryChange(select) {
    document.getElementById("editLazadaCategoryId").value = select.value;
}
function onEditLazadaBrandChange(select) {
    document.getElementById("editLazadaBrandId").value = select.value;
}

function submitEditProduct() {
    const price = Number(document.getElementById("editProductPrice").value) || 0;
    const desc = document.getElementById("editProductDesc").value;
    const lazadaCategoryId = document.getElementById("editLazadaCategoryId")?.value || "";
    const brandId = document.getElementById("editLazadaBrandId")?.value || "";

    if (editImagesList.length === 0) {
        alert("Vui lòng tải lên ít nhất 1 hình ảnh sản phẩm!");
        return;
    }

    // Description validation — Lazada rejects REQUIRED_SHORT_DESC when blank.
    // Previously the marketplace showed a fake template ("productName - Đồng bộ ...")
    // when the operator left it empty. Block empty/short desc at submit time.
    const descTrim = (desc || "").trim();
    if (descTrim.length < 10) {
        showToast("Vui lòng nhập mô tả sản phẩm (tối thiểu 10 ký tự) trước khi cập nhật.", "error", 6000);
        return;
    }
    if (descTrim.length > 5000) {
        showToast("Mô tả vượt quá 5000 ký tự. Vui lòng rút gọn.", "error", 6000);
        return;
    }

    // Split editImagesList into server URLs and base64 strings
    const urls = [];
    const base64s = [];
    editImagesList.forEach(img => {
        const itemUrl = (img && typeof img === "object") ? img.url : (typeof img === "string" ? img : "");
        const itemB64 = (img && typeof img === "object") ? (img.base64 || "") : (typeof img === "string" && img.startsWith("data:") ? img : "");
        if (itemUrl && !itemUrl.startsWith("data:")) {
            urls.push(itemUrl);
            base64s.push("");
        } else {
            urls.push("");
            base64s.push(itemB64 || "");
        }
    });

    showToast("Đang cập nhật sản phẩm lên Lazada...", "info");

    const saveBtn = document.querySelector("#editProductOverlay button[onclick='submitEditProduct()']");
    if (saveBtn) saveBtn.disabled = true;

    fetchJson("${pageContext.request.contextPath}/sales/channel-products", {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: "action=edit&id=" + encodeURIComponent(editTargetProductId)
            + "&price=" + encodeURIComponent(price)
            + "&description=" + encodeURIComponent(desc)
            + "&lazadaCategoryId=" + encodeURIComponent(lazadaCategoryId)
            + "&brandId=" + encodeURIComponent(brandId)
            + "&imageUrls=" + encodeURIComponent(urls.join("|"))
            + "&imageBase64s=" + encodeURIComponent(base64s.join("|"))
    }).then(res => {
        if (res.success) {
             channelProducts = channelProducts.map(p => {
                if (p.id == editTargetProductId) {
                    return {
                        ...p,
                        price: price,
                        description: desc,
                        images: editImagesList,
                        lazadaCategoryId: lazadaCategoryId ? parseInt(lazadaCategoryId) : null,
                        brandId: brandId ? parseInt(brandId) : null,
                        syncStatus: "success",
                        lastErrorCode: null,
                        lastErrorMessage: null,
                        status: "active",
                        channelItemId: res.itemId || p.channelItemId,
                        lazadaSkuId: res.skuId || p.lazadaSkuId
                    };
                }
                return p;
            });
            saveData();
            closeEditModal();
            renderProductsTab();
            showToast("Cập nhật thông tin sản phẩm lên Lazada thành công!", "success");
        } else {
            const rawMsg = (res.fieldErrors && res.fieldErrors.length > 0) ? res.fieldErrors[0].message : (res.message || "Cập nhật thất bại.");
            const cleanMsg = rawMsg.replace(/^[A-Z0-9_]+:(?:Failed by Policy\([^)]+\):)?\s*/g, '').replace(/^Failed by Policy\([^)]+\):\s*/g, '').trim();
            showToast("Lazada báo lỗi: " + cleanMsg, "error", 8000);
        }
    }).catch(err => {
        showToast(err.message || "Lỗi kết nối khi cập nhật sản phẩm.", "error");
    }).finally(() => {
        if (saveBtn) saveBtn.disabled = false;
    });
}

// ══════════════════════════════════════════════════════════════════
// Pricing warning chip — đọc ngưỡng từ /api/config/pricing (cache 60s)
// ══════════════════════════════════════════════════════════════════
(function initPricingThresholds() {
    fetch('${pageContext.request.contextPath}/api/config/pricing')
        .then(r => r.ok ? r.json() : null)
        .then(j => { if (j && j.thresholds) window.WMS_PRICING_THRESHOLDS = j.thresholds; })
        .catch(() => {});
})();

window.updatePriceWarningChip = function(inputEl) {
    var cfg = (typeof wizChannelConfigs !== 'undefined' && wizChannelConfigs.lazada) || {};
    var mac = Number(cfg.macPrice) || 0;
    var price = Number(inputEl.value) || 0;
    var chip = document.getElementById('wizPriceChip');
    if (!chip) return;
    if (!(mac > 0) || !(price > 0)) { chip.style.display = 'none'; return; }
    var margin = (price - mac) / mac;
    var t = window.WMS_PRICING_THRESHOLDS || {};
    var tLow   = Number(t['pricing.warn_margin_low'])       || 0.10;
    var tBeq   = Number(t['pricing.warn_margin_breakeven']) || 0.00;
    var tLoss  = Number(t['pricing.warn_margin_loss_threshold']) || -0.05;
    if (margin < tLoss) {
        chip.textContent = 'Bán lỗ';
        chip.className = 'price-warn-chip price-warn-chip--loss';
        chip.style.display = 'inline-block';
    } else if (margin < tBeq) {
        chip.textContent = 'Hoà vốn';
        chip.className = 'price-warn-chip price-warn-chip--breakeven';
        chip.style.display = 'inline-block';
    } else if (margin < tLow) {
        chip.textContent = 'Lãi ít';
        chip.className = 'price-warn-chip price-warn-chip--low';
        chip.style.display = 'inline-block';
    } else {
        chip.style.display = 'none';
    }
};
</script>
