package com.example;

import java.util.List;

public class Orders {
  boolean allPaid(List<Order> orders) {
    boolean paid = true;
    for (Order order : orders) {
      if (!order.paid()) {
        paid = false;
        break;
      }
    }
    return paid;
  }

  record Order(boolean paid) {}
}
