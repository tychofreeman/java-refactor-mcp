package com.example;

public class Price {
  private int rate = 2;

  void bump() {
    setRate(getRate() + 1);
  }

  void take(Price other) {
    this.setRate(other.getRate());
  }

  static void swap(Price a, Price b) {
    int rate = a.getRate();
    a.setRate(b.getRate());
    b.setRate(rate);
  }

  static void twice(Price p) {
    p.setRate(Math.max(p.getRate(), 1) * 2); // at least 2
  }

  public int getRate() {
    return rate;
  }

  public void setRate(int rate) {
    this.rate = rate;
  }
}
