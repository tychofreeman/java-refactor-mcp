package com.example;

public class Price {
  int total;

  void f(int[] xs) {
    total++;
    for (int x : xs) {
      if (x < 0) {
        break;
      }
    }
  }
}
