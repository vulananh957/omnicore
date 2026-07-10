package com.wms.service.lazada;

import com.wms.dao.LazadaShipmentProviderDAO;
import com.wms.model.LazadaShipmentProvider;

import java.util.List;
import java.util.logging.Logger;

/**
 * ShipmentProviderMappingService — resolves Lazada shipment provider codes
 * from various input formats (display name, Lazada code, partial match).
 *
 * <p>Sales Staff chọn ĐVVC theo tên tiếng Việt trên UI (ví dụ: "J&T Express").
 * Service này map về Lazada carrier code chuẩn (ví dụ: "JT08") để truyền
 * vào Pack/RTS API.</p>
 */
public class ShipmentProviderMappingService {

    private static final Logger LOGGER = Logger.getLogger(ShipmentProviderMappingService.class.getName());

    private final LazadaShipmentProviderDAO providerDAO = new LazadaShipmentProviderDAO();

    // Hard-coded display name → Lazada code mapping as fallback
    // when the DB lookup returns no results (e.g., new carrier not yet in DB).
    private static final String[][] FALLBACK_MAP = {
        {"JT08",        "J&T Express"},
        {"J&T Express", "JT08"},
        {"FM49",        "Flash Express"},
        {"Flash Express","FM49"},
        {"GHTK14",      "GHTK"},
        {"GHTK",        "GHTK14"},
        {"GHN",         "GHN"},
        {"NJV",         "NinjaVan"},
        {"NinjaVan",    "NJV"},
        {"SPX",         "SPX Express"},
        {"SPX Express", "SPX"},
    };

    /**
     * Resolves a provider code from various input formats.
     *
     * <p>Resolution order:</p>
     * <ol>
     *   <li>Exact match on Lazada provider_code in DB</li>
     *   <li>Case-insensitive match on provider_name / provider_name_vn in DB</li>
     *   <li>Fallback hard-coded map</li>
     *   <li>Return input as-is if nothing matches</li>
     * </ol>
     *
     * @param input The provider code, display name, or partial name
     * @return Lazada carrier code (e.g. "JT08", "FM49") or the input if unresolved
     */
    public String resolveProviderCode(String input) {
        if (input == null || input.isBlank()) return null;

        String trimmed = input.trim();

        // 1. Exact DB match on code
        LazadaShipmentProvider p = providerDAO.findByCode(trimmed);
        if (p != null) {
            return p.getProviderCode();
        }

        // 2. Name match in DB
        List<LazadaShipmentProvider> all = providerDAO.findAll();
        for (LazadaShipmentProvider prov : all) {
            if (matches(prov.getProviderName(), trimmed)
                    || matches(prov.getProviderNameVn(), trimmed)) {
                return prov.getProviderCode();
            }
        }

        // 3. Fallback hard-coded map
        for (int i = 0; i < FALLBACK_MAP.length; i++) {
            if (FALLBACK_MAP[i][0].equalsIgnoreCase(trimmed)) {
                return FALLBACK_MAP[i][1];
            }
            if (FALLBACK_MAP[i][1].equalsIgnoreCase(trimmed)) {
                return FALLBACK_MAP[i][0];
            }
        }

        // 4. Return as-is
        LOGGER.warning("ShipmentProviderMappingService: unresolved provider input '" + input
                + "' — returning as-is (may cause Lazada API error)");
        return trimmed;
    }

    /**
     * Returns the display name for a given Lazada provider code.
     * Falls back to the code itself if not found.
     */
    public String getDisplayName(String providerCode) {
        if (providerCode == null || providerCode.isBlank()) return null;
        LazadaShipmentProvider p = providerDAO.findByCode(providerCode.trim());
        if (p != null) {
            String vn = p.getProviderNameVn();
            return (vn != null && !vn.isBlank()) ? vn : p.getProviderName();
        }
        return providerCode;
    }

    private boolean matches(String a, String b) {
        if (a == null || b == null) return false;
        return a.trim().equalsIgnoreCase(b.trim());
    }
}
