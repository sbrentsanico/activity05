# PART B – Contract Discovery & Integration Specification

This document details the interface contract, discovery findings, session behavior, and error handling mechanisms for integrating with the **LegacySupply** supplier system.

---

## 1. Product Mapping Table

LegacySupply operates on package units (cases/boxes) using supplier-specific SKUs (SupplierSku) and pack sizes (PackSize). The internal inventory system tracks individual units (productId).

| Inventory Product ID | Inventory Product Name | LegacySupply SupplierSku | Pack Size (PackSize) | Notes / Conversion |
| :--- | :--- | :--- | :--- | :--- |
| P100 | Wireless Mouse 2.4GHz | JLN-2606 | 6 | Ordering 1 case (Qty=1) yields 6 individual mice. |
| P200 | Mechanical Keyboard TKL | JLN-6772 | 24 | Ordering 1 case (Qty=1) yields 24 individual keyboards. |
| P300 | USB Hub 4-Port | JLN-7018 | 20 | Ordering 1 case (Qty=1) yields 20 individual USB hubs. |

---

## 2. Session Token Lifetime & Management

### How Sessions Work
1. **Authentication**: Call POST /api/v1/auth/token with <AuthRequest> containing ClientId and ApiKey.
2. **Authorization**: Include the returned SessionToken in the X-LS-Session HTTP header on all subsequent calls (GET /catalog, POST /purchase-orders, GET /purchase-orders/{PoNumber}).
3. **Expiration & Renewal**: Sessions are short-lived. Once expired, LegacySupply returns HTTP 401 with error code E-AUTH-07 (Session not valid).

### Measured Session Duration
Through automated polling probes and server log verification, session lifetime was measured at:
* **Measured Lifetime**: **~108 seconds** (~1.8 minutes).
* **Adapter Handling**: The Anti-Corruption Layer (LegacySupplySessionManager) caches the token and performs reactive re-authentication whenever an E-AUTH-07 or 401 response is encountered, ensuring zero disruption to inventory flows.

---

## 3. Observed Error Codes & Root Causes

| Error Code | HTTP Status | Trigger Condition / Root Cause | Observed Example |
| :--- | :--- | :--- | :--- |
| E-AUTH-01 | 401 Unauthorized | Invalid ApiKey or ClientId sent in <AuthRequest>. | Requesting token with key WRONG-KEY. |
| E-AUTH-02 | 401 Unauthorized | X-LS-Session header is missing on protected endpoints. | Calling GET /catalog without X-LS-Session. |
| E-AUTH-03 | 401 Unauthorized | X-LS-Session token is malformed, unrecognized, or fake. | Passing X-LS-Session: fake-token-12345. |
| E-AUTH-07 | 401 Unauthorized | X-LS-Session token has expired (after ~108s). | Making an order request using a token generated >108s prior. |
| E-FMT-01 | 415 Unsupported Media Type | Missing or incorrect Content-Type header (must be pplication/xml). | Sending Content-Type: application/json to /auth/token. |
| E-FMT-02 | 400 Bad Request | Malformed XML syntax or missing required XML tags. | Sending invalid/incomplete XML body in POST /purchase-orders. |
| E-SKU-02 | 422 Unprocessable Entity | SupplierSku does not exist in the client's catalog. | Ordering SKU INVALID-SKU-999. |
| E-QTY-11 | 422 Unprocessable Entity | Qty is less than 1, greater than 99, or non-numeric. | Sending <Qty>0</Qty> or <Qty>100</Qty>. |
| E-REF-05 | 400 Bad Request | BuyerRef is missing, blank, or exceeds 40 characters. | Omitted <BuyerRef> tag in <PurchaseOrder>. |
| E-PO-04 | 404 Not Found | Requesting status for a non-existent PoNumber. | Calling GET /purchase-orders/PO-999999. |
| E-IDEM-04 | 409 Conflict | Reusing X-Request-Id header with a different request payload. | Retrying order with identical X-Request-Id but different SKU or Qty. |
| E-SYS-50 | 503 Service Unavailable | Transient processing error on server side. | Intermittent 503 error returned during order placement or status check. |
| E-SYS-99 | 503 Service Unavailable | Temporary upstream service outage / cold start. | Service unavailable response during server warmup. |

---

## 4. Understanding Qty and Uom

### Concept Explanation
* **Qty (Quantity)**: The number of **wholesale packages** (cases/cartons) being ordered from LegacySupply, expressed as an integer between 1 and 99. It does **not** represent single item units.
* **Uom (Unit of Measure)**: The packaging unit designation returned by LegacySupply (typically CS for Case).
* **PackSize**: The number of individual retail units contained within a single Uom (case).

### Worked Example
Suppose inventory product **P200 (Mechanical Keyboard TKL)** drops below threshold, and the auto-reorder system determines that **30 units** are required.

1. **Catalog Terms**: LegacySupply supplies SKU JLN-6772 with PackSize = 24 and Uom = CS.
2. **Calculation**:
   \text{Cases Needed} = \lceil \frac{30}{24} \rceil = 2 \text{ cases}
3. **LegacySupply Order**:
   * <SupplierSku>JLN-6772</SupplierSku>
   * <Qty>2</Qty>
4. **Result**:
   LegacySupply delivers **2 cases** (Qty=2, Uom=CS), which translates to **48 individual keyboards** ( \times 24$) added back into the internal inventory stock upon fulfillment.

---

## 5. Delivery Tracking & Unexpected Status Handling

### Delivery Tracking Architecture
* **Scheduled Status Polling**: Open purchase orders are polled by the Anti-Corruption Layer at polite intervals (3.0s between requests).
* **Decoupled Restock Event**: When an order status transitions to DELIVERED (StatusCode 40), the supplier module publishes a domain delivery event (OrderDeliveredEvent or direct inventoryService.restock()). The Order and Inventory modules never reference supplier-internal XML models or API endpoints directly.

### Strategy for Unexpected Status Codes
LegacySupply standard status codes are:
* 10: ACCEPTED (Order accepted by supplier)
* 20: PICKING (Items being picked in warehouse)
* 30: SHIPPED (Items in transit)
* 40: DELIVERED (Order delivered)

When LegacySupply returns an unexpected status code (such as StatusCode 90 encountered in live testing):

1. **Domain Enum Mapping**:
   * The LegacySupplyTranslator maps all unrecognized status codes (e.g., 90 or negative/non-standard integers) to SupplierOrderStatus.CANCELLED (or UNKNOWN terminal failure state).

2. **Restock Protection**:
   * The system logs a warning detailing the unrecognized status code ([LegacySupply] Unrecognized status code: 90 -> Mapping to CANCELLED).
   * Orders with CANCELLED status trigger cancellation logic. Stock is **never** credited to inventory unless status strictly reaches DELIVERED (40).
   * Because stock is not credited, the low-stock trigger remains free to place a replacement order if inventory remains below threshold.

3. **Quota & Rate Limit Compliance**:
   * All status checks observe a **3.0-second delay** per request.
   * Total requests strictly honor LegacySupply's rate limits (0 rate-limited responses out of 46+ checks recorded in /verify).
