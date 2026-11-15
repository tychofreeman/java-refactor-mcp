package com.example;

public class Price {
  double tax(double a, double rate) {
    return a * rate;
  }

  double total(double a) {
    return a + tax(a, 0.2);
  }
}
