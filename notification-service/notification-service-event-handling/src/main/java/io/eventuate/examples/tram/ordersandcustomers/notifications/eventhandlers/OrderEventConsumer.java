package io.eventuate.examples.tram.ordersandcustomers.notifications.eventhandlers;

import io.eventuate.examples.tram.ordersandcustomers.notifications.domain.NotificationService;
import io.eventuate.examples.tram.ordersandcustomers.orders.domain.OrderCreatedEvent;
import io.eventuate.examples.tram.ordersandcustomers.orders.domain.OrderShippedEvent;
import io.eventuate.tram.events.subscriber.DomainEventEnvelope;
import io.eventuate.tram.events.subscriber.annotations.EventuateDomainEventHandler;

public class OrderEventConsumer {

  private final NotificationService notificationService;

  public OrderEventConsumer(NotificationService notificationService) {
    this.notificationService = notificationService;
  }

  @EventuateDomainEventHandler(
    subscriberId = "notificationServiceEvents",
    channel = "io.eventuate.examples.tram.ordersandcustomers.orders.domain.Order"
  )
  public void handleOrderCreatedEvent(DomainEventEnvelope<OrderCreatedEvent> envelope) {
    OrderCreatedEvent event = envelope.getEvent();
    Long orderId = Long.parseLong(envelope.getAggregateId());
    notificationService.notifyOrderCreated(
      orderId,
      event.orderDetails().customerId(),
      event.orderDetails().orderTotal().getAmount().toString()
    );
  }

  @EventuateDomainEventHandler(
    subscriberId = "notificationServiceEvents",
    channel = "io.eventuate.examples.tram.ordersandcustomers.orders.domain.Order"
  )
  public void handleOrderShippedEvent(DomainEventEnvelope<OrderShippedEvent> envelope) {
    OrderShippedEvent event = envelope.getEvent();
    Long orderId = Long.parseLong(envelope.getAggregateId());
    notificationService.notifyOrderShipped(orderId, event.orderDetails().customerId());
  }
}
