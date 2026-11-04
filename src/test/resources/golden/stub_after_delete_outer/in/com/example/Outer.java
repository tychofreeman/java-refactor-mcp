package com.example;

class Outer {
  static int label(int x) {
    return x;
  }

  static class Inner {
    int show() {
      return label(1);
    }
  }
}
