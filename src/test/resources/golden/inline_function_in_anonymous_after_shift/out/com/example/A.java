package com.example;

import java.util.function.IntUnaryOperator;

public class A {
  int f(int a) {
    int b = Twice.of(a);
    IntUnaryOperator op =
        new IntUnaryOperator() {
          @Override
          public int applyAsInt(int n) {
            return Twice.of(n);
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
