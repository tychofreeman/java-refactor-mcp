package com.example;

public class Price {
  static int total(int price, int qty, int extra) {
    int sum = (price + extra) * qty;
    return sum * 2;
  }
}
