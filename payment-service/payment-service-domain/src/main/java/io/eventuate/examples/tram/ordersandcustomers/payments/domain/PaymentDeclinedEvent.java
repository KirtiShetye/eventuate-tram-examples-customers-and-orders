package io.eventuate.examples.tram.ordersandcustomers.payments.domain;

import io.eventuate.examples.common.money.Money;

public record PaymentDeclinedEvent(Long orderId, Money amount, String reason) implements PaymentEvent {
}
