package com.example;

public class Price {
  static int twice(int x) {
    return x + x;
  }

  static int next() {
    return 1;
  }

  public int go() {
    return twice(next());
  }
}
