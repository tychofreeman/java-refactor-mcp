package com.example;

class Stats {
  static String report(int total, int count, int mean) {
    return total + " over " + count + ": " + mean;
  }

  static String of(int[] values) {
    int sum = 0;
    for (int v : values) {
      sum += v;
    }
    return report(sum, values.length, sum / values.length);
  }
}
