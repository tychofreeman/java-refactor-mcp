package com.example;

class Gate {
  static final int OUT = 1;

  int limit(int n, int max) {
    return Math.min(n, max) + OUT;
  }

  int run(int n) {
    return limit(n, Config.OUT);
  }
}
