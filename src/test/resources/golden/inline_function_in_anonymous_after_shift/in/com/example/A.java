package com.example;

import java.util.function.IntUnaryOperator;

public class A {
  static int twice(int x) {
    return Twice.of(x);
  }

  int f(int a) {
    int b = twice(a);
    IntUnaryOperator op =
        new IntUnaryOperator() {
          @Override
          public int applyAsInt(int n) {
            return twice(n);
          }
        };
    return op.applyAsInt(b);
  }
}

class Twice {
  static int of(int x) {
    return x * 2;
  }
}
