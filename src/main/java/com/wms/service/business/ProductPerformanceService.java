package com.wms.service.business;

import com.wms.dao.ProductPerformanceDAO;
import com.wms.model.ProductPerformance;
import com.wms.model.ProductPerformance.ChannelLink;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ProductPerformanceService — Business logic for Product Performance Dashboard.
 *
 * <p>Orchestrates data retrieval from DAO, computes derived metrics,
 * enriches with channel links, and handles time period calculations.</p>
 */
public class ProductPerformanceService {

    private final ProductPerformanceDAO dao;

    public ProductPerformanceService() {
        this.dao = new ProductPerformanceDAO();
    }

    // ── Period Constants ──────────────────────────────────────────────────────

    public static final String PERIOD_TODAY = "today";
    public static final String PERIOD_7_DAYS = "7days";
    public static final String PERIOD_30_DAYS = "30days";
    public static final String PERIOD_THIS_MONTH = "thisMonth";
    public static final String PERIOD_CUSTOM = "custom";

    // ── Health Filter Constants ───────────────────────────────────────────────

    public static final String HEALTH_ALL = "ALL";
    public static final String HEALTH_LOW_STOCK = "LOW_STOCK";
    public static final String HEALTH_DEAD_STOCK = "DEAD_STOCK";
    public static final String HEALTH_NORMAL = "NORMAL";

    // ── Core Business Logic ───────────────────────────────────────────────────

    /**
     * Get performance data for the specified time period.
     *
     * @param period Time period filter (today, 7days, 30days, thisMonth, custom)
     * @param customStartDate Required if period is "custom"
     * @param customEndDate Required if period is "custom"
     * @param categoryId Filter by category (null = all)
     * @param channelId Filter by channel (null = all)
     * @param healthFilter Health status filter (ALL, LOW_STOCK, DEAD_STOCK, NORMAL)
     * @param searchQuery Search by SKU code or product name
     * @return List of ProductPerformance with all metrics computed
     */
    public List<ProductPerformance> getPerformanceData(
            String period,
            LocalDate customStartDate,
            LocalDate customEndDate,
            Integer categoryId,
            Integer channelId,
            String healthFilter,
            String searchQuery) {

        // Calculate date range
        Date[] dateRange = calculateDateRange(period, customStartDate, customEndDate);
        Date startDate = dateRange[0];
        Date endDate = dateRange[1];

        // Fetch base data
        List<ProductPerformance> results = dao.findPerformanceByPeriod(
                startDate, endDate, categoryId, channelId, healthFilter, searchQuery);

        // Enrich with channel links
        enrichWithChannelLinks(results);

        return results;
    }

    /**
     * Get performance data sorted by a specific column.
     *
     * @param period Time period
     * @param customStartDate Custom start date
     * @param customEndDate Custom end date
     * @param categoryId Category filter
     * @param channelId Channel filter
     * @param healthFilter Health filter
     * @param searchQuery Search query
     * @param sortBy Column to sort by: totalSold, grossProfit, tiedUpCapital, etc.
     * @param ascending Sort direction
     * @return Sorted list of ProductPerformance
     */
    public List<ProductPerformance> getPerformanceDataSorted(
            String period,
            LocalDate customStartDate,
            LocalDate customEndDate,
            Integer categoryId,
            Integer channelId,
            String healthFilter,
            String searchQuery,
            String sortBy,
            boolean ascending) {

        List<ProductPerformance> results = getPerformanceData(
                period, customStartDate, customEndDate, categoryId, channelId, healthFilter, searchQuery);

        // Sort in memory
        results.sort((a, b) -> {
            int comparison = 0;
            switch (sortBy != null ? sortBy.toLowerCase() : "sku") {
                case "totalsold":
                    comparison = Integer.compare(a.getTotalSold(), b.getTotalSold());
                    break;
                case "grossprofit":
                    comparison = a.getGrossProfit().compareTo(b.getGrossProfit());
                    break;
                case "grossmargin":
                    comparison = Double.compare(a.getGrossMarginPercent(), b.getGrossMarginPercent());
                    break;
                case "tiedupcapital":
                    comparison = a.getTiedUpCapital().compareTo(b.getTiedUpCapital());
                    break;
                case "macprice":
                    comparison = a.getMacPrice().compareTo(b.getMacPrice());
                    break;
                case "sellingprice":
                    comparison = a.getMaxSellingPrice().compareTo(b.getMaxSellingPrice());
                    break;
                case "revenue":
                    comparison = a.getTotalRevenue().compareTo(b.getTotalRevenue());
                    break;
                default:
                    // Default sort by SKU code
                    comparison = a.getSkuCode().compareToIgnoreCase(b.getSkuCode());
                    break;
            }
            return ascending ? comparison : -comparison;
        });

        return results;
    }

    /**
     * Get performance data sorted (no period filter - all data).
     */
    public List<ProductPerformance> getAllPerformanceDataSorted(
            Integer categoryId,
            Integer channelId,
            String healthFilter,
            String searchQuery,
            String sortBy,
            boolean ascending) {

        return getAllPerformanceData(categoryId, channelId, healthFilter, searchQuery, sortBy, ascending);
    }

    /**
     * Get all performance data (no time filter).
     */
    private List<ProductPerformance> getAllPerformanceData(
            Integer categoryId,
            Integer channelId,
            String healthFilter,
            String searchQuery,
            String sortBy,
            boolean ascending) {

        // Fetch all data without date range
        List<ProductPerformance> results = dao.findAllPerformance(
                categoryId, channelId, healthFilter, searchQuery);

        // Enrich with channel links
        enrichWithChannelLinks(results);

        // Sort in memory
        results.sort((a, b) -> {
            int comparison = 0;
            switch (sortBy != null ? sortBy.toLowerCase() : "sku") {
                case "totalsold":
                    comparison = Integer.compare(a.getTotalSold(), b.getTotalSold());
                    break;
                case "grossprofit":
                    comparison = a.getGrossProfit().compareTo(b.getGrossProfit());
                    break;
                case "grossmargin":
                    comparison = Double.compare(a.getGrossMarginPercent(), b.getGrossMarginPercent());
                    break;
                case "tiedupcapital":
                    comparison = a.getTiedUpCapital().compareTo(b.getTiedUpCapital());
                    break;
                case "macprice":
                    comparison = a.getMacPrice().compareTo(b.getMacPrice());
                    break;
                case "sellingprice":
                    comparison = a.getMaxSellingPrice().compareTo(b.getMaxSellingPrice());
                    break;
                case "revenue":
                    comparison = a.getTotalRevenue().compareTo(b.getTotalRevenue());
                    break;
                default:
                    comparison = a.getSkuCode().compareToIgnoreCase(b.getSkuCode());
                    break;
            }
            return ascending ? comparison : -comparison;
        });

        return results;
    }

    /**
     * Get revenue trend data for a specific product (for chart popup).
     */
    public List<Map<String, Object>> getRevenueTrend(
            int productId,
            String period,
            LocalDate customStartDate,
            LocalDate customEndDate) {

        Date[] dateRange = calculateDateRange(period, customStartDate, customEndDate);
        return dao.findRevenueTrend(productId, dateRange[0], dateRange[1]);
    }

    /**
     * Get all categories for filter dropdown.
     */
    public List<Map<String, Object>> getAllCategories() {
        return dao.findAllCategories();
    }

    /**
     * Get all channels for filter dropdown.
     */
    public List<Map<String, Object>> getAllChannels() {
        return dao.findAllChannels();
    }

    // ── Helper Methods ─────────────────────────────────────────────────────────

    /**
     * Calculate date range based on period string.
     */
    private Date[] calculateDateRange(String period, LocalDate customStart, LocalDate customEnd) {
        LocalDate end = LocalDate.now();
        LocalDate start;

        switch (period != null ? period.toLowerCase() : PERIOD_30_DAYS) {
            case PERIOD_TODAY:
                start = end;
                break;
            case PERIOD_7_DAYS:
                start = end.minusDays(7);
                break;
            case PERIOD_30_DAYS:
            default:
                start = end.minusDays(30);
                break;
            case PERIOD_THIS_MONTH:
                start = end.withDayOfMonth(1);
                break;
            case PERIOD_CUSTOM:
                start = (customStart != null) ? customStart : end.minusDays(30);
                end = (customEnd != null) ? customEnd : end;
                break;
        }

        return new Date[] { Date.valueOf(start), Date.valueOf(end) };
    }

    /**
     * Get period label for display.
     */
    public String getPeriodLabel(String period) {
        switch (period != null ? period.toLowerCase() : PERIOD_30_DAYS) {
            case PERIOD_TODAY:
                return "Hôm nay";
            case PERIOD_7_DAYS:
                return "7 ngày qua";
            case PERIOD_30_DAYS:
                return "30 ngày qua";
            case PERIOD_THIS_MONTH:
                return "Tháng này";
            case PERIOD_CUSTOM:
                return "Tùy chỉnh";
            default:
                return "30 ngày qua";
        }
    }

    /**
     * Enrich product performance list with channel links using a single batch query.
     */
    private void enrichWithChannelLinks(List<ProductPerformance> products) {
        if (products == null || products.isEmpty()) {
            return;
        }

        // Collect all product IDs
        List<Integer> productIds = products.stream()
                .map(ProductPerformance::getProductId)
                .toList();

        // Batch fetch all links in one query via SkuMappingDAO
        List<Object[]> rows = new com.wms.dao.SkuMappingDAO().findChannelLinksForProducts(productIds);

        // Group links by productId
        Map<Integer, List<ChannelLink>> linksByProduct = rows.stream()
                .collect(Collectors.groupingBy(
                        r -> (Integer) r[0],
                        Collectors.mapping(
                                r -> new ChannelLink(
                                        (String) r[1],   // channelName
                                        (String) r[2],   // platform
                                        (String) r[3],   // externalSku (seller_sku)
                                        (String) r[4]    // lazadaProductId = channel_item_id
                                ),
                                Collectors.toList()
                        )
                ));

        // Attach to each product
        for (ProductPerformance pp : products) {
            List<ChannelLink> links = linksByProduct.getOrDefault(pp.getProductId(), List.of());
            pp.setChannelLinks(links.isEmpty() ? null : links);
        }
    }

    // ── Summary Statistics ────────────────────────────────────────────────────

    /**
     * Compute summary statistics for the current dataset.
     */
    public Map<String, Object> computeSummary(List<ProductPerformance> products) {
        long totalProducts = products.size();
        long profitableProducts = products.stream()
                .filter(p -> p.getGrossProfit() != null && p.getGrossProfit().compareTo(java.math.BigDecimal.ZERO) > 0)
                .count();
        long lossProducts = products.stream()
                .filter(p -> p.getGrossProfit() != null && p.getGrossProfit().compareTo(java.math.BigDecimal.ZERO) < 0)
                .count();
        long deadStockProducts = products.stream()
                .filter(p -> "DEAD_STOCK".equals(p.getHealthStatus()))
                .count();

        java.math.BigDecimal totalRevenue = products.stream()
                .map(ProductPerformance::getTotalRevenue)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        java.math.BigDecimal totalGrossProfit = products.stream()
                .map(ProductPerformance::getGrossProfit)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        java.math.BigDecimal totalTiedUpCapital = products.stream()
                .map(ProductPerformance::getTiedUpCapital)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        int totalUnitsSold = products.stream()
                .mapToInt(ProductPerformance::getTotalSold)
                .sum();

        java.util.Map<String, Object> summary = new java.util.HashMap<>();
        summary.put("totalProducts", totalProducts);
        summary.put("profitableProducts", profitableProducts);
        summary.put("lossProducts", lossProducts);
        summary.put("deadStockProducts", deadStockProducts);
        summary.put("totalRevenue", totalRevenue);
        summary.put("totalGrossProfit", totalGrossProfit);
        summary.put("totalTiedUpCapital", totalTiedUpCapital);
        summary.put("totalUnitsSold", totalUnitsSold);

        if (totalRevenue.compareTo(java.math.BigDecimal.ZERO) > 0) {
            double avgMargin = totalGrossProfit.doubleValue() / totalRevenue.doubleValue() * 100.0;
            summary.put("avgGrossMarginPercent", avgMargin);
        } else {
            summary.put("avgGrossMarginPercent", 0.0);
        }

        return summary;
    }
}
