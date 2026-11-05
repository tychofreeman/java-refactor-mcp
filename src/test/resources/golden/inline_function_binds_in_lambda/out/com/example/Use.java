package com.example;

import java.util.function.IntSupplier;

public class Use {
  private int n;

  int next() {
    return n++;
  }

  IntSupplier all() {
    int x = next();
    return () -> x;
  }
}
