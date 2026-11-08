package com.example;

public class Price {
  private int rate = 2;

  void tick() {
    setRate(getRate() + 1);
    this.setRate(this.getRate() - 1);
  }

  public int getRate() {
    return rate;
  }

  public void setRate(int rate) {
    this.rate = rate;
  }
}

class Shop {
  void up(Price p) {
    p.setRate(p.getRate() + 1);
  }
}
