package com.example;

public class Price {
  static int sub(int a, int b) {
    return b - a;
  }

  int next() {
    return 2;
  }

  public int total() {
    return sub(next(), next());
  }
}
