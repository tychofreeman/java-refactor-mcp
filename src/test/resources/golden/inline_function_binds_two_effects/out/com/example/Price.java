package com.example;

public class Price {
  int next() {
    return 2;
  }

  public int total() {
    int a = next();
    int b = next();
    return b - a;
  }
}
