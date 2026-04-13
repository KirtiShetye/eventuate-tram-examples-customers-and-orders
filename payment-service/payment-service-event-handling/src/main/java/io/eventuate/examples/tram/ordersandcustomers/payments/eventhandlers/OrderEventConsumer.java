package io.eventuate.examples.tram.ordersandcustomers.payments.eventhandlers;

import io.eventuate.examples.tram.ordersandcustomers.payments.domain.OrderApprovedEvent;
import io.eventuate.examples.tram.ordersandcustomers.payments.domain.OrderCancelledEvent;
import io.eventuate.examples.tram.ordersandcustomers.payments.domain.PaymentRepository;
import io.eventuate.examples.tram.ordersandcustomers.payments.domain.PaymentService;
import io.eventuate.tram.events.subscriber.DomainEventEnvelope;
import io.eventuate.tram.events.subscriber.annotations.EventuateDomainEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OrderEventConsumer {
  private Logger logger = LoggerFactory.getLogger(getClass());

  private final PaymentService paymentService;
  private final PaymentRepository paymentRepository;

  public OrderEventConsumer(PaymentService paymentService, PaymentRepository paymentRepository) {
    this.paymentService = paymentService;
    this.paymentRepository = paymentRepository;
  }

  @EventuateDomainEventHandler(subscriberId = "PaymentOrderEventConsumer", channel = "io.eventuate.examples.tram.ordersandcustomers.orders.domain.Order")
  public void handleOrderApprovedEvent(DomainEventEnvelope<OrderApprovedEvent> domainEventEnvelope) {
    OrderApprovedEvent event = domainEventEnvelope.getEvent();
    Long orderId = Long.parseLong(domainEventEnvelope.getAggregateId());
    
    if (paymentRepository.findByOrderId(orderId).isPresent()) {
      logger.info("Payment already processed for order: {}", orderId);
      return;
    }
    
    logger.info("Processing payment for approved order: {}", orderId);
    paymentService.processPayment(orderId, event.orderTotal());
  }

  @EventuateDomainEventHandler(subscriberId = "PaymentOrderEventConsumer", channel = "io.eventuate.examples.tram.ordersandcustomers.orders.domain.Order")
  public void handleOrderCancelledEvent(DomainEventEnvelope<OrderCancelledEvent> domainEventEnvelope) {
    Long orderId = Long.parseLong(domainEventEnvelope.getAggregateId());
    
    if (paymentRepository.findByOrderId(orderId).isEmpty()) {
      logger.info("No payment found for cancelled order: {}", orderId);
      return;
    }
    
    logger.info("Refunding payment for cancelled order: {}", orderId);
    paymentService.refundPayment(orderId);
  }
}
