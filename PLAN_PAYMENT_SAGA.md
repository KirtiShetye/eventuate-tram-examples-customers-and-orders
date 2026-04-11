# Payment Service and Saga Implementation Plan

## Overview
Add a Payment stub service that accepts/declines payments and implement a Saga orchestration pattern for Order → Payment → Order flow with compensating transactions.

## Architecture Pattern
This implementation uses **Choreography-based Saga** (event-driven) consistent with the existing codebase architecture where services communicate via domain events through Kafka.

**Flow:**
1. Order Service creates order (PENDING) → publishes `OrderCreatedEvent`
2. Payment Service consumes event → processes payment → publishes `PaymentApprovedEvent` or `PaymentDeclinedEvent`
3. Order Service consumes payment result → updates order state (APPROVED/REJECTED)
4. On cancellation: Order Service publishes `OrderCancelledEvent` → Payment Service refunds (compensating action)

---

## Implementation Steps

### 1. Create Payment Service Structure
Following the existing multi-module pattern (domain, persistence, event-handling, event-publishing, main):

```
payment-service/
├── payment-service-domain/
├── payment-service-persistence/
├── payment-service-event-handling/
├── payment-service-event-publishing/
└── payment-service-main/
```

**Files to create:**
- `settings.gradle` - Add payment-service modules
- Module build.gradle files for each submodule
- Dockerfile for containerization

---

### 2. Payment Domain Model

**payment-service-domain module:**

**Payment.java** (Entity)
```java
@Entity
@Table(name="payments")
class Payment {
  @Id Long id;
  Long orderId;
  Money amount;
  @Enumerated PaymentState state; // PENDING, APPROVED, DECLINED, REFUNDED
  String declineReason;
}
```

**PaymentState.java** (Enum)
- PENDING, APPROVED, DECLINED, REFUNDED

**PaymentService.java** (Business Logic)
- `processPayment(orderId, amount)` - Stub logic: approve if amount < $100, decline otherwise
- `refundPayment(orderId)` - Compensating action

**Events:**
- `PaymentRequestedEvent` (optional, for audit)
- `PaymentApprovedEvent(orderId, amount)`
- `PaymentDeclinedEvent(orderId, amount, reason)`
- `PaymentRefundedEvent(orderId, amount)`

---

### 3. Payment Persistence

**payment-service-persistence module:**

**PaymentRepository.java**
```java
interface PaymentRepository extends CrudRepository<Payment, Long> {
  Optional<Payment> findByOrderId(Long orderId);
}
```

---

### 4. Payment Event Handling (Saga Participant)

**payment-service-event-handling module:**

**OrderEventConsumer.java**
- Subscribe to `OrderCreatedEvent` → trigger `PaymentService.processPayment()`
- Subscribe to `OrderCancelledEvent` → trigger `PaymentService.refundPayment()` (compensating action)

**Configuration:**
```java
@Bean
DomainEventDispatcher orderEventDispatcher() {
  return new DomainEventDispatcher(
    "paymentServiceEvents",
    domainEventHandlers(orderEventConsumer)
  );
}
```

---

### 5. Payment Event Publishing

**payment-service-event-publishing module:**

**PaymentEventPublisher.java**
- Publish payment result events to Kafka
- Uses `DomainEventPublisher` from Eventuate Tram

---

### 6. Payment Main Application

**payment-service-main module:**

**PaymentServiceApplication.java**
- Spring Boot main class
- Enable JPA, Eventuate Tram messaging
- Configuration for Kafka, MySQL

**application.yml**
- Database: MySQL (shared database `eventuate`, table `payments`)
- Kafka bootstrap servers
- Port: 8084

**Dockerfile**
- Java 17 base image
- Expose port 8084

---

### 7. Modify Order Service (Saga Initiator)

**order-service-event-handling module:**

**PaymentEventConsumer.java** (NEW)
- Subscribe to `PaymentApprovedEvent` → call `OrderService.approveOrder()`
- Subscribe to `PaymentDeclinedEvent` → call `OrderService.rejectOrder()`

**Update OrderEventConsumer configuration:**
```java
@Bean
DomainEventDispatcher paymentEventDispatcher() {
  return new DomainEventDispatcher(
    "orderServicePaymentEvents",
    domainEventHandlers(paymentEventConsumer)
  );
}
```

**order-service-domain:**
- No changes needed - `approveOrder()` and `rejectOrder()` already exist

---

### 8. Shared Event Definitions

Create payment event classes in both services (following existing pattern where events are duplicated):

**In payment-service-domain:**
- `OrderCreatedEvent.java` (copy from order-service)
- `OrderCancelledEvent.java` (copy from order-service)

**In order-service-event-handling:**
- `PaymentApprovedEvent.java`
- `PaymentDeclinedEvent.java`
- `PaymentRefundedEvent.java`

---

### 9. Database Schema

**MySQL migration (payments table):**
```sql
CREATE TABLE payments (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL UNIQUE,
  amount DECIMAL(19,2) NOT NULL,
  state VARCHAR(20) NOT NULL,
  decline_reason VARCHAR(255),
  version BIGINT
);
```

---

### 10. Docker Compose Integration

**Update docker-compose.yml:**
```yaml
payment-service:
  image: payment-service:latest
  ports:
    - "8084:8084"
  environment:
    SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/eventuate
    SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:29092
  depends_on:
    - mysql
    - kafka
```

---

### 11. Build Configuration

**Root build.gradle:**
- Add payment-service dependencies

**settings.gradle:**
```gradle
include 'payment-service:payment-service-domain'
include 'payment-service:payment-service-persistence'
include 'payment-service:payment-service-event-handling'
include 'payment-service:payment-service-event-publishing'
include 'payment-service:payment-service-main'
```

---

### 12. Testing

**Component Tests:**
- `PaymentServiceTest.java` - Test payment approval/decline logic
- `PaymentSagaTest.java` - Test end-to-end saga flow
- `PaymentCompensationTest.java` - Test refund on order cancellation

**Integration Test Scenario:**
1. Create order with amount $50 → Payment approved → Order APPROVED
2. Create order with amount $150 → Payment declined → Order REJECTED
3. Create and cancel order → Payment refunded

---

## Saga Flow Diagram

```
┌─────────────┐         ┌─────────────┐         ┌─────────────┐
│   Client    │         │   Order     │         │  Payment    │
│             │         │  Service    │         │  Service    │
└──────┬──────┘         └──────┬──────┘         └──────┬──────┘
       │                       │                       │
       │ POST /orders          │                       │
       │──────────────────────>│                       │
       │                       │                       │
       │                       │ OrderCreatedEvent     │
       │                       │──────────────────────>│
       │                       │                       │
       │                       │                       │ Process Payment
       │                       │                       │ (Stub Logic)
       │                       │                       │
       │                       │ PaymentApprovedEvent  │
       │                       │<──────────────────────│
       │                       │                       │
       │ Order APPROVED        │ Update Order State    │
       │<──────────────────────│                       │
       │                       │                       │

Compensating Transaction (Cancel Order):

       │ POST /orders/{id}/cancel                     │
       │──────────────────────>│                       │
       │                       │                       │
       │                       │ OrderCancelledEvent   │
       │                       │──────────────────────>│
       │                       │                       │
       │                       │                       │ Refund Payment
       │                       │                       │ (Compensation)
       │                       │                       │
       │                       │ PaymentRefundedEvent  │
       │                       │<──────────────────────│
```

---

## Key Design Decisions

1. **Choreography over Orchestration**: Uses event-driven saga (consistent with existing architecture) rather than centralized orchestrator

2. **Stub Payment Logic**: Simple rule - approve if amount < $100, decline otherwise (easily replaceable with real payment gateway)

3. **Idempotency**: Payment service checks if payment already exists for orderId before processing

4. **Compensating Action**: Refund on order cancellation (state transition: APPROVED → REFUNDED)

5. **Shared Database**: Uses existing MySQL database for simplicity (production would use separate database)

6. **Event Duplication**: Payment events defined in both services (follows existing pattern in codebase)

---

## Files Summary

**New Files:** ~35 files
- 5 build.gradle files (payment modules)
- 15 Java domain/service classes
- 8 Java event classes
- 3 Java repository/config classes
- 1 Dockerfile
- 1 SQL migration
- Docker compose updates

**Modified Files:** ~5 files
- settings.gradle
- docker-compose.yml
- order-service event handling configuration
- Root build.gradle (dependencies)

---

## Validation Checklist

- [ ] Payment service starts successfully
- [ ] Order with amount < $100 gets approved
- [ ] Order with amount >= $100 gets declined
- [ ] Cancelled order triggers payment refund
- [ ] Events published to correct Kafka topics
- [ ] Database tables created correctly
- [ ] All services communicate via events
- [ ] Compensating transaction works correctly
