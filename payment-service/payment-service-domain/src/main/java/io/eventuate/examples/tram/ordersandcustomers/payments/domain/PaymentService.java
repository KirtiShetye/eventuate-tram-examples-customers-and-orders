package io.eventuate.examples.tram.ordersandcustomers.payments.domain;

import io.eventuate.examples.common.money.Money;
import org.springframework.transaction.annotation.Transactional;

public class PaymentService {

  private final PaymentRepository paymentRepository;
  private final PaymentEventPublisher paymentEventPublisher;

  public PaymentService(PaymentRepository paymentRepository, PaymentEventPublisher paymentEventPublisher) {
    this.paymentRepository = paymentRepository;
    this.paymentEventPublisher = paymentEventPublisher;
  }

  @Transactional
  public void processPayment(Long orderId, Money amount) {
    if (paymentRepository.findByOrderId(orderId).isPresent()) {
      return; // Idempotency
    }

    // Poison message detection for DLQ testing
    if (amount.getAmount().compareTo(java.math.BigDecimal.valueOf(999.99)) == 0) {
      throw new IllegalArgumentException("POISON MESSAGE: Test DLQ routing for amount 999.99");
    }

    Payment payment = new Payment(orderId, amount);

    // Stub logic: approve if amount < 100
    if (amount.getAmount().compareTo(java.math.BigDecimal.valueOf(100)) < 0) {
      payment.approve();
      paymentRepository.save(payment);
      paymentEventPublisher.publish(payment, new PaymentApprovedEvent(orderId, amount));
    } else {
      payment.decline("Amount exceeds limit");
      paymentRepository.save(payment);
      paymentEventPublisher.publish(payment, new PaymentDeclinedEvent(orderId, amount, "Amount exceeds limit"));
    }
  }

  @Transactional
  public void refundPayment(Long orderId) {
    Payment payment = paymentRepository.findByOrderId(orderId)
        .orElseThrow(() -> new IllegalArgumentException("Payment not found for order: " + orderId));

    if (payment.getState() == PaymentState.APPROVED) {
      payment.refund();
      paymentEventPublisher.publish(payment, new PaymentRefundedEvent(orderId, payment.getAmount()));
    }
  }
}
