package com.example;

public class Sums {
  public int sum(int[] values) {
    int acc = 0;
    for (int v : values) {
      acc = acc + v;
    }
    return acc;
  }
}
