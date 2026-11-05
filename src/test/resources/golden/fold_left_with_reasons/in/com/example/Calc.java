package com.example;

public class Calc {
  int n;

  static int diff(int a, int b) {
    return b - a;
  }

  int f() {
    return n++;
  }

  int g() {
    return n--;
  }

  int use(int x, int y, boolean c) {
    int p = y - x;
    int q = g() - f();
    int r = c ? x - (n = 3) : 0;
    return p + q + r;
  }
}
