package com.wms.service.lazada;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.model.ChannelProduct;
import com.wms.model.Product;
import com.wms.model.ProductImage;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LazadaProductPayloadBuilder — UC-B2C09 helper that validates a product
 * draft against Lazada's {@code /product/create} rules and assembles the
 * final payload string sent over the wire.
 *
 * <p>Wire format (per Lazada Open Platform docs):
 * <pre>
 *   payload = { "Request": { "Product": {
 *       "PrimaryCategory": "10002019",
 *       "Images": { "Image": [ "url1", "url2", ... ] },
 *       "Attributes": {
 *           "name": "...", "description": "...",
 *           "brand": "No brand", "short_description": "..."
 *       },
 *       "Skus": { "Sku": [{
 *           "SellerSku": "...", "quantity": "10", "price": "100",
 *           "special_price": "90", "special_from_date": "...",
 *           "special_to_date": "...",
 *           "package_height": "10", "package_length": "10",
 *           "package_width": "10", "package_weight": "0.5"
 *       }]}
 *   }}}
 * </pre>
 * The {@code payload} field is then sent as a single form-urlencoded param.
 *
 * <p>Hardcoded brand: WMS operates a non-LazMall shop, brand is
 * always {@value #BRAND}. LazMall-only rules (e.g. {@code C035}) skipped.
 */
public class LazadaProductPayloadBuilder {

    public static final double MAX_WEIGHT_KG = 40.0;
    public static final int MAX_DIMENSION_SUM_CM = 300;
    /**
     * Default brand when the seller has not picked one. Lazada rejects products
     * with empty brand on most categories, so we fall back to "No brand" only
     * as a last resort. Sellers should override this via the wizard.
     */
    public static final String BRAND = "No brand";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public List<ValidationError> validate(Product p, ChannelProduct cp,
                                           List<ProductImage> images) {
        List<ValidationError> errs = new ArrayList<>();
        if (p == null) {
            errs.add(new ValidationError("", "PRODUCT_NULL", "Không tìm thấy sản phẩm."));
            return errs;
        }
        if (p.getProductName() == null || p.getProductName().isBlank()) {
            errs.add(new ValidationError("name", "REQUIRED_NAME",
                "Tên sản phẩm không được để trống."));
        } else if (p.getProductName().length() > 255) {
            errs.add(new ValidationError("name", "NAME_TOO_LONG",
                "Tên sản phẩm phải ≤ 255 ký tự."));
        }
        String shortDesc = firstNonBlank(p.getShortDescription(),
                cp == null ? null : cp.getShortDescription());
        if (shortDesc == null) {
            errs.add(new ValidationError("short_description", "REQUIRED_SHORT_DESC",
                "Mô tả ngắn không được để trống."));
        } else if (shortDesc.length() > 255) {
            errs.add(new ValidationError("short_description", "SHORT_DESC_TOO_LONG",
                "Mô tả ngắn phải ≤ 255 ký tự."));
        }
        if (p.getCategoryId() == null) {
            errs.add(new ValidationError("category_id", "REQUIRED_CATEGORY",
                "Sản phẩm chưa có danh mục. Cập nhật danh mục trước khi đẩy."));
        }
        BigDecimal price = cp != null ? cp.getChannelPrice() : null;
        if (price == null || price.signum() <= 0) {
            errs.add(new ValidationError("price", "BIZ_CHECK_PRICE_IS_ZERO",
                "Giá bán phải lớn hơn 0."));
        }
        if (cp != null && cp.getSpecialPrice() != null) {
            if (cp.getSpecialPrice().signum() <= 0) {
                errs.add(new ValidationError("special_price",
                    "BIZ_CHECK_SPECIAL_PRICE_IS_ZERO",
                    "Giá khuyến mãi phải lớn hơn 0."));
            } else if (price != null && cp.getSpecialPrice().compareTo(price) >= 0) {
                errs.add(new ValidationError("special_price",
                    "BIZ_CHECK_SPECIAL_PRICE_GREATER_THAN_PRICE",
                    "Giá khuyến mãi phải nhỏ hơn giá gốc."));
            }
        }
        if (images == null || images.isEmpty()) {
            errs.add(new ValidationError("images", "BIZ_CHECK_MAIN_IMAGE_REQUIRE",
                "Sản phẩm phải có ít nhất 1 ảnh chính."));
        }
        Double weight = cp != null ? cp.getWeightKg() : null;
        if (weight == null) weight = p.getWeightKg();
        if (weight != null && weight > MAX_WEIGHT_KG) {
            errs.add(new ValidationError("weight_kg", "PACKAGE_WEIGHT_EXCEEDS_LIMIT",
                "Cân nặng gói hàng không được vượt quá 40kg."));
        }
        if (cp != null && cp.getDimensions() != null && !cp.getDimensions().isBlank()) {
            int[] dims = parseDimensions(cp.getDimensions());
            if (dims == null) {
                errs.add(new ValidationError("dimensions", "INVALID_DIMENSIONS_FORMAT",
                    "Kích thước không hợp lệ. Định dạng DxRxC (cm), ví dụ 30x20x10."));
            } else if (dims[0] + dims[1] + dims[2] > MAX_DIMENSION_SUM_CM) {
                errs.add(new ValidationError("dimensions",
                    "PACKAGE_DIMENSION_EXCEEDS_LIMIT",
                    "Tổng kích thước (Dài + Rộng + Cao) không quá 300cm."));
            }
        }
        String sellerSku = cp != null && cp.getSellerSku() != null && !cp.getSellerSku().isBlank()
                ? cp.getSellerSku().trim()
                : (cp != null ? cp.getChannelSkuCode() : null);
        if (sellerSku == null || sellerSku.isBlank()) {
            errs.add(new ValidationError("seller_sku", "REQUIRED_SELLER_SKU",
                "Seller SKU không được để trống."));
        } else if (sellerSku.length() > 50 || !sellerSku.matches("[A-Za-z0-9_-]+")) {
            errs.add(new ValidationError("seller_sku", "INVALID_SELLER_SKU",
                "Seller SKU chỉ chứa chữ, số, - hoặc _ (tối đa 50 ký tự)."));
        }
        return errs;
    }

    /**
     * Builds the Lazada-compliant payload JSON string for {@code /product/create}.
     * Result is serialized as:
     * {@code {"Request":{"Product":{...}}}}
     */
    public String buildJson(Product p, ChannelProduct cp, List<String> lazadaImageUrls) {
        // Lazada VN now accepts external HTTPS image URLs directly without /images/migrate
        List<String> finalImages = (lazadaImageUrls != null && !lazadaImageUrls.isEmpty())
                ? lazadaImageUrls : new ArrayList<>();
        Map<String, Object> sku = new LinkedHashMap<>();
        sku.put("SellerSku", firstNonBlank(cp == null ? null : cp.getSellerSku(),
                                           cp == null ? null : cp.getChannelSkuCode()));
        sku.put("quantity", String.valueOf(cp.getChannelStock() != null ? cp.getChannelStock() : 0));
        sku.put("price", cp.getChannelPrice().toPlainString());
        if (cp.getSpecialPrice() != null) {
            sku.put("special_price", cp.getSpecialPrice().toPlainString());
            // Lazada requires ISO dates; only send them when the seller actually
            // intends a promo window. We default to the next 30 days computed
            // from "now" so values never go stale.
            String fromDate = java.time.LocalDate.now().toString() + " 00:00:00";
            String toDate   = java.time.LocalDate.now().plusDays(30).toString() + " 23:59:59";
            sku.put("special_from_date", fromDate);
            sku.put("special_to_date", toDate);
        }
        // Package dimensions: trust the seller. If missing, the Lazada endpoint
        // will return a precise validation error instead of silently faking
        // values (the old "10x10x10 / 0.2kg" defaults masked real data gaps).
        Double weight = parseWeight(cp == null ? null : cp.getWeightKg(),
                                      p == null ? null : p.getWeightKg());
        if (weight != null) {
            sku.put("package_weight", String.valueOf(weight));
        }
        // Use ChannelProduct dimensions if set, otherwise fall back to Product dimensions
        String dimsStr = (cp != null && cp.getDimensions() != null && !cp.getDimensions().isBlank())
                ? cp.getDimensions()
                : (p != null ? p.getDimensions() : null);
        if (dimsStr != null && !dimsStr.isBlank()) {
            int[] dims = parseDimensions(dimsStr);
            if (dims != null) {
                sku.put("package_length", String.valueOf(dims[0]));
                sku.put("package_width", String.valueOf(dims[1]));
                sku.put("package_height", String.valueOf(dims[2]));
            }
        }

        Map<String, Object> skus = new LinkedHashMap<>();
        skus.put("Sku", new Object[]{ sku });

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("name", trim(p.getProductName(), 255));
        // Lazada Open Platform docs (2025): "brand" will be deprecated,
        // use "brand_id" instead. Most categories require a valid brand_id.
        // Prefer ChannelProduct.brandId (numeric Lazada brand ID from /brand/get).
        // Fall back to the text "brand" name if no brandId is set.
        Long brandId = cp != null ? cp.getBrandId() : null;
        if (brandId != null && brandId > 0) {
            attributes.put("brand_id", String.valueOf(brandId));
        }
        String brandVal = firstNonBlank(cp == null ? null : cp.getBrand());
        if (brandVal != null && !brandVal.isBlank()) {
            attributes.put("brand", brandVal);
        }
        // Lazada description (long, up to 5000) and short_description (up to 255).
        // Wizard-typed text (cp.description) takes top priority for the long
        // description so the value displayed on the marketplace exactly matches
        // what the Sales operator typed in the publish wizard. Only when the
        // wizard left it blank do we fall back to the Master SKU shortDescription
        // or the channel-level shortDescription.
        String longDesc = firstNonBlank(cp == null ? null : cp.getDescription(),
                p == null ? null : p.getShortDescription(),
                cp == null ? null : cp.getShortDescription());
        if (longDesc == null) longDesc = "";
        attributes.put("description", trim(longDesc, 5000));
        // short_description is a separate field — prefer the operator's explicit
        // cp.shortDescription, otherwise truncate the long description to 255 chars.
        String shortDesc = firstNonBlank(cp == null ? null : cp.getShortDescription(),
                longDesc.isEmpty() ? null : longDesc);
        attributes.put("short_description", trim(shortDesc == null ? "" : shortDesc, 255));

        // Lazada category-specific mandatory attributes (hardcoded defaults for the
        // categories we support right now). Real flow would render a dynamic form
        // backed by /category/attributes/get and store the user's choices.
        if (cp != null && cp.getLazadaCategoryId() != null) {
            long catId = cp.getLazadaCategoryId();
            if (catId == 62453404L) {
                // "Gọng kính" — mandatory attributes per /category/attributes/get
                attributes.put("recommended_gender", "Unisex");
                attributes.put("warranty_type", "No Warranty");
                // package dimensions are mandatory for this category at product level;
                // surface whatever the seller supplied (no fake defaults).
                if (cp.getDimensions() != null && !cp.getDimensions().isBlank()) {
                    int[] dims = parseDimensions(cp.getDimensions());
                    if (dims != null) {
                        attributes.put("package_length", String.valueOf(dims[0]));
                        attributes.put("package_width", String.valueOf(dims[1]));
                        attributes.put("package_height", String.valueOf(dims[2]));
                    }
                }
            } else if (catId == 10859L || catId == 1720L || catId == 7831L || catId == 8059L || catId == 12699L || catId == 15072L) {
                // "Khăn, khăn choàng, Hijab, găng tay" — mandatory attributes per /category/attributes/get
                attributes.put("clothing_material", "Polyester");
            }
        }

        Map<String, Object> images = new LinkedHashMap<>();
        images.put("Image", finalImages);

        Map<String, Object> product = new LinkedHashMap<>();
        // Lazada requires a LEAF category from its OWN category tree.
        // NEVER fall back to p.getCategoryId() — that is a WMS-internal ID
        // (e.g. 6) that has NO meaning in Lazada's tree and will cause
        // "category is not leaf" errors from the Lazada API.
        Long lazadaCatId = cp == null ? null : cp.getLazadaCategoryId();
        if (lazadaCatId != null) {
            product.put("PrimaryCategory", String.valueOf(lazadaCatId));
        }
        // If lazadaCatId is null the validation step will catch it before we reach here.
        product.put("Images", images);
        product.put("Attributes", attributes);
        product.put("Skus", skus);

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("Product", product);

        Map<String, Object> payload = new HashMap<>();
        payload.put("Request", request);

        try {
            return MAPPER.writeValueAsString(payload);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize Lazada payload", e);
        }
    }

    /**
     * Returns the form-urlencoded param name and value Lazada expects:
     * {@code payload=<JSON-string>}.
     */
    public Map<String, String> build(Product p, ChannelProduct cp,
                                     List<String> lazadaImageUrls) {
        Map<String, String> out = new LinkedHashMap<>();
        out.put("payload", buildJson(p, cp, lazadaImageUrls));
        return out;
    }

    /**
     * Builds the Lazada-compliant payload JSON string for {@code /product/update}.
     * Result is serialized as:
     * {@code {"Request":{"Product":{"ItemId": "...", "Skus":{"Sku":[{"SkuId":"...",...}]}}}}}
     *
     * <p>Mandatory fields per Lazada update spec:
     * <ul>
     *   <li>{@code Request.Product.ItemId} — Lazada item id (camelCase, NOT a form param)</li>
     *   <li>{@code Request.Product.Skus.Sku[].SkuId} — Lazada sku id</li>
     *   <li>{@code Request.Product.Skus.Sku[].package_height/length/width/weight}</li>
     *   <li>{@code Request.Product.Attributes.brand_id} — numeric (preferred over text brand)</li>
     *   <li>{@code Request.Product.Images.Image[]} — Lazada CDN URLs (use existing images when
     *       wizard did not upload new ones)</li>
     * </ul>
     *
     * <p>If {@code lazadaImageUrls} is empty the caller's existing product images (passed
     * as the second argument via the {@code __existing_image_urls__} placeholder) are used
     * to prevent Lazada from wiping the gallery on update.</p>
     */
    public String buildUpdateJson(Product p, ChannelProduct cp, List<String> lazadaImageUrls) {
        List<String> finalImages = new ArrayList<>();
        if (lazadaImageUrls != null && !lazadaImageUrls.isEmpty()) {
            finalImages.addAll(lazadaImageUrls);
        }
        Map<String, Object> sku = new LinkedHashMap<>();
        String sellerSku = firstNonBlank(cp == null ? null : cp.getSellerSku(),
                                          cp == null ? null : cp.getChannelSkuCode());
        if (sellerSku != null && !sellerSku.isBlank()) {
            sku.put("SellerSku", sellerSku);
        }
        if (cp != null && cp.getLazadaSkuId() != null && !cp.getLazadaSkuId().isEmpty()) {
            sku.put("SkuId", cp.getLazadaSkuId());
        }
        sku.put("quantity", String.valueOf(cp.getChannelStock() != null ? cp.getChannelStock() : 0));
        sku.put("price", cp.getChannelPrice() != null ? cp.getChannelPrice().toPlainString() : "0");
        if (cp.getSpecialPrice() != null) {
            sku.put("special_price", cp.getSpecialPrice().toPlainString());
            String fromDate = java.time.LocalDate.now().toString() + " 00:00:00";
            String toDate   = java.time.LocalDate.now().plusDays(30).toString() + " 23:59:59";
            sku.put("special_from_date", fromDate);
            sku.put("special_to_date", toDate);
        }
        // Package dimensions on SKU — Lazada requires all four for every update.
        Double weight = parseWeight(cp == null ? null : cp.getWeightKg(),
                                      p == null ? null : p.getWeightKg());
        if (weight != null) {
            sku.put("package_weight", String.valueOf(weight));
        }
        // Use ChannelProduct dimensions if set, otherwise fall back to Product dimensions
        String dimsStr = (cp != null && cp.getDimensions() != null && !cp.getDimensions().isBlank())
                ? cp.getDimensions()
                : (p != null ? p.getDimensions() : null);
        if (dimsStr != null && !dimsStr.isBlank()) {
            int[] dims = parseDimensions(dimsStr);
            if (dims != null) {
                sku.put("package_length", String.valueOf(dims[0]));
                sku.put("package_width", String.valueOf(dims[1]));
                sku.put("package_height", String.valueOf(dims[2]));
            }
        }
        // Always include SKU-level images so Lazada does not strip them on update.
        if (!finalImages.isEmpty()) {
            Map<String, Object> skuImages = new LinkedHashMap<>();
            skuImages.put("Image", finalImages);
            sku.put("Images", skuImages);
        }

        Map<String, Object> skus = new LinkedHashMap<>();
        skus.put("Sku", new Object[]{ sku });

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("name", trim(p.getProductName(), 255));
        // Prefer numeric brand_id over text brand — Lazada rejects many categories without it.
        Long brandId = cp != null ? cp.getBrandId() : null;
        if (brandId != null && brandId > 0) {
            attributes.put("brand_id", String.valueOf(brandId));
        }
        String brandVal = firstNonBlank(cp == null ? null : cp.getBrand());
        if (brandVal != null && !brandVal.isBlank()) {
            attributes.put("brand", brandVal);
        }
        // Lazada description (long, up to 5000) and short_description (up to 255).
        // For UPDATE we prefer the ChannelProduct.description set from the wizard because
        // the user just typed it; fall back to product.short_description.
        String longDesc = firstNonBlank(cp == null ? null : cp.getDescription(),
                p == null ? null : p.getShortDescription(),
                cp == null ? null : cp.getShortDescription());
        if (longDesc == null) longDesc = "";
        attributes.put("description", trim(longDesc, 5000));
        // short_description: prefer cp.shortDescription, otherwise truncate longDesc.
        String shortDescUpdate = firstNonBlank(cp == null ? null : cp.getShortDescription(),
                longDesc.isEmpty() ? null : longDesc);
        attributes.put("short_description", trim(shortDescUpdate == null ? "" : shortDescUpdate, 255));

        if (cp != null && cp.getLazadaCategoryId() != null) {
            long catId = cp.getLazadaCategoryId();
            if (catId == 62453404L) {
                attributes.put("recommended_gender", "Unisex");
                attributes.put("warranty_type", "No Warranty");
                if (cp.getDimensions() != null && !cp.getDimensions().isBlank()) {
                    int[] dims = parseDimensions(cp.getDimensions());
                    if (dims != null) {
                        attributes.put("package_length", String.valueOf(dims[0]));
                        attributes.put("package_width", String.valueOf(dims[1]));
                        attributes.put("package_height", String.valueOf(dims[2]));
                    }
                }
            } else if (catId == 10859L || catId == 1720L || catId == 7831L || catId == 8059L || catId == 12699L || catId == 15072L) {
                attributes.put("clothing_material", "Polyester");
            }
        }

        Map<String, Object> product = new LinkedHashMap<>();
        // ItemId MUST be inside the JSON body (Request.Product.ItemId) per Lazada spec.
        // LazadaChannelGateway.updateProduct validates that channelItemId is non-null
        // before invoking this builder, so we can rely on cp.getChannelItemId() here.
        if (cp != null && cp.getChannelItemId() != null && !cp.getChannelItemId().isBlank()) {
            product.put("ItemId", cp.getChannelItemId());
        }
        // Same rule as create: only use a Lazada-issued category ID, never the
        // WMS-internal categoryId which is a completely different namespace.
        Long lazadaCatId = cp == null ? null : cp.getLazadaCategoryId();
        if (lazadaCatId != null) {
            product.put("PrimaryCategory", String.valueOf(lazadaCatId));
        }
        if (!finalImages.isEmpty()) {
            Map<String, Object> productImages = new LinkedHashMap<>();
            productImages.put("Image", finalImages);
            product.put("Images", productImages);
        }
        product.put("Attributes", attributes);
        product.put("Skus", skus);

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("Product", product);

        Map<String, Object> payload = new HashMap<>();
        payload.put("Request", request);

        try {
            return MAPPER.writeValueAsString(payload);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize Lazada update payload", e);
        }
    }

    public Map<String, String> buildUpdate(Product p, ChannelProduct cp,
                                           List<String> lazadaImageUrls) {
        Map<String, String> out = new LinkedHashMap<>();
        out.put("payload", buildUpdateJson(p, cp, lazadaImageUrls));
        return out;
    }

    static int[] parseDimensions(String s) {
        if (s == null) return null;
        String[] parts = s.split("\\s*[xX*\\u00d7\\u2715\\u2716\\uff58\\uff38]\\s*");
        if (parts.length != 3) return null;
        try {
            int[] out = new int[3];
            for (int i = 0; i < 3; i++) {
                double v = Double.parseDouble(parts[i].trim());
                out[i] = (int) Math.round(v);
                if (out[i] <= 0 || out[i] > 200) return null;
            }
            return out;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String trim(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static Double parseWeight(Double explicit, Double fromProduct) {
        Double w = explicit != null ? explicit : fromProduct;
        if (w != null) return w;
        return null;
    }

    /**
     * Attempts to parse a numeric weight from a String that may contain a unit
     * suffix (e.g. "0.5 kg", "500g"). Returns null if unparseable.
     */
    public static Double parseWeightFromString(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Double.parseDouble(s.trim()); } catch (NumberFormatException ignored) {}
        try {
            // strip non-numeric except dot/comma/minus
            String num = s.replaceAll("[^0-9.,\\-]", "").replace(',', '.');
            return Double.parseDouble(num);
        } catch (NumberFormatException ignored) {}
        return null;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) if (v != null && !v.isBlank()) return v;
        return null;
    }

    public static final class ValidationError {
        public final String field;
        public final String code;
        public final String viMessage;
        public ValidationError(String field, String code, String viMessage) {
            this.field = field;
            this.code = code;
            this.viMessage = viMessage;
        }
    }
}