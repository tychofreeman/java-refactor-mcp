package com.example;

public class Price {
  static int next() {
    return 1;
  }

  public int go() {
    int x = next();
    return x + x;
  }
}
