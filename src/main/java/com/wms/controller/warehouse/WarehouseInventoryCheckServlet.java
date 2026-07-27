package com.wms.controller.warehouse;

import com.wms.controller.BaseController;
import com.wms.dao.InventoryDAO;
import com.wms.model.Category;
import com.wms.model.Product;
import com.wms.model.User;
import com.wms.service.product.ProductService;
import com.wms.service.warehouse.WarehouseService;
import com.wms.service.warehouse.InventoryCheckService;
import com.wms.service.common.NotificationService;
import com.wms.util.AppConstants;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * WarehouseInventoryCheckServlet — Handles Physical Inventory Check (Kiểm kê kho) for the Warehouse Staff.
 *
 * Maps to /warehouse/inventory-check.
 *
 * Simplified: GET renders the JSP with master data only; POST processes form actions
 * (create / submit / adjust) and redirects back. Inventory-check persistence is delegated
 * to WarehouseService (which calls WarehouseDAO), not the legacy InventoryCheckDAO.
 */
public class WarehouseInventoryCheckServlet extends BaseController {

    private static final Logger LOGGER = Logger.getLogger(WarehouseInventoryCheckServlet.class.getName());
    private final ProductService productService = new ProductService();
    private final WarehouseService warehouseService = new WarehouseService();
    private final InventoryCheckService inventoryCheckService = new InventoryCheckService();
    private final NotificationService notificationService = new NotificationService();
    private final InventoryDAO inventoryDAO = new InventoryDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        consumeFlash(req);

        String ajax = req.getParameter("ajax");
        if ("1".equals(ajax)) {
            handleAjax(req, resp);
            return;
        }

        int myWarehouseId = currentWarehouseId(req);
        try {
            com.wms.model.Warehouse myWh = warehouseService.findById(myWarehouseId);
            List<com.wms.model.Warehouse> whs = myWh != null ? List.of(myWh) : List.of();
            req.setAttribute("warehouses", whs);
            setJsonAttr(req, "warehousesJson", whs);
        } catch (Exception e) {
            req.setAttribute("warehouses", List.of());
            req.setAttribute("warehousesJson", "[]");
        }

        try {
            List<com.wms.model.Zone> myZones = warehouseService.findZonesByWarehouseId(myWarehouseId);
            req.setAttribute("zones", myZones);
            setJsonAttr(req, "zonesJson", myZones);
        } catch (Exception e) {
            req.setAttribute("zones", List.of());
            req.setAttribute("zonesJson", "[]");
        }

        try {
            List<User> staff = warehouseService.findByRoles("WAREHOUSE_STAFF");
            List<User> myStaff = staff.stream()
                .filter(u -> u.getWarehouseId() == myWarehouseId)
                .collect(java.util.stream.Collectors.toList());
            req.setAttribute("staffMembers", myStaff);
        } catch (Exception e) {
            req.setAttribute("staffMembers", List.of());
        }

        try {
            req.setAttribute("categories", productService.findAllCategories());
            setJsonAttr(req, "categoriesJson", productService.findAllCategories());
        } catch (Exception e) {
            req.setAttribute("categories", List.of());
            req.setAttribute("categoriesJson", "[]");
        }

        try {
            req.setAttribute("products", productService.findAll());
            setJsonAttr(req, "productsJson", productService.findAll());
        } catch (Exception e) {
            req.setAttribute("products", List.of());
            req.setAttribute("productsJson", "[]");
        }

        req.setAttribute("pageTitle",    "Kiểm Kê & Cân Bằng Tồn Kho");
        req.setAttribute("pageSubtitle", "Đối soát số lượng hệ thống vs. đếm tay thực tế — điều chỉnh độ lệch");
        req.setAttribute("currentPage",  "wh-inventory-check");
        req.setAttribute("contentPage", "/WEB-INF/views/inventory/warehouse-inventory-check.jsp");

        req.getRequestDispatcher("/WEB-INF/views/layout/warehouse-layout.jsp")
           .forward(req, resp);
    }

    private void handleAjax(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String action = req.getParameter("action");
        int myWarehouseId = currentWarehouseId(req);
        if ("checks".equals(action)) {
            List<com.wms.model.PhysicalInventory> checks = warehouseService.findInventoryChecksByWarehouse(myWarehouseId);
            writeJson(resp, com.wms.util.JsonUtil.toJson(checks));
        } else if ("checkDetails".equals(action)) {
            String checkIdStr = req.getParameter("checkId");
            if (checkIdStr != null) {
                try {
                    int checkId = Integer.parseInt(checkIdStr);
                    List<com.wms.model.PhysicalInventoryDetail> details = warehouseService.findPhysicalInventoryDetails(checkId);
                    writeJson(resp, com.wms.util.JsonUtil.toJson(details));
                } catch (NumberFormatException e) {
                    writeJson(resp, "[]");
                }
            } else {
                writeJson(resp, "[]");
            }
        } else {
            writeJson(resp, "[]");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        req.setCharacterEncoding("UTF-8");
        HttpSession session = req.getSession();
        User currentUser = (User) session.getAttribute(AppConstants.SESSION_USER);
        int userId = currentUser != null ? currentUser.getUserId() : 1;

        try {
            String body = req.getReader().lines().reduce("", (a, b) -> a + b);
            if (body == null || body.trim().isEmpty()) {
                writeJson(resp, "{\"success\":false,\"message\":\"Yêu cầu trống.\"}");
                return;
            }

            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> payload = parseJson(body, java.util.Map.class);
            String action = (String) payload.get("action");
            int myWarehouseId = currentWarehouseId(req);

            if ("create".equals(action)) {
                handleCreate(payload, userId, myWarehouseId);
                writeJson(resp, "{\"success\":true}");
            } else if ("submit".equals(action)) {
                handleSubmit(payload, userId, myWarehouseId);
                writeJson(resp, "{\"success\":true}");
            } else if ("adjust".equals(action)) {
                handleAdjust(payload, userId, myWarehouseId);
                writeJson(resp, "{\"success\":true}");
            } else {
                writeJson(resp, "{\"success\":false,\"message\":\"Hành động không hợp lệ: " + action + "\"}");
            }
        } catch (IllegalArgumentException e) {
            writeJson(resp, "{\"success\":false,\"message\":\"" + escapeJson(e.getMessage()) + "\"}");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "WarehouseInventoryCheckServlet doPost error", e);
            writeJson(resp, "{\"success\":false,\"message\":\"Lỗi xử lý: " + escapeJson(e.getMessage()) + "\"}");
        }
    }

    private void handleCreate(java.util.Map<String, Object> payload, int userId, int myWarehouseId) throws Exception {
        String title = (String) payload.get("title");
        String note = (String) payload.get("note");
        String scopeType = (String) payload.get("scopeType");
        String scopeValue = (String) payload.get("scopeValue");

        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("Vui lòng nhập Tiêu đề phiếu.");
        }
        int warehouseId = myWarehouseId;

        // Build checkCode: PK-YYYYMMDD-NNNN
        java.time.LocalDate today = java.time.LocalDate.now();
        String suffix = String.format("-%04d", (int) (Math.random() * 9999));
        String checkCode = "PK-" + today.toString().replace("-", "") + suffix;

        // Collect valid category IDs (including child categories)
        java.util.Set<Integer> validCatIds = new java.util.HashSet<>();
        if ("category".equals(scopeType) && scopeValue != null && !scopeValue.isBlank()) {
            try {
                int targetCatId = Integer.parseInt(scopeValue);
                validCatIds.add(targetCatId);
                List<Category> allCats = productService.findAllCategories();
                for (Category c : allCats) {
                    if (c.getParentId() != null && c.getParentId() == targetCatId) {
                        validCatIds.add(c.getCategoryId());
                    }
                }
            } catch (Exception ignored) {}
        }

        // Fetch stock specific to the target warehouse
        java.util.Map<Integer, java.math.BigDecimal> whStockMap = inventoryDAO.findStockByWarehouse(warehouseId);

        // Build items JSON
        StringBuilder sb = new StringBuilder("[");
        List<Product> products = safeProducts();
        int idx = 0;
        for (Product p : products) {
            boolean include = false;
            if ("all".equals(scopeType)) {
                include = true;
            } else if ("category".equals(scopeType)) {
                include = p.getCategoryId() != null && validCatIds.contains(p.getCategoryId());
            } else if ("sku".equals(scopeType) && scopeValue != null) {
                include = p.getSkuCode() != null && p.getSkuCode().equals(scopeValue);
            }
            if (include) {
                if (idx > 0) sb.append(",");
                java.math.BigDecimal sysQtyBD = whStockMap.getOrDefault(p.getProductId(), java.math.BigDecimal.ZERO);
                double sysQty = sysQtyBD != null ? sysQtyBD.doubleValue() : 0d;
                sb.append("{\"productId\":").append(p.getProductId())
                  .append(",\"systemQty\":").append(sysQty).append("}");
                idx++;
            }
        }
        sb.append("]");
        String itemsJson = sb.toString();

        warehouseService.createInventoryCheck(checkCode, warehouseId, userId, note, itemsJson);
    }

    private void handleSubmit(java.util.Map<String, Object> payload, int userId, int myWarehouseId) throws Exception {
        Number checkIdNum = (Number) payload.get("checkId");
        if (checkIdNum == null) throw new IllegalArgumentException("Thiếu ID phiếu kiểm kê.");
        int checkId = checkIdNum.intValue();

        com.wms.model.PhysicalInventory check = warehouseService.findInventoryCheckById(checkId);
        if (check == null || check.getWarehouseId() != myWarehouseId) {
            throw new IllegalArgumentException("Bạn không có quyền thực hiện trên phiếu kiểm kê thuộc kho khác.");
        }

        String resultsJson = (String) payload.get("resultsJson");
        if (resultsJson == null) throw new IllegalArgumentException("Thiếu dữ liệu kết quả kiểm đếm.");
        
        List<?> list = parseJson(resultsJson, List.class);
        List<java.util.Map<String, Object>> updatedList = new ArrayList<>();
        for (Object obj : list) {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> item = new HashMap<>((java.util.Map<String, Object>) obj);
            item.put("countedBy", userId);
            updatedList.add(item);
        }
        resultsJson = com.wms.util.JsonUtil.toJson(updatedList);
        
        warehouseService.submitInventoryCheckResults(checkId, resultsJson);
        // Approve + notify via service (Controller → Service → DAO, never call DAO directly)
        inventoryCheckService.approveInventoryAdjustments(check, userId);
    }

    private void handleAdjust(java.util.Map<String, Object> payload, int userId, int myWarehouseId) throws Exception {
        Number checkIdNum = (Number) payload.get("checkId");
        if (checkIdNum == null) throw new IllegalArgumentException("Thiếu ID phiếu kiểm kê.");
        int checkId = checkIdNum.intValue();

        com.wms.model.PhysicalInventory check = warehouseService.findInventoryCheckById(checkId);
        if (check == null || check.getWarehouseId() != myWarehouseId) {
            throw new IllegalArgumentException("Bạn không có quyền điều chỉnh phiếu kiểm kê thuộc kho khác.");
        }

        String adjustmentsJson = (String) payload.get("adjustmentsJson");
        if (adjustmentsJson == null) throw new IllegalArgumentException("Thiếu dữ liệu điều chỉnh.");
        warehouseService.adjustInventoryFromCheck(checkId, adjustmentsJson, userId);
    }

    private List<Product> safeProducts() {
        try { return productService.findAll(); } catch (Exception e) { return List.of(); }
    }
}
