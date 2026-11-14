package com.example;

public class Sums {
  int sum(int[] ints) {
    int total = 0;
    for (int item : ints) {
      total += item;
    }
    return total;
  }
}
