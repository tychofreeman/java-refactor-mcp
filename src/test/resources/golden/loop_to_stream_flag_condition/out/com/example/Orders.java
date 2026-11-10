package com.example;

import java.util.List;

public class Orders {
  boolean anyLate(List<Order> orders) {
    boolean late = orders.stream().map(order -> order.late()).reduce(false, Boolean::logicalOr);
    return late;
  }

  record Order(boolean late) {}
}
