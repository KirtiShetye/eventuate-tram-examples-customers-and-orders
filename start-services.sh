#!/bin/bash

# Start Customer Service
echo "Starting Customer Service on port 8081..."
java -jar customer-service/customer-service-main/build/libs/customer-service-main-0.1.0-SNAPSHOT.jar \
  --server.port=8081 \
  > logs/customer-service.log 2>&1 &
echo $! > logs/customer-service.pid

# Start Order Service
echo "Starting Order Service on port 8082..."
java -jar order-service/order-service-main/build/libs/order-service-main-0.1.0-SNAPSHOT.jar \
  --server.port=8082 \
  > logs/order-service.log 2>&1 &
echo $! > logs/order-service.pid

# Start Order History Service
echo "Starting Order History Service on port 8083..."
java -jar order-history-service/order-history-service-main/build/libs/order-history-service-main-0.1.0-SNAPSHOT.jar \
  --server.port=8083 \
  --spring.data.mongodb.uri=mongodb://localhost:27017/order_history \
  > logs/order-history-service.log 2>&1 &
echo $! > logs/order-history-service.pid

# Start API Gateway
echo "Starting API Gateway on port 8080..."
java -jar api-gateway-service/api-gateway-service-main/build/libs/api-gateway-service-main-0.1.0-SNAPSHOT.jar \
  --server.port=8080 \
  > logs/api-gateway.log 2>&1 &
echo $! > logs/api-gateway.pid

echo ""
echo "All services started!"
echo "Logs are in ./logs/"
echo ""
echo "Services:"
echo "  - API Gateway: http://localhost:8080"
echo "  - Customer Service: http://localhost:8081"
echo "  - Order Service: http://localhost:8082"
echo "  - Order History Service: http://localhost:8083"
echo ""
echo "To view logs:"
echo "  tail -f logs/customer-service.log"
echo "  tail -f logs/order-service.log"
echo "  tail -f logs/order-history-service.log"
echo "  tail -f logs/api-gateway.log"
