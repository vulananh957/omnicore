package com.wms.controller.sales;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.controller.BaseController;
import com.wms.dao.LazadaOrderDAO;
import com.wms.dao.WarehouseDAO;
import com.wms.model.Channel;
import com.wms.model.Order;
import com.wms.service.sales.ChannelService;
import com.wms.service.sales.OrderService;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * SalesOrdersServlet — Handles the "Đơn hàng" (Orders) page for Sales Staff.
 * Maps to /sales/orders.
 */
public class SalesOrdersServlet extends BaseController {

    private final OrderService orderService = new OrderService();
    private final ChannelService channelService = new ChannelService();
    private final LazadaOrderDAO lazadaOrderDAO = new LazadaOrderDAO();
    private final WarehouseDAO warehouseDAO = new WarehouseDAO();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        try {
            // Trigger Lazada order sync synchronously before loading the list of orders
            try {
                new com.wms.service.lazada.LazadaOrderSyncService().syncAllActiveChannels(50);
            } catch (Exception syncEx) {
                // Log warning and ignore sync errors so page still loads from database
                java.util.logging.Logger.getLogger(SalesOrdersServlet.class.getName())
                    .log(java.util.logging.Level.WARNING, "Syncing active channels failed in doGet", syncEx);
            }

            List<Order> list = orderService.findAllOrders();
            List<Channel> channels = channelService.findAll();
            req.setAttribute("orderList", list);
            req.setAttribute("channels", channels);
            setJsonAttr(req, "channelsJson", channels);

            // Load Lazada orders with items + WMS stock for inventory table
            List<Map<String, Object>> lazadaOrders = lazadaOrderDAO.findAllWithItemsAndStock();
            req.setAttribute("lazadaOrdersJson", objectMapper.writeValueAsString(lazadaOrders));

            // Load active warehouses for dynamic inventory table columns
            var warehouses = warehouseDAO.findAll().stream()
                .filter(w -> w.isActive())
                .map(w -> Map.<String, Object>of(
                    "warehouseId", w.getWarehouseId(),
                    "warehouseName", w.getWarehouseName()))
                .collect(java.util.stream.Collectors.toList());
            req.setAttribute("warehousesJson", objectMapper.writeValueAsString(warehouses));
        } catch (Exception e) {
            req.setAttribute("orderList", List.of());
            req.setAttribute("channels", List.<Channel>of());
            req.setAttribute("channelsJson", "[]");
            req.setAttribute("lazadaOrdersJson", "[]");
            req.setAttribute("warehousesJson", "[]");
        }

        req.setAttribute("pageTitle",    "Danh Sách Đơn Hàng");
        req.setAttribute("pageSubtitle", "Giám sát đơn hàng từ các kênh bán hàng");
        req.setAttribute("currentPage",  "sales-orders");

        req.setAttribute("contentPage", "/WEB-INF/views/sales/sales-orders.jsp");

        req.getRequestDispatcher("/WEB-INF/views/layout/sales-layout.jsp")
           .forward(req, resp);
    }
}
