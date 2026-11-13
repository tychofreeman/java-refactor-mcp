package com.example;

public class Stats {
  int average(int total, int count) {
    int avg = total / count;
    if (total < 0) {
      return 0;
    }
    return avg;
  }
}
