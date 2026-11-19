package com.example;

import java.util.List;

public class Calc {
  static int score(List<Integer> xs, int bonus) {
    if (xs.isEmpty()) {
      return -1;
    }
    int sum = 0;
    for (int x : xs) {
      sum += x;
    }
    return sum * 2 + bonus;
  }
}

class Use {
  int f(List<Integer> ys, int k) {
    System.out.println(k);
    if (ys.isEmpty()) {
      return -1;
    }
    int total = 0;
    for (int y : ys) {
      total += y;
    }
    return total * 2 + k;
  }
}
