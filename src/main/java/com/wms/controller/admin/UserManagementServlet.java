package com.wms.controller.admin;

import com.wms.controller.BaseController;
import com.wms.model.Role;
import com.wms.model.User;
import com.wms.service.user.UserService;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * UserManagementServlet — Manages User Accounts & Roles (UC-SYS01).
 * Supports pure MVC layout operations, role associations, deactivation toggles, and password validations.
 */
public class UserManagementServlet extends BaseController {

    private static final Logger LOGGER = Logger.getLogger(UserManagementServlet.class.getName());

    private final UserService userService = new UserService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String pathInfo = req.getPathInfo();

        try {
            if (pathInfo == null || "/".equals(pathInfo) || "/list".equals(pathInfo)) {
                handleList(req, resp);
            } else if ("/create".equals(pathInfo)) {
                handleCreateForm(req, resp);
            } else if ("/edit".equals(pathInfo)) {
                handleEditForm(req, resp);
            } else if ("/toggle".equals(pathInfo)) {
                handleToggleStatus(req, resp);
            } else {
                resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "UserManagementServlet: DB error in doGet: " + e.getMessage(), e);
            throw new ServletException("Database access error in UserManagement", e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String pathInfo = req.getPathInfo();

        try {
            if ("/create".equals(pathInfo) || "/save".equals(pathInfo)) {
                handleSaveUser(req, resp);
            } else {
                resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "UserManagementServlet: DB error updating user", e);
            throw new ServletException("Database error during user persistence", e);
        }
    }

    // ── GET Actions ───────────────────────────────────────────

    private void handleList(HttpServletRequest req, HttpServletResponse resp)
            throws SQLException, ServletException, IOException {
        String search = req.getParameter("search");
        String role = req.getParameter("role");
        String status = req.getParameter("status");

        List<User> usersList = userService.findAllFiltered(search, role, status);
        List<Role> rolesList = userService.findAllRoles();
        req.setAttribute("usersList", usersList);
        req.setAttribute("rolesList", rolesList);

        req.setAttribute("pageTitle", "Quản lý Tài khoản & Phân quyền");
        req.setAttribute("pageSubtitle", "Danh sách người dùng, thay đổi phân quyền và trạng thái hoạt động");
        req.setAttribute("currentPage", "admin-users");

        req.setAttribute("contentPage", "/WEB-INF/views/admin/users-management.jsp");
        req.getRequestDispatcher("/WEB-INF/views/layout/admin-layout.jsp").forward(req, resp);
    }

    private void handleCreateForm(HttpServletRequest req, HttpServletResponse resp)
            throws SQLException, ServletException, IOException {
        List<Role> roles = userService.findAllRoles();
        req.setAttribute("roles", roles);
        req.setAttribute("actionUrl", req.getContextPath() + "/admin/users/create");

        req.setAttribute("pageTitle", "Thêm Người Dùng Mới");
        req.setAttribute("pageSubtitle", "Nhập thông tin nhân viên, mật khẩu và gán vai trò hệ thống");
        req.setAttribute("currentPage", "admin-users");

        req.setAttribute("contentPage", "/WEB-INF/views/admin/user-form.jsp");
        req.getRequestDispatcher("/WEB-INF/views/layout/admin-layout.jsp").forward(req, resp);
    }

    private void handleEditForm(HttpServletRequest req, HttpServletResponse resp)
            throws SQLException, ServletException, IOException {
        String idStr = req.getParameter("id");
        if (idStr == null || idStr.trim().isEmpty()) {
            resp.sendRedirect(req.getContextPath() + "/admin/users");
            return;
        }

        int userId = Integer.parseInt(idStr);
        User loggedInUser = (User) req.getSession().getAttribute("loggedInUser");
        if (loggedInUser != null && loggedInUser.getUserId() == userId) {
            resp.sendRedirect(req.getContextPath() + "/admin/profile");
            return;
        }

        var userOpt = userService.findById(userId);

        if (userOpt.isEmpty()) {
            req.setAttribute("errorMessage", "Không tìm thấy người dùng có ID " + userId);
            handleList(req, resp);
            return;
        }

        List<Role> roles = userService.findAllRoles();
        req.setAttribute("user", userOpt.get());
        req.setAttribute("roles", roles);
        req.setAttribute("actionUrl", req.getContextPath() + "/admin/users/create");

        req.setAttribute("pageTitle", "Cập nhật Người Dùng");
        req.setAttribute("pageSubtitle", "Chỉnh sửa thông tin cá nhân hoặc vai trò hoạt động");
        req.setAttribute("currentPage", "admin-users");

        req.setAttribute("contentPage", "/WEB-INF/views/admin/user-form.jsp");
        req.getRequestDispatcher("/WEB-INF/views/layout/admin-layout.jsp").forward(req, resp);
    }

    private void handleToggleStatus(HttpServletRequest req, HttpServletResponse resp)
            throws SQLException, IOException {
        String idStr = req.getParameter("id");
        String activeStr = req.getParameter("active");

        if (idStr != null && activeStr != null) {
            int userId = Integer.parseInt(idStr);
            boolean active = Boolean.parseBoolean(activeStr);
            User loggedInUser = (User) req.getSession().getAttribute("loggedInUser");
            Integer loggedInId = (loggedInUser != null) ? loggedInUser.getUserId() : null;

            if (!userService.canToggleStatus(userId, !active, loggedInId)) {
                resp.sendRedirect(req.getContextPath() + "/admin/users?status=self_toggle_error");
                return;
            }

            userService.toggleStatus(userId, active);
        }

        resp.sendRedirect(req.getContextPath() + "/admin/users?status=toggle_success");
    }

    // ── POST / Form Actions ───────────────────────────────────

    private void handleSaveUser(HttpServletRequest req, HttpServletResponse resp)
            throws SQLException, ServletException, IOException {

        String userIdStr = req.getParameter("userId");
        String username = req.getParameter("username");
        String fullName = req.getParameter("fullName");
        String email = req.getParameter("email");
        String phone = req.getParameter("phone");
        String roleIdStr = req.getParameter("roleId");
        String activeStr = req.getParameter("active");

        boolean isUpdate = userIdStr != null && !userIdStr.trim().isEmpty() && Integer.parseInt(userIdStr) > 0;
        int userId = isUpdate ? Integer.parseInt(userIdStr) : 0;
        int roleId = Integer.parseInt(roleIdStr);
        boolean active = "true".equals(activeStr) || "1".equals(activeStr);

        if (!isUpdate) {
            String randomPassword = userService.generateRandomPassword();
            UserService.Result result = userService.createUser(
                username, fullName, email, phone, roleId, active, randomPassword);

            if (result.isSuccess()) {
                req.getSession().setAttribute("newGeneratedPassword", result.getRawPassword());
                req.getSession().setAttribute("newCreatedUser", result.getUser());
                resp.sendRedirect(req.getContextPath() + "/admin/users?status=success");
            } else {
                setError(req, result.getMessage());
                reloadForm(req, resp, buildTransientUser(userId, username, fullName, email, phone, roleId, active));
            }
        } else {
            Integer existingWarehouseId = null;
            var existingOpt = userService.findById(userId);
            if (existingOpt.isPresent()) {
                existingWarehouseId = existingOpt.get().getWarehouseId();
            }

            UserService.Result result = userService.updateUser(
                userId, username, fullName, email, phone, roleId, active, existingWarehouseId);

            if (result.isSuccess()) {
                resp.sendRedirect(req.getContextPath() + "/admin/users?status=success");
            } else {
                setError(req, result.getMessage());
                reloadForm(req, resp, buildTransientUser(userId, username, fullName, email, phone, roleId, active));
            }
        }
    }

    private void reloadForm(HttpServletRequest req, HttpServletResponse resp, User user)
            throws SQLException, ServletException, IOException {
        req.setAttribute("user", user);
        req.setAttribute("roles", userService.findAllRoles());
        req.setAttribute("actionUrl", req.getContextPath() + "/admin/users/create");

        req.setAttribute("pageTitle", user.getUserId() > 0 ? "Cập nhật Người Dùng" : "Thêm Người Dùng Mới");
        req.setAttribute("pageSubtitle", user.getUserId() > 0 ? "Chỉnh sửa thông tin cá nhân hoặc vai trò hoạt động" : "Nhập thông tin nhân viên, mật khẩu và gán vai trò hệ thống");
        req.setAttribute("currentPage", "admin-users");

        req.setAttribute("contentPage", "/WEB-INF/views/admin/user-form.jsp");
        req.getRequestDispatcher("/WEB-INF/views/layout/admin-layout.jsp").forward(req, resp);
    }

    private User buildTransientUser(int userId, String username, String fullName,
                                    String email, String phone, int roleId, boolean active) {
        String roleName = "WAREHOUSE_STAFF";
        try {
            var role = userService.findRoleById(roleId);
            if (role != null) roleName = role.getRoleName();
        } catch (SQLException ignored) {}

        User user = new User();
        user.setUserId(userId);
        user.setUsername(username);
        user.setFullName(fullName);
        user.setEmail(email);
        user.setPhone(phone);
        user.setRoleId(roleId);
        user.setRole(roleName);
        user.setActive(active);
        return user;
    }
}
