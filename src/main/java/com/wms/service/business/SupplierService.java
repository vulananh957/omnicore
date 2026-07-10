package com.wms.service.business;

import com.wms.dao.SupplierDAO;
import com.wms.model.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * SupplierService — Business logic for supplier management.
 */
public class SupplierService {

    private static final Logger log = LoggerFactory.getLogger(SupplierService.class);

    private final SupplierDAO supplierDAO = new SupplierDAO();

    public PagedResult<Supplier> getSuppliersPaged(int page, int pageSize, String sortBy, boolean ascending,
                                                  String keyword, String debtFilter) {
        try {
            return supplierDAO.findAllPaged(page, pageSize, sortBy, ascending, keyword, debtFilter);
        } catch (Exception e) {
            log.error("getSuppliersPaged failed", e);
            return new PagedResult<>(List.of(), 0, page, pageSize);
        }
    }

    public Supplier getSupplierById(int supplierId) {
        return supplierDAO.findById(supplierId);
    }

    public SaveResult saveSupplier(Supplier supplier) {
        try {
            if (supplier == null) {
                return SaveResult.failure("Dữ liệu nhà cung cấp không hợp lệ.");
            }
            if (supplier.getName() == null || supplier.getName().trim().isEmpty()) {
                return SaveResult.failure("Tên nhà cung cấp không được để trống.");
            }

            if (supplier.getSupplierCode() != null) {
                supplier.setSupplierCode(supplier.getSupplierCode().trim().toUpperCase());
            } else {
                supplier.setSupplierCode(supplierDAO.generateNextSupplierCode());
            }
            supplier.setName(supplier.getName().trim());
            if (supplier.getContactPerson() != null) {
                supplier.setContactPerson(supplier.getContactPerson().trim());
            }
            if (supplier.getPhone() != null) {
                supplier.setPhone(supplier.getPhone().trim());
            }
            if (supplier.getEmail() != null) {
                supplier.setEmail(supplier.getEmail().trim().toLowerCase());
            }
            if (supplier.getAddress() != null) {
                supplier.setAddress(supplier.getAddress().trim());
            }
            if (supplier.getPaymentTerms() != null) {
                supplier.setPaymentTerms(supplier.getPaymentTerms().trim());
            }
            if (supplier.getStatus() == null) {
                supplier.setStatus("ACTIVE");
            }

            boolean codeExists = supplierDAO.existsByCode(supplier.getSupplierCode(),
                    supplier.getSupplierId() > 0 ? supplier.getSupplierId() : null);
            if (codeExists) {
                return SaveResult.failure("Mã nhà cung cấp '" + supplier.getSupplierCode() + "' đã tồn tại.");
            }

            boolean success;
            if (supplier.getSupplierId() > 0) {
                // Update
                success = supplierDAO.update(supplier);
                if (!success) {
                    return SaveResult.failure("Không thể cập nhật nhà cung cấp.");
                }
            } else {
                // Insert
                int newId = supplierDAO.insert(supplier);
                if (newId <= 0) {
                    return SaveResult.failure("Không thể tạo nhà cung cấp mới.");
                }
                supplier.setSupplierId(newId);
            }
            return SaveResult.success();
        } catch (Exception e) {
            log.error("saveSupplier failed", e);
            return SaveResult.failure("Lỗi khi lưu nhà cung cấp: " + e.getMessage());
        }
    }

    public SaveResult deleteSupplier(int supplierId) {
        try {
            if (supplierId <= 0) {
                return SaveResult.failure("ID nhà cung cấp không hợp lệ.");
            }
            boolean success = supplierDAO.delete(supplierId);
            if (!success) {
                return SaveResult.failure("Không thể xóa nhà cung cấp.");
            }
            return SaveResult.success();
        } catch (Exception e) {
            log.error("deleteSupplier failed", e);
            return SaveResult.failure("Lỗi khi xóa nhà cung cấp: " + e.getMessage());
        }
    }

    public List<Supplier> getAllActiveSuppliers() {
        try {
            return supplierDAO.findActive();
        } catch (Exception e) {
            log.error("getAllActiveSuppliers failed", e);
            return List.of();
        }
    }

    public String generateNextSupplierCode() {
        return supplierDAO.generateNextSupplierCode();
    }

    public static class SaveResult {
        private final boolean success;
        private final String message;

        private SaveResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public static SaveResult success() {
            return new SaveResult(true, null);
        }

        public static SaveResult failure(String message) {
            return new SaveResult(false, message);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }
    }

    public static class PagedResult<T> {
        private final List<T> items;
        private final int totalItems;
        private final int totalPages;
        private final int currentPage;
        private final int pageSize;

        public PagedResult(List<T> items, int totalItems, int currentPage, int pageSize) {
            this.items = items;
            this.totalItems = totalItems;
            this.currentPage = currentPage;
            this.pageSize = pageSize;
            this.totalPages = (int) Math.ceil((double) totalItems / pageSize);
        }

        public List<T> getItems() { return items; }
        public int getTotalItems() { return totalItems; }
        public int getTotalPages() { return totalPages; }
        public int getCurrentPage() { return currentPage; }
        public int getPageSize() { return pageSize; }
    }
}
