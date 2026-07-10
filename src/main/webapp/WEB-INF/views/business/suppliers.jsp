<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>

<!-- ══ MAIN CONTENT ══════════════════════════════════════════ -->
<div class="suppliers-page">

    <!-- ── Toolbar (Single Row) ───────────────────────────── -->
    <div class="suppliers-toolbar">
        <div class="suppliers-toolbar__left">
            <!-- Search -->
            <div class="suppliers-search">
                <span class="suppliers-search__icon">
                    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                        <circle cx="11" cy="11" r="8"/><path d="m21 21-4.35-4.35"/>
                    </svg>
                </span>
                <input type="text" id="supplierSearchInput" placeholder="Tìm theo tên, mã, SĐT..." value="${currentKeyword != null ? currentKeyword : ''}">
            </div>

            <!-- Debt Filter -->
            <div class="suppliers-select">
                <select id="debtFilter">
                    <option value="ALL" ${currentDebtFilter == 'ALL' || currentDebtFilter == null ? 'selected' : ''}>Tất cả</option>
                    <option value="HAS_DEBT" ${currentDebtFilter == 'HAS_DEBT' ? 'selected' : ''}>Có công nợ</option>
                    <option value="NO_DEBT" ${currentDebtFilter == 'NO_DEBT' ? 'selected' : ''}>Không có công nợ</option>
                </select>
            </div>
        </div>

        <div class="suppliers-toolbar__right">
            <!-- Add Button -->
            <button class="suppliers-add-btn" id="addSupplierBtn">
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M5 12h14"/><path d="M12 5v14"/>
                </svg>
                <span>Thêm NCC</span>
            </button>
        </div>
    </div>

    <!-- ── Data Table ───────────────────────────────────── -->
    <div class="suppliers-table-card" id="listView">
        <div class="suppliers-table-card__header">
            <div>
                <span class="suppliers-table-card__title">Danh sách nhà cung cấp</span>
                <span class="suppliers-table-card__count" id="supplierCount">(0 NCC)</span>
            </div>
        </div>
        <div class="suppliers-table-wrap">
            <table class="suppliers-table">
                <thead>
                    <tr>
                        <th style="min-width: 120px;">Mã NCC</th>
                        <th style="min-width: 200px;">Tên Nhà Cung Cấp</th>
                        <th style="min-width: 140px;">Người Liên Hệ</th>
                        <th style="min-width: 130px;">Thời Hạn Nợ</th>
                        <th class="right sortable" data-sort="current_balance">
                            <span class="th-label">Công Nợ Hiện Tại</span>
                            <span class="sort-icon"></span>
                        </th>
                        <th class="right sortable" data-sort="total_ordered_value">
                            <span class="th-label">Tổng Tiền Đã Mua</span>
                            <span class="sort-icon"></span>
                        </th>
                        <th style="width: 100px;">Thao Tác</th>
                    </tr>
                </thead>
                <tbody id="supplierTableBody">
                    <c:forEach var="supplier" items="${suppliers}">
                        <tr data-id="${supplier.supplierId}">
                            <td><span class="supplier-code">${supplier.supplierCode}</span></td>
                            <td><span class="supplier-name">${supplier.name}</span></td>
                            <td>
                                <div class="contact-cell">
                                    <span class="contact-name">${supplier.contactPerson != null ? supplier.contactPerson : '—'}</span>
                                    <span class="contact-phone">${supplier.phone != null ? supplier.phone : ''}</span>
                                </div>
                            </td>
                            <td>
                                <c:choose>
                                    <c:when test="${supplier.paymentTerms == 'COD'}"><span class="payment-terms payment-terms--cod">COD</span></c:when>
                                    <c:when test="${supplier.paymentTerms == 'CIA'}"><span class="payment-terms">CIA — Trả tiền trước</span></c:when>
                                    <c:when test="${supplier.paymentTerms == 'Immediate'}"><span class="payment-terms">Trả ngay</span></c:when>
                                    <c:when test="${supplier.paymentTerms.startsWith('Net')}"><span class="payment-terms payment-terms--net">${supplier.paymentTerms}</span></c:when>
                                    <c:when test="${supplier.paymentTerms != null && supplier.paymentTerms != ''}"><span class="payment-terms">${supplier.paymentTerms}</span></c:when>
                                    <c:otherwise><span class="payment-terms muted">—</span></c:otherwise>
                                </c:choose>
                            </td>
                            <td class="right">
                                <c:choose>
                                    <c:when test="${supplier.currentBalance > 0}">
                                        <span class="amount debt-warning">${supplier.currentBalance} đ</span>
                                    </c:when>
                                    <c:otherwise>
                                        <span class="amount no-debt">0 đ</span>
                                    </c:otherwise>
                                </c:choose>
                            </td>
                            <td class="right">
                                <span class="amount">${supplier.totalOrderedValue} đ</span>
                            </td>
                            <td>
                                <button class="supplier-detail-btn" data-id="${supplier.supplierId}" onclick="viewSupplier(this.dataset.id)" title="Xem chi tiết">
                                    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                                        <path d="M2 12s3-7 10-7 10 7 10 7-3 7-10 7-10-7-10-7Z"/>
                                        <circle cx="12" cy="12" r="3"/>
                                    </svg>
                                </button>
                            </td>
                        </tr>
                    </c:forEach>
                </tbody>
            </table>
        </div>

        <!-- Pagination -->
        <div class="suppliers-pagination" id="paginationContainer">
            <c:if test="${totalPages > 1}">
                <span class="pagination-info">
                    Trang ${currentPage} / ${totalPages} — ${totalItems} nhà cung cấp
                </span>
                <div class="pagination-controls">
                    <c:if test="${currentPage > 1}">
                        <a href="?page=${currentPage - 1}&sortBy=${currentSortBy}&sortDir=${currentSortDir}<c:if test='${currentKeyword != null}'>&q=${currentKeyword}</c:if><c:if test='${currentDebtFilter != null && currentDebtFilter != "ALL"}'>&debtFilter=${currentDebtFilter}</c:if>" class="pagination-btn">«</a>
                    </c:if>
                    <c:forEach begin="1" end="${totalPages > 5 ? 5 : totalPages}" var="i">
                        <a href="?page=${i}&sortBy=${currentSortBy}&sortDir=${currentSortDir}<c:if test='${currentKeyword != null}'>&q=${currentKeyword}</c:if><c:if test='${currentDebtFilter != null && currentDebtFilter != "ALL"}'>&debtFilter=${currentDebtFilter}</c:if>"
                           class="pagination-btn ${i == currentPage ? 'active' : ''}">${i}</a>
                    </c:forEach>
                    <c:if test="${currentPage < totalPages}">
                        <a href="?page=${currentPage + 1}&sortBy=${currentSortBy}&sortDir=${currentSortDir}<c:if test='${currentKeyword != null}'>&q=${currentKeyword}</c:if><c:if test='${currentDebtFilter != null && currentDebtFilter != "ALL"}'>&debtFilter=${currentDebtFilter}</c:if>" class="pagination-btn">»</a>
                    </c:if>
                </div>
            </c:if>
        </div>
    </div>

    <!-- ── Empty State ──────────────────────────────────── -->
    <div class="suppliers-empty" id="emptyState" style="display:none;">
        <div class="suppliers-empty__icon">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
                <rect width="20" height="14" x="2" y="5" rx="2"/>
                <line x1="2" x2="22" y1="10" y2="10"/>
            </svg>
        </div>
        <div class="suppliers-empty__title">Không tìm thấy nhà cung cấp</div>
        <div class="suppliers-empty__text">Thử điều chỉnh bộ lọc hoặc từ khóa tìm kiếm</div>
    </div>
</div>

<!-- ══ METADATA ══════════════════════════════════════════════ -->
<div id="supplier-metadata" style="display:none"
     data-sortby="${currentSortBy}"
     data-sortdir="${currentSortDir}"
     data-debt="${currentDebtFilter}"
     data-keyword="${currentKeyword != null ? currentKeyword : ''}"
     data-total="${totalItems}"
     data-page="${currentPage}"
     data-pages="${totalPages}">
</div>

<!-- ══ MODAL ═══════════════════════════════════════════════ -->
<div class="modal-overlay" id="supplierModal">
    <div class="modal-content">
        <div class="modal-header">
            <h3 class="modal-title" id="modalTitle">Thêm Nhà Cung Cấp</h3>
            <button class="modal-close" onclick="closeModal()">&times;</button>
        </div>
        <form id="supplierForm" class="modal-body">
            <input type="hidden" id="supplierId" name="supplierId" value="">

            <div class="form-row">
                <div class="form-group">
                    <label for="supplierCode">Mã NCC <span class="required">*</span></label>
                    <input type="text" id="supplierCode" name="supplierCode" maxlength="20" placeholder="VD: NCC-2507-001">
                    <small class="form-hint">Để trống để tự động sinh mã</small>
                </div>
                <div class="form-group">
                    <label for="status">Trạng thái</label>
                    <select id="status" name="status">
                        <option value="ACTIVE">Hoạt động</option>
                        <option value="INACTIVE">Ngừng hoạt động</option>
                    </select>
                </div>
            </div>

            <div class="form-group">
                <label for="name">Tên Công Ty <span class="required">*</span></label>
                <input type="text" id="name" name="name" required maxlength="255" placeholder="Tên công ty đầy đủ">
            </div>

            <div class="form-row">
                <div class="form-group">
                    <label for="contactPerson">Người Liên Hệ</label>
                    <input type="text" id="contactPerson" name="contactPerson" maxlength="100" placeholder="Họ tên người liên hệ">
                </div>
                <div class="form-group">
                    <label for="phone">Số Điện Thoại</label>
                    <input type="text" id="phone" name="phone" maxlength="20" placeholder="VD: 0909123456">
                </div>
            </div>

            <div class="form-group">
                <label for="email">Email</label>
                <input type="email" id="email" name="email" maxlength="100" placeholder="email@example.com">
            </div>

            <div class="form-group">
                <label for="address">Địa Chỉ</label>
                <textarea id="address" name="address" rows="2" maxlength="500" placeholder="Địa chỉ đầy đủ"></textarea>
            </div>

            <div class="form-row">
                <div class="form-group">
                    <label for="paymentTerms">Thời Hạn Thanh Toán</label>
                    <select id="paymentTerms" name="paymentTerms">
                        <option value="">— Chọn —</option>
                        <option value="COD">COD (Nhận hàng trả tiền)</option>
                        <option value="Net 15">Net 15 — Trả trong 15 ngày</option>
                        <option value="Net 30">Net 30 — Trả trong 30 ngày</option>
                        <option value="Net 45">Net 45 — Trả trong 45 ngày</option>
                        <option value="Net 60">Net 60 — Trả trong 60 ngày</option>
                        <option value="CIA">CIA — Trả tiền trước</option>
                        <option value="Immediate">Thanh toán ngay</option>
                    </select>
                </div>
                <div class="form-group">
                    <label for="creditLimit">Hạn Mức Nợ</label>
                    <input type="number" id="creditLimit" name="creditLimit" min="0" step="1000" placeholder="0">
                </div>
            </div>
        </form>
        <div class="modal-footer">
            <button type="button" class="btn-cancel" onclick="closeModal()">Hủy</button>
            <button type="button" class="btn-save" onclick="saveSupplier()">Lưu</button>
        </div>
    </div>
</div>

<!-- ══ JAVASCRIPT ══════════════════════════════════════════════ -->
<script>
(function() {
    'use strict';

    // ─── Parse Metadata ───────────────────────────────────────
    var meta = document.getElementById('supplier-metadata');
    var state = {
        sortBy: meta.getAttribute('data-sortby') || '',
        sortDir: meta.getAttribute('data-sortdir') || 'asc',
        debtFilter: meta.getAttribute('data-debt') || 'ALL',
        keyword: meta.getAttribute('data-keyword') || '',
        total: parseInt(meta.getAttribute('data-total') || '0'),
        page: parseInt(meta.getAttribute('data-page') || '1'),
        pages: parseInt(meta.getAttribute('data-pages') || '1')
    };

    // ─── Helpers ───────────────────────────────────────────────
    function fmtVnd(n) {
        if (n === null || n === undefined || isNaN(n)) return '0 đ';
        var num = Number(n);
        return Math.round(num).toLocaleString('vi-VN') + ' đ';
    }

    function escHtml(str) {
        if (!str) return '';
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }

    // ─── Build URL ─────────────────────────────────────────────
    function buildUrl(overrides) {
        var params = new URLSearchParams();
        var opts = Object.assign({}, state, overrides || {});
        if (opts.page && opts.page > 1) params.set('page', opts.page);
        if (opts.sortBy) params.set('sortBy', opts.sortBy);
        if (opts.sortDir === 'desc') params.set('sortDir', 'desc');
        if (opts.keyword) params.set('q', opts.keyword);
        if (opts.debtFilter && opts.debtFilter !== 'ALL') params.set('debtFilter', opts.debtFilter);
        var queryString = params.toString();
        return window.location.pathname + (queryString ? '?' + queryString : '');
    }

    // ─── Update Sort Indicators ───────────────────────────────
    function updateSortIndicators() {
        document.querySelectorAll('.suppliers-table th.sortable').forEach(function(th) {
            var col = th.getAttribute('data-sort');
            th.classList.remove('sorted', 'asc', 'desc');
            if (col === state.sortBy) {
                th.classList.add('sorted');
                th.classList.add(state.sortDir === 'desc' ? 'desc' : 'asc');
            }
        });
    }

    // ─── Event Listeners ───────────────────────────────────────
    // Debt Filter
    document.getElementById('debtFilter').addEventListener('change', function() {
        window.location.href = buildUrl({ debtFilter: this.value, page: 1 });
    });

    // Search
    var searchInput = document.getElementById('supplierSearchInput');
    var searchTimeout;
    searchInput.addEventListener('input', function() {
        clearTimeout(searchTimeout);
        searchTimeout = setTimeout(function() {
            window.location.href = buildUrl({ keyword: searchInput.value.trim(), page: 1 });
        }, 500);
    });
    searchInput.addEventListener('keydown', function(e) {
        if (e.key === 'Enter') {
            clearTimeout(searchTimeout);
            window.location.href = buildUrl({ keyword: searchInput.value.trim(), page: 1 });
        }
    });

    // Sort headers
    document.querySelectorAll('.suppliers-table th.sortable').forEach(function(th) {
        th.addEventListener('click', function() {
            var col = this.getAttribute('data-sort');
            if (!col) return;
            var newDir = (state.sortBy === col && state.sortDir === 'asc') ? 'desc' : 'asc';
            window.location.href = buildUrl({ sortBy: col, sortDir: newDir });
        });
    });

    // Credit Limit: lock/disable based on paymentTerms
    var CREDIT_NO_TERMS = ['COD', 'CIA', 'Immediate', ''];
    function syncCreditLimitState() {
        var pt = document.getElementById('paymentTerms').value;
        var cl = document.getElementById('creditLimit');
        if (CREDIT_NO_TERMS.includes(pt)) {
            cl.value = '0';
            cl.disabled = true;
        } else {
            cl.disabled = false;
        }
    }
    document.getElementById('paymentTerms').addEventListener('change', syncCreditLimitState);

    // Add Button
    document.getElementById('addSupplierBtn').addEventListener('click', function() {
        openAddModal();
    });

    // ─── Modal Functions ───────────────────────────────────────
    window.openAddModal = function() {
        document.getElementById('supplierId').value = '';
        document.getElementById('supplierCode').value = '';
        document.getElementById('supplierCode').disabled = false;
        document.getElementById('name').value = '';
        document.getElementById('contactPerson').value = '';
        document.getElementById('phone').value = '';
        document.getElementById('email').value = '';
        document.getElementById('address').value = '';
        document.getElementById('paymentTerms').value = '';
        document.getElementById('creditLimit').value = '0';
        document.getElementById('status').value = 'ACTIVE';
        document.getElementById('modalTitle').textContent = 'Thêm Nhà Cung Cấp';
        document.getElementById('supplierModal').classList.add('is-open');
        syncCreditLimitState();
    }

    window.viewSupplier = function(supplierId) {
        var xhr = new XMLHttpRequest();
        xhr.open('POST', window.location.pathname, true);
        xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded');
        xhr.onreadystatechange = function() {
            if (xhr.readyState === 4) {
                if (xhr.status === 200) {
                    var ct = xhr.getResponseHeader('Content-Type') || '';
                    if (ct.indexOf('application/json') === -1) {
                        window.location.reload();
                        return;
                    }
                    try {
                        var data = JSON.parse(xhr.responseText);
                        if (data.success && data.supplier) {
                            openEditModal(data.supplier);
                        } else {
                            alert(data.message || 'Không thể tải thông tin nhà cung cấp');
                        }
                    } catch (e) {
                        alert('Lỗi xử lý dữ liệu');
                    }
                } else {
                    alert('Lỗi khi tải thông tin');
                }
            }
        };
        xhr.send('action=get&supplierId=' + supplierId);
    };

    function openEditModal(supplier) {
        document.getElementById('supplierId').value = supplier.supplierId;
        document.getElementById('supplierCode').value = supplier.supplierCode || '';
        document.getElementById('supplierCode').disabled = true;
        document.getElementById('name').value = supplier.name || '';
        document.getElementById('contactPerson').value = supplier.contactPerson || '';
        document.getElementById('phone').value = supplier.phone || '';
        document.getElementById('email').value = supplier.email || '';
        document.getElementById('address').value = supplier.address || '';
        document.getElementById('paymentTerms').value = supplier.paymentTerms || '';
        document.getElementById('creditLimit').value = supplier.creditLimit || '0';
        document.getElementById('status').value = supplier.status || 'ACTIVE';
        document.getElementById('modalTitle').textContent = 'Chi Tiết Nhà Cung Cấp';
        document.getElementById('supplierModal').classList.add('is-open');
        syncCreditLimitState();
    }

    window.closeModal = function() {
        document.getElementById('supplierModal').classList.remove('is-open');
    };

    window.saveSupplier = function() {
        var form = document.getElementById('supplierForm');
        var supplierId = document.getElementById('supplierId').value;
        var supplierCode = document.getElementById('supplierCode').value.trim();
        var name = document.getElementById('name').value.trim();

        if (!name) {
            alert('Vui lòng nhập tên nhà cung cấp');
            return;
        }

        var params = 'action=save';
        if (supplierId) params += '&supplierId=' + supplierId;
        params += '&supplierCode=' + encodeURIComponent(supplierCode);
        params += '&name=' + encodeURIComponent(name);
        params += '&contactPerson=' + encodeURIComponent(document.getElementById('contactPerson').value);
        params += '&phone=' + encodeURIComponent(document.getElementById('phone').value);
        params += '&email=' + encodeURIComponent(document.getElementById('email').value);
        params += '&address=' + encodeURIComponent(document.getElementById('address').value);
        params += '&paymentTerms=' + encodeURIComponent(document.getElementById('paymentTerms').value);
        params += '&creditLimit=' + encodeURIComponent(document.getElementById('creditLimit').value);
        params += '&status=' + encodeURIComponent(document.getElementById('status').value);

        var xhr = new XMLHttpRequest();
        xhr.open('POST', window.location.pathname, true);
        xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded');
        xhr.onreadystatechange = function() {
            if (xhr.readyState === 4) {
                if (xhr.status === 200) {
                    var ct = xhr.getResponseHeader('Content-Type') || '';
                    if (ct.indexOf('application/json') === -1) {
                        window.location.reload();
                        return;
                    }
                    try {
                        var data = JSON.parse(xhr.responseText);
                        if (data.success) {
                            alert('Lưu thành công!');
                            closeModal();
                            window.location.reload();
                        } else {
                            alert(data.message || 'Lưu thất bại');
                        }
                    } catch (e) {
                        alert('Lỗi xử lý dữ liệu');
                    }
                } else {
                    alert('Lỗi khi lưu dữ liệu');
                }
            }
        };
        xhr.send(params);
    };

    // Close modal on overlay click
    document.getElementById('supplierModal').addEventListener('click', function(e) {
        if (e.target === this) {
            closeModal();
        }
    });

    // ─── Bootstrap ─────────────────────────────────────────────
    updateSortIndicators();

    // Update count
    document.getElementById('supplierCount').textContent = '(' + state.total + ' NCC)';

})();
</script>
