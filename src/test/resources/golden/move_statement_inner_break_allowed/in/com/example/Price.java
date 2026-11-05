package com.example;

public class Price {
  int total;

  void f(int[] xs) {
    for (int x : xs) {
      if (x < 0) {
        break;
      }
    }
    total++;
  }
}
