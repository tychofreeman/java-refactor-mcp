package com.example;

public class Price {
  int rate = 2;

  public int total(int a) {
    return a * rate;
  }

  void setup(int r) {
    rate = r;
  }
}

class Shop {
  int quote(Price p) {
    return p.rate;
  }
}
