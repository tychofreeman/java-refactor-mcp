package com.example;

public class Gauge {
  double limit;

  static boolean near(double v, double limit) {
    return v >= limit && v - limit < 100;
  }

  boolean check(double v) {
    return near(v, limit);
  }
}
