package com.example;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Orders {
  void print(List<Order> orders) {
    Map<String, List<Order>> byCustomer = new HashMap<>();
    for (Order order : orders) {
      byCustomer.computeIfAbsent(order.customer(), k -> new ArrayList<>()).add(order);
    }
    byCustomer.forEach((customer, theirs) -> System.out.println(customer + theirs.size()));
  }

  record Order(String customer, int id) {}
}
