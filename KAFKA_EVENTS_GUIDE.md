# Kafka Events Guide

## Event Flow for Order Creation

When you create an order, here's the step-by-step event flow:

### Step 1: Order Service - Create Order (Transactional Outbox)
**What happens:**
- Order Service receives POST request
- Creates Order entity in MySQL with state `PENDING`
- Writes `OrderCreatedEvent` to `message` table in same transaction
- Transaction commits (both order and event saved atomically)

**Event Published:**
```
Topic: io.eventuate.examples.tram.ordersandcustomers.orders.domain.Order
Event Type: OrderCreatedEvent
Payload: {"orderDetails":{"customerId":1,"orderTotal":{"amount":300}}}
```

### Step 2: CDC Service - Read Binlog & Publish to Kafka
**What happens:**
- CDC Service reads MySQL binlog
- Detects new row in `message` table
- Publishes event to Kafka topic
- Marks message as published in database

### Step 3: Customer Service - Reserve Credit (Saga Participant)
**What happens:**
- Saga orchestrator sends command to Customer Service
- Customer Service reserves credit (updates available credit)
- Publishes `CustomerCreditReservedEvent` to Kafka via outbox

**Event Published:**
```
Topic: io.eventuate.examples.tram.ordersandcustomers.customers.domain.Customer
Event Type: CustomerCreditReservedEvent
Payload: {"customerId":1,"orderId":4,"amount":300}
```

### Step 4: Order Service - Approve Order
**What happens:**
- Saga orchestrator receives success from Customer Service
- Updates Order state to `APPROVED`
- Writes `OrderApprovedEvent` to outbox

**Event Published:**
```
Topic: io.eventuate.examples.tram.ordersandcustomers.orders.domain.Order
Event Type: OrderApprovedEvent
Payload: {"orderDetails":{"customerId":1,"orderTotal":{"amount":300}}}
```

### Step 5: Order History Service - Update Read Model (CQRS)
**What happens:**
- Consumes `OrderCreatedEvent` and `OrderApprovedEvent` from Kafka
- Updates MongoDB with order information
- Maintains denormalized view for queries

## Viewing Kafka Events

### View All Order Events (from beginning)
```bash
docker exec eventuate-tram-examples-customers-and-orders-kafka-1 \
  kafka-console-consumer --bootstrap-server kafka:29092 \
  --topic io.eventuate.examples.tram.ordersandcustomers.orders.domain.Order \
  --from-beginning --max-messages 100
```

### View All Customer Events (from beginning)
```bash
docker exec eventuate-tram-examples-customers-and-orders-kafka-1 \
  kafka-console-consumer --bootstrap-server kafka:29092 \
  --topic io.eventuate.examples.tram.ordersandcustomers.customers.domain.Customer \
  --from-beginning --max-messages 100
```

### View Events with Timestamps and Keys
```bash
docker exec eventuate-tram-examples-customers-and-orders-kafka-1 \
  kafka-console-consumer --bootstrap-server kafka:29092 \
  --topic io.eventuate.examples.tram.ordersandcustomers.orders.domain.Order \
  --from-beginning \
  --property print.timestamp=true \
  --property print.key=true \
  --property print.headers=true
```

### View Only New Events (tail mode)
```bash
docker exec eventuate-tram-examples-customers-and-orders-kafka-1 \
  kafka-console-consumer --bootstrap-server kafka:29092 \
  --topic io.eventuate.examples.tram.ordersandcustomers.orders.domain.Order
```

### List All Topics
```bash
docker exec eventuate-tram-examples-customers-and-orders-kafka-1 \
  kafka-topics --bootstrap-server kafka:29092 --list
```

### Get Topic Details
```bash
docker exec eventuate-tram-examples-customers-and-orders-kafka-1 \
  kafka-topics --bootstrap-server kafka:29092 \
  --topic io.eventuate.examples.tram.ordersandcustomers.orders.domain.Order \
  --describe
```

### View Consumer Group Offsets
```bash
docker exec eventuate-tram-examples-customers-and-orders-kafka-1 \
  kafka-consumer-groups --bootstrap-server kafka:29092 --list

docker exec eventuate-tram-examples-customers-and-orders-kafka-1 \
  kafka-consumer-groups --bootstrap-server kafka:29092 \
  --group <group-name> --describe
```

## Event Structure

Each event in Kafka has:

**Headers:**
- `event-aggregate-type`: The domain entity type (Order, Customer)
- `event-aggregate-id`: The entity ID
- `event-type`: Specific event class name
- `PARTITION_ID`: Kafka partition
- `DESTINATION`: Topic name
- `ID`: Unique event ID
- `DATE`: Event timestamp

**Payload:**
- JSON containing event-specific data

## Checking Database Outbox

### View Messages in MySQL Outbox (before CDC processes)
```bash
docker exec eventuate-tram-examples-customers-and-orders-mysql-1 \
  mysql -uroot -prootpassword eventuate \
  -e "SELECT id, destination, creation_time, published FROM message ORDER BY creation_time DESC LIMIT 10;"
```

### View Received Messages (consumed by services)
```bash
docker exec eventuate-tram-examples-customers-and-orders-mysql-1 \
  mysql -uroot -prootpassword eventuate \
  -e "SELECT consumer_id, message_id, creation_time FROM received_messages ORDER BY creation_time DESC LIMIT 10;"
```

## Testing Saga Compensation (Failure Scenario)

Create an order that exceeds credit limit to see rejection events:

```bash
curl -X POST http://localhost:8082/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId": 1, "orderTotal": {"amount": "10000"}}'
```

This will trigger:
1. `OrderCreatedEvent`
2. Credit reservation fails
3. `OrderRejectedEvent` (saga compensation)
4. Order state becomes `REJECTED`

## Real-Time Event Monitoring

Open multiple terminals and run:

**Terminal 1 - Order Events:**
```bash
docker exec eventuate-tram-examples-customers-and-orders-kafka-1 \
  kafka-console-consumer --bootstrap-server kafka:29092 \
  --topic io.eventuate.examples.tram.ordersandcustomers.orders.domain.Order
```

**Terminal 2 - Customer Events:**
```bash
docker exec eventuate-tram-examples-customers-and-orders-kafka-1 \
  kafka-console-consumer --bootstrap-server kafka:29092 \
  --topic io.eventuate.examples.tram.ordersandcustomers.customers.domain.Customer
```

**Terminal 3 - Create Orders:**
```bash
curl -X POST http://localhost:8082/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId": 1, "orderTotal": {"amount": "100"}}'
```

You'll see events appear in real-time in the consumer terminals!
