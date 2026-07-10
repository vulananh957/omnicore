package com.wms.controller.business;

import com.wms.controller.BaseController;
import com.wms.model.User;
import com.wms.service.common.PricingConfigService;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;

/**
 * ManagerConfigServlet — Cấu hình ngưỡng cảnh báo giá bán cho Sales Staff.
 *
 * <p>Chỉ user có role = "MANAGER" được truy cập trang này.
 * ADMIN không có quyền.</p>
 *
 * GET  /business/config → render form cấu hình
 * POST /business/config → lưu thay đổi thresholds
 */
public class ManagerConfigServlet extends BaseController {

    private final PricingConfigService pricingConfigService = new PricingConfigService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        // Role guard: chỉ MANAGER được truy cập
        if (!isManager(req)) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Chỉ Manager mới được truy cập trang này.");
            return;
        }

        Map<String, BigDecimal> thresholds = pricingConfigService.getPricing();
        req.setAttribute("thresholds", thresholds);
        req.setAttribute("pageTitle",    "Cấu hình Hệ thống");
        req.setAttribute("pageSubtitle", "Ngưỡng cảnh báo lãi / hoà vốn / bán lỗ cho nhân viên Sales");
        req.setAttribute("currentPage",  "business-config");
        req.setAttribute("contentPage", "/WEB-INF/views/business/business-config.jsp");

        req.getRequestDispatcher("/WEB-INF/views/layout/dashboard-layout.jsp")
           .forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        if (!isManager(req)) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Chỉ Manager mới được thay đổi cấu hình.");
            return;
        }

        try {
            BigDecimal marginLow      = parseDecimal(req.getParameter("marginLow"));
            BigDecimal marginBreakeven = parseDecimal(req.getParameter("marginBreakeven"));
            BigDecimal marginLoss     = parseDecimal(req.getParameter("marginLoss"));

            if (marginLow == null || marginBreakeven == null || marginLoss == null) {
                setFlashError(req, "Vui lòng nhập đầy đủ 3 ngưỡng bằng số.");
                redirect(resp, "/business/config");
                return;
            }

            Integer updatedBy = getCurrentUserId(req);
            boolean ok = pricingConfigService.updatePricing(marginLow, marginBreakeven, marginLoss, updatedBy);

            if (ok) {
                setFlashSuccess(req, "Cập nhật ngưỡng cảnh báo thành công!");
            } else {
                setFlashError(req, "Lỗi khi lưu cấu hình. Vui lòng thử lại.");
            }
        } catch (Exception e) {
            setFlashError(req, "Dữ liệu không hợp lệ: " + e.getMessage());
        }

        redirect(resp, "/business/config");
    }

    private boolean isManager(HttpServletRequest req) {
        Object user = req.getSession(false) != null
                ? req.getSession(false).getAttribute("loggedInUser")
                : null;
        if (user instanceof User) {
            String role = ((User) user).getRole();
            return "MANAGER".equalsIgnoreCase(role);
        }
        return false;
    }

    private Integer getCurrentUserId(HttpServletRequest req) {
        try {
            Object user = req.getSession(false) != null
                    ? req.getSession(false).getAttribute("loggedInUser")
                    : null;
            if (user instanceof User) {
                return ((User) user).getUserId();
            }
        } catch (Exception ignored) {}
        return null;
    }

    private BigDecimal parseDecimal(String val) {
        if (val == null || val.trim().isEmpty()) return null;
        try {
            return new BigDecimal(val.trim().replace(",", "."));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
