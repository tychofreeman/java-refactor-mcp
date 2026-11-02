package com.example;

public class A {
  int b(int x, int y) {
    return x + y;
  }

  int c() {
    int total = b(1, 2);
    return total;
  }
}
