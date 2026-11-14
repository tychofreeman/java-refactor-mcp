package com.example;

public class Tally {
  public int count(boolean a, int[] xs) {
    int n = 0;
    if (a) {
      n = -1;
    } else
      for (int x : xs)
        if (x > 1) n++;
    return n;
  }
}
