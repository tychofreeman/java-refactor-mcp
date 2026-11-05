package com.example;

public class A {
  int n;

  int next() {
    return n++;
  }

  void use(int x) {}

  void f(boolean b) {
    int x = next();
    if (b) {
      use(x);
    }
  }
}
