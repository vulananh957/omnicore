package com.wms.controller.warehouse;

import com.wms.controller.BaseController;
import com.wms.dao.InventoryDAO;
import com.wms.dao.WarehouseDAO;
import com.wms.model.Warehouse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WarehouseInventoryServlet extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(WarehouseInventoryServlet.class);
    private final InventoryDAO inventoryDAO = new InventoryDAO();
    private final WarehouseDAO warehouseDAO = new WarehouseDAO();
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        int warehouseId = currentWarehouseId(req);
        log.info("[WarehouseInventory] warehouseId={}", warehouseId);

        try {
            List<Map<String, Object>> inventoryList = inventoryDAO.findInventorySummaryByWarehouse(warehouseId);
            String json = mapper.writeValueAsString(inventoryList);
            req.setAttribute("inventoryListJson", json);
            log.info("[WarehouseInventory] inventory.size={}", inventoryList.size());
        } catch (Exception e) {
            req.setAttribute("inventoryListJson", "[]");
            log.warn("[WarehouseInventory] error loading inventory", e);
        }

        try {
            Warehouse warehouse = warehouseDAO.findById(warehouseId);
            req.setAttribute("warehouse", warehouse);
        } catch (Exception e) {
            req.setAttribute("warehouse", null);
        }

        req.setAttribute("pageTitle",    "Tồn Kho");
        req.setAttribute("pageSubtitle", "Quản lý tồn kho vật lý theo từng mặt hàng và kho bãi");
        req.setAttribute("currentPage",  "wh-inventory");
        req.setAttribute("contentPage", "/WEB-INF/views/inventory/warehouse-inventory.jsp");

        req.getRequestDispatcher("/WEB-INF/views/layout/warehouse-layout.jsp")
           .forward(req, resp);
    }
}
