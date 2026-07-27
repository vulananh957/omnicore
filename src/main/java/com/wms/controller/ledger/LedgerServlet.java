package com.wms.controller.ledger;

import com.wms.controller.BaseController;
import com.wms.dao.LedgerDAO;
import com.wms.service.ledger.LedgerService;
import com.wms.util.JsonUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

/**
 * LedgerServlet — Handles requests for the Stock Ledger page.
 *
 * Maps to /business/ledger.
 * Role: Manager view-only. Inventory updates are handled directly by
 * warehouse operation servlets (Inbound/Outbound/Transfer/InventoryCheck).
 * This page is for reviewing all warehouse documents across all warehouses.
 */
public class LedgerServlet extends BaseController {

    // khởi tạo 2 service cần cho sổ kho
    private final LedgerService ledgerService = new LedgerService();
    private final com.wms.service.warehouse.WarehouseService warehouseService = new com.wms.service.warehouse.WarehouseService();

    // lấy danh sách tất cả các chứng từ nhập, xuất, chuyển, kiểm kê
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        consumeFlash(req); 
        // xử lý để xem chi tiết sản phẩm trong phiếu (api phụ)
        if ("items".equals(req.getParameter("ajax"))) {
            resp.setContentType("application/json;charset=UTF-8");
            String docId = req.getParameter("docId"); // lấy id chứng từ
            String docType = req.getParameter("docType"); // lấy loại chứng từ
            try {
                List<java.util.Map<String, Object>> items = ledgerService.findDocumentItems(docId, docType);
                resp.getWriter().write(JsonUtil.toJson(items));
                // trả về chuỗi json chứa danh sách sản phẩm
            } catch (Exception e) {
                resp.getWriter().write("[]");
            }
            return; // ngắt luồng ngay lập tức
        }

        try {
            List<com.wms.model.Warehouse> warehouses = warehouseService.findAllActive();
            req.setAttribute("warehouses", warehouses);
            setJsonAttr(req, "warehousesJson", warehouses);
        } catch (Exception e) {
            req.setAttribute("warehouses", List.<com.wms.model.Warehouse>of());
            req.setAttribute("warehousesJson", "[]");
        }

        try {
            List<LedgerDAO.LedgerDocument> docs = ledgerService.findAllDocuments();
            req.setAttribute("documents", docs);
            setJsonAttr(req, "documentsJson", docs);
        } catch (Exception e) {
            req.setAttribute("documents", List.of());
            req.setAttribute("documentsJson", "[]");
        }

        try {
            List<LedgerDAO.GlobalLedgerEntry> entries = ledgerService.findGlobalLedgerEntries();
            req.setAttribute("ledgerEntries", entries);
        } catch (Exception e) {
            req.setAttribute("ledgerEntries", List.of());
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
        req.setAttribute("pageSubtitle", "Xem toàn bộ chứng từ nhập — xuất — chuyển — kiểm kê");
        req.setAttribute("currentPage",  "ledger");

        req.setAttribute("contentPage", "/WEB-INF/views/ledger/ledger.jsp");

        req.getRequestDispatcher("/WEB-INF/views/layout/dashboard-layout.jsp")
           .forward(req, resp);
    }
}
