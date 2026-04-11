package io.eventuate.examples.tram.ordersandcustomers.payments.eventhandlers;

import io.eventuate.examples.tram.ordersandcustomers.payments.domain.PaymentService;
import io.eventuate.tram.spring.flyway.EventuateTramFlywayMigrationConfiguration;
import io.eventuate.tram.spring.optimisticlocking.OptimisticLockingDecoratorConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@EnableAutoConfiguration
@Import({OptimisticLockingDecoratorConfiguration.class, EventuateTramFlywayMigrationConfiguration.class})
public class PaymentServiceEventHandlerConfiguration {

  @Bean
  public OrderEventConsumer orderEventConsumer(PaymentService paymentService) {
    return new OrderEventConsumer(paymentService);
  }
}
