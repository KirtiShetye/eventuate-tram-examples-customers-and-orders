package io.eventuate.examples.tram.ordersandcustomers.orders.eventhandlers;

import io.eventuate.examples.common.money.Money;
import io.eventuate.tram.events.common.DomainEvent;

public record PaymentDeclinedEvent(Long orderId, Money amount, String reason) implements DomainEvent {
}
