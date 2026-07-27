package com.wms.service.notification;

import com.wms.dao.ChannelProductDAO;
import com.wms.model.ChannelProduct;
import com.wms.model.Product;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Service to detect product changes and emit notifications for channels where product is listed.
 * Called when product is created/updated in Main.
 */
public class ProductChangeNotificationService {

    private static final Logger LOG = Logger.getLogger(ProductChangeNotificationService.class.getName());
    private final ChannelProductDAO channelProductDAO = new ChannelProductDAO();

    /**
     * Check if product is listed on any channel, emit notification if yes.
     */
    public void notifyIfListed(Product product, String changeType, String changeDetails) {
        if (product == null || product.getProductId() <= 0) {
            return;
        }

        try {
            // Find which channels this product is listed on
            List<Integer> listedChannels = channelProductDAO.findListedChannelIds(product.getProductId());

            if (listedChannels.isEmpty()) {
                // Not listed on any channel, no notification needed
                return;
            }

            // Map channel IDs to channel names
            List<String> channelNames = new ArrayList<>();
            for (int channelId : listedChannels) {
                String channelName = getChannelName(channelId);
                if (channelName != null) {
                    channelNames.add(channelName);
                }
            }

            if (!channelNames.isEmpty()) {
                String affectedChannels = String.join(",", channelNames);
                ProductChangeNotificationQueue.getInstance().addNotification(
                        product.getProductId(),
                        product.getSkuCode(),
                        product.getProductName(),
                        changeType,
                        affectedChannels,
                        changeDetails
                );
                LOG.info("ProductChangeNotification emitted: SKU=" + product.getSkuCode() +
                         ", channels=" + affectedChannels + ", type=" + changeType);
            }
        } catch (Exception e) {
            LOG.warning("ProductChangeNotificationService error: " + e.getMessage());
            // Don't throw — notification failure shouldn't block product update
        }
    }

    /**
     * Map channel_id to channel name (Lazada, Website, etc).
     */
    private String getChannelName(int channelId) {
        // Query channels table — this is a simplified mapping
        // In real impl, would fetch from ChannelDAO
        switch (channelId) {
            case 1: return "WEBSITE";
            case 2: return "LAZADA";
            case 3: return "SHOPEE";
            case 4: return "TIKTOK";
            default: return "CHANNEL_" + channelId;
        }
    }
}
