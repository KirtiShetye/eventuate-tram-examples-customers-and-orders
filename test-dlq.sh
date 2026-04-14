#!/bin/bash

# Test script for Dead Letter Queue (DLQ) functionality
# Tests poison message routing to DLQ and monitoring

set -e

BASE_URL="http://localhost:8082"

echo "=== DLQ Test Script ==="
echo ""

# Create customer first
echo "1. Creating customer with \$100 credit limit..."
CUSTOMER_RESPONSE=$(curl -s -X POST http://localhost:8081/customers \
  -H "Content-Type: application/json" \
  -d '{"name": "DLQ Test Customer", "creditLimit": {"amount": "100.00"}}')
CUSTOMER_ID=$(echo $CUSTOMER_RESPONSE | grep -o '"customerId":[0-9]*' | grep -o '[0-9]*')
echo "Customer created: $CUSTOMER_ID"
echo ""

# Test 1: Normal order (should succeed)
echo "2. Creating normal order for \$50 (should succeed)..."
ORDER1=$(curl -s -X POST $BASE_URL/orders \
  -H "Content-Type: application/json" \
  -d "{\"customerId\": $CUSTOMER_ID, \"orderTotal\": {\"amount\": \"50.00\"}}")
ORDER1_ID=$(echo $ORDER1 | grep -o '"orderId":[0-9]*' | grep -o '[0-9]*')
echo "Order created: $ORDER1_ID"
echo ""

echo "3. Waiting for saga to complete..."
sleep 5

echo "4. Checking order state (should be APPROVED)..."
curl -s $BASE_URL/orders/$ORDER1_ID | grep -o '"state":"[A-Z]*"'
echo ""

# Test 2: Poison message (amount = 999.99)
echo "5. Creating POISON MESSAGE order for \$999.99..."
ORDER2=$(curl -s -X POST $BASE_URL/orders \
  -H "Content-Type: application/json" \
  -d "{\"customerId\": $CUSTOMER_ID, \"orderTotal\": {\"amount\": \"999.99\"}}")
ORDER2_ID=$(echo $ORDER2 | grep -o '"orderId":[0-9]*' | grep -o '[0-9]*')
echo "Poison order created: $ORDER2_ID"
echo ""

echo "6. Waiting for poison message processing..."
sleep 5

echo "7. Checking DLQ topic for poison message..."
docker exec eventuate-tram-examples-customers-and-orders-kafka-1 \
  kafka-console-consumer \
  --bootstrap-server kafka:29092 \
  --topic payment-service-dlq \
  --from-beginning \
  --max-messages 1 \
  --timeout-ms 5000 2>/dev/null || echo "No messages in DLQ yet"
echo ""

echo "8. Checking DLQ monitor logs..."
echo "Last 20 lines from DLQ monitor:"
tail -20 /tmp/dlq-monitor.log | grep -A 10 "DLQ MESSAGE" || echo "No DLQ messages logged yet"
echo ""

echo "9. Checking payment service logs for error..."
docker-compose logs payment-service 2>&1 | grep -i "poison\|failed to process" | tail -5
echo ""

echo "=== Test Complete ==="
echo ""
echo "Expected results:"
echo "- Order 1 (\$50): APPROVED with payment"
echo "- Order 2 (\$999.99): APPROVED but payment fails with poison message"
echo "- DLQ topic contains error message"
echo "- DLQ monitor logs the failure details"
echo ""
echo "To manually check DLQ topic:"
echo "docker exec eventuate-tram-examples-customers-and-orders-kafka-1 \\"
echo "  kafka-console-consumer --bootstrap-server kafka:29092 \\"
echo "  --topic payment-service-dlq --from-beginning"
