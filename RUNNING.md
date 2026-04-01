# Running the Eventuate Tram Customers and Orders Application

## Application is Now Running!

All services are up and running:

- **API Gateway**: http://localhost:8080
- **Customer Service**: http://localhost:8081
- **Order Service**: http://localhost:8082
- **Order History Service**: http://localhost:8083

## Infrastructure Services

The following infrastructure services are running via Docker Compose:

- **MySQL**: localhost:3306 (eventuate database)
- **MongoDB**: localhost:27017 (order_history database)
- **Kafka**: localhost:9092
- **Zookeeper**: localhost:2181
- **CDC Service**: localhost:8099

## Testing the Application

### 1. Create a Customer

```bash
curl -X POST http://localhost:8080/customers \
  -H "Content-Type: application/json" \
  -d '{"name": {"firstName": "John", "lastName": "Doe"}, "creditLimit": {"amount": 5000}}'
```

Response will include a customer ID.

### 2. Create an Order

```bash
curl -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId": "<CUSTOMER_ID>", "orderTotal": {"amount": 100}}'
```

Replace `<CUSTOMER_ID>` with the ID from step 1.

### 3. View Customer

```bash
curl http://localhost:8080/customers/<CUSTOMER_ID>
```

### 4. View Order

```bash
curl http://localhost:8080/orders/<ORDER_ID>
```

### 5. View Order History

```bash
curl http://localhost:8080/customers/<CUSTOMER_ID>/orderhistory
```

## API Documentation

- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **Customer Service API**: http://localhost:8081/swagger-ui.html
- **Order Service API**: http://localhost:8082/swagger-ui.html
- **Order History Service API**: http://localhost:8083/swagger-ui.html

## Monitoring

- **Customer Service Health**: http://localhost:8081/actuator/health
- **Order Service Health**: http://localhost:8082/actuator/health
- **Order History Service Health**: http://localhost:8083/actuator/health
- **API Gateway Health**: http://localhost:8080/actuator/health

## Viewing Logs

```bash
# Customer Service
tail -f logs/customer-service.log

# Order Service
tail -f logs/order-service.log

# Order History Service
tail -f logs/order-history-service.log

# API Gateway
tail -f logs/api-gateway.log

# Infrastructure (Docker Compose)
docker-compose logs -f
```

## Stopping the Application

### Stop Services

```bash
pkill -f "customer-service-main.*jar"
pkill -f "order-service-main.*jar"
pkill -f "order-history-service-main.*jar"
pkill -f "api-gateway-service-main.*jar"
```

### Stop Infrastructure

```bash
docker-compose down
```

## Restarting the Application

### Start Infrastructure

```bash
docker-compose up -d
```

### Start Services

```bash
./start-services.sh
```

## Architecture Overview

This application demonstrates:

1. **Saga Pattern**: Distributed transactions across Customer and Order services
2. **CQRS**: Order History service maintains a read model
3. **Transactional Outbox**: Reliable event publishing using database transactions
4. **Event-Driven Architecture**: Services communicate via domain events
5. **CDC (Change Data Capture)**: MySQL binlog reader publishes events to Kafka

## Key Patterns Demonstrated

### Create Order Saga

When an order is created:
1. Order Service creates order (pending state)
2. Saga orchestrator reserves credit in Customer Service
3. If successful, order is approved
4. If credit limit exceeded, order is rejected
5. Events are published to Kafka via transactional outbox
6. Order History Service updates its read model

### Failure Scenarios

Try creating an order that exceeds credit limit to see saga compensation in action:

```bash
curl -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId": "<CUSTOMER_ID>", "orderTotal": {"amount": 10000}}'
```

The order will be rejected and you'll see the saga compensation flow in the logs.

## Documentation

For detailed analysis of the codebase, see:
- `CODEBASE_ANALYSIS.md` - Complete functional & technical analysis
- `ARCHITECTURE_DIAGRAM.md` - System architecture diagrams
- `README_ANALYSIS.md` - Quick reference guide
