package com.wms.mockshipping;

import com.wms.dao.OrderDAO;
import com.wms.dao.OutboundDAO;
import com.wms.model.Order;
import com.wms.model.OutboundItem;
import com.wms.model.OutboundOrder;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * MockShippingLabelServlet — renders a printable HTML waybill for Website orders using the
 * mock carrier (com.wms.mockshipping). Mirrors LazadaLabelServlet's URL/print/lock pattern
 * (GET /mockshipping/label?orderCode=..., marks is_label_printed=1 after rendering so the
 * warehouse UI unlocks "Xác nhận đóng gói") but generates its own HTML instead of streaming
 * a real courier's PDF — there is no real courier for a mock carrier.
 */
@WebServlet(name = "MockShippingLabelServlet", urlPatterns = {"/mockshipping/label"})
public class MockShippingLabelServlet extends HttpServlet {

    private final OrderDAO orderDAO = new OrderDAO();
    private final OutboundDAO outboundDAO = new OutboundDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String orderCode = req.getParameter("orderCode");
        if (orderCode == null || orderCode.isBlank()) {
            sendErrorPage(resp, 400, "Thiếu orderCode");
            return;
        }
        orderCode = orderCode.trim();

        int outboundId = outboundDAO.findActiveOutboundIdByOrderCode(orderCode);
        OutboundOrder o = outboundId > 0 ? outboundDAO.findById(outboundId) : null;
        if (o == null) {
            sendErrorPage(resp, 404, "Không tìm thấy phiếu xuất cho đơn: " + orderCode);
            return;
        }
        if (o.getTrackingNo() == null || o.getTrackingNo().isBlank()) {
            sendErrorPage(resp, 409, "Đơn " + orderCode + " chưa có mã vận đơn. Hãy bấm \"Bắt đầu đóng gói\" trước.");
            return;
        }

        resp.setContentType("text/html;charset=UTF-8");
        try (PrintWriter w = resp.getWriter()) {
            w.println("<!DOCTYPE html><html lang='vi'><head><meta charset='UTF-8'/>");
            w.println("<title>Tem vận đơn (mock) — " + esc(orderCode) + "</title>");
            w.println("<style>");
            w.println("body{font-family:Arial,sans-serif;padding:24px;color:#10375C;max-width:420px;margin:0 auto}");
            w.println(".label{border:2px dashed #10375C;border-radius:12px;padding:20px}");
            w.println(".carrier{font-size:20px;font-weight:800;margin-bottom:4px}");
            w.println(".mock-tag{display:inline-block;background:#FEF3C7;color:#92400e;font-size:11px;font-weight:700;padding:2px 8px;border-radius:6px;margin-bottom:12px}");
            w.println(".waybill{font-size:22px;font-weight:800;letter-spacing:1px;background:#F0F4FA;padding:10px;border-radius:8px;text-align:center;margin-bottom:16px}");
            w.println("table{width:100%;border-collapse:collapse;font-size:13px}");
            w.println("td{padding:4px 0;vertical-align:top}");
            w.println("td.k{color:rgba(16,55,92,0.55);width:110px}");
            w.println(".items{margin-top:12px;border-top:1px solid #E5EAF3;padding-top:8px;font-size:12px}");
            w.println("button{margin-top:20px;padding:10px 20px;background:#EB8317;color:#fff;border:none;border-radius:8px;cursor:pointer;font-size:14px;font-weight:600}");
            w.println("@media print{button{display:none}}");
            w.println("</style></head><body>");
            w.println("<div class='label'>");
            Order salesOrder = orderDAO.findByOrderCode(orderCode);
            String carrierName = (salesOrder != null && salesOrder.getShipmentProvider() != null
                    && !salesOrder.getShipmentProvider().isBlank())
                    ? salesOrder.getShipmentProvider() : "Vận chuyển (mock)";

            w.println("<div class='mock-tag'>MOCK — không phải vận đơn thật</div>");
            w.println("<div class='carrier'>" + esc(carrierName) + "</div>");
            w.println("<div class='waybill'>" + esc(o.getTrackingNo()) + "</div>");
            w.println("<table>");
            w.println(row("Mã đơn hàng", o.getOrderCode()));
            w.println(row("Người nhận", o.getRecipientName()));
            w.println(row("Địa chỉ", o.getShippingAddress()));
            w.println("</table>");
            w.println("<div class='items'>");
            if (o.getItems() != null) {
                for (OutboundItem item : o.getItems()) {
                    w.println("<div>" + esc(item.getSkuCode()) + " — " + esc(item.getSkuName())
                            + " x" + item.getQty() + "</div>");
                }
            }
            w.println("</div>");
            w.println("</div>");
            w.println("<button onclick='window.print()'>In tem</button>");
            w.println("</body></html>");
        }

        try {
            orderDAO.markLabelPrinted(orderCode);
        } catch (Exception e) {
            // non-fatal — label already rendered, UI will just show "In tem" again next load
        }
    }

    private String row(String label, String value) {
        return "<tr><td class='k'>" + esc(label) + "</td><td>" + esc(value != null ? value : "—") + "</td></tr>";
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private void sendErrorPage(HttpServletResponse resp, int status, String msg) throws IOException {
        resp.setStatus(status);
        resp.setContentType("text/html;charset=UTF-8");
        try (PrintWriter w = resp.getWriter()) {
            w.println("<!DOCTYPE html><html><head><meta charset='UTF-8'/>"
                + "<title>Lỗi in tem</title></head>"
                + "<body style='font-family:sans-serif;padding:40px;color:#991b1b'>"
                + "<h2>&#9888; Lỗi in tem vận đơn</h2>"
                + "<p>" + esc(msg) + "</p>"
                + "<button onclick='window.close()' style='margin-top:16px;padding:8px 18px;"
                + "border:none;background:#1d4ed8;color:#fff;border-radius:6px;"
                + "cursor:pointer;font-size:14px'>Đóng cửa sổ</button>"
                + "</body></html>");
        }
    }
}
