package com.example;

import java.util.List;

public class A {
  int n;

  int next() {
    return n++;
  }

  void use(int x) {}

  void f(List<String> xs) {
    int x = next();
    for (String s : xs) {
      use(x);
    }
  }
}
