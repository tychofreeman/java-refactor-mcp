package com.example;

import java.util.List;

public class Orders {
  private final List<Integer> log = new java.util.ArrayList<>();

  void ship(List<Order> orders) {
    orders.stream().filter(order -> order.open()).forEach(order -> {
      log.add(order.id());
      System.out.println(order.id());
    });
  }

  record Order(int id, boolean open) {}
}
