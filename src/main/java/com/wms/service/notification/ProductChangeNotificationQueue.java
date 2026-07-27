package com.wms.service.notification;

import com.wms.model.ProductChangeNotification;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * In-memory notification queue for product changes that affect listed channels.
 * Keeps notifications for 24 hours, then auto-purge.
 * Thread-safe using synchronized blocks.
 */
public class ProductChangeNotificationQueue {

    private static final Logger LOG = Logger.getLogger(ProductChangeNotificationQueue.class.getName());
    private static final ProductChangeNotificationQueue INSTANCE = new ProductChangeNotificationQueue();
    private static final long MAX_AGE_MILLIS = 24 * 60 * 60 * 1000; // 24 hours

    private final List<ProductChangeNotification> queue = new ArrayList<>();
    private int nextId = 1;

    private ProductChangeNotificationQueue() {}

    public static ProductChangeNotificationQueue getInstance() {
        return INSTANCE;
    }

    /**
     * Add a notification when a product is modified and listed on channels.
     */
    public synchronized int addNotification(int productId, String skuCode, String productName,
                                             String changeType, String affectedChannels, String changeDetails) {
        ProductChangeNotification notif = new ProductChangeNotification(
                productId, skuCode, productName, changeType, affectedChannels, changeDetails
        );
        notif.setNotificationId(nextId++);
        queue.add(notif);
        LOG.info("ProductChangeNotification added: SKU=" + skuCode + ", type=" + changeType +
                 ", channels=" + affectedChannels);
        return notif.getNotificationId();
    }

    /**
     * Get all pending notifications (PENDING status).
     */
    public synchronized List<ProductChangeNotification> getPendingNotifications() {
        List<ProductChangeNotification> pending = new ArrayList<>();
        for (ProductChangeNotification notif : queue) {
            if ("PENDING".equals(notif.getStatus())) {
                pending.add(notif);
            }
        }
        return pending;
    }

    /**
     * Acknowledge notification (dismiss it).
     */
    public synchronized boolean acknowledgeNotification(int notificationId) {
        for (ProductChangeNotification notif : queue) {
            if (notif.getNotificationId() == notificationId) {
                notif.setStatus("ACKNOWLEDGED");
                notif.setAcknowledgedAt(LocalDateTime.now());
                return true;
            }
        }
        return false;
    }

    /**
     * Get notification by ID.
     */
    public synchronized ProductChangeNotification getNotification(int notificationId) {
        for (ProductChangeNotification notif : queue) {
            if (notif.getNotificationId() == notificationId) {
                return notif;
            }
        }
        return null;
    }

    /**
     * Auto-cleanup old notifications (older than 24 hours).
     */
    public synchronized void cleanup() {
        long now = System.currentTimeMillis();
        queue.removeIf(notif -> {
            long ageMillis = now - notif.getCreatedAt()
                    .atZone(java.time.ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli();
            return ageMillis > MAX_AGE_MILLIS;
        });
    }

    /**
     * Clear all notifications (for testing).
     */
    public synchronized void clear() {
        queue.clear();
        nextId = 1;
    }

    public synchronized int size() {
        return queue.size();
    }
}
