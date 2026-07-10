<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%--
    BusinessDashboard body — injected into dashboard-layout.jsp via jsp:include
    Mirrors React BusinessDashboard.tsx pixel-perfect
--%>

<!-- ══ PERIOD FILTER BAR ══════════════════════════════════════ -->
<div class="filter-bar">
    <div class="period-tabs" id="periodTabs">
        <button class="period-tab ${period == '7ngay' ? 'active' : ''}" data-period="7ngay">7 ngày</button>
        <button class="period-tab ${period == '30ngay' || period == null || period == '' ? 'active' : ''}" data-period="30ngay">30 ngày</button>
        <button class="period-tab ${period == '3thang' ? 'active' : ''}" data-period="3thang">3 tháng</button>
        <button class="period-tab ${period == '6thang' ? 'active' : ''}" data-period="6thang">6 tháng</button>
        <button class="period-tab ${period == '1nam' ? 'active' : ''}" data-period="1nam">1 năm</button>
    </div>
    <div class="action-btns">
        <button class="btn-outline" id="btnRefresh">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
                 stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8"/>
                <path d="M21 3v5h-5"/><path d="M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16"/>
                <path d="M8 16H3v5"/>
            </svg>
            Làm mới
        </button>
        <button class="btn-navy" id="btnExport">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
                 stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
                <polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/>
            </svg>
            Xuất báo cáo
        </button>
    </div>
</div>

<!-- ══ KPI CARDS ══════════════════════════════════════════════ -->
<div class="kpi-grid">

    <!-- Doanh thu -->
    <div class="kpi-card tone-orange">
        <div class="kpi-card__top">
            <div class="kpi-card__icon">
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
                     stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <line x1="12" y1="1" x2="12" y2="23"/><path d="M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6"/>
                </svg>
            </div>
            <span class="kpi-badge" id="kpi-revenue-badge" style="display:none">
                <span class="badge-icon"></span>
                <span class="badge-val">0%</span>
            </span>
        </div>
        <div>
            <div class="kpi-card__value" id="kpi-revenue-value">0 (đồng)</div>
            <div class="kpi-card__sub" id="kpi-revenue-sub">30 ngày qua</div>
        </div>
        <div class="kpi-card__label">Tổng doanh thu</div>
    </div>

    <!-- Tổng đơn hàng -->
    <div class="kpi-card tone-navy">
        <div class="kpi-card__top">
            <div class="kpi-card__icon">
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
                     stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M6 2 3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4Z"/>
                    <line x1="3" y1="6" x2="21" y2="6"/><path d="M16 10a4 4 0 0 1-8 0"/>
                </svg>
            </div>
            <span class="kpi-badge" id="kpi-orders-badge" style="display:none">
                <span class="badge-icon"></span>
                <span class="badge-val">0%</span>
            </span>
        </div>
        <div>
            <div class="kpi-card__value" id="kpi-orders-value">0</div>
            <div class="kpi-card__sub" id="kpi-orders-sub">đơn đã xác nhận</div>
        </div>
        <div class="kpi-card__label">Tổng đơn hàng</div>
    </div>

    <!-- Giá trị đơn TB -->
    <div class="kpi-card tone-yellow">
        <div class="kpi-card__top">
            <div class="kpi-card__icon">
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
                     stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/>
                </svg>
            </div>
            <span class="kpi-badge" id="kpi-avg-badge" style="display:none">
                <span class="badge-icon"></span>
                <span class="badge-val">0%</span>
            </span>
        </div>
        <div>
            <div class="kpi-card__value" id="kpi-avg-value">0 (đồng)</div>
            <div class="kpi-card__sub" id="kpi-avg-sub">trên mỗi đơn hàng</div>
        </div>
        <div class="kpi-card__label">Giá trị đơn TB</div>
    </div>

    <!-- Tỷ lệ hoàn hàng -->
    <div class="kpi-card tone-red">
        <div class="kpi-card__top">
            <div class="kpi-card__icon">
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
                     stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M3 2v6h6"/><path d="M21 12A9 9 0 0 0 6 5.3L3 8"/><path d="M21 22v-6h-6"/>
                    <path d="M3 12a9 9 0 0 0 15 6.7l3-2.7"/>
                </svg>
            </div>
            <span class="kpi-badge" id="kpi-return-badge" style="display:none">
                <span class="badge-icon"></span>
                <span class="badge-val">0%</span>
            </span>
        </div>
        <div>
            <div class="kpi-card__value" id="kpi-return-value">0.0%</div>
            <div class="kpi-card__sub" id="kpi-return-sub">so với tổng đơn</div>
        </div>
        <div class="kpi-card__label">Tỷ lệ hoàn hàng</div>
    </div>
</div>

<!-- ══ REVENUE LINE CHART ═════════════════════════════════════ -->
<div class="chart-card">
    <div class="chart-card__hdr">
        <div>
            <div class="chart-card__title">Xu hướng doanh thu bán hàng theo kênh</div>
            <div class="chart-card__sub" id="chartSubLabel">Doanh thu (nghìn VNĐ) — 30 ngày gần nhất</div>
        </div>
        <div class="legend">
            <c:forEach var="ch" items="${channels}">
                <div class="legend-item"><div class="legend-dot" style="background:#69C9D0"></div><span class="legend-label">${ch.channelName}</span></div>
            </c:forEach>
        </div>
    </div>
    <div class="chart-wrap" id="lineChartWrap">
        <svg id="lineChart" viewBox="0 0 800 260" width="100%" style="display:block;overflow:visible"></svg>
        <div class="chart-tooltip" id="lineTooltip" style="display:none"></div>
    </div>
</div>

<!-- ══ PIE + DONUT ═══════════════════════════════════════════ -->
<div class="two-col-grid">

    <!-- Pie: category revenue breakdown -->
    <div class="panel">
        <div class="panel__title">Cơ cấu doanh thu theo danh mục</div>
        <div class="panel__sub" id="pieSubLabel">Doanh thu bán hàng theo danh mục trong 30 ngày</div>
        <div style="display:flex;justify-content:center;margin:16px 0;position:relative;" id="pieWrap">
            <svg id="pieChart" width="280" height="280" viewBox="0 0 280 280" style="overflow:visible"></svg>
            <div class="chart-tooltip" id="pieTooltip" style="display:none;position:absolute;top:-8px;left:50%;transform:translate(-50%,-100%)"></div>
        </div>
        <hr class="divider-light"/>
        <div id="categoryRows"></div>
    </div>

    <!-- Donut: order status -->
    <div class="panel">
        <div class="panel__title">Trạng thái đơn hàng</div>
        <div class="panel__sub">Phân bố theo tỉ lệ %</div>
        <div style="display:flex;justify-content:center;align-items:center;min-height:260px;position:relative;margin:8px 0;" id="donutWrap">
            <svg id="donutChart" width="214" height="214" viewBox="0 0 214 214" style="overflow:visible"></svg>
            <div class="donut-center" id="donutCenter"
                 style="width:120px;height:120px;top:50%;left:50%;transform:translate(-50%,-50%);">
                <span class="donut-center__num" id="donutNum">15,360</span>
                <span class="donut-center__lbl">đơn hàng</span>
            </div>
            <div class="chart-tooltip" id="donutTooltip" style="display:none;position:absolute;top:-8px;left:50%;transform:translate(-50%,-100%)"></div>
        </div>
        <hr class="divider-light"/>
        <div id="statusRows"></div>
    </div>
</div>

<!-- ══ TOP PRODUCTS TABLE ══════════════════════════════════════ -->
<div class="products-card">
    <div class="products-card__hdr">
        <div>
            <div class="products-card__title">Top sản phẩm theo doanh thu</div>
            <div class="products-card__sub" id="productsSubLabel">Top 5 sản phẩm trong 30 ngày</div>
        </div>
        <button class="btn-view-all" id="btnViewAll">Xem tất cả →</button>
    </div>
    <div id="productsTableWrap">
        <table class="data-table">
            <thead>
                <tr>
                    <th>STT</th>
                    <th>SKU</th>
                    <th>Tên sản phẩm</th>
                    <th>Kênh bán</th>
                    <th class="right">Số lượng bán</th>
                    <th class="right">Doanh thu</th>
                    <th class="right">Tăng trưởng</th>
                </tr>
            </thead>
            <tbody id="productsTableBody"></tbody>
        </table>
    </div>
</div>

<!-- HTML metadata store to safely pass JSP values without breaking JS syntax parsing in IDE -->
<div id="wms-dashboard-metadata" style="display: none;"
     data-revenue="${totalRevenue != null ? totalRevenue : 'null'}"
     data-orders="${totalOrders != null ? totalOrders : 'null'}"
     data-avg="${avgOrderValue != null ? avgOrderValue : 'null'}"
     data-return="${returnRate != null ? returnRate : 'null'}"
     data-rev-growth="${revenueGrowth != null ? revenueGrowth : 'null'}"
     data-orders-growth="${ordersGrowth != null ? ordersGrowth : 'null'}"
     data-avg-growth="${avgOrderGrowth != null ? avgOrderGrowth : 'null'}"
     data-return-growth="${returnRateGrowth != null ? returnRateGrowth : 'null'}"
     data-daily='${dailyDataJson != null ? dailyDataJson : "null"}'
     data-category='${categoryDataJson != null ? categoryDataJson : "null"}'
     data-status='${orderStatusJson != null ? orderStatusJson : "null"}'
     data-products='${topProductsJson != null ? topProductsJson : "null"}'>
</div>

<script>
(function () {
    'use strict';

    // Helper to safely parse metadata attributes
    function parseMetadata(val) {
        if (!val || val === 'null') return null;
        try {
            return JSON.parse(val);
        } catch (e) {
            var num = Number(val);
            return isNaN(num) ? val : num;
        }
    }

    var metaEl = document.getElementById('wms-dashboard-metadata');
    window.WMS_DASHBOARD_DATA = {
        totalRevenue: parseMetadata(metaEl.getAttribute('data-revenue')),
        totalOrders: parseMetadata(metaEl.getAttribute('data-orders')),
        avgOrderValue: parseMetadata(metaEl.getAttribute('data-avg')),
        returnRate: parseMetadata(metaEl.getAttribute('data-return')),
        revenueGrowth: parseMetadata(metaEl.getAttribute('data-rev-growth')),
        ordersGrowth: parseMetadata(metaEl.getAttribute('data-orders-growth')),
        avgOrderGrowth: parseMetadata(metaEl.getAttribute('data-avg-growth')),
        returnRateGrowth: parseMetadata(metaEl.getAttribute('data-return-growth')),
        dailyData: parseMetadata(metaEl.getAttribute('data-daily')),
        categoryData: parseMetadata(metaEl.getAttribute('data-category')),
        orderStatus: parseMetadata(metaEl.getAttribute('data-status')),
        topProducts: parseMetadata(metaEl.getAttribute('data-products'))
    };

/* ─── Config ─────────────────────────────────────────────── */
var CHANNEL_COLORS = { Shopee:'#EE4D2D', TikTok:'#69C9D0', Lazada:'#0F146D', Website:'#EB8317', ONLINE:'#10B981', STORE:'#F59E0B', B2B:'#8B5CF6', 'Khác':'#6B7280' };
var CHANNELS = [];
try {
    var rawChJson = '<c:out value="${channelsJson}" escapeXml="false"/>';
    if (rawChJson && rawChJson.trim() && rawChJson.indexOf('channelsJson') === -1) {
        var chData = JSON.parse(rawChJson);
        CHANNELS = chData.map(function(c) { return c.channelName; });
        chData.forEach(function(c) {
            if (!CHANNEL_COLORS[c.channelName]) {
                CHANNEL_COLORS[c.channelName] = '#69C9D0';
            }
        });
    }
} catch (e) {}
if (CHANNELS.length === 0) {
    CHANNELS = ['Shopee','TikTok','Lazada','Website','ONLINE','STORE','B2B','Khác'];
}

var CATEGORY_COLORS = ['#69C9D0', '#EB8317', '#0F146D', '#EE4D2D', '#8B5CF6', '#10B981', '#F59E0B', '#EC4899', '#3B82F6'];
function getCategoryColor(name, idx) {
    if (name === 'Khác') return '#6B7280';
    return CATEGORY_COLORS[idx % CATEGORY_COLORS.length];
}

var PERIOD_DAYS = { '7ngay':7, '30ngay':30, '3thang':90, '6thang':180, '1nam':365 };
var PERIOD_LABELS = { '7ngay':'7 ngày', '30ngay':'30 ngày', '3thang':'3 tháng', '6thang':'6 tháng', '1nam':'1 năm' };
var TICK_INTERVALS = { '7ngay':0, '30ngay':4, '3thang':14, '6thang':29, '1nam':59 };

// Bind backend data (passed from servlet) or fall back to empty
var data = window.WMS_DASHBOARD_DATA || {
    totalRevenue: null,
    totalOrders: null,
    avgOrderValue: null,
    returnRate: null,
    revenueGrowth: null,
    ordersGrowth: null,
    avgOrderGrowth: null,
    returnRateGrowth: null,
    dailyData: null,
    categoryData: null,
    orderStatus: null,
    topProducts: null
};

/* ─── State ──────────────────────────────────────────────── */
var currentPeriod = '${period != null ? period : "30ngay"}';
var showAll = false;

/* ─── Period tab click ───────────────────────────────────── */
document.querySelectorAll('.period-tab').forEach(function(btn) {
    btn.addEventListener('click', function() {
        var period = btn.dataset.period;
        window.location.href = window.location.pathname + '?period=' + period;
    });
});

/* ─── Refresh btn ────────────────────────────────────────── */
var btnRefresh = document.getElementById('btnRefresh');
if (btnRefresh) {
    btnRefresh.addEventListener('click', function() {
        var svg = btnRefresh.querySelector('svg');
        svg.style.transition = 'transform 0.5s';
        svg.style.transform = 'rotate(360deg)';
        setTimeout(function() { svg.style.transition = ''; svg.style.transform = ''; }, 600);
        updateAll();
    });
}

/* ─── View all toggle ────────────────────────────────────── */
var btnViewAll = document.getElementById('btnViewAll');
if (btnViewAll) {
    btnViewAll.addEventListener('click', function() {
        showAll = !showAll;
        btnViewAll.textContent = showAll ? '← Thu gọn' : 'Xem tất cả →';
        updateAll();
    });
}

/* ─── Export report ──────────────────────────────────────── */
var btnExport = document.getElementById('btnExport');
if (btnExport) {
    btnExport.addEventListener('click', function() {
        exportDashboardCsv();
    });
}

function fmtVndRaw(n) {
    if (n === null || n === undefined) return '0';
    return Number(n).toLocaleString('vi-VN');
}

function csvEscape(val) {
    if (val === null || val === undefined) return '';
    var s = String(val);
    if (s.indexOf(',') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0) {
        return '"' + s.replace(/"/g, '""') + '"';
    }
    return s;
}

function exportDashboardCsv() {
    var periodLabel = PERIOD_LABELS[currentPeriod] || currentPeriod;
    var now = new Date();
    var ts = now.getFullYear() + '-' +
              String(now.getMonth()+1).padStart(2,'0') + '-' +
              String(now.getDate()).padStart(2,'0') + '_' +
              String(now.getHours()).padStart(2,'0') +
              String(now.getMinutes()).padStart(2,'0');

    var lines = [];

    // ── BOM for Excel UTF-8 compatibility
    var BOM = '\uFEFF';

    // ── Section 1: KPI Tổng quan
    lines.push('=== KPI TỔNG QUAN (' + periodLabel + ') ===');
    lines.push(['Chỉ số', 'Giá trị', 'Tăng trưởng'].map(csvEscape).join(','));
    lines.push([
        'Tổng doanh thu (đồng)',
        fmtVndRaw(data.totalRevenue),
        data.revenueGrowth !== null ? (data.revenueGrowth >= 0 ? '+' : '') + Number(data.revenueGrowth).toFixed(1) + '%' : 'N/A'
    ].map(csvEscape).join(','));
    lines.push([
        'Tổng đơn hàng',
        data.totalOrders || 0,
        data.ordersGrowth !== null ? (data.ordersGrowth >= 0 ? '+' : '') + Number(data.ordersGrowth).toFixed(1) + '%' : 'N/A'
    ].map(csvEscape).join(','));
    lines.push([
        'Giá trị đơn trung bình (đồng)',
        fmtVndRaw(data.avgOrderValue),
        data.avgOrderGrowth !== null ? (data.avgOrderGrowth >= 0 ? '+' : '') + Number(data.avgOrderGrowth).toFixed(1) + '%' : 'N/A'
    ].map(csvEscape).join(','));
    lines.push([
        'Tỷ lệ hoàn trả (%)',
        data.returnRate !== null ? Number(data.returnRate).toFixed(1) : '0.0',
        data.returnRateGrowth !== null ? (data.returnRateGrowth >= 0 ? '+' : '') + Number(data.returnRateGrowth).toFixed(1) + '%' : 'N/A'
    ].map(csvEscape).join(','));
    lines.push('');

    // ── Section 2: Doanh thu theo ngày
    lines.push('=== DOANH THU THEO NGÀY ===');
    var dailyData = data.dailyData || [];
    if (dailyData.length > 0) {
        var channelCols = Object.keys(dailyData[0]).filter(function(k) { return k !== 'date'; });
        lines.push(['Ngày'].concat(channelCols).concat(['Tổng']).map(csvEscape).join(','));
        dailyData.forEach(function(row) {
            var total = channelCols.reduce(function(s, ch) { return s + (row[ch] || 0); }, 0);
            var cols = [row.date].concat(channelCols.map(function(ch) { return fmtVndRaw(row[ch] || 0); })).concat([fmtVndRaw(total)]);
            lines.push(cols.map(csvEscape).join(','));
        });
    } else {
        lines.push('Không có dữ liệu');
    }
    lines.push('');

    // ── Section 3: Doanh thu theo danh mục
    lines.push('=== DOANH THU THEO DANH MỤC ===');
    var catData = data.categoryData;
    if (catData && Object.keys(catData).length > 0) {
        lines.push(['Danh mục', 'Doanh thu (đồng)', 'Tỷ trọng (%)'].map(csvEscape).join(','));
        var catTotal = Object.values(catData).reduce(function(s, v) { return s + Number(v); }, 0);
        Object.keys(catData).forEach(function(cat) {
            var rev = Number(catData[cat]);
            var pct = catTotal > 0 ? ((rev / catTotal) * 100).toFixed(1) : '0.0';
            lines.push([cat, fmtVndRaw(rev), pct].map(csvEscape).join(','));
        });
    } else {
        lines.push('Không có dữ liệu');
    }
    lines.push('');

    // ── Section 4: Phân bổ trạng thái đơn hàng
    lines.push('=== PHÂN BỔ TRẠNG THÁI ĐƠN HÀNG ===');
    var statusList = data.orderStatus || [];
    if (statusList.length > 0) {
        lines.push(['Trạng thái', 'Số đơn', 'Tỷ trọng (%)'].map(csvEscape).join(','));
        statusList.forEach(function(s) {
            lines.push([s.name, s.count || 0, (s.value || 0).toFixed ? Number(s.value).toFixed(1) : s.value].map(csvEscape).join(','));
        });
    } else {
        lines.push('Không có dữ liệu');
    }
    lines.push('');

    // ── Section 5: Top sản phẩm
    lines.push('=== TOP SẢN PHẨM ===');
    var topProds = data.topProducts || [];
    if (topProds.length > 0) {
        lines.push(['STT', 'SKU', 'Tên sản phẩm', 'Kênh', 'Số lượng bán', 'Doanh thu (đồng)', 'Tăng trưởng (%)'].map(csvEscape).join(','));
        topProds.forEach(function(p, i) {
            var channels = (p.channels || []).join(' / ');
            lines.push([
                i + 1,
                p.sku || '',
                p.name || '',
                channels,
                p.totalQuantity || 0,
                fmtVndRaw(p.totalRevenue || 0),
                (p.growth >= 0 ? '+' : '') + Number(p.growth || 0).toFixed(1)
            ].map(csvEscape).join(','));
        });
    } else {
        lines.push('Không có dữ liệu');
    }

    // ── Trigger download
    var csvContent = BOM + lines.join('\n');
    var blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    var url = URL.createObjectURL(blob);
    var link = document.createElement('a');
    link.setAttribute('href', url);
    link.setAttribute('download', 'baocao_kinhdoanh_' + ts + '.csv');
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
}

/* ─── Helpers ────────────────────────────────────────────── */
function fmtVnd(n) {
    if (n >= 1e9) return (n / 1e9).toFixed(2) + 'B (đồng)';
    if (n >= 1e6) return (n / 1e6).toFixed(1) + 'M (đồng)';
    if (n >= 1e3) return Math.round(n / 1e3).toLocaleString() + 'K (đồng)';
    return n + ' (đồng)';
}

/* ══ UPDATE ALL ══════════════════════════════════════════ */
function updateAll() {
    var days = PERIOD_DAYS[currentPeriod];
    var label = PERIOD_LABELS[currentPeriod];

    var periodData = data.dailyData || [];
    
    var totalRev = data.totalRevenue || 0;
    var totalOrd = data.totalOrders || 0;
    var avgOrd   = data.avgOrderValue || 0;
    var retRate  = data.returnRate || 0;
    
    var revGrowth   = data.revenueGrowth;
    var ordGrowth   = data.ordersGrowth;
    var avgGrowth   = data.avgOrderGrowth;
    var retGrowth   = data.returnRateGrowth;
    
    var categoryList = [];
    var statusList  = data.orderStatus || [
        { name:'Đã giao',    value:0, color:'#10b981' },
        { name:'Đang giao',  value:0, color:'#EB8317' },
        { name:'Chờ xử lý', value:0,  color:'#F3C623' },
        { name:'Đã huỷ',    value:0,  color:'#ef4444' },
        { name:'Hoàn hàng', value:0,  color:'#8b5cf6' }
    ];
    var topProducts = data.topProducts || [];

    if (data.categoryData) {
        var idx = 0;
        Object.keys(data.categoryData).forEach(function(cat) {
            categoryList.push({
                category: cat,
                revenue: data.categoryData[cat],
                color: getCategoryColor(cat, idx++)
            });
        });
    }

    if (periodData.length === 0) {
        // Zero-state placeholders for trend chart
        for (var i = days - 1; i >= 0; i--) {
            var d = new Date();
            d.setDate(d.getDate() - i);
            var labelStr = d.getDate() + '/' + (d.getMonth() + 1);
            var row = { date: labelStr };
            CHANNELS.forEach(function(ch) { row[ch] = 0; });
            periodData.push(row);
        }
    }

    /* KPI values */
    document.getElementById('kpi-revenue-value').textContent = fmtVnd(totalRev);
    document.getElementById('kpi-revenue-sub').textContent = label + ' qua';
    document.getElementById('kpi-orders-value').textContent = totalOrd.toLocaleString();
    document.getElementById('kpi-avg-value').textContent = fmtVnd(avgOrd);
    document.getElementById('kpi-return-value').textContent = retRate.toFixed(1) + '%';

    var statusTotal = statusList.reduce(function(s, d) { return s + (d.count || 0); }, 0);
    document.getElementById('donutNum').textContent = statusTotal.toLocaleString();
    document.getElementById('chartSubLabel').textContent = 'Xu hướng doanh thu bán hàng theo kênh — ' + label + ' gần nhất';
    document.getElementById('pieSubLabel').textContent = 'Doanh thu bán hàng theo danh mục trong ' + label;
    document.getElementById('productsSubLabel').textContent =
        showAll ? 'Tất cả sản phẩm' : 'Top 5 sản phẩm trong ' + label;

    // Badges
    updateBadge('kpi-revenue-badge', revGrowth);
    updateBadge('kpi-orders-badge', ordGrowth);
    updateBadge('kpi-avg-badge', avgGrowth);
    updateBadge('kpi-return-badge', retGrowth);

    /* Charts */
    renderLineChart(periodData, TICK_INTERVALS[currentPeriod]);
    renderPieChart(categoryList);
    renderDonut(statusList, totalOrd);
    renderCategoryRows(categoryList, totalRev);
    renderStatusRows(statusList, totalOrd);
    renderProductsTable(topProducts);
}

function updateBadge(id, growth) {
    var badge = document.getElementById(id);
    if (!badge) return;
    if (growth === null || growth === undefined) {
        badge.style.display = 'none';
        return;
    }
    badge.style.display = 'inline-flex';
    var isUp = growth >= 0;
    badge.className = 'kpi-badge ' + (isUp ? 'up' : 'down');
    
    var upIcon = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="23 6 13.5 15.5 8.5 10.5 1 18"/><polyline points="17 6 23 6 23 12"/></svg>';
    var downIcon = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="23 18 13.5 8.5 8.5 13.5 1 6"/><polyline points="17 18 23 18 23 12"/></svg>';
    
    badge.innerHTML = (isUp ? upIcon : downIcon) + '<span class="badge-val">' + (isUp ? '+' : '') + growth.toFixed(1) + '%</span>';
}

/* ══ LINE CHART ═════════════════════════════════════════ */
var LC = { w:800, h:260, pl:50, pr:12, pt:10, pb:32 };
var lineTooltip = document.getElementById('lineTooltip');

function renderLineChart(data, tickInterval) {
    var svg = document.getElementById('lineChart');
    svg.innerHTML = '';
    var plotW = LC.w - LC.pl - LC.pr;
    var plotH = LC.h - LC.pt - LC.pb;

    var allVals = [];
    data.forEach(function(d) { CHANNELS.forEach(function(ch) { allVals.push(d[ch] || 0); }); });
    var maxY = Math.max.apply(null, allVals);
    if (maxY <= 0) maxY = 100;
    maxY = maxY * 1.15;

    function xS(i) { return LC.pl + (i / Math.max(data.length - 1, 1)) * plotW; }
    function yS(v) { return LC.pt + (1 - v / maxY) * plotH; }

    /* Y grid */
    var yTicks = [0, 0.25, 0.5, 0.75, 1].map(function(t) { return Math.round(maxY * t); });
    yTicks.forEach(function(v) {
        var line = mkEl('line', { x1:LC.pl, y1:yS(v), x2:LC.w-LC.pr, y2:yS(v), stroke:'#F0F3FA', 'stroke-width':1 });
        svg.appendChild(line);
        var txt = mkEl('text', { x:LC.pl-6, y:yS(v), 'text-anchor':'end', 'dominant-baseline':'middle', 'font-size':10, fill:'rgba(16,55,92,0.6)' });
        txt.textContent = v >= 1e6 ? Math.round(v/1e6)+'tr' : (v >= 1e3 ? Math.round(v/1e3)+'k' : v);
        svg.appendChild(txt);
    });

    /* X labels */
    var xTickIndices = [];
    if (tickInterval === 0) { data.forEach(function(_,i){xTickIndices.push(i);}); }
    else {
        for (var i = 0; i < data.length; i += tickInterval + 1) xTickIndices.push(i);
        if (xTickIndices[xTickIndices.length-1] !== data.length-1) xTickIndices.push(data.length-1);
    }
    xTickIndices.forEach(function(i) {
        var txt = mkEl('text', { x:xS(i), y:LC.h-LC.pb+14, 'text-anchor':'middle', 'font-size':10, fill:'rgba(16,55,92,0.6)' });
        txt.textContent = data[i].date;
        svg.appendChild(txt);
    });

    var hasData = data.some(function(d) {
        return CHANNELS.some(function(ch) { return (d[ch] || 0) > 0; });
    });

    if (!hasData) {
        var emptyText = mkEl('text', {
            x: LC.pl + plotW/2, y: LC.pt + plotH/2,
            'text-anchor': 'middle', 'dominant-baseline': 'middle',
            'font-size': 12, fill: 'rgba(16,55,92,0.3)', 'font-weight': '500'
        });
        emptyText.textContent = 'Không có dữ liệu xu hướng doanh thu';
        svg.appendChild(emptyText);
        return;
    }

    /* Lines */
    CHANNELS.forEach(function(ch) {
        var d = data.map(function(row, i) { return (i===0?'M':'L')+' '+xS(i).toFixed(1)+' '+yS(row[ch] || 0).toFixed(1); }).join(' ');
        var path = mkEl('path', { d:d, fill:'none', stroke:CHANNEL_COLORS[ch], 'stroke-width':2, 'stroke-linecap':'round', 'stroke-linejoin':'round' });
        svg.appendChild(path);
    });

    /* Hover dots (hidden by default) */
    CHANNELS.forEach(function(ch) {
        var c = mkEl('circle', { cx:0, cy:0, r:4, fill:CHANNEL_COLORS[ch], stroke:'white', 'stroke-width':2, opacity:0 });
        c.id = 'dot-'+ch;
        svg.appendChild(c);
    });

    /* Crosshair */
    var crosshair = mkEl('line', { x1:0, y1:LC.pt, x2:0, y2:LC.pt+plotH, stroke:'#10375C', 'stroke-width':1, 'stroke-dasharray':'4 3', 'stroke-opacity':0.2, opacity:0 });
    crosshair.id = 'crosshair';
    svg.appendChild(crosshair);

    /* Mouse capture */
    var overlay = mkEl('rect', { x:LC.pl, y:LC.pt, width:plotW, height:plotH, fill:'transparent' });
    overlay.addEventListener('mousemove', function(e) {
        var rect = svg.getBoundingClientRect();
        var svgX = ((e.clientX - rect.left) / rect.width) * LC.w;
        var relX = svgX - LC.pl;
        var idx = Math.max(0, Math.min(data.length-1, Math.round((relX/plotW)*(data.length-1))));
        showLineTooltip(data, idx, xS, yS, svgX/LC.w*100);
    });
    overlay.addEventListener('mouseleave', function() { hideLineTooltip(); });
    svg.appendChild(overlay);
}

function showLineTooltip(data, idx, xS, yS, pct) {
    var x = xS(idx);

    // Derive actual channel keys from this data row (exclude 'date')
    var rowKeys = Object.keys(data[idx]).filter(function(k) { return k !== 'date'; });

    // Build tooltip rows — show all channels that have a value in this row
    var ch_html = rowKeys.map(function(ch) {
        var val = data[idx][ch] || 0;
        var color = CHANNEL_COLORS[ch] || '#69C9D0';
        return '<div class="chart-tooltip__row">' +
            '<div class="chart-tooltip__ch"><div class="chart-tooltip__dot" style="background:' + color + '"></div>' + ch + '</div>' +
            '<span class="chart-tooltip__val">' + fmtVnd(val) + '</span>' +
        '</div>';
    }).join('');

    var total = rowKeys.reduce(function(s, ch) { return s + (data[idx][ch] || 0); }, 0);
    lineTooltip.innerHTML = '<div class="chart-tooltip__date">' + data[idx].date + '</div>' + ch_html +
        '<div class="chart-tooltip__total"><span>Tổng</span><span>' + fmtVnd(total) + '</span></div>';
    lineTooltip.style.display = 'block';
    lineTooltip.style.top = '8px';
    if (pct > 65) {
        lineTooltip.style.left = (pct) + '%';
        lineTooltip.style.transform = 'translateX(calc(-100% - 8px))';
    } else {
        lineTooltip.style.left = (pct) + '%';
        lineTooltip.style.transform = 'translateX(8px)';
    }

    /* Update dots & crosshair using actual row keys */
    var svg = document.getElementById('lineChart');
    var plotW = LC.w - LC.pl - LC.pr;
    var allVals = [];
    data.forEach(function(d) { rowKeys.forEach(function(ch) { allVals.push(d[ch] || 0); }); });
    var maxY = Math.max.apply(null, allVals);
    if (maxY <= 0) maxY = 100;
    maxY = maxY * 1.15;
    var xPos = LC.pl + (idx / Math.max(data.length - 1, 1)) * plotW;
    function yS2(v) { return LC.pt + (1 - v / maxY) * (LC.h - LC.pt - LC.pb); }

    rowKeys.forEach(function(ch) {
        var dot = document.getElementById('dot-' + ch);
        if (dot) { dot.setAttribute('cx', xPos); dot.setAttribute('cy', yS2(data[idx][ch] || 0)); dot.setAttribute('opacity', 1); }
    });
    var cross = document.getElementById('crosshair');
    if (cross) { cross.setAttribute('x1', xPos); cross.setAttribute('x2', xPos); cross.setAttribute('opacity', 1); }
}

function hideLineTooltip() {
    lineTooltip.style.display = 'none';
    // Hide all hover dots regardless of which channels are present
    var svg = document.getElementById('lineChart');
    if (svg) { svg.querySelectorAll('circle[id^="dot-"]').forEach(function(d) { d.setAttribute('opacity', 0); }); }
    var c = document.getElementById('crosshair'); if (c) c.setAttribute('opacity', 0);
}

/* ══ PIE CHART ══════════════════════════════════════════ */
function renderPieChart(categoryData) {
    var svg = document.getElementById('pieChart');
    svg.innerHTML = '';
    var size=280, cx=140, cy=140, r=110;
    var total = categoryData.reduce(function(s,d){return s+d.revenue;},0);

    if (total === 0) {
        var circle = mkEl('circle', { cx:cx, cy:cy, r:r, fill:'#F4F6F9', stroke:'#D8DFF0', 'stroke-width':1.5 });
        svg.appendChild(circle);
        var txt = mkEl('text', {
            x: cx, y: cy, 'text-anchor':'middle', 'dominant-baseline':'middle',
            'font-size':11, fill:'rgba(16,55,92,0.3)', 'font-weight':'500'
        });
        txt.textContent = 'Chưa có dữ liệu';
        svg.appendChild(txt);
        return;
    }

    var cumulative=0;
    categoryData.forEach(function(d,idx) {
        if (d.revenue <= 0) return; // Skip 0-revenue slices
        var start=(cumulative/total)*360;
        cumulative+=d.revenue;
        var end=(cumulative/total)*360;
        var path = mkEl('path', {
            d: arcPath(cx, cy, r, start, end),
            fill: d.color,
            style:'cursor:pointer;transition:opacity 120ms',
            opacity:1
        });
        path.addEventListener('mouseenter', function() {
            svg.querySelectorAll('path').forEach(function(p){ p.setAttribute('opacity', p === path ? 1 : 0.35); });
            showPieTooltip(d, total);
        });
        path.addEventListener('mouseleave', function() {
            svg.querySelectorAll('path').forEach(function(p){p.setAttribute('opacity',1);});
            document.getElementById('pieTooltip').style.display='none';
        });
        svg.appendChild(path);
    });
}

function showPieTooltip(d, total) {
    var tip = document.getElementById('pieTooltip');
    var pct = Math.round((d.revenue/total)*100);
    tip.innerHTML = '<div style="display:flex;align-items:center;gap:6px;margin-bottom:8px;font-weight:700;color:#10375C">'+
        '<div style="width:10px;height:10px;border-radius:50%;background:'+d.color+'"></div>'+d.category+'</div>'+
        '<div style="display:flex;justify-content:space-between;gap:16px;margin-bottom:4px"><span style="color:rgba(16,55,92,0.60)">Doanh thu</span><span style="font-weight:600;color:#10375C">'+fmtVnd(d.revenue)+'</span></div>'+
        '<div style="display:flex;justify-content:space-between;gap:16px"><span style="color:rgba(16,55,92,0.60)">Tỷ trọng</span><span style="font-weight:600;color:#10375C">'+pct+'%</span></div>';
    tip.style.display = 'block';
}

/* ══ DONUT CHART ════════════════════════════════════════ */
function renderDonut(statusList, totalOrders) {
    var svg = document.getElementById('donutChart');
    svg.innerHTML = '';
    var size=214, cx=107, cy=107, rOuter=100, rInner=62, rHover=104;
    var total = statusList.reduce(function(s,d){return s+d.value;},0);

    if (total === 0) {
        var path = mkEl('path', {
            d: donutArcPath(cx, cy, rOuter, rInner, 0, 359.9),
            fill: '#F4F6F9', stroke: '#D8DFF0', 'stroke-width': 1.5
        });
        svg.appendChild(path);
        return;
    }

    var cumulative=0;
    statusList.forEach(function(d,idx) {
        if (d.value <= 0) return; // Skip 0-value slices
        var start=(cumulative/total)*360;
        cumulative+=d.value;
        var end=(cumulative/total)*360;
        var path = mkEl('path', {
            d: donutArcPath(cx, cy, rOuter, rInner, start, end),
            fill: d.color,
            style:'cursor:pointer;transition:opacity 120ms'
        });
        path.addEventListener('mouseenter', function() {
            svg.querySelectorAll('path').forEach(function(p){ p.setAttribute('opacity', p === path ? 1 : 0.35); });
            showDonutTooltip(d, totalOrders);
        });
        path.addEventListener('mouseleave', function() {
            svg.querySelectorAll('path').forEach(function(p){p.setAttribute('opacity',1);});
            document.getElementById('donutTooltip').style.display='none';
        });
        svg.appendChild(path);
    });
}

function showDonutTooltip(d, totalOrders) {
    var tip = document.getElementById('donutTooltip');
    var count = d.count || 0;
    tip.innerHTML = '<div style="display:flex;align-items:center;gap:6px;margin-bottom:8px;font-weight:700;color:#10375C">'+
        '<div style="width:10px;height:10px;border-radius:50%;background:'+d.color+'"></div>'+d.name+'</div>'+
        '<div style="display:flex;justify-content:space-between;gap:16px;margin-bottom:4px"><span style="color:rgba(16,55,92,0.60)">Số đơn</span><span style="font-weight:600;color:#10375C">'+count.toLocaleString()+'</span></div>'+
        '<div style="display:flex;justify-content:space-between;gap:16px"><span style="color:rgba(16,55,92,0.60)">Tỷ trọng</span><span style="font-weight:600;color:#10375C">'+d.value+'%</span></div>';
    tip.style.display = 'block';
}

/* ══ CATEGORY BREAKDOWN ROWS ════════════════════════════ */
function renderCategoryRows(categoryData, totalRevenue) {
    var total = categoryData.reduce(function(s,d){return s+d.revenue;},0);
    var sorted = categoryData.slice().sort(function(a,b){return b.revenue-a.revenue;});
    var html = sorted.map(function(cat) {
        var pct = total > 0 ? Math.round((cat.revenue/total)*100) : 0;
        return '<div class="channel-row">'+
            '<div class="channel-row__dot" style="background:'+cat.color+'"></div>'+
            '<span class="channel-row__name">'+cat.category+'</span>'+
            '<div class="channel-row__bar-wrap"><div class="channel-row__bar" style="width:'+pct+'%;background:'+cat.color+'"></div></div>'+
            '<span class="channel-row__pct">'+pct+'%</span>'+
            '<span class="channel-row__rev">'+fmtVnd(cat.revenue)+'</span>'+
        '</div>';
    }).join('');
    document.getElementById('categoryRows').innerHTML = html;
}

/* ══ STATUS ROWS ════════════════════════════════════════ */
function renderStatusRows(statusList, totalOrders) {
    var totalPct = statusList.reduce(function(s,d){return s+d.value;},0);
    var html = statusList.map(function(s) {
        var barW = totalPct > 0 ? ((s.value/totalPct)*100).toFixed(1) : '0.0';
        var count = s.count || 0;
        return '<div class="status-row">'+
            '<div class="status-row__info"><span class="status-row__dot" style="background:'+s.color+'"></span><span class="status-row__name">'+s.name+'</span></div>'+
            '<div class="status-row__bar-wrap"><div class="status-row__bar" style="width:'+barW+'%;background:'+s.color+'"></div></div>'+
            '<span class="status-row__pct">'+s.value+'%</span>'+
            '<span class="status-row__count">'+count.toLocaleString()+' đơn</span>'+
        '</div>';
    }).join('');
    document.getElementById('statusRows').innerHTML = html;
}

/* ══ PRODUCTS TABLE ════════════════════════════════════ */
function renderProductsTable(productsList) {
    var products = showAll ? productsList : productsList.slice(0,5);
    if (!products || products.length === 0) {
        document.getElementById('productsTableBody').innerHTML = 
            '<tr><td colspan="7" style="text-align:center;padding:32px;color:rgba(16,55,92,0.4)">Không có dữ liệu sản phẩm</td></tr>';
        return;
    }

    var html = products.map(function(p, i) {
        var channels = p.channels.map(function(ch) {
            return '<span class="channel-badge" style="background:'+CHANNEL_COLORS[ch]+'">'+ch+'</span>';
        }).join('');
        var growthClass = p.growth >= 0 ? 'up' : 'down';
        var growthIcon = p.growth >= 0
            ? '<svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="23 6 13.5 15.5 8.5 10.5 1 18"/><polyline points="17 6 23 6 23 12"/></svg>'
            : '<svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="23 18 13.5 8.5 8.5 13.5 1 6"/><polyline points="17 18 23 18 23 12"/></svg>';
        var growthVal = (p.growth > 0 ? '+' : '') + p.growth + '%';
        return '<tr>'+
            '<td><div class="rank-badge">'+(i+1)+'</div></td>'+
            '<td><div class="sku-code">'+p.sku+'</div></td>'+
            '<td><div class="product-name">'+p.name+'</div></td>'+
            '<td>'+channels+'</td>'+
            '<td class="qty-cell">'+p.totalQuantity.toLocaleString()+'</td>'+
            '<td class="rev-cell">'+fmtVnd(p.totalRevenue)+'</td>'+
            '<td><span class="growth-cell '+growthClass+'">'+growthIcon+growthVal+'</span></td>'+
        '</tr>';
    }).join('');
    document.getElementById('productsTableBody').innerHTML = html;
}

/* ══ SVG helpers ══════════════════════════════════════ */
function mkEl(tag, attrs) {
    var el = document.createElementNS('http://www.w3.org/2000/svg', tag);
    Object.keys(attrs).forEach(function(k){ el.setAttribute(k, attrs[k]); });
    return el;
}
function arcPath(cx, cy, r, startAngle, endAngle) {
    if (endAngle - startAngle >= 360) endAngle = startAngle + 359.9;
    function toXY(a) {
        var rad = ((a-90)*Math.PI)/180;
        return [cx+r*Math.cos(rad), cy+r*Math.sin(rad)];
    }
    var s=toXY(startAngle), e=toXY(endAngle);
    var large = endAngle-startAngle > 180 ? 1:0;
    return 'M '+cx+' '+cy+' L '+s[0]+' '+s[1]+' A '+r+' '+r+' 0 '+large+' 1 '+e[0]+' '+e[1]+' Z';
}
function donutArcPath(cx, cy, rOuter, rInner, startAngle, endAngle) {
    if (endAngle - startAngle >= 360) endAngle = startAngle + 359.9;
    function toXY(a, r) {
        var rad = ((a-90)*Math.PI)/180;
        return [cx+r*Math.cos(rad), cy+r*Math.sin(rad)];
    }
    var p1=toXY(startAngle,rOuter), p2=toXY(endAngle,rOuter);
    var p3=toXY(endAngle,rInner),   p4=toXY(startAngle,rInner);
    var large = endAngle-startAngle > 180 ? 1:0;
    return 'M '+p1[0]+' '+p1[1]+' A '+rOuter+' '+rOuter+' 0 '+large+' 1 '+p2[0]+' '+p2[1]+
           ' L '+p3[0]+' '+p3[1]+' A '+rInner+' '+rInner+' 0 '+large+' 0 '+p4[0]+' '+p4[1]+' Z';
}

/* ── Bootstrap ── */
updateAll();

})();
</script>
