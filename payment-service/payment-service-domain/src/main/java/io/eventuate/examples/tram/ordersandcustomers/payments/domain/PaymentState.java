package io.eventuate.examples.tram.ordersandcustomers.payments.domain;

public enum PaymentState {
  PENDING,
  APPROVED,
  DECLINED,
  REFUNDED
}
