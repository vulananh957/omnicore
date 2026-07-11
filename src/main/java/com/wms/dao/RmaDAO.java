package com.wms.dao;

import com.wms.model.RmaRequest;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.logging.Logger;

/**
 * RmaDAO — customer-submitted return requests (rma_requests table).
 * Currently only written to by the Website channel's return-request API.
 */
public class RmaDAO extends BaseDAO {

    private static final Logger LOGGER = Logger.getLogger(RmaDAO.class.getName());

    private static final String SELECT_BASE =
        "SELECT r.rma_id, r.order_id, o.order_code, r.rma_code, r.status, r.return_reason, "
      + "r.evidence_photos, r.evidence_video, r.resolution_note, r.requested_at, r.returned_at "
      + "FROM rma_requests r JOIN orders o ON r.order_id = o.order_id ";

    public boolean hasPendingRequest(int orderId) {
        Integer count = queryOne(LOGGER,
            "SELECT COUNT(*) AS c FROM rma_requests WHERE order_id = ? AND status = 'PENDING'",
            rs -> rs.getInt("c"), orderId);
        return count != null && count > 0;
    }

    public boolean insert(int orderId, String rmaCode, String returnReason,
                           String evidencePhotos, String evidenceVideo) {
        return update(LOGGER,
            "INSERT INTO rma_requests (order_id, rma_code, status, return_reason, evidence_photos, evidence_video) "
          + "VALUES (?, ?, 'PENDING', ?, ?, ?)",
            orderId, rmaCode, returnReason, evidencePhotos, evidenceVideo) > 0;
    }

    /** Pending return requests for Website orders — feeds the Sales approval view. */
    public List<RmaRequest> findPendingForWebsite() {
        return queryList(LOGGER,
            SELECT_BASE + "WHERE r.status = 'PENDING' AND o.web_order_ref IS NOT NULL ORDER BY r.requested_at ASC",
            this::mapRow);
    }

    public RmaRequest findById(int rmaId) {
        return queryOne(LOGGER, SELECT_BASE + "WHERE r.rma_id = ?", this::mapRow, rmaId);
    }

    /** status: 'APPROVED' or 'RESOLVED' (rejected) — matches rma_requests.status enum. */
    public boolean updateStatus(int rmaId, String status, String resolutionNote) {
        return update(LOGGER,
            "UPDATE rma_requests SET status = ?, resolution_note = ?, "
          + "returned_at = CASE WHEN ? = 'APPROVED' THEN CURRENT_TIMESTAMP ELSE returned_at END "
          + "WHERE rma_id = ?",
            status, resolutionNote, status, rmaId) > 0;
    }

    private RmaRequest mapRow(ResultSet rs) throws SQLException {
        RmaRequest r = new RmaRequest();
        r.setRmaId(rs.getInt("rma_id"));
        r.setOrderId(rs.getInt("order_id"));
        r.setOrderCode(rs.getString("order_code"));
        r.setRmaCode(rs.getString("rma_code"));
        r.setStatus(rs.getString("status"));
        r.setReturnReason(rs.getString("return_reason"));
        r.setEvidencePhotos(rs.getString("evidence_photos"));
        r.setEvidenceVideo(rs.getString("evidence_video"));
        r.setResolutionNote(rs.getString("resolution_note"));
        Timestamp requestedAt = rs.getTimestamp("requested_at");
        if (requestedAt != null) r.setRequestedAt(requestedAt.toLocalDateTime());
        Timestamp returnedAt = rs.getTimestamp("returned_at");
        if (returnedAt != null) r.setReturnedAt(returnedAt.toLocalDateTime());
        return r;
    }
}
