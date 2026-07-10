<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>

<link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/warehouse--warehouse-information.css"/>

<c:if test="${not empty successMessage}">
    <div style="margin-bottom:16px; padding:12px 16px; border-radius:10px; background:#ECFDF5; border:1px solid #A7F3D0; color:#047857; font-size:13px; font-weight:600;">
        <c:out value="${successMessage}"/>
    </div>
</c:if>
<c:if test="${not empty errorMessage}">
    <div style="margin-bottom:16px; padding:12px 16px; border-radius:10px; background:#FEF2F2; border:1px solid #FECACA; color:#b91c1c; font-size:13px; font-weight:600;">
        <c:out value="${errorMessage}"/>
    </div>
</c:if>

<c:choose>
    <c:when test="${empty warehouse}">
        <div class="whinfo-empty">
            Không tìm thấy thông tin kho được phân công.
        </div>
    </c:when>
    <c:otherwise>

        <%-- ══ Warehouse Info Header ═══════════════════════════════════ --%>
        <div class="whinfo-hdr">
            <div class="whinfo-hdr__badge">
                <svg xmlns="http://www.w3.org/2000/svg" width="22" height="22" viewBox="0 0 24 24" fill="none"
                     stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>
                    <polyline points="9 22 9 12 15 12 15 22"/>
                    </svg>
                </div>
            <div class="whinfo-hdr__body">
                <div class="whinfo-hdr__top">
                    <div>
                        <div class="whinfo-hdr__code"><c:out value="${warehouse.warehouseCode}"/></div>
                        <div class="whinfo-hdr__name"><c:out value="${warehouse.warehouseName}"/></div>
                    </div>
                    <div class="whinfo-hdr__meta">
                        <c:choose>
                            <c:when test="${warehouse.active}">
                                <span class="whinfo-pill whinfo-pill--active">
                                    <span class="whinfo-pill__dot"></span>Đang hoạt động
                                </span>
                            </c:when>
                            <c:otherwise>
                                <span class="whinfo-pill whinfo-pill--inactive">
                                    <span class="whinfo-pill__dot"></span>Ngừng hoạt động
                                </span>
                            </c:otherwise>
                        </c:choose>
                    </div>
                </div>
                <%-- 40/60 split: admin fields | live metrics --%>
                <div class="whinfo-hdr__split">
                    <%-- Left: admin (40%) --%>
                    <div class="whinfo-admin-fields">
                        <div class="whinfo-admin-field">
                            <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none"
                                 stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                                <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
                                <circle cx="12" cy="7" r="4"/>
                            </svg>
                            <span class="whinfo-admin-field__lbl">Nhân viên</span>
                            <span class="whinfo-admin-field__val">
                                <c:choose>
                                    <c:when test="${not empty warehouseStaff}">
                                        <c:forEach var="s" items="${warehouseStaff}" varStatus="vs">
                                            <c:out value="${s.fullName}"/><c:if test="${!vs.last}">, </c:if>
                                        </c:forEach>
                                    </c:when>
                                    <c:otherwise>—</c:otherwise>
                                </c:choose>
                            </span>
                        </div>
                        <div class="whinfo-admin-field">
                            <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none"
                                 stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                                <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07A19.5 19.5 0 0 1 4.69 13.5 19.79 19.79 0 0 1 1.61 4.9 2 2 0 0 1 3.6 2.73h3a2 2 0 0 1 2 1.72c.127.96.361 1.903.7 2.81a2 2 0 0 1-.45 2.11L7.91 10a16 16 0 0 0 6 6l.95-.95a2 2 0 0 1 2.11-.45c.907.339 1.85.573 2.81.7A2 2 0 0 1 22 16.92z"/>
                            </svg>
                            <span class="whinfo-admin-field__lbl">Hotline</span>
                            <span class="whinfo-admin-field__val"><c:out value="${not empty warehouse.phone ? warehouse.phone : '—'}"/></span>
                        </div>
                        <div class="whinfo-admin-field">
                            <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none"
                                 stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                                <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"/>
                                <circle cx="12" cy="10" r="3"/>
                            </svg>
                            <span class="whinfo-admin-field__lbl">Địa chỉ</span>
                            <span class="whinfo-admin-field__val"><c:out value="${not empty warehouse.address ? warehouse.address : '—'}"/></span>
                        </div>
                    </div>
                    <%-- Right: live KPIs (60%) --%>
                    <div class="whinfo-kpi-strip">
                        <div class="whinfo-kpi-card">
                            <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none"
                                 stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                                <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/>
                                <polyline points="3.27 6.96 12 12.01 20.73 6.96"/>
                                <line x1="12" y1="22.08" x2="12" y2="12"/>
                            </svg>
                            <div class="whinfo-kpi-card__num"><c:out value="${dashboardMetrics.totalSku}"/></div>
                            <div class="whinfo-kpi-card__lbl">Tổng SKU</div>
                        </div>
                        <div class="whinfo-kpi-card">
                            <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none"
                                 stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                                <rect x="2" y="7" width="20" height="14" rx="2" ry="2"/>
                                <path d="M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16"/>
                            </svg>
                            <div class="whinfo-kpi-card__num">
                                <c:choose>
                                    <c:when test="${dashboardMetrics.totalPhysical >= 1000}">
                                        <fmt:formatNumber value="${dashboardMetrics.totalPhysical / 1000}" pattern="#,##0.#" maxFractionDigits="1"/>k
                                    </c:when>
                                    <c:otherwise>
                                        <fmt:formatNumber value="${dashboardMetrics.totalPhysical}" pattern="#,##0"/>
                                    </c:otherwise>
                                </c:choose>
                            </div>
                            <div class="whinfo-kpi-card__lbl">Tồn vật lý</div>
                        </div>
                        <c:choose>
                            <c:when test="${dashboardMetrics.alertCount > 0}">
                                <a href="${pageContext.request.contextPath}/inventory/list" class="whinfo-kpi-card ${dashboardMetrics.alertCount > 0 ? 'whinfo-kpi-card--alert' : ''}">
                                    <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none"
                                         stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                                        <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/>
                                        <line x1="12" y1="9" x2="12" y2="13"/>
                                        <line x1="12" y1="17" x2="12.01" y2="17"/>
                                    </svg>
                                    <div class="whinfo-kpi-card__num"><c:out value="${dashboardMetrics.alertCount}"/></div>
                                    <div class="whinfo-kpi-card__lbl">Cảnh báo</div>
                                </a>
                            </c:when>
                            <c:otherwise>
                                <div class="whinfo-kpi-card">
                                    <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none"
                                         stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                                        <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/>
                                        <line x1="12" y1="9" x2="12" y2="13"/>
                                        <line x1="12" y1="17" x2="12.01" y2="17"/>
                                    </svg>
                                    <div class="whinfo-kpi-card__num"><c:out value="${dashboardMetrics.alertCount}"/></div>
                                    <div class="whinfo-kpi-card__lbl">Cảnh báo</div>
            </div>
                            </c:otherwise>
                        </c:choose>
                </div>
                </div>
            </div>
        </div>

        <%-- ══ Zones Card ════════════════════════════════════════════════ --%>
        <div class="whinfo-card">
            <div class="whinfo-card__hdr">
                <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none"
                     stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/>
                    <rect x="14" y="14" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/>
                </svg>
                <span>Phân khu lưu trữ (Zones)</span>
                <button type="button" onclick="openCreateZone()"
                        class="btn-add-zone">
                    <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M5 12h14"/><path d="M12 5v14"/></svg>
                    Thêm khu
                </button>
            </div>
            <table class="whinfo-table">
                <thead>
                    <tr>
                        <th style="width:56px; text-align:center;">STT</th>
                        <th>Tên khu</th>
                        <th style="width:150px;">Loại</th>
                        <th>Sức chứa</th>
                        <th style="width:110px;">Trạng thái</th>
                        <th style="width:110px; text-align:center;">Thao tác</th>
                    </tr>
                </thead>
                <tbody>
                    <c:choose>
                        <c:when test="${empty warehouse.zones}">
                            <tr><td colspan="6" class="whinfo-empty-cell">Chưa có phân khu nào.</td></tr>
                        </c:when>
                        <c:otherwise>
                            <c:forEach var="z" items="${warehouse.zones}" varStatus="vs">
                                <tr>
                                    <td style="text-align:center; color:rgba(16,55,92,0.35); font-weight:700;"><c:out value="${vs.count}"/></td>
                                    <td style="font-weight:600;"><c:out value="${z.zoneName}"/></td>
                                    <td>
                                        <c:choose>
                                            <c:when test="${z.zoneType == 'NORMAL'}"><span class="zbadge zbadge--normal">Khu thường</span></c:when>
                                            <c:when test="${z.zoneType == 'RETURN'}"><span class="zbadge zbadge--return">Khu trả hàng</span></c:when>
                                            <c:when test="${z.zoneType == 'DAMAGED'}"><span class="zbadge zbadge--damaged">Khu hỏng</span></c:when>
                                            <c:when test="${z.zoneType == 'DESTROY'}"><span class="zbadge zbadge--destroy">Khu tiêu hủy</span></c:when>
                                            <c:when test="${z.zoneType == 'DISPUTE'}"><span class="zbadge zbadge--dispute">Khu khiếu nại</span></c:when>
                                            <c:otherwise><span class="zbadge zbadge--custom"><c:out value="${z.zoneType}"/></span></c:otherwise>
                                        </c:choose>
                                    </td>
                                    <td style="color:rgba(16,55,92,0.55);"><c:out value="${not empty z.capacity ? z.capacity : '—'}"/></td>
                                    <td>
                                        <c:choose>
                                            <c:when test="${z.active}"><span class="zstatus zstatus--active">Hoạt động</span></c:when>
                                            <c:otherwise><span class="zstatus zstatus--inactive">Ngừng</span></c:otherwise>
                                        </c:choose>
                                    </td>
                                    <td style="text-align:center;">
                                        <button type="button" class="btn-z-action btn-z-edit"
                                                data-id="${z.zoneId}"
                                                data-name="${fn:escapeXml(z.zoneName)}"
                                                data-type="${z.zoneType}"
                                                data-desc="${fn:escapeXml(z.description)}"
                                                data-capacity="${z.capacity}"
                                                data-default="${z['default']}"
                                                onclick="openEditZone(this)">
                                            <svg xmlns="http://www.w3.org/2000/svg" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M12 20h9"/><path d="M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4Z"/></svg>
                                            Sửa
                                        </button>
                                        <c:choose>
                                            <c:when test="${not z['default']}">
                                                <button type="button" class="btn-z-action btn-z-delete"
                                                        data-zone-id="${z.zoneId}"
                                                        data-zone-name="${fn:escapeXml(z.zoneName)}"
                                                        onclick="deleteZone(this)">
                                                    <svg xmlns="http://www.w3.org/2000/svg" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M3 6h18"/><path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6"/><path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2"/></svg>
                                                    Xóa
                                                </button>
                                            </c:when>
                                            <c:otherwise>
                                                <span style="display:inline-block; width:30px;"></span>
                                            </c:otherwise>
                                        </c:choose>
                                    </td>
                                </tr>
                            </c:forEach>
                        </c:otherwise>
                    </c:choose>
                </tbody>
            </table>
        </div>

        <div class="whinfo-note">
            Mã, tên, địa chỉ kho do Quản lý thiết lập. Nhân viên kho quản lý các phân khu lưu trữ tại đây.
            Khu mặc định của hệ thống không thể xóa.
        </div>

        <%-- ══ Zone Modal ═══════════════════════════════════════════════ --%>
        <div id="zoneModalOverlay" class="zm-overlay" onclick="if(event.target===this)closeZoneModal()">
            <div class="zm-box">
                <%-- Header --%>
                <div class="zm-hdr">
                    <div class="zm-hdr__icon">
                        <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none"
                             stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                            <rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/>
                            <rect x="14" y="14" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/>
                        </svg>
                    </div>
                    <div>
                        <div class="zm-hdr__title" id="zoneModalTitle">Thêm phân khu mới</div>
                        <div class="zm-hdr__sub">Thiết lập thông tin chi tiết cho phân khu lưu trữ</div>
                    </div>
                    <button type="button" class="zm-close" onclick="closeZoneModal()">
                        <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M18 6 6 18"/><path d="m6 6 12 12"/></svg>
                    </button>
                </div>

                <%-- Body --%>
                <form method="post" action="${pageContext.request.contextPath}/warehouse/information"
                      id="zoneForm" onsubmit="return resolveZoneTypeSubmit()">
                    <input type="hidden" name="action" id="zoneFormAction" value="createZone"/>
                    <input type="hidden" name="zoneId" id="zoneFormId"/>
                    <input type="hidden" name="zoneType" id="zoneFormTypeResolved" value="NORMAL"/>

                    <div class="zm-body">
                        <%-- Section: Zone type selection --%>
                        <div class="zm-section">
                            <div class="zm-section__title">Loại khu</div>
                            <div class="zm-type-grid" id="zoneTypeGrid">
                                <label class="zm-type-item" data-type="NORMAL">
                                    <input type="radio" name="zoneTypeRadio" value="NORMAL" checked/>
                                    <div class="zm-type-item__inner">
                                        <div class="zm-type-item__icon zm-type-item__icon--normal">
                                            <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 8.35V20a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V8.35A2 2 0 0 1 3.26 6.5l8-3.2a2 2 0 0 1 1.48 0l8 3.2A2 2 0 0 1 22 8.35Z"/><rect width="4" height="6" x="10" y="18" rx="0"/></svg>
                                        </div>
                                        <div class="zm-type-item__info">
                                            <div class="zm-type-item__name">Khu Hàng Thường</div>
                                            <div class="zm-type-item__desc">Hàng đạt tiêu chuẩn, sẵn sàng xuất kho</div>
                                        </div>
                                        <span class="zm-type-item__badge zm-badge-normal">Tiêu chuẩn</span>
                                    </div>
                                </label>
                                <label class="zm-type-item" data-type="DAMAGED">
                                    <input type="radio" name="zoneTypeRadio" value="DAMAGED"/>
                                    <div class="zm-type-item__inner">
                                        <div class="zm-type-item__icon zm-type-item__icon--damaged">
                                            <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="12" x2="12" y1="8" y2="12"/><line x1="12" x2="12.01" y1="16" y2="16"/></svg>
                                        </div>
                                        <div class="zm-type-item__info">
                                            <div class="zm-type-item__name">Khu Hàng Hỏng</div>
                                            <div class="zm-type-item__desc">Hàng hư hỏng, lỗi ngoại quan chờ thanh lý</div>
                                        </div>
                                        <span class="zm-type-item__badge zm-badge-damaged">Bắt buộc</span>
                                    </div>
                                </label>
                                <label class="zm-type-item" data-type="DISPUTE">
                                    <input type="radio" name="zoneTypeRadio" value="DISPUTE"/>
                                    <div class="zm-type-item__inner">
                                        <div class="zm-type-item__icon zm-type-item__icon--dispute">
                                            <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 12a9 9 0 1 0 18 0a9 9 0 0 0 -18 0"/><path d="M12 8v4"/><path d="M12 16h.01"/></svg>
                                        </div>
                                        <div class="zm-type-item__info">
                                            <div class="zm-type-item__name">Khu Hàng Khiếu Nại</div>
                                            <div class="zm-type-item__desc">Hàng khách trả về, chờ QC kiểm định</div>
                                        </div>
                                        <span class="zm-type-item__badge zm-badge-dispute">Hoàn trả</span>
                                    </div>
                                </label>
                                <label class="zm-type-item" data-type="RETURN">
                                    <input type="radio" name="zoneTypeRadio" value="RETURN"/>
                                    <div class="zm-type-item__inner">
                                        <div class="zm-type-item__icon zm-type-item__icon--return">
                                            <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"/><path d="M3 3v5h5"/></svg>
                                        </div>
                                        <div class="zm-type-item__info">
                                            <div class="zm-type-item__name">Khu Hàng Trả Nhà Cung Cấp</div>
                                            <div class="zm-type-item__desc">Hàng hoàn trả nhà cung cấp</div>
                                        </div>
                                        <span class="zm-type-item__badge zm-badge-return">Hoàn trả</span>
                                    </div>
                                </label>
                                <label class="zm-type-item" data-type="DESTROY">
                                    <input type="radio" name="zoneTypeRadio" value="DESTROY"/>
                                    <div class="zm-type-item__inner">
                                        <div class="zm-type-item__icon zm-type-item__icon--destroy">
                                            <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>
                                        </div>
                                        <div class="zm-type-item__info">
                                            <div class="zm-type-item__name">Khu Tiêu Hủy</div>
                                            <div class="zm-type-item__desc">Hàng hết hạn hoặc không thể sử dụng</div>
                                        </div>
                                        <span class="zm-type-item__badge zm-badge-destroy">Tiêu hủy</span>
                                    </div>
                                </label>
                                <label class="zm-type-item zm-type-item--custom" data-type="__CUSTOM__" id="customTypeLabel">
                                    <input type="radio" name="zoneTypeRadio" value="__CUSTOM__"/>
                                    <div class="zm-type-item__inner">
                                        <div class="zm-type-item__icon zm-type-item__icon--custom">
                                            <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><path d="M8 12h8"/><path d="M12 8v8"/></svg>
                                        </div>
                                        <div class="zm-type-item__info">
                                            <div class="zm-type-item__name">+ Thêm loại khác...</div>
                                            <div class="zm-type-item__desc">Tự đặt tên theo nhu cầu vận hành</div>
                                        </div>
                                    </div>
                                </label>
                            </div>

                            <%-- Custom type input --%>
                            <div id="customTypeWrap" class="zm-custom-type-wrap" style="display:none;">
                                <div class="zm-custom-type-inner">
                                    <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z"/></svg>
                                    <input type="text" id="zoneFormCustomType"
                                           placeholder="Nhập tên loại khu (VD: Khu Hàng Dự Trữ, Khu Hàng Khuyến Mãi...)"
                                           maxlength="50"/>
                                </div>
                            </div>
                        </div>

                        <%-- Section: Zone name --%>
                        <div class="zm-section">
                            <div class="zm-section__title">Thông tin khu</div>
                            <div class="zm-field">
                                <label class="zm-field__lbl" for="zoneFormName">Tên khu</label>
                                <input class="zm-field__input" type="text" id="zoneFormName" name="zoneName"
                                       placeholder="VD: Khu Hàng Dự Trữ, Khu Hàng Khuyến Mãi..."
                                       required maxlength="100"/>
                            </div>
                            <div class="zm-field">
                                <label class="zm-field__lbl" for="zoneFormCapacity">Sức chứa <span class="zm-field__opt">(Tùy chọn)</span></label>
                                <input class="zm-field__input" type="number" id="zoneFormCapacity" name="capacity"
                                       placeholder="VD: 1000" min="0" max="999999"/>
                            </div>
                            <div class="zm-field">
                                <label class="zm-field__lbl" for="zoneFormDesc">Mô tả <span class="zm-field__opt">(Tùy chọn)</span></label>
                                <textarea class="zm-field__input" id="zoneFormDesc" name="description"
                                          placeholder="Mô tả ngắn gọn chức năng hoặc quy định của khu vực này..."
                                          rows="2" maxlength="255"></textarea>
                            </div>
                        </div>
                    </div>

                    <%-- Footer --%>
                    <div class="zm-ftr">
                        <button type="button" class="zm-btn zm-btn--cancel" onclick="closeZoneModal()">Hủy</button>
                        <button type="submit" class="zm-btn zm-btn--save">
                            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z"/><polyline points="17 21 17 13 7 13 7 21"/><polyline points="7 3 7 8 15 8"/></svg>
                            Lưu phân khu
                        </button>
                    </div>
                </form>
            </div>
        </div>

        <%-- Delete confirm inline overlay --%>
        <div id="deleteConfirmOverlay" class="zm-overlay" style="display:none; z-index:2000;" onclick="if(event.target===this)document.getElementById('deleteConfirmOverlay').style.display='none'">
            <div class="zm-box zm-box--sm">
                <div class="zm-hdr">
                    <div class="zm-hdr__icon zm-hdr__icon--warn">
                        <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" x2="12" y1="9" y2="13"/><line x1="12" x2="12.01" y1="17" y2="17"/></svg>
                    </div>
                    <div>
                        <div class="zm-hdr__title">Xác nhận xóa phân khu</div>
                        <div class="zm-hdr__sub" id="deleteConfirmMsg"></div>
                    </div>
                    <button type="button" class="zm-close" onclick="document.getElementById('deleteConfirmOverlay').style.display='none'">
                        <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M18 6 6 18"/><path d="m6 6 12 12"/></svg>
                    </button>
                </div>
                <div class="zm-ftr">
                    <button type="button" class="zm-btn zm-btn--cancel" onclick="document.getElementById('deleteConfirmOverlay').style.display='none'">Hủy</button>
                    <form method="post" action="${pageContext.request.contextPath}/warehouse/information" style="display:inline;">
                        <input type="hidden" name="action" value="deleteZone"/>
                        <input type="hidden" name="zoneId" id="deleteZoneId"/>
                        <button type="submit" class="zm-btn zm-btn--delete">
                            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M3 6h18"/><path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6"/><path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2"/></svg>
                            Xóa phân khu
                        </button>
                    </form>
                </div>
            </div>
        </div>

        <script>
            var PRESET_TYPES = ['NORMAL', 'RETURN', 'DAMAGED', 'DESTROY', 'DISPUTE'];

            function openCreateZone() {
                document.getElementById('zoneModalTitle').textContent = 'Thêm phân khu mới';
                document.getElementById('zoneFormAction').value = 'createZone';
                document.getElementById('zoneFormId').value = '';
                document.getElementById('zoneFormName').value = '';
                document.getElementById('zoneFormName').removeAttribute('disabled');
                document.getElementById('zoneFormDesc').value = '';
                document.getElementById('zoneFormCapacity').value = '';
                document.getElementById('zoneFormCustomType').value = '';
                document.getElementById('zoneFormCustomType').removeAttribute('disabled');
                document.getElementById('customTypeWrap').style.display = 'none';
                document.getElementById('zoneFormTypeResolved').value = 'NORMAL';
                document.querySelector('input[name="zoneTypeRadio"][value="NORMAL"]').checked = true;
                
                document.querySelectorAll('input[name="zoneTypeRadio"]').forEach(function(radio) {
                    radio.removeAttribute('disabled');
                });
                
                document.querySelectorAll('.zm-type-item').forEach(function(el){ el.classList.remove('active'); });
                document.querySelector('.zm-type-item[data-type="NORMAL"]').classList.add('active');
                
                var notice = document.getElementById('defaultZoneNotice');
                if (notice) notice.style.display = 'none';

                document.getElementById('zoneModalOverlay').style.display = 'flex';
            }

            function openEditZone(btn) {
                var isDefault = btn.getAttribute('data-default') === 'true';

                document.getElementById('zoneModalTitle').textContent = 'Chỉnh sửa phân khu';
                document.getElementById('zoneFormAction').value = 'updateZone';
                document.getElementById('zoneFormId').value = btn.getAttribute('data-id');
                document.getElementById('zoneFormName').value = btn.getAttribute('data-name');
                document.getElementById('zoneFormDesc').value = btn.getAttribute('data-desc') || '';
                document.getElementById('zoneFormCapacity').value = btn.getAttribute('data-capacity') || '';

                var rawType = btn.getAttribute('data-type') || 'NORMAL';
                var radio;
                if (PRESET_TYPES.indexOf(rawType) !== -1) {
                    radio = document.querySelector('input[name="zoneTypeRadio"][value="' + rawType + '"]');
                    document.getElementById('zoneFormTypeResolved').value = rawType;
                    document.getElementById('zoneFormCustomType').value = '';
                    document.getElementById('customTypeWrap').style.display = 'none';
                } else {
                    radio = document.querySelector('input[name="zoneTypeRadio"][value="__CUSTOM__"]');
                    document.getElementById('zoneFormTypeResolved').value = rawType;
                    document.getElementById('zoneFormCustomType').value = rawType;
                    document.getElementById('customTypeWrap').style.display = 'block';
                }
                if (radio) radio.checked = true;
                document.querySelectorAll('.zm-type-item').forEach(function(el){ el.classList.remove('active'); });
                if (radio) radio.closest('.zm-type-item').classList.add('active');

                // System default zone restrictions
                if (isDefault) {
                    document.getElementById('zoneFormName').setAttribute('disabled', 'disabled');
                    document.querySelectorAll('input[name="zoneTypeRadio"]').forEach(function(r) {
                        r.setAttribute('disabled', 'disabled');
                    });
                    document.getElementById('zoneFormCustomType').setAttribute('disabled', 'disabled');
                    
                    var notice = document.getElementById('defaultZoneNotice');
                    if (!notice) {
                        notice = document.createElement('div');
                        notice.id = 'defaultZoneNotice';
                        notice.style.color = '#ef4444';
                        notice.style.fontSize = '12px';
                        notice.style.marginTop = '6px';
                        notice.style.fontWeight = '600';
                        notice.textContent = 'Khu mặc định của hệ thống: Không thể thay đổi Tên và Loại khu.';
                        document.getElementById('zoneFormName').parentNode.appendChild(notice);
                    }
                    notice.style.display = 'block';
                } else {
                    document.getElementById('zoneFormName').removeAttribute('disabled');
                    document.querySelectorAll('input[name="zoneTypeRadio"]').forEach(function(r) {
                        r.removeAttribute('disabled');
                    });
                    document.getElementById('zoneFormCustomType').removeAttribute('disabled');
                    
                    var notice = document.getElementById('defaultZoneNotice');
                    if (notice) notice.style.display = 'none';
                }

                document.getElementById('zoneModalOverlay').style.display = 'flex';
            }

            function closeZoneModal() {
                document.getElementById('zoneModalOverlay').style.display = 'none';
            }

            function deleteZone(btn) {
                document.getElementById('deleteZoneId').value = btn.getAttribute('data-zone-id');
                document.getElementById('deleteConfirmMsg').textContent =
                    'Bạn có chắc muốn xóa phân khu "' + btn.getAttribute('data-zone-name') + '"? Hành động này không thể hoàn tác.';
                document.getElementById('deleteConfirmOverlay').style.display = 'flex';
            }

            function resolveZoneTypeSubmit() {
                var checked = document.querySelector('input[name="zoneTypeRadio"]:checked');
                var resolved = document.getElementById('zoneFormTypeResolved');
                if (checked.value === '__CUSTOM__') {
                    var customVal = document.getElementById('zoneFormCustomType').value.trim();
                    if (!customVal) {
                        document.getElementById('zoneFormCustomType').style.borderColor = '#ef4444';
                        document.getElementById('zoneFormCustomType').focus();
                        return false;
                    }
                    resolved.value = customVal.toUpperCase().replace(/\s+/g, '_');
                } else {
                    resolved.value = checked.value;
                }
                return true;
            }

            // Wire zone type radio to hidden select + active state
            document.querySelectorAll('input[name="zoneTypeRadio"]').forEach(function(radio) {
                radio.addEventListener('change', function() {
                    document.querySelectorAll('.zm-type-item').forEach(function(el){ el.classList.remove('active'); });
                    this.closest('.zm-type-item').classList.add('active');
                    var customWrap = document.getElementById('customTypeWrap');
                    if (this.value === '__CUSTOM__') {
                        customWrap.style.display = 'block';
                        document.getElementById('zoneFormCustomType').style.borderColor = '';
                    } else {
                        customWrap.style.display = 'none';
                    }
                });
            });
        </script>
    </c:otherwise>
</c:choose>
