package com.example;

import java.util.function.Supplier;

class Greeter {
  Supplier<String> who() {
    return new Supplier<String>() {
      @Override
      public String get() {
        return super.getClass().getName();
      }
    };
  }
}
