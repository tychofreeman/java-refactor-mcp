package com.example;

import java.util.function.IntSupplier;

public class A {
  int f(IntSupplier s, Runnable log) {
    int n = s.getAsInt();
    log.run();
    return n;
  }
}
