package com.wms.util;

import org.json.JSONArray;
import org.json.JSONObject;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

public class InventoryPushUtil {

    public static String generateBatchId() {
        long timestamp = System.currentTimeMillis();
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        return "PUSH-" + timestamp + "-" + uuid;
    }

    public static String signRequest(String method, String path, String body, String secret) {
        try {
            String message = method + "\n" + path + "\n" + body;
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8),
                    0,
                    secret.getBytes(StandardCharsets.UTF_8).length,
                    "HmacSHA256"
            );
            mac.init(keySpec);
            byte[] result = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(result);
        } catch (Exception e) {
            throw new RuntimeException("Failed to sign request: " + e.getMessage(), e);
        }
    }

    public static String buildInventorySyncPayload(List<?> items) {
        JSONArray arr = new JSONArray();

        if (items != null) {
            for (Object item : items) {
                if (item instanceof com.wms.model.InventoryUpdate) {
                    com.wms.model.InventoryUpdate update = (com.wms.model.InventoryUpdate) item;
                    JSONObject obj = new JSONObject();
                    obj.put("product_id", update.getProductId());
                    obj.put("qty_available", update.getQtyAvailable());
                    obj.put("qty_before", update.getQtyBefore());
                    arr.put(obj);
                }
            }
        }

        return arr.toString();
    }

    public static long getRetryDelayMs(int retryCount) {
        switch (retryCount) {
            case 0: return 1_000;        // Retry 1: 1s
            case 1: return 2_000;        // Retry 2: 2s
            case 2: return 5_000;        // Retry 3: 5s
            default: return 0;           // No more retries after 3
        }
    }

    public static LocalDateTime calculateNextRetryTime(int retryCount) {
        long delayMs = getRetryDelayMs(retryCount);
        if (delayMs == 0) {
            return null; // No more retries
        }
        return LocalDateTime.now().plusSeconds(delayMs / 1000);
    }

    public static long toEpochMilli(LocalDateTime ldt) {
        if (ldt == null) {
            return System.currentTimeMillis();
        }
        return ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
