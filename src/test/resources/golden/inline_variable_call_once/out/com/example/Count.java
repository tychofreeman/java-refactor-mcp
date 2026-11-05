package com.example;

public class Count {
  public int next(int[] values) {
    int n = values.length;
    int first = values[0];
    return first + n;
  }

  public int twice(java.util.function.IntSupplier s) {
    return s.getAsInt() + 1;
  }
}
