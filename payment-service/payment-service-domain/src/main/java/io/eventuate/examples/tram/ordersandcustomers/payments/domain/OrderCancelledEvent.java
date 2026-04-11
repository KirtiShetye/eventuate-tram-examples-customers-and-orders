package io.eventuate.examples.tram.ordersandcustomers.payments.domain;

import io.eventuate.tram.events.common.DomainEvent;

public record OrderCancelledEvent() implements DomainEvent {
}
