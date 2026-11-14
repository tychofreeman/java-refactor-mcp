package com.example;

public class Price {
  public double total(double a) {
    return a + tax(a);
  }

  static double tax(double amount) {
    return amount * 0.2;
  }
}
