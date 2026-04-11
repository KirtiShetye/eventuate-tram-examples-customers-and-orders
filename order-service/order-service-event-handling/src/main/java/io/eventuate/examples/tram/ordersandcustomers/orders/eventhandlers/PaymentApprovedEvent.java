package io.eventuate.examples.tram.ordersandcustomers.orders.eventhandlers;

import io.eventuate.examples.common.money.Money;
import io.eventuate.tram.events.common.DomainEvent;

public record PaymentApprovedEvent(Long orderId, Money amount) implements DomainEvent {
}
