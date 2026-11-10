package com.example;

import java.util.List;

public class Orders {
  int open(List<Order> orders) {
    int open = 0;
    for (Order order : orders) {
      if (order.open()) {
        open++;
      }
    }
    return open;
  }

  record Order(boolean open) {}
}
