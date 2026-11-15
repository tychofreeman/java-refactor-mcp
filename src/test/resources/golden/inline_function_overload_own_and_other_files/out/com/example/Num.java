package com.example;

class Num {
  static int twice(int x, int y) {
    return x * y * 2;
  }

  static int eight() {
    return (4 * 2) + twice(1, 2);
  }
}
