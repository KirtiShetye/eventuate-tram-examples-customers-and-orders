package io.eventuate.examples.tram.ordersandcustomers.orders.eventhandlers;

import io.eventuate.examples.tram.ordersandcustomers.orders.domain.Order;
import io.eventuate.examples.tram.ordersandcustomers.orders.domain.OrderRepository;
import io.eventuate.examples.tram.ordersandcustomers.orders.domain.OrderService;
import io.eventuate.examples.tram.ordersandcustomers.orders.domain.OrderState;
import io.eventuate.examples.tram.ordersandcustomers.orders.domain.RejectionReason;
import io.eventuate.tram.events.subscriber.DomainEventEnvelope;
import io.eventuate.tram.events.subscriber.annotations.EventuateDomainEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PaymentEventConsumer {
  private Logger logger = LoggerFactory.getLogger(getClass());

  private final OrderService orderService;
  private final OrderRepository orderRepository;

  public PaymentEventConsumer(OrderService orderService, OrderRepository orderRepository) {
    this.orderService = orderService;
    this.orderRepository = orderRepository;
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
    
    Order order = orderRepository.findById(event.orderId()).orElse(null);
    if (order == null || order.getState() == OrderState.REJECTED) {
      logger.info("Order {} already rejected or not found", event.orderId());
      return;
    }
    
    logger.info("Payment declined for order: {} - Rejecting order and releasing credit", event.orderId());
    orderService.rejectOrder(event.orderId(), RejectionReason.PAYMENT_DECLINED);
  }
}
