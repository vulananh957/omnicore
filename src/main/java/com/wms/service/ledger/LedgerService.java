package com.wms.service.ledger;

import com.wms.dao.LedgerDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * LedgerService — Read-only service for the Stock Ledger page.
 *
 * Inventory mutations (approve/reject) are handled by warehouse operation
 * servlets directly. This service only provides data for the ledger view.
 */
public class LedgerService {

    private static final Logger log = LoggerFactory.getLogger(LedgerService.class);
    private final LedgerDAO ledgerDAO = new LedgerDAO();

    public List<LedgerDAO.LedgerDocument> findAllDocuments() {
        return ledgerDAO.findAllDocuments();
    }

    /** Documents scoped to one warehouse (Warehouse Staff view). */
    public List<LedgerDAO.LedgerDocument> findAllDocuments(int warehouseId) {
        return ledgerDAO.findAllDocuments(warehouseId);
    }

    public List<LedgerDAO.GlobalLedgerEntry> findGlobalLedgerEntries() {
        return ledgerDAO.findGlobalLedgerEntries();
    }

    public List<java.util.Map<String, Object>> findDocumentItems(String docId, String docType) {
        return ledgerDAO.findDocumentItems(docId, docType);
    }

    public boolean verifyDocumentBelongsToWarehouse(String docId, String docType, int warehouseId) {
        return ledgerDAO.verifyDocumentBelongsToWarehouse(docId, docType, warehouseId);
    }
}
