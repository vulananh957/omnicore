package com.wms.controller.sales;

import com.wms.controller.BaseController;
import com.wms.dao.SkuMappingExceptionDAO;
import com.wms.service.sales.SkuMappingService;
import com.wms.service.sales.SkuMappingSuggestService;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * SalesSKUMappingServlet — Handles the "Ánh xạ SKU" (SKU Mapping Center) page for Sales Staff.
 * Maps to /sales/sku-mapping.
 */
public class SalesSKUMappingServlet extends BaseController {

    private final SkuMappingService skuMappingService = new SkuMappingService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        try {
            req.setAttribute("skuMappings", skuMappingService.findAllMappings());
            req.setAttribute("channels", skuMappingService.findAllChannels());
            req.setAttribute("products", skuMappingService.findAllSkus());
            req.setAttribute("unresolvedExceptions", new SkuMappingExceptionDAO().findUnresolved());

            var allMappings = skuMappingService.findAllMappings();
            req.setAttribute("totalMappings", allMappings.size());
            req.setAttribute("pendingMappings",
                (int) allMappings.stream()
                    .filter(m -> "PENDING".equals(m.getSyncStatus()) || "ERROR".equals(m.getSyncStatus()))
                    .count());
            req.setAttribute("syncedMappings",
                (int) allMappings.stream()
                    .filter(m -> "SYNCED".equals(m.getSyncStatus()))
                    .count());
        } catch (Exception e) {
            req.setAttribute("skuMappings", List.of());
            req.setAttribute("channels", List.of());
            req.setAttribute("products", List.of());
            req.setAttribute("unresolvedExceptions", List.of());
            req.setAttribute("totalMappings", 0);
            req.setAttribute("pendingMappings", 0);
            req.setAttribute("syncedMappings", 0);
        }

        req.setAttribute("pageTitle",    "Trung Tâm Ánh Xạ SKU Đa Sàn");
        req.setAttribute("pageSubtitle", "Kết nối Master SKU nội bộ kho hàng với Channel SKU trên các sàn TMĐT");
        req.setAttribute("currentPage",  "sales-sku-mapping");

        req.setAttribute("contentPage", "/WEB-INF/views/sales/sku-mapping.jsp");

        req.getRequestDispatcher("/WEB-INF/views/layout/sales-layout.jsp")
           .forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        req.setCharacterEncoding("UTF-8");
        String action = req.getParameter("action");
        boolean success = false;
        String message = "";

        try {
            if ("create".equals(action)) {
                int skuId = Integer.parseInt(req.getParameter("productId"));
                int channelId = Integer.parseInt(req.getParameter("channelId"));
                success = skuMappingService.createMapping(skuId, channelId,
                    req.getParameter("channelSku"), req.getParameter("sellerSku"), LocalDateTime.now());
                
                String exceptionIdStr = req.getParameter("exceptionId");
                if (success && exceptionIdStr != null && !exceptionIdStr.trim().isEmpty()) {
                    try {
                        int exceptionId = Integer.parseInt(exceptionIdStr.trim());
                        new SkuMappingExceptionDAO().markResolved(exceptionId);
                    } catch (Exception ignored) {}
                }
                
                message = success ? "Tạo ánh xạ SKU thành công!" : "Tạo ánh xạ SKU thất bại.";

            } else if ("update".equals(action)) {
                int mappingId = Integer.parseInt(req.getParameter("mappingId"));
                int skuId = Integer.parseInt(req.getParameter("productId"));
                int channelId = Integer.parseInt(req.getParameter("channelId"));
                success = skuMappingService.updateMapping(mappingId, skuId, channelId,
                    req.getParameter("channelSku"), req.getParameter("sellerSku"),
                    req.getParameter("syncStatus"), LocalDateTime.now());
                message = success ? "Cập nhật ánh xạ SKU thành công!" : "Cập nhật ánh xạ SKU thất bại.";

            } else if ("delete".equals(action)) {
                int mappingId = Integer.parseInt(req.getParameter("mappingId"));
                success = skuMappingService.deleteMapping(mappingId);
                message = success ? "Xóa ánh xạ SKU thành công!" : "Xóa ánh xạ SKU thất bại.";

            } else if ("sync".equals(action)) {
                int channelProductId = Integer.parseInt(req.getParameter("channelProductId"));
                BigDecimal price = null, stock = null;
                String priceStr = req.getParameter("price");
                String stockStr = req.getParameter("stock");
                if (priceStr != null && !priceStr.trim().isEmpty()) {
                    try { price = new BigDecimal(priceStr.trim()); } catch (Exception ignored) {}
                }
                if (stockStr != null && !stockStr.trim().isEmpty()) {
                    try { stock = new BigDecimal(stockStr.trim()); } catch (Exception ignored) {}
                }
                SkuMappingService.SyncResult syncResult = skuMappingService.syncChannelProduct(
                    channelProductId, price, stock, LocalDateTime.now());
                success = syncResult.isSuccess();
                message = syncResult.getMessage();

            } else if ("syncAll".equals(action)) {
                var mappings = skuMappingService.findAllMappings();
                int synced = skuMappingService.syncAllMappings(mappings);
                success = true;
                message = "Đã đồng bộ " + synced + " ánh xạ SKU.";

            } else if ("suggest".equals(action)) {
                int channelId = Integer.parseInt(req.getParameter("channelId"));
                var suggestions = new SkuMappingSuggestService().suggestForChannel(channelId);
                writeJson(resp, suggestions);
                return;

            } else if ("resolveException".equals(action)) {
                int exceptionId = Integer.parseInt(req.getParameter("exceptionId"));
                boolean resolved = new SkuMappingExceptionDAO().markResolved(exceptionId);
                resp.setContentType("application/json;charset=UTF-8");
                try (PrintWriter out = resp.getWriter()) {
                    out.print("{\"success\":" + resolved + "}");
                }
                return;
            }
        } catch (Exception e) {
            message = "Lỗi xử lý: " + e.getMessage();
        }

        req.getSession().setAttribute("toastMessage", message);
        req.getSession().setAttribute("toastSuccess", success);

        resp.sendRedirect(req.getContextPath() + "/sales/sku-mapping");
    }

    private void writeJson(HttpServletResponse resp, Object payload) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        try (PrintWriter out = resp.getWriter()) {
            out.print(new com.fasterxml.jackson.databind.ObjectMapper()
                    .writeValueAsString(payload));
        }
    }
}
