package com.example;

public class Price {
  static double withTax(double amount) {
    return amount * 1.2;
  }

  double total(double x) {
    return x * 1.3;
  }
}
