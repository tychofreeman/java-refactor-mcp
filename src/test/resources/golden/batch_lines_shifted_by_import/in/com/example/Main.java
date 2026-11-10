package com.example;

import java.util.List;

class Main {
  static int total(List<Integer> xs) {
    int sum = 0;
    for (int x : xs) {
      sum += x;
    }
    return sum;
  }

  static int count(List<Integer> xs) {
    int n = 0;
    for (int x : xs) {
      n++;
    }
    return n;
  }
}
