package com.example;

public class Price {
  static int total(int a, int b) {
    int sum = a + b;
    return sum * 2;
  }

  static int run(int x) {
    int t = 1 + total(x, 1);
    return t;
  }
}
