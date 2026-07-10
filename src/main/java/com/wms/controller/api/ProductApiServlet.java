package com.wms.controller.api;

import com.wms.dao.ProductDAO;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GET /api/products (paginated list) and GET /api/products/{id} (detail).
 * Consumed by OmnicoreApiService.getProducts()/getProductsByFilter()/getProductById().
 */
public class ProductApiServlet extends BaseApiServlet {

    private final ProductDAO productDAO = new ProductDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        if (authenticateAndReadBody(req, resp) == null) return;

        String pathInfo = req.getPathInfo();
        if (pathInfo != null && !pathInfo.isBlank() && !pathInfo.equals("/")) {
            handleDetail(req, resp, pathInfo);
        } else {
            handleList(req, resp);
        }
    }

    private void handleList(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        int page = parseIntOrDefault(req.getParameter("page"), 1);
        int size = parseIntOrDefault(req.getParameter("size"), 20);
        if (page < 1) page = 1;
        if (size < 1 || size > 100) size = 20;

        Integer categoryId = null;
        String categoryIdParam = req.getParameter("categoryId");
        if (categoryIdParam != null && !categoryIdParam.isBlank()) {
            try {
                categoryId = Integer.parseInt(categoryIdParam);
            } catch (NumberFormatException e) {
                sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid categoryId");
                return;
            }
        }
        String keyword = req.getParameter("keyword");
        String filter = req.getParameter("filter");

        ProductDAO.SearchResult result = productDAO.search(page, size, categoryId, keyword, filter);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", result.total);
        data.put("page", page);
        data.put("size", size);
        data.put("items", result.items);
        sendJson(resp, HttpServletResponse.SC_OK, data);
    }

    private void handleDetail(HttpServletRequest req, HttpServletResponse resp, String pathInfo) throws IOException {
        String raw = pathInfo.startsWith("/") ? pathInfo.substring(1) : pathInfo;
        int productId;
        try {
            productId = Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid productId");
            return;
        }

        Map<String, Object> product = productDAO.findDetailById(productId);
        if (product == null) {
            sendError(resp, HttpServletResponse.SC_NOT_FOUND, "Product not found");
            return;
        }
        sendJson(resp, HttpServletResponse.SC_OK, product);
    }

    private int parseIntOrDefault(String value, int defaultValue) {
        if (value == null || value.isBlank()) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
