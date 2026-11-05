package com.example;

public class Price {
  int rate = 2;

  public int total(int a) {
    return charge(a) + 1;
  }

  int charge(int a) {
    return a * rate;
  }
}

class Fees {
}
