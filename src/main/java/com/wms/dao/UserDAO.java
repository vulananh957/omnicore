package com.wms.dao;

import com.wms.model.User;
import com.wms.util.DBConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * UserDAO — Data Access Object for the `users` table.
 *
 * Handles CRUD operations for user accounts. Schema initialisation (phone,
 * otp_preference, role_id columns) is performed once by
 * {@link com.wms.listener.SchemaInitListener}.
 */
public class UserDAO {

    private static final Logger log = LoggerFactory.getLogger(UserDAO.class);

    /**
     * Default constructor. Schema setup is handled by SchemaInitListener.
     */
    public UserDAO() {
    }

    // ── Queries (no roles table JOIN — role is an ENUM column in schema) ──

    private static final String SQL_FIND_BY_USERNAME = "SELECT u.*, r.role_id FROM users u "
            + "LEFT JOIN roles r ON u.role = r.role_name "
            + "WHERE (u.username = ? OR u.email = ? OR u.phone = ?)";

    private static final String SQL_FIND_BY_EMAIL = "SELECT u.*, r.role_id FROM users u "
            + "LEFT JOIN roles r ON u.role = r.role_name "
            + "WHERE u.email = ? AND u.active = 1";

    private static final String SQL_FIND_BY_ID = "SELECT u.*, r.role_id FROM users u "
            + "LEFT JOIN roles r ON u.role = r.role_name "
            + "WHERE u.user_id = ?";

    private static final String SQL_FIND_ALL = "SELECT u.*, r.role_id FROM users u "
            + "LEFT JOIN roles r ON u.role = r.role_name "
            + "ORDER BY u.created_at DESC, u.user_id DESC";

    private static final String SQL_FIND_BY_ROLES = "SELECT u.*, r.role_id FROM users u "
            + "LEFT JOIN roles r ON u.role = r.role_name "
            + "WHERE u.role IN (%s) "
            + "ORDER BY u.created_at DESC, u.user_id DESC";

    // ── Public methods ────────────────────────────────────────

    public Optional<User> findByUsername(String username) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(SQL_FIND_BY_USERNAME)) {
            ps.setString(1, username);
            ps.setString(2, username);
            ps.setString(3, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    public Optional<User> findByEmail(String email) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(SQL_FIND_BY_EMAIL)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    public Optional<User> findById(int userId) throws SQLException {
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(SQL_FIND_BY_ID)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    public List<User> findAll() throws SQLException {
        List<User> users = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(SQL_FIND_ALL);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                users.add(mapRow(rs));
            }
        }
        return users;
    }

    private static final String SQL_FIND_BY_WAREHOUSE =
        "SELECT u.*, r.role_id FROM users u "
      + "LEFT JOIN roles r ON u.role = r.role_name "
      + "WHERE u.warehouse_id = ? AND u.active = 1 "
      + "AND u.role = 'WAREHOUSE_STAFF' "
      + "ORDER BY u.full_name ASC";

    public List<User> findByWarehouseId(int warehouseId) throws SQLException {
        List<User> users = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(SQL_FIND_BY_WAREHOUSE)) {
            ps.setInt(1, warehouseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    users.add(mapRow(rs));
                }
            }
        }
        return users;
    }

    public List<User> findFiltered(String search, String role, String status) throws SQLException {
        List<User> users = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
            "SELECT u.*, r.role_id FROM users u "
            + "LEFT JOIN roles r ON u.role = r.role_name "
            + "WHERE 1=1 "
        );
        List<Object> params = new ArrayList<>();

        if (search != null && !search.trim().isEmpty()) {
            sql.append("AND (u.full_name LIKE ? OR u.email LIKE ? OR u.username LIKE ?) ");
            String likeParam = "%" + search.trim() + "%";
            params.add(likeParam);
            params.add(likeParam);
            params.add(likeParam);
        }

        if (role != null && !role.trim().isEmpty()) {
            sql.append("AND u.role = ? ");
            params.add(role.trim());
        }

        if (status != null && !status.trim().isEmpty()) {
            if ("active".equalsIgnoreCase(status)) {
                sql.append("AND u.active = 1 ");
            } else if ("inactive".equalsIgnoreCase(status)) {
                sql.append("AND u.active = 0 ");
            }
        }

        sql.append("ORDER BY u.created_at DESC, u.user_id DESC");

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    users.add(mapRow(rs));
                }
            }
        }
        return users; 
    }

    public List<User> findByRoles(String... roles) throws SQLException {
        List<User> users = new ArrayList<>();
        if (roles == null || roles.length == 0) {
            return users;
        }

        String placeholders = String.join(",", java.util.Collections.nCopies(roles.length, "?"));
        String sql = String.format(SQL_FIND_BY_ROLES, placeholders);

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < roles.length; i++) {
                ps.setString(i + 1, roles[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    users.add(mapRow(rs));
                }
            }
        }
        return users;
    }

    public boolean insert(User user) throws SQLException {
        String sql = "INSERT INTO users (username, password_hash, full_name, email, phone, role, active, otp_preference, warehouse_id) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getPasswordHash());
            ps.setString(3, user.getFullName());
            ps.setString(4, user.getEmail());
            ps.setString(5, user.getPhone());
            ps.setString(6, user.getRole() != null ? user.getRole() : "WAREHOUSE_STAFF");
            ps.setInt(7, user.isActive() ? 1 : 0);
            ps.setString(8, user.getOtpPreference() != null ? user.getOtpPreference() : "EMAIL");
            if (user.getWarehouseId() > 0) {
                ps.setInt(9, user.getWarehouseId());
            } else {
                ps.setNull(9, java.sql.Types.INTEGER);
            }

            boolean success = ps.executeUpdate() > 0;
            if (success) {
                log.info("User created: username={} role={}", user.getUsername(), user.getRole());
            } else {
                log.warn("User insert returned 0 rows: username={}", user.getUsername());
            }
            return success;
        }
    }

    public boolean update(User user) throws SQLException {
        String sql = "UPDATE users SET full_name = ?, email = ?, phone = ?, role = ?, active = ?, warehouse_id = ? WHERE user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getFullName());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getPhone());
            ps.setString(4, user.getRole() != null ? user.getRole() : "WAREHOUSE_STAFF");
            ps.setInt(5, user.isActive() ? 1 : 0);
            if (user.getWarehouseId() > 0) {
                ps.setInt(6, user.getWarehouseId());
            } else {
                ps.setNull(6, java.sql.Types.INTEGER);
            }
            ps.setInt(7, user.getUserId());

            boolean success = ps.executeUpdate() > 0;
            if (success) {
                log.info("User updated: userId={} role={}", user.getUserId(), user.getRole());
            }
            return success;
        }
    }

    public boolean toggleStatus(int userId, boolean active) throws SQLException {
        String sql = "UPDATE users SET active = ? WHERE user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, active ? 1 : 0);
            ps.setInt(2, userId);
            boolean success = ps.executeUpdate() > 0;
            if (success) {
                log.info("User status toggled: userId={} active={}", userId, active);
            }
            return success;
        }
    }

    public void updateProfile(User user) throws SQLException {
        String sql = "UPDATE users SET full_name = ?, email = ?, phone = ? WHERE user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getFullName());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getPhone());
            ps.setInt(4, user.getUserId());
            ps.executeUpdate();
        }
    }

    public void updatePassword(int userId, String passwordHash) throws SQLException {
        String sql = "UPDATE users SET password_hash = ? WHERE user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, passwordHash);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
    }

    public void updateOtpPreference(int userId, String otpPreference) throws SQLException {
        String sql = "UPDATE users SET otp_preference = ? WHERE user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, otpPreference);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
    }

    public boolean isUsernameTaken(String username, int excludeUserId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM users WHERE username = ? AND user_id != ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setInt(2, excludeUserId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }

    public boolean isEmailTaken(String email, int excludeUserId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM users WHERE email = ? AND user_id != ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            ps.setInt(2, excludeUserId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }

    public void updateUsername(int userId, String newUsername) throws SQLException {
        String sql = "UPDATE users SET username = ? WHERE user_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newUsername);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
    }
                
    /**
     * Returns the primary warehouse staff for a given warehouse.
     * Scans users by role WAREHOUSE_STAFF and matches warehouse_id.
     * Falls back to any active warehouse staff if none is marked primary.
     * Returns null if no warehouse staff is found for the given warehouse.
     */
    public User findPrimaryWarehouseStaff(int warehouseId) throws SQLException {
        String sql =
            "SELECT user_id, full_name, username, email, phone, role, active, warehouse_id, created_at "
          + "FROM users "
          + "WHERE role = 'WAREHOUSE_STAFF' AND warehouse_id = ? AND active = 1 "
          + "ORDER BY (warehouse_id = ?) DESC "  // primary assignment first
          + "LIMIT 1";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, warehouseId);
            ps.setInt(2, warehouseId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    // ── Row mapper ────────────────────────────────────────────

    private User mapRow(ResultSet rs) throws SQLException {
        User user = new User();
        user.setUserId(rs.getInt("user_id"));
        user.setUsername(rs.getString("username"));

        try { user.setPasswordHash(rs.getString("password_hash")); } catch (SQLException ignored) {}
        user.setFullName(rs.getString("full_name"));
        user.setEmail(rs.getString("email"));
        try { user.setPhone(rs.getString("phone")); } catch (SQLException ignored) {}
        try { user.setOtpPreference(rs.getString("otp_preference")); } catch (SQLException ignored) {}

        // Role is the ENUM column in schema — no JOIN needed
        user.setRole(rs.getString("role"));

        try { user.setRoleId(rs.getInt("role_id")); } catch (SQLException ignored) {}

        user.setWarehouseId(rs.getInt("warehouse_id"));
        user.setActive(rs.getBoolean("active"));

        try {
            Timestamp ca = rs.getTimestamp("created_at");
            if (ca != null) user.setCreatedAt(ca.toLocalDateTime());
        } catch (SQLException ignored) {}

        return user;
    }
}