package com.wms.controller.business;

import com.wms.controller.BaseController;
import com.wms.model.ProductPerformance;
import com.wms.service.business.ProductPerformanceService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * ProductPerformanceServlet — Controller for the Product Performance Dashboard.
 *
 * <p>Read-only analytics dashboard for Managers to analyze SKU-level financial metrics
 * including gross margin, tied-up capital, and sales performance.</p>
 *
 * <p>URL Pattern: /business/performance</p>
 */
public class ProductPerformanceServlet extends BaseController {

    private static final Logger logger = Logger.getLogger(ProductPerformanceServlet.class.getName());
    private final ProductPerformanceService service = new ProductPerformanceService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        // ── Parse Filter Parameters ───────────────────────────────
        // Filter params
        Integer categoryId = getIntParamOrNull(req, "categoryId");
        Integer channelId = getIntParamOrNull(req, "channelId");
        String healthFilter = req.getParameter("healthFilter");
        if (healthFilter == null || healthFilter.trim().isEmpty()) {
            healthFilter = ProductPerformanceService.HEALTH_ALL;
        }

        // Search
        String searchQuery = req.getParameter("q");
        if (searchQuery != null) {
            searchQuery = searchQuery.trim();
            if (searchQuery.isEmpty()) searchQuery = null;
        }

        // Sort params
        String sortBy = req.getParameter("sortBy");
        boolean ascending = "asc".equalsIgnoreCase(req.getParameter("sortDir"));

        // ── Load Data ───────────────────────────────────────────
        try {
            // Performance data with sorting (no period filter)
            List<ProductPerformance> performanceData = service.getAllPerformanceDataSorted(
                    categoryId, channelId, healthFilter, searchQuery, sortBy, ascending);

            // Summary statistics
            Map<String, Object> summary = service.computeSummary(performanceData);

            // Filter options
            List<Map<String, Object>> categories = service.getAllCategories();
            List<Map<String, Object>> channels = service.getAllChannels();

            // ── Set Attributes ──────────────────────────────────
            req.setAttribute("performanceData", performanceData);
            req.setAttribute("summary", summary);
            req.setAttribute("categories", categories);
            req.setAttribute("channels", channels);

            // JSON for JavaScript
            setJsonAttr(req, "performanceDataJson", performanceData);
            setJsonAttr(req, "summaryJson", summary);
            setJsonAttr(req, "categoriesJson", categories);
            setJsonAttr(req, "channelsJson", channels);

            // Filter state
            req.setAttribute("currentCategory", categoryId);
            req.setAttribute("currentChannel", channelId);
            req.setAttribute("currentHealth", healthFilter);
            req.setAttribute("currentSearch", searchQuery);
            req.setAttribute("currentSortBy", sortBy);
            req.setAttribute("currentSortDir", ascending ? "asc" : "desc");

        } catch (Exception e) {
            // Log error and show empty state
            logger.warning("ProductPerformanceServlet: failed to load data - " + e.getMessage());
            req.setAttribute("performanceData", List.<ProductPerformance>of());
            req.setAttribute("summary", Map.<String, Object>of());
            req.setAttribute("categories", List.<Map<String, Object>>of());
            req.setAttribute("channels", List.<Map<String, Object>>of());
            setJsonAttr(req, "performanceDataJson", "[]");
            setJsonAttr(req, "summaryJson", "{}");
            setJsonAttr(req, "categoriesJson", "[]");
            setJsonAttr(req, "channelsJson", "[]");
        }

        // ── Page Metadata ───────────────────────────────────────
        req.setAttribute("pageTitle", "Hiệu Suất Sản Phẩm");
        req.setAttribute("pageSubtitle", "Phân tích lãi/lỗ theo SKU — Báo cáo chỉ đọc");
        req.setAttribute("currentPage", "product-performance");

        // Content fragment
        req.setAttribute("contentPage", "/WEB-INF/views/business/product-performance.jsp");

        // Forward to shell layout
        req.getRequestDispatcher("/WEB-INF/views/layout/dashboard-layout.jsp")
           .forward(req, resp);
    }

    /**
     * Handle AJAX request for revenue trend chart data.
     * Returns JSON with daily revenue data for a specific product.
     */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        // For now, POST is not used (read-only). Could extend for exports.
        resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }
}
