package io.eventuate.examples.tram.ordersandcustomers.notifications.domain;

public class NotificationService {

  public void notifyOrderCreated(Long orderId, Long customerId, String amount) {
    System.out.println("📧 NOTIFICATION: Order " + orderId + " created for customer " + customerId + " with amount $" + amount);
  }

  public void notifyOrderShipped(Long orderId, Long customerId) {
    System.out.println("📦 NOTIFICATION: Order " + orderId + " shipped to customer " + customerId);
  }
}
