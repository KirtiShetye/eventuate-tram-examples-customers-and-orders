package io.eventuate.examples.tram.ordersandcustomers.orders.domain;

// Consumer-side copy for deserialization
public record OrderShippedEvent(OrderDetails orderDetails) implements OrderEvent {
}
