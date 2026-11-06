package com.example.shop;

import com.example.Order;

class Shop {
  Order order() {
    return new Order("1", "Main St", "Springfield", 5);
  }

  String where(Order o) {
    return o.street() + " " + o.city();
  }
}
