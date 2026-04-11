package io.eventuate.examples.tram.ordersandcustomers.payments.domain;

import java.util.Optional;

public interface PaymentRepository {
  Payment save(Payment payment);
  Optional<Payment> findByOrderId(Long orderId);
}
