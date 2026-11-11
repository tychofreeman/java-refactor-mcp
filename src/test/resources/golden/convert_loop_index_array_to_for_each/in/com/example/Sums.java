package com.example;

public class Sums {
  int sum(int[] values) {
    int total = 0;
    for (int i = 0; i < values.length; i += 1) {
      total += values[i] * values[i];
    }
    return total;
  }
}
