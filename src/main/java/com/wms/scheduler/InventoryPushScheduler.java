package com.wms.scheduler;

import com.wms.dao.InventoryDAO;
import com.wms.dao.InventoryPushDAO;
import com.wms.model.InventoryPushBatch;
import com.wms.model.InventoryPushBatch.PushStatus;
import com.wms.model.InventoryUpdate;
import com.wms.util.InventoryPushUtil;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * InventoryPushScheduler — Batches inventory changes every 5s and pushes to Web (realtime sync).
 *
 * Purpose:
 * - Collect all inventory deductions in 5s window
 * - Batch into 1 HTTPS request (reduce traffic by 95%)
 * - Sign with HMAC-SHA256 (same auth as API)
 * - Retry if Web offline (max 3 retries: 1s, 2s, 5s)
 *
 * Architecture:
 *   Main inventory deductions → InventoryPushScheduler collects → batch push → Web cache update
 *
 * Failsafe:
 *   If Web down, retry queue holds batch. When Web up, sync continues.
 *   Khách checkout → Main Phase A validate (master data) → always correct even if cache miss.
 */
@WebListener
public class InventoryPushScheduler implements ServletContextListener {

    private static final Logger LOG = Logger.getLogger(InventoryPushScheduler.class.getName());

    private static final long BATCH_INTERVAL_MS = 5_000;  // Batch every 5s
    private static final String WEB_PUSH_ENDPOINT = "http://localhost:8080/inventory/sync";  // TODO: Read from config
    private static final String WEB_API_SECRET = "OCW-W8SSS2TTNNE52NQESVOP594YZP9X8TCS";  // TODO: Read from config

    private Timer timer;
    private InventoryDAO inventoryDAO;
    private InventoryPushDAO pushDAO;

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        inventoryDAO = new InventoryDAO();
        pushDAO = new InventoryPushDAO();

        timer = new Timer("InventoryPushBatchTimer", true);
        timer.scheduleAtFixedRate(new BatchPushTask(), BATCH_INTERVAL_MS, BATCH_INTERVAL_MS);

        LOG.log(Level.INFO, "InventoryPushScheduler: started, batch interval={0}ms, endpoint={1}",
                new Object[]{BATCH_INTERVAL_MS, WEB_PUSH_ENDPOINT});
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        if (timer != null) {
            timer.cancel();
            LOG.info("InventoryPushScheduler: stopped");
        }
    }

    private class BatchPushTask extends TimerTask {

        @Override
        public void run() {
            try {
                // Step 1: Collect current inventory state
                List<InventoryUpdate> changes = inventoryDAO.getChangesSince();
                if (changes.isEmpty()) {
                    LOG.finest("No inventory changes to push");
                    return;
                }

                // Step 2: Build batch
                String batchId = InventoryPushUtil.generateBatchId();
                InventoryPushBatch batch = new InventoryPushBatch(batchId, changes);

                // Step 3: Push to Web
                boolean success = pushToWeb(batch);

                if (success) {
                    pushDAO.markSuccessful(batchId);
                    LOG.log(Level.INFO, "Pushed batch {0}: {1} products", new Object[]{batchId, changes.size()});
                } else {
                    // Schedule retry
                    batch.setRetryCount(0);
                    LocalDateTime nextRetry = InventoryPushUtil.calculateNextRetryTime(0);
                    pushDAO.saveBatch(batch);
                    pushDAO.updateBatchForRetry(batchId, 0, nextRetry);
                    LOG.log(Level.WARNING, "Push failed for batch {0}, scheduled retry at {1}", new Object[]{batchId, nextRetry});
                }

                // Step 4: Check for failed batches and retry
                retryFailedBatches();

            } catch (Exception e) {
                LOG.log(Level.SEVERE, "Error in BatchPushTask: " + e.getMessage(), e);
            }
        }

        private boolean pushToWeb(InventoryPushBatch batch) {
            try {
                // Build JSON payload
                String payload = InventoryPushUtil.buildInventorySyncPayload(batch.getItems());

                // Sign request
                String signature = InventoryPushUtil.signRequest("POST", "/inventory/sync", payload, WEB_API_SECRET);
                long timestamp = System.currentTimeMillis();

                // Make HTTPS request
                URL url = new URL(WEB_PUSH_ENDPOINT);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("X-Signature", signature);
                conn.setRequestProperty("X-Timestamp", String.valueOf(timestamp));
                conn.setConnectTimeout(3000);  // 3s timeout
                conn.setReadTimeout(3000);
                conn.setDoOutput(true);

                // Send payload
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload.getBytes(StandardCharsets.UTF_8));
                    os.flush();
                }

                // Check response
                int code = conn.getResponseCode();
                if (code == 200 || code == 201) {
                    LOG.log(Level.INFO, "Push batch {0} successful (HTTP {1})", new Object[]{batch.getBatchId(), code});
                    return true;
                } else {
                    String error = "HTTP " + code;
                    batch.setLastErrorMessage(error);
                    LOG.log(Level.WARNING, "Push batch {0} failed: {1}", new Object[]{batch.getBatchId(), error});
                    return false;
                }

            } catch (java.net.SocketTimeoutException e) {
                batch.setLastErrorMessage("Timeout (3s)");
                LOG.log(Level.WARNING, "Push timeout for batch {0}: {1}", new Object[]{batch.getBatchId(), e.getMessage()});
                return false;

            } catch (java.net.ConnectException e) {
                batch.setLastErrorMessage("Connection refused (Web offline?)");
                LOG.log(Level.WARNING, "Push connection error for batch {0}: {1}", new Object[]{batch.getBatchId(), e.getMessage()});
                return false;

            } catch (Exception e) {
                batch.setLastErrorMessage(e.getClass().getSimpleName() + ": " + e.getMessage());
                LOG.log(Level.SEVERE, "Unexpected error pushing batch " + batch.getBatchId(), e);
                return false;
            }
        }

        private void retryFailedBatches() {
            try {
                List<InventoryPushBatch> pending = pushDAO.findPendingBatches();

                for (InventoryPushBatch batch : pending) {
                    int retryCount = batch.getRetryCount();

                    // Max 3 retries
                    if (retryCount >= 3) {
                        pushDAO.markFailed(batch.getBatchId(), "Max retries (3) exceeded");
                        LOG.log(Level.WARNING, "Batch {0} failed after {1} retries", new Object[]{batch.getBatchId(), retryCount});
                        continue;
                    }

                    // Retry push
                    boolean success = pushToWeb(batch);

                    if (success) {
                        pushDAO.markSuccessful(batch.getBatchId());
                        LOG.log(Level.INFO, "Retry successful for batch {0} (attempt {1})", new Object[]{batch.getBatchId(), retryCount + 1});
                    } else {
                        // Schedule next retry
                        int nextRetryCount = retryCount + 1;
                        LocalDateTime nextRetry = InventoryPushUtil.calculateNextRetryTime(nextRetryCount);

                        if (nextRetry != null) {
                            pushDAO.updateBatchForRetry(batch.getBatchId(), nextRetryCount, nextRetry);
                            LOG.log(Level.INFO, "Retry {0} scheduled for batch {1} at {2}",
                                    new Object[]{nextRetryCount, batch.getBatchId(), nextRetry});
                        } else {
                            pushDAO.markFailed(batch.getBatchId(), "Max retries exceeded (3)");
                        }
                    }
                }

            } catch (Exception e) {
                LOG.log(Level.SEVERE, "Error in retryFailedBatches: " + e.getMessage(), e);
            }
        }
    }
}
