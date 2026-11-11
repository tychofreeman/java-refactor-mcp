package com.example;

public class Sums {
  int sum(int[] values) {
    int total = 0;
    for (int value : values) {
      total += value * value;
    }
    return total;
  }
}
