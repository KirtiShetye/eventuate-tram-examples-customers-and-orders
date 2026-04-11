package io.eventuate.examples.tram.ordersandcustomers.payments.domain;

import io.eventuate.examples.common.money.Money;
import jakarta.persistence.*;

@Entity
@Table(name = "payments")
@Access(AccessType.FIELD)
public class Payment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long orderId;

  @Embedded
  private Money amount;

  @Enumerated(EnumType.STRING)
  private PaymentState state;

  private String declineReason;

  @Version
  private Long version;

  public Payment() {
  }

  public Payment(Long orderId, Money amount) {
    this.orderId = orderId;
    this.amount = amount;
    this.state = PaymentState.PENDING;
  }

  public void approve() {
    this.state = PaymentState.APPROVED;
  }

  public void decline(String reason) {
    this.state = PaymentState.DECLINED;
    this.declineReason = reason;
  }

  public void refund() {
    this.state = PaymentState.REFUNDED;
  }

  public Long getId() {
    return id;
  }

  public Long getOrderId() {
    return orderId;
  }

  public Money getAmount() {
    return amount;
  }

  public PaymentState getState() {
    return state;
  }

  public String getDeclineReason() {
    return declineReason;
  }
}
