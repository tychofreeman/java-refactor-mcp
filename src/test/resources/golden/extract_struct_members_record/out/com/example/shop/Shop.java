package com.example.shop;

import com.example.Address;
import com.example.Order;

class Shop {
  Order order() {
    return new Order("1", new Address("Main St", "Springfield"), 5);
  }

  String where(Order o) {
    return o.address().street() + " " + o.address().city();
  }
}
