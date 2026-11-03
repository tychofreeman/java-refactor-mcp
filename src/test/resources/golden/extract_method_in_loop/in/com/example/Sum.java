package com.example;

class Sum {
  int sum(int[] xs) {
    int total = 0;
    for (int x : xs) {
      total += x * x;
    }
    return total;
  }
}
