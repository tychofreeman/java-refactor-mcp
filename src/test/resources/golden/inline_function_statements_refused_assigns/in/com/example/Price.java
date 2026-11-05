package com.example;

public class Price {
  static int next(int a) {
    a = a + 1;
    return a;
  }

  static int run(int x) {
    int t = next(x);
    return t + x;
  }
}
