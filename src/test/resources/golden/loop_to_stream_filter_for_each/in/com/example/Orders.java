package com.example;

import java.util.List;

public class Orders {
  private final List<Integer> log = new java.util.ArrayList<>();

  void ship(List<Order> orders) {
    for (Order order : orders) {
      if (order.open()) {
        log.add(order.id());
        System.out.println(order.id());
      }
    }
  }

  record Order(int id, boolean open) {}
}
