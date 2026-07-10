package com.wms.controller.lazada;

import com.wms.dao.OrderDAO;
import com.wms.model.Order;
import com.wms.service.lazada.LazadaShipmentService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.logging.Logger;

/**
 * LazadaLabelServlet — streams the Base64-decoded PDF shipping label
 * Lazada returned from /order/package/document/get.
 *
 * <p>URL: {@code GET /lazada/label?orderCode=...}
 * <p>After successfully streaming, marks is_label_printed = 1 so the UI
 * knows the label has been printed and can unlock the "Xuất kho" button.
 */
@WebServlet(name = "LazadaLabelServlet", urlPatterns = {"/lazada/label"})
public class LazadaLabelServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(LazadaLabelServlet.class.getName());

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String orderCode = req.getParameter("orderCode");
        if (orderCode == null || orderCode.isBlank()) {
            sendErrorPage(resp, 400, "Thiếu orderCode");
            return;
        }
        orderCode = orderCode.trim();

        OrderDAO orderDAO = new OrderDAO();
        Order o = orderDAO.findByOrderCode(orderCode);
        if (o == null) {
            sendErrorPage(resp, 404, "Không tìm thấy đơn hàng: " + orderCode);
            return;
        }

        // Lazada requires package_id to fetch the AWB label
        String packageId = o.getLazadaPackageId();
        if (packageId == null || packageId.isBlank()) {
            sendErrorPage(resp, 409,
                "Đơn " + orderCode + " chưa được cấp mã. Hãy bấm \"Cấp mã & in tem\" trước.");
            return;
        }

        byte[] pdf;
        try {
            pdf = new LazadaShipmentService().getShippingLabel(o);
        } catch (Exception e) {
            LOGGER.warning("getShippingLabel error for " + orderCode + ": " + e.getMessage());
            sendErrorPage(resp, 502, "Lỗi khi lấy tem từ Lazada: " + e.getMessage());
            return;
        }

        if (pdf == null || pdf.length == 0) {
            sendErrorPage(resp, 502,
                "Lazada không trả về dữ liệu tem cho đơn " + orderCode + ". Thử lại sau.");
            return;
        }

        // Stream PDF inline — browser opens print dialog
        resp.setContentType("application/pdf");
        resp.setHeader("Content-Disposition",
                "inline; filename=\"lazada-label-" + orderCode + ".pdf\"");
        resp.setContentLength(pdf.length);
        resp.setHeader("Cache-Control", "no-cache, no-store");
        try (OutputStream out = resp.getOutputStream()) {
            out.write(pdf);
            out.flush();
        }

        // Mark label as printed so the UI unlocks "Xuất kho"
        try {
            orderDAO.markLabelPrinted(orderCode);
            LOGGER.info("Label printed for orderCode=" + orderCode);
        } catch (Exception e) {
            LOGGER.warning("markLabelPrinted failed for " + orderCode + ": " + e.getMessage());
        }
    }

    private void sendErrorPage(HttpServletResponse resp, int status, String msg) throws IOException {
        resp.setStatus(status);
        resp.setContentType("text/html;charset=UTF-8");
        try (PrintWriter w = resp.getWriter()) {
            w.println("<!DOCTYPE html><html><head><meta charset='UTF-8'/>"
                + "<title>Lỗi in tem Lazada</title></head>"
                + "<body style='font-family:sans-serif;padding:40px;color:#991b1b'>"
                + "<h2>&#9888; Lỗi in tem Lazada</h2>"
                + "<p>" + msg + "</p>"
                + "<button onclick='window.close()' style='margin-top:16px;padding:8px 18px;"
                + "border:none;background:#1d4ed8;color:#fff;border-radius:6px;"
                + "cursor:pointer;font-size:14px'>Đóng cửa sổ</button>"
                + "</body></html>");
        }
    }
}
