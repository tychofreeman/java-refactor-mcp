package com.example;

public class Price {
  private int rate = 2;

  public int total(int a) {
    return a * getRate();
  }

  void setup(int r) {
    setRate(r);
  }

  public int getRate() {
    return rate;
  }

  public void setRate(int rate) {
    this.rate = rate;
  }
}

class Shop {
  int quote(Price p) {
    return p.getRate();
  }
}
