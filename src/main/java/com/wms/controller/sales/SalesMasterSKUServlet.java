package com.wms.controller.sales;

import com.wms.controller.BaseController;
import com.wms.model.Product;
import com.wms.model.User;
import com.wms.service.product.ProductService;
import com.wms.util.AppConstants;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SalesMasterSKUServlet — Sales Staff quản lý Master SKU catalog (toàn công ty).
 *
 * Maps to /sales/master-sku.
 *
 * Master SKU là thực thể mức công ty (single source of truth), KHÔNG gắn với
 * một kho cụ thể. Vị trí lưu trữ (warehouse + zone) do Warehouse Staff phụ
 * trách thông qua {@code updateProductForWarehouse} tại
 * {@code /warehouse/master-sku}. Servlet này chỉ làm việc với thông tin
 * thuộc tính sản phẩm (tên, mã SKU, danh mục, đơn vị, min/max stock,
 * trọng lượng, kích thước, giá gốc).
 */
public class SalesMasterSKUServlet extends BaseController {

    private static final Logger LOGGER = Logger.getLogger(SalesMasterSKUServlet.class.getName());
    private final ProductService productService = new ProductService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        consumeFlash(req);
        try {
            List<Product> products = productService.findAll();
            req.setAttribute("products", products);
            setJsonAttr(req, "productsJson", products);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "SalesMasterSKUServlet: Failed to load product data", e);
            req.setAttribute("products", List.of());
            req.setAttribute("productsJson", "[]");
        }

        try {
            List<com.wms.model.Category> categories = productService.findAllCategories();
            req.setAttribute("categories", categories);
            setJsonAttr(req, "categoriesJson", categories);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "SalesMasterSKUServlet: Failed to load categories", e);
            req.setAttribute("categories", List.of());
            req.setAttribute("categoriesJson", "[]");
        }

        req.setAttribute("pageTitle",    "Danh Mục Master SKU");
        req.setAttribute("pageSubtitle", "Quản lý thông tin gốc sản phẩm — nguồn chuẩn đồng bộ đa kênh");
        req.setAttribute("currentPage",  "sales-master-sku");

        req.setAttribute("contentPage", "/WEB-INF/views/sales/master-sku.jsp");

        req.getRequestDispatcher("/WEB-INF/views/layout/sales-layout.jsp")
           .forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        req.setCharacterEncoding("UTF-8");
        String action = req.getParameter("action");
        HttpSession session = req.getSession();
        User currentUser = (User) session.getAttribute(AppConstants.SESSION_USER);

        boolean isSalesStaff = currentUser != null && "SALES_STAFF".equals(currentUser.getRole());
        boolean isWriteAction = action != null && (
            "create".equals(action) || "update".equals(action) ||
            "delete".equals(action)
        );

        if (isWriteAction && !isSalesStaff) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN,
                "Chi Sales Staff moi co quyen thuc hien hanh dong nay.");
            return;
        }

        int userId = currentUser != null ? currentUser.getUserId() : 1;

        try {
            if ("create".equals(action)) {
                Product p = new Product();
                p.setSkuCode(req.getParameter("skuCode"));
                p.setProductName(req.getParameter("productName"));
                String catId = req.getParameter("categoryId");
                if (catId != null && !catId.trim().isEmpty()) {
                    p.setCategoryId(Integer.parseInt(catId));
                } else {
                    String catName = req.getParameter("categoryName");
                    if (catName != null && !catName.trim().isEmpty()) {
                        p.setCategoryId(productService.resolveCategoryId(catName));
                    }
                }
                p.setBarcode(req.getParameter("barcode"));
                p.setUnit(req.getParameter("unit"));
                p.setWeightKg(parseDouble(req.getParameter("weight")));
                p.setDimensions(req.getParameter("dimensions"));
                String attrParam = req.getParameter("attributesText");
                p.setAttributesText(attrParam != null && !attrParam.isBlank() ? attrParam : req.getParameter("dimensions"));
                p.setBasePrice(parseDouble(req.getParameter("basePrice")));

                boolean ok = productService.createProduct(p, userId);
                if (!ok) {
                    req.setAttribute("error", "Không thể tạo sản phẩm. Có thể mã SKU đã tồn tại.");
                    doGet(req, resp);
                    return;
                }
                resp.sendRedirect(req.getContextPath() + "/sales/master-sku");

            } else if ("update".equals(action)) {
                int productId = Integer.parseInt(req.getParameter("productId"));
                Product updates = new Product();
                updates.setProductName(req.getParameter("productName"));
                String catId = req.getParameter("categoryId");
                if (catId != null && !catId.trim().isEmpty()) {
                    updates.setCategoryId(Integer.parseInt(catId));
                } else {
                    String catName = req.getParameter("categoryName");
                    if (catName != null && !catName.trim().isEmpty()) {
                        updates.setCategoryId(productService.resolveCategoryId(catName));
                    }
                }
                updates.setWeightKg(parseDouble(req.getParameter("weight")));
                updates.setDimensions(req.getParameter("dimensions"));
                String attrParam = req.getParameter("attributesText");
                if (attrParam != null) updates.setAttributesText(attrParam);
                updates.setBarcode(req.getParameter("barcode"));
                updates.setUnit(req.getParameter("unit"));
                updates.setBasePrice(parseDouble(req.getParameter("basePrice")));

                ProductService.UpdateResult r = productService.updateProduct(productId, updates);
                if (!r.isSuccess()) {
                    session.setAttribute("errorMessage", r.getMessage());
                } else {
                    session.setAttribute("successMessage", "Cập nhật SKU thành công!");
                }
                resp.sendRedirect(req.getContextPath() + "/sales/master-sku");

            } else if ("delete".equals(action)) {
                int productId = Integer.parseInt(req.getParameter("productId"));
                ProductService.DeleteResult r = productService.deleteProduct(productId);
                if (!r.isSuccess()) {
                    setFlashError(req, r.getMessage());
                } else {
                    setFlashSuccess(req, "Đã xóa thành công SKU khỏi WMS và kênh bán hàng (Website)!");
                }
                resp.sendRedirect(req.getContextPath() + "/sales/master-sku");

            } else {
                resp.sendRedirect(req.getContextPath() + "/sales/master-sku");
            }
        } catch (NumberFormatException e) {
            req.setAttribute("error", "Tham số không hợp lệ: " + e.getMessage());
            doGet(req, resp);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "SalesMasterSKUServlet doPost error", e);
            req.setAttribute("error", "Lỗi hệ thống: " + e.getMessage());
            doGet(req, resp);
        }
    }

    private Double parseDouble(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        try { return Double.parseDouble(s.trim()); }
        catch (NumberFormatException e) { return null; }
    }
}
