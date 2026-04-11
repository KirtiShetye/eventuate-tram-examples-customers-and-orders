package io.eventuate.examples.tram.ordersandcustomers.payments.domain;

import io.eventuate.examples.common.money.Money;
import io.eventuate.tram.events.common.DomainEvent;

public record OrderApprovedEvent(Long customerId, Money orderTotal) implements DomainEvent {
}
