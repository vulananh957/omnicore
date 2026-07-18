package com.wms.controller.auth;

import com.wms.controller.BaseController;
import com.wms.model.User;
import com.wms.service.auth.AuthException;
import com.wms.service.auth.AuthService;
import com.wms.util.AppConstants;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;

/**
 * LoginServlet — Handles GET (show form) and POST (authenticate).
 * Performs primary authentication and redirects to 2-Factor OTP verification.
 */
public class LoginServlet extends BaseController {

    private final AuthService authService = new AuthService();

    /** GET /login — display the login form */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {

        // If already logged in, redirect to their role-specific landing page
        if (isLoggedIn(req)) {
            User user = (User) req.getSession().getAttribute(AppConstants.SESSION_USER);
            String role = user != null ? user.getRole() : "";
            String target = getDashboardTarget(role);
            if (!"/login".equals(target)) {
                redirect(res, req.getContextPath() + target);
                return;
            } else {
                HttpSession session = req.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
            }
        }

        HttpSession session = req.getSession(false);
        if (session != null) {
            clearPendingOtp(session);
        }
        forward(req, res, "auth/login");
    }

    /** POST /login — process credentials */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {

        String identity = req.getParameter("identity");
        String password = req.getParameter("password");

        // Basic validation
        if (isNullOrEmpty(identity) || isNullOrEmpty(password)) {
            setError(req, "Vui lòng nhập đầy đủ tên đăng nhập và mật khẩu.");
            forward(req, res, "auth/login");
            return;
        }

        // Authenticate via service layer — distinct errors for locked / not-found / wrong password
        try {
            User user = authService.authenticate(identity.trim(), password);

            // Check if user is active
            if (!user.isActive()) {
                setError(req, "Tài khoản của bạn đã bị khóa. Vui lòng liên hệ quản trị hệ thống.");
                req.setAttribute("identity", identity);
                forward(req, res, "auth/login");
                return;
            }

            // Initialize session and set intermediate 2-Factor Authentication state
            HttpSession session = req.getSession(true);
            clearPendingOtp(session);

            // TEMPORARY BYPASS: Log in directly without OTP for development/testing
            // Set skipOtp to false or uncomment the block below to restore OTP flow
            boolean skipOtp = true;
            if (skipOtp) {
                session.setAttribute(AppConstants.SESSION_USER, user);
                session.setAttribute(AppConstants.SESSION_ROLE, user.getRole());
                session.setAttribute(AppConstants.SESSION_WAREHOUSE, user.getWarehouseId());
                session.setMaxInactiveInterval(30 * 60);
                redirect(res, req.getContextPath() + getDashboardTarget(user.getRole()));
                return;
            }

            /* Original OTP Flow
            session.setAttribute(AppConstants.SESSION_PENDING_USER, user);
            session.setAttribute(AppConstants.SESSION_PENDING_OTP_TARGET, getDashboardTarget(user.getRole()));
            session.setMaxInactiveInterval(10 * 60); // 10 min limit to complete 2FA

            // Redirect to OTP verification page
            redirect(res, req.getContextPath() + "/otp");
            */

        } catch (AuthException e) {
            switch (e.getReason()) {
                case NOT_FOUND:
                case WRONG_PASSWORD:
                    setError(req, "Tên đăng nhập hoặc mật khẩu không đúng.");
                    break;
                case ACCOUNT_LOCKED:
                    setError(req, "Tài khoản của bạn đã bị khóa. Vui lòng liên hệ quản trị hệ thống.");
                    break;
            }
            req.setAttribute("identity", identity);
            forward(req, res, "auth/login");
        }
    }

    /**
     * Resolves the correct landing page URL depending on the user's system role.
     */
    private String getDashboardTarget(String role) {
        if (role == null) {
            return "/login";
        }
        switch (role) {
            case "ADMIN":
                return "/admin/users";
            case "MANAGER":
                return "/business/dashboard";
            case "WAREHOUSE_STAFF":
                return "/warehouse/inventory";
            case "SALES_STAFF":
                return "/sales/orders";
            default:
                return "/login";
        }
    }

    private void clearPendingOtp(HttpSession session) {
        session.removeAttribute(AppConstants.SESSION_USER);
        session.removeAttribute(AppConstants.SESSION_ROLE);
        session.removeAttribute(AppConstants.SESSION_WAREHOUSE);
        session.removeAttribute(AppConstants.SESSION_PENDING_USER);
        session.removeAttribute(AppConstants.SESSION_PENDING_OTP);
        session.removeAttribute(AppConstants.SESSION_PENDING_OTP_EXPIRES);
        session.removeAttribute(AppConstants.SESSION_PENDING_OTP_DEST);
        session.removeAttribute(AppConstants.SESSION_PENDING_OTP_TARGET);
    }
}
