package com.wms.controller.api;

import com.wms.service.common.PricingConfigService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;

/**
 * ConfigPricingApiServlet — JSON endpoint cho pricing thresholds.
 *
 * <p>PUBLIC (không cần đăng nhập) vì chỉ trả về ngưỡng số,
 * không có dữ liệu nhạy cảm.</p>
 *
 * GET /api/config/pricing → {"success":true,"thresholds":{...}}
 */
public class ConfigPricingApiServlet extends HttpServlet {

    private final PricingConfigService pricingConfigService = new PricingConfigService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        try {
            Map<String, BigDecimal> t = pricingConfigService.getPricing();

            String json = "{"
                + "\"success\":true,"
                + "\"thresholds\":{"
                + "\"pricing.warn_margin_low\":\"" + t.get("pricing.warn_margin_low") + "\","
                + "\"pricing.warn_margin_breakeven\":\"" + t.get("pricing.warn_margin_breakeven") + "\","
                + "\"pricing.warn_margin_loss_threshold\":\"" + t.get("pricing.warn_margin_loss_threshold") + "\""
                + "}}";

            resp.getWriter().print(json);
        } catch (Exception e) {
            resp.setStatus(500);
            resp.getWriter().print("{\"success\":false,\"message\":\"Internal error\"}");
        }
    }
}
