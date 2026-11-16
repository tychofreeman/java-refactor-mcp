package com.example;

public class Price {
  int rate = 2;

  void bump() {
    rate = rate + 1;
  }

  void take(Price other) {
    this.rate = other.rate;
  }

  static void swap(Price a, Price b) {
    int rate = a.rate;
    a.rate = b.rate;
    b.rate = rate;
  }

  static void twice(Price p) {
    p.rate = Math.max(p.rate, 1) * 2; // at least 2
  }
}
