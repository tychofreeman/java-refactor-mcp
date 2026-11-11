package com.example;

import java.util.List;

public class Orders {
  void ship(List<Order> orders) {
    for (int i = 0; i < orders.size(); ++i) {
      Order o = orders.get(i);
      if (o.open()) {
        continue;
      }
      System.out.println(o.id());
    }
  }

  record Order(int id, boolean open) {}
}
