package com.example;

public class Price {
  static int one(int x) {
    return 1;
  }

  int next() {
    return 2;
  }

  public int total() {
    return one(next());
  }
}
