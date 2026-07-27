/**
 * pagination-util.js — Generic client-side pagination, dùng chung cho mọi trang danh sách
 * render bằng JS (sales-orders, order-processing, master-sku, ledger, inbound/outbound/
 * transfer, stocktake, returns, sku-mapping, channel-products...).
 *
 * Mỗi trang có thể có NHIỀU bảng độc lập (vd ledger.jsp có 5 bảng con) — state được lưu
 * theo "key" riêng cho từng bảng, không dùng biến toàn cục dùng chung.
 *
 * Usage trong render function của từng trang:
 *   const result = OmniPagination.paginate('salesOrders', allOrders);
 *   // build HTML rows từ result.items (chỉ 10 dòng) thay vì toàn bộ allOrders
 *   OmniPagination.renderControls('salesOrdersPagination', result.currentPage, result.totalPages,
 *       function(newPage) { OmniPagination.setPage('salesOrders', newPage); renderTable(); });
 */
const OmniPagination = (function () {
    const PAGE_SIZE = 10;
    const state = {};

    function getPage(key) {
        return state[key] || 1;
    }

    function setPage(key, page) {
        state[key] = page;
    }

    /**
     * @param key        chuỗi định danh riêng cho bảng này (để nhiều bảng không đụng state nhau)
     * @param fullArray  toàn bộ dữ liệu chưa cắt trang
     * @param pageSize   mặc định 10, hiếm khi cần truyền khác
     * @return {items, currentPage, totalPages, totalItems, pageSize}
     */
    function paginate(key, fullArray, pageSize) {
        pageSize = pageSize || PAGE_SIZE;
        const arr = fullArray || [];
        const total = arr.length;
        const totalPages = Math.max(1, Math.ceil(total / pageSize));
        let page = getPage(key);
        if (page > totalPages) page = totalPages;
        if (page < 1) page = 1;
        setPage(key, page);
        const start = (page - 1) * pageSize;
        return {
            items: arr.slice(start, start + pageSize),
            currentPage: page,
            totalPages: totalPages,
            totalItems: total,
            pageSize: pageSize
        };
    }

    /** Reset về trang 1 — gọi khi filter/search thay đổi để tránh đứng ở trang rỗng. */
    function reset(key) {
        setPage(key, 1);
    }

    function renderControls(containerId, currentPage, totalPages, onPageChange) {
        const el = document.getElementById(containerId);
        if (!el) return;
        if (totalPages <= 1) {
            el.innerHTML = '';
            return;
        }

        const maxButtons = 7;
        let startPage = Math.max(1, currentPage - 3);
        let endPage = Math.min(totalPages, startPage + maxButtons - 1);
        startPage = Math.max(1, endPage - maxButtons + 1);

        let html = '<div class="omni-pagination">';
        html += '<button type="button" class="omni-page-btn" data-page="' + (currentPage - 1) + '"'
              + (currentPage <= 1 ? ' disabled' : '') + '>&laquo; Trước</button>';

        if (startPage > 1) {
            html += '<button type="button" class="omni-page-btn" data-page="1">1</button>';
            if (startPage > 2) html += '<span class="omni-page-ellipsis">…</span>';
        }
        for (let p = startPage; p <= endPage; p++) {
            html += '<button type="button" class="omni-page-btn' + (p === currentPage ? ' active' : '')
                  + '" data-page="' + p + '">' + p + '</button>';
        }
        if (endPage < totalPages) {
            if (endPage < totalPages - 1) html += '<span class="omni-page-ellipsis">…</span>';
            html += '<button type="button" class="omni-page-btn" data-page="' + totalPages + '">' + totalPages + '</button>';
        }

        html += '<button type="button" class="omni-page-btn" data-page="' + (currentPage + 1) + '"'
              + (currentPage >= totalPages ? ' disabled' : '') + '>Sau &raquo;</button>';
        html += '<span class="omni-page-info">Trang ' + currentPage + '/' + totalPages + '</span>';
        html += '</div>';

        el.innerHTML = html;
        el.querySelectorAll('.omni-page-btn').forEach(function (btn) {
            btn.addEventListener('click', function () {
                const p = parseInt(btn.getAttribute('data-page'), 10);
                if (!isNaN(p) && p >= 1 && p <= totalPages && p !== currentPage) {
                    onPageChange(p);
                }
            });
        });
    }

    return {
        PAGE_SIZE: PAGE_SIZE,
        paginate: paginate,
        setPage: setPage,
        getPage: getPage,
        reset: reset,
        renderControls: renderControls
    };
})();

const OMNI_PAGINATION_STYLES = `
    .omni-pagination {
        display: flex;
        align-items: center;
        gap: 6px;
        justify-content: flex-end;
        padding: 14px 4px;
        flex-wrap: wrap;
    }
    .omni-page-btn {
        min-width: 32px;
        height: 32px;
        padding: 0 8px;
        border: 1px solid var(--oc-border, #e2e8f0);
        border-radius: 6px;
        background: #fff;
        color: #334155;
        font-size: 12px;
        font-weight: 600;
        cursor: pointer;
        transition: background 0.15s, border-color 0.15s;
    }
    .omni-page-btn:hover:not(:disabled) { background: #f1f5f9; border-color: #94a3b8; }
    .omni-page-btn.active { background: #10375C; border-color: #10375C; color: #fff; }
    .omni-page-btn:disabled { opacity: 0.4; cursor: not-allowed; }
    .omni-page-ellipsis { padding: 0 4px; color: #94a3b8; font-size: 12px; }
    .omni-page-info { margin-left: 8px; font-size: 12px; color: #64748b; }
`;

document.addEventListener('DOMContentLoaded', function () {
    const style = document.createElement('style');
    style.textContent = OMNI_PAGINATION_STYLES;
    document.head.appendChild(style);
});
