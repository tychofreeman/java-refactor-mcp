package com.example;

public class A {
  int b(int x) {
    return x + 1;
  }

  int c() {
    int sum = b(2);
    return sum;
  }
}
