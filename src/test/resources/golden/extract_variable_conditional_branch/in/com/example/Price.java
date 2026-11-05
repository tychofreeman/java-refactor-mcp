package com.example;

public class Price {
  public int total(boolean any, int price, int qty) {
    return any ? price * qty : 0;
  }
}
