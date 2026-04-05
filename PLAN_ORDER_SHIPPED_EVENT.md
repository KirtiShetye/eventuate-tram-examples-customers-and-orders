# Implementation Plan: Add OrderShipped Event

## Overview
Add a new domain event `OrderShippedEvent` that is published by Order Service when an order is shipped, and consumed by Customer Service and Order History Service.

## Business Flow
```
Order APPROVED → Ship Order → OrderShippedEvent published → 
  → Customer Service notified
  → Order History view updated
```

---

## Event Ownership Decision

**OrderShippedEvent belongs in Order Service** because:
- It's a domain event about the Order aggregate
- Order Service owns the Order lifecycle
- Follows Domain-Driven Design: events are published by the aggregate that owns them

**Customer Service and Order History Service only need copies** of the event class for deserialization when consuming.

---

## Implementation Steps

### **Step 1: Create OrderShipped Event (Order Service Domain)**

**File:** `order-service/order-service-domain/src/main/java/io/eventuate/examples/tram/ordersandcustomers/orders/domain/OrderShippedEvent.java`

```java
package io.eventuate.examples.tram.ordersandcustomers.orders.domain;

public record OrderShippedEvent(OrderDetails orderDetails) implements OrderEvent {
}
```

**Estimated time:** 2 minutes

---

### **Step 2: Update Order State Enum**

**File:** `order-service/order-service-domain/src/main/java/io/eventuate/examples/tram/ordersandcustomers/orders/domain/OrderState.java`

**Change:**
```java
public enum OrderState {
  PENDING,
  APPROVED,
  REJECTED,
  CANCELLED,
  SHIPPED  // ← Add this
}
```

**Estimated time:** 1 minute

---

### **Step 3: Add Ship Method to Order Entity**

**File:** `order-service/order-service-domain/src/main/java/io/eventuate/examples/tram/ordersandcustomers/orders/domain/Order.java`

**Add method:**
```java
public void ship() {
  if (this.state != OrderState.APPROVED) {
    throw new IllegalStateException("Order must be APPROVED to ship");
  }
  this.state = OrderState.SHIPPED;
}
```

**Estimated time:** 3 minutes

---

### **Step 4: Add Ship Order Business Logic**

**File:** `order-service/order-service-domain/src/main/java/io/eventuate/examples/tram/ordersandcustomers/orders/domain/OrderService.java`

**Add method:**
```java
@Transactional
public Order shipOrder(Long orderId) {
  Order order = orderRepository
    .findById(orderId)
    .orElseThrow(() -> new IllegalArgumentException("order with id %s not found".formatted(orderId)));
  
  order.ship();
  orderEventPublisher.publish(order, new OrderShippedEvent(order.getOrderDetails()));
  return order;
}
```

**Estimated time:** 5 minutes

---

### **Step 5: Add REST Endpoint**

**File:** `order-service/order-service-restapi/src/main/java/io/eventuate/examples/tram/ordersandcustomers/orders/restapi/OrderController.java`

**Add endpoint:**
```java
@PostMapping("/{orderId}/ship")
public ResponseEntity<GetOrderResponse> shipOrder(@PathVariable Long orderId) {
  Order order = orderService.shipOrder(orderId);
  return ResponseEntity.ok(new GetOrderResponse(
    order.getId(),
    order.getOrderDetails(),
    order.getState(),
    order.getRejectionReason()
  ));
}
```

**Estimated time:** 5 minutes

---

### **Step 6: Copy Event to Customer Service (Consumer Copy)**

**File:** `customer-service/customer-service-event-handling/src/main/java/io/eventuate/examples/tram/ordersandcustomers/orders/domain/OrderShippedEvent.java`

**Copy the event class:**
```java
package io.eventuate.examples.tram.ordersandcustomers.orders.domain;

// Consumer-side copy for deserialization
public record OrderShippedEvent(OrderDetails orderDetails) implements OrderEvent {
}
```

**Note:** This is a duplicate for deserialization purposes. In production, consider using a shared events library.

**Estimated time:** 2 minutes

---

### **Step 7: Add Event Consumer in Customer Service**

**File:** `customer-service/customer-service-event-handling/src/main/java/io/eventuate/examples/tram/ordersandcustomers/customers/eventhandlers/OrderEventConsumer.java`

**Add handler:**
```java
@EventuateDomainEventHandler(
  subscriberId = "OrderEventConsumer",
  channel = "io.eventuate.examples.tram.ordersandcustomers.orders.domain.Order"
)
public void handleOrderShippedEvent(DomainEventEnvelope<OrderShippedEvent> domainEventEnvelope) {
  OrderShippedEvent event = domainEventEnvelope.getEvent();
  Long orderId = Long.parseLong(domainEventEnvelope.getAggregateId());
  
  // Business logic: Log, send notification, update metrics, etc.
  System.out.println("Order shipped: " + orderId + " for customer: " + event.orderDetails().customerId());
}
```

**Estimated time:** 6 minutes

---

### **Step 8: Copy Event to Order History Service (Consumer Copy)**

**File:** `order-history-service/order-history-service-event-handling/src/main/java/io/eventuate/examples/tram/ordersandcustomers/orders/domain/OrderShippedEvent.java`

**Copy the event class:**
```java
package io.eventuate.examples.tram.ordersandcustomers.orders.domain;

// Consumer-side copy for deserialization
public record OrderShippedEvent(OrderDetails orderDetails) implements OrderEvent {
}
```

**Estimated time:** 2 minutes

---

### **Step 9: Update Order History Service Consumer**

**File:** `order-history-service/order-history-service-event-handling/src/main/java/io/eventuate/examples/tram/ordersandcustomers/orderhistory/backend/OrderHistoryEventConsumer.java`

**Add handler:**
```java
@EventuateDomainEventHandler(
  subscriberId = "customerHistoryServiceEvents",
  channel = "io.eventuate.examples.tram.ordersandcustomers.orders.domain.Order"
)
public void orderShippedEventHandler(DomainEventEnvelope<OrderShippedEvent> domainEventEnvelope) {
  OrderShippedEvent event = domainEventEnvelope.getEvent();
  Long orderId = Long.parseLong(domainEventEnvelope.getAggregateId());
  orderHistoryViewService.shipOrder(event.orderDetails().customerId(), orderId);
}
```

**Estimated time:** 5 minutes

---

### **Step 10: Update Order History View Service**

**File:** `order-history-service/order-history-service-domain/src/main/java/io/eventuate/examples/tram/ordersandcustomers/orderhistory/backend/OrderHistoryViewService.java`

**Add method:**
```java
public void shipOrder(Long customerId, Long orderId) {
  CustomerView customerView = customerViewRepository.findById(customerId)
    .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + customerId));
  
  OrderView orderView = customerView.getOrders().get(orderId.toString());
  if (orderView != null) {
    orderView.setState(OrderState.SHIPPED);
    customerViewRepository.save(customerView);
  }
}
```

**Estimated time:** 5 minutes

---

## Testing Plan

### **Test 1: Manual API Test**
```bash
# 1. Create customer
curl -X POST http://localhost:8081/customers \
  -H "Content-Type: application/json" \
  -d '{"name": {"firstName": "John", "lastName": "Doe"}, "creditLimit": {"amount": "5000"}}'

# 2. Create order
curl -X POST http://localhost:8082/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId": 1, "orderTotal": {"amount": "100"}}'

# 3. Wait for approval (saga completes)
sleep 3

# 4. Ship order
curl -X POST http://localhost:8082/orders/1/ship

# 5. Verify order state
curl http://localhost:8082/orders/1

# Expected: {"orderId": 1, "state": "SHIPPED", ...}
```

**Estimated time:** 5 minutes

---

### **Test 2: Verify Event in Kafka UI**
1. Open http://localhost:8090
2. Navigate to Topics → `io.eventuate.examples.tram.ordersandcustomers.orders.domain.Order`
3. View Messages → Find `OrderShippedEvent`
4. Verify payload contains order details

**Estimated time:** 3 minutes

---

### **Test 3: Verify Customer Service Consumption**
```bash
# Check Customer Service logs
tail -f logs/customer-service.log | grep "Order shipped"

# Expected output:
# Order shipped: 1 for customer: 1
```

**Estimated time:** 2 minutes

---

### **Test 4: Verify Order History View**
```bash
curl http://localhost:8083/customers/1/orderhistory

# Expected: Order 1 should have state: "SHIPPED"
```

**Estimated time:** 2 minutes

---

### **Test 5: Negative Test - Ship Non-Approved Order**
```bash
# Try to ship a pending order
curl -X POST http://localhost:8082/orders/1/ship

# Expected: 500 error with message "Order must be APPROVED to ship"
```

**Estimated time:** 2 minutes

---

## Files to Modify

### Order Service (Publisher - Source of Truth)
1. ✅ **NEW:** `order-service/order-service-domain/.../OrderShippedEvent.java` (Original)
2. **MODIFY:** `order-service/order-service-domain/.../OrderState.java`
3. **MODIFY:** `order-service/order-service-domain/.../Order.java`
4. **MODIFY:** `order-service/order-service-domain/.../OrderService.java`
5. **MODIFY:** `order-service/order-service-restapi/.../OrderController.java`

### Customer Service (Consumer)
6. ✅ **NEW:** `customer-service/customer-service-event-handling/.../OrderShippedEvent.java` (Copy for deserialization)
7. **MODIFY:** `customer-service/customer-service-event-handling/.../OrderEventConsumer.java`

### Order History Service (Consumer)
8. ✅ **NEW:** `order-history-service/order-history-service-event-handling/.../OrderShippedEvent.java` (Copy for deserialization)
9. **MODIFY:** `order-history-service/order-history-service-event-handling/.../OrderHistoryEventConsumer.java`
10. **MODIFY:** `order-history-service/order-history-service-domain/.../OrderHistoryViewService.java`

**Total:** 10 files (3 new, 7 modified)

---

## Architecture Pattern

```
┌─────────────────────────────────────────────────────────────┐
│ ORDER SERVICE (Event Publisher - Owns OrderShippedEvent)    │
│                                                             │
│ order-service-domain/                                       │
│ └── OrderShippedEvent.java  ← SOURCE OF TRUTH              │
│                                                             │
│ Publishes to Kafka Topic:                                   │
│ io.eventuate...orders.domain.Order                          │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       │ Kafka
                       │
        ┌──────────────┴──────────────┐
        │                             │
        ▼                             ▼
┌───────────────────┐      ┌──────────────────────┐
│ CUSTOMER SERVICE  │      │ ORDER HISTORY SERVICE│
│ (Consumer)        │      │ (Consumer)           │
│                   │      │                      │
│ OrderShippedEvent │      │ OrderShippedEvent    │
│ (Copy)            │      │ (Copy)               │
└───────────────────┘      └──────────────────────┘
```

**Event Ownership:** Order Service owns `OrderShippedEvent` because it's part of the Order aggregate's domain.

**Consumer Copies:** Customer Service and Order History Service have copies only for deserialization.

---

## Build & Deploy

```bash
# 1. Rebuild services
./gradlew :order-service:order-service-main:bootJar -x test
./gradlew :customer-service:customer-service-main:bootJar -x test
./gradlew :order-history-service:order-history-service-main:bootJar -x test

# 2. Restart services
./start-services.sh
```

**Estimated time:** 5 minutes

---

## Total Estimated Time
- Implementation: 36 minutes
- Testing: 14 minutes
- Build & Deploy: 5 minutes
- **Total: ~55 minutes**

---

## Rollback Plan
If issues occur:
1. Revert git changes: `git revert HEAD`
2. Rebuild services: `./gradlew bootJar -x test`
3. Restart services: `./start-services.sh`

---

## Success Criteria
- ✅ Order state transitions: APPROVED → SHIPPED
- ✅ OrderShippedEvent published to Kafka from Order Service
- ✅ Customer Service logs event consumption
- ✅ Order History view shows SHIPPED state
- ✅ Cannot ship non-APPROVED orders (validation works)
- ✅ No errors in service logs
- ✅ All existing tests still pass

---

## Future Improvement
Consider creating a shared `events-common` library to avoid duplicating event classes across services. This would be a separate module that all services depend on.
