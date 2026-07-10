package com.wms.controller.warehouse;

import com.wms.controller.BaseController;
import com.wms.dao.UserDAO;
import com.wms.model.User;
import com.wms.model.Warehouse;
import com.wms.service.warehouse.WarehouseService;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * WarehouseInfoServlet — Warehouse information for the Warehouse Staff.
 *
 * Maps to /warehouse/information. Shows the staff's own warehouse (read-only master data)
 * and provides CRUD over its storage zones. Warehouse master data (code/name/address) stays
 * with the Manager screen (/business/warehouses).
 */
public class WarehouseInfoServlet extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(WarehouseInfoServlet.class);
    private static final String CONTEXT_PATH = "/warehouse/information";
    private final WarehouseService warehouseService = new WarehouseService();
    private final UserDAO userDAO = new UserDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        consumeFlash(req);

        int warehouseId = currentWarehouseId(req);
        log.info("[WarehouseInfo] warehouseId={}", warehouseId);
        try {
            Warehouse warehouse = warehouseService.findById(warehouseId);
            req.setAttribute("warehouse", warehouse);
            req.setAttribute("dashboardMetrics", warehouseService.getDashboardMetrics(warehouseId));

            List<User> staff = userDAO.findByWarehouseId(warehouseId);
            log.info("[WarehouseInfo] staff.size={}", staff.size());
            for (User s : staff) {
                log.info("[WarehouseInfo] staff: id={} name={} warehouseId={}",
                    s.getUserId(), s.getFullName(), s.getWarehouseId());
            }
            req.setAttribute("warehouseStaff", staff);
        } catch (Exception e) {
            log.warn("[WarehouseInfo] error", e);
            req.setAttribute("warehouse", null);
            req.setAttribute("dashboardMetrics",
                new com.wms.model.DashboardMetrics(0, 0, 0));
            req.setAttribute("warehouseStaff", List.of());
        }

        req.setAttribute("pageTitle",    "Thông Tin Kho");
        req.setAttribute("pageSubtitle", "Thông tin kho bạn phụ trách và quản lý các phân khu lưu trữ");
        req.setAttribute("currentPage",  "wh-information");
        req.setAttribute("contentPage",  "/WEB-INF/views/warehouse/warehouse-information.jsp");

        req.getRequestDispatcher("/WEB-INF/views/layout/warehouse-layout.jsp")
           .forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        req.setCharacterEncoding("UTF-8");
        int warehouseId = currentWarehouseId(req);
        String action = req.getParameter("action");

        WarehouseService.SaveResult result;
        try {
            if ("createZone".equals(action)) {
                String capStr = req.getParameter("capacity");
                Integer capacity = (capStr != null && !capStr.trim().isEmpty()) ? Integer.parseInt(capStr.trim()) : null;
                result = warehouseService.createZone(warehouseId,
                        null, req.getParameter("zoneName"),
                        req.getParameter("zoneType"), req.getParameter("description"), capacity);
            } else if ("updateZone".equals(action)) {
                int zoneId = Integer.parseInt(req.getParameter("zoneId").trim());
                String capStr = req.getParameter("capacity");
                Integer capacity = (capStr != null && !capStr.trim().isEmpty()) ? Integer.parseInt(capStr.trim()) : null;
                result = warehouseService.updateZone(zoneId, warehouseId,
                        req.getParameter("zoneName"), req.getParameter("zoneType"),
                        req.getParameter("description"), capacity);
            } else if ("deleteZone".equals(action)) {
                int zoneId = Integer.parseInt(req.getParameter("zoneId").trim());
                result = warehouseService.deleteZone(zoneId, warehouseId);
            } else {
                result = WarehouseService.SaveResult.failure("Hành động không hợp lệ.");
            }
        } catch (Exception e) {
            result = WarehouseService.SaveResult.failure("Dữ liệu không hợp lệ: " + e.getMessage());
        }

        if (result.isSuccess()) {
            setFlashSuccess(req, "Cập nhật phân khu thành công.");
        } else {
            setFlashError(req, result.getMessage());
        }
        redirect(resp, req.getContextPath() + CONTEXT_PATH);
    }
}
