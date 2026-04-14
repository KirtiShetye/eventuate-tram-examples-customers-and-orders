package io.eventuate.examples.tram.ordersandcustomers.payments.dlq;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

public class DLQPublisher {
  private Logger logger = LoggerFactory.getLogger(getClass());
  private final KafkaProducer<String, String> producer;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public DLQPublisher(KafkaProducer<String, String> producer) {
    this.producer = producer;
  }

  public void publishToDLQ(String dlqTopic, String originalMessage, Exception error) {
    try {
      Map<String, Object> dlqMessage = new HashMap<>();
      dlqMessage.put("originalMessage", originalMessage);
      dlqMessage.put("errorMessage", error.getMessage());
      dlqMessage.put("errorType", error.getClass().getName());
      dlqMessage.put("stackTrace", getStackTrace(error));
      dlqMessage.put("timestamp", System.currentTimeMillis());

      String json = objectMapper.writeValueAsString(dlqMessage);
      producer.send(new ProducerRecord<>(dlqTopic, json));
      logger.error("Published to DLQ topic {}: {}", dlqTopic, error.getMessage());
    } catch (Exception e) {
      logger.error("Failed to publish to DLQ", e);
    }
  }

  private String getStackTrace(Exception e) {
    StringWriter sw = new StringWriter();
    e.printStackTrace(new PrintWriter(sw));
    return sw.toString();
  }
}
