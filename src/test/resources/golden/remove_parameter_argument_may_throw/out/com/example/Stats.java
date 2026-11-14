package com.example;

class Stats {
  static String report(int total, int count) {
    return total + " over " + count;
  }

  static String of(int sum, int[] values) {
    return report(sum, values.length);
  }
}
