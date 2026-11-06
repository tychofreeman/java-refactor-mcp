package com.example;

import com.example.a.Order;

class Shop {
  static Order make() {
    return new Order();
  }

  int size() {
    return 1;
  }
}
