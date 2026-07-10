package com.wms.filter;

import com.wms.dao.InventoryDAO;
import com.wms.util.AppConstants;
import com.wms.model.User;
import com.wms.service.common.NotificationService;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * DefectiveStockFilter — Injects defective stock notification data into every
 * warehouse page request so the notification bell in the layout can display
 * a badge when defective goods are sitting in the warehouse.
 *
 * <p>Also auto-creates a database notification when the defective stock count
 * increases, so the WH staff and manager are alerted via the notification bell.</p>
 *
 * Runs before any /warehouse/* servlet. Sets:
 *   - request attr "defectiveStocks"  : List of items with stock_type=DEFECTIVE and qty_on_hand > 0
 *   - request attr "defectiveCount"   : int total count of defective SKU rows
 *   - request attr "defectiveTotal"  : int total units across all defective SKUs
 *
 * Only runs for WAREHOUSE_STAFF role scoped to their own warehouse.
 */
public class DefectiveStockFilter implements Filter {

    private static final Logger log = Logger.getLogger(DefectiveStockFilter.class.getName());
    private static final String SESSION_DEFECTIVE_COUNT = "_defectiveStockCount";

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {}

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpSession session = req.getSession(false);

        // Only inject for authenticated warehouse staff with a warehouse
        if (session != null) {
            Object userObj = session.getAttribute(AppConstants.SESSION_USER);
            Object whObj   = session.getAttribute(AppConstants.SESSION_WAREHOUSE);

            if (userObj instanceof User && whObj instanceof Integer) {
                User user = (User) userObj;
                int warehouseId = (Integer) whObj;

                // Skip AJAX / non-GET requests to avoid unnecessary DB hits
                String method = req.getMethod();
                String accept = req.getHeader("Accept");
                boolean isAjax = (accept != null && accept.toLowerCase().contains("application/json"))
                        || "XMLHttpRequest".equals(req.getHeader("X-Requested-With"));

                if ("GET".equalsIgnoreCase(method) && !isAjax) {
                    try {
                        InventoryDAO invDAO = new InventoryDAO();
                        List<Map<String, Object>> invSummary = invDAO.findInventorySummaryByWarehouse(warehouseId);
                        List<Map<String, Object>> defectiveStocks = new ArrayList<>();
                        int defectiveTotal = 0;

                        if (invSummary != null) {
                            for (Map<String, Object> row : invSummary) {
                                if ("DEFECTIVE".equals(row.get("stockType"))) {
                                    Object qty = row.get("qtyOnHand");
                                    if (qty instanceof BigDecimal && ((BigDecimal) qty).compareTo(BigDecimal.ZERO) > 0) {
                                        defectiveStocks.add(row);
                                        defectiveTotal += ((BigDecimal) qty).intValue();
                                    }
                                }
                            }
                        }

                        req.setAttribute("defectiveStocks", defectiveStocks);
                        req.setAttribute("defectiveCount", defectiveStocks.size());
                        req.setAttribute("defectiveTotal", defectiveTotal);

                        // Auto-create notification when defective count increases
                        int currentCount = defectiveStocks.size();
                        Integer previousCount = (Integer) session.getAttribute(SESSION_DEFECTIVE_COUNT);
                        if (currentCount > 0 && (previousCount == null || currentCount > previousCount)) {
                            session.setAttribute(SESSION_DEFECTIVE_COUNT, currentCount);
                            String whName = user.getWarehouseId() > 0
                                    ? String.valueOf(user.getWarehouseId())
                                    : String.valueOf(warehouseId);
                            NotificationService ns = new NotificationService();
                            ns.notifyDefectiveStock(warehouseId, whName, currentCount, defectiveTotal);
                            log.info("DefectiveStockFilter: Defective stock alert sent for warehouseId=" + warehouseId + " count=" + currentCount);
                        } else if (currentCount == 0 && previousCount != null && previousCount > 0) {
                            // Defective stock cleared — update session so next appearance triggers again
                            session.setAttribute(SESSION_DEFECTIVE_COUNT, 0);
                        } else if (previousCount == null) {
                            session.setAttribute(SESSION_DEFECTIVE_COUNT, currentCount);
                        }
                    } catch (Exception e) {
                        log.warning("DefectiveStockFilter: Failed to load defective stocks: " + e.getMessage());
                        req.setAttribute("defectiveStocks", new ArrayList<>());
                        req.setAttribute("defectiveCount", 0);
                        req.setAttribute("defectiveTotal", 0);
                    }
                }
            }
        }

        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {}
}
