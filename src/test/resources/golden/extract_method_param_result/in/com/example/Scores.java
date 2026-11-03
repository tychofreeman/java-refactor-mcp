package com.example;

class Scores {
  int score(int[] xs, int bonus) {
    int total = bonus;
    for (int x : xs) {
      total += x;
    }
    total *= 2;
    return total;
  }
}
