package io.eventuate.examples.tram.ordersandcustomers.payments.eventhandlers;

import io.eventuate.examples.tram.ordersandcustomers.payments.domain.OrderApprovedEvent;
import io.eventuate.examples.tram.ordersandcustomers.payments.domain.OrderCancelledEvent;
import io.eventuate.examples.tram.ordersandcustomers.payments.domain.PaymentService;
import io.eventuate.tram.events.subscriber.DomainEventEnvelope;
import io.eventuate.tram.events.subscriber.annotations.EventuateDomainEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OrderEventConsumer {
  private Logger logger = LoggerFactory.getLogger(getClass());

  private final PaymentService paymentService;

  public OrderEventConsumer(PaymentService paymentService) {
    this.paymentService = paymentService;
  }

  @EventuateDomainEventHandler(subscriberId = "PaymentOrderEventConsumer", channel = "io.eventuate.examples.tram.ordersandcustomers.orders.domain.Order")
  public void handleOrderApprovedEvent(DomainEventEnvelope<OrderApprovedEvent> domainEventEnvelope) {
    OrderApprovedEvent event = domainEventEnvelope.getEvent();
    Long orderId = Long.parseLong(domainEventEnvelope.getAggregateId());
    logger.info("Processing payment for approved order: {}", orderId);
    paymentService.processPayment(orderId, event.orderTotal());
  }

  @EventuateDomainEventHandler(subscriberId = "PaymentOrderEventConsumer", channel = "io.eventuate.examples.tram.ordersandcustomers.orders.domain.Order")
  public void handleOrderCancelledEvent(DomainEventEnvelope<OrderCancelledEvent> domainEventEnvelope) {
    Long orderId = Long.parseLong(domainEventEnvelope.getAggregateId());
    logger.info("Refunding payment for cancelled order: {}", orderId);
    paymentService.refundPayment(orderId);
  }
}
