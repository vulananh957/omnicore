package com.wms.controller.business;

import com.wms.controller.BaseController;
import com.wms.model.Supplier;
import com.wms.service.business.SupplierService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * SupplierServlet — Controller for Supplier Management page.
 *
 * <p>URL Pattern: /business/suppliers</p>
 */
@WebServlet("/business/suppliers")
public class SupplierServlet extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(SupplierServlet.class.getName());
    private final SupplierService supplierService = new SupplierService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        // Parse filter parameters
        int page = getPageNumber(req);
        int pageSize = 20;
        String sortBy = req.getParameter("sortBy");
        boolean ascending = !"desc".equalsIgnoreCase(req.getParameter("sortDir"));
        String keyword = req.getParameter("q");
        if (keyword != null && keyword.trim().isEmpty()) {
            keyword = null;
        }
        String debtFilter = req.getParameter("debtFilter");
        if (debtFilter == null || debtFilter.trim().isEmpty() || "ALL".equalsIgnoreCase(debtFilter)) {
            debtFilter = "ALL";
        }

        // Load data
        SupplierService.PagedResult<Supplier> pagedResponse = supplierService.getSuppliersPaged(
                page, pageSize, sortBy, ascending, keyword, debtFilter);

        // Set attributes
        req.setAttribute("suppliers", pagedResponse.getItems());
        req.setAttribute("totalItems", pagedResponse.getTotalItems());
        req.setAttribute("totalPages", pagedResponse.getTotalPages());
        req.setAttribute("currentPage", pagedResponse.getCurrentPage());
        req.setAttribute("pageSize", pageSize);

        // Filter state
        req.setAttribute("currentSortBy", sortBy != null ? sortBy : "");
        req.setAttribute("currentSortDir", ascending ? "asc" : "desc");
        req.setAttribute("currentKeyword", keyword);
        req.setAttribute("currentDebtFilter", debtFilter);

        // Page metadata
        req.setAttribute("pageTitle", "Quản lý Nhà cung cấp");
        req.setAttribute("pageSubtitle", "Danh sách và thông tin liên hệ nhà cung cấp");
        req.setAttribute("currentPage", "suppliers");

        // Content fragment
        req.setAttribute("contentPage", "/WEB-INF/views/business/suppliers.jsp");

        // Forward to shell layout
        req.getRequestDispatcher("/WEB-INF/views/layout/dashboard-layout.jsp")
           .forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String action = req.getParameter("action");

        if ("save".equalsIgnoreCase(action)) {
            handleSave(req, resp);
        } else if ("delete".equalsIgnoreCase(action)) {
            handleDelete(req, resp);
        } else if ("get".equalsIgnoreCase(action)) {
            handleGet(req, resp);
        } else {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unknown action");
        }
    }

    private void handleSave(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            Supplier supplier = new Supplier();

            String idStr = req.getParameter("supplierId");
            if (idStr != null && !idStr.trim().isEmpty()) {
                supplier.setSupplierId(Integer.parseInt(idStr));
            }

            String providedCode = req.getParameter("supplierCode");
            if (providedCode != null && !providedCode.trim().isEmpty()) {
                supplier.setSupplierCode(providedCode.trim().toUpperCase());
            } else {
                supplier.setSupplierCode(supplierService.generateNextSupplierCode());
            }
            supplier.setName(req.getParameter("name"));
            supplier.setContactPerson(req.getParameter("contactPerson"));
            supplier.setPhone(req.getParameter("phone"));
            supplier.setEmail(req.getParameter("email"));
            supplier.setAddress(req.getParameter("address"));
            supplier.setPaymentTerms(req.getParameter("paymentTerms"));
            supplier.setStatus(req.getParameter("status"));

            String creditLimitStr = req.getParameter("creditLimit");
            if (creditLimitStr != null && !creditLimitStr.trim().isEmpty()) {
                try {
                    supplier.setCreditLimit(new java.math.BigDecimal(creditLimitStr));
                } catch (NumberFormatException e) {
                    supplier.setCreditLimit(java.math.BigDecimal.ZERO);
                }
            }

            SupplierService.SaveResult result = supplierService.saveSupplier(supplier);

            if (result.isSuccess()) {
                resp.setContentType("application/json");
                resp.setCharacterEncoding("UTF-8");
                resp.getWriter().write("{\"success\":true,\"message\":\"Lưu thành công\",\"supplierId\":" + supplier.getSupplierId() + "}");
            } else {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.setContentType("application/json");
                resp.setCharacterEncoding("UTF-8");
                resp.getWriter().write("{\"success\":false,\"message\":\"" + escapeJson(result.getMessage()) + "\"}");
            }
        } catch (Exception e) {
            log.error("handleSave failed", e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.setContentType("application/json");
            resp.setCharacterEncoding("UTF-8");
            resp.getWriter().write("{\"success\":false,\"message\":\"Lỗi hệ thống: " + escapeJson(e.getMessage()) + "\"}");
        }
    }

    private void handleDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            Integer supplierId = getIntParamOrNull(req, "supplierId");
            if (supplierId == null || supplierId <= 0) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.setContentType("application/json");
                resp.getWriter().write("{\"success\":false,\"message\":\"ID không hợp lệ\"}");
                return;
            }

            SupplierService.SaveResult result = supplierService.deleteSupplier(supplierId);

            resp.setContentType("application/json");
            resp.setCharacterEncoding("UTF-8");
            if (result.isSuccess()) {
                resp.getWriter().write("{\"success\":true,\"message\":\"Xóa thành công\"}");
            } else {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write("{\"success\":false,\"message\":\"" + escapeJson(result.getMessage()) + "\"}");
            }
        } catch (Exception e) {
            log.error("handleDelete failed", e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.setContentType("application/json");
            resp.getWriter().write("{\"success\":false,\"message\":\"Lỗi hệ thống\"}");
        }
    }

    private void handleGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            Integer supplierId = getIntParamOrNull(req, "supplierId");
            if (supplierId == null || supplierId <= 0) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.setContentType("application/json");
                resp.getWriter().write("{\"success\":false,\"message\":\"ID không hợp lệ\"}");
                return;
            }

            Supplier supplier = supplierService.getSupplierById(supplierId);
            if (supplier == null) {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                resp.setContentType("application/json");
                resp.getWriter().write("{\"success\":false,\"message\":\"Không tìm thấy nhà cung cấp\"}");
                return;
            }

            resp.setContentType("application/json");
            resp.setCharacterEncoding("UTF-8");
            String json = String.format(
                "{\"success\":true,\"supplier\":{\"supplierId\":%d,\"supplierCode\":\"%s\",\"name\":\"%s\",\"contactPerson\":\"%s\",\"phone\":\"%s\",\"email\":\"%s\",\"address\":\"%s\",\"paymentTerms\":\"%s\",\"creditLimit\":\"%s\",\"status\":\"%s\"}}",
                supplier.getSupplierId(),
                escapeJson(supplier.getSupplierCode()),
                escapeJson(supplier.getName()),
                escapeJson(supplier.getContactPerson() != null ? supplier.getContactPerson() : ""),
                escapeJson(supplier.getPhone() != null ? supplier.getPhone() : ""),
                escapeJson(supplier.getEmail() != null ? supplier.getEmail() : ""),
                escapeJson(supplier.getAddress() != null ? supplier.getAddress() : ""),
                escapeJson(supplier.getPaymentTerms() != null ? supplier.getPaymentTerms() : ""),
                supplier.getCreditLimit() != null ? supplier.getCreditLimit().toPlainString() : "0",
                escapeJson(supplier.getStatus())
            );
            resp.getWriter().write(json);
        } catch (Exception e) {
            log.error("handleGet failed", e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.setContentType("application/json");
            resp.getWriter().write("{\"success\":false,\"message\":\"Lỗi hệ thống\"}");
        }
    }
}
