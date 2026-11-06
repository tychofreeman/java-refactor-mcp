package com.example;

class Util {
  static int twice(int x) {
    return x * 2;
  }

  static int doubled(int y) {
    return y * 2;
  }

  int use(int n) {
    return twice(n) + doubled(n + 1);
  }
}
