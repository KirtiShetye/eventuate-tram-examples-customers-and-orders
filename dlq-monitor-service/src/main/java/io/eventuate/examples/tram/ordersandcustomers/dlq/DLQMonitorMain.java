package io.eventuate.examples.tram.ordersandcustomers.dlq;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DLQMonitorMain {
  public static void main(String[] args) {
    SpringApplication.run(DLQMonitorMain.class, args);
  }
}
