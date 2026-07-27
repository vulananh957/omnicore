package com.wms.controller.warehouse;

import com.wms.controller.BaseController;
import com.wms.dao.TransferDAO;
import com.wms.model.Product;
import com.wms.model.User;
import com.wms.model.Warehouse;
import com.wms.service.warehouse.TransferService;
import com.wms.service.common.NotificationService;
import com.wms.util.AppConstants;
import com.wms.util.JsonUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * WarehouseTransferServlet — Handles Transfer Inventory (Điều chuyển kho) for the Warehouse Staff.
 *
 * Maps to /warehouse/transfer.
 */
public class WarehouseTransferServlet extends BaseController {

    private final TransferService transferService = new TransferService();
    private final NotificationService notificationService = new NotificationService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        int myWarehouseId = currentWarehouseId(req);
        req.setAttribute("MY_WAREHOUSE_ID", myWarehouseId);
        try {
            List<TransferDAO.Transfer> transfers = transferService.findByWarehouseId(myWarehouseId);
            req.setAttribute("transfers", transfers);

            Map<Integer, List<TransferDAO.TransferItem>> transferItemsMap = new HashMap<>();
            for (TransferDAO.Transfer t : transfers) {
                List<TransferDAO.TransferItem> items = transferService.findItemsByTransferId(t.getTransferId());
                transferItemsMap.put(t.getTransferId(), items);
            }
            req.setAttribute("transferItemsMap", transferItemsMap);

            req.setAttribute("products", transferService.findApprovedProducts());
            req.setAttribute("warehouses", transferService.findAllWarehouses());
        } catch (Exception e) {
            req.setAttribute("transfers", java.util.List.<TransferDAO.Transfer>of());
            req.setAttribute("transferItemsMap", new HashMap<Integer, List<TransferDAO.TransferItem>>());
            req.setAttribute("products", java.util.List.<Product>of());
            req.setAttribute("warehouses", java.util.List.<Warehouse>of());
        }

        req.setAttribute("pageTitle",    "Điều Chuyển Kho (Transfer Inventory)");
        req.setAttribute("pageSubtitle", "Điều phối hàng hóa nội bộ hoặc phân phối sang khu hàng hỏng/khiếu nại");
        req.setAttribute("currentPage",  "wh-transfer");

        req.setAttribute("contentPage", "/WEB-INF/views/transfer/warehouse-transfer.jsp");

        req.getRequestDispatcher("/WEB-INF/views/layout/warehouse-layout.jsp")
           .forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json; charset=UTF-8");

        try {
            var body = JsonUtil.getMapper().readTree(req.getInputStream());
            String action = body.has("action") ? body.get("action").asText() : "";

            if ("create".equals(action)) {
                int fromWarehouseId = body.get("fromWarehouseId").asInt();
                int toWarehouseId   = body.get("toWarehouseId").asInt();
                int myWarehouseId = currentWarehouseId(req);
                if (fromWarehouseId != myWarehouseId) {
                    resp.getWriter().write("{\"success\":false,\"message\":\"Bạn chỉ được phép tạo phiếu điều chuyển từ kho của mình.\"}");
                    return;
                }
                String sku  = body.get("sku").asText();
                int qty     = body.get("qty").asInt();
                String note = body.has("note") ? body.get("note").asText() : "";
 
                User currentUser = (User) req.getSession().getAttribute(AppConstants.SESSION_USER);
                int creatorId = currentUser != null ? currentUser.getUserId() : 1;
 
                TransferDAO.Transfer created =
                    transferService.createTransfer(fromWarehouseId, toWarehouseId,
                            creatorId, note, sku, BigDecimal.valueOf(qty));

                // Notify destination warehouse staff: incoming transfer
                String toWhName = transferService.findAllWarehouses().stream()
                        .filter(w -> w.getWarehouseId() == toWarehouseId)
                        .findFirst().map(Warehouse::getWarehouseName).orElse(String.valueOf(toWarehouseId));
                notificationService.notifyIncomingTransfer(toWarehouseId,
                        toWhName,
                        created.getTransferId(), created.getTransferCode(), 1);

                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("transferId", created.getTransferId());
                result.put("transferCode", created.getTransferCode());
                resp.getWriter().write(JsonUtil.toJson(result));

            } else if ("receive".equals(action)) {
                int transferId = body.get("transferId").asInt();
                TransferDAO.Transfer t = transferService.findById(transferId);
                int myWarehouseId = currentWarehouseId(req);
                if (t == null) {
                    resp.getWriter().write("{\"success\":false,\"message\":\"Không tìm thấy phiếu điều chuyển.\"}");
                    return;
                }
                if (t.getToWarehouseId() != myWarehouseId) {
                    resp.getWriter().write("{\"success\":false,\"message\":\"Bạn không thể xác nhận nhận hàng cho kho khác.\"}");
                    return;
                }
                User currentUser = (User) req.getSession().getAttribute(AppConstants.SESSION_USER);
                int userId = currentUser != null ? currentUser.getUserId() : 1;
                transferService.markReceived(transferId, userId);
                resp.getWriter().write("{\"success\":true}");

            } else {
                resp.getWriter().write("{\"success\":false,\"message\":\"Hành động không hợp lệ.\"}");
            }
        } catch (Exception e) {
            resp.getWriter().write("{\"success\":false,\"message\":\"Lỗi: " + e.getMessage() + "\"}");
        }
    }
}
