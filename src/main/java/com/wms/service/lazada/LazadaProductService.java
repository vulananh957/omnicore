package com.wms.service.lazada;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.dao.ChannelProductDAO;
import com.wms.dao.ProductDAO;
import com.wms.dao.ProductImageDAO;
import com.wms.dao.PushErrorDAO;
import com.wms.model.Channel;
import com.wms.model.ChannelProduct;
import com.wms.model.Product;
import com.wms.model.ProductImage;
import com.wms.service.channel.ChannelRegistry;
import com.wms.service.channel.ChannelSyncAudit;
import com.wms.service.channel.ChannelGateway;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * LazadaProductService — UC-B2C01 (Pull & Map) and UC-B2C02 (Push).
 *
 * Two main operations:
 *  - pullProducts(): GET /products/get, then upsert into channel_products
 *    and log unmapped SKUs to mapping_exceptions.
 *  - pushProduct():  POST /product/create for a new SKU.
 *
 * Higher-level code (LazadaProductSyncScheduler, SalesChannelProductsServlet)
 * calls these methods. The scheduler invokes pullProducts() on a cron;
 * the servlet uses pushProduct() from the UI form.
 */
public class LazadaProductService {

    private static final Logger LOGGER = Logger.getLogger(LazadaProductService.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();
    /**
     * Lazada VN brand ID for "No Brand". Used as default when the seller hasn't picked
     * a brand. Lazada requires a numeric brand_id on most categories — text-only
     * "brand" field is deprecated and many categories reject products without it.
     */
    public static final long NO_BRAND_LAZADA_ID = 30768;

    private final ChannelGateway gateway;
    private final ImgbbImageUploader imgbb = new ImgbbImageUploader();
    private final CatboxImageUploader catbox = new CatboxImageUploader();
    private final ChannelProductDAO channelProductDAO = new ChannelProductDAO();
    private final ProductDAO productDAO = new ProductDAO();
    private final ProductImageDAO productImageDAO = new ProductImageDAO();
    private final PushErrorDAO pushErrorDAO = new PushErrorDAO();
    private final LazadaImageMigrator imageMigrator = new LazadaImageMigrator();
    private final LazadaProductPayloadBuilder payloadBuilder = new LazadaProductPayloadBuilder();

    public LazadaProductService() {
        this.gateway = ChannelRegistry.get("Lazada");
        if (this.gateway == null) {
            throw new IllegalStateException(
                    "No ChannelGateway registered for platform 'Lazada'");
        }
    }

    // ── Pull & Map (UC-B2C01) ─────────────────────────────────

    /**
     * Pulls a single Lazada product by its item_id and upserts it into channel_products.
     * Use this when you know the Lazada item_id but the product wasn't returned by
     * the paginated catalog (e.g. inactive/archived items excluded by filter=live).
     *
     * @param channel the Lazada channel
     * @param itemId  Lazada's item_id (from the product URL)
     * @return PullResult with upserted=1 on success, 0 otherwise
     */
    public PullResult pullProductByItemId(Channel channel, String itemId) {
        String response;
        try {
            response = gateway.getProductByItemId(channel, itemId);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "pullProductByItemId: failed for itemId=" + itemId, e);
            ChannelSyncAudit.logFailure(channel.getChannelId(),
                    "PRODUCT_PULL", "itemId=" + itemId, null, null, e.getMessage());
            return new PullResult(false, 0, 0, 0, e.getMessage());
        }
        ChannelSyncAudit.logSuccess(channel.getChannelId(),
                "PRODUCT_PULL", "itemId=" + itemId, 200, null, response, 0);
        try {
            JsonNode root = MAPPER.readTree(response);
            JsonNode data = root.path("data");
            if (data.isMissingNode() || data.isNull()) {
                return new PullResult(false, 0, 0, 0, "No product data returned for itemId=" + itemId);
            }
            LOGGER.info("pullProductByItemId: parsing itemId=" + itemId + " sellerSku=" + data.path("seller_sku"));
            UpsertOutcome o = upsertOne(data, channel);
            LOGGER.info("pullProductByItemId: upsert result upserted=" + o.upserted + " unmapped=" + o.unmapped);
            return new PullResult(true, o.upserted ? 1 : 0, 1, o.unmapped ? 1 : 0, null);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "pullProductByItemId: parse failed for itemId=" + itemId, e);
            return new PullResult(false, 0, 0, 0, e.getMessage());
        }
    }

    /**
     * Pulls Lazada's product list and upserts every item into channel_products.
     * Lazada items whose seller_sku matches a known product.sku_code get the
     * mapping; everything else is left in PENDING and the seller_sku is logged
     * to mapping_exceptions so Sales can resolve it from the UI.
     *
     * @param channel the Lazada channel
     * @return sync summary
     */
    public PullResult pullProducts(Channel channel) {
        // Clear/resolve old unresolved catalog exceptions before pulling the latest catalog
        new com.wms.dao.SkuMappingDAO().resolveCatalogExceptions(channel.getChannelId());

        int pageNumber = 1;
        final int pageSize = 50;
        int totalPulled = 0;
        int totalUpserted = 0;
        int totalUnmapped = 0;
        String lastError = null;
        boolean ok = true;

        for (int page = 0; page < 20; page++) { // hard-cap at 20 pages = 1000 products
            long t0 = System.currentTimeMillis();
            String response = null;
            try {
                response = gateway.listProducts(channel, pageNumber, pageSize);
            } catch (Exception e) {
                lastError = e.getMessage();
                ok = false;
                ChannelSyncAudit.logFailure(channel.getChannelId(),
                        "PRODUCT_PULL", "page=" + pageNumber, null, null, lastError);
                break;
            }
            long dt = System.currentTimeMillis() - t0;
            ChannelSyncAudit.logSuccess(channel.getChannelId(), "PRODUCT_PULL",
                    "page=" + pageNumber, 200, null, response, dt);

            JsonNode root;
            try {
                root = MAPPER.readTree(response);
            } catch (Exception e) {
                lastError = "Invalid JSON from /products/get: " + e.getMessage();
                ok = false;
                break;
            }
            JsonNode data = root.path("data");
            JsonNode products = data.path("products");
            if (!products.isArray() || products.isEmpty()) {
                break;
            }
            int pageCount = products.size();
            totalPulled += pageCount;

            for (JsonNode product : products) {
                UpsertOutcome o = upsertOne(product, channel);
                if (o.upserted) totalUpserted++;
                if (o.unmapped)  totalUnmapped++;
            }
            // Lazada's response doesn't always include "total" or "has_next";
            // we use a conservative heuristic: if the page is short, stop.
            if (pageCount < pageSize) break;
            pageNumber++;
        }
        return new PullResult(ok, totalPulled, totalUpserted, totalUnmapped, lastError);
    }

    /** Upserts a single Lazada product into channel_products. */
    private UpsertOutcome upsertOne(JsonNode product, Channel channel) {
        String sellerSku = product.path("seller_sku").asText().trim();
        String skuId = "";  // Lazada's internal SkuId for stock updates
        if (sellerSku.isEmpty()) {
            JsonNode skus = product.path("skus");
            if (skus.isArray() && !skus.isEmpty()) {
                JsonNode firstSku = skus.get(0);
                String nested = firstSku.path("SellerSku").asText();
                sellerSku = (nested.isEmpty() ? firstSku.path("seller_sku").asText() : nested).trim();
                // Extract SkuId from nested structure (required for stock updates post-Nov 2023)
                String nestedSkuId = firstSku.path("SkuId").asText();
                if (!nestedSkuId.isEmpty()) skuId = nestedSkuId;
            }
        }
        String itemId    = product.path("item_id").asText().trim();
        if (sellerSku.isEmpty() && itemId.isEmpty()) {
            return new UpsertOutcome(false, false);
        }
        if (sellerSku.isEmpty()) sellerSku = itemId;

        // Try to match seller_sku -> product.sku_code (master SKU on our side)
        Product matched = productDAO.findBySkuCode(sellerSku);
        if (matched == null) {
            // Check if there is an active/pending mapping in sku_mappings table
            com.wms.model.SkuMapping mapping = new com.wms.dao.SkuMappingDAO()
                    .findMappingByChannelAndExternalSku(channel.getChannelId(), sellerSku);
            if (mapping == null && !sellerSku.equals(itemId)) {
                mapping = new com.wms.dao.SkuMappingDAO()
                        .findMappingByChannelAndExternalSku(channel.getChannelId(), itemId);
            }
            if (mapping != null) {
                matched = productDAO.findById(mapping.getSkuId());
            }
        }

        if (matched == null) {
            // Retrieve actual product name from Lazada API response
            String productName = product.path("attributes").path("name").asText().trim();
            if (productName.isEmpty()) {
                productName = "Sản phẩm sàn (" + sellerSku + ")";
            }
            // No match — log exception so Sales sees it on the mapping page
            new com.wms.dao.SkuMappingDAO().logMappingException(
                    channel.getChannelId(), sellerSku, null, productName);
            return new UpsertOutcome(false, true);
        } else {
            // Ensure there is a record in the sku_mappings table for the auto-matched SKU
            com.wms.dao.SkuMappingDAO mappingDAO = new com.wms.dao.SkuMappingDAO();
            com.wms.model.SkuMapping existingMapping = mappingDAO
                    .findMappingByChannelAndExternalSku(channel.getChannelId(), sellerSku);
            if (existingMapping == null && !sellerSku.equals(itemId)) {
                existingMapping = mappingDAO.findMappingByChannelAndExternalSku(channel.getChannelId(), itemId);
            }
            if (existingMapping == null) {
                com.wms.model.SkuMapping newMapping = new com.wms.model.SkuMapping();
                newMapping.setSkuId(matched.getProductId());
                newMapping.setChannelId(channel.getChannelId());
                newMapping.setExternalSku(itemId);
                newMapping.setSellerSku(sellerSku);
                newMapping.setSyncStatus("SYNCED");
                newMapping.setLastSyncAt(java.time.LocalDateTime.now());
                mappingDAO.insert(newMapping);
            }
        }

        String priceStr = product.path("price").asText();
        if (priceStr.isEmpty()) priceStr = product.path("special_price").asText();
        BigDecimal price = new BigDecimal(priceStr.isEmpty() ? "0" : priceStr);
        String stockStr = product.path("quantity").asText();
        if (stockStr.isEmpty()) stockStr = product.path("stock").asText();
        BigDecimal stock = new BigDecimal(stockStr.isEmpty() ? "0" : stockStr);
        if (stock.signum() < 0) stock = BigDecimal.ZERO;

        ChannelProduct existing = channelProductDAO.findByChannelSku(
                channel.getChannelId(), sellerSku);

        if (existing == null) {
            // Look up by product_id+channel (the UNIQUE KEY) in case channel_sku_code
            // was empty but the product is already there.
            existing = channelProductDAO.findByProductAndChannel(
                    matched.getProductId(), channel.getChannelId());
        }

        if (existing == null) {
            ChannelProduct cp = new ChannelProduct();
            cp.setChannelId(channel.getChannelId());
            cp.setProductId(matched.getProductId());
            cp.setChannelSkuCode(sellerSku);
            BigDecimal defaultInsertPrice = (price != null && price.signum() > 0) ? price
                    : (matched.getBasePrice() != null && matched.getBasePrice() > 0 ? BigDecimal.valueOf(matched.getBasePrice()) : BigDecimal.ZERO);
            cp.setChannelPrice(defaultInsertPrice);
            cp.setChannelStock(stock);
            cp.setStatus("ACTIVE");
            cp.setListedAt(java.time.LocalDateTime.now());
            boolean inserted = channelProductDAO.insert(cp);
            if (inserted) {
                // Persist Lazada item_id + sku_id so /product/stock/sellable/update can target it later
                ChannelProduct fresh = channelProductDAO.findByChannelSku(
                        channel.getChannelId(), sellerSku);
                if (fresh != null) {
                    channelProductDAO.setChannelItemId(
                            fresh.getId(), itemId.isEmpty() ? null : itemId, skuId);
                }
            }
            return new UpsertOutcome(inserted, false);
        } else {
            // Update price + stock only; don't touch status / listed_at if price is 0
            if (price != null && price.signum() > 0) {
                channelProductDAO.syncPrice(existing.getId(), price);
            }
            channelProductDAO.syncStock(existing.getId(), stock);
            channelProductDAO.setChannelItemId(existing.getId(),
                    itemId.isEmpty() ? null : itemId, skuId);
            return new UpsertOutcome(true, false);
        }
    }

    // ── Push (UC-B2C02) ───────────────────────────────────────

    /**
     * UC-B2C09 / UC-B2C02: 6-step push pipeline.
     *
     * <pre>
     *   0. Load product + channel_product + images
     *   1. Client-side validation (LazadaProductPayloadBuilder)
     *   2. Migrate external images via /images/migrate (cache-aware)
     *   3. Build final Lazada payload
     *   4. POST /product/create via Gateway
     *   5. Persist item_id / sku_id on success, or error details on failure
     * </pre>
     *
     * <p>All HTTP goes through {@link ChannelGateway}; this service never
     * builds HTTP requests or speaks Lazada's signing protocol directly.</p>
     *
     * @return PushResult with item_id (success) or list of ValidationError
     *         / parsed field errors (failure).
     */
    public PushResult pushProduct(Channel channel, int productId) {
        return pushProduct(channel, productId, null, null, null);
    }

    /** Convenience overload: call with customImageUrls only (no base64 fallback). */
    public PushResult pushProduct(Channel channel, int productId, List<String> customImageUrls) {
        return pushProduct(channel, productId, customImageUrls, null, null);
    }

    public PushResult pushProduct(Channel channel, int productId,
                                  List<String> customImageUrls,
                                  List<String> customImageBase64s) {
        return pushProduct(channel, productId, customImageUrls, customImageBase64s, null);
    }

    /**
     * Pushes a product to Lazada, optionally overriding the image set with
     * URLs the user uploaded in the publish wizard. When {@code customImageUrls}
     * is non-null and non-empty, those URLs are used (after {@code /image/migrate})
     * instead of the WMS {@code product_images} table.
     *
     * @param customImageUrls    server-side image URLs for migration
     * @param customImageBase64s base64 data URLs as fallback when migration fails
     * @param wizardCp           fully-populated ChannelProduct containing wizard-typed values (price, dimensions, weight, etc.)
     */
    public PushResult pushProduct(Channel channel, int productId,
                                  List<String> customImageUrls,
                                  List<String> customImageBase64s,
                                  ChannelProduct wizardCp) {
        LOGGER.info("LazadaProductService.pushProduct: ENTER productId=" + productId + " channelId=" + channel.getChannelId() + " customUrls=" + (customImageUrls != null ? customImageUrls.size() : "null") + " customBase64s=" + (customImageBase64s != null ? customImageBase64s.size() : "null"));
        // ── Step 0: load context ───────────────────────────────────
        Product p = productDAO.findById(productId);
        if (p == null) {
            return PushResult.failure("NOT_FOUND", "Không tìm thấy sản phẩm: " + productId);
        }
        ChannelProduct cp = wizardCp;
        if (cp == null) {
            cp = channelProductDAO.findByProductAndChannel(
                    productId, channel.getChannelId());
        }
        if (cp == null) {
            // Lazada draft not yet staged — create a skeleton for the push payload
            cp = new ChannelProduct();
            cp.setChannelId(channel.getChannelId());
            cp.setProductId(productId);
            cp.setChannelSkuCode(p.getSkuCode());
            cp.setChannelPrice(BigDecimal.ZERO);
            cp.setChannelStock(BigDecimal.ZERO);
            cp.setStatus("PENDING");
        }
        // Auto-resolve Lazada leaf from category_mappings if wizard didn't pick one.
        // This makes the "Ánh xạ WMS ↔ Lazada" tab pay off: sales curate mappings
        // once, then every push automatically uses the right leaf.
        if (cp.getLazadaCategoryId() == null && p.getCategoryId() != null) {
            try {
                List<com.wms.model.CategoryMapping> primary =
                        new com.wms.dao.CategoryMappingDAO()
                                .findPrimaryForWms(channel.getChannelId(), p.getCategoryId());
                if (!primary.isEmpty()) {
                    cp.setLazadaCategoryId(primary.get(0).getLazadaCategoryId());
                    LOGGER.info("LazadaProductService: auto-resolved Lazada leaf "
                            + cp.getLazadaCategoryId() + " from mapping for WMS cat " + p.getCategoryId());
                    // Persist so future pushes don't need to re-resolve
                    if (cp.getId() > 0) {
                        channelProductDAO.updateLazadaCategoryId(
                                p.getProductId(), channel.getChannelId(), primary.get(0).getLazadaCategoryId());
                    }
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "LazadaProductService: auto-resolve mapping failed", e);
            }
        }
        // Default No Brand (numeric Lazada brand ID) if neither brand_id nor brand name was set.
        // Lazada requires a numeric brand_id on most categories — text-only brand is deprecated.
        if ((cp.getBrandId() == null || cp.getBrandId() <= 0)
                && (cp.getBrand() == null || cp.getBrand().isBlank())) {
            cp.setBrandId(NO_BRAND_LAZADA_ID);
            cp.setBrand("No Brand");
            LOGGER.info("LazadaProductService: defaulting to No Brand (brand_id=" + NO_BRAND_LAZADA_ID + ")");
        }
        // Image set: prefer wizard-uploaded URLs (already on server disk),
        // fall back to WMS product_images table.
        // `customImageBase64s` holds the original base64 data URIs as a fallback
        // when /image/migrate fails (e.g. E304 for internal-network URLs).
        List<String> sourceUrls = new ArrayList<>();
        List<String> sourceBase64s = new ArrayList<>();
        boolean useCustomImages = (customImageUrls != null && !customImageUrls.isEmpty());
        List<ProductImage> images;
        if (useCustomImages) {
            for (int i = 0; i < customImageUrls.size(); i++) {
                String u = customImageUrls.get(i);
                if (u != null && !u.isBlank()) sourceUrls.add(u.trim());
            }
            // base64 list has same size as sourceUrls; guard against index OOB
            if (customImageBase64s != null) {
                for (int i = 0; i < customImageUrls.size() && i < customImageBase64s.size(); i++) {
                    String b64 = customImageBase64s.get(i);
                    if (b64 != null && !b64.isBlank()) sourceBase64s.add(b64.trim());
                    else sourceBase64s.add(null); // placeholder so indices align
                }
            }
            // Pad base64 list to match sourceUrls count
            while (sourceBase64s.size() < sourceUrls.size()) sourceBase64s.add(null);
            // Build a synthetic ProductImage list so payloadBuilder.validate() passes.
            images = new ArrayList<>();
            for (String url : sourceUrls) {
                ProductImage img = new ProductImage();
                img.setImageUrl(url);
                images.add(img);
            }
            LOGGER.info("LazadaProductService.pushProduct: using " + sourceUrls.size()
                    + " wizard-uploaded image(s) for product " + p.getSkuCode());
        } else {
            images = productImageDAO.findByProductId(productId);
            for (ProductImage img : images) {
                if (img.getImageUrl() != null && !img.getImageUrl().isBlank()) {
                    sourceUrls.add(img.getImageUrl());
                }
            }
        }

        // ── Step 1: client-side validation ─────────────────────────────────
        List<LazadaProductPayloadBuilder.ValidationError> vErrs =
                payloadBuilder.validate(p, cp, images);
        if (!vErrs.isEmpty()) {
            // Record on channel row so Sales sees why the draft failed without re-running
            String firstMsg = vErrs.get(0).viMessage;
            try {
                channelProductDAO.recordPushFailure(cp.getId(), "VALIDATION", firstMsg);
            } catch (Exception ignore) { /* id may be 0 on a not-yet-inserted draft */ }
            return PushResult.validation(vErrs);
        }

        // ── Step 2: migrate images (cache-aware, with catbox relay fallback) ──────
        LOGGER.info("LazadaProductService.pushProduct: START migrate images step, sourceUrls=" + sourceUrls.size());
        // 1. Try /images/migrate for server URLs (cached, fast).
        // 2. If migration fails (E304 for internal/ngrok URLs), encode the base64
        //    image as JPEG and upload to catbox.moe — a public HTTPS host that
        //    Lazada can reliably reach — then feed that public URL into migrate.
        List<String> lazadaImageUrls = new ArrayList<>();
        boolean anyUsedRelay = false;
        LOGGER.info("LazadaProductService.pushProduct: calling imageMigrator.migrateImages...");
        try {
            List<String> migrated = imageMigrator.migrateImages(channel, sourceUrls);
            LOGGER.info("LazadaProductService.pushProduct: imageMigrator.migrateImages returned, size=" + migrated.size());
            for (int i = 0; i < migrated.size(); i++) {
                String migratedUrl = migrated.get(i);
                if (migratedUrl != null && !migratedUrl.isBlank()) {
                    lazadaImageUrls.add(migratedUrl);
                } else {
                    // Migration failed — use local file bytes or imgbb/catbox relay fallback.
                    String srcUrl = (i < sourceUrls.size()) ? sourceUrls.get(i) : null;
                    String b64 = (i < sourceBase64s.size()) ? sourceBase64s.get(i) : null;
                    byte[] bytes = fetchOrReadBytes(srcUrl, b64);
                    if (bytes != null && bytes.length > 0) {
                        String publicUrl = imgbb.upload(bytes, "image.jpg");
                        String verified = null;
                        if (publicUrl != null && !publicUrl.isBlank()) {
                            verified = imageMigrator.migrateSingle(channel, publicUrl);
                        }
                        if (verified != null && !verified.isBlank()) {
                            lazadaImageUrls.add(verified);
                            anyUsedRelay = true;
                            LOGGER.info("LazadaProductService: imgbb relay success for image #" + i
                                    + " => " + verified);
                        } else {
                            LOGGER.info("LazadaProductService: imgbb relay failed, trying catbox fallback for image #" + i);
                            String catboxUrl = catbox.upload(bytes, "image.jpg");
                            if (catboxUrl != null && !catboxUrl.isBlank()) {
                                verified = imageMigrator.migrateSingle(channel, catboxUrl);
                            }
                            if (verified != null && !verified.isBlank()) {
                                lazadaImageUrls.add(verified);
                                anyUsedRelay = true;
                                LOGGER.info("LazadaProductService: catbox fallback success for image #" + i
                                        + " => " + verified);
                            } else {
                                LOGGER.warning("LazadaProductService: both imgbb and catbox failed for image #" + i);
                                lazadaImageUrls.add(null);
                            }
                        }
                    } else {
                        lazadaImageUrls.add(null);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "LazadaProductService: image migration threw", e);
            lazadaImageUrls = new ArrayList<>();
            for (int i = 0; i < sourceUrls.size(); i++) {
                String srcUrl = (i < sourceUrls.size()) ? sourceUrls.get(i) : null;
                String b64 = (i < sourceBase64s.size()) ? sourceBase64s.get(i) : null;
                byte[] bytes = fetchOrReadBytes(srcUrl, b64);
                if (bytes != null && bytes.length > 0) {
                    String publicUrl = imgbb.upload(bytes, "image.jpg");
                    String verified = null;
                    if (publicUrl != null && !publicUrl.isBlank()) {
                        verified = imageMigrator.migrateSingle(channel, publicUrl);
                    }
                    if (verified != null && !verified.isBlank()) {
                        lazadaImageUrls.add(verified);
                        anyUsedRelay = true;
                    } else {
                        LOGGER.info("LazadaProductService: imgbb relay failed, trying catbox fallback for image #" + i);
                        String catboxUrl = catbox.upload(bytes, "image.jpg");
                        if (catboxUrl != null && !catboxUrl.isBlank()) {
                            verified = imageMigrator.migrateSingle(channel, catboxUrl);
                        }
                        if (verified != null && !verified.isBlank()) {
                            lazadaImageUrls.add(verified);
                            anyUsedRelay = true;
                        } else {
                            lazadaImageUrls.add(null);
                        }
                    }
                } else {
                    lazadaImageUrls.add(null);
                }
            }
        }

        if (anyUsedRelay) {
            LOGGER.info("LazadaProductService: image relay used for some images for product " + p.getSkuCode());
        }

        // Drop nulls — payloadBuilder will fail on missing images
        List<String> usableImages = new ArrayList<>();
        for (String u : lazadaImageUrls) if (u != null && !u.isBlank()) usableImages.add(u);

        if (usableImages.isEmpty()) {
            String msg = "Không thể upload ảnh lên Lazada (internal relay failed). "
                    + "Vui lòng kiểm tra cấu hình imgBB/Catbox hoặc thử lại.";
            channelProductDAO.recordPushFailure(cp.getId(), "IMAGE_UPLOAD_FAILED", msg);
            pushErrorDAO.insert(cp.getId(), channel.getChannelId(), p.getSkuCode(),
                    "IMAGE_UPLOAD_FAILED", msg, null, null);
            return PushResult.failure("IMAGE_UPLOAD_FAILED", msg);
        }

        // ── Step 3: build final payload ────────────────────────────
        Map<String, String> payload = payloadBuilder.build(p, cp, usableImages);
        LOGGER.info("Lazada payload JSON for " + p.getSkuCode() + ": " + (payload != null ? payload.get("payload") : "null"));

        // ── Step 4: POST /product/create via Gateway ────────────────
        long t0 = System.currentTimeMillis();
        String response;
        try {
            response = gateway.createProduct(channel, payload);
        } catch (Exception e) {
            ChannelSyncAudit.logFailure(channel.getChannelId(),
                    "PRODUCT_PUSH", p.getSkuCode(), null, payload.toString(), e.getMessage());
            pushErrorDAO.insert(cp.getId(), channel.getChannelId(), p.getSkuCode(),
                    "TRANSPORT", e.getMessage(), null, null);
            channelProductDAO.recordPushFailure(cp.getId(), "TRANSPORT", e.getMessage());
            return PushResult.failure("TRANSPORT", e.getMessage());
        }
        long dt = System.currentTimeMillis() - t0;
        LOGGER.info("LazadaProductService.push raw response: " + response);
        ChannelSyncAudit.logSuccess(channel.getChannelId(),
                "PRODUCT_PUSH", p.getSkuCode(), 200, payload.toString(), response, dt);

        // ── Step 5: parse + persist ────────────────────────────────
        LazadaErrorTranslator.ParsedLazadaResponse parsed =
                LazadaErrorTranslator.parse(response);
        if (!parsed.success) {
            String msg = parsed.topMessage != null && !parsed.topMessage.isBlank()
                    ? parsed.topMessage : "Lazada từ chối yêu cầu";
            // If we have field-level errors, build a concise VI message
            if (!parsed.fieldErrors.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < parsed.fieldErrors.size() && i < 3; i++) {
                    if (i > 0) sb.append("; ");
                    sb.append(parsed.fieldErrors.get(i).viMessage);
                }
                msg = sb.toString();
            }
            String errorCode = !parsed.fieldErrors.isEmpty()
                    ? parsed.fieldErrors.get(0).fieldHint
                    : "LAZADA_REJECTED";
            pushErrorDAO.insert(cp.getId(), channel.getChannelId(), p.getSkuCode(),
                    errorCode, msg, response, response);
            channelProductDAO.recordPushFailure(cp.getId(), errorCode, msg);
            return PushResult.failure(errorCode, msg);
        }

        // Success path: persist Lazada identifiers + qty on channel row
        int cpId = ensureChannelProductRow(cp, channel, p);
        if (cpId > 0) {
            channelProductDAO.recordPushSuccess(
                    cpId,
                    parsed.itemId,
                    parsed.skuId,
                    cp.getChannelStock() != null ? cp.getChannelStock() : BigDecimal.ZERO);
        }
        return PushResult.success(parsed.itemId, parsed.skuId, parsed.fieldErrors);
    }

    public PushResult updateProduct(Channel channel, int channelProductId,
                                    BigDecimal price, String description,
                                    List<String> customImageUrls, List<String> customImageBase64s) {
        return updateProduct(channel, channelProductId, null, price, description, customImageUrls, customImageBase64s);
    }

    /**
     * UC-B2C09 / UC-B2C02 (edit flow): update an existing product on Lazada.
     * Called by the "Cấu hình lại sản phẩm kênh" modal.
     *
     * <p>The servlet has already applied all modal inputs to {@code cpFromServlet} and
     * persisted them to DB. We reload from DB (to get the persisted state) and then
     * apply any remaining overrides from {@code cpFromServlet}. This way all fields
     * (category, brand, dimensions, price, qty) are correct before building the payload.</p>
     *
     * <p>Auto-mapping rule: if {@code lazadaCategoryId} is still null after DB persist,
     * resolve it from {@code category_mappings} using the master product's WMS category.
     * Brand defaults to {@value #NO_BRAND_LAZADA_ID} ("No Brand") if neither brand_id
     * nor brand name was set.</p>
     */
    public PushResult updateProduct(Channel channel, int channelProductId,
                                    ChannelProduct cpFromServlet,
                                    BigDecimal price, String description,
                                    List<String> customImageUrls, List<String> customImageBase64s) {
        try {
            ChannelProduct cp = channelProductDAO.findById(channelProductId);
            if (cp == null) {
                return PushResult.failure("NOT_FOUND", "Sản phẩm kênh không tồn tại.");
            }
            if (cp.getChannelItemId() == null || cp.getChannelItemId().trim().isEmpty()) {
                LOGGER.info("updateProduct: channel_item_id is null for id=" + channelProductId
                        + " — falling back to pushProduct for initial create");
                return pushProduct(channel, cp.getProductId(), customImageUrls, customImageBase64s, cp);
            }
            Product p = productDAO.findById(cp.getProductId());
            if (p == null) {
                return PushResult.failure("PRODUCT_NOT_FOUND", "Master product not found");
            }

            // Apply overrides from servlet (all fields already persisted to DB by servlet,
            // but cpFromServlet may carry transient values not stored in DB).
            if (cpFromServlet != null) {
                if (cpFromServlet.getLazadaCategoryId() != null) cp.setLazadaCategoryId(cpFromServlet.getLazadaCategoryId());
                if (cpFromServlet.getBrandId() != null) cp.setBrandId(cpFromServlet.getBrandId());
                if (cpFromServlet.getBrand() != null) cp.setBrand(cpFromServlet.getBrand());
                if (cpFromServlet.getDimensions() != null) cp.setDimensions(cpFromServlet.getDimensions());
                if (cpFromServlet.getWeightKg() != null) cp.setWeightKg(cpFromServlet.getWeightKg());
                if (cpFromServlet.getSellerSku() != null) cp.setSellerSku(cpFromServlet.getSellerSku());
                if (cpFromServlet.getChannelStock() != null) cp.setChannelStock(cpFromServlet.getChannelStock());
                // description and channelPrice are already set on cp from DB persist; don't
                // reassign from cpFromServlet to avoid stale values overriding the user's input.
            }

            // User picks category from dropdown in the modal — no auto-mapping.
            // Brand is always "No Brand" per business rule.
            if (cp.getLazadaCategoryId() == null) {
                return PushResult.failure("MISSING_CATEGORY",
                        "Vui lòng chọn Danh mục Lazada (leaf category) trước khi cập nhật sản phẩm.");
            }
            if ((cp.getBrandId() == null || cp.getBrandId() <= 0)
                    && (cp.getBrand() == null || cp.getBrand().isBlank())) {
                cp.setBrandId(NO_BRAND_LAZADA_ID);
                cp.setBrand("No Brand");
            }

            // Apply price and description overrides
            if (price != null) cp.setChannelPrice(price);
            if (description != null) cp.setDescription(description);
            if (description != null) cp.setShortDescription(description);

            // Image migration
            List<String> sourceUrls = new ArrayList<>();
            List<String> sourceBase64s = new ArrayList<>();
            boolean userProvidedImages = false;
            if (customImageUrls != null) {
                for (String u : customImageUrls) {
                    if (u != null && !u.isBlank()) sourceUrls.add(u.trim());
                }
                userProvidedImages = !sourceUrls.isEmpty();
            }
            if (customImageBase64s != null) {
                for (String b64 : customImageBase64s) {
                    if (b64 != null && !b64.isBlank()) sourceBase64s.add(b64.trim());
                    else sourceBase64s.add(null);
                }
            }
            while (sourceBase64s.size() < sourceUrls.size()) sourceBase64s.add(null);

            // Lazada /product/update REQUIRES the existing image gallery to be re-sent,
            // otherwise the call strips them. When the wizard did not upload new images,
            // fall back to the master product's product_images table so the SPU keeps
            // its gallery after update.
            if (!userProvidedImages) {
                java.util.List<com.wms.model.ProductImage> existing = productImageDAO.findByProductId(cp.getProductId());
                for (com.wms.model.ProductImage img : existing) {
                    if (img != null && img.getImageUrl() != null && !img.getImageUrl().isBlank()) {
                        sourceUrls.add(img.getImageUrl().trim());
                    }
                }
                while (sourceBase64s.size() < sourceUrls.size()) sourceBase64s.add(null);
                if (!sourceUrls.isEmpty()) {
                    LOGGER.info("LazadaProductService.updateProduct: reusing " + sourceUrls.size()
                            + " existing product image(s) for product " + p.getSkuCode());
                }

                // Last-resort fallback: when the wizard didn't upload anything AND
                // product_images is empty, ask Lazada itself for the current gallery
                // via GET /product/item/get. Without this, Lazada's update endpoint
                // would silently strip the existing images.
                if (sourceUrls.isEmpty() && cp.getChannelItemId() != null
                        && !cp.getChannelItemId().isBlank()) {
                    List<String> lazExisting = fetchLazadaExistingImages(channel, cp.getChannelItemId());
                    if (!lazExisting.isEmpty()) {
                        sourceUrls.addAll(lazExisting);
                        while (sourceBase64s.size() < sourceUrls.size()) sourceBase64s.add(null);
                        LOGGER.info("LazadaProductService.updateProduct: reusing " + lazExisting.size()
                                + " Lazada-CDN image(s) from /product/item/get for item_id="
                                + cp.getChannelItemId());
                    }
                }
            }

            List<String> lazadaImageUrls = new ArrayList<>();
            boolean anyUsedRelay = false;
            try {
                List<String> migrated = imageMigrator.migrateImages(channel, sourceUrls);
                for (int i = 0; i < migrated.size(); i++) {
                    String migratedUrl = migrated.get(i);
                    if (migratedUrl != null && !migratedUrl.isBlank()) {
                        lazadaImageUrls.add(migratedUrl);
                    } else {
                        String b64 = (i < sourceBase64s.size()) ? sourceBase64s.get(i) : null;
                        if (b64 != null && !b64.isBlank()) {
                            byte[] bytes = decodeBase64(b64);
                            String publicUrl = imgbb.upload(bytes, "image.jpg");
                            String verified = null;
                            if (publicUrl != null && !publicUrl.isBlank()) {
                                verified = imageMigrator.migrateSingle(channel, publicUrl);
                            }
                            if (verified != null && !verified.isBlank()) {
                                lazadaImageUrls.add(verified);
                                anyUsedRelay = true;
                            } else {
                                String catboxUrl = catbox.upload(bytes, "image.jpg");
                                if (catboxUrl != null && !catboxUrl.isBlank()) {
                                    verified = imageMigrator.migrateSingle(channel, catboxUrl);
                                }
                                if (verified != null && !verified.isBlank()) {
                                    lazadaImageUrls.add(verified);
                                    anyUsedRelay = true;
                                } else {
                                    lazadaImageUrls.add(null);
                                }
                            }
                        } else {
                            lazadaImageUrls.add(null);
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "LazadaProductService: image migration threw", e);
                lazadaImageUrls = new ArrayList<>();
                for (int i = 0; i < sourceUrls.size(); i++) {
                    String b64 = (i < sourceBase64s.size()) ? sourceBase64s.get(i) : null;
                    if (b64 != null && !b64.isBlank()) {
                        byte[] bytes = decodeBase64(b64);
                        String publicUrl = imgbb.upload(bytes, "image.jpg");
                        String verified = null;
                        if (publicUrl != null && !publicUrl.isBlank()) {
                            verified = imageMigrator.migrateSingle(channel, publicUrl);
                        }
                        if (verified != null && !verified.isBlank()) {
                            lazadaImageUrls.add(verified);
                            anyUsedRelay = true;
                        } else {
                            String catboxUrl = catbox.upload(bytes, "image.jpg");
                            if (catboxUrl != null && !catboxUrl.isBlank()) {
                                verified = imageMigrator.migrateSingle(channel, catboxUrl);
                            }
                            if (verified != null && !verified.isBlank()) {
                                lazadaImageUrls.add(verified);
                                anyUsedRelay = true;
                            } else {
                                lazadaImageUrls.add(null);
                            }
                        }
                    } else {
                        lazadaImageUrls.add(null);
                    }
                }
            }

            List<String> usableImages = new ArrayList<>();
            for (String u : lazadaImageUrls) {
                if (u != null && !u.isBlank()) usableImages.add(u);
            }

            // Build update payload
            Map<String, String> payload = payloadBuilder.buildUpdate(p, cp, usableImages);
            LOGGER.info("Lazada update payload JSON for " + p.getSkuCode() + ": " + (payload != null ? payload.get("payload") : "null"));

            // POST /product/update via Gateway
            long t0 = System.currentTimeMillis();
            String response;
            try {
                response = gateway.updateProduct(channel, cp.getChannelItemId(), payload);
            } catch (Exception e) {
                ChannelSyncAudit.logFailure(channel.getChannelId(),
                        "PRODUCT_UPDATE", p.getSkuCode(), null, payload.toString(), e.getMessage());
                pushErrorDAO.insert(cp.getId(), channel.getChannelId(), p.getSkuCode(),
                        "TRANSPORT", e.getMessage(), null, null);
                channelProductDAO.recordPushFailure(cp.getId(), "TRANSPORT", e.getMessage());
                return PushResult.failure("TRANSPORT", e.getMessage());
            }
            long dt = System.currentTimeMillis() - t0;
            LOGGER.info("LazadaProductService.updateProduct raw response: " + response);
            ChannelSyncAudit.logSuccess(channel.getChannelId(),
                    "PRODUCT_UPDATE", p.getSkuCode(), 200, payload.toString(), response, dt);

            // Parse + persist
            LazadaErrorTranslator.ParsedLazadaResponse parsed = LazadaErrorTranslator.parse(response);
            if (!parsed.success) {
                String msg = parsed.topMessage != null && !parsed.topMessage.isBlank()
                        ? parsed.topMessage : "Lazada từ chối cập nhật sản phẩm";
                if (!parsed.fieldErrors.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < parsed.fieldErrors.size() && i < 3; i++) {
                        if (i > 0) sb.append("; ");
                        sb.append(parsed.fieldErrors.get(i).viMessage);
                    }
                    msg = sb.toString();
                }
                String errorCode = !parsed.fieldErrors.isEmpty()
                        ? parsed.fieldErrors.get(0).fieldHint
                        : "LAZADA_REJECTED";
                pushErrorDAO.insert(cp.getId(), channel.getChannelId(), p.getSkuCode(),
                        errorCode, msg, response, response);
                channelProductDAO.recordPushFailure(cp.getId(), errorCode, msg);
                return PushResult.failure(errorCode, msg);
            }

            // Success path: Lazada echoed back updated identifiers — keep them.
            // All other fields (price, description, etc.) are already persisted to DB
            // by the servlet before this call; no need to reassign from method params.
            if (parsed.itemId != null && !parsed.itemId.isBlank()) {
                cp.setChannelItemId(parsed.itemId);
            }
            if (parsed.skuId != null && !parsed.skuId.isBlank()) {
                cp.setLazadaSkuId(parsed.skuId);
            }
            channelProductDAO.update(cp);

            // Clear any old error status and mark ACTIVE
            channelProductDAO.recordPushSuccess(cp.getId(), cp.getChannelItemId(), cp.getLazadaSkuId(), cp.getChannelStock() != null ? cp.getChannelStock() : BigDecimal.ZERO);

            return PushResult.success(cp.getChannelItemId(), cp.getLazadaSkuId(), parsed.fieldErrors);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "LazadaProductService: updateProduct failed for id=" + channelProductId, e);
            return PushResult.failure("SYSTEM_ERROR", "Lỗi hệ thống: " + e.getMessage());
        }
    }

    /** Ensures a {@code channel_products} row exists for this product+channel. Returns its id. */
    private int ensureChannelProductRow(ChannelProduct cp, Channel channel, Product p) {
        if (cp.getId() > 0) return cp.getId();
        ChannelProduct fresh = channelProductDAO.findByProductAndChannel(
                p.getProductId(), channel.getChannelId());
        if (fresh != null) return fresh.getId();
        ChannelProduct draft = new ChannelProduct();
        draft.setChannelId(channel.getChannelId());
        draft.setProductId(p.getProductId());
        draft.setChannelSkuCode(p.getSkuCode());
        draft.setChannelPrice(cp.getChannelPrice());
        draft.setChannelStock(cp.getChannelStock());
        draft.setStatus("ACTIVE");
        draft.setListedAt(java.time.LocalDateTime.now());
        boolean ok = channelProductDAO.insert(draft);
        if (!ok) return -1;
        ChannelProduct just = channelProductDAO.findByProductAndChannel(
                p.getProductId(), channel.getChannelId());
        return just != null ? just.getId() : -1;
    }

    /**
     * Last-resort: fetches the current image gallery directly from Lazada via
     * GET /product/item/get. Used by {@link #updateProduct} when the wizard did
     * not upload new images AND the master product's product_images table is empty.
     * Without this, Lazada's /product/update call would strip the gallery.
     *
     * @return list of Lazada-CDN image URLs (possibly empty on failure)
     */
    public List<String> fetchLazadaExistingImages(Channel channel, String itemId) {
        List<String> result = new ArrayList<>();
        try {
            String response = gateway.getProductByItemId(channel, itemId);
            if (response == null || response.isBlank()) return result;
            JsonNode root = MAPPER.readTree(response);
            JsonNode data = root.path("data");
            if (data.isMissingNode() || data.isNull()) return result;
            // Lazada returns images in data.images.image[] (string or array)
            JsonNode images = data.path("images");
            if (images.isArray()) {
                for (JsonNode img : images) {
                    if (img.isTextual()) {
                        String u = img.asText().trim();
                        if (!u.isEmpty()) result.add(u);
                    } else {
                        String u = img.path("url").asText("").trim();
                        if (!u.isEmpty()) result.add(u);
                    }
                }
            } else {
                JsonNode singleImg = images.path("image");
                if (singleImg.isArray()) {
                    for (JsonNode img : singleImg) {
                        String u = img.asText("").trim();
                        if (!u.isEmpty()) result.add(u);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING,
                    "LazadaProductService.fetchLazadaExistingImages failed for itemId=" + itemId, e);
        }
        return result;
    }

    // ── DTOs ──────────────────────────────────────────────────

    /** Decodes a data:image/...;base64,... URI to raw bytes. */
    private static byte[] decodeBase64(String dataUri) {
        if (dataUri == null || dataUri.isBlank()) return null;
        String b64 = dataUri;
        int comma = b64.indexOf(',');
        if (comma >= 0) b64 = b64.substring(comma + 1);
        try {
            return java.util.Base64.getDecoder().decode(b64);
        } catch (IllegalArgumentException e) {
            LOGGER.warning("decodeBase64: invalid base64 string");
            return null;
        }
    }

    public static final class PullResult {
        public final boolean ok;
        public final int pulled;
        public final int upserted;
        public final int unmapped;
        public final String error;
        public PullResult(boolean ok, int pulled, int upserted, int unmapped, String error) {
            this.ok = ok; this.pulled = pulled;
            this.upserted = upserted; this.unmapped = unmapped;
            this.error = error;
        }
    }

    public static final class PushResult {
        public final boolean success;
        public final String code;        // "0" on success, otherwise Lazada error code
        public final String message;     // user-friendly message
        public final String itemId;      // Lazada item_id on success
        public final String skuId;       // Lazada sku_id on success
        public final List<LazadaErrorTranslator.FieldError> fieldErrors; // server-side errors
        public final List<LazadaProductPayloadBuilder.ValidationError> validationErrors; // client-side
        private PushResult(boolean s, String c, String m, String itemId, String skuId,
                           List<LazadaErrorTranslator.FieldError> fieldErrors,
                           List<LazadaProductPayloadBuilder.ValidationError> validationErrors) {
            this.success = s; this.code = c; this.message = m;
            this.itemId = itemId; this.skuId = skuId;
            this.fieldErrors = fieldErrors;
            this.validationErrors = validationErrors;
        }
        public static PushResult success(String itemId, String skuId,
                                         List<LazadaErrorTranslator.FieldError> fieldErrors) {
            return new PushResult(true, "0", "Đẩy sản phẩm lên Lazada thành công.",
                    itemId, skuId, fieldErrors, null);
        }
        public static PushResult failure(String code, String message) {
            return new PushResult(false, code, message, null, null, null, null);
        }
        public static PushResult validation(List<LazadaProductPayloadBuilder.ValidationError> errs) {
            String summary = errs.isEmpty() ? "" : errs.get(0).viMessage;
            return new PushResult(false, "VALIDATION", summary, null, null, null, errs);
        }
    }

    public static class DeleteResult {
        public final boolean success;
        public final String code;
        public final String message;
        public DeleteResult(boolean success, String code, String message) {
            this.success = success;
            this.code = code;
            this.message = message;
        }
    }

    public DeleteResult deleteProduct(Channel channel, int channelProductId) {
        try {
            ChannelProduct cp = channelProductDAO.findById(channelProductId);
            if (cp == null) {
                return new DeleteResult(false, "NOT_FOUND", "Sản phẩm kênh không tồn tại trong hệ thống WMS.");
            }

            String sellerSku = cp.getChannelSkuCode();
            String itemId = cp.getChannelItemId();
            String skuId = cp.getLazadaSkuId();

            if (itemId == null || itemId.isEmpty()) {
                // If it was never pushed successfully, we can just delete WMS records directly
                channelProductDAO.delete(channelProductId);
                new com.wms.dao.SkuMappingDAO().deleteByProductAndChannel(cp.getProductId(), cp.getChannelId());
                return new DeleteResult(true, "0", "Đã xóa sản phẩm nháp khỏi WMS.");
            }

            // Construct lists
            String sellerSkuListJson = "[\"" + sellerSku + "\"]";
            String skuIdListJson = "[\"SkuId_" + itemId + "_" + (skuId != null ? skuId : "") + "\"]";

            LOGGER.info("LazadaProductService: removing product from Lazada. sellerSku=" + sellerSku + " itemId=" + itemId + " skuId=" + skuId);
            String response = gateway.removeProduct(channel, null, skuIdListJson);
            JsonNode root = MAPPER.readTree(response);
            String code = root.path("code").asText();

            // "0" = success. Also accept typical already-deleted/not-found codes:
            // "4137" (ITEM_NOT_FOUND) or "4136" (SELLER_SKU_NOT_FOUND)
            boolean isDeletedOrMissing = "0".equals(code) || "4137".equals(code) || "4136".equals(code);

            if (isDeletedOrMissing) {
                // Delete from local WMS database
                channelProductDAO.delete(channelProductId);
                new com.wms.dao.SkuMappingDAO().deleteByProductAndChannel(cp.getProductId(), cp.getChannelId());
                return new DeleteResult(true, "0", "Xóa sản phẩm khỏi sàn và hệ thống WMS thành công!");
            } else {
                String errorMsg = root.path("message").asText();
                return new DeleteResult(false, code, "Lỗi từ Lazada API: " + errorMsg);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "LazadaProductService: deleteProduct failed for id=" + channelProductId, e);
            return new DeleteResult(false, "SYSTEM_ERROR", "Lỗi hệ thống: " + e.getMessage());
        }
    }

    private byte[] fetchOrReadBytes(String srcUrl, String b64) {
        if (b64 != null && !b64.isBlank()) {
            try { return decodeBase64(b64); } catch (Exception ignore) {}
        }
        if (srcUrl != null && !srcUrl.isBlank()) {
            if (srcUrl.contains("/publish-images/")) {
                String filename = srcUrl.substring(srcUrl.lastIndexOf("/publish-images/") + "/publish-images/".length());
                java.io.File file = new java.io.File("/root/wms-uploads/publish-images/", filename);
                if (file.exists() && file.isFile()) {
                    try { return java.nio.file.Files.readAllBytes(file.toPath()); } catch (Exception ignore) {}
                }
            }
            try {
                java.net.URL u = new java.net.URL(srcUrl);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) u.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                if (conn.getResponseCode() == 200) {
                    try (java.io.InputStream is = conn.getInputStream()) {
                        return is.readAllBytes();
                    }
                }
            } catch (Exception ignore) {}
        }
        return null;
    }

    private static final class UpsertOutcome {
        final boolean upserted;
        final boolean unmapped;
        UpsertOutcome(boolean u, boolean um) { this.upserted = u; this.unmapped = um; }
    }
}
