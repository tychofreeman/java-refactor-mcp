package com.example;

public class Cart {
  private int total;

  /** Adds a price. */
  void add(int price) {
    total += price;
  }

  void add(int price, int qty) {
    total += price * qty;
  }

  int count() {
    return 1;
  }
}
