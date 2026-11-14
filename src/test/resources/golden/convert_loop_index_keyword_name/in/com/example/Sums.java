package com.example;

public class Sums {
  int sum(int[] ints) {
    int total = 0;
    for (int i = 0; i < ints.length; i++) {
      total += ints[i];
    }
    return total;
  }
}
