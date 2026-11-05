package com.example;

public class A {
  int n;

  int next() {
    return n++;
  }

  void g(boolean flag, int x) {
    if (flag) {
      use(x);
    }
  }

  void use(int x) {}

  void f(boolean b) {
    g(b, next());
  }
}
