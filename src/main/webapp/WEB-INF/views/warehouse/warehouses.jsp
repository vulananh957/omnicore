<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>

<link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/warehouse--warehouses.css"/>

<!-- Stats Grid -->
<div class="stats-grid-3">
    <!-- Stat card 1: Total Warehouses -->
    <div class="stat-card theme-navy">
        <div class="stat-card__icon">
            <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M22 8.35V20a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V8.35A2 2 0 0 1 3.26 6.5l8-3.2a2 2 0 0 1 1.48 0l8 3.2A2 2 0 0 1 22 8.35Z"/>
                <path d="M6 18h12"/>
                <path d="M6 14h12"/>
                <rect width="4" height="6" x="10" y="18" rx="0"/>
            </svg>
        </div>
        <div class="stat-card__info">
            <div class="stat-card__val" id="totalCountEl">0</div>
            <div class="stat-card__lbl">Tổng số chi nhánh kho</div>
        </div>
    </div>

    <!-- Stat card 2: Active Warehouses -->
    <div class="stat-card theme-emerald">
        <div class="stat-card__icon">
            <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                <circle cx="12" cy="12" r="10"/>
                <path d="m9 12 2 2 4-4"/>
            </svg>
        </div>
        <div class="stat-card__info">
            <div class="stat-card__val" id="activeCountEl">0</div>
            <div class="stat-card__lbl">Đang hoạt động</div>
        </div>
    </div>

    <!-- Stat card 3: Closed Warehouses -->
    <div class="stat-card theme-orange">
        <div class="stat-card__icon">
            <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                <circle cx="12" cy="12" r="10"/>
                <line x1="12" x2="12" y1="8" y2="12"/>
                <line x1="12" x2="12.01" y1="16" y2="16"/>
            </svg>
        </div>
        <div class="stat-card__info">
            <div class="stat-card__val" id="closedCountEl">0</div>
            <div class="stat-card__lbl">Tạm đóng cửa</div>
        </div>
    </div>
</div>

<!-- Actions Toolbar -->
<div class="action-bar">
    <div class="search-input-wrap">
        <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="11" cy="11" r="8"/>
            <path d="m21 21-4.3-4.3"/>
        </svg>
        <input class="search-input" type="text" id="warehouseSearch" placeholder="Tìm mã kho, tên kho, địa chỉ..."/>
    </div>
    <button class="btn-add" id="btnAddNewWarehouse">
        <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M5 12h14"/>
            <path d="M12 5v14"/>
        </svg>
        Thêm kho mới
    </button>
</div>

<!-- Table Card wrapper -->
<div class="table-card">
    <div class="table-responsive">
        <table class="wms-table">
            <thead>
                <tr>
                    <th style="width: 130px;">Mã kho</th>
                    <th style="width: 200px;">Tên kho</th>
                    <th>Địa chỉ & Các phân khu (Zones)</th>
                    <th style="width: 130px; text-align: center;">Số điện thoại</th>
                    <th style="width: 140px; text-align: center;">Trạng thái</th>
                    <th style="width: 155px; text-align: center;">Hành động</th>
                </tr>
            </thead>
            <tbody id="warehouseTableBody">
                <!-- Will be dynamically populated -->
            </tbody>
        </table>
    </div>
    <div class="table-footer">
        <span id="showingCountEl">Hiển thị 0 / 0 kho hàng</span>
    </div>
</div>

<!-- ════════════════════════════════════════════════════
    ADD / EDIT MODAL — identical to React WarehouseList modal
    ════════════════════════════════════════════════════ -->
<div class="modal-overlay" id="warehouseModalOverlay">
    <div class="modal-box">
        <!-- Header -->
        <div class="modal-hdr">
            <div class="modal-title-group">
                <h3 class="modal-title" id="modalTitleText">Thêm kho mới (Chi nhánh vật lý)</h3>
                <p class="modal-subtitle">Cấu hình các thông số định danh và phân khu mặc định của kho</p>
            </div>
            <button class="modal-close" id="btnModalClose">&times;</button>
        </div>

        <!-- Body -->
        <div class="modal-body">
            <!-- SECTION 1: BASIC INFORMATION -->
            <div class="section-title-row">
                <h4 class="section-title">Thông tin cơ bản</h4>
                
                <!-- Status Switch -->
                <div class="status-toggle-container">
                    <span class="status-toggle-lbl">Trạng thái:</span>
                    <button type="button" class="btn-switch active" id="btnSwitchStatus" aria-label="Status Toggle">
                        <svg id="toggleIcon" xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                            <rect width="20" height="12" x="2" y="6" rx="6" ry="6"/>
                            <circle cx="16" cy="12" r="2"/>
                        </svg>
                    </button>
                    <span class="status-text-indicator active" id="statusTextIndicator">Đang hoạt động</span>
                </div>
            </div>

            <div class="form-grid">
                <div class="form-group">
                    <label class="form-label" for="whCode">Mã kho *</label>
                    <input class="form-input" type="text" id="whCode" placeholder="VD: WH-HCM-01"/>
                </div>
            </div>

            <div class="form-group">
                <label class="form-label" for="whName">Tên kho *</label>
                <input class="form-input" type="text" id="whName" placeholder="VD: Kho HCM - Quận 1"/>
            </div>

            <div class="form-group">
                <label class="form-label" for="whAddress">Địa chỉ *</label>
                <textarea class="form-input" id="whAddress" rows="2" placeholder="VD: 123 Nguyễn Huệ, Phường Bến Nghé, Quận 1, TP.HCM"></textarea>
            </div>

            <!-- Separator line -->
            <div style="border-top: 1px solid var(--border); margin: 4px 0;"></div>

            <!-- SECTION 2: ZONES CONFIGURATION -->
            <div>
                <h4 class="section-title">
                    <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                        <path d="m12 3-10 5L12 13l10-5-10-5Z"/>
                        <path d="m2 17 10 5 10-5"/>
                        <path d="m2 12 10 5 10-5"/>
                    </svg>
                    Thiết lập phân khu lưu trữ (Zones) trong kho
                </h4>
                <p class="section-desc">
                    Theo quy chuẩn vận hành hệ thống WMS, chi nhánh kho mới khởi tạo sẽ tự động được gán các phân khu tiêu chuẩn để phục vụ đúng luồng các Use Cases (nhập hàng mới, hoàn trả, khiếu nại).
                </p>
            </div>

            <!-- Default checklist -->
            <div class="modal-checklist">
                <!-- Normal Zone -->
                <label class="checklist-item">
                    <input type="checkbox" class="checklist-checkbox" id="chkNormal" checked/>
                    <div class="checklist-info">
                        <div class="checklist-title-line">
                            Khu Hàng Thường (Normal Zone)
                            <span class="checklist-badge checklist-badge-normal">Tiêu chuẩn</span>
                        </div>
                        <p class="checklist-desc">Chứa hàng hóa đạt tiêu chuẩn chất lượng, sẵn sàng phục vụ xuất kho bán hàng.</p>
                    </div>
                </label>

                <!-- Defect Zone -->
                <label class="checklist-item" style="border-top: 1px solid rgba(229, 234, 243, 0.6); padding-top: 10px;">
                    <input type="checkbox" class="checklist-checkbox" id="chkDefect" checked/>
                    <div class="checklist-info">
                        <div class="checklist-title-line">
                            Khu Hàng Hỏng (Defect Zone)
                            <span class="checklist-badge checklist-badge-defect">Bắt buộc</span>
                        </div>
                        <p class="checklist-desc">Lưu trữ hàng hóa bị hư hỏng, lỗi ngoại quan chờ phê duyệt thanh lý hủy bỏ.</p>
                    </div>
                </label>

                <!-- Dispute Zone -->
                <label class="checklist-item" style="border-top: 1px solid rgba(229, 234, 243, 0.6); padding-top: 10px;">
                    <input type="checkbox" class="checklist-checkbox" id="chkDispute" checked/>
                    <div class="checklist-info">
                        <div class="checklist-title-line">
                            Khu Hàng Khiếu Nại (Dispute Zone)
                            <span class="checklist-badge checklist-badge-dispute">Hoàn trả</span>
                        </div>
                        <p class="checklist-desc">Phục vụ cách ly kiện hàng bị khách trả về, chờ nhân viên QC kiểm định chất lượng.</p>
                    </div>
                </label>
            </div>

            <!-- Custom Zones -->
            <div class="custom-zones-box">
                <div class="custom-zones-header">
                    <span class="custom-zones-lbl">Các phân khu tùy chỉnh thêm (Tùy chọn)</span>
                    <button type="button" class="btn-add-cz" id="btnAddCustomZone">
                        <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                            <circle cx="12" cy="12" r="10"/>
                            <path d="M8 12h8"/>
                            <path d="M12 8v8"/>
                        </svg>
                        Thêm khu vực tùy chỉnh
                    </button>
                </div>
                
                <!-- Custom zone list placeholder -->
                <div id="customZonesContainer" style="display: flex; flex-direction: column; gap: 8px;"></div>
            </div>

            <!-- DB Sync Info Callout -->
            <div class="sync-card">
                <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <circle cx="12" cy="12" r="10"/>
                    <path d="M12 16v-4"/>
                    <path d="M12 8h.01"/>
                </svg>
                <div>
                    <span class="sync-card-title">Đồng bộ kiến trúc CSDL (Database Architecture)</span>
                    Khi bạn nhấn lưu kho, hệ thống sẽ thực hiện đồng thời: khởi tạo 1 bản ghi <code>location</code> mới và tự động chèn liên kết khóa ngoại <code>location_id</code> vào bảng <code>zone</code>.
                </div>
            </div>
        </div>

        <!-- Footer -->
        <div class="modal-ftr">
            <button class="btn-modal-cancel" id="btnModalCancel">Hủy</button>
            <button class="btn-modal-save" id="btnModalSave" disabled>
                <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z"/>
                    <polyline points="17 21 17 13 7 13 7 21"/>
                    <polyline points="7 3 7 8 15 8"/>
                </svg>
                Lưu thông tin kho
            </button>
        </div>
    </div>
</div>

<!-- ════════════════════════════════════════════════════
    JAVASCRIPT LOGIC
    ════════════════════════════════════════════════════ -->
<script>
    // In-memory data store. Starts empty and populated from backend.
    window.WMS_WAREHOUSE_DATA = [];

    (function() {
        'use strict';

        // DOM caching
        var totalCountEl = document.getElementById('totalCountEl');
        var activeCountEl = document.getElementById('activeCountEl');
        var closedCountEl = document.getElementById('closedCountEl');
        var warehouseSearch = document.getElementById('warehouseSearch');
        var btnAddNewWarehouse = document.getElementById('btnAddNewWarehouse');
        var warehouseTableBody = document.getElementById('warehouseTableBody');
        var showingCountEl = document.getElementById('showingCountEl');

        // Modal DOM Elements
        var modalOverlay = document.getElementById('warehouseModalOverlay');
        var modalTitleText = document.getElementById('modalTitleText');
        var btnModalClose = document.getElementById('btnModalClose');
        var btnModalCancel = document.getElementById('btnModalCancel');
        var btnModalSave = document.getElementById('btnModalSave');
        var btnSwitchStatus = document.getElementById('btnSwitchStatus');
        var toggleIcon = document.getElementById('toggleIcon');
        var statusTextIndicator = document.getElementById('statusTextIndicator');
        var whCode = document.getElementById('whCode');
        var whName = document.getElementById('whName');
        var whAddress = document.getElementById('whAddress');
        
        // Checklist Checkboxes
        var chkNormal = document.getElementById('chkNormal');
        var chkDefect = document.getElementById('chkDefect');
        var chkDispute = document.getElementById('chkDispute');

        // Custom zones DOM
        var btnAddCustomZone = document.getElementById('btnAddCustomZone');
        var customZonesContainer = document.getElementById('customZonesContainer');

        // Local state variables for form
        var editingWarehouseId = null; // null means adding new, otherwise ID of edited
        var currentFormStatus = "active"; // "active" | "closed"
        var currentCustomZones = []; // list of { id: string, name: string }

        // Filter text
        var searchText = "";

        // Bind standard event listeners
        warehouseSearch.addEventListener('input', function(e) {
            searchText = e.target.value;
            renderWarehouses();
        });

        btnAddNewWarehouse.addEventListener('click', function() {
            openModal(null);
        });

        btnModalClose.addEventListener('click', closeModal);
        btnModalCancel.addEventListener('click', closeModal);

        // Status switch logic
        btnSwitchStatus.addEventListener('click', function() {
            if (currentFormStatus === "active") {
                setFormStatus("closed");
            } else {
                setFormStatus("active");
            }
        });

        // Add custom zone
        btnAddCustomZone.addEventListener('click', function() {
            var newId = 'cz-' + Math.random().toString(36).substring(2, 9);
            currentCustomZones.push({ id: newId, name: "" });
            renderCustomZonesForm();
        });

        // Form Validation triggers
        var formFields = [whCode, whName, whAddress];
        formFields.forEach(function(field) {
            field.addEventListener('input', validateForm);
        });

        btnModalSave.addEventListener('click', saveForm);

        // Function to update Form Toggle buttons state visually
        function setFormStatus(status) {
            currentFormStatus = status;
            if (status === "active") {
                btnSwitchStatus.classList.add('active');
                statusTextIndicator.className = "status-text-indicator active";
                statusTextIndicator.textContent = "Đang hoạt động";
                // SVG for ToggleRight
                toggleIcon.innerHTML = '<rect width="20" height="12" x="2" y="6" rx="6" ry="6"/><circle cx="16" cy="12" r="2"/>';
            } else {
                btnSwitchStatus.classList.remove('active');
                statusTextIndicator.className = "status-text-indicator closed";
                statusTextIndicator.textContent = "Tạm ngưng";
                // SVG for ToggleLeft
                toggleIcon.innerHTML = '<rect width="20" height="12" x="2" y="6" rx="6" ry="6"/><circle cx="8" cy="12" r="2"/>';
            }
        }

        // Custom zones rendering in Form modal
        function renderCustomZonesForm() {
            customZonesContainer.innerHTML = "";
            currentCustomZones.forEach(function(cz, index) {
                var rowDiv = document.createElement('div');
                rowDiv.className = "cz-row";

                var numSpan = document.createElement('span');
                numSpan.className = "cz-label";
                numSpan.textContent = "ZONE #" + (index + 1);

                var input = document.createElement('input');
                input.className = "cz-input";
                input.type = "text";
                input.value = cz.name;
                input.placeholder = "Ví dụ: Khu Hàng Dự Trữ, Khu Hàng Khuyến Mãi...";
                input.addEventListener('input', function(e) {
                    cz.name = e.target.value;
                });

                var removeBtn = document.createElement('button');
                removeBtn.type = "button";
                removeBtn.className = "btn-remove-cz";
                removeBtn.title = "Xóa khu vực này";
                removeBtn.innerHTML = '<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 6h18"/><path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6"/><path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2"/><line x1="10" x2="10" y1="11" y2="17"/><line x1="14" x2="14" y1="11" y2="17"/></svg>';
                removeBtn.addEventListener('click', function() {
                    currentCustomZones = currentCustomZones.filter(function(item) {
                        return item.id !== cz.id;
                    });
                    renderCustomZonesForm();
                });

                rowDiv.appendChild(numSpan);
                rowDiv.appendChild(input);
                rowDiv.appendChild(removeBtn);
                customZonesContainer.appendChild(rowDiv);
            });
        }

        function validateForm() {
            var codeVal = whCode.value.trim();
            var nameVal = whName.value.trim();
            var addressVal = whAddress.value.trim();

            var isValid = codeVal && nameVal && addressVal;
            btnModalSave.disabled = !isValid;
        }

        // Open Modal Handler
        function openModal(warehouse) {
            if (warehouse) {
                // Edit mode
                editingWarehouseId = warehouse.id;
                modalTitleText.textContent = "Sửa thông tin kho chi nhánh";
                
                whCode.value = warehouse.code;
                whName.value = warehouse.name;
                whAddress.value = warehouse.address;

                setFormStatus(warehouse.status);

                // Detect check status of standard zones
                var hasNormal = warehouse.zones.some(function(z) { return z.name.indexOf("Normal") !== -1 || z.name.indexOf("Thường") !== -1; });
                var hasDefect = warehouse.zones.some(function(z) { return z.name.indexOf("Defect") !== -1 || z.name.indexOf("Hỏng") !== -1; });
                var hasDispute = warehouse.zones.some(function(z) { return z.name.indexOf("Dispute") !== -1 || z.name.indexOf("Khiếu Nại") !== -1; });

                chkNormal.checked = hasNormal;
                chkDefect.checked = hasDefect;
                chkDispute.checked = hasDispute;

                // Load custom zones
                currentCustomZones = [];
                warehouse.zones.forEach(function(z) {
                    if (!z.isDefault) {
                        currentCustomZones.push({ id: z.id, name: z.name });
                    }
                });
            } else {
                // Add new mode
                editingWarehouseId = null;
                modalTitleText.textContent = "Thêm kho mới (Chi nhánh vật lý)";

                whCode.value = "";
                whName.value = "";
                whAddress.value = "";

                setFormStatus("active");

                chkNormal.checked = true;
                chkDefect.checked = true;
                chkDispute.checked = true;

                currentCustomZones = [];
            }

            renderCustomZonesForm();
            validateForm();
            modalOverlay.classList.add('active');
        }

        function closeModal() {
            modalOverlay.classList.remove('active');
            editingWarehouseId = null;
        }

        // Save Modal Form
        function saveForm() {
            var codeVal = whCode.value.trim().toUpperCase();
            var nameVal = whName.value.trim();
            var addressVal = whAddress.value.trim();

            var generatedZones = [];

            // Add standard default zones if checked.
            // IMPORTANT: suffix convention must match WarehouseService.java which uses
            // zoneType.substring(0,4).toUpperCase() → NORM, DAMA, RETU
            // When editing an existing warehouse, prefer the real zone code from DB to avoid
            // mis-match that causes a false "duplicate code" SQL error.
            var existingNormalCode  = null;
            var existingDefectCode  = null;
            var existingDisputeCode = null;
            if (editingWarehouseId) {
                var whData = window.WMS_WAREHOUSE_DATA.find(function(w) { return w.id === parseInt(editingWarehouseId); });
                if (whData && whData.zones) {
                    whData.zones.forEach(function(z) {
                        var nl = z.name.toLowerCase();
                        if (nl.indexOf('normal') !== -1 || nl.indexOf('thường') !== -1) existingNormalCode  = z.code;
                        else if (nl.indexOf('defect') !== -1 || nl.indexOf('hỏng') !== -1)  existingDefectCode  = z.code;
                        else if (nl.indexOf('dispute') !== -1 || nl.indexOf('khiếu nại') !== -1) existingDisputeCode = z.code;
                    });
                }
            }
            if (chkNormal.checked) {
                generatedZones.push({
                    id: 0,
                    code: existingNormalCode  || (codeVal + '-NORM'),
                    name: "Khu Hàng Thường (Normal Zone)",
                    zoneType: "NORMAL",
                    isDefault: true
                });
            }
            if (chkDefect.checked) {
                generatedZones.push({
                    id: 0,
                    code: existingDefectCode  || (codeVal + '-DAMA'),
                    name: "Khu Hàng Hỏng (Defect Zone)",
                    zoneType: "DAMAGED",
                    isDefault: true
                });
            }
            if (chkDispute.checked) {
                generatedZones.push({
                    id: 0,
                    code: existingDisputeCode || (codeVal + '-RETU'),
                    name: "Khu Hàng Khiếu Nại (Dispute Zone)",
                    zoneType: "RETURN",
                    isDefault: true
                });
            }

            // Add custom zones
            currentCustomZones.forEach(function(cz, idx) {
                if (cz.name.trim()) {
                    var isNew = cz.id.toString().indexOf('cz-') === 0;
                    generatedZones.push({
                        id: isNew ? 0 : parseInt(cz.id),
                        code: codeVal + '-CUST-' + (idx + 1),
                        name: cz.name.trim(),
                        zoneType: "NORMAL",
                        isDefault: false
                    });
                }
            });

            var payload = {
                id: editingWarehouseId ? parseInt(editingWarehouseId) : 0,
                code: codeVal,
                name: nameVal,
                address: addressVal,
                status: currentFormStatus,
                zones: generatedZones
            };

            fetch(window.location.pathname + '?action=save', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(payload)
            })
            .then(function(res) { return res.json(); })
            .then(function(resData) {
                if (resData.success) {
                    closeModal();
                    fetchWarehouses();
                } else {
                    alert(resData.message || 'Lỗi khi lưu kho hàng.');
                }
            })
            .catch(function(err) {
                console.error('Error saving warehouse:', err);
                alert('Có lỗi mạng xảy ra khi lưu kho hàng.');
            });
        }

        // Fetch warehouses from backend API
        function fetchWarehouses() {
            fetch(window.location.pathname + '?action=list')
                .then(function(res) { return res.json(); })
                .then(function(data) {
                    window.WMS_WAREHOUSE_DATA = data;
                    renderWarehouses();
                })
                .catch(function(err) {
                    console.error('Error fetching warehouses:', err);
                });
        }

        // Render function
        function renderWarehouses() {
            var list = window.WMS_WAREHOUSE_DATA;
            
            // Search filters
            var filtered = list.filter(function(w) {
                var term = searchText.toLowerCase();
                return w.code.toLowerCase().indexOf(term) !== -1 ||
                       w.name.toLowerCase().indexOf(term) !== -1 ||
                       w.address.toLowerCase().indexOf(term) !== -1;
            });

            // Calculate KPIs
            var totalCount = list.length;
            var activeCount = list.filter(function(w) { return w.status === 'active'; }).length;
            var closedCount = list.filter(function(w) { return w.status === 'closed'; }).length;

            totalCountEl.textContent = totalCount;
            activeCountEl.textContent = activeCount;
            closedCountEl.textContent = closedCount;

            showingCountEl.textContent = "Hiển thị " + filtered.length + " / " + totalCount + " kho hàng";

            // Clear table
            warehouseTableBody.innerHTML = "";

            if (filtered.length === 0) {
                // Empty state rendering
                var tr = document.createElement('tr');
                var td = document.createElement('td');
                td.colSpan = 6;
                td.style.padding = "0";

                var emptyDiv = document.createElement('div');
                emptyDiv.className = "empty-state";
                emptyDiv.innerHTML = '<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M22 8.35V20a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V8.35A2 2 0 0 1 3.26 6.5l8-3.2a2 2 0 0 1 1.48 0l8 3.2A2 2 0 0 1 22 8.35Z"/><rect width="4" height="6" x="10" y="18" rx="0"/></svg>' +
                                     '<div class="empty-state-title">Chưa có chi nhánh kho nào</div>' +
                                     '<div class="empty-state-desc">Hãy nhấn nút "Thêm kho mới" ở góc trên bên phải để bắt đầu thiết lập chi nhánh kho hàng đầu tiên.</div>';
                
                td.appendChild(emptyDiv);
                tr.appendChild(td);
                warehouseTableBody.appendChild(tr);
                return;
            }

            // Populate rows
            filtered.forEach(function(warehouse) {
                var tr = document.createElement('tr');

                // Column 1: Code
                var tdCode = document.createElement('td');
                tdCode.innerHTML = '<div class="code-cell-wrapper">' +
                                    '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 8.35V20a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V8.35A2 2 0 0 1 3.26 6.5l8-3.2a2 2 0 0 1 1.48 0l8 3.2A2 2 0 0 1 22 8.35Z"/><path d="M6 18h12"/><path d="M6 14h12"/><rect width="4" height="6" x="10" y="18" rx="0"/></svg>' +
                                    '<span class="warehouse-code">' + escapeHtml(warehouse.code) + '</span>' +
                                   '</div>';
                tr.appendChild(tdCode);

                // Column 2: Name
                var tdName = document.createElement('td');
                tdName.innerHTML = '<div class="name-cell">' + escapeHtml(warehouse.name) + '</div>';
                tr.appendChild(tdName);

                // Column 3: Address & Zones
                var tdAddress = document.createElement('td');
                var addressHtml = '<div class="address-row">' +
                                    '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20 10c0 6-8 12-8 12s-8-6-8-12a8 8 0 0 1 16 0Z"/><circle cx="12" cy="10" r="3"/></svg>' +
                                    '<span>' + escapeHtml(warehouse.address) + '</span>' +
                                  '</div>';

                if (warehouse.zones && warehouse.zones.length > 0) {
                    addressHtml += '<div class="zones-row">' +
                                     '<span class="zones-label">' +
                                       '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m12 3-10 5L12 13l10-5-10-5Z"/><path d="m2 17 10 5 10-5"/><path d="m2 12 10 5 10-5"/></svg>' +
                                       'Zones (' + warehouse.zones.length + '):' +
                                     '</span>';
                    
                    warehouse.zones.forEach(function(zone) {
                        var tagStyleClass = "zone-tag-custom";
                        var nameLower = zone.name.toLowerCase();
                        if (nameLower.indexOf("normal") !== -1 || nameLower.indexOf("thường") !== -1) {
                            tagStyleClass = "zone-tag-normal";
                        } else if (nameLower.indexOf("defect") !== -1 || nameLower.indexOf("hỏng") !== -1) {
                            tagStyleClass = "zone-tag-defect";
                        } else if (nameLower.indexOf("dispute") !== -1 || nameLower.indexOf("khiếu nại") !== -1) {
                            tagStyleClass = "zone-tag-dispute";
                        }

                        // Remove standard subtitle " (Normal Zone)" etc. for tag visual cleanliness
                        var displayName = zone.name.split(" (")[0];

                        addressHtml += '<span class="zone-tag ' + tagStyleClass + '" title="' + escapeHtml(zone.code) + '">' +
                                         escapeHtml(displayName) +
                                       '</span>';
                    });
                    addressHtml += '</div>';
                }

                tdAddress.innerHTML = addressHtml;
                tr.appendChild(tdAddress);

                // Column 4: Phone
                var tdPhone = document.createElement('td');
                tdPhone.style.textAlign = "center";
                tdPhone.innerHTML = '<div class="phone-cell">' +
                                      '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z"/></svg>' +
                                      escapeHtml(warehouse.phone) +
                                    '</div>';
                tr.appendChild(tdPhone);

                // Column 5: Status
                var tdStatus = document.createElement('td');
                tdStatus.style.textAlign = "center";
                if (warehouse.status === "active") {
                    tdStatus.innerHTML = '<span class="status-badge status-badge-active">Đang hoạt động</span>';
                } else {
                    tdStatus.innerHTML = '<span class="status-badge status-badge-closed">Tạm đóng cửa</span>';
                }
                tr.appendChild(tdStatus);

                // Column 6: Actions
                var tdActions = document.createElement('td');
                tdActions.style.textAlign = "center";
                
                var actionWrapper = document.createElement('div');
                actionWrapper.className = "actions-wrap";

                var editBtn = document.createElement('button');
                editBtn.className = "btn-edit";
                editBtn.title = "Sửa thông tin & Zones";
                editBtn.innerHTML = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 20h9"/><path d="M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4Z"/></svg>';
                editBtn.addEventListener('click', function() {
                    openModal(warehouse);
                });

                var statusBtn = document.createElement('button');
                if (warehouse.status === "active") {
                    statusBtn.className = "btn-status-toggle btn-status-toggle-active";
                    statusBtn.textContent = "Đóng cửa";
                } else {
                    statusBtn.className = "btn-status-toggle btn-status-toggle-closed";
                    statusBtn.textContent = "Mở lại";
                }
                statusBtn.addEventListener('click', function() {
                    toggleWarehouseStatus(warehouse.id);
                });

                actionWrapper.appendChild(editBtn);
                actionWrapper.appendChild(statusBtn);
                tdActions.appendChild(actionWrapper);
                tr.appendChild(tdActions);

                warehouseTableBody.appendChild(tr);
            });
        }

        // Toggle status handler
        function toggleWarehouseStatus(id) {
            var currentWh = window.WMS_WAREHOUSE_DATA.find(function(w) { return w.id === id; });
            if (!currentWh) return;
            
            var nextActive = currentWh.status !== 'active';
            
            fetch(window.location.pathname + '?action=toggleStatus&id=' + id + '&active=' + nextActive, {
                method: 'POST'
            })
            .then(function(res) { return res.json(); })
            .then(function(resData) {
                if (resData.success) {
                    fetchWarehouses();
                } else {
                    alert(resData.message || 'Không thể thay đổi trạng thái kho hàng.');
                }
            })
            .catch(function(err) {
                console.error('Error toggling status:', err);
                alert('Có lỗi mạng xảy ra khi cập nhật trạng thái.');
            });
        }

        // Helper: Escape HTML to avoid XSS injections
        function escapeHtml(text) {
            if (!text) return "";
            return text.toString()
                .replace(/&/g, "&amp;")
                .replace(/</g, "&lt;")
                .replace(/>/g, "&gt;")
                .replace(/"/g, "&quot;")
                .replace(/'/g, "&#039;");
        }

        // Initialize table render from backend
        fetchWarehouses();
    })();
</script>
