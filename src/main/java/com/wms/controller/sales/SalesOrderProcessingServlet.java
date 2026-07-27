package com.wms.controller.sales;

import com.wms.controller.BaseController;
import com.wms.dao.LazadaOrderDAO;
import com.wms.dao.LazadaShipmentProviderDAO;
import com.wms.model.LazadaShipmentProvider;
import com.wms.model.Order;
import com.wms.model.Warehouse;
import com.wms.service.sales.OrderService;
import com.wms.service.warehouse.WarehouseService;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * SalesOrderProcessingServlet — Handles the "Xử lý đơn hàng" (Order Processing) page for Sales Staff.
 * Maps to /sales/order-processing.
 */
public class SalesOrderProcessingServlet extends BaseController {

    private final OrderService orderService = new OrderService();
    private final WarehouseService warehouseService = new WarehouseService();
    private final LazadaOrderDAO lazadaOrderDAO = new LazadaOrderDAO();
    private final LazadaShipmentProviderDAO providerDAO = new LazadaShipmentProviderDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        try {
            // Trigger Lazada order sync synchronously before loading the list of orders
            try {
                new com.wms.service.lazada.LazadaOrderSyncService().syncAllActiveChannels(50);
            } catch (Exception syncEx) {
                // Log warning and ignore sync errors so page still loads from database
                java.util.logging.Logger.getLogger(SalesOrderProcessingServlet.class.getName())
                    .log(java.util.logging.Level.WARNING, "Syncing active channels failed in doGet", syncEx);
            }

            List<Order> list = orderService.findAllOrders();
            List<Warehouse> warehouses = warehouseService.findAllActive();
            List<Map<String, Object>> lazadaOrders = lazadaOrderDAO.findAllWithItemsAndStock();
            List<LazadaShipmentProvider> providers = providerDAO.findAllActive();
            req.setAttribute("orderList", list);
            req.setAttribute("warehouses", warehouses);
            setJsonAttr(req, "warehousesJson", warehouses);
            setJsonAttr(req, "lazadaOrdersJson", lazadaOrders);
            setJsonAttr(req, "shipmentProvidersJson", providers);

            List<com.wms.model.RmaRequest> pendingRmaList = new com.wms.dao.RmaDAO().findPendingForWebsite();
            req.setAttribute("pendingRmaList", pendingRmaList);
        } catch (Exception e) {
            req.setAttribute("orderList", List.of());
            req.setAttribute("warehouses", List.<Warehouse>of());
            req.setAttribute("warehousesJson", "[]");
            req.setAttribute("lazadaOrdersJson", "[]");
            req.setAttribute("shipmentProvidersJson", "[]");
        }

        req.setAttribute("pageTitle",    "Xử Lý Đơn Hàng");
        req.setAttribute("pageSubtitle", "Sales Staff duyệt đơn, in tem vận chuyển hàng loạt, xác nhận RTS và xử lý khiếu nại RMA");
        req.setAttribute("currentPage",  "sales-processing");

        req.setAttribute("contentPage", "/WEB-INF/views/sales/order-processing.jsp");

        req.getRequestDispatcher("/WEB-INF/views/layout/sales-layout.jsp")
           .forward(req, resp);
    }
}
