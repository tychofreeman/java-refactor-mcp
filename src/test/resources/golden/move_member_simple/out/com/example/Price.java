package com.example;

public class Price {
  int rate = 2;

  public int total(int a) {
    return Fees.charge(this, a) + 1;
  }
}

class Fees {
  int fee(int a) {
    return a;
  }

  static int charge(Price receiver, int a) {
    return a * receiver.rate;
  }
}
