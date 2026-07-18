package com.wms.dao;

import com.wms.model.InventoryPushBatch;
import com.wms.model.InventoryPushBatch.PushStatus;
import com.wms.util.DBConnection;
import org.json.JSONArray;
import org.json.JSONObject;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class InventoryPushDAO {
    private static final Logger LOG = Logger.getLogger(InventoryPushDAO.class.getName());

    public void saveBatch(InventoryPushBatch batch) {
        String sql = "INSERT INTO inventory_push_batch " +
                "(batch_id, retry_count, next_retry_time, status, payload, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, batch.getBatchId());
            ps.setInt(2, batch.getRetryCount());
            ps.setTimestamp(3, batch.getNextRetryTime() != null ?
                    Timestamp.valueOf(batch.getNextRetryTime()) : null);
            ps.setString(4, batch.getStatus().toString());
            ps.setString(5, buildPayloadJson(batch.getItems()));
            ps.setTimestamp(6, Timestamp.valueOf(batch.getCreatedAt()));
            ps.setTimestamp(7, Timestamp.valueOf(LocalDateTime.now()));

            ps.executeUpdate();
            LOG.log(Level.INFO, "Saved push batch: {0}", batch.getBatchId());

        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Error saving push batch: " + e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    public void updateBatchStatus(String batchId, PushStatus status, String errorMessage) {
        String sql = "UPDATE inventory_push_batch " +
                "SET status = ?, last_error_message = ?, updated_at = ? " +
                "WHERE batch_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, status.toString());
            ps.setString(2, errorMessage);
            ps.setTimestamp(3, new Timestamp(System.currentTimeMillis()));
            ps.setString(4, batchId);

            ps.executeUpdate();
            LOG.log(Level.INFO, "Updated batch {0} status to {1}", new Object[]{batchId, status});

        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Error updating batch status: " + e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    public void updateBatchForRetry(String batchId, int newRetryCount, LocalDateTime nextRetryTime) {
        String sql = "UPDATE inventory_push_batch " +
                "SET retry_count = ?, next_retry_time = ?, status = ?, updated_at = ? " +
                "WHERE batch_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, newRetryCount);
            ps.setTimestamp(2, Timestamp.valueOf(nextRetryTime));
            ps.setString(3, PushStatus.PENDING.toString());
            ps.setTimestamp(4, new Timestamp(System.currentTimeMillis()));
            ps.setString(5, batchId);

            ps.executeUpdate();
            LOG.log(Level.INFO, "Scheduled retry for batch {0} at {1}", new Object[]{batchId, nextRetryTime});

        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Error updating batch for retry: " + e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    public List<InventoryPushBatch> findPendingBatches() {
        String sql = "SELECT batch_id, retry_count, next_retry_time, status, payload, created_at " +
                "FROM inventory_push_batch " +
                "WHERE status = 'PENDING' AND (next_retry_time IS NULL OR next_retry_time <= NOW()) " +
                "ORDER BY created_at ASC " +
                "LIMIT 50";

        List<InventoryPushBatch> batches = new ArrayList<>();

        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                InventoryPushBatch batch = new InventoryPushBatch();
                batch.setBatchId(rs.getString("batch_id"));
                batch.setRetryCount(rs.getInt("retry_count"));
                batch.setStatus(PushStatus.valueOf(rs.getString("status")));
                batch.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());

                Timestamp nextRetry = rs.getTimestamp("next_retry_time");
                if (nextRetry != null) {
                    batch.setNextRetryTime(nextRetry.toLocalDateTime());
                }

                batches.add(batch);
            }

            LOG.log(Level.INFO, "Found {0} pending push batches", batches.size());
            return batches;

        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Error finding pending batches: " + e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    public void markSuccessful(String batchId) {
        updateBatchStatus(batchId, PushStatus.SUCCESS, null);
    }

    public void markFailed(String batchId, String errorMessage) {
        updateBatchStatus(batchId, PushStatus.FAILED, errorMessage);
    }

    private String buildPayloadJson(List<?> items) {
        if (items == null || items.isEmpty()) {
            return "[]";
        }

        JSONArray arr = new JSONArray();
        for (Object item : items) {
            if (item instanceof com.wms.model.InventoryUpdate) {
                com.wms.model.InventoryUpdate update = (com.wms.model.InventoryUpdate) item;
                JSONObject obj = new JSONObject();
                obj.put("product_id", update.getProductId());
                obj.put("qty_available", update.getQtyAvailable());
                obj.put("qty_before", update.getQtyBefore());
                arr.put(obj);
            }
        }

        return arr.toString();
    }
}
