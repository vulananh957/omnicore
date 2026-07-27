package com.wms.filter;

import com.wms.util.AppConstants;
import com.wms.dao.UserDAO;
import com.wms.model.User;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * AuthFilter — Session-based authentication + Role-based access control (RBAC).
 *
 * React equivalent: PrivateRoute / AuthGuard wrapper component + role-based redirect.
 *
 * Allows unauthenticated access to public paths (login, assets, etc.)
 * All other requests require:
 *   1. Valid session with SESSION_USER set
 *   2. Active user account
 *   3. Role allowed to access the requested URL prefix
 *
 * RBAC: if the user accesses a URL outside their role, returns 403.
 */
public class AuthFilter implements Filter {

    private static final Logger log = Logger.getLogger(AuthFilter.class.getName());

    /** Paths accessible WITHOUT login (React: public routes) */
    private static final Set<String> PUBLIC_PATHS = new HashSet<>(Arrays.asList(
            "/login",
            "/logout",
            "/otp",
            "/otp-verify",
            "/password-change-otp",
            "/forgot-password",
            "/reset-password",
            // Lazada end-to-end: webhooks fire from Lazada servers with no
            // session. Auth is enforced via channel-level signature instead
            // (verified in LazadaWebhookServlet once a secret is configured).
            "/lazada/webhook",
            "/webhook/lazada",
            // Pricing thresholds: public JSON endpoint — no sensitive data
            "/api/config/pricing",
            // Manual stock sync trigger — no sensitive data
            "/api/lazada/stock-sync",
            // Lazada order API: public JSON endpoint for internal JS fetch
            "/api/lazada/",
            "/trigger-sync.jsp"
    ));

    /** Map URL prefix → allowed roles. */
    private static final java.util.Map<String, Set<String>> PATH_ROLES = new java.util.HashMap<>();
    static {
        PATH_ROLES.put("/admin",       setOf(AppConstants.ROLE_ADMIN));
        PATH_ROLES.put("/business",    setOf(AppConstants.ROLE_MANAGER, AppConstants.ROLE_ADMIN));
        PATH_ROLES.put("/warehouse",   setOf(AppConstants.ROLE_WAREHOUSE_STAFF, AppConstants.ROLE_MANAGER, AppConstants.ROLE_ADMIN));
        PATH_ROLES.put("/sales",       setOf(AppConstants.ROLE_SALES_STAFF, AppConstants.ROLE_MANAGER, AppConstants.ROLE_ADMIN));
    }

    private static Set<String> setOf(String... values) {
        return new HashSet<>(Arrays.asList(values));
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {}

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest  req  = (HttpServletRequest)  request;
        HttpServletResponse res  = (HttpServletResponse) response;

        String contextPath = req.getContextPath();
        String requestURI  = req.getRequestURI();
        String path        = requestURI.substring(contextPath.length());


        // Always allow static assets and public pages
        if (isPublicPath(path)) {
            chain.doFilter(request, response);
            return;
        }

        // Check session
        HttpSession session = req.getSession(false);
        boolean loggedIn    = (session != null)
                && (session.getAttribute(AppConstants.SESSION_USER) != null);

        if (loggedIn) {
            User userInSession = (User) session.getAttribute(AppConstants.SESSION_USER);
            try {
                UserDAO userDAO = new UserDAO();
                Optional<User> uOpt = userDAO.findById(userInSession.getUserId());

                if (uOpt.isPresent() && uOpt.get().isActive()) {
                    User currentUser = uOpt.get();
                    // Sync changed role to current session immediately
                    if (!currentUser.getRole().equals(userInSession.getRole())) {
                        session.setAttribute(AppConstants.SESSION_USER, currentUser);
                        session.setAttribute(AppConstants.SESSION_ROLE, currentUser.getRole());
                    }
                    if (currentUser.getWarehouseId() != userInSession.getWarehouseId()) {
                        session.setAttribute(AppConstants.SESSION_USER, currentUser);
                        session.setAttribute(AppConstants.SESSION_WAREHOUSE, currentUser.getWarehouseId());
                    }

                    // RBAC check
                    if (!isAuthorized(path, currentUser.getRole())) {
                        log.warning("RBAC denied: user=" + currentUser.getUsername()
                            + " role=" + currentUser.getRole() + " path=" + path);
                        res.sendError(HttpServletResponse.SC_FORBIDDEN,
                            "Tài khoản của bạn không có quyền truy cập trang này.");
                        return;
                    }

                    chain.doFilter(request, response);
                } else {
                    // Account was deactivated or deleted: force logout
                    session.invalidate();
                    res.sendRedirect(contextPath + "/login?status=locked");
                }
            } catch (Exception e) {
                // In case of transient DB issues, fall back to allow session
                chain.doFilter(request, response);
            }
        } else {
            // If the request is from JavaScript (XHR/fetch with Accept: JSON
            // or X-Requested-With header), don't 302 to the login page — that
            // returns HTML which the client can't parse as JSON. Instead return
            // 401 with a JSON body so the frontend can show "session expired".
            String accept = req.getHeader("Accept");
            String xrw   = req.getHeader("X-Requested-With");
            boolean wantsJson = (accept != null && accept.toLowerCase().contains("application/json"))
                    || (xrw != null && !xrw.isBlank());
            if (wantsJson) {
                res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                res.setContentType("application/json;charset=UTF-8");
                res.getWriter().write("{\"success\":false,\"code\":\"SESSION_EXPIRED\",\"message\":\"Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.\"}");
            } else {
                res.sendRedirect(contextPath + "/login");
            }
        }
    }

    /** Checks whether the user is allowed to access the URL based on their role. */
    private boolean isAuthorized(String path, String role) {
        if (role == null) return false;
        for (java.util.Map.Entry<String, Set<String>> entry : PATH_ROLES.entrySet()) {
            String prefix = entry.getKey();
            if (path.equals(prefix) || path.startsWith(prefix + "/")) {
                return entry.getValue().contains(role);
            }
        }
        // Path không thuộc prefix nào trong PATH_ROLES → mặc định cho phép
        // (các URL chung như /, /profile, /api không cần RBAC cụ thể)
        return true;
    }

    private boolean isPublicPath(String path) {
        // Allow static assets
        if (path.startsWith("/assets/")) return true;
        // Allow favicon
        if (path.equals("/favicon.ico")) return true;
        // Allow test-pt.jsp under /login
        if (path.startsWith("/login/")) return true;
        // Allow all Lazada API paths (secured at channel level)
        if (path.startsWith("/api/lazada/")) return true;
        // Storefront (omnicore-web) API — secured via HMAC-SHA256 in BaseApiServlet,
        // not session-based, so must bypass AuthFilter entirely.
        if (path.equals("/api/categories") || path.startsWith("/api/categories/")) return true;
        if (path.equals("/api/products") || path.startsWith("/api/products/")) return true;
        if (path.equals("/api/inventory") || path.startsWith("/api/inventory/")) return true;
        if (path.startsWith("/api/website/")) return true;
        // Allow declared public paths
        return PUBLIC_PATHS.contains(path);
    }

    @Override
    public void destroy() {}
}
