package io.eventuate.examples.tram.ordersandcustomers.orders.eventhandlers;

import io.eventuate.examples.tram.ordersandcustomers.customers.domain.CustomerCreditReservationFailedEvent;
import io.eventuate.examples.tram.ordersandcustomers.customers.domain.CustomerCreditReservedEvent;
import io.eventuate.examples.tram.ordersandcustomers.customers.domain.CustomerValidationFailedEvent;
import io.eventuate.examples.tram.ordersandcustomers.orders.domain.Order;
import io.eventuate.examples.tram.ordersandcustomers.orders.domain.OrderRepository;
import io.eventuate.examples.tram.ordersandcustomers.orders.domain.OrderService;
import io.eventuate.examples.tram.ordersandcustomers.orders.domain.OrderState;
import io.eventuate.examples.tram.ordersandcustomers.orders.domain.RejectionReason;
import io.eventuate.tram.events.subscriber.DomainEventEnvelope;
import io.eventuate.tram.events.subscriber.annotations.EventuateDomainEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class CustomerEventConsumer {
  private Logger logger = LoggerFactory.getLogger(getClass());

  private final OrderService orderService;
  private final OrderRepository orderRepository;

  public CustomerEventConsumer(OrderService orderService, OrderRepository orderRepository) {
    this.orderService = orderService;
    this.orderRepository = orderRepository;
  }

  @EventuateDomainEventHandler(subscriberId = "customerServiceEvents", channel = "io.eventuate.examples.tram.ordersandcustomers.customers.domain.Customer")
  public void handleCustomerCreditReservedEvent(DomainEventEnvelope<CustomerCreditReservedEvent> domainEventEnvelope) {
    Long orderId = domainEventEnvelope.getEvent().orderId();
    
    Order order = orderRepository.findById(orderId).orElse(null);
    if (order == null || order.getState() == OrderState.APPROVED) {
      logger.info("Order {} already approved or not found", orderId);
      return;
    }
    
    orderService.approveOrder(orderId);
  }

  @EventuateDomainEventHandler(subscriberId = "customerServiceEvents", channel = "io.eventuate.examples.tram.ordersandcustomers.customers.domain.Customer")
  public void handleCustomerCreditReservationFailedEvent(DomainEventEnvelope<CustomerCreditReservationFailedEvent> domainEventEnvelope) {
    Long orderId = domainEventEnvelope.getEvent().orderId();
    
    Order order = orderRepository.findById(orderId).orElse(null);
    if (order == null || order.getState() == OrderState.REJECTED) {
      logger.info("Order {} already rejected or not found", orderId);
      return;
    }
    
    orderService.rejectOrder(orderId, RejectionReason.INSUFFICIENT_CREDIT);
  }

  @EventuateDomainEventHandler(subscriberId = "customerServiceEvents", channel = "io.eventuate.examples.tram.ordersandcustomers.customers.domain.Customer")
  public void handleCustomerValidationFailedEvent(DomainEventEnvelope<CustomerValidationFailedEvent> domainEventEnvelope) {
    Long orderId = domainEventEnvelope.getEvent().orderId();
    
    Order order = orderRepository.findById(orderId).orElse(null);
    if (order == null || order.getState() == OrderState.REJECTED) {
      logger.info("Order {} already rejected or not found", orderId);
      return;
    }
    
    orderService.rejectOrder(orderId, RejectionReason.UNKNOWN_CUSTOMER);
  }

}
