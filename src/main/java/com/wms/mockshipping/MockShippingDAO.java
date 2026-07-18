package com.wms.mockshipping;

import com.wms.util.DBConnection;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * MockShippingDAO — data access for mock_shipping_carriers + the
 * 'website.mock_shipping.enabled' system_config toggle. Deliberately does not extend
 * BaseDAO / reuse PricingConfigDAO — kept self-contained so the whole mockshipping package
 * can be deleted without touching any other file if the feature is retired.
 */
public class MockShippingDAO {

    private static final Logger LOGGER = Logger.getLogger(MockShippingDAO.class.getName());
    private static final String ENABLED_KEY = "website.mock_shipping.enabled";

    private MockCarrier mapRow(ResultSet rs) throws SQLException {
        MockCarrier c = new MockCarrier();
        c.setCarrierId(rs.getInt("carrier_id"));
        c.setCarrierName(rs.getString("carrier_name"));
        c.setFee(rs.getBigDecimal("fee"));
        c.setActive(rs.getInt("is_active") == 1);
        c.setDisplayOrder(rs.getInt("display_order"));
        return c;
    }

    public List<MockCarrier> findActive() {
        String sql = "SELECT * FROM mock_shipping_carriers WHERE is_active = 1 ORDER BY display_order ASC";
        List<MockCarrier> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "findActive failed", e);
        }
        return list;
    }

    public MockCarrier findById(int carrierId) {
        String sql = "SELECT * FROM mock_shipping_carriers WHERE carrier_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, carrierId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "findById failed: " + carrierId, e);
        }
        return null;
    }

    /** True if Admin has turned on mock shipping simulation for the Website channel. */
    public boolean isEnabled() {
        String sql = "SELECT config_value FROM system_config WHERE config_key = ? AND is_active = 1";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ENABLED_KEY);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && "1".equals(rs.getString(1));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "isEnabled failed", e);
            return false;
        }
    }

    public boolean setEnabled(boolean enabled) {
        String sql = "UPDATE system_config SET config_value = ? WHERE config_key = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, enabled ? "1" : "0");
            ps.setString(2, ENABLED_KEY);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "setEnabled failed", e);
            return false;
        }
    }

    /** Persists the customer's chosen mock carrier + fee onto the order (reuses existing columns). */
    public boolean assignCarrierToOrder(int orderId, String carrierName, BigDecimal fee) {
        String sql = "UPDATE orders SET shipment_provider = ?, shipping_fee = ?, total_amount = total_amount + ? WHERE order_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, carrierName);
            ps.setBigDecimal(2, fee);
            ps.setBigDecimal(3, fee);
            ps.setInt(4, orderId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "assignCarrierToOrder failed orderId=" + orderId, e);
            return false;
        }
    }
}
