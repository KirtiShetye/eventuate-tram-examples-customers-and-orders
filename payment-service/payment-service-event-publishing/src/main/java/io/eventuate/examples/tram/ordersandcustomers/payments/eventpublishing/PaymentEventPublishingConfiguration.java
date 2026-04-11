package io.eventuate.examples.tram.ordersandcustomers.payments.eventpublishing;

import io.eventuate.examples.tram.ordersandcustomers.payments.domain.PaymentEventPublisher;
import io.eventuate.tram.events.publisher.DomainEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PaymentEventPublishingConfiguration {

  @Bean
  public PaymentEventPublisher paymentEventPublisher(DomainEventPublisher domainEventPublisher) {
    return new PaymentEventPublisherImpl(domainEventPublisher);
  }
}
