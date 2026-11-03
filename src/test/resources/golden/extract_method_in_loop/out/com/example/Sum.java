package com.example;

class Sum {
  int sum(int[] xs) {
    int total = 0;
    for (int x : xs) {
      total = addSquare(total, x);
    }
    return total;
  }

  private int addSquare(int total, int x) {
    total += x * x;
    return total;
  }
}
