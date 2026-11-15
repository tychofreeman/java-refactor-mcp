package com.example;

class Num {
  static int twice(int x) {
    return x * 2;
  }

  static int twice(int x, int y) {
    return x * y * 2;
  }

  static int eight() {
    return twice(4) + twice(1, 2);
  }
}
