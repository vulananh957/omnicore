package com.wms.controller.api;

import com.wms.dao.InventoryDAO;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GET /api/inventory/{productId} — total available stock across active warehouses.
 * Consumed by OmnicoreApiService.getAvailableStock() (checkout stock check).
 */
public class InventoryApiServlet extends BaseApiServlet {

    private final InventoryDAO inventoryDAO = new InventoryDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        if (authenticateAndReadBody(req, resp) == null) return;

        String pathInfo = req.getPathInfo();
        Integer productId = parseProductId(pathInfo);
        if (productId == null) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Missing or invalid productId in path");
            return;
        }

        int qtyAvailable = inventoryDAO.getTotalAvailableStock(productId);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("product_id", productId);
        data.put("qty_available", qtyAvailable);
        sendJson(resp, HttpServletResponse.SC_OK, data);
    }

    private Integer parseProductId(String pathInfo) {
        if (pathInfo == null || pathInfo.isBlank() || pathInfo.equals("/")) return null;
        String raw = pathInfo.startsWith("/") ? pathInfo.substring(1) : pathInfo;
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
