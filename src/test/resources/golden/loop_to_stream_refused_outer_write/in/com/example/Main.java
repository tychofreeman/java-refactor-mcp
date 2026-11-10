package com.example;

import java.util.List;

public class Main {
  int sum(List<Integer> xs) {
    int total = 0;
    for (Integer x : xs) {
      total += x;
    }
    return total;
  }
}
