package com.example;

public class Price {
  private final int cents;

  Price(int cents) {
    this.cents = cents;
  }

  String name() {
    return "tax";
  }
}
