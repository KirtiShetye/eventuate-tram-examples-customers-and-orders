package io.eventuate.examples.tram.ordersandcustomers.notifications;

import io.eventuate.examples.tram.ordersandcustomers.notifications.eventhandlers.NotificationServiceEventHandlerConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(NotificationServiceEventHandlerConfiguration.class)
public class NotificationServiceMain {
  public static void main(String[] args) {
    SpringApplication.run(NotificationServiceMain.class, args);
  }
}
