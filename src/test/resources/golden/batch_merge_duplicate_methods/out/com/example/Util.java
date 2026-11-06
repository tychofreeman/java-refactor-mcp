package com.example;

class Util {
  static int twice(int x) {
    return x * 2;
  }

  int use(int n) {
    return twice(n) + twice(n + 1);
  }
}
