package com.wms.controller;

import com.wms.util.AppConstants;
import com.wms.util.JsonUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * BaseController — Abstract base class for ALL WMS Servlets.
 *
 * ┌─────────────────────────────────────────────────────────────────────┐
 * │  REACT → JSP MAPPING                                               │
 * │  React Component  →  Servlet + JSP pair                           │
 * │  useNavigate()    →  response.sendRedirect(...)                    │
 * │  useState()       →  request.setAttribute(...) [per-request]      │
 * │  useContext()     →  session.getAttribute(...) [cross-request]     │
 * │  props            →  request.getAttribute(...) passed to JSP       │
 * └─────────────────────────────────────────────────────────────────────┘
 */
public abstract class BaseController extends HttpServlet {

    // ── Navigation helpers (replaces React Router) ────────────

    /**
     * Forward to a JSP view inside WEB-INF/views/.
     * e.g., forward(req, res, "dashboard/index") →  /WEB-INF/views/dashboard/index.jsp
     */
    protected void forward(HttpServletRequest req, HttpServletResponse res, String viewPath)
            throws ServletException, IOException {
        String fullPath = AppConstants.VIEW_PREFIX + viewPath + AppConstants.VIEW_SUFFIX;
        req.getRequestDispatcher(fullPath).forward(req, res);
    }

    /**
     * Redirect to a servlet URL (POST-Redirect-GET pattern).
     * e.g., redirect(res, "dashboard")
     */
    protected void redirect(HttpServletResponse res, String servletPath) throws IOException {
        res.sendRedirect(res.encodeRedirectURL(
                res.encodeURL(servletPath)));
    }

    // ── Attribute helpers (replaces React state/props) ────────

    protected void setData(HttpServletRequest req, Object data) {
        req.setAttribute(AppConstants.ATTR_DATA, data);
    }

    protected void setList(HttpServletRequest req, Object list) {
        req.setAttribute(AppConstants.ATTR_LIST, list);
    }

    protected void setPageTitle(HttpServletRequest req, String title) {
        req.setAttribute(AppConstants.ATTR_PAGE, title);
    }

    protected void setError(HttpServletRequest req, String message) {
        req.setAttribute(AppConstants.ATTR_ERROR, message);
    }

    protected void setSuccess(HttpServletRequest req, String message) {
        req.setAttribute(AppConstants.ATTR_SUCCESS, message);
    }

    protected void setInfo(HttpServletRequest req, String message) {
        req.setAttribute(AppConstants.ATTR_INFO, message);
    }

    // ── Session-based flash messages (survive POST-Redirect-GET) ──

    /**
     * Store an error flash message in the session so it survives a redirect.
     * Call consumeFlash() in doGet to move it to request scope.
     */
    protected void setFlashError(HttpServletRequest req, String message) {
        HttpSession session = req.getSession(true);
        session.setAttribute(AppConstants.ATTR_ERROR, message);
    }

    /**
     * Store a success flash message in the session so it survives a redirect.
     * Call consumeFlash() in doGet to move it to request scope.
     */
    protected void setFlashSuccess(HttpServletRequest req, String message) {
        HttpSession session = req.getSession(true);
        session.setAttribute(AppConstants.ATTR_SUCCESS, message);
    }

    protected void setFlashInfo(HttpServletRequest req, String message) {
        HttpSession session = req.getSession(true);
        session.setAttribute(AppConstants.ATTR_INFO, message);
    }

    /**
     * Move any session flash messages into the request scope and remove them.
     * Call this at the start of every doGet that may follow a redirect.
     */
    protected void consumeFlash(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        if (session == null) return;
        String err = (String) session.getAttribute(AppConstants.ATTR_ERROR);
        if (err != null) {
            req.setAttribute(AppConstants.ATTR_ERROR, err);
            session.removeAttribute(AppConstants.ATTR_ERROR);
        }
        String ok = (String) session.getAttribute(AppConstants.ATTR_SUCCESS);
        if (ok != null) {
            req.setAttribute(AppConstants.ATTR_SUCCESS, ok);
            session.removeAttribute(AppConstants.ATTR_SUCCESS);
        }
        String info = (String) session.getAttribute(AppConstants.ATTR_INFO);
        if (info != null) {
            req.setAttribute(AppConstants.ATTR_INFO, info);
            session.removeAttribute(AppConstants.ATTR_INFO);
        }
    }

    // ── Session helpers (replaces React Context) ───────────────

    protected Object getSessionAttr(HttpServletRequest req, String key) {
        HttpSession session = req.getSession(false);
        return (session != null) ? session.getAttribute(key) : null;
    }

    protected boolean isLoggedIn(HttpServletRequest req) {
        return getSessionAttr(req, AppConstants.SESSION_USER) != null;
    }

    // ── JSON response helper (for AJAX calls) ─────────────────

    protected void writeJson(HttpServletResponse res, String json) throws IOException {
        res.setContentType("application/json");
        res.setCharacterEncoding("UTF-8");
        try (PrintWriter out = res.getWriter()) {
            out.print(json);
        }
    }

    /**
     * Serialise an object to JSON and put it on the request as a String attribute
     * so the JSP can include it with EL: ${<name>Json}. Uses the shared
     * {@link JsonUtil} mapper so every endpoint has identical LocalDateTime /
     * UTF-8 / Vietnamese handling.
     */
    protected void setJsonAttr(HttpServletRequest req, String attrName, Object value) {
        req.setAttribute(attrName, JsonUtil.toJson(value));
    }

    /**
     * Deserialise a JSON string into the given type. Convenience wrapper
     * around {@link JsonUtil} so servlets don't have to import Jackson
     * types directly.
     */
    protected <T> T parseJson(String json, com.fasterxml.jackson.core.type.TypeReference<T> typeRef) {
        try {
            return JsonUtil.getMapper().readValue(json, typeRef);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse JSON: " + e.getMessage(), e);
        }
    }

    protected <T> T parseJson(String json, Class<T> clazz) {
        try {
            return JsonUtil.getMapper().readValue(json, clazz);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse JSON: " + e.getMessage(), e);
        }
    }

    protected String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    // ── Input validation helpers ───────────────────────────────

    protected boolean isNullOrEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    protected int getPageNumber(HttpServletRequest req) {
        try {
            String p = req.getParameter("page");
            return (p != null) ? Math.max(1, Integer.parseInt(p)) : 1;
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    /**
     * Read an int request parameter with a default fallback. Returns the
     * default for null, empty, or non-numeric values.
     */
    protected int getIntParam(HttpServletRequest req, String name, int defaultValue) {
        String v = req.getParameter(name);
        if (v == null || v.isEmpty()) return defaultValue;
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Read an int request parameter, returning null if missing or invalid.
     * Use this when the caller wants to distinguish "0" (a valid value)
     * from "not provided".
     */
    protected Integer getIntParamOrNull(HttpServletRequest req, String name) {
        String v = req.getParameter(name);
        if (v == null || v.isEmpty()) return null;
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    protected int currentWarehouseId(HttpServletRequest req) {
        Object u = req.getSession().getAttribute(AppConstants.SESSION_USER);
        if (u instanceof com.wms.model.User && ((com.wms.model.User) u).getWarehouseId() > 0) {
            return ((com.wms.model.User) u).getWarehouseId();
        }
        return 1;
    }

    protected Integer currentUserId(HttpServletRequest req) {
        Object u = req.getSession().getAttribute(AppConstants.SESSION_USER);
        if (u instanceof com.wms.model.User) {
            return ((com.wms.model.User) u).getUserId();
        }
        return null;
    }

    protected String currentUserRole(HttpServletRequest req) {
        Object u = req.getSession().getAttribute(AppConstants.SESSION_USER);
        if (u instanceof com.wms.model.User) {
            return ((com.wms.model.User) u).getRole();
        }
        return null;
    }
}
