package com.example;

public class Counter {
  private final RuntimeException err = new IllegalArgumentException("zero");
  private int count;

  public int f(int n) {
    if (n == 0) throw err;
    count++;
    return 10 / n;
  }
}
