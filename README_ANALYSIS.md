# Codebase Analysis - Quick Reference

## 📚 Documentation Created

I've created comprehensive documentation for the Eventuate Tram Customers and Orders codebase:

### 1. **CODEBASE_ANALYSIS.md** - Functional & Technical Deep Dive
Complete analysis covering:
- **Functional Perspective:** Business capabilities, rules, user scenarios
- **Technical Perspective:** Architecture patterns (Saga, CQRS, Transactional Outbox, Hexagonal)
- **Implementation Details:** Event publishing/consumption flows, concurrency control
- **Design Decisions & Tradeoffs:** Why specific patterns were chosen
- **Failure Scenarios & Recovery:** How the system handles failures
- **Performance & Security:** Characteristics and considerations
- **Operational Runbook:** How to run, monitor, and troubleshoot

### 2. **ARCHITECTURE_DIAGRAM.md** - Visual Architecture
Comprehensive diagrams showing:
- **System Overview:** Client → API Gateway → Services
- **Service Layer:** Detailed view of each microservice
- **Data Layer:** Database schemas and MESSAGE tables
- **Messaging Infrastructure:** Kafka, CDC, Zookeeper
- **Saga Flow:** Step-by-step sequence diagram
- **Module Structure:** Hexagonal architecture breakdown

## 🎯 Quick Summary

### What This System Does
An **Order Management System** demonstrating distributed transactions across microservices using:
- **Choreography-based Sagas** for cross-service transactions
- **CQRS** for optimized queries
- **Event-driven architecture** for loose coupling
- **Transactional Outbox** for reliable event publishing

### Key Business Flow
```
1. Client creates order ($50)
2. Order Service creates order in PENDING state
3. Customer Service reserves $50 credit
4. Order Service approves order → APPROVED state
5. Order History Service updates CQRS view
```

### Architecture Patterns

**1. Saga Pattern (Choreography)**
- No central orchestrator
- Services react to domain events
- Eventual consistency across services

**2. Transactional Outbox**
- Atomically update database + publish event
- CDC reads binlog → publishes to Kafka
- Guarantees no lost events

**3. CQRS**
- Separate read model (Order History Service)
- Denormalized view in MongoDB
- Optimized for queries

**4. Hexagonal Architecture**
- Domain logic isolated from infrastructure
- Clear separation: domain, restapi, event-handling, persistence

### Technology Stack
- **Services:** Spring Boot 3.x, JPA
- **Messaging:** Kafka, Eventuate Tram, CDC Service
- **Databases:** MySQL (Customer, Order), MongoDB (Order History)
- **Infrastructure:** Docker, Zookeeper

### Services

**Customer Service (Port 8081)**
- Manages customers and credit limits
- Reserves/releases credit for orders
- Publishes: CustomerCreatedEvent, CustomerCreditReservedEvent, etc.
- Consumes: OrderCreatedEvent, OrderCancelledEvent

**Order Service (Port 8082)**
- Manages orders and their states
- Creates orders in PENDING state
- Approves/rejects based on credit availability
- Publishes: OrderCreatedEvent, OrderApprovedEvent, OrderRejectedEvent
- Consumes: CustomerCreditReservedEvent, CustomerCreditReservationFailedEvent

**Order History Service (Port 8083)**
- CQRS read model
- Maintains denormalized view of customers + orders
- Consumes: All Customer and Order events
- Publishes: Nothing (read-only)

### Data Flow

**Write Path:**
```
Service → Update Entity → Insert MESSAGE table → CDC reads binlog → Kafka → Consumers
```

**Read Path:**
```
Client → API Gateway → Service → Database → Response
```

**CQRS Path:**
```
Events → Order History Service → MongoDB → Query API
```

### Key Design Decisions

**Why Choreography?**
- Simple saga (3 steps)
- Services remain autonomous
- No single point of failure

**Why Separate Databases?**
- Bounded contexts
- Independent scaling
- Technology flexibility

**Why CQRS?**
- Avoid distributed queries
- Optimize for read performance
- Denormalized data for UI

### Consistency Model

**Within Service:** Strong consistency (ACID)
**Across Services:** Eventual consistency (~100-500ms lag)
**Event Delivery:** At-least-once (idempotency required)

### Running Locally

```bash
# Start infrastructure
docker-compose up -d

# Build and run
./gradlew :end-to-end-tests:runApplicationMySQL

# Test
curl -X POST http://localhost:8080/customers \
  -H "Content-Type: application/json" \
  -d '{"name": "Jane Doe", "creditLimit": {"amount": 100}}'

curl -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId": 1, "orderTotal": {"amount": 50}}'

curl http://localhost:8080/orders/1
curl http://localhost:8080/customers/1/orderhistory
```

### Monitoring

```bash
# Service health
curl http://localhost:8081/actuator/health

# Kafka consumer lag
docker exec <kafka> kafka-consumer-groups --describe --group OrderEventConsumer

# Unpublished events
mysql -e "SELECT COUNT(*) FROM MESSAGE WHERE published = 0"
```

## 📖 Next Steps

1. Read **CODEBASE_ANALYSIS.md** for detailed functional and technical understanding
2. Review **ARCHITECTURE_DIAGRAM.md** for visual architecture reference
3. Explore the code starting with domain models:
   - `Customer.java` - Customer entity with credit management
   - `Order.java` - Order entity with state machine
   - `CustomerService.java` - Business logic for credit operations
   - `OrderService.java` - Business logic for order operations

## 🔍 Key Files to Explore

**Domain Logic:**
- `customer-service/customer-service-domain/src/main/java/.../Customer.java`
- `order-service/order-service-domain/src/main/java/.../Order.java`

**Event Handlers:**
- `customer-service/customer-service-event-handling/.../OrderEventConsumer.java`
- `order-service/order-service-event-handling/.../CustomerEventConsumer.java`

**REST APIs:**
- `customer-service/customer-service-restapi/.../CustomerController.java`
- `order-service/order-service-restapi/.../OrderController.java`

**CQRS View:**
- `order-history-service/order-history-service-domain/.../CustomerView.java`
- `order-history-service/order-history-service-event-handling/.../OrderHistoryEventConsumer.java`
