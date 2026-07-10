package com.wms.controller.warehouse;

import com.wms.controller.BaseController;
import com.wms.dao.InventoryDAO;
import com.wms.model.Category;
import com.wms.model.Product;
import com.wms.model.Warehouse;
import com.wms.model.Zone;
import com.wms.service.product.ProductService;
import com.wms.service.warehouse.WarehouseService;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

/**
 * WarehouseMasterSKUServlet — Handles requests for the Master SKU catalog under the Warehouse Staff role.
 *
 * Maps to /warehouse/master-sku.
 *
 * doGet:  loads full product list and forwards to JSP
 * doPost: warehouse staff may edit ONLY min/max stock and the zone of their own warehouse.
 */
public class WarehouseMasterSKUServlet extends BaseController {

    private static final String CONTEXT_PATH = "/warehouse/master-sku";

    private final ProductService productService = new ProductService();
    private final WarehouseService warehouseService = new WarehouseService();
    private final InventoryDAO inventoryDAO = new InventoryDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        consumeFlash(req);

        int myWarehouseId = currentWarehouseId(req);

        try {
            List<Product> products = productService.findAll();
            java.util.Map<Integer, java.math.BigDecimal> stockMap =
                    inventoryDAO.findStockByWarehouse(myWarehouseId);
            for (Product p : products) {
                java.math.BigDecimal qty = stockMap.get(p.getProductId());
                p.setQtyOnHand(qty != null ? qty.doubleValue() : 0.0);
            }
            req.setAttribute("products", products);
        } catch (Exception e) {
            req.setAttribute("products", List.<Product>of());
        }

        try {
            List<Category> categories = productService.findAllCategories();
            req.setAttribute("categories", categories);
            setJsonAttr(req, "categoriesJson", categories);
        } catch (Exception e) {
            req.setAttribute("categories", List.<Category>of());
            req.setAttribute("categoriesJson", "[]");
        }

        try {
            Warehouse myWh = warehouseService.findById(myWarehouseId);
            List<Warehouse> warehouses = (myWh != null) ? List.of(myWh) : List.of();
            req.setAttribute("warehouses", warehouses);
            setJsonAttr(req, "warehousesJson", warehouses);
        } catch (Exception e) {
            req.setAttribute("warehouses", List.<Warehouse>of());
            req.setAttribute("warehousesJson", "[]");
        }

        try {
            List<Zone> myZones = warehouseService.findZonesByWarehouseId(myWarehouseId);
            req.setAttribute("zones", myZones);
            setJsonAttr(req, "zonesJson", myZones);
        } catch (Exception e) {
            req.setAttribute("zones", List.<Zone>of());
            req.setAttribute("zonesJson", "[]");
        }

        req.setAttribute("myWarehouseId", currentWarehouseId(req));

        req.setAttribute("pageTitle",    "Tra cứu sản phẩm");
        req.setAttribute("pageSubtitle", "Bảng tra hình dáng / kích thước sản phẩm và phân khu vực lưu trữ tại kho của bạn");
        req.setAttribute("currentPage",  "wh-master-sku");
        req.setAttribute("contentPage", "/WEB-INF/views/sku/warehouse-master-sku.jsp");

        req.getRequestDispatcher("/WEB-INF/views/layout/warehouse-layout.jsp")
           .forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        req.setCharacterEncoding("UTF-8");
        String action = req.getParameter("action");

        if ("edit".equals(action)) {
            try {
                // Warehouse Staff may edit ONLY min/max stock and the zone of their own warehouse.
                int productId = Integer.parseInt(req.getParameter("productId"));
                int warehouseId = currentWarehouseId(req);
                Double minStock = parseDouble(req.getParameter("minStock"));
                Double maxStock = parseDouble(req.getParameter("maxStock"));
                Integer zoneId = parseInteger(req.getParameter("zoneId"));

                ProductService.UpdateResult r =
                        productService.updateProductForWarehouse(productId, minStock, maxStock, warehouseId, zoneId);
                if (!r.isSuccess()) {
                    setFlashError(req, r.getMessage());
                } else {
                    setFlashSuccess(req, "Cập nhật SKU thành công!");
                }
            } catch (Exception e) {
                setFlashError(req, "Lỗi khi cập nhật SKU: " + e.getMessage());
            }
        } else if (action != null && !action.isEmpty()) {
            setFlashError(req, "Warehouse staff không có quyền thực hiện thao tác này.");
        }
        redirect(resp, req.getContextPath() + CONTEXT_PATH);
    }

    private Double parseDouble(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        try { return Double.parseDouble(s.trim()); }
        catch (NumberFormatException e) { return null; }
    }

    private Integer parseInteger(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        try { return Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { return null; }
    }
}
