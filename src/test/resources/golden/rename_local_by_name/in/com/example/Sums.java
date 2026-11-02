package com.example;

public class Sums {
  public int sum(int[] values) {
    int sum = 0;
    for (int v : values) {
      sum = sum + v;
    }
    return sum;
  }
}
