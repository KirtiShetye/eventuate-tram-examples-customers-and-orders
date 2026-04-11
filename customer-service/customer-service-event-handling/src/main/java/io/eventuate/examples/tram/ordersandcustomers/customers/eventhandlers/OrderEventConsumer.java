package io.eventuate.examples.tram.ordersandcustomers.customers.eventhandlers;

import io.eventuate.examples.tram.ordersandcustomers.customers.domain.CustomerService;
import io.eventuate.examples.tram.ordersandcustomers.orders.domain.OrderCancelledEvent;
import io.eventuate.examples.tram.ordersandcustomers.orders.domain.OrderCreatedEvent;
import io.eventuate.examples.tram.ordersandcustomers.orders.domain.OrderRejectedEvent;
import io.eventuate.examples.tram.ordersandcustomers.orders.domain.OrderShippedEvent;
import io.eventuate.tram.events.subscriber.DomainEventEnvelope;
import io.eventuate.tram.events.subscriber.annotations.EventuateDomainEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OrderEventConsumer {
  private Logger logger = LoggerFactory.getLogger(getClass());

  private CustomerService customerService;

  public OrderEventConsumer(CustomerService customerService) {
    this.customerService = customerService;
  }

  @EventuateDomainEventHandler(subscriberId = "OrderEventConsumer", channel = "io.eventuate.examples.tram.ordersandcustomers.orders.domain.Order")
  public void handleOrderCreatedEvent(DomainEventEnvelope<OrderCreatedEvent> domainEventEnvelope) {
    OrderCreatedEvent event = domainEventEnvelope.getEvent();
    customerService.reserveCredit(Long.parseLong(domainEventEnvelope.getAggregateId()),
            event.orderDetails().customerId(), event.orderDetails().orderTotal());
  }

  @EventuateDomainEventHandler(subscriberId = "OrderEventConsumer", channel = "io.eventuate.examples.tram.ordersandcustomers.orders.domain.Order")
  public void handleOrderCancelledEvent(DomainEventEnvelope<OrderCancelledEvent> domainEventEnvelope) {
    customerService.releaseCredit(Long.parseLong(domainEventEnvelope.getAggregateId()), domainEventEnvelope.getEvent().orderDetails().customerId());
  }

  @EventuateDomainEventHandler(subscriberId = "OrderEventConsumer", channel = "io.eventuate.examples.tram.ordersandcustomers.orders.domain.Order")
  public void handleOrderRejectedEvent(DomainEventEnvelope<OrderRejectedEvent> domainEventEnvelope) {
    logger.info("Order rejected, releasing credit for order: {}", domainEventEnvelope.getAggregateId());
    customerService.releaseCredit(Long.parseLong(domainEventEnvelope.getAggregateId()), domainEventEnvelope.getEvent().orderDetails().customerId());
  }

  @EventuateDomainEventHandler(subscriberId = "OrderEventConsumer", channel = "io.eventuate.examples.tram.ordersandcustomers.orders.domain.Order")
  public void handleOrderShippedEvent(DomainEventEnvelope<OrderShippedEvent> domainEventEnvelope) {
    OrderShippedEvent event = domainEventEnvelope.getEvent();
    Long orderId = Long.parseLong(domainEventEnvelope.getAggregateId());
    System.out.println("Order shipped: " + orderId + " for customer: " + event.orderDetails().customerId());
  }

}
