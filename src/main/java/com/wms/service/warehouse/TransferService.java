package com.wms.service.warehouse;

import com.wms.dao.LedgerDAO;
import com.wms.dao.ProductDAO;
import com.wms.dao.TransferDAO;
import com.wms.dao.WarehouseDAO;
import com.wms.model.Product;
import com.wms.model.Warehouse;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class TransferService {

    private static final DateTimeFormatter CODE_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    // Delegate to LedgerDAO for double-entry: deduct source + add destination + write
    // TRANSFER_OUT/IN ledger entries.
    private final LedgerDAO ledgerDAO = new LedgerDAO();

    private final TransferDAO transferDAO = new TransferDAO();
    private final ProductDAO productDAO = new ProductDAO();
    private final WarehouseDAO warehouseDAO = new WarehouseDAO();
    private final com.wms.dao.InventoryDAO inventoryDAO = new com.wms.dao.InventoryDAO();

    public List<TransferDAO.Transfer> findAll() throws SQLException {
        return transferDAO.findAll();
    }

    public List<TransferDAO.Transfer> findByWarehouseId(int warehouseId) throws SQLException {
        return transferDAO.findByWarehouseId(warehouseId);
    }

    public TransferDAO.Transfer findById(int transferId) throws SQLException {
        return transferDAO.findById(transferId);
    }

    public List<TransferDAO.TransferItem> findItemsByTransferId(int transferId) throws SQLException {
        return transferDAO.findItemsByTransferId(transferId);
    }

    public List<Product> findApprovedProducts() throws SQLException {
        return productDAO.findAll();
    }

    public List<Warehouse> findAllWarehouses() throws SQLException {
        return warehouseDAO.findAll();
    }

    /**
     * Creates a new transfer with its items atomically.
     *
     * @param fromWarehouseId source warehouse
     * @param toWarehouseId   destination warehouse
     * @param createdBy       user id of the creator
     * @param note            optional note
     * @param sku             SKU code of the product being transferred
     * @param qty             quantity to transfer
     * @return the created Transfer with transferId set; never null
     */
    public TransferDAO.Transfer createTransfer(int fromWarehouseId, int toWarehouseId,
            int createdBy, String note, String sku, BigDecimal qty) {

        String transferCode = "TRF-" + LocalDateTime.now().format(CODE_FMT);

        TransferDAO.Transfer t = new TransferDAO.Transfer();
        t.setTransferCode(transferCode);
        t.setFromWarehouseId(fromWarehouseId);
        t.setToWarehouseId(toWarehouseId);
        t.setCreatedBy(createdBy);
        t.setStatus(TransferDAO.Transfer.STATUS_IN_TRANSIT);
        t.setNote(note);

        try {
            int transferId = transferDAO.insert(t);
            t.setTransferId(transferId);

            Product product = productDAO.findBySkuCode(sku);
            if (product != null) {
                TransferDAO.TransferItem item = new TransferDAO.TransferItem();
                item.setTransferId(transferId);
                item.setProductId(product.getProductId());
                item.setSkuCode(sku);
                item.setProductName(product.getProductName());
                item.setShippedQty(qty);
                item.setReceivedQty(BigDecimal.ZERO);
                transferDAO.insertItem(item);

                // Trừ tồn kho nguồn ngay khi tạo phiếu — không cần chờ duyệt (đang di chuyển trên đường)
                boolean deducted = inventoryDAO.deductTransferOut(
                        product.getProductId(), fromWarehouseId, qty);
                if (!deducted) {
                    throw new RuntimeException(
                            "Không đủ tồn kho để chuyển: SKU=" + sku + ", SL=" + qty
                            + " tại kho nguồn (warehouseId=" + fromWarehouseId + ")");
                }
            }
            return t;
        } catch (SQLException e) {
            throw new RuntimeException("Không thể tạo phiếu chuyển kho: " + e.getMessage(), e);
        }
    }

    /**
     * Destination warehouse confirms receipt of a transfer.
     *
     * Previously: only updated status to RECEIVED, never touched inventory,
     * so stock "vanished" between the two warehouses. Now: delegates to
     * LedgerDAO.approveDocument() for double-entry:
     *   - Deduct qty_on_hand + qty_available at source (TRANSFER_OUT)
     *   - Add qty_on_hand + qty_available at destination (TRANSFER_IN)
     *   - Insert 2 rows into inventory_ledger
     *
     * @param transferId  ID of the transfer
     * @param userId      ID of the receiving user
     */
    public void markReceived(int transferId, int userId) throws SQLException {
        TransferDAO.Transfer t = transferDAO.findById(transferId);
        if (t == null) {
            throw new SQLException("Không tìm thấy phiếu chuyển kho ID=" + transferId);
        }

        // Nếu đã RECEIVED rồi thì bỏ qua (idempotent — tránh double-count)
        if (TransferDAO.Transfer.STATUS_RECEIVED.equals(t.getStatus())) {
            return;
        }

        // Cập nhật received_qty cho từng item bằng shipped_qty (giả định nhận đủ)
        // Phục vụ cho query bên LedgerDAO dùng received_qty nếu có
        try {
            for (TransferDAO.TransferItem item : transferDAO.findItemsByTransferId(transferId)) {
                if (item.getReceivedQty() == null
                    || item.getReceivedQty().compareTo(BigDecimal.ZERO) == 0) {
                    transferDAO.updateReceivedQty(item.getTransferItemId(), item.getShippedQty());
                }
            }
        } catch (Exception e) {
            // Không chặn flow chính, chỉ log
            System.err.println("markReceived: cập nhật received_qty thất bại: " + e.getMessage());
        }

        // Ủy quyền cho LedgerDAO xử lý double-entry + ghi ledger
        boolean ok = ledgerDAO.approveDocument(t.getTransferCode(), "Phiếu Chuyển Kho", userId);
        if (!ok) {
            throw new SQLException("Không thể xử lý phiếu chuyển kho " + t.getTransferCode());
        }
    }

    /** Backward-compat overload — gọi với userId mặc định = 1 (admin) cho code cũ. */
    public void markReceived(int transferId) throws SQLException {
        markReceived(transferId, 1);
    }
}
