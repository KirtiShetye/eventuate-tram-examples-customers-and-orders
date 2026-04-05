package io.eventuate.examples.tram.ordersandcustomers.orders.domain;

public record OrderShippedEvent(OrderDetails orderDetails) implements OrderEvent {
}
