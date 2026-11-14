package com.example;

public class Stats {
  int average(int total, int count) {
    if (total < 0) {
      return 0;
    }
    int avg = total / count;
    return avg;
  }
}
