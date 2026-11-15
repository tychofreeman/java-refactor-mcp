package com.example;

public class Pricing {
  int price(int qty) {
    return qty * 2;
  }

  int total() {
    return price(3) + 1;
  }
}
