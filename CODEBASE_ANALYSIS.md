# Eventuate Tram Customers and Orders - Codebase Analysis

## FUNCTIONAL PERSPECTIVE

### What Does This System Do?

This is an **Order Management System** that demonstrates distributed transaction management across microservices using event-driven architecture.

### Business Capabilities

**1. Customer Management**
- Create customers with credit limits
- Track available credit
- Reserve credit for orders
- Release credit when orders cancelled

**2. Order Processing**
- Create orders (requires credit check)
- Approve orders (when credit available)
- Reject orders (insufficient credit or invalid customer)
- Cancel orders (releases reserved credit)

**3. Order History Queries**
- View customer's complete order history
- See all orders with their states
- Unified view across services

### Business Rules

**Credit Management:**
```
Available Credit = Credit Limit - Sum(All Reserved Credits)

Example:
- Customer has $100 credit limit
- Order 1: $30 (APPROVED) → Reserved: $30, Available: $70
- Order 2: $80 (PENDING) → Would exceed limit → REJECTED
- Cancel Order 1 → Reserved: $0, Available: $100
```

**Order State Machine:**
```
PENDING → APPROVED (credit reserved successfully)
PENDING → REJECTED (insufficient credit or invalid customer)
APPROVED → CANCELLED (user cancels, credit released)
```

**Validation Rules:**
- Customer must exist
- Order total must be > 0
- Customer must have sufficient available credit
- Only APPROVED orders can be cancelled

### User Scenarios

**Scenario 1: Successful Order**
```
1. Customer "Jane" exists with $100 credit limit
2. Create order for $50
3. System reserves $50 credit
4. Order approved
5. Jane now has $50 available credit
```

**Scenario 2: Insufficient Credit**
```
1. Customer "John" has $100 credit limit
2. Already has order for $80 (approved)
3. Create new order for $30
4. Available credit = $100 - $80 = $20
5. $20 < $30 → Order rejected
```

**Scenario 3: Order Cancellation**
```
1. Customer has approved order for $50
2. Cancel the order
3. System releases $50 credit
4. Credit becomes available again
```

---

## TECHNICAL PERSPECTIVE

### Architecture Patterns

#### 1. Choreography-Based Saga
**Purpose:** Maintain data consistency across services without distributed transactions

**How it works:**
- No central orchestrator
- Services react to domain events
- Each service publishes events after local transactions
- Other services consume events and perform their actions

**Example Flow:**
```
Order Service: Create Order → Publish OrderCreatedEvent
Customer Service: Consume event → Reserve Credit → Publish CustomerCreditReservedEvent
Order Service: Consume event → Approve Order → Publish OrderApprovedEvent
```

**Pros:**
- Loose coupling
- Services are autonomous
- No single point of failure

**Cons:**
- Harder to understand flow
- No centralized saga state
- Debugging is complex

#### 2. Transactional Outbox Pattern
**Purpose:** Atomically update database and publish events

**Implementation:**
```java
@Transactional
public void reserveCredit(long orderId, long customerId, Money orderTotal) {
  Customer customer = customerRepository.findById(customerId).get();
  customer.reserveCredit(orderId, orderTotal);  // Update entity
  
  // Both happen in same transaction:
  customerRepository.save(customer);  // 1. Save to Customer table
  customerEventPublisher.publish(     // 2. Insert into MESSAGE table
    customer, 
    new CustomerCreditReservedEvent(customerId, orderId)
  );
}
```

**Database Transaction:**
```sql
BEGIN;
  UPDATE Customer SET credit_reservations = ... WHERE id = 1;
  INSERT INTO MESSAGE (destination, payload, published) VALUES (...);
COMMIT;
```

**CDC Process:**
```
1. CDC Service monitors MySQL binlog
2. Detects INSERT into MESSAGE table
3. Publishes event to Kafka
4. Updates MESSAGE.published = 1
```

**Guarantees:**
- Event published if and only if entity updated
- No lost events
- At-least-once delivery (duplicates possible)

#### 3. CQRS (Command Query Responsibility Segregation)
**Purpose:** Optimize reads by maintaining separate read model

**Write Side:**
- Customer Service: Manages customers
- Order Service: Manages orders
- Each owns its data

**Read Side:**
- Order History Service: Denormalized view
- Subscribes to events from both services
- Builds CustomerView in MongoDB

**CustomerView Structure:**
```json
{
  "_id": 1,
  "name": "Jane Doe",
  "creditLimit": {"amount": 100},
  "orders": {
    "1": {"state": "APPROVED", "orderTotal": {"amount": 50}},
    "2": {"state": "REJECTED", "orderTotal": {"amount": 200}}
  }
}
```

**Benefits:**
- Single query returns customer + all orders
- No distributed joins
- Optimized for UI needs
- Can use different database technology

**Tradeoffs:**
- Eventual consistency (lag: ~100-500ms)
- Data duplication
- Must handle out-of-order events

#### 4. Hexagonal Architecture (Ports & Adapters)
**Purpose:** Separate business logic from infrastructure concerns

**Layers:**
```
domain/           → Core business logic (no framework dependencies)
restapi/          → REST adapter (Spring MVC)
event-handling/   → Event consumer adapter (Kafka)
event-publishing/ → Event publisher adapter (Kafka)
persistence/      → Database adapter (JPA)
main/             → Application wiring (Spring Boot)
```

**Benefits:**
- Business logic testable without infrastructure
- Easy to swap adapters (REST → gRPC, MySQL → Postgres)
- Clear boundaries

---

## TECHNICAL IMPLEMENTATION DETAILS

### Event Publishing Flow

**1. Service Layer:**
```java
// CustomerService.java
@Transactional
public void reserveCredit(long orderId, long customerId, Money orderTotal) {
  Customer customer = customerRepository.findById(customerId).get();
  customer.reserveCredit(orderId, orderTotal);
  customerEventPublisher.publish(customer, 
    new CustomerCreditReservedEvent(customerId, orderId));
}
```

**2. Event Publisher (Eventuate Tram):**
```java
// CustomerEventPublisherImpl.java
public class CustomerEventPublisherImpl 
    extends AbstractDomainEventPublisherForAggregateImpl<Customer, Long, CustomerEvent> 
    implements CustomerEventPublisher {
  
  // Inherits publish() method that:
  // 1. Serializes event to JSON
  // 2. Inserts into MESSAGE table
  // 3. All in same transaction as entity save
}
```

**3. MESSAGE Table:**
```
| id  | destination | aggregate_id | event_type                      | payload          | published |
|-----|-------------|--------------|----------------------------------|------------------|-----------|
| 123 | Customer    | 1            | CustomerCreditReservedEvent     | {"orderId": 1}   | 0         |
```

**4. CDC Service:**
- Monitors MySQL binlog for MESSAGE table changes
- Reads new rows where published = 0
- Publishes to Kafka topic
- Updates published = 1

**5. Kafka Topic:**
```
Topic: io.eventuate.examples.tram.ordersandcustomers.customers.domain.Customer
Key: 1 (aggregate ID)
Value: {
  "eventType": "CustomerCreditReservedEvent",
  "aggregateId": "1",
  "payload": {"customerId": 1, "orderId": 1}
}
```

### Event Consumption Flow

**1. Kafka Consumer:**
```java
// CustomerEventConsumer.java (in Order Service)
@EventuateDomainEventHandler(
  subscriberId = "customerServiceEvents",
  channel = "io.eventuate.examples.tram.ordersandcustomers.customers.domain.Customer"
)
public void handleCustomerCreditReservedEvent(
    DomainEventEnvelope<CustomerCreditReservedEvent> envelope) {
  
  Long orderId = envelope.getEvent().orderId();
  orderService.approveOrder(orderId);
}
```

**2. Eventuate Tram Framework:**
- Manages Kafka consumer
- Deserializes event
- Routes to correct handler method
- Handles consumer group management
- Provides at-least-once delivery

**3. Service Layer:**
```java
// OrderService.java
public void approveOrder(Long orderId) {
  Order order = orderRepository.findById(orderId).get();
  order.noteCreditReserved();  // State: PENDING → APPROVED
  orderEventPublisher.publish(order, 
    new OrderApprovedEvent(order.getOrderDetails()));
}
```

### Concurrency Control

**Optimistic Locking:**
```java
@Entity
public class Customer {
  @Id
  private Long id;
  
  @Version  // Optimistic locking
  private Long version;
  
  // If two transactions try to update same customer:
  // 1. First transaction commits → version incremented
  // 2. Second transaction fails with OptimisticLockException
  // 3. Retry logic handles the failure
}
```

### Error Handling

**1. Business Validation Errors:**
```java
try {
  customer.reserveCredit(orderId, orderTotal);
  // Publish success event
} catch (CustomerCreditLimitExceededException e) {
  // Publish failure event
  customerEventPublisher.publish(customer, 
    new CustomerCreditReservationFailedEvent(customerId, orderId));
}
```

**2. Technical Errors:**
- Database down → Transaction fails, no event published
- Kafka down → CDC retries publishing
- Service down → Kafka retains events, consumed when service restarts

**3. Idempotency:**
Event handlers should be idempotent (handle duplicates):
```java
// Check if already processed
if (order.getState() != OrderState.PENDING) {
  return;  // Already processed, skip
}
order.noteCreditReserved();
```

---

## KEY TECHNOLOGIES

### Eventuate Tram Framework
**What it provides:**
- Transactional messaging (outbox pattern)
- Domain event publishing/subscribing
- CDC service integration
- Kafka abstraction

**Core Components:**
- `DomainEventPublisher` - Publishes events
- `@EventuateDomainEventHandler` - Marks event handlers
- `DomainEventEnvelope` - Event wrapper with metadata
- `MessageProducer/MessageConsumer` - Low-level messaging

### Spring Boot Integration
- Auto-configuration for Eventuate Tram
- JPA for persistence
- Spring Data MongoDB for CQRS view
- Spring Cloud Gateway for API routing

### Database Technologies

**MySQL (Customer & Order Services):**
- ACID transactions
- Binlog for CDC
- JPA/Hibernate ORM

**MongoDB (Order History Service):**
- Document model (flexible schema)
- Fast queries (no joins)
- Embedded documents for orders

---

## DEPLOYMENT & OPERATIONS

### Local Development
```bash
# Start infrastructure
docker-compose up -d

# Run services
./gradlew :end-to-end-tests:runApplicationMySQL
```

### Production Deployment
- AWS Fargate (Terraform configs provided)
- Kubernetes (manifests provided)
- Azure (Terraform configs provided)

### Monitoring Points
- Service health: Spring Boot Actuator `/actuator/health`
- Kafka lag: Consumer group lag monitoring
- CDC health: Check MESSAGE.published = 0 count
- Database: Connection pool, query performance

### Scaling Considerations
- Services: Horizontal scaling (multiple instances)
- Kafka: Partition by aggregate ID for parallelism
- CDC: Single instance per database (leader election)
- Databases: Read replicas, sharding

---

## TESTING STRATEGY

### Unit Tests
- Domain logic in isolation
- No external dependencies
- Fast execution

### Component Tests
- Test entire service with Testcontainers
- Spin up MySQL, Kafka in Docker
- Verify REST API and event handling
- Located in `src/componentTest/`

### End-to-End Tests
- All services + infrastructure
- Test complete saga flows
- Located in `end-to-end-tests/`

### Contract Tests
- Verify event schemas between services
- Ensure backward compatibility
- Located in `src/contractTest/`

---

## DESIGN DECISIONS & TRADEOFFS

### Why Choreography over Orchestration?
**Decision:** Use event choreography instead of saga orchestrator

**Reasoning:**
- Simple saga (only 3 steps)
- Services remain autonomous
- No need for additional orchestrator service

**Tradeoff:**
- Harder to visualize complete saga flow
- No centralized saga state/monitoring
- Debugging requires tracing across services

### Why Separate Databases per Service?
**Decision:** Each service has its own database

**Reasoning:**
- Database per service pattern (bounded context)
- Independent scaling
- Technology flexibility (MySQL vs MongoDB)
- Failure isolation

**Tradeoff:**
- Cannot use database joins
- Data duplication (CQRS view)
- Eventual consistency

### Why Eventuate Tram?
**Decision:** Use Eventuate Tram framework instead of raw Kafka

**Reasoning:**
- Handles transactional outbox automatically
- Provides CDC service out-of-box
- Abstracts Kafka complexity
- Domain event abstraction

**Tradeoff:**
- Framework dependency
- Learning curve
- Less control over low-level messaging

### Why CQRS for Order History?
**Decision:** Separate read model in MongoDB

**Reasoning:**
- Avoid distributed queries across services
- Optimize for read performance
- Different database technology (document model)
- UI needs denormalized data

**Tradeoff:**
- Eventual consistency
- Data duplication
- Synchronization complexity
- Must handle event ordering issues

---

## DATA CONSISTENCY MODEL

### Consistency Guarantees

**Within a Service:**
- Strong consistency (ACID transactions)
- Optimistic locking prevents lost updates

**Across Services:**
- Eventual consistency
- Typical lag: 100-500ms
- At-least-once event delivery

### Handling Eventual Consistency

**1. Order State Visibility:**
```
Client creates order → Receives orderId immediately
Order state = PENDING (visible to client)
After ~500ms → Order state = APPROVED/REJECTED
Client must poll or use webhooks for final state
```

**2. CQRS View Lag:**
```
Order approved in Order Service at T0
Order History view updated at T0 + 200ms
Client may see stale data briefly
```

**3. Idempotency:**
```java
// Event handlers must be idempotent
public void handleOrderCreatedEvent(DomainEventEnvelope<OrderCreatedEvent> envelope) {
  // Check if already processed
  if (customerView.hasOrder(orderId)) {
    return;  // Skip duplicate
  }
  customerView.addOrder(orderId, orderTotal);
}
```

---

## FAILURE SCENARIOS & RECOVERY

### Scenario 1: Customer Service Down
```
1. Order created → OrderCreatedEvent published to Kafka
2. Customer Service is down
3. Kafka retains event
4. Customer Service restarts
5. Consumes event from Kafka
6. Processes credit reservation
7. Saga continues normally
```

**Recovery:** Automatic (Kafka retention + consumer offset tracking)

### Scenario 2: Database Transaction Fails
```
1. Customer Service tries to reserve credit
2. Database transaction fails (deadlock, connection lost)
3. Transaction rolled back
4. No event inserted into MESSAGE table
5. No event published to Kafka
6. Order remains in PENDING state
```

**Recovery:** Requires retry or timeout mechanism

### Scenario 3: CDC Service Down
```
1. Services continue writing to MESSAGE table
2. Events accumulate (published = 0)
3. CDC Service restarts
4. Reads binlog from last position
5. Publishes all pending events
6. System catches up
```

**Recovery:** Automatic (binlog position tracking)

### Scenario 4: Duplicate Events
```
1. CDC publishes event to Kafka
2. Kafka acknowledges
3. CDC crashes before marking published = 1
4. CDC restarts, republishes same event
5. Consumer receives duplicate
```

**Recovery:** Event handlers must be idempotent

---

## PERFORMANCE CHARACTERISTICS

### Latency
- Synchronous REST call: ~10-50ms
- End-to-end saga completion: ~500ms-2s
- CQRS view update: ~200-500ms lag

### Throughput
- Limited by database write capacity
- Kafka can handle 100K+ messages/sec
- CDC service: ~1000-5000 events/sec per pipeline

### Scalability
- Services: Horizontal scaling (stateless)
- Kafka: Partition by aggregate ID
- Databases: Vertical scaling, read replicas
- CDC: Single instance per database (bottleneck)

---

## SECURITY CONSIDERATIONS

### Authentication & Authorization
- Not implemented in this example
- Production needs: OAuth2, JWT tokens
- API Gateway should handle auth

### Data Security
- Database credentials in environment variables
- No encryption at rest (should add for production)
- Kafka messages not encrypted (should use TLS)

### Network Security
- Services communicate over internal network
- API Gateway is public-facing
- Database ports should not be exposed

---

## OPERATIONAL RUNBOOK

### Starting the System
```bash
# 1. Start infrastructure
docker-compose up -d

# 2. Wait for services to be healthy
docker-compose ps

# 3. Run application
./gradlew :end-to-end-tests:runApplicationMySQL

# 4. Verify services
curl http://localhost:8081/actuator/health  # Customer Service
curl http://localhost:8082/actuator/health  # Order Service
curl http://localhost:8083/actuator/health  # Order History Service
```

### Monitoring Commands
```bash
# Check Kafka topics
docker exec -it <kafka-container> kafka-topics --list --bootstrap-server localhost:9092

# Check consumer lag
docker exec -it <kafka-container> kafka-consumer-groups --bootstrap-server localhost:9092 --describe --group OrderEventConsumer

# Check MESSAGE table
mysql -h localhost -u root -p customerdb -e "SELECT COUNT(*) FROM MESSAGE WHERE published = 0"

# Check CDC service logs
docker logs <cdc-container>
```

### Troubleshooting

**Problem: Order stuck in PENDING**
```
1. Check if Customer Service is running
2. Check Kafka consumer lag
3. Check CDC service health
4. Check MESSAGE table for unpublished events
```

**Problem: CQRS view out of sync**
```
1. Check Order History Service logs
2. Check Kafka consumer lag for customerHistoryServiceEvents
3. Verify events published to Kafka
4. Rebuild view from event stream if needed
```

**Problem: CDC service unhealthy**
```
1. Check database connectivity
2. Verify binlog enabled (MySQL) or WAL enabled (Postgres)
3. Check CDC service logs for errors
4. Restart CDC service
```
