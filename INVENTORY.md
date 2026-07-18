# Inventory Management - Realtime Push + Atomic Deduction

**Cập nhật:** 2026-07-18  
**Status:** Design (chưa implement)

---

## 1. Mục đích

Giải quyết 2 vấn đề:
1. **Stale Cache:** Web cache hết ngày (không sync realtime với Main) → Khách order sản phẩm hết → UX xấu
2. **Overselling:** Nhiều channel cùng bán → Race condition → Tồn kho âm

---

## 2. Vấn đề Chi Tiết

### 2.1 Stale Inventory Cache

```
Tình huống: Omnicore có 2 channel (Web + Lazada)

t=0s:
  Main DB: Sản phẩm A = 100 cái
  Web cache: A = 100 (sync lần cuối)
  
t=0.5s: Lazada bán 50 cái A
  Main DB: A = 50 (deduct ngay)
  Web cache: A = 100 ❌ (STALE! chưa update)
  
t=1s: Khách Web order 60 cái A
  Web check cache: A = 100 >= 60 ✓
  POST tới Main: order A (qty=60)
  Main check DB: A = 50 < 60 ❌
  → 409 Conflict (khách chán)
```

**Hệ quả:**
- ❌ Khách thấy "Còn 100" → Order 60 → "Hàng hết" → Conversion giảm
- ❌ UX tệ (khách bối rối)

---

### 2.2 Overselling Risk

```
Nếu check & deduct tách riêng:

t=0: Web check A = 100 >= 2 ✓
t=0.1s: Lazada bán 99 cái A → Main A = 1
t=0.2s: Web insert order (qty=2)
t=0.3s: Web deduct A = 1 - 2 = -1 ❌ OVERSELL
```

**Hệ quả:**
- ❌ Tồn kho âm
- ❌ Khách thanh toán nhưng không giao được
- ❌ Phải refund (loss money)

---

## 3. Giải pháp

### 3.1 Realtime Push (Giải quyết Stale Cache)

**Khái niệm:** Main chủ động push inventory updates tới Web mỗi 5s

```
Main DB (Source of Truth)
  ↓ (Batch deduct mỗi 5s)
Main Inventory Cache
  ↓ (Push mỗi 5s)
POST /inventory/sync
  ↓
Web Inventory Cache (updated)
  ↓
Web UI (Khách thấy số mới)
```

**Batch mechanism:**
- Collect tất cả deduction trong 5s
- Gộp lại thành 1 batch
- Push 1 request (thay vì N requests)
- Giảm 95% traffic + Main load

### 3.2 Atomic Deduction (Giải quyết Overselling)

**Cơ chế:** Phase A + B + C trong 1 transaction

```
POST /api/website/orders
  │
  ├─ Phase A: SELECT qty FOR UPDATE (lock row)
  │           Check: qty_available >= qty?
  │           ├─ YES → Continue
  │           └─ NO → ROLLBACK → 409
  │
  ├─ Phase B: INSERT order + order_items
  │
  └─ Phase C: UPDATE qty_available (deduct) + lock flag
  
COMMIT → 201 / 409
```

**Đảm bảo:**
- ✅ All-or-nothing (không partial)
- ✅ No race condition (Phase A lock)
- ✅ No oversell (atomic deduct)

---

## 4. Architecture

### 4.1 Realtime Push Flow

```
┌─────────────────┐
│ Main DB         │
│ (Source Truth)  │
│  Prod A = 74    │
└────────┬────────┘
         │
         ↓ (mỗi deduct)
┌─────────────────┐
│ Deduction Queue │
│ [{prod: A,      │
│   qty: 74,      │
│   ts: 12:34:50} │
│  {prod: B,      │
│   qty: 45}]     │
└────────┬────────┘
         │
         ↓ (mỗi 5s)
┌─────────────────────────┐
│ InventoryPushScheduler  │
│ (Batch + Sign HMAC)     │
└────────┬────────────────┘
         │
         ↓ HTTPS POST
┌─────────────────────────────────┐
│ Web InventorySyncServlet        │
│ ├─ Verify HMAC signature        │
│ ├─ Update cache                 │
│ ├─ Update DOM                   │
│ └─ Retry failed pushes          │
└─────────────────────────────────┘
         │
         ↓
┌─────────────────┐
│ Web Cache       │
│ Prod A = 74     │
└─────────────────┘
         │
         ↓
┌─────────────────┐
│ Web UI (DOM)    │
│ "Còn 74 cái"    │
└─────────────────┘
```

### 4.2 Retry Queue (Nếu Web down)

```
Push fail → Queue retry:

t=5s:   Try 1 (Web offline) → Schedule retry in 1s
t=6s:   Try 2 (Web offline) → Schedule retry in 2s
t=8s:   Try 3 (Web offline) → Schedule retry in 5s
t=13s:  Try 4 (Web offline) → Give up (remove from queue)

Fallback: Khách checkout → Main Phase A validate
          (Master data always at Main)
```

---

## 5. Luồng Chi Tiết

### 5.1 Scenario: Lazada bán hàng

```
Timeline:

t=0s:
  Main DB: Prod A = 100
  Web cache: A = 100
  Khách Web xem giỏ

t=0.5s: Lazada order A (qty=50)
  ├─ Main POST /api/lazada/orders
  ├─ Phase A: Check A=100 >= 50 ✓
  ├─ Phase B: INSERT order (Lazada)
  ├─ Phase C: A = 100 - 50 = 50
  └─ Deduction Queue: {prod: A, qty: 50, ts: 12:34:00.5}

t=1.2s: Web bán hàng A (qty=10)
  └─ Deduction Queue add: {prod: A, qty: 10, ts: 12:34:01.2}

t=5s: BATCH PUSH chạy ⏰
  ├─ Batch: [{prod: A, qty_available: 40}, ...]
  ├─ Main POST /inventory/sync → Web
  │  Headers: X-Signature (HMAC), X-Timestamp
  │  Body: {items: [{product_id: "A", qty_available: 40}]}
  │
  ├─ Web nhận + verify signature ✓
  ├─ Web update cache: A = 40
  ├─ Web update DOM: "Còn 40 cái" (live)
  └─ Web disable nút nếu A < 20 (tùy config)

t=6s: Khách Web order A (qty=30)
  ├─ Web cache check: A = 40 >= 30 ✓
  ├─ Form valid ✓
  ├─ POST /api/website/orders
  │  Body: {items: [{product_id: "A", qty: 30}]}
  │
  ├─ Main Phase A: Check A = 40 >= 30 ✓
  ├─ Main Phase B: INSERT order
  ├─ Main Phase C: A = 40 - 30 = 10
  └─ Main 201 Created ✓

✅ Khách thành công (không bị 409)
```

### 5.2 Scenario: Web down → Retry

```
t=5s: Main batch push
  ├─ POST /inventory/sync
  ├─ Web offline ❌
  ├─ Timeout: 3s
  └─ Main queue: {retry_count: 1, next_retry: t=6s}

t=6s: Retry 1
  ├─ POST /inventory/sync
  ├─ Web offline ❌
  └─ Queue: {retry_count: 2, next_retry: t=8s}

t=8s: Retry 2
  ├─ POST /inventory/sync
  ├─ Web offline ❌
  └─ Queue: {retry_count: 3, next_retry: t=13s}

t=13s: Retry 3
  ├─ POST /inventory/sync
  ├─ Web offline ❌
  └─ Queue remove (give up after 3 retries)

t=13.1s: Web up ✅
  └─ Cache still old (A=100 vs actual 40)

t=14s: Khách Web order A (qty=60)
  ├─ Web cache: A = 100
  ├─ Check: 100 >= 60 ✓
  ├─ POST /api/website/orders
  ├─ Main Phase A: A = 40 < 60 ❌
  └─ Main 409 Conflict
     (Fallback: Khách retry lagi sau khi xoá item hết)
```

### 5.3 Scenario: Timeout + Fallback (Order sync fail)

```
t=1s: Khách Web click "Xác nhận"
  ├─ Web disable UI
  ├─ POST /api/website/orders (A + B)
  └─ Chờ response (timeout: 10s)

t=0.5s: Lazada bán hết B
  ├─ Main: B = 0
  └─ (Web POST vẫn chờ)

t=2s: Main response 409 (B hết)
  ├─ Web nhận 409
  ├─ Alert: "B hết hàng"
  └─ Khách loại B + retry

t=2.5s: Khách click "Xác nhận lại"
  ├─ Web POST (chỉ A)
  ├─ Main Phase A+B+C (atomic)
  └─ Main 201 ✓

---

Nếu timeout (Main slow/down):

t=1s: Web POST
  └─ Chờ response...

t=11s: Timeout! (10s pass, chưa response)
  ├─ Web cancel request
  ├─ Web: Retry 1 (wait 1s)
  └─ POST lại

t=12s: Retry 1 response = timeout ❌
  ├─ Web: Retry 2 (wait 2s)
  └─ POST lại

t=14s: Retry 2 response = timeout ❌
  ├─ Web: Retry 3 (wait 5s)
  └─ POST lại

t=19s: Retry 3 response = timeout ❌
  ├─ Fallback! (3 retries = fail)
  ├─ Web: Reserve stock locally
  ├─ Web: INSERT order (status = PENDING_SYNC)
  ├─ Web: 201 (phía Web OK)
  └─ Khách thấy: "Đặt hàng thành công"

Background (Scheduler):
  ├─ Retry sync mỗi 30s
  ├─ Retry: 1s → 2s → 5s → 10s → 30s → 30s
  ├─ Nếu Main up: Sync thành công
  └─ Nếu fail 120s: Auto-cancel + release + email
```

---

## 6. Implementation Plan

### Phase 1: Main Side (Realtime Push Infrastructure)

#### 1.1 Create InventoryPushScheduler.java
**File:** `src/main/java/com/wms/scheduler/InventoryPushScheduler.java`

**Responsibilities:**
- Batch inventory changes mỗi 5s
- Sign HTTPS request (HMAC-SHA256)
- Push tới Web endpoint
- Track push success/fail

**Methods:**
```java
@Scheduled(fixedRate = 5000)
public void batchAndPushInventory()
  - Lấy deduction changes trong 5s
  - Batch thành 1 request
  - Gọi pushToWeb()

private void pushToWeb(List<InventoryUpdate> updates)
  - Build JSON body
  - Sign HMAC
  - POST /inventory/sync tới Web
  - Handle response (retry nếu fail)

private void scheduleRetry(InventoryPushBatch batch, int retryCount)
  - Schedule retry: delay(retryCount)
  - Max 3 retries
```

#### 1.2 Create InventoryPushBatch.java
**File:** `src/main/java/com/wms/model/InventoryPushBatch.java`

**Fields:**
```java
String batchId
List<InventoryUpdate> items
long createdAt
int retryCount
long nextRetryTime
PushStatus status (PENDING, SUCCESS, FAILED)
```

#### 1.3 Create InventoryPushDAO.java
**File:** `src/main/java/com/wms/dao/InventoryPushDAO.java`

**Methods:**
```java
void saveBatch(InventoryPushBatch)
void updateBatchStatus(batchId, status)
List<InventoryPushBatch> findFailedBatches()  // For retry
void markSuccessful(batchId)
```

#### 1.4 Create InventoryPushUtil.java
**File:** `src/main/java/com/wms/util/InventoryPushUtil.java`

**Methods:**
```java
String signRequest(String method, String path, String body, String secret)
  - HMAC-SHA256 (same as BaseApiServlet)

String buildInventorySyncPayload(List<InventoryUpdate> items)
  - JSON body
  
long getRetryDelayMs(int retryCount)
  - Retry 1: 1s
  - Retry 2: 2s
  - Retry 3: 5s
```

#### 1.5 Modify InventoryDAO.java
**File:** `src/main/java/com/wms/dao/InventoryDAO.java`

**New Methods:**
```java
void logDeductionForPush(productId, qtyBefore, qtyAfter)
  - Track changes for batch push
  
List<InventoryUpdate> getChangesSince(long timestampMs)
  - Lấy changes trong 5s qua
  - Clear after batch
```

#### 1.6 Create SQL Migration
**File:** `sql/inventory_push_2026-07-18.sql`

**Creates:**
```sql
CREATE TABLE inventory_push_batch (
  batch_id VARCHAR(50) PRIMARY KEY,
  created_at TIMESTAMP,
  retry_count INT,
  next_retry_time TIMESTAMP,
  status ENUM('PENDING', 'SUCCESS', 'FAILED'),
  payload JSON,
  last_error_message TEXT,
  created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_push_batch_status ON inventory_push_batch(status, next_retry_time);
```

---

### Phase 2: Web Side (Receive + Cache Update)

#### 2.1 Create InventorySyncServlet.java
**File:** `omnicore-web/src/main/java/com/omnicore/web/servlet/InventorySyncServlet.java`

**Responsibilities:**
- Receive push từ Main
- Verify HMAC signature
- Update Web cache
- Update DOM (real-time)
- Handle failure (retry notify Main)

**Methods:**
```java
@PostMapping("/inventory/sync")
void receiveInventorySync(HttpServletRequest req)
  - Parse request
  - Verify X-Signature (HMAC-SHA256)
  - Verify X-Timestamp (300s window)
  - Parse body: [{product_id, qty_available}, ...]
  - Update cache per product
  - Update DOM (WebSocket / SSE)
  - Return 200 OK
  
  Nếu fail:
  - Return 5xx (Main sẽ retry)
  - Log error
```

#### 2.2 Modify InventoryCache (Web)
**File:** `omnicore-web/src/main/java/com/omnicore/web/cache/InventoryCache.java`

**New Methods:**
```java
void updateFromPush(List<InventoryUpdate> updates)
  - Atomic update cache
  - Notify listeners (DOM update)

List<String> getOutOfStockProducts()
  - Lấy danh sách hết hàng
  - Use để disable UI
```

#### 2.3 Update Web DOM (JavaScript)
**File:** `omnicore-web/src/main/webapp/js/inventory-sync.js`

**Functionality:**
```javascript
// WebSocket / Server-Sent Events listener
function onInventoryUpdate(items) {
  items.forEach(item => {
    updateCartItemQty(item.productId, item.qty_available);
    
    // Disable nút nếu hết
    if (item.qty_available <= 0) {
      disableCheckoutButton(item.productId);
      showOutOfStockBadge(item.productId);
    }
    
    // Update price total
    recalculateTotal();
  });
}
```

---

### Phase 3: Web to Main (Atomic Deduction - Already Partially Done)

#### 3.1 Verify WebsiteOrderApiServlet.java
**File:** `omnicore-main/src/main/java/com/wms/servlet/WebsiteOrderApiServlet.java`

**Verify 3 Phases:**
```java
@PostMapping("/api/website/orders")
public void createOrder() {
  BEGIN TRANSACTION
  
  // Phase A: Check all items
  for (OrderItem item : request.items) {
    SELECT qty_available FOR UPDATE WHERE product_id = ?
    if (qty < item.qty) {
      ROLLBACK
      return 409 Conflict (với details)
    }
  }
  
  // Phase B: Insert order
  INSERT INTO orders (...)
  INSERT INTO order_items (...)
  
  // Phase C: Deduct inventory
  for (OrderItem item : request.items) {
    UPDATE inventory SET qty_available -= item.qty
    UPDATE inventory SET deduction_lock = 1
    Log to audit_log
  }
  
  COMMIT
  return 201 Created
}
```

#### 3.2 Verify InventoryDAO.java (Main)
**File:** `omnicore-main/src/main/java/com/wms/dao/InventoryDAO.java`

**Verify deductWithLock() exists:**
```java
boolean deductWithLock(productId, qty, orderId)
  - Check deduction_lock = 0
  - Deduct qty
  - Set lock = 1
  - Log audit
  - Return success/fail
```

---

### Phase 4: Testing Scenarios

#### 4.1 Test: Realtime Push (Batch)
```
Setup:
  - Main DB: A = 100
  - Web cache: A = 100
  
Steps:
  1. Lazada order A (qty=50)
  2. Wait 5s (batch push)
  3. Check Web cache: A = 50 ✓
  4. Check DOM: "Còn 50" ✓
  5. Khách order A (qty=40) → 201 ✓
```

#### 4.2 Test: Web Down + Retry
```
Setup:
  - Main ready to push
  - Web stop
  
Steps:
  1. Lazada bán → Main batch push
  2. Main: Try 1 fail (Web down)
  3. Main: Try 2 at t=6s fail
  4. Main: Try 3 at t=8s fail
  5. Start Web at t=10s
  6. Web cache still old
  7. Khách order → 409 (fallback validate)
```

#### 4.3 Test: Overselling Prevention
```
Setup:
  - A = 100
  - Concurrent orders: Web (60) + Lazada (60)
  
Steps:
  1. Both order at same time
  2. Phase A lock (one waits)
  3. First deduct: A = 40
  4. Second check: 40 < 60 → 409
  
Result:
  - ✅ Only 1 success
  - ✅ No oversell (-20)
```

---

## 7. Files Summary

### Main Side
| File | Type | Status |
|------|------|--------|
| InventoryPushScheduler.java | New | TODO |
| InventoryPushBatch.java | New | TODO |
| InventoryPushDAO.java | New | TODO |
| InventoryPushUtil.java | New | TODO |
| InventoryDAO.java | Modify | Verify deductWithLock() |
| inventory_push_2026-07-18.sql | New | TODO |

### Web Side
| File | Type | Status |
|------|------|--------|
| InventorySyncServlet.java | New | TODO |
| InventoryCache.java | Modify | Update with push handler |
| inventory-sync.js | New | TODO (DOM update) |

### Both
| File | Type | Status |
|------|------|--------|
| HMAC signature verify | Both | ✅ Already done |
| Timestamp validation (300s) | Both | ✅ Already done |
| Audit logging | Main | ✅ Already done |

---

## 8. Timeline

- **Week 1:** Main side (Scheduler + DAO + Push logic)
- **Week 2:** Web side (Servlet + Cache + DOM update)
- **Week 3:** Integration test + edge case handling
- **Week 4:** Performance test + production hardening

---

## 9. Notes

- Batch mỗi 5s (configurable, có thể thay đổi)
- Retry max 3 lần (configurable)
- Timeout 3s per push (configurable)
- HMAC signature same as API auth (BaseApiServlet)
- Timestamp validation: now ± 300s
- Fallback: Khách checkout → Main validate (Phase A) → master data always correct

---

**Next step:** Review & approve design, sau đó bắt đầu implement Phase 1 (Main side)
