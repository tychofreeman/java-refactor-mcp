package com.example;

public class Tally {
  public int count(boolean a, int[] xs) {
    int n = 0;
    if (!a) {
      for (int x : xs)
        if (x > 1) n++;
    } else {
      n = -1;
    }
    return n;
  }
}
