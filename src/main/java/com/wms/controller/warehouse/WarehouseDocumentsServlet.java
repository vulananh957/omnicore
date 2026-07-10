package com.wms.controller.warehouse;

import com.wms.controller.BaseController;
import com.wms.dao.LedgerDAO;
import com.wms.model.Warehouse;
import com.wms.service.ledger.LedgerService;
import com.wms.service.warehouse.WarehouseService;
import com.wms.util.JsonUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

/**
 * WarehouseDocumentsServlet — Handles stock ledger / documents list (Sổ kho) for the Warehouse Staff.
 *
 * Maps to /warehouse/documents. Documents are scoped to the staff's own warehouse, and
 * an AJAX endpoint returns the line items of a single document for the detail modal.
 */
public class WarehouseDocumentsServlet extends BaseController {

    private final WarehouseService warehouseService = new WarehouseService();
    private final LedgerService ledgerService = new LedgerService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        // AJAX: return the line items of one document as JSON (for the detail modal)
        if ("items".equals(req.getParameter("ajax"))) {
            resp.setContentType("application/json;charset=UTF-8");
            String docId = req.getParameter("docId");
            String docType = req.getParameter("docType");
            int myWarehouseId = currentWarehouseId(req);
            try {
                if (!ledgerService.verifyDocumentBelongsToWarehouse(docId, docType, myWarehouseId)) {
                    resp.getWriter().write("[]");
                    return;
                }
                List<java.util.Map<String, Object>> items = ledgerService.findDocumentItems(docId, docType);
                resp.getWriter().write(JsonUtil.toJson(items));
            } catch (Exception e) {
                resp.getWriter().write("[]");
            }
            return;
        }

        try {
            List<Warehouse> warehouses = warehouseService.findAllActive();
            req.setAttribute("warehouses", warehouses);
            setJsonAttr(req, "warehousesJson", warehouses);
        } catch (Exception e) {
            req.setAttribute("warehouses", List.<Warehouse>of());
            req.setAttribute("warehousesJson", "[]");
        }

        // Load documents from all sources, scoped to the staff's own warehouse
        try {
            int myWarehouseId = currentWarehouseId(req);
            List<LedgerDAO.LedgerDocument> documents = ledgerService.findAllDocuments(myWarehouseId);
            setJsonAttr(req, "documentsJson", documents);
        } catch (Exception e) {
            req.setAttribute("documentsJson", "[]");
        }

        // Load system settings (company info) so PDF headers reflect real data
        try {
            java.util.Map<String, String> settings = new java.util.HashMap<>();
            try (java.sql.Connection conn = com.wms.util.DBConnection.getConnection();
                 java.sql.PreparedStatement ps = conn.prepareStatement(
                     "SELECT setting_key, setting_value FROM system_settings");
                 java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    settings.put(rs.getString("setting_key"), rs.getString("setting_value"));
                }
            }
            req.setAttribute("companyName",    settings.getOrDefault("company_name", "Công ty TNHH OmniCore"));
            req.setAttribute("companyAddress", settings.getOrDefault("company_address", ""));
            req.setAttribute("companyPhone",   settings.getOrDefault("company_phone", ""));
            req.setAttribute("companyTaxCode", settings.getOrDefault("company_tax_code", ""));
        } catch (Exception e) {
            req.setAttribute("companyName", "Công ty TNHH OmniCore");
        }

        req.setAttribute("pageTitle",    "Sổ Kho");
        req.setAttribute("pageSubtitle", "Quản lý phiếu kho — lưu nhập, trình duyệt và xác nhận hoàn hàng");
        req.setAttribute("currentPage",  "wh-documents");
        req.setAttribute("contentPage", "/WEB-INF/views/warehouse/warehouse-documents.jsp");

        req.getRequestDispatcher("/WEB-INF/views/layout/warehouse-layout.jsp")
           .forward(req, resp);
    }
}
