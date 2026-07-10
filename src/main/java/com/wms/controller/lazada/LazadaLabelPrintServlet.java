package com.wms.controller.lazada;

import com.wms.dao.LazadaOrderDAO;
import com.wms.model.LazadaOrder;
import com.wms.service.channel.ChannelRegistry;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * LazadaLabelPrintServlet — Fetches and streams a Lazada shipping label PDF.
 *
 * <p>GET /lazada/print-label?lazadaOrderIdStr=ORDER_ID</p>
 *
 * <p>Flow:</p>
 * <ol>
 *   <li>Loads the LazadaOrder from DB to get package_id</li>
 *   <li>Calls LazadaChannelGateway.downloadShippingLabelPdf(channel, packageId)</li>
 *   <li>Streams the raw PDF bytes back to the browser</li>
 * </ol>
 */
public class LazadaLabelPrintServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(LazadaLabelPrintServlet.class.getName());

    private final LazadaOrderDAO orderDAO = new LazadaOrderDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String lazadaOrderIdStr = req.getParameter("lazadaOrderIdStr");
        if (lazadaOrderIdStr == null || lazadaOrderIdStr.isBlank()) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Thiếu tham số lazadaOrderIdStr");
            return;
        }

        LazadaOrder order = orderDAO.findByLazadaOrderIdStr(lazadaOrderIdStr);
        if (order == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Không tìm thấy đơn hàng: " + lazadaOrderIdStr);
            return;
        }

        String packageId = order.getPackageId();
        if (packageId == null || packageId.isBlank()) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST,
                    "Đơn hàng chưa được đóng gói (chưa có package_id). Vui lòng gọi [Đóng gói] trước.");
            return;
        }

        // Load channel to get credentials
        com.wms.dao.ChannelDAO channelDAO = new com.wms.dao.ChannelDAO();
        var channel = channelDAO.findById(order.getChannelId());
        if (channel == null) {
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Không tìm thấy cấu hình kênh.");
            return;
        }

        try {
            if (!(ChannelRegistry.get("Lazada")
                    instanceof com.wms.service.channel.LazadaChannelGateway lcg)) {
                resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        "Không tìm được Lazada gateway.");
                return;
            }

            byte[] pdfBytes = lcg.downloadShippingLabelPdf(channel, packageId);
            if (pdfBytes == null || pdfBytes.length == 0) {
                resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        "Lazada không trả về nội dung PDF nhãn.");
                return;
            }

            resp.setContentType("application/pdf");
            resp.setContentLength(pdfBytes.length);
            resp.setHeader("Content-Disposition",
                    "inline; filename=\"lazada-awb-" + lazadaOrderIdStr + ".pdf\"");

            try (OutputStream os = resp.getOutputStream()) {
                os.write(pdfBytes);
                os.flush();
            }

            LOGGER.info("LazadaLabelPrintServlet: streamed AWB PDF for " + lazadaOrderIdStr
                    + " size=" + pdfBytes.length);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE,
                    "LazadaLabelPrintServlet: failed for order " + lazadaOrderIdStr, e);
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Lỗi khi lấy nhãn vận chuyển: " + e.getMessage());
        }
    }
}
