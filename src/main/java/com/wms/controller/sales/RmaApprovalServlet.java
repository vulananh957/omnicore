package com.wms.controller.sales;

import com.wms.controller.BaseController;
import com.wms.dao.OrderDAO;
import com.wms.dao.RmaDAO;
import com.wms.model.RmaRequest;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

/**
 * RmaApprovalServlet — "Yêu cầu hoàn trả (Website)" page for Sales Staff.
 * Reviews customer-submitted return requests (rma_requests, Website channel only)
 * with photo/video evidence, approves or rejects them.
 * Maps to /sales/rma-approval.
 */
public class RmaApprovalServlet extends BaseController {

    private final RmaDAO rmaDAO = new RmaDAO();
    private final OrderDAO orderDAO = new OrderDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        List<RmaRequest> pending = rmaDAO.findPendingForWebsite();
        req.setAttribute("pendingRmaList", pending);
        req.setAttribute("pageTitle",    "Yêu Cầu Hoàn Trả (Website)");
        req.setAttribute("pageSubtitle", "Duyệt yêu cầu trả hàng do khách gửi trực tiếp trên website, kèm ảnh/video bằng chứng");
        req.setAttribute("currentPage",  "sales-rma-approval");
        req.setAttribute("contentPage",  "/WEB-INF/views/sales/rma-approval.jsp");
        req.getRequestDispatcher("/WEB-INF/views/layout/sales-layout.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String action = req.getParameter("action");
        int rmaId = parseId(req.getParameter("rmaId"));
        int orderId = parseId(req.getParameter("orderId"));
        String note = req.getParameter("note");

        if (rmaId > 0 && orderId > 0) {
            if ("approve".equals(action)) {
                orderDAO.markReturnedByOrderId(orderId);
                rmaDAO.updateStatus(rmaId, "APPROVED", note);
            } else if ("reject".equals(action)) {
                orderDAO.revertDisputeToCompleted(orderId);
                rmaDAO.updateStatus(rmaId, "RESOLVED", note);
            }
        }
        resp.sendRedirect(req.getContextPath() + "/sales/rma-approval");
    }

    private int parseId(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return -1;
        }
    }
}
