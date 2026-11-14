package com.example;

class Gate {
  static final int OUT = 1;

  int limit(int n) {
    return Math.min(n, Config.OUT) + OUT;
  }

  int run(int n) {
    return limit(n);
  }
}
