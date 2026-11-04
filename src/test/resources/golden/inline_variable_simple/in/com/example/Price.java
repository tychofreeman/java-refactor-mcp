package com.example;

public class Price {
  public int total(int price, int qty, int tax) {
    int subtotal = price * qty;
    return subtotal + tax;
  }
}
