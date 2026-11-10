package com.example;

import java.util.List;

public class Orders {
  boolean anyLate(List<Order> orders) {
    boolean late = false;
    for (Order order : orders) {
      if (order.late()) {
        late = true;
        break;
      }
    }
    return late;
  }

  record Order(boolean late) {}
}
