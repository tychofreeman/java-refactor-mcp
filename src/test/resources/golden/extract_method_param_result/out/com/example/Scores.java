package com.example;

class Scores {
  int score(int[] xs, int bonus) {
    int total = bonus;
    total = doubled(xs, total);
    return total;
  }

  /** Adds xs to total and doubles it. */
  private int doubled(int[] xs, int total) {
    for (int x : xs) {
      total += x;
    }
    total *= 2;
    return total;
  }
}
