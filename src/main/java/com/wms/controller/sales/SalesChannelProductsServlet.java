package com.wms.controller.sales;

import com.wms.controller.BaseController;
import com.wms.dao.ChannelDAO;
import com.wms.dao.ProductImageDAO;
import com.wms.model.Channel;
import com.wms.model.Product;
import com.wms.service.channel.LazadaChannelGateway;
import com.wms.service.lazada.CatboxImageUploader;
import com.wms.service.lazada.LazadaProductService;
import com.wms.service.lazada.LazadaProductService.PushResult;
import com.wms.service.sales.ChannelService;
import com.wms.service.product.ProductService;
import com.wms.service.website.WebsiteProductService;
import com.wms.util.JsonUtil;
import java.util.Map;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * SalesChannelProductsServlet — Handles the "Sản phẩm theo kênh" page for Sales Staff.
 * Maps to /sales/channel-products.
 */
import com.wms.dao.CategoryMappingDAO;
import com.wms.dao.ChannelProductDAO;
import com.wms.dao.LazadaCategoryDAO;
import com.wms.dao.ProductDAO;
import com.wms.model.ChannelProduct;
import com.wms.model.ProductImage;
import com.wms.service.lazada.LazadaCategorySyncService;
import com.wms.service.lazada.LazadaCategorySyncService.SyncResult;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SalesChannelProductsServlet extends BaseController {

    private static final Logger LOGGER =
            Logger.getLogger(SalesChannelProductsServlet.class.getName());

    private final ChannelService channelService = new ChannelService();
    private final ProductService productService = new ProductService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        // ── AJAX GET actions (return JSON) — check BEFORE loading heavy DB data ──
        String action = req.getParameter("action");
        if ("getProductDetail".equals(action)) {
            handleGetProductDetail(req, resp);
            return;
        }

        try {
            List<?> channels = channelService.findAll();
            req.setAttribute("channelsList", channels);
            req.setAttribute("channelsJson", JsonUtil.toJson(channels));

            List<?> products = productService.findAll();
            req.setAttribute("products", products);
            req.setAttribute("productsJson", JsonUtil.toJson(products));

            List<?> categories = productService.findAllCategories();
            req.setAttribute("categories", categories);
            req.setAttribute("categoriesJson", JsonUtil.toJson(categories));
        } catch (Exception e) {
            req.setAttribute("channelsList", List.of());
            req.setAttribute("channelsJson", "[]");
            req.setAttribute("products", List.of());
            req.setAttribute("productsJson", "[]");
            req.setAttribute("categories", List.of());
            req.setAttribute("categoriesJson", "[]");
        }

        // Load channel products from DB so the page always shows real data
        // (previously relied on localStorage which is cleared on new browser/device)
        try {
            List<ChannelProduct> channelProducts =
                    new ChannelProductDAO().findAll();
            req.setAttribute("channelProductsList", channelProducts);
            req.setAttribute("channelProductsJson", JsonUtil.toJson(channelProducts));
        } catch (Exception e) {
            req.setAttribute("channelProductsList", List.of());
            req.setAttribute("channelProductsJson", "[]");
        }

        req.setAttribute("pageTitle",    "Sản Phẩm Theo Kênh");
        req.setAttribute("pageSubtitle", "Quản lý sản phẩm kinh doanh trên các sàn thương mại điện tử");
        req.setAttribute("currentPage",  "sales-channel-products");

        req.setAttribute("contentPage", "/WEB-INF/views/sales/channel-products.jsp");

        req.getRequestDispatcher("/WEB-INF/views/layout/sales-layout.jsp")
           .forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String action = req.getParameter("action");
        if ("updateBufferStock".equals(action)) {
            String channelIdStr = req.getParameter("channelId");
            String bufferStockStr = req.getParameter("bufferStock");

            if (isNullOrEmpty(channelIdStr) || isNullOrEmpty(bufferStockStr)) {
                writeJson(resp, "{\"success\":false,\"message\":\"Missing parameters\"}");
                return;
            }

            try {
                int channelId = Integer.parseInt(channelIdStr);
                double bufferStock = Double.parseDouble(bufferStockStr);
                boolean updated = channelService.updateBufferStock(channelId, bufferStock);
                if (updated) {
                    Channel ch = new ChannelDAO().findById(channelId);
                    if (ch != null) {
                        new Thread(() -> {
                            try {
                                if ("Website".equalsIgnoreCase(ch.getPlatform())) {
                                    new WebsiteProductService().syncAllProductStock(ch);
                                } else if ("Lazada".equalsIgnoreCase(ch.getPlatform())) {
                                    new com.wms.service.marketplace.MarketplaceSyncService().triggerStockSyncForChannel(ch);
                                }
                            } catch (Exception ex) {
                                LOGGER.log(java.util.logging.Level.WARNING, "Failed to auto re-push stock after bufferStock update for channel " + channelId, ex);
                            }
                        }).start();
                    }
                    writeJson(resp, "{\"success\":true,\"message\":\"Đã lưu tồn đệm an toàn vào CSDL và kích hoạt đồng bộ lại tồn kho lên sàn!\"}");
                } else {
                    writeJson(resp, "{\"success\":false,\"message\":\"Channel not found\"}");
                }
            } catch (Exception e) {
                writeJson(resp, "{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}");
            }
        } else if ("pull".equals(action)) {
            // Lazada end-to-end: pull marketplace products (synchronous for direct UI response)
            int channelId = Integer.parseInt(req.getParameter("channelId"));
            Channel ch = new ChannelDAO().findById(channelId);
            if (ch == null) {
                writeJson(resp, "{\"success\":false,\"message\":\"Channel not found\"}");
                return;
            }
            try {
                LazadaProductService.PullResult r;
                if ("Website".equalsIgnoreCase(ch.getPlatform())) {
                    r = new WebsiteProductService().pullProducts(ch);
                } else {
                    r = new LazadaProductService().pullProducts(ch);
                }
                if (r.ok) {
                    writeJson(resp, "{\"success\":true,\"message\":\"Kéo sản phẩm thành công! Đã tải " + r.pulled + " sản phẩm từ sàn, phát hiện " + r.unmapped + " sản phẩm chưa ánh xạ.\"}");
                } else {
                    writeJson(resp, "{\"success\":false,\"message\":\"Kéo sản phẩm thất bại: " + esc(r.error) + "\"}");
                }
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "channel-products pull: failed", ex);
                writeJson(resp, "{\"success\":false,\"message\":\"Lỗi hệ thống: " + esc(ex.getMessage()) + "\"}");
            }

        } else if ("push".equals(action)) {
            LOGGER.info("=== PUSH REQUEST received: action=push ===");
            // UC-B2C09 / UC-B2C02: push a single product with structured errors.
            // Wizard passes lazadaCategoryId (leaf category from /category/tree/get)
            // plus price/qty/desc/brand/weight/dimensions/images — we apply them
            // to the channel_products row so the payload builder uses the values
            // the user just typed in the wizard (not stale DB state).
            try {
            LOGGER.info("push params: channelId=" + req.getParameter("channelId") + " productId=" + req.getParameter("productId"));
            NumberFormat nf = NumberFormat.getInstance(Locale.forLanguageTag("vi"));
            int channelId = Integer.parseInt(req.getParameter("channelId"));
            int productId = Integer.parseInt(req.getParameter("productId"));
            Channel ch = new ChannelDAO().findById(channelId);
            if (ch == null) {
                writeJson(resp, "{\"success\":false,\"message\":\"Channel not found\"}");
                return;
            }
                ChannelProductDAO cpDao = new ChannelProductDAO();

                ChannelProduct cp = cpDao.findByProductAndChannel(productId, channelId);
                Product prod = new ProductDAO().findById(productId);
                if (cp == null) {
                    cp = new ChannelProduct();
                    cp.setChannelId(channelId);
                    cp.setProductId(productId);
                    if (prod != null) cp.setChannelSkuCode(prod.getSkuCode());
                }

                // 1) Lazada category from wizard
                String lzCatParam = req.getParameter("lazadaCategoryId");
                if (lzCatParam != null && !lzCatParam.isBlank()) {
                    try {
                        long lzCatId = Long.parseLong(lzCatParam.trim());
                        cp.setLazadaCategoryId(lzCatId);
                    } catch (NumberFormatException ignored) {}
                }

                // 1b) Lazada brand_id from wizard (mandatory per Lazada Open Platform 2025 docs)
                String brandIdParam = req.getParameter("brandId");
                if (brandIdParam != null && !brandIdParam.isBlank()) {
                    try {
                        long brandIdVal = Long.parseLong(brandIdParam.trim());
                        cp.setBrandId(brandIdVal);
                    } catch (NumberFormatException ignored) {}
                }
                String priceParam = req.getParameter("price");
                BigDecimal channelPrice = null;
                if (priceParam != null && !priceParam.isBlank()) {
                    try {
                        channelPrice = new BigDecimal(priceParam);
                    } catch (NumberFormatException ignored) {}
                }

                // BR-PRICE-01: Removed hard block — warning chip is displayed in UI, allowing manager flexibility.

                if (priceParam != null && !priceParam.isBlank()) {
                    cp.setChannelPrice(channelPrice);
                }
                String qtyParam = req.getParameter("quantity");
                if (qtyParam != null && !qtyParam.isBlank()) {
                    try {
                        BigDecimal channelStock = new BigDecimal(qtyParam.trim());
                        double availableQty = (prod != null && prod.getQtyOnHand() != null) ? prod.getQtyOnHand() : 0.0;
                        if (channelStock.compareTo(BigDecimal.ZERO) < 0) {
                            writeJson(resp, "{\"success\":false,\"message\":\"Số lượng sản phẩm không được dưới 0.\"}");
                            return;
                        }
                        if (channelStock.compareTo(BigDecimal.valueOf(availableQty)) > 0) {
                            writeJson(resp, "{\"success\":false,\"message\":\"Số lượng (" + channelStock + ") vượt quá tồn kho khả dụng (" + (int)availableQty + ").\"}");
                            return;
                        }
                        cp.setChannelStock(channelStock);
                    } catch (NumberFormatException e) {
                        writeJson(resp, "{\"success\":false,\"message\":\"Số lượng không hợp lệ.\"}");
                        return;
                    }
                }
                String descParam = req.getParameter("description");
                if (descParam != null) cp.setDescription(descParam);
                String shortDescParam = req.getParameter("shortDescription");
                if (shortDescParam != null) cp.setShortDescription(shortDescParam);
                String brandParam = req.getParameter("brand");
                if (brandParam != null && !brandParam.isBlank()) cp.setBrand(brandParam);
                String weightParam = req.getParameter("weight");
                if (weightParam != null && !weightParam.isBlank()) {
                    cp.setWeightKg(Double.parseDouble(weightParam));
                }
                String dimsParam = req.getParameter("dimensions");
                if (dimsParam != null && !dimsParam.isBlank()) cp.setDimensions(dimsParam);
                String skuParam = req.getParameter("sellerSku");
                if (skuParam != null && !skuParam.isBlank()) cp.setSellerSku(skuParam);

                // 3) Persist draft to DB so the service can read it back.
                // The DAO now persists ALL fields (seller_sku, description, short_description, brand,
                // dimensions, weight_kg) so no save/restore needed — cp already has correct values.
                if (cp.getId() > 0) {
                    cpDao.update(cp);
                } else {
                    cpDao.insert(cp);
                    cp = cpDao.findByProductAndChannel(productId, channelId);
                }

                // 4) Wizard-uploaded images (pipe-separated). These override the
                //    WMS product_images set for this push only — they're passed
                //    straight to /image/migrate via the service, not persisted.
                //    base64 originals are sent as fallback when migration fails (e.g. E304 for internal URLs).
                List<String> customImageUrls = new java.util.ArrayList<>();
                List<String> customImageBase64s = new java.util.ArrayList<>();
                String imageUrlsParam = req.getParameter("imageUrls");
                String imageBase64sParam = req.getParameter("imageBase64s");
                if ((imageUrlsParam != null && !imageUrlsParam.isBlank()) || (imageBase64sParam != null && !imageBase64sParam.isBlank())) {
                    String[] urlParts = (imageUrlsParam != null && !imageUrlsParam.isBlank())
                            ? imageUrlsParam.split("\\|", -1) : new String[0];
                    String[] b64Parts = (imageBase64sParam != null && !imageBase64sParam.isBlank())
                            ? imageBase64sParam.split("\\|", -1) : new String[0];
                    int maxLen = Math.max(urlParts.length, b64Parts.length);
                    for (int i = 0; i < maxLen; i++) {
                        String trimmedUrl = (i < urlParts.length) ? urlParts[i].trim() : "";
                        String b64 = (i < b64Parts.length) ? b64Parts[i].trim() : null;
                        if (!trimmedUrl.isEmpty()) {
                            customImageUrls.add(toAbsoluteUrl(req, trimmedUrl));
                            customImageBase64s.add((b64 != null && !b64.isEmpty()) ? b64 : null);
                        } else if (b64 != null && !b64.isEmpty()) {
                            customImageUrls.add("");
                            customImageBase64s.add(b64);
                        }
                    }
                }

                    PushResult r;
                try {
                    LOGGER.info("pushProduct START: channelId=" + channelId + " productId=" + productId);
                    long t0 = System.currentTimeMillis();
                    String platform = ch.getPlatform();
                    if ("Website".equalsIgnoreCase(platform)) {
                        WebsiteProductService wps = new WebsiteProductService();
                        boolean ok = wps.pushProduct(ch, productId, customImageUrls);
                        if (ok) {
                            r = PushResult.success(String.valueOf(productId), null, null);
                        } else {
                            r = PushResult.failure("PUSH_FAILED", "Không thể kết nối hoặc đẩy sản phẩm sang Website.");
                        }
                    } else if ("Shopee".equalsIgnoreCase(platform) || "Tiktok".equalsIgnoreCase(platform) || "TikTok".equalsIgnoreCase(platform)) {
                        r = PushResult.failure("NOT_IMPLEMENTED",
                                "Kênh " + platform + " chưa hỗ trợ đẩy sản phẩm từ giao diện này. Vui lòng liên hệ Admin.");
                    } else {
                        r = new LazadaProductService().pushProduct(
                                ch, productId, customImageUrls, customImageBase64s, cp);
                    }
                    long elapsed = System.currentTimeMillis() - t0;
                    LOGGER.info("pushProduct DONE in " + elapsed + "ms: success=" + r.success + " code=" + r.code);
                } catch (Exception ex) {
                    LOGGER.log(Level.WARNING, "pushProduct THREW", ex);
                    r = PushResult.failure("EXCEPTION",
                        ex.getClass().getSimpleName() + ": " + ex.getMessage());
                }
                String json = renderPushResultJson(r);
                LOGGER.info("push response JSON (length=" + json.length() + "): " + json.substring(0, Math.min(300, json.length())));
                writeJson(resp, json);
            } catch (NumberFormatException e) {
                LOGGER.warning("channel-products push: invalid channelId or productId: " + e.getMessage());
                writeJson(resp, "{\"success\":false,\"message\":\"Invalid channel or product: " + esc(e.getMessage()) + "\"}");
            } catch (Exception e) {
                LOGGER.log(Level.WARNING,
                        "channel-products push: failed", e);
                writeJson(resp, "{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}");
            }
        } else if ("pushAllToWebsite".equals(action)) {
            // "Push tất cả sản phẩm" — batch push every product to the Website channel via
            // WebsiteProductService.pushProductBatch() (1 HTTP call instead of N). That method
            // existed already but had zero callers anywhere in the codebase until this action.
            try {
                int channelId = Integer.parseInt(req.getParameter("channelId"));
                Channel ch = new ChannelDAO().findById(channelId);
                if (ch == null) {
                    writeJson(resp, "{\"success\":false,\"message\":\"Channel not found\"}");
                    return;
                }
                if (!"Website".equalsIgnoreCase(ch.getPlatform())) {
                    writeJson(resp, "{\"success\":false,\"message\":\"Chỉ áp dụng cho kênh Website.\"}");
                    return;
                }

                List<Product> allProducts = productService.findAll();
                List<Integer> productIds = new java.util.ArrayList<>();
                for (Product p : allProducts) {
                    productIds.add(p.getProductId());
                }

                LOGGER.info("pushAllToWebsite START: channelId=" + channelId + " totalProducts=" + productIds.size());
                long t0 = System.currentTimeMillis();
                int successCount = new WebsiteProductService().pushProductBatch(ch, productIds);
                long elapsed = System.currentTimeMillis() - t0;
                LOGGER.info("pushAllToWebsite DONE in " + elapsed + "ms: success=" + successCount + "/" + productIds.size());

                writeJson(resp, "{\"success\":true,\"message\":\"Đã đẩy " + successCount + "/" + productIds.size()
                        + " sản phẩm sang Website.\",\"successCount\":" + successCount
                        + ",\"total\":" + productIds.size() + "}");
            } catch (NumberFormatException e) {
                writeJson(resp, "{\"success\":false,\"message\":\"Invalid channelId\"}");
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "channel-products pushAllToWebsite: failed", e);
                writeJson(resp, "{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}");
            }
        } else if ("getProductDetail".equals(action)) {
            handleGetProductDetail(req, resp);
        } else if ("delete".equals(action)) {
            try {
                String idStr = req.getParameter("id");
                int id = 0;
                try {
                    id = Integer.parseInt(idStr);
                } catch (NumberFormatException e) {
                    writeJson(resp, "{\"success\":true,\"message\":\"Đã xóa sản phẩm khỏi danh sách.\"}");
                    return;
                }
                ChannelProduct cp = new ChannelProductDAO().findById(id);
                if (cp == null) {
                    writeJson(resp, "{\"success\":true,\"message\":\"Sản phẩm kênh không còn tồn tại trong cơ sở dữ liệu, đã dọn dẹp khỏi danh sách.\"}");
                    return;
                }
                Channel ch = new ChannelDAO().findById(cp.getChannelId());
                if (ch == null) {
                    new ChannelProductDAO().delete(id);
                    writeJson(resp, "{\"success\":true,\"message\":\"Đã xóa sản phẩm kênh khỏi hệ thống.\"}");
                    return;
                }
                LazadaProductService.DeleteResult r;
                if ("Website".equalsIgnoreCase(ch.getPlatform())) {
                    WebsiteProductService wps = new WebsiteProductService();
                    boolean ok = wps.deleteProduct(ch, id);
                    if (ok) {
                        r = new LazadaProductService.DeleteResult(true, "0", "Xóa sản phẩm khỏi Website thành công!");
                    } else {
                        r = new LazadaProductService.DeleteResult(false, "DELETE_FAILED", "Không thể gỡ sản phẩm khỏi Website.");
                    }
                } else {
                    r = new LazadaProductService().deleteProduct(ch, id);
                }
                if (r.success) {
                    writeJson(resp, "{\"success\":true,\"message\":\"" + esc(r.message) + "\"}");
                } else {
                    writeJson(resp, "{\"success\":false,\"message\":\"Xóa thất bại: " + esc(r.message) + "\"}");
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "channel-products delete failed", e);
                writeJson(resp, "{\"success\":false,\"message\":\"Lỗi: " + esc(e.getMessage()) + "\"}");
            }
        } else if ("edit".equals(action)) {
            try {
                int id = Integer.parseInt(req.getParameter("id"));
                BigDecimal price = new BigDecimal(req.getParameter("price"));
                String description = req.getParameter("description");

                ChannelProductDAO cpDao = new ChannelProductDAO();
                ChannelProduct cp = cpDao.findById(id);
                if (cp == null) {
                    writeJson(resp, "{\"success\":false,\"message\":\"Sản phẩm kênh không tồn tại.\"}");
                    return;
                }
                Channel ch = new ChannelDAO().findById(cp.getChannelId());
                if (ch == null) {
                    writeJson(resp, "{\"success\":false,\"message\":\"Không tìm thấy kênh cấu hình.\"}");
                    return;
                }
                Product prod = new ProductDAO().findById(cp.getProductId());

                // ── 1. Category & brand from modal ────────────────────────────
                String lzCatParam = req.getParameter("lazadaCategoryId");
                if (lzCatParam != null && !lzCatParam.isBlank()) {
                    try {
                        long lzCatId = Long.parseLong(lzCatParam.trim());
                        cpDao.updateLazadaCategoryId(cp.getProductId(), cp.getChannelId(), lzCatId);
                        cp.setLazadaCategoryId(lzCatId);
                    } catch (NumberFormatException ignored) {}
                }
                String brandIdParam = req.getParameter("brandId");
                if (brandIdParam != null && !brandIdParam.isBlank()) {
                    try {
                        long brandIdVal = Long.parseLong(brandIdParam.trim());
                        cpDao.updateBrandId(cp.getProductId(), cp.getChannelId(), brandIdVal);
                        cp.setBrandId(brandIdVal);
                    } catch (NumberFormatException ignored) {}
                }

                // ── 2. Override fields from modal ─────────────────────────────
                String qtyParam = req.getParameter("quantity");
                if (qtyParam != null && !qtyParam.isBlank()) {
                    cp.setChannelStock(new java.math.BigDecimal(qtyParam));
                }
                String weightParam = req.getParameter("weight");
                if (weightParam != null && !weightParam.isBlank()) {
                    cp.setWeightKg(Double.parseDouble(weightParam));
                }
                String dimsParam = req.getParameter("dimensions");
                if (dimsParam != null && !dimsParam.isBlank()) cp.setDimensions(dimsParam);
                String skuParam = req.getParameter("sellerSku");
                if (skuParam != null && !skuParam.isBlank()) cp.setSellerSku(skuParam);
                String brandParam = req.getParameter("brand");
                if (brandParam != null && !brandParam.isBlank()) cp.setBrand(brandParam);
                String shortDescParam = req.getParameter("shortDescription");
                if (shortDescParam != null && !shortDescParam.isBlank()) cp.setShortDescription(shortDescParam);
                // description: set last so the service reads the right value
                if (description != null) cp.setDescription(description);
                // Only use description as fallback for shortDescription if no explicit shortDescription was provided
                if (shortDescParam == null || shortDescParam.isBlank()) {
                    cp.setShortDescription(description);
                }
                if (price != null) cp.setChannelPrice(price);

                // ── 3. Persist to DB before Lazada call ──────────────────────
                // BRAND: nếu chưa set brand_id nào, mặc định No Brand (30768).
                // Lazada yêu cầu brand_id số, không chỉ text brand.
                if ((cp.getBrandId() == null || cp.getBrandId() <= 0)
                        && (cp.getBrand() == null || cp.getBrand().isBlank())) {
                    cp.setBrandId(LazadaProductService.NO_BRAND_LAZADA_ID);
                    cp.setBrand("No Brand");
                    cpDao.updateBrandId(cp.getProductId(), cp.getChannelId(), LazadaProductService.NO_BRAND_LAZADA_ID);
                }
                cpDao.update(cp);

                // ── 4. Image URLs from modal ─────────────────────────────────
                List<String> imageUrls = new java.util.ArrayList<>();
                List<String> imageBase64s = new java.util.ArrayList<>();
                String imageUrlsParam = req.getParameter("imageUrls");
                String imageBase64sParam = req.getParameter("imageBase64s");
                if ((imageUrlsParam != null && !imageUrlsParam.isBlank()) || (imageBase64sParam != null && !imageBase64sParam.isBlank())) {
                    String[] urlParts = (imageUrlsParam != null && !imageUrlsParam.isBlank())
                            ? imageUrlsParam.split("\\|", -1) : new String[0];
                    String[] b64Parts = (imageBase64sParam != null && !imageBase64sParam.isBlank())
                            ? imageBase64sParam.split("\\|", -1) : new String[0];
                    int maxLen = Math.max(urlParts.length, b64Parts.length);
                    for (int i = 0; i < maxLen; i++) {
                        String trimmedUrl = (i < urlParts.length) ? urlParts[i].trim() : "";
                        String b64 = (i < b64Parts.length) ? b64Parts[i].trim() : null;
                        if (!trimmedUrl.isEmpty()) {
                            imageUrls.add(toAbsoluteUrl(req, trimmedUrl));
                            imageBase64s.add((b64 != null && !b64.isEmpty()) ? b64 : null);
                        } else if (b64 != null && !b64.isEmpty()) {
                            imageUrls.add("");
                            imageBase64s.add(b64);
                        }
                    }
                }

                PushResult r;
                if ("Website".equalsIgnoreCase(ch.getPlatform())) {
                    WebsiteProductService wps = new WebsiteProductService();
                    boolean ok = wps.updateProduct(ch, id, cp != null ? cp.getChannelPrice() : null, cp != null ? cp.getDescription() : null);
                    if (ok) {
                        r = PushResult.success(cp != null ? cp.getChannelItemId() : "", null, null);
                    } else {
                        r = PushResult.failure("UPDATE_FAILED", "Không thể cập nhật sản phẩm lên Website storefront.");
                    }
                } else {
                    // Pass price and description from the request so the service sends the
                    // user's fresh values instead of reading stale DB values.
                    // The 3rd arg (cpFromServlet) is null because all fields are already
                    // persisted to DB by cpDao.update(cp) above.
                    r = new LazadaProductService().updateProduct(
                            ch, id, null, price, description, imageUrls, imageBase64s);
                }
                String rendered = renderPushResultJson(r);
                LOGGER.info("=== PUSH RESPONSE === " + rendered);
                writeJson(resp, rendered);
            } catch (NumberFormatException e) {
                writeJson(resp, "{\"success\":false,\"message\":\"Định dạng số hoặc giá bán không hợp lệ.\"}");
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "channel-products edit failed", e);
                writeJson(resp, "{\"success\":false,\"message\":\"Lỗi: " + esc(e.getMessage()) + "\"}");
            }
        } else if ("loadLazadaLeaves".equals(action)) {
            // GET — return cached leaves from lazada_categories (UC-B2C09)
            try {
                int channelId = Integer.parseInt(req.getParameter("channelId"));
                var leaves = new LazadaCategoryDAO().findLeaves(channelId);
                StringBuilder json = new StringBuilder("{\"success\":true,\"leaves\":[");
                for (int i = 0; i < leaves.size(); i++) {
                    var c = leaves.get(i);
                    if (i > 0) json.append(",");
                    String displayName = c.getPath() != null && !c.getPath().isBlank() ? c.getPath() : c.getName();
                    json.append("{\"lazadaCategoryId\":").append(c.getLazadaCategoryId())
                        .append(",\"name\":\"").append(esc(displayName)).append("\"}");
                }
                json.append("],\"total\":").append(leaves.size()).append("}");
                writeJson(resp, json.toString());
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "loadLazadaLeaves failed", e);
                writeJson(resp, "{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}");
            }
        } else if ("getBrands".equals(action)) {
            // Lazada /brand/get — returns valid brand list for the seller's country.
            // Brands are needed in /product/create and /product/update payloads.
            try {
                int channelId = Integer.parseInt(req.getParameter("channelId"));
                Channel ch = new ChannelDAO().findById(channelId);
                if (ch == null) {
                    writeJson(resp, "{\"success\":false,\"message\":\"Channel not found\"}");
                    return;
                }
                String countryCode = "vn";
                String apiUrl = ch.getApiUrl();
                if (apiUrl != null && apiUrl.contains(".my")) countryCode = "my";
                else if (apiUrl != null && apiUrl.contains(".id")) countryCode = "id";
                else if (apiUrl != null && apiUrl.contains(".th")) countryCode = "th";
                else if (apiUrl != null && apiUrl.contains(".ph")) countryCode = "ph";
                else if (apiUrl != null && apiUrl.contains(".sg")) countryCode = "sg";
                String respJson = new LazadaChannelGateway().getBrands(ch, countryCode, 100);
                writeJson(resp, "{\"success\":true,\"brandsResponse\":" + respJson + "}");
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "getBrands failed", e);
                writeJson(resp, "{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}");
            }
        } else if ("uploadImageBase64".equals(action)) {
            // UC-B2C09: wizard uploads a base64 image, we save it to disk and
            // return the public server URL. This bypasses Lazada's E302 error
            // when /image/migrate receives data:image URIs directly.
            try {
                String base64Data = req.getParameter("base64");
                String filename = req.getParameter("filename");
                if (base64Data == null || base64Data.isBlank()) {
                    writeJson(resp, "{\"success\":false,\"message\":\"No base64 data\"}");
                    return;
                }

                // Strip data:image/...;base64, prefix if present
                String b64 = base64Data;
                int commaIdx = b64.indexOf(',');
                if (commaIdx >= 0) b64 = b64.substring(commaIdx + 1);
                byte[] imageBytes = Base64.getDecoder().decode(b64);

                // Determine extension from optional filename hint
                String ext = ".jpg";
                if (filename != null && !filename.isBlank()) {
                    String lower = filename.toLowerCase();
                    if (lower.endsWith(".png")) ext = ".png";
                    else if (lower.endsWith(".webp")) ext = ".webp";
                    else if (lower.endsWith(".gif")) ext = ".gif";
                }
                String safeName = UUID.randomUUID().toString().replace("-", "") + ext;

                // Strategy 1: upload directly to imgBB for a truly public URL (Lazada can reach it)
                CatboxImageUploader relay = new CatboxImageUploader();
                String publicUrl = relay.upload(imageBytes, safeName);
                if (publicUrl != null && !publicUrl.isBlank()) {
                    LOGGER.info("uploadImageBase64: imgBB/catbox relay success => " + publicUrl);
                    writeJson(resp, "{\"success\":true,\"url\":\"" + publicUrl + "\"}");
                    return;
                }

                // Strategy 2 (last resort): save locally — this URL will only work if the
                // WMS server is publicly reachable from the outside internet.
                Path uploadRoot = Paths.get(
                        System.getProperty("user.home"), "wms-uploads", "publish-images");
                Files.createDirectories(uploadRoot);
                Path target = uploadRoot.resolve(safeName);
                Files.write(target, imageBytes);
                String localUrl = req.getContextPath() + "/publish-images/" + safeName;
                LOGGER.warning("uploadImageBase64: no public relay succeeded, falling back to local URL: " + localUrl);
                writeJson(resp, "{\"success\":true,\"url\":\"" + localUrl + "\"}");
            } catch (java.lang.IllegalArgumentException e) {
                writeJson(resp, "{\"success\":false,\"message\":\"Invalid base64 data: " + esc(e.getMessage()) + "\"}");
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "uploadImageBase64 failed", e);
                writeJson(resp, "{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}");
            }
        } else if ("getCategoryMapping".equals(action)) {
            // GET — find Lazada leaf mapped to a WMS category (UC-B2C09)
            try {
                int channelId = Integer.parseInt(req.getParameter("channelId"));
                int wmsCategoryId = Integer.parseInt(req.getParameter("wmsCategoryId"));
                var mappings = new CategoryMappingDAO().findPrimaryForWms(channelId, wmsCategoryId);
                if (mappings.isEmpty()) {
                    writeJson(resp, "{\"success\":true,\"found\":false,\"mappings\":[]}");
                    return;
                }
                StringBuilder json = new StringBuilder("{\"success\":true,\"found\":true,\"mappings\":[");
                for (int i = 0; i < mappings.size(); i++) {
                    var m = mappings.get(i);
                    if (i > 0) json.append(",");
                    json.append("{\"lazadaCategoryId\":").append(m.getLazadaCategoryId())
                        .append(",\"name\":\"").append(esc(m.getLazadaName())).append("\"}");
                }
                json.append("]}");
                writeJson(resp, json.toString());
            } catch (NumberFormatException e) {
                writeJson(resp, "{\"success\":false,\"message\":\"Invalid channelId or wmsCategoryId\"}");
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "getCategoryMapping failed", e);
                writeJson(resp, "{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}");
            }
        } else if ("syncLazadaCategories".equals(action)) {
            // POST — pull /category/tree/get and store leaves (UC-B2C09)
            try {
                int channelId = Integer.parseInt(req.getParameter("channelId"));
                Channel ch = new ChannelDAO().findById(channelId);
                if (ch == null) {
                    writeJson(resp, "{\"success\":false,\"message\":\"Channel not found\"}");
                    return;
                }
                LazadaCategorySyncService svc = new LazadaCategorySyncService();
                SyncResult r = svc.syncCategories(ch);
                writeJson(resp, "{\"success\":" + r.success
                        + ",\"count\":" + r.count
                        + ",\"message\":\"" + esc(r.message) + "\"}");
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "syncLazadaCategories failed", e);
                writeJson(resp, "{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}");
            }
        } else if ("debugPush".equals(action)) {
            // TEMP: bypass wizard, call pushProduct directly, return raw Lazada response
            try {
                int channelId = Integer.parseInt(req.getParameter("channelId"));
                int productId = Integer.parseInt(req.getParameter("productId"));
                Channel ch = new ChannelDAO().findById(channelId);
                if (ch == null) {
                    writeJson(resp, "{\"success\":false,\"message\":\"Channel not found\"}");
                    return;
                }
                LOGGER.info("=== DEBUG PUSH START channel=" + channelId + " product=" + productId + " ===");
                PushResult r;
                if ("Website".equalsIgnoreCase(ch.getPlatform())) {
                    WebsiteProductService wps = new WebsiteProductService();
                    boolean ok = wps.pushProduct(ch, productId);
                    if (ok) {
                        r = PushResult.success(String.valueOf(productId), null, null);
                    } else {
                        r = PushResult.failure("PUSH_FAILED", "Không thể đẩy sản phẩm lên Website.");
                    }
                } else {
                    r = new LazadaProductService().pushProduct(ch, productId);
                }
                LOGGER.info("=== DEBUG PUSH END success=" + r.success + " code=" + r.code
                        + " msg=" + r.message + " ===");
                writeJson(resp, renderPushResultJson(r));
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "debugPush failed", e);
                writeJson(resp, "{\"success\":false,\"message\":\"" + esc(e.toString()) + "\"}");
            }
        } else {
            writeJson(resp, "{\"success\":false,\"message\":\"Unknown action\"}");
        }
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", " ").replace("\r", " ");
    }

    private static String toAbsoluteUrl(HttpServletRequest req, String url) {
        if (url == null || url.isBlank()) return url;
        if (url.startsWith("http://") || url.startsWith("https://")) {
            // Upgrade http to https for isp392.click as required by Lazada API
            if (url.startsWith("http://isp392.click")) {
                return "https://" + url.substring("http://".length());
            }
            return url;
        }
        String forwardedProto = req.getHeader("X-Forwarded-Proto");
        String host = req.getServerName();
        String scheme = (forwardedProto != null && !forwardedProto.isBlank()) ? forwardedProto : req.getScheme();
        if ("isp392.click".equalsIgnoreCase(host) || "443".equals(req.getHeader("X-Forwarded-Port"))) {
            scheme = "https";
        }
        int port = req.getServerPort();
        boolean standardPort = "https".equalsIgnoreCase(scheme) ? (port == 443 || port == 80) : (port == 80);
        String base = scheme + "://" + host + (standardPort ? "" : ":" + port);
        String contextPath = req.getContextPath();
        if (url.startsWith("/")) {
            if (contextPath != null && !contextPath.isEmpty() && !"/".equals(contextPath) && url.startsWith(contextPath)) {
                return base + url;
            }
            return base + contextPath + url;
        }
        return base + contextPath + "/" + url;
    }

    /** Serializes a {@link PushResult} to JSON, including validation + field errors. */
    private static String renderPushResultJson(PushResult r) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper M = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.node.ObjectNode root = M.createObjectNode();
            root.put("success", r.success);
            root.put("code", r.code == null ? "" : r.code);
            root.put("message", r.message == null ? "" : r.message);
            root.put("itemId", r.itemId == null ? "" : r.itemId);
            root.put("skuId", r.skuId == null ? "" : r.skuId);
            if (r.validationErrors != null && !r.validationErrors.isEmpty()) {
                var arr = M.createArrayNode();
                for (var ve : r.validationErrors) {
                    var node = M.createObjectNode();
                    node.put("field", ve.field == null ? "" : ve.field);
                    node.put("code", ve.code == null ? "" : ve.code);
                    node.put("message", ve.viMessage == null ? "" : ve.viMessage);
                    arr.add(node);
                }
                root.set("validationErrors", arr);
            }
            if (r.fieldErrors != null && !r.fieldErrors.isEmpty()) {
                var arr = M.createArrayNode();
                for (var fe : r.fieldErrors) {
                    var node = M.createObjectNode();
                    node.put("field", fe.fieldHint == null ? (fe.field == null ? "" : fe.field) : fe.fieldHint);
                    node.put("message", fe.viMessage == null ? "" : fe.viMessage);
                    arr.add(node);
                }
                root.set("fieldErrors", arr);
            }
            return M.writeValueAsString(root);
        } catch (Exception e) {
            LOGGER.warning("renderPushResultJson failed: " + e.getMessage());
            return "{\"success\":false,\"message\":\"JSON render error: " + esc(e.getMessage()) + "\"}";
        }
    }
     private void handleGetProductDetail(HttpServletRequest req, HttpServletResponse resp)
            throws jakarta.servlet.ServletException, java.io.IOException {
        try {
            int productId = Integer.parseInt(req.getParameter("productId"));
            Product p = productService.findById(productId);
            if (p == null) {
                writeJson(resp, "{\"success\":false,\"message\":\"Không tìm thấy Master SKU.\"}");
                return;
            }

            List<String> imageUrls = new java.util.ArrayList<>();
            String description = null;
            ChannelProduct cp = null;
            Channel ch = null;

            String cpIdParam = req.getParameter("channelProductId");
            if (cpIdParam != null && !cpIdParam.isBlank()) {
                try {
                    int cpId = Integer.parseInt(cpIdParam);
                    cp = new ChannelProductDAO().findById(cpId);
                    if (cp != null && cp.getChannelItemId() != null && !cp.getChannelItemId().isBlank()) {
                        ch = new ChannelDAO().findById(cp.getChannelId());
                        if (ch != null) {
                            LazadaProductService lazService = new LazadaProductService();
                            List<String> lazadaImages = lazService.fetchLazadaExistingImages(ch, cp.getChannelItemId());
                            if (lazadaImages != null && !lazadaImages.isEmpty()) {
                                imageUrls.addAll(lazadaImages);
                            }
                            // Fetch description from Lazada when DB description and shortDescription are both null
                            String masterDesc = p.getShortDescription();
                            String channelDesc = cp.getDescription();
                            String channelShortDesc = cp.getShortDescription();
                            if ((masterDesc == null || masterDesc.isBlank())
                                    && (channelDesc == null || channelDesc.isBlank())
                                    && (channelShortDesc == null || channelShortDesc.isBlank())) {
                                description = lazService.fetchLazadaDescription(ch, cp.getChannelItemId());
                            }
                        }
                    }
                } catch (Exception e) {
                    LOGGER.warning("Failed to fetch live Lazada data for detail: " + e.getMessage());
                }
            }

            // Fallback: master product images
            if (imageUrls.isEmpty()) {
                List<ProductImage> images = new ProductImageDAO().findByProductId(productId);
                for (ProductImage img : images) {
                    if (img.getImageUrl() != null && !img.getImageUrl().isBlank()) {
                        imageUrls.add(img.getImageUrl());
                    }
                }
            }

            // Resolve description: prefer explicit fields, fall back to Lazada or empty
            if (description == null || description.isBlank()) {
                description = p.getShortDescription();
                if (description == null || description.isBlank()) {
                    if (cp != null && cp.getDescription() != null && !cp.getDescription().isBlank()) {
                        description = cp.getDescription();
                    } else if (cp != null && cp.getShortDescription() != null && !cp.getShortDescription().isBlank()) {
                        description = cp.getShortDescription();
                    }
                }
            }
            if (description == null) description = "";

            Map<String, Object> result = new java.util.HashMap<>();
            result.put("success", true);
            result.put("productId", productId);
            result.put("description", description);
            result.put("images", imageUrls);
            writeJson(resp, JsonUtil.toJson(result));
        } catch (Exception e) {
            writeJson(resp, "{\"success\":false,\"message\":\"" + esc(e.getMessage()) + "\"}");
        }
    }
}
