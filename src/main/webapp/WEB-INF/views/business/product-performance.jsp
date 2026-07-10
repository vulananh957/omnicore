<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>

<!-- ══ MAIN CONTENT ══════════════════════════════════════════ -->
<div class="perf-page">

    <!-- ── Toolbar (Single Row) ───────────────────────────── -->
    <div class="perf-toolbar">
        <div class="perf-toolbar__left">
            <!-- Search -->
            <div class="perf-search">
                <span class="perf-search__icon">
                    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                        <circle cx="11" cy="11" r="8"/><path d="m21 21-4.35-4.35"/>
                    </svg>
                </span>
                <input type="text" id="perfSearchInput" placeholder="Tìm mã, tên sản phẩm..." value="${currentSearch != null ? currentSearch : ''}">
            </div>
        </div>

        <div class="perf-toolbar__right">
            <!-- Category Filter -->
            <div class="perf-select">
                <select id="categoryFilter">
                    <option value="">Danh mục</option>
                    <% java.util.List<?> cats = (java.util.List<?>) request.getAttribute("categories"); %>
                    <% if (cats != null) {
                        String currentParent = null;
                        for (Object obj : cats) {
                            java.util.Map<?, ?> cat = (java.util.Map<?, ?>) obj;
                            String parentName = cat.get("parentName") != null ? cat.get("parentName").toString() : "Khác";
                            Integer catId = (Integer) cat.get("categoryId");
                            Integer currentCat = (Integer) request.getAttribute("currentCategory");
                            String selected = (currentCat != null && currentCat.equals(catId)) ? "selected" : "";
                            if (!parentName.equals(currentParent)) {
                                if (currentParent != null) { %></optgroup><% }
                                currentParent = parentName; %><optgroup label="<%= parentName %>"><%
                            }
                    %><option value="<%= catId %>" <%= selected %>><%= cat.get("categoryName") %></option><%
                        }
                        if (currentParent != null) { %></optgroup><% }
                    } %>
                </select>
            </div>

            <!-- Channel Filter -->
            <div class="perf-select">
                <select id="channelFilter">
                    <option value="">Kênh bán</option>
                    <c:forEach var="ch" items="${channels}">
                        <option value="${ch.channelId}" ${currentChannel == ch.channelId ? 'selected' : ''}>${ch.channelName}</option>
                    </c:forEach>
                </select>
            </div>

            <!-- Health Filter -->
            <div class="perf-select">
                <select id="healthFilter">
                    <option value="ALL" ${currentHealth == 'ALL' || currentHealth == null ? 'selected' : ''}>Tình trạng</option>
                    <option value="LOW_STOCK" ${currentHealth == 'LOW_STOCK' ? 'selected' : ''}>Sắp hết</option>
                    <option value="DEAD_STOCK" ${currentHealth == 'DEAD_STOCK' ? 'selected' : ''}>Tồn đọng</option>
                    <option value="NORMAL" ${currentHealth == 'NORMAL' ? 'selected' : ''}>Bình thường</option>
                </select>
            </div>

        </div>
    </div>

    <!-- ── Data Table ───────────────────────────────────── -->
    <div class="perf-table-card" id="listView">
        <div class="perf-table-card__header">
            <div>
                <span class="perf-table-card__title">Danh sách sản phẩm</span>
                <span class="perf-table-card__count" id="productCount">(0 SKU)</span>
            </div>
        </div>
        <div class="perf-table-wrap">
            <table class="perf-table">
                <thead>
                    <tr>
                        <th style="min-width: 180px;">Sản phẩm</th>
                        <th class="right sortable" data-sort="macprice">
                            <span class="th-label">Giá vốn</span>
                            <span class="sort-icon"></span>
                        </th>
                        <th class="right sortable" data-sort="totalsold">
                            <span class="th-label">Lượt bán</span>
                            <span class="sort-icon"></span>
                        </th>
                        <th class="right sortable" data-sort="grossmargin">
                            <span class="th-label">Biên lợi nhuận</span>
                            <span class="sort-icon"></span>
                        </th>
                        <th class="right sortable" data-sort="tiedupcapital">
                            <span class="th-label">Đọng vốn</span>
                            <span class="sort-icon"></span>
                        </th>
                        <th style="min-width: 90px;">Tình trạng</th>
                        <th style="width: 60px;">Lazada</th>
                    </tr>
                </thead>
                <tbody id="perfTableBody"></tbody>
            </table>
        </div>
    </div>

    <!-- ── Empty State ──────────────────────────────────── -->
    <div class="perf-empty" id="emptyState" style="display: none;">
        <div class="perf-empty__icon">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
                <circle cx="11" cy="11" r="8"/><path d="m21 21-4.35-4.35"/>
            </svg>
        </div>
        <div class="perf-empty__title">Không tìm thấy sản phẩm</div>
        <div class="perf-empty__text">Thử điều chỉnh bộ lọc hoặc từ khóa tìm kiếm</div>
    </div>
</div>

<!-- ══ METADATA ══════════════════════════════════════════════ -->
<div id="perf-metadata" style="display:none"
     data-sortby="${currentSortBy}"
     data-sortdir="${currentSortDir}"
     data-health="${currentHealth}"
     data-channel="${currentChannel}"
     data-category="${currentCategory}"
     data-search='${currentSearch != null ? currentSearch : ""}'
     data-data='${performanceDataJson != null ? performanceDataJson : "[]"}'>
</div>

<!-- ══ JAVASCRIPT ══════════════════════════════════════════════ -->
<script>
(function() {
    'use strict';

    // ─── Parse Metadata ───────────────────────────────────────
    function parseJsonSafe(val) {
        if (!val || val === 'null' || val === 'undefined') return null;
        try { return JSON.parse(val); } catch(e) { return null; }
    }

    var meta = document.getElementById('perf-metadata');
    var state = {
        sortBy: meta.getAttribute('data-sortby') || '',
        sortDir: meta.getAttribute('data-sortdir') || 'asc',
        health: meta.getAttribute('data-health') || 'ALL',
        channel: meta.getAttribute('data-channel') || '',
        category: meta.getAttribute('data-category') || '',
        search: meta.getAttribute('data-search') || '',
        products: parseJsonSafe(meta.getAttribute('data-data')) || []
    };

    // ─── Helpers ───────────────────────────────────────────────
    function fmtVnd(n) {
        if (n === null || n === undefined || isNaN(n)) return '0đ';
        var num = Number(n);
        if (num >= 1e9) return (num / 1e9).toFixed(2) + 'B đ';
        if (num >= 1e6) return (num / 1e6).toFixed(1) + 'M đ';
        if (num >= 1e3) return Math.round(num / 1e3).toLocaleString() + 'K đ';
        return Math.round(num).toLocaleString() + ' đ';
    }

    function fmtNumber(n) {
        if (n === null || n === undefined || isNaN(n)) return '0';
        return Number(n).toLocaleString();
    }

    function getHealthClass(status) {
        switch (status) {
            case 'NORMAL': return 'health-badge--normal';
            case 'LOW_STOCK': return 'health-badge--low-stock';
            case 'OVERSTOCKED': return 'health-badge--overstocked';
            case 'DEAD_STOCK': return 'health-badge--dead-stock';
            default: return 'health-badge--normal';
        }
    }

    function getHealthLabel(status) {
        switch (status) {
            case 'NORMAL': return 'Bình thường';
            case 'LOW_STOCK': return 'Sắp hết';
            case 'OVERSTOCKED': return 'Dư stock';
            case 'DEAD_STOCK': return 'Tồn đọng';
            default: return status;
        }
    }

    function getMarginClass(marginPct, profit) {
        if (profit < 0) return 'perf-margin--loss';
        if (marginPct < 5) return 'perf-margin--neutral';
        return 'perf-margin--profit';
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
        if (opts.period && opts.period !== '30days') params.set('period', opts.period);
        if (opts.health && opts.health !== 'ALL') params.set('healthFilter', opts.health);
        if (opts.channel) params.set('channelId', opts.channel);
        if (opts.category) params.set('categoryId', opts.category);
        if (opts.search) params.set('q', opts.search);
        if (opts.sortBy) params.set('sortBy', opts.sortBy);
        if (opts.sortDir === 'desc') params.set('sortDir', 'desc');
        var queryString = params.toString();
        return window.location.pathname + (queryString ? '?' + queryString : '');
    }

    // ─── Render Table ──────────────────────────────────────────
    function renderTable() {
        var tbody = document.getElementById('perfTableBody');
        var products = state.products || [];

        document.getElementById('productCount').textContent = '(' + products.length + ' SKU)';

        if (!products || products.length === 0) {
            document.getElementById('listView').style.display = 'none';
            document.getElementById('emptyState').style.display = 'block';
            return;
        }

        document.getElementById('listView').style.display = 'block';
        document.getElementById('emptyState').style.display = 'none';

        var html = '';
        products.forEach(function(p) {
            // Lazada URL
            var lazadaUrl = null;
            if (p.channelLinks && p.channelLinks.length > 0) {
                p.channelLinks.forEach(function(ch) {
                    if (!lazadaUrl && ch.externalUrl) lazadaUrl = ch.externalUrl;
                });
            }

            // Margin class
            var profit = p.grossProfit || 0;
            var marginPct = p.grossMarginPercent || 0;
            var marginClass = getMarginClass(marginPct, profit);

            var lazadaCell = lazadaUrl
                ? '<a href="' + lazadaUrl + '" target="_blank" rel="noopener" class="lazada-link" title="Xem trên Lazada">'
                    + '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" width="16" height="16">'
                    + '<path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"/>'
                    + '<polyline points="15 3 21 3 21 9"/><line x1="10" y1="14" x2="21" y2="3"/>'
                    + '</svg>'
                    + '</a>'
                : '—';

            html += '<tr>' +
                '<td>' +
                    '<div class="perf-product">' +
                        '<div class="perf-product__info">' +
                            '<div class="perf-product__sku">' + escHtml(p.skuCode) + '</div>' +
                            '<div class="perf-product__name" title="' + escHtml(p.productName) + '">' + escHtml(p.productName) + '</div>' +
                        '</div>' +
                    '</div>' +
                '</td>' +
                '<td class="right"><span class="perf-price">' + fmtVnd(p.macPrice) + '</span></td>' +
                '<td class="right"><span class="perf-qty">' + fmtNumber(p.totalSold) + '</span></td>' +
                '<td class="right">' +
                    '<div class="perf-margin ' + marginClass + '">' +
                        '<span class="perf-margin__pct">' + (marginPct >= 0 ? '+' : '') + marginPct.toFixed(1) + '%</span>' +
                        '<span class="perf-margin__amount">' + fmtVnd(profit) + '</span>' +
                    '</div>' +
                '</td>' +
                '<td class="right"><span class="perf-price">' + fmtVnd(p.tiedUpCapital) + '</span></td>' +
                '<td><span class="health-badge ' + getHealthClass(p.healthStatus) + '">' + getHealthLabel(p.healthStatus) + '</span></td>' +
                '<td class="center">' + lazadaCell + '</td>' +
            '</tr>';
        });

        tbody.innerHTML = html;
        updateSortIndicators();
    }

    // ─── Bootstrap ─────────────────────────────────────────────
    function updateSortIndicators() {
        document.querySelectorAll('.perf-table th.sortable').forEach(function(th) {
            var col = th.getAttribute('data-sort');
            th.classList.remove('sorted', 'asc', 'desc');
            if (col === state.sortBy) {
                th.classList.add('sorted');
                th.classList.add(state.sortDir === 'desc' ? 'desc' : 'asc');
            }
        });
    }

    // ─── Bootstrap ─────────────────────────────────────────────
    // Filters
    document.getElementById('healthFilter').addEventListener('change', function() {
        window.location.href = buildUrl({ health: this.value });
    });
    document.getElementById('channelFilter').addEventListener('change', function() {
        window.location.href = buildUrl({ channel: this.value });
    });
    document.getElementById('categoryFilter').addEventListener('change', function() {
        window.location.href = buildUrl({ category: this.value });
    });

    // Search
    var searchInput = document.getElementById('perfSearchInput');
    var searchTimeout;
    searchInput.addEventListener('input', function() {
        clearTimeout(searchTimeout);
        searchTimeout = setTimeout(function() {
            window.location.href = buildUrl({ search: searchInput.value.trim() });
        }, 500);
    });
    searchInput.addEventListener('keydown', function(e) {
        if (e.key === 'Enter') {
            clearTimeout(searchTimeout);
            window.location.href = buildUrl({ search: searchInput.value.trim() });
        }
    });

    // Sort headers
    document.querySelectorAll('.perf-table th.sortable').forEach(function(th) {
        th.addEventListener('click', function() {
            var col = this.getAttribute('data-sort');
            if (!col) return;
            var newDir = (state.sortBy === col && state.sortDir === 'asc') ? 'desc' : 'asc';
            window.location.href = buildUrl({ sortBy: col, sortDir: newDir });
        });
    });

    // ─── Bootstrap ─────────────────────────────────────────────
    renderTable();

})();
</script>
