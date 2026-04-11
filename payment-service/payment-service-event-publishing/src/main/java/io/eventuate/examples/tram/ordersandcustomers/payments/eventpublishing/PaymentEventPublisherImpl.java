package io.eventuate.examples.tram.ordersandcustomers.payments.eventpublishing;

import io.eventuate.examples.tram.ordersandcustomers.payments.domain.Payment;
import io.eventuate.examples.tram.ordersandcustomers.payments.domain.PaymentEvent;
import io.eventuate.examples.tram.ordersandcustomers.payments.domain.PaymentEventPublisher;
import io.eventuate.tram.events.publisher.AbstractDomainEventPublisherForAggregateImpl;
import io.eventuate.tram.events.publisher.DomainEventPublisher;

public class PaymentEventPublisherImpl extends AbstractDomainEventPublisherForAggregateImpl<Payment, Long, PaymentEvent> implements PaymentEventPublisher {

  public PaymentEventPublisherImpl(DomainEventPublisher domainEventPublisher) {
    super(Payment.class, Payment::getId, domainEventPublisher, PaymentEvent.class);
  }
}
