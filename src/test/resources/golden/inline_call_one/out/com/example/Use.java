package com.example;

public class Use {
  double a(double p) {
    return p + Lib.tax(p);
  }

  double b(double q) {
    return q - (q * 0.2);
  }
}
