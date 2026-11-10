package com.example;

import java.util.List;

public class Orders {
  boolean allPaid(List<Order> orders) {
    boolean paid = orders.stream().allMatch(order -> order.paid());
    return paid;
  }

  record Order(boolean paid) {}
}
