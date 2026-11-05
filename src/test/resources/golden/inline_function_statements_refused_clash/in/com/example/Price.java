package com.example;

public class Price {
  static int total(int a, int b) {
    int sum = a + b;
    return sum * 2;
  }

  static int run(int x) {
    int sum = 0;
    int t = total(x, sum);
    return t;
  }
}
