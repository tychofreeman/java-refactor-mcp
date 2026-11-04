package com.example;

import java.util.function.Supplier;

class Basket {
  void run() {
    store(() -> 1);
  }

  void store(Supplier<Object> supplier) {
    throw new UnsupportedOperationException("not implemented");
  }
}
