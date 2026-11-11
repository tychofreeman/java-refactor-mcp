package com.example;

import java.util.List;

public class Orders {
  void ship(List<Order> orders) {
    for (Order o : orders) {
      if (o.open()) {
        continue;
      }
      System.out.println(o.id());
    }
  }

  record Order(int id, boolean open) {}
}
