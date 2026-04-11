package io.eventuate.examples.tram.ordersandcustomers.orders.eventhandlers;

import io.eventuate.examples.tram.ordersandcustomers.orders.domain.OrderService;
import io.eventuate.examples.tram.ordersandcustomers.orders.domain.RejectionReason;
import io.eventuate.tram.events.subscriber.DomainEventEnvelope;
import io.eventuate.tram.events.subscriber.annotations.EventuateDomainEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PaymentEventConsumer {
  private Logger logger = LoggerFactory.getLogger(getClass());

  private final OrderService orderService;

  public PaymentEventConsumer(OrderService orderService) {
    this.orderService = orderService;
  }

  @EventuateDomainEventHandler(subscriberId = "OrderPaymentEventConsumer", channel = "io.eventuate.examples.tram.ordersandcustomers.payments.domain.Payment")
  public void handlePaymentApprovedEvent(DomainEventEnvelope<PaymentApprovedEvent> domainEventEnvelope) {
    PaymentApprovedEvent event = domainEventEnvelope.getEvent();
    logger.info("Payment approved for order: {} - Order remains APPROVED", event.orderId());
    // Order is already APPROVED after credit reservation, no action needed
  }

  @EventuateDomainEventHandler(subscriberId = "OrderPaymentEventConsumer", channel = "io.eventuate.examples.tram.ordersandcustomers.payments.domain.Payment")
  public void handlePaymentDeclinedEvent(DomainEventEnvelope<PaymentDeclinedEvent> domainEventEnvelope) {
    PaymentDeclinedEvent event = domainEventEnvelope.getEvent();
    logger.info("Payment declined for order: {} - Rejecting order and releasing credit", event.orderId());
    // Reject the order - this should trigger credit release in customer service
    orderService.rejectOrder(event.orderId(), RejectionReason.PAYMENT_DECLINED);
  }
}
