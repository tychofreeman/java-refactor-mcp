package com.example;

import java.util.List;

public class Orders {
  int open(List<Order> orders) {
    int open = (int) orders.stream().filter(order -> order.open()).count();
    return open;
  }

  record Order(boolean open) {}
}
