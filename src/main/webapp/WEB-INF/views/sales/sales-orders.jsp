<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" isELIgnored="false" %>
    <%@ taglib prefix="c" uri="jakarta.tags.core" %>
        <%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
            <%@ taglib prefix="fn" uri="jakarta.tags.functions" %>

            <%-- ══════════════════════════════════════════════════════════════════ Sales Staff — Tất cả đơn hàng (Order
                Management) JSP port of React: OrderManagement.tsx All logic is pure vanilla JS — no hardcoded data, no
                seed data. Data will be loaded from the backend when connected.
                ══════════════════════════════════════════════════════════════════ --%>

<link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/sales--sales-orders.css?v=2"/>

                <script id="orders-seed-data" type="application/json">${ordersJson}</script>

                <%-- Lazada orders with WMS inventory data for stock table in modal --%>
                <script id="lazada-orders-seed" type="application/json">${lazadaOrdersJson}</script>

                <%-- Active warehouses for dynamic inventory table columns --%>
                <script id="warehouses-seed" type="application/json">${warehousesJson}</script>

                <%-- ── STATUS CONFIG DATA (mirroring React STATUS_CONFIG) ─────── --%>
                    <script>
                        const STATUS_CONFIG = {
                            pending_review: { label: "Chờ xác nhận", bg: "#fffbeb", text: "#d97706", border: "#fde68a", dot: "#f59e0b" },
                            confirmed: { label: "Chờ lấy hàng", bg: "#eff6ff", text: "#2563eb", border: "#bfdbfe", dot: "#3b82f6" },
                            packing: { label: "Đang đóng gói", bg: "#faf5ff", text: "#7c3aed", border: "#ddd6fe", dot: "#8b5cf6" },
                            packed: { label: "Đã đóng gói", bg: "#f0fdfa", text: "#0d9488", border: "#99f6e4", dot: "#14b8a6" },
                            shipping: { label: "Đang giao", bg: "#eef2ff", text: "#4338ca", border: "#c7d2fe", dot: "#6366f1" },
                            delivered: { label: "Đã giao", bg: "#ecfdf5", text: "#059669", border: "#a7f3d0", dot: "#10b981" },
                            completed: { label: "Hoàn thành", bg: "#f0fdf4", text: "#15803d", border: "#bbf7d0", dot: "#16a34a" },
                            returned: { label: "Trả hàng (Hoàn thành công)", bg: "#fff1f2", text: "#e11d48", border: "#fecdd3", dot: "#f43f5e" },
                            disputed: { label: "Đang khiếu nại", bg: "#fef3c7", text: "#b45309", border: "#fcd34d", dot: "#f59e0b" },
                            dispute_success: { label: "Đã bồi thường", bg: "#ecfdf5", text: "#059669", border: "#a7f3d0", dot: "#10b981" },
                            cancelled: { label: "Đã hủy", bg: "#f9fafb", text: "#374151", border: "#e5e7eb", dot: "#6b7280" },
                        };

                        const CHANNEL_COLORS = {};
                        let CHANNELS = [];
                        try {
                            const rawChannelsJson = '<c:out value="${channelsJson}" escapeXml="false"/>';
                            if (rawChannelsJson && rawChannelsJson.trim() && rawChannelsJson.indexOf('channelsJson') === -1) {
                                const parsedChannels = JSON.parse(rawChannelsJson);
                                CHANNELS = parsedChannels.map(function(channel) { return channel.channelName; });
                                parsedChannels.forEach(function(channel) {
                                    CHANNEL_COLORS[channel.channelName] = '#69C9D0';
                                });
                            }
                        } catch (e) {
                            CHANNELS = [];
                        }
                        if (CHANNELS.length === 0) {
                            CHANNELS = ["Shopee", "TikTok", "Lazada", "Website"];
                            CHANNEL_COLORS.Shopee = "#EE4D2D";
                            CHANNEL_COLORS.TikTok = "#69C9D0";
                            CHANNEL_COLORS.Lazada = "#0F146D";
                            CHANNEL_COLORS.Website = "#EB8317";
                        }
                        const SHIPPING_CARRIERS = ["SPX Express", "Lazada Express", "TikTok Express", "Viettel Post", "Tự túc"];

                        function getCarrierByChannel(ch) {
                            // Lazada uses Lazada Express (LEX), not SPX. Shopee & TikTok use SPX.
                            if (!ch) return 'Chưa chỉ định';
                            if (ch === 'Lazada') return 'Lazada Express';
                            if (ch === 'Shopee' || ch === 'TikTok') return 'SPX Express';
                            if (CHANNELS.indexOf(ch) !== -1) return 'SPX Express';
                            return 'Tự túc';
                        }

                        // ── App Stock Lookup (from server-side embedded lazadaOrders) ─────────────────
                        // lazadaOrders is loaded from JSP: find the item with matching sku
                        // then return stock for the requested warehouse
                        var LAZADA_ORDERS_DATA = [];
                        try {
                            var seedEl = document.getElementById("lazada-orders-seed");
                            if (seedEl && seedEl.textContent) {
                                LAZADA_ORDERS_DATA = JSON.parse(seedEl.textContent || "[]");
                            }
                        } catch(e) { console.warn("lazadaOrders parse error", e); }

                        var WAREHOUSES = [];
                        try {
                            var whSeedEl = document.getElementById("warehouses-seed");
                            if (whSeedEl && whSeedEl.textContent) {
                                WAREHOUSES = JSON.parse(whSeedEl.textContent || "[]");
                            }
                        } catch(e) { console.warn("warehouses parse error", e); }

                        // Parse warehouseStocks string like "Kho Hà Nội:10,Kho Hồ Chí Minh:5"
                        // Returns { "Kho Hà Nội": 10, "Kho Hồ Chí Minh": 5 }
                        function parseWarehouseStocks(warehouseStocksStr) {
                            var result = {};
                            if (!warehouseStocksStr) return result;
                            var parts = warehouseStocksStr.split(",");
                            for (var pi = 0; pi < parts.length; pi++) {
                                var kv = parts[pi].split(":");
                                if (kv.length === 2) {
                                    result[kv[0].trim()] = parseFloat(kv[1]) || 0;
                                }
                            }
                            return result;
                        }

                        function getWarehouseStock(sku, wname) {
                            if (!sku) return 0;
                            for (var i = 0; i < LAZADA_ORDERS_DATA.length; i++) {
                                var order = LAZADA_ORDERS_DATA[i];
                                var items = order.items || [];
                                for (var j = 0; j < items.length; j++) {
                                    var item = items[j];
                                    var itemSku = item.wmsSku || item.sku || '';
                                    if (itemSku === sku) {
                                        var stocks = parseWarehouseStocks(item.warehouseStocks || '');
                                        // Try exact match first, then partial
                                        if (stocks[wname] !== undefined) return stocks[wname];
                                        for (var key in stocks) {
                                            if (key.indexOf(wname.replace("Kho ", "")) !== -1) return stocks[key];
                                        }
                                    }
                                }
                            }
                            // Fallback: check wh_pricing_sales from localStorage
                            var ps = JSON.parse(localStorage.getItem('wh_pricing_sales') || '[]');
                            var record = ps.find(function (p) { return p.sku === sku; });
                            if (!record) return 0;
                            if (record.warehouseStock && record.warehouseStock[wname] !== undefined) {
                                return record.warehouseStock[wname];
                            }
                            return 0;
                        }


                        function resolvePhysicalItems(itemSku, itemQuantity, warehouseStocks) {
                            const stored = localStorage.getItem("sku_raw_mappings_v2");
                            if (!stored) return [{ sku: itemSku, name: null, quantity: itemQuantity, conversionRate: 1, isComboSplit: false, warehouseStocks: warehouseStocks || '' }];
                            try {
                                const mappings = JSON.parse(stored);
                                const relations = mappings.filter(m => m.channelSKU === itemSku);
                                if (relations.length === 0) {
                                    return [{ sku: itemSku, name: null, quantity: itemQuantity, conversionRate: 1, isComboSplit: false, warehouseStocks: warehouseStocks || '' }];
                                }
                                return relations.map(m => ({
                                    sku: m.masterSKU,
                                    name: m.masterName,
                                    quantity: itemQuantity * m.conversionRate,
                                    conversionRate: m.conversionRate,
                                    isComboSplit: true,
                                    warehouseStocks: warehouseStocks || ''
                                }));
                            } catch (e) {
                                console.error(e);
                                return [{ sku: itemSku, name: null, quantity: itemQuantity, conversionRate: 1, isComboSplit: false }];
                            }
                        }

                        const ordersSeedEl = document.getElementById("orders-seed-data");
                        let allOrders = ordersSeedEl ? JSON.parse(ordersSeedEl.textContent || "[]") : [];

                        // Merge Lazada buyer/shipping data into allOrders for table display
                        for (var _ai = 0; _ai < allOrders.length; _ai++) {
                            var _oa = allOrders[_ai];
                            var _lmatch = LAZADA_ORDERS_DATA.find(function(x) {
                                return String(x.orderCode) === String(_oa.id) || String(x.lazadaOrderIdStr) === String(_oa.id);
                            });
                            if (_lmatch) {
                                _oa.customerName = _lmatch.customerName || _lmatch.recipientName || _oa.customerName;
                                _oa.customerPhone = _lmatch.customerPhone || _oa.customerPhone;
                                _oa.shippingAddress = _lmatch.shippingAddress || _oa.shippingAddress;
                            }
                        }
                        let activeTab = "all";
                        let selectedChannel = "all";
                        let selectedStatus = "all";
                        let selectedProduct = "all";
                        let selectedCarrier = "all";
                        let searchQuery = "";
                        let activeOrderId = null;

                        // ── Init ────────────────────────────────────────────────────────────
                        document.addEventListener("DOMContentLoaded", function () {
                            // Listen for order store updates (cross-tab / React interop)
                            window.addEventListener("ORDER_STORE_UPDATED", function () {
                                const s = localStorage.getItem("b2c_orders_v2");
                                if (s) { try { allOrders = JSON.parse(s); } catch (e) { } }
                                renderAll();
                            });

                            localStorage.setItem("b2c_orders_v2", JSON.stringify(allOrders));
                            buildProductDropdown();

                            // Check initial search params
                            const urlParams = new URLSearchParams(window.location.search);
                            const tabParam = urlParams.get("tab");
                            if (tabParam && ["all", "pending_review", "awaiting_pickup", "shipping", "delivered", "completed", "returned", "cancelled"].includes(tabParam)) {
                                activeTab = tabParam;
                                // Update active tab class
                                document.querySelectorAll(".om-tab").forEach(el => {
                                    el.classList.remove("active", "active-amber", "active-blue", "active-indigo", "active-emerald", "active-green", "active-red");
                                });
                                const activeClsMap = {
                                    "all": "active",
                                    "pending_review": "active-amber",
                                    "awaiting_pickup": "active-blue",
                                    "shipping": "active-indigo",
                                    "delivered": "active-emerald",
                                    "completed": "active-green",
                                    "returned": "active-red"
                                };
                                const el = document.getElementById("tab-" + tabParam);
                                if (el) el.classList.add(activeClsMap[tabParam] || "active");
                                const statusFilter = document.getElementById("omStatusFilter");
                                if (statusFilter) statusFilter.style.display = (tabParam === "all") ? "" : "none";
                            }

                            renderAll();
                            bindEvents();
                        });

                        // ── Render everything ───────────────────────────────────────────────
                        function renderAll() {
                            renderTabCounts();
                            renderTable();
                            if (activeOrderId) {
                                const o = allOrders.find(x => x.id === activeOrderId);
                                if (o) renderModal(o);
                            }

                            // Update website RMA container visibility
                            const websiteRmaContainer = document.getElementById("websiteRmaContainer");
                            if (websiteRmaContainer) {
                                websiteRmaContainer.style.display = (activeTab === "returned") ? "block" : "none";
                            }
                        }

                        // ── Tab counts ──────────────────────────────────────────────────────
                        function renderTabCounts() {
                            const total = allOrders.length;
                            const pending = allOrders.filter(o => o.status === "pending_review").length;
                            const await_p = allOrders.filter(o => ["confirmed", "packing", "packed"].includes(o.status)).length;
                            const shipping = allOrders.filter(o => o.status === "shipping").length;
                            const delivered = allOrders.filter(o => o.status === "delivered").length;
                            const completed = allOrders.filter(o => o.status === "completed").length;
                            const returned = allOrders.filter(o => ["returned", "disputed", "dispute_success"].includes(o.status)).length;
                            const cancelled = allOrders.filter(o => o.status === "cancelled").length;

                            document.getElementById("cnt-all").textContent = total;
                            document.getElementById("cnt-pending").textContent = pending;
                            document.getElementById("cnt-await").textContent = await_p;
                            document.getElementById("cnt-shipping").textContent = shipping;
                            document.getElementById("cnt-delivered").textContent = delivered;
                            document.getElementById("cnt-completed").textContent = completed;
                            document.getElementById("cnt-returned").textContent = returned;
                            document.getElementById("cnt-cancelled").textContent = cancelled;
                        }

                        // ── Filter logic ────────────────────────────────────────────────────
                        function filteredOrders() {
                            return allOrders.filter(o => {
                                const matchTab =
                                    activeTab === "all" ||
                                    (activeTab === "pending_review" && o.status === "pending_review") ||
                                    (activeTab === "awaiting_pickup" && ["confirmed", "packing", "packed"].includes(o.status)) ||
                                    (activeTab === "shipping" && o.status === "shipping") ||
                                    (activeTab === "delivered" && o.status === "delivered") ||
                                    (activeTab === "completed" && o.status === "completed") ||
                                    (activeTab === "returned" && ["returned", "disputed", "dispute_success"].includes(o.status)) ||
                                    (activeTab === "cancelled" && o.status === "cancelled");

                                const matchCh = selectedChannel === "all" || o.channel === selectedChannel;
                                const matchSt = activeTab !== "all" || selectedStatus === "all" || o.status === selectedStatus;
                                const carrier = o.shipmentProvider || o._lazCourierName || (o.channel === "WEBSITE" ? "Chưa chỉ định" : getCarrierByChannel(o.channel));
                                const matchCar = selectedCarrier === "all" || carrier === selectedCarrier;
                                const matchProd = selectedProduct === "all" || (o.items || []).some(i => i.name === selectedProduct);
                                const q = searchQuery.toLowerCase();
                                const matchSrch = !q ||
                                    o.id.toLowerCase().includes(q) ||
                                    (o.trackingNo || "").toLowerCase().includes(q) ||
                                    (o.customerName || "").toLowerCase().includes(q) ||
                                    (o.items || []).some(i => i.name.toLowerCase().includes(q) || i.sku.toLowerCase().includes(q));

                                return matchTab && matchCh && matchSt && matchCar && matchProd && matchSrch;
                            });
                        }

                        // ── Render table ────────────────────────────────────────────────────
                        function renderTable() {
                            const rows = filteredOrders();
                            const tbody = document.getElementById("omTbody");

                            if (rows.length === 0) {
                                tbody.innerHTML = `<tr><td colspan="9" class="om-empty">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M6 2 3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4z"/><line x1="3" y1="6" x2="21" y2="6"/><path d="M16 10a4 4 0 0 1-8 0"/></svg>
            Không tìm thấy đơn hàng nào khớp với bộ lọc
        </td></tr>`;
                                return;
                            }

                            tbody.innerHTML = rows.map((o, idx) => {
                                const cfg = STATUS_CONFIG[o.status] || { label: o.status, bg: "#f9fafb", text: "#374151", border: "#e5e7eb", dot: "#6b7280" };
                                const isPending = o.status === "pending_review";
                                const chColor = CHANNEL_COLORS[o.channel] || "#6b7280";
                                const trackingHtml = o.trackingNo
                                    ? `<div class="om-tracking">\${escHtml(o.trackingNo)}</div>`
                                    : `<div class="om-no-tracking">Chưa cấp tracking</div>`;
                                const warehouseHtml = o.warehouse
                                    ? `<div class="om-warehouse"><svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m3 9 9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><polyline points="9 22 9 12 15 12 15 22"/></svg>\${escHtml(o.warehouse)}</div>`
                                    : `<span class="om-no-warehouse">Chưa chỉ định kho</span>`;

                                return `<tr class="\${isPending ? 'pending-row' : ''}" data-order-id="\${escHtml(o.id)}" onclick="openModal('\${escHtml(o.id)}')">
            <td><span class="om-stt">\${idx + 1}</span></td>
            <td>
                <div class="om-order-id">\${escHtml(o.id)}</div>
                \${trackingHtml}
            </td>
            <td>
                <span class="om-channel-badge" style="background:\${chColor}">
                    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="2" y1="12" x2="22" y2="12"/><path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"/></svg>
                    \${escHtml(o.channel)}
                </span>
            </td>
            <td>
                <div class="om-customer-name">\${escHtml(o.customerName||'')}</div>
                <div class="om-customer-phone">\${escHtml(o.customerPhone||'')}</div>
            </td>
            <td class="om-qty">\${o.totalItems||0}</td>
            <td class="om-amount">\${(o.totalAmount||0).toLocaleString()}đ</td>
            <td>
                <span class="om-status-badge" style="background:\${cfg.bg};color:\${cfg.text};border:1px solid \${cfg.border}">
                    <span class="om-status-dot" style="background:\${cfg.dot}"></span>
                    \${escHtml(cfg.label)}
                </span>
            </td>
            <td>\${warehouseHtml}</td>
            <td style="text-align:center" onclick="event.stopPropagation()">
                <button class="om-eye-btn" onclick="openModal('\${escHtml(o.id)}')" title="Xem chi tiết">
                    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
                </button>
            </td>
        </tr>`;
                            }).join("");
                        }

                        // ── Open/Close Modal ────────────────────────────────────────────────
                        function openModal(orderId) {
                            const o = allOrders.find(x => x.id === orderId);
                            if (!o) return;
                            // Also find Lazada order data for full buyer/shipping/stock info
                            const lazOrder = LAZADA_ORDERS_DATA.find(x => String(x.orderCode) === String(orderId) || String(x.lazadaOrderIdStr) === String(orderId));
                            if (lazOrder) {
                                // Merge ALL Lazada fields
                                o._lazCustomerName = lazOrder.customerName || lazOrder.recipientName || '';
                                o._lazCustomerPhone = lazOrder.customerPhone || '';
                                o._lazShippingAddress = lazOrder.shippingAddress || lazOrder.shipAddress || '';
                                o._lazShippingCity = lazOrder.shippingCity || '';
                                o._lazCustomerId = lazOrder.customerId || '';
                                o._lazOrderNumber = lazOrder.channelOrderId || lazOrder.lazadaOrderNumber || lazOrder.lazadaOrderIdStr || '';
                                o._lazPaymentMethod = lazOrder.paymentMethod || '';
                                o._lazPrice = lazOrder.price || 0;
                                o._lazShippingFee = lazOrder.shippingFee || 0;
                                o._lazVoucherSeller = lazOrder.voucherSeller || 0;
                                o._lazVoucherPlatform = lazOrder.voucherPlatform || 0;
                                o._lazCourierName = lazOrder.courierName || '';
                                o._lazTrackingNumber = lazOrder.trackingNumber || '';
                                o.shipmentProvider = lazOrder.shipmentProvider || lazOrder.shipment_provider || '';
                                o._lazCreatedAt = lazOrder.lazadaCreatedAt ? new Date(lazOrder.lazadaCreatedAt).toLocaleString('vi-VN') : (lazOrder.orderCreatedAt ? new Date(lazOrder.orderCreatedAt).toLocaleString('vi-VN') : '');
                                o._lazWarehouseCode = lazOrder.warehouseId || '';
                                o._lazItems = lazOrder.items || [];
                                o._lazChannelName = lazOrder.channelName || o.channel || '';
                            }
                            activeOrderId = orderId;
                            renderModal(o);
                            document.getElementById("omModalOverlay").classList.add("open");
                            document.body.style.overflow = "hidden";
                            document.body.classList.add("modal-open");
                            // Hide notification bell/panel while modal is open
                            var _nb = document.getElementById('notifBtn');
                            var _np = document.getElementById('notifPanel');
                            var _nbk = document.getElementById('notifBackdrop');
                            if (_nb) _nb.style.visibility = 'hidden';
                            if (_np) { _np.classList.remove('open'); _np.style.visibility = 'hidden'; }
                            if (_nbk) _nbk.style.display = 'none';
                        }

                        function closeModal() {
                            document.getElementById("omModalOverlay").classList.remove("open");
                            document.body.style.overflow = "";
                            document.body.classList.remove("modal-open");
                            // Restore notification bell
                            var _nb = document.getElementById('notifBtn');
                            var _np = document.getElementById('notifPanel');
                            if (_nb) _nb.style.visibility = '';
                            if (_np) _np.style.visibility = '';
                            activeOrderId = null;
                        }

                        // ── Render Modal ────────────────────────────────────────────────────
                        function renderModal(o) {
                            const cfg = STATUS_CONFIG[o.status] || { label: o.status, bg: "#f9fafb", text: "#374151", border: "#e5e7eb", dot: "#6b7280" };
                            const chColor = CHANNEL_COLORS[o.channel] || "#6b7280";
                            const carrier = getCarrierByChannel(o.channel);

                            // Helpers for grouping items
                            function groupOrderItems(itemList, isLaz) {
                                var grouped = {};
                                itemList.forEach(function(item) {
                                    if (!item) return;
                                    var sku = item.sku || '';
                                    var qty = parseInt(item.quantity || 1);
                                    if (!grouped[sku]) {
                                        grouped[sku] = Object.assign({}, item, {
                                            quantity: qty
                                        });
                                    } else {
                                        grouped[sku].quantity += qty;
                                    }
                                });
                                var keys = Object.keys(grouped);
                                return keys.map(function(k) { return grouped[k]; });
                            }

                            function groupForReconciliation(itemList) {
                                var grouped = {};
                                itemList.forEach(function(item) {
                                    if (!item) return;
                                    var wmsSku = item.wmsSku || item.sku || '';
                                    var qty = parseInt(item.quantity || 1);
                                    if (!grouped[wmsSku]) {
                                        grouped[wmsSku] = Object.assign({}, item, {
                                            quantity: qty
                                        });
                                    } else {
                                        grouped[wmsSku].quantity += qty;
                                    }
                                });
                                var keys = Object.keys(grouped);
                                return keys.map(function(k) { return grouped[k]; });
                            }

                            // Header
                            document.getElementById("omModalHeader").innerHTML = `
        <div>
            <div class="om-modal-header-status">
                <span class="om-channel-badge" style="background:${chColor};padding:4px 10px;font-size:11px">
                    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="2" y1="12" x2="22" y2="12"/><path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"/></svg>
                    \${escHtml(o.channel)}
                </span>
                <span style="display:inline-flex;align-items:center;gap:4px;padding:2px 10px;font-size:11px;font-weight:700;border-radius:9999px;background:rgba(255,255,255,.1);color:#fff">\${escHtml(cfg.label)}</span>
            </div>
            <div class="om-modal-title">\${o._lazOrderNumber ? 'Số đơn Lazada: ' + escHtml(o._lazOrderNumber) : 'Đơn hàng: #' + escHtml(o.id)}</div>
        </div>
        <button class="om-modal-close" onclick="closeModal()">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
        </button>
    `;

                            // Body
                            let bodyHtml = "";

                            const isLazada = (o.channel === 'Lazada');
                            const buyerName = isLazada ? (o._lazCustomerName || o.customerName || '') : (o.customerName || '');
                            const buyerPhone = isLazada ? (o._lazCustomerPhone || o.customerPhone || '') : (o.customerPhone || '');
                            const buyerAddress = isLazada ? (o._lazShippingAddress || o.customerAddress || '') : (o.customerAddress || '');
                            const buyerPayment = isLazada ? (o._lazPaymentMethod || '') : 'COD';

                            let buyerHtml = `
                                <div class="om-info-row"><span class="om-info-label">Họ và tên:</span><strong class="om-info-value">` + escHtml(buyerName) + `</strong></div>
                                <div class="om-info-row"><span class="om-info-label">Số điện thoại:</span><strong class="om-info-value">` + escHtml(buyerPhone) + `</strong></div>
                                <div class="om-info-row top-align"><span class="om-info-label">Địa chỉ giao hàng:</span><strong class="om-info-value">` + escHtml(buyerAddress) + `</strong></div>
                                ` + (buyerPayment ? '<div class="om-info-row"><span class="om-info-label">Phương thức thanh toán:</span><strong class="om-info-value">' + escHtml(buyerPayment) + '</strong></div>' : '') + `
                            `;
                            if (isLazada) {
                                buyerHtml += `
                                    <div class="om-info-row" style="margin-top:8px;font-size:10px;color:#999">
                                        * Thông tin khách hàng được Lazada ẩn phần giữa theo chính sách bảo mật cho app bên thứ 3
                                    </div>
                                `;
                            }

                            // ── Phần 1: Customer + Shipping info (Lazada full details)
                            bodyHtml += `<div class="om-info-grid">
        <div class="om-info-card">
            <div class="om-info-card-title">
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
                Thông tin người mua
            </div>
            \${o._lazOrderNumber ? '<div class="om-info-row"><span class="om-info-label">Mã khách hàng:</span><strong class="om-info-value">' + escHtml(o._lazOrderNumber) + '</strong></div>' : ''}
            ` + buyerHtml + `
        </div>
        <div class="om-info-card">
            <div class="om-info-card-title">
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="1" y="3" width="15" height="13"/><polygon points="16 8 20 8 23 11 23 16 16 16 16 8"/><circle cx="5.5" cy="18.5" r="2.5"/><circle cx="18.5" cy="18.5" r="2.5"/></svg>
                Thông tin Giao vận &amp; Thanh toán
            </div>
            <div class="om-info-row"><span class="om-info-label">Thời gian đặt:</span><strong class="om-info-value" style="font-family:monospace">\${escHtml(o._lazCreatedAt || o.createdAt || '')}</strong></div>
            \${(o.shipmentProvider) ? '<div class="om-info-row"><span class="om-info-label">Đơn vị vận chuyển:</span><strong class="om-info-value">' + escHtml(o.shipmentProvider) + '</strong></div>' : (o._lazCourierName ? '<div class="om-info-row"><span class="om-info-label">Đơn vị vận chuyển:</span><strong class="om-info-value">' + escHtml(o._lazCourierName) + '</strong></div>' : '<div class="om-info-row"><span class="om-info-label">Đơn vị vận chuyển:</span><strong class="om-info-value">' + escHtml(getCarrierByChannel(o.channel)) + '</strong></div>')}
            <div class="om-info-row"><span class="om-info-label">Mã vận đơn:</span><strong class="om-info-value" style="font-family:monospace">\${escHtml(o._lazTrackingNumber || o.trackingNo || 'Chưa tạo mã vận đơn')}</strong></div>
            \${o._lazWarehouseCode ? '<div class="om-info-row"><span class="om-info-label">Kho hàng Lazada:</span><strong class="om-info-value">' + escHtml(o._lazWarehouseCode) + '</strong></div>' : ''}
        </div>
    </div>`;

                            // ── Phần 2: Product list
                            const rawItems = (o._lazItems && o._lazItems.length > 0) ? o._lazItems : (o.items || []);
                            const isLazItems = (o._lazItems && o._lazItems.length > 0);
                            const items = groupOrderItems(rawItems, isLazItems);
                            let productsHtml = items.map(item => {
                                const itemName = isLazItems ? (item.productName || '') : (item.name || '');
                                const itemSku = item.sku || '';
                                const itemPrice = isLazItems ? parseFloat(item.paidPrice || item.itemPrice || 0) : parseFloat(item.price || 0);
                                const lineTotal = itemPrice * (item.quantity || 0);
                                const itemWmsSku = item.wmsSku || '';
                                const itemWmsName = item.wmsProductName || '';
                                const itemTotalStock = parseFloat(item.qtyAvailable || item.qtyOnHand || 0);
                                const itemWarehouseStocks = item.warehouseStocks || '';
                                const isAllocated = o.qtyAllocated;
                                const allocationBadge = isAllocated
                                    ? '<span class="om-badge-allocated"><svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="20 6 9 17 4 12" /></svg>Đã giữ kho: ' + escHtml(o.warehouse || '') + '</span>'
                                    : '<span class="om-badge-stock-ok"><svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10" /><line x1="12" y1="8" x2="12" y2="12" /><line x1="12" y1="16" x2="12.01" y2="16" /></svg>Chờ phân bổ kho</span>';
                                const physHtml = itemWmsSku ? '<div style="margin-top:0.75rem;padding-top:0.625rem;border-top:1px dashed #E5EAF3;padding-left:1rem"><div class="om-physical-label"><svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M5 12h14"/><path d="m12 5 7 7-7 7"/></svg>Đã Quy đổi thành Master SKU Vật Lý (Kho WMS):</div><div class="om-physical-row"><div><div class="om-physical-name"><svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/><polyline points="3.29 7 12 12 20.71 7"/><line x1="12" x2="12" y1="22" y2="12"/></svg>' + escHtml(itemWmsName || itemWmsSku) + '</div><div class="om-physical-sku">Master SKU: <span style="font-family:monospace;font-weight:700;color:rgba(16,55,92,.7)">' + escHtml(itemWmsSku) + '</span> ' + (itemTotalStock > 0 ? '| Tồn WMS: ' + itemTotalStock : '| Chưa có tồn kho WMS') + (itemWarehouseStocks ? '<br><span style="color:#888;font-size:11px">Phân bổ: ' + escHtml(itemWarehouseStocks) + '</span>' : '') + '</div></div><div class="om-physical-right"><div class="om-qty-req">SL yêu cầu: <strong>x' + (item.quantity||0) + '</strong></div>' + allocationBadge + '</div></div></div>' : '';
                                return '<div class="om-product-item"><div class="om-product-header"><div><div class="om-product-name">' + escHtml(itemName) + '</div><div class="om-product-meta">Channel SKU: <span style="font-family:monospace">' + escHtml(itemSku) + '</span> | Giá bán: ' + itemPrice.toLocaleString() + 'đ</div></div><div style="text-align:right;white-space:nowrap"><span class="om-product-qty">x' + (item.quantity||0) + '</span><div class="om-product-price">' + lineTotal.toLocaleString() + 'đ</div></div></div>' + physHtml + '</div>';
                            }).join("");

                            // Calculate Lazada payment breakdown
                            const lazPrice = isLazada 
                                ? parseFloat(o._lazPrice || o.totalAmount || 0)
                                : (parseFloat(o.totalAmount || 0) - parseFloat(o.shippingFee || 0));
                            const lazShip = isLazada 
                                ? parseFloat(o._lazShippingFee || 0)
                                : parseFloat(o.shippingFee || 0);
                            const lazVoucherSel = isLazada ? parseFloat(o._lazVoucherSeller || 0) : 0;
                            const lazVoucherPlat = isLazada ? parseFloat(o._lazVoucherPlatform || 0) : 0;
                            const lazTotal = isLazada 
                                ? (lazPrice + lazShip - lazVoucherSel - lazVoucherPlat)
                                : parseFloat(o.totalAmount || 0);

                            const discountHtml = isLazada ? `
                                <div style="margin-bottom:4px"><span>Giảm giá từ Cửa hàng:</span> <strong style="float:right;color:#dc2626">-\${lazVoucherSel.toLocaleString()}đ</strong></div>
                                <div style="margin-bottom:4px"><span>Giảm giá từ Lazada:</span> <strong style="float:right;color:#dc2626">-\${lazVoucherPlat.toLocaleString()}đ</strong></div>
                            ` : '';

                            bodyHtml += `<div class="om-section">
        <div class="om-section-title">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M6 2 3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4z"/><line x1="3" y1="6" x2="21" y2="6"/><path d="M16 10a4 4 0 0 1-8 0"/></svg>
            Danh sách sản phẩm từ sàn TMĐT
        </div>
        \${productsHtml}
        <div class="om-total-row">
            <span class="om-total-label">Tổng thanh toán đơn hàng (Total Paid):</span>
            <span class="om-total-amount">\${lazPrice.toLocaleString()}đ</span>
        </div>
        <div style="margin-top:8px;padding:10px;background:#f9fafb;border-radius:4px;font-size:12px">
            <div style="margin-bottom:4px"><span>Phí vận chuyển:</span> <strong style="float:right">\${lazShip.toLocaleString()}đ</strong></div>
            \${discountHtml}
            <div style="font-weight:700;border-top:1px solid #ddd;padding-top:4px;margin-top:4px"><span>Tổng cộng:</span> <strong style="float:right">\${lazTotal.toLocaleString()}đ</strong></div>
        </div>
    </div>`;

                            // ── Phần A: Inventory table
                            const reconItems = groupForReconciliation(rawItems);
                            bodyHtml += `<div class="om-section">
        <div class="om-section-title">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="m3 9 9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><polyline points="9 22 9 12 15 12 15 22"/></svg>
            Bảng đối chiếu Tồn Kho Thực Tế tại các Chi Nhánh WMS
        </div>
        <div style="border:1px solid #E5EAF3;border-radius:4px;overflow:hidden">
        <table class="om-inv-table">
            <thead><tr>
                <th style="text-align:left">Master SKU</th>
                <th style="text-align:right">Tổng tồn</th>
                \${WAREHOUSES.map(wh => '<th style="text-align:right">' + escHtml(wh.warehouseName) + '</th>').join('')}
            </tr></thead>
            <tbody>
                \${reconItems.map(item => {
                    var stocks = parseWarehouseStocks(item.warehouseStocks || '');
                    var total = 0;
                    var cells = WAREHOUSES.map(function(wh) {
                        var qty = stocks[wh.warehouseName] !== undefined ? stocks[wh.warehouseName] : 0;
                        total += qty;
                        var cls = qty >= item.quantity ? 'ok-cell' : 'dim-cell';
                        return '<td class="' + cls + '" style="text-align:right">' + qty + '</td>';
                    }).join('');
                    return '<tr>'
                        + '<td class="sku-cell">' + escHtml(item.wmsSku || item.sku || '') + '</td>'
                        + '<td class="total-cell" style="text-align:right; font-weight:800; color:var(--navy);">' + total + '</td>'
                        + cells
                        + '</tr>';
                }).join("")}
            </tbody>
        </table>
        </div>
    </div>`;

                            // ── Phần B: Timeline
                            const s = o.status;
                            const isAfterPending = s !== "pending_review" && s !== "cancelled";
                            const isAfterPacked = ["shipping", "delivered", "completed", "returned", "disputed", "dispute_success"].includes(s);
                            const isAfterDelivered = ["delivered", "completed"].includes(s);
                            const isReturned = ["returned", "disputed", "dispute_success"].includes(s);
                            const isCancelled = s === "cancelled";
                            const isPackedWait = ["confirmed", "packing", "packed"].includes(s);

                            let timelineHtml = "";

                            // Step 1: Always done
                            timelineHtml += `<div class="om-timeline-step">
        <div class="om-timeline-dot done" style="background:#10b981">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2.5"><polyline points="20 6 9 17 4 12"/></svg>
        </div>
        <div class="om-timeline-step-title">Chờ xác nhận (Order Synced)</div>
        <p class="om-timeline-step-body">Đơn hàng được ghi nhận từ sàn đa kênh. Kênh bán: \${escHtml(o.channel)}. Thời gian tạo: \${escHtml(o.createdAt||'')}</p>
    </div>`;

                            // Step 2: Warehouse packed
                            if (isAfterPending) {
                                timelineHtml += `<div class="om-timeline-step">
            <div class="om-timeline-dot done"><svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2.5"><polyline points="20 6 9 17 4 12"/></svg></div>
            <div class="om-timeline-step-title">Chuẩn bị hàng (Warehouse Packed)</div>
            <p class="om-timeline-step-body">Đã phê duyệt thủ công, phân bổ tồn kho và đóng gói hoàn tất tại: \${escHtml(o.warehouse||'WMS Kho trung tâm')}</p>
        </div>`;
                            } else if (isCancelled) {
                                timelineHtml += `<div class="om-timeline-step">
            <div class="om-timeline-dot failed"><svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2.5"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg></div>
            <div class="om-timeline-step-title red">Đơn hàng đã bị hủy (Cancelled)</div>
            <p class="om-timeline-step-body red">Lý do hủy đơn: \${escHtml(o.reviewNote||'Khách hàng hủy trên hệ thống sàn.')}</p>
        </div>`;
                            } else {
                                timelineHtml += `<div class="om-timeline-step">
            <div class="om-timeline-dot pending pulse"><svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2.5"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg></div>
            <div class="om-timeline-step-title amber">Chờ duyệt đơn &amp; Gán kho</div>
            <p class="om-timeline-step-body amber">Đang chờ Sales Staff phê duyệt chéo và chọn kho WMS xuất hàng trong trang "Xử lý đơn hàng".</p>
        </div>`;
                            }

                            if (!isCancelled) {
                                // Step 3: Shipping
                                if (isAfterPacked) {
                                    timelineHtml += `<div class="om-timeline-step">
                <div class="om-timeline-dot done"><svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2.5"><polyline points="20 6 9 17 4 12"/></svg></div>
                <div class="om-timeline-step-title">Đang giao hàng (In Transit)</div>
                <p class="om-timeline-step-body">Bàn giao ĐVVC thành công. Shipper đã bốc hàng ra khỏi kho. Mã vận đơn: \${escHtml(o.trackingNo||'')}</p>
            </div>`;
                                } else if (isPackedWait) {
                                    timelineHtml += `<div class="om-timeline-step">
                <div class="om-timeline-dot pending"><svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2.5"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg></div>
                <div class="om-timeline-step-title amber">Chờ bàn giao ĐVVC lấy hàng (Awaiting Pickup)</div>
                <p class="om-timeline-step-body amber">Kho đang chuẩn bị in mã và dán tem. Đơn sẵn sàng đợi xe bưu cục qua lấy hàng.</p>
            </div>`;
                                } else {
                                    timelineHtml += `<div class="om-timeline-step">
                <div class="om-timeline-dot inactive"><svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="rgba(16,55,92,.4)" stroke-width="2.5"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg></div>
                <div class="om-timeline-step-title dim">Chờ giao hàng (Awaiting Shipping)</div>
                <p class="om-timeline-step-body dim">Chờ chuẩn bị hàng xong để bàn giao cho hãng giao vận.</p>
            </div>`;
                                }

                                // Step 4: Delivered
                                if (isAfterDelivered) {
                                    timelineHtml += `<div class="om-timeline-step">
                <div class="om-timeline-dot done"><svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2.5"><polyline points="20 6 9 17 4 12"/></svg></div>
                <div class="om-timeline-step-title">Đã giao hàng thành công (Delivered)</div>
                <p class="om-timeline-step-body">Bưu tá đã phát hàng và giao tận tay khách hàng thành công. Đang chờ 3 ngày khiếu nại trước khi đối soát ví.</p>
            </div>`;
                                } else if (s === "shipping") {
                                    timelineHtml += `<div class="om-timeline-step">
                <div class="om-timeline-dot pending"><svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2.5"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg></div>
                <div class="om-timeline-step-title amber">Đang đi giao hàng... (Out for Delivery)</div>
                <p class="om-timeline-step-body amber">Hàng đã rời kho và đang trên xe bưu tá phát.</p>
            </div>`;
                                } else {
                                    timelineHtml += `<div class="om-timeline-step">
                <div class="om-timeline-dot inactive"><svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="rgba(16,55,92,.4)" stroke-width="2.5"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg></div>
                <div class="om-timeline-step-title dim">Đã giao hàng (Delivered)</div>
                <p class="om-timeline-step-body dim">ĐVVC chưa phát thành công tới khách.</p>
            </div>`;
                                }

                                // Step 5: Completed
                                if (s === "completed") {
                                    timelineHtml += `<div class="om-timeline-step">
                <div class="om-timeline-dot" style="background:#16a34a"><svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2.5"><polyline points="20 6 9 17 4 12"/></svg></div>
                <div class="om-timeline-step-title green">Đơn hàng Hoàn Thành (Completed)</div>
                <p class="om-timeline-step-body green">Khách hàng đã xác nhận đã nhận hoặc đã quá 3 ngày đối soát mà không có khiếu nại. Tiền hàng đã chuyển vào ví Doanh nghiệp.</p>
            </div>`;
                                } else {
                                    timelineHtml += `<div class="om-timeline-step">
                <div class="om-timeline-dot inactive"><svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="rgba(16,55,92,.4)" stroke-width="2.5"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg></div>
                <div class="om-timeline-step-title dim">Hoàn thành (Completed)</div>
                <p class="om-timeline-step-body dim">Hệ thống tự động chuyển sang hoàn thành sau 3 ngày đối soát.</p>
            </div>`;
                                }

                                // Step 6: Return
                                if (isReturned) {
                                    let disputeExtra = "";
                                    if (s === "disputed") {
                                        disputeExtra = `<span class="om-badge-dispute open"><svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>Hệ thống đang theo dõi Trạng thái Khiếu nại bồi thường trên Sàn.</span>`;
                                    } else if (s === "dispute_success") {
                                        disputeExtra = `<span class="om-badge-dispute won"><svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="20 6 9 17 4 12"/></svg>Sàn TMĐT đã duyệt khiếu nại! Shop nhận đền bù 100% giá trị ví.</span>`;
                                    }
                                    timelineHtml += `<div class="om-timeline-step">
                <div class="om-timeline-dot failed"><svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2.5"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg></div>
                <div class="om-timeline-step-title red">Đơn hàng bị hoàn trả (Returned)</div>
                <p class="om-timeline-step-body red">Khách hàng bấm hoàn hàng hoặc từ chối nhận hàng. Hàng đang được quay đầu trả về WMS.\${o.rmaReason ? ' Lý do hoàn: "' + escHtml(o.rmaReason) + '"' : ''}</p>
                \${disputeExtra}
            </div>`;
                                } else {
                                    timelineHtml += `<div class="om-timeline-step">
                <div class="om-timeline-dot inactive"><svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="rgba(16,55,92,.4)" stroke-width="2.5"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg></div>
                <div class="om-timeline-step-title dim">Hoàn hàng (Return &amp; Disputes)</div>
                <p class="om-timeline-step-body dim">Theo dõi hàng trả về và bồi thường nếu có sự cố.</p>
            </div>`;
                                }
                            }

                            // Webhook events
                            const events = o.webhookEvents || [];
                            let webhookHtml = "";
                            if (events.length > 0) {
                                webhookHtml = `<div class="om-webhook-title">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/></svg>
            Nhật ký hành trình ĐVVC (Real-time Webhook Logs)
        </div>` + events.map(evt => `<div class="om-webhook-event">
            <div class="om-webhook-dot"></div>
            <div style="flex:1">
                <div style="display:flex;align-items:center;justify-content:space-between">
                    <strong class="om-webhook-event-name">\${escHtml(evt.eventName||'')}</strong>
                    <span class="om-webhook-time">\${escHtml(evt.time||'')}</span>
                </div>
                <p class="om-webhook-desc">\${escHtml(evt.description||'')}</p>
            </div>
        </div>`).join("");
                            }

                            bodyHtml += `<div class="om-section">
        <div class="om-section-title">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/></svg>
            Hành trình vòng đời Đơn Hàng trên Sàn (E-Commerce Tracking Timeline)
        </div>
        <div class="om-timeline">\${timelineHtml}</div>
        \${webhookHtml}
    </div>`;

                            document.getElementById("omModalBody").innerHTML = bodyHtml;
                        }

                        // ── Tab switching ────────────────────────────────────────────────────
                        function switchTab(tab) {
                            activeTab = tab;

                            // Sync URL parameter
                            const url = new URL(window.location);
                            url.searchParams.set("tab", tab);
                            window.history.pushState({}, '', url);

                            // Update tab styles
                            document.querySelectorAll(".om-tab").forEach(el => {
                                el.classList.remove("active", "active-amber", "active-blue", "active-indigo", "active-emerald", "active-green", "active-red");
                            });
                            const activeClsMap = {
                                "all": "active",
                                "pending_review": "active-amber",
                                "awaiting_pickup": "active-blue",
                                "shipping": "active-indigo",
                                "delivered": "active-emerald",
                                "completed": "active-green",
                                "returned": "active-red"
                            };
                            const el = document.getElementById("tab-" + tab);
                            if (el) el.classList.add(activeClsMap[tab] || "active");

                            // Hide status filter if not "all" tab
                            const statusFilter = document.getElementById("omStatusFilter");
                            if (statusFilter) statusFilter.style.display = (tab === "all") ? "" : "none";

                            renderAll();
                        }

                        // ── Search input ─────────────────────────────────────────────────────
                        function onSearchInput(val) {
                            searchQuery = val;
                            renderTable();
                        }

                        // ── Dropdown helpers ─────────────────────────────────────────────────
                        function toggleDropdown(id) {
                            closeAllDropdowns(id);
                            document.getElementById(id).classList.toggle("open");
                        }
                        function closeAllDropdowns(except) {
                            document.querySelectorAll(".om-dropdown").forEach(d => {
                                if (d.id !== except) d.classList.remove("open");
                            });
                        }
                        function selectChannel(val) {
                            selectedChannel = val;
                            document.getElementById("omChannelLabel").textContent = val === "all" ? "Tất cả" : val;
                            document.getElementById("ddChannel").classList.remove("open");
                            renderTable();
                        }
                        function selectStatus(val) {
                            selectedStatus = val;
                            const cfg = STATUS_CONFIG[val];
                            document.getElementById("omStatusLabel").textContent = val === "all" ? "Tất cả" : (cfg ? cfg.label : val);
                            document.getElementById("ddStatus").classList.remove("open");
                            renderTable();
                        }
                        function selectProduct(val) {
                            selectedProduct = val;
                            document.getElementById("omProductLabel").textContent = val === "all" ? "Tất cả" : val;
                            document.getElementById("ddProduct").classList.remove("open");
                            renderTable();
                        }
                        function selectCarrier(val) {
                            selectedCarrier = val;
                            document.getElementById("omCarrierLabel").textContent = val === "all" ? "Tất cả" : val;
                            document.getElementById("ddCarrier").classList.remove("open");
                            renderTable();
                        }

                        function clearFilter(filterName, e) {
                            e.stopPropagation();
                            if (filterName === "channel") selectChannel("all");
                            if (filterName === "status") selectStatus("all");
                            if (filterName === "product") selectProduct("all");
                            if (filterName === "carrier") selectCarrier("all");
                        }

                        // ── Populate product dropdown dynamically ────────────────────────────
                        function buildProductDropdown() {
                            const names = [...new Set(allOrders.flatMap(o => (o.items || []).map(i => i.name)))];
                            const dd = document.getElementById("ddProduct");
                            const extra = names.map(n => `<button class="om-dropdown-item" onclick="selectProduct('\${escJs(n)}')">\${escHtml(n)}</button>`).join("");
                            const fixed = `<button onclick="selectProduct('all')" class="selected">Tất cả sản phẩm</button>`;
                            dd.innerHTML = fixed + extra;
                        }

                        // ── Bind static events ───────────────────────────────────────────────
                        function bindEvents() {
                            // Close dropdowns on outside click
                            document.addEventListener("click", function (e) {
                                if (!e.target.closest(".om-filter-btn") && !e.target.closest(".om-dropdown")) {
                                    closeAllDropdowns(null);
                                }
                            });
                            // Close modal on overlay click
                            document.getElementById("omModalOverlay").addEventListener("click", function (e) {
                                if (e.target === this) closeModal();
                            });
                            // Escape key closes modal
                            document.addEventListener("keydown", function (e) {
                                if (e.key === "Escape") closeModal();
                            });
                        }

                        // ── Utility ──────────────────────────────────────────────────────────
                        function escHtml(str) {
                            if (!str) return "";
                            return String(str).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
                        }
                        function escJs(str) {
                            if (!str) return "";
                            return String(str).replace(/'/g, "\\'").replace(/\\/g, "\\\\");
                        }

                        // ── Auto-poll: real-time order updates every 30s ──────────────────────
                        (function startOrderPolling() {
                            const POLL_INTERVAL_MS = 30000; // 30 seconds
                            let pollTimer = null;
                            let knownOrderIds = new Set(allOrders.map(o => o.id));
                            let isFetching = false;

                            function showNewOrderToast(count) {
                                let toast = document.getElementById('om-new-order-toast');
                                if (!toast) {
                                    toast = document.createElement('div');
                                    toast.id = 'om-new-order-toast';
                                    toast.style.cssText = [
                                        'position:fixed', 'top:72px', 'right:24px', 'z-index:10000',
                                        'background:linear-gradient(135deg,#10b981,#059669)',
                                        'color:#fff', 'padding:12px 20px', 'border-radius:12px',
                                        'box-shadow:0 8px 32px rgba(16,185,129,.35)',
                                        'font-size:14px', 'font-weight:600', 'cursor:pointer',
                                        'display:flex', 'align-items:center', 'gap:10px',
                                        'animation:slideInRight .3s ease-out'
                                    ].join(';');
                                    toast.innerHTML = '<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M6 2 3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4z"/><line x1="3" y1="6" x2="21" y2="6"/><path d="M16 10a4 4 0 0 1-8 0"/></svg>'
                                        + '<span id="om-new-order-toast-msg"></span>'
                                        + '<span style="margin-left:8px;opacity:.7;font-size:18px;line-height:1" onclick="document.getElementById(\'om-new-order-toast\').remove()">×</span>';
                                    document.body.appendChild(toast);
                                    setTimeout(() => { if (toast.parentNode) toast.remove(); }, 8000);
                                }
                                const msg = document.getElementById('om-new-order-toast-msg');
                                if (msg) msg.textContent = count + ' đơn hàng mới từ Lazada';
                            }

                            function doPoll() {
                                if (isFetching) return;
                                isFetching = true;
                                fetch('/sales/orders/poll', { credentials: 'same-origin' })
                                    .then(function(r) { return r.ok ? r.json() : null; })
                                    .then(function(data) {
                                        if (!data || !data.ok) return;

                                        // Update LAZADA_ORDERS_DATA in-place
                                        if (Array.isArray(data.orders)) {
                                            LAZADA_ORDERS_DATA = data.orders;
                                        }
                                        if (Array.isArray(data.warehouses)) {
                                            WAREHOUSES = data.warehouses;
                                        }

                                        // Rebuild allOrders from lazada orders (same mapping as server-side)
                                        if (Array.isArray(data.orders)) {
                                            function wmsStatusToFrontend(ws) {
                                                if (!ws) return 'pending_review';
                                                var m = {
                                                    'PENDING': 'pending_review',
                                                    'CONFIRMED': 'confirmed',
                                                    'PACKING': 'packing',
                                                    'PACKED': 'packed',
                                                    'SHIPPED': 'shipping',
                                                    'DELIVERED': 'delivered',
                                                    'COMPLETED': 'completed',
                                                    'RETURNED': 'returned',
                                                    'DISPUTED': 'disputed',
                                                    'DISPUTE_SUCCESS': 'dispute_success',
                                                    'CANCELLED': 'cancelled'
                                                };
                                                return m[ws.toUpperCase()] || ws.toLowerCase();
                                            }
                                            const newAllOrders = data.orders.map(function(lo) {
                                                var totalItems = 0;
                                                if (Array.isArray(lo.items)) {
                                                    lo.items.forEach(function(it) { totalItems += (it.quantity || 0); });
                                                }
                                                return {
                                                    id: String(lo.orderCode || lo.lazadaOrderIdStr || ''),
                                                    channel: lo.channelName || '',
                                                    customerName: lo.customerName || lo.recipientName || '',
                                                    customerPhone: lo.customerPhone || '',
                                                    totalItems: totalItems,
                                                    totalAmount: lo.totalAmount || lo.price || 0,
                                                    status: wmsStatusToFrontend(lo.wmsStatus),
                                                    trackingNo: lo.trackingNumber || '',
                                                    warehouse: lo.warehouseName || '',
                                                    items: lo.items || [],
                                                    paymentMethod: lo.paymentMethod || ''
                                                };
                                            });

                                            // Detect new orders
                                            const newIds = newAllOrders
                                                .map(o => o.id)
                                                .filter(id => id && !knownOrderIds.has(id));

                                            if (newIds.length > 0) {
                                                newAllOrders.forEach(function(nu) {
                                                    const existingIdx = allOrders.findIndex(o => o.id === nu.id);
                                                    if (existingIdx !== -1) {
                                                        allOrders[existingIdx] = nu;
                                                    } else {
                                                        allOrders.push(nu);
                                                    }
                                                    knownOrderIds.add(nu.id);
                                                });
                                                renderAll();
                                                showNewOrderToast(newIds.length);
                                            } else {
                                                // Still update status changes silently
                                                let changed = false;
                                                newAllOrders.forEach(function(nu) {
                                                    const existing = allOrders.find(o => o.id === nu.id);
                                                    if (existing && existing.status !== nu.status) {
                                                        existing.status = nu.status;
                                                        existing.trackingNo = nu.trackingNo;
                                                        existing.warehouse = nu.warehouse;
                                                        changed = true;
                                                    }
                                                });
                                                if (changed) renderAll();
                                            }
                                        }
                                    })
                                    .catch(function(e) { /* silent fail */ })
                                    .finally(function() { isFetching = false; });
                            }

                            // Start polling after DOMContentLoaded
                            document.addEventListener('DOMContentLoaded', function() {
                                pollTimer = setInterval(doPoll, POLL_INTERVAL_MS);
                            });

                            // Pause polling when tab is hidden, resume when visible
                            document.addEventListener('visibilitychange', function() {
                                if (document.hidden) {
                                    clearInterval(pollTimer);
                                    pollTimer = null;
                                } else {
                                    doPoll(); // immediate catch-up
                                    pollTimer = setInterval(doPoll, POLL_INTERVAL_MS);
                                }
                            });
                        })();
                    </script>

                    <%-- ══════════════════════════════════════════════════════════════════ PAGE HTML
                        ══════════════════════════════════════════════════════════════════ --%>

                        <%-- ── Tab Bar ──────────────────────────────────────────────────────── --%>
                            <div class="om-tab-bar">
                                <button id="tab-all" class="om-tab active" onclick="switchTab('all')">
                                    Tất cả đơn hàng
                                    <span id="cnt-all" class="om-tab-badge">0</span>
                                </button>
                                <button id="tab-pending_review" class="om-tab" onclick="switchTab('pending_review')">
                                    Chờ xác nhận
                                    <span id="cnt-pending" class="om-tab-badge">0</span>
                                </button>
                                <button id="tab-awaiting_pickup" class="om-tab" onclick="switchTab('awaiting_pickup')">
                                    Chờ ĐVVC lấy hàng
                                    <span id="cnt-await" class="om-tab-badge">0</span>
                                </button>
                                <button id="tab-shipping" class="om-tab" onclick="switchTab('shipping')">
                                    Đang giao
                                    <span id="cnt-shipping" class="om-tab-badge">0</span>
                                </button>
                                <button id="tab-delivered" class="om-tab" onclick="switchTab('delivered')">
                                    Đã giao
                                    <span id="cnt-delivered" class="om-tab-badge">0</span>
                                </button>
                                <button id="tab-completed" class="om-tab" onclick="switchTab('completed')">
                                    Hoàn thành
                                    <span id="cnt-completed" class="om-tab-badge">0</span>
                                </button>
                                <button id="tab-returned" class="om-tab" onclick="switchTab('returned')">
                                    Hoàn hàng (Return)
                                    <span id="cnt-returned" class="om-tab-badge">0</span>
                                </button>
                                <button id="tab-cancelled" class="om-tab" onclick="switchTab('cancelled')">
                                    Đã hủy
                                    <span id="cnt-cancelled" class="om-tab-badge">0</span>
                                </button>
                            </div>

                            <%-- ── Filter Bar ────────────────────────────────────────────────────── --%>
                                <div class="om-filter-bar">
                                    <%-- Search --%>
                                        <div class="om-search">
                                            <svg class="om-search-icon" xmlns="http://www.w3.org/2000/svg"
                                                viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                                                <circle cx="11" cy="11" r="8" />
                                                <path d="m21 21-4.35-4.35" />
                                            </svg>
                                            <input type="text"
                                                placeholder="Tìm theo Mã đơn, Mã vận đơn, Tên khách, SKU, Tên sản phẩm..."
                                                oninput="onSearchInput(this.value)" id="omSearchInput" />
                                        </div>

                                        <%-- Channel filter --%>
                                            <div style="position:relative">
                                                <button class="om-filter-btn" onclick="toggleDropdown('ddChannel')">
                                                    <span
                                                        style="display:flex;align-items:center;gap:6px;white-space:nowrap">
                                                        <svg class="filter-icon" xmlns="http://www.w3.org/2000/svg"
                                                            viewBox="0 0 24 24" fill="none" stroke="currentColor"
                                                            stroke-width="2">
                                                            <circle cx="12" cy="12" r="10" />
                                                            <line x1="2" y1="12" x2="22" y2="12" />
                                                            <path
                                                                d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z" />
                                                        </svg>
                                                        Kênh bán: <strong id="omChannelLabel"
                                                            style="color:var(--navy)">Tất cả</strong>
                                                    </span>
                                                    <svg class="clear-x" xmlns="http://www.w3.org/2000/svg"
                                                        viewBox="0 0 24 24" fill="none" stroke="currentColor"
                                                        stroke-width="2.5" onclick="clearFilter('channel',event)">
                                                        <line x1="18" y1="6" x2="6" y2="18" />
                                                        <line x1="6" y1="6" x2="18" y2="18" />
                                                    </svg>
                                                </button>
                                                <div id="ddChannel" class="om-dropdown">
                                                    <button onclick="selectChannel('all')" class="selected">Tất cả các
                                                        kênh</button>
                                                    <c:forEach var="ch" items="${channels}">
                                                        <button onclick="selectChannel('${ch.channelName}')">${ch.channelName}</button>
                                                    </c:forEach>
                                                </div>
                                            </div>

                                            <%-- Status filter (only shown on "all" tab) --%>
                                                <div id="omStatusFilter" style="position:relative">
                                                    <button class="om-filter-btn" onclick="toggleDropdown('ddStatus')">
                                                        <span
                                                            style="display:flex;align-items:center;gap:6px;white-space:nowrap">
                                                            <svg class="filter-icon" xmlns="http://www.w3.org/2000/svg"
                                                                viewBox="0 0 24 24" fill="none" stroke="currentColor"
                                                                stroke-width="2">
                                                                <polyline points="22 12 18 12 15 21 9 3 6 12 2 12" />
                                                            </svg>
                                                            Trạng thái: <strong id="omStatusLabel"
                                                                style="color:var(--navy)">Tất cả</strong>
                                                        </span>
                                                        <svg class="clear-x" xmlns="http://www.w3.org/2000/svg"
                                                            viewBox="0 0 24 24" fill="none" stroke="currentColor"
                                                            stroke-width="2.5" onclick="clearFilter('status',event)">
                                                            <line x1="18" y1="6" x2="6" y2="18" />
                                                            <line x1="6" y1="6" x2="18" y2="18" />
                                                        </svg>
                                                    </button>
                                                    <div id="ddStatus" class="om-dropdown right"
                                                        style="min-width:210px">
                                                        <button onclick="selectStatus('all')" class="selected">Tất cả
                                                            trạng thái</button>
                                                        <button onclick="selectStatus('pending_review')">Chờ xác
                                                            nhận</button>
                                                        <button onclick="selectStatus('confirmed')">Chờ lấy
                                                            hàng</button>
                                                        <button onclick="selectStatus('packing')">Đang đóng gói</button>
                                                        <button onclick="selectStatus('packed')">Đã đóng gói</button>
                                                        <button onclick="selectStatus('shipping')">Đang giao</button>
                                                        <button onclick="selectStatus('delivered')">Đã giao</button>
                                                        <button onclick="selectStatus('completed')">Hoàn thành</button>
                                                        <button onclick="selectStatus('returned')">Trả hàng (Hoàn thành
                                                            công)</button>
                                                        <button onclick="selectStatus('disputed')">Đang khiếu nại</button>
                                                        <button onclick="selectStatus('dispute_success')">Đã bồi
                                                            thường</button>
                                                        <button onclick="selectStatus('cancelled')">Đã hủy</button>
                                                    </div>
                                                </div>

                                                <%-- Product filter --%>
                                                    <div style="position:relative">
                                                        <button class="om-filter-btn"
                                                            onclick="toggleDropdown('ddProduct')">
                                                            <span
                                                                style="display:flex;align-items:center;gap:6px;white-space:nowrap">
                                                                <svg class="filter-icon"
                                                                    xmlns="http://www.w3.org/2000/svg"
                                                                    viewBox="0 0 24 24" fill="none"
                                                                    stroke="currentColor" stroke-width="2">
                                                                    <path
                                                                        d="M6 2 3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4z" />
                                                                    <line x1="3" y1="6" x2="21" y2="6" />
                                                                    <path d="M16 10a4 4 0 0 1-8 0" />
                                                                </svg>
                                                                Sản phẩm: <strong id="omProductLabel"
                                                                    style="color:var(--navy);max-width:140px;overflow:hidden;text-overflow:ellipsis">Tất
                                                                    cả</strong>
                                                            </span>
                                                            <svg class="clear-x" xmlns="http://www.w3.org/2000/svg"
                                                                viewBox="0 0 24 24" fill="none" stroke="currentColor"
                                                                stroke-width="2.5"
                                                                onclick="clearFilter('product',event)">
                                                                <line x1="18" y1="6" x2="6" y2="18" />
                                                                <line x1="6" y1="6" x2="18" y2="18" />
                                                            </svg>
                                                        </button>
                                                        <div id="ddProduct" class="om-dropdown right"
                                                            style="min-width:256px;max-height:240px;overflow-y:auto">
                                                            <button onclick="selectProduct('all')" class="selected">Tất
                                                                cả sản phẩm</button>
                                                        </div>
                                                    </div>

                                                    <%-- Carrier filter --%>
                                                        <div style="position:relative">
                                                            <button class="om-filter-btn"
                                                                onclick="toggleDropdown('ddCarrier')">
                                                                <span
                                                                    style="display:flex;align-items:center;gap:6px;white-space:nowrap">
                                                                    <svg class="filter-icon"
                                                                        xmlns="http://www.w3.org/2000/svg"
                                                                        viewBox="0 0 24 24" fill="none"
                                                                        stroke="currentColor" stroke-width="2">
                                                                        <rect x="1" y="3" width="15" height="13" />
                                                                        <polygon
                                                                            points="16 8 20 8 23 11 23 16 16 16 16 8" />
                                                                        <circle cx="5.5" cy="18.5" r="2.5" />
                                                                        <circle cx="18.5" cy="18.5" r="2.5" />
                                                                    </svg>
                                                                    Đơn vị vận chuyển: <strong id="omCarrierLabel"
                                                                        style="color:var(--navy)">Tất cả</strong>
                                                                </span>
                                                                <svg class="clear-x" xmlns="http://www.w3.org/2000/svg"
                                                                    viewBox="0 0 24 24" fill="none"
                                                                    stroke="currentColor" stroke-width="2.5"
                                                                    onclick="clearFilter('carrier',event)">
                                                                    <line x1="18" y1="6" x2="6" y2="18" />
                                                                    <line x1="6" y1="6" x2="18" y2="18" />
                                                                </svg>
                                                            </button>
                                                            <div id="ddCarrier" class="om-dropdown right"
                                                                style="min-width:210px">
                                                                <button onclick="selectCarrier('all')"
                                                                    class="selected">Tất cả đơn vị vận chuyển</button>
                                                                <button onclick="selectCarrier('SPX Express')">SPX
                                                                    Express</button>
                                                                <button onclick="selectCarrier('Lazada Express')">Lazada
                                                                    Express</button>
                                                                <button onclick="selectCarrier('TikTok Express')">TikTok
                                                                    Express</button>
                                                                <button onclick="selectCarrier('Viettel Post')">Viettel
                                                                    Post</button>
                                                            </div>
                                                        </div>
                                </div>

                                <%-- ── Data Table ─────────────────────────────────────────────────────── --%>
                                 <%-- Website RMA Requests Integration --%>
                                 <c:if test="${not empty pendingRmaList}">
                                     <div id="websiteRmaContainer" style="display: none; max-width: 100%; margin: 0 auto 1.5rem auto;">
                                         <h3 style="font-size: 15px; color: var(--navy); font-weight: 700; margin: 0 0 1rem 0; display: flex; align-items: center; gap: 8px;">
                                             <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" style="width: 18px; height: 18px; color: var(--orange);">
                                                 <path d="m3 9 9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>
                                                 <polyline points="9 22 9 12 15 12 15 22"/>
                                             </svg>
                                             Yêu Cầu Hoàn Trả từ Website (${fn:length(pendingRmaList)})
                                         </h3>
                                         <c:forEach var="rma" items="${pendingRmaList}">
                                             <div style="background: white; border: 1px solid #E5EAF3; border-radius: var(--radius-card); padding: 1.5rem; margin-bottom: 1rem; box-shadow: 0 1px 3px rgba(0,0,0,0.02);">
                                                 <div style="display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 1rem;">
                                                     <div>
                                                         <h3 style="color: var(--navy); font-size: 15px; font-weight: 700; margin: 0;">Đơn #${fn:escapeXml(rma.orderCode)}</h3>
                                                         <p style="color: rgba(16,55,92,0.45); font-size: 12px; margin: 0.25rem 0 0 0;">
                                                             Mã yêu cầu: ${fn:escapeXml(rma.rmaCode)} &middot;
                                                             Gửi lúc: <fmt:formatDate value="${rma.requestedAtAsDate}" pattern="dd/MM/yyyy HH:mm"/>
                                                         </p>
                                                     </div>
                                                     <span style="background: #fef9c3; color: #854d0e; padding: 2px 10px; border-radius: 999px; font-size: 11px; font-weight: 600;">Chờ duyệt</span>
                                                 </div>

                                                 <div style="background: var(--alice); border-radius: calc(var(--radius-btn) - 2px); padding: 1rem; margin-bottom: 1rem;">
                                                     <span style="font-size: 11px; font-weight: 700; color: rgba(16,55,92,0.4); text-transform: uppercase;">Lý do trả hàng</span>
                                                     <p style="margin: 0.375rem 0 0 0; font-size: 13px; color: var(--navy);">${fn:escapeXml(rma.returnReason)}</p>
                                                 </div>

                                                 <c:if test="${not empty rma.evidencePhotos}">
                                                     <div style="margin-bottom: 1rem;">
                                                         <span style="font-size: 11px; font-weight: 700; color: rgba(16,55,92,0.4); text-transform: uppercase;">Ảnh bằng chứng</span>
                                                         <div style="display: flex; gap: 8px; margin-top: 0.5rem; flex-wrap: wrap;">
                                                             <c:forEach var="photoUrl" items="${fn:split(rma.evidencePhotos, ',')}">
                                                                 <a href="${pageContext.request.contextPath}${photoUrl}" target="_blank">
                                                                     <img src="${pageContext.request.contextPath}${photoUrl}"
                                                                          style="width: 90px; height: 90px; object-fit: cover; border: 1px solid #E5EAF3; border-radius: calc(var(--radius-btn) - 4px);" />
                                                                 </a>
                                                             </c:forEach>
                                                         </div>
                                                     </div>
                                                 </c:if>

                                                 <c:if test="${not empty rma.evidenceVideo}">
                                                     <div style="margin-bottom: 1rem;">
                                                         <span style="font-size: 11px; font-weight: 700; color: rgba(16,55,92,0.4); text-transform: uppercase;">Video bằng chứng</span>
                                                         <div style="margin-top: 0.5rem;">
                                                             <video src="${pageContext.request.contextPath}${rma.evidenceVideo}" controls style="max-width: 320px; border-radius: calc(var(--radius-btn) - 4px);"></video>
                                                         </div>
                                                     </div>
                                                 </c:if>

                                                 <form method="POST" action="${pageContext.request.contextPath}/sales/rma-approval">
                                                     <input type="hidden" name="rmaId" value="${rma.rmaId}" />
                                                     <input type="hidden" name="orderId" value="${rma.orderId}" />
                                                     <input type="hidden" name="redirect" value="${pageContext.request.contextPath}/sales/orders?tab=returned" />
                                                     <label style="display: block; color: rgba(16,55,92,0.70); font-size: 12px; font-weight: 600; margin-bottom: 0.375rem;">Ghi chú duyệt / từ chối</label>
                                                     <textarea name="note" rows="2" placeholder="VD: Sản phẩm lỗi rõ ràng qua ảnh, đồng ý hoàn trả..."
                                                               style="width: 100%; padding: 0.625rem 1rem; background: var(--alice); border: 1px solid #E5EAF3; color: var(--navy); font-size: 13px; outline: none; border-radius: calc(var(--radius-btn) - 2px); margin-bottom: 0.75rem;"></textarea>
                                                     <div style="display: flex; justify-content: flex-end; gap: 0.75rem;">
                                                         <button type="submit" name="action" value="reject"
                                                                 style="padding: 0.5rem 1.25rem; background: #fee2e2; color: #991b1b; border: 1px solid #fecaca; font-size: 13px; font-weight: 600; border-radius: calc(var(--radius-btn) - 2px); cursor: pointer;">
                                                             Từ chối
                                                         </button>
                                                         <button type="submit" name="action" value="approve"
                                                                 style="padding: 0.5rem 1.25rem; background: var(--orange); color: white; border: none; font-size: 13px; font-weight: 600; border-radius: calc(var(--radius-btn) - 2px); cursor: pointer;">
                                                             Duyệt hoàn trả
                                                         </button>
                                                     </div>
                                                 </form>
                                             </div>
                                         </c:forEach>
                                     </div>
                                 </c:if>

                                    <div class="om-table-card">
                                        <div class="om-table-scroll">
                                            <table class="om-table">
                                                <thead>
                                                    <tr>
                                                        <th style="width:56px">STT</th>
                                                        <th style="width:144px">Mã đơn hàng</th>
                                                        <th style="width:128px">Kênh bán</th>
                                                        <th style="width:192px">Khách hàng</th>
                                                        <th class="text-right" style="width:96px">Số lượng</th>
                                                        <th class="text-right" style="width:128px">Tổng tiền</th>
                                                        <th style="width:192px">Trạng thái</th>
                                                        <th style="width:160px">Kho xử lý</th>
                                                        <th class="text-center" style="width:96px">Chi tiết</th>
                                                    </tr>
                                                </thead>
                                                <tbody id="omTbody">
                                                    <c:forEach var="order" items="${orderList}" varStatus="status">
                                                        <c:set var="tQty" value="0" />
                                                        <c:forEach var="item" items="${order.items}">
                                                            <c:set var="tQty" value="${tQty + item.quantity}" />
                                                        </c:forEach>
                                                        <tr class="${order.status == 'PENDING' ? 'pending-row' : ''}"
                                                            data-order-id="${order.orderCode}"
                                                            onclick="openModal('${order.orderCode}')">
                                                            <td><span class="om-stt">${status.index + 1}</span></td>
                                                            <td>
                                                                <div class="om-order-id">${order.orderCode}</div>
                                                                <div class="om-tracking">LHD-${order.orderCode}</div>
                                                            </td>
                                                            <td>
                                                                <c:choose>
                                                                    <c:when test="${order.channel == 'Shopee'}">
                                                                        <span class="om-channel-badge" style="background:#EE4D2D;">
                                                                    </c:when>
                                                                    <c:when test="${order.channel == 'TikTok'}">
                                                                        <span class="om-channel-badge" style="background:#69C9D0;">
                                                                    </c:when>
                                                                    <c:when test="${order.channel == 'Website'}">
                                                                        <span class="om-channel-badge" style="background:#EB8317;">
                                                                    </c:when>
                                                                    <c:otherwise>
                                                                        <span class="om-channel-badge" style="background:#0F146D;">
                                                                    </c:otherwise>
                                                                </c:choose>
                                                                    <svg xmlns="http://www.w3.org/2000/svg"
                                                                        viewBox="0 0 24 24" fill="none"
                                                                        stroke="currentColor" stroke-width="2">
                                                                        <circle cx="12" cy="12" r="10" />
                                                                        <line x1="2" y1="12" x2="22" y2="12" />
                                                                        <path
                                                                            d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z" />
                                                                    </svg>
                                                                    <c:choose>
                                                                        <c:when test="${order.channel == 'ONLINE'}">Lazada</c:when>
                                                                        <c:otherwise><c:out value="${order.channel}"/></c:otherwise>
                                                                    </c:choose>
                                                                </span>
                                                            </td>
                                                            <td>
                                                                <div class="om-customer-name">Khách hàng
                                                                    #${order.customerId != null ? order.customerId :
                                                                    'N/A'}</div>
                                                                <div class="om-customer-phone">090xxxxxxx</div>
                                                            </td>
                                                            <td class="om-qty">${tQty}</td>
                                                            <td class="om-amount">
                                                                <fmt:formatNumber value="${order.totalAmount}"
                                                                    type="number" maxFractionDigits="0" />đ
                                                            </td>
                                                            <td>
                                                                <c:choose>
                                                                    <c:when test="${order.status == 'PENDING'}">
                                                                        <span class="om-status-badge"
                                                                            style="background:#fffbeb;color:#d97706;border:1px solid #fde68a">
                                                                            <span class="om-status-dot"
                                                                                style="background:#f59e0b"></span>
                                                                            Chờ xác nhận
                                                                        </span>
                                                                    </c:when>
                                                                    <c:when test="${order.status == 'PACKED'}">
                                                                        <span class="om-status-badge"
                                                                            style="background:#f0fdfa;color:#0d9488;border:1px solid #99f6e4">
                                                                            <span class="om-status-dot"
                                                                                style="background:#14b8a6"></span>
                                                                            Đã đóng gói
                                                                        </span>
                                                                    </c:when>
                                                                    <c:when test="${order.status == 'SHIPPED'}">
                                                                        <span class="om-status-badge"
                                                                            style="background:#eef2ff;color:#4338ca;border:1px solid #c7d2fe">
                                                                            <span class="om-status-dot"
                                                                                style="background:#6366f1"></span>
                                                                            Đang giao
                                                                        </span>
                                                                    </c:when>
                                                                    <c:when test="${order.status == 'DELIVERED'}">
                                                                        <span class="om-status-badge"
                                                                            style="background:#ecfdf5;color:#059669;border:1px solid #a7f3d0">
                                                                            <span class="om-status-dot"
                                                                                style="background:#10b981"></span>
                                                                            Đã giao
                                                                        </span>
                                                                    </c:when>
                                                                    <c:when test="${order.status == 'CANCELLED'}">
                                                                        <span class="om-status-badge"
                                                                            style="background:#f9fafb;color:#374151;border:1px solid #e5e7eb">
                                                                            <span class="om-status-dot"
                                                                                style="background:#6b7280"></span>
                                                                            Đã hủy
                                                                        </span>
                                                                    </c:when>
                                                                    <c:otherwise>
                                                                        <span class="om-status-badge"
                                                                            style="background:#f9fafb;color:#374151;border:1px solid #e5e7eb">
                                                                            <span class="om-status-dot"
                                                                                style="background:#6b7280"></span>
                                                                            ${order.status}
                                                                        </span>
                                                                    </c:otherwise>
                                                                </c:choose>
                                                            </td>
                                                            <td>
                                                                <div class="om-warehouse">
                                                                    <svg xmlns="http://www.w3.org/2000/svg"
                                                                        viewBox="0 0 24 24" fill="none"
                                                                        stroke="currentColor" stroke-width="2"
                                                                        stroke-linecap="round" stroke-linejoin="round">
                                                                        <path
                                                                            d="m3 9 9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" />
                                                                        <polyline points="9 22 9 12 15 12 15 22" />
                                                                    </svg>
                                                                    ${order.warehouseName != null ? fn:escapeXml(order.warehouseName) : 'Chua chi dinh kho'}
                                                                </div>
                                                            </td>
                                                            <td style="text-align:center"
                                                                onclick="event.stopPropagation()">
                                                                <button class="om-eye-btn"
                                                                    onclick="openModal('${order.orderCode}')"
                                                                    title="Xem chi tiết">
                                                                    <svg xmlns="http://www.w3.org/2000/svg"
                                                                        viewBox="0 0 24 24" fill="none"
                                                                        stroke="currentColor" stroke-width="2">
                                                                        <path
                                                                            d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
                                                                        <circle cx="12" cy="12" r="3" />
                                                                    </svg>
                                                                </button>
                                                            </td>
                                                        </tr>
                                                    </c:forEach>
                                                </tbody>
                                            </table>
                                        </div>
                                    </div>

                                    <%-- ── Detail Modal ───────────────────────────────────────────────────── --%>
                                        <div id="omModalOverlay" class="om-modal-overlay">
                                            <div class="om-modal">
                                                <div class="om-modal-header" id="omModalHeader">
                                                    <%-- Rendered by JS --%>
                                                </div>
                                                <div class="om-modal-body" id="omModalBody">
                                                    <%-- Rendered by JS --%>
                                                </div>
                                                <div class="om-modal-footer">
                                                    <button class="om-close-btn" onclick="closeModal()">Đóng tra
                                                        cứu</button>
                                                </div>
                                            </div>
                                        </div>