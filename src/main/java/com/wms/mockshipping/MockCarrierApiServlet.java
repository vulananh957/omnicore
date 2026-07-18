package com.wms.mockshipping;

import com.wms.controller.api.BaseApiServlet;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GET /api/website/mock-carriers — omnicore-web calls this at checkout to know whether mock
 * shipping is on, and if so, which fake carriers + fees to offer. HMAC-authenticated like the
 * other /api/website/* endpoints (see BaseApiServlet). Returns carriers:[] when disabled so
 * the storefront can just check "enabled" without a second branch.
 */
@WebServlet("/api/website/mock-carriers")
public class MockCarrierApiServlet extends BaseApiServlet {

    private final MockShippingService service = new MockShippingService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        if (authenticateAndReadBody(req, resp) == null) return;

        boolean enabled = service.isEnabled();
        List<Map<String, Object>> carriers = enabled
                ? service.findActiveCarriers().stream().map(c -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", c.getCarrierId());
                    m.put("name", c.getCarrierName());
                    m.put("fee", c.getFee());
                    return (Map<String, Object>) m;
                  }).toList()
                : List.of();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enabled", enabled);
        data.put("carriers", carriers);
        sendJson(resp, HttpServletResponse.SC_OK, data);
    }
}
