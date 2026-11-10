package com.example;

import java.util.List;

public class Orders {
  boolean anyLate(List<Order> orders) {
    boolean late = orders.stream().anyMatch(order -> order.late());
    return late;
  }

  record Order(boolean late) {}
}
