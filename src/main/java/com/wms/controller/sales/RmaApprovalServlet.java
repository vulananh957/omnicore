package com.wms.controller.sales;

import com.wms.controller.BaseController;
import com.wms.dao.OrderDAO;
import com.wms.dao.RmaDAO;
import com.wms.dao.ReturnDAO;
import com.wms.model.Order;
import com.wms.model.OrderItem;
import com.wms.model.ReturnOrder;
import com.wms.model.ReturnItem;
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
                
                // Automatically create a return_orders ticket for Warehouse Staff QC
                try {
                    RmaRequest rma = rmaDAO.findById(rmaId);
                    Order order = orderDAO.findByOrderCode(rma.getOrderCode());
                    if (order != null && rma != null) {
                        ReturnOrder ro = new ReturnOrder();
                        ro.setOrderId(orderId);
                        ro.setOrderCode(order.getOrderCode());
                        ro.setCustomerName(order.getCustomerName() != null ? order.getCustomerName() : "Khách Website");
                        ro.setCustomerPhone(order.getCustomerPhone() != null ? order.getCustomerPhone() : "");
                        ro.setReason(rma.getReturnReason());
                        ro.setStatus("RECEIVED");
                        ro.setWarehouseId(order.getWarehouseId() > 0 ? order.getWarehouseId() : 1);
                        ro.setReturnCode(rma.getRmaCode());
                        
                        List<OrderItem> orderItems = orderDAO.findItemsByOrderId(orderId);
                        if (orderItems != null) {
                            List<ReturnItem> returnItems = new java.util.ArrayList<>();
                            for (OrderItem item : orderItems) {
                                ReturnItem ri = new ReturnItem();
                                ri.setSkuCode(item.getSkuCode());
                                ri.setSkuName(item.getProductName());
                                ri.setQty(new java.math.BigDecimal(item.getQuantity()));
                                ri.setReturnReason(rma.getReturnReason());
                                ri.setQcDecision("pending");
                                returnItems.add(ri);
                            }
                            ro.setItems(returnItems);
                        }
                        
                        ReturnDAO returnDAO = new ReturnDAO();
                        boolean inserted = returnDAO.insert(ro);
                        System.out.println("[RmaApproval] Auto-created return order. Success: " + inserted + ", Code: " + ro.getReturnCode());
                    }
                } catch (Exception e) {
                    System.out.println("[RmaApproval] ERROR auto-creating return order: " + e.getMessage());
                    e.printStackTrace();
                }
            } else if ("reject".equals(action)) {
                orderDAO.revertDisputeToCompleted(orderId);
                rmaDAO.updateStatus(rmaId, "RESOLVED", note);
            }
        }
        String redirect = req.getParameter("redirect");
        if (redirect == null || redirect.trim().isEmpty()) {
            redirect = req.getContextPath() + "/sales/orders?tab=returned";
        }
        resp.sendRedirect(redirect);
    }

    private int parseId(String s) {
        if (s == null) return -1;
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return -1;
        }
    }
}
