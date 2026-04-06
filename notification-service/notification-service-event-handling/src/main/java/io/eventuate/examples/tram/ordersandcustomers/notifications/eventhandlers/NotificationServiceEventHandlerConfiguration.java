package io.eventuate.examples.tram.ordersandcustomers.notifications.eventhandlers;

import io.eventuate.examples.tram.ordersandcustomers.notifications.domain.NotificationService;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableAutoConfiguration
public class NotificationServiceEventHandlerConfiguration {

  @Bean
  public NotificationService notificationService() {
    return new NotificationService();
  }

  @Bean
  public OrderEventConsumer orderEventConsumer(NotificationService notificationService) {
    return new OrderEventConsumer(notificationService);
  }
}
