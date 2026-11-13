package com.example;

class Tally {
  private int factor = 2;
  private int total;

  void add(int amount, int scaled, int step) {
    total += amount + scaled + step;
  }

  int raise() {
    return ++factor;
  }

  void run(int n) {
    add(n, n * factor, raise());
  }
}
