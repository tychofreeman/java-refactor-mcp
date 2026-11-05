package com.example;

public class Price {
  int rate = 2;

  int total(int a) {
    return Price.charge(this, a) + 1;
  }

  static int charge(Price receiver, int a) {
    return a * receiver.rate;
  }
}
