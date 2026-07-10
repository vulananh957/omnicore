<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%-- Categories list — data is provided by CategoryServlet (doGet).
     MVC rule: this JSP does not call DAOs. It only reads request attributes
     populated by the controller: categories, categoriesJson, successMessage,
     errorMessage, infoMessage. --%>

<link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/category--categories.css"/>

<%-- Toast container (populated by JS) --%>
<div id="toastContainer" class="toast-container"
     data-info-message="<c:out value="${infoMessage}"/>"
     data-success-message="<c:out value="${successMessage}"/>"
     data-error-message="<c:out value="${errorMessage}"/>"></div>

<div class="cat-layout-grid">
    <!-- ══ FULL PANEL: TREE VIEW & INLINE OPERATIONS ════════════════════════ -->
    <div class="cat-panel-full">
        <div class="panel-hdr">
            <div>
                <h3 class="panel-title">
                    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
                        stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                        <rect x="3" y="3" width="7" height="9" rx="1" />
                        <rect x="14" y="3" width="7" height="5" rx="1" />
                        <rect x="14" y="12" width="7" height="9" rx="1" />
                        <rect x="3" y="16" width="7" height="5" rx="1" />
                    </svg>
                    Cơ cấu cây phân cấp danh mục sản phẩm
                </h3>
            </div>
            <button class="btn-primary-sm" id="btnRootCategoryTrigger">
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
                Thêm danh mục
            </button>
        </div>
        <div class="tree-container" id="treeContainer"></div>
        <div id="feedbackBannerWrap"></div>
    </div>
</div>



<!-- ══ JAVASCRIPT STATE & LOGIC ═════════════════════════════ -->
<script>
(function () {
    'use strict';

    /* ─── Server-side data ─────────────────────────────────── */
    var serverCategories = [];
    try {
        var rawCategories = '<c:out value="${categoriesJson}" escapeXml="false"/>';
        if (rawCategories && rawCategories.trim() && rawCategories.indexOf('categoriesJson') === -1) {
            serverCategories = JSON.parse(rawCategories);
        }
    } catch (e) {
        console.error('categories: Failed to parse categoriesJson', e);
    }

    var categories = serverCategories.slice();

    var expandedNodes = {};
    try {
        var saved = sessionStorage.getItem('wms_expanded_categories');
        if (saved) {
            expandedNodes = JSON.parse(saved);
        }
    } catch (e) {}

    function saveExpandedNodes() {
        try {
            sessionStorage.setItem('wms_expanded_categories', JSON.stringify(expandedNodes));
        } catch (e) {}
    }

    var activeForm = {
        mode: 'empty', // 'empty' | 'create' | 'edit' | 'delete'
        selectedCategory: null,
        parentId: null
    };

    /* ─── Toast notification system ─────────────────────────── */
    function showToast(message, type) {
        type = type || 'info';
        var icons = {
            success: '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><polyline points="9 12 12 15 16 10"/></svg>',
            error:   '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>',
            info:    '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>'
        };

        var toast = document.createElement('div');
        toast.className = 'toast toast-' + type;
        toast.innerHTML = icons[type] + '<span>' + escapeHtml(message) + '</span>';

        var container = document.getElementById('toastContainer');
        container.appendChild(toast);

        setTimeout(function () {
            toast.style.animation = 'toastSlideOut 0.3s ease forwards';
            setTimeout(function () {
                if (toast.parentNode) toast.parentNode.removeChild(toast);
            }, 300);
        }, 3500);
    }

    /* Show server-side flash message on page load */
    var toastContainer = document.getElementById('toastContainer');
    if (toastContainer) {
        var infoMsg = toastContainer.getAttribute('data-info-message');
        var successMsg = toastContainer.getAttribute('data-success-message');
        var errorMsg = toastContainer.getAttribute('data-error-message');

        if (infoMsg && infoMsg.trim()) {
            showToast(infoMsg, "info");
        }
        if (successMsg && successMsg.trim()) {
            showToast(successMsg, "success");
        }
        if (errorMsg && errorMsg.trim()) {
            showToast(errorMsg, "error");
        }
    }
    /* ─── Helper Functions for Hierarchy and Levels ─────────── */
    function getCategoryLevel(catId) {
        if (!catId) return 0;
        var cat = categories.find(function (c) { return c.id === catId; });
        if (!cat) return 0;
        if (cat.parentId === null) return 1;
        return 1 + getCategoryLevel(cat.parentId);
    }

    function getSubtreeDepth(catId) {
        var children = categories.filter(function (c) { return c.parentId === catId; });
        if (children.length === 0) return 0;
        var maxSubDepth = 0;
        children.forEach(function (child) {
            var d = getSubtreeDepth(child.id);
            if (d > maxSubDepth) maxSubDepth = d;
        });
        return 1 + maxSubDepth;
    }

    function isDescendant(parentId, childId) {
        if (!childId) return false;
        var child = categories.find(function (c) { return c.id === childId; });
        if (!child || child.parentId === null) return false;
        if (child.parentId === parentId) return true;
        return isDescendant(parentId, child.parentId);
    }

    /* Returns true if any ancestor of this category is currently inactive. */
    function hasInactiveAncestor(catId) {
        var cat = categories.find(function (c) { return c.id === catId; });
        if (!cat || cat.parentId === null) return false;
        var parent = categories.find(function (c) { return c.id === cat.parentId; });
        if (!parent) return false;
        if (!parent.active) return true;
        return hasInactiveAncestor(parent.id);
    }

    /* ─── DOM Elements ───────────────────────────────────────── */
    var treeContainer = document.getElementById('treeContainer');
    var feedbackBanner = document.getElementById('feedbackBannerWrap');
    var btnAddRoot = document.getElementById('btnRootCategoryTrigger');

    /* ─── Handlers ───────────────────────────────────────────── */
    if (btnAddRoot) {
        btnAddRoot.addEventListener('click', function () {
            activeForm.mode = 'create';
            activeForm.selectedId = null;
            activeForm.parentId = null;
            renderTree();
        });
    }

    /* ─── UI Rendering ───────────────────────────────────────── */

    /* 1. Left Tree Column */
    function renderTree() {
        var roots = categories.filter(function (c) { return c.parentId === null; });
        var html = '';

        if (activeForm.mode === 'create' && activeForm.parentId === null) {
            html += buildInlineCreateFormHtml(null);
        }

        if (roots.length === 0 && html === '') {
            treeContainer.innerHTML =
                '<div class="empty-state-card">' +
                '<div class="empty-state-icon">' +
                '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/></svg>' +
                '</div>' +
                '<h4 class="empty-state-title">Chưa có danh mục nào</h4>' +
                '<p class="empty-state-desc">Danh mục sản phẩm của bạn hiện đang trống. Hãy nhấn nút "+ Thêm danh mục" ở góc trên để tạo danh mục đầu tiên.</p>' +
                '</div>';
            return;
        }

        html += roots.map(function (root) {
            return buildNodeHtml(root, 1);
        }).join('');

        treeContainer.innerHTML = html;
    }

    function buildInlineCreateFormHtml(parentId) {
        var idSuffix = parentId === null ? 'root' : parentId;
        var rowClass = parentId === null ? 'inline-create-form' : 'inline-create-form new-node-row';
        var isRoot = parentId === null;
        
        return '<div class="tree-node-wrapper">' +
            '<form class="' + rowClass + '" onsubmit="WMS_SUBMIT_INLINE_CREATE(event, ' + (parentId === null ? 'null' : parentId) + ')">' +
            (parentId === null ? '' : '<div class="bullet-dot" style="opacity: 0; flex-shrink: 0;"></div>') +
            '<svg class="folder-icon" style="color: #cbd5e1; flex-shrink: 0;" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/></svg>' +
            '<input type="text" class="inline-input" style="width: 70px; font-weight: 700; text-transform: uppercase;" id="createCode_' + idSuffix + '" maxlength="4" placeholder="Mã..." required title="Mã định danh 3-4 ký tự (VD: EYE)" oninput="this.value = this.value.toUpperCase().replace(/[^A-Z0-9]/g, \'\')" />' +
            '<input type="text" class="inline-input name-input" id="createName_' + idSuffix + '" required placeholder="Tên danh mục mới..." />' +
            '<input type="text" class="inline-input desc-input" id="createDesc_' + idSuffix + '" placeholder="Mô tả..." />' +
            '<button type="submit" class="btn-action-sm-inline save" title="Lưu danh mục">' +
            '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"/></svg>' +
            '</button>' +
            '<button type="button" class="btn-action-sm-inline cancel" onclick="WMS_CANCEL_ACTION()" title="Hủy bỏ">' +
            '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>' +
            '</button>' +
            '</form>' +
            '</div>';
    }

    function buildNodeHtml(node, level) {
        var children = categories.filter(function (c) { return c.parentId === node.id; });
        var hasChildren = children.length > 0;
        var isExpanded = !!expandedNodes[node.id];

        var toggleBtn;
        if (hasChildren) {
            var chevronSvg = isExpanded ?
                '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="6 9 12 15 18 9"/></svg>' :
                '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="9 18 15 12 9 6"/></svg>';
            toggleBtn = '<button class="btn-toggle-chevron" onclick="WMS_TOGGLE_NODE(' + node.id + ')">' + chevronSvg + '</button>';
        } else {
            toggleBtn = '<div class="bullet-dot"><span></span></div>';
        }

        var folderSvg = '';
        if (level >= 3) {
            folderSvg = '<svg class="folder-icon level-3" style="color: rgba(16, 55, 92, 0.40);" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>';
        } else {
            var color = (level === 1) ? 'var(--orange)' : '#EB8317';
            if (hasChildren && isExpanded) {
                folderSvg = '<svg class="folder-icon level-' + level + '" style="color: ' + color + '" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/><path d="M2 10h20"/></svg>';
            } else {
                folderSvg = '<svg class="folder-icon level-' + level + '" style="color: ' + color + '" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/></svg>';
            }
        }

        var addSubBtn = '';
        var nodeLockedByAncestor = !node.active ? false : hasInactiveAncestor(node.id);
        // Lock add-sub when:
        //  - this node is inactive, OR
        //  - any ancestor is inactive (defensive: even if this node still has
        //    active=true in the DB because the page was loaded before the
        //    server healed it, we still refuse to add a child here).
        if (level < 3 && node.active && !nodeLockedByAncestor) {
            addSubBtn = '<button class="btn-action-sm" onclick="WMS_ADD_SUB(' + node.id + ')" title="Thêm thể loại con">' +
                '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="16"/><line x1="8" y1="12" x2="16" y2="12"/></svg>' +
                '</button>';
        } else if (level < 3) {
            var addSubTitle = !node.active
                ? 'Khong the them con: danh muc dang ngung hoat dong'
                : 'Khong the them con: nhanh cha dang ngung hoat dong';
            addSubBtn = '<button class="btn-action-sm disabled" disabled title="' + addSubTitle + '" aria-disabled="true">' +
                '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="16"/><line x1="8" y1="12" x2="16" y2="12"/></svg>' +
                '</button>';
        }

        var rowClass = level === 1 ? 'tree-row root-row' : 'tree-row';
        var badgeClass = 'node-level-badge level-' + (level > 2 ? '3' : level);

        var childrenHtml = '';
        if (isExpanded) {
            var childrenListHtml = '';
            
            if (activeForm.mode === 'create' && activeForm.parentId === node.id) {
                childrenListHtml += buildInlineCreateFormHtml(node.id);
            }
            
            if (hasChildren) {
                childrenListHtml += children.map(function (c) { return buildNodeHtml(c, level + 1); }).join('');
            }
            
            if (childrenListHtml !== '') {
                childrenHtml = '<div class="tree-children-container">' + childrenListHtml + '</div>';
            }
        }

        // Inline Delete Confirmation Row
        if (activeForm.mode === 'delete' && activeForm.selectedId === node.id) { 
            var deleteMsg = 'Xác nhận xóa danh mục <strong>' + escapeHtml(node.name) + '</strong>';
            if (node.isImmutable) {
                deleteMsg += '<br><span style="color: #dc2626; font-weight: 500;">Danh mục đã có sản phẩm. Chỉ có thể ngừng hoạt động.</span>';
            }
            return '<div class="tree-node-wrapper">' +
                '<div class="tree-row delete-confirm-row">' +
                '<svg style="width: 18px; height: 18px; color: #dc2626; flex-shrink: 0;" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>' +
                '<span style="font-size: 13px; color: #991b1b; font-weight: 600;">' + deleteMsg + '</span>' +
                '<div style="margin-left: auto; display: flex; gap: 8px;">' +
                (node.isImmutable 
                    ? '<button type="button" class="btn-inline-danger-confirm" style="background: #f59e0b;" onclick="WMS_DEACTIVATE(' + node.id + ')">Ngung hoat dong</button>'
                    : '<button type="button" class="btn-inline-danger-confirm" onclick="WMS_CONFIRM_DELETE(' + node.id + ')">Xoa</button>') +
                '<button type="button" class="btn-inline-cancel" onclick="WMS_CANCEL_ACTION()">Huy</button>' +
                '</div>' +
                '</div>' +
                childrenHtml +
                '</div>';
        }

        // Inline Edit Form Row
        if (activeForm.mode === 'edit' && activeForm.selectedId === node.id) {
            var parentSelectOptions = buildParentSelectOptions(node.id, node.parentId);
            // Ma dinh danh bi khoa vinh vien ngay khi tao, khong cho sua o bat ky luc nao.
            var codeReadonly = 'readonly disabled';
            var codeClass = 'inline-input locked-code';

            return '<div class="tree-node-wrapper">' +
                '<form class="inline-edit-form" onsubmit="WMS_SUBMIT_INLINE_EDIT(event, ' + node.id + ')">' +
                toggleBtn +
                folderSvg +
                '<input type="text" class="' + codeClass + '" style="width: 70px; font-weight: 700; text-transform: uppercase;" id="editCode_' + node.id + '" value="' + escapeHtml(node.code) + '" ' + codeReadonly + ' title="Ma dinh danh — khong the sua" />' +
                '<input type="text" class="inline-input name-input" id="editName_' + node.id + '" value="' + escapeHtml(node.name) + '" required placeholder="Ten danh muc..." />' +
                '<input type="text" class="inline-input desc-input" id="editDesc_' + node.id + '" value="' + escapeHtml(node.description || '') + '" placeholder="Mo ta danh muc..." />' +
                '<div style="display: flex; align-items: center; gap: 8px;">' +
                '<span style="font-size: 11.5px; color: rgba(16, 55, 92, 0.6); font-weight: 600; white-space: nowrap;">Cha:</span>' +
                '<select class="inline-input parent-select" id="editParent_' + node.id + '">' + parentSelectOptions + '</select>' +
                '</div>' +
                '<button type="submit" class="btn-action-sm-inline save" title="Luu thay doi">' +
                '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"/></svg>' +
                '</button>' +
                '<button type="button" class="btn-action-sm-inline cancel" onclick="WMS_CANCEL_ACTION()" title="Huy bo">' +
                '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>' +
                '</button>' +
                '</form>' +
                childrenHtml +
                '</div>';
        }

        // Standard View Row
        var descSpan = node.description ? '<span style="font-size: 11.5px; color: rgba(16, 55, 92, 0.45); margin-left: 8px; font-weight: normal;">— ' + escapeHtml(node.description) + '</span>' : '';
        var codeBadge = '<span class="cat-code-badge" style="font-size: 11px; font-weight: 700; color: var(--orange); background: rgba(16, 55, 92, 0.08); padding: 2px 6px; border-radius: 4px; margin-left: 8px;">' + escapeHtml(node.code) + '</span>';

        // Effective "locked" state — self inactive OR any ancestor inactive.
        // Even if DB still says active=true for the descendant (e.g. page loaded
        // before server healed it), we still show it as locked here.
        var nodeInactive = !node.active;
        var ancestorInactive = hasInactiveAncestor(node.id);
        var effectivelyInactive = nodeInactive || ancestorInactive;
        var cascadeFromAncestor = node.active && ancestorInactive; // self active but parent path is dead

        var nameClass = effectivelyInactive ? 'node-name struck' : 'node-name';
        var wrapperClass = effectivelyInactive ? 'tree-node-wrapper is-inactive' : 'tree-node-wrapper';
        var statusBadge;
        if (cascadeFromAncestor) {
            // Defensive: should be rare after server heals, but cover it.
            statusBadge = '<span class="cascade-badge" title="Danh muc cha dang ngung hoat dong — trang thai dang duoc dong bo">' +
                '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" style="width:10px;height:10px;">' +
                '<path d="M12 2v4M12 18v4M4.93 4.93l2.83 2.83M16.24 16.24l2.83 2.83M2 12h4M18 12h4M4.93 19.07l2.83-2.83M16.24 7.76l2.83-2.83"/>' +
                '</svg>' +
                'Ngung (cascade)</span>';
        } else if (nodeInactive) {
            if (ancestorInactive) {
                statusBadge = '<span class="cascade-badge" title="Vo hieu hoa theo cascade tu danh muc cha">' +
                    '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" style="width:10px;height:10px;">' +
                    '<path d="M12 2v4M12 18v4M4.93 4.93l2.83 2.83M16.24 16.24l2.83 2.83M2 12h4M18 12h4M4.93 19.07l2.83-2.83M16.24 7.76l2.83-2.83"/>' +
                    '</svg>' +
                    'Ngung (cascade)</span>';
            } else {
                statusBadge = '<span style="font-size: 10px; font-weight: 600; color: #dc2626; background: #fef2f2; padding: 2px 6px; border-radius: 4px; margin-left: 6px;">Ngừng</span>';
            }
        } else {
            statusBadge = '';
        }

        // Build action buttons — locked when node is effectively inactive.
        var editBtn;
        var delBtn;
        var reactivateBtn;
        if (effectivelyInactive) {
            var lockTitleEdit = cascadeFromAncestor
                ? 'Khong the sua: nhanh cha dang ngung hoat dong'
                : 'Khong the sua: danh muc dang ngung hoat dong. Hay kich hoat lai truoc.';
            var lockTitleDel = cascadeFromAncestor
                ? 'Khong the xoa: nhanh cha dang ngung hoat dong'
                : 'Khong the xoa: danh muc dang ngung hoat dong';
            editBtn = '<button class="btn-action-sm disabled" disabled title="' + lockTitleEdit + '" aria-disabled="true">' +
                '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"></path><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"></path></svg>' +
                '</button>';
            delBtn = '<button class="btn-action-sm del disabled" disabled title="' + lockTitleDel + '" aria-disabled="true">' +
                '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>' +
                '</button>';
            // Only show reactivate when the node itself is the one we need to
            // bring back. If the ancestor is dead, the user must reactivate
            // the ancestor first; show a placeholder instead.
            if (cascadeFromAncestor) {
                reactivateBtn = '<button class="btn-action-sm reactivate disabled" disabled title="Can kich hoat danh muc cha truoc" aria-disabled="true">' +
                    '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/></svg>' +
                    '</button>';
            } else {
                var reactivateTitle = ancestorInactive
                    ? 'Kich hoat lai (can kich hoat danh muc cha truoc)'
                    : 'Kich hoat lai danh muc';
                reactivateBtn = '<button class="btn-action-sm reactivate" onclick="WMS_REACTIVATE(' + node.id + ')" title="' + reactivateTitle + '">' +
                    '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/></svg>' +
                    '</button>';
            }
        } else {
            editBtn = '<button class="btn-action-sm" onclick="WMS_EDIT_NODE(' + node.id + ')" title="Chinh sua">' +
                '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"></path><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"></path></svg>' +
                '</button>';
            delBtn = '<button class="btn-action-sm del" onclick="WMS_DEL_NODE(' + node.id + ')" title="Xoa danh muc">' +
                '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>' +
                '</button>';
            reactivateBtn = '';
        }

        return '<div class="' + wrapperClass + '">' +
            '<div class="' + rowClass + '">' +
            toggleBtn +
            folderSvg +
            '<div class="node-title-wrap">' +
            '<span class="' + nameClass + '">' + escapeHtml(node.name) + '</span>' +
            codeBadge +
            statusBadge +
            descSpan +
            '</div>' +
            '<div class="node-actions">' +
            addSubBtn +
            editBtn +
            reactivateBtn +
            delBtn +
            '</div>' +
            '</div>' +
            childrenHtml +
            '</div>';
    }

    function buildParentSelectOptions(nodeId, parentId) {
        var parentOptions = '<option value="none">Không có (Cấp cao nhất)</option>';
        categories.forEach(function (p) {
            if (nodeId) {
                if (p.id === nodeId) return;
                if (isDescendant(nodeId, p.id)) return;
                if (getCategoryLevel(p.id) + 1 + getSubtreeDepth(nodeId) > 3) return;
            } else {
                if (getCategoryLevel(p.id) >= 3) return;
            }

            var sel = (parentId !== null && parentId == p.id) ? 'selected' : '';
            parentOptions += '<option value="' + p.id + '" ' + sel + '>' + escapeHtml(p.name) + '</option>';
        });
        return parentOptions;
    }

    function showSuccessBanner() {
        if (!feedbackBanner) return;
        feedbackBanner.innerHTML =
            '<div class="feedback-banner">' +
            '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><polyline points="12 8 12 12 16 14"/></svg>' +
            '<span>Đồng bộ thành công! Cây danh mục sản phẩm đã được cập nhật.</span>' +
            '</div>';
        setTimeout(function () { feedbackBanner.innerHTML = ''; }, 3500);
    }

    /* ─── Helpers ─── */
    function escapeHtml(str) {
        if (!str) return '';
        return str
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#039;");
    }

    /* ─── Global Scope bindings ─── */
    window.WMS_TOGGLE_NODE = function (nodeId) {
        expandedNodes[nodeId] = !expandedNodes[nodeId];
        saveExpandedNodes();
        renderTree();
    };

    window.WMS_ADD_SUB = function (parentId) {
        var parent = categories.find(function (c) { return c.id === parentId; });
        if (!parent) return;
        if (!parent.active) {
            showToast('Khong the them danh muc con: danh muc cha dang ngung hoat dong. Hay kich hoat lai truoc.', 'error');
            return;
        }
        if (hasInactiveAncestor(parentId)) {
            showToast('Khong the them danh muc con: nhanh cha dang ngung hoat dong. Hay kich hoat cac danh muc to tien truoc.', 'error');
            return;
        }
        activeForm.mode = 'create';
        activeForm.selectedId = null;
        activeForm.parentId = parentId;
        expandedNodes[parentId] = true;
        saveExpandedNodes();
        renderTree();
    };

    window.WMS_EDIT_NODE = function (categoryId) {
        var cat = categories.find(function (c) { return c.id === categoryId; });
        if (!cat) return;
        if (!cat.active) {
            showToast('Danh muc dang ngung hoat dong. Hay kich hoat lai truoc khi sua.', 'error');
            return;
        }
        if (hasInactiveAncestor(categoryId)) {
            showToast('Danh muc dang nam trong nhanh ngung hoat dong. Hay kich hoat danh muc to tien truoc.', 'error');
            return;
        }
        activeForm.mode = 'edit';
        activeForm.selectedId = categoryId;
        activeForm.parentId = cat.parentId;
        renderTree();
    };

    window.WMS_DEL_NODE = function (categoryId) {
        var cat = categories.find(function (c) { return c.id === categoryId; });
        if (!cat) return;
        if (!cat.active || hasInactiveAncestor(categoryId)) {
            showToast('Khong the xoa: danh muc dang ngung hoat dong. Hay kich hoat truoc neu muon xoa.', 'error');
            return;
        }
        activeForm.mode = 'delete';
        activeForm.selectedId = categoryId;
        renderTree();
    };

    window.WMS_CANCEL_ACTION = function () {
        activeForm.mode = 'empty';
        activeForm.selectedId = null;
        activeForm.parentId = null;
        renderTree();
    };

    window.WMS_SUBMIT_INLINE_CREATE = function (event, parentId) {
        event.preventDefault();
        var idSuffix = parentId === null ? 'root' : parentId;
        var codeInput = document.getElementById('createCode_' + idSuffix);
        var nameInput = document.getElementById('createName_' + idSuffix);
        var descInput = document.getElementById('createDesc_' + idSuffix);
        if (!codeInput || !nameInput) return;

        var code = codeInput.value.trim().toUpperCase();
        var name = nameInput.value.trim();
        if (!code || code.length < 3) {
            showToast('Ma dinh danh phai tu 3-4 ky tu!', 'error');
            return;
        }
        if (!name) {
            showToast('Vui long nhap ten danh muc!', 'error');
            return;
        }

        var description = descInput ? descInput.value.trim() : '';
        var params = new URLSearchParams();
        params.append('action', 'create');
        params.append('categoryCode', code);
        params.append('categoryName', name);
        if (parentId !== null) {
            params.append('parentId', parentId);
        }
        params.append('description', description);

        var form = event.target;
        var submitBtn = form.querySelector('button[type="submit"]');
        var originalText = submitBtn.innerHTML;
        submitBtn.disabled = true;
        submitBtn.innerHTML = '...';

        fetch('${pageContext.request.contextPath}/sales/categories', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: params.toString()
        })
        .then(function (resp) {
            if (resp.redirected) {
                window.location.href = resp.url;
            } else {
                submitBtn.disabled = false;
                submitBtn.innerHTML = originalText;
                showToast('Đã xảy ra lỗi khi tạo danh mục!', 'error');
            }
        })
        .catch(function () {
            submitBtn.disabled = false;
            submitBtn.innerHTML = originalText;
            showToast('Đã xảy ra lỗi kết nối!', 'error');
        });
    };

    window.WMS_SUBMIT_INLINE_EDIT = function (event, nodeId) {
        event.preventDefault();
        var codeInput = document.getElementById('editCode_' + nodeId);
        var nameInput = document.getElementById('editName_' + nodeId);
        var descInput = document.getElementById('editDesc_' + nodeId);
        var parentSelect = document.getElementById('editParent_' + nodeId);
        if (!nameInput) return;

        var name = nameInput.value.trim();
        if (!name) {
            showToast('Vui long nhap ten danh muc!', 'error');
            return;
        }

        // Lay code tu input hoac tu node goc (neu disabled)
        var code = codeInput ? codeInput.value.trim().toUpperCase() : '';
        if (codeInput && codeInput.disabled) {
            // Neu disabled, lay tu node goc
            var node = categories.find(function (c) { return c.id === nodeId; });
            code = node ? node.code : '';
        }
        if (!code || code.length < 3) {
            showToast('Ma dinh danh phai tu 3-4 ky tu!', 'error');
            return;
        }

        var description = descInput ? descInput.value.trim() : '';
        var parentVal = parentSelect ? parentSelect.value : 'none';

        var params = new URLSearchParams();
        params.append('action', 'update');
        params.append('categoryId', nodeId);
        params.append('categoryCode', code);
        params.append('categoryName', name);
        if (parentVal && parentVal !== 'none') {
            params.append('parentId', parentVal);
        }
        params.append('description', description);

        var form = event.target;
        var submitBtn = form.querySelector('button[type="submit"]');
        var originalText = submitBtn.innerHTML;
        submitBtn.disabled = true;
        submitBtn.innerHTML = '...';

        fetch('${pageContext.request.contextPath}/sales/categories', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: params.toString()
        })
        .then(function (resp) {
            if (resp.redirected) {
                window.location.href = resp.url;
            } else {
                submitBtn.disabled = false;
                submitBtn.innerHTML = originalText;
                showToast('Đã xảy ra lỗi khi cập nhật danh mục!', 'error');
            }
        })
        .catch(function () {
            submitBtn.disabled = false;
            submitBtn.innerHTML = originalText;
            showToast('Đã xảy ra lỗi kết nối!', 'error');
        });
    };

    window.WMS_DEACTIVATE = function (nodeId) {
        var params = new URLSearchParams();
        params.append('action', 'deactivate');
        params.append('categoryId', nodeId);

        fetch('${pageContext.request.contextPath}/sales/categories', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: params.toString()
        })
        .then(function (resp) {
            if (resp.redirected) {
                window.location.href = resp.url;
            } else {
                showToast('Da xay ra loi khi ngung hoat dong danh muc!', 'error');
            }
        })
        .catch(function () {
            showToast('Da xay ra loi khi ngung hoat dong danh muc!', 'error');
        });
    };

    window.WMS_REACTIVATE = function (nodeId) {
        var cat = categories.find(function (c) { return c.id === nodeId; });
        if (!cat) return;
        if (cat.active) return;

        if (!window.confirm('Kich hoat lai danh muc "' + cat.name + '"?\n\nDanh muc se xuat hien lai voi nhan vien kho khi tao SKU moi.')) {
            return;
        }

        var params = new URLSearchParams();
        params.append('action', 'reactivate');
        params.append('categoryId', nodeId);

        fetch('${pageContext.request.contextPath}/sales/categories', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: params.toString()
        })
        .then(function (resp) {
            if (resp.redirected) {
                window.location.href = resp.url;
            } else {
                showToast('Da xay ra loi khi kich hoat lai danh muc!', 'error');
            }
        })
        .catch(function () {
            showToast('Da xay ra loi khi kich hoat lai danh muc!', 'error');
        });
    };

    window.WMS_CONFIRM_DELETE = function (nodeId) {
        var params = new URLSearchParams();
        params.append('action', 'delete');
        params.append('categoryId', nodeId);

        fetch('${pageContext.request.contextPath}/sales/categories', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: params.toString()
        })
        .then(function (resp) {
            if (resp.redirected) {
                window.location.href = resp.url;
            } else {
                showToast('Đã xảy ra lỗi khi xóa danh mục!', 'error');
            }
        })
        .catch(function () {
            showToast('Đã xảy ra lỗi kết nối!', 'error');
        });
    };

    /* ─── Bootstrap ─── */
    renderTree();
})();
</script>
