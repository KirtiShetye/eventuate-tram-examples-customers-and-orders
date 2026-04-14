package io.eventuate.examples.tram.ordersandcustomers.payments.eventhandlers;

import io.eventuate.examples.tram.ordersandcustomers.payments.dlq.DLQPublisher;
import io.eventuate.examples.tram.ordersandcustomers.payments.domain.PaymentRepository;
import io.eventuate.examples.tram.ordersandcustomers.payments.domain.PaymentService;
import io.eventuate.tram.spring.flyway.EventuateTramFlywayMigrationConfiguration;
import io.eventuate.tram.spring.optimisticlocking.OptimisticLockingDecoratorConfiguration;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.util.Properties;

@Configuration
@EnableAutoConfiguration
@Import({OptimisticLockingDecoratorConfiguration.class, EventuateTramFlywayMigrationConfiguration.class})
public class PaymentServiceEventHandlerConfiguration {

  @Bean
  public DLQPublisher dlqPublisher(@Value("${eventuatelocal.kafka.bootstrap.servers}") String bootstrapServers) {
    Properties props = new Properties();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    KafkaProducer<String, String> producer = new KafkaProducer<>(props);
    return new DLQPublisher(producer);
  }

  @Bean
  public OrderEventConsumer orderEventConsumer(PaymentService paymentService, PaymentRepository paymentRepository, DLQPublisher dlqPublisher) {
    return new OrderEventConsumer(paymentService, paymentRepository, dlqPublisher);
  }
}
