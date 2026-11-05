package com.example;

public class Price {
  private static final double RATE = 0.2;

  double tax(double a) {
    return a * RATE;
  }
}
