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

            // Serialize orders to JSON safely using Jackson to avoid parsing errors
            List<Map<String, Object>> ordersJsonList = new java.util.ArrayList<>();
            for (Order o : list) {
                Map<String, Object> oMap = new java.util.HashMap<>();
                oMap.put("id", o.getOrderCode());
                oMap.put("channel", "ONLINE".equals(o.getChannel()) ? "Lazada" : o.getChannel());
                oMap.put("customerName", o.getCustomerName());
                oMap.put("customerPhone", o.getCustomerPhone());
                oMap.put("customerAddress", o.getCustomerAddress());
                oMap.put("shippingFee", o.getShippingFee());
                
                int tQty = 0;
                List<Map<String, Object>> itemsList = new java.util.ArrayList<>();
                for (com.wms.model.OrderItem item : o.getItems()) {
                    tQty += item.getQuantity();
                    Map<String, Object> iMap = new java.util.HashMap<>();
                    iMap.put("sku", item.getSkuCode());
                    iMap.put("name", item.getProductName());
                    iMap.put("quantity", item.getQuantity());
                    iMap.put("price", item.getUnitPrice());
                    iMap.put("warehouseStocks", item.getWarehouseStocks());
                    iMap.put("qtyAvailable", item.getQtyAvailable());
                    itemsList.add(iMap);
                }
                oMap.put("totalItems", tQty);
                oMap.put("totalAmount", o.getTotalAmount());
                
                String ws = o.getStatus();
                String fs = "pending_review";
                if (ws != null) {
                    switch (ws.toUpperCase()) {
                        case "PENDING": fs = "pending_review"; break;
                        case "CONFIRMED":
                        case "PICKING": fs = "confirmed"; break;
                        case "PACKING": fs = "packing"; break;
                        case "PACKED": fs = "packed"; break;
                        case "SHIPPED": fs = "shipping"; break;
                        case "DELIVERED": fs = "delivered"; break;
                        case "COMPLETED": fs = "completed"; break;
                        case "RETURNED": fs = "returned"; break;
                        case "DISPUTED": fs = "disputed"; break;
                        case "DISPUTE_SUCCESS": fs = "dispute_success"; break;
                        case "CANCELLED": fs = "cancelled"; break;
                        default: fs = ws.toLowerCase();
                    }
                }
                oMap.put("status", fs);
                oMap.put("warehouse", o.getWarehouseName());
                oMap.put("trackingNo", o.getTrackingNo());
                oMap.put("reviewNote", o.getReviewNote());
                oMap.put("rmaReason", o.getRmaReason());
                oMap.put("rmaPhysicalStatus", o.getRmaPhysicalStatus());
                oMap.put("rmaPlatformStatus", o.getRmaPlatformStatus());
                oMap.put("disputeEvidenceVideo", o.getDisputeEvidenceVideo());
                oMap.put("disputeNote", o.getDisputeNote());
                oMap.put("createdAt", o.getCreatedAt() != null ? o.getCreatedAt().toString() : "");
                oMap.put("items", itemsList);
                ordersJsonList.add(oMap);
            }
            req.setAttribute("ordersJson", objectMapper.writeValueAsString(ordersJsonList));

            List<com.wms.model.RmaRequest> pendingRmaList = new com.wms.dao.RmaDAO().findPendingForWebsite();
            req.setAttribute("pendingRmaList", pendingRmaList);

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
            req.setAttribute("ordersJson", "[]");
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
