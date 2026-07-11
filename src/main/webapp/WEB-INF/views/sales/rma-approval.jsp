<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>

<div style="max-width: 60rem; margin: 0 auto; padding-bottom: 2rem;">

    <c:choose>
        <c:when test="${empty pendingRmaList}">
            <div style="background: white; border: 1px solid #E5EAF3; border-radius: var(--radius-card); padding: 2rem; text-align: center; color: rgba(16,55,92,0.5);">
                Không có yêu cầu hoàn trả nào đang chờ duyệt.
            </div>
        </c:when>
        <c:otherwise>
            <c:forEach var="rma" items="${pendingRmaList}">
                <div style="background: white; border: 1px solid #E5EAF3; border-radius: var(--radius-card); padding: 1.5rem; margin-bottom: 1rem;">
                    <div style="display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 1rem;">
                        <div>
                            <h3 style="color: var(--navy); font-size: 15px; font-weight: 700; margin: 0;">Đơn ${fn:escapeXml(rma.orderCode)}</h3>
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
        </c:otherwise>
    </c:choose>
</div>
