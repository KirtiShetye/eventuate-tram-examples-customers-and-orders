package io.eventuate.examples.tram.ordersandcustomers.payments.domain;

public interface PaymentEventPublisher {
  void publish(Payment payment, PaymentEvent event);
}
