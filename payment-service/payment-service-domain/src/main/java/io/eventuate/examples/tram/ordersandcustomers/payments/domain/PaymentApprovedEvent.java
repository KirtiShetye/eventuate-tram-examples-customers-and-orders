package io.eventuate.examples.tram.ordersandcustomers.payments.domain;

import io.eventuate.examples.common.money.Money;

public record PaymentApprovedEvent(Long orderId, Money amount) implements PaymentEvent {
}
