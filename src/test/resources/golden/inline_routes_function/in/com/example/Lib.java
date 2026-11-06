package com.example;

import java.util.function.IntSupplier;

public class Lib {
  static IntSupplier later(int x) {
    return () -> x;
  }
}
