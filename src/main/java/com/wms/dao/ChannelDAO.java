package com.wms.dao;

import com.wms.model.Channel;
import com.wms.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ChannelDAO — Data Access Object for managing sales channel integration records.
 */
public class ChannelDAO {

    private static final Logger LOGGER = Logger.getLogger(ChannelDAO.class.getName());

    /**
     * Default constructor. Schema setup is handled by {@link com.wms.listener.SchemaInitListener}
     * (runs once on app startup) so no self-modifying logic is needed here.
     */
    public ChannelDAO() {
    }

    /**
     * Inserts a new sales channel configuration into the database.
     *
     * @param channel The channel model instance to insert.
     * @return true if successful, false otherwise.
     */
    public boolean insert(Channel channel) {
        String sql = "INSERT INTO channels (channel_name, platform, api_url, api_key, app_secret, webhook_secret, webhook_callback_url, buffer_stock, is_active, access_token, refresh_token, token_expires_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, channel.getChannelName());
            ps.setString(2, channel.getPlatform());
            ps.setString(3, channel.getApiUrl());
            ps.setString(4, channel.getApiKey());
            ps.setString(5, channel.getAppSecret());
            ps.setString(6, channel.getWebhookSecret());
            ps.setString(7, channel.getWebhookCallbackUrl());
            ps.setDouble(8, channel.getBufferStock());
            ps.setInt(9, channel.isActive() ? 1 : 0);
            ps.setString(10, channel.getAccessToken());
            ps.setString(11, channel.getRefreshToken());
            ps.setTimestamp(12, channel.getTokenExpiresAt() != null
                    ? Timestamp.valueOf(channel.getTokenExpiresAt()) : null);
            int rowsAffected = ps.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "ChannelDAO: Failed to update channel " + channel.getChannelId(), e);
            return false;
        }
    }

    /**
     * Retrieves all channels configured in the system.
     * Convenience overload; equivalent to {@code findAll(null)}.
     *
     * @return A list of all configured channels.
     */
    public List<Channel> findAll() {
        return findAll(null);
    }

    /**
     * Retrieves all channels, optionally filtered by a name search keyword.
     *
     * @param keyword The channel name search term (case-insensitive prefix match).
     *                Pass null or blank to return all channels.
     * @return A list of matching channels.
     */
    public List<Channel> findAll(String keyword) {
        List<Channel> list = new ArrayList<>();
        String sql;
        boolean hasKeyword = keyword != null && !keyword.trim().isEmpty();
        if (hasKeyword) {
            sql = "SELECT * FROM channels WHERE channel_name LIKE ? ORDER BY created_at DESC";
        } else {
            sql = "SELECT * FROM channels ORDER BY created_at DESC";
        }
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (hasKeyword) {
                ps.setString(1, "%" + keyword.trim() + "%");
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToChannel(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "ChannelDAO: Failed to find channels", e);
        }
        return list;
    }

    /**
     * Retrieves all active channels (where is_active = 1).
     *
     * @return A list of active channels.
     */
    public List<Channel> findAllActive() {
        List<Channel> list = new ArrayList<>();
        String sql = "SELECT * FROM channels WHERE is_active = 1 ORDER BY channel_name ASC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapResultSetToChannel(rs));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "ChannelDAO: Failed to find active channels", e);
        }
        return list;
    }

    /**
     * Finds a single channel by its primary key.
     *
     * @param channelId The channel ID to look up.
     * @return The Channel object, or null if not found.
     */
    public Channel findById(int channelId) {
        String sql = "SELECT * FROM channels WHERE channel_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, channelId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToChannel(rs);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "ChannelDAO: Failed to find channel by ID " + channelId, e);
        }
        return null;
    }

    /**
     * Updates an existing channel configuration.
     *
     * @param channel The channel with updated field values.
     * @return true if the update succeeded, false otherwise.
     */
    public boolean update(Channel channel) {
        String sql = "UPDATE channels SET "
                + "channel_name = ?, platform = ?, api_url = ?, api_key = ?, "
                + "app_secret = ?, webhook_secret = ?, webhook_callback_url = ?, buffer_stock = ?, is_active = ?, "
                + "access_token = ?, refresh_token = ?, token_expires_at = ?, updated_at = CURRENT_TIMESTAMP "
                + "WHERE channel_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, channel.getChannelName());
            ps.setString(2, channel.getPlatform());
            ps.setString(3, channel.getApiUrl());
            ps.setString(4, channel.getApiKey());
            ps.setString(5, channel.getAppSecret());
            ps.setString(6, channel.getWebhookSecret());
            ps.setString(7, channel.getWebhookCallbackUrl());
            ps.setDouble(8, channel.getBufferStock());
            ps.setInt(9, channel.isActive() ? 1 : 0);
            ps.setString(10, channel.getAccessToken());
            ps.setString(11, channel.getRefreshToken());
            ps.setTimestamp(12, channel.getTokenExpiresAt() != null
                    ? Timestamp.valueOf(channel.getTokenExpiresAt()) : null);
            ps.setInt(13, channel.getChannelId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "ChannelDAO: Failed to update channel " + channel.getChannelId(), e);
            return false;
        }
    }

    /**
     * Maps a ResultSet row to a Channel object.
     */
    private Channel mapResultSetToChannel(ResultSet rs) throws SQLException {
        Channel channel = new Channel();
        channel.setChannelId(rs.getInt("channel_id"));
        channel.setChannelName(rs.getString("channel_name"));
        channel.setPlatform(rs.getString("platform"));
        channel.setApiUrl(rs.getString("api_url"));
        channel.setApiKey(rs.getString("api_key"));
        channel.setAppSecret(rs.getString("app_secret"));
        channel.setWebhookSecret(rs.getString("webhook_secret"));
        channel.setWebhookCallbackUrl(rs.getString("webhook_callback_url"));
        channel.setBufferStock(rs.getDouble("buffer_stock"));
        channel.setActive(rs.getInt("is_active") == 1);
        channel.setAccessToken(rs.getString("access_token"));
        channel.setRefreshToken(rs.getString("refresh_token"));
        Timestamp tokenExpires = rs.getTimestamp("token_expires_at");
        if (tokenExpires != null) {
            channel.setTokenExpiresAt(tokenExpires.toLocalDateTime());
        }
        Timestamp lastSync = rs.getTimestamp("last_order_sync_at");
        if (lastSync != null) {
            channel.setLastOrderSyncAt(lastSync.toLocalDateTime());
        }
        return channel;
    }

    /**
     * Deletes a channel by its primary key.
     *
     * @param channelId The channel ID to delete.
     * @return true if a row was deleted, false otherwise.
     */
    public boolean delete(int channelId) {
        String deleteItems = "DELETE FROM lazada_order_items WHERE lazada_order_id_str IN (SELECT lazada_order_id_str FROM lazada_orders WHERE channel_id = ?)";
        String deleteOrders = "DELETE FROM lazada_orders WHERE channel_id = ?";
        String deleteSkuMappings = "DELETE FROM sku_mappings WHERE channel_id = ?";
        String deleteChannel = "DELETE FROM channels WHERE channel_id = ?";

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(deleteItems)) {
                    ps.setInt(1, channelId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(deleteOrders)) {
                    ps.setInt(1, channelId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(deleteSkuMappings)) {
                    ps.setInt(1, channelId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(deleteChannel)) {
                    ps.setInt(1, channelId);
                    boolean ok = ps.executeUpdate() > 0;
                    conn.commit();
                    return ok;
                }
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "ChannelDAO: Failed to delete channel " + channelId, e);
            return false;
        }
    }

    /**
     * Updates the Lazada access token and refresh token in the database.
     *
     * @param channelId    The identifier of the sales channel.
     * @param accessToken  The new access token received from Lazada.
     * @param refreshToken The new refresh token received from Lazada.
     * @return true if the tokens were updated, false otherwise.
     */
    public boolean updateLazadaTokens(int channelId, String accessToken, String refreshToken) {
        String sql = "UPDATE channels SET access_token = ?, refresh_token = ?, updated_at = CURRENT_TIMESTAMP "
                + "WHERE channel_id = ? AND platform = 'Lazada'";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, accessToken);
            ps.setString(2, refreshToken);
            ps.setInt(3, channelId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "ChannelDAO: Failed to update Lazada tokens for channel " + channelId, e);
            return false;
        }
    }

    /**
     * Updates access token, refresh token, and expiry timestamp for a Lazada channel.
     *
     * @param channelId      The channel ID.
     * @param accessToken    New access token.
     * @param refreshToken   New refresh token.
     * @param tokenExpiresAt The UTC expiry timestamp (null = unknown/legacy).
     * @return true if updated successfully.
     */
    public boolean updateLazadaTokens(int channelId, String accessToken,
                                      String refreshToken, java.time.LocalDateTime tokenExpiresAt) {
        String sql = "UPDATE channels SET access_token = ?, refresh_token = ?, "
                + "token_expires_at = ?, updated_at = CURRENT_TIMESTAMP "
                + "WHERE channel_id = ? AND platform = 'Lazada'";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, accessToken);
            ps.setString(2, refreshToken);
            ps.setTimestamp(3, tokenExpiresAt != null ? Timestamp.valueOf(tokenExpiresAt) : null);
            ps.setInt(4, channelId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "ChannelDAO: Failed to update Lazada tokens (with expiry) for channel " + channelId, e);
            return false;
        }
    }

    /**
     * Finds all Lazada channels whose access token has expired or will expire
     * within the given buffer window, and that have a valid refresh token.
     *
     * @param bufferMinutes Refresh tokens that expire sooner than this window are included.
     * @return List of channels needing token refresh.
     */
    public List<Channel> findChannelsNeedingTokenRefresh(int bufferMinutes) {
        List<Channel> list = new ArrayList<>();
        String sql = "SELECT * FROM channels "
                + "WHERE platform = 'Lazada' AND is_active = 1 "
                + "AND refresh_token IS NOT NULL AND refresh_token != '' "
                + "AND (token_expires_at IS NULL "
                + "     OR token_expires_at <= DATE_ADD(UTC_TIMESTAMP(), INTERVAL ? MINUTE))";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, bufferMinutes);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToChannel(rs));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "ChannelDAO: Failed to find channels needing token refresh", e);
        }
        return list;
    }

    /**
     * Finds the single active channel for a given platform (e.g. "Website").
     * Used by the storefront API auth layer to look up the shared HMAC secret.
     *
     * @param platform The platform identifier to look up.
     * @return The active Channel for that platform, or null if none configured.
     */
    public Channel findByPlatform(String platform) {
        String sql = "SELECT * FROM channels WHERE platform = ? AND is_active = 1 LIMIT 1";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, platform);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToChannel(rs);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "ChannelDAO: Failed to find channel by platform " + platform, e);
        }
        return null;
    }

    public boolean updateLastOrderSyncAt(int channelId, java.time.LocalDateTime lastOrderSyncAt) {
        String sql = "UPDATE channels SET last_order_sync_at = ?, updated_at = CURRENT_TIMESTAMP WHERE channel_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, lastOrderSyncAt != null ? Timestamp.valueOf(lastOrderSyncAt) : null);
            ps.setInt(2, channelId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "ChannelDAO: Failed to update last_order_sync_at for channel " + channelId, e);
            return false;
        }
    }
}
