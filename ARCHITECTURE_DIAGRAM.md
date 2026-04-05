# Eventuate Tram - Complete Architecture Diagram

## System Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                                   CLIENT                                     │
│                          (Browser / Mobile / API Client)                     │
└────────────────────────────────────┬────────────────────────────────────────┘
                                     │ HTTP/REST
                                     ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                        API GATEWAY (Spring Cloud Gateway)                    │
│                                  Port: 8080                                  │
│                                                                              │
│  Routes:                                                                     │
│  - /customers/**  → Customer Service (8081)                                 │
│  - /orders/**     → Order Service (8082)                                    │
│  - /customers/*/orderhistory → Order History Service (8083)                 │
└──────────┬─────────────────────┬─────────────────────┬────────────────────┘
           │                     │                     │
           │ REST                │ REST                │ REST
           ▼                     ▼                     ▼
```

**Note:** This implementation uses a **shared MySQL database** for simplicity. In production microservices, each service should have its own database (database-per-service pattern) to ensure loose coupling and independent scalability.

## Service Layer

```
┌──────────────────────────┐  ┌──────────────────────────┐  ┌──────────────────────────┐
│   CUSTOMER SERVICE       │  │     ORDER SERVICE        │  │  ORDER HISTORY SERVICE   │
│   Port: 8081             │  │     Port: 8082           │  │     Port: 8083           │
├──────────────────────────┤  ├──────────────────────────┤  ├──────────────────────────┤
│ REST API:                │  │ REST API:                │  │ REST API:                │
│ • POST /customers        │  │ • POST /orders           │  │ • GET /customers/{id}/   │
│ • GET /customers/{id}    │  │ • GET /orders/{id}       │  │   orderhistory           │
│ • GET /customers         │  │ • POST /orders/{id}/     │  │                          │
│                          │  │   cancel                 │  │                          │
├──────────────────────────┤  ├──────────────────────────┤  ├──────────────────────────┤
│ Domain Model:            │  │ Domain Model:            │  │ View Model:              │
│ • Customer Entity        │  │ • Order Entity           │  │ • CustomerView           │
│   - id                   │  │   - id                   │  │   - id                   │
│   - name                 │  │   - customerId           │  │   - name                 │
│   - creditLimit          │  │   - orderTotal           │  │   - creditLimit          │
│   - creditReservations   │  │   - state                │  │   - orders (Map)         │
│                          │  │   - rejectionReason      │  │                          │
├──────────────────────────┤  ├──────────────────────────┤  ├──────────────────────────┤
│ Business Logic:          │  │ Business Logic:          │  │ Business Logic:          │
│ • Reserve credit         │  │ • Create order (PENDING) │  │ • Build CQRS view        │
│ • Release credit         │  │ • Approve order          │  │ • Update on events       │
│ • Check credit limit     │  │ • Reject order           │  │ • Query order history    │
│                          │  │ • Cancel order           │  │                          │
├──────────────────────────┤  ├──────────────────────────┤  ├──────────────────────────┤
│ Events Published:        │  │ Events Published:        │  │ Events Consumed:         │
│ • CustomerCreatedEvent   │  │ • OrderCreatedEvent      │  │ • CustomerCreatedEvent   │
│ • CustomerCreditReserved │  │ • OrderApprovedEvent     │  │ • OrderCreatedEvent      │
│   Event                  │  │ • OrderRejectedEvent     │  │ • OrderApprovedEvent     │
│ • CustomerCreditReserva  │  │ • OrderCancelledEvent    │  │ • OrderRejectedEvent     │
│   tionFailedEvent        │  │                          │  │ • OrderCancelledEvent    │
│ • CustomerValidation     │  │                          │  │                          │
│   FailedEvent            │  │                          │  │                          │
├──────────────────────────┤  ├──────────────────────────┤  ├──────────────────────────┤
│ Events Consumed:         │  │ Events Consumed:         │  │ No events published      │
│ • OrderCreatedEvent      │  │ • CustomerCreditReserved │  │                          │
│ • OrderCancelledEvent    │  │   Event                  │  │                          │
│                          │  │ • CustomerCreditReserva  │  │                          │
│                          │  │   tionFailedEvent        │  │                          │
│                          │  │ • CustomerValidation     │  │                          │
│                          │  │   FailedEvent            │  │                          │
└──────────┬───────────────┘  └──────────┬───────────────┘  └──────────┬───────────────┘
           │                              │                              │
           │ JPA                          │ JPA                          │ MongoDB
           ▼                              ▼                              ▼
```

## Data Layer

```
┌─────────────────────────────────────────────────────────┐  ┌──────────────────────────┐
│   MySQL (Shared Database: eventuate)                    │  │   MongoDB                │
│   Port: 3306                                            │  │   Port: 27017            │
├─────────────────────────────────────────────────────────┤  ├──────────────────────────┤
│ Tables:                                                 │  │ Collections:             │
│                                                         │  │                          │
│ customer (Customer Service)                             │  │ CustomerView             │
│ ├─ id (PK)                                              │  │ {                        │
│ ├─ name                                                 │  │   _id: 1,                │
│ ├─ credit_limit                                         │  │   name: "Jane",          │
│ └─ version                                              │  │   creditLimit: 100,      │
│                                                         │  │   orders: {              │
│ customer_credit_reservations (Customer Service)         │  │     "1": {               │
│ ├─ customer_id (FK)                                     │  │       state: "APPROVED", │
│ ├─ order_id                                             │  │       orderTotal: 50     │
│ └─ amount                                               │  │     }                    │
│                                                         │  │   }                      │
│ orders (Order Service)                                  │  │ }                        │
│ ├─ id (PK)                                              │  │                          │
│ ├─ customer_id                                          │  │                          │
│ ├─ order_total                                          │  │                          │
│ ├─ state (PENDING/APPROVED/REJECTED/CANCELLED)         │  │                          │
│ ├─ rejection_reason                                     │  │                          │
│ └─ version                                              │  │                          │
│                                                         │  │                          │
│ message (Shared Outbox - Transactional Outbox Pattern) │  │                          │
│ ├─ id (PK)                                              │  │                          │
│ ├─ destination (Kafka topic)                            │  │                          │
│ ├─ headers (JSON - aggregate_id, event_type, etc.)     │  │                          │
│ ├─ payload (JSON - event data)                          │  │                          │
│ ├─ published (0 = pending, 1 = published to Kafka)     │  │                          │
│ └─ creation_time                                        │  │                          │
│                                                         │  │                          │
│ offset_store (CDC Service offset tracking)              │  │                          │
│ received_messages (Duplicate detection)                 │  │                          │
│ cdc_monitoring (CDC health monitoring)                  │  │                          │
└──────────────────────────┬──────────────────────────────┘  └──────────────────────────┘
                           │
                           │ Binlog Monitoring
                           ▼
```

## Messaging Infrastructure

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    EVENTUATE CDC SERVICE (Change Data Capture)               │
│                                                                              │
│  Single Pipeline: Shared MySQL Database                                     │
│  ├─ Reads MySQL binlog (eventuate database)                                 │
│  ├─ Detects MESSAGE table inserts                                           │
│  └─ Publishes to Kafka                                                      │
│                                                                              │
│  Technology: Debezium-based CDC                                              │
└────────────────────────────────────┬────────────────────────────────────────┘
                                     │ Publishes
                                     ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                          APACHE KAFKA (Message Broker)                       │
│                              Port: 9092                                      │
├─────────────────────────────────────────────────────────────────────────────┤
│ Topics:                                                                      │
│                                                                              │
│ 1. io.eventuate.examples.tram.ordersandcustomers.customers.domain.Customer  │
│    ├─ CustomerCreatedEvent                                                  │
│    ├─ CustomerCreditReservedEvent                                           │
│    ├─ CustomerCreditReservationFailedEvent                                  │
│    └─ CustomerValidationFailedEvent                                         │
│                                                                              │
│ 2. io.eventuate.examples.tram.ordersandcustomers.orders.domain.Order        │
│    ├─ OrderCreatedEvent                                                     │
│    ├─ OrderApprovedEvent                                                    │
│    ├─ OrderRejectedEvent                                                    │
│    └─ OrderCancelledEvent                                                   │
│                                                                              │
│ Consumer Groups:                                                             │
│ • OrderEventConsumer (Customer Service subscribes to Order events)          │
│ • customerServiceEvents (Order Service subscribes to Customer events)       │
│ • customerHistoryServiceEvents (Order History subscribes to both)           │
└─────────────────────────────────────────────────────────────────────────────┘
                                     │
                                     │ Coordination
                                     ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                          ZOOKEEPER                                           │
│                          Port: 2181                                          │
│                                                                              │
│  • Kafka cluster coordination                                               │
│  • Leader election                                                           │
│  • Configuration management                                                  │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Saga Flow: Create Order (Success Path)

```
┌────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐
│ Client │    │  Order   │    │  Kafka   │    │ Customer │    │  Order   │
│        │    │ Service  │    │          │    │ Service  │    │ History  │
└───┬────┘    └────┬─────┘    └────┬─────┘    └────┬─────┘    └────┬─────┘
    │              │               │               │               │
    │ POST /orders │               │               │               │
    ├─────────────>│               │               │               │
    │              │               │               │               │
    │              │ 1. Create     │               │               │
    │              │    Order      │               │               │
    │              │    (PENDING)  │               │               │
    │              │    + Insert   │               │               │
    │              │    MESSAGE    │               │               │
    │              │               │               │               │
    │<─────────────┤               │               │               │
    │ {orderId: 1} │               │               │               │
    │              │               │               │               │
    │              │ 2. CDC reads  │               │               │
    │              │    binlog     │               │               │
    │              ├──────────────>│               │               │
    │              │ OrderCreated  │               │               │
    │              │ Event         │               │               │
    │              │               │               │               │
    │              │               │ 3. Consume    │               │
    │              │               │    Order      │               │
    │              │               │    Created    │               │
    │              │               ├──────────────>│               │
    │              │               │               │               │
    │              │               │               │ 4. Reserve    │
    │              │               │               │    Credit     │
    │              │               │               │    + Insert   │
    │              │               │               │    MESSAGE    │
    │              │               │               │               │
    │              │               │ 5. CDC reads  │               │
    │              │               │    binlog     │               │
    │              │               │<──────────────┤               │
    │              │               │ CustomerCredit│               │
    │              │               │ ReservedEvent │               │
    │              │               │               │               │
    │              │ 6. Consume    │               │               │
    │              │    Customer   │               │               │
    │              │    Credit     │               │               │
    │              │<──────────────┤               │               │
    │              │    Reserved   │               │               │
    │              │               │               │               │
    │              │ 7. Approve    │               │               │
    │              │    Order      │               │               │
    │              │    (APPROVED) │               │               │
    │              │    + Insert   │               │               │
    │              │    MESSAGE    │               │               │
    │              │               │               │               │
    │              │ 8. CDC reads  │               │               │
    │              │    binlog     │               │               │
    │              ├──────────────>│               │               │
    │              │ OrderApproved │               │               │
    │              │ Event         │               │               │
    │              │               │               │               │
    │              │               │ 9. Consume    │               │
    │              │               │    Order      │               │
    │              │               │    Approved   │               │
    │              │               ├───────────────────────────────>│
    │              │               │               │               │
    │              │               │               │               │ 10. Update
    │              │               │               │               │     CQRS
    │              │               │               │               │     View
    │              │               │               │               │
```

**Timeline:** T0 (0ms) → T1 (50ms) → T2 (100ms) → ... → T10 (500ms)
**Result:** Order APPROVED, Credit Reserved, View Updated

## Module Structure (Hexagonal Architecture)

### Customer Service Modules
```
customer-service/
├── customer-service-domain/              [CORE - Business Logic]
│   ├── Customer.java                     (Entity with business rules)
│   ├── CustomerService.java              (Domain service)
│   ├── CustomerRepository.java           (Port interface)
│   ├── CustomerEvent.java                (Event interface)
│   ├── CustomerCreatedEvent.java
│   ├── CustomerCreditReservedEvent.java
│   ├── CustomerCreditReservationFailedEvent.java
│   └── CustomerValidationFailedEvent.java
│
├── customer-service-restapi/             [ADAPTER - Inbound]
│   └── CustomerController.java           (REST endpoints)
│
├── customer-service-event-handling/      [ADAPTER - Inbound]
│   └── OrderEventConsumer.java           (Kafka consumer)
│       ├── @EventuateDomainEventHandler
│       ├── handleOrderCreatedEvent()
│       └── handleOrderCancelledEvent()
│
├── customer-service-event-publishing/    [ADAPTER - Outbound]
│   └── CustomerEventPublisherImpl.java   (Publishes to Kafka)
│
├── customer-service-persistence/         [ADAPTER - Outbound]
│   └── CustomerPersistenceConfiguration  (JPA setup)
│
└── customer-service-main/                [APPLICATION]
    ├── CustomerServiceMain.java          (@SpringBootApplication)
    └── application.properties            (Configuration)
```

### Order Service Modules
```
order-service/
├── order-service-domain/
│   ├── Order.java
│   ├── OrderService.java
│   ├── OrderState.java (PENDING/APPROVED/REJECTED/CANCELLED)
│   └── Events: OrderCreated, OrderApproved, OrderRejected, OrderCancelled
│
├── order-service-restapi/
│   └── OrderController.java
│
├── order-service-event-handling/
│   └── CustomerEventConsumer.java
│       ├── handleCustomerCreditReservedEvent()
│       ├── handleCustomerCreditReservationFailedEvent()
│       └── handleCustomerValidationFailedEvent()
│
├── order-service-event-publishing/
│   └── OrderEventPublisherImpl.java
│
├── order-service-persistence/
│   └── OrderPersistenceConfiguration
│
└── order-service-main/
    └── OrderServiceMain.java
```

### Order History Service Modules
```
order-history-service/
├── order-history-service-domain/
│   ├── CustomerView.java (@Document - MongoDB)
│   ├── OrderView.java
│   └── OrderHistoryViewService.java
│
├── order-history-service-restapi/
│   └── OrderHistoryController.java
│
├── order-history-service-event-handling/
│   └── OrderHistoryEventConsumer.java
│       ├── customerCreatedEventHandler()
│       ├── orderCreatedEventHandler()
│       ├── orderApprovedEventHandler()
│       ├── orderRejectedEventHandler()
│       └── handleOrderCancelledEvent()
│
└── order-history-service-main/
    └── OrderHistoryServiceMain.java
```
