package com.example;

public class Price {
  double tax(double a) {
    return a * 0.2;
  }

  double total(double a) {
    return a + tax(a);
  }
}
