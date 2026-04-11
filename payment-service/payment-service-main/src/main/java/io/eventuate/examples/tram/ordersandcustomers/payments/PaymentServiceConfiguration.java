package io.eventuate.examples.tram.ordersandcustomers.payments;

import io.eventuate.examples.tram.ordersandcustomers.payments.domain.PaymentEventPublisher;
import io.eventuate.examples.tram.ordersandcustomers.payments.domain.PaymentRepository;
import io.eventuate.examples.tram.ordersandcustomers.payments.domain.PaymentService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaRepositories
public class PaymentServiceConfiguration {

  @Bean
  public PaymentService paymentService(PaymentRepository paymentRepository, PaymentEventPublisher paymentEventPublisher) {
    return new PaymentService(paymentRepository, paymentEventPublisher);
  }
}
