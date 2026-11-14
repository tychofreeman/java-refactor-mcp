package com.example;

class Steps {
  static int second(int n) {
    return n <= 0 ? 1 : Walk.third(n - 1);
  }

  static int keep() {
    return 1;
  }
}
