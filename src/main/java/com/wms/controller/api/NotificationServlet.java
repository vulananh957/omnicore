package com.wms.controller.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wms.controller.BaseController;
import com.wms.model.Notification;
import com.wms.service.common.NotificationService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * NotificationServlet — REST API endpoint for the notification bell.
 *
 * <p>Base path: /api/notifications</p>
 *
 * <p>Endpoints:</p>
 * <ul>
 *   <li>GET  /api/notifications           — returns list + unread count as JSON</li>
 *   <li>GET  /api/notifications/count     — returns unread count only</li>
 *   <li>POST /api/notifications/{id}/read  — marks one notification as read</li>
 *   <li>POST /api/notifications/read-all   — marks all for current user as read</li>
 * </ul>
 */
public class NotificationServlet extends BaseController {

    private final NotificationService notificationService = new NotificationService();
    private final ObjectMapper objectMapper;

    public NotificationServlet() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String pathInfo = req.getPathInfo();

        if (pathInfo != null && pathInfo.startsWith("/count")) {
            serveCount(req, resp);
        } else {
            serveList(req, resp);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String pathInfo = req.getPathInfo();

        if (pathInfo != null && pathInfo.matches("/\\d+/read")) {
            long id = parseId(pathInfo);
            HttpSession session = req.getSession(false);
            if (id > 0 && session != null) {
                notificationService.markAsRead(id, session);
            }
            int unreadCount = 0;
            if (session != null) {
                unreadCount = notificationService.getUnreadCountForSession(session);
            }
            writeJson(resp, Map.of("success", true, "id", id, "unreadCount", unreadCount));

        } else if (pathInfo != null && pathInfo.equals("/read-all")) {
            HttpSession session = req.getSession(false);
            if (session != null) {
                int count = notificationService.markAllAsReadForSession(session);
                writeJson(resp, Map.of("success", true, "markedCount", count));
            } else {
                writeJson(resp, Map.of("success", false, "error", "No session"));
            }

        } else {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    // ── Handlers ───────────────────────────────────────────────────

    private void serveList(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession session = req.getSession(false);
        if (session == null) {
            writeJson(resp, Map.of("notifications", List.of(), "unreadCount", 0));
            return;
        }

        String limitStr = req.getParameter("limit");
        int limit = 20;
        if (limitStr != null) {
            try { limit = Math.min(Integer.parseInt(limitStr), 50); } catch (NumberFormatException ignored) {}
        }

        List<Notification> notifications = notificationService.getNotificationsForSession(session, limit);
        int unreadCount = notificationService.getUnreadCountForSession(session);

        Map<String, Object> out = new HashMap<>();
        out.put("notifications", notifications);
        out.put("unreadCount", unreadCount);
        out.put("success", true);
        writeJson(resp, out);
    }

    private void serveCount(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession session = req.getSession(false);
        int count = 0;
        if (session != null) {
            count = notificationService.getUnreadCountForSession(session);
        }
        writeJson(resp, Map.of("unreadCount", count));
    }

    // ── Helpers ───────────────────────────────────────────────────

    private long parseId(String pathInfo) {
        String[] parts = pathInfo.split("/");
        if (parts.length >= 2) {
            try { return Long.parseLong(parts[1]); } catch (NumberFormatException ignored) {}
        }
        return -1;
    }

    private void writeJson(HttpServletResponse resp, Object data) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        resp.getWriter().write(objectMapper.writeValueAsString(data));
    }
}
