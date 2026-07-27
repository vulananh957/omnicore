package com.wms.dao;

import com.wms.model.Supplier;
import com.wms.service.business.SupplierService.PagedResult;
import com.wms.util.DBConnection;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SupplierDAO — Data Access Object for supplier management.
 */
public class SupplierDAO extends BaseDAO {

    private static final Logger LOGGER = Logger.getLogger(SupplierDAO.class.getName());

    private static final RowMapper<Supplier> MAP_SUPPLIER = rs -> {
        Supplier s = new Supplier();
        s.setSupplierId(rs.getInt("supplier_id"));
        s.setSupplierCode(rs.getString("supplier_code"));
        s.setName(rs.getString("name"));
        s.setContactPerson(rs.getString("contact_person"));
        s.setPhone(rs.getString("phone"));
        s.setEmail(rs.getString("email"));
        s.setAddress(rs.getString("address"));
        s.setCreditLimit(rs.getBigDecimal("credit_limit"));
        s.setPaymentTerms(rs.getString("payment_terms"));
        s.setStatus(rs.getString("status"));
        return s;
    };

    public SupplierDAO() {
    }

    /**
     * Find all suppliers with pagination, sorting, and filtering.
     * Uses a single CTE to compute debt once, avoiding double subquery execution.
     *
     * @param page       Page number (1-based)
     * @param pageSize   Items per page
     * @param sortBy     Field to sort by: supplier_code, name, current_balance, total_ordered_value
     * @param ascending  Sort direction
     * @param keyword    Search keyword (name, code, phone)
     * @param debtFilter Filter by debt status: ALL, HAS_DEBT, NO_DEBT
     * @return PagedResult<Supplier> with paginated results
     */
    public PagedResult<Supplier> findAllPaged(int page, int pageSize, String sortBy, boolean ascending,
                                              String keyword, String debtFilter) {
        List<Supplier> items = new ArrayList<>();
        int totalItems = 0;

        String searchPattern = (keyword != null && !keyword.trim().isEmpty()) ? "%" + keyword.trim() + "%" : null;
        String orderBy = buildOrderByClause(sortBy, ascending);
        String effectiveDebtFilter = (debtFilter != null && !debtFilter.isEmpty()) ? debtFilter : "ALL";

        int offset = (page - 1) * pageSize;

        try (Connection conn = DBConnection.getConnection()) {
            String dataSql = buildCteDataSql(searchPattern, effectiveDebtFilter, orderBy);

            // Count: same CTE + window COUNT(*) — no extra query
            String countSql = buildCteCountSql(searchPattern, effectiveDebtFilter);

            try (PreparedStatement psCount = conn.prepareStatement(countSql)) {
                if (searchPattern != null) {
                    psCount.setString(1, searchPattern);
                    psCount.setString(2, searchPattern);
                    psCount.setString(3, searchPattern);
                }
                try (ResultSet rs = psCount.executeQuery()) {
                    if (rs.next()) {
                        totalItems = rs.getInt("total_count");
                    }
                }
            }

            try (PreparedStatement psData = conn.prepareStatement(dataSql)) {
                setCommonParams(psData, searchPattern, effectiveDebtFilter, pageSize, offset);
                try (ResultSet rs = psData.executeQuery()) {
                    while (rs.next()) {
                        Supplier s = new Supplier();
                        s.setSupplierId(rs.getInt("supplier_id"));
                        s.setSupplierCode(rs.getString("supplier_code"));
                        s.setName(rs.getString("name"));
                        s.setContactPerson(rs.getString("contact_person"));
                        s.setPhone(rs.getString("phone"));
                        s.setEmail(rs.getString("email"));
                        s.setAddress(rs.getString("address"));
                        s.setPaymentTerms(rs.getString("payment_terms"));
                        s.setCreditLimit(rs.getBigDecimal("credit_limit"));
                        s.setCurrentBalance(rs.getBigDecimal("current_balance") != null
                                ? rs.getBigDecimal("current_balance") : BigDecimal.ZERO);
                        s.setTotalOrderedValue(rs.getBigDecimal("total_ordered_value") != null
                                ? rs.getBigDecimal("total_ordered_value") : BigDecimal.ZERO);
                        s.setStatus(rs.getString("status"));
                        items.add(s);
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "SupplierDAO.findAllPaged failed", e);
        }

        return new PagedResult<>(items, totalItems, page, pageSize);
    }

    private void setCommonParams(PreparedStatement ps, String searchPattern, String debtFilter,
                                  int pageSize, int offset) throws SQLException {
        int i = 1;
        if (searchPattern != null) {
            ps.setString(i++, searchPattern);
            ps.setString(i++, searchPattern);
            ps.setString(i++, searchPattern);
        }
        if (pageSize > 0) ps.setInt(i++, pageSize);
        if (offset >= 0) ps.setInt(i++, offset);
    }

    private String buildOrderByClause(String sortBy, boolean ascending) {
        String dir = ascending ? "ASC" : "DESC";
        switch (sortBy != null ? sortBy : "") {
            case "supplier_code":  return "s.supplier_code " + dir;
            case "name":           return "s.name " + dir;
            case "current_balance":   return "current_balance " + dir;
            case "total_ordered_value": return "total_ordered_value " + dir;
            default:               return "s.name ASC";
        }
    }

    /**
     * Count query mirrors buildCteDataSql's debt_cte + HAVING filter so totalItems matches
     * the actual filtered row count (previously ignored debtFilter entirely, and built the
     * WHERE clause via raw string concatenation — SQL injectable through the search keyword).
     */
    private String buildCteCountSql(String searchPattern, String debtFilter) {
        return """
            %s
            SELECT COUNT(*) AS total_count FROM (
                SELECT s.supplier_id,
                       COALESCE(dc.current_balance, 0) AS current_balance
                FROM suppliers s
                LEFT JOIN debt_cte dc ON s.supplier_id = dc.supplier_id
                WHERE 1=1
                %s
            ) AS filtered
            """.formatted(
                debtCteSubquery(),
                searchWhereClause(searchPattern, debtFilter, true)
            );
    }

    private String buildCteDataSql(String searchPattern, String debtFilter, String orderBy) {
        return """
            WITH debt_cte AS (
                SELECT 
                    s.supplier_id,
                    SUM(CASE WHEN s.payment_terms LIKE 'Net%%' OR s.payment_terms LIKE 'NET%%' OR s.payment_terms IS NULL OR s.payment_terms = 'CREDIT' 
                             THEN ii.accepted_qty * ii.unit_cost ELSE 0 END) AS current_balance,
                    SUM(ii.accepted_qty * ii.unit_cost) AS total_ordered_value
                FROM inbound_orders io
                JOIN inbound_items ii ON io.inbound_id = ii.inbound_id
                JOIN suppliers s ON io.supplier_id = s.supplier_id
                WHERE io.status = 'RECEIVED'
                GROUP BY s.supplier_id
            )
            SELECT s.supplier_id,
                   s.supplier_code,
                   s.name,
                   s.contact_person,
                   s.phone,
                   s.email,
                   s.address,
                   s.payment_terms,
                   s.credit_limit,
                   s.status,
                   COALESCE(dc.current_balance, 0)      AS current_balance,
                   COALESCE(dc.total_ordered_value, 0)  AS total_ordered_value
            FROM suppliers s
            LEFT JOIN debt_cte dc ON s.supplier_id = dc.supplier_id
            WHERE 1=1
            %s
            ORDER BY %s
            LIMIT ? OFFSET ?
            """.formatted(
                searchWhereClause(searchPattern, debtFilter, true),
                orderBy
            );
    }

    private String debtCteSubquery() {
        return """
            WITH debt_cte AS (
                SELECT 
                    s.supplier_id,
                    SUM(CASE WHEN s.payment_terms LIKE 'Net%%' OR s.payment_terms LIKE 'NET%%' OR s.payment_terms IS NULL OR s.payment_terms = 'CREDIT' 
                             THEN ii.accepted_qty * ii.unit_cost ELSE 0 END) AS current_balance,
                    SUM(ii.accepted_qty * ii.unit_cost) AS total_ordered_value
                FROM inbound_orders io
                JOIN inbound_items ii ON io.inbound_id = ii.inbound_id
                JOIN suppliers s ON io.supplier_id = s.supplier_id
                WHERE io.status = 'RECEIVED'
                GROUP BY s.supplier_id
            )
            """;
    }

    private String searchWhereClause(String searchPattern, String debtFilter, boolean includeHaving) {
        StringBuilder sb = new StringBuilder();
        if (searchPattern != null) {
            sb.append(" AND (s.name LIKE ? OR s.supplier_code LIKE ? OR s.phone LIKE ?)");
        }
        if (includeHaving && !"ALL".equals(debtFilter)) {
            if ("HAS_DEBT".equals(debtFilter)) {
                sb.append(" HAVING current_balance > 0");
            } else if ("NO_DEBT".equals(debtFilter)) {
                sb.append(" HAVING COALESCE(current_balance, 0) = 0");
            }
        }
        return sb.toString();
    }

    private int countTotalItems(String countSql, String searchPattern, String debtFilter) {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(countSql)) {

            int paramIndex = 1;
            if (searchPattern != null) {
                ps.setString(paramIndex++, searchPattern);
                ps.setString(paramIndex++, searchPattern);
                ps.setString(paramIndex++, searchPattern);
            }
            ps.setString(paramIndex++, debtFilter != null ? debtFilter : "ALL");
            ps.setString(paramIndex++, debtFilter != null ? debtFilter : "ALL");
            ps.setString(paramIndex++, debtFilter != null ? debtFilter : "ALL");

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "SupplierDAO.countTotalItems failed", e);
        }
        return 0;
    }

    public Supplier findById(int supplierId) {
        return queryOne(LOGGER,
            "SELECT * FROM suppliers WHERE supplier_id = ?",
            MAP_SUPPLIER, supplierId);
    }

    public Supplier findByCode(String code) {
        return queryOne(LOGGER,
            "SELECT * FROM suppliers WHERE supplier_code = ?",
            MAP_SUPPLIER, code);
    }

    public int insert(Supplier s) {
        String sql = """
            INSERT INTO suppliers (supplier_code, name, contact_person, phone, email, address,
                                  credit_limit, payment_terms, status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        return update(LOGGER, sql,
            s.getSupplierCode(),
            s.getName(),
            s.getContactPerson(),
            s.getPhone(),
            s.getEmail(),
            s.getAddress(),
            s.getCreditLimit() != null ? s.getCreditLimit() : BigDecimal.ZERO,
            s.getPaymentTerms(),
            s.getStatus() != null ? s.getStatus() : "ACTIVE");
    }

    public boolean update(Supplier s) {
        String sql = """
            UPDATE suppliers
            SET supplier_code = ?, name = ?, contact_person = ?, phone = ?, email = ?,
                address = ?, credit_limit = ?, payment_terms = ?, status = ?
            WHERE supplier_id = ?
            """;
        return update(LOGGER, sql,
            s.getSupplierCode(),
            s.getName(),
            s.getContactPerson(),
            s.getPhone(),
            s.getEmail(),
            s.getAddress(),
            s.getCreditLimit() != null ? s.getCreditLimit() : BigDecimal.ZERO,
            s.getPaymentTerms(),
            s.getStatus(),
            s.getSupplierId()) > 0;
    }

    public boolean delete(int supplierId) {
        return update(LOGGER,
            "DELETE FROM suppliers WHERE supplier_id = ?",
            supplierId) > 0;
    }

    public boolean existsByCode(String code, Integer excludeId) {
        String sql;
        if (excludeId != null) {
            sql = "SELECT 1 FROM suppliers WHERE supplier_code = ? AND supplier_id != ?";
            return queryOne(LOGGER, sql, rs -> 1, code, excludeId) != null;
        } else {
            sql = "SELECT 1 FROM suppliers WHERE supplier_code = ?";
            return queryOne(LOGGER, sql, rs -> 1, code) != null;
        }
    }

    private static final String SQL_NEXT_SEQUENCE =
        "SELECT COUNT(*) + 1 AS next_seq FROM suppliers WHERE supplier_code LIKE ?";

    /**
     * Generates the next supplier code in format: NCC-YYMM-NNN
     * Example: NCC-2507-001
     */
    public String generateNextSupplierCode() {
        String today = java.time.LocalDate.now().toString();
        String yy = today.substring(2, 4);
        String mm = today.substring(5, 7);
        String prefix = "NCC-" + yy + mm + "-%";
        String todayPrefix = "NCC-" + yy + mm;

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_NEXT_SEQUENCE)) {
            ps.setString(1, prefix);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int nextSeq = rs.getInt("next_seq");
                    return String.format("%s-%03d", todayPrefix, nextSeq);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "generateNextSupplierCode failed", e);
        }
        return todayPrefix + "-001";
    }

    public List<Supplier> findAll() {
        return queryList(LOGGER,
            "SELECT * FROM suppliers ORDER BY name ASC",
            MAP_SUPPLIER);
    }

    public List<Supplier> findActive() {
        return queryList(LOGGER,
            "SELECT * FROM suppliers WHERE status = 'ACTIVE' ORDER BY name ASC",
            MAP_SUPPLIER);
    }
}
