package com.wms.service.lazada;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * LazadaErrorTranslator — Parses raw JSON responses from Lazada Open Platform
 * and translates known error codes into user-facing Vietnamese messages,
 * each annotated with the UI field that needs fixing.
 *
 * <p>UC-B2C09: The Sales UI's push wizard needs to highlight exactly which
 * form field failed so the operator can fix the value and re-push without
 * having to read raw English error text.</p>
 *
 * <p>Structure of a Lazada error response:
 * <pre>
 * {
 *   "code": "500",
 *   "message": "Some generic message",
 *   "data": {
 *     "errors": [
 *       {"field": "price", "message": "Price is zero"},
 *       {"field": "weight", "message": "Exceeds 40kg"}
 *     ]
 *   }
 * }
 * </pre></p>
 */
public class LazadaErrorTranslator {

    /** Lazada's top-level "success" code. Also accepts the literal string "Success". */
    public static final String SUCCESS_CODE = "0";
    public static final String SUCCESS_LEGACY = "Success";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Maps known Lazada error codes to VI translations + the UI field they refer to. */
    private static final Map<String, ErrorMapping> ERROR_MAP = Map.ofEntries(
        Map.entry("BIZ_CHECK_PRICE_IS_ZERO", new ErrorMapping(
            "Giá bán phải lớn hơn 0.", "price")),
        Map.entry("BIZ_CHECK_SPECIAL_PRICE_GREATER_THAN_PRICE", new ErrorMapping(
            "Giá khuyến mãi đang cao hơn giá gốc. Vui lòng kiểm tra lại.", "special_price")),
        Map.entry("BIZ_CHECK_MAIN_IMAGE_REQUIRE", new ErrorMapping(
            "Sản phẩm phải có ít nhất 1 ảnh chính.", "images")),
        Map.entry("BIZ_CHECK_EXIST_OUTER_IMAGE", new ErrorMapping(
            "Ảnh từ link ngoài không hợp lệ. Hệ thống đã tự động migrate.", "images")),
        Map.entry("E201", new ErrorMapping(
            "Danh mục Lazada không hợp lệ. Vui lòng bấm 'Đồng bộ danh mục' rồi chọn lại.", "category_id")),
        Map.entry("E030", new ErrorMapping(
            "Yêu cầu rỗng — không có dữ liệu gửi đi.", "")),
        Map.entry("E512", new ErrorMapping(
            "Tìm kiếm SPU thất bại. Vui lòng thử lại.", "")),
        Map.entry("BIZ_CHECK_MANGROVE_RULE_QC", new ErrorMapping(
            "Danh mục này bị hạn chế đăng bán. Vui lòng chọn danh mục khác.", "category_id")),
        Map.entry("BIZ_CHECK_SELLER_SKU_DUPLICATE", new ErrorMapping(
            "Seller SKU này đã tồn tại trên gian hàng Lazada. Vui lòng đổi mã SKU khác.", "seller_sku")),
        Map.entry("THD_IC_ERR_F_IC_INFRA_PRODUCT_036", new ErrorMapping(
            "Seller SKU đã tồn tại trên Lazada. Vui lòng đổi mã SKU khác.", "seller_sku")),
        Map.entry("BIZ_CHECK_CAT_PROP_MANDATORY", new ErrorMapping(
            "Thiếu thuộc tính bắt buộc của danh mục. Vui lòng kiểm tra lại thông tin sản phẩm.", "category_id")),
        Map.entry("THD_IC_F_IC_DOMAIN_PROPERTY_002", new ErrorMapping(
            "Thiếu thuộc tính bắt buộc của danh mục. Vui lòng kiểm tra lại thông tin sản phẩm.", "category_id")),
        Map.entry("CHK_SKU_PROPS_DUPLICATE", new ErrorMapping(
            "Thuộc tính biến thể (màu sắc, kích thước) bị trùng lặp giữa các SKU. Vui lòng kiểm tra lại.", "")),
        Map.entry("CHK_SKU_PROPS_NOT_IDENTICAL", new ErrorMapping(
            "Thuộc tính biến thể không nhất quán. Mỗi SKU cần có cùng bộ thuộc tính.", "")),
        Map.entry("BIZ_CHECK_SALEPROP_ATTRIBUTE_INVALID", new ErrorMapping(
            "Thuộc tính biến thể không hợp lệ với danh mục đã chọn.", "")),
        Map.entry("BIZ_CHECK_OFFICIAL_STORE_BRAND_UNAUTHORIZED", new ErrorMapping(
            "Thương hiệu bạn chọn không được phép sử dụng cho loại gian hàng này. Vui lòng chọn thương hiệu khác hoặc đăng ký quyền sử dụng thương hiệu trên Seller Center.", "brand")),
        Map.entry("BIZ_CHECK_CAT_PROP_SENSITIVE_WORDS", new ErrorMapping(
            "Nội dung mô tả hoặc tên sản phẩm chứa từ nhạy cảm. Vui lòng kiểm tra lại.", "description")),
        Map.entry("E4134", new ErrorMapping(
            "Bạn phải chọn danh mục ở cấp cuối cùng (leaf category). Vui lòng bấm 'Đồng bộ danh mục' rồi chọn lại.", "category_id")),
        Map.entry("Please select the last level category.", new ErrorMapping(
            "Bạn phải chọn danh mục ở cấp cuối cùng (leaf category). Vui lòng bấm 'Đồng bộ danh mục' rồi chọn lại.", "category_id")),
        Map.entry("BIZ_CHECK_MAX_PACKAGE_WEIGHT", new ErrorMapping(
            "Cân nặng gói hàng vượt quá 40kg. Vui lòng kiểm tra lại.", "weight_kg")),
        Map.entry("BIZ_CHECK_MAX_PACKAGE_DIMENISIONS", new ErrorMapping(
            "Tổng kích thước (Dài + Rộng + Cao) vượt quá 300cm. Vui lòng kiểm tra lại.", "dimensions")),
        Map.entry("THD_IC_ERR_F_IC_INFRA_SPU_036", new ErrorMapping(
            "Mã sản phẩm (EAN/ISBN) đã tồn tại trên Lazada cho sản phẩm khác.", "")),
        Map.entry("SELLER_SKU_INVALID", new ErrorMapping(
            "Seller SKU chứa ký tự không hợp lệ. Chỉ dùng chữ, số, - , _ , .", "seller_sku")),
        Map.entry("BIZ_CHECK_ITEM_HAS_REACH_LIMIT", new ErrorMapping(
            "Gian hàng đã đạt giới hạn số lượng sản phẩm online. Vui lòng liên hệ Lazada để tăng hạn mức.", "")),
        Map.entry("BIZ_CHECK_PACKAGE_DECIMAL_INVALID", new ErrorMapping(
            "Giá trị kích thước hoặc cân nặng gói hàng không hợp lệ. Vui lòng kiểm tra lại.", "dimensions")),
        Map.entry("CHK_CATPROP_CPV_REQUIRED", new ErrorMapping(
            "Danh mục đã chọn yêu cầu thuộc tính bắt buộc trên Lazada. Vui lòng kiểm tra lại danh mục.", "category_id")),
        Map.entry("BIZ_CHECK_PRICE_PRECISION_INVALID", new ErrorMapping(
            "Giá bán có quá nhiều chữ số thập phân. Vui lòng nhập giá hợp lệ (VD: 150000).", "price")),
        Map.entry("BIZ_CHECK_SPECIAL_PRICE_PRECISION_INVALID", new ErrorMapping(
            "Giá khuyến mãi có quá nhiều chữ số thập phân. Vui lòng nhập giá hợp lệ.", "special_price")),
        Map.entry("BIZ_CHECK_RESTRICTED_CATEGORY", new ErrorMapping(
            "Bạn không được phép đăng bán danh mục này. Vui lòng chọn danh mục khác.", "category_id")),
        Map.entry("BIZ_CHECK_MTEE_RISK_RULE_TRIGGER", new ErrorMapping(
            "Nội dung hoặc giá sản phẩm vi phạm chính sách Lazada. Vui lòng kiểm tra lại.", "")),
        Map.entry("Invalid Request Format", new ErrorMapping(
            "Định dạng yêu cầu không hợp lệ. Vui lòng thử lại.", "")),
        Map.entry("SellerNotActive", new ErrorMapping(
            "Gian hàng Lazada đang không hoạt động. Vui lòng kiểm tra trạng thái gian hàng trên Seller Center.", "")),
        Map.entry("BIZ_CHECK_PROP_SPECIAL_CHAR", new ErrorMapping(
            "Tên hoặc mô tả sản phẩm chứa ký tự đặc biệt không được phép.", "name")),
        Map.entry("REQUIRED_NAME", new ErrorMapping(
            "Tên sản phẩm không được để trống và phải ≤ 255 ký tự.", "name")),
        Map.entry("REQUIRED_SHORT_DESC", new ErrorMapping(
            "Mô tả ngắn không được để trống và phải ≤ 255 ký tự.", "short_description")),
        Map.entry("category_id", new ErrorMapping(
            "Danh mục chưa đúng (Lazada yêu cầu chọn danh mục lá — cấp sâu nhất). Bấm 'Đồng bộ danh mục' rồi chọn lại.", "category_id")),
        Map.entry("E4139", new ErrorMapping(
            "Sản phẩm phải có ít nhất 1 ảnh chính.", "images")),
        // Generic top-level Lazada API codes
        Map.entry("500", new ErrorMapping(
            "Lazada tạm thời không phản hồi. Vui lòng thử lại sau vài phút.", "")),
        Map.entry("5", new ErrorMapping(
            "Định dạng yêu cầu không hợp lệ. Vui lòng thử lại.", "")),
        Map.entry("901", new ErrorMapping(
            "Yêu cầu quá nhiều lần — Lazada tạm khóa. Vui lòng chờ 1-2 phút rồi thử lại.", "")),
        Map.entry("209", new ErrorMapping(
            "Số lượng biến thể vượt quá giới hạn hoặc không hợp lệ. Vui lòng kiểm tra lại.", "")),
        Map.entry("1000", new ErrorMapping(
            "Lỗi nội bộ của Lazada. Vui lòng thử lại sau.", "")),
        Map.entry("4105", new ErrorMapping(
            "Seller SKU đã tồn tại trên gian hàng Lazada. Vui lòng đổi mã SKU khác.", "seller_sku")),
        Map.entry("4129", new ErrorMapping(
            "Tên hoặc mô tả chứa ký tự đặc biệt không được phép (^~<>|). Vui lòng kiểm tra lại.", "description"))
    );

    /** Translates a single Lazada error code, or returns a generic message if unknown. */
    public static ErrorMapping translate(String errorCode, String fallbackMessage) {
        if (errorCode != null && !errorCode.isBlank()) {
            if (ERROR_MAP.containsKey(errorCode)) {
                return ERROR_MAP.get(errorCode);
            }
            // Partial match for SKU duplicate codes (various prefixes exist)
            String ec = errorCode.toUpperCase(java.util.Locale.ROOT);
            if (ec.contains("DUPLICATE") && ec.contains("SKU")) {
                return new ErrorMapping(
                    "Seller SKU này đã tồn tại trên gian hàng Lazada. Vui lòng đổi mã SKU khác.",
                    "seller_sku");
            }
            if (ec.contains("MTEE_RISK") || ec.contains("POLICY") || ec.contains("CATEGORY") || ec.contains("MANGROVE")) {
                String msg = cleanMessage(fallbackMessage);
                return new ErrorMapping(
                    (msg != null && !msg.isBlank()) ? msg : "Đăng sản phẩm thất bại do chính sách danh mục. Vui lòng cập nhật lại danh mục phù hợp và thử lại.",
                    "category_id");
            }
        }
        String cleanFallback = cleanMessage(fallbackMessage);
        return new ErrorMapping(
            (cleanFallback != null && !cleanFallback.isBlank()) ? cleanFallback : "Lazada từ chối yêu cầu. Vui lòng thử lại.",
            "");
    }

    public static String cleanMessage(String raw) {
        if (raw == null || raw.isBlank()) return raw;
        String cleaned = raw.trim();
        // Strip technical code prefixes e.g. BIZ_CHECK_MTEE_...: or Failed by Policy(R_...):
        cleaned = cleaned.replaceAll("^[A-Z0-9_]+:(?:Failed by Policy\\([^)]+\\):)?\\s*", "");
        cleaned = cleaned.replaceAll("^Failed by Policy\\([^)]+\\):\\s*", "");
        return cleaned.trim();
    }

    /**
     * Parses a Lazada JSON response into a {@link ParsedLazadaResponse} that
     * separates the top-level outcome (success/fail) from any field-level
     * error details. Tolerates malformed JSON by returning a synthetic fail
     * result.
     */
    public static ParsedLazadaResponse parse(String jsonResponse) {
        ParsedLazadaResponse out = new ParsedLazadaResponse();
        if (jsonResponse == null || jsonResponse.isBlank()) {
            out.success = false;
            out.topMessage = "Không nhận được phản hồi từ Lazada.";
            return out;
        }
        try {
            JsonNode root = MAPPER.readTree(jsonResponse);
            String code = textOr(root.get("code"), "");
            out.success = SUCCESS_CODE.equals(code) || SUCCESS_LEGACY.equalsIgnoreCase(code);
            out.topMessage = cleanMessage(textOr(root.get("message"), ""));

            // ── 1. ISP format: root-level "detail" array (authoritative for errors) ──
            JsonNode detail = root.get("detail");
            if (detail != null && detail.isArray()) {
                for (JsonNode e : detail) {
                    FieldError fe = new FieldError();
                    String msg = textOr(e.get("message"), "");
                    fe.code = textOr(e.get("code"), "");
                    fe.field = textOr(e.get("field"), "");
                    if (fe.code.isEmpty() && msg.contains(":")) {
                        fe.code = msg.substring(0, msg.indexOf(':')).trim();
                        msg = msg.substring(msg.indexOf(':') + 1).trim();
                    }
                    ErrorMapping m = translate(fe.code, msg);
                    fe.fieldHint = m.fieldHint.isEmpty() ? fe.field : m.fieldHint;
                    fe.viMessage = m.viMessage;
                    out.fieldErrors.add(fe);
                }
            }

            // ── 2. Fallback: single root error code when detail/data is missing ────
            if (!out.success && out.fieldErrors.isEmpty() && !code.isEmpty()) {
                ErrorMapping m = translate(code, out.topMessage);
                FieldError fe = new FieldError();
                fe.code = code;
                fe.field = m.fieldHint;
                fe.fieldHint = m.fieldHint;
                fe.viMessage = m.viMessage;
                out.fieldErrors.add(fe);
                out.topMessage = m.viMessage;
            }

            // ── 2. Legacy format: data.errors[] (used in old success payloads) ────
            if (out.fieldErrors.isEmpty()) {
                JsonNode data = root.get("data");
                if (data != null && data.isObject()) {
                    JsonNode errors = data.get("errors");
                    if (errors != null && errors.isArray()) {
                        for (JsonNode e : errors) {
                            FieldError fe = new FieldError();
                            fe.field = textOr(e.get("field"), "");
                            String msg = textOr(e.get("message"), "");
                            ErrorMapping m = translate(fe.field, msg);
                            fe.fieldHint = m.fieldHint.isEmpty() ? fe.field : m.fieldHint;
                            fe.viMessage = m.viMessage;
                            out.fieldErrors.add(fe);
                        }
                    }
                    if (out.success) {
                        JsonNode itemId = data.get("item_id");
                        if (itemId != null && !itemId.isNull()) out.itemId = itemId.asText();

                        // Lazada /product/create returns sku_list[] with seller_sku and sku_id
                        // for every variant. We pick the first entry — the WMS push pipeline
                        // creates exactly one SKU per product. Lazada /product/update and
                        // /images/migrate return a flat sku_id field instead; we handle both.
                        JsonNode skuList = data.get("sku_list");
                        if (skuList != null && skuList.isArray() && skuList.size() > 0) {
                            JsonNode first = skuList.get(0);
                            JsonNode skuId = first.get("sku_id");
                            if (skuId != null && !skuId.isNull()) out.skuId = skuId.asText();
                        }
                        if (out.skuId == null || out.skuId.isEmpty()) {
                            JsonNode skuId = data.get("sku_id");
                            if (skuId != null && !skuId.isNull()) out.skuId = skuId.asText();
                        }

                        JsonNode images = data.get("images");
                        if (images != null && images.isArray()) {
                            for (JsonNode img : images) {
                                ImageItem it = new ImageItem();
                                it.imageUrl = textOr(img.get("url"), textOr(img.get("image_url"), ""));
                                it.imageId = textOr(img.get("image_id"),
                                        textOr(img.get("hash_code"), textOr(img.get("hash"), "")));
                                it.sourceUrl = textOr(img.get("original_url"), "");
                                out.images.add(it);
                            }
                        }
                    }
                }
            }
            return out;
        } catch (Exception e) {
            out.success = false;
            out.topMessage = "Phản hồi từ Lazada không hợp lệ: " + e.getMessage();
            return out;
        }
    }

    private static String textOr(JsonNode n, String def) {
        if (n == null || n.isNull()) return def;
        String v = n.asText();
        return v.isEmpty() ? def : v;
    }

    /** Translation record: VI message + UI field hint. */
    public static final class ErrorMapping {
        public final String viMessage;
        public final String fieldHint;
        public ErrorMapping(String vi, String field) {
            this.viMessage = vi;
            this.fieldHint = field;
        }
    }

    /** Parsed response from Lazada. */
    public static final class ParsedLazadaResponse {
        public boolean success;
        public String topMessage;
        public String itemId;
        public String skuId;
        public final List<FieldError> fieldErrors = new ArrayList<>();
        public final List<ImageItem> images = new ArrayList<>();
    }

    public static final class FieldError {
        public String code;
        public String field;
        public String fieldHint;
        public String viMessage;
    }

    /** Image record inside an {@code /images/migrate} response. */
    public static final class ImageItem {
        public String sourceUrl;
        public String imageUrl;
        public String imageId;
    }
}
