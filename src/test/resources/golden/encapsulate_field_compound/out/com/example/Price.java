package com.example;

public class Price {
  private int rate = 2;

  void bump(int k) {
    setRate(getRate() + (k + 1));
    setRate(getRate() * 2);
  }

  public int getRate() {
    return rate;
  }

  public void setRate(int rate) {
    this.rate = rate;
  }
}

class Shop {
  void cut(Price p) {
    p.setRate(p.getRate() - 3);
  }
}
