package com.example;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Orders {
  void print(List<Order> orders) {
    Map<String, List<Order>> byCustomer = orders.stream()
        .collect(Collectors.groupingBy(order -> order.customer()));
    byCustomer.forEach((customer, theirs) -> System.out.println(customer + theirs.size()));
  }

  record Order(String customer, int id) {}
}
