package com.example;

import java.util.List;

public class A {
  int n;

  int next() {
    return n++;
  }

  void each(List<String> xs, int x) {
    for (String s : xs) {
      use(x);
    }
  }

  void use(int x) {}

  void f(List<String> xs) {
    each(xs, next());
  }
}
