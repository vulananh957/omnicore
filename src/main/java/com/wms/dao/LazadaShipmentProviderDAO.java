package com.wms.dao;

import com.wms.model.LazadaShipmentProvider;
import com.wms.util.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * LazadaShipmentProviderDAO — Data Access Object for lazada_shipment_providers table.
 *
 * <p>Manages the Lazada-recognised shipping carriers (VN region) used when
 * calling the Pack and RTS APIs.</p>
 */
public class LazadaShipmentProviderDAO {

    private static final Logger LOGGER = Logger.getLogger(LazadaShipmentProviderDAO.class.getName());

    private LazadaShipmentProvider mapRow(ResultSet rs) throws SQLException {
        LazadaShipmentProvider p = new LazadaShipmentProvider();
        p.setId(rs.getInt("provider_id"));
        p.setRegion(rs.getString("region"));
        p.setProviderCode(rs.getString("provider_code"));
        p.setProviderName(rs.getString("provider_name"));
        p.setProviderNameVn(rs.getString("provider_name_vn"));
        p.setActive(rs.getInt("is_active") == 1);
        p.setDisplayOrder(rs.getInt("display_order"));
        return p;
    }

    // ══ QUERIES ════════════════════════════════════════════════════════════

    /**
     * Returns all active shipment providers for VN, ordered by display_order.
     */
    public List<LazadaShipmentProvider> findAllActive() {
        String sql = "SELECT * FROM lazada_shipment_providers "
                   + "WHERE is_active = 1 AND region = 'VN' "
                   + "ORDER BY display_order ASC";
        List<LazadaShipmentProvider> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "findAllActive failed", e);
        }
        return list;
    }

    /**
     * Returns all providers for a region, ordered by display_order.
     */
    public List<LazadaShipmentProvider> findByRegion(String region) {
        String sql = "SELECT * FROM lazada_shipment_providers "
                   + "WHERE region = ? "
                   + "ORDER BY display_order ASC";
        List<LazadaShipmentProvider> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, region);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "findByRegion failed: " + region, e);
        }
        return list;
    }

    /**
     * Finds a provider by its code and region.
     */
    public LazadaShipmentProvider findByCode(String code) {
        String sql = "SELECT * FROM lazada_shipment_providers WHERE provider_code = ? LIMIT 1";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "findByCode failed: " + code, e);
        }
        return null;
    }

    /**
     * Returns all providers, active and inactive.
     */
    public List<LazadaShipmentProvider> findAll() {
        String sql = "SELECT * FROM lazada_shipment_providers ORDER BY display_order ASC";
        List<LazadaShipmentProvider> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "findAll failed", e);
        }
        return list;
    }

    // ══ MUTATIONS ════════════════════════════════════════════════════════════

    /**
     * Inserts a new provider. Returns generated key or -1.
     */
    public int insert(LazadaShipmentProvider provider) {
        String sql =
            "INSERT INTO lazada_shipment_providers "
          + "(region, provider_code, provider_name, provider_name_vn, is_active, display_order) "
          + "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, provider.getRegion());
            ps.setString(2, provider.getProviderCode());
            ps.setString(3, provider.getProviderName());
            ps.setString(4, provider.getProviderNameVn());
            ps.setInt(5,    provider.isActive() ? 1 : 0);
            ps.setInt(6,    provider.getDisplayOrder());
            int rows = ps.executeUpdate();
            if (rows > 0) {
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) return keys.getInt(1);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "insert failed: " + provider.getProviderCode(), e);
        }
        return -1;
    }

    /**
     * Updates an existing provider.
     */
    public boolean update(LazadaShipmentProvider provider) {
        String sql =
            "UPDATE lazada_shipment_providers SET "
          + "region = ?, provider_code = ?, provider_name = ?, provider_name_vn = ?, "
          + "is_active = ?, display_order = ? "
          + "WHERE provider_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, provider.getRegion());
            ps.setString(2, provider.getProviderCode());
            ps.setString(3, provider.getProviderName());
            ps.setString(4, provider.getProviderNameVn());
            ps.setInt(5,    provider.isActive() ? 1 : 0);
            ps.setInt(6,    provider.getDisplayOrder());
            ps.setInt(7,    provider.getId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "update failed: " + provider.getId(), e);
            return false;
        }
    }

    /**
     * Deletes a provider by ID.
     */
    public boolean delete(int id) {
        String sql = "DELETE FROM lazada_shipment_providers WHERE provider_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "delete failed: " + id, e);
            return false;
        }
    }

    /**
     * Activates or deactivates a provider.
     */
    public boolean setActive(int id, boolean active) {
        String sql = "UPDATE lazada_shipment_providers SET is_active = ? WHERE provider_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, active ? 1 : 0);
            ps.setInt(2, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "setActive failed: " + id, e);
            return false;
        }
    }
}
