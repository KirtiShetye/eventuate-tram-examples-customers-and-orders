# Eventuate Tram Customers and Orders - Functional & Technical Analysis

## 1. FUNCTIONAL PERSPECTIVE

### Business Domain
This is an **Order Management System** that demonstrates how to maintain data consistency across microservices without distributed transactions.

### Core Business Rules

**Customer Management:**
- Customers have a credit limit (e.g., $100)
- Credit can be reserved for orders
- Available credit = Credit limit - Sum of all reservations
- Cannot reserve credit if insufficient funds

**Order Management:**
- Orders go through states: PENDING → APPROVED/REJECTED
- Orders require credit reservation before approval
- Orders can be cancelled (releases reserved credit)
- Rejection reasons: INSUFFICIENT_CREDIT, UNKNOWN_CUSTOMER

**Order History:**
- Provides unified view of customer and their orders
- Read-only query service
- Denormalized data for fast queries

### User Workflows

**1. Create Order (Happy Path):**
```
Client → POST /orders {customerId: 1, orderTotal: $50}
↓
Order created in PENDING state
↓
Customer Service reserves $50 credit
↓
Order approved → APPROVED state
↓
Order History updated
```

**2. Create Order (Insufficient Credit):**
```
Client → POST /orders {customerId: 1, orderTotal: $200}
↓
Order created in PENDING state
↓
Customer Service fails to reserve (exceeds limit)
↓
Order rejected → REJECTED state
```

**3. Cancel Order:**
```
Client → POST /orders/{id}/cancel
↓
Order state → CANCELLED
↓
Customer Service releases reserved credit
↓
Order History updated
```

**4. Query Order History:**
```
Client → GET /customers/{id}/orderhistory
↓
Returns: customer info + all orders with states
```

---

## 2. TECHNICAL PERSPECTIVE

### Architecture Style
**Event-Driven Microservices with Choreography-Based Saga**

### Technology Stack

**Services:**
- Spring Boot 3.x
- Spring Data JPA (for Customer & Order services)
- Spring Data MongoDB (for Order History service)

**Messaging:**
- Apache Kafka (event broker)
- Eventuate Tram (transactional messaging framework)
- Eventuate CDC Service (Change Data Capture)

**Databases:**
- MySQL 8.0 (Customer DB, Order DB)
- MongoDB 7 (Order History CQRS view)

**Infrastructure:**
- Spring Cloud Gateway (API Gateway)
- Docker & Docker Compose
- Zookeeper (Kafka coordination)

### Key Design Patterns

#### 1. Saga Pattern (Choreography)
**Problem:** How to maintain data consistency across services without 2PC?

**Solution:** Each service publishes domain events after local transactions. Other services react to these events.

**Implementation:**
- Order Service creates order in PENDING state → publishes OrderCreatedEvent
- Customer Service listens → reserves credit → publishes CustomerCreditReservedEvent
- Order Service listens → approves order → publishes OrderApprovedEvent

**Benefits:**
- No central coordinator
- Loose coupling
- Each service owns its data

**Tradeoffs:**
- Eventual consistency
- Complex to understand flow
- Harder to debug

#### 2. Transactional Outbox Pattern
**Problem:** How to atomically update database AND publish event?

**Solution:** Write event to MESSAGE table in same transaction as entity update.

**Implementation:**
```
BEGIN TRANSACTION
  UPDATE Customer SET credit_reservations = ...
  INSERT INTO MESSAGE (destination, payload, published) VALUES (...)
COMMIT
```

CDC Service reads MESSAGE table → publishes to Kafka

**Benefits:**
- Guaranteed event delivery
- No dual-write problem
- ACID guarantees

#### 3. CQRS (Command Query Responsibility Segregation)
**Problem:** How to query data across multiple services?

**Solution:** Maintain a separate read model optimized for queries.

**Implementation:**
- Order History Service subscribes to Customer & Order events
- Builds denormalized CustomerView in MongoDB
- CustomerView contains customer info + all orders

**Benefits:**
- Fast queries (no joins across services)
- Optimized read model
- Independent scaling

**Tradeoffs:**
- Eventual consistency
- Data duplication
- Synchronization complexity

#### 4. Hexagonal Architecture (Ports & Adapters)
Each service is organized into modules:

```
domain/          - Business logic, entities, domain events
restapi/         - REST controllers (inbound adapter)
event-handling/  - Event consumers (inbound adapter)
event-publishing/- Event publishers (outbound adapter)
persistence/     - JPA configuration (outbound adapter)
main/            - Application entry point, configuration
```

**Benefits:**
- Clear separation of concerns
- Business logic independent of frameworks
- Easy to test
- Swappable adapters

---

## 3. TECHNICAL DEEP DIVE

### Event Flow Mechanism

**Step 1: Domain Event Creation**
```java
// In CustomerService.reserveCredit()
customer.reserveCredit(orderId, orderTotal);
CustomerCreditReservedEvent event = new CustomerCreditReservedEvent(customerId, orderId);
customerEventPublisher.publish(customer, event);
```

**Step 2: Transactional Outbox**
```java
// Eventuate Tram framework does this:
@Transactional
public void publish(Customer aggregate, CustomerEvent event) {
  // 1. Save aggregate
  customerRepository.save(aggregate);
  
  // 2. Insert into MESSAGE table (same transaction)
  messageTable.insert(
    destination: "Customer",
    aggregateId: customer.getId(),
    eventType: "CustomerCreditReservedEvent",
    payload: serialize(event)
  );
}
```

**Step 3: CDC (Change Data Capture)**
```
CDC Service monitors MySQL binlog
→ Detects INSERT into MESSAGE table
→ Publishes to Kafka topic: "...customers.domain.Customer"
→ Marks message as published in MESSAGE table
```

**Step 4: Event Consumption**
```java
// In OrderService's CustomerEventConsumer
@EventuateDomainEventHandler(
  subscriberId = "customerServiceEvents",
  channel = "...customers.domain.Customer"
)
public void handleCustomerCreditReservedEvent(
    DomainEventEnvelope<CustomerCreditReservedEvent> envelope) {
  orderService.approveOrder(envelope.getEvent().orderId());
}
```

### Database Schema

**Customer Service (MySQL):**
```sql
Customer table:
- id (PK)
- name
- credit_limit
- credit_reservations (JSON: {orderId: amount})
- version (optimistic locking)

MESSAGE table:
- id (PK)
- destination (aggregate type)
- aggregate_id
- event_type
- payload (JSON)
- published (0/1)
```

**Order Service (MySQL):**
```sql
orders table:
- id (PK)
- customer_id
- order_total
- state (PENDING/APPROVED/REJECTED/CANCELLED)
- rejection_reason
- version

MESSAGE table: (same structure)
```

**Order History Service (MongoDB):**
```javascript
CustomerView collection:
{
  _id: customerId,
  name: "Jane Doe",
  creditLimit: { amount: 100 },
  orders: {
    "1": { state: "APPROVED", orderTotal: { amount: 50 } },
    "2": { state: "REJECTED", orderTotal: { amount: 200 } }
  }
}
```

### Module Dependencies

**Customer Service:**
```
customer-service-main
├── depends on: customer-service-domain
├── depends on: customer-service-restapi
├── depends on: customer-service-event-handling
├── depends on: customer-service-event-publishing
└── depends on: customer-service-persistence

customer-service-event-handling
└── depends on: customer-service-domain

customer-service-event-publishing
└── depends on: customer-service-domain
```

**Order Service:** (same structure)

**Order History Service:**
```
order-history-service-main
├── depends on: order-history-service-domain
├── depends on: order-history-service-restapi
└── depends on: order-history-service-event-handling

order-history-service-event-handling
└── depends on: order-history-service-domain
```

### Concurrency & Consistency

**Optimistic Locking:**
- Both Customer and Order entities use `@Version`
- Prevents lost updates in concurrent scenarios

**Idempotency:**
- Event handlers should be idempotent
- Kafka provides at-least-once delivery
- Duplicate events may occur

**Eventual Consistency:**
- Order is PENDING until Customer Service responds
- Order History view may lag behind source services
- Typical lag: milliseconds to seconds

### Error Handling

**Saga Compensation:**
- If order cancelled → Customer Service releases credit
- No automatic rollback (choreography pattern)
- Each service handles its own failures

**Failure Scenarios:**
1. Customer doesn't exist → CustomerValidationFailedEvent → Order REJECTED
2. Insufficient credit → CustomerCreditReservationFailedEvent → Order REJECTED
3. Service down → Kafka retries event delivery
4. Database down → Transaction fails, no event published

---

## 4. DEPLOYMENT ARCHITECTURE

### Local Development
- Docker Compose runs infrastructure (MySQL, MongoDB, Kafka, CDC)
- Services run as Spring Boot applications on host
- Ports: 8081 (Customer), 8082 (Order), 8083 (Order History), 8080 (Gateway)

### Production Options
- **AWS Fargate:** Terraform configs in `aws-fargate-terraform/`
- **Kubernetes:** Manifests in `deployment/kubernetes/`
- **Azure:** Terraform configs in `deployment/terraform_azure/`

### Scalability Considerations

**Horizontal Scaling:**
- Each service can scale independently
- Kafka consumer groups ensure each event processed once
- CDC service should run as single instance per database

**Database Scaling:**
- Customer & Order services: MySQL read replicas
- Order History: MongoDB sharding for large datasets

**Bottlenecks:**
- CDC service (single point of failure per DB)
- Kafka throughput
- Database write capacity

---

## 5. TESTING STRATEGY

**Unit Tests:**
- Domain logic in isolation
- No external dependencies

**Component Tests:**
- Test entire service with Testcontainers
- Spin up MySQL, Kafka, service in Docker
- Verify REST API and event handling

**End-to-End Tests:**
- All services + infrastructure
- Test complete saga flows
- Located in `end-to-end-tests/`

**Contract Tests:**
- Verify event schemas between services
- Located in `*-event-publishing/src/contractTest/`

---

## 6. KEY TECHNICAL DECISIONS

### Why Choreography over Orchestration?
- Simpler for this use case (3 steps)
- No need for saga orchestrator service
- Services remain autonomous

### Why Eventuate Tram?
- Handles transactional outbox automatically
- Provides CDC service out-of-box
- Abstracts Kafka complexity
- Supports multiple databases

### Why Separate Databases?
- Each service owns its data (bounded context)
- Independent scaling
- Failure isolation
- Technology flexibility (MySQL vs MongoDB)

### Why CQRS for Order History?
- Avoids distributed queries
- Fast read performance
- Can use different database (MongoDB)
- Read model optimized for UI needs

---

## 7. OPERATIONAL CONCERNS

### Monitoring
- Each service exposes Spring Boot Actuator endpoints
- Kafka lag monitoring for consumer groups
- CDC service health checks

### Data Consistency
- Eventually consistent (typical lag: < 1 second)
- No lost events (CDC guarantees)
- Duplicate events possible (idempotency required)

### Disaster Recovery
- Kafka retains events (configurable retention)
- Can rebuild CQRS view from event stream
- Database backups for source data

### Performance
- Async processing (non-blocking)
- Event-driven scales better than sync calls
- CQRS view optimized for reads
