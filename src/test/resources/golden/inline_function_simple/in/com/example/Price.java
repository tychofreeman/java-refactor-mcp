package com.example;

public class Price {
  static double tax(double amount) {
    return amount * 0.2;
  }

  public double total(double a) {
    return a + tax(a);
  }
}
