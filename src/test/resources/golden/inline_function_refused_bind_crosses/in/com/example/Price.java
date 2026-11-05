package com.example;

public class Price {
  int n;

  int next() {
    return n++;
  }

  static int twice(int x) {
    return x + x;
  }

  int f() {
    return next() + twice(next());
  }
}
