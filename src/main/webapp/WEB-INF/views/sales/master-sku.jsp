<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ page import="com.wms.model.Product" %>
<%@ page import="java.util.List" %>
<%@ page import="com.fasterxml.jackson.databind.ObjectMapper" %>
<%
    response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
    response.setHeader("Pragma", "no-cache");
    response.setDateHeader("Expires", 0);

    List<Product> products = (List<Product>) request.getAttribute("products");
    if (products == null) products = java.util.Collections.emptyList();

    ObjectMapper mapper = new ObjectMapper();
    String productsJson = mapper.valueToTree(products).toString();
    request.setAttribute("productsJson", productsJson);
%>
<link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/sku--master-sku.css"/>

<!-- Toast Notification Element -->
<div id="skuToast" class="toast-notification">
    <span id="skuToastIcon">✓</span>
    <span id="skuToastMsg">Cập nhật thành công!</span>
</div>



<!-- ══ TOOLBAR SECTION ═══════════════════════════════════════ -->
<div class="toolbar-wrap">
    <div class="filters-left">
        <!-- Search -->
        <div class="search-input-wrap">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"></svg>
            <input type="text" placeholder="Tìm mã SKU, tên sản phẩm..." id="skuSearchInput"/>
        </div>
        
        <!-- Category Select -->
        <div class="select-wrap">
            <select id="skuCategorySelect">
                <option>Tất cả</option>
                <c:forEach var="c" items="${categories}">
                    <option><c:out value="${c.categoryName}"/></option>
                </c:forEach>
            </select>
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="6 9 12 15 18 9"></svg>
        </div>
    </div>
    
    <button class="btn-export" id="btnExportCSV">
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="7 10 12 15 17 10"></polyline><line x1="12" y1="15" x2="12" y2="3"></line></svg>
        Xuất CSV
    </button>

    <!-- Create SKU — only for SALES_STAFF -->
    <c:if test="${loggedInUser.role == 'SALES_STAFF'}">
    <button class="btn-add-sku" id="btnCreateSKUTrigger">
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="12" y1="5" x2="12" y2="19"></line><line x1="5" y1="12" x2="19" y2="12"></line></svg>
        Thêm SKU mới
    </button>
    </c:if>
</div>

<!-- ══ TABLE SECTION ═════════════════════════════════════════ -->
<div class="table-card">
    <div class="table-scroll">
        <table class="sku-table">
            <thead>
                <tr>
                    <th style="width: 130px;">Mã SKU</th>
                    <th style="width: 180px;">Tên sản phẩm</th>
                    <th style="width: 90px;">Danh mục</th>
                    <th style="width: 140px; text-align: center;">KL / Kích thước</th>
                    <th style="width: 90px; text-align: right;">Tồn kho</th>
                    <th style="width: 185px;">Thông tin</th>
                    <th style="width: 120px; text-align: center;">Thao tác</th>
                </tr>
            </thead>
            <tbody id="skuTableBody"></tbody>
        </table>
    </div>
    
    <div class="table-footer">
        <span id="skuTableInfo">Hiển thị 0 / 0 SKU</span>
    </div>
</div>

<!-- ══ CREATE MODAL ══════════════════════════════════════════ -->
<div id="createModalOverlay" style="display:none; position:fixed; inset:0; background:rgba(16,55,92,0.55); backdrop-filter:blur(4px); z-index:9999; align-items:flex-start; justify-content:center; padding:32px 16px;">
    <div class="modal-box" style="background:#fff; width:100%; max-width:680px; border-radius:8px; box-shadow:0 20px 25px -5px rgba(16,55,92,0.15), 0 10px 10px -5px rgba(16,55,92,0.1); display:flex; flex-direction:column; animation:modalBoxSlideIn 0.2s ease;">
        <div class="modal-hdr">
                <div>
                <h2 class="modal-title">Tạo Master SKU</h2>
                <p class="modal-subtitle">Khai báo thông tin gốc sản phẩm</p>
                </div>
            <button class="modal-close" id="createModalClose">&times;</button>
            </div>
        <div class="modal-body">
            <div class="form-group">
                <label class="form-label" for="create-sku">Mã SKU *</label>
                <div style="display: flex; gap: 8px; align-items: stretch;">
                    <input class="form-input" type="text" id="create-sku" readonly placeholder="Bấm nút Auto để sinh mã..." style="flex:1; background:rgba(16,55,92,0.03); cursor:not-allowed; font-weight:bold; color:var(--navy);"/>
                    <button type="button" class="btn-export" id="btnAutoGenSku" title="Tự động tạo SKU" style="white-space:nowrap; padding:0 14px; height:38px;">
                        <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="16 3 21 3 21 8"/><line x1="4" y1="20" x2="21" y2="3"/><polyline points="21 16 21 21 16 21"/><line x1="15" y1="15" x2="21" y2="21"/><line x1="4" y1="4" x2="9" y2="9"/></svg>
                        Auto
                    </button>
            </div>
                <input type="hidden" id="create-category-id" value=""/>
        </div>
            <div class="form-group">
                <label class="form-label" for="create-name">Tên sản phẩm *</label>
                <input class="form-input" type="text" id="create-name" placeholder="Ví dụ: Lược chải tóc gỡ rối - Màu hồng"/>
            </div>
            <div class="form-group">
                <label class="form-label" style="margin-bottom: 2px;">Danh mục hàng hóa * <span style="font-weight:400; color:rgba(16,55,92,0.40);">(chọn từ cây danh mục bên dưới)</span></label>
                <input type="text" class="form-input" id="selectedCategoryDisplay" readonly placeholder="Nhấp chọn danh mục..." style="cursor:default; font-weight:600; background:#fff;"/>
                <input type="hidden" id="create-category" value=""/>
                <div class="category-tree-picker-box" id="categoryTreePicker"></div>
                </div>
            <div class="form-grid" style="grid-template-columns: 1.8fr 1fr; gap: 12px;">
                <div class="form-group">
                    <label class="form-label">Kích thước (D×R×C) cm</label>
                    <div style="display:flex; gap:6px; align-items:center;">
                        <input class="form-input" type="number" id="create-dim-length" min="0" step="any" placeholder="Dài" style="flex:1; min-width:0; width:0; text-align:center; padding:10px 4px;"/>
                        <span style="color:rgba(16,55,92,0.35); font-size:13px;">×</span>
                        <input class="form-input" type="number" id="create-dim-width"  min="0" step="any" placeholder="Rộng" style="flex:1; min-width:0; width:0; text-align:center; padding:10px 4px;"/>
                        <span style="color:rgba(16,55,92,0.35); font-size:13px;">×</span>
                        <input class="form-input" type="number" id="create-dim-height" min="0" step="any" placeholder="Cao" style="flex:1; min-width:0; width:0; text-align:center; padding:10px 4px;"/>
                </div>
            </div>
                <div class="form-group">
                    <label class="form-label" for="create-weight">Khối lượng (kg)</label>
                    <input class="form-input" type="number" id="create-weight" min="0" step="any" placeholder="VD: 0.28" style="padding:10px 6px; min-width:0; width:100%;"/>
            </div>
        </div>
        <input type="hidden" id="create-base-price" value=""/>
        </div>
        <div class="modal-ftr">
            <button class="modal-close btn-export" id="createModalCancel" style="padding:9px 16px;">Hủy</button>
            <button class="btn-add-sku" id="btnCreateSKUSubmit">Tạo SKU</button>
        </div>
    </div>
</div>

<!-- ══ EDIT MODAL ══════════════════════════════════════════ -->
<div id="editModalOverlay" style="display:none; position:fixed; inset:0; background:rgba(16,55,92,0.55); backdrop-filter:blur(4px); z-index:9999; align-items:flex-start; justify-content:center; padding:32px 16px;">
    <div class="modal-box" style="background:#fff; width:100%; max-width:680px; border-radius:8px; box-shadow:0 20px 25px -5px rgba(16,55,92,0.15), 0 10px 10px -5px rgba(16,55,92,0.1); display:flex; flex-direction:column; animation:modalBoxSlideIn 0.2s ease;">
        <div class="modal-hdr">
                <div>
                <h2 class="modal-title">Chỉnh sửa SKU</h2>
                <p class="modal-subtitle" id="edit-sku-code-label">SKU-XXXX</p>
                </div>
            <button class="modal-close" id="editModalClose">&times;</button>
            </div>
        <div class="modal-body">
            <input type="hidden" id="edit-id"/>
            <div class="form-group">
                <label class="form-label" for="edit-name">Tên sản phẩm *</label>
                <input class="form-input" type="text" id="edit-name" placeholder="Tên sản phẩm..."/>
            </div>
            <div class="form-group">
                <label class="form-label" style="margin-bottom: 2px;">Danh mục hàng hóa * <span style="font-weight:400; color:rgba(16,55,92,0.40);">(chọn từ cây danh mục bên dưới)</span></label>
                <input type="text" class="form-input" id="selectedEditCategoryDisplay" readonly placeholder="Nhấp chọn danh mục..." style="cursor:default; font-weight:600; background:#fff;"/>
                <input type="hidden" id="edit-category" value=""/>
                <div class="category-tree-picker-box" id="editCategoryTreePicker"></div>
        </div>
            <div class="form-grid" style="grid-template-columns: 1.8fr 1fr; gap: 12px;">
                <div class="form-group">
                    <label class="form-label">Kích thước (D×R×C) cm</label>
                    <div style="display:flex; gap:6px; align-items:center;">
                        <input class="form-input" type="number" id="edit-dim-length" min="0" step="any" placeholder="Dài" style="flex:1; min-width:0; width:0; text-align:center; padding:10px 4px;"/>
                        <span style="color:rgba(16,55,92,0.35); font-size:13px;">×</span>
                        <input class="form-input" type="number" id="edit-dim-width"  min="0" step="any" placeholder="Rộng" style="flex:1; min-width:0; width:0; text-align:center; padding:10px 4px;"/>
                        <span style="color:rgba(16,55,92,0.35); font-size:13px;">×</span>
                        <input class="form-input" type="number" id="edit-dim-height" min="0" step="any" placeholder="Cao" style="flex:1; min-width:0; width:0; text-align:center; padding:10px 4px;"/>
                    </div>
                </div>
                <div class="form-group">
                    <label class="form-label" for="edit-weight">Khối lượng (kg)</label>
                    <input class="form-input" type="number" id="edit-weight" min="0" step="any" placeholder="VD: 0.28" style="padding:10px 6px; min-width:0; width:100%;"/>
                </div>
            </div>
            <div class="form-group">
                <label class="form-label" for="edit-base-price">Giá nhập (VNĐ)</label>
                <input class="form-input" type="number" id="edit-base-price" min="0" step="1000" placeholder="VD: 189000" style="padding:10px 6px; min-width:0; width:100%;"/>
            </div>
            <input type="hidden" id="edit-barcode"/>
            <div class="form-group">
                <label class="form-label" for="edit-unit">Đơn vị tính</label>
                <input class="form-input" type="text" id="edit-unit" placeholder="VD: Cái, Hộp..."/>
            </div>
        </div>
        <div class="modal-ftr">
            <button class="btn-cancel" id="editModalCancel">Hủy</button>
            <button class="btn-submit" id="btnEditSKUSubmit">Lưu thay đổi</button>
        </div>
    </div>
</div>

<div id="productsJsonData" style="display:none;"><c:out value="${productsJson}"/></div>
<div id="categoriesJsonData" style="display:none;"><c:out value="${categoriesJson}"/></div>

<!-- ══ SCRIPT LOGIC ══════════════════════════════════════════ -->
<script>
// Expose JSTL session user details to client-side
window.WMS_USER = {
    fullName: "${fn:escapeXml(not empty loggedInUser.fullName ? loggedInUser.fullName : 'Guest')}",
    role: "${fn:escapeXml(not empty loggedInUser.role ? loggedInUser.role : 'Guest')}"
};

function showToast(message, isError) {
    var toast = document.getElementById("skuToast");
    var msgSpan = document.getElementById("skuToastMsg");
    var iconSpan = document.getElementById("skuToastIcon");
    if (!toast || !msgSpan || !iconSpan) return;

    msgSpan.textContent = message;
    iconSpan.textContent = isError ? "✕" : "✓";
    toast.className = "toast-notification show";
    if (isError) {
        toast.classList.add("error");
    }

    setTimeout(function() {
        toast.classList.remove("show");
    }, 4000);
}

// Check for flash messages from Servlet
(function() {
    var errorMsg = "${fn:escapeXml(errorMessage)}";
    var successMsg = "${fn:escapeXml(successMessage)}";
    if (errorMsg && errorMsg.trim() !== "" && errorMsg.indexOf('errorMessage') === -1) {
        showToast(errorMsg, true);
    } else if (successMsg && successMsg.trim() !== "" && successMsg.indexOf('successMessage') === -1) {
        showToast(successMsg, false);
    }
})();

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

(function () {
'use strict';

var skus = [];
try {
    var rawJsonEl = document.getElementById('productsJsonData');
    var rawJson = rawJsonEl ? rawJsonEl.textContent : '';
    if (rawJson && rawJson.trim()) {
        var SERVER_PRODUCTS = JSON.parse(rawJson);
        skus = SERVER_PRODUCTS.map(function(p) {
    return {
                id: 'p-' + (p.productId || 0),
                productId: p.productId || 0,
        sku: p.sku || p.skuCode || '',
        name: p.name || p.productName || '',
                categoryId: p.categoryId,
                barcode: p.barcode || '',
                unit: p.unit || '',
                basePrice: p.basePrice || 0,
        category: p.category || p.categoryName || '',
        dimensions: p.dimensions || p.attributesText || 'N/A',
        weight: p.weight || (p.weightKg ? p.weightKg + ' kg' : 'N/A'),
        qtyOnHand: typeof p.qtyOnHand !== 'undefined' ? p.qtyOnHand : 0,
        minStock: p.minStock || 0,
        maxStock: p.maxStock || 0,
        createdBy: p.creatorName || p.createdBy || '',
        createdAt: p.createdAt || '',
        updatedBy: p.creatorName || p.createdBy || '',
                lastUpdated: p.lastUpdated || p.updatedAt || ''
    };
});
    }
} catch (e) {
    console.warn('master-sku: No server product data');
}


var DB_CATEGORIES = [];
try {
    var rawCategoriesJsonEl = document.getElementById('categoriesJsonData');
    var rawCategoriesJson = rawCategoriesJsonEl ? rawCategoriesJsonEl.textContent : '';
    if (rawCategoriesJson && rawCategoriesJson.trim()) {
        var parsed = JSON.parse(rawCategoriesJson);
        DB_CATEGORIES = parsed.map(function(c) {
            return {
                categoryId: c.id,
                categoryName: c.name,
                categoryCode: c.code,
                parentId: c.parentId,
                description: c.description,
                immutable: c.immutable,
                active: c.active
            };
        });
    }
} catch (e) {
    console.warn('master-sku: Failed to parse categoriesJson', e);
}

function buildCategoryTreeOptions(categories, isFilter) {
    var html = isFilter ? '<option value="Tất cả">Tất cả</option>' : '';
    
    function recurse(parentId, prefix) {
        var levelNodes = categories.filter(function(c) {
            var nodeParentId = c.parentId;
            if (parentId === null) {
                return nodeParentId === null || nodeParentId === 0 || nodeParentId === 'null';
            }
            return nodeParentId == parentId;
        });
        
        levelNodes.forEach(function(node) {
            html += '<option value="' + escapeHtml(node.categoryName) + '">' + prefix + escapeHtml(node.categoryName) + '</option>';
            recurse(node.categoryId, prefix + '    ');
        });
    }
    
    recurse(null, '');
    return html;
}

function escapeHtml(str) {
    if (!str) return '';
    return str
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#039;");
}

var search = '';
var selectedCategory = 'Tất cả';

/* ─── DOM Elements ───────────────────────────────────────── */
var tableBody   = document.getElementById('skuTableBody');
var tableInfo   = document.getElementById('skuTableInfo');
var searchInput = document.getElementById('skuSearchInput');
var catSelect   = document.getElementById('skuCategorySelect');

if (catSelect && DB_CATEGORIES.length > 0) {
    catSelect.innerHTML = buildCategoryTreeOptions(DB_CATEGORIES, true);
}

/* ─── Create Modal — Category Tree Picker ──────────────────── */
var selectedPickerCategory = '';
var expandedPickerNodes = {};

function renderPickerTree() {
    var pickerContainer = document.getElementById('categoryTreePicker');
    if (!pickerContainer) return;

    var roots = DB_CATEGORIES.filter(function (c) {
        return c.parentId === null || c.parentId === 0 || c.parentId === 'null';
    });

    if (roots.length === 0) {
        pickerContainer.innerHTML = '<div style="color:rgba(16,55,92,0.4);text-align:center;padding:24px;">Chưa có danh mục nào.</div>';
        return;
    }

    function buildPickerNodeHtml(node, level) {
        var children = DB_CATEGORIES.filter(function (c) { return c.parentId === node.categoryId; });
        var hasChildren = children.length > 0;

        if (expandedPickerNodes[node.categoryId] === undefined) {
            expandedPickerNodes[node.categoryId] = true;
        }
        var isExpanded = expandedPickerNodes[node.categoryId];
        var isSelected = selectedPickerCategory === node.categoryName;
        var indent = level * 16;

        var html = '<div class="tree-node-wrapper">';
        html += '<div class="tree-node' + (isSelected ? ' selected' : '') + '"'
            + ' onclick="window.selectPickerCategory(decodeURIComponent(\'' + encodeURIComponent(node.categoryName) + '\'))"'
            + ' style="padding-left:' + indent + 'px">';

        if (hasChildren) {
            html += '<span class="tree-expand-btn"'
                + ' onclick="event.stopPropagation();window.togglePickerNode(' + node.categoryId + ')">'
                + '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"'
                + ' style="transform:rotate(' + (isExpanded ? '90deg' : '0deg') + ');transition:transform 0.15s">'
                + '<polyline points="9 18 15 12 9 6"/></svg></span>';
            } else {
            html += '<span class="tree-leaf-spacer"></span>';
        }

        html += '<span class="tree-node-label">' + escapeHtml(node.categoryName) + '</span>';
        html += '</div>';

        if (hasChildren && isExpanded) {
            html += '<div class="tree-children">';
            children.forEach(function (child) {
                html += buildPickerNodeHtml(child, level + 1);
            });
            html += '</div>';
        }

        html += '</div>';
        return html;
    }

    var html = '<div style="display:flex;flex-direction:column;gap:4px;">';
    roots.forEach(function (root) {
        html += buildPickerNodeHtml(root, 0);
    });
    html += '</div>';
    pickerContainer.innerHTML = html;
}

window.togglePickerNode = function (nodeId) {
    expandedPickerNodes[nodeId] = !expandedPickerNodes[nodeId];
    renderPickerTree();
};

window.selectPickerCategory = function (categoryName) {
    selectedPickerCategory = categoryName;
    var catIdInput = document.getElementById('create-category-id');
    var catDisplay = document.getElementById('selectedCategoryDisplay');
    var catHidden = document.getElementById('create-category');
    var found = DB_CATEGORIES.find(function (c) { return c.categoryName === categoryName; });

    if (catDisplay) catDisplay.value = categoryName;
    if (catHidden) catHidden.value = categoryName;
    if (catIdInput && found) catIdInput.value = found.categoryId || '';

    renderPickerTree();
};

if (DB_CATEGORIES.length > 0) {
    renderPickerTree();
}

/* ─── Edit Modal — Category Tree Picker ──────────────────── */
var selectedEditPickerCategory = '';
var expandedEditPickerNodes = {};

function renderEditPickerTree() {
    var pickerContainer = document.getElementById('editCategoryTreePicker');
    if (!pickerContainer) return;

    var roots = DB_CATEGORIES.filter(function (c) {
        return c.parentId === null || c.parentId === 0 || c.parentId === 'null';
    });

    if (roots.length === 0) {
        pickerContainer.innerHTML = '<div style="color:rgba(16,55,92,0.4);text-align:center;padding:24px;">Chưa có danh mục nào.</div>';
        return;
    }

    function buildPickerNodeHtml(node, level) {
        var children = DB_CATEGORIES.filter(function (c) { return c.parentId === node.categoryId; });
        var hasChildren = children.length > 0;

        if (expandedEditPickerNodes[node.categoryId] === undefined) {
            expandedEditPickerNodes[node.categoryId] = true;
        }
        var isExpanded = expandedEditPickerNodes[node.categoryId];
        var isSelected = selectedEditPickerCategory === node.categoryName;
        var indent = level * 16;

        var html = '<div class="tree-node-wrapper">';
        html += '<div class="tree-node' + (isSelected ? ' selected' : '') + '"'
            + ' onclick="window.selectEditPickerCategory(decodeURIComponent(\'' + encodeURIComponent(node.categoryName) + '\'))"'
            + ' style="padding-left:' + indent + 'px">';

        if (hasChildren) {
            html += '<span class="tree-expand-btn"'
                + ' onclick="event.stopPropagation();window.toggleEditPickerNode(' + node.categoryId + ')">'
                + '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"'
                + ' style="transform:rotate(' + (isExpanded ? '90deg' : '0deg') + ');transition:transform 0.15s">'
                + '<polyline points="9 18 15 12 9 6"/></svg></span>';
        } else {
            html += '<span class="tree-leaf-spacer"></span>';
        }

        html += '<span class="tree-node-label">' + escapeHtml(node.categoryName) + '</span>';
        html += '</div>';

        if (hasChildren && isExpanded) {
            html += '<div class="tree-children">';
            children.forEach(function (child) {
                html += buildPickerNodeHtml(child, level + 1);
            });
            html += '</div>';
        }

        html += '</div>';
        return html;
    }

    var html = '<div style="display:flex;flex-direction:column;gap:4px;">';
    roots.forEach(function (root) {
        html += buildPickerNodeHtml(root, 0);
    });
    html += '</div>';
    pickerContainer.innerHTML = html;
}

window.toggleEditPickerNode = function (nodeId) {
    expandedEditPickerNodes[nodeId] = !expandedEditPickerNodes[nodeId];
    renderEditPickerTree();
};

window.selectEditPickerCategory = function (categoryName) {
    selectedEditPickerCategory = categoryName;
    var catDisplay = document.getElementById('selectedEditCategoryDisplay');
    var catHidden = document.getElementById('edit-category');
    if (catDisplay) catDisplay.value = categoryName;
    if (catHidden) catHidden.value = categoryName;

    renderEditPickerTree();
};

/* Edit Modal DOM Elements & Handlers */
var editOverlay   = document.getElementById('editModalOverlay');
var btnEditClose  = document.getElementById('editModalClose');
var btnEditCancel = document.getElementById('editModalCancel');
var btnEditSubmit = document.getElementById('btnEditSKUSubmit');

var editIdInput     = document.getElementById('edit-id');
var editNameInput   = document.getElementById('edit-name');
var editDimLengthInput = document.getElementById('edit-dim-length');
var editDimWidthInput  = document.getElementById('edit-dim-width');
var editDimHeightInput = document.getElementById('edit-dim-height');
var editWgtInput    = document.getElementById('edit-weight');
var editCodeLabel   = document.getElementById('edit-sku-code-label');
var editBarcodeInput = document.getElementById('edit-barcode');
var editUnitInput   = document.getElementById('edit-unit');
var editBasePriceInput = document.getElementById('edit-base-price');
var editCategoryInput = document.getElementById('edit-category');

[btnEditClose, btnEditCancel].forEach(function (btn) {
    if (btn) {
        btn.addEventListener('click', function () {
            editOverlay.style.display = 'none';
        });
    }
});

editOverlay.addEventListener('click', function (e) {
    if (e.target === editOverlay) {
        editOverlay.style.display = 'none';
    }
});

window.triggerEditSKU = function (id) {
    console.log('[master-sku] triggerEditSKU called for id:', id);
    try {
        var item = skus.find(function (s) { return s.id === id; });
        console.log('[master-sku] item found:', item);
        if (!item) { console.warn('[master-sku] no item for id', id); return; }

        editIdInput.value = item.productId || item.id.substring(2);
        editCodeLabel.textContent = item.sku;
        editNameInput.value = item.name;

        // Parse dimensions
        var len = '', wid = '', hgt = '';
        if (item.dimensions && item.dimensions !== 'N/A') {
            var dimParts = item.dimensions.split(/[×x]/);
            if (dimParts.length === 3) {
                len = parseFloat(dimParts[0]) || '';
                wid = parseFloat(dimParts[1]) || '';
                hgt = parseFloat(dimParts[2]) || '';
            }
        }
        editDimLengthInput.value = len;
        editDimWidthInput.value = wid;
        editDimHeightInput.value = hgt;

        var wVal = '';
        if (item.weight && item.weight !== 'N/A') {
            wVal = parseFloat(item.weight.replace(' kg', '')) || '';
        }
        editWgtInput.value = wVal;
        editBarcodeInput.value = item.barcode || '';
        editUnitInput.value = item.unit || '';
        editBasePriceInput.value = item.basePrice || '';

        window.selectEditPickerCategory(item.category || '');

        console.log('[master-sku] opening edit overlay');
        editOverlay.style.display = 'flex';
        console.log('[master-sku] edit overlay display:', editOverlay.style.display);
    } catch (err) {
        console.error('[master-sku] triggerEditSKU FAILED:', err);
    }
};

window.triggerDeleteSKU = function (id) {
    var item = skus.find(function (s) { return s.id === id; });
    if (!item) return;

    if (confirm('Bạn có chắc chắn muốn xóa SKU "' + item.sku + '"?')) {
        if (id.indexOf('p-') === 0) {
            var productId = id.substring(2);
            submitPostAction('delete', { productId: productId });
        }
    }
};

if (btnEditSubmit) {
    btnEditSubmit.addEventListener('click', function () {
        var productId = editIdInput.value;
        var nameVal = editNameInput.value.trim();
        
        if (!nameVal) {
            alert('Tên sản phẩm không được bỏ trống!');
            return;
        }

        var lVal = editDimLengthInput.value.trim();
        var wVal = editDimWidthInput.value.trim();
        var hVal = editDimHeightInput.value.trim();
        
        var dimensionsVal = 'N/A';
        if (lVal || wVal || hVal) {
            var l = parseFloat(lVal) || 0;
            var w = parseFloat(wVal) || 0;
            var h = parseFloat(hVal) || 0;
            if (l <= 0 || w <= 0 || h <= 0) {
                alert('Kích thước Dài, Rộng, Cao phải là số thực dương!');
                return;
            }
            dimensionsVal = l + '×' + w + '×' + h;
        }
        
        var wgtVal = editWgtInput.value.trim();
        var weightVal = '0';
        if (wgtVal) {
            var wgt = parseFloat(wgtVal) || 0;
            if (wgt <= 0) {
                alert('Khối lượng phải là số thực dương!');
                return;
            }
            weightVal = wgt.toString();
        }

        var catName = editCategoryInput.value ? editCategoryInput.value.trim() : '';
        var catId = '';
        if (catName) {
            var found = DB_CATEGORIES.find(function (c) { return c.categoryName === catName; });
            if (found) catId = found.categoryId;
        }

        submitPostAction('update', {
            productId: productId,
            productName: nameVal,
            categoryId: catId,
            dimensions: dimensionsVal,
            weight: weightVal,
            barcode: editBarcodeInput.value.trim(),
            unit: editUnitInput.value.trim(),
            basePrice: editBasePriceInput.value.trim()
        });
    });
}

/* ─── Create Modal DOM Elements & Handlers ─────────────────── */
var createOverlay = document.getElementById('createModalOverlay');
var btnCreateTrigger = document.getElementById('btnCreateSKUTrigger');
var btnCreateClose   = document.getElementById('createModalClose');
var btnCreateCancel  = document.getElementById('createModalCancel');
var btnCreateSubmit  = document.getElementById('btnCreateSKUSubmit');

var createSkuInput  = document.getElementById('create-sku');
var createNameInput = document.getElementById('create-name');
var createCatInput  = document.getElementById('create-category');
var createDimLengthInput = document.getElementById('create-dim-length');
var createDimWidthInput  = document.getElementById('create-dim-width');
var createDimHeightInput = document.getElementById('create-dim-height');
var createWgtInput  = document.getElementById('create-weight');

if (btnCreateTrigger) {
    btnCreateTrigger.addEventListener('click', function () {
        if (DB_CATEGORIES.length > 0 && !selectedPickerCategory) {
            window.selectPickerCategory(DB_CATEGORIES[0].categoryName);
        }
        createOverlay.style.display = 'flex';
    });
}

[btnCreateClose, btnCreateCancel].forEach(function (btn) {
    if (btn) {
        btn.addEventListener('click', function () {
            createOverlay.style.display = 'none';
            clearCreateForm();
        });
    }
});

createOverlay.addEventListener('click', function (e) {
    if (e.target === createOverlay) {
        createOverlay.style.display = 'none';
        clearCreateForm();
    }
});

/* Auto Generate SKU */
var btnAutoGen = document.getElementById('btnAutoGenSku');
if (btnAutoGen) {
    btnAutoGen.addEventListener('click', function () {
        var catIdInput = document.getElementById('create-category-id');
        var catId = catIdInput ? catIdInput.value : '';
        if (!catId) {
            alert('Vui lòng chọn danh mục trước!');
            return;
        }
        btnAutoGen.disabled = true;
        btnAutoGen.innerHTML = '<svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="animation:spin 0.8s linear infinite"><path d="M21 12a9 9 0 1 1-6.219-8.56"/></svg> Đang tạo...';
        fetch('${pageContext.request.contextPath}/sales/sku/generate?categoryId=' + catId)
            .then(function(resp) { return resp.json(); })
            .then(function(data) {
                if (data.sku) {
                    createSkuInput.value = data.sku;
                    createSkuInput.removeAttribute('readonly');
                    createSkuInput.style.background = '#fff';
                    createSkuInput.style.cursor = 'text';
                    createSkuInput.style.color = '#059669';
                    createSkuInput.style.fontWeight = '700';
                    setTimeout(function() {
                        createSkuInput.style.color = '';
                        createSkuInput.style.fontWeight = '';
                    }, 2000);
                } else {
                    alert('Lỗi: ' + (data.error || 'Không thể tạo SKU tự động.'));
                }
            })
            .catch(function() {
                alert('Lỗi mạng khi tạo SKU tự động.');
            })
            .finally(function() {
                btnAutoGen.disabled = false;
                btnAutoGen.innerHTML = '<svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="16 3 21 3 21 8"/><line x1="4" y1="20" x2="21" y2="3"/><polyline points="21 16 21 21 16 21"/><line x1="15" y1="15" x2="21" y2="21"/><line x1="4" y1="4" x2="9" y2="9"/></svg> Auto';
            });
    });
}

/* Create Submit */
if (btnCreateSubmit) {
    btnCreateSubmit.addEventListener('click', function () {
        var skuVal  = createSkuInput.value.trim();
        var nameVal = createNameInput.value.trim();

        if (!skuVal || !nameVal) {
            alert('Vui lòng nhập đầy đủ Mã SKU và Tên sản phẩm!');
            return;
        }

        var lVal = createDimLengthInput.value.trim();
        var wVal = createDimWidthInput.value.trim();
        var hVal = createDimHeightInput.value.trim();

        var dimensionsVal = 'N/A';
        if (lVal || wVal || hVal) {
            var l = parseFloat(lVal) || 0;
            var w = parseFloat(wVal) || 0;
            var h = parseFloat(hVal) || 0;
            if (l <= 0 || w <= 0 || h <= 0) {
                alert('Kích thước Dài, Rộng, Cao phải là số thực dương!');
            return;
            }
            dimensionsVal = l + '×' + w + '×' + h;
        }

        var wgtVal = createWgtInput.value.trim();
        var weightVal = '0';
        if (wgtVal) {
            var wgt = parseFloat(wgtVal) || 0;
            if (wgt <= 0) {
                alert('Khối lượng phải là số thực dương!');
                return;
            }
            weightVal = wgt.toString();
        }

        submitPostAction('create', {
            skuCode: skuVal,
            productName: nameVal,
            categoryName: createCatInput.value ? createCatInput.value.trim() : '',
            dimensions: dimensionsVal,
            weight: weightVal,
            basePrice: document.getElementById('create-base-price').value.trim()
        });
    });
}

function clearCreateForm() {
    if (!createSkuInput) return;
    createSkuInput.value = '';
    createSkuInput.setAttribute('readonly', 'true');
    createSkuInput.style.background = 'rgba(16, 55, 92, 0.03)';
    createSkuInput.style.cursor = 'not-allowed';
    createNameInput.value = '';
    if (DB_CATEGORIES.length > 0) {
        window.selectPickerCategory(DB_CATEGORIES[0].categoryName);
    } else {
        window.selectPickerCategory('');
    }
    createDimLengthInput.value = '';
    createDimWidthInput.value  = '';
    createDimHeightInput.value = '';
    createWgtInput.value  = '';
    if (document.getElementById('create-base-price')) {
        document.getElementById('create-base-price').value = '';
    }
}

function padZero(n) { return n < 10 ? '0' + n : n; }

/* ─── Handlers ───────────────────────────────────────────── */
if (searchInput) {
    searchInput.addEventListener('input', function (e) {
        search = e.target.value;
        renderAll();
    });
}
if (catSelect) {
    catSelect.addEventListener('change', function (e) {
        selectedCategory = e.target.value;
        renderAll();
    });
}



/* CSV Export */
var btnExportCSV = document.getElementById('btnExportCSV');
if (btnExportCSV) {
    btnExportCSV.addEventListener('click', function () {
        var filteredList = getFilteredList();
        var headers = ["Mã SKU", "Tên sản phẩm", "Danh mục", "Khối lượng", "Kích thước", "Tồn kho", "Tạo bởi", "Cập nhật"];
        var csvContent = "\uFEFF" + headers.join(",") + "\n";

        filteredList.forEach(function (item) {
            var row = [
                '"' + item.sku.replace(/"/g, '""') + '"',
                '"' + item.name.replace(/"/g, '""') + '"',
                '"' + item.category.replace(/"/g, '""') + '"',
                '"' + item.weight.replace(/"/g, '""') + '"',
                '"' + item.dimensions.replace(/"/g, '""') + '"',
                item.qtyOnHand,
                '"' + (item.createdBy || '').replace(/"/g, '""') + '"',
                '"' + (item.lastUpdated || '').replace(/"/g, '""') + '"'
            ];
            csvContent += row.join(",") + "\n";
        });
        
        var blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
        var link = document.createElement("a");
        link.href = URL.createObjectURL(blob);
        link.setAttribute("download", "master_sku_" + new Date().toISOString().slice(0, 10) + ".csv");
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
    });
}

/* ─── Helpers ─── */

function getFilteredList() {
    return skus.filter(function (s) {
        var matchSearch = s.sku.toLowerCase().indexOf(search.toLowerCase()) > -1 || 
                          s.name.toLowerCase().indexOf(search.toLowerCase()) > -1;
        var matchCat    = selectedCategory === 'Tất cả' || s.category === selectedCategory;
        return matchSearch && matchCat;
    });
}

/* ══ RENDER TABLE ══════════════════════════════════════════ */
function renderAll() {
    
    var filtered = getFilteredList();

    tableInfo.textContent = 'Hiển thị ' + filtered.length + ' / ' + skus.length + ' SKU';

    if (filtered.length === 0) {
        tableBody.innerHTML = '<tr><td colspan="8" style="text-align:center;padding:48px;color:rgba(16, 55, 92, 0.4)">Không tìm thấy sản phẩm SKU nào.</td></tr>';
        return;
    }

    var html = filtered.map(function (item, idx) {
        var isLowStock = item.qtyOnHand < item.minStock;
        var qtyTextClass = isLowStock ? 'stock-qty low-stock' : 'stock-qty normal-stock';

        var specHtml = '<div class="detail-icon-wrap">' +
            '<div class="detail-icon-row">' +
                '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 3v18M12 3L7 8m5-5 5 5"/></svg>' + item.weight +
            '</div>' +
            '<div class="detail-icon-row">' +
                '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="18" height="18" rx="2" ry="2"/><line x1="9" y1="3" x2="9" y2="21"/></svg>' + item.dimensions +
            '</div>' +
        '</div>';

        var stockHtml = '<div class="stock-val-wrap">';
        if (isLowStock) {
            stockHtml += '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>';
        }
        stockHtml += '<span class="' + qtyTextClass + '">' + item.qtyOnHand.toLocaleString() + '</span></div>';

        var infoHtml = '<div class="info-lbl"><span class="info-lbl-inner">Tạo:</span> ' + item.createdBy + '</div>' +
                       '<div class="info-time">' + item.createdAt + '</div>';

        var isSalesStaffUser = (window.WMS_USER && window.WMS_USER.role === 'SALES_STAFF');

        var editBtnHtml = '<button type="button" class="btn-act-circle edit" onclick="window.triggerEditSKU(\'' + item.id + '\')" title="Sửa" style="' + (isSalesStaffUser ? '' : 'display:none;') + '">' +
            '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"></path><path d="M18.5 2.5a2.121 2.121 0 1 1 3 3L12 15l-4 1 1-4 9.5-9.5z"></path></svg>' +
            '</button>';
        var deleteBtnHtml = '<button type="button" class="btn-act-circle del" onclick="window.triggerDeleteSKU(\'' + item.id + '\')" title="Xóa" style="' + (isSalesStaffUser ? '' : 'display:none;') + '">' +
            '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="18" y1="6" x2="6" y2="18"></line><line x1="6" y1="6" x2="18" y2="18"></line></svg>' +
            '</button>';

        var rowClass = idx % 2 === 0 ? '' : 'style="background:rgba(240, 244, 250, 0.25)"';

        return '<tr ' + rowClass + '>' +
            '<td><span class="sku-code-cell">' + item.sku + '</span></td>' +
            '<td><div class="sku-name-cell" title="' + item.name + '">' + item.name + '</div></td>' +
            '<td><span class="sku-cat-cell">' + item.category + '</span></td>' +
            '<td>' + specHtml + '</td>' +
            '<td>' + stockHtml + '</td>' +
            '<td>' + infoHtml + '</td>' +
            '<td>' +
                '<div style="display:flex;align-items:center;justify-content:center;gap:8px">' +
                    editBtnHtml +
                    deleteBtnHtml +
                '</div>' +
            '</td>' +
        '</tr>';
    }).join('');

    tableBody.innerHTML = html;
}

/* ─── Bootstrap ─── */
renderAll();

})();
</script>
