package com.wms.controller.api;

import com.wms.dao.CategoryDAO;
import com.wms.model.Category;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GET /api/categories — flat list of active categories for the storefront (omnicore-web
 * builds the tree client-side from parent_id). Consumed by OmnicoreApiService.getCategories().
 */
public class CategoryApiServlet extends BaseApiServlet {

    private final CategoryDAO categoryDAO = new CategoryDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        if (authenticateAndReadBody(req, resp) == null) return;

        List<Category> categories = categoryDAO.findActiveOnly();
        Map<Integer, Integer> productCounts = categoryDAO.countActiveProductsByCategory();

        List<Map<String, Object>> data = new ArrayList<>();
        for (Category c : categories) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("category_id", c.getCategoryId());
            node.put("category_code", c.getCategoryCode());
            node.put("category_name", c.getCategoryName());
            node.put("level_depth", c.getLevelDepth());
            node.put("parent_id", c.getParentId());
            node.put("product_count", productCounts.getOrDefault(c.getCategoryId(), 0));
            data.add(node);
        }
        sendJson(resp, HttpServletResponse.SC_OK, data);
    }
}
