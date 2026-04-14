package io.eventuate.examples.tram.ordersandcustomers.dlq;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class DLQConsumer {
  private Logger logger = LoggerFactory.getLogger(getClass());
  private final ObjectMapper objectMapper = new ObjectMapper();

  @KafkaListener(topics = {"payment-service-dlq", "order-service-dlq", "customer-service-dlq"}, groupId = "dlq-monitor")
  public void handleDLQMessage(String message) {
    try {
      logger.error("========================================");
      logger.error("=== DLQ MESSAGE RECEIVED ===");
      logger.error("========================================");
      
      Map<String, Object> dlqMessage = objectMapper.readValue(message, Map.class);
      
      logger.error("Timestamp: {}", dlqMessage.get("timestamp"));
      logger.error("Error Type: {}", dlqMessage.get("errorType"));
      logger.error("Error Message: {}", dlqMessage.get("errorMessage"));
      logger.error("Original Message: {}", dlqMessage.get("originalMessage"));
      logger.error("Stack Trace:\n{}", dlqMessage.get("stackTrace"));
      logger.error("========================================");
    } catch (Exception e) {
      logger.error("Failed to parse DLQ message: {}", message, e);
    }
  }
}
