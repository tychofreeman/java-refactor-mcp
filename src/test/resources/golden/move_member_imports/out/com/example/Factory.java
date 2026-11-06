package com.example;

import com.example.a.Order;

class Factory {
  static Order make() {
    return new Order();
  }
}
