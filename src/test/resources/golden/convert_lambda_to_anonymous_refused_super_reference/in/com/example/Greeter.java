package com.example;

import java.util.function.Supplier;

class Greeter {
  Supplier<String> who() {
    return () -> name(super::toString);
  }

  static String name(Supplier<String> s) {
    return s.get();
  }
}
