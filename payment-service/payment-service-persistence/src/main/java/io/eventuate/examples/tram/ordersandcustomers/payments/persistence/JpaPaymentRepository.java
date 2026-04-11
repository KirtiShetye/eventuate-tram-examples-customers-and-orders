package io.eventuate.examples.tram.ordersandcustomers.payments.persistence;

import io.eventuate.examples.tram.ordersandcustomers.payments.domain.Payment;
import io.eventuate.examples.tram.ordersandcustomers.payments.domain.PaymentRepository;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface JpaPaymentRepository extends CrudRepository<Payment, Long>, PaymentRepository {
  Optional<Payment> findByOrderId(Long orderId);
}
