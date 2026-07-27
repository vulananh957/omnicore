package com.wms.controller.warehouse;

import com.wms.controller.BaseController;
import com.wms.dao.FulfillmentRequestDAO;
import com.wms.model.FulfillmentRequest;
import com.wms.util.JsonUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FulfillmentRequestServlet — Handles AJAX API for fulfillment requests.
 *
 * URL patterns:
 *   GET  /warehouse/fulfillment        → list PENDING requests as JSON
 *   POST /warehouse/fulfillment?action=seed     → seed 2 test requests
 *   POST /warehouse/fulfillment?action=cleanup  → delete test requests
 */
public class FulfillmentRequestServlet extends BaseController {

    private final FulfillmentRequestDAO dao = new FulfillmentRequestDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        int warehouseId = currentWarehouseId(req);
        List<FulfillmentRequest> list = dao.findPendingByWarehouse(warehouseId);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", list);
        response.put("count", list.size());

        writeJson(resp, JsonUtil.toJson(response));
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        req.setCharacterEncoding("UTF-8");
        String action = req.getParameter("action");

        Map<String, Object> response = new HashMap<>();

        if ("seed".equals(action)) {
            dao.seedTestData();
            List<FulfillmentRequest> list = dao.findPending();
            response.put("success", true);
            response.put("message", "Đã seed 2 yêu cầu xuất hàng test vào database.");
            response.put("data", list);
            response.put("count", list.size());
            writeJson(resp, JsonUtil.toJson(response));
            return;
        }

        if ("convert".equals(action)) {
            String requestId = req.getParameter("requestId");
            if (requestId == null || requestId.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "Thiếu requestId.");
                writeJson(resp, JsonUtil.toJson(response));
                return;
            }
            requestId = requestId.trim();
            FulfillmentRequest fr = dao.findById(requestId);
            if (fr == null) {
                response.put("success", false);
                response.put("message", "Không tìm thấy yêu cầu xuất hàng: " + requestId);
                writeJson(resp, JsonUtil.toJson(response));
                return;
            }
            int warehouseId = currentWarehouseId(req);
            if (fr.getWarehouseId() != warehouseId) {
                response.put("success", false);
                response.put("message", "Bạn không có quyền chuyển đổi yêu cầu xuất hàng của kho khác.");
                writeJson(resp, JsonUtil.toJson(response));
                return;
            }
            boolean updated = dao.updateStatus(requestId, FulfillmentRequest.STATUS_CONVERTED);
            if (updated) {
                Integer currentUserId = currentUserId(req);
                String currentRole = currentUserRole(req);
                new com.wms.service.warehouse.OutboundService().autoCreateFromOrder(
                        fr.getOrderId(), fr.getWarehouseId(),
                        currentUserId != null ? currentUserId : 1,
                        currentRole != null ? currentRole : "");
                response.put("success", true);
                response.put("message", "Đã chuyển yêu cầu " + requestId + " thành CONVERTED và tạo phiếu xuất.");
            } else {
                response.put("success", false);
                response.put("message", "Không thể cập nhật trạng thái yêu cầu.");
            }
            writeJson(resp, JsonUtil.toJson(response));
            return;
        }

        if ("cleanup".equals(action)) {
            dao.deleteTestData();
            response.put("success", true);
            response.put("message", "Đã xóa dữ liệu test.");
            writeJson(resp, JsonUtil.toJson(response));
            return;
        }

        response.put("success", false);
        response.put("message", "Hành động không hợp lệ: " + action);
        writeJson(resp, JsonUtil.toJson(response));
    }
}
