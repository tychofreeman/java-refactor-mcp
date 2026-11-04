package com.example;

import java.util.function.BiFunction;

class Basket {
  void run() {
    combine((a, b) -> a);
  }

  void combine(BiFunction<Object, Object, Object> biFunction) {
    throw new UnsupportedOperationException("not implemented");
  }
}
