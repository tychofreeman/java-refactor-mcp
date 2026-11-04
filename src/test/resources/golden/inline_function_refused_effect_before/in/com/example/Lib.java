package com.example;

public class Lib {
  static int count;

  static int g() {
    return count++;
  }

  static int f(int x) {
    return g() + x;
  }
}
