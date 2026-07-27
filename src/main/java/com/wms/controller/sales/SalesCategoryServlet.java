package com.wms.controller.sales;

import com.wms.controller.BaseController;
import com.wms.model.Category;
import com.wms.model.User;
import com.wms.service.product.CategoryService;
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
 * SalesCategoryServlet — Cho phép Sales Staff quản lý danh mục sản phẩm.
 *
 * Maps to /sales/categories.
 */
public class SalesCategoryServlet extends BaseController {

    private static final Logger LOGGER = Logger.getLogger(SalesCategoryServlet.class.getName());
    private final CategoryService categoryService = new CategoryService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        consumeFlash(req);
        try {
            // Heal pre-existing inconsistent state where a descendant is active
            // while one of its ancestors is inactive. Safe to call repeatedly.
            int repaired = categoryService.ensureCascadeConsistency();
            if (repaired > 0) {
                LOGGER.info("SalesCategoryServlet: Healed " + repaired
                    + " inconsistent active-descendant(s) under inactive ancestor(s).");
            }

            List<Category> categoryList = categoryService.findAll();
            req.setAttribute("categories", categoryList);
            setJsonAttr(req, "categoriesJson", categoryList);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "SalesCategoryServlet: Failed to load categories", e);
        }

        req.setAttribute("pageTitle",    "Quản lý danh mục sản phẩm");
        req.setAttribute("pageSubtitle", "Xây dựng sơ đồ cây phân cấp danh mục sản phẩm và ánh xạ danh mục đa sàn");
        req.setAttribute("currentPage",  "sales-categories");

        req.setAttribute("contentPage", "/WEB-INF/views/sales/categories.jsp");

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
            "delete".equals(action) || "deactivate".equals(action) ||
            "reactivate".equals(action) || "resync_all".equals(action)
        );

        if (isWriteAction && !isSalesStaff) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN,
                "Chỉ Sales Staff mới có quyền thực hiện hành động này.");
            return;
        }

        boolean success = false;
        String message = "";

        try {
            if ("create".equals(action)) {
                success = handleCreate(req);
                message = success ? "Tạo danh mục thành công!" : "Tạo danh mục thất bại.";
            } else if ("update".equals(action)) {
                success = handleUpdate(req);
                message = success ? "Cập nhật danh mục thành công!" : "Cập nhật danh mục thất bại.";
            } else if ("delete".equals(action)) {
                success = handleDelete(req);
                message = success ? "Xóa danh mục thành công!" : "Xóa danh mục thất bại.";
            } else if ("deactivate".equals(action)) {
                success = handleDeactivate(req);
                message = success ? "Ngừng hoạt động danh mục thành công!" : "Ngừng hoạt động thất bại.";
            } else if ("reactivate".equals(action)) {
                success = handleReactivate(req);
                message = success ? "Kích hoạt lại danh mục thành công!" : "Kích hoạt lại thất bại.";
            } else if ("resync_all".equals(action)) {
                int total = categoryService.findAll().size();
                int synced = categoryService.resyncAllToWebsite();
                success = synced > 0 || total == 0;
                message = "Đã đồng bộ " + synced + "/" + total + " danh mục sang Website.";
            } else {
                message = "Hành động không hợp lệ.";
            }
        } catch (IllegalArgumentException e) {
            LOGGER.log(Level.WARNING, "SalesCategoryServlet: Validation error: " + e.getMessage());
            message = e.getMessage();
            success = false;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "SalesCategoryServlet: Error processing action " + action, e);
            message = "Đã xảy ra lỗi khi xử lý yêu cầu.";
            success = false;
        }

        if (success) {
            setFlashSuccess(req, message);
        } else {
            setFlashError(req, message);
        }

        resp.sendRedirect(req.getContextPath() + "/sales/categories");
    }

    private boolean handleCreate(HttpServletRequest req) throws Exception {
        String name = req.getParameter("categoryName");
        String code = req.getParameter("categoryCode");
        String parentIdStr = req.getParameter("parentId");

        if (isNullOrEmpty(name) || isNullOrEmpty(code)) {
            return false;
        }

        CategoryService.ValidationResult codeValidation = categoryService.validateCategoryCode(code);
        if (!codeValidation.isSuccess()) {
            throw new IllegalArgumentException(codeValidation.getMessage());
        }

        Integer parentId = getIntParamOrNull(req, "parentId");
        if (parentIdStr != null && !isNullOrEmpty(parentIdStr) && parentId == null) {
            LOGGER.log(Level.WARNING, "SalesCategoryServlet: Invalid parentId: " + parentIdStr);
        }

        return categoryService.createCategory(name, code, parentId);
    }

    private boolean handleUpdate(HttpServletRequest req) throws Exception {
        String categoryIdStr = req.getParameter("categoryId");
        String name = req.getParameter("categoryName");
        String parentIdStr = req.getParameter("parentId");

        if (isNullOrEmpty(categoryIdStr) || isNullOrEmpty(name)) {
            return false;
        }

        int categoryId = getIntParam(req, "categoryId", -1);
        if (categoryId <= 0) return false;
        Category existing = categoryService.findById(categoryId);
        if (existing == null) {
            return false;
        }

        existing.setCategoryName(name.trim());
        existing.setDescription(req.getParameter("description"));
        existing.setParentId(parseParentId(parentIdStr));
        return categoryService.updateCategory(existing, null);
    }

    private boolean handleDelete(HttpServletRequest req) throws Exception {
        int categoryId = getIntParam(req, "categoryId", -1);
        if (categoryId <= 0) return false;
        CategoryService.DeleteResult result = categoryService.deleteCategory(categoryId);
        if (result.isWasSoftDelete()) {
            setFlashInfo(req, result.getMessage());
        } else if (result.isSuccess()) {
            setFlashSuccess(req, result.getMessage());
        } else {
            setFlashError(req, result.getMessage());
        }
        return result.isSuccess();
    }

    private boolean handleDeactivate(HttpServletRequest req) throws Exception {
        int categoryId = getIntParam(req, "categoryId", -1);
        if (categoryId <= 0) return false;
        int affected = categoryService.deactivateCategory(categoryId);
        if (affected > 1) {
            setFlashInfo(req,
                "Đã ngừng hoạt động danh mục và " + (affected - 1) + " danh mục con (cascade).");
        } else if (affected > 0) {
            setFlashSuccess(req, "Ngừng hoạt động danh mục thành công.");
        } else {
            setFlashError(req, "Ngừng hoạt động thất bại.");
        }
        return affected > 0;
    }

    private boolean handleReactivate(HttpServletRequest req) throws Exception {
        int categoryId = getIntParam(req, "categoryId", -1);
        if (categoryId <= 0) return false;
        Category existing = categoryService.findById(categoryId);
        if (existing == null) {
            return false;
        }
        Category blockedBy = categoryService.findInactiveAncestor(categoryId);
        if (blockedBy != null) {
            throw new IllegalArgumentException(
                "Không thể kích hoạt lại: danh mục cha '" + blockedBy.getCategoryName()
                + "' đang ngừng hoạt động. Hãy kích hoạt danh mục cha trước.");
        }
        return categoryService.reactivateCategory(categoryId);
    }

    private Integer parseParentId(String parentIdStr) {
        if (isNullOrEmpty(parentIdStr)) return null;
        try {
            return Integer.parseInt(parentIdStr);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
