package com.example;

class Walk {
  int depth;

  static int first(int n) {
    return n <= 0 ? 0 : Steps.second(n - 1);
  }

  static int third(int n) {
    return n <= 0 ? 2 : first(n - 1);
  }
}
