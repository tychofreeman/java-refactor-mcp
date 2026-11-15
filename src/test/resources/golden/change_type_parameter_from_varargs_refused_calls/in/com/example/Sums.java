package com.example;

class Sums {
  static int sum(String label, int... values) {
    int total = 0;
    for (int v : values) {
      total += v;
    }
    return total;
  }
}
