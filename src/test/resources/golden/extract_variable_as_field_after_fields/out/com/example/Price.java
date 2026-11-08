package com.example;

public class Price {
  private final int cents;

  private final String label = "tax";

  Price(int cents) {
    this.cents = cents;
  }

  String name() {
    return label;
  }
}
