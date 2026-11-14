package com.example;

class Sum {
  static int sum(int a, int b, int c) {
    return a + b + c;
  }

  static int run() {
    int x = 1;
    return sum(x, x++, x * 2);
  }
}
