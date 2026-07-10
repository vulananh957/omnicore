package com.wms.controller.sales;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.dao.ChannelDAO;
import com.wms.dao.LazadaShipmentProviderDAO;
import com.wms.model.Channel;
import com.wms.model.LazadaShipmentProvider;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * LazadaShipmentProviderServlet — Returns the list of Lazada-recognised shipment
 * providers (from local DB, refreshed from Lazada API on-demand).
 *
 * <p>GET  /sales/shipment-providers
 *   → refreshes from Lazada API, returns JSON list of providers
 *
 * <p>The providers are also embedded in the page on GET /sales/order-processing
 * (via {@link com.wms.controller.sales.SalesOrderProcessingServlet}) so the
 * dropdown is always available without an extra fetch.
 */
public class LazadaShipmentProviderServlet extends com.wms.controller.BaseController {


    private final LazadaShipmentProviderDAO providerDAO = new LazadaShipmentProviderDAO();
    private final ChannelDAO channelDAO = new ChannelDAO();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json;charset=UTF-8");

        // Find any active Lazada channel to use for the API call
        Channel channel = findActiveLazadaChannel();
        if (channel == null) {
            writeJson(resp, Map.of(
                    "success", false,
                    "message", "Không tìm thấy kênh Lazada đang hoạt động. Vui lòng cấu hình kênh Lazada trước."
            ));
            return;
        }

        // Return cached providers from DB (populated by the sync scheduler)
        List<LazadaShipmentProvider> providers = providerDAO.findAllActive();

        if (providers.isEmpty()) {
            providers = providerDAO.findAll();
        }

        List<Map<String, Object>> data = providers.stream()
                .map(p -> Map.<String, Object>of(
                        "providerCode", p.getProviderCode() != null ? p.getProviderCode() : "",
                        "providerName", p.getProviderName() != null ? p.getProviderName() : "",
                        "providerNameVn", p.getProviderNameVn() != null ? p.getProviderNameVn() : "",
                        "displayOrder", p.getDisplayOrder()))
                .toList();

        writeJson(resp, Map.of(
                "success", true,
                "providers", data
        ));
    }

    private Channel findActiveLazadaChannel() {
        List<Channel> all = channelDAO.findAll();
        for (Channel c : all) {
            if ("Lazada".equalsIgnoreCase(c.getChannelName())
                    && c.getAccessToken() != null
                    && !c.getAccessToken().trim().isEmpty()) {
                return c;
            }
        }
        return null;
    }

    private void writeJson(HttpServletResponse resp, Map<String, Object> data) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        try (PrintWriter w = resp.getWriter()) {
            w.print(objectMapper.writeValueAsString(data));
        }
    }
}
