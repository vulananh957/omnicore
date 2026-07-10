package com.wms.controller.dashboard;

import com.wms.controller.BaseController;
import com.wms.model.Channel;
import com.wms.service.sales.ChannelService;
import com.wms.service.sales.OrderService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

public class BusinessDashboardServlet extends BaseController {

    private final ChannelService channelService = new ChannelService();
    private final OrderService orderService = new OrderService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String period = req.getParameter("period");
        if (period == null || period.trim().isEmpty()) period = "30ngay";
        req.setAttribute("period", period);

        try {
            List<Channel> channels = channelService.findAll();
            req.setAttribute("channels", channels);
            setJsonAttr(req, "channelsJson", channels);
        } catch (Exception e) {
            req.setAttribute("channels", List.<Channel>of());
            req.setAttribute("channelsJson", "[]");
        }

        // Load KPI data
        try {
            java.math.BigDecimal totalRevenue = orderService.getTotalRevenue(period);
            int totalOrders = orderService.getTotalOrders(period);
            java.math.BigDecimal avgOrderValue = orderService.getAvgOrderValue(period);
            java.math.BigDecimal returnRate = orderService.getReturnRate(period);

            java.math.BigDecimal revenueGrowth = orderService.getRevenueGrowth(period);
            java.math.BigDecimal ordersGrowth = orderService.getOrdersGrowth(period);
            java.math.BigDecimal avgOrderGrowth = orderService.getAvgOrderGrowth(period);
            java.math.BigDecimal returnRateGrowth = orderService.getReturnRateGrowth(period);

            List<java.util.Map<String, Object>> dailyData = orderService.getDailyRevenueData(period);
            java.util.Map<String, java.math.BigDecimal> categoryData = orderService.getCategoryRevenueData(period);
            List<java.util.Map<String, Object>> orderStatus = orderService.getOrderStatusBreakdown(period);
            List<java.util.Map<String, Object>> topProducts = orderService.getTopProductsDetailed(period, 50);

            req.setAttribute("totalRevenue", totalRevenue);
            req.setAttribute("totalOrders", totalOrders);
            req.setAttribute("avgOrderValue", avgOrderValue);
            req.setAttribute("returnRate", returnRate);

            req.setAttribute("revenueGrowth", revenueGrowth);
            req.setAttribute("ordersGrowth", ordersGrowth);
            req.setAttribute("avgOrderGrowth", avgOrderGrowth);
            req.setAttribute("returnRateGrowth", returnRateGrowth);

            req.setAttribute("dailyData", dailyData);
            req.setAttribute("categoryData", categoryData);
            req.setAttribute("orderStatus", orderStatus);
            req.setAttribute("topProducts", topProducts);

            setJsonAttr(req, "dailyDataJson", dailyData);
            setJsonAttr(req, "categoryDataJson", categoryData);
            setJsonAttr(req, "orderStatusJson", orderStatus);
            setJsonAttr(req, "topProductsJson", topProducts);
        } catch (Exception e) {
            req.setAttribute("dailyDataJson", "null");
            req.setAttribute("categoryDataJson", "null");
            req.setAttribute("orderStatusJson", "null");
            req.setAttribute("topProductsJson", "null");
        }

        // Page metadata for the layout
        req.setAttribute("pageTitle",    "Bảng Điều Khiển Hệ Thống Bán Hàng");
        req.setAttribute("pageSubtitle", "Quản lý bán hàng đa kênh - Theo dõi doanh thu và xu hướng");
        req.setAttribute("currentPage",  "dashboard");

        // Tell the layout which body fragment to include
        req.setAttribute("contentPage",
            "/WEB-INF/views/dashboard/business.jsp");

        // Forward to the shell layout
        req.getRequestDispatcher("/WEB-INF/views/layout/dashboard-layout.jsp")
           .forward(req, resp);
    }
}
