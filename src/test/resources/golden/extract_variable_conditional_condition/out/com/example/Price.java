package com.example;

public class Price {
  public int total(int price, int qty) {
    boolean big = price * qty > 100;
    return big ? price : qty;
  }
}
