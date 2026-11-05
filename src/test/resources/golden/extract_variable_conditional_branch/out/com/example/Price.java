package com.example;

public class Price {
  public int total(boolean any, int price, int qty) {
    int total = price * qty;
    return any ? total : 0;
  }
}
